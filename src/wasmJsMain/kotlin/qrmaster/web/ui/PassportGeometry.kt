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
 * (mm→px at 300 DPI). Print sheets use a 2 mm margin / 2.5 mm gap so
 * the standard studio counts fit (35×45 mm on 6×4 in paper = 8 copies)
 * with comfortable room to cut between copies.
 * Face framing is
 * HEAD-based: the detector's box starts at the forehead, so it is first
 * expanded into a head box (crown above, chin below — see [ppHeadBox])
 * and the crop is sized so the crown→chin height is ≈ 64% of the crop
 * height with the crown ≈ 11% below the crop's top edge.
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
 * Crown allowance: how far the top of the HEAD (crown, incl. hair) sits
 * above the detector box's top, as a fraction of the box height. The
 * face-api box starts at the forehead/hairline, NOT the crown. Measured
 * against the background-removal alpha silhouette on real portraits
 * (2026-11-24): 0.26 (bald selfie), 0.32–0.42 (short / voluminous hair),
 * 0.47 (hat-like hair mass). 0.36 plus the headroom margin in
 * [ppHeadCrop] (together ≈ 0.60 × box height above the box top) covers
 * the measured range.
 */
const val PP_CROWN_FRAC = 0.36f

/** Chin margin: the head box extends this fraction of the box height
 *  below the detector box's bottom (the box bottom is ~at the chin). */
const val PP_CHIN_FRAC = 0.04f

/** Target crown→chin height as a fraction of the crop height. Passport
 *  photos want the head at 55–70% of the frame height; 0.64 centres it. */
const val PP_HEAD_TARGET = 0.64f

/** Where the crown sits in the crop, as a fraction of the crop height
 *  (0.11 = 11% of the frame height of clear space above the head). */
const val PP_HEAD_TOP = 0.11f

/**
 * Expands a detector face box into a HEAD box: same x/width (the box is
 * ~ear-to-ear), top moved up by [PP_CROWN_FRAC] × box height (forehead
 * → crown), bottom moved down by [PP_CHIN_FRAC] × box height. Returned
 * as a [PPRectF] in source pixels; y may be negative (head partly out
 * of the photo's top) — callers clamp when they need image bounds.
 */
fun ppHeadBox(faceX: Float, faceY: Float, faceW: Float, faceH: Float): PPRectF {
    val top = faceY - PP_CROWN_FRAC * faceH
    val bottom = faceY + faceH + PP_CHIN_FRAC * faceH
    return PPRectF(faceX, top, faceW, bottom - top)
}

/**
 * Head-based framing: the crop is sized so the HEAD (crown→chin, see
 * [ppHeadBox]) fills [target] of the crop height, and positioned so the
 * crown sits [topFrac] of the crop height below the crop's top edge,
 * centred horizontally on the face. The window always stays inside the
 * photo and always keeps the target aspect; when the photo is too small
 * for the ideal window, the largest aspect window is used.
 * Face box is in source pixels (x, y = top-left).
 */
fun ppFaceCrop(
    imgW: Int, imgH: Int, aspect: Double,
    faceX: Float, faceY: Float, faceW: Float, faceH: Float
): PPCrop = ppHeadCrop(imgW, imgH, aspect, faceX, faceY, faceW, faceH, PP_HEAD_TARGET, PP_HEAD_TOP)

/** [ppFaceCrop] with explicit proportion targets (fallback tiers). */
private fun ppHeadCrop(
    imgW: Int, imgH: Int, aspect: Double,
    faceX: Float, faceY: Float, faceW: Float, faceH: Float,
    target: Float, topFrac: Float
): PPCrop {
    if (imgW <= 0 || imgH <= 0 || aspect <= 0 || faceW <= 0f || faceH <= 0f) {
        return ppCentreCrop(imgW, imgH, aspect)
    }
    val head = ppHeadBox(faceX, faceY, faceW, faceH)
    var cropH = head.h / target
    var cropW = (cropH * aspect).toFloat()
    // The window must be wide enough for the face itself.
    val minW = faceW * 1.04f
    if (cropW < minW) { cropW = minW; cropH = (cropW / aspect).toFloat() }
    // Clamp to the largest window of this aspect that fits the photo.
    val maxH = min(imgH.toFloat(), (imgW / aspect).toFloat())
    if (cropH > maxH) { cropH = maxH; cropW = (cropH * aspect).toFloat() }
    val headCx = faceX + faceW / 2f
    val left = (headCx - cropW / 2f).coerceIn(0f, max(0f, imgW - cropW))
    val top = (head.y - topFrac * cropH).coerceIn(0f, max(0f, imgH - cropH))
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

/**
 * Defensive framing invariant: the fraction of the face box's area that
 * lies inside [crop] (0..1), or -1 when the face box is degenerate.
 * A correct face framing keeps the WHOLE face inside the crop; anything
 * below ~0.6 means the mapping between the detection space and the image
 * space went wrong somewhere upstream.
 */
fun ppFaceCoverage(crop: PPCrop, faceX: Float, faceY: Float, faceW: Float, faceH: Float): Float {
    if (faceW <= 0f || faceH <= 0f || crop.w <= 0f || crop.h <= 0f) return -1f
    val ix = max(0f, min(faceX + faceW, crop.x + crop.w) - max(faceX, crop.x))
    val iy = max(0f, min(faceY + faceH, crop.y + crop.h) - max(faceY, crop.y))
    return (ix * iy) / (faceW * faceH)
}

/** Result of the guarded framing: the crop to use + whether the guard
 *  had to fall back because the face did not land inside the frame. */
data class PPFraming(val crop: PPCrop, val fellBack: Boolean)

/**
 * Verifies a candidate crop against the HEAD framing contract:
 *  - the crop lies inside the photo;
 *  - the whole in-image face AND head (crown→chin) lie inside the crop
 *    (no part of the head touches a frame edge);
 *  - the crown has real headroom — ≥ ~4% of the frame height — unless
 *    the source photo itself makes that impossible (the window is
 *    already at the photo's top, or the crown itself is within 4% of
 *    the photo's top);
 *  - the head height is 50–75% of the frame height (the 55–70% output
 *    target, widened for detector-box variance), unless the window is
 *    already the largest the photo allows.
 */
private fun ppHeadVerify(
    crop: PPCrop, imgW: Int, imgH: Int, aspect: Double,
    faceX: Float, faceY: Float, faceW: Float, faceH: Float
): Boolean {
    if (crop.w <= 0f || crop.h <= 0f) return false
    if (crop.x < -0.5f || crop.y < -0.5f ||
        crop.x + crop.w > imgW + 0.5f || crop.y + crop.h > imgH + 0.5f) return false
    // In-image part of the face box.
    val fx0 = faceX.coerceIn(0f, imgW.toFloat())
    val fy0 = faceY.coerceIn(0f, imgH.toFloat())
    val fx1 = (faceX + faceW).coerceIn(0f, imgW.toFloat())
    val fy1 = (faceY + faceH).coerceIn(0f, imgH.toFloat())
    if (fx1 <= fx0 || fy1 <= fy0) return false
    if (fx0 < crop.x - 1f || fx1 > crop.x + crop.w + 1f) return false
    if (fy0 < crop.y - 1f || fy1 > crop.y + crop.h + 1f) return false
    // In-image part of the head box (crown→chin).
    val head = ppHeadBox(faceX, faceY, faceW, faceH)
    val hTop = head.y.coerceIn(0f, imgH.toFloat())
    val hBot = (head.y + head.h).coerceIn(0f, imgH.toFloat())
    if (hTop < crop.y - 1f || hBot > crop.y + crop.h + 1f) return false
    // Crown headroom.
    val headroom = (hTop - crop.y) / crop.h
    val sourceLimitedTop = crop.y <= 0.5f || hTop <= 0.04f * crop.h
    if (headroom < 0.035f && !sourceLimitedTop) return false
    // Head proportion.
    val maxWindowH = min(imgH.toFloat(), (imgW / aspect).toFloat())
    val largest = crop.h >= maxWindowH - 1f
    val headFrac = (hBot - hTop) / crop.h
    if (!largest && (headFrac < 0.50f || headFrac > 0.75f)) return false
    // The face itself must be (essentially) fully covered.
    val cov = ppFaceCoverage(crop, fx0, fy0, fx1 - fx0, fy1 - fy0)
    return cov >= 0.99f
}

/**
 * The ONLY framing entry point the tool may use. Computes the head-based
 * face crop, then VERIFIES it against the head contract ([ppHeadVerify]:
 * whole head inside, crown headroom, head proportion). If verification
 * fails — e.g. the face box arrived in an unexpected coordinate space —
 * it falls back through relaxed head framing, a window built directly
 * around the head box, and finally the centred crop, so a faceless or
 * crown-cut output is impossible rather than merely unlikely.
 */
fun ppFaceCropSafe(
    imgW: Int, imgH: Int, aspect: Double,
    faceX: Float, faceY: Float, faceW: Float, faceH: Float
): PPFraming {
    if (imgW <= 0 || imgH <= 0 || aspect <= 0 || faceW <= 0f || faceH <= 0f) {
        return PPFraming(ppCentreCrop(imgW, imgH, aspect), fellBack = true)
    }
    // A box whose centre is outside the photo, or that spills far past
    // the photo's edges, is in the wrong coordinate space — framing from
    // it directly would be garbage; go straight to the safe tiers.
    val centreOutside = faceX + faceW / 2f !in 0f..imgW.toFloat() ||
        faceY + faceH / 2f !in 0f..imgH.toFloat()
    val overX = max(0f, -faceX) + max(0f, faceX + faceW - imgW)
    val overY = max(0f, -faceY) + max(0f, faceY + faceH - imgH)
    val wrongSpace = centreOutside || overX > 0.35f * faceW || overY > 0.35f * faceH ||
        faceW > imgW * 1.05f || faceH > imgH * 1.05f
    if (!wrongSpace) {
        val crop = ppFaceCrop(imgW, imgH, aspect, faceX, faceY, faceW, faceH)
        if (ppHeadVerify(crop, imgW, imgH, aspect, faceX, faceY, faceW, faceH)) {
            return PPFraming(crop, fellBack = false)
        }
        val relaxed = ppHeadCrop(imgW, imgH, aspect, faceX, faceY, faceW, faceH, 0.58f, 0.07f)
        if (ppHeadVerify(relaxed, imgW, imgH, aspect, faceX, faceY, faceW, faceH)) {
            return PPFraming(relaxed, fellBack = true)
        }
    }
    // Window built directly around the (clamped) head box — the head is
    // inside by construction wherever the photo allows it.
    val raw = ppFaceCropRaw(imgW, imgH, aspect, faceX, faceY, faceW, faceH)
    val fx0 = faceX.coerceIn(0f, imgW.toFloat())
    val fy0 = faceY.coerceIn(0f, imgH.toFloat())
    val fw0 = (faceX + faceW).coerceIn(0f, imgW.toFloat()) - fx0
    val fh0 = (faceY + faceH).coerceIn(0f, imgH.toFloat()) - fy0
    if (fw0 > 0f && fh0 > 0f && ppFaceCoverage(raw, fx0, fy0, fw0, fh0) >= 0.6f) {
        return PPFraming(raw, fellBack = true)
    }
    return PPFraming(ppCentreCrop(imgW, imgH, aspect), fellBack = true)
}

/** Last-resort framing: build the window AROUND the (clamped-to-image)
 *  head box itself, ignoring the usual proportions if needed. The head
 *  is inside by construction. */
private fun ppFaceCropRaw(
    imgW: Int, imgH: Int, aspect: Double,
    faceX: Float, faceY: Float, faceW: Float, faceH: Float
): PPCrop {
    if (imgW <= 0 || imgH <= 0 || aspect <= 0) return ppCentreCrop(imgW, imgH, aspect)
    val fx = faceX.coerceIn(0f, imgW.toFloat())
    val fy = faceY.coerceIn(0f, imgH.toFloat())
    val fw = min(faceW, imgW - fx).coerceAtLeast(1f)
    val fh = min(faceH, imgH - fy).coerceAtLeast(1f)
    val head = ppHeadBox(fx, fy, fw, fh)
    var cropH = head.h / PP_HEAD_TARGET
    var cropW = (cropH * aspect).toFloat()
    val maxH = min(imgH.toFloat(), (imgW / aspect).toFloat())
    if (cropH > maxH) { cropH = maxH; cropW = (cropH * aspect).toFloat() }
    val cx = fx + fw / 2f
    val x = (cx - cropW / 2f).coerceIn(0f, max(0f, imgW - cropW))
    val y = (head.y - PP_HEAD_TOP * cropH).coerceIn(0f, max(0f, imgH - cropH))
    return PPCrop(x, y, cropW, cropH)
}

data class PPRectF(val x: Float, val y: Float, val w: Float, val h: Float)

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

/** Default print-sheet edge margin (mm). 2 mm — studios print 4×6
 *  borderless, and the slimmer edge is what leaves room for a real
 *  cutting gap between copies (see [PP_SHEET_GAP_MM]) while keeping
 *  the standard studio grids: 35×45 mm on 6×4 in (4R) paper needs
 *  2×2 mm margins + 4×35 mm + 3 gaps of 2.5 mm = 151.5 of 152.4 mm. */
const val PP_SHEET_MARGIN_MM = 2.0

/** Default gap between copies on a print sheet (mm). 2.5 mm of white
 *  between photos so scissors / a trimmer have room to cut — the old
 *  1 mm gap was too tight to cut comfortably (owner's report). */
const val PP_SHEET_GAP_MM = 2.5

/**
 * Grid of copies on a print paper at 300 DPI (row-major positions).
 * Defaults are [PP_SHEET_MARGIN_MM] / [PP_SHEET_GAP_MM], which give
 * the counts photo studios expect — 35×45 mm on 6×4 in (4R) paper is
 * 8 copies (4 × 2). Returns count 0 when even one copy does not fit.
 * This is the ONE layout used by both the on-screen sheet preview and
 * the exported sheet file, so the two can never diverge.
 */
fun ppSheetLayout(
    paperWMm: Double, paperHMm: Double,
    photoWMm: Double, photoHMm: Double,
    marginMm: Double = PP_SHEET_MARGIN_MM, gapMm: Double = PP_SHEET_GAP_MM,
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
