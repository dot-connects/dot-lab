package kr.dot.sb.platform

import kr.dot.sb.data.Needs
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.CardType
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.NightMode
import kr.dot.sb.model.PAGE_PHOTOS
import kr.dot.sb.model.PAGE_WIDGETS
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** r11 perf: the light sensor and the pictures are asked for only when a rule or a card uses them. */
class NeedsTest {
    @Test fun lightSensorNotRegisteredWhenNightOffAndThemeDark() {
        // the screen-off rule reads lux too, so it is off here as well
        assertFalse(Needs.lux(NightMode.OFF, DisplayOff.AFTER_20S, lightTheme = false))
        assertFalse(Needs.lux(NightMode.SCHEDULE, DisplayOff.NEVER, lightTheme = false))
    }

    @Test fun anyLuxRuleRegistersIt() {
        assertTrue(Needs.lux(NightMode.AUTO, DisplayOff.NEVER, lightTheme = false))
        assertTrue(Needs.lux(NightMode.OFF, DisplayOff.NEVER, lightTheme = true))   // a light theme forces night AUTO
        assertTrue(Needs.lux(NightMode.OFF, DisplayOff.AUTO, lightTheme = false))   // 어두워지면 꺼짐
    }

    @Test fun photosOnlyWithThePhotosPage() {
        assertTrue(Needs.photos(AppConfig()))
        assertFalse(Needs.photos(AppConfig(pagesOff = setOf(PAGE_PHOTOS))))
    }

    @Test fun memoryOnlyForAMemoriesCardOrThePhotoBackground() {
        val d = AppConfig()
        assertTrue("the book preset has a memories card", Needs.memory(d))
        val none = d.copy(boards = d.boards.filter { b -> b.slots.none { CardType.MEMORIES in it } })
        assertFalse(Needs.memory(none))
        assertTrue(Needs.memory(none.copy(boardBg = BoardBg.PHOTO_BLUR)))
        // a memories card on a page that is off does not count
        assertFalse(Needs.memory(d.copy(boards = d.boards.map { b -> if (b.slots.any { CardType.MEMORIES in it }) b.copy(on = false) else b })))
        assertTrue(Needs.memory(none.copy(widgetLeft = listOf(CardType.MEMORIES))))
        assertFalse(Needs.memory(none.copy(widgetLeft = listOf(CardType.MEMORIES), pagesOff = setOf(PAGE_WIDGETS))))
    }
}
