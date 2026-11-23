package qrmaster.web.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class IconKind {
    Logo, Home, Grid, Qr, Scan, Barcode, Pdf, Image, Wallet, Spin, Calendar, Gift,
    Task, Users, Play, Crown, Clock, Bell, Gear, Help, Info, Search, Sun, Moon,
    Menu, Download, Right, Back, Star, Zap, Shield, Merge, Lock, Logout, Eye,
    EyeOff, Check, Edit, Compress, FileText, Music, Share, Printer, Percent, Calc, Swap
}

@Composable
fun QrIcon(kind: IconKind, tint: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier = modifier.size(size)) {
        drawIcon(kind, tint)
    }
}

private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float, w: Float) {
    val s = size.width
    drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), strokeWidth = w * s, cap = androidx.compose.ui.graphics.StrokeCap.Round)
}

private fun DrawScope.rrect(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float, sw: Float, fill: Boolean = false) {
    val s = size.width
    if (fill) {
        drawRoundRect(color, Offset(x * s, y * s), Size(w * s, h * s), CornerRadius(r * s, r * s), style = Fill)
    } else {
        drawRoundRect(color, Offset(x * s, y * s), Size(w * s, h * s), CornerRadius(r * s, r * s), style = Stroke(width = sw * s))
    }
}

private fun DrawScope.circle(color: Color, cx: Float, cy: Float, rad: Float, sw: Float, fill: Boolean = false) {
    val s = size.width
    if (fill) drawCircle(color, rad * s, Offset(cx * s, cy * s), style = Fill)
    else drawCircle(color, rad * s, Offset(cx * s, cy * s), style = Stroke(width = sw * s))
}

private fun DrawScope.poly(color: Color, points: List<Pair<Float, Float>>, fill: Boolean = true) {
    val s = size.width
    val path = Path()
    points.forEachIndexed { i, (x, y) ->
        if (i == 0) path.moveTo(x * s, y * s) else path.lineTo(x * s, y * s)
    }
    path.close()
    drawPath(path, color, style = if (fill) Fill else Stroke(width = 0.07f * s))
}

private fun DrawScope.drawIcon(kind: IconKind, c: Color) {
    val w = 0.075f // stroke width fraction
    when (kind) {
        IconKind.Logo -> {
            rrect(c, 0.08f, 0.08f, 0.34f, 0.34f, 0.08f, w)
            rrect(c, 0.58f, 0.08f, 0.34f, 0.34f, 0.08f, w)
            rrect(c, 0.08f, 0.58f, 0.34f, 0.34f, 0.08f, w)
            rrect(c, 0.19f, 0.19f, 0.12f, 0.12f, 0.03f, w, fill = true)
            rrect(c, 0.69f, 0.19f, 0.12f, 0.12f, 0.03f, w, fill = true)
            rrect(c, 0.19f, 0.69f, 0.12f, 0.12f, 0.03f, w, fill = true)
            rrect(c, 0.58f, 0.58f, 0.15f, 0.15f, 0.03f, w, fill = true)
            rrect(c, 0.81f, 0.58f, 0.11f, 0.11f, 0.02f, w, fill = true)
            rrect(c, 0.58f, 0.81f, 0.11f, 0.11f, 0.02f, w, fill = true)
            rrect(c, 0.81f, 0.81f, 0.11f, 0.11f, 0.02f, w, fill = true)
        }
        IconKind.Home -> {
            poly(c, listOf(0.5f to 0.1f, 0.92f to 0.46f, 0.82f to 0.46f, 0.82f to 0.9f, 0.6f to 0.9f, 0.6f to 0.66f, 0.4f to 0.66f, 0.4f to 0.9f, 0.18f to 0.9f, 0.18f to 0.46f, 0.08f to 0.46f), fill = true)
        }
        IconKind.Grid -> {
            rrect(c, 0.1f, 0.1f, 0.36f, 0.36f, 0.07f, w, fill = true)
            rrect(c, 0.54f, 0.1f, 0.36f, 0.36f, 0.07f, w, fill = true)
            rrect(c, 0.1f, 0.54f, 0.36f, 0.36f, 0.07f, w, fill = true)
            rrect(c, 0.54f, 0.54f, 0.36f, 0.36f, 0.07f, w, fill = true)
        }
        IconKind.Qr -> {
            rrect(c, 0.08f, 0.08f, 0.34f, 0.34f, 0.06f, w)
            rrect(c, 0.58f, 0.08f, 0.34f, 0.34f, 0.06f, w)
            rrect(c, 0.08f, 0.58f, 0.34f, 0.34f, 0.06f, w)
            rrect(c, 0.19f, 0.19f, 0.12f, 0.12f, 0.02f, w, fill = true)
            rrect(c, 0.69f, 0.19f, 0.12f, 0.12f, 0.02f, w, fill = true)
            rrect(c, 0.19f, 0.69f, 0.12f, 0.12f, 0.02f, w, fill = true)
            rrect(c, 0.58f, 0.58f, 0.14f, 0.14f, 0.02f, w, fill = true)
            rrect(c, 0.8f, 0.8f, 0.12f, 0.12f, 0.02f, w, fill = true)
        }
        IconKind.Scan -> {
            line(c, 0.1f, 0.3f, 0.1f, 0.12f, w); line(c, 0.1f, 0.1f, 0.3f, 0.1f, w)
            line(c, 0.7f, 0.1f, 0.9f, 0.1f, w); line(c, 0.9f, 0.1f, 0.9f, 0.3f, w)
            line(c, 0.9f, 0.7f, 0.9f, 0.9f, w); line(c, 0.9f, 0.9f, 0.7f, 0.9f, w)
            line(c, 0.3f, 0.9f, 0.1f, 0.9f, w); line(c, 0.1f, 0.9f, 0.1f, 0.7f, w)
            line(c, 0.14f, 0.5f, 0.86f, 0.5f, w)
        }
        IconKind.Barcode -> {
            val xs = listOf(0.12f, 0.22f, 0.3f, 0.42f, 0.5f, 0.56f, 0.66f, 0.78f, 0.86f)
            val ws = listOf(0.05f, 0.03f, 0.07f, 0.03f, 0.05f, 0.03f, 0.07f, 0.03f, 0.05f)
            xs.forEachIndexed { i, x -> rrect(c, x, 0.14f, ws[i], 0.72f, 0.01f, w, fill = true) }
        }
        IconKind.Pdf, IconKind.FileText -> {
            rrect(c, 0.2f, 0.08f, 0.6f, 0.84f, 0.06f, w)
            line(c, 0.34f, 0.36f, 0.66f, 0.36f, w)
            line(c, 0.34f, 0.52f, 0.66f, 0.52f, w)
            line(c, 0.34f, 0.68f, 0.54f, 0.68f, w)
        }
        IconKind.Image -> {
            rrect(c, 0.08f, 0.12f, 0.84f, 0.76f, 0.07f, w)
            circle(c, 0.33f, 0.36f, 0.08f, w, fill = true)
            poly(c, listOf(0.12f to 0.84f, 0.4f to 0.56f, 0.55f to 0.7f, 0.68f to 0.58f, 0.88f to 0.84f), fill = true)
        }
        IconKind.Wallet -> {
            rrect(c, 0.06f, 0.18f, 0.88f, 0.64f, 0.1f, w)
            line(c, 0.06f, 0.36f, 0.94f, 0.36f, w)
            circle(c, 0.76f, 0.6f, 0.05f, w, fill = true)
        }
        IconKind.Spin -> {
            val s = size.width
            drawArc(c, startAngle = -60f, sweepAngle = 300f, useCenter = false,
                topLeft = Offset(0.14f * s, 0.14f * s), size = Size(0.72f * s, 0.72f * s), style = Stroke(width = w * s))
            poly(c, listOf(0.86f to 0.08f, 0.98f to 0.3f, 0.68f to 0.3f), fill = true)
            circle(c, 0.5f, 0.5f, 0.07f, w, fill = true)
        }
        IconKind.Calendar -> {
            rrect(c, 0.1f, 0.16f, 0.8f, 0.74f, 0.07f, w)
            line(c, 0.1f, 0.34f, 0.9f, 0.34f, w)
            line(c, 0.3f, 0.08f, 0.3f, 0.24f, w); line(c, 0.7f, 0.08f, 0.7f, 0.24f, w)
            line(c, 0.34f, 0.62f, 0.46f, 0.74f, w); line(c, 0.46f, 0.74f, 0.68f, 0.5f, w)
        }
        IconKind.Gift -> {
            rrect(c, 0.12f, 0.36f, 0.76f, 0.56f, 0.05f, w)
            line(c, 0.08f, 0.36f, 0.92f, 0.36f, w)
            line(c, 0.5f, 0.36f, 0.5f, 0.92f, w)
            circle(c, 0.36f, 0.22f, 0.11f, w)
            circle(c, 0.64f, 0.22f, 0.11f, w)
        }
        IconKind.Task -> {
            rrect(c, 0.18f, 0.1f, 0.64f, 0.82f, 0.07f, w)
            line(c, 0.33f, 0.38f, 0.44f, 0.49f, w); line(c, 0.44f, 0.49f, 0.66f, 0.27f, w)
            line(c, 0.33f, 0.68f, 0.44f, 0.79f, w); line(c, 0.44f, 0.79f, 0.66f, 0.57f, w)
        }
        IconKind.Users -> {
            circle(c, 0.38f, 0.34f, 0.15f, w)
            val s = size.width
            drawArc(c, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(0.1f * s, 0.42f * s), size = Size(0.56f * s, 0.56f * s), style = Stroke(width = w * s))
            circle(c, 0.72f, 0.38f, 0.11f, w)
        }
        IconKind.Play -> {
            circle(c, 0.5f, 0.5f, 0.4f, w)
            poly(c, listOf(0.42f to 0.34f, 0.68f to 0.5f, 0.42f to 0.66f), fill = true)
        }
        IconKind.Crown -> {
            poly(c, listOf(0.08f to 0.78f, 0.14f to 0.34f, 0.34f to 0.56f, 0.5f to 0.24f, 0.66f to 0.56f, 0.86f to 0.34f, 0.92f to 0.78f), fill = true)
        }
        IconKind.Clock -> {
            circle(c, 0.5f, 0.5f, 0.4f, w)
            line(c, 0.5f, 0.5f, 0.5f, 0.3f, w)
            line(c, 0.5f, 0.5f, 0.68f, 0.58f, w)
        }
        IconKind.Bell -> {
            val s = size.width
            drawArc(c, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(0.24f * s, 0.12f * s), size = Size(0.52f * s, 0.52f * s), style = Stroke(width = w * s))
            line(c, 0.24f, 0.4f, 0.2f, 0.74f, w); line(c, 0.76f, 0.4f, 0.8f, 0.74f, w)
            line(c, 0.14f, 0.74f, 0.86f, 0.74f, w)
            circle(c, 0.5f, 0.86f, 0.06f, w, fill = true)
        }
        IconKind.Gear -> {
            circle(c, 0.5f, 0.5f, 0.17f, w)
            for (i in 0 until 8) {
                val a = (PI * 2 * i / 8).toFloat()
                line(c, 0.5f + 0.26f * cos(a), 0.5f + 0.26f * sin(a), 0.5f + 0.4f * cos(a), 0.5f + 0.4f * sin(a), w + 0.02f)
            }
        }
        IconKind.Help -> {
            circle(c, 0.5f, 0.5f, 0.4f, w)
            val s = size.width
            drawArc(c, startAngle = 180f, sweepAngle = 200f, useCenter = false,
                topLeft = Offset(0.36f * s, 0.28f * s), size = Size(0.28f * s, 0.28f * s), style = Stroke(width = w * s))
            line(c, 0.5f, 0.56f, 0.5f, 0.62f, w)
            circle(c, 0.5f, 0.74f, 0.035f, w, fill = true)
        }
        IconKind.Info -> {
            circle(c, 0.5f, 0.5f, 0.4f, w)
            circle(c, 0.5f, 0.32f, 0.04f, w, fill = true)
            line(c, 0.5f, 0.46f, 0.5f, 0.7f, w)
        }
        IconKind.Search -> {
            circle(c, 0.44f, 0.44f, 0.3f, w)
            line(c, 0.66f, 0.66f, 0.9f, 0.9f, w + 0.015f)
        }
        IconKind.Sun -> {
            circle(c, 0.5f, 0.5f, 0.2f, w)
            for (i in 0 until 8) {
                val a = (PI * 2 * i / 8).toFloat()
                line(c, 0.5f + 0.3f * cos(a), 0.5f + 0.3f * sin(a), 0.5f + 0.42f * cos(a), 0.5f + 0.42f * sin(a), w)
            }
        }
        IconKind.Moon -> {
            val s = size.width
            val path = Path()
            path.addArc(Rect(0.14f * s, 0.1f * s, 0.86f * s, 0.9f * s), 100f, 320f)
            path.addArc(Rect(0.38f * s, 0.02f * s, 1.0f * s, 0.82f * s), 280f, -260f)
            path.fillType = PathFillType.EvenOdd
            drawPath(path, c, style = Fill)
        }
        IconKind.Menu -> {
            line(c, 0.14f, 0.3f, 0.86f, 0.3f, w)
            line(c, 0.14f, 0.5f, 0.86f, 0.5f, w)
            line(c, 0.14f, 0.7f, 0.86f, 0.7f, w)
        }
        IconKind.Download -> {
            line(c, 0.5f, 0.12f, 0.5f, 0.62f, w)
            line(c, 0.3f, 0.44f, 0.5f, 0.64f, w); line(c, 0.7f, 0.44f, 0.5f, 0.64f, w)
            line(c, 0.16f, 0.86f, 0.84f, 0.86f, w)
        }
        IconKind.Right -> {
            line(c, 0.36f, 0.24f, 0.66f, 0.5f, w)
            line(c, 0.66f, 0.5f, 0.36f, 0.76f, w)
        }
        IconKind.Back -> {
            line(c, 0.64f, 0.24f, 0.34f, 0.5f, w)
            line(c, 0.34f, 0.5f, 0.64f, 0.76f, w)
        }
        IconKind.Star -> {
            val pts = mutableListOf<Pair<Float, Float>>()
            for (i in 0 until 10) {
                val a = (PI / 2 + i * PI / 5).toFloat()
                val r = if (i % 2 == 0) 0.42f else 0.18f
                pts.add(0.5f + r * cos(a) to 0.5f - r * sin(a))
            }
            poly(c, pts, fill = true)
        }
        IconKind.Zap -> {
            poly(c, listOf(0.54f to 0.06f, 0.22f to 0.56f, 0.44f to 0.56f, 0.38f to 0.94f, 0.76f to 0.4f, 0.53f to 0.4f), fill = true)
        }
        IconKind.Shield -> {
            val s = size.width
            val path = Path()
            path.moveTo(0.5f * s, 0.06f * s)
            path.lineTo(0.86f * s, 0.18f * s)
            path.lineTo(0.86f * s, 0.48f * s)
            path.cubicTo(0.86f * s, 0.74f * s, 0.68f * s, 0.88f * s, 0.5f * s, 0.95f * s)
            path.cubicTo(0.32f * s, 0.88f * s, 0.14f * s, 0.74f * s, 0.14f * s, 0.48f * s)
            path.lineTo(0.14f * s, 0.18f * s)
            path.close()
            drawPath(path, c, style = Stroke(width = w * s))
            line(c, 0.36f, 0.5f, 0.47f, 0.62f, w); line(c, 0.47f, 0.62f, 0.66f, 0.38f, w)
        }
        IconKind.Merge -> {
            line(c, 0.3f, 0.1f, 0.3f, 0.55f, w); line(c, 0.7f, 0.1f, 0.7f, 0.55f, w)
            line(c, 0.3f, 0.55f, 0.5f, 0.78f, w); line(c, 0.7f, 0.55f, 0.5f, 0.78f, w)
            line(c, 0.5f, 0.78f, 0.5f, 0.92f, w)
            poly(c, listOf(0.22f to 0.2f, 0.38f to 0.2f, 0.3f to 0.06f), fill = true)
            poly(c, listOf(0.62f to 0.2f, 0.78f to 0.2f, 0.7f to 0.06f), fill = true)
        }
        IconKind.Lock -> {
            rrect(c, 0.18f, 0.42f, 0.64f, 0.5f, 0.07f, w)
            val s = size.width
            drawArc(c, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(0.28f * s, 0.12f * s), size = Size(0.44f * s, 0.6f * s), style = Stroke(width = w * s))
            circle(c, 0.5f, 0.64f, 0.06f, w, fill = true)
        }
        IconKind.Logout -> {
            line(c, 0.55f, 0.14f, 0.3f, 0.14f, w); line(c, 0.3f, 0.14f, 0.3f, 0.86f, w); line(c, 0.3f, 0.86f, 0.55f, 0.86f, w)
            line(c, 0.42f, 0.5f, 0.84f, 0.5f, w)
            line(c, 0.68f, 0.34f, 0.86f, 0.5f, w); line(c, 0.68f, 0.66f, 0.86f, 0.5f, w)
        }
        IconKind.Eye -> {
            val s = size.width
            val path = Path()
            path.moveTo(0.06f * s, 0.5f * s)
            path.cubicTo(0.28f * s, 0.2f * s, 0.72f * s, 0.2f * s, 0.94f * s, 0.5f * s)
            path.cubicTo(0.72f * s, 0.8f * s, 0.28f * s, 0.8f * s, 0.06f * s, 0.5f * s)
            drawPath(path, c, style = Stroke(width = w * s))
            circle(c, 0.5f, 0.5f, 0.13f, w)
        }
        IconKind.EyeOff -> {
            val s = size.width
            val path = Path()
            path.moveTo(0.06f * s, 0.5f * s)
            path.cubicTo(0.28f * s, 0.2f * s, 0.72f * s, 0.2f * s, 0.94f * s, 0.5f * s)
            path.cubicTo(0.72f * s, 0.8f * s, 0.28f * s, 0.8f * s, 0.06f * s, 0.5f * s)
            drawPath(path, c, style = Stroke(width = w * s))
            line(c, 0.16f, 0.84f, 0.84f, 0.16f, w)
        }
        IconKind.Check -> {
            line(c, 0.2f, 0.54f, 0.42f, 0.76f, w + 0.02f)
            line(c, 0.42f, 0.76f, 0.82f, 0.28f, w + 0.02f)
        }
        IconKind.Edit -> {
            poly(c, listOf(0.68f to 0.12f, 0.88f to 0.32f, 0.4f to 0.8f, 0.2f to 0.86f, 0.26f to 0.64f), fill = true)
            line(c, 0.14f, 0.92f, 0.88f, 0.92f, w)
        }
        IconKind.Compress -> {
            line(c, 0.36f, 0.14f, 0.36f, 0.4f, w); line(c, 0.22f, 0.3f, 0.36f, 0.44f, w); line(c, 0.5f, 0.3f, 0.36f, 0.44f, w)
            line(c, 0.64f, 0.14f, 0.64f, 0.4f, w); line(c, 0.5f, 0.3f, 0.64f, 0.44f, w); line(c, 0.78f, 0.3f, 0.64f, 0.44f, w)
            line(c, 0.36f, 0.86f, 0.36f, 0.6f, w); line(c, 0.22f, 0.7f, 0.36f, 0.56f, w); line(c, 0.5f, 0.7f, 0.36f, 0.56f, w)
            line(c, 0.64f, 0.86f, 0.64f, 0.6f, w); line(c, 0.5f, 0.7f, 0.64f, 0.56f, w); line(c, 0.78f, 0.7f, 0.64f, 0.56f, w)
        }
        IconKind.Music -> {
            circle(c, 0.33f, 0.74f, 0.11f, w, fill = true)
            circle(c, 0.73f, 0.66f, 0.11f, w, fill = true)
            line(c, 0.44f, 0.74f, 0.44f, 0.26f, w)
            line(c, 0.84f, 0.66f, 0.84f, 0.18f, w)
            line(c, 0.44f, 0.26f, 0.84f, 0.18f, w + 0.05f)
        }
        IconKind.Share -> {
            circle(c, 0.24f, 0.5f, 0.1f, w)
            circle(c, 0.76f, 0.2f, 0.1f, w)
            circle(c, 0.76f, 0.8f, 0.1f, w)
            line(c, 0.32f, 0.45f, 0.67f, 0.25f, w)
            line(c, 0.32f, 0.55f, 0.67f, 0.75f, w)
        }
        IconKind.Printer -> {
            rrect(c, 0.3f, 0.1f, 0.4f, 0.26f, 0.02f, w)
            rrect(c, 0.16f, 0.32f, 0.68f, 0.36f, 0.05f, w)
            rrect(c, 0.3f, 0.56f, 0.4f, 0.32f, 0.02f, w)
            circle(c, 0.74f, 0.44f, 0.03f, w, fill = true)
        }
        IconKind.Percent -> {
            line(c, 0.8f, 0.14f, 0.2f, 0.86f, w + 0.02f)
            circle(c, 0.3f, 0.28f, 0.12f, w)
            circle(c, 0.7f, 0.72f, 0.12f, w)
        }
        IconKind.Calc -> {
            rrect(c, 0.2f, 0.08f, 0.6f, 0.84f, 0.06f, w)
            line(c, 0.32f, 0.24f, 0.68f, 0.24f, w + 0.03f)
            circle(c, 0.36f, 0.44f, 0.035f, w, fill = true); circle(c, 0.5f, 0.44f, 0.035f, w, fill = true); circle(c, 0.64f, 0.44f, 0.035f, w, fill = true)
            circle(c, 0.36f, 0.6f, 0.035f, w, fill = true); circle(c, 0.5f, 0.6f, 0.035f, w, fill = true); circle(c, 0.64f, 0.6f, 0.035f, w, fill = true)
            circle(c, 0.36f, 0.76f, 0.035f, w, fill = true); circle(c, 0.5f, 0.76f, 0.035f, w, fill = true); circle(c, 0.64f, 0.76f, 0.035f, w, fill = true)
        }
        IconKind.Swap -> {
            line(c, 0.16f, 0.34f, 0.76f, 0.34f, w)
            line(c, 0.62f, 0.2f, 0.78f, 0.34f, w); line(c, 0.62f, 0.48f, 0.78f, 0.34f, w)
            line(c, 0.84f, 0.66f, 0.24f, 0.66f, w)
            line(c, 0.38f, 0.52f, 0.22f, 0.66f, w); line(c, 0.38f, 0.8f, 0.22f, 0.66f, w)
        }
    }
}
