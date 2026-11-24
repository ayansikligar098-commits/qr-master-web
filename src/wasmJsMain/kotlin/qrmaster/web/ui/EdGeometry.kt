package qrmaster.web.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Pure editor geometry (no Compose / browser dependencies) so the resize
 * math can be unit-tested on the JVM — see qrtest/EdGeometryTest.kt.
 *
 * Handle indices (same order as edHandles): 0 = top-left, 1 = top-right,
 * 2 = bottom-left, 3 = bottom-right, 4 = top edge, 5 = right edge,
 * 6 = bottom edge, 7 = left edge.
 */

/** Axis-aligned box in page points. */
data class EdBox(val x: Float, val y: Float, val w: Float, val h: Float)

const val ED_MIN_SIZE = 10f

/**
 * Free resize — the editor's original behaviour for text, shapes and
 * drawings: width and height follow the pointer independently.
 */
fun edResizeFree(b: EdBox, handle: Int, dx: Float, dy: Float): EdBox {
    var x = b.x; var y = b.y; var w = b.w; var h = b.h
    when (handle) {
        0 -> { x += dx; w -= dx; y += dy; h -= dy }
        1 -> { w += dx; y += dy; h -= dy }
        2 -> { x += dx; w -= dx; h += dy }
        3 -> { w += dx; h += dy }
        4 -> { y += dy; h -= dy }
        5 -> { w += dx }
        6 -> { h += dy }
        7 -> { x += dx; w -= dx }
    }
    if (w < ED_MIN_SIZE) { if (handle == 0 || handle == 2 || handle == 7) x -= (ED_MIN_SIZE - w); w = ED_MIN_SIZE }
    if (h < ED_MIN_SIZE) { if (handle == 0 || handle == 1 || handle == 4) y -= (ED_MIN_SIZE - h); h = ED_MIN_SIZE }
    return EdBox(x, y, w, h)
}

/**
 * Aspect-ratio-locked resize for IMAGE objects: whichever handle is dragged
 * (corner OR edge), the box scales uniformly — the photo can only grow or
 * shrink, never stretch, squeeze or crop. The opposite corner (corner
 * handles) or opposite edge (edge handles) stays anchored; for edge handles
 * the box stays centred on the perpendicular axis. The minimum size applies
 * to both axes together, so the ratio survives even at the floor.
 */
fun edResizeLocked(b: EdBox, handle: Int, dx: Float, dy: Float): EdBox {
    if (b.w <= 0f || b.h <= 0f || handle !in 0..7) return b
    // Size each axis would reach if it followed the pointer on its own.
    var rawW = b.w
    var rawH = b.h
    when (handle) {
        0 -> { rawW = b.w - dx; rawH = b.h - dy }
        1 -> { rawW = b.w + dx; rawH = b.h - dy }
        2 -> { rawW = b.w - dx; rawH = b.h + dy }
        3 -> { rawW = b.w + dx; rawH = b.h + dy }
        4 -> { rawH = b.h - dy }
        5 -> { rawW = b.w + dx }
        6 -> { rawH = b.h + dy }
        7 -> { rawW = b.w - dx }
    }
    // One uniform scale: edges are driven by their own axis; corners follow
    // whichever axis moved most (relative), so the box tracks the pointer.
    val sw = rawW / b.w
    val sh = rawH / b.h
    var s = when (handle) {
        4, 6 -> sh
        5, 7 -> sw
        else -> if (abs(sw - 1f) >= abs(sh - 1f)) sw else sh
    }
    // Minimum size on BOTH axes at once — ratio is kept even at the floor,
    // and a drag past the anchor clamps to the minimum instead of flipping.
    val sMin = max(ED_MIN_SIZE / b.w, ED_MIN_SIZE / b.h)
    if (s < sMin) s = sMin
    val w = b.w * s
    val h = b.h * s
    // Anchor the opposite side/corner.
    val cx = b.x + b.w / 2f
    val cy = b.y + b.h / 2f
    val x = when (handle) {
        0, 2 -> (b.x + b.w) - w   // right edge anchored
        7 -> (b.x + b.w) - w      // right edge anchored
        4, 6 -> cx - w / 2f       // horizontal centre kept
        else -> b.x               // 1, 3, 5: left edge anchored
    }
    val y = when (handle) {
        0, 1 -> (b.y + b.h) - h   // bottom edge anchored
        4 -> (b.y + b.h) - h      // bottom edge anchored
        5, 7 -> cy - h / 2f       // vertical centre kept
        else -> b.y               // 2, 3, 6: top edge anchored
    }
    return EdBox(x, y, w, h)
}

/**
 * Handle hit test for the editor's drag wiring: the index of the handle
 * NEAREST to the press point (page points, square/Chebyshev distance —
 * the handles are drawn as squares), or -1 when no handle is within
 * [tol]. The previous wiring scanned every handle and let the LAST one
 * inside a fixed ±11 pt box win, so a press between two handles (small
 * object, low zoom) could silently bind the wrong one — and a press that
 * landed just outside the small box started a move instead of a resize.
 */
fun edPickHandle(handles: List<Pair<Float, Float>>, px: Float, py: Float, tol: Float): Int {
    var best = -1
    var bestD = Float.MAX_VALUE
    handles.forEachIndexed { i, hp ->
        val d = max(abs(px - hp.first), abs(py - hp.second))
        if (d <= tol && d < bestD) { bestD = d; best = i }
    }
    return best
}

/**
 * One step of a resize drag SESSION. A session is anchored at the object
 * exactly as it was when the pointer went down ([start]) and is fed the
 * pointer's TOTAL displacement from the press point — never per-event
 * deltas accumulated onto the live object. That makes the result depend
 * only on where the pointer is now: dropped, coalesced or reordered move
 * events (all normal in a browser) cannot corrupt it, every intermediate
 * box is a valid proportional resize of the same start box, and ending
 * the session (release OR cancellation) at any moment leaves the last
 * applied box in place — there is nothing to roll back.
 */
fun edResizeSession(start: EdBox, isImage: Boolean, handle: Int, totalDx: Float, totalDy: Float): EdBox =
    if (isImage) edResizeLocked(start, handle, totalDx, totalDy)
    else edResizeFree(start, handle, totalDx, totalDy)

/** Properties-panel Width edit for an image: height follows proportionally. */
fun edWithWidthLocked(b: EdBox, newW: Float): EdBox {
    val w = max(8f, newW)
    return if (b.w > 0f) EdBox(b.x, b.y, w, w * b.h / b.w) else EdBox(b.x, b.y, w, b.h)
}

/** Properties-panel Height edit for an image: width follows proportionally. */
fun edWithHeightLocked(b: EdBox, newH: Float): EdBox {
    val h = max(8f, newH)
    return if (b.h > 0f) EdBox(b.x, b.y, h * b.w / b.h, h) else EdBox(b.x, b.y, b.w, h)
}

/**
 * Starting size (w, h in page points) for a newly added image: its natural
 * aspect ratio, no wider than ~35% of the page width and no taller than
 * ~45% of the page height, so a photo lands at a handy size instead of
 * covering the page. Unknown pixel size falls back to a modest 4:3 box.
 */
fun edFitNewImage(iw: Int, ih: Int, pageW: Float, pageH: Float): Pair<Float, Float> {
    val maxW = pageW * 0.35f
    val maxH = pageH * 0.45f
    if (iw <= 0 || ih <= 0) {
        var w = min(220f, maxW)
        var h = w * 0.75f
        if (h > maxH) { h = maxH; w = h / 0.75f }
        return w to h
    }
    var w = min(iw.toFloat(), maxW)
    var h = w * ih.toFloat() / iw.toFloat()
    if (h > maxH) { h = maxH; w = h * iw.toFloat() / ih.toFloat() }
    return w to h
}

// ── Rotation / text-markup / preset-shape helpers (Smallpdf parity) ────────
// All coordinates here are DISPLAYED page points (origin top-left, y down),
// so a positive angle turns CLOCKWISE on screen — matching Compose's
// graphicsLayer rotationZ, the browser canvas rotate(), and the shell
// export's canvas helpers.

/** Rotates point (px, py) around centre (cx, cy) by [deg] degrees. */
fun edRotatePoint(cx: Float, cy: Float, px: Float, py: Float, deg: Float): Pair<Float, Float> {
    if (deg == 0f) return px to py
    val rad = deg * (PI / 180.0)
    val c = cos(rad).toFloat(); val s = sin(rad).toFloat()
    val dx = px - cx; val dy = py - cy
    return (cx + dx * c - dy * s) to (cy + dx * s + dy * c)
}

/**
 * Hit test for a possibly-rotated object: the pointer is rotated back into
 * the object's unrotated frame and tested against its box. With rotation 0
 * this is exactly the plain box test the editor always used.
 */
fun edHitTest(x: Float, y: Float, w: Float, h: Float, rotation: Float, px: Float, py: Float): Boolean {
    val (lx, ly) = edRotatePoint(x + w / 2f, y + h / 2f, px, py, -rotation)
    return lx >= x && lx <= x + w && ly >= y && ly <= y + h
}

/** Pointer angle around a centre: degrees 0..360, 0 = east, growing clockwise (y-down space). */
fun edAngleDeg(cx: Float, cy: Float, px: Float, py: Float): Float {
    var a = (atan2((py - cy).toDouble(), (px - cx).toDouble()) * 180.0 / PI).toFloat() % 360f
    if (a < 0f) a += 360f
    return a
}

/** Snaps [deg] to the nearest multiple of [step] when within [tol] degrees of it. */
fun edSnapAngle(deg: Float, step: Float = 15f, tol: Float = 4f): Float {
    var a = deg % 360f; if (a < 0f) a += 360f
    val snapped = ((a / step).roundToInt() * step) % 360f
    var diff = abs(snapped - a); if (diff > 180f) diff = 360f - diff
    return if (diff <= tol) snapped else a
}

/** Axis-aligned size of a w×h box after rotating it by [deg] degrees. */
fun edRotatedBounds(w: Float, h: Float, deg: Float): Pair<Float, Float> {
    val rad = deg * (PI / 180.0)
    val c = abs(cos(rad)).toFloat(); val s = abs(sin(rad)).toFloat()
    return (w * c + h * s) to (w * s + h * c)
}

/**
 * All selection-handle anchor points of an object, in page points:
 * indices 0..7 are the resize handles (same order as edHandles), rotated
 * with the object; index 8 is the ROTATE handle, floating [rotateGap]
 * points above the top-centre edge (also rotated with the object).
 */
fun edHandlesRotated(
    x: Float, y: Float, w: Float, h: Float, rotation: Float, rotateGap: Float = 30f
): List<Pair<Float, Float>> {
    val base = listOf(
        x to y, (x + w) to y, x to (y + h), (x + w) to (y + h),
        (x + w / 2) to y, (x + w) to (y + h / 2),
        (x + w / 2) to (y + h), x to (y + h / 2),
        (x + w / 2) to (y - rotateGap)
    )
    if (rotation == 0f) return base
    val cx = x + w / 2f; val cy = y + h / 2f
    return base.map { (px, py) -> edRotatePoint(cx, cy, px, py, rotation) }
}

/**
 * Box for a text-markup stroke (underline / strikethrough / squiggle)
 * created by dragging across a line of text: the drag's horizontal extent
 * is kept; underline and squiggle sit at the drag's BOTTOM edge,
 * strikethrough through its middle. [kind]: "underline" | "strike" | "squiggle".
 */
fun edMarkupBox(kind: String, x0: Float, y0: Float, x1: Float, y1: Float): EdBox {
    val x = min(x0, x1); val w = max(10f, abs(x1 - x0))
    val top = min(y0, y1); val bottom = max(y0, y1); val mid = (top + bottom) / 2f
    return when (kind) {
        "strike" -> EdBox(x, mid - 4f, w, 8f)
        "squiggle" -> EdBox(x, bottom - 5f, w, 10f)
        else -> EdBox(x, bottom - 4f, w, 8f) // underline
    }
}

/**
 * Relative points (0..1 in the box) of a squiggly underline: a smooth sine
 * wave, [cycles] full waves across the box, sampled finely enough to look
 * hand-drawn when stroked segment by segment.
 */
fun edSquigglePoints(cycles: Float = 3f, steps: Int = 36): List<Pair<Float, Float>> {
    if (steps < 2) return listOf(0f to 0.5f, 1f to 0.5f)
    return (0..steps).map { i ->
        val fx = i.toFloat() / steps
        val fy = (0.5 + 0.30 * sin(fx * cycles * 2.0 * PI)).toFloat()
        fx to fy
    }
}

/** Checkmark polyline (relative 0..1 in its box). */
fun edCheckPoints(): List<Pair<Float, Float>> =
    listOf(0.08f to 0.58f, 0.36f to 0.88f, 0.94f to 0.12f)

/** Cross (✕): two strokes joined by the (-1,-1) pen-up separator. */
fun edCrossPoints(): List<Pair<Float, Float>> = listOf(
    0.12f to 0.12f, 0.88f to 0.88f, -1f to -1f, 0.88f to 0.12f, 0.12f to 0.88f
)

/** Arrow along the box diagonal (same direction as the Line tool) with a two-stroke head. */
fun edArrowPoints(): List<Pair<Float, Float>> = listOf(
    0.06f to 0.06f, 0.94f to 0.94f, -1f to -1f,
    0.94f to 0.94f, 0.66f to 0.90f, -1f to -1f,
    0.94f to 0.94f, 0.90f to 0.66f
)
