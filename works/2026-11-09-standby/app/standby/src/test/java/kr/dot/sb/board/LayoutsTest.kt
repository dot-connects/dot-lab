package kr.dot.sb.board

import kr.dot.sb.ui.cards.CalendarShape
import kr.dot.sb.ui.cards.MediaShape
import kr.dot.sb.ui.cards.NotifTier
import kr.dot.sb.ui.cards.WeatherShape
import kr.dot.sb.ui.cards.calendarShape
import kr.dot.sb.ui.cards.mediaShape
import kr.dot.sb.ui.cards.notifTier
import kr.dot.sb.ui.cards.rectPoint
import kr.dot.sb.ui.cards.tileGrid
import kr.dot.sb.ui.cards.weatherShape
import org.junit.Assert.assertEquals
import org.junit.Test

/** spec-r9 §1 layout thresholds on the Fold-cover slot sizes (BoardGridTest table). */
class LayoutsTest {
    @Test fun notif() {
        assertEquals(NotifTier.D, notifTier(338f, 200f))
        assertEquals(NotifTier.A, notifTier(172f, 153f))
        assertEquals(NotifTier.S, notifTier(362f, 152f))
        assertEquals(NotifTier.E, notifTier(695f, 419f))
        assertEquals(NotifTier.B, notifTier(200f, 338f))
        assertEquals(NotifTier.C, notifTier(270f, 200f))
    }

    @Test fun media() {
        assertEquals(MediaShape.TALL, mediaShape(338f, 419f))
        assertEquals(MediaShape.COVER, mediaShape(419f, 338f))
    }

    @Test fun weatherAndCalendar() {
        assertEquals(WeatherShape.TINY, weatherShape(140f, 300f))
        assertEquals(WeatherShape.SHORT_WIDE, weatherShape(400f, 150f))
        assertEquals(WeatherShape.SMALL, weatherShape(170f, 300f))
        assertEquals(WeatherShape.WIDE, weatherShape(695f, 419f))
        assertEquals(WeatherShape.STACK, weatherShape(338f, 419f))
        assertEquals(CalendarShape.SHORT_WIDE, calendarShape(270f, 200f))
        assertEquals(CalendarShape.NARROW, calendarShape(218f, 419f))
        assertEquals(CalendarShape.WIDE_MONTH, calendarShape(695f, 419f))
        assertEquals(CalendarShape.MONTH, calendarShape(338f, 419f))
    }

    @Test fun tiles() {
        assertEquals(2, tileGrid(6, 338f, 380f).cols)
        assertEquals(3, tileGrid(6, 695f, 419f).cols)
    }

    @Test fun rect() {
        val (x, y) = rectPoint(400f, 200f, 90f, 10f)
        assertEquals(390f, x, 0.01f); assertEquals(100f, y, 0.01f)
        val (x0, y0) = rectPoint(400f, 200f, 0f, 10f)
        assertEquals(200f, x0, 0.01f); assertEquals(10f, y0, 0.01f)
        val (x45, y45) = rectPoint(400f, 200f, 45f, 10f)   // hits the top edge, not the corner
        assertEquals(290f, x45, 0.01f); assertEquals(10f, y45, 0.01f)
    }
}
