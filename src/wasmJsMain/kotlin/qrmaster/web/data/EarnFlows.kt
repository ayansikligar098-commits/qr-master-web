package qrmaster.web.data

import kotlinx.serialization.json.JsonObject
import qrmaster.web.calc.Sha256
import kotlin.math.roundToLong

// ── Tasks / Withdraw / Premium / Support / Referral — faithful ports of the
// Android app's flows (EarnApi task section, WalletScreen withdraw,
// PremiumScreen + PremiumLogic, InviteScreen + ReferralLogic, SupportScreen).

// ───────────────────────── Tasks ───────────────────────────────────────────

data class WebTask(
    val id: String,
    val title: String,
    val desc: String,
    val details: String,
    val conditions: String,
    val linkUrl: String,
    val rewardPaise: Long,
    val mode: String,               // "auto" | "admin"
    val timerSec: Int,
    val usageLimit: Long,
    val usedCount: Long,
    val repeatDaily: Boolean,
    val payDays: Long,
    val applyDays: Long,
    val guideImageB64: String,
    val claimStatus: String?,       // taskClaims status for me (claimed/pending/approved/rejected)
    val applyStatus: String?,       // taskApplies state for me (active/submitted/expired)
    val applyExpiresAtMs: Long,
    val linkOpenedAtMs: Long,
    val linkOpenCount: Long
)

data class TasksBundle(val tasks: List<WebTask>, val error: String?)

suspend fun EarnApi.loadTasks(): TasksBundle {
    val token = FirebaseApi.validToken() ?: return TasksBundle(emptyList(), "Please sign in again.")
    val uid = FirebaseApi.uid ?: return TasksBundle(emptyList(), "Please sign in again.")
    val docs = fsListCollection("tasks", token) ?: return TasksBundle(emptyList(), "Could not load tasks.")
    val claims = fsQueryEq("taskClaims", "uid", uid, 100, token) ?: emptyList()
    val applies = fsQueryEq("taskApplies", "uid", uid, 100, token) ?: emptyList()
    val today = todayIstE()
    val claimById = HashMap<String, JsonObject>()
    for ((id, f) in claims) claimById[id] = f
    val applyById = HashMap<String, JsonObject>()
    for ((id, f) in applies) applyById[id] = f
    val now = nowMillis()
    val out = ArrayList<WebTask>()
    for ((id, f) in docs) {
        if (f.eBool("deleted")) continue
        val active = if (f.eHas("active")) f.eBool("active") else true
        if (!active) continue
        val repeat = f.eBool("repeatDaily")
        val cid = TaskLogic.claimId(uid, id, repeat, today)
        val claimStatus = claimById[cid]?.eStr("status")?.ifBlank { null }
        val af = applyById[TaskLogic.applyId(uid, id)]
        val applyStatus = af?.let {
            TaskLogic.applyState(it.eStr("status").ifBlank { "active" }, it.eTsMs("expiresAt"), now)
        }
        out.add(
            WebTask(
                id = id,
                title = f.eStr("title").ifBlank { "Task" },
                desc = f.eText("desc").ifBlank { f.eText("description") },
                details = f.eText("details"),
                conditions = f.eText("conditions"),
                linkUrl = f.eStr("linkUrl").ifBlank { f.eStr("link") },
                rewardPaise = f.eLong("rewardPaise"),
                mode = f.eStr("mode").ifBlank { "auto" },
                timerSec = if (f.eHas("timerSec")) f.eLong("timerSec").toInt() else 30,
                usageLimit = f.eLong("usageLimit"),
                usedCount = f.eLong("usedCount"),
                repeatDaily = repeat,
                payDays = f.eLong("payDays"),
                applyDays = f.eLong("applyDays"),
                guideImageB64 = f.eStr("guideImageB64"),
                claimStatus = claimStatus,
                applyStatus = applyStatus,
                applyExpiresAtMs = af?.eTsMs("expiresAt") ?: 0L,
                linkOpenedAtMs = af?.eTsMs("linkOpenedAt") ?: 0L,
                linkOpenCount = af?.eLong("linkOpenCount") ?: 0L
            )
        )
    }
    return TasksBundle(out, null)
}

sealed interface SimpleOutcome {
    data object Ok : SimpleOutcome
    data class Rejected(val message: String) : SimpleOutcome
}

suspend fun EarnApi.applyTask(task: WebTask): SimpleOutcome {
    val token = FirebaseApi.validToken() ?: return SimpleOutcome.Rejected("Please sign in again.")
    val uid = FirebaseApi.uid ?: return SimpleOutcome.Rejected("Please sign in again.")
    val cfg = loadWebConfig(token)
    var days = task.applyDays
    if (days <= 0) days = cfg.taskApplyDays
    if (days <= 0) days = 7
    val now = nowMillis()
    val fields = mapOf(
        "uid" to fsStr(uid),
        "taskId" to fsStr(task.id),
        "appliedAt" to fsTs(now),
        "expiresAt" to fsTs(now + TaskLogic.applyWindowMs(days)),
        "status" to fsStr("active")
    )
    val w = updateWriteJson(
        "taskApplies/${TaskLogic.applyId(uid, task.id)}", fields,
        listOf("uid", "taskId", "appliedAt", "expiresAt", "status"), null
    )
    val err = fsCommit(listOf(w), token)
    return if (err == null) SimpleOutcome.Ok else SimpleOutcome.Rejected(err)
}

/** Best-effort link-open stamp (the app's markLinkOpened). */
suspend fun EarnApi.markLinkOpened(taskId: String) {
    try {
        val token = FirebaseApi.validToken() ?: return
        val uid = FirebaseApi.uid ?: return
        val path = "taskApplies/${TaskLogic.applyId(uid, taskId)}"
        val cur = (fsGetDoc(path, token) as? DocFetch.Found)?.fields ?: return
        if (cur.eStr("status").ifBlank { "active" } != "active") return
        val first = cur.eTsMs("linkOpenedAt") <= 0L
        val fields = HashMap<String, JsonObject>()
        val mask = ArrayList<String>()
        if (first) { fields["linkOpenedAt"] = fsTs(nowMillis()); mask.add("linkOpenedAt") }
        fields["linkOpenCount"] = fsInt(cur.eLong("linkOpenCount") + 1); mask.add("linkOpenCount")
        fsPatchDoc(path, fields, mask, token)
    } catch (e: Exception) { /* best effort, like the app */ }
}

suspend fun EarnApi.claimAutoTask(task: WebTask): SimpleOutcome {
    val token = FirebaseApi.validToken() ?: return SimpleOutcome.Rejected("Please sign in again.")
    val uid = FirebaseApi.uid ?: return SimpleOutcome.Rejected("Please sign in again.")
    val cfg = loadWebConfig(token)
    if (cfg.earnPaused) return SimpleOutcome.Rejected(PAUSED_MSG)
    val user = freshUser(token) ?: return SimpleOutcome.Rejected(PAUSED_MSG)
    if (user.walletFrozen) return SimpleOutcome.Rejected(FROZEN_MSG)
    val doc = fsGetDoc("tasks/${task.id}", token)
    val tf = (doc as? DocFetch.Found)?.fields
    val existing = fsGetDoc("taskClaims/${TaskLogic.claimId(uid, task.id, task.repeatDaily, todayIstE())}", token)
    val existingStatus = (existing as? DocFetch.Found)?.fields?.eStr("status")
    val cls = TaskLogic.classify(
        doc is DocFetch.Found,
        tf?.eBool("deleted") ?: false,
        if (tf != null && tf.eHas("active")) tf.eBool("active") else true,
        tf?.eLong("usageLimit") ?: 0L,
        tf?.eLong("usedCount") ?: 0L,
        existingStatus
    )
    if (cls != TaskLogic.OK) return SimpleOutcome.Rejected(TaskLogic.messageFor(cls))

    // Apply gate (app applyClaimError): an active, unexpired apply is required.
    val af = (fsGetDoc("taskApplies/${TaskLogic.applyId(uid, task.id)}", token) as? DocFetch.Found)?.fields
    if (af == null) return SimpleOutcome.Rejected(APPLY_FIRST_MSG)
    val state = TaskLogic.applyState(af.eStr("status").ifBlank { "active" }, af.eTsMs("expiresAt"), nowMillis())
    when (state) {
        "expired" -> return SimpleOutcome.Rejected(APPLY_EXPIRED_MSG)
        "submitted" -> return SimpleOutcome.Rejected(TaskLogic.MSG_PENDING)
        "approved" -> return SimpleOutcome.Rejected(TaskLogic.MSG_DONE)
        "active" -> Unit
        else -> return SimpleOutcome.Rejected(APPLY_FIRST_MSG)
    }

    val reward = tf!!.eLong("rewardPaise")
    if (reward <= 0) return SimpleOutcome.Rejected(TaskLogic.MSG_NOT_FOUND)
    val today = todayIstE()
    val claimId = TaskLogic.claimId(uid, task.id, task.repeatDaily, today)
    val claimKey = TaskLogic.claimKey(task.id, task.repeatDaily, today)

    val fund = checkFund(reward, token) ?: return SimpleOutcome.Rejected(PAUSED_MSG)

    val writes = ArrayList<JsonObject>()
    if (!task.repeatDaily) {
        writes.add(updateWriteJson(
            "taskApplies/${TaskLogic.applyId(uid, task.id)}",
            mapOf("status" to fsStr("submitted"), "claimId" to fsStr(claimId)),
            listOf("status", "claimId"), true
        ))
    }
    val claimFields = mapOf(
        "uid" to fsStr(uid),
        "taskId" to fsStr(task.id),
        "taskTitle" to fsStr(tf.eStr("title")),
        "rewardPaise" to fsInt(reward),
        "payDays" to fsInt(tf.eLong("payDays")),
        "status" to fsStr("claimed"),
        "deviceIdHash" to fsStr(webDeviceHash()),
        "createdAt" to fsTs(nowMillis())
    )
    writes.add(updateWriteJson("taskClaims/$claimId", claimFields, null, false))
    writes.add(updateWriteJson(
        "tasks/${task.id}",
        mapOf("usedCount" to fsInt(tf.eLong("usedCount") + 1)),
        listOf("usedCount"), true
    ))
    val claimedFields = HashMap<String, JsonObject>()
    for (k in user.taskClaimed) claimedFields[k] = fsBool(true)
    claimedFields[claimKey] = fsBool(true)
    val userFields = mapOf(
        "walletBalancePaise" to fsInt(user.walletPaise + reward),
        "taskClaimed" to fsMap(claimedFields)
    )
    writes.add(updateWriteJson("users/$uid", userFields, listOf("walletBalancePaise", "taskClaimed"), true))
    writes.addAll(fundWrites(reward, "task", uid, "taskId", task.id, fund))
    val err = fsCommit(writes, token)
    return if (err == null) SimpleOutcome.Ok else SimpleOutcome.Rejected(err)
}

suspend fun EarnApi.submitTaskProof(task: WebTask, screenshotB64: String, userNote: String): SimpleOutcome {
    if (screenshotB64.isBlank()) return SimpleOutcome.Rejected("Please add a screenshot first.")
    if (screenshotB64.length > 700_000)
        return SimpleOutcome.Rejected("This screenshot is too large. Please choose a smaller one.")
    val token = FirebaseApi.validToken() ?: return SimpleOutcome.Rejected("Please sign in again.")
    val uid = FirebaseApi.uid ?: return SimpleOutcome.Rejected("Please sign in again.")
    val cfg = loadWebConfig(token)
    if (cfg.earnPaused) return SimpleOutcome.Rejected(PAUSED_MSG)
    val user = freshUser(token) ?: return SimpleOutcome.Rejected(PAUSED_MSG)
    if (user.walletFrozen) return SimpleOutcome.Rejected(FROZEN_MSG)
    val doc = fsGetDoc("tasks/${task.id}", token)
    val tf = (doc as? DocFetch.Found)?.fields
    val today = todayIstE()
    val claimId = TaskLogic.claimId(uid, task.id, task.repeatDaily, today)
    val existing = fsGetDoc("taskClaims/$claimId", token)
    val existingFields = (existing as? DocFetch.Found)?.fields
    val existingStatus = existingFields?.eStr("status")
    val cls = TaskLogic.classify(
        doc is DocFetch.Found,
        tf?.eBool("deleted") ?: false,
        if (tf != null && tf.eHas("active")) tf.eBool("active") else true,
        tf?.eLong("usageLimit") ?: 0L,
        tf?.eLong("usedCount") ?: 0L,
        existingStatus
    )
    val rejectedRetry = cls == TaskLogic.OK && existingStatus == "rejected"
    if (cls != TaskLogic.OK && !rejectedRetry) return SimpleOutcome.Rejected(TaskLogic.messageFor(cls))

    val af = (fsGetDoc("taskApplies/${TaskLogic.applyId(uid, task.id)}", token) as? DocFetch.Found)?.fields
    if (af == null) return SimpleOutcome.Rejected(APPLY_FIRST_MSG)
    val state = TaskLogic.applyState(af.eStr("status").ifBlank { "active" }, af.eTsMs("expiresAt"), nowMillis())
    when (state) {
        "expired" -> return SimpleOutcome.Rejected(APPLY_EXPIRED_MSG)
        "submitted" -> return SimpleOutcome.Rejected(TaskLogic.MSG_PENDING)
        "approved" -> return SimpleOutcome.Rejected(TaskLogic.MSG_DONE)
        "active" -> Unit
        else -> return SimpleOutcome.Rejected(APPLY_FIRST_MSG)
    }

    val claimFields = HashMap<String, JsonObject>()
    claimFields["uid"] = fsStr(uid)
    claimFields["taskId"] = fsStr(task.id)
    claimFields["taskTitle"] = fsStr(tf!!.eStr("title"))
    claimFields["rewardPaise"] = fsInt(tf.eLong("rewardPaise"))
    claimFields["payDays"] = fsInt(tf.eLong("payDays"))
    claimFields["status"] = fsStr("pending")
    claimFields["screenshotB64"] = fsStr(screenshotB64)
    claimFields["deviceIdHash"] = fsStr(webDeviceHash())
    claimFields["createdAt"] = fsTs(nowMillis())
    val note = userNote.trim()
    if (note.isNotEmpty()) claimFields["userNote"] = fsStr(note.take(300))
    val writes = ArrayList<JsonObject>()
    if (!task.repeatDaily) {
        writes.add(updateWriteJson(
            "taskApplies/${TaskLogic.applyId(uid, task.id)}",
            mapOf("status" to fsStr("submitted"), "claimId" to fsStr(claimId)),
            listOf("status", "claimId"), true
        ))
    }
    writes.add(updateWriteJson("taskClaims/$claimId", claimFields, null, existing != null && existing is DocFetch.Found))
    val err = fsCommit(writes, token)
    return if (err == null) SimpleOutcome.Ok else SimpleOutcome.Rejected(err)
}

// ───────────────────────── Withdraw (WalletScreen) ─────────────────────────

suspend fun EarnApi.withdraw(upiRaw: String): SimpleOutcome {
    val upi = upiRaw.trim().lowercase()
    if (upi.length < 5 || !upi.contains("@")) return SimpleOutcome.Rejected("Please enter a valid UPI ID.")
    val token = FirebaseApi.validToken() ?: return SimpleOutcome.Rejected("Please sign in again.")
    val uid = FirebaseApi.uid ?: return SimpleOutcome.Rejected("Please sign in again.")
    val cfg = loadWebConfig(token)
    val user = freshUser(token) ?: return SimpleOutcome.Rejected(PAUSED_MSG)
    if (user.walletFrozen) return SimpleOutcome.Rejected(FROZEN_MSG)
    if (user.walletPaise < cfg.spin.minWithdrawPaise)
        return SimpleOutcome.Rejected("Minimum withdrawal is ₹${rupeesStr(cfg.spin.minWithdrawPaise)}.")
    val fields = mapOf(
        "uid" to fsStr(uid),
        "name" to fsStr(user.name),
        "email" to fsStr(user.email),
        "upiId" to fsStr(upi),
        "amountPaise" to fsInt(user.walletPaise),
        "status" to fsStr("pending"),
        "deviceIdHash" to fsStr(webDeviceHash()),
        "createdAt" to fsTs(nowMillis())
    )
    val writes = listOf(
        updateWriteJson("withdrawRequests/${randomUuid()}", fields, null, false),
        updateWriteJson("users/$uid", mapOf("walletBalancePaise" to fsInt(0)),
            listOf("walletBalancePaise"), true)
    )
    val err = fsCommit(writes, token)
    return if (err == null) SimpleOutcome.Ok else SimpleOutcome.Rejected(err)
}

// ───────────────────────── Premium (PremiumScreen + PremiumLogic) ──────────

object PremiumLogic {
    const val MSG_INVALID = "Invalid coupon code"
    const val MSG_EXPIRED = "This coupon has expired"
    const val MSG_ALL_USED = "All coupons are used up! Stay tuned — new offers are coming soon."
    const val MSG_NOT_ACTIVE = "This coupon is not active."
    const val MSG_NOT_VALID = "This coupon is not valid."

    enum class CouponClass { OK, DELETED, LIMIT_FULL, NOT_ACTIVE, NO_DISCOUNT }

    data class CouponFacts(
        val deleted: Boolean, val active: Boolean,
        val usageLimit: Long, val usedCount: Long,
        val amount: Long, val percent: Long
    ) {
        val hasDiscount: Boolean get() = amount > 0 || percent > 0
    }

    fun classify(f: CouponFacts): CouponClass = when {
        f.deleted -> CouponClass.DELETED
        f.usageLimit > 0 && f.usedCount >= f.usageLimit -> CouponClass.LIMIT_FULL
        !f.active -> CouponClass.NOT_ACTIVE
        !f.hasDiscount -> CouponClass.NO_DISCOUNT
        else -> CouponClass.OK
    }

    fun usable(f: CouponFacts): Boolean =
        !f.deleted && (f.usageLimit <= 0 || f.usedCount < f.usageLimit)

    fun limitFullMessage(anyUsableAnywhere: Boolean): String =
        if (anyUsableAnywhere) MSG_EXPIRED else MSG_ALL_USED

    fun discountInputs(f: CouponFacts): Pair<Long, Int> =
        if (f.amount > 0) f.amount to 0 else -1L to minOf(f.percent, 100L).toInt()

    fun discountFor(base: Long, discountAmount: Long, discountPct: Int): Long {
        if (discountAmount >= 0) return minOf(discountAmount, base)
        if (discountPct > 0) return (base * discountPct / 100.0).roundToLong()
        return 0L
    }

    val PLAN_KEYS: List<String> = listOf("1m", "3m", "6m", "1y")
    val PLAN_NAMES: List<String> = listOf("1 Month", "3 Months", "6 Months", "1 Year")
    val PLAN_MONTHS: List<Int> = listOf(1, 3, 6, 12)

    fun planMonths(planKey: String): Int {
        val i = PLAN_KEYS.indexOf(planKey)
        return if (i >= 0) PLAN_MONTHS[i] else 1
    }

    fun planName(planKey: String): String {
        val i = PLAN_KEYS.indexOf(planKey)
        return if (i >= 0) PLAN_NAMES[i] else planKey
    }

    fun couponFacts(f: JsonObject): CouponFacts = CouponFacts(
        deleted = f.eBool("deleted"),
        active = f.eBool("active"),
        usageLimit = f.eLong("usageLimit"),
        usedCount = f.eLong("usedCount"),
        amount = if (f.eHas("amount")) f.eLong("amount") else -1L,
        percent = f.eLong("percent")
    )
}

sealed interface CouponOutcome {
    data class Ok(val code: String, val discountAmount: Long, val discountPct: Int) : CouponOutcome
    data class Rejected(val message: String) : CouponOutcome
}

suspend fun EarnApi.applyCoupon(raw: String): CouponOutcome {
    val code = raw.trim().uppercase()
    if (code.isEmpty()) return CouponOutcome.Rejected(PremiumLogic.MSG_INVALID)
    val token = FirebaseApi.validToken() ?: return CouponOutcome.Rejected("Please sign in again.")
    val doc = fsGetDoc("coupons/${urlEncodeComponent(code)}", token)
    val fields = (doc as? DocFetch.Found)?.fields
        ?: return CouponOutcome.Rejected(PremiumLogic.MSG_INVALID)
    val facts = PremiumLogic.couponFacts(fields)
    return when (PremiumLogic.classify(facts)) {
        PremiumLogic.CouponClass.OK -> {
            val (amt, pct) = PremiumLogic.discountInputs(facts)
            CouponOutcome.Ok(code, amt, pct)
        }
        PremiumLogic.CouponClass.DELETED -> CouponOutcome.Rejected(PremiumLogic.MSG_EXPIRED)
        PremiumLogic.CouponClass.LIMIT_FULL -> {
            val all = fsListCollection("coupons", token) ?: emptyList()
            val anyUsable = all.any { PremiumLogic.usable(PremiumLogic.couponFacts(it.second)) }
            CouponOutcome.Rejected(PremiumLogic.limitFullMessage(anyUsable))
        }
        PremiumLogic.CouponClass.NOT_ACTIVE -> CouponOutcome.Rejected(PremiumLogic.MSG_NOT_ACTIVE)
        PremiumLogic.CouponClass.NO_DISCOUNT -> CouponOutcome.Rejected(PremiumLogic.MSG_NOT_VALID)
    }
}

fun urlEncodeComponent(s: String): String {
    val hex = "0123456789ABCDEF"
    val sb = StringBuilder()
    for (ch in s) {
        val safe = ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9' ||
            ch == '-' || ch == '_' || ch == '.' || ch == '~'
        if (safe) sb.append(ch) else {
            val bytes = ch.toString().encodeToByteArray()
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                sb.append('%'); sb.append(hex[v shr 4]); sb.append(hex[v and 15])
            }
        }
    }
    return sb.toString()
}

/** UTR rule (the app's): trimmed, at least 12 chars, letters/digits only. */
fun isValidUtr(raw: String): Boolean {
    val u = raw.trim()
    if (u.length < 12) return false
    return u.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }
}

suspend fun EarnApi.submitPlanRequest(
    planKey: String, basePrice: Int,
    coupon: CouponOutcome.Ok?, utr: String, screenshotB64: String?
): SimpleOutcome {
    val token = FirebaseApi.validToken() ?: return SimpleOutcome.Rejected("Please sign in again.")
    val uid = FirebaseApi.uid ?: return SimpleOutcome.Rejected("Please sign in again.")
    val user = freshUser(token) ?: return SimpleOutcome.Rejected(PAUSED_MSG)
    val discount = if (coupon != null)
        PremiumLogic.discountFor(basePrice.toLong(), coupon.discountAmount, coupon.discountPct) else 0L
    val finalAmt = basePrice - discount
    val fields = LinkedHashMap<String, JsonObject>()
    fields["uid"] = fsStr(uid)
    fields["email"] = fsStr(user.email)
    fields["name"] = fsStr(user.name)
    fields["plan"] = fsStr(planKey)
    fields["months"] = fsInt(PremiumLogic.planMonths(planKey).toLong())
    fields["amountPaid"] = fsInt(finalAmt)
    fields["couponCode"] = fsStr(coupon?.code ?: "")
    fields["utr"] = fsStr(utr.trim())
    if (screenshotB64 != null) fields["screenshotB64"] = fsStr(screenshotB64)
    fields["status"] = fsStr("pending")
    fields["createdAt"] = fsTs(nowMillis())
    return try {
        if (coupon == null) {
            fsCreateDoc("payRequests", fields, token)
            SimpleOutcome.Ok
        } else {
            // Fresh coupon re-read + usable re-check before the atomic commit.
            val fresh = (fsGetDoc("coupons/${urlEncodeComponent(coupon.code)}", token) as? DocFetch.Found)?.fields
                ?: return SimpleOutcome.Rejected(PremiumLogic.MSG_INVALID)
            val facts = PremiumLogic.couponFacts(fresh)
            if (PremiumLogic.classify(facts) != PremiumLogic.CouponClass.OK)
                return SimpleOutcome.Rejected(PremiumLogic.MSG_EXPIRED)
            val writes = listOf(
                updateWriteJson("payRequests/${randomUuid()}", fields, null, false),
                updateWriteJson("coupons/${coupon.code}",
                    mapOf("usedCount" to fsInt(facts.usedCount + 1)), listOf("usedCount"), true)
            )
            val err = fsCommit(writes, token)
            if (err == null) SimpleOutcome.Ok else SimpleOutcome.Rejected(err)
        }
    } catch (e: Exception) {
        SimpleOutcome.Rejected(e.message ?: "Could not send the request.")
    }
}

suspend fun EarnApi.submitCreditRequest(
    packCredits: Int, packPrice: Int, qty: Int, utr: String, screenshotB64: String?
): SimpleOutcome {
    val token = FirebaseApi.validToken() ?: return SimpleOutcome.Rejected("Please sign in again.")
    val uid = FirebaseApi.uid ?: return SimpleOutcome.Rejected("Please sign in again.")
    val user = freshUser(token) ?: return SimpleOutcome.Rejected(PAUSED_MSG)
    val totalCredits = (packCredits * qty).toLong()
    val amount = (packPrice * qty).toLong()
    val fields = LinkedHashMap<String, JsonObject>()
    fields["uid"] = fsStr(uid)
    fields["email"] = fsStr(user.email)
    fields["name"] = fsStr(user.name)
    fields["type"] = fsStr("credits")
    fields["packCredits"] = fsInt(totalCredits)
    fields["qty"] = fsInt(qty.toLong())
    fields["amount"] = fsInt(amount)
    fields["amountPaid"] = fsInt(amount)
    fields["plan"] = fsStr("")
    fields["months"] = fsInt(0)
    fields["couponCode"] = fsStr("")
    fields["utr"] = fsStr(utr.trim())
    if (screenshotB64 != null) fields["screenshotB64"] = fsStr(screenshotB64)
    fields["status"] = fsStr("pending")
    fields["createdAt"] = fsTs(nowMillis())
    return try {
        fsCreateDoc("payRequests", fields, token)
        SimpleOutcome.Ok
    } catch (e: Exception) {
        SimpleOutcome.Rejected(e.message ?: "Could not send the request.")
    }
}

data class MyRequest(
    val id: String, val kind: String, val title: String,
    val amountPaid: Long, val status: String, val rejectReason: String, val createdAtMs: Long
)

suspend fun EarnApi.myRequests(): List<MyRequest> {
    val token = FirebaseApi.validToken() ?: return emptyList()
    val uid = FirebaseApi.uid ?: return emptyList()
    val rows = fsQueryEq("payRequests", "uid", uid, 10, token) ?: return emptyList()
    return rows.map { (id, f) ->
        val type = f.eStr("type")
        val plan = f.eStr("plan")
        val kind: String; val title: String
        if (type == "credits") {
            kind = "credits"; title = "${f.eLong("packCredits")} Credits Pack"
        } else {
            kind = "premium"; title = "Premium — ${PremiumLogic.planName(plan)}"
        }
        MyRequest(
            id = id, kind = kind, title = title,
            amountPaid = f.eLong("amountPaid"),
            status = f.eStr("status").ifBlank { "pending" },
            rejectReason = f.eStr("rejectReason"),
            createdAtMs = f.eTsMs("createdAt")
        )
    }
}

// ───────────────────────── Support tickets ─────────────────────────────────

// Backend category KEY -> display label, exactly like the Android app.
// Firestore rules only accept the lowercase keys ('bug', 'payment', ...);
// sending the display label is rejected with PERMISSION_DENIED.
val SUPPORT_CATEGORIES: List<Pair<String, String>> = listOf(
    "bug" to "Bug",
    "payment" to "Payment",
    "refer" to "Referral",
    "spin" to "Spin & Earn",
    "task" to "Tasks",
    "bonus" to "Bonus",
    "wallet" to "Wallet",
    "credits" to "Credits",
    "notification" to "Notification",
    "other" to "Other"
)

fun supportCategoryLabel(key: String): String =
    SUPPORT_CATEGORIES.firstOrNull { it.first == key }?.second ?: "Other"

data class SupportTicket(
    val id: String, val category: String, val message: String,
    val status: String, val createdAtMs: Long
)

data class TicketReply(val from: String, val text: String, val createdAtMs: Long)

suspend fun EarnApi.createTicket(category: String, message: String, imageB64: String?): SimpleOutcome {
    val token = FirebaseApi.validToken() ?: return SimpleOutcome.Rejected("Please sign in again.")
    val uid = FirebaseApi.uid ?: return SimpleOutcome.Rejected("Please sign in again.")
    val user = freshUser(token)
    val fields = LinkedHashMap<String, JsonObject>()
    fields["uid"] = fsStr(uid)
    fields["email"] = fsStr(user?.email ?: (FirebaseApi.currentEmail() ?: ""))
    fields["name"] = fsStr(user?.name ?: "")
    fields["category"] = fsStr(category)
    fields["message"] = fsStr(message.trim())
    if (imageB64 != null) fields["imageB64"] = fsStr(imageB64)
    fields["status"] = fsStr("open")
    fields["appVersion"] = fsStr("web")
    fields["createdAt"] = fsTs(nowMillis())
    return try {
        fsCreateDoc("supportTickets", fields, token)
        SimpleOutcome.Ok
    } catch (e: Exception) {
        SimpleOutcome.Rejected(e.message ?: "Could not send the ticket.")
    }
}

suspend fun EarnApi.myTickets(): List<SupportTicket> {
    val token = FirebaseApi.validToken() ?: return emptyList()
    val uid = FirebaseApi.uid ?: return emptyList()
    val rows = fsQueryEq("supportTickets", "uid", uid, 30, token) ?: return emptyList()
    return rows.map { (id, f) ->
        SupportTicket(
            id = id,
            category = f.eStr("category"),
            message = f.eStr("message"),
            status = f.eStr("status").ifBlank { "open" },
            createdAtMs = f.eTsMs("createdAt")
        )
    }
}

suspend fun EarnApi.ticketReplies(ticketId: String): List<TicketReply> {
    val token = FirebaseApi.validToken() ?: return emptyList()
    val rows = fsListCollection("supportTickets/$ticketId/replies", token) ?: return emptyList()
    return rows.map { (_, f) ->
        TicketReply(f.eStr("from"), f.eStr("text"), f.eTsMs("createdAt"))
    }.sortedBy { it.createdAtMs }
}

suspend fun EarnApi.addTicketReply(ticketId: String, text: String): SimpleOutcome {
    val token = FirebaseApi.validToken() ?: return SimpleOutcome.Rejected("Please sign in again.")
    return try {
        fsCreateDoc("supportTickets/$ticketId/replies", mapOf(
            "from" to fsStr("user"),
            "text" to fsStr(text.trim()),
            "createdAt" to fsTs(nowMillis())
        ), token)
        fsPatchDoc("supportTickets/$ticketId",
            mapOf("status" to fsStr("open")), listOf("status"), token)
        SimpleOutcome.Ok
    } catch (e: Exception) {
        SimpleOutcome.Rejected(e.message ?: "Could not send the reply.")
    }
}

// ───────────────────────── Referral (InviteScreen + ReferralLogic) ─────────

object ReferralLogic {
    private const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // 31 chars, no 0/O/1/I/L

    fun codeFor(uid: String, attempt: Int): String {
        val hex = Sha256.hex("$uid#$attempt")
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < hex.length && sb.length < 7) {
            val b = hex.substring(i, i + 2).toInt(16)
            sb.append(ALPHABET[b % 31])
            i += 2
        }
        return sb.toString()
    }

    fun extendedUntil(nowMs: Long, existingUntilMs: Long, days: Int): Long {
        val base = if (existingUntilMs > nowMs) existingUntilMs else nowMs
        return base + days.toLong() * 86400000L
    }

    fun codeFromLink(text: String): String? {
        val t = text.trim()
        val idx = t.indexOf("ref=")
        if (idx < 0) return null
        val rest = t.substring(idx + 4)
        val code = rest.takeWhile { it.isLetterOrDigit() }.uppercase()
        return code.ifBlank { null }
    }

    fun normalizeCode(raw: String): String {
        codeFromLink(raw)?.let { return it }
        return raw.trim().uppercase().filter { it.isLetterOrDigit() }
    }
}

data class ReferralInfo(
    val myCode: String,
    val referralCount: Int,
    val alreadyRedeemed: Boolean,
    val referrals: List<MyReferral>
)

data class MyReferral(
    val id: String, val redeemerName: String, val redeemerEmail: String,
    val status: String, val createdAtMs: Long
)

/** accounts:lookup createdAt (ms) — for the createdAt backfill + 7-day rule. */
private suspend fun authCreatedAtMs(token: String): Long {
    return try {
        val body = kotlinx.serialization.json.buildJsonObject {
            put("idToken", kotlinx.serialization.json.JsonPrimitive(token))
        }
        val res = httpRequest(
            "POST",
            "https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=${FirebaseConfig.API_KEY}",
            body.toString(), null
        )
        val users = kotlinx.serialization.json.Json.parseToJsonElement(res)
            .let { (it as? kotlinx.serialization.json.JsonObject)?.get("users") as? kotlinx.serialization.json.JsonArray }
        val created = (users?.firstOrNull() as? JsonObject)?.get("createdAt")
            ?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }?.toLongOrNull() ?: 0L
        created
    } catch (e: Exception) { 0L }
}

/** ensureProfile mirror (best-effort presence + referral index maintenance). */
suspend fun EarnApi.ensureReferralProfile() {
    try {
        val token = FirebaseApi.validToken() ?: return
        val uid = FirebaseApi.uid ?: return
        var user = freshUser(token) ?: return
        val hash = webDeviceHash()
        val patches = HashMap<String, JsonObject>()
        val mask = ArrayList<String>()
        if (user.lastIp.isBlank()) { /* ip is flag-only in the app; skip on web */ }
        patches["lastDeviceHash"] = fsStr(hash); mask.add("lastDeviceHash")
        patches["lastSeenAt"] = fsTs(nowMillis()); mask.add("lastSeenAt")
        if (user.createdAtMs <= 0L) {
            val created = authCreatedAtMs(token)
            if (created > 0) { patches["createdAt"] = fsTs(created); mask.add("createdAt") }
        }
        var code = user.referCode
        if (code.isBlank()) {
            var attempt = 0
            var chosen: String? = null
            while (attempt < 5) {
                val cand = ReferralLogic.codeFor(uid, attempt)
                when (val idx = fsGetDoc("referCodes/$cand", token)) {
                    is DocFetch.Found -> {
                        if (idx.fields.eStr("uid") == uid) { chosen = cand; break }
                        attempt++
                    }
                    DocFetch.Missing -> { chosen = cand; break }
                    is DocFetch.Fail -> { chosen = cand; break }
                }
            }
            if (chosen != null) {
                code = chosen
                patches["referCode"] = fsStr(code); mask.add("referCode")
            }
        }
        if (mask.isNotEmpty()) fsPatchDoc("users/$uid", patches, mask, token)
        if (code.isNotBlank()) {
            val idxFields = mapOf(
                "uid" to fsStr(uid),
                "deviceIdHash" to fsStr(hash),
                "updatedAt" to fsTs(nowMillis())
            )
            try { fsPatchDoc("referCodes/$code", idxFields, listOf("uid", "deviceIdHash", "updatedAt"), token) }
            catch (e: Exception) { /* index upsert is best-effort */ }
        }
    } catch (e: Exception) { /* best effort, like the app */ }
}

suspend fun EarnApi.referralInfo(): ReferralInfo? {
    val token = FirebaseApi.validToken() ?: return null
    val uid = FirebaseApi.uid ?: return null
    val user = freshUser(token) ?: return null
    val rows = fsQueryEq("referrals", "inviterUid", uid, 100, token) ?: emptyList()
    val mine = rows.mapNotNull { (id, f) ->
        val st = f.eStr("status")
        if (st == "success" || st == "admin-approved") {
            MyReferral(id, f.eStr("redeemerName"), f.eStr("redeemerEmail"), st, f.eTsMs("createdAt"))
        } else null
    }
    return ReferralInfo(user.referCode, user.referralCount, user.referralRedeemed, mine)
}

sealed interface RedeemOutcome {
    data class Ok(val days: Int) : RedeemOutcome
    data class Rejected(val message: String) : RedeemOutcome
}

private suspend fun logReferAttempt(
    uid: String, email: String, code: String, hash: String,
    result: String, reason: String, token: String
) {
    try {
        val fields = LinkedHashMap<String, JsonObject>()
        fields["uid"] = fsStr(uid)
        fields["email"] = fsStr(email)
        fields["code"] = fsStr(code)
        fields["deviceIdHash"] = fsStr(hash)
        fields["redeemIp"] = fsStr("")
        fields["result"] = fsStr(result)
        fields["reason"] = fsStr(reason)
        fields["createdAt"] = fsTs(nowMillis())
        fsCreateDoc("referAttempts", fields, token)
    } catch (e: Exception) { /* logging is best-effort */ }
}

/**
 * Referral redeem — the app's redeemFlow. Web divergence: the device hash
 * is a per-browser persistent hash (clearing site data resets it) and the
 * Android clone/dual-app signal (REF-09) cannot exist on web.
 */
suspend fun EarnApi.redeemReferral(rawInput: String, cfg: WebConfig): RedeemOutcome {
    val token = FirebaseApi.validToken() ?: return RedeemOutcome.Rejected("Please sign in again.")
    val uid = FirebaseApi.uid ?: return RedeemOutcome.Rejected("Please sign in again.")
    val myEmail = FirebaseApi.currentEmail() ?: ""
    val code = ReferralLogic.normalizeCode(rawInput)
    val hash = webDeviceHash()
    suspend fun reject(reason: String, message: String): RedeemOutcome {
        logReferAttempt(uid, myEmail, code, hash, "rejected", reason, token)
        return RedeemOutcome.Rejected(message)
    }
    val me = freshUser(token) ?: return reject("REF-08", "Something went wrong. Please try again.")
    if (me.referralRedeemed) return reject("REF-02", "You have already used a referral code.")
    var createdMs = me.createdAtMs
    if (createdMs <= 0L) createdMs = authCreatedAtMs(token)
    if (createdMs > 0L && nowMillis() - createdMs > 7L * 86400000L)
        return reject("REF-05", "Referral codes work only on new accounts (first 7 days).")
    val idx = fsGetDoc("referCodes/$code", token)
    val inviterUid = (idx as? DocFetch.Found)?.fields?.eStr("uid") ?: ""
    if (inviterUid.isBlank()) return reject("REF-01", "This referral code is not valid.")
    if (inviterUid == uid) return reject("REF-03", "You cannot use your own referral code.")
    val inviter = (fsGetDoc("users/$inviterUid", token) as? DocFetch.Found)?.fields?.let { EarnUser.parse(it) }
        ?: return reject("REF-01", "This referral code is not valid.")
    if (inviter.banned) return reject("REF-06", "This referral code is not valid.")
    if (hash.isNotEmpty()) {
        val inviterDoc = fsGetDoc("users/$inviterUid", token)
        val inviterHash = (inviterDoc as? DocFetch.Found)?.fields?.eStr("lastDeviceHash") ?: ""
        if (inviterHash.isNotEmpty() && inviterHash == hash)
            return reject("REF-04", "This code cannot be used on this device.")
        val devDoc = fsGetDoc("referDevices/$hash", token)
        if (devDoc is DocFetch.Found)
            return reject("REF-07", "A referral was already used on this device.")
    }

    val daysNew = cfg.referDaysNew
    val daysInviter = cfg.referDaysInviter
    val now = nowMillis()
    val myUntil = ReferralLogic.extendedUntil(now, me.premiumUntilMs, daysNew)
    val fieldsMe = mapOf(
        "premium" to fsBool(myUntil > now),
        "premiumUntil" to fsTs(myUntil),
        "referralRedeemed" to fsBool(true),
        "referredBy" to fsStr(inviterUid)
    )
    val writes = ArrayList<JsonObject>()
    val refFields = LinkedHashMap<String, JsonObject>()
    refFields["redeemerUid"] = fsStr(uid)
    refFields["redeemerEmail"] = fsStr(me.email)
    refFields["redeemerName"] = fsStr(me.name)
    refFields["inviterUid"] = fsStr(inviterUid)
    refFields["inviterEmail"] = fsStr(inviter.email)
    refFields["inviterName"] = fsStr(inviter.name)
    refFields["code"] = fsStr(code)
    refFields["deviceIdHash"] = fsStr(hash)
    refFields["deviceIdSource"] = fsStr("web")
    refFields["redeemIp"] = fsStr("")
    refFields["inviterIp"] = fsStr(inviter.lastIp)
    refFields["daysNew"] = fsInt(daysNew.toLong())
    refFields["daysInviter"] = fsInt(daysInviter.toLong())
    refFields["status"] = fsStr("success")
    refFields["createdAt"] = fsTs(now)
    writes.add(updateWriteJson("referrals/$uid", refFields, null, false))
    writes.add(updateWriteJson("users/$uid", fieldsMe,
        listOf("premium", "premiumUntil", "referralRedeemed", "referredBy"), null))
    if (daysInviter > 0) {
        val invUntil = ReferralLogic.extendedUntil(now, inviter.premiumUntilMs, daysInviter)
        val invFields = mapOf(
            "premium" to fsBool(invUntil > now),
            "premiumUntil" to fsTs(invUntil),
            "referralCount" to fsInt(inviter.referralCount + 1L)
        )
        writes.add(updateWriteJson("users/$inviterUid", invFields,
            listOf("premium", "premiumUntil", "referralCount"), null))
    }
    if (hash.isNotEmpty()) {
        val devFields = mapOf(
            "redeemerUid" to fsStr(uid),
            "code" to fsStr(code),
            "createdAt" to fsTs(now)
        )
        writes.add(updateWriteJson("referDevices/$hash", devFields, null, false))
    }
    val err = fsCommit(writes, token)
    if (err != null) {
        val reason = when {
            err.contains("403") || err.contains("PERMISSION") -> "REF-08"
            err.contains("FAILED_PRECONDITION") || err.contains("ALREADY_EXISTS") || err.contains("409") -> "REF-07"
            else -> "REF-08"
        }
        logReferAttempt(uid, myEmail, code, hash, "rejected", reason, token)
        return RedeemOutcome.Rejected(
            if (reason == "REF-07") "A referral was already used on this device."
            else "Something went wrong. Please try again."
        )
    }
    logReferAttempt(uid, myEmail, code, hash, "success", "", token)
    return RedeemOutcome.Ok(daysNew)
}
