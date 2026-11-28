package qrmaster.web.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Coin system — WEBSITE client (Ayan ka order 2026-11-28; pehle ye
 * sirf Android app me tha). Android app ke CoinLogic/CoinsApi ka
 * wafadar web port.
 *
 * Sona niyam app wala hi: coins SIRF Worker (payment server) likhta
 * hai — users/{uid}.coins ek protected key hai, ye client balance
 * kabhi khud nahi badalta. Har grant/redeem Worker ke purane
 * endpoints se hi hota hai, isliye app aur website ka balance EK hi
 * hai (wahi users/{uid} doc).
 *
 * Web-khass baatein:
 *  - Spin ka BLNT inaam (1–2 coin) POST /v1/spin/coinprize se — key
 *    usi spin ke committed counters se banti hai, server user doc ke
 *    asal counters se jaanchta hai.
 *  - Watch-Ads-&-Spin: POST /v1/spin/adstart + /v1/spin/adstep —
 *    2 sponsored steps = +1 gift spin (server token, roz ki hadd
 *    webSpinAdDailyCap). Asal ad-dekhna web par SDK callback se
 *    verify NAHI hota; taale token + hadd + idempotency hain.
 *  - Welcome: device lock ke liye webDeviceHash() (browser ki
 *    persisted UUID ka SHA-256) — Android ke ANDROID_ID se kamzor
 *    hai (storage saaf karne se badal sakta hai); server ke
 *    ek-account-ek-baar + referral niyam waise ke waise hain.
 */
data class CoinWebConfig(
    val enabled: Boolean = true,
    val coinsPerAd: Int = 4,
    val playRedeemCoins: Int = 200,
    val amazonRedeemCoins: Int = 200,
    val spinAdCoins: Int = 3,
    val welcomeCoins: Int = 200,
    val redeemUnlockAds: Int = 5,
    val referralCoins: Int = 50,
    val spinCoinPrizeDailyCap: Int = 20,
    val webSpinAdDailyCap: Int = 10,
    val adSpinSmartlinkUrl: String = ""
) {
    fun costFor(type: String): Int = if (type == TYPE_AMAZON) amazonRedeemCoins else playRedeemCoins

    companion object {
        const val TYPE_PLAY = "play"
        const val TYPE_AMAZON = "amazon"
    }
}

fun coinRewardName(type: String?): String = when (type) {
    CoinWebConfig.TYPE_AMAZON -> "Amazon Pay Gift Card"
    else -> "Google Play Code"
}

/** Redeem Policy — app ke CoinLogic.policyLines ki hu-ba-hu copy (settings-driven numbers ke saath). */
fun coinPolicyLines(cfg: CoinWebConfig): List<String> = listOf(
    "Your reward is delivered within 24 hours of your redeem request.",
    "Every redeem request is reviewed and approved by our team before delivery.",
    "You can have only one pending redeem request at a time.",
    "Redeem unlocks after you watch ${cfg.redeemUnlockAds} rewarded ads in total.",
    "Delivery depends on code stock. If codes are out of stock, your request stays pending until new codes arrive — your coins stay safely on hold.",
    "If your request is rejected, the held coins are refunded in full to your coin balance.",
    "Cheating — such as using cloned or dual apps, modified apps or fake ad views — can lead to a coin ban, coin reversal, or both.",
    "Coins have no cash value and cannot be transferred to another account."
)

/** Server error code -> saaf English sandesh (app ka errorMessage + web ke naye codes). */
fun coinErrorMessage(code: String?, cfg: CoinWebConfig): String = when (code) {
    "coin_daily_cap" -> "You have watched all of today's coin ads. Come back tomorrow!"
    "spin_ad_daily_cap" -> "You have used all of today's ad spins. Come back tomorrow!"
    "spin_coin_prize_cap" -> "You have earned all of today's spin coins. Come back tomorrow!"
    "grant_too_fast", "step_too_fast" -> "Please wait a few seconds and try again."
    "insufficient_coins" -> "Not enough coins yet — keep earning to collect more."
    "redeem_locked" -> "Watch ${cfg.redeemUnlockAds} ads in the Kitnook Android app to unlock redeem."
    "redeem_pending_exists" -> "You already have a redeem request waiting for approval."
    "coin_banned" -> "Coin rewards have been disabled for this account."
    "account_banned" -> "This account is suspended."
    "wallet_frozen" -> "Your wallet is frozen — rewards are paused."
    "device_used" -> "Welcome bonus was already claimed on this device."
    "first_spin_required" -> "Complete your first spin to claim the welcome bonus."
    "clone_detected" -> "Rewards cannot be earned from a cloned or dual app. Please use the original Kitnook app."
    "policy_agreement_required" -> "Please read and agree to the Redeem Policy first."
    "invalid_code" -> "Invalid gift code — please check and try again."
    "code_expired" -> "This gift code has expired."
    "code_exhausted" -> "This gift code is fully used up."
    "already_used" -> "You have already used this gift code."
    "code_bound_to_other" -> "This gift code was made for another account."
    "already_claimed" -> "This gift was already claimed."
    "not_your_gift" -> "This gift belongs to another account."
    "gift_not_found" -> "This gift is no longer available."
    "code_unavailable" -> "This gift code is not available right now — please try again later."
    "spin_not_verified" -> "That spin could not be verified, so no coins were added."
    "invalid_token", "token_used" -> "This ad session has expired. Please start again."
    "token_expired" -> "This ad session took too long. Please start again."
    "auth_required", "auth_invalid" -> "Session expired — please log in again."
    else -> "Something went wrong — please try again."
}

// ─────────────────────────── History / rewards ─────────────────────────────

data class CoinTxnWeb(
    val id: String,
    val type: String,
    val coins: Long,
    val spins: Long,
    val rewardType: String,
    val reason: String,
    val createdAtMs: Long
) {
    /** App ke CoinLogic.historyLabel ki copy (+ web ka spin_prize). */
    fun label(): String {
        val signed = if (coins >= 0) "+$coins" else "$coins"
        return when (type) {
            "earn" -> "$signed Coins — Watched ad"
            "spin_ad" -> "$signed Coins — Spin ad (+$spins spins)"
            "spin_prize" -> "$signed Coins — Spin prize"
            "welcome_first_spin" -> "$signed Coins — Welcome bonus"
            "referral_bonus" -> "$signed Coins — Referral bonus"
            "redeem_hold" -> "$signed Coins — Redeem requested (${coinRewardName(rewardType)})"
            "redeem_refund" -> "$signed Coins — Redeem refund"
            "redeem_approved" -> "Reward approved (${coinRewardName(rewardType)})"
            "gift_code" -> "$signed Coins — Gift code"
            "admin_gift" -> "$signed Coins — Gift from Admin" + (if (reason.isNotBlank()) ": $reason" else "")
            "coin_reversal" -> "$signed Coins — Adjusted by Admin" + (if (reason.isNotBlank()) ": $reason" else "")
            else -> "$signed Coins"
        }
    }
}

data class CoinRewardWeb(
    val id: String,
    val type: String,
    val coins: Long,
    val status: String,
    val code: String,
    val reason: String,
    val createdAtMs: Long
)

data class CoinGiftWeb(
    val id: String,
    val kind: String,
    val amount: Long,
    val code: String,
    val note: String,
    val status: String,
    val createdAtMs: Long
) {
    fun label(): String = when (kind) {
        "play_code" -> "Google Play Code"
        "amazon_code" -> "Amazon Pay Gift Card"
        "coins" -> "$amount Coins"
        "credits" -> "$amount Credits"
        else -> "Gift"
    }
}

data class CoinWebState(
    val balance: Long = 0L,
    val history: List<CoinTxnWeb> = emptyList(),
    val rewards: List<CoinRewardWeb> = emptyList(),
    val pendingGifts: List<CoinGiftWeb> = emptyList()
) {
    /** Kul rewarded ads (earn + spin_ad) — app ki ginati, history se hi. */
    val lifetimeAds: Int get() = history.count { it.type == "earn" || it.type == "spin_ad" }
    val welcomeReceived: Boolean get() = history.any { it.type == "welcome_first_spin" }
    val pendingRedeem: CoinRewardWeb? get() = rewards.firstOrNull { it.status == "pending" }
}

/** Sirf balance (header chip ke liye halka read). */
suspend fun loadCoinBalance(): Long? {
    val token = FirebaseApi.validToken() ?: return null
    val uid = FirebaseApi.uid ?: return null
    val doc = fsGetDoc("users/$uid", token)
    return (doc as? DocFetch.Found)?.fields?.eLong("coins")
}

/** Balance + history + pending gifts Firestore se; rewards Worker se. */
suspend fun loadCoinWebState(): CoinWebState? {
    val token = FirebaseApi.validToken() ?: return null
    val uid = FirebaseApi.uid ?: return null
    val userDoc = fsGetDoc("users/$uid", token)
    val balance = (userDoc as? DocFetch.Found)?.fields?.eLong("coins") ?: 0L

    val txnRows = fsQueryEq("coinTxns", "uid", uid, 100, token) ?: emptyList()
    val history = txnRows.map { (id, f) ->
        CoinTxnWeb(
            id = id,
            type = f.eStr("type"),
            coins = f.eLong("coins"),
            spins = f.eLong("spins"),
            rewardType = f.eStr("rewardType"),
            reason = f.eStr("reason"),
            createdAtMs = f.eTsMs("createdAt")
        )
    }.sortedByDescending { it.createdAtMs }

    val giftRows = fsQueryEq("userGifts", "userId", uid, 50, token) ?: emptyList()
    val gifts = giftRows.map { (id, f) ->
        CoinGiftWeb(
            id = id,
            kind = f.eStr("kind"),
            amount = f.eLong("amount"),
            code = f.eStr("code"),
            note = f.eStr("note"),
            status = f.eStr("status"),
            createdAtMs = f.eTsMs("createdAt")
        )
    }.filter { it.status == "pending" }.sortedByDescending { it.createdAtMs }

    val rewards = CoinsApi.myRewards() ?: emptyList()
    return CoinWebState(balance, history, rewards, gifts)
}

// ─────────────────────────── Worker client ─────────────────────────────────

object CoinsApi {
    private val json = Json { ignoreUnknownKeys = true }
    private const val API_BASE = "https://linktoqr-payment-api.ayansikligar98.workers.dev"

    data class CallResult(val ok: Boolean, val errorCode: String?, val body: JsonObject?)

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.content ?: ""

    private fun JsonObject.lng(key: String): Long =
        this[key]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

    private fun JsonObject.bool(key: String): Boolean =
        this[key]?.jsonPrimitive?.content == "true"

    private suspend fun post(path: String, bodyJson: String): CallResult {
        val token = FirebaseApi.validToken()
            ?: return CallResult(false, "auth_required", null)
        return try {
            val res = httpRequest("POST", API_BASE + path, bodyJson, token)
            val o = json.parseToJsonElement(res).jsonObject
            CallResult(o.bool("ok"), null, o)
        } catch (e: HttpException) {
            val code = try {
                json.parseToJsonElement(e.body).jsonObject.str("error")
            } catch (x: Exception) { "http_${e.status}" }
            CallResult(false, code.ifBlank { "http_${e.status}" }, null)
        } catch (e: Exception) {
            CallResult(false, "network", null)
        }
    }

    private suspend fun get(path: String): CallResult {
        val token = FirebaseApi.validToken()
            ?: return CallResult(false, "auth_required", null)
        return try {
            val res = httpRequest("GET", API_BASE + path, null, token)
            val o = json.parseToJsonElement(res).jsonObject
            CallResult(o.bool("ok"), null, o)
        } catch (e: HttpException) {
            val code = try {
                json.parseToJsonElement(e.body).jsonObject.str("error")
            } catch (x: Exception) { "http_${e.status}" }
            CallResult(false, code.ifBlank { "http_${e.status}" }, null)
        } catch (e: Exception) {
            CallResult(false, "network", null)
        }
    }

    /** Gift-card redeem — coins hold hote hain, code Admin approval ke baad myRewards se milta hai. */
    suspend fun redeem(type: String, policyAgreedAt: Long): CallResult {
        val key = randomUuid()
        return post(
            "/v1/coins/redeem",
            """{"type":${FirebaseApi.jsonStr(type)},"idempotencyKey":${FirebaseApi.jsonStr(key)},"policyAgreedAt":$policyAgreedAt}"""
        )
    }

    suspend fun myRewards(): List<CoinRewardWeb>? {
        val r = get("/v1/coins/myrewards")
        if (!r.ok) return null
        val arr = r.body?.get("rewards") as? kotlinx.serialization.json.JsonArray ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            CoinRewardWeb(
                id = o.str("id"),
                type = o.str("type"),
                coins = o.lng("coins"),
                status = o.str("status"),
                code = o.str("code"),
                reason = o.str("reason"),
                createdAtMs = o.lng("createdAt")
            )
        }
    }

    suspend fun giftRedeem(code: String): CallResult =
        post(
            "/v1/coins/gift-redeem",
            """{"code":${FirebaseApi.jsonStr(code)},"idempotencyKey":${FirebaseApi.jsonStr(randomUuid())}}"""
        )

    suspend fun giftClaim(giftId: String): CallResult =
        post(
            "/v1/coins/gift-claim",
            """{"giftId":${FirebaseApi.jsonStr(giftId)},"idempotencyKey":${FirebaseApi.jsonStr(randomUuid())}}"""
        )

    /** Pehle spin ke baad welcome daava — server device lock + ek-baar niyam lagata hai. */
    suspend fun claimWelcome(): CallResult =
        post(
            "/v1/coins/welcome",
            """{"idempotencyKey":${FirebaseApi.jsonStr(randomUuid())},"deviceHash":${FirebaseApi.jsonStr(webDeviceHash())}}"""
        )

    /** BLNT spin ka coin inaam — key usi spin ke committed counters se. */
    suspend fun spinCoinPrize(spinDate: String, usedToday: Int, giftUsed: Int): CallResult {
        val key = "spinprize_${spinDate}_${usedToday}_${giftUsed}"
        return post(
            "/v1/spin/coinprize",
            """{"idempotencyKey":${FirebaseApi.jsonStr(key)},"spinDate":${FirebaseApi.jsonStr(spinDate)},"usedToday":$usedToday,"giftUsed":$giftUsed,"deviceHash":${FirebaseApi.jsonStr(webDeviceHash())}}"""
        )
    }

    /** Ad-earned spin ka BLNT faisla — server tay karta hai: asal BLNT ya 1–2 coin. */
    suspend fun adSpinResult(spinDate: String, usedToday: Int, giftUsed: Int): CallResult {
        val key = "spinprize_${spinDate}_${usedToday}_${giftUsed}"
        return post(
            "/v1/spin/adspin-result",
            """{"idempotencyKey":${FirebaseApi.jsonStr(key)},"spinDate":${FirebaseApi.jsonStr(spinDate)},"usedToday":$usedToday,"giftUsed":$giftUsed,"deviceHash":${FirebaseApi.jsonStr(webDeviceHash())}}"""
        )
    }

    /** Watch-Ads-&-Spin: token shuru (step 0). */
    suspend fun adStart(): CallResult =
        post("/v1/spin/adstart", """{"deviceHash":${FirebaseApi.jsonStr(webDeviceHash())}}""")

    /** Ek sponsored step poora — dusre step par +1 gift spin unlock hota hai. */
    suspend fun adStep(token: String): CallResult =
        post(
            "/v1/spin/adstep",
            """{"token":${FirebaseApi.jsonStr(token)},"deviceHash":${FirebaseApi.jsonStr(webDeviceHash())}}"""
        )
}

/** Worker jawab se typed maan (CoinsApi.CallResult par). */
fun CoinsApi.CallResult.lng(key: String): Long =
    body?.get(key)?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

fun CoinsApi.CallResult.str(key: String): String =
    body?.get(key)?.jsonPrimitive?.content ?: ""

fun CoinsApi.CallResult.bool(key: String): Boolean =
    body?.get(key)?.jsonPrimitive?.content == "true"
