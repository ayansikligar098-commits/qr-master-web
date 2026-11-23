package qrmaster.web.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ── Passport Photo Maker ─────────────────────────────────────────────────
// Mirrors the reference passport-photo flow: upload → automatic background
// removal + face framing → maker with Size / Background / Outfit tabs →
// free download of the single photo or a tiled print sheet. Everything
// runs on-device; downloads are free and need no sign-in.

private enum class PPStage { Landing, Processing, Maker }
private enum class PPViewMode { Result, Cutout, Original }

private fun ppParseHex(hex: String): Color {
    val h = hex.trim().removePrefix("#")
    val v = h.toLongOrNull(16) ?: return Color.Gray
    return if (h.length >= 8) Color(v) else Color(0xFF000000L or v)
}

private fun ppHex(r: Int, g: Int, b: Int): String {
    fun p(v: Int) = v.coerceIn(0, 255).toString(16).uppercase().padStart(2, '0')
    return "#${p(r)}${p(g)}${p(b)}"
}

private fun ppFmtMm(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString()
    else ((v * 10).roundToInt() / 10.0).toString()

private class PpModel {
    var stage by mutableStateOf(PPStage.Landing)
    var progress by mutableStateOf("")
    var landingError by mutableStateOf<String?>(null)
    var note by mutableStateOf<String?>(null)

    var fileName by mutableStateOf("")
    var originalUrl by mutableStateOf("")
    var cutoutUrl by mutableStateOf("")
    var bgFailed by mutableStateOf(false)
    var imgW by mutableStateOf(0)
    var imgH by mutableStateOf(0)
    var faceBox by mutableStateOf<FloatArray?>(null)
    var crop by mutableStateOf(PPCrop(0f, 0f, 0f, 0f))

    var presetId by mutableStateOf<String?>("in")
    var customW by mutableStateOf(35.0)
    var customH by mutableStateOf(45.0)
    var search by mutableStateOf("")
    var paperId by mutableStateOf("single")

    var bgId by mutableStateOf("white")
    var customR by mutableStateOf(136)
    var customG by mutableStateOf(184)
    var customB by mutableStateOf(232)

    var jpg by mutableStateOf(false)

    var outfitCat by mutableStateOf(PP_OUTFIT_CATEGORIES[0])
    var outfitId by mutableStateOf<String?>(null)
    var outfitScale by mutableStateOf(1f)
    var outfitShift by mutableStateOf(0f)
    var outfitUrls by mutableStateOf<Map<String, String>>(emptyMap())
    var outfitLoading by mutableStateOf(false)

    var view by mutableStateOf(PPViewMode.Result)
    var tab by mutableStateOf(0)
    var cropOpen by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var dlError by mutableStateOf<String?>(null)

    val sizeWmm: Double get() = presetId?.let { ppPreset(it).wMm } ?: customW
    val sizeHmm: Double get() = presetId?.let { ppPreset(it).hMm } ?: customH
    val aspect: Double get() = sizeWmm / sizeHmm
    val outW: Int get() = ppMmToPx(sizeWmm)
    val outH: Int get() = ppMmToPx(sizeHmm)
    val bgDef: PPBackground
        get() = if (bgId == "custom") PPBackground("custom", "Custom", listOf(ppHex(customR, customG, customB)))
        else ppBackground(bgId) ?: PP_BG_CLASSICAL[2]
    val sheet: PPSheet?
        get() {
            val p = ppPaper(paperId)
            return if (p.id == "single") null else ppSheetLayout(p.wMm, p.hMm, sizeWmm, sizeHmm)
        }
}

@Composable
fun PassportPhotoScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    val m = remember { PpModel() }
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }

    // Pasted images land in the shared dropped queue while this tool is open.
    DisposableEffect(Unit) {
        setPasteArmed(true)
        onDispose { setPasteArmed(false) }
    }

    fun startProcessing(f: WebFile) {
        files = listOf(f)
        m.fileName = f.name
        m.stage = PPStage.Processing
        m.progress = "Reading your photo…"
        m.landingError = null
        m.note = null
        pollDroppedQueue() // drain any stale drops so they don't replay later
        scope.launch { processPhoto(m, f, state) }
    }

    // (File pick / drop / paste all arrive through FileZone below, which
    // calls startProcessing when a new file is added.)

    ToolPage(
        IconKind.Users, "Passport Photo Maker",
        "Make a passport-size photo with the right size, background and print sheet — free, on your device."
    ) {
        when (m.stage) {
            PPStage.Landing -> item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        FileZone(state, files, IMAGE_SPEC, "PNG or JPG photo (max 15 MB) — or paste with Ctrl+V") { picked ->
                            val grew = picked.size > files.size
                            files = picked.take(1)
                            if (grew && m.stage == PPStage.Landing) {
                                picked.lastOrNull()?.let { startProcessing(it) }
                            }
                        }
                        Text(
                            "Upload a photo and it is prepared automatically: background removed, face centred and cropped to the passport size. " +
                                "First use downloads the background-removal model (about 40 MB); after that it is cached. Your photo never leaves this device.",
                            fontSize = 12.sp, color = MutedText
                        )
                        if (m.landingError != null) {
                            Text(m.landingError!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }
                }
            }

            PPStage.Processing -> item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(38.dp))
                        Text("Preparing your photo…", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = NavyText)
                        if (m.progress.isNotBlank()) Text(m.progress, fontSize = 12.5.sp, color = MutedText)
                    }
                }
            }

            PPStage.Maker -> item {
                PpMaker(state, m, scope, onNewPhoto = {
                    files = emptyList()
                    m.stage = PPStage.Landing
                    m.outfitId = null
                    m.cropOpen = false
                })
            }
        }
    }
}

private suspend fun processPhoto(m: PpModel, f: WebFile, state: AppState) {
    val dataUrl = loadFileDataUrl(f)
    if (dataUrl == null) {
        m.stage = PPStage.Landing
        m.landingError = "Could not read that file. Please try another photo."
        return
    }
    m.originalUrl = dataUrl

    // Face detection and background removal run in parallel on their own
    // async slots; progress text comes from the removal model download.
    val (faceRes, bgRes) = kotlinx.coroutines.coroutineScope {
        val f = async { faceCall(dataUrl) }
        val b = async { bgCall(dataUrl) { p -> if (p.isNotBlank()) m.progress = p } }
        f.await() to b.await()
    }

    if (bgRes != null && bgRes.asyncOk() && bgRes.asyncStr("dataUrl").isNotBlank()) {
        m.cutoutUrl = bgRes.asyncStr("dataUrl")
        m.bgFailed = false
    } else {
        // Honest fallback: keep the original photo (its own background
        // stays); cropping, sizing and outfits still work.
        m.cutoutUrl = dataUrl
        m.bgFailed = true
    }

    var iw = faceRes?.asyncStr("iw")?.toIntOrNull() ?: 0
    var ih = faceRes?.asyncStr("ih")?.toIntOrNull() ?: 0
    if (iw <= 0 || ih <= 0) {
        imgOp("info", "{\"dataUrl\":\"$dataUrl\"}")
        val info = awaitAsync("img", 30_000)
        iw = info?.asyncStr("w")?.toIntOrNull() ?: 0
        ih = info?.asyncStr("h")?.toIntOrNull() ?: 0
    }
    if (iw <= 0 || ih <= 0) {
        m.stage = PPStage.Landing
        m.landingError = "That photo could not be opened. Please try another one."
        return
    }
    m.imgW = iw
    m.imgH = ih

    val notes = ArrayList<String>()
    val found = faceRes != null && faceRes.asyncOk() && faceRes.asyncStr("found") == "true"
    if (found) {
        val fx = faceRes!!.asyncStr("x").toFloatOrNull() ?: 0f
        val fy = faceRes.asyncStr("y").toFloatOrNull() ?: 0f
        val fw = faceRes.asyncStr("w").toFloatOrNull() ?: 0f
        val fh = faceRes.asyncStr("h").toFloatOrNull() ?: 0f
        m.faceBox = floatArrayOf(fx, fy, fw, fh)
        m.crop = ppFaceCrop(iw, ih, m.aspect, fx, fy, fw, fh)
    } else {
        m.faceBox = null
        m.crop = ppCentreCrop(iw, ih, m.aspect)
        notes.add("No face was detected, so the photo was centred automatically. Use “Crop / Choose area” to frame it exactly.")
    }
    if (m.bgFailed) {
        notes.add("Background removal could not run on this photo, so its original background is kept. Cropping, sizes and outfits still work.")
    }
    m.note = if (notes.isEmpty()) null else notes.joinToString(" ")
    m.stage = PPStage.Maker
    m.view = PPViewMode.Result
}

@Composable
private fun PpMaker(
    state: AppState,
    m: PpModel,
    scope: CoroutineScope,
    onNewPhoto: () -> Unit
) {
    if (m.cropOpen) {
        PpCropEditor(m)
        return
    }
    WhiteCard(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.padding(18.dp)) {
            val wide = maxWidth >= 880.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PpPreview(m)
                        PpMakerActions(m, onNewPhoto)
                    }
                    Column(Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        PpTabs(m, scope)
                        PpDownload(state, m, scope)
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    PpPreview(m)
                    PpMakerActions(m, onNewPhoto)
                    PpTabs(m, scope)
                    PpDownload(state, m, scope)
                }
            }
        }
    }
    if (m.note != null) {
        Spacer(Modifier.height(10.dp))
        Text(m.note!!, fontSize = 12.5.sp, color = MutedText)
    }
}

@Composable
private fun PpMakerActions(m: PpModel, onNewPhoto: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { m.cropOpen = true }) { Text("Crop / Choose area", fontSize = 13.sp) }
        TextButton(onClick = onNewPhoto) { Text("New photo", fontSize = 13.sp) }
    }
}

@Composable
private fun PpPreview(m: PpModel) {
    val originalBmp = remember(m.originalUrl) { if (m.originalUrl.isBlank()) null else decodeBitmapFromDataUrl(m.originalUrl) }
    val cutoutBmp = remember(m.cutoutUrl) { if (m.cutoutUrl.isBlank()) null else decodeBitmapFromDataUrl(m.cutoutUrl) }
    val outfit = PP_OUTFITS.firstOrNull { it.id == m.outfitId }
    val outfitBmp = remember(m.outfitUrls, m.outfitId) {
        outfit?.let { m.outfitUrls[it.id] }?.let { decodeBitmapFromDataUrl(it) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(PPViewMode.Result to "Result", PPViewMode.Cutout to "Cut-out", PPViewMode.Original to "Original").forEach { (mode, label) ->
                FilterChip(
                    selected = m.view == mode,
                    onClick = { m.view = mode },
                    label = { Text(label, fontSize = 12.5.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PurplePrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        when (m.view) {
            PPViewMode.Result -> PpResultPreview(m, cutoutBmp, outfitBmp)
            PPViewMode.Cutout -> PpFitPreview(cutoutBmp, m.imgW, m.imgH, checker = true)
            PPViewMode.Original -> PpOriginalPreview(m, originalBmp)
        }
        Text(
            "Preview — ${ppFmtMm(m.sizeWmm)} × ${ppFmtMm(m.sizeHmm)} mm · ${m.outW} × ${m.outH} px at 300 DPI",
            fontSize = 12.sp, color = MutedText
        )
    }
}

@Composable
private fun PpResultPreview(m: PpModel, cutoutBmp: androidx.compose.ui.graphics.ImageBitmap?, outfitBmp: androidx.compose.ui.graphics.ImageBitmap?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val frameW = min(maxWidth.value, 430f)
        val frameH = (frameW / m.aspect).toFloat()
        val bg = m.bgDef
        val bgMod = if (bg.isGradient) {
            Modifier.background(Brush.verticalGradient(listOf(ppParseHex(bg.colors[0]), ppParseHex(bg.colors[1]))))
        } else {
            Modifier.background(ppParseHex(bg.colors[0]))
        }
        Box(
            Modifier.size(frameW.dp, frameH.dp).clip(RoundedCornerShape(10.dp)).then(bgMod)
                .border(1.dp, Color(0xFFDDD6F3), RoundedCornerShape(10.dp))
        ) {
            if (cutoutBmp != null && m.imgW > 0) {
                val t = ppPreviewTransform(frameW, frameH, m.crop, m.imgW, m.imgH)
                Image(
                    cutoutBmp, contentDescription = "Passport photo preview",
                    modifier = Modifier.offset(t.left.dp, t.top.dp).size(t.imgW.dp, t.imgH.dp),
                    contentScale = ContentScale.FillBounds
                )
            }
            if (outfitBmp != null) {
                val r = ppOutfitRect(m.outW, m.outH, m.outfitScale, m.outfitShift)
                Image(
                    outfitBmp, contentDescription = "Outfit overlay",
                    modifier = Modifier
                        .offset((r.x.toFloat() / m.outW * frameW).dp, (r.y.toFloat() / m.outH * frameH).dp)
                        .size((r.w.toFloat() / m.outW * frameW).dp, (r.h.toFloat() / m.outH * frameH).dp),
                    contentScale = ContentScale.FillBounds
                )
            }
        }
    }
}

@Composable
private fun PpFitPreview(bmp: androidx.compose.ui.graphics.ImageBitmap?, imgW: Int, imgH: Int, checker: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val frameW = min(maxWidth.value, 430f)
        val aspect = if (imgW > 0 && imgH > 0) imgW.toDouble() / imgH else 1.0
        val frameH = (frameW / aspect).toFloat().coerceAtMost(520f)
        Box(
            Modifier.size(frameW.dp, frameH.dp).clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFDDD6F3), RoundedCornerShape(10.dp))
        ) {
            if (checker) {
                Canvas(Modifier.fillMaxSize()) {
                    val cell = 14.dp.toPx()
                    var yy = 0
                    var y = 0f
                    while (y < size.height) {
                        var xx = 0
                        var x = 0f
                        while (x < size.width) {
                            if ((xx + yy) % 2 == 0) drawRect(Color(0xFFE3E1EF), Offset(x, y), androidx.compose.ui.geometry.Size(cell, cell))
                            x += cell; xx++
                        }
                        y += cell; yy++
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(Color(0xFFF1EFFD)))
            }
            if (bmp != null) {
                Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            }
        }
    }
}

@Composable
private fun PpOriginalPreview(m: PpModel, bmp: androidx.compose.ui.graphics.ImageBitmap?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val frameW = min(maxWidth.value, 430f)
        val aspect = if (m.imgW > 0 && m.imgH > 0) m.imgW.toDouble() / m.imgH else 1.0
        val frameH = (frameW / aspect).toFloat().coerceAtMost(520f)
        Box(
            Modifier.size(frameW.dp, frameH.dp).clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF1EFFD))
                .border(1.dp, Color(0xFFDDD6F3), RoundedCornerShape(10.dp))
        ) {
            if (bmp != null) {
                Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            }
            if (m.imgW > 0 && m.crop.w > 0f) {
                Box(
                    Modifier
                        .offset((m.crop.x / m.imgW * frameW).dp, (m.crop.y / m.imgH * frameH).dp)
                        .size((m.crop.w / m.imgW * frameW).dp, (m.crop.h / m.imgH * frameH).dp)
                        .border(2.dp, PurplePrimary, RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PpTabs(m: PpModel, scope: CoroutineScope) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Size", "Background", "Change outfit").forEachIndexed { i, label ->
                FilterChip(
                    selected = m.tab == i,
                    onClick = { m.tab = i },
                    label = { Text(label, fontSize = 12.5.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PurplePrimary,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        when (m.tab) {
            0 -> PpSizeTab(m)
            1 -> PpBackgroundTab(m)
            else -> PpOutfitTab(m, scope)
        }
    }
}

private fun PpModel.reframe() {
    val fb = faceBox
    crop = if (fb != null) ppFaceCrop(imgW, imgH, aspect, fb[0], fb[1], fb[2], fb[3])
    else ppCentreCrop(imgW, imgH, aspect)
}

@Composable
private fun PpSizeTab(m: PpModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = m.search,
            onValueChange = { m.search = it },
            placeholder = { Text("Search country…", fontSize = 13.sp) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(Modifier.fillMaxWidth().height(238.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item {
                Text("Common", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MutedText)
                Spacer(Modifier.height(4.dp))
            }
            items(PASSPORT_COMMON) { p -> PpPresetRow(m, p) }
            item {
                Spacer(Modifier.height(6.dp))
                Text("Countries", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MutedText)
                Spacer(Modifier.height(4.dp))
            }
            items(ppSearchCountries(m.search)) { p -> PpPresetRow(m, p) }
        }

        Text("Custom size", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MutedText)
        var wMmText by remember(m.presetId, m.customW) { mutableStateOf(ppFmtMm(m.sizeWmm)) }
        var hMmText by remember(m.presetId, m.customH) { mutableStateOf(ppFmtMm(m.sizeHmm)) }
        var wPxText by remember(m.outW) { mutableStateOf(m.outW.toString()) }
        var hPxText by remember(m.outH) { mutableStateOf(m.outH.toString()) }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = wMmText,
                onValueChange = { t ->
                    wMmText = t
                    t.toDoubleOrNull()?.let { v -> if (v in 10.0..120.0) { m.presetId = null; m.customW = v; m.reframe() } }
                },
                label = { Text("Width (mm)", fontSize = 11.sp) }, singleLine = true,
                shape = RoundedCornerShape(9.dp), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = hMmText,
                onValueChange = { t ->
                    hMmText = t
                    t.toDoubleOrNull()?.let { v -> if (v in 10.0..120.0) { m.presetId = null; m.customH = v; m.reframe() } }
                },
                label = { Text("Height (mm)", fontSize = 11.sp) }, singleLine = true,
                shape = RoundedCornerShape(9.dp), modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = wPxText,
                onValueChange = { t ->
                    wPxText = t
                    t.toIntOrNull()?.let { v -> if (v in 100..1500) { m.presetId = null; m.customW = ppPxToMm(v); m.reframe() } }
                },
                label = { Text("Width (px)", fontSize = 11.sp) }, singleLine = true,
                shape = RoundedCornerShape(9.dp), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = hPxText,
                onValueChange = { t ->
                    hPxText = t
                    t.toIntOrNull()?.let { v -> if (v in 100..1500) { m.presetId = null; m.customH = ppPxToMm(v); m.reframe() } }
                },
                label = { Text("Height (px)", fontSize = 11.sp) }, singleLine = true,
                shape = RoundedCornerShape(9.dp), modifier = Modifier.weight(1f)
            )
        }
        Text("Pixels are at 300 DPI. Editing any field switches to a custom size.", fontSize = 11.5.sp, color = MutedText)

        LabeledDropdown(
            label = "Paper Size (print sheet)",
            options = PP_PAPER_OPTIONS.map { it.label },
            selected = PP_PAPER_OPTIONS.indexOfFirst { it.id == m.paperId }.coerceAtLeast(0),
            onSelect = { i -> m.paperId = PP_PAPER_OPTIONS[i].id }
        )
        val sh = m.sheet
        Text(
            when {
                sh == null -> "Download gives the single photo."
                sh.count == 0 -> "This photo size does not fit on the chosen paper."
                else -> "Download gives a print sheet with ${sh.count} copies (${sh.cols} × ${sh.rows})."
            },
            fontSize = 12.sp, color = MutedText
        )
    }
}

@Composable
private fun PpPresetRow(m: PpModel, p: PassportPreset) {
    val selected = m.presetId == p.id
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color(0xFFEDE9FE) else Color(0xFFFBFAFF))
            .clickable { m.presetId = p.id; m.reframe() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(p.label, fontSize = 13.sp, color = NavyText, modifier = Modifier.weight(1f), maxLines = 1)
        Text("${p.pxW()} × ${p.pxH()} px", fontSize = 11.5.sp, color = MutedText)
        if (selected) {
            Spacer(Modifier.width(8.dp))
            QrIcon(IconKind.Check, PurplePrimary, size = 16.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PpBackgroundTab(m: PpModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Classical", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MutedText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PP_BG_CLASSICAL.forEach { bg -> PpSwatch(m, bg) }
        }
        Text("Stylish", fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = MutedText)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PP_BG_STYLISH.forEach { bg -> PpSwatch(m, bg) }
            PpSwatch(m, PPBackground("custom", "Custom", listOf(ppHex(m.customR, m.customG, m.customB))))
        }
        if (m.bgId == "custom") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PpRgbSlider("Red", m.customR) { m.customR = it }
                PpRgbSlider("Green", m.customG) { m.customG = it }
                PpRgbSlider("Blue", m.customB) { m.customB = it }
                Text("Colour: ${ppHex(m.customR, m.customG, m.customB)}", fontSize = 12.sp, color = MutedText)
            }
        }
        if (m.bgFailed) {
            Text(
                "Note: this photo kept its original background, so colour choices will not show on it.",
                fontSize = 12.sp, color = MutedText
            )
        }
    }
}

@Composable
private fun PpRgbSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 12.sp, color = MutedText, modifier = Modifier.width(48.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f)
        )
        Text(value.toString(), fontSize = 12.sp, color = NavyText, modifier = Modifier.width(30.dp))
    }
}

@Composable
private fun PpSwatch(m: PpModel, bg: PPBackground) {
    val selected = m.bgId == bg.id
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
        val mod = if (bg.isGradient) {
            Modifier.background(Brush.verticalGradient(listOf(ppParseHex(bg.colors[0]), ppParseHex(bg.colors[1]))))
        } else {
            Modifier.background(ppParseHex(bg.colors[0]))
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).then(mod)
                .border(
                    if (selected) 2.5.dp else 1.dp,
                    if (selected) PurplePrimary else Color(0xFFD8D2EA),
                    RoundedCornerShape(10.dp)
                )
                .clickable { m.bgId = bg.id }
        )
        Spacer(Modifier.height(4.dp))
        Text(bg.name, fontSize = 10.5.sp, color = if (selected) PurplePrimary else MutedText, maxLines = 2)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PpOutfitTab(m: PpModel, scope: CoroutineScope) {
    // Load this category's overlay PNGs (as data URLs) on first view.
    LaunchedEffect(m.outfitCat) {
        val missing = PP_OUTFITS.filter { it.category == m.outfitCat && it.id !in m.outfitUrls }
        if (missing.isNotEmpty()) {
            m.outfitLoading = true
            for (o in missing) {
                val url = fetchResourceDataUrl(o.path)
                if (url != null) m.outfitUrls = m.outfitUrls + (o.id to url)
            }
            m.outfitLoading = false
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = m.outfitId == null,
                onClick = { m.outfitId = null },
                label = { Text("None", fontSize = 12.5.sp) },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                )
            )
            PP_OUTFIT_CATEGORIES.forEach { cat ->
                FilterChip(
                    selected = m.outfitId != null && m.outfitCat == cat,
                    onClick = { m.outfitCat = cat },
                    label = { Text(cat, fontSize = 12.5.sp) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                    )
                )
            }
        }
        if (m.outfitLoading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Loading outfits…", fontSize = 12.5.sp, color = MutedText)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PP_OUTFITS.filter { it.category == m.outfitCat }.forEach { o ->
                val bmp = remember(m.outfitUrls, o.id) { m.outfitUrls[o.id]?.let { decodeBitmapFromDataUrl(it) } }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
                    Box(
                        Modifier.size(76.dp, 57.dp).clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE8F0FA))
                            .border(
                                if (m.outfitId == o.id) 2.5.dp else 1.dp,
                                if (m.outfitId == o.id) PurplePrimary else Color(0xFFD8D2EA),
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { m.outfitId = o.id }
                    ) {
                        if (bmp != null) {
                            Image(bmp, contentDescription = o.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(o.name, fontSize = 10.5.sp, color = if (m.outfitId == o.id) PurplePrimary else MutedText, maxLines = 2)
                }
            }
        }
        if (m.outfitId != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Outfit size: ${(m.outfitScale * 100).roundToInt()}%", fontSize = 12.5.sp, color = MutedText)
                Slider(value = m.outfitScale, onValueChange = { m.outfitScale = it }, valueRange = 0.8f..1.3f)
                Text("Outfit position", fontSize = 12.5.sp, color = MutedText)
                Slider(value = m.outfitShift, onValueChange = { m.outfitShift = it }, valueRange = -0.08f..0.08f)
            }
        }
        Text(
            "Outfits are artwork templates placed over your photo. Pick one, then use the size and position sliders so it sits naturally on the shoulders.",
            fontSize = 11.5.sp, color = MutedText
        )
    }
}

@Composable
private fun PpDownload(state: AppState, m: PpModel, scope: CoroutineScope) {
    WhiteCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Format:", fontSize = 13.sp, color = MutedText)
                listOf(false to "PNG", true to "JPG").forEach { (isJpg, label) ->
                    FilterChip(
                        selected = m.jpg == isJpg,
                        onClick = { m.jpg = isJpg },
                        label = { Text(label, fontSize = 12.5.sp) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                        )
                    )
                }
            }
            PrimaryToolButton(
                text = if (m.sheet == null) "Download Photo" else "Download Print Sheet",
                enabled = m.sheet?.count != 0,
                busy = m.busy
            ) { scope.launch { ppDownload(state, m) } }
            if (m.dlError != null) Text(m.dlError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.5.sp)
            Text(
                "Free download — no sign-in, no credits. " +
                    if (m.sheet == null) "Single photo, ${m.outW} × ${m.outH} px at 300 DPI."
                    else "Print sheet with ${m.sheet!!.count} copies at 300 DPI.",
                fontSize = 11.5.sp, color = MutedText
            )
        }
    }
}

private suspend fun ppDownload(state: AppState, m: PpModel) {
    m.busy = true
    m.dlError = null
    try {
        val mime = if (m.jpg) "image/jpeg" else "image/png"
        val ext = if (m.jpg) "jpg" else "png"
        val bg = m.bgDef
        val bgJson = if (bg.isGradient) {
            "\"bgGrad\":[\"${bg.colors[0]}\",\"${bg.colors[1]}\"]"
        } else {
            "\"bg\":\"${bg.colors[0]}\""
        }
        val outfit = PP_OUTFITS.firstOrNull { it.id == m.outfitId }
        val outfitUrl = outfit?.let { m.outfitUrls[it.id] }
        val outfitJson = if (outfit != null && outfitUrl != null) {
            val r = ppOutfitRect(m.outW, m.outH, m.outfitScale, m.outfitShift)
            ",\"outfit\":\"$outfitUrl\",\"outfitX\":${r.x},\"outfitY\":${r.y},\"outfitW\":${r.w},\"outfitH\":${r.h}"
        } else ""
        val args = "{\"dataUrl\":\"${m.cutoutUrl}\",\"cropX\":${m.crop.x},\"cropY\":${m.crop.y}," +
            "\"cropW\":${m.crop.w},\"cropH\":${m.crop.h},\"outW\":${m.outW},\"outH\":${m.outH}," +
            "$bgJson$outfitJson,\"mime\":\"$mime\",\"quality\":0.92}"
        val res = passportCall("renderPhoto", args)
        if (res == null || !res.asyncOk()) {
            m.dlError = res?.asyncError() ?: "Rendering timed out. Please try again."
            return
        }
        val photoUrl = res.asyncStr("dataUrl")
        val sheet = m.sheet
        if (sheet == null) {
            val name = "passport-photo-${m.presetId ?: "custom"}-${ppFmtMm(m.sizeWmm)}x${ppFmtMm(m.sizeHmm)}mm.$ext"
            downloadFileBase64(name, photoUrl.substringAfter(",", ""), mime)
            recordHistory("Passport Photo", "Passport photo ${ppFmtMm(m.sizeWmm)}×${ppFmtMm(m.sizeHmm)} mm", m.fileName)
            state.toast("Downloaded $name")
        } else {
            val cellsJson = sheet.cells.joinToString(",") { "{\"x\":${it.x},\"y\":${it.y},\"w\":${it.w},\"h\":${it.h}}" }
            val sArgs = "{\"photoDataUrl\":\"$photoUrl\",\"paperW\":${sheet.paperPxW},\"paperH\":${sheet.paperPxH}," +
                "\"cells\":[$cellsJson],\"mime\":\"$mime\",\"quality\":0.92}"
            val sRes = passportCall("renderSheet", sArgs)
            if (sRes == null || !sRes.asyncOk()) {
                m.dlError = sRes?.asyncError() ?: "Sheet rendering timed out. Please try again."
                return
            }
            val name = "passport-sheet-${m.paperId}.$ext"
            downloadFileBase64(name, sRes.asyncStr("dataUrl").substringAfter(",", ""), mime)
            recordHistory("Passport Photo", "Print sheet (${sheet.count} copies)", m.fileName)
            state.toast("Downloaded $name")
        }
    } finally {
        m.busy = false
    }
}

@Composable
private fun PpCropEditor(m: PpModel) {
    val backup = remember { m.crop }
    val bmp = remember(m.originalUrl) { if (m.originalUrl.isBlank()) null else decodeBitmapFromDataUrl(m.originalUrl) }
    WhiteCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Crop / Choose area", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NavyText)
            Text(
                "Drag the frame to move it, or drag its corners and edges to resize. The frame keeps the passport shape.",
                fontSize = 12.5.sp, color = MutedText
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val ds = min(maxWidth.value / max(1, m.imgW), 520f / max(1, m.imgH))
                val boxW = m.imgW * ds
                val boxH = m.imgH * ds
                Box(
                    Modifier.size(boxW.dp, boxH.dp).clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF1EFFD))
                        .pointerInput(m.imgW, m.imgH) {
                            awaitPointerEventScope {
                                while (true) {
                                    var down = awaitPointerEvent()
                                    while (down.type != PointerEventType.Press) down = awaitPointerEvent()
                                    val change = down.changes.firstOrNull() ?: continue
                                    val pw = size.width.toFloat()
                                    val ph = size.height.toFloat()
                                    if (pw <= 0f || ph <= 0f) continue
                                    fun toSrc(o: Offset) = (o.x / pw * m.imgW) to (o.y / ph * m.imgH)
                                    val (sx, sy) = toSrc(change.position)
                                    val c = m.crop
                                    val handles = listOf(
                                        c.x to c.y, (c.x + c.w) to c.y, c.x to (c.y + c.h), (c.x + c.w) to (c.y + c.h),
                                        (c.x + c.w / 2) to c.y, (c.x + c.w) to (c.y + c.h / 2),
                                        (c.x + c.w / 2) to (c.y + c.h), c.x to (c.y + c.h / 2)
                                    )
                                    val tol = 30f / (pw / max(1, m.imgW)) * density
                                    var mode = -2 // -1 = move, 0..7 = handle
                                    var best = Float.MAX_VALUE
                                    handles.forEachIndexed { i, hp ->
                                        val d = abs(hp.first - sx) + abs(hp.second - sy)
                                        if (d < best) { best = d; if (d <= tol * 2) mode = i }
                                    }
                                    if (mode == -2 && sx >= c.x && sx <= c.x + c.w && sy >= c.y && sy <= c.y + c.h) mode = -1
                                    if (mode == -2) continue
                                    val startCrop = c
                                    var last = change.position
                                    var totalDx = 0f
                                    var totalDy = 0f
                                    var released = false
                                    while (!released) {
                                        val ev = awaitPointerEvent()
                                        val ch = ev.changes.firstOrNull() ?: break
                                        when (ev.type) {
                                            PointerEventType.Move -> {
                                                totalDx += (ch.position.x - last.x) / pw * m.imgW
                                                totalDy += (ch.position.y - last.y) / ph * m.imgH
                                                last = ch.position
                                                m.crop = if (mode == -1) {
                                                    ppCropMove(startCrop, totalDx, totalDy, m.imgW, m.imgH)
                                                } else {
                                                    ppCropResize(startCrop, mode, totalDx, totalDy, m.imgW, m.imgH)
                                                }
                                            }
                                            PointerEventType.Release, PointerEventType.Exit -> released = true
                                            else -> {}
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    if (bmp != null) {
                        Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
                    }
                    // Dim outside the frame + rule-of-thirds grid inside it.
                    Canvas(Modifier.fillMaxSize()) {
                        val fx = m.crop.x / m.imgW * size.width
                        val fy = m.crop.y / m.imgH * size.height
                        val fw = m.crop.w / m.imgW * size.width
                        val fh = m.crop.h / m.imgH * size.height
                        val dim = Color(0x73000000)
                        drawRect(dim, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, fy))
                        drawRect(dim, Offset(0f, fy + fh), androidx.compose.ui.geometry.Size(size.width, size.height - fy - fh))
                        drawRect(dim, Offset(0f, fy), androidx.compose.ui.geometry.Size(fx, fh))
                        drawRect(dim, Offset(fx + fw, fy), androidx.compose.ui.geometry.Size(size.width - fx - fw, fh))
                        val grid = Color(0x8CFFFFFF)
                        for (i in 1..2) {
                            drawLine(grid, Offset(fx + fw * i / 3, fy), Offset(fx + fw * i / 3, fy + fh), strokeWidth = 1f)
                            drawLine(grid, Offset(fx, fy + fh * i / 3), Offset(fx + fw, fy + fh * i / 3), strokeWidth = 1f)
                        }
                    }
                    Box(
                        Modifier
                            .offset((m.crop.x / m.imgW * boxW).dp, (m.crop.y / m.imgH * boxH).dp)
                            .size((m.crop.w / m.imgW * boxW).dp, (m.crop.h / m.imgH * boxH).dp)
                            .border(2.dp, PurplePrimary, RoundedCornerShape(3.dp))
                    )
                    val handlePts = listOf(
                        m.crop.x to m.crop.y, (m.crop.x + m.crop.w) to m.crop.y,
                        m.crop.x to (m.crop.y + m.crop.h), (m.crop.x + m.crop.w) to (m.crop.y + m.crop.h),
                        (m.crop.x + m.crop.w / 2) to m.crop.y, (m.crop.x + m.crop.w) to (m.crop.y + m.crop.h / 2),
                        (m.crop.x + m.crop.w / 2) to (m.crop.y + m.crop.h), m.crop.x to (m.crop.y + m.crop.h / 2)
                    )
                    handlePts.forEach { hp ->
                        Box(
                            Modifier
                                .offset((hp.first / m.imgW * boxW).dp - 6.dp, (hp.second / m.imgH * boxH).dp - 6.dp)
                                .size(12.dp)
                                .background(Color.White, RoundedCornerShape(3.dp))
                                .border(1.5.dp, PurplePrimary, RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { m.crop = backup; m.cropOpen = false }) { Text("Cancel", fontSize = 13.sp) }
                Button(
                    onClick = { m.cropOpen = false },
                    shape = RoundedCornerShape(11.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                ) { Text("Done", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            }
        }
    }
}
