package qrmaster.web.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import qrmaster.web.data.CoinWebConfig
import qrmaster.web.data.CoinWebState
import qrmaster.web.data.CoinsApi
import qrmaster.web.data.WebConfig
import qrmaster.web.data.coinErrorMessage
import qrmaster.web.data.coinPolicyLines
import qrmaster.web.data.coinRewardName
import qrmaster.web.data.loadCoinWebState
import qrmaster.web.data.loadWebConfig
import qrmaster.web.data.nowMillis

/**
 * Coins page (2026-11-28) — Android app ke CoinScreen ka web roop.
 * Balance, history, My Rewards, gift-code redeem aur gift-card
 * redeem (policy sheet ke saath) — sab kuchh wahi Worker endpoints
 * aur wahi users/{uid} balance jo app istemal karta hai.
 */
@Composable
fun CoinsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var cfg by remember { mutableStateOf<WebConfig?>(null) }
    var coinState by remember { mutableStateOf<CoinWebState?>(null) }
    var loading by remember { mutableStateOf(false) }
    var giftCode by remember { mutableStateOf("") }
    var giftBusy by remember { mutableStateOf(false) }
    var giftMessage by remember { mutableStateOf<String?>(null) }
    var giftSuccess by remember { mutableStateOf(false) }
    var policyFor by remember { mutableStateOf<String?>(null) }
    var redeemBusy by remember { mutableStateOf(false) }
    var claimBusy by remember { mutableStateOf(false) }
    var giftPopupDismissed by remember { mutableStateOf(false) }

    suspend fun refresh() {
        loading = true
        coinState = try { loadCoinWebState() } catch (e: Exception) { null }
        loading = false
        state.refreshCoinBalance()
    }
    LaunchedEffect(state.user) {
        cfg = try { loadWebConfig(null) } catch (e: Exception) { WebConfig() }
        if (state.user != null) refresh()
    }

    val coinCfg = cfg?.coins ?: CoinWebConfig()
    val cs = coinState

    ToolPage(IconKind.Star, "Coins", "Earn coins from spins and rewards — redeem them for Google Play or Amazon Pay gift cards.") {
        if (state.user == null) {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Login to see your coins.", fontSize = 13.5.sp, color = NavyText)
                        Button(
                            onClick = { state.navigate(Screen.Login) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                        ) { Text("Login") }
                    }
                }
            }
        } else if (cfg != null && !coinCfg.enabled) {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Coins are paused right now. Please check again later.",
                        fontSize = 13.5.sp, color = NavyText,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            // ── Balance ─────────────────────────────────────────────
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoinBadge(30.dp)
                            Spacer(Modifier.width(11.dp))
                            Column {
                                Text("Coin Balance", fontSize = 12.5.sp, color = MutedText)
                                Text(
                                    if (cs == null) "…" else "${cs.balance}",
                                    fontSize = 24.sp, fontWeight = FontWeight.Bold, color = NavyText
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { refresh() } }) { Text("Refresh") }
                        }
                        if (cs != null) {
                            val target = minOf(coinCfg.playRedeemCoins, coinCfg.amazonRedeemCoins)
                            if (target > 0) {
                                LinearProgressIndicator(
                                    progress = { (cs.balance.toFloat() / target).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = PurplePrimary
                                )
                                Text(
                                    if (cs.balance >= target) "You can redeem a reward now!"
                                    else "${target - cs.balance} more coins for a ${coinRewardName(CoinWebConfig.TYPE_PLAY)} / Amazon Pay Gift Card",
                                    fontSize = 12.sp, color = MutedText
                                )
                            }
                            if (cs.welcomeReceived) {
                                Text(
                                    "Welcome bonus of ${coinCfg.welcomeCoins} coins received ✓",
                                    fontSize = 12.5.sp, color = Color(0xFF059669)
                                )
                            }
                            Text(
                                if (cs.lifetimeAds >= coinCfg.redeemUnlockAds)
                                    "Redeem unlocked ✓ (${cs.lifetimeAds}/${coinCfg.redeemUnlockAds} rewarded ads)"
                                else
                                    "Redeem unlocks after ${coinCfg.redeemUnlockAds} rewarded ads in the Kitnook Android app — ${cs.lifetimeAds}/${coinCfg.redeemUnlockAds} done. Coins and balance are shared between the app and this website.",
                                fontSize = 12.sp, color = MutedText
                            )
                        } else if (loading) {
                            Text("Loading your coins…", fontSize = 12.5.sp, color = MutedText)
                        }
                    }
                }
            }

            // ── Redeem cards ────────────────────────────────────────
            item {
                CoinRedeemCard(
                    title = "Google Play Gift Card",
                    type = CoinWebConfig.TYPE_PLAY,
                    cfg = coinCfg,
                    cs = cs,
                    busy = redeemBusy,
                    onRedeem = { policyFor = CoinWebConfig.TYPE_PLAY }
                )
            }
            item {
                CoinRedeemCard(
                    title = "Amazon Pay Gift Card",
                    type = CoinWebConfig.TYPE_AMAZON,
                    cfg = coinCfg,
                    cs = cs,
                    busy = redeemBusy,
                    onRedeem = { policyFor = CoinWebConfig.TYPE_AMAZON }
                )
            }

            // ── My Rewards ──────────────────────────────────────────
            if (cs != null && cs.rewards.isNotEmpty()) {
                item {
                    WhiteCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                            Text("My Rewards", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyText)
                            for (rw in cs.rewards) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(coinRewardName(rw.type), fontSize = 13.5.sp, color = NavyText)
                                    when (rw.status) {
                                        "approved" -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    rw.code,
                                                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF059669),
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Text(
                                                    "Copy",
                                                    fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
                                                    color = PurplePrimary,
                                                    modifier = Modifier.clickable {
                                                        try { shareText(rw.code) } catch (e: Exception) { }
                                                        state.toast("Code copied — redeem it in the ${if (rw.type == CoinWebConfig.TYPE_AMAZON) "Amazon Pay" else "Google Play Store"} app.")
                                                    }
                                                )
                                            }
                                            Text(
                                                "Copy this code and redeem it in the ${if (rw.type == CoinWebConfig.TYPE_AMAZON) "Amazon Pay" else "Google Play Store"} app.",
                                                fontSize = 12.sp, color = MutedText
                                            )
                                        }
                                        "rejected" -> Text(
                                            "Rejected — ${rw.coins} coins refunded." +
                                                (if (rw.reason.isNotBlank()) " Reason: ${rw.reason}" else ""),
                                            fontSize = 12.5.sp, color = Color(0xFFDC2626)
                                        )
                                        else -> Text(
                                            "Waiting for approval — your reward will be delivered within 24 hours.",
                                            fontSize = 12.5.sp, color = MutedText
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Gift code ───────────────────────────────────────────
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Have a Gift Code?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyText)
                        OutlinedTextField(
                            value = giftCode,
                            onValueChange = { giftCode = it.uppercase() },
                            label = { Text("Gift code") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        PrimaryToolButton("Redeem Gift Code", enabled = giftCode.isNotBlank(), busy = giftBusy) {
                            giftBusy = true; giftMessage = null
                            scope.launch {
                                val r = CoinsApi.giftRedeem(giftCode.trim())
                                giftBusy = false
                                if (r.ok) {
                                    giftSuccess = true
                                    giftMessage = "🎉 Gift code redeemed — coins added to your balance!"
                                    giftCode = ""
                                    refresh()
                                } else {
                                    giftSuccess = false
                                    giftMessage = coinErrorMessage(r.errorCode, coinCfg)
                                }
                            }
                        }
                        if (giftMessage != null) {
                            Text(
                                giftMessage!!, fontSize = 13.sp,
                                color = if (giftSuccess) Color(0xFF059669) else Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }

            // ── History ─────────────────────────────────────────────
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Coin History", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyText)
                        when {
                            cs == null -> Text("Loading…", fontSize = 12.5.sp, color = MutedText)
                            cs.history.isEmpty() -> Text(
                                "No coin activity yet. Spin the wheel — the Coins slot pays 1–2 coins!",
                                fontSize = 12.5.sp, color = MutedText
                            )
                            else -> for (t in cs.history) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.label(), fontSize = 13.sp, color = NavyText, modifier = Modifier.weight(1f))
                                    Text(fmtDateTime(t.createdAtMs), fontSize = 11.sp, color = MutedText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Pending gift popup (app parity) ─────────────────────────────
    val pendingGift = cs?.pendingGifts?.firstOrNull()
    if (state.user != null && coinCfg.enabled && pendingGift != null && !giftPopupDismissed) {
        AlertDialog(
            onDismissRequest = { giftPopupDismissed = true },
            title = { Text("🎁 You have a gift!") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("You received: ${pendingGift.label()}", fontWeight = FontWeight.SemiBold)
                    if (pendingGift.note.isNotBlank()) Text(pendingGift.note, fontSize = 12.5.sp, color = MutedText)
                    if (pendingGift.code.isNotBlank()) Text(pendingGift.code, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        claimBusy = true
                        scope.launch {
                            val r = CoinsApi.giftClaim(pendingGift.id)
                            claimBusy = false
                            if (r.ok) {
                                state.toast("Gift claimed!")
                                giftPopupDismissed = true
                                refresh()
                            } else {
                                state.toast(coinErrorMessage(r.errorCode, coinCfg))
                            }
                        }
                    },
                    enabled = !claimBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) { Text("Claim") }
            },
            dismissButton = {
                TextButton(onClick = { giftPopupDismissed = true }) { Text("Later") }
            }
        )
    }

    // ── Redeem Policy sheet — scroll-to-bottom gate (app parity) ────
    if (policyFor != null) {
        val type = policyFor!!
        val scrollState = rememberScrollState()
        var atBottom by remember(type) { mutableStateOf(false) }
        LaunchedEffect(scrollState.value, scrollState.maxValue) {
            if (!scrollState.canScrollForward) atBottom = true
        }
        AlertDialog(
            onDismissRequest = { if (!redeemBusy) policyFor = null },
            title = { Text("Redeem Policy") },
            text = {
                Column(
                    Modifier.heightIn(max = 340.dp).verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    for (line in coinPolicyLines(coinCfg)) {
                        Text("• $line", fontSize = 13.sp, color = NavyText)
                    }
                    Text(
                        "Scroll to the bottom to agree.",
                        fontSize = 11.5.sp, color = MutedText
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        redeemBusy = true
                        scope.launch {
                            val r = CoinsApi.redeem(type, nowMillis())
                            redeemBusy = false
                            policyFor = null
                            if (r.ok) {
                                state.toast("Redeem request sent! Your reward will be delivered within 24 hours of approval.")
                                refresh()
                            } else {
                                state.toast(coinErrorMessage(r.errorCode, coinCfg))
                            }
                        }
                    },
                    enabled = atBottom && !redeemBusy,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) {
                    if (redeemBusy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(17.dp))
                    else Text("Agree & Redeem")
                }
            },
            dismissButton = {
                TextButton(onClick = { policyFor = null }, enabled = !redeemBusy) { Text("Cancel") }
            }
        )
    }
}

/** Ek redeem card — haal sandesh app ke RedeemCard jaisa. */
@Composable
private fun CoinRedeemCard(
    title: String,
    type: String,
    cfg: CoinWebConfig,
    cs: CoinWebState?,
    busy: Boolean,
    onRedeem: () -> Unit
) {
    val cost = cfg.costFor(type)
    val balance = cs?.balance ?: 0L
    val locked = cs != null && cs.lifetimeAds < cfg.redeemUnlockAds
    val pending = cs?.pendingRedeem != null
    val afford = cs != null && balance >= cost
    WhiteCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyText)
            Text("$cost Coins", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
            Text(
                when {
                    cs == null -> "Loading…"
                    pending -> "You already have a request waiting for approval."
                    locked -> "Locked — watch ${cfg.redeemUnlockAds - cs.lifetimeAds} more rewarded ads in the Android app to unlock (${cs.lifetimeAds}/${cfg.redeemUnlockAds})."
                    !afford -> "You need ${cost - balance} more coins for this reward."
                    else -> "Delivered within 24 hours of your request, after a quick approval."
                },
                fontSize = 12.5.sp, color = MutedText
            )
            Spacer(Modifier.height(3.dp))
            PrimaryToolButton(
                "Redeem",
                enabled = cs != null && !locked && !pending && afford,
                busy = busy,
                onClick = onRedeem
            )
        }
    }
}
