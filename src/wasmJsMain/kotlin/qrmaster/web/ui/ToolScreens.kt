package qrmaster.web.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.skia.Image as SkiaImage
import qrmaster.web.calc.AgeLogic
import qrmaster.web.calc.BmiLogic
import qrmaster.web.calc.CalcFormat
import qrmaster.web.calc.CurrencyLogic
import qrmaster.web.calc.PercentLogic
import qrmaster.web.calc.UnitLogic
import qrmaster.web.calc.Ymd
import qrmaster.web.data.httpRequest
import qrmaster.web.data.istDateOf
import qrmaster.web.data.nowMillis
import qrmaster.web.tools.BarcodeLogic
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// ── Shared tool-screen components ──────────────────────────────────────────

@Composable
fun ToolPage(
    icon: IconKind, title: String, subtitle: String,
    content: LazyListScope.() -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp),
        content = {
            item { Spacer(Modifier.height(2.dp)) }
            item { PageHeader(icon, title, subtitle) }
            content()
            item { Spacer(Modifier.height(8.dp)) }
        }
    )
}

@Composable
fun LabeledDropdown(
    label: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label.isNotBlank()) {
            Text(label, fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(5.dp))
        }
        Box {
            Row(
                Modifier.fillMaxWidth().height(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .clickable { open = true }
                    .padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(options.getOrElse(selected) { "" }, fontSize = 13.5.sp, color = NavyText, modifier = Modifier.weight(1f), maxLines = 1)
                Text("▾", color = MutedText, fontSize = 15.sp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEachIndexed { i, o ->
                    DropdownMenuItem(text = { Text(o) }, onClick = { onSelect(i); open = false })
                }
            }
        }
    }
}

/**
 * The one upload zone used by every file tool: click opens the picker,
 * drag & drop lands here through FileDropCollector, and both paths share
 * validateIncoming(). Shows the selected files with sizes + remove.
 */
@Composable
fun FileZone(
    state: AppState,
    files: List<WebFile>,
    spec: FileSpec,
    hint: String,
    bigUploadButton: Boolean = false,
    bigUploadLabel: String = "Upload Image",
    bigUploadPasteHint: Boolean = true,
    onChange: (List<WebFile>) -> Unit
) {
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }

    fun addFiles(incoming: List<WebFile>, err: String?) {
        if (incoming.isNotEmpty()) onChange(files + incoming)
        if (err != null) state.toast(err)
        error = err
    }
    FileDropCollector(spec, current = { files }) { incoming, err -> addFiles(incoming, err) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.fillMaxWidth().height(if (files.isEmpty()) (if (bigUploadButton) 216.dp else 150.dp) else 96.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFFBFAFF))
                .drawBehind {
                    drawRoundRect(
                        color = Color(0xFFB9A8F5),
                        style = Stroke(width = 1.6f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f))),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    )
                }
                .clickable {
                    launchPick(scope, spec, spec.maxCount > 1, current = { files }) { incoming, err ->
                        addFiles(incoming, err)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (bigUploadButton && files.isEmpty()) {
                // Big-button entry layout (Passport Photo Maker, PDF
                // Editor): one large brand-purple Upload button, centred,
                // with the drop / paste helper lines under it. The whole
                // zone stays clickable and droppable — only the
                // presentation changes.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(PurplePrimary)
                            .padding(horizontal = 38.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        QrIcon(IconKind.Upload, Color.White, size = 25.dp)
                        Spacer(Modifier.width(11.dp))
                        Text(bigUploadLabel, fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color.White)
                    }
                    Spacer(Modifier.height(15.dp))
                    Text("or drop a file here", fontSize = 13.5.sp, color = MutedText)
                    if (bigUploadPasteHint) {
                        Spacer(Modifier.height(3.dp))
                        Text("Ctrl+V to paste image", fontSize = 13.5.sp, color = MutedText)
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(hint, fontSize = 12.sp, color = MutedText)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    QrIcon(IconKind.Download, PurplePrimary, size = 34.dp)
                    Spacer(Modifier.height(8.dp))
                    Text("Click to upload or drag and drop", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = NavyText)
                    Spacer(Modifier.height(3.dp))
                    Text(hint, fontSize = 12.sp, color = MutedText)
                }
            }
        }
        files.forEach { f ->
            WhiteCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Image files show their real photo as a thumbnail —
                    // a generic icon alone left users guessing which
                    // photo they had picked.
                    val isImage = spec != PDF_SPEC && (f.type.startsWith("image/") || spec == IMAGE_SPEC || spec == IMAGE_BATCH_SPEC)
                    var thumb by remember(f.id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    LaunchedEffect(f.id) {
                        if (isImage) {
                            val dataUrl = loadFileDataUrl(f)
                            if (dataUrl != null) {
                                // Own async slot per file — the shared
                                // 'img' slot would clobber parallel rows.
                                val slot = "thumb:${f.id}"
                                imgOpKeyed("thumb", "{\"dataUrl\":\"$dataUrl\",\"maxDim\":128}", slot)
                                val res = awaitAsync(slot, 30_000)
                                if (res != null && res.asyncOk()) {
                                    thumb = decodeBitmapFromDataUrl(res.asyncStr("dataUrl"))
                                }
                            }
                        }
                    }
                    if (thumb != null) {
                        androidx.compose.foundation.Image(
                            thumb!!, contentDescription = f.name,
                            modifier = Modifier.size(46.dp).clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE8E6F5)),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        QrIcon(if (spec == PDF_SPEC) IconKind.Pdf else IconKind.Image, PurplePrimary, size = 20.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NavyText, maxLines = 1)
                        Text(formatBytes(f.size), fontSize = 11.5.sp, color = MutedText)
                    }
                    TextButton(onClick = {
                        releaseFile(f.id)
                        onChange(files - f)
                    }) { Text("Remove", fontSize = 12.sp) }
                }
            }
        }
        if (error != null) {
            Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.5.sp)
        }
    }
}

@OptIn(ExperimentalEncodingApi::class)
fun decodeBitmapFromDataUrl(dataUrl: String): ImageBitmap? {
    return try {
        val b64 = dataUrl.substringAfter(",", "")
        if (b64.isBlank()) return null
        val bytes = Base64.decode(b64)
        SkiaImage.makeFromEncoded(bytes).toComposeImageBitmap()
    } catch (e: Exception) { null }
}

@Composable
fun PrimaryToolButton(text: String, enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
        modifier = Modifier.fillMaxWidth().height(46.dp)
    ) {
        if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(19.dp))
        else Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

// ── Barcode Generator ──────────────────────────────────────────────────────

@Composable
fun BarcodeScreen(state: AppState) {
    val kinds = listOf("Code 128", "EAN-13", "UPC-A", "Code 39")
    var kind by remember { mutableStateOf("Code 128") }
    var input by remember { mutableStateOf("") }
    val encoded = remember(kind, input) {
        if (input.isBlank()) null else BarcodeLogic.encode(kind, input.trim())
    }
    ToolPage(IconKind.Barcode, "Barcode Generator", "Create scannable barcodes — Code 128, EAN-13, UPC-A and Code 39.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("Barcode Type", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        kinds.forEach { k ->
                            FilterChip(
                                selected = kind == k,
                                onClick = { kind = k },
                                label = { Text(k, fontSize = 12.5.sp) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PurplePrimary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text("Barcode content") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(BarcodeLogic.hintFor(kind), fontSize = 12.sp, color = MutedText)
                    when {
                        input.isBlank() -> Text("Type the content to see the barcode.", fontSize = 13.sp, color = MutedText)
                        encoded == null -> Text(
                            "This content is not valid for $kind. Check the hint above.",
                            color = MaterialTheme.colorScheme.error, fontSize = 13.sp
                        )
                        else -> {
                            val enc = encoded!!
                            Canvas(
                                Modifier.fillMaxWidth().height(150.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White)
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                            ) {
                                val unit = size.width / enc.modules.length
                                enc.modules.forEachIndexed { i, c ->
                                    if (c == '1') {
                                        drawRect(
                                            Color.Black,
                                            topLeft = Offset(i * unit, 14f),
                                            size = Size(unit + 0.6f, size.height - 28f)
                                        )
                                    }
                                }
                            }
                            Text(
                                enc.displayText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                                color = NavyText, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = {
                                    val modulePx = 5
                                    val margin = 30
                                    val w = enc.modules.length * modulePx + margin * 2
                                    val h = 190
                                    val png = PngEncoder.encodeGray(w, h) { x, y ->
                                        val mx = (x - margin) / modulePx
                                        if (y in 25..164 && mx in enc.modules.indices && enc.modules[mx] == '1') 0 else 255
                                    }
                                    downloadPngBase64("qr-master-barcode.png", b64Encode(png))
                                    recordHistory("Barcode", "$kind barcode", enc.displayText)
                                    state.toast("Barcode downloaded")
                                },
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) { Text("Download PNG", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}

// ── QR Scanner (decode from an image) ──────────────────────────────────────

@Composable
fun QrScanScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    ToolPage(IconKind.Scan, "QR Scanner", "Upload an image of a QR code and read what it contains.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_SPEC, "PNG, JPG or WebP image with a QR code (max 15 MB)") { newFiles ->
                        files = newFiles.take(1)
                        result = null; error = null
                    }
                    PrimaryToolButton("Scan QR Code", enabled = files.isNotEmpty(), busy = busy) {
                        val f = files.firstOrNull() ?: return@PrimaryToolButton
                        busy = true; result = null; error = null
                        scope.launch {
                            val dataUrl = loadFileDataUrl(f)
                            if (dataUrl == null) {
                                error = "Could not read that file."
                                busy = false
                                return@launch
                            }
                            qrDecodeStart(dataUrl)
                            val res = awaitAsync("qrscan", 60_000)
                            busy = false
                            when {
                                res == null -> error = "Scanning timed out. Try a clearer image."
                                res.asyncOk() -> {
                                    result = res.asyncStr("text")
                                    recordHistory("QR Scanner", "QR decoded", result!!.take(60))
                                }
                                else -> error = res.asyncError()
                            }
                        }
                    }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    if (result != null) {
                        Text("Decoded text:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                        Text(result!!, fontSize = 14.sp, color = NavyText)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    copyTextToClipboard(result!!)
                                    state.toast("Copied to clipboard")
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Copy", fontSize = 12.5.sp) }
                            if (result!!.startsWith("http://") || result!!.startsWith("https://")) {
                                OutlinedButton(
                                    onClick = { openExternal(result!!) },
                                    shape = RoundedCornerShape(10.dp)
                                ) { Text("Open Link", fontSize = 12.5.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Age Calculator (Advanced — the app's v44) ──────────────────────────────

private fun todayYmd(): Ymd {
    val s = istDateOf(nowMillis())
    val p = s.split("-")
    return Ymd(p[0].toInt(), p[1].toInt(), p[2].toInt())
}

@Composable
private fun DateInputRow(label: String, ymd: Triple<String, String, String>, onChange: (Triple<String, String, String>) -> Unit) {
    Column {
        Text(label, fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            OutlinedTextField(
                value = ymd.first, onValueChange = { onChange(Triple(it.filter { c -> c.isDigit() }.take(2), ymd.second, ymd.third)) },
                label = { Text("Day") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = ymd.second, onValueChange = { onChange(Triple(ymd.first, it.filter { c -> c.isDigit() }.take(2), ymd.third)) },
                label = { Text("Month") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = ymd.third, onValueChange = { onChange(Triple(ymd.first, ymd.second, it.filter { c -> c.isDigit() }.take(4))) },
                label = { Text("Year") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1.4f)
            )
        }
    }
}

@Composable
fun AgeCalcScreen(state: AppState) {
    val today = remember { todayYmd() }
    var dobIn by remember { mutableStateOf(Triple("", "", "")) }
    var tgtIn by remember {
        mutableStateOf(Triple(today.d.toString(), today.m.toString(), today.y.toString()))
    }
    var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<AgeLogic.DetailedAge?>(null) }
    var usedDob by remember { mutableStateOf<Ymd?>(null) }
    var usedTgt by remember { mutableStateOf<Ymd?>(null) }
    var showSaved by remember { mutableStateOf(false) }
    var savedList by remember { mutableStateOf<List<String>>(emptyList()) }

    fun parse(t: Triple<String, String, String>): Ymd? {
        val d = t.first.toIntOrNull(); val m = t.second.toIntOrNull(); val y = t.third.toIntOrNull()
        if (d == null || m == null || y == null) return null
        if (!AgeLogic.isValid(y, m, d)) return null
        return Ymd(y, m, d)
    }
    fun loadSaved() {
        savedList = try {
            val raw = window.localStorage.getItem("qm_age_results") ?: "[]"
            val arr = Json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonArray
            arr?.mapNotNull { (it as? JsonObject)?.get("label")?.jsonPrimitive?.content } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }
    fun saveCurrent() {
        val d = usedDob ?: return; val t = usedTgt ?: return; val det = detail ?: return
        try {
            val label = "${d.display()} → ${t.display()}: ${det.years}y ${det.months}m ${det.days}d"
            val raw = window.localStorage.getItem("qm_age_results") ?: "[]"
            val arr = Json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonArray
            val items = mutableListOf<String>()
            items.add("{\"dob\":\"${d.y}-${d.m}-${d.d}\",\"tgt\":\"${t.y}-${t.m}-${t.d}\",\"label\":${kotlinx.serialization.json.JsonPrimitive(label)}}")
            arr?.forEach { el -> items.add(el.toString()) }
            val trimmed = items.take(20)
            window.localStorage.setItem("qm_age_results", "[" + trimmed.joinToString(",") + "]")
            state.toast("Result saved")
        } catch (e: Exception) { state.toast("Could not save") }
    }

    ToolPage(IconKind.Calendar, "Age Calculator", "Exact age between two dates — with the full calendar timeline.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    DateInputRow("Date of Birth", dobIn) { dobIn = it }
                    DateInputRow("Age at the Date of", tgtIn) { tgtIn = it }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    PrimaryToolButton("Calculate Age", enabled = true, busy = false) {
                        val dob = parse(dobIn); val tgt = parse(tgtIn)
                        when {
                            dob == null -> error = "Please enter a valid date of birth."
                            tgt == null -> error = "Please enter a valid target date."
                            tgt < dob -> error = "The target date is before the date of birth."
                            else -> {
                                error = null
                                detail = AgeLogic.computeDetailed(dob, tgt)
                                usedDob = dob; usedTgt = tgt
                                if (detail != null) recordHistory("Age Calculator", "Age calculated", "${dob.display()} → ${tgt.display()}")
                            }
                        }
                    }
                    val det = detail
                    if (det != null && usedDob != null && usedTgt != null) {
                        Text(
                            "${det.years} years, ${det.months} months, ${det.days} days",
                            fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = PurplePrimary
                        )
                        val rows = listOf(
                            "Total months" to "${CalcFormat.grouped(det.totalMonths.toLong())} months, ${det.monthRemainDays} days",
                            "Total weeks" to "${CalcFormat.grouped(det.totalWeeks)} weeks, ${det.weekRemainDays} days",
                            "Total days" to CalcFormat.grouped(det.totalDays),
                            "Total hours" to CalcFormat.grouped(det.totalHours),
                            "Total minutes" to CalcFormat.grouped(det.totalMinutes),
                            "Total seconds" to CalcFormat.grouped(det.totalSeconds)
                        )
                        rows.forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth()) {
                                Text(k, fontSize = 13.sp, color = MutedText, modifier = Modifier.weight(1f))
                                Text(v, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
                            }
                        }
                        val age = AgeLogic.computeAge(usedDob!!, today)
                        if (age != null && usedTgt == today) {
                            Text(
                                if (age.isBirthdayToday) "🎉 Birthday today!"
                                else "Next birthday: ${age.nextBirthday.displayFull()} (in ${age.countdownMonths} months, ${age.countdownDays} days)",
                                fontSize = 13.sp, color = NavyText
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = { saveCurrent() }, shape = RoundedCornerShape(10.dp)) {
                                Text("Save Result", fontSize = 12.5.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    loadSaved()
                                    if (savedList.isEmpty()) state.toast("No saved results yet")
                                    else showSaved = true
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Saved Results", fontSize = 12.5.sp) }
                            OutlinedButton(
                                onClick = { shareText(AgeLogic.shareText(usedDob!!, usedTgt!!, det)) },
                                shape = RoundedCornerShape(10.dp)
                            ) { Text("Share Result", fontSize = 12.5.sp) }
                        }
                    }
                }
            }
        }
        val dob = usedDob; val tgt = usedTgt
        if (detail != null && dob != null && tgt != null) {
            item {
                Text("Calendar Timeline", fontSize = 15.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                Text(
                    "Purple = date of birth • Amber = target date • Months from ${dob.display()} to ${tgt.display()}",
                    fontSize = 12.sp, color = MutedText
                )
            }
            val months = AgeLogic.monthsSpanInclusive(dob, tgt)
            items(months) { i ->
                val first = AgeLogic.shiftMonth(dob.y, dob.m, i)
                MonthGrid(first.y, first.m, dob, tgt)
            }
        }
    }

    if (showSaved) {
        AlertDialog(
            onDismissRequest = { showSaved = false },
            title = { Text("Saved Results") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    savedList.forEachIndexed { idx, label ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, fontSize = 12.5.sp, color = NavyText, modifier = Modifier.weight(1f))
                            TextButton(onClick = {
                                try {
                                    val raw = window.localStorage.getItem("qm_age_results") ?: "[]"
                                    val arr = Json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonArray
                                    val el = arr?.getOrNull(idx) as? JsonObject
                                    val dobS = el?.get("dob")?.jsonPrimitive?.content ?: ""
                                    val tgtS = el?.get("tgt")?.jsonPrimitive?.content ?: ""
                                    val dp = dobS.split("-"); val tp = tgtS.split("-")
                                    if (dp.size == 3 && tp.size == 3) {
                                        dobIn = Triple(dp[2], dp[1], dp[0])
                                        tgtIn = Triple(tp[2], tp[1], tp[0])
                                        val d = Ymd(dp[0].toInt(), dp[1].toInt(), dp[2].toInt())
                                        val t = Ymd(tp[0].toInt(), tp[1].toInt(), tp[2].toInt())
                                        detail = AgeLogic.computeDetailed(d, t)
                                        usedDob = d; usedTgt = t
                                    }
                                } catch (e: Exception) { }
                                showSaved = false
                            }) { Text("Restore", fontSize = 12.sp) }
                            TextButton(onClick = {
                                try {
                                    val raw = window.localStorage.getItem("qm_age_results") ?: "[]"
                                    val arr = Json.parseToJsonElement(raw) as? kotlinx.serialization.json.JsonArray
                                    val kept = arr?.filterIndexed { j, _ -> j != idx } ?: emptyList()
                                    window.localStorage.setItem("qm_age_results", kept.toString())
                                    loadSaved()
                                    if (savedList.isEmpty()) showSaved = false
                                } catch (e: Exception) { }
                            }) { Text("Delete", fontSize = 12.sp) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSaved = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun MonthGrid(year: Int, month: Int, dob: Ymd, target: Ymd) {
    val firstDow = AgeLogic.weekdaySunday0(Ymd(year, month, 1))
    val days = AgeLogic.daysInMonth(year, month)
    WhiteCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${AgeLogic.MONTH_NAMES_FULL.getOrElse(month - 1) { "" }} $year",
                fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = NavyText
            )
            Row(Modifier.fillMaxWidth()) {
                listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { d ->
                    Text(d, fontSize = 10.5.sp, color = MutedText, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            val cells = ArrayList<Int?>()
            repeat(firstDow) { cells.add(null) }
            for (d in 1..days) cells.add(d)
            while (cells.size % 7 != 0) cells.add(null)
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { d ->
                        Box(Modifier.weight(1f).height(30.dp), contentAlignment = Alignment.Center) {
                            if (d != null) {
                                val isDob = year == dob.y && month == dob.m && d == dob.d
                                val isTgt = year == target.y && month == target.m && d == target.d
                                Box(
                                    Modifier.size(27.dp).clip(CircleShape)
                                        .background(
                                            when {
                                                isDob -> PurplePrimary
                                                isTgt -> Color(0xFFF59E0B)
                                                else -> Color.Transparent
                                            }
                                        )
                                        .then(
                                            if (isDob && isTgt) Modifier.border(2.dp, Color(0xFFF59E0B), CircleShape)
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        d.toString(), fontSize = 11.5.sp,
                                        color = if (isDob || isTgt) Color.White else NavyText,
                                        fontWeight = if (isDob || isTgt) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── BMI Calculator ─────────────────────────────────────────────────────────

@Composable
fun BmiScreen(state: AppState) {
    var imperial by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var heightFt by remember { mutableStateOf("") }
    var heightIn by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Double?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    ToolPage(IconKind.Calc, "BMI Calculator", "Body Mass Index from your weight and height.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf(false to "Metric (kg, cm)", true to "Imperial (lb, ft/in)").forEach { (imp, label) ->
                            FilterChip(
                                selected = imperial == imp,
                                onClick = { imperial = imp; result = null },
                                label = { Text(label, fontSize = 12.5.sp) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    if (!imperial) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Weight (kg)") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = height, onValueChange = { height = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Height (cm)") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = weight, onValueChange = { weight = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Weight (lb)") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = heightFt, onValueChange = { heightFt = it.filter { c -> c.isDigit() } },
                                label = { Text("Feet") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(0.6f)
                            )
                            OutlinedTextField(
                                value = heightIn, onValueChange = { heightIn = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Inches") }, singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(0.7f)
                            )
                        }
                    }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    PrimaryToolButton("Calculate BMI", enabled = true, busy = false) {
                        val w = weight.toDoubleOrNull()
                        val bmi = if (!imperial) {
                            val h = height.toDoubleOrNull()
                            if (w == null || h == null) null else BmiLogic.bmiMetric(w, h)
                        } else {
                            val ft = heightFt.toIntOrNull(); val inc = heightIn.toDoubleOrNull()
                            if (w == null || ft == null || inc == null) null else BmiLogic.bmiImperial(w, ft, inc)
                        }
                        if (bmi == null) { error = "Please enter valid weight and height."; result = null }
                        else { error = null; result = bmi; recordHistory("BMI Calculator", "BMI calculated", BmiLogic.display(bmi)) }
                    }
                    val bmi = result
                    if (bmi != null) {
                        val cat = BmiLogic.category(bmi)
                        Text(BmiLogic.display(bmi), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = PurplePrimary)
                        Text(cat.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyText)
                        Text(BmiLogic.note(cat), fontSize = 12.5.sp, color = MutedText)
                    }
                }
            }
        }
    }
}

// ── Unit Converter ─────────────────────────────────────────────────────────

@Composable
fun UnitScreen(state: AppState) {
    var catIndex by remember { mutableStateOf(0) }
    val cat = UnitLogic.CATEGORIES[catIndex]
    var fromIndex by remember { mutableStateOf(0) }
    var toIndex by remember { mutableStateOf(1) }
    var value by remember { mutableStateOf("1") }
    LaunchedEffect(catIndex) { fromIndex = 0; toIndex = if (cat.units.size > 1) 1 else 0 }
    val from = cat.units.getOrElse(fromIndex) { cat.units[0] }
    val to = cat.units.getOrElse(toIndex) { cat.units[0] }
    val inputVal = value.toDoubleOrNull()
    val converted = inputVal?.let { UnitLogic.convert(cat, from.id, to.id, it) }
    ToolPage(IconKind.Swap, "Unit Converter", "Length, weight, temperature, area, volume, speed, time, data, pressure and energy.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("Category", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // two rows of chips via chunking
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            UnitLogic.CATEGORIES.chunked(5).forEach { rowCats ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowCats.forEach { c ->
                                        val idx = UnitLogic.CATEGORIES.indexOf(c)
                                        FilterChip(
                                            selected = catIndex == idx,
                                            onClick = { catIndex = idx },
                                            label = { Text(c.name, fontSize = 12.sp) },
                                            shape = RoundedCornerShape(20.dp),
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = value, onValueChange = { value = it },
                        label = { Text("Value") }, singleLine = true, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                        LabeledDropdown("From", cat.units.map { "${it.name} (${it.symbol})" }, fromIndex, { fromIndex = it }, Modifier.weight(1f))
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFF0EAFE))
                                .clickable { val t = fromIndex; fromIndex = toIndex; toIndex = t },
                            contentAlignment = Alignment.Center
                        ) { QrIcon(IconKind.Swap, PurplePrimary, size = 19.dp) }
                        LabeledDropdown("To", cat.units.map { "${it.name} (${it.symbol})" }, toIndex, { toIndex = it }, Modifier.weight(1f))
                    }
                    if (converted != null && inputVal != null) {
                        Text(
                            "${CalcFormat.number(inputVal)} ${from.symbol} = ${CalcFormat.number(converted)} ${to.symbol}",
                            fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = PurplePrimary
                        )
                    } else {
                        Text("Enter a valid number to convert.", fontSize = 13.sp, color = MutedText)
                    }
                }
            }
        }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Common Conversions — ${cat.name}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NavyText)
                    UnitLogic.commonLines(cat).forEach { line ->
                        Text(line, fontSize = 13.sp, color = MutedText)
                    }
                }
            }
        }
    }
}

// ── Currency Converter (live rates, app endpoints) ─────────────────────────

private data class FxState(
    val snapshot: CurrencyLogic.RatesSnapshot? = null,
    val stale: Boolean = false,
    val loading: Boolean = true,
    val failed: Boolean = false
)

private fun parseFxCache(): CurrencyLogic.RatesSnapshot? {
    return try {
        val raw = window.localStorage.getItem("qm_fx_cache") ?: return null
        val o = Json.parseToJsonElement(raw).jsonObject
        val ratesObj = o["rates"]?.jsonObject ?: return null
        val rates = ratesObj.mapValues { it.value.jsonPrimitive.content.toDouble() }
        CurrencyLogic.RatesSnapshot(
            rates,
            o["fetchedAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            o["source"]?.jsonPrimitive?.content ?: ""
        )
    } catch (e: Exception) { null }
}

private fun saveFxCache(s: CurrencyLogic.RatesSnapshot) {
    try {
        val ratesJson = s.rates.entries.joinToString(",") { "\"${it.key}\":${it.value}" }
        window.localStorage.setItem(
            "qm_fx_cache",
            "{\"rates\":{$ratesJson},\"fetchedAt\":${s.fetchedAtMillis},\"source\":\"${s.source}\"}"
        )
    } catch (e: Exception) { }
}

private suspend fun fetchFxRates(): CurrencyLogic.RatesSnapshot? {
    // Primary: open.er-api.com (same as the Android app)
    try {
        val res = httpRequest("GET", "https://open.er-api.com/v6/latest/USD")
        val o = Json.parseToJsonElement(res).jsonObject
        val ratesObj = o["rates"]?.jsonObject
        if (ratesObj != null) {
            val rates = ratesObj.mapValues { it.value.jsonPrimitive.content.toDouble() }
            return CurrencyLogic.RatesSnapshot(rates, nowMillis(), "open.er-api.com")
        }
    } catch (e: Exception) { }
    // Fallback: frankfurter.app (same as the Android app)
    try {
        val res = httpRequest("GET", "https://api.frankfurter.app/latest?from=USD")
        val o = Json.parseToJsonElement(res).jsonObject
        val ratesObj = o["rates"]?.jsonObject
        if (ratesObj != null) {
            val rates = ratesObj.mapValues { it.value.jsonPrimitive.content.toDouble() }
            return CurrencyLogic.RatesSnapshot(rates, nowMillis(), "frankfurter.app")
        }
    } catch (e: Exception) { }
    return null
}

@Composable
fun CurrencyScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var fx by remember { mutableStateOf(FxState(loading = true)) }
    var amount by remember { mutableStateOf("100") }
    var fromCode by remember { mutableStateOf("USD") }
    var toCode by remember { mutableStateOf("INR") }

    fun load(force: Boolean) {
        scope.launch {
            fx = fx.copy(loading = true, failed = false)
            val cached = parseFxCache()
            val fetched = if (force || cached == null) fetchFxRates() else null
            if (fetched != null) saveFxCache(fetched)
            val resolved = CurrencyLogic.resolveRates(fetched, cached)
            fx = if (resolved != null) {
                FxState(snapshot = resolved.first, stale = resolved.second, loading = false)
            } else {
                FxState(snapshot = null, loading = false, failed = true)
            }
        }
    }
    LaunchedEffect(Unit) { load(false) }

    val snap = fx.snapshot
    val currencies = remember(snap) {
        if (snap != null) CurrencyLogic.availableCurrencies(snap.rates) else CurrencyLogic.CURRENCIES
    }
    val fromIdx = currencies.indexOfFirst { it.code == fromCode }.coerceAtLeast(0)
    val toIdx = currencies.indexOfFirst { it.code == toCode }.coerceAtLeast(0)
    val amt = amount.toDoubleOrNull()
    val converted = if (snap != null && amt != null) CurrencyLogic.convert(amt, fromCode, toCode, snap.rates) else null
    val rate = if (snap != null) CurrencyLogic.crossRate(fromCode, toCode, snap.rates) else null

    ToolPage(IconKind.Wallet, "Currency Converter", "Live exchange rates — the same sources the Android app uses.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    when {
                        fx.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Loading live rates…", fontSize = 13.sp, color = MutedText)
                        }
                        fx.failed -> Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(
                                "Could not load exchange rates. Check your connection and try again.",
                                color = MaterialTheme.colorScheme.error, fontSize = 13.sp
                            )
                            OutlinedButton(onClick = { load(true) }, shape = RoundedCornerShape(10.dp)) {
                                Text("Retry", fontSize = 12.5.sp)
                            }
                        }
                        snap != null -> {
                            OutlinedTextField(
                                value = amount, onValueChange = { amount = it },
                                label = { Text("Amount") }, singleLine = true, shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
                                LabeledDropdown("From", currencies.map { "${it.code} — ${it.name}" }, fromIdx,
                                    { fromCode = currencies[it].code }, Modifier.weight(1f))
                                Box(
                                    Modifier.size(46.dp).clip(CircleShape).background(Color(0xFFF0EAFE))
                                        .clickable { val t = fromCode; fromCode = toCode; toCode = t },
                                    contentAlignment = Alignment.Center
                                ) { QrIcon(IconKind.Swap, PurplePrimary, size = 19.dp) }
                                LabeledDropdown("To", currencies.map { "${it.code} — ${it.name}" }, toIdx,
                                    { toCode = currencies[it].code }, Modifier.weight(1f))
                            }
                            if (converted != null && amt != null) {
                                Text(
                                    "${CalcFormat.number(amt)} $fromCode = ${CalcFormat.money(converted)} $toCode",
                                    fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = PurplePrimary
                                )
                                if (rate != null) {
                                    Text("1 $fromCode = ${CalcFormat.rate(rate)} $toCode", fontSize = 12.5.sp, color = MutedText)
                                }
                            }
                            Text(
                                "Rates from ${snap.source} • Updated ${fmtDateTime(snap.fetchedAtMillis)}" +
                                    if (fx.stale) " • Showing saved rates (offline)" else "",
                                fontSize = 11.5.sp, color = MutedText
                            )
                            OutlinedButton(onClick = { load(true) }, shape = RoundedCornerShape(10.dp)) {
                                Text("Refresh Rates", fontSize = 12.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Percentage Calculator ──────────────────────────────────────────────────

@Composable
fun PercentScreen(state: AppState) {
    // ── Hero mode (the owner's flow): Amount + % → one plain sentence ──
    var amount by remember { mutableStateOf("") }
    var percent by remember { mutableStateOf("") }
    val amt = amount.toDoubleOrNull()
    val pct = percent.toDoubleOrNull()
    val heroResult = if (amt != null && pct != null)
        "${CalcFormat.number(pct)}% of ${CalcFormat.number(amt)} = ${CalcFormat.number(PercentLogic.percentOf(pct, amt))}"
    else null

    // ── Extra modes below: share, change, discount ──
    var mode by remember { mutableStateOf(0) } // 0 = what %, 1 = % change, 2 = discount
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val x = a.toDoubleOrNull(); val y = b.toDoubleOrNull()
    val extraResult: String? = when {
        x == null || y == null -> null
        mode == 0 -> PercentLogic.whatPercent(x, y)?.let {
            "${CalcFormat.number(x)} is ${CalcFormat.number(it)}% of ${CalcFormat.number(y)}"
        } ?: "Total cannot be zero."
        mode == 1 -> PercentLogic.change(x, y)?.let {
            val sign = if (it > 0) "increase" else if (it < 0) "decrease" else "no change"
            "${CalcFormat.number(x)} → ${CalcFormat.number(y)} is a ${CalcFormat.number(kotlin.math.abs(it))}% $sign"
        } ?: "The starting value cannot be zero."
        else -> when {
            y < 0 || y > 100 -> "Discount must be between 0% and 100%."
            x < 0 -> "Price cannot be negative."
            else -> {
                val saved = PercentLogic.percentOf(y, x)
                "You pay ₹${CalcFormat.money(x - saved)} — you save ₹${CalcFormat.money(saved)} (${CalcFormat.number(y)}% off ₹${CalcFormat.number(x)})"
            }
        }
    }

    // Record settled results in History (debounced — typing through
    // partial numbers must not spam the log).
    val settled = heroResult ?: extraResult
    LaunchedEffect(settled) {
        if (settled != null && !settled.contains("cannot") && !settled.startsWith("Discount must")) {
            delay(1200)
            recordHistory("Percentage Calculator", "Percentage calculated", settled)
        }
    }

    ToolPage(IconKind.Percent, "Percentage Calculator", "Enter an amount and a percent — the answer appears as you type.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("How much is the percent of an amount?", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyText)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = amount, onValueChange = { amount = it },
                            label = { Text("Amount") },
                            singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = percent, onValueChange = { percent = it },
                            label = { Text("Percent (%)") },
                            singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                        )
                    }
                    if (heroResult != null) {
                        Text(heroResult, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                    } else {
                        Text(
                            if (amount.isBlank() && percent.isBlank()) "Type an amount and a percent — for example, 12% of 5000."
                            else "Please enter valid numbers in both fields.",
                            fontSize = 13.sp, color = MutedText
                        )
                    }
                }
            }
        }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("More percentage tools", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyText)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf("X is what % of Y?", "% change X → Y", "Discount").forEachIndexed { i, m ->
                            FilterChip(
                                selected = mode == i,
                                onClick = { mode = i },
                                label = { Text(m, fontSize = 12.sp) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = a, onValueChange = { a = it },
                            label = { Text(when (mode) { 0 -> "Value (X)"; 1 -> "From (X)"; else -> "Price (₹)" }) },
                            singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = b, onValueChange = { b = it },
                            label = { Text(when (mode) { 0 -> "Total (Y)"; 1 -> "To (Y)"; else -> "Discount (% off)" }) },
                            singleLine = true, shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                        )
                    }
                    if (extraResult != null) {
                        Text(extraResult, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = PurplePrimary)
                    } else {
                        Text(
                            when (mode) {
                                0 -> "For example: 50 is what % of 200?"
                                1 -> "For example: from 100 to 125 — how much % change?"
                                else -> "For example: ₹999 with 10% off — what do you pay?"
                            },
                            fontSize = 13.sp, color = MutedText
                        )
                    }
                }
            }
        }
    }
}
