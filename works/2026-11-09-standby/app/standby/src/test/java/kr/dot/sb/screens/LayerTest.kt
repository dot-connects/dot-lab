package kr.dot.sb.screens

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.ui.screens.FIXED_PAGES
import kr.dot.sb.ui.screens.Layer
import kr.dot.sb.ui.screens.notifPage
import kr.dot.sb.ui.screens.topLayer
import kr.dot.sb.ui.state.AlarmRinging
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.LiveActivity
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.MediaKind
import kr.dot.sb.ui.state.StandbyState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** Interrupt priority: alarm > live full > now playing; a new notification is a banner, never a layer (spec-r9 §1). */
class LayerTest {
    private val base = StandbyState(LocalDateTime.of(2026, 11, 9, 21, 41))
    private val n = BigNotification("k", "앱", "보낸 이", "본문", null, postedAtEpochMs = 0)
    private val l = LiveActivity("l", "앱", "t", "v", "d", 0.5f)
    private val m = MediaInfo("앱", "p", MediaKind.MUSIC, "t", "a", null, true, 0, 1)
    private val all = base.copy(alarm = AlarmRinging("알람"), notification = n, live = l, liveExpanded = true, media = m)

    @Test fun priority() {
        assertEquals(Layer.ALARM, topLayer(all, true))
        assertEquals(Layer.LIVE, topLayer(all.copy(alarm = null), true))
        assertEquals(Layer.NOW_PLAYING, topLayer(all.copy(alarm = null, liveExpanded = false), true))
        assertEquals(Layer.NONE, topLayer(all.copy(alarm = null, liveExpanded = false), false))
    }

    @Test fun nowPlayingNeedsMedia() = assertEquals(Layer.NONE, topLayer(base, true))

    @Test fun bannerOpensTheCurrentNotifBoardElseTheFirst() {
        val plain = listOf(CardType.CLOCK)
        fun board(id: String, vararg slots: List<CardType>) = Board(id, id, Layout.TWO, slots.toList())
        val cfg = AppConfig(
            widgetLeft = plain, widgetRight = plain,
            boards = listOf(board("a", plain, plain), board("b", plain, listOf(CardType.WEATHER, CardType.NOTIF)), board("c", listOf(CardType.NOTIF), plain)),
        )
        assertEquals(FIXED_PAGES + 1, notifPage(cfg, 2))
        assertEquals(FIXED_PAGES + 2, notifPage(cfg, FIXED_PAGES + 2))
        assertNull(notifPage(cfg.copy(boards = cfg.boards.take(1)), 0))
    }
}
