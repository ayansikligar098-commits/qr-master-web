package qrmaster.web.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

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
