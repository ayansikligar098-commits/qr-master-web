package qrmaster.web.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import qrmaster.web.data.FileShareApi
import qrmaster.web.data.FirebaseApi
import qrmaster.web.data.fmtBytes
import qrmaster.web.data.nowMillis
import qrmaster.web.qr.QrCode
import qrmaster.web.qr.QrEcc

/* ------------------------------------------------------------------ */
/* File Share — upload tool (login required) + My Shares               */
/* ------------------------------------------------------------------ */

private data class FsPicked(val ref: Int, val name: String, val size: Long)

private data class FsUpStatus(
    val state: String = "idle",
    val pct: Double = 0.0,
    val uploaded: Long = 0,
    val total: Long = 0,
    val speed: Double = 0.0,
    val eta: Double = -1.0,
    val partsDone: Int = 0,
    val partsTotal: Int = 0,
    val shareId: String = "",
    val expiresAt: Long = 0,
    val tier: String = "",
    val maxBytes: Long = 0,
    val error: String = "",
    val fileName: String = ""
)

private val fsJson = Json { ignoreUnknownKeys = true }

private fun kotlinx.serialization.json.JsonObject.s(key: String): String =
    this[key]?.jsonPrimitive?.content ?: ""

private fun kotlinx.serialization.json.JsonObject.l(key: String): Long =
    this[key]?.jsonPrimitive?.content?.toLongOrNull()
        ?: this[key]?.jsonPrimitive?.content?.toDoubleOrNull()?.toLong() ?: 0L

private fun kotlinx.serialization.json.JsonObject.d(key: String): Double =
    this[key]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0

private fun parseFsStatus(raw: String): FsUpStatus = try {
    val o = fsJson.parseToJsonElement(raw).jsonObject
    FsUpStatus(
        state = o.s("state"), pct = o.d("pct"), uploaded = o.l("uploaded"),
        total = o.l("total"), speed = o.d("speed"), eta = o.d("eta"),
        partsDone = o.l("partsDone").toInt(), partsTotal = o.l("partsTotal").toInt(),
        shareId = o.s("shareId"), expiresAt = o.l("expiresAt"),
        tier = o.s("tier"), maxBytes = o.l("maxBytes"), error = o.s("error"),
        fileName = o.s("fileName")
    )
} catch (e: Exception) { FsUpStatus() }

private fun parseFsPicked(raw: String): FsPicked? = try {
    val o = fsJson.parseToJsonElement(raw).jsonObject
    FsPicked(o.l("ref").toInt(), o.s("name"), o.l("size"))
} catch (e: Exception) { null }

private fun fsShareLink(id: String): String =
    window.location.protocol + "//" + window.location.host + "/s/" + id

private fun fmtEta(sec: Double): String = when {
    sec < 0 -> "—"
    sec < 60 -> "${sec.toInt() + 1}s left"
    sec < 3600 -> "${(sec / 60).toInt() + 1} min left"
    else -> "${(sec / 3600).toInt()} h ${(sec % 3600 / 60).toInt()} min left"
}

private fun friendlyFsError(st: FsUpStatus): String = when {
    st.error == "file_too_large" ->
        "This file is over your plan's limit (${fmtBytes(st.maxBytes)} per file). " +
            "Free: up to 5 GB per file · Premium: up to 10 GB per file."
    st.error == "daily_limit" -> "You've reached today's sharing limit. Please try again tomorrow."
    st.error == "server_not_configured" -> "File Share is being set up right now. Please try again later."
    st.error == "file_lost" -> "The file selection was lost. Please pick the file again."
    st.error.isNotBlank() && !st.error.contains("_") && st.error.length > 12 -> st.error
    else -> "Upload failed. Check your connection and try again."
}

@Composable
fun FileShareScreen(state: AppState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp)
    ) {
        PageHeader(IconKind.Upload, "File Share", "Send any file with a link + QR — free up to 5 GB, Premium up to 10 GB")
        Spacer(Modifier.height(16.dp))
        if (state.user == null) {
            WhiteCard(Modifier.fillMaxWidth().widthIn(max = 820.dp)) {
                Column(
                    Modifier.padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    QrIcon(IconKind.Lock, PurplePrimary, size = 34.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Log in to share files",
                        fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NavyText
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "An account keeps your shares in one place — see them under My Shares, " +
                            "and delete them any time. Anyone with your link can download without logging in.",
                        fontSize = 12.5.sp, color = MutedText, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { state.navigate(Screen.Login) },
                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Log in / Sign up") }
                }
            }
        } else {
            FileShareContent(state)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun FileShareContent(state: AppState) {
    val scope = rememberCoroutineScope()
    var pendingPick by remember { mutableStateOf<Int?>(null) }
    var picked by remember { mutableStateOf<FsPicked?>(null) }
    var resumable by remember { mutableStateOf(false) }
    var resumableText by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf("pick") } // pick | uploading | done
    var status by remember { mutableStateOf(FsUpStatus()) }
    var pickError by remember { mutableStateOf("") }
    var dragging by remember { mutableStateOf(false) }
    var expiryDays by remember { mutableStateOf(7) }
    var password by remember { mutableStateOf("") }
    var doneDeleteConfirm by remember { mutableStateOf(false) }

    var mine by remember { mutableStateOf<List<FileShareApi.MyShare>?>(null) }
    // The share this browser just uploaded. KV lists are eventually
    // consistent (a fresh record can take ~a minute to appear in
    // /api/shares/mine), so the just-finished share is shown from local
    // state until the server list catches up — otherwise the uploader
    // sees "No shares yet" right after a successful upload.
    var localDone by remember { mutableStateOf<FileShareApi.MyShare?>(null) }
    var mineLoading by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<FileShareApi.MyShare?>(null) }
    var qrForId by remember { mutableStateOf<String?>(null) }
    var qrCode by remember { mutableStateOf<QrCode?>(null) }

    LaunchedEffect(qrForId) {
        qrCode = qrForId?.let {
            try { QrCode.encodeText(fsShareLink(it), QrEcc.MEDIUM) } catch (e: Exception) { null }
        }
    }

    suspend fun loadMine() {
        mineLoading = true
        mine = FileShareApi.fetchMine()
        mineLoading = false
    }

    fun onPicked(p: FsPicked) {
        picked = p
        pickError = ""
        resumable = false
        resumableText = ""
        try {
            val o = fsJson.parseToJsonElement(fsResumable(p.ref)).jsonObject
            if (o.s("found") == "true" || o["found"]?.jsonPrimitive?.content == "true") {
                resumable = true
                resumableText = "An earlier upload of this same file was interrupted " +
                    "(${o.l("partsDone")} of ${o.l("partCount")} parts done)."
            }
        } catch (e: Exception) { }
    }

    fun startUpload(mode: String) {
        val p = picked ?: return
        phase = "uploading"
        scope.launch {
            val token = FirebaseApi.validToken() ?: ""
            fsStartUpload(p.ref, mode, token, expiryDays, password)
        }
    }

    DisposableEffect(Unit) {
        fsSetDrop(true)
        onDispose { fsSetDrop(false) }
    }
    LaunchedEffect(Unit) {
        loadMine()
        // The JS engine keeps uploading while the user visits other
        // screens — re-sync the phase with the engine on entry.
        try {
            val st0 = parseFsStatus(fsUploadStatus())
            if (st0.state == "uploading" || st0.state == "paused" ||
                st0.state == "finishing" || st0.state == "creating"
            ) {
                status = st0
                phase = "uploading"
            } else if (st0.state == "done" && st0.shareId.isNotBlank()) {
                status = st0
                phase = "done"
            }
        } catch (e: Exception) { }
        while (true) {
            try {
                val droppedRaw = fsPollDrop()
                if (droppedRaw.isNotBlank() && phase == "pick") {
                    parseFsPicked(droppedRaw)?.let { onPicked(it) }
                }
                dragging = fsDragging()
                val pp = pendingPick
                if (pp != null) {
                    val r = fsPollPick(pp)
                    if (r == "CANCELLED") pendingPick = null
                    else if (r.isNotBlank()) {
                        pendingPick = null
                        parseFsPicked(r)?.let { onPicked(it) }
                    }
                }
                val st = parseFsStatus(fsUploadStatus())
                status = st
                if (phase == "uploading") {
                    when (st.state) {
                        "done" -> {
                            phase = "done"
                            if (st.shareId.isNotBlank()) {
                                val nowMs = nowMillis()
                                localDone = FileShareApi.MyShare(
                                    id = st.shareId,
                                    fileName = st.fileName.ifBlank { picked?.name ?: "File" },
                                    size = st.total,
                                    createdAt = nowMs,
                                    expiresAt = st.expiresAt,
                                    daysLeft = ((st.expiresAt - nowMs) / 86400000L).toInt().coerceAtLeast(0),
                                    state = "active",
                                    downloads = 0,
                                    hasPassword = password.isNotBlank(),
                                    tier = st.tier
                                )
                            }
                            loadMine()
                        }
                        "error" -> { phase = "pick"; pickError = friendlyFsError(st) }
                        "cancelled" -> { phase = "pick"; picked = null; resumable = false }
                    }
                }
            } catch (e: Exception) { }
            delay(300)
        }
    }

    Column(Modifier.fillMaxWidth().widthIn(max = 820.dp)) {
        WhiteCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                when (phase) {
                    "uploading" -> FsUploadingPane(
                        status = status,
                        fileName = picked?.name ?: status.fileName,
                        onPauseResume = {
                            if (status.state == "paused") fsResumeUpload() else fsPauseUpload()
                        },
                        onCancel = {
                            scope.launch {
                                val token = FirebaseApi.validToken() ?: ""
                                fsCancelUpload(token)
                            }
                        }
                    )
                    "done" -> FsDonePane(
                        state = state,
                        status = status,
                        onDelete = { doneDeleteConfirm = true },
                        onAnother = {
                            picked = null; password = ""; phase = "pick"
                        }
                    )
                    else -> {
                        // ---------- drop zone + big upload button ----------
                        val borderColor = if (dragging) PurplePrimary else Color(0xFFC9BDF0)
                        Box(
                            Modifier.fillMaxWidth().height(168.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (dragging) Color(0xFFF1EBFF) else Color(0xFFFBFAFF))
                                .drawBehind {
                                    drawRoundRect(
                                        color = borderColor,
                                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                                        style = Stroke(
                                            width = 2.dp.toPx(),
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 11f))
                                        )
                                    )
                                }
                                .clickable { if (pendingPick == null) pendingPick = fsPickFile() },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))
                                        .background(PurplePrimary),
                                    contentAlignment = Alignment.Center
                                ) { QrIcon(IconKind.Upload, Color.White, size = 27.dp) }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    if (dragging) "Drop the file to add it"
                                    else "Drag & drop a file here, or tap to choose",
                                    fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "All file types welcome — photos, videos, PDFs, anything",
                                    fontSize = 12.sp, color = MutedText
                                )
                                Text(
                                    "Free up to 5 GB · Premium up to 10 GB · the link expires automatically",
                                    fontSize = 12.sp, color = MutedText
                                )
                            }
                        }

                        val p = picked
                        if (p != null) {
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(42.dp).clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF0EAFE)),
                                    contentAlignment = Alignment.Center
                                ) { QrIcon(IconKind.FileText, PurplePrimary, size = 22.dp) }
                                Spacer(Modifier.width(11.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NavyText, maxLines = 2)
                                    Text(fmtBytes(p.size), fontSize = 12.sp, color = MutedText)
                                }
                                TextButton(onClick = {
                                    picked = null; resumable = false; pickError = ""
                                }) { Text("Remove", color = Color(0xFFC62F3B)) }
                            }
                            Spacer(Modifier.height(13.dp))
                            Text("Link valid for", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
                            Spacer(Modifier.height(7.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(1, 3, 7).forEach { d ->
                                    FilterChip(
                                        selected = expiryDays == d,
                                        onClick = { expiryDays = d },
                                        label = { Text(if (d == 1) "1 day" else "$d days", fontSize = 12.5.sp) },
                                        shape = RoundedCornerShape(20.dp),
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = PurplePrimary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it },
                                label = { Text("Password (optional)") },
                                placeholder = { Text("No password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (resumable) {
                                Spacer(Modifier.height(13.dp))
                                Text(resumableText, fontSize = 12.5.sp, color = Color(0xFF12805C))
                                Spacer(Modifier.height(9.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { startUpload("resume") },
                                        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).height(48.dp)
                                    ) { Text("Resume upload", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold) }
                                    OutlinedButton(
                                        onClick = {
                                            val pp = picked
                                            if (pp != null) {
                                                scope.launch {
                                                    val token = FirebaseApi.validToken() ?: ""
                                                    fsForgetSaved(pp.ref, token)
                                                }
                                            }
                                            resumable = false
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.height(48.dp)
                                    ) { Text("Start over") }
                                }
                            } else {
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = { startUpload("fresh") },
                                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                ) {
                                    QrIcon(IconKind.Upload, Color.White, size = 19.dp)
                                    Spacer(Modifier.width(9.dp))
                                    Text("Upload & get link", fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        if (pickError.isNotBlank()) {
                            Spacer(Modifier.height(11.dp))
                            Text(pickError, fontSize = 13.sp, color = Color(0xFFC62F3B))
                            if (pickError.contains("Premium")) {
                                TextButton(onClick = { state.navigate(Screen.Premium) }) {
                                    Text("See Premium plans", color = PurplePrimary, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
        }

        /* ---------------- My Shares ---------------- */
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("My Shares", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
            Spacer(Modifier.weight(1f))
            if (mineLoading) {
                CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = PurplePrimary)
            } else {
                TextButton(onClick = { scope.launch { loadMine() } }) {
                    Text("Refresh", color = PurplePrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                }
            }
        }
        val serverShares = mine
        val shares: List<FileShareApi.MyShare>? = when {
            serverShares == null -> localDone?.let { listOf(it) }
            localDone != null && serverShares.none { it.id == localDone!!.id } ->
                listOf(localDone!!) + serverShares
            else -> serverShares
        }
        when {
            shares == null -> Text("Loading your shares…", fontSize = 12.5.sp, color = MutedText)
            shares.isEmpty() -> Text(
                "No shares yet. Upload a file above and it will appear here.",
                fontSize = 12.5.sp, color = MutedText
            )
            else -> shares.forEach { s ->
                Spacer(Modifier.height(9.dp))
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.size(38.dp).clip(RoundedCornerShape(9.dp))
                                    .background(Color(0xFFF0EAFE)),
                                contentAlignment = Alignment.Center
                            ) { QrIcon(IconKind.FileText, PurplePrimary, size = 20.dp) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.fileName, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText, maxLines = 1)
                                Text(
                                    "${fmtBytes(s.size)} · ${fmtDateTime(s.createdAt)} · ${s.downloads} downloads" +
                                        if (s.hasPassword) " · password set" else "",
                                    fontSize = 11.5.sp, color = MutedText, maxLines = 1
                                )
                            }
                            val (chipBg, chipFg, chipText) = when (s.state) {
                                "active" -> Triple(Color(0xFFE5F6EF), Color(0xFF12805C), "Active · ${s.daysLeft}d left")
                                "expired" -> Triple(Color(0xFFF1F0F6), MutedText, "Expired")
                                else -> Triple(Color(0xFFFFF3E0), Color(0xFFB26A00), "Uploading")
                            }
                            Box(
                                Modifier.clip(RoundedCornerShape(8.dp)).background(chipBg)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) { Text(chipText, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = chipFg) }
                        }
                        if (s.state == "active") {
                            Spacer(Modifier.height(9.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        copyTextToClipboard(fsShareLink(s.id))
                                        state.toast("Link copied")
                                    },
                                    shape = RoundedCornerShape(9.dp),
                                    modifier = Modifier.height(38.dp)
                                ) { Text("Copy link", fontSize = 12.5.sp) }
                                OutlinedButton(
                                    onClick = { qrForId = if (qrForId == s.id) null else s.id },
                                    shape = RoundedCornerShape(9.dp),
                                    modifier = Modifier.height(38.dp)
                                ) { Text(if (qrForId == s.id) "Hide QR" else "QR", fontSize = 12.5.sp) }
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { deleteTarget = s }) {
                                    Text("Delete", color = Color(0xFFC62F3B), fontSize = 12.5.sp)
                                }
                            }
                            if (qrForId == s.id && qrCode != null) {
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    QrCanvas(qrCode!!, NavyText, Color.White, PngEncoder.QrStyle.SQUARE, null, Modifier.size(132.dp))
                                }
                            }
                        } else if (s.state == "uploading") {
                            Spacer(Modifier.height(6.dp))
                            Row {
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { deleteTarget = s }) {
                                    Text("Delete", color = Color(0xFFC62F3B), fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (doneDeleteConfirm && status.shareId.isNotBlank()) {
        AlertDialog(
            onDismissRequest = { doneDeleteConfirm = false },
            title = { Text("Delete this file?") },
            text = { Text("The file will be removed and the link will stop working for everyone.") },
            confirmButton = {
                TextButton(onClick = {
                    doneDeleteConfirm = false
                    val id = status.shareId
                    scope.launch {
                        val ok = FileShareApi.deleteShare(id, fsSavedToken(id).ifBlank { null })
                        if (ok) {
                            state.toast("File deleted")
                            if (localDone?.id == id) localDone = null
                            picked = null; password = ""; phase = "pick"
                            loadMine()
                        } else state.toast("Could not delete. Please try again.")
                    }
                }) { Text("Delete", color = Color(0xFFC62F3B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { doneDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    val target = deleteTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete this share?") },
            text = { Text("\"${target.fileName}\" will be removed and its link will stop working for everyone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    scope.launch {
                        val ok = FileShareApi.deleteShare(target.id, fsSavedToken(target.id).ifBlank { null })
                        if (ok) {
                            state.toast("Share deleted")
                            mine = mine?.filter { it.id != target.id }
                            if (localDone?.id == target.id) localDone = null
                        } else state.toast("Could not delete. Please try again.")
                    }
                }) { Text("Delete", color = Color(0xFFC62F3B), fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun FsUploadingPane(
    status: FsUpStatus,
    fileName: String,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF0EAFE)),
                contentAlignment = Alignment.Center
            ) { QrIcon(IconKind.Upload, PurplePrimary, size = 22.dp) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(fileName, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = NavyText, maxLines = 2)
                Text(
                    when (status.state) {
                        "creating" -> "Preparing upload…"
                        "finishing" -> "Finishing — checking all parts…"
                        "paused" -> "Paused"
                        else -> "Uploading…"
                    },
                    fontSize = 12.sp, color = MutedText
                )
            }
            Text(
                "${status.pct.toInt()}%",
                fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = PurplePrimary
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().height(13.dp).clip(RoundedCornerShape(99.dp))
                .background(Color(0xFFECE8FA))
        ) {
            Box(
                Modifier.fillMaxWidth(status.pct.toFloat() / 100f).fillMaxHeight()
                    .clip(RoundedCornerShape(99.dp))
                    .background(Brush.horizontalGradient(listOf(PurplePrimary, Color(0xFF9B5CFF))))
            )
        }
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(15.dp)) {
            Text("${fmtBytes(status.uploaded)} / ${fmtBytes(status.total)}", fontSize = 12.sp, color = MutedText)
            if (status.speed > 0) Text("${fmtBytes(status.speed.toLong())}/s", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
            if (status.eta > 0) Text(fmtEta(status.eta), fontSize = 12.sp, color = MutedText)
            if (status.partsTotal > 0) Text("Parts ${status.partsDone}/${status.partsTotal}", fontSize = 12.sp, color = MutedText)
        }
        if (status.error.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            Text(status.error, fontSize = 12.5.sp, color = Color(0xFFC62F3B))
        }
        Spacer(Modifier.height(15.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onPauseResume,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp)
            ) { Text(if (status.state == "paused") "Resume" else "Pause", fontSize = 13.5.sp) }
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp)
            ) { Text("Cancel upload", color = Color(0xFFC62F3B), fontSize = 13.5.sp) }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Keep this tab open while uploading. If it is interrupted, pick the same file again here to resume.",
            fontSize = 11.5.sp, color = MutedText
        )
    }
}

@Composable
private fun FsDonePane(
    state: AppState,
    status: FsUpStatus,
    onDelete: () -> Unit,
    onAnother: () -> Unit
) {
    val link = fsShareLink(status.shareId)
    val qr = remember(link) {
        try { QrCode.encodeText(link, QrEcc.MEDIUM) } catch (e: Exception) { null }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(99.dp)).background(Color(0xFFE5F6EF)),
            contentAlignment = Alignment.Center
        ) { QrIcon(IconKind.Check, Color(0xFF12805C), size = 25.dp) }
        Spacer(Modifier.height(9.dp))
        Text("Your link is ready", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
        Spacer(Modifier.height(4.dp))
        Text(
            "Anyone with this link can download the file." +
                if (status.expiresAt > 0) " It expires on ${fmtDateTime(status.expiresAt)}." else "",
            fontSize = 12.5.sp, color = MutedText, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(13.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF6F4FB))
                .border(1.dp, Color(0xFFE3DDFA), RoundedCornerShape(10.dp))
                .padding(horizontal = 13.dp, vertical = 11.dp)
        ) {
            Text(link, fontSize = 13.sp, color = NavyText)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = {
                    copyTextToClipboard(link)
                    state.toast("Link copied")
                },
                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(46.dp)
            ) { Text("Copy link", fontWeight = FontWeight.SemiBold) }
            OutlinedButton(
                onClick = {
                    val r = shareText(link)
                    state.toast(if (r == "copy") "Link copied" else "Share opened")
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(46.dp)
            ) { Text("Share") }
            OutlinedButton(
                onClick = onDelete,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(46.dp)
            ) { Text("Delete now", color = Color(0xFFC62F3B)) }
        }
        if (qr != null) {
            Spacer(Modifier.height(14.dp))
            QrCanvas(qr, NavyText, Color.White, PngEncoder.QrStyle.SQUARE, null, Modifier.size(168.dp))
            Text("Scan to open the download page", fontSize = 11.5.sp, color = MutedText)
        }
        Spacer(Modifier.height(13.dp))
        TextButton(onClick = onAnother) {
            Text("Upload another file", color = PurplePrimary, fontWeight = FontWeight.SemiBold)
        }
    }
}

/* ------------------------------------------------------------------ */
/* Public download page — /s/<id>, NO login                            */
/* ------------------------------------------------------------------ */

@Composable
fun ShareDownloadScreen(state: AppState) {
    val id = state.shareId
    val scope = rememberCoroutineScope()
    var info by remember(id) { mutableStateOf<FileShareApi.ShareInfo?>(null) }
    var password by remember(id) { mutableStateOf("") }
    var pwError by remember(id) { mutableStateOf("") }
    var busy by remember(id) { mutableStateOf(false) }
    var started by remember(id) { mutableStateOf(false) }

    LaunchedEffect(id) {
        info = null
        if (id.isNotBlank()) info = FileShareApi.fetchInfo(id)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LogoMark(34.dp)
            Spacer(Modifier.width(9.dp))
            Text("Kitnook", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = NavyText)
        }
        Spacer(Modifier.height(6.dp))
        Text("File Share", fontSize = 12.5.sp, color = MutedText)
        Spacer(Modifier.height(18.dp))

        WhiteCard(Modifier.fillMaxWidth().widthIn(max = 560.dp)) {
            Column(
                Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val inf = info
                when {
                    inf == null -> {
                        CircularProgressIndicator(color = PurplePrimary)
                        Spacer(Modifier.height(12.dp))
                        Text("Loading file details…", fontSize = 13.sp, color = MutedText)
                    }
                    inf.state == "expired" -> {
                        QrIcon(IconKind.Clock, MutedText, size = 40.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("This link has expired", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "Shared files are deleted automatically after their set time. " +
                                "Ask the sender to share the file again.",
                            fontSize = 12.5.sp, color = MutedText, textAlign = TextAlign.Center
                        )
                    }
                    inf.state == "not_found" -> {
                        QrIcon(IconKind.Info, MutedText, size = 40.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("This link doesn't exist", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "The file may have been deleted by its owner, or the link may be incomplete. " +
                                "Check the link and try again.",
                            fontSize = 12.5.sp, color = MutedText, textAlign = TextAlign.Center
                        )
                    }
                    inf.state == "not_ready" -> {
                        QrIcon(IconKind.Upload, MutedText, size = 40.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("Still uploading", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "The sender hasn't finished uploading this file yet. Please try again in a little while.",
                            fontSize = 12.5.sp, color = MutedText, textAlign = TextAlign.Center
                        )
                    }
                    inf.state != "ready" -> {
                        QrIcon(IconKind.Info, MutedText, size = 40.dp)
                        Spacer(Modifier.height(10.dp))
                        Text("Something went wrong", fontSize = 16.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "We couldn't load this file. Check your connection and reload the page.",
                            fontSize = 12.5.sp, color = MutedText, textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Box(
                            Modifier.size(62.dp).clip(RoundedCornerShape(15.dp))
                                .background(Color(0xFFF0EAFE)),
                            contentAlignment = Alignment.Center
                        ) { QrIcon(IconKind.FileText, PurplePrimary, size = 32.dp) }
                        Spacer(Modifier.height(13.dp))
                        Text(
                            inf.fileName, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                            color = NavyText, textAlign = TextAlign.Center, maxLines = 3
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "${fmtBytes(inf.size)} · ${inf.daysLeft} day${if (inf.daysLeft == 1) "" else "s"} left" +
                                if (inf.downloads > 0) " · ${inf.downloads} downloads" else "",
                            fontSize = 12.5.sp, color = MutedText
                        )
                        if (inf.hasPassword) {
                            Spacer(Modifier.height(15.dp))
                            OutlinedTextField(
                                value = password,
                                onValueChange = { password = it; pwError = "" },
                                label = { Text("Password") },
                                placeholder = { Text("This file is password protected") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (pwError.isNotBlank()) {
                            Spacer(Modifier.height(9.dp))
                            Text(pwError, fontSize = 12.5.sp, color = Color(0xFFC62F3B))
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (busy) return@Button
                                busy = true
                                pwError = ""
                                scope.launch {
                                    try {
                                        val url = FileShareApi.requestDownload(
                                            id, if (inf.hasPassword) password else null
                                        )
                                        downloadUrl(url, inf.fileName)
                                        started = true
                                    } catch (e: FileShareApi.FsException) {
                                        pwError = when (e.code) {
                                            "wrong_password" -> "Wrong password. Please try again."
                                            "expired" -> "This link has expired."
                                            "not_found" -> "This link doesn't exist."
                                            "not_ready" -> "The file is still uploading. Try again shortly."
                                            else -> "Download failed. Please try again."
                                        }
                                        if (e.code == "expired") {
                                            info = inf.copy(state = "expired")
                                        }
                                    } catch (e: Exception) {
                                        pwError = "Download failed. Check your connection and try again."
                                    }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(52.dp)
                        ) {
                            if (busy) {
                                CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(Modifier.width(9.dp))
                                Text("Preparing…", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            } else {
                                QrIcon(IconKind.Download, Color.White, size = 20.dp)
                                Spacer(Modifier.width(9.dp))
                                Text("Download", fontSize = 15.5.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (started) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Your download has started. If nothing happens, tap Download again.",
                                fontSize = 12.sp, color = Color(0xFF12805C), textAlign = TextAlign.Center
                            )
                        }
                        Spacer(Modifier.height(13.dp))
                        Text(
                            "Shared by a Kitnook user. Kitnook does not scan shared files — " +
                                "download only files from people you trust.",
                            fontSize = 11.sp, color = MutedText, textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { state.navigate(Screen.FileShare) }) {
            Text("Share your own file with Kitnook →", color = PurplePrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(10.dp))
    }
}
