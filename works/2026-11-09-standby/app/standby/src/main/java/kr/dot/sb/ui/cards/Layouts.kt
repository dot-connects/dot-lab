package kr.dot.sb.ui.cards

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** spec-r9 §1: which layout a card uses for a slot of w × h dp. Pure, so the size thresholds are testable. */

enum class MediaShape { TALL, COVER }

fun mediaShape(w: Float, h: Float): MediaShape = if (w / h <= 0.82f) MediaShape.TALL else MediaShape.COVER

enum class NotifTier { A, B, C, D, E, S }

fun notifTier(w: Float, h: Float): NotifTier = when {
    w < 150f || (w < 230f && h < 170f) -> NotifTier.A
    w < 230f -> NotifTier.B
    h < 170f -> NotifTier.S
    w < 300f -> NotifTier.C
    w < 600f -> NotifTier.D
    else -> NotifTier.E
}

enum class WeatherShape { TINY, SHORT_WIDE, SMALL, WIDE, STACK }

fun weatherShape(w: Float, h: Float): WeatherShape = when {
    w < 150f || h < 130f -> WeatherShape.TINY
    h < 176f -> WeatherShape.SHORT_WIDE
    min(w, h) < 176f -> WeatherShape.SMALL
    w / h >= 1.3f -> WeatherShape.WIDE
    else -> WeatherShape.STACK
}

enum class CalendarShape { TINY, SHORT_WIDE, NARROW, WIDE_MONTH, MONTH }

fun calendarShape(w: Float, h: Float): CalendarShape = when {
    w < 150f || h < 130f -> CalendarShape.TINY
    h < 240f -> CalendarShape.SHORT_WIDE
    w < 260f -> CalendarShape.NARROW
    w / h >= 1.25f -> CalendarShape.WIDE_MONTH
    else -> CalendarShape.MONTH
}

data class TileGrid(val cols: Int, val rows: Int, val tileW: Float, val tileH: Float)

/** Columns 2..6 that give the largest min(tileW, tileH) for [n] tiles; ties keep fewer columns. */
fun tileGrid(n: Int, w: Float, h: Float, gap: Float = 8f): TileGrid = (2..6).map { c ->
    val r = (n.coerceAtLeast(1) + c - 1) / c
    TileGrid(c, r, (w - gap * (c - 1)) / c, (h - gap * (r - 1)) / r)
}.maxWith(compareBy<TileGrid> { min(it.tileW, it.tileH) }.thenByDescending { it.cols })

/**
 * Where a ray from the centre at [deg] (0 = 12 o'clock, clockwise) leaves the rectangle inset by [inset].
 * Rect analog dials place numerals here, so 3/6/9/12 sit on the axes.
 */
fun rectPoint(w: Float, h: Float, deg: Float, inset: Float): Pair<Float, Float> {
    val rad = Math.toRadians(deg.toDouble())
    val dx = sin(rad).toFloat()
    val dy = -cos(rad).toFloat()
    val a = w / 2 - inset
    val b = h / 2 - inset
    val t = min(if (abs(dx) < 1e-6f) Float.MAX_VALUE else a / abs(dx), if (abs(dy) < 1e-6f) Float.MAX_VALUE else b / abs(dy))
    return (w / 2 + dx * t) to (h / 2 + dy * t)
}
