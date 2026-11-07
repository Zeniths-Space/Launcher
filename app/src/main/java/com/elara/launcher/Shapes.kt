package com.elara.launcher

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Scalloped "cookie" badge (M3 Expressive): 8 lobes, valleys at 88% of the radius, as in Zenboard and Kontract. */
val ScallopShape = polarShape(lobes = 8, depth = 0.12f)

/** A rounded star with [lobes] bumps whose valleys sit at (1 - [depth]) of the radius, without a shapes library. */
fun polarShape(lobes: Int, depth: Float, rotation: Double = 0.0) = GenericShape { size, _ ->
    val r = min(size.width, size.height) / 2
    val cx = size.width / 2
    val cy = size.height / 2
    val steps = 160
    for (i in 0..steps) {
        val t = 2 * PI * i / steps
        val radius = r * (1 - depth * (1 - cos(lobes * (t - rotation))) / 2).toFloat()
        val x = cx + radius * cos(t).toFloat()
        val y = cy + radius * sin(t).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** Grouped-list corner: large outer, small inner. */
fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val top = if (index == 0) 28.dp else 8.dp
    val bottom = if (index == count - 1) 28.dp else 8.dp
    return RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom)
}

/** A polygon of [sides] with corners rounded by [rounding] (0 sharp, 0.5 round) of each side, first corner at [rotation]. */
fun roundedPolygon(sides: Int, rounding: Float, rotation: Double = -PI / 2, scale: Float = 1f, shiftY: Float = 0f) = GenericShape { size, _ ->
    val r = min(size.width, size.height) / 2 * scale
    val cx = size.width / 2
    val cy = size.height / 2 + shiftY * size.height
    val pts = List(sides) { i ->
        val t = rotation + 2 * PI * i / sides
        Pair(cx + r * cos(t).toFloat(), cy + r * sin(t).toFloat())
    }
    fun lerp(a: Pair<Float, Float>, b: Pair<Float, Float>, f: Float) = Pair(a.first + (b.first - a.first) * f, a.second + (b.second - a.second) * f)
    pts.forEachIndexed { i, cur ->
        val start = lerp(cur, pts[(i + sides - 1) % sides], rounding)
        val end = lerp(cur, pts[(i + 1) % sides], rounding)
        if (i == 0) moveTo(start.first, start.second) else lineTo(start.first, start.second)
        quadraticTo(cur.first, cur.second, end.first, end.second)
    }
    close()
}

/** A superellipse |x|⁴ + |y|⁴ = 1: a square with smooth, continuous corners. */
val SquircleShape = GenericShape { size, _ ->
    val rx = size.width / 2
    val ry = size.height / 2
    val steps = 120
    for (i in 0..steps) {
        val t = 2 * PI * i / steps
        val c = cos(t)
        val s = sin(t)
        val x = rx + rx * (Math.signum(c) * Math.sqrt(Math.abs(c))).toFloat()
        val y = ry + ry * (Math.signum(s) * Math.sqrt(Math.abs(s))).toFloat()
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

val HeartShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(0.5f * w, 0.95f * h)
    cubicTo(0.2f * w, 0.75f * h, 0f, 0.55f * h, 0f, 0.32f * h)
    cubicTo(0f, 0.14f * h, 0.14f * w, 0.02f * h, 0.3f * w, 0.02f * h)
    cubicTo(0.4f * w, 0.02f * h, 0.47f * w, 0.07f * h, 0.5f * w, 0.16f * h)
    cubicTo(0.53f * w, 0.07f * h, 0.6f * w, 0.02f * h, 0.7f * w, 0.02f * h)
    cubicTo(0.86f * w, 0.02f * h, w, 0.14f * h, w, 0.32f * h)
    cubicTo(w, 0.55f * h, 0.8f * w, 0.75f * h, 0.5f * w, 0.95f * h)
    close()
}

/** App icon outlines. [System] is the phone's own; the rest mask every icon to the same outline. */
enum class IconShape(val label: Int, val shape: Shape?) {
    System(R.string.shape_system, null),
    Circle(R.string.shape_circle, CircleShape),
    Squircle(R.string.shape_squircle, SquircleShape),
    RoundedSquare(R.string.shape_rounded_square, RoundedCornerShape(24)),
    Square(R.string.shape_square, RoundedCornerShape(6)),
    Teardrop(R.string.shape_teardrop, RoundedCornerShape(50, 50, 12, 50)),
    Arch(R.string.shape_arch, RoundedCornerShape(50, 50, 14, 14)),
    Hexagon(R.string.shape_hexagon, roundedPolygon(6, 0.18f, scale = 1.04f)),
    Octagon(R.string.shape_octagon, roundedPolygon(8, 0.16f, rotation = PI / 8, scale = 1.06f)),
    RiceBall(R.string.shape_rice_ball, roundedPolygon(3, 0.42f, scale = 1.36f, shiftY = 0.1f)),
    Heart(R.string.shape_heart, HeartShape),
    Cookie(R.string.shape_cookie, polarShape(lobes = 9, depth = 0.12f)),
    Flower(R.string.shape_flower, polarShape(lobes = 6, depth = 0.26f, rotation = -PI / 2)),
    Clover(R.string.shape_clover, polarShape(lobes = 4, depth = 0.32f, rotation = PI / 4)),
    ;

    companion object {
        fun parse(name: String?) = entries.firstOrNull { it.name == name } ?: System
    }
}
