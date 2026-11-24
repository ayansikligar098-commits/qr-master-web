import qrmaster.web.ui.PPCrop
import qrmaster.web.ui.ppCentreCrop
import qrmaster.web.ui.ppFaceCrop
import qrmaster.web.ui.ppInchToPx
import qrmaster.web.ui.ppMmToPt
import qrmaster.web.ui.ppMmToPx
import qrmaster.web.ui.ppPreset
import qrmaster.web.ui.ppPreviewTransform
import qrmaster.web.ui.ppSheetLayout
import qrmaster.web.ui.ppZoomShift
import kotlin.math.abs

var failures = 0

fun check(name: String, cond: Boolean, detail: String = "") {
    if (cond) println("PASS $name")
    else { failures++; println("FAIL $name $detail") }
}

fun near(a: Float, b: Float, tol: Float = 1.5f) = abs(a - b) <= tol
fun nearD(a: Double, b: Double, tol: Double = 0.01) = abs(a - b) <= tol

fun main() {
    // ── mm / inch → px at 300 DPI ────────────────────────────────────────
    check("35mm = 413px", ppMmToPx(35.0) == 413, "got ${ppMmToPx(35.0)}")
    check("45mm = 531px", ppMmToPx(45.0) == 531, "got ${ppMmToPx(45.0)}")
    check("50.8mm = 600px", ppMmToPx(50.8) == 600, "got ${ppMmToPx(50.8)}")
    check("2in = 600px", ppInchToPx(2.0) == 600, "got ${ppInchToPx(2.0)}")
    check("A4 w = 2480px", ppMmToPx(210.0) == 2480, "got ${ppMmToPx(210.0)}")
    check("A4 h = 3508px", ppMmToPx(297.0) == 3508, "got ${ppMmToPx(297.0)}")
    check("4in = 1200px", ppMmToPx(101.6) == 1200, "got ${ppMmToPx(101.6)}")
    check("6in = 1800px", ppMmToPx(152.4) == 1800, "got ${ppMmToPx(152.4)}")
    check("25.4mm = 72pt", nearD(ppMmToPt(25.4), 72.0), "got ${ppMmToPt(25.4)}")

    // ── Presets (task list) ──────────────────────────────────────────────
    val india = ppPreset("in")
    check("India px 413x531", india.pxW() == 413 && india.pxH() == 531, "got ${india.pxW()}x${india.pxH()}")
    val us = ppPreset("us")
    check("US px 600x600", us.pxW() == 600 && us.pxH() == 600, "got ${us.pxW()}x${us.pxH()}")
    val ukeu = ppPreset("ukeu")
    check("UK/EU px 413x531", ukeu.pxW() == 413 && ukeu.pxH() == 531, "got ${ukeu.pxW()}x${ukeu.pxH()}")
    val ca = ppPreset("ca")
    check("Canada px 591x827", ca.pxW() == 591 && ca.pxH() == 827, "got ${ca.pxW()}x${ca.pxH()}")
    val cn = ppPreset("cn")
    check("China px 390x567", cn.pxW() == 390 && cn.pxH() == 567, "got ${cn.pxW()}x${cn.pxH()}")
    check("India aspect", nearD(india.aspect, 35.0 / 45.0, 0.0001), "got ${india.aspect}")

    val aspect = 35.0 / 45.0

    // ── Face framing: centred face in a big portrait photo ───────────────
    // img 3000x4000, face box (1200,800,600,600). Head-based framing:
    // head = 600 × 1.40 = 840 tall (crown 0.36 + chin 0.04 allowances),
    // crop height = 840 / 0.64 = 1312.5, crown 11% below the crop top.
    val fc = ppFaceCrop(3000, 4000, aspect, 1200f, 800f, 600f, 600f)
    check("face crop keeps aspect", nearD((fc.w / fc.h).toDouble(), aspect, 0.002), "crop=$fc")
    check("face crop height = headH/0.64", near(fc.h, 840f / 0.64f, 2f), "crop=$fc")
    check("face crop left", near(fc.x, 989.6f, 2f), "crop=$fc")
    check("face crop top", near(fc.y, 439.6f, 2f), "crop=$fc")
    check(
        "face fully inside crop",
        1200f >= fc.x && 1800f <= fc.x + fc.w && 800f >= fc.y && 1400f <= fc.y + fc.h,
        "crop=$fc"
    )
    check(
        "face crop inside image",
        fc.x >= 0f && fc.y >= 0f && fc.x + fc.w <= 3000f && fc.y + fc.h <= 4000f,
        "crop=$fc"
    )

    // Face in the top-left corner → window clamps to the corner.
    val corner = ppFaceCrop(1000, 1200, aspect, 20f, 10f, 300f, 300f)
    check("corner face clamps x=0", corner.x == 0f, "crop=$corner")
    check("corner face clamps y=0", corner.y == 0f, "crop=$corner")
    check("corner face keeps aspect", nearD((corner.w / corner.h).toDouble(), aspect, 0.002), "crop=$corner")

    // Face nearly fills a small photo → crop height = photo height.
    val big = ppFaceCrop(800, 1000, aspect, 100f, 100f, 600f, 700f)
    check("big face crop h = imgH", near(big.h, 1000f, 0.6f), "crop=$big")
    check("big face crop y = 0", big.y == 0f, "crop=$big")
    check("big face keeps aspect", nearD((big.w / big.h).toDouble(), aspect, 0.002), "crop=$big")

    // Invalid face → centre crop fallback (never crashes, never empty).
    val fb = ppFaceCrop(3000, 4000, aspect, 0f, 0f, 0f, 0f)
    check("invalid face falls back non-empty", fb.w > 0f && fb.h > 0f, "crop=$fb")

    // ── Centre crop ──────────────────────────────────────────────────────
    val land = ppCentreCrop(4000, 3000, aspect)
    check("landscape centre crop h = imgH", near(land.h, 3000f, 0.6f), "crop=$land")
    check("landscape centre crop centred x", near(land.x, (4000f - land.w) / 2f, 0.6f), "crop=$land")
    val port = ppCentreCrop(3000, 4000, aspect)
    check("portrait centre crop w = imgW", near(port.w, 3000f, 0.6f), "crop=$port")
    check("portrait centre crop top bias 0.42", near(port.y, (4000f - port.h) * 0.42f, 1f), "crop=$port")

    // ── Zoom / shift ─────────────────────────────────────────────────────
    val zoomed = ppZoomShift(fc, 3000, 4000, 2f, 0f, 0f)
    check("zoom 2 halves window", near(zoomed.w, fc.w / 2f, 1f) && near(zoomed.h, fc.h / 2f, 1f), "crop=$zoomed")
    check(
        "zoom keeps centre",
        near(zoomed.x + zoomed.w / 2f, fc.x + fc.w / 2f, 1f) && near(zoomed.y + zoomed.h / 2f, fc.y + fc.h / 2f, 1f),
        "crop=$zoomed base=$fc"
    )
    check("zoom keeps aspect", nearD((zoomed.w / zoomed.h).toDouble(), aspect, 0.002), "crop=$zoomed")
    val clampedZoom = ppZoomShift(fc, 3000, 4000, 9f, 0f, 0f)
    val zoom25 = ppZoomShift(fc, 3000, 4000, 2.5f, 0f, 0f)
    check("zoom clamps at 2.5", near(clampedZoom.w, zoom25.w, 0.6f), "crop=$clampedZoom vs $zoom25")
    // Base spans the full width → no horizontal movement possible.
    val full = ppZoomShift(port, 3000, 4000, 1f, 0.5f, 0f)
    check("full-width base cannot shift x", full.x == 0f, "crop=$full")
    // Shift moves the window but never out of the photo.
    val shifted = ppZoomShift(fc, 3000, 4000, 1.5f, 0.5f, -0.5f)
    check(
        "shifted window inside image",
        shifted.x >= 0f && shifted.y >= 0f && shifted.x + shifted.w <= 3000f && shifted.y + shifted.h <= 4000f,
        "crop=$shifted"
    )
    check("shift moved window right", shifted.x > ppZoomShift(fc, 3000, 4000, 1.5f, 0f, 0f).x, "crop=$shifted")

    // ── Preview transform ────────────────────────────────────────────────
    val t = ppPreviewTransform(350f, 450f, fc, 3000, 4000)
    check("preview scale", near(t.scale, 350f / fc.w, 0.001f), "t=$t")
    check("preview crop fills frame height", near(fc.h * t.scale, 450f, 1f), "t=$t crop=$fc")
    check("preview left", near(t.left, -fc.x * t.scale, 0.5f), "t=$t")
    check("preview top", near(t.top, -fc.y * t.scale, 0.5f), "t=$t")
    check("preview img size", near(t.imgW, 3000f * t.scale, 1f) && near(t.imgH, 4000f * t.scale, 1f), "t=$t")

    // ── Sheet layout (margin 4mm, gap 1mm — studio counts) ──────────────
    val s46 = ppSheetLayout(101.6, 152.4, 35.0, 45.0)
    check("4x6 paper px", s46.paperPxW == 1200 && s46.paperPxH == 1800, "sheet=$s46")
    check("4x6 India grid 2x3 = 6", s46.cols == 2 && s46.rows == 3 && s46.count == 6, "sheet cols=${s46.cols} rows=${s46.rows} count=${s46.count}")
    check("4x6 first cell at margin", s46.cells[0].x == ppMmToPx(4.0) && s46.cells[0].y == ppMmToPx(4.0), "cell=${s46.cells[0]}")
    check(
        "4x6 second cell offset by copy+gap",
        s46.cells[1].x == ppMmToPx(4.0) + 413 + ppMmToPx(1.0) && s46.cells[1].y == ppMmToPx(4.0),
        "cell=${s46.cells[1]}"
    )
    check(
        "4x6 all cells inside paper with margin",
        s46.cells.all { it.x + it.w <= s46.paperPxW - ppMmToPx(4.0) + 1 && it.y + it.h <= s46.paperPxH - ppMmToPx(4.0) + 1 },
        "sheet=$s46"
    )
    check("4x6 cell size = photo px", s46.cells.all { it.w == 413 && it.h == 531 }, "sheet=$s46")

    val sA4 = ppSheetLayout(210.0, 297.0, 35.0, 45.0)
    check("A4 India grid 5x6 = 30", sA4.cols == 5 && sA4.rows == 6 && sA4.count == 30, "cols=${sA4.cols} rows=${sA4.rows} count=${sA4.count}")

    val sUs = ppSheetLayout(210.0, 297.0, 50.8, 50.8)
    check("A4 US grid 3x5 = 15", sUs.cols == 3 && sUs.rows == 5 && sUs.count == 15, "cols=${sUs.cols} rows=${sUs.rows} count=${sUs.count}")

    val sCa = ppSheetLayout(101.6, 152.4, 50.0, 70.0)
    check("4x6 Canada grid 1x2 = 2", sCa.count == 2, "count=${sCa.count}")

    val sBig = ppSheetLayout(101.6, 152.4, 100.0, 150.0)
    check("oversize photo fits 0 copies", sBig.count == 0 && sBig.cells.isEmpty(), "count=${sBig.count}")

    val sCn = ppSheetLayout(210.0, 297.0, 33.0, 48.0)
    check(
        "A4 China cells inside paper",
        sCn.count > 0 && sCn.cells.all { it.x >= 0 && it.y >= 0 && it.x + it.w <= sCn.paperPxW && it.y + it.h <= sCn.paperPxH },
        "count=${sCn.count}"
    )

    println(if (failures == 0) "ALL PASSPORT GEOMETRY TESTS PASSED" else "$failures FAILURES")
}
