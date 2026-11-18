package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A stored ANALOG card reads as CLOCK in the same position; v17 takes the first clock's face for every card.
 */
class AnalogMergeTest {
    @Test fun boardAnalogCardsBecomeClockCardsWithTheAnalogFace() {
        val c = ConfigCodec.decode(
            """{"widgetLeft":[],"widgetRight":[],"boards":[{"id":"a","layout":"TWO","clockFace":"DIGITAL",
            |"slots":[["ANALOG","CLOCK","WEATHER"],["ANALOG"]]}]}""".trimMargin(),
        )
        val b = c.boards[0]
        assertEquals(listOf(listOf(CardType.CLOCK, CardType.CLOCK, CardType.WEATHER), listOf(CardType.CLOCK)), b.slots)
        assertEquals(FaceId.DIGITAL, b.clockFace)
        assertEquals(FaceId.ANALOG, c.clockCardFace)
    }

    @Test fun widgetsPageAnalogBecomesAClockWithTheAnalogFace() {
        val c = ConfigCodec.decode("""{"widgetLeft":["ANALOG","CALENDAR"],"widgetRight":["WEATHER","ANALOG"]}""")
        assertEquals(listOf(CardType.CLOCK, CardType.CALENDAR), c.widgetLeft)
        assertEquals(listOf(CardType.WEATHER, CardType.CLOCK), c.widgetRight)
        assertEquals(FaceId.ANALOG, c.clockCardFace)
    }

    @Test fun anOldWidgetsClockWithoutAFaceKeepsDigital() {
        val c = ConfigCodec.decode("""{"widgetLeft":["MUSIC"],"widgetRight":["CLOCK"]}""")
        assertEquals(FaceId.DIGITAL, c.clockCardFace)
    }

    @Test fun aMissingWidgetsPageKeepsTheDefaultAnalogClock() {
        val d = AppConfig()
        assertEquals(CardType.CLOCK, d.widgetLeft[0])
        assertEquals(FaceId.ANALOG, d.clockCardFace)
        assertEquals(d.widgetLeft to d.clockCardFace, ConfigCodec.decode("{}").let { it.widgetLeft to it.clockCardFace })
    }

    @Test fun facesRoundTrip() {
        val d = AppConfig()
        for (face in FaceId.entries + listOf(null)) {
            val c = d.copy(clockCardFace = face, widgetRight = listOf(CardType.CLOCK))
            assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
        }
        assertEquals(d, ConfigCodec.decode(ConfigCodec.encode(d)))
    }

    @Test fun twoClocksWithDifferentFacesShareAStackButTwinsDedupe() {
        val c = ConfigCodec.decode("""{"widgetLeft":[],"widgetRight":[],"boards":[{"id":"a","layout":"ONE","slots":[["ANALOG","CLOCK","ANALOG","CLOCK"]]}]}""")
        assertEquals(listOf(listOf(CardType.CLOCK, CardType.CLOCK)), c.boards[0].slots)
        assertEquals(FaceId.ANALOG, c.clockCardFace)
    }
}
