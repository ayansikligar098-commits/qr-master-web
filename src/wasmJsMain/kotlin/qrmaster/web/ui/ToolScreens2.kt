package qrmaster.web.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import qrmaster.web.data.EarnApi
import qrmaster.web.data.FirebaseApi
import qrmaster.web.data.HistoryItem
import qrmaster.web.data.loadWebConfig
import qrmaster.web.data.myRequests

// ── Image Tools (convert + compress) ───────────────────────────────────────

@Composable
fun ImageToolsScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var quality by remember { mutableStateOf(0.8f) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var resultInfo by remember { mutableStateOf<String?>(null) }
    var resultName by remember { mutableStateOf("image") }
    var resultMime by remember { mutableStateOf("image/png") }
    val resultBitmap = remember(resultUrl) { resultUrl?.let { decodeBitmapFromDataUrl(it) } }
    val file = files.firstOrNull()

    LaunchedEffect(file?.id) {
        info = null
        resultUrl = null
        val f = file ?: return@LaunchedEffect
        val dataUrl = loadFileDataUrl(f) ?: return@LaunchedEffect
        imgOp("info", "{\"dataUrl\":\"$dataUrl\"}")
        val res = awaitAsync("img", 30_000)
        if (res != null && res.asyncOk()) {
            info = "${res.asyncStr("w")} × ${res.asyncStr("h")} px • ${formatBytes(f.size)}"
        }
    }

    fun convert(mime: String, q: Float, label: String) {
        val f = file ?: return
        busy = true
        scope.launch {
            val dataUrl = loadFileDataUrl(f)
            if (dataUrl == null) { busy = false; state.toast("Could not read that file"); return@launch }
            imgOp("convert", "{\"dataUrl\":\"$dataUrl\",\"mime\":\"$mime\",\"quality\":$q}")
            val res = awaitAsync("img", 60_000)
            busy = false
            if (res != null && res.asyncOk()) {
                resultUrl = res.asyncStr("dataUrl")
                val ext = when (mime) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
                resultName = f.name.substringBeforeLast(".") + "." + ext
                resultMime = mime
                val approxBytes = (resultUrl!!.length * 3) / 4
                resultInfo = "New size ≈ ${formatBytes(approxBytes.toLong())} (was ${formatBytes(f.size)})"
                recordHistory("Image Tools", label, f.name)
            } else {
                state.toast(res?.asyncError() ?: "Conversion failed")
            }
        }
    }

    ToolPage(IconKind.Image, "Image Tools", "Convert between PNG, JPG and WebP, and compress images — all in your browser.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_SPEC, "PNG, JPG or WebP (max 15 MB)") { files = it.take(1) }
                    if (info != null) Text(info!!, fontSize = 12.5.sp, color = MutedText)
                    Text("Convert to:", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { convert("image/png", 1f, "Converted to PNG") },
                            enabled = file != null && !busy, shape = RoundedCornerShape(10.dp)
                        ) { Text("PNG", fontSize = 12.5.sp) }
                        OutlinedButton(
                            onClick = { convert("image/jpeg", quality, "Converted to JPG") },
                            enabled = file != null && !busy, shape = RoundedCornerShape(10.dp)
                        ) { Text("JPG", fontSize = 12.5.sp) }
                        OutlinedButton(
                            onClick = { convert("image/webp", quality, "Converted to WebP") },
                            enabled = file != null && !busy, shape = RoundedCornerShape(10.dp)
                        ) { Text("WebP", fontSize = 12.5.sp) }
                    }
                    Text("Quality for JPG / WebP: ${(quality * 100).toInt()}%", fontSize = 12.5.sp, color = MutedText)
                    Slider(value = quality, onValueChange = { quality = it }, valueRange = 0.1f..1f)
                    Text(
                        "Lower quality = smaller file. PNG is lossless and ignores quality.",
                        fontSize = 11.5.sp, color = MutedText
                    )
                    if (busy) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(9.dp))
                            Text("Working…", fontSize = 13.sp, color = MutedText)
                        }
                    }
                    if (resultBitmap != null) {
                        Image(
                            resultBitmap, contentDescription = "Converted image",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F2FA))
                        )
                    }
                    if (resultUrl != null) {
                        if (resultInfo != null) Text(resultInfo!!, fontSize = 12.5.sp, color = MutedText)
                        Button(
                            onClick = {
                                downloadFileBase64(resultName, resultUrl!!.substringAfter(",", ""), resultMime)
                                state.toast("Downloaded $resultName")
                            },
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) { Text("Download Result", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

// ── Resize Image ───────────────────────────────────────────────────────────

/** Converts a print size (inches or centimetres) at [dpi] to pixels. */
private fun physToPx(value: String, unit: String, dpi: Int): Int {
    val x = value.toDoubleOrNull() ?: 0.0
    val inches = if (unit == "cm") x / 2.54 else x
    return (inches * dpi).roundToInt()
}

@Composable
fun ResizeImageScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var dims by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var mode by remember { mutableStateOf(0) } // 0 percent, 1 exact pixels, 2 physical size
    var percent by remember { mutableStateOf(50f) }
    var widthIn by remember { mutableStateOf("") }
    var heightIn by remember { mutableStateOf("") }
    var keepAspect by remember { mutableStateOf(true) }
    var physUnit by remember { mutableStateOf("in") } // "in" | "cm"
    var physW by remember { mutableStateOf("") }
    var physH by remember { mutableStateOf("") }
    var dpiIn by remember { mutableStateOf("300") }
    var physFormat by remember { mutableStateOf("image/png") }
    var busy by remember { mutableStateOf(false) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var resultDims by remember { mutableStateOf<String?>(null) }
    var resultMime by remember { mutableStateOf("image/png") }
    val file = files.firstOrNull()
    val resultBitmap = remember(resultUrl) { resultUrl?.let { decodeBitmapFromDataUrl(it) } }

    LaunchedEffect(file?.id) {
        dims = null; resultUrl = null
        val f = file ?: return@LaunchedEffect
        val dataUrl = loadFileDataUrl(f) ?: return@LaunchedEffect
        imgOp("info", "{\"dataUrl\":\"$dataUrl\"}")
        val res = awaitAsync("img", 30_000)
        if (res != null && res.asyncOk()) {
            val w = res.asyncStr("w").toIntOrNull() ?: 0
            val h = res.asyncStr("h").toIntOrNull() ?: 0
            dims = w to h
            widthIn = w.toString(); heightIn = h.toString()
        }
    }

    ToolPage(IconKind.Edit, "Resize Image", "Resize an image by percent, to exact pixel dimensions, or to a print size in inches/centimetres at any DPI.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_SPEC, "PNG, JPG or WebP (max 15 MB)") { files = it.take(1) }
                    val d = dims
                    if (d != null) Text("Current size: ${d.first} × ${d.second} px", fontSize = 12.5.sp, color = MutedText)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf(0 to "By Percent", 1 to "Exact Pixels", 2 to "Print Size (in/cm + DPI)").forEach { (pm, label) ->
                            FilterChip(
                                selected = mode == pm,
                                onClick = { mode = pm },
                                label = { Text(label, fontSize = 12.5.sp) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    if (mode == 0) {
                        Text("Scale: ${percent.toInt()}%", fontSize = 13.sp, color = NavyText)
                        Slider(value = percent, onValueChange = { percent = it }, valueRange = 5f..200f)
                        if (d != null) {
                            Text(
                                "New size: ${(d.first * percent / 100).toInt()} × ${(d.second * percent / 100).toInt()} px",
                                fontSize = 12.5.sp, color = MutedText
                            )
                        }
                    } else if (mode == 1) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = widthIn,
                                onValueChange = { v ->
                                    widthIn = v.filter { it.isDigit() }
                                    val dd = dims
                                    if (keepAspect && dd != null && dd.second > 0) {
                                        val w = widthIn.toIntOrNull()
                                        if (w != null) heightIn = (w * dd.second / dd.first).toString()
                                    }
                                },
                                label = { Text("Width (px)") }, singleLine = true,
                                shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = heightIn,
                                onValueChange = { v ->
                                    heightIn = v.filter { it.isDigit() }
                                    val dd = dims
                                    if (keepAspect && dd != null && dd.first > 0) {
                                        val h = heightIn.toIntOrNull()
                                        if (h != null) widthIn = (h * dd.first / dd.second).toString()
                                    }
                                },
                                label = { Text("Height (px)") }, singleLine = true,
                                shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = keepAspect, onCheckedChange = { keepAspect = it })
                                Text("Keep aspect", fontSize = 12.sp)
                            }
                        }
                    } else {
                        // Print size: inches or centimetres at a chosen DPI.
                        // The pixel size is computed from the physical size,
                        // and the DPI is stamped into the downloaded file.
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            listOf("in" to "Inches", "cm" to "Centimetres").forEach { (u, label) ->
                                FilterChip(
                                    selected = physUnit == u,
                                    onClick = { physUnit = u },
                                    label = { Text(label, fontSize = 12.5.sp) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = physW,
                                onValueChange = { physW = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                                label = { Text(if (physUnit == "in") "Width (in)" else "Width (cm)") },
                                singleLine = true, shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = physH,
                                onValueChange = { physH = it.filter { c -> c.isDigit() || c == '.' }.take(7) },
                                label = { Text(if (physUnit == "in") "Height (in)" else "Height (cm)") },
                                singleLine = true, shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = dpiIn,
                                onValueChange = { dpiIn = it.filter { c -> c.isDigit() }.take(4) },
                                label = { Text("DPI") },
                                singleLine = true, shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(0.7f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            listOf("image/png" to "PNG", "image/jpeg" to "JPG").forEach { (fm, label) ->
                                FilterChip(
                                    selected = physFormat == fm,
                                    onClick = { physFormat = fm },
                                    label = { Text(label, fontSize = 12.5.sp) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                        val dpiNow = dpiIn.toIntOrNull() ?: 300
                        val pwPx = physToPx(physW, physUnit, dpiNow)
                        val phPx = physToPx(physH, physUnit, dpiNow)
                        if (pwPx > 0 && phPx > 0) {
                            Text("= $pwPx × $phPx px at $dpiNow DPI — the DPI is saved inside the file.",
                                fontSize = 12.5.sp, color = MutedText)
                        } else {
                            Text("Example: a 2 × 2 inch photo at 300 DPI = 600 × 600 px.",
                                fontSize = 12.5.sp, color = MutedText)
                        }
                    }
                    PrimaryToolButton("Resize Image", enabled = file != null && dims != null, busy = busy) {
                        val f = file ?: return@PrimaryToolButton
                        val dd = dims ?: return@PrimaryToolButton
                        val tw: Int; val th: Int
                        if (mode == 0) {
                            tw = (dd.first * percent / 100).toInt().coerceAtLeast(1)
                            th = (dd.second * percent / 100).toInt().coerceAtLeast(1)
                        } else if (mode == 1) {
                            tw = widthIn.toIntOrNull() ?: 0
                            th = heightIn.toIntOrNull() ?: 0
                            if (tw < 1 || th < 1) { state.toast("Enter valid dimensions"); return@PrimaryToolButton }
                        } else {
                            val dpiV = (dpiIn.toIntOrNull() ?: 300).coerceIn(10, 2400)
                            tw = physToPx(physW, physUnit, dpiV)
                            th = physToPx(physH, physUnit, dpiV)
                            if (tw < 1 || th < 1) { state.toast("Enter the print width and height"); return@PrimaryToolButton }
                        }
                        busy = true
                        scope.launch {
                            val dataUrl = loadFileDataUrl(f)
                            if (dataUrl == null) { busy = false; state.toast("Could not read that file"); return@launch }
                            if (mode == 2) {
                                val dpiV = (dpiIn.toIntOrNull() ?: 300).coerceIn(10, 2400)
                                imgOp("resizeDpi", "{\"dataUrl\":\"$dataUrl\",\"w\":$tw,\"h\":$th,\"dpi\":$dpiV,\"mime\":\"$physFormat\"}")
                                val res = awaitAsync("img", 60_000)
                                busy = false
                                if (res != null && res.asyncOk()) {
                                    resultUrl = res.asyncStr("dataUrl")
                                    resultMime = physFormat
                                    resultDims = "${res.asyncStr("w")} × ${res.asyncStr("h")} px · ${res.asyncStr("dpi")} DPI"
                                    recordHistory("Resize Image", "Image resized to print size", resultDims ?: "")
                                } else {
                                    state.toast(res?.asyncError() ?: "Resize failed")
                                }
                            } else {
                                imgOp("resize", "{\"dataUrl\":\"$dataUrl\",\"w\":$tw,\"h\":$th}")
                                val res = awaitAsync("img", 60_000)
                                busy = false
                                if (res != null && res.asyncOk()) {
                                    resultUrl = res.asyncStr("dataUrl")
                                    resultMime = "image/png"
                                    resultDims = "${res.asyncStr("w")} × ${res.asyncStr("h")} px"
                                    recordHistory("Resize Image", "Image resized", resultDims ?: "")
                                } else {
                                    state.toast(res?.asyncError() ?: "Resize failed")
                                }
                            }
                        }
                    }
                    if (resultBitmap != null) {
                        Image(
                            resultBitmap, contentDescription = "Resized image",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)
                                .clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F2FA))
                        )
                    }
                    if (resultUrl != null) {
                        if (resultDims != null) Text("Result: $resultDims", fontSize = 12.5.sp, color = MutedText)
                        Button(
                            onClick = {
                                val ext = if (resultMime == "image/jpeg") "jpg" else "png"
                                val name = (file?.name?.substringBeforeLast(".") ?: "image") + "-resized.$ext"
                                downloadFileBase64(name, resultUrl!!.substringAfter(",", ""), resultMime)
                                state.toast("Downloaded $name")
                            },
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) { Text("Download Result", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

// ── Remove Background ──────────────────────────────────────────────────────

@Composable
fun RemoveBgScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val file = files.firstOrNull()
    val resultBitmap = remember(resultUrl) { resultUrl?.let { decodeBitmapFromDataUrl(it) } }

    // Start warming the model cache as soon as this tool opens, so the
    // download is already done (or well along) when the user clicks.
    LaunchedEffect(Unit) { warmBgModel() }

    ToolPage(IconKind.Star, "Remove Background", "Remove the background from a photo — processed on your device in the browser.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_SPEC, "PNG or JPG photo (max 15 MB)") { files = it.take(1) }
                    Text(
                        "First use downloads the removal model once (about 60 MB) — it stays cached on your device, so later visits start instantly. Your photo never leaves this device.",
                        fontSize = 12.sp, color = MutedText
                    )
                    PrimaryToolButton("Remove Background", enabled = file != null, busy = busy) {
                        val f = file ?: return@PrimaryToolButton
                        busy = true; error = null; resultUrl = null; progress = "Starting…"
                        scope.launch {
                            val dataUrl = loadFileDataUrl(f)
                            if (dataUrl == null) { busy = false; error = "Could not read that file."; return@launch }
                            bgRemoveStart(dataUrl)
                            // poll progress while waiting for the result
                            var res: JsonObject? = null
                            var waited = 0L
                            while (waited < 300_000) {
                                progress = bgProgress()
                                val s = pollAsync("bg")
                                if (s.isNotEmpty()) {
                                    res = try { Json.parseToJsonElement(s).jsonObject } catch (e: Exception) { null }
                                    break
                                }
                                delay(400)
                                waited += 400
                            }
                            busy = false
                            when {
                                res == null -> error = "Background removal timed out. Try a smaller photo."
                                res.asyncOk() -> {
                                    resultUrl = res.asyncStr("dataUrl")
                                    recordHistory("Remove BG", "Background removed", f.name)
                                }
                                else -> error = res.asyncError()
                            }
                        }
                    }
                    if (busy && progress.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(progress, fontSize = 13.sp, color = MutedText)
                        }
                    }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    if (resultBitmap != null) {
                        Image(
                            resultBitmap, contentDescription = "Background removed",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)
                                .clip(RoundedCornerShape(10.dp)).background(Color(0xFFE8E6F5))
                        )
                    }
                    if (resultUrl != null) {
                        Button(
                            onClick = {
                                val name = (file?.name?.substringBeforeLast(".") ?: "image") + "-no-bg.png"
                                downloadFileBase64(name, resultUrl!!.substringAfter(",", ""), "image/png")
                                state.toast("Downloaded $name")
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

// ── Document Scanner ───────────────────────────────────────────────────────

@Composable
fun ScannerScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var dims by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    // corners as fractions (TL, TR, BR, BL)
    var corners by remember { mutableStateOf(floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)) }
    var enhance by remember { mutableStateOf("enh") }
    var busy by remember { mutableStateOf(false) }
    var resultUrl by remember { mutableStateOf<String?>(null) }
    val file = files.firstOrNull()
    val resultBitmap = remember(resultUrl) { resultUrl?.let { decodeBitmapFromDataUrl(it) } }
    val cornerNames = listOf("Top-Left", "Top-Right", "Bottom-Right", "Bottom-Left")

    LaunchedEffect(file?.id) {
        dims = null; resultUrl = null
        corners = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
        val f = file ?: return@LaunchedEffect
        val dataUrl = loadFileDataUrl(f) ?: return@LaunchedEffect
        imgOp("info", "{\"dataUrl\":\"$dataUrl\"}")
        val res = awaitAsync("img", 30_000)
        if (res != null && res.asyncOk()) {
            dims = (res.asyncStr("w").toIntOrNull() ?: 0) to (res.asyncStr("h").toIntOrNull() ?: 0)
        }
    }

    ToolPage(IconKind.Scan, "Document Scanner", "Straighten a photographed document: adjust the four corners, enhance, and save as PNG or PDF.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_SPEC, "A clear photo of the document (max 15 MB)") { files = it.take(1) }
                    val d = dims
                    if (d != null) {
                        Text("Photo: ${d.first} × ${d.second} px. Move each corner to the document's corners:", fontSize = 12.5.sp, color = MutedText)
                        for (ci in 0 until 4) {
                            Text("${cornerNames[ci]} corner", fontSize = 12.5.sp, color = NavyText, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("X", fontSize = 11.5.sp, color = MutedText)
                                Slider(
                                    value = corners[ci * 2],
                                    onValueChange = { v ->
                                        val c = corners.copyOf(); c[ci * 2] = v; corners = c
                                    },
                                    valueRange = 0f..1f, modifier = Modifier.weight(1f)
                                )
                                Text("${(corners[ci * 2] * 100).toInt()}%", fontSize = 11.5.sp, color = MutedText)
                                Text("Y", fontSize = 11.5.sp, color = MutedText)
                                Slider(
                                    value = corners[ci * 2 + 1],
                                    onValueChange = { v ->
                                        val c = corners.copyOf(); c[ci * 2 + 1] = v; corners = c
                                    },
                                    valueRange = 0f..1f, modifier = Modifier.weight(1f)
                                )
                                Text("${(corners[ci * 2 + 1] * 100).toInt()}%", fontSize = 11.5.sp, color = MutedText)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            listOf("orig" to "Original", "enh" to "Enhanced", "bw" to "Black & White").forEach { (mode, label) ->
                                FilterChip(
                                    selected = enhance == mode,
                                    onClick = { enhance = mode },
                                    label = { Text(label, fontSize = 12.5.sp) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                    PrimaryToolButton("Scan & Straighten", enabled = file != null && dims != null, busy = busy) {
                        val f = file ?: return@PrimaryToolButton
                        val dd = dims ?: return@PrimaryToolButton
                        busy = true
                        scope.launch {
                            val dataUrl = loadFileDataUrl(f)
                            if (dataUrl == null) { busy = false; state.toast("Could not read that file"); return@launch }
                            val px = corners.mapIndexed { i, v ->
                                if (i % 2 == 0) (v * dd.first).toInt() else (v * dd.second).toInt()
                            }
                            imgOp(
                                "warp",
                                "{\"dataUrl\":\"$dataUrl\",\"corners\":[${px.joinToString(",")}],\"enhance\":\"$enhance\"}"
                            )
                            val res = awaitAsync("img", 120_000)
                            busy = false
                            if (res != null && res.asyncOk()) {
                                resultUrl = res.asyncStr("dataUrl")
                                recordHistory("Document Scanner", "Document scanned", "${res.asyncStr("w")} × ${res.asyncStr("h")} px")
                            } else {
                                state.toast(res?.asyncError() ?: "Scan failed")
                            }
                        }
                    }
                    if (resultBitmap != null) {
                        Image(
                            resultBitmap, contentDescription = "Scanned document",
                            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)
                                .clip(RoundedCornerShape(10.dp)).background(Color(0xFFF3F2FA))
                        )
                    }
                    if (resultUrl != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    downloadFileBase64("scan.png", resultUrl!!.substringAfter(",", ""), "image/png")
                                    state.toast("Downloaded scan.png")
                                },
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) { Text("Download PNG", fontWeight = FontWeight.SemiBold) }
                            OutlinedButton(
                                onClick = {
                                    busy = true
                                    scope.launch {
                                        loadPdfLibs()
                                        var tries = 0
                                        while (pdfLibStatus() != "ready" && tries < 100) { delay(300); tries++ }
                                        val b64 = resultUrl!!.substringAfter(",", "")
                                        val res = pdfCall("imagesToPdf", "{\"images\":[{\"b64\":\"$b64\",\"mime\":\"image/png\"}]}", 60_000)
                                        busy = false
                                        if (res != null && res.asyncOk()) {
                                            downloadFileBase64("scan.pdf", res.asyncStr("b64"), "application/pdf")
                                            state.toast("Downloaded scan.pdf")
                                        } else {
                                            state.toast(res?.asyncError() ?: "Could not make the PDF")
                                        }
                                    }
                                },
                                enabled = !busy,
                                shape = RoundedCornerShape(11.dp),
                                modifier = Modifier.weight(1f).height(44.dp)
                            ) { Text("Download PDF", fontSize = 13.sp) }
                        }
                    }
                }
            }
        }
    }
}

// ── History (on-device, like the Android app) ──────────────────────────────

private data class HistoryRow(val tool: String, val title: String, val detail: String, val ts: Long)

@Composable
fun HistoryScreen(state: AppState) {
    var rows by remember { mutableStateOf<List<HistoryRow>>(emptyList()) }
    fun load() {
        rows = try {
            val arr = Json.parseToJsonElement(readHistoryJson()) as? JsonArray ?: return
            arr.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                HistoryRow(
                    o["tool"]?.jsonPrimitive?.content ?: "",
                    o["title"]?.jsonPrimitive?.content ?: "",
                    o["detail"]?.jsonPrimitive?.content ?: "",
                    o["ts"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                )
            }
        } catch (e: Exception) { emptyList() }
    }
    LaunchedEffect(Unit) { load() }
    ToolPage(IconKind.Clock, "History", "Your recent tool activity on this device (stored in this browser only).") {
        if (rows.isEmpty()) {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Nothing here yet. Generate a QR, convert a file or use a calculator and it will appear here.",
                        fontSize = 13.sp, color = MutedText, modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${rows.size} recent actions", fontSize = 13.sp, color = MutedText, modifier = Modifier.weight(1f))
                    TextButton(onClick = { clearHistoryJson(); load() }) { Text("Clear All", fontSize = 12.5.sp) }
                }
            }
            items(rows) { r ->
                WhiteCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText, maxLines = 1)
                            Text(
                                listOf(r.tool, r.detail, if (r.ts > 0) fmtDateTime(r.ts) else "").filter { it.isNotBlank() }.joinToString("  •  "),
                                fontSize = 11.5.sp, color = MutedText, maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Notifications ──────────────────────────────────────────────────────────

@Composable
fun NotificationsScreen(state: AppState) {
    var announcement by remember { mutableStateOf<Pair<String, String>?>(null) }
    var requests by remember { mutableStateOf<List<qrmaster.web.data.MyRequest>>(emptyList()) }
    var history by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(state.user) {
        loading = true
        val cfg = try { loadWebConfig(null) } catch (e: Exception) { null }
        if (cfg != null && (cfg.announcementTitle.isNotBlank() || cfg.announcementText.isNotBlank())) {
            announcement = cfg.announcementTitle to cfg.announcementText
        }
        if (state.user != null) {
            requests = try { EarnApi.myRequests() } catch (e: Exception) { emptyList() }
            history = FirebaseApi.fetchHistory()
        }
        loading = false
        state.notifDot = announcement != null ||
            requests.any { it.status.equals("pending", ignoreCase = true) } ||
            history.any { it.status.equals("pending", ignoreCase = true) }
    }

    ToolPage(IconKind.Bell, "Notifications", "Announcements and your latest account updates.") {
        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PurplePrimary)
                }
            }
        }
        val ann = announcement
        if (ann != null) {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QrIcon(IconKind.Zap, Color(0xFFF59E0B), size = 19.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(ann.first.ifBlank { "Announcement" }, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                        }
                        if (ann.second.isNotBlank()) Text(ann.second, fontSize = 13.sp, color = MutedText)
                    }
                }
            }
        }
        if (state.user == null) {
            item {
                WhiteCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Login to see your updates", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = NavyText)
                        Text("Your payment requests, task claims and withdrawals appear here after login.", fontSize = 12.5.sp, color = MutedText)
                        Button(
                            onClick = { state.navigate(Screen.Login) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary)
                        ) { Text("Login") }
                    }
                }
            }
        } else {
            val pendingReq = requests.filter { it.status.equals("pending", ignoreCase = true) }
            val pendingHist = history.filter { it.status.equals("pending", ignoreCase = true) }
            if (!loading && ann == null && pendingReq.isEmpty() && pendingHist.isEmpty()) {
                item {
                    WhiteCard(Modifier.fillMaxWidth()) {
                        Text(
                            "No new notifications. When a request is approved or an announcement is published, it shows here.",
                            fontSize = 13.sp, color = MutedText, modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            }
            if (pendingReq.isNotEmpty()) {
                item { Text("Payment Requests", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyText) }
                items(pendingReq) { r ->
                    WhiteCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
                                Text("${fmtDateTime(r.createdAtMs)}  •  Waiting for approval", fontSize = 11.5.sp, color = MutedText)
                            }
                            Text("₹${r.amountPaid}", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                        }
                    }
                }
            }
            if (pendingHist.isNotEmpty()) {
                item { Text("Pending Activity", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NavyText) }
                items(pendingHist) { h ->
                    WhiteCard(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 15.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(h.title, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
                                Text("${fmtDateTime(h.createdAtMs)}  •  ${h.status}", fontSize = 11.5.sp, color = MutedText)
                            }
                            Text(h.amountText, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = NavyText)
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Tip: spins and rewards are shared between this website and the Kitnook Android app — it is one account.",
                fontSize = 12.sp, color = MutedText
            )
        }
    }
}

// ── About ──────────────────────────────────────────────────────────────────

@Composable
fun AboutScreen(state: AppState) {
    ToolPage(IconKind.Info, "About Us", "Kitnook — Everything You Need, One Nook.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoMark(44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Kitnook", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = NavyText)
                            Text("Website version 1.0", fontSize = 12.sp, color = MutedText)
                        }
                    }
                    Text(
                        "Kitnook brings QR codes, barcodes, PDF and image tools, calculators, converters and the Spin & Earn rewards wallet together in one place. This website and the Kitnook Android app share the same account, wallet and rewards.",
                        fontSize = 13.sp, color = MutedText
                    )
                    Text("What you can do here", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NavyText)
                    listOf(
                        "Create QR codes and scannable barcodes",
                        "Merge, split, compress and rotate PDFs; turn images into PDFs and PDFs into images",
                        "Convert and resize images, remove photo backgrounds, scan documents straight",
                        "Age, BMI, unit, currency and percentage calculators with live exchange rates",
                        "Spin & Earn, daily check-in, bonus codes, tasks, referrals and wallet — the same balance as the app"
                    ).forEach { line ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QrIcon(IconKind.Check, Color(0xFF16A34A), size = 15.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(line, fontSize = 12.5.sp, color = NavyText)
                        }
                    }
                }
            }
        }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Links", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NavyText)
                    OutlinedButton(
                        onClick = { openExternal("https://ayansikligar098-commits.github.io/privacy-qr-master.html") },
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                    ) { Text("Privacy Policy", fontSize = 12.5.sp) }
                    OutlinedButton(
                        onClick = { state.navigate(Screen.Support) },
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                    ) { Text("Help & Support", fontSize = 12.5.sp) }
                    OutlinedButton(
                        onClick = { state.navigate(Screen.Premium) },
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()
                    ) { Text("Go Premium", fontSize = 12.5.sp) }
                }
            }
        }
    }
}
