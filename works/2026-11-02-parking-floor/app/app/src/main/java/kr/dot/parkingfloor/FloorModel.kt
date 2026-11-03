package kr.dot.parkingfloor

import kotlin.math.abs

/**
 * Floor from height change at one place. Depth below the entrance (or height above it) of level n is
 * first + (n - 1) * step for n >= 1, and 0 at the entrance level (1F). Each place learns first and step from
 * the floors the user confirmed; with nothing learned the defaults apply.
 *
 * Floor index as everywhere in the app: 1F = 0, 2F = 1, B1 = -1, B2 = -2.
 */
object FloorModel {
    const val DEFAULT_FIRST = 4.0
    const val DEFAULT_STEP = 3.3
    private const val MIN_STEP = 2.4
    private const val MAX_STEP = 8.0
    private const val MAX_LEVELS = 70

    /** One confirmed park: measured height change (m, negative = below the entrance) and the true floor index. */
    data class Pair(val deltaM: Double, val floor: Int)

    data class Shape(val first: Double, val step: Double)

    data class Estimate(val floor: Int, val alt: Int, val confidence: Double)

    /** Learns the shape for one direction (below or above the entrance) from that direction's pairs. */
    fun shape(pairs: List<Pair>, below: Boolean): Shape {
        val usable = pairs.mapNotNull { p ->
            val levels = if (below) -p.floor else p.floor
            val dist = if (below) -p.deltaM else p.deltaM
            if (levels >= 1 && dist > 0) levels to dist else null
        }
        if (usable.isEmpty()) return Shape(DEFAULT_FIRST, DEFAULT_STEP)
        val distinct = usable.map { it.first }.toSet()
        if (distinct.size == 1) {
            // one level known: scale the default shape so that level lands where it was measured
            val n = usable[0].first
            val mean = usable.map { it.second }.average()
            val scale = mean / position(Shape(DEFAULT_FIRST, DEFAULT_STEP), n)
            val step = (DEFAULT_STEP * scale).coerceIn(MIN_STEP, MAX_STEP)
            return Shape(mean - (n - 1) * step, step)
        }
        // two or more levels: least squares line dist = first + (n - 1) * step
        val xs = usable.map { (it.first - 1).toDouble() }
        val ys = usable.map { it.second }
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        val sxy = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val step = (sxy / sxx).coerceIn(MIN_STEP, MAX_STEP)
        return Shape((my - step * mx).coerceAtLeast(1.5), step)
    }

    fun position(shape: Shape, levels: Int): Double = if (levels <= 0) 0.0 else shape.first + (levels - 1) * shape.step

    /** Best floor and the runner-up for a height change, using the pairs learned at this place. */
    fun estimate(deltaM: Double, pairs: List<Pair>): Estimate {
        val below = deltaM < 0
        val shape = shape(pairs.filter { if (below) it.floor < 0 else it.floor > 0 }, below)
        val dist = abs(deltaM)
        val ranked = (0..MAX_LEVELS).sortedBy { abs(position(shape, it) - dist) }
        val best = ranked[0]
        val second = ranked[1]
        val gap = abs(position(shape, second) - position(shape, best)).coerceAtLeast(0.1)
        val confidence = (1.0 - abs(position(shape, best) - dist) / gap * 2).coerceIn(0.0, 1.0)
        val toIndex = { levels: Int -> if (below) -levels else levels }
        return Estimate(toIndex(best), toIndex(second), confidence)
    }
}
