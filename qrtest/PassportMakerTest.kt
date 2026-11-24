import qrmaster.web.ui.PASSPORT_COMMON
import qrmaster.web.ui.PASSPORT_COUNTRIES
import qrmaster.web.ui.PPCrop
import qrmaster.web.ui.PPRectF
import qrmaster.web.ui.PP_BG_CLASSICAL
import qrmaster.web.ui.PP_BG_STYLISH
import qrmaster.web.ui.PP_OUTFITS
import qrmaster.web.ui.PP_OUTFIT_TOP_FRAC
import qrmaster.web.ui.PP_PAPER_OPTIONS
import qrmaster.web.ui.ppBackground
import qrmaster.web.ui.ppCropMove
import qrmaster.web.ui.ppCropResize
import qrmaster.web.ui.ppFaceCrop
import qrmaster.web.ui.ppHeadBox
import qrmaster.web.ui.ppMmToPx
import qrmaster.web.ui.ppOutfitRect
import qrmaster.web.ui.ppOutfitRectForPerson
import qrmaster.web.ui.ppPaper
import qrmaster.web.ui.ppPreset
import qrmaster.web.ui.ppPxToMm
import qrmaster.web.ui.ppSearchCountries
import qrmaster.web.ui.ppSheetLayout
import kotlin.math.abs

var makerFailures = 0

fun mcheck(name: String, cond: Boolean, detail: String = "") {
    if (cond) println("PASS $name")
    else { makerFailures++; println("FAIL $name $detail") }
}

fun mnear(a: Float, b: Float, tol: Float = 1.5f) = abs(a - b) <= tol
fun mnearD(a: Double, b: Double, tol: Double = 0.01) = abs(a - b) <= tol

fun main() {
    // ── Preset catalogue ─────────────────────────────────────────────────
    mcheck("common has 3 entries", PASSPORT_COMMON.size == 3, "got ${PASSPORT_COMMON.size}")
    mcheck(
        "common order: China 1in, China 2in, USA",
        PASSPORT_COMMON[0].wMm == 25.0 && PASSPORT_COMMON[0].hMm == 35.0 &&
            PASSPORT_COMMON[1].wMm == 35.0 && PASSPORT_COMMON[1].hMm == 49.0 &&
            PASSPORT_COMMON[2].id == "us",
        "got ${PASSPORT_COMMON.map { it.id }}"
    )
    mcheck("countries = 118 (38 named + 80 standard)", PASSPORT_COUNTRIES.size == 118, "got ${PASSPORT_COUNTRIES.size}")
    mcheck(
        "standard 35x45 tail present (UAE, Zimbabwe, Nepal, Qatar)",
        listOf("ae", "zw", "np", "qa").all { id -> PASSPORT_COUNTRIES.any { it.id == id && it.wMm == 35.0 && it.hMm == 45.0 } }
    )
    mcheck("preset ids unique", (PASSPORT_COMMON + PASSPORT_COUNTRIES).map { it.id }.distinct().size == PASSPORT_COMMON.size + PASSPORT_COUNTRIES.size)
    mcheck("India is default fallback", ppPreset("nonexistent").id == "in")
    mcheck("ukeu alias resolves to UK 35x45", ppPreset("ukeu").let { it.wMm == 35.0 && it.hMm == 45.0 })
    mcheck("USA common = 600x600 px", ppPreset("us").let { it.pxW() == 600 && it.pxH() == 600 }, "got ${ppPreset("us").pxW()}x${ppPreset("us").pxH()}")
    mcheck("United States country = 591x591 px", ppPreset("us50").let { it.pxW() == 591 && it.pxH() == 591 }, "got ${ppPreset("us50").pxW()}x${ppPreset("us50").pxH()}")
    mcheck("Spain 32x26 = 378x307 px", ppPreset("es").let { it.pxW() == 378 && it.pxH() == 307 }, "got ${ppPreset("es").pxW()}x${ppPreset("es").pxH()}")
    mcheck("Finland 36x47 = 425x555 px", ppPreset("fi").let { it.pxW() == 425 && it.pxH() == 555 }, "got ${ppPreset("fi").pxW()}x${ppPreset("fi").pxH()}")
    mcheck("Canada 50x70", ppPreset("ca").let { it.wMm == 50.0 && it.hMm == 70.0 })
    mcheck("Indonesia 40x60", ppPreset("idn").let { it.wMm == 40.0 && it.hMm == 60.0 })
    mcheck("Argentina 40x40", ppPreset("ar").let { it.wMm == 40.0 && it.hMm == 40.0 })
    mcheck("Colombia 30x40", ppPreset("co").let { it.wMm == 30.0 && it.hMm == 40.0 })

    // ── Search ───────────────────────────────────────────────────────────
    mcheck("search blank returns all", ppSearchCountries("").size == PASSPORT_COUNTRIES.size)
    mcheck("search 'india' finds India first", ppSearchCountries("india").firstOrNull()?.id == "in", "got ${ppSearchCountries("india").map { it.id }}")
    mcheck("search is case-insensitive", ppSearchCountries("INDIA").isNotEmpty())
    mcheck("search 'united' finds US + UK + UAE", ppSearchCountries("united").size >= 3, "got ${ppSearchCountries("united").size}")
    mcheck("search nonsense is empty", ppSearchCountries("zzzz").isEmpty())

    // ── mm/px round trip ─────────────────────────────────────────────────
    mcheck("413px back to ~35mm", mnearD(ppPxToMm(413), 35.0, 0.05), "got ${ppPxToMm(413)}")
    mcheck("600px back to ~50.8mm", mnearD(ppPxToMm(600), 50.8, 0.05), "got ${ppPxToMm(600)}")

    // ── Paper options ────────────────────────────────────────────────────
    mcheck("paper options = 8, single first", PP_PAPER_OPTIONS.size == 8 && PP_PAPER_OPTIONS[0].id == "single")
    mcheck("3R px 1050x1500", ppPaper("3r").let { ppMmToPx(it.wMm) == 1050 && ppMmToPx(it.hMm) == 1500 })
    mcheck("Letter px 2550x3300", ppPaper("letter").let { ppMmToPx(it.wMm) == 2550 && ppMmToPx(it.hMm) == 3300 })
    mcheck("Legal px 2550x4200", ppPaper("legal").let { ppMmToPx(it.wMm) == 2550 && ppMmToPx(it.hMm) == 4200 })

    val s4r = ppSheetLayout(152.4, 101.6, 35.0, 45.0)
    mcheck("4R India sheet = 8 copies (4x2)", s4r.count == 8 && s4r.cols == 4 && s4r.rows == 2, "got ${s4r.count} (${s4r.cols}x${s4r.rows})")
    mcheck(
        "4R cells inside paper with margin",
        s4r.cells.all { it.x >= ppMmToPx(2.0) - 1 && it.y >= ppMmToPx(2.0) - 1 &&
            it.x + it.w <= s4r.paperPxW - ppMmToPx(2.0) + 1 && it.y + it.h <= s4r.paperPxH - ppMmToPx(2.0) + 1 },
        "sheet=$s4r"
    )
    val sA5 = ppSheetLayout(148.5, 210.0, 35.0, 45.0)
    mcheck("A5 India sheet = 12 copies (3x4)", sA5.count == 12 && sA5.cols == 3 && sA5.rows == 4, "got ${sA5.count} (${sA5.cols}x${sA5.rows})")
    val sLetter = ppSheetLayout(215.9, 279.4, 35.0, 45.0)
    mcheck("Letter India sheet = 25 copies (5x5)", sLetter.count == 25 && sLetter.cols == 5 && sLetter.rows == 5, "got ${sLetter.count} (${sLetter.cols}x${sLetter.rows})")
    val s3r = ppSheetLayout(88.9, 127.0, 35.0, 45.0)
    mcheck("3R India sheet = 4 copies (2x2)", s3r.count == 4 && s3r.cols == 2 && s3r.rows == 2, "got ${s3r.count} (${s3r.cols}x${s3r.rows})")
    val s5r = ppSheetLayout(177.8, 127.0, 35.0, 45.0)
    mcheck("5R India sheet = 8 copies (4x2)", s5r.count == 8 && s5r.cols == 4 && s5r.rows == 2, "got ${s5r.count} (${s5r.cols}x${s5r.rows})")
    val sLegalCa = ppSheetLayout(215.9, 355.6, 50.0, 70.0)
    mcheck(
        "Legal Canada = 16 copies, cells inside paper",
        sLegalCa.count == 16 && sLegalCa.cells.all { it.x + it.w <= sLegalCa.paperPxW && it.y + it.h <= sLegalCa.paperPxH },
        "count=${sLegalCa.count}"
    )

    // ── Backgrounds ──────────────────────────────────────────────────────
    mcheck("classical = 5", PP_BG_CLASSICAL.size == 5, "got ${PP_BG_CLASSICAL.size}")
    mcheck("stylish = 15", PP_BG_STYLISH.size == 15, "got ${PP_BG_STYLISH.size}")
    mcheck("white is #FFFFFF", ppBackground("white")?.colors == listOf("#FFFFFF"))
    mcheck("blue is #438edb", ppBackground("blue")?.colors == listOf("#438edb"))
    mcheck("dark blue is #2a385b", ppBackground("darkblue")?.colors == listOf("#2a385b"))
    mcheck("light blue is #00BFF3", ppBackground("lightblue")?.colors == listOf("#00BFF3"))
    mcheck("red is #FF0000", ppBackground("red")?.colors == listOf("#FF0000"))
    mcheck("3 gradients in stylish", PP_BG_STYLISH.count { it.isGradient } == 3, "got ${PP_BG_STYLISH.count { it.isGradient }}")
    mcheck("sky gradient stops", ppBackground("skygradient")?.colors == listOf("#66B5F2", "#CBE8FB"))
    mcheck("all bg ids unique", (PP_BG_CLASSICAL + PP_BG_STYLISH).map { it.id }.distinct().size == 20)

    // ── Outfits ──────────────────────────────────────────────────────────
    mcheck("outfits = 8", PP_OUTFITS.size == 8, "got ${PP_OUTFITS.size}")
    mcheck("men = 3", PP_OUTFITS.count { it.category == "Men's" } == 3)
    mcheck("women = 3", PP_OUTFITS.count { it.category == "Women's" } == 3)
    mcheck("children = 2", PP_OUTFITS.count { it.category == "Children's" } == 2)
    mcheck("outfit paths are pngs in outfits/", PP_OUTFITS.all { it.path.startsWith("outfits/") && it.path.endsWith(".png") })

    // ── Outfit placement (India frame 413x531) ───────────────────────────
    val r = ppOutfitRect(413, 531, 1f, 0f)
    mcheck("outfit w = 450", r.w == 450, "got ${r.w}")
    mcheck("outfit h = 338", r.h == 338, "got ${r.h}")
    mcheck("outfit centred (x = -19)", r.x == -19, "got ${r.x}")
    mcheck("outfit collar at 77% height", mnear(r.y + 0.30f * r.h, 531f * 0.77f, 2f), "got ${r.y + 0.30f * r.h}")
    mcheck("outfit covers frame bottom", r.y + r.h >= 531, "got ${r.y + r.h}")
    val rBig = ppOutfitRect(413, 531, 9f, 0f)
    val r14 = ppOutfitRect(413, 531, 1.4f, 0f)
    mcheck("outfit scale clamps at 1.4", rBig.w == r14.w && rBig.h == r14.h, "got ${rBig.w} vs ${r14.w}")
    val rShift = ppOutfitRect(413, 531, 1f, 0.05f)
    mcheck("outfit yShift moves it down", rShift.y > r.y, "got ${rShift.y} vs ${r.y}")
    val rUs = ppOutfitRect(600, 600, 1f, 0f)
    mcheck("outfit US frame keeps aspect", mnearD(rUs.w.toDouble() / rUs.h, 4.0 / 3.0, 0.01), "got ${rUs.w}x${rUs.h}")

    // ── Face-anchored outfit placement ───────────────────────────────────
    // Standard framing: 3000x4000 photo, face (1200,800,600,600), India out.
    val aCrop = ppFaceCrop(3000, 4000, 35.0 / 45.0, 1200f, 800f, 600f, 600f)
    val aFace = PPRectF(1200f, 800f, 600f, 600f)
    val aHead = ppHeadBox(1200f, 800f, 600f, 600f)
    val anchored = ppOutfitRectForPerson(413, 531, aCrop, aFace, 0.94f, 1f, 0f)
    val headWOut = aHead.w * (413f / aCrop.w)
    val chinOut = ((aHead.y + aHead.h) - aCrop.y) * (531f / aCrop.h)
    mcheck(
        "anchored garment span = 2x head width",
        mnear((0.94f * anchored.w), 2f * headWOut, 6f),
        "span=${0.94f * anchored.w} headWOut=$headWOut rect=$anchored"
    )
    mcheck(
        "anchored garment top sits just below chin",
        mnear(anchored.y + PP_OUTFIT_TOP_FRAC * anchored.h, chinOut + 0.012f * 531f, 3f),
        "top=${anchored.y + PP_OUTFIT_TOP_FRAC * anchored.h} chin=$chinOut rect=$anchored"
    )
    mcheck(
        "anchored outfit centred on face centre",
        mnear(anchored.x + anchored.w / 2f, (1500f - aCrop.x) * (413f / aCrop.w), 3f),
        "rect=$anchored"
    )
    mcheck("anchored outfit covers frame bottom", anchored.y + anchored.h >= 531, "rect=$anchored")
    // Same face, crop window moved DOWN 200 source px (chin rides higher
    // in the frame): the garment must follow the chin UP by 200*sy.
    val cropLower = PPCrop(aCrop.x, aCrop.y + 200f, aCrop.w, aCrop.h)
    val anchoredLower = ppOutfitRectForPerson(413, 531, cropLower, aFace, 0.94f, 1f, 0f)
    mcheck(
        "anchored outfit follows chin when crop moves",
        mnear((anchored.y - anchoredLower.y).toFloat(), 200f * (531f / aCrop.h), 4f),
        "dy=${anchored.y - anchoredLower.y} expected=${200f * (531f / aCrop.h)}"
    )
    // Off-centre face: garment centre follows the face, not the frame.
    val offCrop = ppFaceCrop(3000, 4000, 35.0 / 45.0, 300f, 800f, 600f, 600f)
    val offAnchored = ppOutfitRectForPerson(413, 531, offCrop, PPRectF(300f, 800f, 600f, 600f), 0.94f, 1f, 0f)
    mcheck(
        "anchored outfit follows off-centre face",
        mnear(offAnchored.x + offAnchored.w / 2f, (600f - offCrop.x) * (413f / offCrop.w), 3f),
        "rect=$offAnchored crop=$offCrop"
    )
    // Children's artwork is narrower (spanFrac 0.81) → wider rect for the
    // same garment span.
    val childAnchored = ppOutfitRectForPerson(413, 531, aCrop, aFace, 0.81f, 1f, 0f)
    mcheck(
        "child outfit rect wider for same span",
        mnear((0.81f * childAnchored.w), 2f * headWOut, 6f) && childAnchored.w > anchored.w,
        "child=$childAnchored adult=$anchored"
    )
    // No face → exact fallback to the frame-fraction placement.
    val noFace = ppOutfitRectForPerson(413, 531, aCrop, null, 0.94f, 1f, 0f)
    val fallback = ppOutfitRect(413, 531, 1f, 0f)
    mcheck(
        "anchored falls back without a face",
        noFace.x == fallback.x && noFace.y == fallback.y && noFace.w == fallback.w && noFace.h == fallback.h,
        "got $noFace vs $fallback"
    )

    // ── Crop move / resize ───────────────────────────────────────────────
    val base = PPCrop(200f, 200f, 350f, 450f) // 35:45 aspect in a 1000x1200 photo
    val moved = ppCropMove(base, 5000f, -5000f, 1000, 1200)
    mcheck("crop move clamps right/top", moved.x == 650f && moved.y == 0f, "got $moved")
    val movedNeg = ppCropMove(base, -500f, 5000f, 1000, 1200)
    mcheck("crop move clamps left/bottom", movedNeg.x == 0f && movedNeg.y == 750f, "got $movedNeg")

    val corner = ppCropResize(base, 3, 100f, 40f, 1000, 1200)
    mcheck("corner resize keeps aspect", mnearD((corner.w / corner.h).toDouble(), 35.0 / 45.0, 0.004), "got $corner")
    mcheck("corner resize follows dominant axis (w=450)", mnear(corner.w, 450f, 1.5f), "got $corner")
    mcheck("corner resize anchors top-left", mnear(corner.x, 200f, 0.6f) && mnear(corner.y, 200f, 0.6f), "got $corner")

    val edge = ppCropResize(base, 5, 70f, 0f, 1000, 1200)
    mcheck("edge resize keeps aspect", mnearD((edge.w / edge.h).toDouble(), 35.0 / 45.0, 0.004), "got $edge")
    mcheck("edge resize w=420", mnear(edge.w, 420f, 1.5f), "got $edge")
    mcheck("edge resize keeps vertical centre", mnear(edge.y + edge.h / 2, 425f, 1.5f), "got $edge")

    val huge = ppCropResize(base, 3, 5000f, 5000f, 1000, 1200)
    mcheck(
        "resize never leaves the photo",
        huge.x >= 0f && huge.y >= 0f && huge.x + huge.w <= 1000.5f && huge.y + huge.h <= 1200.5f,
        "got $huge"
    )
    mcheck("oversize resize keeps aspect", mnearD((huge.w / huge.h).toDouble(), 35.0 / 45.0, 0.01), "got $huge")

    val tiny = ppCropResize(base, 0, 400f, 500f, 1000, 1200)
    mcheck("shrink keeps aspect + min size", tiny.w >= 9.9f && mnearD((tiny.w / tiny.h).toDouble(), 35.0 / 45.0, 0.01), "got $tiny")

    println(if (makerFailures == 0) "ALL PASSPORT MAKER TESTS PASSED" else "$makerFailures FAILURES")
}
