package kr.dot.sb.data

import kr.dot.sb.data.MediaClock.Anchor
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaClockTest {
    @Test fun playingMovesWithTimeAndSpeed() {
        assertEquals(12_500, MediaClock.position(Anchor(10_000, 1_000, 1f, 200_000, true), 3_500))
        assertEquals(15_000, MediaClock.position(Anchor(10_000, 1_000, 2f, 200_000, true), 3_500))
    }

    @Test fun pausedStaysPut() {
        assertEquals(10_000, MediaClock.position(Anchor(10_000, 1_000, 1f, 200_000, false), 60_000))
    }

    @Test fun clampedToTrack() {
        assertEquals(200_000, MediaClock.position(Anchor(199_000, 0 + 1, 1f, 200_000, true), 10_000))
        assertEquals(0, MediaClock.position(Anchor(-500, 1, 1f, 200_000, false), 1))
        // unknown duration: no upper clamp
        assertEquals(301_000, MediaClock.position(Anchor(300_000, 1, 1f, 0, true), 1_001))
    }

    @Test fun nextStepLandsOnTheTracksWholeSecond() {
        assertEquals(1_000, MediaClock.untilNextSecond(12_000, 1f))
        assertEquals(300, MediaClock.untilNextSecond(12_700, 1f))
        assertEquals(150, MediaClock.untilNextSecond(12_700, 2f))
        assertEquals(50, MediaClock.untilNextSecond(12_990, 1f))   // floor: no busy loop
        assertEquals(1_000, MediaClock.untilNextSecond(12_700, 0f))
        assertEquals(1_000, MediaClock.untilNextSecond(12_000, 0.5f)) // capped at 1 s
    }
}
