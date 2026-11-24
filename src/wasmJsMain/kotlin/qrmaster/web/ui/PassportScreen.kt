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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.delay
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    var outfitRotate by mutableStateOf(0f)
    var outfitUrls by mutableStateOf<Map<String, String>>(emptyMap())
    var outfitLoading by mutableStateOf(false)

    // Retouch adjustments (1 = neutral for the colour trio, 0 = off for
    // sharpness). Applied by the shell render — the SAME op that produces
    // the download — so the preview and the export cannot drift apart.
    var rtBright by mutableStateOf(1f)
    var rtContrast by mutableStateOf(1f)
    var rtSaturation by mutableStateOf(1f)
    var rtSharp by mutableStateOf(0f)

    // The shell-rendered composite shown by the Result preview (exact
    // export pixels), plus the input key it was rendered from.
    var previewUrl by mutableStateOf("")
    var previewKey by mutableStateOf("")

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

    /** The detected face box as a rect (source px), for outfit anchoring. */
    fun faceRectF(): PPRectF? = faceBox?.let { PPRectF(it[0], it[1], it[2], it[3]) }

    /** The outfit's placement in the rendered photo — face-anchored. */
    fun outfitRect(): PPSheetCell {
        val o = PP_OUTFITS.firstOrNull { it.id == outfitId }
        return ppOutfitRectForPerson(outW, outH, crop, faceRectF(), o?.spanFrac ?: 0.94f, outfitScale, outfitShift)
    }

    /** Key of everything that feeds the composite render; the Result
     *  preview re-renders whenever it changes. */
    fun previewRenderKey(): String = listOf(
        cutoutUrl.hashCode(), crop, outW, outH, bgId, customR, customG, customB,
        outfitId, outfitUrls[outfitId ?: ""], outfitScale, outfitShift, outfitRotate,
        rtBright, rtContrast, rtSaturation, rtSharp
    ).joinToString("|")
    val sheet: PPSheet?
        get() {
            val p = ppPaper(paperId)
            return if (p.id == "single") null else ppSheetLayout(p.wMm, p.hMm, sizeWmm, sizeHmm)
        }

    /** Back to a brand-new tool state (the "New photo" action). */
    fun reset() {
        stage = PPStage.Landing
        progress = ""
        landingError = null
        note = null
        fileName = ""
        originalUrl = ""
        cutoutUrl = ""
        bgFailed = false
        imgW = 0
        imgH = 0
        faceBox = null
        crop = PPCrop(0f, 0f, 0f, 0f)
        presetId = "in"
        customW = 35.0
        customH = 45.0
        search = ""
        paperId = "single"
        bgId = "white"
        customR = 136
        customG = 184
        customB = 232
        jpg = false
        outfitCat = PP_OUTFIT_CATEGORIES[0]
        outfitId = null
        outfitScale = 1f
        outfitShift = 0f
        outfitRotate = 0f
        outfitUrls = emptyMap()
        outfitLoading = false
        rtBright = 1f
        rtContrast = 1f
        rtSaturation = 1f
        rtSharp = 0f
        previewUrl = ""
        previewKey = ""
        view = PPViewMode.Result
        tab = 0
        cropOpen = false
        busy = false
        dlError = null
    }
}

/**
 * The Passport session, held OUTSIDE the composable on purpose — the app
 * shell swaps layouts at the 760 dp breakpoint, which disposes and
 * recreates this screen; with the model only in `remember`, crossing
 * that breakpoint (resize, rotation, tab restore) threw away the photo
 * and every setting. Mirrors the PDF Editor's PdfEditorDocCache fix.
 * On top of this, the session is also persisted to IndexedDB (see
 * ppSaveSession) so even a full page reload restores it.
 */
private object PpSessionCache {
    var model: PpModel? = null
    var files: List<WebFile> = emptyList()
    var restoreTried: Boolean = false
}

private const val PP_IDB_NAME = "passport-session"

private fun ppJsonEsc(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

/** Serialises the maker session for IndexedDB (photo + cut-out as data
 *  URLs, plus every setting). Data URLs are base64 — JSON-safe as-is. */
private fun ppSessionJson(m: PpModel): String {
    val face = m.faceBox
    val faceJson = if (face != null) "[${face[0]},${face[1]},${face[2]},${face[3]}]" else "null"
    val presetJson = if (m.presetId != null) "\"${ppJsonEsc(m.presetId!!)}\"" else "null"
    val outfitJson = if (m.outfitId != null) "\"${ppJsonEsc(m.outfitId!!)}\"" else "null"
    return "{" +
        "\"v\":1,\"fileName\":\"${ppJsonEsc(m.fileName)}\"," +
        "\"originalUrl\":\"${m.originalUrl}\",\"cutoutUrl\":\"${m.cutoutUrl}\"," +
        "\"bgFailed\":${m.bgFailed},\"imgW\":${m.imgW},\"imgH\":${m.imgH}," +
        "\"face\":$faceJson,\"crop\":[${m.crop.x},${m.crop.y},${m.crop.w},${m.crop.h}]," +
        "\"presetId\":$presetJson,\"customW\":${m.customW},\"customH\":${m.customH}," +
        "\"paperId\":\"${ppJsonEsc(m.paperId)}\",\"bgId\":\"${ppJsonEsc(m.bgId)}\"," +
        "\"customR\":${m.customR},\"customG\":${m.customG},\"customB\":${m.customB}," +
        "\"jpg\":${m.jpg},\"outfitId\":$outfitJson,\"outfitCat\":\"${ppJsonEsc(m.outfitCat)}\"," +
        "\"outfitScale\":${m.outfitScale},\"outfitShift\":${m.outfitShift},\"outfitRotate\":${m.outfitRotate}," +
        "\"rtB\":${m.rtBright},\"rtC\":${m.rtContrast},\"rtS\":${m.rtSaturation},\"rtSh\":${m.rtSharp}," +
        "\"view\":\"${m.view.name}\",\"tab\":${m.tab}}"
}

private fun ppSaveSession(m: PpModel) {
    if (m.originalUrl.isBlank() || m.cutoutUrl.isBlank() || m.imgW <= 0) return
    idbSave(PP_IDB_NAME, ppSessionJson(m))
}

/** Restores a session saved by [ppSaveSession]; lands in the Maker.
 *  Only ever applies to a FRESH landing — if the user has already
 *  started new work by the time the saved JSON arrives, applying it
 *  would slam stale settings (preset, paper, outfit…) over theirs. */
private fun ppRestoreSession(m: PpModel, json: String): Boolean {
    if (m.stage != PPStage.Landing || m.originalUrl.isNotBlank()) return false
    val root = try {
        toolsJson.parseToJsonElement(json).jsonObject
    } catch (e: Exception) {
        return false
    }
    val original = root.asyncStr("originalUrl")
    val cutout = root.asyncStr("cutoutUrl")
    val iw = root.asyncStr("imgW").toIntOrNull() ?: 0
    val ih = root.asyncStr("imgH").toIntOrNull() ?: 0
    if (original.isBlank() || cutout.isBlank() || iw <= 0 || ih <= 0) return false
    m.fileName = root.asyncStr("fileName")
    m.originalUrl = original
    m.cutoutUrl = cutout
    m.bgFailed = root.asyncStr("bgFailed") == "true"
    m.imgW = iw
    m.imgH = ih
    val faceArr = root["face"] as? kotlinx.serialization.json.JsonArray
    m.faceBox = if (faceArr != null && faceArr.size == 4) {
        FloatArray(4) { i -> faceArr[i].jsonPrimitive.content.toFloatOrNull() ?: 0f }
    } else null
    val cropArr = root["crop"] as? kotlinx.serialization.json.JsonArray
    if (cropArr != null && cropArr.size == 4) {
        val c = FloatArray(4) { i -> cropArr[i].jsonPrimitive.content.toFloatOrNull() ?: 0f }
        if (c[2] > 0f && c[3] > 0f) m.crop = PPCrop(c[0], c[1], c[2], c[3])
    }
    if (m.crop.w <= 0f) m.reframe()
    m.presetId = (root["presetId"] as? kotlinx.serialization.json.JsonPrimitive)
        ?.let { if (it.isString) it.content else null }
    m.customW = root.asyncStr("customW").toDoubleOrNull() ?: 35.0
    m.customH = root.asyncStr("customH").toDoubleOrNull() ?: 45.0
    m.paperId = root.asyncStr("paperId").ifBlank { "single" }
    m.bgId = root.asyncStr("bgId").ifBlank { "white" }
    m.customR = root.asyncStr("customR").toIntOrNull() ?: 136
    m.customG = root.asyncStr("customG").toIntOrNull() ?: 184
    m.customB = root.asyncStr("customB").toIntOrNull() ?: 232
    m.jpg = root.asyncStr("jpg") == "true"
    m.outfitId = (root["outfitId"] as? kotlinx.serialization.json.JsonPrimitive)
        ?.let { if (it.isString) it.content else null }
    m.outfitCat = root.asyncStr("outfitCat").ifBlank { PP_OUTFIT_CATEGORIES[0] }
    m.outfitScale = root.asyncStr("outfitScale").toFloatOrNull() ?: 1f
    m.outfitShift = root.asyncStr("outfitShift").toFloatOrNull() ?: 0f
    m.outfitRotate = root.asyncStr("outfitRotate").toFloatOrNull() ?: 0f
    m.rtBright = root.asyncStr("rtB").toFloatOrNull() ?: 1f
    m.rtContrast = root.asyncStr("rtC").toFloatOrNull() ?: 1f
    m.rtSaturation = root.asyncStr("rtS").toFloatOrNull() ?: 1f
    m.rtSharp = root.asyncStr("rtSh").toFloatOrNull() ?: 0f
    m.view = if (root.asyncStr("view") == "Cutout") PPViewMode.Cutout
        else if (root.asyncStr("view") == "Original") PPViewMode.Original
        else PPViewMode.Result
    m.tab = root.asyncStr("tab").toIntOrNull() ?: 0
    m.note = "Your last photo was restored — pick up right where you left off, or tap “New photo” to start over."
    m.stage = PPStage.Maker
    return true
}

@Composable
fun PassportPhotoScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    val m = remember { PpSessionCache.model ?: PpModel().also { PpSessionCache.model = it } }
    var files by remember { mutableStateOf(PpSessionCache.files) }

    // Keep the picked-file list in the session cache too (breakpoint-safe).
    LaunchedEffect(files) { PpSessionCache.files = files }

    // Warm the background-removal model cache in the background, and
    // restore any session saved in IndexedDB (tab discard / reload) —
    // the user returns to the Maker with photo + settings intact.
    LaunchedEffect(Unit) {
        warmBgModel()
        if (!PpSessionCache.restoreTried) {
            PpSessionCache.restoreTried = true
            if (m.stage == PPStage.Landing && m.originalUrl.isBlank()) {
                idbLoadStart(PP_IDB_NAME, "pp:restore")
                val res = awaitAsync("pp:restore", 10_000)
                val json = if (res != null && res.asyncOk()) res.asyncStr("json") else ""
                if (json.isNotBlank()) ppRestoreSession(m, json)
            }
        }
    }

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
                                "First use downloads the photo model once (about 60 MB); it stays cached on your device, so later visits start instantly. Your photo never leaves this device.",
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
                        // The uploaded photo stays visible while it is
                        // being prepared — never a bare spinner.
                        val procBmp = remember(m.originalUrl) {
                            if (m.originalUrl.isBlank()) null else decodeBitmapFromDataUrl(m.originalUrl)
                        }
                        if (procBmp != null) {
                            Image(
                                procBmp, contentDescription = "Your uploaded photo",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp)
                                    .clip(RoundedCornerShape(10.dp)).background(Color(0xFFF1EFFD)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(38.dp))
                        Text("Preparing your photo…", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = NavyText)
                        if (m.progress.isNotBlank()) Text(m.progress, fontSize = 12.5.sp, color = MutedText)
                    }
                }
            }

            PPStage.Maker -> item {
                PpMaker(state, m, scope, onNewPhoto = {
                    files = emptyList()
                    idbClear(PP_IDB_NAME)
                    m.reset()
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
        // Guarded framing: the crop is verified to contain the face; if
        // the detected box ever arrives in a wrong coordinate space, the
        // guard falls back instead of producing a faceless photo.
        val framing = ppFaceCropSafe(iw, ih, m.aspect, fx, fy, fw, fh)
        m.crop = framing.crop
        if (framing.fellBack) {
            notes.add("The photo was framed automatically. Use “Crop / Choose area” if you want to adjust it.")
        }
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
    // Persist the finished session so a discarded/reloaded tab restores it.
    ppSaveSession(m)
}

@Composable
private fun PpMaker(
    state: AppState,
    m: PpModel,
    scope: CoroutineScope,
    onNewPhoto: () -> Unit
) {
    // Persist every maker change (debounced) so a discarded or reloaded
    // tab restores photo + settings exactly as they were.
    LaunchedEffect(
        m.bgId, m.customR, m.customG, m.customB, m.outfitId, m.outfitScale, m.outfitShift, m.outfitRotate,
        m.rtBright, m.rtContrast, m.rtSaturation, m.rtSharp,
        m.paperId, m.presetId, m.customW, m.customH, m.crop, m.jpg, m.view, m.tab
    ) {
        delay(600)
        ppSaveSession(m)
    }
    // The selected outfit's artwork must be loaded even if the outfit
    // tab was never opened in this session (e.g. after a restore), or
    // the preview and the download would silently drop it.
    LaunchedEffect(m.outfitId) {
        val o = PP_OUTFITS.firstOrNull { it.id == m.outfitId } ?: return@LaunchedEffect
        if (o.id !in m.outfitUrls) {
            val url = fetchResourceDataUrl(o.path)
            if (url != null) m.outfitUrls = m.outfitUrls + (o.id to url)
        }
    }
    if (m.cropOpen) {
        PpCropEditor(m)
        return
    }
    // A compact, centred workspace — on wide screens the maker must not
    // stretch edge to edge (the preview used to dominate the screen).
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 1080.dp)) {
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

    // The Result preview shows the shell-rendered composite — the exact
    // pixels the download produces (background, crop, outfit placement
    // and rotation, retouch) — re-rendered (debounced) whenever any of
    // its inputs change. Until the first render lands, the layered
    // canvas draw below stands in.
    val renderKey = m.previewRenderKey()
    LaunchedEffect(renderKey) {
        if (m.cutoutUrl.isBlank() || m.crop.w <= 0f || m.crop.h <= 0f) return@LaunchedEffect
        delay(250)
        val res = passportCall("renderPhoto", ppRenderArgsJson(m, "image/png"))
        if (res != null && res.asyncOk() && res.asyncStr("dataUrl").isNotBlank()) {
            m.previewUrl = res.asyncStr("dataUrl")
            m.previewKey = renderKey
        }
    }
    val compositeBmp = remember(m.previewUrl) {
        if (m.previewUrl.isBlank()) null else decodeBitmapFromDataUrl(m.previewUrl)
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
            PPViewMode.Result -> PpResultPreview(m, cutoutBmp, outfitBmp, compositeBmp)
            PPViewMode.Cutout -> PpCutoutPreview(m, cutoutBmp, outfitBmp)
            PPViewMode.Original -> PpOriginalPreview(m, originalBmp)
        }
        Text(
            "Preview — ${ppFmtMm(m.sizeWmm)} × ${ppFmtMm(m.sizeHmm)} mm · ${m.outW} × ${m.outH} px at 300 DPI",
            fontSize = 12.sp, color = MutedText
        )
    }
}

@Composable
private fun PpResultPreview(m: PpModel, cutoutBmp: ImageBitmap?, outfitBmp: ImageBitmap?, compositeBmp: ImageBitmap?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // A normal passport-photo preview size — never a giant canvas:
        // at most ~380 px wide and ~400 px tall on any screen.
        val frameW = min(maxWidth.value, min(380f, (400.0 * m.aspect).toFloat()))
        val frameH = (frameW / m.aspect).toFloat()
        val bg = m.bgDef
        Box(
            Modifier.size(frameW.dp, frameH.dp).clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFDDD6F3), RoundedCornerShape(10.dp))
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (compositeBmp != null) {
                    // The exact export render (shell composite).
                    drawImage(
                        image = compositeBmp,
                        dstOffset = IntOffset.Zero,
                        dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                        filterQuality = FilterQuality.High
                    )
                } else {
                    if (bg.isGradient) {
                        drawRect(Brush.verticalGradient(listOf(ppParseHex(bg.colors[0]), ppParseHex(bg.colors[1]))))
                    } else {
                        drawRect(ppParseHex(bg.colors[0]))
                    }
                    if (cutoutBmp != null && m.crop.w > 0f && m.crop.h > 0f) {
                        drawImage(
                            image = cutoutBmp,
                            srcOffset = IntOffset(m.crop.x.roundToInt(), m.crop.y.roundToInt()),
                            srcSize = IntSize(m.crop.w.roundToInt().coerceAtLeast(1), m.crop.h.roundToInt().coerceAtLeast(1)),
                            dstOffset = IntOffset.Zero,
                            dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), size.height.roundToInt().coerceAtLeast(1)),
                            filterQuality = FilterQuality.High
                        )
                    }
                    if (outfitBmp != null && m.outW > 0 && m.outH > 0) {
                        val r = m.outfitRect()
                        val ox = r.x.toFloat() / m.outW * size.width
                        val oy = r.y.toFloat() / m.outH * size.height
                        val ow = r.w.toFloat() / m.outW * size.width
                        val oh = r.h.toFloat() / m.outH * size.height
                        withTransform({ rotate(m.outfitRotate, Offset(ox + ow / 2f, oy + oh / 2f)) }) {
                            drawImage(
                                image = outfitBmp,
                                dstOffset = IntOffset(ox.roundToInt(), oy.roundToInt()),
                                dstSize = IntSize(ow.roundToInt().coerceAtLeast(1), oh.roundToInt().coerceAtLeast(1)),
                                filterQuality = FilterQuality.High
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The Cut-out view: the full photo on a checkerboard (background stays
 * transparent here), WITH the chosen outfit drawn on the person — the
 * outfit is part of the person layer, so it shows in this view too.
 */
@Composable
private fun PpCutoutPreview(m: PpModel, bmp: ImageBitmap?, outfitBmp: ImageBitmap?) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val frameW = min(maxWidth.value, 380f)
        val aspect = if (m.imgW > 0 && m.imgH > 0) m.imgW.toDouble() / m.imgH else 1.0
        val frameH = (frameW / aspect).toFloat().coerceAtMost(400f)
        Box(
            Modifier.size(frameW.dp, frameH.dp).clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color(0xFFDDD6F3), RoundedCornerShape(10.dp))
        ) {
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
                if (bmp != null && m.imgW > 0 && m.imgH > 0) {
                    val s = min(size.width / m.imgW, size.height / m.imgH)
                    val dw = m.imgW * s
                    val dh = m.imgH * s
                    val dx = (size.width - dw) / 2f
                    val dy = (size.height - dh) / 2f
                    drawImage(
                        image = bmp,
                        dstOffset = IntOffset(dx.roundToInt(), dy.roundToInt()),
                        dstSize = IntSize(dw.roundToInt().coerceAtLeast(1), dh.roundToInt().coerceAtLeast(1)),
                        filterQuality = FilterQuality.High
                    )
                    if (outfitBmp != null && m.crop.w > 0f && m.outW > 0 && m.outH > 0) {
                        val rOut = m.outfitRect()
                        val rs = ppOutfitRectInSource(m.crop, rOut, m.outW, m.outH)
                        val oox = dx + rs.x * s
                        val ooy = dy + rs.y * s
                        val oow = rs.w * s
                        val ooh = rs.h * s
                        withTransform({ rotate(m.outfitRotate, Offset(oox + oow / 2f, ooy + ooh / 2f)) }) {
                            drawImage(
                                image = outfitBmp,
                                dstOffset = IntOffset(oox.roundToInt(), ooy.roundToInt()),
                                dstSize = IntSize(oow.roundToInt().coerceAtLeast(1), ooh.roundToInt().coerceAtLeast(1)),
                                filterQuality = FilterQuality.High
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PpFitPreview(bmp: androidx.compose.ui.graphics.ImageBitmap?, imgW: Int, imgH: Int, checker: Boolean) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val frameW = min(maxWidth.value, 380f)
        val aspect = if (imgW > 0 && imgH > 0) imgW.toDouble() / imgH else 1.0
        val frameH = (frameW / aspect).toFloat().coerceAtMost(400f)
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
        val frameW = min(maxWidth.value, 380f)
        val aspect = if (m.imgW > 0 && m.imgH > 0) m.imgW.toDouble() / m.imgH else 1.0
        val frameH = (frameW / aspect).toFloat().coerceAtMost(400f)
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
            listOf("Size", "Background", "Change outfit", "Retouch").forEachIndexed { i, label ->
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
            2 -> PpOutfitTab(m, scope)
            else -> PpRetouchTab(m)
        }
    }
}

private fun PpModel.reframe() {
    val fb = faceBox
    crop = if (fb != null) ppFaceCropSafe(imgW, imgH, aspect, fb[0], fb[1], fb[2], fb[3]).crop
    else ppCentreCrop(imgW, imgH, aspect)
    // A size change alters the framing — show its effect immediately.
    view = PPViewMode.Result
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
        LazyColumn(Modifier.fillMaxWidth().height(200.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
            onSelect = { i -> m.paperId = PP_PAPER_OPTIONS[i].id; m.view = PPViewMode.Result }
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
                PpRgbSlider("Red", m.customR) { m.customR = it; m.view = PPViewMode.Result }
                PpRgbSlider("Green", m.customG) { m.customG = it; m.view = PPViewMode.Result }
                PpRgbSlider("Blue", m.customB) { m.customB = it; m.view = PPViewMode.Result }
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
                .clickable { m.bgId = bg.id; m.view = PPViewMode.Result }
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
                onClick = { m.outfitId = null; m.view = PPViewMode.Result },
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
                            .clickable { m.outfitId = o.id; m.view = PPViewMode.Result }
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
                Slider(value = m.outfitScale, onValueChange = { m.outfitScale = it; m.view = PPViewMode.Result }, valueRange = 0.8f..1.3f)
                Text("Outfit position", fontSize = 12.5.sp, color = MutedText)
                Slider(value = m.outfitShift, onValueChange = { m.outfitShift = it; m.view = PPViewMode.Result }, valueRange = -0.08f..0.08f)
                Text("Outfit rotation: ${m.outfitRotate.roundToInt()}°", fontSize = 12.5.sp, color = MutedText)
                Slider(value = m.outfitRotate, onValueChange = { m.outfitRotate = it; m.view = PPViewMode.Result }, valueRange = -15f..15f)
            }
        }
        Text(
            "Outfits are artwork templates placed over your photo. The outfit is aligned to the shoulders automatically using the detected face — use the size, position and rotation sliders to fine-tune it.",
            fontSize = 11.5.sp, color = MutedText
        )
    }
}

@Composable
private fun PpRetouchTab(m: PpModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PpAdjustSlider("Brightness", m.rtBright, 0.5f..1.5f) { m.rtBright = it; m.view = PPViewMode.Result }
        PpAdjustSlider("Contrast", m.rtContrast, 0.5f..1.5f) { m.rtContrast = it; m.view = PPViewMode.Result }
        PpAdjustSlider("Saturation", m.rtSaturation, 0.5f..1.5f) { m.rtSaturation = it; m.view = PPViewMode.Result }
        PpAdjustSlider("Sharpness", m.rtSharp, 0f..1f) { m.rtSharp = it; m.view = PPViewMode.Result }
        OutlinedButton(onClick = {
            m.rtBright = 1f; m.rtContrast = 1f; m.rtSaturation = 1f; m.rtSharp = 0f
            m.view = PPViewMode.Result
        }) { Text("Reset adjustments", fontSize = 13.sp) }
        Text(
            "Adjustments are applied to the photo itself — what you see in the preview is exactly what downloads.",
            fontSize = 11.5.sp, color = MutedText
        )
    }
}

@Composable
private fun PpAdjustSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("$label: ${(value * 100).roundToInt()}%", fontSize = 12.5.sp, color = MutedText)
        Slider(value = value, onValueChange = onChange, valueRange = range)
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

/**
 * The ONE argument builder for the shell's renderPhoto op — used by the
 * Result preview AND by the download, so both render identical pixels
 * (background, crop, face-anchored outfit + rotation, retouch).
 */
private fun ppRenderArgsJson(m: PpModel, mime: String): String {
    val bg = m.bgDef
    val bgJson = if (bg.isGradient) {
        "\"bgGrad\":[\"${bg.colors[0]}\",\"${bg.colors[1]}\"]"
    } else {
        "\"bg\":\"${bg.colors[0]}\""
    }
    val outfit = PP_OUTFITS.firstOrNull { it.id == m.outfitId }
    val outfitUrl = outfit?.let { m.outfitUrls[it.id] }
    val outfitJson = if (outfit != null && outfitUrl != null) {
        val r = m.outfitRect()
        ",\"outfit\":\"$outfitUrl\",\"outfitX\":${r.x},\"outfitY\":${r.y},\"outfitW\":${r.w},\"outfitH\":${r.h},\"outfitRotate\":${m.outfitRotate}"
    } else ""
    return "{\"dataUrl\":\"${m.cutoutUrl}\",\"cropX\":${m.crop.x},\"cropY\":${m.crop.y}," +
        "\"cropW\":${m.crop.w},\"cropH\":${m.crop.h},\"outW\":${m.outW},\"outH\":${m.outH}," +
        "$bgJson$outfitJson,\"brightness\":${m.rtBright},\"contrast\":${m.rtContrast}," +
        "\"saturation\":${m.rtSaturation},\"sharpness\":${m.rtSharp}," +
        "\"mime\":\"$mime\",\"quality\":0.92}"
}

private suspend fun ppDownload(state: AppState, m: PpModel) {
    m.busy = true
    m.dlError = null
    try {
        val mime = if (m.jpg) "image/jpeg" else "image/png"
        val ext = if (m.jpg) "jpg" else "png"
        val args = ppRenderArgsJson(m, mime)
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
