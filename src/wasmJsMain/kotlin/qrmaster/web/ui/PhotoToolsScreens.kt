package qrmaster.web.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

// ── Photo Tools (Item 7) ─────────────────────────────────────────────────
// One-job photo tools for the shop counter, all running on the browser's
// own Canvas via the shell's __qmImgOp ops — no third-party editor
// library, no uploads: the file never leaves the device. The exact-KB
// compressor measures real encoded bytes (toBlob) until they fit the
// target; the signature maker separates ink from paper with an Otsu
// threshold and returns white + transparent versions.

/** Shared result block: preview, an info line and the download button. */
@Composable
private fun PhotoResultBlock(
    state: AppState,
    resultUrl: String?,
    resultName: String,
    resultMime: String,
    resultInfo: String?,
    checker: Boolean = false
) {
    val bmp = remember(resultUrl) { resultUrl?.let { decodeBitmapFromDataUrl(it) } }
    if (bmp != null) {
        Image(
            bmp, contentDescription = "Result",
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (checker) Color(0xFFE2E0EE) else Color(0xFFF3F2FA))
        )
    }
    if (resultUrl != null) {
        if (resultInfo != null) Text(resultInfo, fontSize = 12.5.sp, color = MutedText)
        Button(
            onClick = {
                downloadFileBase64(resultName, resultUrl.substringAfter(",", ""), resultMime)
                state.toast("Downloaded $resultName")
            },
            shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) { Text("Download $resultName", fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun BusyRow(text: String = "Working…") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, fontSize = 13.sp, color = MutedText)
    }
}

@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label, fontSize = 12.sp) },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                )
            )
        }
    }
}

/** Loads the picked file once: data URL + natural dimensions. */
@Composable
private fun rememberPhotoSource(
    file: WebFile?,
    key: String,
    onLoaded: (String, Int, Int) -> Unit
) {
    LaunchedEffect(file?.id) {
        val f = file ?: return@LaunchedEffect
        val dataUrl = loadFileDataUrl(f) ?: return@LaunchedEffect
        imgOpKeyed("info", "{\"dataUrl\":\"$dataUrl\"}", key)
        val res = awaitAsync(key, 30_000)
        if (res != null && res.asyncOk()) {
            onLoaded(dataUrl, res.asyncStr("w").toIntOrNull() ?: 0, res.asyncStr("h").toIntOrNull() ?: 0)
        } else {
            onLoaded(dataUrl, 0, 0)
        }
    }
}

// ── (a) Compress Image to an exact KB target ─────────────────────────────

@Composable
fun CompressKbScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var srcUrl by remember { mutableStateOf<String?>(null) }
    var dims by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var preset by remember { mutableStateOf("50") }
    var customKb by remember { mutableStateOf("") }
    var format by remember { mutableStateOf("image/jpeg") }
    var busy by remember { mutableStateOf(false) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var resultInfo by remember { mutableStateOf<String?>(null) }
    var warning by remember { mutableStateOf<String?>(null) }
    val file = files.firstOrNull()
    val targetKb = if (preset == "custom") customKb.toIntOrNull() ?: 0 else preset.toIntOrNull() ?: 50

    rememberPhotoSource(file, "imgkb:info") { url, w, h ->
        srcUrl = url
        dims = if (w > 0) w to h else null
        resultUrl = null; resultInfo = null; warning = null
    }

    ToolPage(IconKind.Compress, "Compress Image to KB", "All Tools › Photo Tools › Compress to KB — shrink a photo to an exact file size, for form uploads and portals with KB limits.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_SPEC, "PNG, JPG or WebP (max 15 MB)") { files = it.take(1) }
                    val d = dims
                    if (file != null && d != null) {
                        Text("${file.name} — ${d.first} × ${d.second} px • ${formatBytes(file.size)}",
                            fontSize = 12.5.sp, color = MutedText)
                    }
                    Text("Target size:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    ChipRow(
                        listOf("20" to "20 KB", "50" to "50 KB", "100" to "100 KB", "200" to "200 KB", "custom" to "Custom"),
                        preset
                    ) { preset = it }
                    if (preset == "custom") {
                        OutlinedTextField(
                            value = customKb,
                            onValueChange = { customKb = it.filter { c -> c.isDigit() }.take(5) },
                            label = { Text("Target size (KB)") }, singleLine = true,
                            shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text("Output format:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    ChipRow(listOf("image/jpeg" to "JPG (smallest)", "image/webp" to "WebP"), format) { format = it }
                    PrimaryToolButton(
                        "Compress to $targetKb KB",
                        enabled = file != null && srcUrl != null && targetKb >= 5, busy = busy
                    ) {
                        val url = srcUrl ?: return@PrimaryToolButton
                        busy = true; warning = null; resultUrl = null
                        scope.launch {
                            val args = buildJsonObject {
                                put("dataUrl", url); put("targetKB", targetKb); put("mime", format)
                            }.toString()
                            imgOpKeyed("targetSize", args, "imgkb")
                            val res = awaitAsync("imgkb", 180_000)
                            busy = false
                            if (res != null && res.asyncOk()) {
                                resultUrl = res.asyncStr("dataUrl")
                                val bytes = res.asyncStr("bytes").toLongOrNull() ?: 0L
                                val met = res.asyncStr("met") == "true"
                                resultInfo = "${formatBytes(file?.size ?: 0)} → ${formatBytes(bytes)} " +
                                    "(${res.asyncStr("w")} × ${res.asyncStr("h")} px)"
                                if (!met) {
                                    warning = "This photo could not quite reach $targetKb KB — " +
                                        "${formatBytes(bytes)} is the smallest it can go without becoming unusable."
                                }
                                recordHistory("Compress to KB", "Compressed to ${formatBytes(bytes)}", file?.name ?: "")
                            } else {
                                state.toast(res?.asyncError() ?: "Compression failed")
                            }
                        }
                    }
                    if (busy) BusyRow("Compressing — trying sizes until it fits…")
                    if (warning != null) Text(warning!!, fontSize = 12.5.sp, color = Color(0xFFB45309))
                    PhotoResultBlock(
                        state, resultUrl,
                        (file?.name?.substringBeforeLast(".") ?: "photo") +
                            "-${targetKb}kb." + if (format == "image/webp") "webp" else "jpg",
                        format, resultInfo
                    )
                }
            }
        }
    }
}

// ── (b) Signature Maker ──────────────────────────────────────────────────

@Composable
fun SignatureMakerScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var srcUrl by remember { mutableStateOf<String?>(null) }
    var autoThr by remember { mutableStateOf(true) }
    var threshold by remember { mutableStateOf(180f) }
    var busy by remember { mutableStateOf(false) }
    var whiteUrl by remember { mutableStateOf<String?>(null) }
    var transUrl by remember { mutableStateOf<String?>(null) }
    var resultInfo by remember { mutableStateOf<String?>(null) }
    val file = files.firstOrNull()

    rememberPhotoSource(file, "imgsig:info") { url, _, _ ->
        srcUrl = url
        whiteUrl = null; transUrl = null; resultInfo = null
    }

    ToolPage(IconKind.Edit, "Signature Maker", "All Tools › Photo Tools › Signature Maker — turn a photo of a signature on paper into a clean signature for forms and documents.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_SPEC, "A clear photo of the signature on plain paper (max 15 MB)") { files = it.take(1) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoThr, onCheckedChange = { autoThr = it })
                        Text("Automatic ink detection", fontSize = 12.5.sp, color = NavyText)
                    }
                    if (!autoThr) {
                        Text("Ink cutoff: ${threshold.toInt()} — lower keeps only dark ink, higher also keeps faint strokes.",
                            fontSize = 12.5.sp, color = MutedText)
                        Slider(value = threshold, onValueChange = { threshold = it }, valueRange = 90f..235f)
                    }
                    PrimaryToolButton("Extract Signature", enabled = file != null && srcUrl != null, busy = busy) {
                        val url = srcUrl ?: return@PrimaryToolButton
                        busy = true
                        scope.launch {
                            val args = buildJsonObject {
                                put("dataUrl", url)
                                if (!autoThr) put("threshold", threshold.toInt())
                            }.toString()
                            imgOpKeyed("signature", args, "imgsig")
                            val res = awaitAsync("imgsig", 120_000)
                            busy = false
                            if (res != null && res.asyncOk()) {
                                whiteUrl = res.asyncStr("white")
                                transUrl = res.asyncStr("transparent")
                                resultInfo = "Signature found and trimmed to ${res.asyncStr("w")} × ${res.asyncStr("h")} px."
                                recordHistory("Signature Maker", "Signature extracted", file?.name ?: "")
                            } else {
                                state.toast(res?.asyncError() ?: "Could not extract the signature")
                            }
                        }
                    }
                    if (busy) BusyRow("Finding the ink…")
                    if (resultInfo != null) Text(resultInfo!!, fontSize = 12.5.sp, color = MutedText)
                    if (whiteUrl != null) {
                        Text("On white (for printing and forms):", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                        PhotoResultBlock(state, whiteUrl, "signature-white.png", "image/png", null)
                    }
                    if (transUrl != null) {
                        Text("Transparent (paste onto documents and PDFs):", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                        PhotoResultBlock(state, transUrl, "signature-transparent.png", "image/png", null, checker = true)
                    }
                    Text("Tip: blue or black pen on plain white paper, photographed straight from above in good light, gives the cleanest result.",
                        fontSize = 11.5.sp, color = MutedText)
                }
            }
        }
    }
}

// ── (d) Crop, Rotate & Flip ──────────────────────────────────────────────

@Composable
fun CropFlipScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var srcUrl by remember { mutableStateOf<String?>(null) }
    var dims by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var rotate by remember { mutableStateOf(0) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }
    var cropL by remember { mutableStateOf(0f) }
    var cropT by remember { mutableStateOf(0f) }
    var cropR by remember { mutableStateOf(0f) }
    var cropB by remember { mutableStateOf(0f) }
    var format by remember { mutableStateOf("image/png") }
    var busy by remember { mutableStateOf(false) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var resultInfo by remember { mutableStateOf<String?>(null) }
    val file = files.firstOrNull()
    val srcBitmap = remember(srcUrl) { srcUrl?.let { decodeBitmapFromDataUrl(it) } }

    rememberPhotoSource(file, "imgcrop:info") { url, w, h ->
        srcUrl = url
        dims = if (w > 0) w to h else null
        resultUrl = null; resultInfo = null
        rotate = 0; flipH = false; flipV = false
        cropL = 0f; cropT = 0f; cropR = 0f; cropB = 0f
    }

    fun cropPx(): IntArray? {
        val d = dims ?: return null
        val x = (d.first * cropL / 100).roundToInt()
        val y = (d.second * cropT / 100).roundToInt()
        val w = d.first - x - (d.first * cropR / 100).roundToInt()
        val h = d.second - y - (d.second * cropB / 100).roundToInt()
        return intArrayOf(x, y, w, h)
    }

    ToolPage(IconKind.Swap, "Crop, Rotate & Flip", "All Tools › Photo Tools › Crop, Rotate & Flip — trim the edges, turn the photo and mirror it, in one pass.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_SPEC, "PNG, JPG or WebP (max 15 MB)") { files = it.take(1) }
                    val d = dims
                    if (srcBitmap != null) {
                        Image(
                            srcBitmap, contentDescription = "Original photo",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F2FA))
                                .graphicsLayer {
                                    rotationZ = rotate.toFloat()
                                    scaleX = if (flipH) -1f else 1f
                                    scaleY = if (flipV) -1f else 1f
                                }
                        )
                        Text("Preview shows rotation and flips. Cropping is applied exactly, in pixels, when you tap Apply.",
                            fontSize = 11.5.sp, color = MutedText)
                    }
                    if (d != null) Text("Original: ${d.first} × ${d.second} px", fontSize = 12.5.sp, color = MutedText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = false, onClick = { rotate = (rotate + 90) % 360 },
                            label = { Text("Rotate 90° (now $rotate°)", fontSize = 12.sp) },
                            shape = RoundedCornerShape(20.dp)
                        )
                        FilterChip(
                            selected = flipH, onClick = { flipH = !flipH },
                            label = { Text("Flip ↔", fontSize = 12.sp) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                            )
                        )
                        FilterChip(
                            selected = flipV, onClick = { flipV = !flipV },
                            label = { Text("Flip ↕", fontSize = 12.sp) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                            )
                        )
                    }
                    Text("Quick crop:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    ChipRow(
                        listOf("0" to "No crop", "10" to "Centre 80%", "20" to "Centre 60%"),
                        when {
                            cropL == 0f && cropT == 0f && cropR == 0f && cropB == 0f -> "0"
                            cropL == 10f -> "10"
                            cropL == 20f -> "20"
                            else -> ""
                        }
                    ) { v ->
                        val p = v.toFloatOrNull() ?: 0f
                        cropL = p; cropT = p; cropR = p; cropB = p
                    }
                    listOf(
                        "Left" to cropL, "Top" to cropT, "Right" to cropR, "Bottom" to cropB
                    ).forEach { (label, value) ->
                        Text("Crop $label edge: ${value.toInt()}%", fontSize = 12.5.sp, color = MutedText)
                        Slider(
                            value = value,
                            onValueChange = { nv ->
                                when (label) {
                                    "Left" -> cropL = nv; "Top" -> cropT = nv
                                    "Right" -> cropR = nv; else -> cropB = nv
                                }
                            },
                            valueRange = 0f..45f
                        )
                    }
                    val cp = cropPx()
                    if (d != null && cp != null) {
                        Text("Crop area: ${cp[2]} × ${cp[3]} px (from ${cp[0]}, ${cp[1]})",
                            fontSize = 12.5.sp, color = MutedText)
                    }
                    Text("Output format:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    ChipRow(listOf("image/png" to "PNG", "image/jpeg" to "JPG"), format) { format = it }
                    PrimaryToolButton("Apply Crop / Rotate / Flip", enabled = file != null && srcUrl != null, busy = busy) {
                        val url = srcUrl ?: return@PrimaryToolButton
                        val c = cropPx() ?: return@PrimaryToolButton
                        if (c[2] < 8 || c[3] < 8) { state.toast("The crop area is too small"); return@PrimaryToolButton }
                        busy = true
                        scope.launch {
                            val args = buildJsonObject {
                                put("dataUrl", url)
                                put("x", c[0]); put("y", c[1]); put("w", c[2]); put("h", c[3])
                                put("rotate", rotate); put("flipH", flipH); put("flipV", flipV)
                                put("mime", format)
                            }.toString()
                            imgOpKeyed("transform", args, "imgcrop")
                            val res = awaitAsync("imgcrop", 120_000)
                            busy = false
                            if (res != null && res.asyncOk()) {
                                resultUrl = res.asyncStr("dataUrl")
                                resultInfo = "Result: ${res.asyncStr("w")} × ${res.asyncStr("h")} px"
                                recordHistory("Crop & Flip", "Photo transformed", file?.name ?: "")
                            } else {
                                state.toast(res?.asyncError() ?: "Transform failed")
                            }
                        }
                    }
                    if (busy) BusyRow()
                    PhotoResultBlock(
                        state, resultUrl,
                        (file?.name?.substringBeforeLast(".") ?: "photo") + "-edited." +
                            if (format == "image/jpeg") "jpg" else "png",
                        format, resultInfo
                    )
                }
            }
        }
    }
}

// ── (f) Join / Collage photos ────────────────────────────────────────────

@Composable
fun JoinPhotosScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var layout by remember { mutableStateOf("h") }
    var gap by remember { mutableStateOf(10f) }
    var bg by remember { mutableStateOf("#ffffff") }
    var busy by remember { mutableStateOf(false) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var resultInfo by remember { mutableStateOf<String?>(null) }

    ToolPage(IconKind.Grid, "Join Photos", "All Tools › Photo Tools › Join Photos — put two or more photos into one image, side by side, stacked or in a grid.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_BATCH_SPEC, "Two or more JPG, PNG or WebP photos (20 MB each)") { newFiles ->
                        files = newFiles
                        resultUrl = null
                    }
                    Text("Layout:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    ChipRow(
                        listOf("h" to "Side by Side", "v" to "Stacked", "grid" to "Grid (2 columns)"),
                        layout
                    ) { layout = it }
                    Text("Gap between photos: ${gap.toInt()} px", fontSize = 12.5.sp, color = MutedText)
                    Slider(value = gap, onValueChange = { gap = it }, valueRange = 0f..40f)
                    Text("Gap colour:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    ChipRow(
                        listOf("#ffffff" to "White", "#111827" to "Black", "#E5E7EB" to "Light Grey"),
                        bg
                    ) { bg = it }
                    PrimaryToolButton(
                        "Join ${files.size} Photos",
                        enabled = files.size >= 2, busy = busy
                    ) {
                        busy = true
                        scope.launch {
                            val urls = ArrayList<String>()
                            var failed = false
                            for (f in files) {
                                val u = loadFileDataUrl(f)
                                if (u == null) { failed = true; break }
                                urls.add(u)
                            }
                            if (failed) { busy = false; state.toast("Could not read one of the photos"); return@launch }
                            val args = buildJsonObject {
                                put("images", kotlinx.serialization.json.buildJsonArray {
                                    urls.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) }
                                })
                                put("layout", layout); put("gap", gap.toInt()); put("bg", bg)
                                put("mime", "image/png")
                            }.toString()
                            imgOpKeyed("join", args, "imgjoin")
                            val res = awaitAsync("imgjoin", 180_000)
                            busy = false
                            if (res != null && res.asyncOk()) {
                                resultUrl = res.asyncStr("dataUrl")
                                resultInfo = "Joined image: ${res.asyncStr("w")} × ${res.asyncStr("h")} px"
                                recordHistory("Join Photos", "Photos joined", "${files.size} photos")
                            } else {
                                state.toast(res?.asyncError() ?: "Join failed")
                            }
                        }
                    }
                    if (busy) BusyRow("Joining…")
                    PhotoResultBlock(state, resultUrl, "joined-photos.png", "image/png", resultInfo)
                }
            }
        }
    }
}

// ── (g) Light Photo Editor ───────────────────────────────────────────────

@Composable
fun PhotoEditorScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var srcUrl by remember { mutableStateOf<String?>(null) }
    var brightness by remember { mutableStateOf(0f) }
    var contrast by remember { mutableStateOf(0f) }
    var saturation by remember { mutableStateOf(0f) }
    var filter by remember { mutableStateOf("none") }
    var text by remember { mutableStateOf("") }
    var textPos by remember { mutableStateOf("bottom") }
    var textColor by remember { mutableStateOf("#111827") }
    var busy by remember { mutableStateOf(false) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var resultInfo by remember { mutableStateOf<String?>(null) }
    val file = files.firstOrNull()
    val srcBitmap = remember(srcUrl) { srcUrl?.let { decodeBitmapFromDataUrl(it) } }

    rememberPhotoSource(file, "imgedit:info") { url, _, _ ->
        srcUrl = url
        resultUrl = null; resultInfo = null
    }

    ToolPage(IconKind.Star, "Photo Editor", "All Tools › Photo Tools › Photo Editor — brightness, contrast, colour and simple filters, plus a text caption. Adjustments always apply to the original, so you can retry freely.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_SPEC, "PNG, JPG or WebP (max 15 MB)") { files = it.take(1) }
                    if (srcBitmap != null) {
                        Image(
                            srcBitmap, contentDescription = "Original photo",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                                .clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F2FA))
                        )
                    }
                    listOf(
                        Triple("Brightness", brightness, { v: Float -> brightness = v }),
                        Triple("Contrast", contrast, { v: Float -> contrast = v }),
                        Triple("Saturation (colour)", saturation, { v: Float -> saturation = v })
                    ).forEach { (label, value, onChange) ->
                        Text("$label: ${value.toInt()}", fontSize = 12.5.sp, color = MutedText)
                        Slider(value = value, onValueChange = onChange, valueRange = -100f..100f)
                    }
                    Text("Filter:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    ChipRow(
                        listOf(
                            "none" to "None", "bw" to "Black & White", "sepia" to "Sepia",
                            "warm" to "Warm", "cool" to "Cool", "vivid" to "Vivid"
                        ),
                        filter
                    ) { filter = it }
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text("Caption text (optional)") }, singleLine = true,
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                    )
                    if (text.isNotBlank()) {
                        Text("Caption position:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                        ChipRow(
                            listOf("top" to "Top", "centre" to "Centre", "bottom" to "Bottom"),
                            textPos
                        ) { textPos = it }
                        Text("Caption colour:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                        ChipRow(
                            listOf("#111827" to "Black", "#ffffff" to "White", "#DC2626" to "Red", "#2563EB" to "Blue"),
                            textColor
                        ) { textColor = it }
                    }
                    PrimaryToolButton("Apply & Preview", enabled = file != null && srcUrl != null, busy = busy) {
                        val url = srcUrl ?: return@PrimaryToolButton
                        busy = true
                        scope.launch {
                            val sat = saturation + if (filter == "vivid") 45f else 0f
                            val con = contrast + if (filter == "vivid") 12f else 0f
                            val args = buildJsonObject {
                                put("dataUrl", url)
                                put("brightness", brightness.roundToInt())
                                put("contrast", con.roundToInt())
                                put("saturation", sat.roundToInt())
                                put("grayscale", filter == "bw")
                                put("sepia", filter == "sepia")
                                put("warmth", when (filter) { "warm" -> 45; "cool" -> -45; else -> 0 })
                                if (text.isNotBlank()) {
                                    put("text", text)
                                    put("textColor", textColor)
                                    put("textX", 0.5)
                                    put("textY", when (textPos) { "top" -> 0.08; "centre" -> 0.5; else -> 0.92 })
                                }
                                put("mime", "image/png")
                            }.toString()
                            imgOpKeyed("edit", args, "imgedit")
                            val res = awaitAsync("imgedit", 120_000)
                            busy = false
                            if (res != null && res.asyncOk()) {
                                resultUrl = res.asyncStr("dataUrl")
                                resultInfo = "Edited photo: ${res.asyncStr("w")} × ${res.asyncStr("h")} px — download it, or adjust and apply again."
                                recordHistory("Photo Editor", "Photo edited", file?.name ?: "")
                            } else {
                                state.toast(res?.asyncError() ?: "Edit failed")
                            }
                        }
                    }
                    if (busy) BusyRow()
                    PhotoResultBlock(
                        state, resultUrl,
                        (file?.name?.substringBeforeLast(".") ?: "photo") + "-edited.png",
                        "image/png", resultInfo
                    )
                }
            }
        }
    }
}

// ── (h) Photo Watermark ──────────────────────────────────────────────────

@Composable
fun PhotoWatermarkScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var srcUrl by remember { mutableStateOf<String?>(null) }
    var dims by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var text by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("bottom-right") }
    var tile by remember { mutableStateOf(false) }
    var opacity by remember { mutableStateOf(0.55f) }
    var sizeFactor by remember { mutableStateOf(1f) }
    var colorHex by remember { mutableStateOf("#ffffff") }
    var busy by remember { mutableStateOf(false) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var resultInfo by remember { mutableStateOf<String?>(null) }
    val file = files.firstOrNull()

    rememberPhotoSource(file, "imgwm:info") { url, w, h ->
        srcUrl = url
        dims = if (w > 0) w to h else null
        resultUrl = null; resultInfo = null
    }

    ToolPage(IconKind.FileText, "Photo Watermark", "All Tools › Photo Tools › Photo Watermark — stamp your name or shop name on a photo, once in a corner or repeated across it.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_SPEC, "PNG, JPG or WebP (max 15 MB)") { files = it.take(1) }
                    OutlinedTextField(
                        value = text, onValueChange = { text = it },
                        label = { Text("Watermark text (e.g. your name or shop)") }, singleLine = true,
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = tile, onCheckedChange = { tile = it })
                        Text("Repeat across the whole photo", fontSize = 12.5.sp, color = NavyText)
                    }
                    if (!tile) {
                        Text("Position:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                        ChipRow(
                            listOf("top-left" to "Top Left", "top-centre" to "Top Centre", "top-right" to "Top Right"),
                            position
                        ) { position = it }
                        ChipRow(
                            listOf("left" to "Left", "centre" to "Centre", "right" to "Right"),
                            position
                        ) { position = it }
                        ChipRow(
                            listOf("bottom-left" to "Bottom Left", "bottom-centre" to "Bottom Centre", "bottom-right" to "Bottom Right"),
                            position
                        ) { position = it }
                    }
                    Text("Colour:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    ChipRow(
                        listOf("#ffffff" to "White", "#111827" to "Black", "#DC2626" to "Red", "#2563EB" to "Blue"),
                        colorHex
                    ) { colorHex = it }
                    Text("Strength: ${(opacity * 100).toInt()}%", fontSize = 12.5.sp, color = MutedText)
                    Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.15f..1f)
                    Text("Text size: ${(sizeFactor * 100).toInt()}%", fontSize = 12.5.sp, color = MutedText)
                    Slider(value = sizeFactor, onValueChange = { sizeFactor = it }, valueRange = 0.5f..2.5f)
                    PrimaryToolButton(
                        "Add Watermark",
                        enabled = file != null && srcUrl != null && text.isNotBlank(), busy = busy
                    ) {
                        val url = srcUrl ?: return@PrimaryToolButton
                        val d = dims
                        busy = true
                        scope.launch {
                            val sizePx = if (d != null) {
                                (minOf(d.first, d.second) * 0.055f * sizeFactor).roundToInt().coerceAtLeast(10)
                            } else 0
                            val args = buildJsonObject {
                                put("dataUrl", url)
                                put("text", text)
                                put("position", position)
                                put("tile", tile)
                                put("opacity", opacity.toDouble())
                                put("color", colorHex)
                                if (sizePx > 0) put("size", sizePx)
                                put("mime", "image/png")
                            }.toString()
                            imgOpKeyed("imgWatermark", args, "imgwm")
                            val res = awaitAsync("imgwm", 120_000)
                            busy = false
                            if (res != null && res.asyncOk()) {
                                resultUrl = res.asyncStr("dataUrl")
                                resultInfo = "Watermarked photo: ${res.asyncStr("w")} × ${res.asyncStr("h")} px"
                                recordHistory("Photo Watermark", "Watermark added", text)
                            } else {
                                state.toast(res?.asyncError() ?: "Watermark failed")
                            }
                        }
                    }
                    if (busy) BusyRow()
                    PhotoResultBlock(
                        state, resultUrl,
                        (file?.name?.substringBeforeLast(".") ?: "photo") + "-watermarked.png",
                        "image/png", resultInfo
                    )
                }
            }
        }
    }
}
