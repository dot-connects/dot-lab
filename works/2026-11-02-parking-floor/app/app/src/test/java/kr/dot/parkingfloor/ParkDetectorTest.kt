package kr.dot.parkingfloor

import kr.dot.parkingfloor.ParkDetector.Sample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.pow

class ParkDetectorTest {
    private val p0 = 1013.0

    /** Pressure at height h metres above the reference, inverse of FloorMath.heightM. */
    private fun hpaAt(h: Double) = p0 * (1 - h / 44330.0).pow(5.255)

    /** 1 Hz trace from height segments: (duration s, start h, end h). */
    private fun trace(vararg segments: Triple<Int, Double, Double>, noise: Double = 0.0): List<Sample> {
        val out = mutableListOf<Sample>()
        var t = 0
        for ((dur, a, b) in segments) {
            for (i in 0 until dur) {
                val h = a + (b - a) * i / dur + if (noise > 0) noise * kotlin.math.sin(t * 1.7) else 0.0
                out += Sample(t.toDouble(), hpaAt(h))
                t++
            }
        }
        return out
    }

    @Test
    fun undergroundParkFromStreet() {
        // street 300 s, ramp down 7.3 m in 60 s, drive around B2 90 s, park
        val s = trace(Triple(300, 0.0, 0.0), Triple(60, 0.0, -7.3), Triple(90, -7.3, -7.3), noise = 0.25)
        val r = ParkDetector.detect(s, endS = s.last().tS)!!
        assertEquals(-7.3, r.deltaM, 0.4)
        assertEquals(-2, FloorModel.estimate(r.deltaM, emptyList()).floor)
    }

    @Test
    fun passingThroughB1DoesNotBecomeTheEntrance() {
        // stops on B1 for a minute looking for space, then goes on to B2
        val s = trace(Triple(200, 0.0, 0.0), Triple(30, 0.0, -4.0), Triple(60, -4.0, -4.0), Triple(30, -4.0, -7.3), Triple(40, -7.3, -7.3))
        val r = ParkDetector.detect(s, endS = s.last().tS)!!
        assertEquals(-7.3, r.deltaM, 0.4)
    }

    @Test
    fun parkingTowerGoesUp() {
        val s = trace(Triple(200, 0.0, 0.0), Triple(80, 0.0, 10.6), Triple(40, 10.6, 10.6))
        val r = ParkDetector.detect(s, endS = s.last().tS)!!
        assertEquals(10.6, r.deltaM, 0.4)
    }

    @Test
    fun hillBeforeTheGarageIsCutOffByTheGpsFix() {
        // a 12 m hill four minutes out, then flat street, garage entrance after the last GPS fix
        val s = trace(Triple(60, 0.0, 12.0), Triple(60, 12.0, 0.0), Triple(120, 0.0, 0.0), Triple(40, 0.0, -4.0), Triple(30, -4.0, -4.0))
        val withFix = ParkDetector.detect(s, endS = s.last().tS, lastOutdoorFixS = 230.0)!!
        assertEquals(-4.0, withFix.deltaM, 0.4)
    }

    @Test
    fun doorSlamAfterParkIsSkipped() {
        val s = trace(Triple(200, 0.0, 0.0), Triple(40, 0.0, -4.0), Triple(40, -4.0, -4.0)).toMutableList()
        // last two seconds: door closes, +1.5 hPa spike
        s[s.size - 1] = Sample(s.last().tS, s.last().hpa + 1.5)
        s[s.size - 2] = Sample(s[s.size - 2].tS, s[s.size - 2].hpa + 1.5)
        val r = ParkDetector.detect(s, endS = s.last().tS)!!
        assertEquals(-4.0, r.deltaM, 0.4)
    }

    @Test
    fun staleSensorIsNull() {
        // samples stop two minutes before the car drops: no park value rather than a fake 1F
        val s = trace(Triple(200, 0.0, 0.0), Triple(40, 0.0, -4.0))
        assertNull(ParkDetector.detect(s, endS = s.last().tS + 120))
    }

    @Test
    fun tooShortIsNull() {
        assertNull(ParkDetector.detect(trace(Triple(5, 0.0, 0.0)), endS = 4.0))
    }
}
