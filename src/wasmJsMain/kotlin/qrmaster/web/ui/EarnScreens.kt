package qrmaster.web.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlinx.browser.window
import kotlinx.coroutines.launch
import qrmaster.web.data.EarnApi
import qrmaster.web.data.SUPPORT_CATEGORIES
import qrmaster.web.data.supportCategoryLabel
import qrmaster.web.data.SupportTicket
import qrmaster.web.data.TicketReply
import qrmaster.web.data.addTicketReply
import qrmaster.web.data.createTicket
import qrmaster.web.data.loadWebConfig
import qrmaster.web.data.myTickets
import qrmaster.web.data.redeemReferral
import qrmaster.web.data.referralInfo
import qrmaster.web.data.ticketReplies

// ── Bonus Code ─────────────────────────────────────────────────────────────

@Composable
fun BonusScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    ToolPage(IconKind.Gift, "Bonus Code", "Redeem a bonus code — the money goes straight into your wallet.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    if (state.user == null) {
                        Text("Login to redeem a bonus code.", fontSize = 13.5.sp, color = NavyText)
                        Button(
                            onClick = { state.navigate(Screen.Login) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                        ) { Text("Login") }
                    } else {
                        Text(
                            "Wallet balance: ${fmtRupees(state.profile?.walletPaise ?: 0L)}",
                            fontSize = 13.sp, color = MutedText
                        )
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it.uppercase() },
                            label = { Text("Bonus code") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        PrimaryToolButton("Redeem Bonus", enabled = code.isNotBlank(), busy = busy) {
                            busy = true; message = null
                            scope.launch {
                                when (val r = EarnApi.redeemBonus(code)) {
                                    is EarnApi.BonusOutcome.Ok -> {
                                        success = true
                                        message = "🎉 ₹${qrmaster.web.data.rupeesStr(r.amountPaise)} added to your wallet!"
                                        code = ""
                                        state.refreshProfile()
                                    }
                                    is EarnApi.BonusOutcome.Rejected -> {
                                        success = false
                                        message = r.message
                                    }
                                }
                                busy = false
                            }
                        }
                        if (message != null) {
                            Text(
                                message!!, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold,
                                color = if (success) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            "Bonus codes are created by the admin and have a usage limit. Each account can use a code once.",
                            fontSize = 12.sp, color = MutedText
                        )
                    }
                }
            }
        }
    }
}

// ── Invite & Earn (referral) ───────────────────────────────────────────────

@Composable
fun InviteScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf<qrmaster.web.data.ReferralInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var redeemInput by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }
    var cfgDays by remember { mutableStateOf(3 to 6) }

    LaunchedEffect(state.user) {
        loading = true
        try {
            val cfg = loadWebConfig(null)
            cfgDays = cfg.referDaysNew to cfg.referDaysInviter
        } catch (e: Exception) { }
        if (state.user != null) {
            info = try { EarnApi.referralInfo() } catch (e: Exception) { null }
            try {
                val pending = window.localStorage.getItem("qm_pending_ref")
                if (!pending.isNullOrBlank() && redeemInput.isBlank()) redeemInput = pending
            } catch (e: Exception) { }
        }
        loading = false
    }

    val myCode = info?.myCode ?: state.profile?.referCode ?: ""
    val link = if (myCode.isNotBlank()) pageBaseUrl().trimEnd('/') + "/?ref=" + myCode else ""

    ToolPage(IconKind.Users, "Invite & Earn", "Share your referral link — you both get free Premium days.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    if (state.user == null) {
                        Text("Login to get your referral link.", fontSize = 13.5.sp, color = NavyText)
                        Button(
                            onClick = { state.navigate(Screen.Login) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                        ) { Text("Login") }
                    } else if (loading) {
                        CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(22.dp))
                    } else {
                        Text(
                            "When a new user joins with your code, they get ${cfgDays.first} days of Premium free and you get ${cfgDays.second} days.",
                            fontSize = 13.sp, color = MutedText
                        )
                        Text("Your referral code", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(
                                Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFF0EAFE))
                                    .padding(horizontal = 16.dp, vertical = 11.dp)
                            ) {
                                Text(myCode.ifBlank { "…" }, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = PurplePrimary)
                            }
                            OutlinedButton(
                                onClick = { copyTextToClipboard(myCode); state.toast("Code copied") },
                                enabled = myCode.isNotBlank(), shape = RoundedCornerShape(10.dp)
                            ) { Text("Copy Code", fontSize = 12.5.sp) }
                        }
                        Text("Your referral link", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                        Text(link, fontSize = 12.5.sp, color = NavyText)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { copyTextToClipboard(link); state.toast("Link copied") },
                                enabled = link.isNotBlank(), shape = RoundedCornerShape(10.dp)
                            ) { Text("Copy Link", fontSize = 12.5.sp) }
                            OutlinedButton(
                                onClick = { shareText("Join Kitnook with my referral link and get free Premium days: $link") },
                                enabled = link.isNotBlank(), shape = RoundedCornerShape(10.dp)
                            ) { Text("Share", fontSize = 12.5.sp) }
                        }
                        Text(
                            "Successful referrals: ${info?.referralCount ?: 0}",
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NavyText
                        )
                    }
                }
            }
        }
        if (state.user != null && info?.alreadyRedeemed != true) {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Have a referral code?", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                        Text(
                            "Codes work once per account, only in the first 7 days after sign-up, and not on the same device as the inviter.",
                            fontSize = 12.sp, color = MutedText
                        )
                        OutlinedTextField(
                            value = redeemInput,
                            onValueChange = { redeemInput = it },
                            label = { Text("Referral code or link") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        PrimaryToolButton("Apply Code", enabled = redeemInput.isNotBlank(), busy = busy) {
                            busy = true; message = null
                            scope.launch {
                                val cfg = try { loadWebConfig(null) } catch (e: Exception) { null }
                                if (cfg == null) {
                                    message = "Something went wrong. Please try again."
                                    success = false
                                } else {
                                    when (val r = EarnApi.redeemReferral(redeemInput, cfg)) {
                                        is qrmaster.web.data.RedeemOutcome.Ok -> {
                                            success = true
                                            message = "🎉 ${r.days} days of Premium added to your account!"
                                            try { window.localStorage.removeItem("qm_pending_ref") } catch (e: Exception) { }
                                            state.refreshProfile()
                                            info = try { EarnApi.referralInfo() } catch (e: Exception) { info }
                                        }
                                        is qrmaster.web.data.RedeemOutcome.Rejected -> {
                                            success = false
                                            message = r.message
                                        }
                                    }
                                }
                                busy = false
                            }
                        }
                        if (message != null) {
                            Text(
                                message!!, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                color = if (success) Color(0xFF16A34A) else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
        val refs = info?.referrals ?: emptyList()
        if (refs.isNotEmpty()) {
            item { Text("My Referrals", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyText) }
            items(refs) { r ->
                WhiteCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(PurplePrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (r.redeemerName.firstOrNull() ?: r.redeemerEmail.firstOrNull() ?: 'U').uppercaseChar().toString(),
                                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.redeemerName.ifBlank { r.redeemerEmail }, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText, maxLines = 1)
                            Text(fmtDateTime(r.createdAtMs), fontSize = 11.5.sp, color = MutedText)
                        }
                        Text(
                            if (r.status == "admin-approved") "Approved" else "Success",
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A)
                        )
                    }
                }
            }
        }
        item {
            Text(
                "Note: on the website your device is recognised by this browser. Clearing browser data resets it, so the one-device rule is strongest in the Android app.",
                fontSize = 11.5.sp, color = MutedText
            )
        }
    }
}

// ── Help & Support ─────────────────────────────────────────────────────────

// Display categories for the Help & Support form (Ayan's spec, 25 Nov 2026).
// The Firestore rules only accept the existing lowercase backend keys, so
// every display category maps to the closest existing key and the ticket
// record keeps its exact old shape — this is a UI rebuild, not a backend
// change. The subject travels as the first line of the message because the
// rules allow no extra field on a ticket.
private data class SupportCategoryUi(
    val label: String, val key: String, val icon: IconKind, val tint: Color, val bg: Color
)

private val SUPPORT_CATEGORY_UI: List<SupportCategoryUi> = listOf(
    SupportCategoryUi("Account & Login", "other", IconKind.Person, Color(0xFF7C3AED), Color(0xFFEFE9FD)),
    SupportCategoryUi("Payment & Subscription", "payment", IconKind.Card, Color(0xFF2563EB), Color(0xFFE1EBFD)),
    SupportCategoryUi("Wallet & Withdrawal", "wallet", IconKind.Wallet, Color(0xFF0D9488), Color(0xFFD7F0EC)),
    SupportCategoryUi("Spin & Earn", "spin", IconKind.Target, Color(0xFFDB2777), Color(0xFFFBE0EE)),
    SupportCategoryUi("Tasks & Rewards", "task", IconKind.Task, Color(0xFF16A34A), Color(0xFFDFF3E4)),
    SupportCategoryUi("Referral Program", "refer", IconKind.Users, Color(0xFF7C3AED), Color(0xFFEFE9FD)),
    SupportCategoryUi("App / Website Issue", "other", IconKind.Phone, Color(0xFF0284C7), Color(0xFFDFF1FB)),
    SupportCategoryUi("Feature Request", "other", IconKind.Bulb, Color(0xFFD97706), Color(0xFFFAF0DA)),
    SupportCategoryUi("Bug Report", "bug", IconKind.Bug, Color(0xFFDC2626), Color(0xFFFBE0E0)),
    SupportCategoryUi("Other", "other", IconKind.Dots, Color(0xFF6B7280), Color(0xFFEBEDEF))
)

// Ticket attachments: images go through the existing screenshot pipeline
// (compressed base64 stored on the ticket document). The picker also accepts
// a PDF so the user gets a clear message — the ticket document itself cannot
// hold a PDF, so PDFs are explained, not silently dropped.
private val TICKET_ATTACH_SPEC = FileSpec(
    acceptAttr = "image/png,image/jpeg,.png,.jpg,.jpeg,application/pdf,.pdf",
    tokens = listOf("image/", ".png", ".jpg", ".jpeg", ".pdf", "application/pdf"),
    maxBytes = 5L * 1024 * 1024, maxCount = 1, kindLabel = "image or PDF"
)

private val SUPPORT_FAQS: List<Pair<String, String>> = listOf(
    "How do I get Premium?" to "Open Premium, choose a plan and pay online — Premium activates instantly after a successful payment. You can also redeem a coupon code shared by the admin.",
    "When can I withdraw my Spin & Earn money?" to "Withdrawals start at ₹20 and go up to ₹1,500 per request. The money is sent to your UPI ID after the admin approves the request.",
    "Why are my spins locked today?" to "Spins unlock after your daily check-in. Free members get 3 spins a day and Premium members get 10.",
    "How does the referral program work?" to "Share your referral link or code. A new user who joins with it gets 3 days of Premium free, and you get 6 days of Premium.",
    "What are credits used for?" to "Credits unlock extras like the HD passport-photo sheet (10 credits per sheet). Buy credit packs from the Wallet, or get free credits with Premium plans.",
    "How long does support take to reply?" to "We usually reply within 24–48 hours. Replies appear right here in My Tickets, below the form."
)

private val SUPPORT_GUIDES: List<Pair<String, String>> = listOf(
    "Create a QR code" to "All Tools → QR Generator → pick a type (link, WhatsApp, UPI...), type your content, customise the colours and download.",
    "Edit a PDF" to "All Tools → PDF Tools → PDF Editor → open your PDF, add text, images or a signature, then Download PDF.",
    "Make a passport photo" to "All Tools → Passport Photo Maker → upload a photo, choose the size, adjust the sheet and download it for printing.",
    "Compress a photo to an exact KB size" to "All Tools → Photo Tools → Compress to KB → upload the photo, enter the target size (for example 50 KB) and download.",
    "Buy Premium" to "Premium → choose a plan → Pay Online. Premium starts as soon as the payment succeeds.",
    "Withdraw your wallet balance" to "Wallet → Withdraw → enter your UPI ID and an amount between ₹20 and ₹1,500. The admin pays approved requests to that UPI ID."
)

private fun ticketSubjectPreview(t: SupportTicket): String {
    val first = t.message.substringBefore("\n\n")
    return if (first != t.message && first.length <= 100) first else t.message.take(48)
}

@Composable
fun SupportScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var selectedCat by remember { mutableStateOf<SupportCategoryUi?>(null) }
    var subject by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var catError by remember { mutableStateOf(false) }
    var subjectError by remember { mutableStateOf(false) }
    var descError by remember { mutableStateOf(false) }
    var shotB64 by remember { mutableStateOf<String?>(null) }
    var shotName by remember { mutableStateOf("") }
    var attachError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showFaq by remember { mutableStateOf(false) }
    var showGuides by remember { mutableStateOf(false) }
    var tickets by remember { mutableStateOf<List<SupportTicket>>(emptyList()) }
    var openTicket by remember { mutableStateOf<SupportTicket?>(null) }
    var replies by remember { mutableStateOf<List<TicketReply>>(emptyList()) }
    var replyText by remember { mutableStateOf("") }
    var replyBusy by remember { mutableStateOf(false) }

    fun reloadTickets() {
        scope.launch { tickets = try { EarnApi.myTickets() } catch (e: Exception) { emptyList() } }
    }
    LaunchedEffect(state.user) { if (state.user != null) reloadTickets() }

    fun encodeShot(f: WebFile) {
        scope.launch {
            val dataUrl = loadFileDataUrl(f) ?: return@launch
            imgOp("shotEncode", "{\"dataUrl\":\"$dataUrl\"}")
            val res = awaitAsync("img", 60_000)
            if (res != null && res.asyncOk()) {
                shotB64 = res.asyncStr("b64")
                shotName = f.name
            } else {
                state.toast("Could not process that screenshot")
            }
        }
    }

    fun onAttach(incoming: List<WebFile>, err: String?) {
        if (err != null) { attachError = err; return }
        val f = incoming.firstOrNull() ?: return
        if (f.name.lowercase().endsWith(".pdf")) {
            attachError = "PDF files can't be attached to a ticket yet — please attach a screenshot (PNG or JPG) instead."
        } else {
            attachError = null
            encodeShot(f)
        }
    }
    FileDropCollector(TICKET_ATTACH_SPEC, current = { emptyList() }) { incoming, err -> onAttach(incoming, err) }

    fun submit() {
        val cat = selectedCat
        catError = cat == null
        subjectError = subject.isBlank()
        descError = description.isBlank()
        if (cat == null || subjectError || descError || busy) return
        busy = true
        val fullMessage = subject.trim() + "\n\n" + description.trim()
        scope.launch {
            when (val r = EarnApi.createTicket(cat.key, fullMessage, shotB64)) {
                is qrmaster.web.data.SimpleOutcome.Ok -> {
                    state.toast("Ticket sent — we will reply here")
                    selectedCat = null; subject = ""; description = ""
                    shotB64 = null; shotName = ""; attachError = null
                    reloadTickets()
                }
                is qrmaster.web.data.SimpleOutcome.Rejected -> state.toast(r.message)
            }
            busy = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item { SupportBanner() }
        item {
            SupportCardsRow(
                onFaq = { showFaq = true },
                onGuides = { showGuides = true },
                onContact = { openExternal("https://wa.me/917048234115?text=Hi%20Kitnook%20Support%2C%20I%20need%20help%20with%20the%20app") }
            )
        }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFEFE9FD)),
                            contentAlignment = Alignment.Center
                        ) { QrIcon(IconKind.Ticket, PurplePrimary, size = 20.dp) }
                        Spacer(Modifier.width(11.dp))
                        Text("Create Support Ticket", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NavyText)
                    }
                    if (state.user == null) {
                        Text("Login to contact support.", fontSize = 13.5.sp, color = NavyText)
                        Button(
                            onClick = { state.navigate(Screen.Login) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                        ) { Text("Login") }
                    } else {
                        BoxWithConstraints(Modifier.fillMaxWidth()) {
                            if (maxWidth >= 680.dp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    Box(Modifier.weight(1f)) {
                                        SupportCategoryField(selectedCat, catError) { selectedCat = it; catError = false }
                                    }
                                    Box(Modifier.weight(1f)) {
                                        SupportSubjectField(subject, subjectError) { subject = it; subjectError = false }
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    SupportCategoryField(selectedCat, catError) { selectedCat = it; catError = false }
                                    SupportSubjectField(subject, subjectError) { subject = it; subjectError = false }
                                }
                            }
                        }
                        Column(Modifier.fillMaxWidth()) {
                            SupportFieldLabel("Describe Your Issue", required = true)
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it.take(500); descError = false },
                                placeholder = { Text("Write your issue or query in detail...", color = MutedText) },
                                isError = descError,
                                minLines = 4, maxLines = 6,
                                shape = RoundedCornerShape(10.dp),
                                colors = supportFieldColors(),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                if (descError) {
                                    Text("Please describe your issue.", fontSize = 11.5.sp, color = Color(0xFFDC2626), modifier = Modifier.weight(1f))
                                } else {
                                    Spacer(Modifier.weight(1f))
                                }
                                Text("${description.length} / 500", fontSize = 11.5.sp, color = MutedText)
                            }
                        }
                        Column(Modifier.fillMaxWidth()) {
                            Text("Attachment (Optional)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NavyText)
                            Spacer(Modifier.height(6.dp))
                            SupportAttachZone(
                                fileName = shotName,
                                onClick = {
                                    launchPick(scope, TICKET_ATTACH_SPEC, false, current = { emptyList() }) { incoming, err ->
                                        onAttach(incoming, err)
                                    }
                                },
                                onRemove = { shotB64 = null; shotName = "" }
                            )
                            if (attachError != null) {
                                Text(attachError!!, fontSize = 11.5.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(top = 5.dp))
                            }
                        }
                        Button(
                            onClick = { submit() },
                            enabled = !busy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(9.dp))
                                Text("Sending...", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            } else {
                                QrIcon(IconKind.Send, Color.White, size = 17.dp)
                                Spacer(Modifier.width(9.dp))
                                Text("Send Ticket", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
        if (state.user != null && tickets.isNotEmpty()) {
            item { Text("My Tickets", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyText) }
            items(tickets) { t ->
                WhiteCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.padding(horizontal = 15.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${supportCategoryLabel(t.category)} — ${ticketSubjectPreview(t)}", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText, maxLines = 1)
                            Text(fmtDateTime(t.createdAtMs), fontSize = 11.5.sp, color = MutedText)
                        }
                        Box(
                            Modifier.clip(RoundedCornerShape(9.dp))
                                .background(if (t.status == "closed") Color(0xFFE5E7EB) else Color(0xFFDCFCE7))
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        ) {
                            Text(
                                t.status.replaceFirstChar { it.uppercase() },
                                fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                                color = if (t.status == "closed") Color(0xFF6B7280) else Color(0xFF15803D)
                            )
                        }
                        Spacer(Modifier.width(9.dp))
                        OutlinedButton(
                            onClick = {
                                openTicket = t
                                scope.launch { replies = try { EarnApi.ticketReplies(t.id) } catch (e: Exception) { emptyList() } }
                            },
                            shape = RoundedCornerShape(9.dp)
                        ) { Text("View", fontSize = 12.sp) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }

    if (showFaq) {
        AlertDialog(
            onDismissRequest = { showFaq = false },
            title = { Text("FAQs") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SUPPORT_FAQS.forEach { (q, a) ->
                        Column {
                            Text(q, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
                            Text(a, fontSize = 12.5.sp, color = MutedText)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFaq = false }) { Text("Close") } }
        )
    }

    if (showGuides) {
        AlertDialog(
            onDismissRequest = { showGuides = false },
            title = { Text("Guides") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SUPPORT_GUIDES.forEach { (q, a) ->
                        Column {
                            Text(q, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
                            Text(a, fontSize = 12.5.sp, color = MutedText)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showGuides = false }) { Text("Close") } }
        )
    }

    val t = openTicket
    if (t != null) {
        AlertDialog(
            onDismissRequest = { openTicket = null },
            title = { Text("${supportCategoryLabel(t.category)} Ticket") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(t.message, fontSize = 13.sp)
                    Text("Status: ${t.status}", fontSize = 12.sp, color = MutedText)
                    replies.forEach { r ->
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                                .background(if (r.from == "admin") Color(0xFFF0EAFE) else Color(0xFFF7F6FC))
                                .padding(10.dp)
                        ) {
                            Text(if (r.from == "admin") "Support Team" else "You", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                            Text(r.text, fontSize = 12.5.sp, color = NavyText)
                            Text(fmtDateTime(r.createdAtMs), fontSize = 10.5.sp, color = MutedText)
                        }
                    }
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text("Write a reply") },
                        minLines = 2, maxLines = 4,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (replyText.isBlank() || replyBusy) return@TextButton
                        replyBusy = true
                        scope.launch {
                            when (val r = EarnApi.addTicketReply(t.id, replyText)) {
                                is qrmaster.web.data.SimpleOutcome.Ok -> {
                                    replyText = ""
                                    replies = try { EarnApi.ticketReplies(t.id) } catch (e: Exception) { replies }
                                    reloadTickets()
                                }
                                is qrmaster.web.data.SimpleOutcome.Rejected -> state.toast(r.message)
                            }
                            replyBusy = false
                        }
                    },
                    enabled = replyText.isNotBlank() && !replyBusy
                ) { Text("Send Reply") }
            },
            dismissButton = { TextButton(onClick = { openTicket = null }) { Text("Close") } }
        )
    }
}

// ── Help & Support building blocks ─────────────────────────────────────────

@Composable
private fun SupportBanner() {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6D28D9), Color(0xFF5B21B6))))
            .padding(horizontal = 26.dp, vertical = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Help & Support", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Get help, report an issue, or contact our support team.",
                    fontSize = 13.sp, color = Color.White.copy(alpha = 0.88f)
                )
            }
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(118.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
                Box(Modifier.size(88.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)))
                QrIcon(IconKind.Headset, Color.White, size = 72.dp)
            }
        }
    }
}

@Composable
private fun SupportCardsRow(onFaq: () -> Unit, onGuides: () -> Unit, onContact: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 720.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SupportInfoCard(Modifier.weight(1f), IconKind.Help, Color(0xFF7C3AED), Color(0xFFEFE9FD), "FAQs", "Find quick answers", onFaq)
                SupportInfoCard(Modifier.weight(1f), IconKind.FileText, Color(0xFF6D28D9), Color(0xFFEFE9FD), "Guides", "How to use features", onGuides)
                SupportInfoCard(Modifier.weight(1f), IconKind.Chat, Color(0xFF16A34A), Color(0xFFDFF3E4), "Contact Us", "Chat on WhatsApp", onContact)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SupportInfoCard(Modifier.fillMaxWidth(), IconKind.Help, Color(0xFF7C3AED), Color(0xFFEFE9FD), "FAQs", "Find quick answers", onFaq)
                SupportInfoCard(Modifier.fillMaxWidth(), IconKind.FileText, Color(0xFF6D28D9), Color(0xFFEFE9FD), "Guides", "How to use features", onGuides)
                SupportInfoCard(Modifier.fillMaxWidth(), IconKind.Chat, Color(0xFF16A34A), Color(0xFFDFF3E4), "Contact Us", "Chat on WhatsApp", onContact)
            }
        }
    }
}

@Composable
private fun SupportInfoCard(
    modifier: Modifier, icon: IconKind, tint: Color, bg: Color,
    title: String, subtitle: String, onClick: () -> Unit
) {
    WhiteCard(modifier.clickable { onClick() }) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(bg),
                contentAlignment = Alignment.Center
            ) { QrIcon(icon, tint, size = 22.dp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
                Text(subtitle, fontSize = 12.sp, color = MutedText)
            }
            QrIcon(IconKind.Right, MutedText, size = 15.dp)
        }
    }
}

@Composable
private fun SupportFieldLabel(text: String, required: Boolean) {
    Row {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NavyText)
        if (required) Text(" *", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFFDC2626))
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun supportFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PurplePrimary,
    unfocusedBorderColor = Color(0xFFD9D6E8),
    errorBorderColor = Color(0xFFDC2626),
    focusedContainerColor = Color.White,
    unfocusedContainerColor = Color.White,
    cursorColor = PurplePrimary
)

@Composable
private fun SupportSubjectField(value: String, error: Boolean, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SupportFieldLabel("Subject", required = true)
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it.take(100)) },
            placeholder = { Text("Enter ticket subject", color = MutedText) },
            singleLine = true,
            isError = error,
            shape = RoundedCornerShape(10.dp),
            colors = supportFieldColors(),
            modifier = Modifier.fillMaxWidth()
        )
        if (error) {
            Text("Please enter a subject.", fontSize = 11.5.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SupportCategoryField(
    selected: SupportCategoryUi?,
    error: Boolean,
    onSelect: (SupportCategoryUi) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf(0) }
    var fieldSize by remember { mutableStateOf(IntSize.Zero) }
    val focusRequester = remember { FocusRequester() }
    val density = LocalDensity.current

    fun selectedIndex(): Int = selected?.let { SUPPORT_CATEGORY_UI.indexOf(it) } ?: 0
    fun choose(i: Int) {
        onSelect(SUPPORT_CATEGORY_UI[i])
        open = false
    }
    LaunchedEffect(open) { if (open) focusRequester.requestFocus() }

    Column(Modifier.fillMaxWidth()) {
        SupportFieldLabel("Category", required = true)
        Box(Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().height(50.dp)
                    .onSizeChanged { fieldSize = it }
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
                    .border(
                        when {
                            open -> 1.6.dp
                            error -> 1.4.dp
                            else -> 1.2.dp
                        },
                        when {
                            open -> PurplePrimary
                            error -> Color(0xFFDC2626)
                            else -> Color(0xFFD9D6E8)
                        },
                        RoundedCornerShape(10.dp)
                    )
                    .focusRequester(focusRequester)
                    .focusable()
                    .onKeyEvent { ev ->
                        if (ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (ev.key) {
                            Key.DirectionDown -> {
                                if (!open) {
                                    open = true; highlight = selectedIndex()
                                } else {
                                    highlight = (highlight + 1).coerceAtMost(SUPPORT_CATEGORY_UI.lastIndex)
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (open) highlight = (highlight - 1).coerceAtLeast(0)
                                true
                            }
                            Key.Enter, Key.NumPadEnter, Key.Spacebar -> {
                                if (open) choose(highlight) else {
                                    open = true; highlight = selectedIndex()
                                }
                                true
                            }
                            Key.Escape -> {
                                open = false
                                true
                            }
                            else -> false
                        }
                    }
                    .clickable {
                        open = !open
                        if (open) highlight = selectedIndex()
                    }
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected != null) {
                    Box(
                        Modifier.size(28.dp).clip(CircleShape).background(selected.bg),
                        contentAlignment = Alignment.Center
                    ) { QrIcon(selected.icon, selected.tint, size = 16.dp) }
                    Spacer(Modifier.width(10.dp))
                    Text(selected.label, fontSize = 14.sp, color = NavyText, modifier = Modifier.weight(1f), maxLines = 1)
                } else {
                    Text("Select Category", fontSize = 14.sp, color = MutedText, modifier = Modifier.weight(1f), maxLines = 1)
                }
                Text("▾", color = MutedText, fontSize = 15.sp)
            }
            if (open) {
                Popup(
                    popupPositionProvider = object : PopupPositionProvider {
                        override fun calculatePosition(
                            anchorBounds: IntRect,
                            windowSize: IntSize,
                            layoutDirection: LayoutDirection,
                            popupContentSize: IntSize
                        ): IntOffset {
                            val below = anchorBounds.bottom + 6
                            val y = if (below + popupContentSize.height <= windowSize.height) below
                            else maxOf(0, anchorBounds.top - popupContentSize.height - 6)
                            return IntOffset(anchorBounds.left, y)
                        }
                    },
                    onDismissRequest = { open = false },
                    properties = PopupProperties(focusable = false)
                ) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier.width(with(density) { fieldSize.width.toDp() })
                    ) {
                        LazyColumn(Modifier.heightIn(max = 440.dp).padding(vertical = 6.dp)) {
                            itemsIndexed(SUPPORT_CATEGORY_UI) { i, cat ->
                                Row(
                                    Modifier.fillMaxWidth()
                                        .background(
                                            when {
                                                i == highlight -> Color(0xFFEFE9FD)
                                                selected == cat -> Color(0xFFF6F2FF)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .onPointerEvent(PointerEventType.Enter) { highlight = i }
                                        .clickable { choose(i) }
                                        .padding(horizontal = 12.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(28.dp).clip(CircleShape).background(cat.bg),
                                        contentAlignment = Alignment.Center
                                    ) { QrIcon(cat.icon, cat.tint, size = 15.dp) }
                                    Spacer(Modifier.width(11.dp))
                                    Text(
                                        cat.label, fontSize = 14.sp, color = NavyText,
                                        fontWeight = if (selected == cat) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f), maxLines = 1
                                    )
                                    if (selected == cat) QrIcon(IconKind.Check, PurplePrimary, size = 15.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (error) {
            Text("Please select a category.", fontSize = 11.5.sp, color = Color(0xFFDC2626), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SupportAttachZone(fileName: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier.fillMaxWidth().height(104.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFFBFAFF))
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFFB9A8F5),
                        style = Stroke(width = 1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx())
                    )
                }
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFEFE9FD)),
                    contentAlignment = Alignment.Center
                ) { QrIcon(IconKind.Upload, PurplePrimary, size = 21.dp) }
                Spacer(Modifier.height(7.dp))
                Text("Upload Screenshot or File", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
                Text("PNG, JPG, JPEG, PDF (Max 5 MB)", fontSize = 11.5.sp, color = MutedText)
            }
        }
        if (fileName.isNotBlank()) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fileName.take(34), fontSize = 12.5.sp, color = NavyText, maxLines = 1)
                TextButton(onClick = onRemove) { Text("Remove", fontSize = 12.sp) }
            }
        }
    }
}
