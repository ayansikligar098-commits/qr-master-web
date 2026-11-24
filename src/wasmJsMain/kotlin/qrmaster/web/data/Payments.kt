package qrmaster.web.data

import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Razorpay online payments via the Kitnook payment server (TEST MODE).
 *
 * The server is the only authority: it reads prices from settings/config,
 * creates the Razorpay order, and grants premium/credits ONLY after it has
 * fetched the payment from Razorpay and confirmed status "captured".
 * This client never sends an amount for premium/packs and never grants
 * anything itself — it just runs the checkout and reports the outcome.
 */
object PaymentsApi {
    private val json = Json { ignoreUnknownKeys = true }
    private const val API_BASE = "https://linktoqr-payment-api.ayansikligar98.workers.dev"

    data class Order(
        val orderId: String,
        val razorpayOrderId: String,
        val amountPaise: Long,
        val currency: String,
        val keyId: String
    )

    sealed interface CheckoutOutcome {
        /** Server confirmed the payment and granted the benefit. */
        data class Granted(val alreadyProcessed: Boolean) : CheckoutOutcome
        /** Payment captured at Razorpay but not settled yet — never a grant promise. */
        data object Processing : CheckoutOutcome
        data class Failed(val message: String) : CheckoutOutcome
        data object Dismissed : CheckoutOutcome
    }

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.content ?: ""

    private fun errorCodeOf(e: Exception): String {
        if (e is HttpException) {
            try {
                return json.parseToJsonElement(e.body).jsonObject.str("error")
            } catch (x: Exception) { }
            return "http_${e.status}"
        }
        return ""
    }

    fun friendlyPayError(code: String): String = when (code) {
        "account_banned" -> "This account is banned, so online payment is not available."
        "wallet_frozen" -> "Your wallet is frozen, so online payment is not available."
        "rate_limited" -> "Too many payment attempts. Please wait a few minutes and try again."
        "backend_not_configured", "payments_locked" ->
            "Online payments are not available right now. Please use the UPI request option below."
        "coupon_invalid" -> "That coupon code is not valid for online payment."
        "coupon_expired" -> "That coupon has expired."
        "coupon_limit_reached" -> "That coupon has reached its usage limit."
        "coupon_covers_full_amount" ->
            "Your coupon covers the full price — use the free claim option instead of online payment."
        "invalid_pack", "invalid_qty", "invalid_plan", "invalid_kind", "invalid_amount" ->
            "This payment option is not available. Please try again."
        "user_not_found" -> "Your profile was not found. Please log in again."
        else -> "Payment could not be started. Please try again."
    }

    private suspend fun post(path: String, body: String): JsonObject {
        val token = FirebaseApi.validToken()
            ?: throw Exception("Please log in again to pay online.")
        val res = httpRequest("POST", API_BASE + path, body, token)
        return json.parseToJsonElement(res).jsonObject
    }

    suspend fun createPremiumOrder(planKey: String, couponCode: String?): Result<Order> {
        return try {
            val couponPart = if (!couponCode.isNullOrBlank())
                ""","couponCode":${FirebaseApi.jsonStr(couponCode.trim().uppercase())}""" else ""
            val o = post("/v1/orders", """{"kind":"premium","plan":${FirebaseApi.jsonStr(planKey)}$couponPart}""")
            Result.success(o.toOrder())
        } catch (e: Exception) {
            Result.failure(Exception(friendlyPayError(errorCodeOf(e))))
        }
    }

    suspend fun createCreditPackOrder(packCredits: Int, qty: Int): Result<Order> {
        return try {
            val o = post("/v1/orders", """{"kind":"credit_pack","pack":$packCredits,"qty":$qty}""")
            Result.success(o.toOrder())
        } catch (e: Exception) {
            Result.failure(Exception(friendlyPayError(errorCodeOf(e))))
        }
    }

    private fun JsonObject.toOrder(): Order = Order(
        orderId = str("orderId"),
        razorpayOrderId = str("razorpayOrderId"),
        amountPaise = str("amountPaise").toLongOrNull() ?: 0L,
        currency = str("currency").ifBlank { "INR" },
        keyId = str("keyId")
    )

    /** Returns the server's verify status: granted / already_processed / processing / failed. */
    private suspend fun verify(orderId: String, paymentId: String, signature: String): String {
        val body = """{"orderId":${FirebaseApi.jsonStr(orderId)},"paymentId":${FirebaseApi.jsonStr(paymentId)},"signature":${FirebaseApi.jsonStr(signature)}}"""
        val o = post("/v1/payments/verify", body)
        return o.str("status")
    }

    /**
     * Opens Razorpay Checkout for [order] and, on a completed payment,
     * asks the server to verify + grant. The checkout outcome alone never
     * grants anything — only the server's verify answer does.
     */
    suspend fun runCheckout(order: Order, description: String, email: String, name: String): CheckoutOutcome {
        val opts = buildString {
            append("{")
            append("\"key\":${FirebaseApi.jsonStr(order.keyId)},")
            append("\"amount\":${order.amountPaise},")
            append("\"currency\":${FirebaseApi.jsonStr(order.currency)},")
            append("\"description\":${FirebaseApi.jsonStr(description)},")
            append("\"order_id\":${FirebaseApi.jsonStr(order.razorpayOrderId)},")
            append("\"prefill\":{\"email\":${FirebaseApi.jsonStr(email)},\"name\":${FirebaseApi.jsonStr(name)}}")
            append("}")
        }
        qrmaster.web.ui.startRazorpayCheckout(opts)
        var outcome: JsonObject? = null
        var waited = 0
        while (waited < 600) { // up to ~5 minutes inside the checkout
            delay(500)
            val raw = qrmaster.web.ui.pollRazorpay()
            if (raw.isNotBlank()) {
                outcome = try { json.parseToJsonElement(raw).jsonObject } catch (e: Exception) { null }
                if (outcome != null) break
            }
            waited++
        }
        val o = outcome ?: return CheckoutOutcome.Failed(
            "The payment window closed without a result. If money was deducted, it will be confirmed automatically — please check back in a few minutes."
        )
        return when (o.str("state")) {
            "paid" -> {
                try {
                    when (verify(order.orderId, o.str("paymentId"), o.str("signature"))) {
                        "granted" -> CheckoutOutcome.Granted(alreadyProcessed = false)
                        "already_processed" -> CheckoutOutcome.Granted(alreadyProcessed = true)
                        "processing" -> CheckoutOutcome.Processing
                        else -> CheckoutOutcome.Failed(
                            "Payment could not be confirmed. If money was deducted, it will be settled automatically — please check back in a few minutes."
                        )
                    }
                } catch (e: Exception) {
                    // The webhook settles captured payments even when this
                    // confirmation call cannot get through.
                    CheckoutOutcome.Processing
                }
            }
            "failed" -> CheckoutOutcome.Failed(
                o.str("message").ifBlank { "Payment failed. Please try again." }
            )
            "dismissed" -> CheckoutOutcome.Dismissed
            else -> CheckoutOutcome.Failed(
                o.str("message").ifBlank { "Payment could not be completed. Please try again." }
            )
        }
    }
}
