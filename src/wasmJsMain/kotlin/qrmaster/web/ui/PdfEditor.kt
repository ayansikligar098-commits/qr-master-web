package qrmaster.web.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import qrmaster.web.data.nowMillis
import kotlin.JsFun
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// ── PDF Editor (Smallpdf-style) ────────────────────────────────────────────
// pdf.js renders page previews; the overlay objects live in Compose in
// DISPLAYED page points (origin top-left). On Download, the shell rebuilds
// the document with pdf-lib in the editor's page order and burns every
// object onto the real pages (see __qmEditorExport in index.html).
// Honest limits (also shown in the UI): text already inside the PDF cannot
// be edited in place — cover it (white rectangle) and add new text;
// redaction draws an opaque cover box, it is not certified content removal.

@JsFun("(argsJson) => { window.__qmEditorExport(argsJson); }")
external fun editorExportStart(argsJson: String)

@JsFun("(k) => { return window.__qmStoreGet ? window.__qmStoreGet(k) : ''; }")
external fun storeGet(k: String): String

@JsFun("(k, v) => { if (window.__qmStoreSet) window.__qmStoreSet(k, v); }")
external fun storeSet(k: String, v: String)

data class EdObj(
    val id: Int,
    val type: String, // text | image | rect | ellipse | highlight | redact | path
    val x: Float, val y: Float, val w: Float, val h: Float,
    val text: String = "",
    val fontSize: Float = 16f,
    val color: Long = 0xFF111827,
    val bold: Boolean = false,
    val opacity: Float = 1f,
    val dataUrl: String? = null,
    val points: List<Pair<Float, Float>> = emptyList(), // relative 0..1 in box; (-1,-1) = pen up
    val strokeWidth: Float = 2f,
    val fill: Boolean = false,
    val rotation: Float = 0f, // degrees, clockwise on screen (Smallpdf-style rotate handle)
    val fontFamily: String = "sans", // text only: sans | serif | mono | script
    val align: String = "left" // text only: left | center | right
)

data class EdPageState(
    val srcIndex: Int, // -1 = blank page
    val extraRotation: Int = 0,
    val crop: List<Float>? = null, // x, y, w, h in displayed points
    val objects: List<EdObj> = emptyList()
)

/**
 * The currently open document, held OUTSIDE the composable on purpose.
 * The app shell swaps between its desktop and mobile layouts at 760 dp,
 * which disposes and recreates this screen — with the document only in
 * `remember`, crossing that breakpoint (window resize, device rotation)
 * threw away the loaded PDF and every edit. The screen initialises from
 * this cache and mirrors back into it; previews/thumbnails are not
 * cached (they re-render from pdfB64 on restore).
 *
 * The cache hands its document back ONLY to a recreation under the same
 * navigation epoch ([ownerEpoch] — a layout flip while the user stays
 * on this screen). A real navigation (fresh visit, or away and back)
 * bumps the epoch, and the editor then starts EMPTY: the previous
 * document lives on only as the IndexedDB record, behind the explicit
 * "Resume last PDF" button (owner's order — the old PDF must never
 * pop back open unasked).
 */
private object PdfEditorDocCache {
    var pdfB64: String? = null
    var fileName: String = ""
    var pages: List<EdPageState> = emptyList()
    var ownerEpoch: Int = -1
}

private const val PDF_IDB_NAME = "pdf-session"

/** Base64 length cap for IndexedDB persistence (~48 MB of PDF). Beyond
 *  this the document still opens and works in-session via
 *  [PdfEditorDocCache]; it just isn't written to durable storage. */
private const val PDF_IDB_MAX_B64 = 64 * 1024 * 1024

private fun pdfJsonEsc(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"")

/** Serialises the open document for IndexedDB (original PDF bytes as
 *  base64 — JSON-safe as-is — plus the file name). Mirrors the Passport
 *  tool's ppSaveSession: document-level only, edits are NOT saved. */
private fun pdfSessionJson(b64: String, name: String): String =
    "{\"v\":1,\"fileName\":\"${pdfJsonEsc(name)}\",\"pdf\":\"$b64\"}"

private fun pdfSaveSession(b64: String, name: String) {
    if (b64.length > PDF_IDB_MAX_B64) return
    idbSave(PDF_IDB_NAME, pdfSessionJson(b64, name))
}

private enum class EdTool { Select, Text, Draw, Highlight, Underline, Strike, Squiggle, Rect, Ellipse, Line, Check, Cross, Arrow, Redact, Crop, Eraser }
private enum class EdTab { Edit, Sign, Organize, Form }

/**
 * One fillable AcroForm field of the open document, as reported by the
 * shell's `formFields` op. [value] is the field's current value: the
 * text content, "true"/"false" for a checkbox, or the selected option
 * for a radio group / dropdown. On download the values travel in the
 * export args and the shell fills + flattens them into the real PDF
 * (see qmEditorExport in index.html).
 */
data class EdFormField(
    val name: String,
    val type: String, // text | checkbox | radio | dropdown | signature | button | other
    val value: String,
    val options: List<String> = emptyList()
)

/** Parses the `fields` array of a `formFields` op result. */
private fun parseEdFormFields(res: JsonObject): List<EdFormField> {
    val arr = res["fields"] as? JsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val opts = (o["options"] as? JsonArray)
            ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content } ?: emptyList()
        EdFormField(o.asyncStr("name"), o.asyncStr("type"), o.asyncStr("value"), opts)
    }
}

/** Parses "1-3, 5" style page ranges into 0-based page indices. */
private fun edParseRange(input: String, total: Int): List<Int> {
    val out = ArrayList<Int>()
    for (part in input.split(",")) {
        val p = part.trim()
        if (p.isEmpty()) continue
        val dash = p.indexOf('-', 1)
        if (dash > 0) {
            val a = p.substring(0, dash).trim().toIntOrNull()
            val b = p.substring(dash + 1).trim().toIntOrNull()
            if (a != null && b != null) {
                val lo = min(a, b); val hi = max(a, b)
                for (n in lo..hi) if (n in 1..total && (n - 1) !in out) out.add(n - 1)
            }
        } else {
            val n = p.toIntOrNull()
            if (n != null && n in 1..total && (n - 1) !in out) out.add(n - 1)
        }
    }
    return out
}

private val ED_COLORS = listOf(
    0xFF111827L, 0xFFFFFFFFL, 0xFFDC2626L, 0xFF2563EBL, 0xFF059669L,
    0xFFF59E0BL, 0xFF7C3AEDL, 0xFFFDE047L, 0xFFDB2777L, 0xFF64748BL
)

private fun edHex(argb: Long): String =
    "#" + (argb and 0xFFFFFF).toString(16).uppercase().padStart(6, '0')

/** Maps a shell/pdf.js failure to the honest message shown to the user. */
private fun friendlyPdfError(raw: String?): String {
    val m = (raw ?: "").trim()
    return when {
        m.contains("password", ignoreCase = true) ->
            "This PDF is password-protected and can't be opened here. If you know the password, remove it first (for example, open the file in a PDF viewer and save or print it to a new PDF), then try again."
        m.contains("timed out", ignoreCase = true) ->
            "Rendering timed out — this file may be too heavy for the browser right now. Please try again."
        m.contains("invalid", ignoreCase = true) || m.contains("corrupt", ignoreCase = true) ->
            "This PDF couldn't be opened — the file appears to be corrupt or invalid."
        m.isBlank() -> "Couldn't render this page. Please try again."
        else -> m
    }
}

private fun edTextBox(text: String, size: Float): Pair<Float, Float> {
    val lines = text.split("\n")
    val w = (lines.maxOfOrNull { it.length } ?: 0) * size * 0.56f + 10f
    val h = lines.size * size * 1.25f + 6f
    return max(30f, w) to max(18f, h)
}

private suspend fun ensureEditorLibs(): String? {
    loadPdfLibs()
    var tries = 0
    var status = pdfLibStatus()
    while (status != "ready" && tries < 120) {
        if (status.startsWith("error")) return status.removePrefix("error:")
        delay(300); tries++; status = pdfLibStatus()
    }
    return if (status == "ready") null else "The PDF engine is still loading. Try again in a moment."
}

@Composable
fun PdfEditorScreen(state: AppState) {
    val scope = rememberCoroutineScope()
    // The navigation epoch this screen instance was created under. The
    // doc cache only hands its document back when it was cached under
    // THIS epoch (a 760 dp layout flip recreating us mid-edit). Any
    // real navigation — a fresh visit, or leaving and coming back —
    // bumps the epoch, so the editor starts EMPTY and the previous
    // document can return only via Upload or "Resume last PDF".
    val entryEpoch = remember { state.navEpoch }
    val cacheLive = PdfEditorDocCache.ownerEpoch == entryEpoch && PdfEditorDocCache.pdfB64 != null
    var files by remember { mutableStateOf<List<WebFile>>(emptyList()) }
    var pdfB64 by remember { mutableStateOf<String?>(if (cacheLive) PdfEditorDocCache.pdfB64 else null) }
    var fileName by remember { mutableStateOf(if (cacheLive) PdfEditorDocCache.fileName else "") }
    var pages by remember { mutableStateOf(if (cacheLive) PdfEditorDocCache.pages else emptyList()) }
    // The IndexedDB record from a previous visit (base64 to file name),
    // once the entry check finds one. It is NEVER opened automatically —
    // it only arms the "Resume last PDF" button on the empty state.
    var savedSession by remember { mutableStateOf<Pair<String, String>?>(null) }
    var current by remember { mutableStateOf(0) }
    var tool by remember { mutableStateOf(EdTool.Select) }
    var tab by remember { mutableStateOf(EdTab.Edit) }
    var selectedId by remember { mutableStateOf<Int?>(null) }
    var nextId by remember { mutableStateOf(1) }
    var zoom by remember { mutableStateOf(1f) }
    var zoomTouched by remember { mutableStateOf(false) } // user picked a zoom: stop auto-fitting
    var propsOpen by remember { mutableStateOf(false) } // narrow screens: Properties overlay
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var previews by remember { mutableStateOf<Map<Int, String>>(emptyMap()) } // outPage -> dataUrl (1.6x)
    var thumbs by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var dims by remember { mutableStateOf<Map<Int, Pair<Float, Float>>>(emptyMap()) } // outPage -> displayed pts
    var pageErrors by remember { mutableStateOf<Map<Int, String>>(emptyMap()) } // outPage -> render error (never spin forever)
    var undoStack by remember { mutableStateOf<List<List<EdPageState>>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<List<EdPageState>>>(emptyList()) }
    var wmText by remember { mutableStateOf("") }
    var wmOpacity by remember { mutableStateOf(0.16f) }
    var wmColor by remember { mutableStateOf(0xFF7C3AEDL) }
    var wmPanel by remember { mutableStateOf(false) }
    var draftRect by remember { mutableStateOf<List<Float>?>(null) } // x,y,w,h displayed pts
    var draftPoints by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
    var sigStrokes by remember { mutableStateOf<List<List<Pair<Float, Float>>>>(emptyList()) }
    var savedSigs by remember { mutableStateOf<List<List<List<Pair<Float, Float>>>>>(emptyList()) }
    // Fillable form fields of the open document (Fill Form tab). Empty
    // for PDFs without an AcroForm — the tab then stays hidden.
    var formFields by remember { mutableStateOf<List<EdFormField>>(emptyList()) }
    var extractRange by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    val page = pages.getOrNull(current)
    val pageDims = dims[current] ?: (595.28f to 841.89f)
    val selected = page?.objects?.firstOrNull { it.id == selectedId }

    fun snapshot() {
        undoStack = (undoStack + listOf(pages)).takeLast(60)
        redoStack = emptyList()
    }
    fun replacePage(idx: Int, p: EdPageState) {
        pages = pages.toMutableList().also { it[idx] = p }
    }
    fun mutateSelected(transform: (EdObj) -> EdObj) {
        val p = pages.getOrNull(current) ?: return; val id = selectedId ?: return
        replacePage(current, p.copy(objects = p.objects.map { if (it.id == id) transform(it) else it }))
    }
    fun deleteSelected() {
        val p = pages.getOrNull(current) ?: return; val id = selectedId ?: return
        snapshot()
        replacePage(current, p.copy(objects = p.objects.filter { it.id != id }))
        selectedId = null
    }
    fun duplicateSelected() {
        val p = pages.getOrNull(current) ?: return
        val sel = p.objects.firstOrNull { it.id == selectedId } ?: return
        val copy = sel.copy(id = nextId++, x = sel.x + 14f, y = sel.y + 14f)
        snapshot()
        replacePage(current, p.copy(objects = p.objects + copy))
        selectedId = copy.id
    }
    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack = redoStack + listOf(pages)
        pages = undoStack.last(); undoStack = undoStack.dropLast(1)
        selectedId = null
        if (current >= pages.size) current = max(0, pages.size - 1)
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack = undoStack + listOf(pages)
        pages = redoStack.last(); redoStack = redoStack.dropLast(1)
        selectedId = null
        if (current >= pages.size) current = max(0, pages.size - 1)
    }

    suspend fun renderOutPage(idx: Int, forThumb: Boolean): Boolean {
        val p = pages.getOrNull(idx) ?: return false
        val b64 = pdfB64 ?: return false
        if (p.srcIndex < 0) {
            dims = dims + (idx to (595.28f to 841.89f))
            return true
        }
        val scale = if (forThumb) 0.35 else 1.6
        val res = pdfCall("renderPage", buildJsonObject {
            put("pdf", b64); put("page", p.srcIndex + 1); put("scale", scale); put("rotation", p.extraRotation)
        }.toString(), 120_000)
        if (res == null) {
            pageErrors = pageErrors + (idx to friendlyPdfError("Rendering timed out"))
            return false
        }
        if (!res.asyncOk()) {
            pageErrors = pageErrors + (idx to friendlyPdfError(res.asyncError()))
            return false
        }
        val img = res.asyncStr("image")
        val pw = res.asyncStr("pw").toFloatOrNull()
        val ph = res.asyncStr("ph").toFloatOrNull()
        if (pw == null || ph == null) {
            pageErrors = pageErrors + (idx to friendlyPdfError(null))
            return false
        }
        pageErrors = pageErrors - idx
        if (forThumb) thumbs = thumbs + (idx to img)
        else { previews = previews + (idx to img); dims = dims + (idx to (pw to ph)) }
        return true
    }

    fun renderCurrent() { scope.launch { renderOutPage(current, false) } }
    fun renderThumb(idx: Int) { scope.launch { renderOutPage(idx, true) } }

    // Mirror the open document into the cache so a shell layout flip
    // (desktop <-> mobile at 760 dp) can't lose it, and — when this screen
    // was recreated with a document still open — re-render its pages
    // (previews/thumbs/dims start empty after recreation).
    LaunchedEffect(pdfB64, fileName, pages) {
        PdfEditorDocCache.pdfB64 = pdfB64
        PdfEditorDocCache.fileName = fileName
        PdfEditorDocCache.pages = pages
        PdfEditorDocCache.ownerEpoch = entryEpoch
    }
    LaunchedEffect(Unit) {
        if (pages.isNotEmpty() && pdfB64 != null && previews.isEmpty()) {
            statusText = "Opened ${pages.size} pages."
            renderCurrent()
            pages.indices.forEach { renderThumb(it) }
        }
    }

    // Opens a document from its base64 bytes — the one pipeline both a
    // freshly picked file and an IndexedDB restore go through (page-count
    // check + page-1 render gate). Returns true when the document opened.
    suspend fun openDocument(b64: String, name: String, restored: Boolean): Boolean {
        busy = true; errorText = null; statusText = "Opening $name…"
        val libErr = ensureEditorLibs()
        if (libErr != null) { busy = false; errorText = libErr; return false }
        val res = pdfCall("count", buildJsonObject { put("pdf", b64) }.toString(), 60_000)
        if (res == null || !res.asyncOk()) {
            busy = false
            errorText = if (res == null) "Opening timed out. Please try again."
                else friendlyPdfError(res.asyncError())
            return false
        }
        val count = res.asyncStr("pages").toIntOrNull() ?: 0
        if (count <= 0) { busy = false; errorText = "This PDF has no pages."; return false }
        pdfB64 = b64; fileName = name
        val capped = min(count, 50)
        pages = (0 until capped).map { EdPageState(srcIndex = it) }
        current = 0; selectedId = null; undoStack = emptyList(); redoStack = emptyList()
        formFields = emptyList()
        zoom = 1f; zoomTouched = false; propsOpen = false
        previews = emptyMap(); thumbs = emptyMap(); dims = emptyMap(); pageErrors = emptyMap()
        // Gate on page 1 actually rendering. The pdf-lib page count above
        // ignores encryption, so without this gate a password-protected
        // (or otherwise unrenderable) file "opens" into a wall of endless
        // spinners. On failure: show the real reason and go back to the
        // file picker, usable for another file.
        val firstOk = renderOutPage(0, false)
        busy = false
        if (!firstOk) {
            val msg = pageErrors[0] ?: "Couldn't render this PDF."
            pages = emptyList(); pdfB64 = null
            previews = emptyMap(); thumbs = emptyMap(); dims = emptyMap(); pageErrors = emptyMap()
            statusText = null
            errorText = msg
            return false
        }
        statusText = when {
            restored -> "Your last document was restored — pick up right where you left off, or tap “Open PDF” to start over."
            count > 50 -> "Opened the first 50 of $count pages (editor limit)."
            else -> "Opened $count pages."
        }
        // Discover the document's fillable form fields (Fill Form tab).
        // Best-effort: a PDF without an AcroForm yields an empty list
        // and the tab simply never appears.
        val fres = pdfCall("formFields", buildJsonObject { put("pdf", b64) }.toString(), 60_000)
        if (fres != null && fres.asyncOk()) {
            formFields = parseEdFormFields(fres)
            if (formFields.isNotEmpty()) {
                statusText = (statusText ?: "") +
                    " This PDF has ${formFields.size} fillable form field(s) — see the Fill Form tab."
            }
        }
        if (tab == EdTab.Form && formFields.isEmpty()) tab = EdTab.Edit
        for (i in 0 until capped) renderThumb(i)
        // Persist the freshly opened document (a different file replaces
        // the previous record). A restore doesn't re-save — its record
        // is already the one in storage.
        if (!restored) pdfSaveSession(b64, name)
        return true
    }

    // Load a newly picked PDF.
    LaunchedEffect(files.firstOrNull()?.id) {
        val f = files.firstOrNull() ?: return@LaunchedEffect
        busy = true; errorText = null; statusText = "Opening ${f.name}…"
        val b64 = loadFileBase64(f.id)
        if (b64 == null) { busy = false; errorText = "Could not read this file."; return@LaunchedEffect }
        openDocument(b64, f.name, restored = false)
    }

    // Bring back the document saved in IndexedDB — ONLY when the user
    // taps "Resume last PDF" on the empty state (owner's order: the
    // editor must open empty; the old PDF must never appear unasked).
    // Document-level restore only: the original PDF comes back exactly
    // as opened; in-progress edits are not saved.
    fun resumeLastPdf() {
        val saved = savedSession ?: return
        if (pdfB64 != null || pages.isNotEmpty() || busy) return
        scope.launch {
            val ok = openDocument(saved.first, saved.second, restored = true)
            if (!ok) {
                // The saved copy no longer opens — drop the record so
                // the empty state comes up clean, quietly.
                idbClear(PDF_IDB_NAME)
                savedSession = null
                errorText = null; statusText = null
            }
        }
    }

    // Entry check: does IndexedDB hold a document from a previous
    // visit? The record is only READ here — it arms the "Resume last
    // PDF" button and is never opened automatically. (PdfEditorDocCache
    // covers layout flips inside one page load; this record is what
    // survives a discarded tab or a full page reload.)
    LaunchedEffect(Unit) {
        if (pdfB64 == null && pages.isEmpty()) {
            idbLoadStart(PDF_IDB_NAME, "pdf:restore")
            val res = awaitAsync("pdf:restore", 10_000)
            val json = if (res != null && res.asyncOk()) res.asyncStr("json") else ""
            // Re-check: the user may have picked a file meanwhile.
            if (json.isNotBlank() && pdfB64 == null && pages.isEmpty()) {
                val root = try {
                    toolsJson.parseToJsonElement(json).jsonObject
                } catch (e: Exception) { null }
                val savedB64 = root?.asyncStr("pdf") ?: ""
                val savedName = root?.asyncStr("fileName") ?: ""
                if (savedB64.isNotBlank()) {
                    savedSession = savedB64 to savedName.ifBlank { "Restored document.pdf" }
                }
            }
        }
    }

    // Saved signatures from local storage.
    LaunchedEffect(Unit) {
        try {
            val raw = storeGet("signatures")
            if (raw.isNotBlank()) {
                val arr = kotlinx.serialization.json.Json.parseToJsonElement(raw).jsonArray
                savedSigs = arr.map { sig ->
                    sig.jsonArray.map { stroke ->
                        stroke.jsonArray.map { pt ->
                            val a = pt.jsonArray
                            (a[0].jsonPrimitive.content.toFloat()) to (a[1].jsonPrimitive.content.toFloat())
                        }
                    }
                }
            }
        } catch (e: Exception) { /* no saved signatures yet */ }
    }

    fun persistSigs() {
        val arr = buildJsonArray {
            savedSigs.forEach { sig ->
                add(buildJsonArray {
                    sig.forEach { stroke ->
                        add(buildJsonArray {
                            stroke.forEach { pt -> add(buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(pt.first)); add(kotlinx.serialization.json.JsonPrimitive(pt.second)) }) }
                        })
                    }
                })
            }
        }
        storeSet("signatures", arr.toString())
    }

    fun addObject(obj: EdObj) {
        val p = pages.getOrNull(current) ?: return
        snapshot()
        replacePage(current, p.copy(objects = p.objects + obj))
        selectedId = obj.id
        tool = EdTool.Select
    }

    fun rotateCurrentPage(delta: Int) {
        val p = pages.getOrNull(current) ?: return
        snapshot()
        replacePage(current, p.copy(extraRotation = ((p.extraRotation + delta) % 360 + 360) % 360))
        previews = previews - current
        renderCurrent(); renderThumb(current)
    }

    fun download() {
        val b64 = pdfB64 ?: return
        busy = true; errorText = null; statusText = "Building your edited PDF…"
        scope.launch {
            val args = buildJsonObject {
                put("pdf", b64)
                // Form values collected in the Fill Form tab: the shell
                // fills them into a scratch copy and flattens it before
                // rebuilding the pages, so they survive as real content.
                val formValues = buildJsonObject {
                    formFields.forEach { f ->
                        when (f.type) {
                            "text", "checkbox" -> put(f.name, f.value)
                            "radio", "dropdown" -> if (f.value.isNotBlank()) put(f.name, f.value)
                        }
                    }
                }
                if (formValues.isNotEmpty()) put("formValues", formValues)
                if (wmText.isNotBlank()) put("watermark", buildJsonObject {
                    put("text", wmText); put("opacity", wmOpacity.toDouble()); put("color", edHex(wmColor))
                })
                put("pages", buildJsonArray {
                    pages.forEach { p ->
                        add(buildJsonObject {
                            put("src", p.srcIndex); put("rot", p.extraRotation)
                            p.crop?.let { c -> put("crop", buildJsonObject {
                                put("x", c[0].toDouble()); put("y", c[1].toDouble()); put("w", c[2].toDouble()); put("h", c[3].toDouble())
                            }) }
                            put("objects", buildJsonArray {
                                p.objects.forEach { o ->
                                    add(buildJsonObject {
                                        put("t", o.type); put("x", o.x.toDouble()); put("y", o.y.toDouble())
                                        put("w", o.w.toDouble()); put("h", o.h.toDouble())
                                        put("color", edHex(o.color)); put("opacity", o.opacity.toDouble())
                                        put("border", o.strokeWidth.toDouble()); put("fill", o.fill)
                                        put("rot", o.rotation.toDouble()); put("font", o.fontFamily); put("align", o.align)
                                        if (o.type == "text") { put("text", o.text); put("size", o.fontSize.toDouble()); put("bold", o.bold) }
                                        if (o.type == "image" && o.dataUrl != null) put("dataUrl", o.dataUrl)
                                        if (o.type == "path") put("points", buildJsonArray {
                                            o.points.forEach { pt -> add(buildJsonArray {
                                                add(kotlinx.serialization.json.JsonPrimitive(pt.first)); add(kotlinx.serialization.json.JsonPrimitive(pt.second))
                                            }) }
                                        })
                                    })
                                }
                            })
                        })
                    }
                })
            }
            editorExportStart(args.toString())
            val res = awaitAsync("editor", 300_000)
            busy = false
            if (res == null) { errorText = "Export timed out. Try a smaller PDF."; return@launch }
            if (!res.asyncOk()) { errorText = res.asyncError(); return@launch }
            val outB64 = res.asyncStr("b64")
            val outName = fileName.substringBeforeLast(".").ifBlank { "document" } + "-edited.pdf"
            downloadFileBase64(outName, outB64, "application/pdf")
            recordHistory("PDF Editor", outName, "Edited PDF · ${pages.size} pages")
            statusText = "Downloaded $outName — open it to check your edits are in the file."
        }
    }

    // ── actions used by the toolbar / panels ──────────────────────────────
    fun addImageFlow() {
        launchPick(scope, IMAGE_SPEC, false, current = { emptyList() }) { incoming, err ->
            if (err != null) { errorText = err; return@launchPick }
            val f = incoming.firstOrNull() ?: return@launchPick
            scope.launch {
                var dataUrl = loadFileDataUrl(f) ?: run { errorText = "Could not read this image."; return@launch }
                var iw = 0; var ih = 0
                imgOp("info", buildJsonObject { put("dataUrl", dataUrl) }.toString())
                val info = awaitAsync("img", 60_000)
                if (info != null && info.asyncOk()) {
                    iw = info.asyncStr("w").toIntOrNull() ?: 0; ih = info.asyncStr("h").toIntOrNull() ?: 0
                }
                if (f.type.contains("webp") || f.name.lowercase().endsWith(".webp")) {
                    imgOp("convert", buildJsonObject { put("dataUrl", dataUrl); put("mime", "image/png"); put("quality", 1.0) }.toString())
                    val conv = awaitAsync("img", 60_000)
                    if (conv != null && conv.asyncOk()) dataUrl = conv.asyncStr("dataUrl")
                }
                // Sensible starting size: natural ratio, at most ~35% of the
                // page wide / ~45% tall (edFitNewImage) — never page-filling.
                val (w, h) = edFitNewImage(iw, ih, pageDims.first, pageDims.second)
                addObject(EdObj(nextId++, "image",
                    x = (pageDims.first - w) / 2f, y = (pageDims.second - h) / 2f,
                    w = w, h = h, dataUrl = dataUrl))
            }
        }
    }

    fun addTextAt(x: Float, y: Float, initial: String = "Type here") {
        val (tw, th) = edTextBox(initial, 18f)
        addObject(EdObj(nextId++, "text", x = x, y = y, w = tw, h = th, text = initial, fontSize = 18f))
    }

    fun placeSignature(sig: List<List<Pair<Float, Float>>>) {
        val flat = ArrayList<Pair<Float, Float>>()
        sig.forEachIndexed { i, stroke ->
            if (i > 0) flat.add(-1f to -1f)
            flat.addAll(stroke)
        }
        if (flat.isEmpty()) return
        addObject(EdObj(nextId++, "path",
            x = pageDims.first * 0.55f, y = pageDims.second * 0.62f, w = 210f, h = 74f,
            color = 0xFF1E3A8A, points = flat, strokeWidth = 2.4f))
        statusText = "Signature placed — drag it onto the signature line, resize with the corners."
    }

    val propsPanel: @Composable () -> Unit = {
        LazyColumn(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text("Properties", fontWeight = FontWeight.Bold, fontSize = 14.5.sp, color = NavyText)
            }
            val sel = selected
            if (sel == null) {
                item {
                    Text("Select an object on the page to edit it here — or use the tools above to add text, images, shapes and signatures.",
                        fontSize = 12.5.sp, color = MutedText)
                }
            } else {
                if (sel.type == "text") {
                    item {
                        OutlinedTextField(
                            value = sel.text,
                            onValueChange = { v ->
                                val (tw, th) = edTextBox(v, sel.fontSize)
                                mutateSelected { it.copy(text = v, w = tw, h = th) }
                            },
                            label = { Text("Text") }, shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text("Font size: ${sel.fontSize.toInt()} pt", fontSize = 12.sp, color = MutedText)
                        Slider(value = sel.fontSize, onValueChange = { v ->
                            val (tw, th) = edTextBox(sel.text, v)
                            mutateSelected { it.copy(fontSize = v, w = tw, h = th) }
                        }, valueRange = 8f..72f)
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = sel.bold, onClick = { mutateSelected { it.copy(bold = !it.bold) } },
                                label = { Text("Bold", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White))
                        }
                    }
                    item {
                        Text("Font", fontSize = 12.sp, color = MutedText)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            listOf("sans" to "Sans", "serif" to "Serif", "mono" to "Mono", "script" to "Script").forEach { (v, label) ->
                                FilterChip(selected = sel.fontFamily == v, onClick = { mutateSelected { it.copy(fontFamily = v) } },
                                    label = { Text(label, fontSize = 11.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White))
                            }
                        }
                    }
                    item {
                        Text("Alignment", fontSize = 12.sp, color = MutedText)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            listOf("left" to "Left", "center" to "Centre", "right" to "Right").forEach { (v, label) ->
                                FilterChip(selected = sel.align == v, onClick = { mutateSelected { it.copy(align = v) } },
                                    label = { Text(label, fontSize = 11.5.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White))
                            }
                        }
                    }
                    item {
                        Text("Opacity: ${(sel.opacity * 100).toInt()}%", fontSize = 12.sp, color = MutedText)
                        Slider(value = sel.opacity, onValueChange = { v -> mutateSelected { it.copy(opacity = v) } },
                            valueRange = 0.05f..1f)
                    }
                }
                if (sel.type == "image" || sel.type == "rect" || sel.type == "ellipse" ||
                    sel.type == "highlight" || sel.type == "redact" || sel.type == "path") {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            EdNumField("X", sel.x) { v -> mutateSelected { it.copy(x = v) } }
                            EdNumField("Y", sel.y) { v -> mutateSelected { it.copy(y = v) } }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // For images the two fields stay locked to the
                            // photo's ratio: editing one updates the other.
                            EdNumField("Width", sel.w) { v -> mutateSelected {
                                if (it.type == "image") {
                                    val r = edWithWidthLocked(EdBox(it.x, it.y, it.w, it.h), v)
                                    it.copy(w = r.w, h = r.h)
                                } else it.copy(w = max(8f, v))
                            } }
                            EdNumField("Height", sel.h) { v -> mutateSelected {
                                if (it.type == "image") {
                                    val r = edWithHeightLocked(EdBox(it.x, it.y, it.w, it.h), v)
                                    it.copy(w = r.w, h = r.h)
                                } else it.copy(h = max(8f, v))
                            } }
                        }
                    }
                    item {
                        Text("Opacity: ${(sel.opacity * 100).toInt()}%", fontSize = 12.sp, color = MutedText)
                        Slider(value = sel.opacity, onValueChange = { v -> mutateSelected { it.copy(opacity = v) } },
                            valueRange = 0.05f..1f)
                    }
                    if (sel.type == "image") {
                        item {
                            OutlinedButton(onClick = {
                                launchPick(scope, IMAGE_SPEC, false, current = { emptyList() }) { incoming, err ->
                                    if (err != null) { errorText = err; return@launchPick }
                                    val f = incoming.firstOrNull() ?: return@launchPick
                                    scope.launch {
                                        var dataUrl = loadFileDataUrl(f) ?: return@launch
                                        if (f.type.contains("webp") || f.name.lowercase().endsWith(".webp")) {
                                            imgOp("convert", buildJsonObject { put("dataUrl", dataUrl); put("mime", "image/png"); put("quality", 1.0) }.toString())
                                            val conv = awaitAsync("img", 60_000)
                                            if (conv != null && conv.asyncOk()) dataUrl = conv.asyncStr("dataUrl")
                                        }
                                        // Refit the box to the NEW photo's ratio
                                        // (width kept) so a differently-shaped
                                        // replacement is never stretched.
                                        var iw = 0; var ih = 0
                                        imgOp("info", buildJsonObject { put("dataUrl", dataUrl) }.toString())
                                        val info = awaitAsync("img", 60_000)
                                        if (info != null && info.asyncOk()) {
                                            iw = info.asyncStr("w").toIntOrNull() ?: 0
                                            ih = info.asyncStr("h").toIntOrNull() ?: 0
                                        }
                                        snapshot()
                                        mutateSelected {
                                            if (iw > 0 && ih > 0 && it.w > 0f)
                                                it.copy(dataUrl = dataUrl, h = it.w * ih / iw)
                                            else it.copy(dataUrl = dataUrl)
                                        }
                                    }
                                }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Replace Image", fontSize = 13.sp) }
                        }
                    }
                    if (sel.type == "rect" || sel.type == "ellipse") {
                        item {
                            FilterChip(selected = sel.fill, onClick = { mutateSelected { it.copy(fill = !it.fill) } },
                                label = { Text("Filled shape", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White))
                        }
                    }
                    if (sel.type == "path") {
                        item {
                            Text("Pen width: ${sel.strokeWidth.toInt()} pt", fontSize = 12.sp, color = MutedText)
                            Slider(value = sel.strokeWidth, onValueChange = { v -> mutateSelected { it.copy(strokeWidth = v) } },
                                valueRange = 1f..8f)
                        }
                    }
                }
                if (sel.type != "redact") {
                    item {
                        Text("Colour", fontSize = 12.sp, color = MutedText)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            ED_COLORS.forEach { c ->
                                Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
                                    .background(Color(c))
                                    .border(if (sel.color == c) 2.dp else 0.5.dp,
                                        if (sel.color == c) PurplePrimary else Color(0xFFCBD5E1), RoundedCornerShape(7.dp))
                                    .clickable { mutateSelected { it.copy(color = c) } })
                            }
                        }
                    }
                }
                item {
                    Text("Rotation: ${sel.rotation.toInt()}°", fontSize = 12.sp, color = MutedText)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)) {
                        OutlinedButton(onClick = { mutateSelected { it.copy(rotation = (it.rotation - 15f + 360f) % 360f) } }) {
                            Text("⟲ 15°", fontSize = 12.sp)
                        }
                        OutlinedButton(onClick = { mutateSelected { it.copy(rotation = (it.rotation + 15f) % 360f) } }) {
                            Text("⟳ 15°", fontSize = 12.sp)
                        }
                        if (sel.rotation != 0f) {
                            TextButton(onClick = { mutateSelected { it.copy(rotation = 0f) } }) { Text("Reset", fontSize = 12.sp) }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { duplicateSelected() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Duplicate Object", fontSize = 13.sp)
                    }
                }
                item {
                    Button(onClick = { deleteSelected() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("Delete Object", fontSize = 13.sp)
                    }
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("This Page", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = NavyText)
                Text("Size: ${pageDims.first.toInt()} × ${pageDims.second.toInt()} pt" +
                    (if (page != null && page.extraRotation != 0) " · rotated ${page.extraRotation}°" else ""),
                    fontSize = 12.sp, color = MutedText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    OutlinedButton(onClick = { rotateCurrentPage(-90) }) { Text("Rotate Left", fontSize = 12.sp) }
                    OutlinedButton(onClick = { rotateCurrentPage(90) }) { Text("Rotate Right", fontSize = 12.sp) }
                }
                if (page?.crop != null) {
                    Spacer(Modifier.height(6.dp))
                    Text("Crop is set on this page (applied on download).", fontSize = 12.sp, color = MutedText)
                    TextButton(onClick = { snapshot(); page?.let { replacePage(current, it.copy(crop = null)) } }) {
                        Text("Clear Crop", fontSize = 12.5.sp)
                    }
                    TextButton(onClick = {
                        val c = page?.crop
                        if (c != null) {
                            snapshot()
                            var applied = 0
                            pages = pages.mapIndexed { i, p ->
                                val d = dims[i]
                                if (d != null && d.first == pageDims.first && d.second == pageDims.second) {
                                    applied++; p.copy(crop = c)
                                } else p
                            }
                            statusText = "Crop applied to $applied page(s) of the same size."
                        }
                    }) { Text("Apply Crop to All Pages (same size)", fontSize = 12.5.sp) }
                    TextButton(onClick = {
                        snapshot()
                        pages = pages.map { it.copy(crop = null) }
                        statusText = "Crop cleared on all pages."
                    }) { Text("Clear Crop on All Pages", fontSize = 12.5.sp) }
                }
                TextButton(onClick = {
                    if (pages.size > 1) {
                        snapshot()
                        pages = pages.filterIndexed { i, _ -> i != current }
                        if (current >= pages.size) current = pages.size - 1
                        selectedId = null
                        previews = emptyMap(); thumbs = emptyMap(); dims = emptyMap()
                        renderCurrent()
                        pages.indices.forEach { renderThumb(it) }
                    }
                }) { Text("Delete This Page", fontSize = 12.5.sp, color = Color(0xFFDC2626)) }
            }
            item {
                Spacer(Modifier.height(4.dp))
                Text("Watermark", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = NavyText)
                OutlinedTextField(value = wmText, onValueChange = { wmText = it },
                    label = { Text("Watermark text (all pages)") }, singleLine = true,
                    shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth())
                if (wmText.isNotBlank()) {
                    Text("Opacity: ${(wmOpacity * 100).toInt()}%", fontSize = 12.sp, color = MutedText)
                    Slider(value = wmOpacity, onValueChange = { wmOpacity = it }, valueRange = 0.05f..0.5f)
                    Text("Colour", fontSize = 12.sp, color = MutedText)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                        ED_COLORS.forEach { c ->
                            Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp))
                                .background(Color(c))
                                .border(if (wmColor == c) 2.dp else 0.5.dp,
                                    if (wmColor == c) PurplePrimary else Color(0xFFCBD5E1), RoundedCornerShape(7.dp))
                                .clickable { wmColor = c })
                        }
                    }
                }
            }
            item {
                Text("Note: text already inside the PDF can't be edited in place — cover it with a white rectangle (Shape → filled, white) and add new text on top. Redaction covers content with an opaque box.",
                    fontSize = 11.5.sp, color = MutedText)
            }
        }
    }

    // The page canvas: preview image + overlay objects + one unified pointer
    // handler (hit-testing done manually, so child gestures never conflict).
    // All state reads inside the handler go through the state delegates, so
    // they stay live for the whole drag.
    val pageCanvas: @Composable () -> Unit = {
        val d = dims[current] ?: (595.28f to 841.89f)
        val pg = pages.getOrNull(current)
        val previewBmp = remember(previews[current]) { previews[current]?.let { decodeBitmapFromDataUrl(it) } }
        Box(
            Modifier.size((d.first * zoom).dp, (d.second * zoom).dp)
                .background(Color.White)
                .border(1.dp, Color(0xFFCBD5E1))
                .pointerInput(tool, current) {
                    awaitPointerEventScope {
                        while (true) {
                            var down = awaitPointerEvent()
                            while (down.type != PointerEventType.Press) down = awaitPointerEvent()
                            val change = down.changes.firstOrNull() ?: continue
                            focusRequester.requestFocus()
                            val liveDims = dims[current] ?: (595.28f to 841.89f)
                            val pw = size.width.toFloat(); val ph = size.height.toFloat()
                            fun toPt(o: Offset) = ((o.x / pw) * liveDims.first) to ((o.y / ph) * liveDims.second)
                            val (sx, sy) = toPt(change.position)
                            val livePage = pages.getOrNull(current)
                            var mode = "none"
                            var dragId: Int? = null
                            var handle = -1
                            var grabDx = 0f; var grabDy = 0f
                            var didSnapshot = false
                            // Resize session anchor: the object exactly as it
                            // was at pointer-down. Every move recomputes the
                            // box from THIS plus the pointer's total
                            // displacement — per-event deltas accumulated
                            // onto live state could be corrupted by dropped
                            // or coalesced browser move events, which left
                            // edge-handle drags uncommitted (the object
                            // snapped back on release).
                            var dragStartObj: EdObj? = null
                            // Rotate-drag session anchors (handle index 8).
                            var startAngle = 0f
                            var startRotation = 0f
                            val liveSel = livePage?.objects?.firstOrNull { it.id == selectedId }
                            if (tool == EdTool.Select && liveSel != null) {
                                // Nearest handle within a zoom-aware tolerance
                                // (the old scan used a fixed ±11 pt box and let
                                // the LAST match win). Handles rotate with the
                                // object; text objects expose only the rotate
                                // handle (they have no resize handles).
                                val tol = (14f / zoom).coerceIn(11f, 40f)
                                val allHandles = edHandlesRotated(liveSel.x, liveSel.y, liveSel.w, liveSel.h,
                                    liveSel.rotation, 30f / zoom)
                                handle = if (liveSel.type == "text") {
                                    val rp = allHandles[8]
                                    if (max(abs(sx - rp.first), abs(sy - rp.second)) <= tol) 8 else -1
                                } else edPickHandle(allHandles, sx, sy, tol)
                                if (handle == 8) {
                                    startAngle = edAngleDeg(liveSel.x + liveSel.w / 2f, liveSel.y + liveSel.h / 2f, sx, sy)
                                    startRotation = liveSel.rotation
                                }
                            }
                            // Eraser: removes drawn strokes and highlights it
                            // touches — one undo snapshot per gesture.
                            var eraseSnap = false
                            fun eraseNow(px: Float, py: Float) {
                                val p = pages.getOrNull(current) ?: return
                                val hit = p.objects.lastOrNull {
                                    (it.type == "path" || it.type == "highlight") &&
                                        edHitTest(it.x, it.y, it.w, it.h, it.rotation, px, py)
                                } ?: return
                                if (!eraseSnap) { snapshot(); eraseSnap = true }
                                replacePage(current, p.copy(objects = p.objects.filter { o -> o.id != hit.id }))
                                if (selectedId == hit.id) selectedId = null
                            }
                            if (handle >= 0 && liveSel != null) {
                                mode = if (handle == 8) "rotate" else "resize"; dragId = liveSel.id; dragStartObj = liveSel
                            }
                            else when (tool) {
                                EdTool.Select -> {
                                    val hit = livePage?.objects?.lastOrNull {
                                        edHitTest(it.x, it.y, it.w, it.h, it.rotation, sx, sy)
                                    }
                                    if (hit != null) {
                                        selectedId = hit.id; mode = "move"; dragId = hit.id
                                        grabDx = sx - hit.x; grabDy = sy - hit.y
                                    } else selectedId = null
                                }
                                EdTool.Text -> mode = "text"
                                EdTool.Draw -> { mode = "draw"; draftPoints = listOf(sx to sy) }
                                EdTool.Eraser -> { mode = "erase"; eraseNow(sx, sy) }
                                EdTool.Highlight, EdTool.Underline, EdTool.Strike, EdTool.Squiggle,
                                EdTool.Rect, EdTool.Ellipse, EdTool.Line, EdTool.Check, EdTool.Cross,
                                EdTool.Arrow, EdTool.Redact, EdTool.Crop -> {
                                    mode = "rect"; draftRect = listOf(sx, sy, 0f, 0f)
                                }
                            }
                            // A started gesture belongs to the canvas: consume
                            // the press (and the moves below) so the two scroll
                            // containers wrapping the canvas don't also claim
                            // the drag and scroll the page away mid-resize.
                            if (mode != "none") change.consume()
                            var released = false
                            while (!released) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull() ?: break
                                val (cx, cy) = toPt(ch.position)
                                when (ev.type) {
                                    PointerEventType.Move -> {
                                        if (mode != "none") ch.consume()
                                        if (!didSnapshot && (mode == "move" || mode == "resize" || mode == "rotate") &&
                                            (abs(cx - sx) > 1f || abs(cy - sy) > 1f)) { snapshot(); didSnapshot = true }
                                        when (mode) {
                                            "rotate" -> {
                                                // Angle of the pointer around the
                                                // object's centre, relative to the
                                                // grab angle — snapped near 15° steps.
                                                val id = dragId; val startObj = dragStartObj; val p = pages.getOrNull(current)
                                                if (id != null && startObj != null && p != null) {
                                                    val ocx = startObj.x + startObj.w / 2f
                                                    val ocy = startObj.y + startObj.h / 2f
                                                    val rot = edSnapAngle(startRotation +
                                                        (edAngleDeg(ocx, ocy, cx, cy) - startAngle))
                                                    replacePage(current, p.copy(objects = p.objects.map {
                                                        if (it.id == id) it.copy(rotation = rot) else it
                                                    }))
                                                }
                                            }
                                            "erase" -> eraseNow(cx, cy)
                                            "move" -> {
                                                val id = dragId; val p = pages.getOrNull(current)
                                                if (id != null && p != null) replacePage(current, p.copy(objects = p.objects.map {
                                                    if (it.id == id) it.copy(
                                                        x = (cx - grabDx).coerceIn(-it.w / 2, liveDims.first - 16f),
                                                        y = (cy - grabDy).coerceIn(0f, liveDims.second - 12f)
                                                    ) else it
                                                }))
                                            }
                                            "resize" -> {
                                                // Session math: start object + TOTAL
                                                // displacement from the press point.
                                                // The rendered box IS the committed
                                                // box at every instant, so release
                                                // (or cancellation) simply keeps it.
                                                val id = dragId; val startObj = dragStartObj; val p = pages.getOrNull(current)
                                                if (id != null && startObj != null && p != null) replacePage(current, p.copy(objects = p.objects.map {
                                                    if (it.id == id) edResize(startObj, handle, cx - sx, cy - sy) else it
                                                }))
                                            }
                                            "draw" -> {
                                                val last = draftPoints.lastOrNull()
                                                if (last == null || abs(cx - last.first) > 2f || abs(cy - last.second) > 2f)
                                                    draftPoints = draftPoints + (cx to cy)
                                            }
                                            "rect" -> draftRect = listOf(min(sx, cx), min(sy, cy), abs(cx - sx), abs(cy - sy))
                                        }
                                    }
                                    PointerEventType.Release -> {
                                        released = true
                                        when (mode) {
                                            "text" -> if (abs(cx - sx) < 5f && abs(cy - sy) < 5f) addTextAt(sx, sy)
                                            "draw" -> {
                                                val pts = draftPoints; draftPoints = emptyList()
                                                if (pts.size > 1) {
                                                    val minX = pts.minOf { it.first }; val minY = pts.minOf { it.second }
                                                    val maxX = pts.maxOf { it.first }; val maxY = pts.maxOf { it.second }
                                                    val w = max(4f, maxX - minX); val h = max(4f, maxY - minY)
                                                    addObject(EdObj(nextId++, "path", minX, minY, w, h,
                                                        color = 0xFF2563EB, strokeWidth = 2.2f,
                                                        points = pts.map { ((it.first - minX) / w) to ((it.second - minY) / h) }))
                                                }
                                            }
                                            "rect" -> {
                                                val dr = draftRect; draftRect = null
                                                // Text-markup strokes are thin by
                                                // nature: they need drag WIDTH,
                                                // not height.
                                                val isMarkup = tool == EdTool.Underline || tool == EdTool.Strike ||
                                                    tool == EdTool.Squiggle
                                                if (dr != null && (if (isMarkup) dr[2] > 10f else (dr[2] > 6f && dr[3] > 6f))) {
                                                    when (tool) {
                                                        EdTool.Highlight -> addObject(EdObj(nextId++, "highlight", dr[0], dr[1], dr[2], dr[3], color = 0xFFFDE047, opacity = 0.35f))
                                                        EdTool.Underline -> {
                                                            val b = edMarkupBox("underline", sx, sy, cx, cy)
                                                            addObject(EdObj(nextId++, "path", b.x, b.y, b.w, b.h, color = 0xFF111827, strokeWidth = 1.8f, points = listOf(0f to 0.5f, 1f to 0.5f)))
                                                        }
                                                        EdTool.Strike -> {
                                                            val b = edMarkupBox("strike", sx, sy, cx, cy)
                                                            addObject(EdObj(nextId++, "path", b.x, b.y, b.w, b.h, color = 0xFF111827, strokeWidth = 1.8f, points = listOf(0f to 0.5f, 1f to 0.5f)))
                                                        }
                                                        EdTool.Squiggle -> {
                                                            val b = edMarkupBox("squiggle", sx, sy, cx, cy)
                                                            addObject(EdObj(nextId++, "path", b.x, b.y, b.w, b.h, color = 0xFF111827, strokeWidth = 1.6f, points = edSquigglePoints()))
                                                        }
                                                        EdTool.Redact -> addObject(EdObj(nextId++, "redact", dr[0], dr[1], dr[2], dr[3]))
                                                        EdTool.Rect -> addObject(EdObj(nextId++, "rect", dr[0], dr[1], dr[2], dr[3], color = 0xFF7C3AED, strokeWidth = 1.5f))
                                                        EdTool.Ellipse -> addObject(EdObj(nextId++, "ellipse", dr[0], dr[1], dr[2], dr[3], color = 0xFF7C3AED, strokeWidth = 1.5f))
                                                        EdTool.Line -> addObject(EdObj(nextId++, "path", dr[0], dr[1], dr[2], dr[3], color = 0xFF111827, strokeWidth = 1.5f, points = listOf(0f to 0f, 1f to 1f)))
                                                        EdTool.Check -> addObject(EdObj(nextId++, "path", dr[0], dr[1], dr[2], dr[3], color = 0xFF059669, strokeWidth = 3.2f, points = edCheckPoints()))
                                                        EdTool.Cross -> addObject(EdObj(nextId++, "path", dr[0], dr[1], dr[2], dr[3], color = 0xFFDC2626, strokeWidth = 3.2f, points = edCrossPoints()))
                                                        EdTool.Arrow -> addObject(EdObj(nextId++, "path", dr[0], dr[1], dr[2], dr[3], color = 0xFF111827, strokeWidth = 2.6f, points = edArrowPoints()))
                                                        EdTool.Crop -> {
                                                            val p = pages.getOrNull(current)
                                                            if (p != null) {
                                                                snapshot()
                                                                replacePage(current, p.copy(crop = dr))
                                                                statusText = "Crop set on this page — it applies when you download."
                                                            }
                                                        }
                                                        else -> {}
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
        ) {
            if (pg != null && pg.srcIndex < 0) {
                Text("Blank page", color = MutedText, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            } else if (previewBmp != null) {
                Image(previewBmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            } else if (pageErrors[current] != null) {
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(pageErrors[current]!!, color = Color(0xFFB91C1C), fontSize = 13.sp,
                        textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { renderCurrent(); renderThumb(current) }) { Text("Retry") }
                }
            } else {
                CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.align(Alignment.Center).size(22.dp))
            }
            // crop mask
            val crop = pg?.crop
            if (crop != null) {
                val mask = Color(0x59000000)
                Box(Modifier.offset(0.dp, 0.dp).size((crop[0] * zoom).dp, (d.second * zoom).dp).background(mask))
                Box(Modifier.offset(((crop[0] + crop[2]) * zoom).dp, 0.dp)
                    .size((((d.first - crop[0] - crop[2]).coerceAtLeast(0f)) * zoom).dp, (d.second * zoom).dp).background(mask))
                Box(Modifier.offset((crop[0] * zoom).dp, 0.dp).size((crop[2] * zoom).dp, (crop[1] * zoom).dp).background(mask))
                Box(Modifier.offset((crop[0] * zoom).dp, ((crop[1] + crop[3]) * zoom).dp)
                    .size((crop[2] * zoom).dp, (((d.second - crop[1] - crop[3]).coerceAtLeast(0f)) * zoom).dp).background(mask))
                Box(Modifier.offset((crop[0] * zoom).dp, (crop[1] * zoom).dp)
                    .size((crop[2] * zoom).dp, (crop[3] * zoom).dp)
                    .border(1.5.dp, PurplePrimary))
            }
            // watermark preview
            if (wmText.isNotBlank()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(wmText, fontSize = (54 * zoom).sp, fontWeight = FontWeight.Bold,
                        color = Color(wmColor).copy(alpha = wmOpacity),
                        modifier = Modifier.graphicsLayer(rotationZ = -30f))
                }
            }
            // objects
            pg?.objects?.forEach { o -> EdObjView(o, zoom, o.id == selectedId) }
            // draft shape
            val dr = draftRect
            if (dr != null && dr[2] > 0f) {
                Box(Modifier.offset((dr[0] * zoom).dp, (dr[1] * zoom).dp)
                    .size((dr[2] * zoom).dp, (dr[3] * zoom).dp)
                    .background(PurplePrimary.copy(alpha = 0.15f))
                    .border(1.5.dp, PurplePrimary))
            }
            // draft freehand
            if (draftPoints.size > 1) {
                Canvas(Modifier.fillMaxSize()) {
                    val col = Color(0xFF2563EB)
                    for (i in 0 until draftPoints.size - 1) {
                        drawLine(col,
                            Offset(draftPoints[i].first / d.first * size.width, draftPoints[i].second / d.second * size.height),
                            Offset(draftPoints[i + 1].first / d.first * size.width, draftPoints[i + 1].second / d.second * size.height),
                            strokeWidth = (2.2f * zoom).dp.toPx())
                    }
                }
            }
        }
    }

    fun afterStructureChange(newCurrent: Int) {
        current = newCurrent.coerceIn(0, max(0, pages.size - 1))
        selectedId = null
        previews = emptyMap(); thumbs = emptyMap(); dims = emptyMap(); pageErrors = emptyMap()
        renderCurrent()
        pages.indices.forEach { renderThumb(it) }
    }

    /**
     * Organize → "Add Pages from PDF": appends every page of another
     * PDF to the open document (the shell's appendTo op returns the
     * combined file, which becomes the editor's new source) and adds
     * the new pages at the end of the page list.
     */
    fun addPagesFlow() {
        val srcB64 = pdfB64 ?: return
        if (pages.size >= 50) { errorText = "This document already fills the editor's 50-page limit."; return }
        launchPick(scope, PDF_SPEC, false, current = { emptyList() }) { incoming, err ->
            if (err != null) { errorText = err; return@launchPick }
            val f = incoming.firstOrNull() ?: return@launchPick
            scope.launch {
                val other = loadFileBase64(f.id) ?: run { errorText = "Could not read ${f.name}."; return@launch }
                busy = true; errorText = null; statusText = "Adding pages from ${f.name}…"
                val libErr = ensureEditorLibs()
                if (libErr != null) { busy = false; errorText = libErr; return@launch }
                val res = pdfCall("appendTo", buildJsonObject {
                    put("pdf", srcB64); put("pdf2", other)
                }.toString(), 300_000)
                busy = false
                if (res == null || !res.asyncOk()) {
                    errorText = res?.asyncError() ?: "Could not add pages from that PDF."
                    return@launch
                }
                val combined = res.asyncStr("b64")
                val from = res.asyncStr("from").toIntOrNull() ?: 0
                val added = res.asyncStr("added").toIntOrNull() ?: 0
                if (combined.isBlank() || added <= 0) { errorText = "That PDF has no pages to add."; return@launch }
                snapshot()
                val oldSize = pages.size
                val take = min(added, max(0, 50 - oldSize))
                pdfB64 = combined
                if (take > 0) pages = pages + (0 until take).map { EdPageState(srcIndex = from + it) }
                pdfSaveSession(combined, fileName)
                statusText = when {
                    take < added -> "Added the first $take of $added pages from ${f.name} (50-page editor limit)."
                    else -> "Added $added page(s) from ${f.name} — they are at the end of the list; reorder them here if you need to."
                }
                // The combined document may carry different form fields.
                val fres = pdfCall("formFields", buildJsonObject { put("pdf", combined) }.toString(), 60_000)
                if (fres != null && fres.asyncOk()) formFields = parseEdFormFields(fres)
                afterStructureChange(if (take > 0) oldSize else current)
            }
        }
    }

    /**
     * Organize → "Extract to New PDF": downloads a new PDF holding the
     * chosen pages (numbers as shown in the page list) in list order.
     */
    fun extractFlow() {
        val srcB64 = pdfB64 ?: return
        val picked = edParseRange(extractRange, pages.size)
        if (picked.isEmpty()) { errorText = "Enter the pages to extract first, like 1-3, 5."; return }
        val srcPages = picked.mapNotNull { i ->
            pages.getOrNull(i)?.srcIndex?.takeIf { it >= 0 }?.plus(1)
        }
        if (srcPages.isEmpty()) { errorText = "Those are blank pages — there is nothing to extract."; return }
        busy = true; errorText = null
        scope.launch {
            val libErr = ensureEditorLibs()
            if (libErr != null) { busy = false; errorText = libErr; return@launch }
            val res = pdfCall("extract", buildJsonObject {
                put("pdf", srcB64)
                put("pages", buildJsonArray { srcPages.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
            }.toString(), 300_000)
            busy = false
            if (res == null || !res.asyncOk()) { errorText = res?.asyncError() ?: "Extract failed."; return@launch }
            val outName = fileName.substringBeforeLast(".").ifBlank { "document" } + "-extract.pdf"
            downloadFileBase64(outName, res.asyncStr("b64"), "application/pdf")
            recordHistory("PDF Editor", outName, "Extracted ${srcPages.size} page(s)")
            statusText = "Downloaded $outName with ${res.asyncStr("pages")} page(s)."
        }
    }

    // ── layout ────────────────────────────────────────────────────────────
    Column(
        Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 8.dp)
            .focusRequester(focusRequester).focusable()
            .onKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown || pages.isEmpty()) return@onKeyEvent false
                val sel = selected
                when {
                    ev.key == Key.D && ev.isCtrlPressed -> {
                        if (sel != null) { duplicateSelected(); true } else false
                    }
                    ev.key == Key.Delete || ev.key == Key.Backspace -> {
                        if (sel != null) { deleteSelected(); true } else false
                    }
                    ev.key == Key.Z && ev.isCtrlPressed -> { undo(); true }
                    ev.key == Key.Y && ev.isCtrlPressed -> { redo(); true }
                    sel != null && (ev.key == Key.DirectionLeft || ev.key == Key.DirectionRight ||
                        ev.key == Key.DirectionUp || ev.key == Key.DirectionDown) -> {
                        val step = if (ev.isShiftPressed) 10f else 1f
                        mutateSelected {
                            it.copy(
                                x = it.x + when (ev.key) { Key.DirectionLeft -> -step; Key.DirectionRight -> step; else -> 0f },
                                y = it.y + when (ev.key) { Key.DirectionUp -> -step; Key.DirectionDown -> step; else -> 0f }
                            )
                        }
                        true
                    }
                    else -> false
                }
            }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(PurplePrimary),
                contentAlignment = Alignment.Center) {
                QrIcon(IconKind.Edit, Color.White, size = 22.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PDF Editor", fontSize = 21.sp, fontWeight = FontWeight.Bold, color = NavyText)
                Text("All Tools › PDF Tools › PDF Editor — edits are burned into the real PDF on download.",
                    fontSize = 12.5.sp, color = MutedText)
            }
            if (pages.isNotEmpty()) {
                OutlinedButton(onClick = {
                    files = emptyList(); pages = emptyList(); pdfB64 = null
                    previews = emptyMap(); thumbs = emptyMap(); dims = emptyMap(); selectedId = null
                    formFields = emptyList(); extractRange = ""
                    savedSession = null
                    idbClear(PDF_IDB_NAME)
                }) { Text("Open PDF", fontSize = 13.sp) }
                Spacer(Modifier.width(10.dp))
                Button(onClick = { download() }, enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = PurplePrimary),
                    shape = RoundedCornerShape(11.dp)) {
                    if (busy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    else Text("Download PDF", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (errorText != null) {
            Text(errorText!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
        }
        if (statusText != null && pages.isNotEmpty()) {
            Text(statusText!!, color = MutedText, fontSize = 12.5.sp)
            Spacer(Modifier.height(6.dp))
        }

        if (pages.isEmpty()) {
            WhiteCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(statusText ?: "Opening…", fontSize = 13.sp, color = MutedText)
                    }
                    FileZone(state, files, PDF_BIG_SPEC, "PDF only (max 100 MB) — forms, scans and documents",
                        bigUploadButton = true, bigUploadLabel = "Upload PDF", bigUploadPasteHint = false) { files = it.take(1) }
                    savedSession?.let { saved ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("You were editing “${saved.second}” on this device last time.",
                                fontSize = 12.5.sp, color = MutedText)
                            OutlinedButton(onClick = { resumeLastPdf() }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp)) {
                                Text("Resume last PDF", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                        }
                    }
                    Text("Open a PDF to add text, photos and signatures, highlight, redact, crop, rotate and organise pages — then download the finished file. Everything runs in your browser; your file never leaves this device.",
                        fontSize = 12.5.sp, color = MutedText)
                }
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
              val wide = maxWidth >= 980.dp
              // Full editor workspace (owner's order): opening a PDF opens
              // a real edit page — a full-width toolbar on top, page
              // thumbnails on the left, the page itself large and centred,
              // and Properties as a right-hand panel. On narrower screens
              // Properties floats over the page as an overlay instead of
              // stacking underneath and crushing the page into a sliver
              // (the old <1150 dp layout did exactly that on smaller PCs).
              LaunchedEffect(selectedId, wide) {
                  if (selectedId != null && !wide) propsOpen = true
              }
              Box(Modifier.fillMaxSize()) {
              Column(Modifier.fillMaxSize()) {
                // ── workspace toolbar (full width) ──
                    // file chip
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        QrIcon(IconKind.Pdf, Color(0xFFDC2626), size = 18.dp)
                        Spacer(Modifier.width(7.dp))
                        Text(fileName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = NavyText, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Text("${pages.size} pages", fontSize = 12.sp, color = MutedText)
                    }
                    Spacer(Modifier.height(8.dp))
                    // tabs (Fill Form appears only when the open PDF
                    // actually has fillable form fields)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val tabList = listOf(
                            EdTab.Edit to "Edit & Annotate",
                            EdTab.Sign to "Fill & Sign",
                            EdTab.Organize to "Organize Pages"
                        ) + if (formFields.isNotEmpty())
                            listOf(EdTab.Form to "Fill Form (${formFields.size})") else emptyList()
                        tabList.forEach { (t, label) ->
                            FilterChip(selected = tab == t, onClick = { tab = t },
                                label = { Text(label, fontSize = 12.5.sp) },
                                shape = RoundedCornerShape(10.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PurplePrimary, selectedLabelColor = Color.White))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    // tool rows per tab
                    when (tab) {
                        EdTab.Edit -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                EdToolBtn("Add Text", tool == EdTool.Text) { tool = EdTool.Text }
                                EdToolBtn("Draw", tool == EdTool.Draw) { tool = EdTool.Draw }
                                Box {
                                    var markOpen by remember { mutableStateOf(false) }
                                    EdToolBtn("Highlight ▾", tool == EdTool.Highlight || tool == EdTool.Underline ||
                                        tool == EdTool.Strike || tool == EdTool.Squiggle) { markOpen = true }
                                    DropdownMenu(expanded = markOpen, onDismissRequest = { markOpen = false }) {
                                        DropdownMenuItem(text = { Text("Highlight") }, onClick = { tool = EdTool.Highlight; markOpen = false })
                                        DropdownMenuItem(text = { Text("Underline") }, onClick = { tool = EdTool.Underline; markOpen = false })
                                        DropdownMenuItem(text = { Text("Strikethrough") }, onClick = { tool = EdTool.Strike; markOpen = false })
                                        DropdownMenuItem(text = { Text("Squiggle") }, onClick = { tool = EdTool.Squiggle; markOpen = false })
                                    }
                                }
                                Box {
                                    var shapeOpen by remember { mutableStateOf(false) }
                                    EdToolBtn("Shape ▾", tool == EdTool.Rect || tool == EdTool.Ellipse || tool == EdTool.Line ||
                                        tool == EdTool.Check || tool == EdTool.Cross || tool == EdTool.Arrow) { shapeOpen = true }
                                    DropdownMenu(expanded = shapeOpen, onDismissRequest = { shapeOpen = false }) {
                                        DropdownMenuItem(text = { Text("Rectangle") }, onClick = { tool = EdTool.Rect; shapeOpen = false })
                                        DropdownMenuItem(text = { Text("Ellipse") }, onClick = { tool = EdTool.Ellipse; shapeOpen = false })
                                        DropdownMenuItem(text = { Text("Line") }, onClick = { tool = EdTool.Line; shapeOpen = false })
                                        DropdownMenuItem(text = { Text("Checkmark") }, onClick = { tool = EdTool.Check; shapeOpen = false })
                                        DropdownMenuItem(text = { Text("Cross") }, onClick = { tool = EdTool.Cross; shapeOpen = false })
                                        DropdownMenuItem(text = { Text("Arrow") }, onClick = { tool = EdTool.Arrow; shapeOpen = false })
                                    }
                                }
                                EdToolBtn("Redact", tool == EdTool.Redact) { tool = EdTool.Redact }
                                EdToolBtn("Eraser", tool == EdTool.Eraser) { tool = EdTool.Eraser }
                                EdToolBtn("Add Image", false) { addImageFlow() }
                                EdToolBtn("Watermark", wmPanel) { wmPanel = !wmPanel }
                                EdToolBtn("Crop", tool == EdTool.Crop) { tool = EdTool.Crop }
                                EdToolBtn("Rotate", false) { rotateCurrentPage(90) }
                                EdToolBtn("↩ Undo", false, enabled = undoStack.isNotEmpty()) { undo() }
                                EdToolBtn("↪ Redo", false, enabled = redoStack.isNotEmpty()) { redo() }
                            }
                            if (tool == EdTool.Eraser) {
                                Text("Eraser: click or drag over drawings and highlights to remove them.",
                                    fontSize = 12.sp, color = MutedText, modifier = Modifier.padding(top = 6.dp))
                            }
                            if (wmPanel) {
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(value = wmText, onValueChange = { wmText = it },
                                        label = { Text("Watermark (all pages)") }, singleLine = true,
                                        shape = RoundedCornerShape(10.dp), modifier = Modifier.weight(1f))
                                    Column(Modifier.width(170.dp)) {
                                        Text("Opacity ${(wmOpacity * 100).toInt()}%", fontSize = 11.5.sp, color = MutedText)
                                        Slider(value = wmOpacity, onValueChange = { wmOpacity = it }, valueRange = 0.05f..0.5f)
                                    }
                                    TextButton(onClick = { wmPanel = false }) { Text("Done", fontSize = 12.5.sp) }
                                }
                            }
                        }
                        EdTab.Sign -> {
                            var curStroke by remember { mutableStateOf<List<Pair<Float, Float>>>(emptyList()) }
                            var typedName by remember { mutableStateOf("") }
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    var down = awaitPointerEvent()
                            while (down.type != PointerEventType.Press) down = awaitPointerEvent()
                                                    val c0 = down.changes.firstOrNull() ?: continue
                                                    curStroke = listOf((c0.position.x / size.width) to (c0.position.y / size.height))
                                                    var done = false
                                                    while (!done) {
                                                        val ev = awaitPointerEvent()
                                                        val ch = ev.changes.firstOrNull() ?: break
                                                        when (ev.type) {
                                                            PointerEventType.Move -> curStroke = curStroke + ((ch.position.x / size.width) to (ch.position.y / size.height))
                                                            PointerEventType.Release -> { done = true; if (curStroke.size > 1) sigStrokes = sigStrokes + listOf(curStroke); curStroke = emptyList() }
                                                            else -> {}
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    Canvas(Modifier.fillMaxSize()) {
                                        val col = Color(0xFF1E3A8A)
                                        (sigStrokes + if (curStroke.isNotEmpty()) listOf(curStroke) else emptyList()).forEach { stroke ->
                                            for (i in 0 until stroke.size - 1) {
                                                drawLine(col,
                                                    Offset(stroke[i].first * size.width, stroke[i].second * size.height),
                                                    Offset(stroke[i + 1].first * size.width, stroke[i + 1].second * size.height),
                                                    strokeWidth = 2.4.dp.toPx())
                                            }
                                        }
                                    }
                                    if (sigStrokes.isEmpty() && curStroke.isEmpty()) {
                                        Text("Draw your signature here", color = MutedText, fontSize = 13.sp,
                                            modifier = Modifier.align(Alignment.Center))
                                    }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    EdToolBtn("Clear", false, enabled = sigStrokes.isNotEmpty()) { sigStrokes = emptyList() }
                                    EdToolBtn("Save Signature", false, enabled = sigStrokes.isNotEmpty()) {
                                        savedSigs = savedSigs + listOf(sigStrokes); persistSigs()
                                        state.toast("Signature saved on this device")
                                    }
                                    EdToolBtn("Place on Page", false, enabled = sigStrokes.isNotEmpty()) { placeSignature(sigStrokes) }
                                    EdToolBtn("Add Text", false) { tab = EdTab.Edit; tool = EdTool.Text }
                                    EdToolBtn("Today's Date", false) {
                                        val parts = todayIst().split("-")
                                        val months = listOf("Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec")
                                        val label = if (parts.size == 3) "${parts[2].toIntOrNull() ?: parts[2]} ${months.getOrElse((parts[1].toIntOrNull() ?: 1) - 1) { "" }} ${parts[0]}" else todayIst()
                                        addTextAt(60f, 60f, label)
                                    }
                                }
                                if (savedSigs.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Saved:", fontSize = 12.sp, color = MutedText)
                                        savedSigs.forEachIndexed { si, sig ->
                                            Box(Modifier.width(110.dp).height(44.dp).clip(RoundedCornerShape(8.dp))
                                                .background(Color.White)
                                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                                .clickable { placeSignature(sig) }) {
                                                Canvas(Modifier.fillMaxSize().padding(4.dp)) {
                                                    val col = Color(0xFF1E3A8A)
                                                    sig.forEach { stroke ->
                                                        for (i in 0 until stroke.size - 1) {
                                                            drawLine(col,
                                                                Offset(stroke[i].first * size.width, stroke[i].second * size.height),
                                                                Offset(stroke[i + 1].first * size.width, stroke[i + 1].second * size.height),
                                                                strokeWidth = 1.6.dp.toPx())
                                                        }
                                                    }
                                                }
                                            }
                                            TextButton(onClick = { savedSigs = savedSigs.filterIndexed { i, _ -> i != si }; persistSigs() }) {
                                                Text("✕", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                                // Type-a-signature: the name becomes a
                                // placeable signature in a script style —
                                // no drawing needed.
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                    OutlinedTextField(value = typedName, onValueChange = { typedName = it },
                                        label = { Text("Or type your name") }, singleLine = true,
                                        shape = RoundedCornerShape(10.dp), modifier = Modifier.width(230.dp))
                                    EdToolBtn("Place Typed Signature", false, enabled = typedName.isNotBlank()) {
                                        val name = typedName.trim()
                                        val (tw, th) = edTextBox(name, 30f)
                                        addObject(EdObj(nextId++, "text",
                                            x = pageDims.first * 0.5f, y = pageDims.second * 0.62f, w = tw, h = th,
                                            text = name, fontSize = 30f, color = 0xFF1E3A8A, fontFamily = "script"))
                                        statusText = "Signature placed — drag it onto the signature line, rotate it with the round handle if needed."
                                    }
                                }
                            }
                        }
                        EdTab.Organize -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                EdToolBtn("Add Blank Page", false) {
                                    snapshot()
                                    pages = pages.toMutableList().also { it.add(current + 1, EdPageState(srcIndex = -1)) }
                                    afterStructureChange(current + 1)
                                }
                                EdToolBtn("Duplicate Page", false) {
                                    val p = page
                                    if (p != null) {
                                        snapshot()
                                        pages = pages.toMutableList().also { it.add(current + 1, p.copy(objects = p.objects.map { o -> o.copy() })) }
                                        afterStructureChange(current + 1)
                                    }
                                }
                                EdToolBtn("Move Earlier", false, enabled = current > 0) {
                                    snapshot()
                                    pages = pages.toMutableList().also { val t = it[current - 1]; it[current - 1] = it[current]; it[current] = t }
                                    afterStructureChange(current - 1)
                                }
                                EdToolBtn("Move Later", false, enabled = current < pages.size - 1) {
                                    snapshot()
                                    pages = pages.toMutableList().also { val t = it[current + 1]; it[current + 1] = it[current]; it[current] = t }
                                    afterStructureChange(current + 1)
                                }
                                EdToolBtn("Delete Page", false, enabled = pages.size > 1) {
                                    snapshot()
                                    pages = pages.filterIndexed { i, _ -> i != current }
                                    afterStructureChange(min(current, pages.size - 1))
                                }
                                EdToolBtn("Add Pages from PDF", false) { addPagesFlow() }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 8.dp)) {
                                OutlinedTextField(
                                    value = extractRange,
                                    onValueChange = { extractRange = it },
                                    label = { Text("Pages to extract, e.g. 1-3, 5") },
                                    singleLine = true, shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.width(230.dp)
                                )
                                EdToolBtn("Extract to New PDF", false) { extractFlow() }
                            }
                            Text("Reorder with the ↑ ↓ buttons under each thumbnail, or select a page and use Move Earlier / Move Later. Page order applies on download.",
                                fontSize = 12.sp, color = MutedText, modifier = Modifier.padding(top = 6.dp))
                        }
                        EdTab.Form -> {
                            Column(
                                Modifier.fillMaxWidth().heightIn(max = 330.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("Fill this PDF's form fields below. The values are written into the file and flattened when you tap Download PDF, so they print and display everywhere.",
                                    fontSize = 12.sp, color = MutedText)
                                formFields.forEach { f ->
                                    when (f.type) {
                                        "text" -> OutlinedTextField(
                                            value = f.value,
                                            onValueChange = { v ->
                                                formFields = formFields.map { if (it.name == f.name) it.copy(value = v) else it }
                                            },
                                            label = { Text(f.name) }, singleLine = true,
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        "checkbox" -> Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = f.value == "true",
                                                onCheckedChange = { c ->
                                                    formFields = formFields.map {
                                                        if (it.name == f.name) it.copy(value = if (c) "true" else "false") else it
                                                    }
                                                }
                                            )
                                            Text(f.name, fontSize = 13.sp, color = NavyText)
                                        }
                                        "radio", "dropdown" -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(f.name, fontSize = 12.5.sp, color = MutedText, fontWeight = FontWeight.Medium)
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                                f.options.forEach { opt ->
                                                    FilterChip(
                                                        selected = f.value == opt,
                                                        onClick = {
                                                            formFields = formFields.map {
                                                                if (it.name == f.name) it.copy(value = opt) else it
                                                            }
                                                        },
                                                        label = { Text(opt, fontSize = 12.sp) },
                                                        shape = RoundedCornerShape(16.dp),
                                                        colors = FilterChipDefaults.filterChipColors(
                                                            selectedContainerColor = PurplePrimary,
                                                            selectedLabelColor = Color.White)
                                                    )
                                                }
                                            }
                                        }
                                        else -> Text("${f.name} — this field type can't be filled here.",
                                            fontSize = 12.sp, color = MutedText)
                                    }
                                }
                            }
                        }
                    }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.weight(1f).fillMaxWidth()) {
                // ── thumbnails rail ──
                LazyColumn(
                    Modifier.width(96.dp).fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(pages) { idx, pg ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val thumbBmp = remember(thumbs[idx]) { thumbs[idx]?.let { decodeBitmapFromDataUrl(it) } }
                            Box(
                                Modifier.width(68.dp).height(92.dp).clip(RoundedCornerShape(8.dp))
                                    .background(Color.White)
                                    .border(if (idx == current) 2.dp else 1.dp,
                                        if (idx == current) PurplePrimary else Color(0xFFCBD5E1), RoundedCornerShape(8.dp))
                                    .clickable { current = idx; selectedId = null; if (previews[idx] == null) renderCurrent() },
                                contentAlignment = Alignment.Center
                            ) {
                                if (thumbBmp != null) Image(thumbBmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                                else if (pg.srcIndex < 0) Text("Blank", fontSize = 10.sp, color = MutedText)
                                else if (pageErrors[idx] != null) Text("!", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C))
                                else CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.size(14.dp))
                            }
                            Text("${idx + 1}", fontSize = 11.sp, fontWeight = if (idx == current) FontWeight.Bold else FontWeight.Normal,
                                color = if (idx == current) PurplePrimary else MutedText)
                            if (tab == EdTab.Organize) {
                                Row {
                                    TextButton(onClick = {
                                        if (idx > 0) { snapshot(); pages = pages.toMutableList().also { val t = it[idx - 1]; it[idx - 1] = it[idx]; it[idx] = t }; afterStructureChange(idx - 1) }
                                    }, enabled = idx > 0) { Text("↑", fontSize = 13.sp) }
                                    TextButton(onClick = {
                                        if (idx < pages.size - 1) { snapshot(); pages = pages.toMutableList().also { val t = it[idx + 1]; it[idx + 1] = it[idx]; it[idx] = t }; afterStructureChange(idx + 1) }
                                    }, enabled = idx < pages.size - 1) { Text("↓", fontSize = 13.sp) }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = {
                            snapshot()
                            pages = pages.toMutableList().also { it.add(current + 1, EdPageState(srcIndex = -1)) }
                            afterStructureChange(current + 1)
                        }) { Text("+ Add Page", fontSize = 11.5.sp) }
                    }
                }
                Spacer(Modifier.width(12.dp))
                // ── centre: page viewport + bottom bar ──
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    // ── page viewport: the page is fitted to the
                    // viewport width until the user zooms manually,
                    // and centred whenever it is narrower than the
                    // viewport — the page is the dominant element ──
                    BoxWithConstraints(
                        Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFFECECF3))
                    ) {
                        val vpW = maxWidth
                        val vpH = maxHeight
                        LaunchedEffect(vpW, vpH, current, dims[current], zoomTouched) {
                            if (!zoomTouched) {
                                val pw = dims[current]?.first ?: 595.28f
                                val ph = dims[current]?.second ?: 841.89f
                                // Fit the WHOLE page into the viewport — width
                                // AND height. Fitting the width alone left tall
                                // pages cut off at the bottom, with no way for
                                // the user to see or edit the lower half
                                // (Ayan's shop report, 25 Nov 2026).
                                val fit = min((vpW.value - 36f) / pw, (vpH.value - 36f) / ph)
                                    .coerceIn(0.1f, 1.6f)
                                if (abs(fit - zoom) > 0.01f) zoom = fit
                            }
                        }
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                val pageDp = ((dims[current]?.first ?: 595.28f) * zoom).dp
                                val pageHDp = ((dims[current]?.second ?: 841.89f) * zoom).dp
                                val sidePad = maxOf(16.dp, (vpW - pageDp) / 2)
                                val topPad = maxOf(16.dp, (vpH - pageHDp) / 2)
                                Box(Modifier.padding(horizontal = sidePad, vertical = topPad)) {
                                    pageCanvas()
                                }
                            }
                        }
                        if (busy) CircularProgressIndicator(color = PurplePrimary, modifier = Modifier.align(Alignment.Center))
                    }
                    Spacer(Modifier.height(8.dp))
                    // ── bottom bar (scrolls sideways on narrow screens
                    // instead of clipping the zoom controls) ──
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        OutlinedButton(onClick = { if (current > 0) { current--; selectedId = null; if (previews[current] == null) renderCurrent() } },
                            enabled = current > 0) { Text("‹ Prev", fontSize = 12.5.sp) }
                        Spacer(Modifier.width(10.dp))
                        Text("Page ${current + 1} / ${pages.size}", fontSize = 13.sp, color = NavyText, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(10.dp))
                        OutlinedButton(onClick = { if (current < pages.size - 1) { current++; selectedId = null; if (previews[current] == null) renderCurrent() } },
                            enabled = current < pages.size - 1) { Text("Next ›", fontSize = 12.5.sp) }
                        Spacer(Modifier.weight(1f))
                        if (!wide) {
                            TextButton(onClick = { propsOpen = !propsOpen }) { Text("Properties", fontSize = 12.5.sp) }
                        }
                        TextButton(onClick = { zoomTouched = true; zoom = max(0.4f, zoom - 0.1f) }) { Text("−", fontSize = 16.sp) }
                        Text("${(zoom * 100).toInt()}%", fontSize = 12.5.sp, color = NavyText)
                        TextButton(onClick = { zoomTouched = true; zoom = min(2.5f, zoom + 0.1f) }) { Text("+", fontSize = 16.sp) }
                        TextButton(onClick = { zoomTouched = true; zoom = 1f }) { Text("100%", fontSize = 12.5.sp) }
                        TextButton(onClick = { zoomTouched = false }) { Text("Fit", fontSize = 12.5.sp) }
                    }
                }
                    if (wide) {
                        Spacer(Modifier.width(12.dp))
                        WhiteCard(Modifier.width(300.dp).fillMaxHeight()) { propsPanel() }
                    }
                }
              }
              // ── narrow screens: Properties overlay ──
              if (!wide && propsOpen) {
                  Box(
                      Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(320.dp)
                          .padding(start = 4.dp, end = 10.dp, top = 2.dp, bottom = 2.dp)
                  ) {
                      WhiteCard(Modifier.fillMaxSize()) {
                          Column(Modifier.fillMaxSize()) {
                              Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                  TextButton(onClick = { propsOpen = false }) { Text("Close ✕", fontSize = 12.5.sp) }
                              }
                              Box(Modifier.weight(1f).fillMaxWidth()) { propsPanel() }
                          }
                      }
                  }
              }
              }
            }
        }
    }
}

// ── editor helper composables / geometry ───────────────────────────────────

@Composable
fun EdToolBtn(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.height(38.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) PurplePrimary else Color(0xFFF1EFFD))
            .border(1.dp, if (selected) PurplePrimary else Color(0xFFD9D2F7), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 13.dp)
            .alpha(if (enabled) 1f else 0.45f),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium,
            color = if (selected) Color.White else NavyText, maxLines = 1)
    }
}

@Composable
fun EdNumField(label: String, value: Float, onChange: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toInt().toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { t ->
            text = t
            // Clamp to a sane range: an unbounded typed value (e.g. a height
            // of 357160 pt) makes the canvas try to render an object bigger
            // than any real page and kills the renderer.
            t.toFloatOrNull()?.let { onChange(it.coerceIn(-2000f, 4000f)) }
        },
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.width(84.dp)
    )
}

/** Handle anchor points in page points: 4 corners, then T / R / B / L edges. */
fun edHandles(o: EdObj): List<Pair<Float, Float>> = listOf(
    o.x to o.y, (o.x + o.w) to o.y, o.x to (o.y + o.h), (o.x + o.w) to (o.y + o.h),
    (o.x + o.w / 2) to o.y, (o.x + o.w) to (o.y + o.h / 2),
    (o.x + o.w / 2) to (o.y + o.h), o.x to (o.y + o.h / 2)
)

fun edResize(o: EdObj, handle: Int, dx: Float, dy: Float): EdObj {
    // Images resize aspect-ratio-locked (any handle — corners AND edges —
    // scales the photo uniformly, so it can never be stretched or squeezed);
    // every other object type keeps the free resize. Math lives in
    // EdGeometry.kt (pure, unit-tested). The drag wiring calls this with
    // the object as it was at pointer-down plus the pointer's TOTAL
    // displacement (a drag session — see edResizeSession).
    val r = edResizeSession(EdBox(o.x, o.y, o.w, o.h), o.type == "image", handle, dx, dy)
    return o.copy(x = r.x, y = r.y, w = r.w, h = r.h)
}

@Composable
fun EdObjView(o: EdObj, zoom: Float, selected: Boolean) {
    Box(
        Modifier.offset((o.x * zoom).dp, (o.y * zoom).dp)
            .size((o.w * zoom).dp, (o.h * zoom).dp)
            .graphicsLayer(rotationZ = o.rotation)
    ) {
        when (o.type) {
            "text" -> Text(
                o.text,
                color = Color(o.color),
                fontSize = (o.fontSize * zoom).sp,
                fontWeight = if (o.bold) FontWeight.Bold else FontWeight.Normal,
                fontFamily = when (o.fontFamily) {
                    "serif" -> FontFamily.Serif
                    "mono" -> FontFamily.Monospace
                    "script" -> FontFamily.Cursive
                    else -> FontFamily.SansSerif
                },
                textAlign = when (o.align) {
                    "center" -> TextAlign.Center
                    "right" -> TextAlign.Right
                    else -> TextAlign.Left
                },
                lineHeight = (o.fontSize * 1.25f * zoom).sp,
                modifier = Modifier.alpha(o.opacity).fillMaxWidth()
            )
            "image" -> {
                val bmp = remember(o.dataUrl) { o.dataUrl?.let { decodeBitmapFromDataUrl(it) } }
                if (bmp != null) Image(bmp, null, Modifier.fillMaxSize().alpha(o.opacity), contentScale = ContentScale.FillBounds)
                else Box(Modifier.fillMaxSize().background(Color(0xFFE2E8F0)))
            }
            "rect" -> Box(
                Modifier.fillMaxSize().then(
                    if (o.fill) Modifier.background(Color(o.color).copy(alpha = o.opacity))
                    else Modifier.border((o.strokeWidth * zoom).dp, Color(o.color).copy(alpha = o.opacity))
                )
            )
            "highlight" -> Box(Modifier.fillMaxSize().background(Color(o.color).copy(alpha = o.opacity)))
            "redact" -> Box(Modifier.fillMaxSize().background(Color.Black))
            "ellipse" -> Canvas(Modifier.fillMaxSize()) {
                val col = Color(o.color).copy(alpha = o.opacity)
                if (o.fill) drawOval(col)
                else drawOval(col, style = Stroke(width = (o.strokeWidth * zoom).dp.toPx()))
            }
            "path" -> Canvas(Modifier.fillMaxSize()) {
                val col = Color(o.color).copy(alpha = o.opacity)
                for (i in 0 until o.points.size - 1) {
                    val a = o.points[i]; val b = o.points[i + 1]
                    if (a.first < 0 || b.first < 0) continue
                    drawLine(col,
                        Offset(a.first * size.width, a.second * size.height),
                        Offset(b.first * size.width, b.second * size.height),
                        strokeWidth = (o.strokeWidth * zoom).dp.toPx())
                }
            }
        }
        if (selected) {
            Box(Modifier.fillMaxSize().border(1.5.dp, PurplePrimary))
            // Rotate handle: a knob floating 30 dp above the top-centre
            // edge, joined by a stem. It lives inside the rotated layer,
            // so it orbits the object exactly where the canvas hit-test
            // (edHandlesRotated, gap 30/zoom points) expects it.
            Box(
                Modifier.offset((o.w * zoom / 2).dp - 0.75.dp, (-22).dp)
                    .size(1.5.dp, 22.dp)
                    .background(PurplePrimary)
            )
            Box(
                Modifier.offset((o.w * zoom / 2).dp - 8.dp, (-38).dp)
                    .size(16.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .border(1.5.dp, PurplePrimary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("⟳", fontSize = 10.sp, color = PurplePrimary)
            }
            if (o.type != "text") {
                edHandles(o).forEach { hp ->
                    Box(
                        Modifier.offset(((hp.first - o.x) * zoom).dp - 5.dp, ((hp.second - o.y) * zoom).dp - 5.dp)
                            .size(10.dp)
                            .background(Color.White, RoundedCornerShape(3.dp))
                            .border(1.5.dp, PurplePrimary, RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}
