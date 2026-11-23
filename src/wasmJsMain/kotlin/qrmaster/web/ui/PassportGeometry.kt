package qrmaster.web.ui

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Pure passport-photo geometry (no Compose / browser dependencies) so the
 * math can be unit-tested on the JVM — see qrtest/PassportGeometryTest.kt.
 *
 * The numbers mirror the Android app's PhotoLogic where the two overlap
 * (mm→px at 300 DPI, face framing: face height ≈ 55% of the crop height,
 * face centre at 38% of the crop height, sheet margin 10 mm / gap 5 mm).
 * Sizes are common passport/visa photo dimensions only — no claim of
 * official compliance for any country.
 */

const val PASSPORT_DPI = 300
const val PP_MM_PER_INCH = 25.4

/** mm → px at [dpi]. 35 mm @300 dpi = 413 px, 45 mm = 531 px. */
fun ppMmToPx(mm: Double, dpi: Int = PASSPORT_DPI): Int =
    (mm / PP_MM_PER_INCH * dpi).roundToInt()

/** inch → px at [dpi]. 2 in @300 dpi = 600 px. */
fun ppInchToPx(inch: Double, dpi: Int = PASSPORT_DPI): Int =
    (inch * dpi).roundToInt()

/** mm → PDF points (1 pt = 1/72 in). */
fun ppMmToPt(mm: Double): Double = mm / PP_MM_PER_INCH * 72.0

data class PassportPreset(val id: String, val label: String, val wMm: Double, val hMm: Double) {
    val aspect: Double get() = wMm / hMm
    fun pxW(dpi: Int = PASSPORT_DPI): Int = ppMmToPx(wMm, dpi)
    fun pxH(dpi: Int = PASSPORT_DPI): Int = ppMmToPx(hMm, dpi)
}

/** "Common" presets shown first (mirrors the reference tool's list). */
val PASSPORT_COMMON: List<PassportPreset> = listOf(
    PassportPreset("cn1", "China 1 inch — 25 × 35 mm", 25.0, 35.0),
    PassportPreset("cn2", "China 2 inch — 35 × 49 mm", 35.0, 49.0),
    PassportPreset("us", "USA — 2 × 2 inch", 50.8, 50.8)
)

/** Country presets (mm). India is the tool's default selection. */
val PASSPORT_COUNTRIES: List<PassportPreset> = listOf(
    PassportPreset("in", "India — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("us50", "United States — 50 × 50 mm", 50.0, 50.0),
    PassportPreset("uk", "United Kingdom — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("cn", "China — 33 × 48 mm", 33.0, 48.0),
    PassportPreset("ca", "Canada — 50 × 70 mm", 50.0, 70.0),
    PassportPreset("br", "Brazil — 50 × 70 mm", 50.0, 70.0),
    PassportPreset("au", "Australia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("fr", "France — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("de", "Germany — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("jp", "Japan — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("kr", "Korea — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ru", "Russia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("es", "Spain — 32 × 26 mm", 32.0, 26.0),
    PassportPreset("my", "Malaysia — 35 × 50 mm", 35.0, 50.0),
    PassportPreset("idn", "Indonesia — 40 × 60 mm", 40.0, 60.0),
    PassportPreset("sa", "Saudi Arabia — 40 × 60 mm", 40.0, 60.0),
    PassportPreset("vn", "Vietnam — 40 × 60 mm", 40.0, 60.0),
    PassportPreset("om", "Oman — 40 × 60 mm", 40.0, 60.0),
    PassportPreset("la", "Laos — 40 × 60 mm", 40.0, 60.0),
    PassportPreset("bd", "Bangladesh — 50 × 50 mm", 50.0, 50.0),
    PassportPreset("hn", "Honduras — 50 × 50 mm", 50.0, 50.0),
    PassportPreset("by", "Belarus — 40 × 50 mm", 40.0, 50.0),
    PassportPreset("hk", "Hong Kong — 40 × 50 mm", 40.0, 50.0),
    PassportPreset("kw", "Kuwait — 40 × 50 mm", 40.0, 50.0),
    PassportPreset("ar", "Argentina — 40 × 40 mm", 40.0, 40.0),
    PassportPreset("bo", "Bolivia — 40 × 40 mm", 40.0, 40.0),
    PassportPreset("cl", "Chile — 40 × 40 mm", 40.0, 40.0),
    PassportPreset("ly", "Libya — 40 × 40 mm", 40.0, 40.0),
    PassportPreset("py", "Paraguay — 40 × 40 mm", 40.0, 40.0),
    PassportPreset("uy", "Uruguay — 40 × 40 mm", 40.0, 40.0),
    PassportPreset("co", "Colombia — 30 × 40 mm", 30.0, 40.0),
    PassportPreset("ir", "Iran — 30 × 40 mm", 30.0, 40.0),
    PassportPreset("tm", "Turkmenistan — 30 × 40 mm", 30.0, 40.0),
    PassportPreset("fi", "Finland — 36 × 47 mm", 36.0, 47.0),
    PassportPreset("mx", "Mexico — 25 × 35 mm", 25.0, 35.0),
    PassportPreset("mo", "Macau — 33 × 48 mm", 33.0, 48.0),
    PassportPreset("dz", "Algeria — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("am", "Armenia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("at", "Austria — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("az", "Azerbaijan — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("bh", "Bahrain — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("be", "Belgium — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ba", "Bosnia and Herzegovina — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("bg", "Bulgaria — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("kh", "Cambodia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("cm", "Cameroon — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("cg", "Congo — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("cr", "Costa Rica — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("hr", "Croatia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("cu", "Cuba — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("cy", "Cyprus — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("cz", "Czech Republic — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("dk", "Denmark — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("do", "Dominican Republic — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ec", "Ecuador — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("eg", "Egypt — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("sv", "El Salvador — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ee", "Estonia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("et", "Ethiopia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ge", "Georgia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("gr", "Greece — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("gt", "Guatemala — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("hu", "Hungary — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("is", "Iceland — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ie", "Ireland — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("il", "Israel — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("it", "Italy — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("jo", "Jordan — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("kz", "Kazakhstan — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ke", "Kenya — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("kg", "Kyrgyzstan — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("lv", "Latvia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("lb", "Lebanon — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("lt", "Lithuania — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("lu", "Luxembourg — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("mk", "Macedonia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("mt", "Malta — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("mu", "Mauritius — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("md", "Moldova — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("mc", "Monaco — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("mn", "Mongolia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("me", "Montenegro — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ma", "Morocco — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("mm", "Myanmar — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("np", "Nepal — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("nl", "Netherlands — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("nz", "New Zealand — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ni", "Nicaragua — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ng", "Nigeria — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("no", "Norway — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("pk", "Pakistan — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ps", "Palestine — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("pa", "Panama — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("pe", "Peru — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ph", "Philippines — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("pl", "Poland — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("pt", "Portugal — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("qa", "Qatar — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ro", "Romania — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("rs", "Serbia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("sg", "Singapore — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("sk", "Slovakia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("si", "Slovenia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("za", "South Africa — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("lk", "Sri Lanka — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("se", "Sweden — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ch", "Switzerland — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("tw", "Taiwan — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("tj", "Tajikistan — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("th", "Thailand — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("tn", "Tunisia — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("tr", "Turkey — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ug", "Uganda — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ua", "Ukraine — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ae", "United Arab Emirates — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("uz", "Uzbekistan — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("ve", "Venezuela — 35 × 45 mm", 35.0, 45.0),
    PassportPreset("zw", "Zimbabwe — 35 × 45 mm", 35.0, 45.0)
)

/** Every preset (Common first, then countries). */
val PASSPORT_PRESETS: List<PassportPreset> = PASSPORT_COMMON + PASSPORT_COUNTRIES

fun ppPreset(id: String): PassportPreset {
    val wanted = if (id == "ukeu") "uk" else id
    return PASSPORT_PRESETS.firstOrNull { it.id == wanted } ?: PASSPORT_COUNTRIES[0]
}

/** Case-insensitive name search over the country list (Size tab search box). */
fun ppSearchCountries(query: String): List<PassportPreset> {
    val q = query.trim()
    if (q.isEmpty()) return PASSPORT_COUNTRIES
    return PASSPORT_COUNTRIES.filter { it.label.contains(q, ignoreCase = true) }
}

/** px → mm at [dpi] (inverse of [ppMmToPx], for the custom-size fields). */
fun ppPxToMm(px: Int, dpi: Int = PASSPORT_DPI): Double = px.toDouble() / dpi * PP_MM_PER_INCH

/** Print papers for the copy sheet (portrait orientation). */
data class PassportPaper(val id: String, val label: String, val wMm: Double, val hMm: Double)

val PASSPORT_PAPERS: List<PassportPaper> = listOf(
    PassportPaper("4x6", "4 × 6 inch", 101.6, 152.4),
    PassportPaper("a4", "A4", 210.0, 297.0)
)

/**
 * Paper Size options of the Maker's Size tab, in the reference tool's
 * order. "single" is the individual photo (no sheet); the rest tile
 * copies with [ppSheetLayout]. Dimensions are the physical papers.
 */
val PP_PAPER_OPTIONS: List<PassportPaper> = listOf(
    PassportPaper("single", "Individual photo", 0.0, 0.0),
    PassportPaper("3r", "3R — 3.5 × 5 inch", 88.9, 127.0),
    PassportPaper("4r", "4R — 6 × 4 inch", 152.4, 101.6),
    PassportPaper("5r", "5R — 7 × 5 inch", 177.8, 127.0),
    PassportPaper("a5", "A5 — 21 × 14.85 cm", 148.5, 210.0),
    PassportPaper("a4", "A4 — 29.7 × 21 cm", 210.0, 297.0),
    PassportPaper("letter", "Letter — 8.5 × 11 inch", 215.9, 279.4),
    PassportPaper("legal", "Legal — 8.5 × 14 inch", 215.9, 355.6)
)

fun ppPaper(id: String): PassportPaper =
    PP_PAPER_OPTIONS.firstOrNull { it.id == id } ?: PP_PAPER_OPTIONS[0]

/** A crop window inside the source photo, in source pixels (floats). */
data class PPCrop(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Face-based framing, mirroring the Android app: the detected face height
 * is ~55% of the crop height and the face centre sits at 38% of the crop
 * height (eyes in the upper third, headroom above). The window always
 * stays inside the photo and always keeps the target aspect.
 * Face box is in source pixels (x, y = top-left).
 */
fun ppFaceCrop(
    imgW: Int, imgH: Int, aspect: Double,
    faceX: Float, faceY: Float, faceW: Float, faceH: Float
): PPCrop {
    if (imgW <= 0 || imgH <= 0 || aspect <= 0 || faceW <= 0f || faceH <= 0f) {
        return ppCentreCrop(imgW, imgH, aspect)
    }
    val fh = max(1f, faceH)
    val faceCx = faceX + faceW / 2f
    val faceCy = faceY + faceH / 2f

    var cropH = fh / 0.55f
    var cropW = (cropH * aspect).toFloat()
    if (cropW > imgW) { cropW = imgW.toFloat(); cropH = (cropW / aspect).toFloat() }
    if (cropH > imgH) { cropH = imgH.toFloat(); cropW = (cropH * aspect).toFloat() }
    // Never frame tighter than the face itself.
    val minH = min(imgH.toFloat(), fh * 1.15f)
    if (cropH < minH) {
        cropH = minH
        cropW = (cropH * aspect).toFloat()
        if (cropW > imgW) { cropW = imgW.toFloat(); cropH = (cropW / aspect).toFloat() }
    }
    val left = (faceCx - cropW / 2f).coerceIn(0f, max(0f, imgW - cropW))
    val top = (faceCy - cropH * 0.38f).coerceIn(0f, max(0f, imgH - cropH))
    return PPCrop(left, top, cropW, cropH)
}

/**
 * Largest centred crop of [aspect] in the photo; vertical position biased
 * to 42% of the slack (faces usually sit above centre). Fallback framing
 * when no face is detected, and the base for manual adjustment.
 */
fun ppCentreCrop(imgW: Int, imgH: Int, aspect: Double): PPCrop {
    if (imgW <= 0 || imgH <= 0 || aspect <= 0) return PPCrop(0f, 0f, 0f, 0f)
    val srcAspect = imgW.toDouble() / imgH
    val cw: Float
    val ch: Float
    if (srcAspect > aspect) {
        ch = imgH.toFloat(); cw = (imgH * aspect).toFloat()
    } else {
        cw = imgW.toFloat(); ch = (imgW / aspect).toFloat()
    }
    return PPCrop((imgW - cw) * 0.5f, (imgH - ch) * 0.42f, cw, ch)
}

/**
 * Applies the manual adjust controls to a base crop: [zoom] 1..2.5 shrinks
 * the window around the base centre; [shiftX]/[shiftY] (-0.5..0.5, in
 * units of the window size) move it. Result always keeps the aspect and
 * stays inside the photo.
 */
fun ppZoomShift(
    base: PPCrop, imgW: Int, imgH: Int,
    zoom: Float, shiftX: Float, shiftY: Float
): PPCrop {
    if (base.w <= 0f || base.h <= 0f || imgW <= 0 || imgH <= 0) return base
    val z = zoom.coerceIn(1f, 2.5f)
    var cw = base.w / z
    var ch = base.h / z
    if (cw > imgW) { cw = imgW.toFloat(); ch = cw * base.h / base.w }
    if (ch > imgH) { ch = imgH.toFloat(); cw = ch * base.w / base.h }
    val bcx = base.x + base.w / 2f
    val bcy = base.y + base.h / 2f
    val cx = bcx + shiftX.coerceIn(-0.5f, 0.5f) * cw
    val cy = bcy + shiftY.coerceIn(-0.5f, 0.5f) * ch
    val x = (cx - cw / 2f).coerceIn(0f, max(0f, imgW - cw))
    val y = (cy - ch / 2f).coerceIn(0f, max(0f, imgH - ch))
    return PPCrop(x, y, cw, ch)
}

/** How to draw the full source image inside the preview frame so that
 *  exactly [crop] fills the frame. All values in frame (display) units. */
data class PPPreviewTransform(
    val scale: Float, val imgW: Float, val imgH: Float, val left: Float, val top: Float
)

fun ppPreviewTransform(frameW: Float, frameH: Float, crop: PPCrop, imgW: Int, imgH: Int): PPPreviewTransform {
    if (crop.w <= 0f || frameW <= 0f) return PPPreviewTransform(1f, frameW, frameH, 0f, 0f)
    val scale = frameW / crop.w
    return PPPreviewTransform(
        scale = scale,
        imgW = imgW * scale,
        imgH = imgH * scale,
        left = -crop.x * scale,
        top = -crop.y * scale
    )
}

// ── Print sheet ──────────────────────────────────────────────────────────

data class PPSheetCell(val x: Int, val y: Int, val w: Int, val h: Int)

data class PPSheet(
    val cols: Int, val rows: Int, val count: Int,
    val cells: List<PPSheetCell>,
    val paperPxW: Int, val paperPxH: Int, val photoPxW: Int, val photoPxH: Int
)

/**
 * Grid of copies on a print paper at 300 DPI — same layout math as the
 * Android app (margin 10 mm, gap 5 mm by default, row-major positions).
 * Returns count 0 when even one copy does not fit.
 */
fun ppSheetLayout(
    paperWMm: Double, paperHMm: Double,
    photoWMm: Double, photoHMm: Double,
    marginMm: Double = 10.0, gapMm: Double = 5.0,
    dpi: Int = PASSPORT_DPI
): PPSheet {
    val paperW = ppMmToPx(paperWMm, dpi)
    val paperH = ppMmToPx(paperHMm, dpi)
    val copyW = ppMmToPx(photoWMm, dpi)
    val copyH = ppMmToPx(photoHMm, dpi)
    val margin = ppMmToPx(marginMm, dpi)
    val gap = ppMmToPx(gapMm, dpi)
    fun empty() = PPSheet(0, 0, 0, emptyList(), paperW, paperH, copyW, copyH)
    if (copyW <= 0 || copyH <= 0 || paperW <= 0 || paperH <= 0) return empty()
    val usableW = paperW - 2 * margin
    val usableH = paperH - 2 * margin
    if (usableW < copyW || usableH < copyH) return empty()
    val cols = floor((usableW + gap).toDouble() / (copyW + gap)).toInt()
    val rows = floor((usableH + gap).toDouble() / (copyH + gap)).toInt()
    val cells = ArrayList<PPSheetCell>(cols * rows)
    for (r in 0 until rows) {
        for (c in 0 until cols) {
            cells.add(
                PPSheetCell(
                    x = margin + c * (copyW + gap),
                    y = margin + r * (copyH + gap),
                    w = copyW, h = copyH
                )
            )
        }
    }
    return PPSheet(cols, rows, cells.size, cells, paperW, paperH, copyW, copyH)
}

// ── Backgrounds (Background tab) ─────────────────────────────────────────

/**
 * One background choice: [colors] holds one hex colour for a solid fill
 * or two hex colours for a top→bottom gradient. Hex strings keep this
 * file free of Compose types so it stays JVM-testable.
 */
data class PPBackground(val id: String, val name: String, val colors: List<String>) {
    val isGradient: Boolean get() = colors.size > 1
}

val PP_BG_CLASSICAL: List<PPBackground> = listOf(
    PPBackground("red", "Red", listOf("#FF0000")),
    PPBackground("blue", "Blue", listOf("#438edb")),
    PPBackground("white", "White", listOf("#FFFFFF")),
    PPBackground("darkblue", "Dark blue", listOf("#2a385b")),
    PPBackground("lightblue", "Light blue", listOf("#00BFF3"))
)

val PP_BG_STYLISH: List<PPBackground> = listOf(
    PPBackground("skygradient", "Blue gradient", listOf("#66B5F2", "#CBE8FB")),
    PPBackground("peach", "Peach", listOf("#F7BEB3")),
    PPBackground("paleteal", "Pale teal", listOf("#B8DDE6")),
    PPBackground("greygradient", "Grey gradient", listOf("#999999", "#FFFFFF")),
    PPBackground("tan", "Tan", listOf("#E0B97E")),
    PPBackground("palemint", "Pale mint", listOf("#CCE2D6")),
    PPBackground("green", "Green", listOf("#40896E")),
    PPBackground("crimson", "Crimson", listOf("#C42134")),
    PPBackground("greybluegradient", "Grey-blue gradient", listOf("#777E90", "#ABB2C5")),
    PPBackground("royalblue", "Royal blue", listOf("#3F64BF")),
    PPBackground("terracotta", "Terracotta", listOf("#D16541")),
    PPBackground("palepink", "Pale pink", listOf("#FDDAE1")),
    PPBackground("black", "Black", listOf("#000000")),
    PPBackground("mint", "Mint", listOf("#CDE9DF")),
    PPBackground("beige", "Beige", listOf("#DFCCB0"))
)

fun ppBackground(id: String): PPBackground? =
    (PP_BG_CLASSICAL + PP_BG_STYLISH).firstOrNull { it.id == id }

// ── Outfit overlays (Change outfit tab) ──────────────────────────────────

/**
 * A formal-wear overlay PNG (transparent, drawn artwork shipped in
 * resources/outfits/). The honest client-side equivalent of the
 * reference tool's AI outfit swap: a garment graphic laid over the
 * photo's torso, aligned by [ppOutfitRect] and adjustable by the user.
 */
data class PPOutfit(val id: String, val name: String, val category: String, val path: String)

val PP_OUTFIT_CATEGORIES: List<String> = listOf("Men's", "Women's", "Children's")

val PP_OUTFITS: List<PPOutfit> = listOf(
    PPOutfit("men-black", "Black suit & tie", "Men's", "outfits/men-black.png"),
    PPOutfit("men-navy", "Navy suit & tie", "Men's", "outfits/men-navy.png"),
    PPOutfit("men-grey", "Grey suit & tie", "Men's", "outfits/men-grey.png"),
    PPOutfit("women-black", "Black blazer", "Women's", "outfits/women-black.png"),
    PPOutfit("women-navy", "Navy blazer", "Women's", "outfits/women-navy.png"),
    PPOutfit("women-maroon", "Maroon blazer", "Women's", "outfits/women-maroon.png"),
    PPOutfit("child-black", "Black suit & tie", "Children's", "outfits/child-black.png"),
    PPOutfit("child-navy", "Navy blazer", "Children's", "outfits/child-navy.png")
)

/** Overlay artwork aspect (width / height) — the PNGs are 1000 × 750. */
const val PP_OUTFIT_ASPECT = 4.0 / 3.0

/** Where the collar sits inside the artwork, as a fraction of its height. */
const val PP_OUTFIT_COLLAR_FRAC = 0.30f

/** Overlay width at scale 1, as a fraction of the photo frame width. */
const val PP_OUTFIT_WIDTH_FRAC = 1.09f

/** Collar height at scale 1, as a fraction of the photo frame height
 *  (just below the chin of the standard face framing). */
const val PP_OUTFIT_COLLAR_Y_FRAC = 0.68f

/**
 * Placement of the outfit overlay inside the rendered photo (output px).
 * The collar point of the artwork lands at [PP_OUTFIT_COLLAR_Y_FRAC] of
 * the frame height (moved by [yShift], a fraction of the frame height);
 * [scale] grows/shrinks the garment around that collar point. The
 * garment always covers the bottom of the frame.
 */
fun ppOutfitRect(frameW: Int, frameH: Int, scale: Float, yShift: Float): PPSheetCell {
    if (frameW <= 0 || frameH <= 0) return PPSheetCell(0, 0, 0, 0)
    val s = scale.coerceIn(0.7f, 1.4f)
    val w = frameW * PP_OUTFIT_WIDTH_FRAC * s
    val h = (w / PP_OUTFIT_ASPECT).toFloat()
    val collarY = frameH * PP_OUTFIT_COLLAR_Y_FRAC + yShift.coerceIn(-0.1f, 0.1f) * frameH
    val x = (frameW - w) / 2f
    val y = collarY - PP_OUTFIT_COLLAR_FRAC * h
    return PPSheetCell(x.roundToInt(), y.roundToInt(), w.roundToInt(), h.roundToInt())
}

// ── Manual crop (Crop / Choose area modal) ───────────────────────────────

/** Moves the crop window by (dx, dy) source px, clamped inside the photo. */
fun ppCropMove(crop: PPCrop, dx: Float, dy: Float, imgW: Int, imgH: Int): PPCrop {
    if (crop.w <= 0f || crop.h <= 0f) return crop
    val x = (crop.x + dx).coerceIn(0f, max(0f, imgW - crop.w))
    val y = (crop.y + dy).coerceIn(0f, max(0f, imgH - crop.h))
    return PPCrop(x, y, crop.w, crop.h)
}

/**
 * Aspect-locked crop resize (same handle numbering and semantics as the
 * PDF Editor's [edResizeLocked]): the window only grows/shrinks, never
 * changes shape, and stays inside the photo. Deltas are meant to be the
 * TOTAL drag distance from the crop captured at drag start.
 */
fun ppCropResize(crop: PPCrop, handle: Int, dx: Float, dy: Float, imgW: Int, imgH: Int): PPCrop {
    if (crop.w <= 0f || crop.h <= 0f || imgW <= 0 || imgH <= 0) return crop
    val r = edResizeLocked(EdBox(crop.x, crop.y, crop.w, crop.h), handle, dx, dy)
    var w = r.w
    var h = r.h
    if (w > imgW) { w = imgW.toFloat(); h = w * crop.h / crop.w }
    if (h > imgH) { h = imgH.toFloat(); w = h * crop.w / crop.h }
    val x = r.x.coerceIn(0f, max(0f, imgW - w))
    val y = r.y.coerceIn(0f, max(0f, imgH - h))
    return PPCrop(x, y, w, h)
}
