package kr.dot.parkingfloor

import kotlin.math.floor
import kotlin.math.pow

const val FLOOR_HEIGHT_M = 3.3

/** Floor index: 1F = 0, 2F = 1, B1 = -1, B2 = -2. There is no gap between B1 and 1F. */
object FloorMath {
    val FLOORS: List<Int> = (-6..-1).toList() + (0..69).toList()

    /** Height of pressure p above reference p0, in metres. Lower pressure gives positive height. */
    fun heightM(p: Double, p0: Double): Double = 44330.0 * (1.0 - (p / p0).pow(1.0 / 5.255))

    fun estimate(baseIndex: Int, deltaM: Double, floorM: Double = FLOOR_HEIGHT_M): Int =
        baseIndex + floor(deltaM / floorM + 0.5).toInt()

    fun labelOf(index: Int): String = if (index >= 0) "${index + 1}층" else "B${-index}"

    fun indexOf(label: String): Int =
        if (label.startsWith("B")) -label.drop(1).toInt() else label.removeSuffix("층").toInt() - 1

    fun median(values: List<Double>): Double {
        require(values.isNotEmpty())
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    fun csv(text: String): String =
        if (text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + text.replace("\"", "\"\"") + "\"" else text
}
