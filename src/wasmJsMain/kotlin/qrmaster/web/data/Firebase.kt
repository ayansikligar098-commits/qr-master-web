package qrmaster.web.data

import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class HttpException(val status: Int, val body: String) : Exception("HTTP $status")

/**
 * HTTP via XMLHttpRequest — reliable on Kotlin/Wasm (no engine lookup),
 * with a hard 20s timeout so the UI never spins forever.
 */
suspend fun httpRequest(
    method: String,
    url: String,
    body: String? = null,
    token: String? = null
): String = suspendCancellableCoroutine { cont ->
    val xhr = XMLHttpRequest()
    xhr.open(method, url, async = true)
    xhr.timeout = 20000
    if (body != null) xhr.setRequestHeader("Content-Type", "application/json")
    if (token != null) xhr.setRequestHeader("Authorization", "Bearer $token")
    xhr.addEventListener("load", {
        val text = xhr.responseText
        val st = xhr.status.toInt()
        if (st in 200..299) cont.resume(text)
        else cont.resumeWithException(HttpException(st, text))
    })
    xhr.addEventListener("error", {
        cont.resumeWithException(Exception("Network error. Check your internet connection and try again."))
    })
    xhr.addEventListener("timeout", {
        cont.resumeWithException(Exception("Request timed out. Your internet seems slow — please try again."))
    })
    cont.invokeOnCancellation { try { xhr.abort() } catch (e: Exception) { } }
    if (body != null) xhr.send(body) else xhr.send()
}

@OptIn(kotlin.time.ExperimentalTime::class)
fun nowMillis(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

data class WebUser(
    val uid: String,
    val email: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAtMs: Long
)

data class Profile(
    val name: String = "",
    val email: String = "",
    val walletPaise: Long = 0,
    val credits: Long = 0,
    val isPremium: Boolean = false,
    val premiumExpiryMs: Long = 0,
    val premiumStatus: String = "FREE",
    val plan: String = "",
    val banned: Boolean = false,
    val walletFrozen: Boolean = false,
    val walletFreezeReason: String = "",
    val spinUsedToday: Long = 0,
    val spinDate: String = "",
    val giftSpins: Long = 0,
    val giftSpinsUsed: Long = 0,
    val notifSpins: Long = 0,
    val notifSpinsUsed: Long = 0,
    val spinDebt: Long = 0,
    val checkinDate: String = "",
    val checkinStreak: Long = 0,
    val referCode: String = "",
    val referralRedeemed: Boolean = false
)

data class TaskItem(
    val id: String,
    val title: String,
    val description: String,
    val rewardPaise: Long,
    val link: String,
    val conditions: String,
    val mode: String,
    val payDays: Long,
    val applyDays: Long
)

data class HistoryItem(
    val title: String,
    val subtitle: String,
    val amountText: String,
    val positive: Boolean?,
    val status: String,
    val createdAtMs: Long
)

object FirebaseApi {
    private val json = Json { ignoreUnknownKeys = true }
    private const val AUTH_BASE = "https://identitytoolkit.googleapis.com/v1"
    private const val TOKEN_URL = "https://securetoken.googleapis.com/v1/token"
    private const val FS_BASE =
        "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents"

    var session: WebUser? = null
        private set

    /** Convenience accessors used by the earn engine. */
    val uid: String? get() = session?.uid
    fun currentEmail(): String? = session?.email

    init {
        restoreSession()
    }

    private fun restoreSession() {
        try {
            val ls = window.localStorage
            val uid = ls.getItem("qm_uid")
            val idt = ls.getItem("qm_idt")
            val rft = ls.getItem("qm_rft")
            val exp = ls.getItem("qm_exp")?.toLongOrNull() ?: 0L
            if (!uid.isNullOrBlank() && !idt.isNullOrBlank() && !rft.isNullOrBlank()) {
                session = WebUser(uid, ls.getItem("qm_email") ?: "", idt, rft, exp)
            }
        } catch (e: Exception) { }
    }

    private fun persist(u: WebUser?) {
        session = u
        try {
            val ls = window.localStorage
            if (u == null) {
                listOf("qm_uid", "qm_email", "qm_idt", "qm_rft", "qm_exp").forEach { ls.removeItem(it) }
            } else {
                ls.setItem("qm_uid", u.uid)
                ls.setItem("qm_email", u.email)
                ls.setItem("qm_idt", u.idToken)
                ls.setItem("qm_rft", u.refreshToken)
                ls.setItem("qm_exp", u.expiresAtMs.toString())
            }
        } catch (e: Exception) { }
    }

    fun signOut() = persist(null)

    fun friendlyError(e: Exception): String {
        if (e is HttpException) {
            val msg = try {
                json.parseToJsonElement(e.body).jsonObject["error"]?.jsonObject?.get("message")
                    ?.jsonPrimitive?.content ?: ""
            } catch (x: Exception) { "" }
            return when {
                msg.contains("INVALID_LOGIN_CREDENTIALS") || msg.contains("EMAIL_NOT_FOUND") ||
                    msg.contains("INVALID_PASSWORD") -> "Email or password is wrong. Please check and try again."
                msg.contains("EMAIL_EXISTS") -> "This email is already registered. Please log in instead."
                msg.contains("WEAK_PASSWORD") -> "Password is too weak — use at least 6 characters."
                msg.contains("INVALID_EMAIL") -> "Please enter a valid email address."
                msg.contains("TOO_MANY_ATTEMPTS") -> "Too many attempts. Please wait and try again later."
                msg.contains("USER_DISABLED") -> "This account has been disabled."
                msg.contains("PERMISSION_DENIED") -> "Permission denied. Please log in again."
                msg.isNotBlank() -> "Something went wrong. Please try again."
                else -> "Request failed. Please try again."
            }
        }
        return e.message ?: "Something went wrong. Please try again."
    }

    suspend fun signIn(email: String, password: String): Result<WebUser> {
        return try {
            val body = """{"email":${jsonStr(email)},"password":${jsonStr(password)},"returnSecureToken":true}"""
            val res = httpRequest("POST", "$AUTH_BASE/accounts:signInWithPassword?key=${FirebaseConfig.API_KEY}", body)
            val o = json.parseToJsonElement(res).jsonObject
            val u = WebUser(
                uid = o.str("localId"),
                email = o.str("email").ifBlank { email },
                idToken = o.str("idToken"),
                refreshToken = o.str("refreshToken"),
                expiresAtMs = nowMillis() + (o.str("expiresIn").toLongOrNull() ?: 3600L) * 1000L
            )
            persist(u)
            Result.success(u)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    suspend fun signUp(name: String, email: String, password: String): Result<WebUser> {
        return try {
            val body = """{"email":${jsonStr(email)},"password":${jsonStr(password)},"returnSecureToken":true}"""
            val res = httpRequest("POST", "$AUTH_BASE/accounts:signUp?key=${FirebaseConfig.API_KEY}", body)
            val o = json.parseToJsonElement(res).jsonObject
            val u = WebUser(
                uid = o.str("localId"),
                email = o.str("email").ifBlank { email },
                idToken = o.str("idToken"),
                refreshToken = o.str("refreshToken"),
                expiresAtMs = nowMillis() + (o.str("expiresIn").toLongOrNull() ?: 3600L) * 1000L
            )
            persist(u)
            // Create the profile document EXACTLY like the Android app does:
            // a plain profile (name / email / lastActive only). The Firestore
            // rules reject client writes that touch protected money/premium
            // fields, so wallet/credits/premium are never written here.
            try { writePlainProfile(u, name, email) } catch (e: Exception) { /* login stays valid */ }
            Result.success(u)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    /**
     * Writes ONLY the plain-profile fields (name / email / lastActive) with a
     * field mask — the exact write the Android app's ProfileSync performs.
     * Creates the document when missing; never touches protected fields.
     */
    private suspend fun writePlainProfile(u: WebUser, name: String, email: String) {
        val fields = buildString {
            append("{\"fields\":{")
            append("\"name\":{\"stringValue\":${jsonStr(name)}},")
            append("\"email\":{\"stringValue\":${jsonStr(email)}},")
            append("\"lastActive\":{\"timestampValue\":\"${isoFromMs(nowMillis())}\"}")
            append("}}")
        }
        httpRequest(
            "PATCH",
            "$FS_BASE/users/${u.uid}?updateMask.fieldPaths=name&updateMask.fieldPaths=email&updateMask.fieldPaths=lastActive",
            fields, u.idToken
        )
    }

    /**
     * Google sign-in via the Firebase JS SDK popup (bridge in Platform.kt).
     * Firebase itself creates the account for first-time Google users and
     * signs existing users into the same account; afterwards the session is
     * a normal WebUser handled by this REST layer.
     */
    suspend fun signInWithGoogle(): Result<WebUser> {
        return try {
            qrmaster.web.ui.startGoogleSignIn(
                FirebaseConfig.API_KEY, FirebaseConfig.AUTH_DOMAIN, FirebaseConfig.PROJECT_ID
            )
            var outcome: JsonObject? = null
            var waited = 0
            while (waited < 240) {
                delay(500)
                val raw = qrmaster.web.ui.pollGoogleSignIn()
                if (raw.isNotBlank()) {
                    val o = json.parseToJsonElement(raw).jsonObject
                    if (o.str("state") != "pending") { outcome = o; break }
                }
                waited++
            }
            val o = outcome ?: throw Exception("Google sign-in timed out. Please try again.")
            if (o.str("state") == "error") {
                val code = o.str("code")
                throw Exception(
                    when {
                        code.contains("unauthorized-domain") ->
                            "Google sign-in is not enabled for this website domain yet."
                        code.contains("popup-closed") || code.contains("cancelled") ->
                            "Google sign-in was cancelled."
                        code.contains("popup-blocked") ->
                            "Your browser blocked the Google popup. Please allow popups for this site and try again."
                        else -> o.str("message").ifBlank { "Google sign-in failed. Please try again." }
                    }
                )
            }
            val u = WebUser(
                uid = o.str("uid"),
                email = o.str("email"),
                idToken = o.str("idToken"),
                refreshToken = o.str("refreshToken"),
                expiresAtMs = nowMillis() + 3600_000L
            )
            persist(u)
            // First-time Google users get their plain profile; for existing
            // users this only refreshes name/email/lastActive (app parity).
            try {
                val gName = o.str("name").ifBlank { u.email.substringBefore("@") }
                writePlainProfile(u, gName, u.email)
            } catch (e: Exception) { /* session stays valid */ }
            Result.success(u)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    suspend fun validToken(): String? {
        val s = session ?: return null
        if (nowMillis() < s.expiresAtMs - 60_000L) return s.idToken
        return try {
            val res = httpRequestForm(
                "$TOKEN_URL?key=${FirebaseConfig.API_KEY}",
                "grant_type=refresh_token&refresh_token=${s.refreshToken}"
            )
            val o = json.parseToJsonElement(res).jsonObject
            val u = WebUser(
                uid = o.str("user_id").ifBlank { s.uid },
                email = s.email,
                idToken = o.str("id_token"),
                refreshToken = o.str("refresh_token").ifBlank { s.refreshToken },
                expiresAtMs = nowMillis() + (o.str("expires_in").toLongOrNull() ?: 3600L) * 1000L
            )
            persist(u)
            u.idToken
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun httpRequestForm(url: String, formBody: String): String =
        suspendCancellableCoroutine { cont ->
            val xhr = XMLHttpRequest()
            xhr.open("POST", url, async = true)
            xhr.timeout = 20000
            xhr.setRequestHeader("Content-Type", "application/x-www-form-urlencoded")
            xhr.addEventListener("load", {
                val st = xhr.status.toInt()
                if (st in 200..299) cont.resume(xhr.responseText)
                else cont.resumeWithException(HttpException(st, xhr.responseText))
            })
            xhr.addEventListener("error", { cont.resumeWithException(Exception("Network error")) })
            xhr.addEventListener("timeout", { cont.resumeWithException(Exception("Request timed out")) })
            cont.invokeOnCancellation { try { xhr.abort() } catch (e: Exception) { } }
            xhr.send(formBody)
        }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            val body = """{"requestType":"PASSWORD_RESET","email":${jsonStr(email)}}"""
            httpRequest("POST", "$AUTH_BASE/accounts:sendOobCode?key=${FirebaseConfig.API_KEY}", body)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    // ---------------- Firestore helpers ----------------

    private fun JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.content ?: ""

    private fun fieldsOf(doc: JsonObject): JsonObject? = doc["fields"]?.jsonObject

    private fun JsonObject.fStr(key: String): String =
        this[key]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""

    private fun JsonObject.fLong(key: String): Long {
        val f = this[key]?.jsonObject ?: return 0L
        return f["integerValue"]?.jsonPrimitive?.content?.toLongOrNull()
            ?: f["doubleValue"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: 0L
    }

    private fun JsonObject.fBool(key: String): Boolean =
        this[key]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

    private fun JsonObject.fTimestampMs(key: String): Long {
        val s = this[key]?.jsonObject?.get("timestampValue")?.jsonPrimitive?.content ?: return 0L
        return parseIsoMs(s)
    }

    private fun JsonObject.fText(key: String): String {
        val f = this[key]?.jsonObject ?: return ""
        f["stringValue"]?.jsonPrimitive?.content?.let { return it }
        val arr = f["arrayValue"]?.jsonObject?.get("values") as? JsonArray ?: return ""
        return arr.mapNotNull { (it as? JsonObject)?.get("stringValue")?.jsonPrimitive?.content }
            .joinToString("\n")
    }

    /** Epoch millis -> ISO-8601 UTC string (for timestampValue fields). */
    private fun isoFromMs(ms: Long): String {
        val days = floorDivLong(ms, 86400000L)
        var rem = ms - days * 86400000L
        val hh = rem / 3600000L; rem -= hh * 3600000L
        val mm = rem / 60000L; rem -= mm * 60000L
        val ss = rem / 1000L
        val z = days + 719468L
        val era = floorDivLong(z, 146097L)
        val doe = z - era * 146097L
        val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
        var y = yoe + era * 400L
        val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
        val mp = (5L * doy + 2L) / 153L
        val d = doy - (153L * mp + 2L) / 5L + 1L
        val m = if (mp < 10L) mp + 3L else mp - 9L
        if (m <= 2L) y += 1L
        fun p2(v: Long) = v.toString().padStart(2, '0')
        return "$y-${p2(m)}-${p2(d)}T${p2(hh)}:${p2(mm)}:${p2(ss)}.000Z"
    }

    private fun floorDivLong(a: Long, b: Long): Long {
        val q = a / b
        return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
    }

    private fun parseIsoMs(iso: String): Long {
        return try {
            val datePart = iso.substring(0, 10)
            val timePart = iso.substring(11, 19)
            val y = datePart.substring(0, 4).toInt()
            val mo = datePart.substring(5, 7).toInt()
            val d = datePart.substring(8, 10).toInt()
            val h = timePart.substring(0, 2).toInt()
            val mi = timePart.substring(3, 5).toInt()
            val s = timePart.substring(6, 8).toInt()
            val yy = if (mo <= 2) y - 1 else y
            val era = if (yy >= 0) yy / 400 else -((-yy + 399) / 400)
            val yoe = yy - era * 400
            val mp = (mo + 9) % 12
            val doy = (153 * mp + 2) / 5 + d - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            val days = era * 146097L + doe - 719468L
            ((days * 24 + h) * 60 + mi) * 60000L + s * 1000L
        } catch (e: Exception) { 0L }
    }

    fun jsonStr(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        sb.append("\"")
        return sb.toString()
    }

    suspend fun fetchProfile(): Profile? {
        val s = session ?: return null
        val token = validToken() ?: return null
        return try {
            val res = httpRequest("GET", "$FS_BASE/users/${s.uid}", token = token)
            val doc = json.parseToJsonElement(res).jsonObject
            val f = fieldsOf(doc) ?: return Profile()
            // Production docs carry premium/premiumUntil (the app's fields);
            // isPremium/premiumExpiry are legacy fallbacks only.
            val pFlag = if (f.containsKey("premium")) f.fBool("premium") else f.fBool("isPremium")
            val pUntil = if (f.containsKey("premiumUntil")) f.fTimestampMs("premiumUntil")
                         else f.fTimestampMs("premiumExpiry")
            val email = f.fStr("email").ifBlank { s.email }
            val now = nowMillis()
            val status = when {
                email.equals("ayansikligar98@gmail.com", ignoreCase = true) -> "OWNER"
                pFlag && pUntil > now -> "ACTIVE"
                pFlag && pUntil > 0L -> "EXPIRED"
                else -> "FREE"
            }
            Profile(
                name = f.fStr("name"),
                email = email,
                walletPaise = f.fLong("walletBalancePaise"),
                credits = f.fLong("credits"),
                isPremium = status == "ACTIVE" || status == "OWNER",
                premiumExpiryMs = pUntil,
                premiumStatus = status,
                plan = f.fStr("plan"),
                banned = f.fBool("banned"),
                walletFrozen = f.fBool("walletFrozen"),
                walletFreezeReason = f.fStr("walletFreezeReason"),
                spinUsedToday = f.fLong("spinUsedToday"),
                spinDate = f.fStr("spinDate"),
                giftSpins = f.fLong("giftSpins"),
                giftSpinsUsed = f.fLong("giftSpinsUsed"),
                notifSpins = f.fLong("notifRewardSpins"),
                notifSpinsUsed = f.fLong("notifSpinsUsed"),
                spinDebt = f.fLong("spinDebt"),
                checkinDate = f.fStr("checkinDate"),
                checkinStreak = f.fLong("checkinStreak"),
                referCode = f.fStr("referCode"),
                referralRedeemed = f.fBool("referralRedeemed")
            )
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateProfileName(name: String): Result<Unit> {
        val s = session ?: return Result.failure(Exception("Not logged in"))
        val token = validToken() ?: return Result.failure(Exception("Session expired"))
        return try {
            val body = "{\"fields\":{\"name\":{\"stringValue\":${jsonStr(name)}}}}"
            httpRequest("PATCH", "$FS_BASE/users/${s.uid}?updateMask.fieldPaths=name", body, token)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    /** Daily check-in write — same fields the Android app updates. */
    suspend fun dailyCheckIn(dateStr: String, streak: Long): Result<Unit> {
        val s = session ?: return Result.failure(Exception("Not logged in"))
        val token = validToken() ?: return Result.failure(Exception("Session expired"))
        return try {
            val body = "{\"fields\":{\"checkinDate\":{\"stringValue\":${jsonStr(dateStr)}},\"checkinStreak\":{\"integerValue\":\"$streak\"}}}"
            httpRequest(
                "PATCH",
                "$FS_BASE/users/${s.uid}?updateMask.fieldPaths=checkinDate&updateMask.fieldPaths=checkinStreak",
                body, token
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(Exception(friendlyError(e)))
        }
    }

    private suspend fun runQueryByUid(collectionId: String, uid: String, limit: Int): List<JsonObject> {
        val token = validToken() ?: return emptyList()
        val body = """{"structuredQuery":{"from":[{"collectionId":"$collectionId"}],"where":{"fieldFilter":{"field":{"fieldPath":"uid"},"op":"EQUAL","value":{"stringValue":${jsonStr(uid)}}}},"orderBy":[{"field":{"fieldPath":"createdAt"},"direction":"DESCENDING"}],"limit":$limit}}"""
        val res = httpRequest("POST", "$FS_BASE:runQuery", body, token)
        val arr = json.parseToJsonElement(res) as? JsonArray ?: return emptyList()
        return arr.mapNotNull { el -> (el as? JsonObject)?.get("document") as? JsonObject }
    }

    suspend fun fetchTasks(): List<TaskItem> {
        val token = validToken() ?: return emptyList()
        return try {
            val body = """{"structuredQuery":{"from":[{"collectionId":"tasks"}],"limit":50}}"""
            val res = httpRequest("POST", "$FS_BASE:runQuery", body, token)
            val arr = json.parseToJsonElement(res) as? JsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                val doc = (el as? JsonObject)?.get("document") as? JsonObject ?: return@mapNotNull null
                val f = fieldsOf(doc) ?: return@mapNotNull null
                val name = doc["name"]?.jsonPrimitive?.content ?: ""
                TaskItem(
                    id = name.substringAfterLast("/"),
                    title = f.fStr("title"),
                    description = f.fStr("description"),
                    rewardPaise = f.fLong("rewardPaise"),
                    link = f.fStr("link"),
                    conditions = f.fText("conditions"),
                    mode = f.fStr("mode"),
                    payDays = f.fLong("payDays"),
                    applyDays = f.fLong("applyDays")
                )
            }.filter { it.title.isNotBlank() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchHistory(): List<HistoryItem> {
        val s = session ?: return emptyList()
        val out = mutableListOf<HistoryItem>()
        try {
            for (d in runQueryByUid("withdrawRequests", s.uid, 10)) {
                val f = fieldsOf(d) ?: continue
                out += HistoryItem(
                    title = "Withdrawal",
                    subtitle = f.fStr("upiId"),
                    amountText = "-₹" + fmtPaise(f.fLong("amountPaise")),
                    positive = false,
                    status = f.fStr("status").ifBlank { "pending" },
                    createdAtMs = f.fTimestampMs("createdAt")
                )
            }
        } catch (e: Exception) { }
        try {
            for (d in runQueryByUid("bonusRedemptions", s.uid, 10)) {
                val f = fieldsOf(d) ?: continue
                out += HistoryItem(
                    title = "Bonus Code — " + f.fStr("code"),
                    subtitle = "Bonus redeemed",
                    amountText = "+₹" + fmtPaise(f.fLong("amountPaise")),
                    positive = true,
                    status = "",
                    createdAtMs = f.fTimestampMs("createdAt")
                )
            }
        } catch (e: Exception) { }
        try {
            // The Android app's real collection name (bonusRedeems).
            val seenBonus = out.filter { it.title.startsWith("Bonus Code") }
                .map { it.createdAtMs to it.amountText }.toSet()
            for (d in runQueryByUid("bonusRedeems", s.uid, 10)) {
                val f = fieldsOf(d) ?: continue
                val item = HistoryItem(
                    title = "Bonus Code — " + f.fStr("code"),
                    subtitle = "Bonus redeemed",
                    amountText = "+₹" + fmtPaise(f.fLong("amountPaise")),
                    positive = true,
                    status = "",
                    createdAtMs = f.fTimestampMs("createdAt")
                )
                if ((item.createdAtMs to item.amountText) !in seenBonus) out += item
            }
        } catch (e: Exception) { }
        try {
            for (d in runQueryByUid("spinWins", s.uid, 15)) {
                val f = fieldsOf(d) ?: continue
                out += HistoryItem(
                    title = "Spin & Earn",
                    subtitle = "Spin reward",
                    amountText = "+₹" + fmtPaise(f.fLong("prizePaise")),
                    positive = true,
                    status = f.fStr("status").ifBlank { "credited" },
                    createdAtMs = f.fTimestampMs("createdAt")
                )
            }
        } catch (e: Exception) { }
        try {
            for (d in runQueryByUid("walletAdjusts", s.uid, 10)) {
                val f = fieldsOf(d) ?: continue
                val amt = f.fLong("amountPaise")
                out += HistoryItem(
                    title = "Wallet Adjustment",
                    subtitle = f.fStr("reason").ifBlank { f.fStr("type") },
                    amountText = (if (amt >= 0) "+₹" else "-₹") + fmtPaise(kotlin.math.abs(amt)),
                    positive = amt >= 0,
                    status = "",
                    createdAtMs = f.fTimestampMs("createdAt")
                )
            }
        } catch (e: Exception) { }
        try {
            for (d in runQueryByUid("payRequests", s.uid, 10)) {
                val f = fieldsOf(d) ?: continue
                val isCredits = f.fStr("type") == "credits"
                val title = if (isCredits) "${f.fLong("packCredits")} Credits Pack"
                else "Premium — " + f.fStr("plan").ifBlank { "Plan" }
                val paid = f.fLong("amountPaid")
                out += HistoryItem(
                    title = title,
                    subtitle = "Payment request",
                    amountText = "₹" + if (paid > 0) paid.toString() else fmtPaise(f.fLong("amountPaise")),
                    positive = null,
                    status = f.fStr("status").ifBlank { "pending" },
                    createdAtMs = f.fTimestampMs("createdAt")
                )
            }
        } catch (e: Exception) { }
        try {
            for (d in runQueryByUid("taskClaims", s.uid, 10)) {
                val f = fieldsOf(d) ?: continue
                val st = f.fStr("status")
                out += HistoryItem(
                    title = f.fStr("taskTitle").ifBlank { "Task Reward" },
                    subtitle = "Task reward",
                    amountText = "+₹" + fmtPaise(f.fLong("rewardPaise")),
                    positive = if (st == "approved") true else null,
                    status = st,
                    createdAtMs = f.fTimestampMs("createdAt")
                )
            }
        } catch (e: Exception) { }
        return out.sortedByDescending { it.createdAtMs }.take(15)
    }

    fun fmtPaise(p: Long): String {
        val r = p / 100
        val rem = p % 100
        return if (rem == 0L) r.toString() else "$r.${rem.toString().padStart(2, '0')}"
    }
}
