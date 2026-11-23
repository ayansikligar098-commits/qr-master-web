package qrmaster.web.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Latest Android app release, read at runtime from the same public
 * version feed the Android app itself polls for updates
 * (version-native.json on GitHub Pages). Nothing is hardcoded here:
 * when a new production APK is published to the feed, this website
 * automatically offers that exact build.
 */
data class AppReleaseInfo(
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String
) {
    /** File name for the browser download (last path segment of the APK URL). */
    val fileName: String
        get() = apkUrl.substringAfterLast('/').ifBlank { "QRMaster.apk" }
}

const val RELEASE_FEED_URL = "https://ayansikligar098-commits.github.io/version-native.json"

/** Fallback when the feed cannot be loaded: the public releases list. */
const val RELEASES_PAGE_URL =
    "https://github.com/ayansikligar098-commits/ayansikligar098-commits.github.io/releases"

/** Returns the latest release info, or null if the feed is unreachable/invalid. */
suspend fun fetchAppRelease(): AppReleaseInfo? {
    return try {
        val text = httpRequest("GET", RELEASE_FEED_URL)
        val obj = Json.parseToJsonElement(text).jsonObject
        val apkUrl = obj["apkUrl"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: return null
        val name = obj["versionName"]?.jsonPrimitive?.content.orEmpty()
        val code = obj["versionCode"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
        AppReleaseInfo(versionName = name, versionCode = code, apkUrl = apkUrl)
    } catch (e: Exception) {
        null
    }
}
