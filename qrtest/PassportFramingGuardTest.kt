import qrmaster.web.ui.PPCrop
import qrmaster.web.ui.ppCentreCrop
import qrmaster.web.ui.ppFaceCoverage
import qrmaster.web.ui.ppFaceCrop
import qrmaster.web.ui.ppFaceCropSafe
import qrmaster.web.ui.ppHeadBox
import qrmaster.web.ui.ppOutfitRect
import qrmaster.web.ui.ppOutfitRectInSource
import qrmaster.web.ui.ppSheetLayout
import kotlin.math.abs

// Tests for the framing guard added after the 2026-11-24 live failure:
// the face box arrived correctly from detection, the crop math was
// correct, yet the rendered photo showed no face — the preview used a
// layout mapping that did not match the render mapping. These tests pin
// (a) the guarded framing contract — a faceless output must be
// impossible, (b) the preview/download src→dst mapping contract, and
// (c) the A4 sheet count the UI displays.

var guardFailures = 0

fun gcheck(name: String, cond: Boolean, detail: String = "") {
    if (cond) println("PASS $name")
    else { guardFailures++; println("FAIL $name $detail") }
}

fun gnear(a: Float, b: Float, tol: Float = 2f) = abs(a - b) <= tol

private fun centreInside(crop: PPCrop, fx: Float, fy: Float, fw: Float, fh: Float): Boolean {
    val cx = fx + fw / 2f
    val cy = fy + fh / 2f
    return cx >= crop.x && cx <= crop.x + crop.w && cy >= crop.y && cy <= crop.y + crop.h
}

private fun cropInsideImage(crop: PPCrop, iw: Int, ih: Int): Boolean =
    crop.x >= -0.5f && crop.y >= -0.5f && crop.x + crop.w <= iw + 0.5f && crop.y + crop.h <= ih + 0.5f

fun main() {
    val aspect = 35.0 / 45.0

    // ── The exact live case (2026-11-24): selfie 736×1263, face-api box ──
    // Head-based framing: head = box expanded by ppHeadBox (crown 0.36×
    // above the forehead box top, chin 0.04× below), crop sized so the
    // head is 64% of the crop height, crown 11% below the crop top.
    run {
        val f = ppFaceCropSafe(736, 1263, aspect, 223.55f, 369.04f, 295.41f, 297.31f)
        gcheck("live selfie: no fallback needed", !f.fellBack)
        gcheck("live selfie: crop ≈ (118,190,506,650)",
            gnear(f.crop.x, 118f) && gnear(f.crop.y, 190f) && gnear(f.crop.w, 506f) && gnear(f.crop.h, 650f),
            "got ${f.crop}")
        gcheck("live selfie: face fully covered",
            ppFaceCoverage(f.crop, 223.55f, 369.04f, 295.41f, 297.31f) >= 0.999f,
            "cov=${ppFaceCoverage(f.crop, 223.55f, 369.04f, 295.41f, 297.31f)}")
        val head = ppHeadBox(223.55f, 369.04f, 295.41f, 297.31f)
        val headFrac = head.h / f.crop.h
        gcheck("live selfie: head height 55–70% of frame", headFrac in 0.55f..0.70f, "frac=$headFrac")
        val crownRoom = (head.y - f.crop.y) / f.crop.h
        gcheck("live selfie: crown headroom 4–30% of frame", crownRoom in 0.04f..0.30f, "room=$crownRoom")
    }

    // ── Acceptance measurements on REAL photos (2026-11-24) ─────────────
    // Face boxes are the site's own face-api output; crown/chin are the
    // person's true extremes measured from the background-removal alpha
    // silhouette (crown = topmost opaque pixel in the head band; chin =
    // jaw bottom from the silhouette profile / box bottom). The OUTPUT
    // (crop mapped to the frame) must satisfy: crown ≥ 4% of frame
    // height below the top edge, crown→chin = 55–70% of frame height,
    // and no part of the head touching any frame edge.
    data class Sample(
        val label: String, val iw: Int, val ih: Int,
        val fx: Float, val fy: Float, val fw: Float, val fh: Float,
        val crown: Float, val chin: Float
    )
    val samples = listOf(
        // The failing live case: bald selfie, ocean background.
        Sample("selfie (bald)", 736, 1263, 223.55f, 369.04f, 295.41f, 297.31f, 292f, 666.35f),
        // Tight head-shot, voluminous hair (chin = jaw from alpha profile).
        Sample("tight head-shot (hair)", 960, 960, 231.47f, 277.17f, 438.14f, 400.53f, 108f, 640f),
        // Same head-shot with chin at the detector box bottom (upper bound).
        Sample("tight head-shot (box chin)", 960, 960, 231.47f, 277.17f, 438.14f, 400.53f, 108f, 677.70f),
        // Full-length 1870s portrait: small face, hat-like hair mass.
        Sample("full-length (small face)", 994, 1536, 331.71f, 224.40f, 191.54f, 171.68f, 144f, 396.08f),
        // Face low in frame (top 760 px of the selfie; face centre at 68%).
        Sample("face low in frame", 736, 760, 222.57f, 378.16f, 295.26f, 269.77f, 291f, 666.35f)
    )
    for (s in samples) {
        val f = ppFaceCropSafe(s.iw, s.ih, aspect, s.fx, s.fy, s.fw, s.fh)
        val room = (s.crown - f.crop.y) / f.crop.h
        val headFrac = (s.chin - s.crown) / f.crop.h
        gcheck("${s.label}: primary framing (no fallback)", !f.fellBack, "crop=${f.crop}")
        gcheck("${s.label}: crown headroom ≥ 4% (was ${room})", room >= 0.04f, "crop=${f.crop}")
        gcheck("${s.label}: crown→chin 55–70% (was ${headFrac})", headFrac in 0.55f..0.70f, "crop=${f.crop}")
        gcheck("${s.label}: head strictly inside frame vertically",
            s.crown > f.crop.y && s.chin < f.crop.y + f.crop.h, "crop=${f.crop}")
    }

    // ── Face positions × sizes × image shapes: face must end INSIDE ──────
    data class Img(val w: Int, val h: Int, val label: String)
    val images = listOf(
        Img(736, 1263, "portrait"),
        Img(1263, 736, "landscape"),
        Img(1000, 1000, "square"),
        Img(400, 300, "small-landscape")
    )
    // face boxes as fractions of the image: (cx, cy, sizeFrac of height)
    val spots = listOf(
        Triple(0.5f, 0.30f, 0.24f),   // classic passport pose
        Triple(0.5f, 0.12f, 0.20f),   // face near the top
        Triple(0.5f, 0.80f, 0.22f),   // face low
        Triple(0.18f, 0.35f, 0.20f),  // face left
        Triple(0.82f, 0.35f, 0.20f),  // face right
        Triple(0.5f, 0.45f, 0.08f),   // small face (far subject)
        Triple(0.5f, 0.42f, 0.45f)    // big face (close-up)
    )
    for (img in images) {
        for ((i, s) in spots.withIndex()) {
            val fh = img.h * s.third
            val fw = fh * 0.95f
            val fx = (img.w * s.first - fw / 2f).coerceIn(0f, img.w - fw)
            val fy = (img.h * s.second - fh / 2f).coerceIn(0f, img.h - fh)
            val r = ppFaceCropSafe(img.w, img.h, aspect, fx, fy, fw, fh)
            val cov = ppFaceCoverage(r.crop, fx, fy, fw, fh)
            gcheck("${img.label} spot$i: face centre inside crop",
                centreInside(r.crop, fx, fy, fw, fh), "crop=${r.crop} face=($fx,$fy,$fw,$fh)")
            gcheck("${img.label} spot$i: coverage ≥ 0.6 (was $cov)",
                cov >= 0.6f, "crop=${r.crop} face=($fx,$fy,$fw,$fh)")
            gcheck("${img.label} spot$i: crop inside image", cropInsideImage(r.crop, img.w, img.h), "crop=${r.crop}")
            if (!r.fellBack) {
                gcheck("${img.label} spot$i: normal path keeps the WHOLE face",
                    cov >= 0.99f, "cov=$cov crop=${r.crop} face=($fx,$fy,$fw,$fh)")
                // Head contract on the primary path: whole head inside,
                // crown headroom (unless the photo's top forbids it), and
                // head proportion (unless the window is the largest fit).
                val head = ppHeadBox(fx, fy, fw, fh)
                val hTop = head.y.coerceIn(0f, img.h.toFloat())
                val hBot = (head.y + head.h).coerceIn(0f, img.h.toFloat())
                gcheck("${img.label} spot$i: whole head inside crop",
                    hTop >= r.crop.y - 1f && hBot <= r.crop.y + r.crop.h + 1f,
                    "crop=${r.crop} head=($hTop..$hBot)")
                val room = (hTop - r.crop.y) / r.crop.h
                gcheck("${img.label} spot$i: crown headroom ≥ 3.5% or source-limited (was $room)",
                    room >= 0.035f || r.crop.y <= 0.5f || hTop <= 0.04f * r.crop.h,
                    "crop=${r.crop} headTop=$hTop")
                val maxH = minOf(img.h.toFloat(), (img.w / aspect).toFloat())
                val hFrac = (hBot - hTop) / r.crop.h
                gcheck("${img.label} spot$i: head fraction 50–75% or largest window (was $hFrac)",
                    hFrac in 0.50f..0.75f || r.crop.h >= maxH - 1f,
                    "crop=${r.crop} maxH=$maxH")
            }
        }
    }

    // ── Wrong coordinate spaces: the guard must catch and fall back ──────
    run {
        // Box reported in a 2× space (e.g. detection ran on an upscaled
        // copy): centre lands outside the image → fallback must trigger,
        // and the final crop must still frame the box's in-image part.
        val r = ppFaceCropSafe(736, 1263, aspect, 447f, 738f, 591f, 595f)
        gcheck("2x-space box: fallback engaged", r.fellBack, "crop=${r.crop}")
        gcheck("2x-space box: crop inside image", cropInsideImage(r.crop, 736, 1263), "got ${r.crop}")
        gcheck("2x-space box: in-image part of the box is fully framed",
            ppFaceCoverage(r.crop, 447f, 738f, 289f, 525f) >= 0.99f,
            "cov=${ppFaceCoverage(r.crop, 447f, 738f, 289f, 525f)} crop=${r.crop}")
        // Degenerate box → centre crop, no crash.
        val d = ppFaceCropSafe(736, 1263, aspect, 0f, 0f, 0f, 0f)
        gcheck("degenerate box: sane crop", d.crop.w > 0f && cropInsideImage(d.crop, 736, 1263), "got ${d.crop}")
        // Box hanging off the top-left corner: the in-image part must be framed.
        val p = ppFaceCropSafe(736, 1263, aspect, -80f, -40f, 300f, 300f)
        gcheck("off-corner box: crop inside image", cropInsideImage(p.crop, 736, 1263), "got ${p.crop}")
        gcheck("off-corner box: covers ≥60% of the given box",
            ppFaceCoverage(p.crop, -80f, -40f, 300f, 300f) >= 0.6f,
            "cov=${ppFaceCoverage(p.crop, -80f, -40f, 300f, 300f)} crop=${p.crop}")
    }

    // ── ppFaceCoverage arithmetic ────────────────────────────────────────
    gcheck("coverage: identical rects = 1",
        ppFaceCoverage(PPCrop(10f, 10f, 100f, 100f), 10f, 10f, 100f, 100f) == 1f)
    gcheck("coverage: disjoint = 0",
        ppFaceCoverage(PPCrop(0f, 0f, 50f, 50f), 100f, 100f, 50f, 50f) == 0f)
    gcheck("coverage: half overlap = 0.5",
        ppFaceCoverage(PPCrop(0f, 0f, 100f, 100f), 50f, 0f, 100f, 100f) == 0.5f)

    // ── Preview/download mapping contract ────────────────────────────────
    // Both the on-screen preview (Canvas drawImageRect) and the download
    // (shell drawImage 9-arg) map srcRect=crop → dstRect=frame. Pin the
    // arithmetic: the face rect mapped through that transform must lie
    // inside the frame, at passport proportion.
    run {
        val crop = ppFaceCrop(736, 1263, aspect, 223.55f, 369.04f, 295.41f, 297.31f)
        val frameW = 430f; val frameH = 553f
        val sx = frameW / crop.w; val sy = frameH / crop.h
        val faceDstX = (223.55f - crop.x) * sx
        val faceDstY = (369.04f - crop.y) * sy
        val faceDstW = 295.41f * sx
        val faceDstH = 297.31f * sy
        gcheck("mapping: face inside frame horizontally",
            faceDstX >= 0f && faceDstX + faceDstW <= frameW + 0.5f, "x=$faceDstX w=$faceDstW")
        gcheck("mapping: face inside frame vertically",
            faceDstY >= 0f && faceDstY + faceDstH <= frameH + 0.5f, "y=$faceDstY h=$faceDstH")
        gcheck("mapping: headroom above face (5–35% of frame)",
            faceDstY / frameH in 0.05f..0.35f, "headroom=${faceDstY / frameH}")
        gcheck("mapping: isotropic (sx ≈ sy)", abs(sx - sy) < 0.02f, "sx=$sx sy=$sy")
    }

    // ── Outfit rect: output space → source space (Cut-out view) ──────────
    run {
        val crop = PPCrop(161f, 312f, 420f, 541f)
        val r = ppOutfitRect(413, 531, 1f, 0f)
        val rs = ppOutfitRectInSource(crop, r, 413, 531)
        // The outfit artwork is wider than the photo frame by design (it
        // bleeds off the edges and is clipped at render), so containment
        // is NOT expected — exact fraction preservation is the contract:
        // the Cut-out view must show the overlay exactly where the
        // Result view (and the download) put it.
        gcheck("outfit-in-source: fractions preserved",
            gnear((rs.x - crop.x) / crop.w, r.x / 413f, 0.01f) && gnear((rs.w) / crop.w, r.w / 413f, 0.01f) &&
                gnear((rs.y - crop.y) / crop.h, r.y / 531f, 0.01f) && gnear((rs.h) / crop.h, r.h / 531f, 0.01f),
            "rs=$rs r=$r")
        gcheck("outfit-in-source: centres aligned",
            gnear(rs.x + rs.w / 2f, crop.x + crop.w / 2f, 1f),
            "rs=$rs crop=$crop")
    }

    // ── Sheet counts the UI shows (Size tab text) ────────────────────────
    run {
        val a4 = ppSheetLayout(210.0, 297.0, 35.0, 45.0)
        gcheck("A4 + India 35×45 = 20 copies (4×5)",
            a4.count == 20 && a4.cols == 4 && a4.rows == 5, "got ${a4.count} (${a4.cols}x${a4.rows})")
        val letter = ppSheetLayout(215.9, 279.4, 35.0, 45.0)
        gcheck("Letter + India 35×45 = 25 copies (5×5)",
            letter.count == 25 && letter.cols == 5 && letter.rows == 5, "got ${letter.count}")
        val a4us = ppSheetLayout(210.0, 297.0, 50.8, 50.8)
        gcheck("A4 + USA 2×2in = 15 copies (3×5)",
            a4us.count == 15 && a4us.cols == 3 && a4us.rows == 5, "got ${a4us.count} (${a4us.cols}x${a4us.rows})")
    }

    println(if (guardFailures == 0) "ALL GUARD TESTS PASSED" else "$guardFailures GUARD TEST(S) FAILED")
}
