package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.SlotOptions
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 11-14: the analog card merged into the clock card. A stored ANALOG card reads as a CLOCK card whose own face is
 * ANALOG, in the same slot and position, keeping its slot options; a stack holding both keeps both.
 */
class AnalogMergeTest {
    @Test fun boardAnalogCardsBecomeClockCardsWithTheAnalogFace() {
        val c = ConfigCodec.decode(
            """{"boards":[{"id":"a","layout":"TWO","clockFace":"DIGITAL","slots":[["CLOCK","ANALOG","WEATHER"],["ANALOG"]],
            |"options":{"0:1":{"hideAtNight":true}}}]}""".trimMargin(),
        )
        val b = c.boards[0]
        assertEquals(listOf(listOf(CardType.CLOCK, CardType.CLOCK, CardType.WEATHER), listOf(CardType.CLOCK)), b.slots)
        assertEquals(FaceId.DIGITAL, b.clockFace)
        assertEquals(null, b.options["0:0"]?.face) // the digital clock keeps the board's face
        assertEquals(SlotOptions(hideAtNight = true, face = FaceId.ANALOG), b.options["0:1"])
        assertEquals(FaceId.ANALOG, b.options["1:0"]?.face)
    }

    @Test fun widgetsPageAnalogBecomesAClockWithTheAnalogFace() {
        val c = ConfigCodec.decode("""{"widgetLeft":["ANALOG","CALENDAR"],"widgetRight":["WEATHER","ANALOG"]}""")
        assertEquals(listOf(CardType.CLOCK, CardType.CALENDAR), c.widgetLeft)
        assertEquals(listOf(CardType.WEATHER, CardType.CLOCK), c.widgetRight)
        assertEquals(mapOf("0:0" to SlotOptions(face = FaceId.ANALOG), "1:1" to SlotOptions(face = FaceId.ANALOG)), c.widgetOptions)
    }

    @Test fun anOldSaveWithoutAnalogGetsNoFaces() {
        val c = ConfigCodec.decode("""{"widgetLeft":["MUSIC"],"widgetRight":["CLOCK"]}""")
        assertEquals(emptyMap<String, SlotOptions>(), c.widgetOptions)
    }

    @Test fun aMissingWidgetsPageKeepsTheDefaultAnalogClock() {
        val d = AppConfig()
        assertEquals(CardType.CLOCK, d.widgetLeft[0])
        assertEquals(FaceId.ANALOG, d.widgetOptions["0:0"]?.face)
        assertEquals(d.widgetLeft to d.widgetOptions, ConfigCodec.decode("{}").let { it.widgetLeft to it.widgetOptions })
    }

    @Test fun facesRoundTrip() {
        val d = AppConfig()
        val c = d.copy(widgetOptions = mapOf("1:0" to SlotOptions(face = FaceId.FLIP)), widgetRight = listOf(CardType.CLOCK))
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
        assertEquals(d, ConfigCodec.decode(ConfigCodec.encode(d)))
    }

    @Test fun twoClocksWithDifferentFacesShareAStackButTwinsDedupe() {
        val c = ConfigCodec.decode("""{"boards":[{"id":"a","layout":"ONE","slots":[["ANALOG","CLOCK","ANALOG","CLOCK"]]}]}""")
        assertEquals(listOf(listOf(CardType.CLOCK, CardType.CLOCK)), c.boards[0].slots)
        assertEquals(FaceId.ANALOG, c.boards[0].options["0:0"]?.face)
        assertEquals(null, c.boards[0].options["0:1"])
    }
}
