package kr.dot.sb.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The sleep loop's AudioTrack writes: partial writes finish the chunk, an error or a stall reports failure. */
class TrackWriteTest {
    private val buf = ShortArray(100)

    @Test fun aFullWriteIsOneCall() {
        val calls = mutableListOf<Pair<Int, Int>>()
        assertTrue(TrackWrite.writeAll(buf) { _, off, n -> calls += off to n; n })
        assertEquals(listOf(0 to 100), calls)
    }

    @Test fun partialWritesContinueFromWhereTheyStopped() {
        val calls = mutableListOf<Pair<Int, Int>>()
        val results = ArrayDeque(listOf(30, 50, 20))
        assertTrue(TrackWrite.writeAll(buf) { _, off, n -> calls += off to n; results.removeFirst() })
        assertEquals(listOf(0 to 100, 30 to 70, 80 to 20), calls)
    }

    @Test fun anErrorCodeFails() {
        // AudioTrack.ERROR_DEAD_OBJECT = -6, ERROR = -1, ERROR_INVALID_OPERATION = -3
        for (code in listOf(-6, -1, -3)) assertFalse(TrackWrite.writeAll(buf) { _, _, _ -> code })
        val results = ArrayDeque(listOf(40, -6))
        assertFalse(TrackWrite.writeAll(buf) { _, _, _ -> results.removeFirst() })
    }

    @Test fun aZeroWriteFailsInsteadOfSpinning() {
        var calls = 0
        assertFalse(TrackWrite.writeAll(buf) { _, _, _ -> calls++; 0 })
        assertEquals(1, calls)
    }

    @Test fun retriesBackOffAndStopAfterThree() {
        assertEquals(listOf(250L, 500L, 1000L), (1..3).map { TrackWrite.backoffMs(it) })
        assertTrue((1..3).all { TrackWrite.mayRetry(it) })
        assertFalse(TrackWrite.mayRetry(4))
    }
}
