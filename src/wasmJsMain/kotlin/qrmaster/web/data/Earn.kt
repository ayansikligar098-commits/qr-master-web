package qrmaster.web.data

import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import qrmaster.web.calc.Sha256
import kotlin.random.Random

// ── Earn engine — a faithful port of the Android app's earn/EarnApi.kt +
// earn/EarnLogic.kt + wallet/premium/referral/support flows.
//
// Every money write follows the app's exact shapes:
//   1) read the FRESH user doc, 2) decide with the ported logic,
//   3) ONE atomic :commit with deterministic ids + exists preconditions,
//   4) the admin-fund deduction rides in the SAME commit.
// Fund missing/short -> fail closed (PAUSED). Nothing is invented locally.
//
// Web divergences (documented, money-safe):
//  - The notification-toggle reconcile (debt creation / toggle ban) is NOT
//    run: it is driven by the Android app's notification-permission state,
//    which a website cannot observe. The start-of-day spin-debt APPLICATION
//    (pure field logic) IS mirrored exactly.
//  - Notification extra spins are therefore displayed but not spendable on
//    the web (extraSpinsAvailable with notifEnabled = false).
//  - deviceIdHash is a persistent per-browser hash (localStorage id,
//    SHA-256, first 40 hex chars — the app's format), source "web".

const val PAUSED_MSG = "Rewards are paused right now. They will be back soon."
const val FROZEN_MSG = "❄️ Your wallet is frozen"
const val BAN_MSG = "Earning is paused for today because notifications were turned off and on repeatedly. It will be available again tomorrow."
const val CHECKIN_MSG = "Please check in first — then today's spins will unlock."
const val APPLY_FIRST_MSG = "Please apply first — then this task will start for you."
const val APPLY_EXPIRED_MSG = "Time expired — the days are over, so no reward will be given. Apply again and try once more."

private const val FS_DOCS =
    "https://firestore.googleapis.com/v1/projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents"
private const val FS_COMMIT = "$FS_DOCS:commit"
private const val FS_RUNQUERY = "$FS_DOCS:runQuery"

private val earnJson = Json { ignoreUnknownKeys = true }

// ───────────────────────── Firestore value codec ─────────────────────────

fun fsStr(v: String): JsonObject = buildJsonObject { put("stringValue", JsonPrimitive(v)) }
fun fsInt(v: Long): JsonObject = buildJsonObject { put("integerValue", JsonPrimitive(v.toString())) }
fun fsBool(v: Boolean): JsonObject = buildJsonObject { put("booleanValue", JsonPrimitive(v)) }
fun fsTs(ms: Long): JsonObject = buildJsonObject { put("timestampValue", JsonPrimitive(isoFromMsE(ms))) }
fun fsMap(fields: Map<String, JsonObject>): JsonObject = buildJsonObject {
    put("mapValue", buildJsonObject { put("fields", JsonObject(fields)) })
}

fun isoFromMsE(ms: Long): String {
    val days = floorDivL(ms, 86400000L)
    var rem = ms - days * 86400000L
    val hh = rem / 3600000L; rem -= hh * 3600000L
    val mm = rem / 60000L; rem -= mm * 60000L
    val ss = rem / 1000L
    val mss = rem - ss * 1000L
    val (y, mo, d) = civilFromDaysE(days)
    fun p2(v: Long) = v.toString().padStart(2, '0')
    return "$y-${p2(mo)}-${p2(d)}T${p2(hh)}:${p2(mm)}:${p2(ss)}.${mss.toString().padStart(3, '0')}Z"
}

fun civilFromDaysE(zIn: Long): Triple<Long, Long, Long> {
    val z = zIn + 719468L
    val era = floorDivL(z, 146097L)
    val doe = z - era * 146097L
    val yoe = (doe - doe / 1460L + doe / 36524L - doe / 146096L) / 365L
    var y = yoe + era * 400L
    val doy = doe - (365L * yoe + yoe / 4L - yoe / 100L)
    val mp = (5L * doy + 2L) / 153L
    val d = doy - (153L * mp + 2L) / 5L + 1L
    val m = if (mp < 10L) mp + 3L else mp - 9L
    if (m <= 2L) y += 1L
    return Triple(y, m, d)
}

fun floorDivL(a: Long, b: Long): Long {
    val q = a / b
    return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
}

fun parseIsoMsE(iso: String): Long {
    return try {
        val datePart = iso.substring(0, 10)
        val timePart = iso.substring(11, 19)
        val y = datePart.substring(0, 4).toInt()
        val mo = datePart.substring(5, 7).toInt()
        val d = datePart.substring(8, 10).toInt()
        val h = timePart.substring(0, 2).toInt()
        val mi = timePart.substring(3, 5).toInt()
        val s = timePart.substring(6, 8).toInt()
        val fracMs = if (iso.length > 20 && iso[19] == '.') {
            iso.substring(20).takeWhile { it.isDigit() }.padEnd(3, '0').take(3).toIntOrNull() ?: 0
        } else 0
        val yy = if (mo <= 2) y - 1 else y
        val era = if (yy >= 0) yy / 400 else -((-yy + 399) / 400)
        val yoe = yy - era * 400
        val mp = (mo + 9) % 12
        val doy = (153 * mp + 2) / 5 + d - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        val days = era * 146097L + doe - 719468L
        ((days * 24 + h) * 60 + mi) * 60000L + s * 1000L + fracMs
    } catch (e: Exception) { 0L }
}

/** IST date string (yyyy-MM-dd) for an epoch-ms instant. */
fun istDateOf(ms: Long): String {
    val ist = ms + (5 * 60 + 30) * 60000L
    val days = floorDivL(ist, 86400000L)
    val (y, m, d) = civilFromDaysE(days)
    return y.toString().padStart(4, '0') + "-" + m.toString().padStart(2, '0') + "-" + d.toString().padStart(2, '0')
}

fun todayIstE(): String = istDateOf(nowMillis())
fun yesterdayIstE(): String = istDateOf(nowMillis() - 86400000L)

// ───────────────────────── Field readers (typed-value JSON) ────────────────

fun JsonObject.eStr(key: String): String =
    this[key]?.jsonObject?.get("stringValue")?.jsonPrimitive?.content ?: ""

fun JsonObject.eLong(key: String): Long {
    val f = this[key]?.jsonObject ?: return 0L
    return f["integerValue"]?.jsonPrimitive?.content?.toLongOrNull()
        ?: f["doubleValue"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: 0L
}

fun JsonObject.eBool(key: String): Boolean =
    this[key]?.jsonObject?.get("booleanValue")?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false

fun JsonObject.eHas(key: String): Boolean = this.containsKey(key)

fun JsonObject.eTsMs(key: String): Long {
    val f = this[key]?.jsonObject ?: return 0L
    f["timestampValue"]?.jsonPrimitive?.content?.let { return parseIsoMsE(it) }
    f["integerValue"]?.jsonPrimitive?.content?.toLongOrNull()?.let { return it }
    f["stringValue"]?.jsonPrimitive?.content?.let { s ->
        val t = s.trim()
        t.toLongOrNull()?.let { return it }
        return parseIsoMsE(t)
    }
    return 0L
}

fun JsonObject.eDouble(key: String): Double? {
    val f = this[key]?.jsonObject ?: return null
    return f["doubleValue"]?.jsonPrimitive?.content?.toDoubleOrNull()
        ?: f["integerValue"]?.jsonPrimitive?.content?.toDoubleOrNull()
        ?: f["stringValue"]?.jsonPrimitive?.content?.trim()?.toDoubleOrNull()
}

fun JsonObject.eMapKeys(key: String): Set<String> {
    val mv = this[key]?.jsonObject?.get("mapValue")?.jsonObject ?: return emptySet()
    return mv["fields"]?.jsonObject?.keys ?: emptySet()
}

/** Text field that may be a plain string or an array of strings (task conditions). */
fun JsonObject.eText(key: String): String {
    val f = this[key]?.jsonObject ?: return ""
    f["stringValue"]?.jsonPrimitive?.content?.let { return it }
    val arr = f["arrayValue"]?.jsonObject?.get("values") as? JsonArray ?: return ""
    return arr.mapNotNull { (it as? JsonObject)?.get("stringValue")?.jsonPrimitive?.content }
        .joinToString("\n")
}

// ───────────────────────── HTTP primitives ─────────────────────────────────

sealed interface DocFetch {
    data class Found(val fields: JsonObject) : DocFetch
    data object Missing : DocFetch
    data class Fail(val message: String) : DocFetch
}

suspend fun fsGetDoc(path: String, token: String?): DocFetch {
    return try {
        val res = httpRequest("GET", "$FS_DOCS/$path", token = token)
        val doc = earnJson.parseToJsonElement(res).jsonObject
        val fields = doc["fields"]?.jsonObject ?: JsonObject(emptyMap())
        DocFetch.Found(fields)
    } catch (e: HttpException) {
        if (e.status == 404) DocFetch.Missing else DocFetch.Fail("HTTP ${e.status}")
    } catch (e: Exception) {
        DocFetch.Fail(e.message ?: "network")
    }
}

/** One write in a commit batch (Firestore REST commit format). */
fun updateWriteJson(
    path: String,
    fields: Map<String, JsonObject>,
    mask: List<String>?,
    exists: Boolean?
): JsonObject = buildJsonObject {
    put("update", buildJsonObject {
        put("name", JsonPrimitive("projects/${FirebaseConfig.PROJECT_ID}/databases/(default)/documents/$path"))
        put("fields", JsonObject(fields))
    })
    if (!mask.isNullOrEmpty()) {
        put("updateMask", buildJsonObject {
            put("fieldPaths", buildJsonArray { mask.forEach { add(JsonPrimitive(it)) } })
        })
    }
    if (exists != null) {
        put("currentDocument", buildJsonObject { put("exists", JsonPrimitive(exists)) })
    }
}

/** Atomic :commit. Returns null on success, "HTTP <code> <status>" on failure. */
suspend fun fsCommit(writes: List<JsonObject>, token: String): String? {
    return try {
        val body = buildJsonObject {
            put("writes", buildJsonArray { writes.forEach { add(it) } })
        }
        httpRequest("POST", FS_COMMIT, body.toString(), token)
        null
    } catch (e: HttpException) {
        val status = try {
            earnJson.parseToJsonElement(e.body).jsonObject["error"]?.jsonObject?.get("status")
                ?.jsonPrimitive?.content ?: ""
        } catch (x: Exception) { "" }
        "HTTP ${e.status}${if (status.isEmpty()) "" else " $status"} on commit"
    } catch (e: Exception) {
        e.message ?: "network error"
    }
}

/** Masked PATCH (creates the doc when missing, like the app's patchDoc). */
suspend fun fsPatchDoc(path: String, fields: Map<String, JsonObject>, mask: List<String>, token: String) {
    val q = mask.joinToString("&") { "updateMask.fieldPaths=$it" }
    val body = buildJsonObject { put("fields", JsonObject(fields)) }
    httpRequest("PATCH", "$FS_DOCS/$path?$q", body.toString(), token)
}

/** Creates a doc with a server auto-id; returns the resource name. */
suspend fun fsCreateDoc(collection: String, fields: Map<String, JsonObject>, token: String): String {
    val body = buildJsonObject { put("fields", JsonObject(fields)) }
    val res = httpRequest("POST", "$FS_DOCS/$collection", body.toString(), token)
    return earnJson.parseToJsonElement(res).jsonObject["name"]?.jsonPrimitive?.content ?: ""
}

/** Lists a collection (pageSize=100): (id, fields) pairs; null on failure. */
suspend fun fsListCollection(path: String, token: String?): List<Pair<String, JsonObject>>? {
    return try {
        val res = httpRequest("GET", "$FS_DOCS/$path?pageSize=100", token = token)
        val docs = earnJson.parseToJsonElement(res).jsonObject["documents"] as? JsonArray
            ?: return emptyList()
        docs.mapNotNull { el ->
            val d = (el as? JsonObject) ?: return@mapNotNull null
            val name = d["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            name.substringAfterLast("/") to (d["fields"]?.jsonObject ?: JsonObject(emptyMap()))
        }
    } catch (e: HttpException) {
        if (e.status == 404) emptyList() else null
    } catch (e: Exception) {
        null
    }
}

/**
 * field == value query — the app's strategy: ordered (createdAt DESC +
 * limit) first; if Firestore refuses (missing index), equality-only with
 * client-side sort + trim.
 */
suspend fun fsQueryEq(
    collection: String, field: String, value: String, limit: Int, token: String
): List<Pair<String, JsonObject>>? {
    suspend fun run(ordered: Boolean): List<Pair<String, JsonObject>>? {
        return try {
            val q = buildJsonObject {
                put("structuredQuery", buildJsonObject {
                    put("from", buildJsonArray {
                        add(buildJsonObject { put("collectionId", JsonPrimitive(collection)) })
                    })
                    put("where", buildJsonObject {
                        put("fieldFilter", buildJsonObject {
                            put("field", buildJsonObject { put("fieldPath", JsonPrimitive(field)) })
                            put("op", JsonPrimitive("EQUAL"))
                            put("value", fsStr(value))
                        })
                    })
                    if (ordered) {
                        put("orderBy", buildJsonArray {
                            add(buildJsonObject {
                                put("field", buildJsonObject { put("fieldPath", JsonPrimitive("createdAt")) })
                                put("direction", JsonPrimitive("DESCENDING"))
                            })
                        })
                        put("limit", JsonPrimitive(limit))
                    }
                })
            }
            val res = httpRequest("POST", FS_RUNQUERY, q.toString(), token)
            val arr = earnJson.parseToJsonElement(res) as? JsonArray ?: return emptyList()
            arr.mapNotNull { el ->
                val doc = (el as? JsonObject)?.get("document") as? JsonObject ?: return@mapNotNull null
                val name = doc["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                name.substringAfterLast("/") to (doc["fields"]?.jsonObject ?: JsonObject(emptyMap()))
            }
        } catch (e: Exception) {
            null
        }
    }
    val ordered = run(true)
    if (ordered != null) return ordered
    val plain = run(false) ?: return null
    return plain.sortedByDescending { it.second.eTsMs("createdAt") }.take(limit)
}

/** Random UUID v4 (for commit doc ids, same role as the app's UUID.randomUUID()). */
fun randomUuid(): String {
    val hex = "0123456789abcdef"
    fun r(n: Int): String = buildString { repeat(n) { append(hex[Random.nextInt(16)]) } }
    return r(8) + "-" + r(4) + "-4" + r(3) + "-" + "89ab"[Random.nextInt(4)] + r(3) + "-" + r(12)
}

/** Persistent per-browser device hash — the app's format (SHA-256, 40 hex). */
fun webDeviceHash(): String {
    return try {
        val ls = window.localStorage
        var id = ls.getItem("qm_device_id")
        if (id.isNullOrBlank()) {
            id = randomUuid()
            ls.setItem("qm_device_id", id)
        }
        Sha256.hex40("$id|qrmaster.web")
    } catch (e: Exception) {
        ""
    }
}

// ───────────────────────── Ported pure logic (EarnLogic) ───────────────────

object SpinLogic {
    data class Config(
        val dailyFree: Int = 3,
        val dailyPremium: Int = 10,
        val minWithdrawPaise: Long = 1000L,
        val prize1: Long = 100L,
        val prize2: Long = 200L,
        val a2: Int = 20, val a1: Int = 49,
        val b2: Int = 0, val b1: Int = 15,
        val c2: Int = 0, val c1: Int = 3,
        val a5Pct: Double = 1.0,
        val jackpotPct: Double = 0.90,
        val win10Pct: Double = 0.05,
        val win15Pct: Double = 0.03,
        val win20Pct: Double = 0.02,
        val notifRewardSpins: Int = 3,
        val notifBanLimit: Int = 6,
        val adRewardPaise: Long = 5L,
        val adDailyCap: Int = 5
    ) {
        companion object {
            fun fromSettings(raw: Map<String, Double>): Config {
                fun intOf(key: String, def: Int, min: Int, max: Int): Int {
                    val v = raw[key] ?: return def
                    val i = v.toInt()
                    return if (i in min..max) i else def
                }
                fun longOf(key: String, def: Long, min: Long, max: Long): Long {
                    val v = raw[key] ?: return def
                    val l = v.toLong()
                    return if (l in min..max) l else def
                }
                fun pctOf(key: String, def: Double): Double {
                    val v = raw[key] ?: return def
                    return if (v >= 0.0 && v <= 100.0) v else def
                }
                return Config(
                    dailyFree = intOf("spinDailyFree", 3, 0, 50),
                    dailyPremium = intOf("spinDailyPremium", 10, 0, 50),
                    minWithdrawPaise = longOf("spinMinWithdrawPaise", 1000L, 100, 1000000),
                    prize1 = longOf("spinPrize1", 100L, 0, 100000),
                    prize2 = longOf("spinPrize2", 200L, 0, 100000),
                    a2 = intOf("spinA2", 20, 0, 100),
                    a1 = intOf("spinA1", 49, 0, 100),
                    b2 = intOf("spinB2", 0, 0, 100),
                    b1 = intOf("spinB1", 15, 0, 100),
                    c2 = intOf("spinC2", 0, 0, 100),
                    c1 = intOf("spinC1", 3, 0, 100),
                    a5Pct = pctOf("spinA5Pct", 1.0),
                    jackpotPct = pctOf("spinJackpotPct", 0.90),
                    win10Pct = pctOf("spinWin10Pct", 0.05),
                    win15Pct = pctOf("spinWin15Pct", 0.03),
                    win20Pct = pctOf("spinWin20Pct", 0.02),
                    notifRewardSpins = intOf("notifRewardSpins", 3, 0, 50),
                    notifBanLimit = intOf("notifToggleBanLimit", 6, 0, 100),
                    adRewardPaise = longOf("adRewardPaise", 5L, 0, 100000),
                    adDailyCap = intOf("adDailyCap", 5, 0, 50)
                )
            }
        }
    }

    const val PRIZE5 = 500L; const val PRIZE10 = 1000L; const val PRIZE15 = 1500L; const val PRIZE20 = 2000L
    const val BAND_A = 0; const val BAND_B = 1; const val BAND_C = 2; const val BAND_D = 3

    fun isEarnBanned(earnBanDate: String?, todayIst: String?): Boolean =
        earnBanDate != null && todayIst != null && earnBanDate == todayIst

    fun extraSpinsAvailable(rewardSpins: Int, notifSpinsUsed: Int, notifEnabled: Boolean): Int {
        if (!notifEnabled) return 0
        var left = rewardSpins - notifSpinsUsed
        if (left < 0) left = 0
        if (left > rewardSpins) left = rewardSpins
        return left
    }

    fun giftSpinsAvailable(giftSpins: Int, giftSpinsUsed: Int): Int {
        var left = giftSpins - giftSpinsUsed
        if (left < 0) left = 0
        if (left > giftSpins) left = giftSpins
        return left
    }

    fun debtAppliedToday(spinDebt: Int, baseUsed: Int, dailyLimit: Int): Int {
        var room = dailyLimit - baseUsed
        if (room < 0) room = 0
        return minOf(maxOf(0, spinDebt), room)
    }

    fun bandFor(balancePaise: Long): Int = when {
        balancePaise < 500 -> BAND_A
        balancePaise < 800 -> BAND_B
        balancePaise < 900 -> BAND_C
        else -> BAND_D
    }

    private fun pct100(pct: Int): Int = pct * 100
    private fun pct100(pct: Double): Int = (pct * 100.0 + 0.5).toInt() // roundToInt for positive values

    fun decidePrize(cfg: Config, balancePaise: Long, firstSpin: Boolean, rollIn: Int): Long {
        if (balancePaise >= cfg.minWithdrawPaise) return 0
        var roll = rollIn
        if (roll < 0) roll = 0
        if (roll > 9999) roll = 9999
        val band = bandFor(balancePaise)
        var win = 0L
        var directBig = false
        if (band == BAND_D) {
            val tJ = pct100(cfg.jackpotPct)
            val t10 = tJ + pct100(cfg.win10Pct)
            val t15 = t10 + pct100(cfg.win15Pct)
            val t20 = t15 + pct100(cfg.win20Pct)
            when {
                roll < tJ -> win = cfg.prize1
                roll < t10 -> { win = PRIZE10; directBig = true }
                roll < t15 -> { win = PRIZE15; directBig = true }
                roll < t20 -> { win = PRIZE20; directBig = true }
            }
        } else {
            val p2: Int; val p1: Int; val p5: Double
            when (band) {
                BAND_A -> { p2 = cfg.a2; p1 = cfg.a1; p5 = cfg.a5Pct }
                BAND_B -> { p2 = cfg.b2; p1 = cfg.b1; p5 = 0.0 }
                else -> { p2 = cfg.c2; p1 = cfg.c1; p5 = 0.0 }
            }
            val t2 = pct100(p2)
            val t1 = t2 + pct100(p1)
            val t5 = t1 + pct100(p5)
            when {
                roll < t2 -> win = cfg.prize2
                roll < t1 -> win = cfg.prize1
                roll < t5 -> win = PRIZE5
            }
        }
        if (firstSpin) {
            if (win == 0L) win = cfg.prize1
            else if (win == PRIZE5) win = cfg.prize2
        }
        if (!directBig) {
            val room = cfg.minWithdrawPaise - balancePaise
            if (win > room) win = room
        }
        if (win < 0) win = 0
        return win
    }

    fun isJackpot(cfg: Config, balancePaise: Long, prizePaise: Long): Boolean =
        prizePaise > 0 && prizePaise <= cfg.prize1 && bandFor(balancePaise) == BAND_D

    fun isDirectBig(balancePaise: Long, prizePaise: Long): Boolean =
        bandFor(balancePaise) == BAND_D &&
            (prizePaise == PRIZE10 || prizePaise == PRIZE15 || prizePaise == PRIZE20)

    fun usedForToday(spinDate: String?, usedToday: Int, todayIst: String?): Int =
        if (spinDate != null && todayIst != null && spinDate == todayIst) usedToday else 0

    fun dailyLimit(cfg: Config, premium: Boolean): Int =
        if (premium) cfg.dailyPremium else cfg.dailyFree

    fun spinsLeft(cfg: Config, premium: Boolean, spinDate: String?, usedToday: Int, todayIst: String): Int {
        val left = dailyLimit(cfg, premium) - usedForToday(spinDate, usedToday, todayIst)
        return maxOf(0, left)
    }

    fun segmentFor(cfg: Config, balanceBeforePaise: Long, prizePaise: Long): String {
        if (prizePaise <= 0) return "0"
        if (isJackpot(cfg, balanceBeforePaise, prizePaise)) return "J"
        if (prizePaise == PRIZE10) return "10"
        if (prizePaise == PRIZE15) return "15"
        if (prizePaise == PRIZE20) return "20"
        if (prizePaise == PRIZE5) return "5"
        if (prizePaise == cfg.prize2) return "2"
        return "1"
    }
}

object SegmentPicker {
    val SEGMENTS: List<String> = listOf("1", "2", "5", "10", "15", "20", "0", "J")
    fun pickSegment(cfg: SpinLogic.Config, balanceBeforePaise: Long, prizePaise: Long): String =
        SpinLogic.segmentFor(cfg, balanceBeforePaise, prizePaise)
    fun indexOf(segmentId: String): Int = SEGMENTS.indexOf(segmentId).let { if (it < 0) 6 else it }
}

object CheckinLogic {
    fun isCheckedIn(checkinDate: String?, todayIst: String?): Boolean =
        todayIst != null && todayIst == checkinDate

    fun canSpin(checkinDate: String?, todayIst: String?): Boolean = isCheckedIn(checkinDate, todayIst)

    fun nextStreak(checkinDate: String?, streak: Int, todayIst: String, yesterdayIst: String): Int {
        if (isCheckedIn(checkinDate, todayIst)) return maxOf(0, streak)
        if (checkinDate != null && checkinDate == yesterdayIst) return maxOf(0, streak) + 1
        return 1
    }

    fun isMilestone(streak: Int): Boolean = streak > 0 && streak % 7 == 0
}

object BonusLogic {
    const val MSG_INVALID = "Invalid bonus code"
    const val MSG_EXPIRED = "This bonus code has expired"
    const val MSG_USED = "You have already used this code"

    const val OK = 0; const val NOT_FOUND = 1; const val EXPIRED = 2; const val ALREADY_USED = 3

    fun classify(
        found: Boolean, deleted: Boolean, active: Boolean,
        usageLimit: Long, usedCount: Long, alreadyRedeemed: Boolean
    ): Int {
        if (!found) return NOT_FOUND
        if (deleted || !active || (usageLimit > 0 && usedCount >= usageLimit)) return EXPIRED
        if (alreadyRedeemed) return ALREADY_USED
        return OK
    }

    fun messageFor(cls: Int): String = when (cls) {
        NOT_FOUND -> MSG_INVALID
        EXPIRED -> MSG_EXPIRED
        ALREADY_USED -> MSG_USED
        else -> ""
    }

    fun normalize(raw: String?): String = raw?.trim()?.uppercase() ?: ""
}

object TaskLogic {
    const val MSG_NOT_FOUND = "Task not found"
    const val MSG_ENDED = "This task has ended"
    const val MSG_FULL = "This task is full now. Try another task."
    const val MSG_DONE = "You already completed this task"
    const val MSG_PENDING = "Your proof is waiting for approval"

    const val OK = 0; const val NOT_FOUND = 1; const val ENDED = 2
    const val FULL = 3; const val DONE = 4; const val PENDING = 5

    fun classify(
        found: Boolean, deleted: Boolean, active: Boolean,
        usageLimit: Long, usedCount: Long, existingStatus: String?
    ): Int {
        if (!found) return NOT_FOUND
        if (deleted || !active) return ENDED
        if (existingStatus == "claimed" || existingStatus == "approved") return DONE
        if (existingStatus == "pending") return PENDING
        if (usageLimit > 0 && usedCount >= usageLimit) return FULL
        return OK
    }

    fun messageFor(cls: Int): String = when (cls) {
        NOT_FOUND -> MSG_NOT_FOUND
        ENDED -> MSG_ENDED
        FULL -> MSG_FULL
        DONE -> MSG_DONE
        PENDING -> MSG_PENDING
        else -> ""
    }

    fun claimId(uid: String, taskId: String, repeatDaily: Boolean, todayIst: String): String =
        if (repeatDaily) "${uid}_${taskId}_$todayIst" else "${uid}_$taskId"

    fun claimKey(taskId: String, repeatDaily: Boolean, todayIst: String): String =
        if (repeatDaily) "${taskId}_$todayIst" else taskId

    fun applyId(uid: String, taskId: String): String = "${uid}_$taskId"

    fun bonusRedeemId(uid: String, code: String): String = "${uid}_$code"

    fun spinWinId(uid: String, postSpinDate: String, postUsedToday: Int, postNotifUsed: Int): String =
        "${uid}_${postSpinDate}_${postUsedToday}_$postNotifUsed"

    fun spinWinIdGift(uid: String, postSpinDate: String, postUsedToday: Int, postNotifUsed: Int, postGiftUsed: Int): String =
        "${uid}_${postSpinDate}_${postUsedToday}_${postNotifUsed}_$postGiftUsed"

    fun applyState(status: String, expiresAtMs: Long, nowMs: Long): String {
        var st = status
        if (st == "active" && expiresAtMs > 0 && nowMs > expiresAtMs) st = "expired"
        return st
    }

    fun applyWindowMs(days: Long): Long = days * 86_400_000L
}

/** Paise -> rupee string without trailing zeros (the app's rupees()). */
fun rupeesStr(paise: Long): String {
    val r = paise / 100
    val p = paise % 100
    return if (p == 0L) r.toString() else "$r.${if (p < 10) "0" else ""}$p"
}

// ───────────────────────── User snapshot + site config ─────────────────────

data class EarnUser(
    val exists: Boolean = false,
    val name: String = "",
    val email: String = "",
    val premiumFlag: Boolean = false,
    val premiumUntilMs: Long = 0L,
    val banned: Boolean = false,
    val credits: Long = 0L,
    val walletPaise: Long = 0L,
    val spinDate: String = "",
    val spinUsedToday: Int = 0,
    val spinFirstDone: Boolean = false,
    val checkinDate: String = "",
    val checkinStreak: Int = 0,
    val bonusUsed: Set<String> = emptySet(),
    val taskClaimed: Set<String> = emptySet(),
    val notifSpinsUsed: Int = 0,
    val notifDebtCounted: Int = 0,
    val spinDebt: Int = 0,
    val spinDebtDate: String = "",
    val notifToggleDate: String = "",
    val notifToggleCount: Int = 0,
    val earnBanDate: String = "",
    val walletFrozen: Boolean = false,
    val walletFreezeReason: String = "",
    val giftSpins: Int = 0,
    val giftSpinsUsed: Int = 0,
    val plan: String = "",
    val referCode: String = "",
    val referralRedeemed: Boolean = false,
    val referralCount: Int = 0,
    val createdAtMs: Long = 0L,
    val lastIp: String = ""
) {
    companion object {
        fun parse(f: JsonObject): EarnUser = EarnUser(
            exists = true,
            name = f.eStr("name"),
            email = f.eStr("email"),
            // Production docs use premium/premiumUntil; legacy fallbacks kept.
            premiumFlag = if (f.eHas("premium")) f.eBool("premium") else f.eBool("isPremium"),
            premiumUntilMs = if (f.eHas("premiumUntil")) f.eTsMs("premiumUntil") else f.eTsMs("premiumExpiry"),
            banned = f.eBool("banned"),
            credits = f.eLong("credits"),
            walletPaise = f.eLong("walletBalancePaise"),
            spinDate = f.eStr("spinDate"),
            spinUsedToday = f.eLong("spinUsedToday").toInt(),
            spinFirstDone = f.eBool("spinFirstDone"),
            checkinDate = f.eStr("checkinDate"),
            checkinStreak = f.eLong("checkinStreak").toInt(),
            bonusUsed = f.eMapKeys("bonusUsed"),
            taskClaimed = f.eMapKeys("taskClaimed"),
            notifSpinsUsed = f.eLong("notifSpinsUsed").toInt(),
            notifDebtCounted = f.eLong("notifDebtCounted").toInt(),
            spinDebt = f.eLong("spinDebt").toInt(),
            spinDebtDate = f.eStr("spinDebtDate"),
            notifToggleDate = f.eStr("notifToggleDate"),
            notifToggleCount = f.eLong("notifToggleCount").toInt(),
            earnBanDate = f.eStr("earnBanDate"),
            walletFrozen = f.eBool("walletFrozen"),
            walletFreezeReason = f.eStr("walletFreezeReason"),
            giftSpins = f.eLong("giftSpins").toInt(),
            giftSpinsUsed = f.eLong("giftSpinsUsed").toInt(),
            plan = f.eStr("plan"),
            referCode = f.eStr("referCode"),
            referralRedeemed = f.eBool("referralRedeemed"),
            referralCount = f.eLong("referralCount").toInt(),
            createdAtMs = f.eTsMs("createdAt"),
            lastIp = f.eStr("lastIp")
        )

        /** App ProfileSyncEngine.derive: OWNER / ACTIVE / EXPIRED / FREE. */
        fun premiumStatus(email: String, flag: Boolean, untilMs: Long, nowMs: Long): String = when {
            email.equals("ayansikligar98@gmail.com", ignoreCase = true) -> "OWNER"
            flag && untilMs > nowMs -> "ACTIVE"
            flag && untilMs > 0L -> "EXPIRED"
            else -> "FREE"
        }

        fun isPremium(email: String, flag: Boolean, untilMs: Long, nowMs: Long): Boolean {
            val s = premiumStatus(email, flag, untilMs, nowMs)
            return s == "ACTIVE" || s == "OWNER"
        }
    }
}

data class WebConfig(
    val spin: SpinLogic.Config = SpinLogic.Config(),
    val earnPaused: Boolean = false,
    val taskApplyDays: Long = 0L,
    val upiId: String = "ayansikligar@slc",
    val prices: Map<String, Int> = mapOf("1m" to 99, "3m" to 249, "6m" to 449, "1y" to 849),
    val creditPacks: List<Pair<Int, Int>> = listOf(50 to 49, 120 to 99, 300 to 199),
    val planCredits: Map<String, Int> = mapOf("1m" to 100, "3m" to 300, "6m" to 600, "1y" to 1200),
    val freeLimit: Int = 5,
    val referDaysNew: Int = 3,
    val referDaysInviter: Int = 6,
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "The app is under maintenance. Please try again in a little while.",
    val announcementTitle: String = "",
    val announcementText: String = ""
)

/** settings/config -> numeric map + typed config (the app's AppConfig.load). */
suspend fun loadWebConfig(token: String?): WebConfig {
    val fields = when (val r = fsGetDoc("settings/config", token)) {
        is DocFetch.Found -> r.fields
        else -> return WebConfig()
    }
    val numRaw = HashMap<String, Double>()
    for ((k, _) in fields) fields.eDouble(k)?.let { numRaw[k] = it }

    val spin = SpinLogic.Config.fromSettings(numRaw)

    fun intOf(key: String, def: Int, min: Int, max: Int): Int {
        val v = numRaw[key] ?: return def
        val i = v.toInt()
        return if (i in min..max) i else def
    }
    fun longOf(key: String, def: Long, min: Long, max: Long): Long {
        val v = numRaw[key] ?: return def
        val l = v.toLong()
        return if (l in min..max) l else def
    }

    val prices = HashMap<String, Int>()
    val pricesMap = fields["prices"]?.jsonObject?.get("mapValue")?.jsonObject?.get("fields")?.jsonObject
    for (k in listOf("1m", "3m", "6m", "1y")) {
        val direct = intOf("price$k", -1, 1, 100000)
        if (direct > 0) { prices[k] = direct; continue }
        if (pricesMap != null) {
            val mv = pricesMap.eLong(k).toInt()
            if (mv in 1..100000) { prices[k] = mv; continue }
        }
    }
    if (prices.isEmpty()) prices.putAll(mapOf("1m" to 99, "3m" to 249, "6m" to 449, "1y" to 849))
    else for (k in listOf("1m", "3m", "6m", "1y")) if (!prices.containsKey(k)) {
        prices[k] = mapOf("1m" to 99, "3m" to 249, "6m" to 449, "1y" to 849)[k]!!
    }

    val packs = ArrayList<Pair<Int, Int>>()
    val packsArr = fields["creditPacks"]?.jsonObject?.get("arrayValue")?.jsonObject?.get("values") as? JsonArray
    if (packsArr != null) for (el in packsArr) {
        val mf = (el as? JsonObject)?.get("mapValue")?.jsonObject?.get("fields")?.jsonObject ?: continue
        val c = mf.eLong("credits").toInt(); val p = mf.eLong("price").toInt()
        if (c > 0 && p > 0) packs.add(c to p)
    }
    if (packs.isEmpty()) packs.addAll(listOf(50 to 49, 120 to 99, 300 to 199))

    val planCredits = HashMap<String, Int>()
    val pcMap = fields["planCredits"]?.jsonObject?.get("mapValue")?.jsonObject?.get("fields")?.jsonObject
    for (k in listOf("1m", "3m", "6m", "1y")) {
        if (pcMap != null) {
            val mv = pcMap.eLong(k).toInt()
            if (mv > 0) planCredits[k] = mv
        }
    }
    if (planCredits.isEmpty()) planCredits.putAll(mapOf("1m" to 100, "3m" to 300, "6m" to 600, "1y" to 1200))

    var annText = fields.eStr("announcementText")
    if (annText.isBlank()) annText = fields.eStr("announcement")
    val maintMsg = fields.eStr("maintenanceMessage")

    return WebConfig(
        spin = spin,
        earnPaused = fields.eBool("earnPaused"),
        taskApplyDays = longOf("taskApplyDays", 0L, 0, 365),
        upiId = fields.eStr("upiId").ifBlank { "ayansikligar@slc" },
        prices = prices,
        creditPacks = packs,
        planCredits = planCredits,
        freeLimit = intOf("freeLimit", 5, 0, 10000),
        referDaysNew = intOf("referDaysNew", 3, 0, 365),
        referDaysInviter = intOf("referDaysInviter", 6, 0, 365),
        maintenanceMode = fields.eBool("maintenanceMode"),
        maintenanceMessage = maintMsg.ifBlank { "The app is under maintenance. Please try again in a little while." },
        announcementTitle = fields.eStr("announcementTitle"),
        announcementText = annText
    )
}

// ───────────────────────── Fund coupling (shared) ──────────────────────────

data class FundSnapshot(val balancePaise: Long, val totalGivenPaise: Long)

suspend fun checkFund(amountPaise: Long, token: String): FundSnapshot? {
    if (amountPaise <= 0) return FundSnapshot(0, 0)
    val f = when (val r = fsGetDoc("adminFund/main", token)) {
        is DocFetch.Found -> r.fields
        else -> return null
    }
    val bal = f.eLong("balancePaise")
    if (bal < amountPaise) return null
    return FundSnapshot(bal, f.eLong("totalGivenPaise"))
}

/** The two fund writes appended to the SAME commit as the user's credit. */
fun fundWrites(
    amountPaise: Long, type: String, uid: String,
    labelKey: String?, labelValue: String?, fund: FundSnapshot
): List<JsonObject> {
    val fundFields = mapOf(
        "balancePaise" to fsInt(fund.balancePaise - amountPaise),
        "totalGivenPaise" to fsInt(fund.totalGivenPaise + amountPaise)
    )
    val fundUpdate = updateWriteJson(
        "adminFund/main", fundFields, listOf("balancePaise", "totalGivenPaise"), null
    )
    val txnFields = HashMap<String, JsonObject>()
    txnFields["type"] = fsStr(type)
    txnFields["amountPaise"] = fsInt(-amountPaise)
    txnFields["uid"] = fsStr(uid)
    if (labelKey != null && labelValue != null) txnFields[labelKey] = fsStr(labelValue)
    txnFields["createdAt"] = fsTs(nowMillis())
    val txnCreate = updateWriteJson("adminFundTxns/${randomUuid()}", txnFields, null, false)
    return listOf(fundUpdate, txnCreate)
}

// ───────────────────────── Snapshot + debt reconcile ───────────────────────

data class EarnSnapshot(
    val cfg: WebConfig,
    val user: EarnUser,
    val premium: Boolean,
    val today: String,
    val dailyLeft: Int,
    val extraGranted: Int,
    val giftSpins: Int,
    val spinsLeft: Int,
    val checkedIn: Boolean,
    val earnBanned: Boolean
)

private fun snapshotOf(cfg: WebConfig, user: EarnUser, email: String): EarnSnapshot {
    val today = todayIstE()
    val premium = EarnUser.isPremium(email, user.premiumFlag, user.premiumUntilMs, nowMillis())
    val dailyLeft = SpinLogic.spinsLeft(cfg.spin, premium, user.spinDate, user.spinUsedToday, today)
    // Web cannot observe the Android notification toggle: extras are
    // reported as granted but are NOT spendable here (notifEnabled=false).
    val extraGranted = maxOf(0, cfg.spin.notifRewardSpins - user.notifSpinsUsed)
    val gift = SpinLogic.giftSpinsAvailable(user.giftSpins, user.giftSpinsUsed)
    return EarnSnapshot(
        cfg = cfg, user = user, premium = premium, today = today,
        dailyLeft = dailyLeft, extraGranted = extraGranted, giftSpins = gift,
        spinsLeft = dailyLeft + gift,
        checkedIn = CheckinLogic.isCheckedIn(user.checkinDate, today),
        earnBanned = SpinLogic.isEarnBanned(user.earnBanDate, today)
    )
}

/**
 * Start-of-day spin-debt application — the debt branch of the app's
 * reconcile, mirrored exactly (the toggle branch is not observable on web).
 */
private suspend fun reconcileDebt(user: EarnUser, cfg: WebConfig, email: String, token: String): EarnUser {
    if (user.spinDebt <= 0) return user
    val today = todayIstE()
    if (today == user.spinDebtDate) return user
    val premium = EarnUser.isPremium(email, user.premiumFlag, user.premiumUntilMs, nowMillis())
    val baseUsed = SpinLogic.usedForToday(user.spinDate, user.spinUsedToday, today)
    val limit = SpinLogic.dailyLimit(cfg.spin, premium)
    val applied = SpinLogic.debtAppliedToday(user.spinDebt, baseUsed, limit)
    if (applied <= 0 && today == user.spinDate) return user
    val newDebt = user.spinDebt - applied
    val newUsed = baseUsed + applied
    val fields = mapOf(
        "spinDebt" to fsInt(newDebt.toLong()),
        "spinDebtDate" to fsStr(today),
        "spinDate" to fsStr(today),
        "spinUsedToday" to fsInt(newUsed.toLong())
    )
    val w = updateWriteJson("users/${FirebaseApi.uid}", fields,
        listOf("spinDebt", "spinDebtDate", "spinDate", "spinUsedToday"), true)
    val err = fsCommit(listOf(w), token)
    return if (err == null) user.copy(
        spinDebt = newDebt, spinDebtDate = today, spinDate = today, spinUsedToday = newUsed
    ) else user
}

object EarnApi {

    suspend fun loadSnapshot(): EarnSnapshot? {
        val token = FirebaseApi.validToken() ?: return null
        val uid = FirebaseApi.uid ?: return null
        val cfg = loadWebConfig(token)
        var user = when (val r = fsGetDoc("users/$uid", token)) {
            is DocFetch.Found -> EarnUser.parse(r.fields)
            else -> EarnUser()
        }
        val email = FirebaseApi.currentEmail() ?: user.email
        user = reconcileDebt(user, cfg, email, token)
        return snapshotOf(cfg, user, email)
    }

    suspend fun freshUser(token: String): EarnUser? {
        val uid = FirebaseApi.uid ?: return null
        return when (val r = fsGetDoc("users/$uid", token)) {
            is DocFetch.Found -> EarnUser.parse(r.fields)
            else -> null
        }
    }

    // ── Daily check-in (app EarnApi.checkIn) ────────────────────────────────
    sealed interface CheckInOutcome {
        data class Ok(val streak: Int, val already: Boolean, val milestone: Boolean) : CheckInOutcome
        data class Rejected(val message: String) : CheckInOutcome
    }

    suspend fun checkIn(): CheckInOutcome {
        val token = FirebaseApi.validToken()
            ?: return CheckInOutcome.Rejected("Please sign in again.")
        val uid = FirebaseApi.uid ?: return CheckInOutcome.Rejected("Please sign in again.")
        val user = freshUser(token) ?: return CheckInOutcome.Rejected(PAUSED_MSG)
        val today = todayIstE()
        if (CheckinLogic.isCheckedIn(user.checkinDate, today)) {
            return CheckInOutcome.Ok(user.checkinStreak, true, false)
        }
        val streak = CheckinLogic.nextStreak(user.checkinDate, user.checkinStreak, today, yesterdayIstE())
        val fields = mapOf(
            "checkinDate" to fsStr(today),
            "checkinStreak" to fsInt(streak.toLong())
        )
        val w = updateWriteJson("users/$uid", fields, listOf("checkinDate", "checkinStreak"), true)
        val err = fsCommit(listOf(w), token)
        return if (err == null) CheckInOutcome.Ok(streak, false, CheckinLogic.isMilestone(streak))
        else CheckInOutcome.Rejected(err)
    }

    // ── Spin (app EarnApi.spin; consumption order daily -> gift on web) ────
    sealed interface SpinOutcome {
        data class Ok(
            val prizePaise: Long,
            val segment: String,
            val jackpot: Boolean,
            val directBig: Boolean,
            val walletAfterPaise: Long,
            val dailyLeftAfter: Int,
            val giftLeftAfter: Int
        ) : SpinOutcome
        data class Rejected(val code: String, val message: String) : SpinOutcome
    }

    suspend fun spin(): SpinOutcome {
        val token = FirebaseApi.validToken()
            ?: return SpinOutcome.Rejected("NETWORK", "Please sign in again.")
        val uid = FirebaseApi.uid ?: return SpinOutcome.Rejected("NETWORK", "Please sign in again.")
        val cfg = loadWebConfig(token)
        if (cfg.earnPaused) return SpinOutcome.Rejected("PAUSED", PAUSED_MSG)
        var user = freshUser(token) ?: return SpinOutcome.Rejected("NETWORK", PAUSED_MSG)
        val email = FirebaseApi.currentEmail() ?: user.email
        user = reconcileDebt(user, cfg, email, token)
        val today = todayIstE()
        val c = cfg.spin

        if (user.walletFrozen) return SpinOutcome.Rejected("FROZEN", FROZEN_MSG)
        if (SpinLogic.isEarnBanned(user.earnBanDate, today))
            return SpinOutcome.Rejected("BAN", BAN_MSG)
        if (!CheckinLogic.canSpin(user.checkinDate, today))
            return SpinOutcome.Rejected("CHECKIN", CHECKIN_MSG)
        if (user.walletPaise >= c.minWithdrawPaise)
            return SpinOutcome.Rejected("WITHDRAW_READY",
                "Your wallet reached ₹${rupeesStr(c.minWithdrawPaise)}. Withdraw it first!")

        val premium = EarnUser.isPremium(email, user.premiumFlag, user.premiumUntilMs, nowMillis())
        val baseUsed = SpinLogic.usedForToday(user.spinDate, user.spinUsedToday, today)
        val dailyLeft = maxOf(0, SpinLogic.dailyLimit(c, premium) - baseUsed)
        val giftLeft = SpinLogic.giftSpinsAvailable(user.giftSpins, user.giftSpinsUsed)
        val useGift = dailyLeft <= 0
        if (useGift && giftLeft <= 0) return SpinOutcome.Rejected("NO_SPINS", "No spins left today")

        val prize = SpinLogic.decidePrize(c, user.walletPaise, !user.spinFirstDone, Random.nextInt(10000))
        val walletAfter = user.walletPaise + prize

        val fields = HashMap<String, JsonObject>()
        fields["walletBalancePaise"] = fsInt(walletAfter)
        fields["spinFirstDone"] = fsBool(true)
        val mask = arrayListOf("walletBalancePaise", "spinFirstDone")
        var postDate = user.spinDate
        var postUsed = user.spinUsedToday
        var postNotif = user.notifSpinsUsed
        var postGift = user.giftSpinsUsed
        if (!useGift) {
            postDate = today; postUsed = baseUsed + 1
            fields["spinDate"] = fsStr(postDate)
            fields["spinUsedToday"] = fsInt(postUsed.toLong())
            mask.add("spinDate"); mask.add("spinUsedToday")
        } else {
            postGift = user.giftSpinsUsed + 1
            fields["giftSpinsUsed"] = fsInt(postGift.toLong())
            mask.add("giftSpinsUsed")
        }
        val writes = arrayListOf(updateWriteJson("users/$uid", fields, mask, true))

        if (prize > 0) {
            val fund = checkFund(prize, token)
                ?: return SpinOutcome.Rejected("PAUSED", PAUSED_MSG)
            writes.addAll(fundWrites(prize, "spin", uid, null, null, fund))
            val winId = if (!useGift) TaskLogic.spinWinId(uid, postDate, postUsed, postNotif)
                        else TaskLogic.spinWinIdGift(uid, postDate, postUsed, postNotif, postGift)
            val winFields = mapOf(
                "uid" to fsStr(uid),
                "prizePaise" to fsInt(prize),
                "date" to fsStr(today),
                "status" to fsStr("credited"),
                "createdAt" to fsTs(nowMillis())
            )
            writes.add(updateWriteJson("spinWins/$winId", winFields, null, false))
        }

        val err = fsCommit(writes, token)
        if (err != null) return SpinOutcome.Rejected("NETWORK", err)
        return SpinOutcome.Ok(
            prizePaise = prize,
            segment = SegmentPicker.pickSegment(c, user.walletPaise, prize),
            jackpot = SpinLogic.isJackpot(c, user.walletPaise, prize),
            directBig = SpinLogic.isDirectBig(user.walletPaise, prize),
            walletAfterPaise = walletAfter,
            dailyLeftAfter = if (useGift) dailyLeft else dailyLeft - 1,
            giftLeftAfter = if (useGift) giftLeft - 1 else giftLeft
        )
    }

    // ── Bonus codes (app EarnApi.redeemBonus) ───────────────────────────────
    sealed interface BonusOutcome {
        data class Ok(val amountPaise: Long, val walletAfterPaise: Long) : BonusOutcome
        data class Rejected(val message: String) : BonusOutcome
    }

    suspend fun redeemBonus(rawCode: String): BonusOutcome {
        val code = BonusLogic.normalize(rawCode)
        if (code.isEmpty()) return BonusOutcome.Rejected(BonusLogic.MSG_INVALID)
        if (code.contains('/') || code.contains('?') || code.contains('#'))
            return BonusOutcome.Rejected(BonusLogic.MSG_INVALID)
        val token = FirebaseApi.validToken()
            ?: return BonusOutcome.Rejected("Please sign in again.")
        val uid = FirebaseApi.uid ?: return BonusOutcome.Rejected("Please sign in again.")
        val user = freshUser(token) ?: return BonusOutcome.Rejected(PAUSED_MSG)
        if (user.walletFrozen) return BonusOutcome.Rejected(FROZEN_MSG)

        val doc = fsGetDoc("bonusCodes/$code", token)
        val already = fsGetDoc("bonusRedeems/${TaskLogic.bonusRedeemId(uid, code)}", token) is DocFetch.Found
        val found = doc is DocFetch.Found
        val bf = (doc as? DocFetch.Found)?.fields
        val cls = BonusLogic.classify(
            found,
            bf?.eBool("deleted") ?: false,
            if (bf != null && bf.eHas("active")) bf.eBool("active") else true,
            bf?.eLong("usageLimit") ?: 0L,
            bf?.eLong("usedCount") ?: 0L,
            already
        )
        if (cls != BonusLogic.OK) return BonusOutcome.Rejected(BonusLogic.messageFor(cls))
        val amount = bf!!.eLong("amountPaise")
        if (amount <= 0) return BonusOutcome.Rejected(BonusLogic.MSG_INVALID)

        val fund = checkFund(amount, token) ?: return BonusOutcome.Rejected(PAUSED_MSG)

        val redeemFields = mapOf(
            "uid" to fsStr(uid),
            "code" to fsStr(code),
            "amountPaise" to fsInt(amount),
            "deviceIdHash" to fsStr(webDeviceHash()),
            "createdAt" to fsTs(nowMillis())
        )
        val codeFields = mapOf("usedCount" to fsInt((bf.eLong("usedCount") + 1)))
        val walletAfter = user.walletPaise + amount
        val bonusUsedFields = HashMap<String, JsonObject>()
        for (k in user.bonusUsed) bonusUsedFields[k] = fsBool(true)
        bonusUsedFields[code] = fsBool(true)
        val userFields = mapOf(
            "walletBalancePaise" to fsInt(walletAfter),
            "bonusUsed" to fsMap(bonusUsedFields)
        )
        val writes = arrayListOf(
            updateWriteJson("bonusRedeems/${TaskLogic.bonusRedeemId(uid, code)}", redeemFields, null, false),
            updateWriteJson("bonusCodes/$code", codeFields, listOf("usedCount"), true),
            updateWriteJson("users/$uid", userFields, listOf("walletBalancePaise", "bonusUsed"), true)
        )
        writes.addAll(fundWrites(amount, "bonus", uid, "code", code, fund))
        val err = fsCommit(writes, token)
        return if (err == null) BonusOutcome.Ok(amount, walletAfter)
        else BonusOutcome.Rejected(err)
    }
}
