package qrmaster.web.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import qrmaster.web.data.nowMillis
import kotlin.JsFun
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

// ── Shell tool bridges (index.html owns the JS) ────────────────────────────
// One shared file pipeline for BOTH input paths:
//   click-to-upload  -> pickFilesMulti()  -> __qmPickedQ
//   drag & drop      -> shell drop handler -> __qmDroppedQ
// Both queues deliver WebFile metadata; bytes are read on demand through
// the registry, and every tool validates through validateIncoming().

@JsFun(
    """(accept, multiple) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = accept;
        if (multiple) input.multiple = true;
        input.onchange = () => {
            const files = input.files || [];
            for (let i = 0; i < files.length; i++) {
                window.__qmFileSeq = (window.__qmFileSeq || 0) + 1;
                const id = 'f' + window.__qmFileSeq;
                window.__qmFiles = window.__qmFiles || {};
                window.__qmFiles[id] = files[i];
                (window.__qmPickedQ = window.__qmPickedQ || []).push(
                    JSON.stringify({ id: id, name: files[i].name || 'file', type: files[i].type || '', size: files[i].size || 0 }));
            }
            input.remove();
        };
        document.body.appendChild(input);
        input.click();
        setTimeout(() => { try { input.remove(); } catch (e) {} }, 120000);
    }"""
)
external fun pickFilesMulti(accept: String, multiple: Boolean)

@JsFun("() => { const q = window.__qmPickedQ || []; window.__qmPickedQ = []; return q.join('\\n'); }")
external fun pollPickedQueue(): String

@JsFun("() => { const q = window.__qmDroppedQ || []; window.__qmDroppedQ = []; return q.join('\\n'); }")
external fun pollDroppedQueue(): String

@JsFun(
    """(id) => {
        const key = 'file:' + id;
        window.__qmAsync = window.__qmAsync || {};
        window.__qmAsync[key] = '';
        const f = (window.__qmFiles || {})[id];
        if (!f) { window.__qmAsync[key] = JSON.stringify({ ok: false, error: 'File not found' }); return; }
        f.arrayBuffer().then((buf) => {
            const bytes = new Uint8Array(buf);
            let bin = '';
            const CH = 0x8000;
            for (let i = 0; i < bytes.length; i += CH) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + CH));
            window.__qmAsync[key] = JSON.stringify({ ok: true, b64: btoa(bin) });
        }).catch((e) => { window.__qmAsync[key] = JSON.stringify({ ok: false, error: '' + e }); });
    }"""
)
external fun readFileStart(id: String)

@JsFun("(id) => { if (window.__qmFiles) delete window.__qmFiles[id]; }")
external fun releaseFile(id: String)

@JsFun("(key) => { return (window.__qmAsync || {})[key] || ''; }")
external fun pollAsync(key: String): String

@JsFun("() => { if (window.__qmLoadPdfLibs) window.__qmLoadPdfLibs(); }")
external fun loadPdfLibs()

@JsFun("() => { return window.__qmLibStatus ? window.__qmLibStatus() : 'error:unavailable'; }")
external fun pdfLibStatus(): String

@JsFun("(op, argsJson, key) => { window.__qmPdfOp(op, argsJson, key); }")
external fun pdfOp(op: String, argsJson: String, key: String)

@JsFun("(op, argsJson) => { window.__qmImgOp(op, argsJson); }")
external fun imgOp(op: String, argsJson: String)

@JsFun("(op, argsJson, key) => { window.__qmImgOp(op, argsJson, key); }")
external fun imgOpKeyed(op: String, argsJson: String, key: String)

@JsFun("(dataUrl) => { window.__qmBgRemove(dataUrl); }")
external fun bgRemoveStart(dataUrl: String)

@JsFun("() => { return window.__qmBgProgress || ''; }")
external fun bgProgress(): String

@JsFun("(dataUrl, key) => { window.__qmBgRemove(dataUrl, key); }")
external fun bgRemoveStartKeyed(dataUrl: String, key: String)

@JsFun("(dataUrl, key) => { window.__qmFaceDetect(dataUrl, key); }")
external fun faceDetectStart(dataUrl: String, key: String)

@JsFun("(op, argsJson, key) => { window.__qmPassportOp(op, argsJson, key); }")
external fun passportOp(op: String, argsJson: String, key: String)

@JsFun("(name, json) => { if (window.__qmIdbSave) window.__qmIdbSave(name, json); }")
external fun idbSave(name: String, json: String)

@JsFun("(name, key) => { if (window.__qmIdbLoad) window.__qmIdbLoad(name, key); }")
external fun idbLoadStart(name: String, key: String)

@JsFun("(name) => { if (window.__qmIdbClear) window.__qmIdbClear(name); }")
external fun idbClear(name: String)

@JsFun("() => { if (window.__qmWarmBgModel) window.__qmWarmBgModel(); }")
external fun warmBgModel()

@JsFun("(url, key) => { window.__qmFetchDataUrl(url, key); }")
external fun fetchDataUrlStart(url: String, key: String)

@JsFun("(on) => { window.__qmPasteArmed = !!on; }")
external fun setPasteArmed(on: Boolean)

@JsFun(
    """(name, b64, mime) => {
        const bin = atob(b64);
        const bytes = new Uint8Array(bin.length);
        for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
        const blob = new Blob([bytes], { type: mime || 'application/octet-stream' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url; a.download = name;
        document.body.appendChild(a);
        a.click(); a.remove();
        setTimeout(() => URL.revokeObjectURL(url), 3000);
    }"""
)
external fun downloadFileBase64(name: String, base64: String, mime: String)

@JsFun(
    """(text) => {
        if (navigator.clipboard) { navigator.clipboard.writeText(text).catch(() => {}); return true; }
        return false;
    }"""
)
external fun copyTextToClipboard(text: String): Boolean

@JsFun("(dataUrl) => { window.__qmQrDecode(dataUrl); }")
external fun qrDecodeStart(dataUrl: String)

@JsFun("(b64, targetBytes) => { window.__qmPdfCompress(b64, targetBytes); }")
external fun pdfCompressStart(base64: String, targetBytes: Int)

@JsFun("() => { return window.__qmCompressProgress || ''; }")
external fun compressProgress(): String

@JsFun("(name, entriesJson) => { window.__qmZipDownload(name, entriesJson); }")
external fun zipDownload(name: String, entriesJson: String)

/** Exact byte size of a data URL's payload. */
fun dataUrlByteSize(dataUrl: String): Long {
    val b64 = dataUrl.substringAfter(",", "")
    if (b64.isEmpty()) return 0L
    val padding = when {
        b64.endsWith("==") -> 2
        b64.endsWith("=") -> 1
        else -> 0
    }
    return (b64.length * 3L) / 4 - padding
}

@JsFun("() => { return window.location.origin + window.location.pathname; }")
external fun pageBaseUrl(): String

// ── Kotlin-side helpers ────────────────────────────────────────────────────

data class WebFile(val id: String, val name: String, val type: String, val size: Long)

val toolsJson = Json { ignoreUnknownKeys = true }

fun parseFileMetas(joined: String): List<WebFile> {
    if (joined.isBlank()) return emptyList()
    return joined.split('\n').mapNotNull { line ->
        try {
            val o = toolsJson.parseToJsonElement(line).jsonObject
            WebFile(
                id = o["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                name = o["name"]?.jsonPrimitive?.content ?: "file",
                type = o["type"]?.jsonPrimitive?.content ?: "",
                size = o["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            )
        } catch (e: Exception) { null }
    }
}

/** Polls window.__qmAsync[key] until a non-empty JSON result or timeout. */
suspend fun awaitAsync(key: String, timeoutMs: Long = 120_000): JsonObject? {
    var waited = 0L
    while (waited < timeoutMs) {
        val s = pollAsync(key)
        if (s.isNotEmpty()) {
            return try { toolsJson.parseToJsonElement(s).jsonObject } catch (e: Exception) { null }
        }
        delay(150)
        waited += 150
    }
    return null
}

private var pdfCallSeq = 0

/**
 * Runs one shell PDF op on its OWN async slot and awaits its result.
 * Every op used to share the single 'pdf' slot: concurrent calls (the
 * PDF Editor fires a page render plus all thumbnail renders at once)
 * reset and overwrote the slot under each other, so callers consumed
 * another call's result — or none at all — and waited out the timeout.
 */
suspend fun pdfCall(op: String, argsJson: String, timeoutMs: Long = 120_000): JsonObject? {
    pdfCallSeq += 1
    val key = "pdf:$pdfCallSeq"
    pdfOp(op, argsJson, key)
    return awaitAsync(key, timeoutMs)
}

private var bgCallSeq = 0

/**
 * Background removal on its OWN async slot (same discipline as pdfCall),
 * reporting the shell's progress text through [onProgress] while waiting.
 * The first call also downloads the removal model (~40 MB), hence the
 * generous default timeout.
 */
suspend fun bgCall(
    dataUrl: String,
    timeoutMs: Long = 300_000,
    onProgress: (String) -> Unit = {}
): JsonObject? {
    bgCallSeq += 1
    val key = "bg:$bgCallSeq"
    bgRemoveStartKeyed(dataUrl, key)
    var waited = 0L
    while (waited < timeoutMs) {
        onProgress(bgProgress())
        val s = pollAsync(key)
        if (s.isNotEmpty()) {
            return try { toolsJson.parseToJsonElement(s).jsonObject } catch (e: Exception) { null }
        }
        delay(300)
        waited += 300
    }
    return null
}

private var faceCallSeq = 0

/** Face detection on its own async slot; result carries the face box. */
suspend fun faceCall(dataUrl: String, timeoutMs: Long = 120_000): JsonObject? {
    faceCallSeq += 1
    val key = "face:$faceCallSeq"
    faceDetectStart(dataUrl, key)
    return awaitAsync(key, timeoutMs)
}

private var passportCallSeq = 0

/** Passport render ops (renderPhoto / renderSheet) on their own slot. */
suspend fun passportCall(op: String, argsJson: String, timeoutMs: Long = 120_000): JsonObject? {
    passportCallSeq += 1
    val key = "pp:$passportCallSeq"
    passportOp(op, argsJson, key)
    return awaitAsync(key, timeoutMs)
}

private var fetchCallSeq = 0

/** Fetches a same-origin resource (e.g. an outfit overlay PNG) as a data URL. */
suspend fun fetchResourceDataUrl(url: String, timeoutMs: Long = 30_000): String? {
    fetchCallSeq += 1
    val key = "fetch:$fetchCallSeq"
    fetchDataUrlStart(url, key)
    val res = awaitAsync(key, timeoutMs) ?: return null
    return if (res.asyncOk()) res.asyncStr("dataUrl").ifBlank { null } else null
}

fun JsonObject.asyncOk(): Boolean =
    this["ok"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() == true

fun JsonObject.asyncError(): String =
    this["error"]?.jsonPrimitive?.content ?: "Something went wrong."

fun JsonObject.asyncStr(key: String): String = this[key]?.jsonPrimitive?.content ?: ""

@OptIn(ExperimentalEncodingApi::class)
fun b64Encode(bytes: ByteArray): String = Base64.encode(bytes)

@OptIn(ExperimentalEncodingApi::class)
fun b64Decode(b64: String): ByteArray? = try { Base64.decode(b64) } catch (e: Exception) { null }

/** Reads a registered file's bytes (picker or dropped — same registry). */
suspend fun loadFileBytes(id: String): ByteArray? {
    readFileStart(id)
    val res = awaitAsync("file:$id", 60_000) ?: return null
    if (!res.asyncOk()) return null
    return b64Decode(res.asyncStr("b64"))
}

suspend fun loadFileBase64(id: String): String? {
    readFileStart(id)
    val res = awaitAsync("file:$id", 60_000) ?: return null
    if (!res.asyncOk()) return null
    return res.asyncStr("b64").ifBlank { null }
}

fun WebFile.dataUrlMime(): String = when {
    type.isNotBlank() -> type
    name.lowercase().endsWith(".pdf") -> "application/pdf"
    name.lowercase().endsWith(".png") -> "image/png"
    name.lowercase().endsWith(".jpg") || name.lowercase().endsWith(".jpeg") -> "image/jpeg"
    name.lowercase().endsWith(".webp") -> "image/webp"
    else -> "application/octet-stream"
}

suspend fun loadFileDataUrl(f: WebFile): String? {
    val b64 = loadFileBase64(f.id) ?: return null
    return "data:${f.dataUrlMime()};base64,$b64"
}

fun formatBytes(n: Long): String = when {
    n < 1024 -> "$n B"
    n < 1024 * 1024 -> "${(n * 10 / 1024) / 10.0} KB"
    else -> "${(n * 100 / (1024 * 1024)) / 100.0} MB"
}

/** Per-tool file rules — the single validation path for pick AND drop. */
data class FileSpec(
    val acceptAttr: String,                 // for the native picker
    val tokens: List<String>,               // ".pdf" (extension) or "image/" (mime prefix)
    val maxBytes: Long,
    val maxCount: Int,
    val kindLabel: String                   // "PDF", "image"
)

val PDF_SPEC = FileSpec(
    acceptAttr = "application/pdf,.pdf",
    tokens = listOf(".pdf", "application/pdf"),
    maxBytes = 25L * 1024 * 1024, maxCount = 10, kindLabel = "PDF"
)

val IMAGE_SPEC = FileSpec(
    acceptAttr = "image/*",
    tokens = listOf("image/", ".png", ".jpg", ".jpeg", ".webp"),
    maxBytes = 15L * 1024 * 1024, maxCount = 10, kindLabel = "image"
)

/** Compress PDF mockup spec: a single PDF up to 100 MB. */
val PDF_BIG_SPEC = FileSpec(
    acceptAttr = "application/pdf,.pdf",
    tokens = listOf(".pdf", "application/pdf"),
    maxBytes = 100L * 1024 * 1024, maxCount = 1, kindLabel = "PDF"
)

/** Compress Image mockup spec: up to 20 images, 20 MB each. */
val IMAGE_BATCH_SPEC = FileSpec(
    acceptAttr = "image/*",
    tokens = listOf("image/", ".png", ".jpg", ".jpeg", ".webp"),
    maxBytes = 20L * 1024 * 1024, maxCount = 20, kindLabel = "image"
)

fun FileSpec.accepts(f: WebFile): Boolean {
    val name = f.name.lowercase()
    val mime = f.type.lowercase()
    return tokens.any { t ->
        if (t.startsWith(".")) name.endsWith(t) else mime.startsWith(t)
    }
}

/**
 * Validates incoming files against the spec and the already-selected list.
 * Returns (accepted, errorMessage?) — rejected files produce ONE clear
 * message; accepted files keep order and respect the count limit.
 */
fun validateIncoming(
    existing: List<WebFile>, incoming: List<WebFile>, spec: FileSpec
): Pair<List<WebFile>, String?> {
    val accepted = ArrayList<WebFile>()
    var error: String? = null
    for (f in incoming) {
        when {
            !spec.accepts(f) -> if (error == null)
                error = "\"${f.name}\" is not a supported ${spec.kindLabel} file."
            f.size > spec.maxBytes -> if (error == null)
                error = "\"${f.name}\" is too large (max ${formatBytes(spec.maxBytes)})."
            existing.size + accepted.size >= spec.maxCount -> if (error == null)
                error = "You can add up to ${spec.maxCount} files here."
            else -> accepted.add(f)
        }
        if (!spec.accepts(f) || f.size > spec.maxBytes) releaseFile(f.id)
    }
    return accepted to error
}

/** Opens the picker and delivers validated files through the shared path. */
fun launchPick(
    scope: CoroutineScope,
    spec: FileSpec,
    multiple: Boolean,
    current: () -> List<WebFile>,
    onFiles: (List<WebFile>, String?) -> Unit
) {
    pickFilesMulti(spec.acceptAttr, multiple)
    scope.launch {
        // The picker is async: poll the picked queue until files arrive
        // (or give up after ~2 minutes, e.g. the user cancelled).
        var waited = 0L
        while (waited < 120_000) {
            val metas = parseFileMetas(pollPickedQueue())
            if (metas.isNotEmpty()) {
                val (ok, err) = validateIncoming(current(), metas, spec)
                onFiles(ok, err)
                return@launch
            }
            delay(300)
            waited += 300
        }
    }
}

/**
 * Collects drag-&-dropped files while a file screen is visible and routes
 * them through the SAME validation as click-to-upload.
 */
@Composable
fun FileDropCollector(
    spec: FileSpec,
    current: () -> List<WebFile>,
    onFiles: (List<WebFile>, String?) -> Unit
) {
    LaunchedEffect(spec) {
        while (true) {
            val metas = parseFileMetas(pollDroppedQueue())
            if (metas.isNotEmpty()) {
                val (ok, err) = validateIncoming(current(), metas, spec)
                onFiles(ok, err)
            }
            delay(350)
        }
    }
}

// ── Local tool history (localStorage; the Android app keeps it on-device) ──

@JsFun(
    """(entryJson) => {
        try {
            const key = 'qm_history';
            const arr = JSON.parse(localStorage.getItem(key) || '[]');
            arr.unshift(JSON.parse(entryJson));
            while (arr.length > 50) arr.pop();
            localStorage.setItem(key, JSON.stringify(arr));
        } catch (e) {}
    }"""
)
external fun pushHistoryJson(entryJson: String)

@JsFun("() => { try { return localStorage.getItem('qm_history') || '[]'; } catch (e) { return '[]'; } }")
external fun readHistoryJson(): String

@JsFun("() => { try { localStorage.removeItem('qm_history'); } catch (e) {} }")
external fun clearHistoryJson()

fun recordHistory(tool: String, title: String, detail: String) {
    fun esc(s: String): String = s.replace("\\", "\\\\").replace("\"", "\\\"")
    pushHistoryJson(
        "{\"tool\":\"${esc(tool)}\",\"title\":\"${esc(title)}\",\"detail\":\"${esc(detail)}\",\"ts\":${nowMillis()}}"
    )
}
