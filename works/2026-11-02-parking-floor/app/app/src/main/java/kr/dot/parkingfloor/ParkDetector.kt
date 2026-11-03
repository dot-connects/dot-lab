package kr.dot.parkingfloor

/**
 * Height change between the garage entrance and the parked spot, from the barometer trace of one drive.
 *
 * Park pressure: median of the last settled stretch before the car Bluetooth dropped (door slams and the last
 * seconds are skipped). Entrance: the drive is searched back from the park over a window that starts at the
 * last good GPS fix (the car was still outside then) or, without one, a few minutes before the park. Inside
 * the window the entrance is the level furthest from the park (the street above an underground garage, or
 * below a parking tower), taken as a median around that point.
 */
object ParkDetector {
    const val PARK_SKIP_S = 3.0
    const val PARK_WINDOW_S = 12.0
    const val SEARCH_S = 240.0
    const val GPS_MARGIN_S = 30.0
    const val REF_HALF_S = 8.0
    const val SMOOTH_S = 9.0
    const val FRESH_S = 20.0

    data class Sample(val tS: Double, val hpa: Double)

    data class Result(val deltaM: Double, val parkHpa: Double, val refHpa: Double, val refTS: Double)

    /**
     * @param samples barometer samples, any order, seconds on one clock
     * @param endS time the car Bluetooth dropped
     * @param lastOutdoorFixS time of the last GPS fix good enough to mean "outside", or null
     */
    fun detect(samples: List<Sample>, endS: Double, lastOutdoorFixS: Double? = null): Result? {
        val sorted = samples.filter { it.tS <= endS }.sortedBy { it.tS }
        if (sorted.size < 10) return null
        // no fresh samples at the park means the sensor stalled: no answer beats a confident 1F
        if (sorted.last().tS < endS - FRESH_S) return null
        val parkWin = sorted.filter { it.tS in (endS - PARK_SKIP_S - PARK_WINDOW_S)..(endS - PARK_SKIP_S) }
        if (parkWin.isEmpty()) return null
        val park = median(parkWin.map { it.hpa })

        val searchFrom = lastOutdoorFixS?.let { it - GPS_MARGIN_S } ?: (endS - SEARCH_S)
        val window = sorted.filter { it.tS >= searchFrom && it.tS <= endS - PARK_SKIP_S }
        if (window.size < 5) return null
        val smooth = window.map { s ->
            Sample(s.tS, median(window.filter { kotlin.math.abs(it.tS - s.tS) <= SMOOTH_S / 2 }.map { it.hpa }))
        }
        val far = smooth.maxBy { kotlin.math.abs(it.hpa - park) }
        val ref = median(window.filter { kotlin.math.abs(it.tS - far.tS) <= REF_HALF_S }.map { it.hpa })
        return Result(FloorMath.heightM(park, ref), park, ref, far.tS)
    }

    fun median(values: List<Double>): Double = FloorMath.median(values)
}
