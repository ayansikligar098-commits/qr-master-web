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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

// ── Compress PDF (dedicated tool) ──────────────────────────────────────────
// Method (same family as Pi7): the PDF's embedded images are re-encoded at
// reduced quality/scale and the document is re-saved with object streams.
// Text, fonts and layout stay untouched and selectable. All sizes shown
// are computed from the actual produced bytes.

private data class PdfCompressResult(
    val b64: String, val before: Long, val after: Long,
    val replaced: Int, val skipped: Int,
    val noGain: Boolean, val targetReached: Boolean?
)

@Composable
fun CompressPdfScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var targetValue by remember { mutableStateOf("") }
    var targetUnitMb by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<PdfCompressResult?>(null) }
    var outName by remember { mutableStateOf("") }
    val file = files.firstOrNull()

    LaunchedEffect(file?.id) {
        result = null; error = null
        val f = file
        outName = if (f != null) f.name.substringBeforeLast(".") + "-compressed" else ""
    }

    fun sanitizeName(raw: String): String {
        var n = raw.trim().replace(Regex("[^A-Za-z0-9._ -]"), "").replace(' ', '-')
        if (n.lowercase().endsWith(".pdf")) n = n.dropLast(4)
        if (n.isBlank()) n = "compressed"
        return "$n.pdf"
    }

    ToolPage(IconKind.Compress, "Compress PDF", "All Tools › PDF Tools › Compress PDF — shrink a PDF by re-encoding its images. Text stays selectable.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, PDF_BIG_SPEC, "PDF only (max 100 MB)", bigUploadButton = true, bigUploadLabel = "Upload PDF") { files = it.take(1) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = targetValue,
                            onValueChange = { targetValue = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Target size (optional)") },
                            singleLine = true, shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(false to "KB", true to "MB").forEach { (mb, label) ->
                                FilterChip(
                                    selected = targetUnitMb == mb,
                                    onClick = { targetUnitMb = mb },
                                    label = { Text(label, fontSize = 12.5.sp) },
                                    shape = RoundedCornerShape(20.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                    Text(
                        "Leave the target empty for the best balance of size and quality. With a target, the tool tries stronger settings until it gets close.",
                        fontSize = 12.sp, color = MutedText
                    )
                    PrimaryToolButton("Compress PDF", enabled = file != null, busy = busy) {
                        val f = file ?: return@PrimaryToolButton
                        busy = true; error = null; result = null; progress = "Loading the PDF engine…"
                        scope.launch {
                            loadPdfLibs()
                            var tries = 0
                            var status = pdfLibStatus()
                            while (status != "ready" && tries < 120) {
                                if (status.startsWith("error")) break
                                delay(300); tries++; status = pdfLibStatus()
                            }
                            if (status != "ready") {
                                busy = false
                                error = if (status.startsWith("error")) status.removePrefix("error:")
                                        else "The PDF engine is still loading. Try again in a moment."
                                return@launch
                            }
                            val b64 = loadFileBase64(f.id)
                            if (b64 == null) { busy = false; error = "Could not read that file."; return@launch }
                            val targetBytes = targetValue.toDoubleOrNull()?.let {
                                (it * if (targetUnitMb) 1024 * 1024 else 1024).toInt()
                            } ?: 0
                            pdfCompressStart(b64, targetBytes)
                            var res: JsonObject? = null
                            var waited = 0L
                            while (waited < 600_000) {
                                progress = compressProgress()
                                val s = pollAsync("pdfzip")
                                if (s.isNotEmpty()) {
                                    res = try {
                                        kotlinx.serialization.json.Json.parseToJsonElement(s).jsonObject
                                    } catch (e: Exception) { null }
                                    break
                                }
                                delay(400); waited += 400
                            }
                            busy = false
                            when {
                                res == null -> error = "Compression timed out. Try a smaller PDF."
                                res.asyncOk() -> {
                                    result = PdfCompressResult(
                                        b64 = res.asyncStr("b64"),
                                        before = res.asyncStr("before").toLongOrNull() ?: f.size,
                                        after = res.asyncStr("after").toLongOrNull() ?: 0L,
                                        replaced = res.asyncStr("imagesReplaced").toIntOrNull() ?: 0,
                                        skipped = res.asyncStr("imagesSkipped").toIntOrNull() ?: 0,
                                        noGain = res.asyncStr("noGain").toBooleanStrictOrNull() == true,
                                        targetReached = (res["targetReached"] as? kotlinx.serialization.json.JsonPrimitive)
                                            ?.content?.toBooleanStrictOrNull()
                                    )
                                    recordHistory("Compress PDF", "PDF compressed",
                                        "${formatBytes(result!!.before)} → ${formatBytes(result!!.after)}")
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
                    val r = result
                    if (r != null) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF0FDF4)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text("✅ Compression complete", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                            SizeRow("Original size", formatBytes(r.before))
                            SizeRow("Compressed size", formatBytes(r.after))
                            val savedPct = if (r.before > 0) (r.before - r.after) * 100.0 / r.before else 0.0
                            SizeRow("You saved", "${formatBytes(r.before - r.after)} (${pctText(savedPct)}%)")
                            when {
                                r.noGain -> Text(
                                    "This PDF is already well compressed — no smaller version could be made without changing its content, so the original file is returned unchanged.",
                                    fontSize = 12.sp, color = MutedText
                                )
                                r.replaced == 0 -> Text(
                                    "No embedded images could be re-encoded in this PDF, so only the file structure was optimised. Text, fonts and layout are unchanged.",
                                    fontSize = 12.sp, color = MutedText
                                )
                                else -> Text(
                                    "${r.replaced} embedded image(s) were re-encoded; text, fonts and layout are untouched." +
                                        if (r.skipped > 0) " ${r.skipped} image(s) were left as-is (transparency or special format)." else "",
                                    fontSize = 12.sp, color = MutedText
                                )
                            }
                            if (r.targetReached == false) {
                                Text(
                                    "The target size could not be reached — this is the smallest result that keeps the document intact.",
                                    fontSize = 12.sp, color = Color(0xFFB45309)
                                )
                            }
                        }
                        OutlinedTextField(
                            value = outName,
                            onValueChange = { outName = it },
                            label = { Text("Output file name") },
                            singleLine = true, shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                val name = sanitizeName(outName)
                                downloadFileBase64(name, r.b64, "application/pdf")
                                state.toast("Downloaded $name")
                            },
                            shape = RoundedCornerShape(11.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) { Text("Download Compressed PDF", fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SizeRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, fontSize = 13.sp, color = MutedText, modifier = Modifier.weight(1f))
        Text(value, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = NavyText)
    }
}

private fun pctText(v: Double): String {
    val r = kotlin.math.round(v * 10) / 10.0
    return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
}

// ── Compress Image (dedicated tool, batch) ─────────────────────────────────

private data class ImgJob(
    val file: WebFile,
    val dims: Pair<Int, Int>? = null,
    val thumb: String? = null,
    val resultDataUrl: String? = null,
    val resultName: String = "",
    val resultMime: String = "image/jpeg",
    val resultW: Int = 0,
    val resultH: Int = 0,
    val error: String? = null
) {
    val resultSize: Long get() = resultDataUrl?.let { dataUrlByteSize(it) } ?: 0L
}

@Composable
fun CompressImageScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var jobs by remember { mutableStateOf<List<ImgJob>>(emptyList()) }
    var format by remember { mutableStateOf("image/jpeg") }
    var quality by remember { mutableStateOf(80f) }
    var resizeMode by remember { mutableStateOf(0) } // 0 keep, 1 percent, 2 custom
    var percent by remember { mutableStateOf(100f) }
    var customW by remember { mutableStateOf("") }
    var customH by remember { mutableStateOf("") }
    var lockAspect by remember { mutableStateOf(true) }
    var suffix by remember { mutableStateOf("-compressed") }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf("") }
    var thumbs by remember { mutableStateOf<Map<String, androidx.compose.ui.graphics.ImageBitmap>>(emptyMap()) }

    // Load dims + thumbnails for newly added files.
    LaunchedEffect(files) {
        val known = jobs.map { it.file.id }.toSet()
        for (f in files) {
            if (f.id in known) continue
            var job = ImgJob(f)
            jobs = jobs + job
            val dataUrl = loadFileDataUrl(f) ?: continue
            imgOp("info", "{\"dataUrl\":\"$dataUrl\"}")
            val infoRes = awaitAsync("img", 30_000)
            var dims: Pair<Int, Int>? = null
            if (infoRes != null && infoRes.asyncOk()) {
                dims = (infoRes.asyncStr("w").toIntOrNull() ?: 0) to (infoRes.asyncStr("h").toIntOrNull() ?: 0)
            }
            imgOp("thumb", "{\"dataUrl\":\"$dataUrl\",\"maxDim\":128}")
            val thumbRes = awaitAsync("img", 30_000)
            val thumb = if (thumbRes != null && thumbRes.asyncOk()) thumbRes.asyncStr("dataUrl") else null
            if (thumb != null) {
                decodeBitmapFromDataUrl(thumb)?.let { bmp -> thumbs = thumbs + (f.id to bmp) }
            }
            job = job.copy(dims = dims, thumb = thumb)
            jobs = jobs.map { if (it.file.id == f.id) job else it }
        }
        jobs = jobs.filter { j -> files.any { it.id == j.file.id } }
    }

    fun extFor(mime: String) = when (mime) { "image/jpeg" -> "jpg"; "image/webp" -> "webp"; else -> "png" }
    fun outNameFor(f: WebFile): String =
        f.name.substringBeforeLast(".") + suffix + "." + extFor(format)

    fun targetDims(d: Pair<Int, Int>): Pair<Int, Int> {
        return when (resizeMode) {
            1 -> {
                val p = percent / 100f
                ((d.first * p).toInt().coerceAtLeast(1)) to ((d.second * p).toInt().coerceAtLeast(1))
            }
            2 -> {
                val w = customW.toIntOrNull() ?: 0
                val h = customH.toIntOrNull() ?: 0
                when {
                    w < 1 && h < 1 -> d
                    lockAspect -> {
                        val scale = minOf(
                            if (w > 0) w.toDouble() / d.first else Double.MAX_VALUE,
                            if (h > 0) h.toDouble() / d.second else Double.MAX_VALUE
                        )
                        ((d.first * scale).toInt().coerceAtLeast(1)) to ((d.second * scale).toInt().coerceAtLeast(1))
                    }
                    else -> (if (w > 0) w else d.first) to (if (h > 0) h else d.second)
                }
            }
            else -> d
        }
    }

    val doneJobs = jobs.filter { it.resultDataUrl != null }
    val totalOrig = doneJobs.sumOf { it.file.size }
    val totalComp = doneJobs.sumOf { it.resultSize }

    ToolPage(IconKind.Compress, "Compress Image", "All Tools › Image Tools › Compress Image — shrink JPG, PNG and WebP images, in batches.") {
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    FileZone(state, files, IMAGE_BATCH_SPEC, "JPG, PNG or WebP — up to 20 files, 20 MB each", bigUploadButton = true, bigUploadLabel = "Upload Images") { newFiles ->
                        files = newFiles
                    }
                    jobs.forEach { job ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFFF7F6FC)).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val bmp = thumbs[job.file.id]
                            if (bmp != null) {
                                Image(
                                    bmp, contentDescription = job.file.name,
                                    modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFE8E6F5))
                                )
                            } else {
                                Box(
                                    Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFE8E6F5)),
                                    contentAlignment = Alignment.Center
                                ) { QrIcon(IconKind.Image, PurplePrimary, size = 22.dp) }
                            }
                            Spacer(Modifier.width(11.dp))
                            Column(Modifier.weight(1f)) {
                                Text(job.file.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NavyText, maxLines = 1)
                                Text(
                                    listOf(
                                        job.dims?.let { "${it.first} × ${it.second} px" } ?: "",
                                        formatBytes(job.file.size),
                                        if (job.resultDataUrl != null) "→ ${formatBytes(job.resultSize)}" else "",
                                        job.error ?: ""
                                    ).filter { it.isNotBlank() }.joinToString("  •  "),
                                    fontSize = 11.5.sp,
                                    color = if (job.error != null) MaterialTheme.colorScheme.error else MutedText,
                                    maxLines = 1
                                )
                            }
                            if (job.resultDataUrl != null) {
                                OutlinedButton(
                                    onClick = {
                                        downloadFileBase64(job.resultName, job.resultDataUrl.substringAfter(",", ""), job.resultMime)
                                        state.toast("Downloaded ${job.resultName}")
                                    },
                                    shape = RoundedCornerShape(9.dp)
                                ) { Text("Download", fontSize = 11.5.sp) }
                                Spacer(Modifier.width(6.dp))
                            }
                            TextButton(onClick = {
                                releaseFile(job.file.id)
                                files = files.filter { it.id != job.file.id }
                            }) { Text("Remove", fontSize = 11.5.sp) }
                        }
                    }
                }
            }
        }
        item {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Text("Output format", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf("image/jpeg" to "JPG", "image/png" to "PNG", "image/webp" to "WebP").forEach { (mime, label) ->
                            FilterChip(
                                selected = format == mime,
                                onClick = { format = mime },
                                label = { Text(label, fontSize = 12.5.sp) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    if (format != "image/png") {
                        Text("Quality: ${quality.toInt()}%", fontSize = 12.5.sp, color = MutedText)
                        Slider(value = quality, onValueChange = { quality = it }, valueRange = 10f..100f)
                    } else {
                        Text("PNG is lossless — quality does not apply.", fontSize = 12.sp, color = MutedText)
                    }
                    Text("Resize", fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        listOf(0 to "Keep Original", 1 to "Percentage", 2 to "Custom Size").forEach { (mode, label) ->
                            FilterChip(
                                selected = resizeMode == mode,
                                onClick = { resizeMode = mode },
                                label = { Text(label, fontSize = 12.5.sp) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    when (resizeMode) {
                        1 -> {
                            Text("Scale: ${percent.toInt()}%", fontSize = 12.5.sp, color = MutedText)
                            Slider(value = percent, onValueChange = { percent = it }, valueRange = 5f..100f)
                        }
                        2 -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = customW, onValueChange = { customW = it.filter { c -> c.isDigit() } },
                                    label = { Text("Max width") }, singleLine = true,
                                    shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = customH, onValueChange = { customH = it.filter { c -> c.isDigit() } },
                                    label = { Text("Max height") }, singleLine = true,
                                    shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f)
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = lockAspect, onCheckedChange = { lockAspect = it })
                                    Text("Keep aspect ratio", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = suffix,
                        onValueChange = { suffix = it },
                        label = { Text("Output name suffix") },
                        singleLine = true, shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    PrimaryToolButton(
                        if (doneJobs.isEmpty()) "Compress ${jobs.size} Image(s)" else "Compress Again",
                        enabled = jobs.isNotEmpty(), busy = busy
                    ) {
                        busy = true
                        scope.launch {
                            val current = jobs
                            for ((idx, job) in current.withIndex()) {
                                progress = "Compressing ${idx + 1} of ${current.size}: ${job.file.name}"
                                val dataUrl = loadFileDataUrl(job.file)
                                if (dataUrl == null) {
                                    jobs = jobs.map { if (it.file.id == job.file.id) it.copy(error = "Could not read this file") else it }
                                    continue
                                }
                                val d = job.dims
                                val td = if (d != null) targetDims(d) else null
                                val args = if (td != null && td != d) {
                                    "{\"dataUrl\":\"$dataUrl\",\"mime\":\"$format\",\"quality\":${quality / 100f},\"w\":${td.first},\"h\":${td.second}}"
                                } else {
                                    "{\"dataUrl\":\"$dataUrl\",\"mime\":\"$format\",\"quality\":${quality / 100f}}"
                                }
                                imgOp("process", args)
                                val res = awaitAsync("img", 60_000)
                                jobs = if (res != null && res.asyncOk()) {
                                    val url = res.asyncStr("dataUrl")
                                    jobs.map {
                                        if (it.file.id == job.file.id) it.copy(
                                            resultDataUrl = url,
                                            resultName = outNameFor(job.file),
                                            resultMime = format,
                                            resultW = res.asyncStr("w").toIntOrNull() ?: 0,
                                            resultH = res.asyncStr("h").toIntOrNull() ?: 0,
                                            error = null
                                        ) else it
                                    }
                                } else {
                                    jobs.map { if (it.file.id == job.file.id) it.copy(error = res?.asyncError() ?: "Failed") else it }
                                }
                            }
                            busy = false
                            progress = ""
                            val done = jobs.count { it.resultDataUrl != null }
                            if (done > 0) recordHistory("Compress Image", "Images compressed", "$done file(s)")
                        }
                    }
                    if (busy && progress.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(19.dp))
                            Spacer(Modifier.width(9.dp))
                            Text(progress, fontSize = 13.sp, color = MutedText)
                        }
                    }
                    if (doneJobs.isNotEmpty()) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF0FDF4)).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(7.dp)
                        ) {
                            Text("✅ Done — ${doneJobs.size} image(s) compressed", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                            SizeRow("Total original", formatBytes(totalOrig))
                            SizeRow("Total compressed", formatBytes(totalComp))
                            val pct = if (totalOrig > 0) (totalOrig - totalComp) * 100.0 / totalOrig else 0.0
                            SizeRow("You saved", "${formatBytes(totalOrig - totalComp)} (${pctText(pct)}%)")
                            if (totalComp >= totalOrig) {
                                Text(
                                    "The results are not smaller than the originals at these settings — try a lower quality or JPG/WebP format.",
                                    fontSize = 12.sp, color = Color(0xFFB45309)
                                )
                            }
                        }
                        if (doneJobs.size > 1) {
                            Button(
                                onClick = {
                                    fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
                                    val entries = doneJobs.joinToString(",") { j ->
                                        "{\"name\":\"${esc(j.resultName)}\",\"b64\":\"${j.resultDataUrl!!.substringAfter(",", "")}\"}"
                                    }
                                    zipDownload("compressed-images.zip", "[$entries]")
                                    state.toast("Downloading compressed-images.zip")
                                },
                                shape = RoundedCornerShape(11.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                                modifier = Modifier.fillMaxWidth().height(46.dp)
                            ) { Text("Download All as ZIP", fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
            }
        }
    }
}
