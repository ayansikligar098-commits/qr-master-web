package qrmaster.web.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.browser.localStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.dom.get
import org.w3c.dom.set

@Serializable
data class UserSession(
    val uid: String,
    val email: String,
    val idToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long
)

data class UserProfile(
    val name: String?,
    val email: String?,
    val walletPaise: Long?,
    val isPremium: Boolean,
    val premiumExpiryMillis: Long?
)

private val jsonFmt = Json { ignoreUnknownKeys = true }

object SessionStore {
    private const val KEY = "qrmaster_session"
    fun save(s: UserSession) {
        try { localStorage[KEY] = jsonFmt.encodeToString(UserSession.serializer(), s) } catch (e: Exception) { }
    }
    fun load(): UserSession? {
        return try {
            val raw = localStorage[KEY] ?: return null
            jsonFmt.decodeFromString(UserSession.serializer(), raw)
        } catch (e: Exception) { null }
    }
    fun clear() {
        try { localStorage.removeItem(KEY) } catch (e: Exception) { }
    }
}

object FirebaseApi {
    private const val AUTH_BASE = "https://identitytoolkit.googleapis.com/v1"
    private const val TOKEN_URL = "https://securetoken.googleapis.com/v1/token"
    private const val FIRESTORE_BASE = "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents"

    private val client = HttpClient {
        install(ContentNegotiation) { json(jsonFmt) }
    }

    private fun friendlyError(code: String): String = when {
        code.contains("INVALID_LOGIN_CREDENTIALS") || code.contains("INVALID_PASSWORD") || code.contains("EMAIL_NOT_FOUND") ->
            "Email ya password galat hai. Dobara try karo."
        code.contains("EMAIL_EXISTS") -> "Is email se account pehle se hai. Login karo."
        code.contains("WEAK_PASSWORD") -> "Password kam se kam 6 characters ka rakho."
        code.contains("INVALID_EMAIL") -> "Email address sahi format me nahi hai."
        code.contains("USER_DISABLED") -> "Ye account band hai. Support se baat karo."
        code.contains("TOO_MANY_ATTEMPTS") -> "Bahut zyada koshish ho gayi. Thodi der baad try karo."
        code.contains("OPERATION_NOT_ALLOWED") -> "Email/password login abhi enabled nahi hai."
        else -> "Kuch galat ho gaya ($code). Dobara try karo."
    }

    private fun errorCode(text: String): String {
        return try {
            val obj = jsonFmt.parseToJsonElement(text).jsonObject
            obj["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull ?: "UNKNOWN"
        } catch (e: Exception) { "UNKNOWN" }
    }

    suspend fun signIn(email: String, password: String): Result<UserSession> = authCall("signInWithEmailAndPassword", email, password)

    suspend fun signUp(email: String, password: String): Result<UserSession> = authCall("signUp", email, password)

    private suspend fun authCall(path: String, email: String, password: String): Result<UserSession> {
        return try {
            val resp = client.post("$AUTH_BASE/accounts:$path?key=${FirebaseConfig.API_KEY}") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"${escapeJson(email)}","password":"${escapeJson(password)}","returnSecureToken":true}""")
            }
            val text = resp.bodyAsText()
            if (resp.status.value !in 200..299) {
                return Result.failure(Exception(friendlyError(errorCode(text))))
            }
            val obj = jsonFmt.parseToJsonElement(text).jsonObject
            val expiresIn = obj["expiresIn"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600
            val session = UserSession(
                uid = obj["localId"]?.jsonPrimitive?.contentOrNull ?: "",
                email = obj["email"]?.jsonPrimitive?.contentOrNull ?: email,
                idToken = obj["idToken"]?.jsonPrimitive?.contentOrNull ?: "",
                refreshToken = obj["refreshToken"]?.jsonPrimitive?.contentOrNull ?: "",
                expiresAtMillis = currentTimeMillis() + expiresIn * 1000
            )
            SessionStore.save(session)
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(Exception("Network problem — internet check karke dobara try karo."))
        }
    }

    suspend fun refreshSession(old: UserSession): UserSession? {
        return try {
            val resp = client.post("$TOKEN_URL?key=${FirebaseConfig.API_KEY}") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody("grant_type=refresh_token&refresh_token=${old.refreshToken}")
            }
            val text = resp.bodyAsText()
            if (resp.status.value !in 200..299) return null
            val obj = jsonFmt.parseToJsonElement(text).jsonObject
            val expiresIn = obj["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600
            val s = old.copy(
                idToken = obj["id_token"]?.jsonPrimitive?.contentOrNull ?: old.idToken,
                refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNull ?: old.refreshToken,
                expiresAtMillis = currentTimeMillis() + expiresIn * 1000
            )
            SessionStore.save(s)
            s
        } catch (e: Exception) { null }
    }

    suspend fun fetchProfile(session: UserSession): UserProfile? {
        return try {
            val resp = client.get("$FIRESTORE_BASE/users/${session.uid}") {
                header("Authorization", "Bearer ${session.idToken}")
            }
            val text = resp.bodyAsText()
            if (resp.status.value !in 200..299) return null
            val fields = jsonFmt.parseToJsonElement(text).jsonObject["fields"]?.jsonObject ?: return null
            fun str(name: String): String? =
                fields[name]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull
            fun lng(name: String): Long? {
                val f = fields[name]?.jsonObject ?: return null
                return f["integerValue"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                    ?: f["doubleValue"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toLong()
                    ?: f["stringValue"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            }
            fun bool(name: String): Boolean =
                fields[name]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.contentOrNull == "true"
            UserProfile(
                name = str("name") ?: str("displayName"),
                email = str("email") ?: session.email,
                walletPaise = lng("walletBalancePaise"),
                isPremium = bool("isPremium") || bool("premium"),
                premiumExpiryMillis = lng("premiumExpiryMillis") ?: lng("premiumExpiry")
            )
        } catch (e: Exception) { null }
    }

    fun signOut() {
        SessionStore.clear()
    }

    private fun escapeJson(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    private fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()
}
