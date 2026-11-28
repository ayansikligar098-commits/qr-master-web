package qrmaster.web.data

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.w3c.xhr.XMLHttpRequest
import kotlin.coroutines.resume

/**
 * Kitnook File Share — client for the standalone share Worker
 * (kitnook-share-api). File BYTES never pass through this layer or the
 * Worker: uploads run in the JS engine (FileShareBridge) straight to R2
 * via presigned URLs; downloads are presigned GETs. This file only moves
 * small JSON: share info, download tickets, My Shares, deletes.
 */
object FileShareApi {
    const val API_BASE = "https://kitnook-share-api.ayansikligar98.workers.dev"

    const val FREE_MAX_BYTES: Long = 5L * 1024 * 1024 * 1024
    const val PREMIUM_MAX_BYTES: Long = 10L * 1024 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true }

    data class FsResponse(val status: Int, val body: String) {
        fun jsonObj(): kotlinx.serialization.json.JsonObject? = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) { null }

        fun errorCode(): String = jsonObj()?.str("error") ?: ""
    }

    private fun kotlinx.serialization.json.JsonObject.str(key: String): String =
        this[key]?.jsonPrimitive?.content ?: ""

    private fun kotlinx.serialization.json.JsonObject.lng(key: String): Long =
        this[key]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

    private fun kotlinx.serialization.json.JsonObject.bool(key: String): Boolean =
        this[key]?.jsonPrimitive?.content == "true"

    /** XHR with explicit status (the shared httpRequest throws on non-2xx;
     *  the share API's states ARE status codes — 410 expired, 409 not
     *  ready — so the screens need them, not exceptions). */
    suspend fun request(
        method: String,
        path: String,
        body: String? = null,
        token: String? = null,
        deleteToken: String? = null
    ): FsResponse = suspendCancellableCoroutine { cont ->
        val xhr = XMLHttpRequest()
        xhr.open(method, API_BASE + path, async = true)
        xhr.timeout = 30000
        if (body != null) xhr.setRequestHeader("Content-Type", "application/json")
        if (token != null) xhr.setRequestHeader("Authorization", "Bearer $token")
        if (deleteToken != null) xhr.setRequestHeader("X-Delete-Token", deleteToken)
        xhr.addEventListener("load", {
            if (cont.isActive) cont.resume(FsResponse(xhr.status.toInt(), xhr.responseText))
        })
        xhr.addEventListener("error", {
            if (cont.isActive) cont.resume(FsResponse(0, ""))
        })
        xhr.addEventListener("timeout", {
            if (cont.isActive) cont.resume(FsResponse(-1, ""))
        })
        cont.invokeOnCancellation { try { xhr.abort() } catch (e: Exception) { } }
        if (body != null) xhr.send(body) else xhr.send()
    }

    // ---------------- public download page ----------------

    data class ShareInfo(
        val state: String,           // ready | expired | not_found | not_ready | error
        val fileName: String = "",
        val size: Long = 0,
        val expiresAt: Long = 0,
        val daysLeft: Int = 0,
        val hasPassword: Boolean = false,
        val downloads: Long = 0
    )

    suspend fun fetchInfo(id: String): ShareInfo {
        val r = request("GET", "/api/shares/$id")
        val o = r.jsonObj()
        return when {
            r.status == 200 && o != null -> ShareInfo(
                state = "ready",
                fileName = o.str("fileName"),
                size = o.lng("size"),
                expiresAt = o.lng("expiresAt"),
                daysLeft = o.lng("daysLeft").toInt(),
                hasPassword = o.bool("hasPassword"),
                downloads = o.lng("downloads")
            )
            r.status == 410 && o != null -> ShareInfo(
                state = "expired",
                fileName = o.str("fileName"),
                size = o.lng("size"),
                expiresAt = o.lng("expiresAt")
            )
            r.status == 404 -> ShareInfo(state = "not_found")
            r.status == 409 -> ShareInfo(state = "not_ready")
            else -> ShareInfo(state = "error")
        }
    }

    /** Returns the presigned download URL, or throws FsException with the
     *  Worker's error code (wrong_password / expired / not_found ...). */
    class FsException(val code: String) : Exception(code)

    suspend fun requestDownload(id: String, password: String?): String {
        val body = if (password.isNullOrEmpty()) "{}"
        else """{"password":${FirebaseApi.jsonStr(password)}}"""
        val r = request("POST", "/api/shares/$id/download", body)
        val o = r.jsonObj()
        if (r.status == 200 && o != null) {
            val url = o.str("url")
            if (url.isNotBlank()) return url
        }
        throw FsException(r.errorCode().ifBlank { "http_${r.status}" })
    }

    // ---------------- My Shares ----------------

    data class MyShare(
        val id: String,
        val fileName: String,
        val size: Long,
        val createdAt: Long,
        val expiresAt: Long,
        val daysLeft: Int,
        val state: String,           // active | expired | uploading
        val downloads: Long,
        val hasPassword: Boolean,
        val tier: String
    )

    suspend fun fetchMine(): List<MyShare> {
        val token = FirebaseApi.validToken() ?: return emptyList()
        val r = request("GET", "/api/shares/mine", token = token)
        val o = r.jsonObj() ?: return emptyList()
        if (r.status != 200) return emptyList()
        val arr = try { o["shares"]?.jsonArray } catch (e: Exception) { null } ?: return emptyList()
        return arr.mapNotNull { el ->
            val s = try { el.jsonObject } catch (e: Exception) { return@mapNotNull null }
            MyShare(
                id = s.str("id"),
                fileName = s.str("fileName"),
                size = s.lng("size"),
                createdAt = s.lng("createdAt"),
                expiresAt = s.lng("expiresAt"),
                daysLeft = s.lng("daysLeft").toInt(),
                state = s.str("state"),
                downloads = s.lng("downloads"),
                hasPassword = s.bool("hasPassword"),
                tier = s.str("tier")
            )
        }.filter { it.id.isNotBlank() }
    }

    /**
     * Delete a share. Two proofs are accepted by the Worker: the delete
     * token (kept in this browser at upload time) or the owner's ID token
     * (My Shares). Either may be null; at least one must be present.
     */
    suspend fun deleteShare(id: String, deleteToken: String?): Boolean {
        val token = FirebaseApi.validToken()
        val r = request("DELETE", "/api/shares/$id", token = token, deleteToken = deleteToken)
        return r.status == 200
    }
}

/** Human byte sizes for the File Share UI (B / KB / MB / GB). */
fun fmtBytes(bytes: Long): String {
    if (bytes < 0) return "—"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024.0 && i < units.size - 1) { v /= 1024.0; i++ }
    return when {
        i == 0 -> "${bytes} B"
        v >= 100 -> "${v.toInt()} ${units[i]}"
        v >= 10 -> "${(v * 10).toInt() / 10.0} ${units[i]}"
        else -> "${(v * 100).toInt() / 100.0} ${units[i]}"
    }
}
