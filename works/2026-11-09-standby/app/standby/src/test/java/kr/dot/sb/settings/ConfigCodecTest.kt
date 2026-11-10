package kr.dot.sb.settings

import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Background
import kr.dot.sb.model.CardType
import kr.dot.sb.model.City
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.DDay
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.NightMode
import kr.dot.sb.model.Numerals
import kr.dot.sb.model.UnfoldAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ConfigCodecTest {
    private fun changed(): AppConfig {
        val d = AppConfig()
        val digital = d.styles.getValue(FaceId.DIGITAL).copy(
            font = FontId.SLAB, weight = 640, sizePct = 85, widthPct = 112, color = 0xFF123456, effect = Effect.GLASS,
            background = Background.GRADIENT, numerals = Numerals.DEVA, analogNumbers = AnalogNumbers.ROMAN, showInfo = false,
        )
        return d.copy(
            styles = d.styles + (FaceId.DIGITAL to digital),
            boards = BoardOps.add(d).boards.map { it.copy(accent = 0xFFFD6890) },
            startPage = 4, use24h = true, nightMode = NightMode.SCHEDULE, nightEnterLux = 2.5f, nightExitLux = 11f,
            nightMaxBrightness = 0.45f, dayBrightness = 0.6f, startOnCharge = false, displayOff = DisplayOff.NEVER, motionToWake = false, showNotifications = false,
            previewOnTapOnly = true, burnInShift = false, widgetLeft = listOf(CardType.MUSIC), widgetRight = emptyList(),
            glowColor = 0xFF85D4BA, glowStartMinBefore = 10, glowRampSec = 9, onUnfold = UnfoldAction.MOOD_LIGHT,
            ddays = listOf(DDay("수능", LocalDate.of(2026, 11, 19)), DDay("생일", LocalDate.of(2027, 2, 3))),
            city = City("부산", 35.1796, 129.0756), worldCities = listOf(City("도쿄", 35.6762, 139.6503)),
            usageEnabled = false, focusMinutes = 50, breakMinutes = 10, sleepMinutes = 90, theme = kr.dot.sb.model.Theme.MOODLIGHT,
        )
    }

    @Test fun roundTripKeepsEveryField() {
        val c = changed()
        assertNotEquals(AppConfig(), c)
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
    }

    @Test fun defaultsRoundTrip() {
        assertEquals(AppConfig(), ConfigCodec.decode(ConfigCodec.encode(AppConfig())))
    }

    @Test fun dayBrightnessNullMeansAutoAndSurvives() {
        assertEquals(null, ConfigCodec.decode(ConfigCodec.encode(AppConfig(dayBrightness = null))).dayBrightness)
        assertEquals(0.35f, ConfigCodec.decode(ConfigCodec.encode(AppConfig(dayBrightness = 0.35f))).dayBrightness)
    }

    @Test fun oldSaveWithoutNewFieldsGetsDefaults() {
        val c = ConfigCodec.decode("""{"startPage":1}""")
        assertEquals(null, c.dayBrightness)
        assertEquals(true, c.startOnCharge)
    }

    @Test fun outOfRangeDayBrightnessIsClamped() =
        assertEquals(1f, ConfigCodec.decode("""{"dayBrightness":7.5}""").dayBrightness)

    @Test fun nullUse24hSurvives() {
        assertEquals(null, ConfigCodec.decode(ConfigCodec.encode(AppConfig(use24h = null))).use24h)
    }

    @Test fun emptyOrBrokenSavesGiveDefaults() {
        assertEquals(AppConfig(), ConfigCodec.decode(null))
        assertEquals(AppConfig(), ConfigCodec.decode("{}"))
        assertEquals(AppConfig(), ConfigCodec.decode("not json {{{"))
    }

    @Test fun unknownFieldsAndEnumValuesFallBack() {
        val json = """{"future":{"x":1},"nightMode":"MOON","displayOff":"NEVER","startPage":5,
            |"styles":{"DIGITAL":{"font":"COMIC","weight":300,"newField":true},"HOLOGRAM":{"font":"BOLD"}},
            |"boards":[{"id":"a","layout":"HEX","slots":[["CLOCK","TELEPORT"]]},{"name":"no id"}],
            |"widgetLeft":["MUSIC","NOPE"],"ddays":[{"name":"x","date":"garbage"}]}""".trimMargin()
        val c = ConfigCodec.decode(json)
        assertEquals(NightMode.AUTO, c.nightMode)          // unknown enum value
        assertEquals(DisplayOff.NEVER, c.displayOff)       // known value in the same file still read
        assertEquals(5, c.startPage)
        val digital = c.styles.getValue(FaceId.DIGITAL)
        assertEquals(ClockStyle.default(FaceId.DIGITAL).font, digital.font)   // unknown font value
        assertEquals(300, digital.weight)
        assertEquals(ClockStyle.default(FaceId.FLOAT), c.styles.getValue(FaceId.FLOAT))
        assertEquals(1, c.boards.size)                     // the board without an id is dropped
        assertEquals(Layout.TWO, c.boards[0].layout)       // unknown layout
        assertEquals(2, c.boards[0].slots.size)            // slots refitted to the layout
        assertEquals(listOf(CardType.CLOCK), c.boards[0].slots[0])
        assertEquals(listOf(CardType.MUSIC), c.widgetLeft)
        assertTrue(c.ddays.isEmpty())                      // unparsable date dropped
    }
}
