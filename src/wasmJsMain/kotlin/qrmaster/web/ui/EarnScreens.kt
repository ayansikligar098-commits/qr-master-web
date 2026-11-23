package qrmaster.web.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun SupportScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf(SUPPORT_CATEGORIES[0].first) }
    var catIndex by remember { mutableStateOf(0) }
    var message by remember { mutableStateOf("") }
    var shotB64 by remember { mutableStateOf<String?>(null) }
    var shotName by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
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

    ToolPage(IconKind.Help, "Help & Support", "Send a support ticket — replies appear here in My Tickets.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    if (state.user == null) {
                        Text("Login to contact support.", fontSize = 13.5.sp, color = NavyText)
                        Button(
                            onClick = { state.navigate(Screen.Login) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                        ) { Text("Login") }
                    } else {
                        LabeledDropdown("Category", SUPPORT_CATEGORIES.map { it.second }, catIndex, {
                            catIndex = it; category = SUPPORT_CATEGORIES[it].first
                        }, Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = message,
                            onValueChange = { message = it },
                            label = { Text("Describe your issue") },
                            minLines = 3, maxLines = 6,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    launchPick(scope, IMAGE_SPEC, false, current = { emptyList() }) { incoming, err ->
                                        if (err != null) state.toast(err)
                                        incoming.firstOrNull()?.let { encodeShot(it) }
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Attach Screenshot (optional)", fontSize = 12.5.sp) }
                            if (shotName.isNotBlank()) {
                                Text(shotName.take(22), fontSize = 12.sp, color = MutedText, maxLines = 1)
                                TextButton(onClick = { shotB64 = null; shotName = "" }) { Text("Remove", fontSize = 12.sp) }
                            }
                        }
                        PrimaryToolButton("Send Ticket", enabled = message.isNotBlank(), busy = busy) {
                            busy = true
                            scope.launch {
                                when (val r = EarnApi.createTicket(category, message, shotB64)) {
                                    is qrmaster.web.data.SimpleOutcome.Ok -> {
                                        state.toast("Ticket sent — we will reply here")
                                        message = ""; shotB64 = null; shotName = ""
                                        reloadTickets()
                                    }
                                    is qrmaster.web.data.SimpleOutcome.Rejected -> state.toast(r.message)
                                }
                                busy = false
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
                            Text("${supportCategoryLabel(t.category)} — ${t.message.take(48)}", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText, maxLines = 1)
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
