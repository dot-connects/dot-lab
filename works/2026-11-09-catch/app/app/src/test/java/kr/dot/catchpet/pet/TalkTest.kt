package kr.dot.catchpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TalkTest {
    private val day = 20_000L
    private val t0 = 1_000_000_000L

    @Test fun slotsByHour() {
        assertEquals(Talk.Slot.NIGHT, Talk.slot(4))
        assertEquals(Talk.Slot.MORNING, Talk.slot(5))
        assertEquals(Talk.Slot.NOON, Talk.slot(11))
        assertEquals(Talk.Slot.EVENING, Talk.slot(17))
        assertEquals(Talk.Slot.NIGHT, Talk.slot(22))
    }

    @Test fun aPlainUnlockSpeaksOnlyByChance() {
        assertNull(Talk.pick(t0, day, 9, null, "몽글", Talk.State(), { 0.9f }))
        assertTrue(Talk.pick(t0, day, 9, null, "몽글", Talk.State(), { 0.1f }) != null)
        // an unlock always speaks, whatever the roll
        assertTrue(Talk.pick(t0, day, 9, null, "몽글", Talk.State(), sure = true, rnd = { 0.9f }) != null)
    }

    @Test fun aSituationAlwaysSpeaksAndComesFirst() {
        val l = Talk.pick(t0, day, 9, Talk.Why.NewFriend("도토리"), "몽글", Talk.State(), { 0.99f })
        assertTrue(l, l == "새 친구 생겼다!" || l == "도토리 반가워!")
    }

    @Test fun tenMinutesApart() {
        val st = Talk.said(t0, day, Talk.State(), "좋은 아침!")
        assertNull(Talk.pick(t0 + Talk.GAP_MS - 1, day, 9, Talk.Why.Missed, "몽글", st, { 0f }))
        assertTrue(Talk.pick(t0 + Talk.GAP_MS, day, 9, Talk.Why.Missed, "몽글", st, { 0f }) != null)
    }

    @Test fun atMostTwelveADayThenANewDay() {
        var st = Talk.State()
        repeat(Talk.DAY_MAX) { st = Talk.said(t0, day, st, "x$it") }
        assertEquals(Talk.DAY_MAX, st.count)
        assertNull(Talk.pick(t0 + Talk.GAP_MS, day, 9, Talk.Why.Missed, "몽글", st, { 0f }))
        assertTrue(Talk.pick(t0 + Talk.GAP_MS, day + 1, 9, Talk.Why.Missed, "몽글", st, { 0f }) != null)
        assertEquals(1, Talk.said(t0, day + 1, st, "y").count)
    }

    @Test fun neverTheSameLineTwiceInARow() {
        val st = Talk.State(last = "보고 싶었어")
        repeat(20) { i -> assertNotEquals("보고 싶었어", Talk.pick(t0, day, 9, Talk.Why.Missed, "몽글", st, { i / 20f })) }
    }

    @Test fun theNameGoesIn() {
        assertEquals("몽글 여기 있어", Talk.slotLine(13, "몽글", 4))
    }
}
