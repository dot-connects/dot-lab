package kr.dot.sb.screens

import kr.dot.sb.model.AppConfig
import kr.dot.sb.ui.screens.Layer
import kr.dot.sb.ui.screens.topLayer
import kr.dot.sb.ui.state.AlarmRinging
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.LiveActivity
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.MediaKind
import kr.dot.sb.ui.state.StandbyState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

/** Interrupt priority (spec §2): alarm > notification > live full > now playing. */
class LayerTest {
    private val base = StandbyState(LocalDateTime.of(2026, 11, 9, 21, 41))
    private val n = BigNotification("k", "앱", "보낸 이", "본문", null, postedAtEpochMs = 0)
    private val l = LiveActivity("l", "앱", "t", "v", "d", 0.5f)
    private val m = MediaInfo("앱", "p", MediaKind.MUSIC, "t", "a", null, true, 0, 1)
    private val all = base.copy(alarm = AlarmRinging("알람"), notification = n, live = l, liveExpanded = true, media = m)
    private val cfg = AppConfig()

    @Test fun priority() {
        assertEquals(Layer.ALARM, topLayer(all, cfg, true))
        assertEquals(Layer.NOTIFICATION, topLayer(all.copy(alarm = null), cfg, true))
        assertEquals(Layer.LIVE, topLayer(all.copy(alarm = null, notification = null), cfg, true))
        assertEquals(Layer.NOW_PLAYING, topLayer(all.copy(alarm = null, notification = null, liveExpanded = false), cfg, true))
        assertEquals(Layer.NONE, topLayer(all.copy(alarm = null, notification = null, liveExpanded = false), cfg, false))
    }

    @Test fun notificationsOffSkipsToNext() =
        assertEquals(Layer.LIVE, topLayer(all.copy(alarm = null), cfg.copy(showNotifications = false), false))

    @Test fun nowPlayingNeedsMedia() = assertEquals(Layer.NONE, topLayer(base, cfg, true))
}
