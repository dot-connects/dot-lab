package kr.dot.sb.ui.state

import java.time.LocalDate
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import kr.dot.sb.model.City
import kr.dot.sb.model.SleepSound
import java.time.LocalDateTime

/**
 * Everything the StandBy screens show, as plain values. The platform layer ([kr.dot.sb.platform]) produces it;
 * screenshot tests build it by hand. Time is minute-resolution on purpose: only the analog second hand ticks
 * per second, and it does that inside its own composable while visible (keep the app light).
 */
@Immutable
data class StandbyState(
    val now: LocalDateTime,
    val use24h: Boolean = false,
    /** 0 = day, 1 = full night red (animated by the platform layer). */
    val night: Float = 0f,
    /** Display auto-off: true renders the black "off" state. */
    val displayOff: Boolean = false,
    val media: MediaInfo? = null,
    val usage: UsageInfo? = null,
    val weather: WeatherInfo? = null,
    /** null = calendar permission missing; empty = no events today. */
    val agenda: List<AgendaItem>? = emptyList(),
    /** Today and the next 14 days, at most 8 (calendar card: events list, "다가오는 날"). */
    val upcoming: List<AgendaItem> = emptyList(),
    val nextAlarm: LocalDateTime? = null,
    val alarm: AlarmRinging? = null,
    val notification: BigNotification? = null,
    /** The phone's shade for the notification cards, newest first, already redacted for the lock screen. */
    val notifications: List<NotifItem> = emptyList(),
    val live: LiveActivity? = null,
    val liveExpanded: Boolean = false,
    val timers: Map<String, TimerState> = emptyMap(),
    val sleep: SleepState = SleepState(),
    /** Photos page queue (already decoded at screen size); null = permission missing. */
    val photos: List<Photo>? = emptyList(),
    val memory: Photo? = null,
    /** Album names for the Photos page themes (after 추천); empty until the first load. */
    val photoBuckets: List<String> = emptyList(),
    val permissions: Permissions = Permissions(),
    /** Fold inner screen open (book posture). */
    val unfolded: Boolean = false,
    /** Burn-in shift offset in dp, applied to the whole root. */
    val shiftX: Int = 0,
    val shiftY: Int = 0,
    /** World face city (from AppConfig.worldCities; its dot is placed from lat/lon). */
    val worldCity: City = City("서울", 37.5665, 126.9780),
    /** IANA zone of [worldCity] ("Europe/London"); null = the system zone. */
    val worldZoneId: String? = null,
)

enum class MediaKind { MUSIC, VIDEO }

@Immutable
data class MediaInfo(
    val appLabel: String, // "YouTube Music", "TVING" …
    val packageName: String,
    val kind: MediaKind,
    val title: String,
    val artist: String,
    val art: ImageBitmap?,
    val playing: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val canPrev: Boolean = true,
    val canNext: Boolean = true,
)

/** [pace] = the share an even spend would have LEFT by now (1.0 at the window's start), null when unknown. */
@Immutable
data class UsageWindow(val usedPct: Int, val resetsAtEpochSec: Long?, val pace: Double? = null)

/** A named Claude session on the PC. [state] busy | wait | idle | off; [ctx] context used %; [cacheUntil] prompt cache expiry (unix s). */
@Immutable
data class UsageSession(val name: String, val state: String, val ctx: Int?, val cacheUntil: Long?)

@Immutable
data class UsageInfo(
    val claude5h: UsageWindow?,
    val claude7d: UsageWindow?,
    val codex5h: UsageWindow?,
    val codex7d: UsageWindow?,
    val fetchedAtEpochSec: Long,
    /** true when the last fetch failed (show last values + "연결 안 됨"). */
    val offline: Boolean = false,
    /** dev, dev2, sync, lab in that order (a missing one is "off"); empty on older feeds. */
    val sessions: List<UsageSession> = emptyList(),
    /** content, trading bot state (busy | idle | off). */
    val bots: List<Pair<String, String>> = emptyList(),
)

/** The PC usage feed; only the personal build has a real one (UsageFeature). Null = the fetch failed. */
interface UsageFeed {
    suspend fun fetch(): UsageInfo?
}

enum class WeatherCondition(val ko: String) {
    CLEAR("맑음"), PARTLY("구름 조금"), CLOUDY("대체로 흐림"), RAIN("비"), SNOW("눈"), FOG("안개"), THUNDER("뇌우"), NIGHT_CLEAR("맑은 밤")
}

@Immutable
data class HourTemp(val hour: Int, val tempC: Int, val condition: WeatherCondition)

@Immutable
data class DayTemp(val date: LocalDate, val highC: Int, val lowC: Int, val condition: WeatherCondition)

@Immutable
data class WeatherInfo(
    val city: String,
    val tempC: Int,
    val condition: WeatherCondition,
    val highC: Int,
    val lowC: Int,
    val hourly: List<HourTemp>,
    val fetchedAtEpochSec: Long,
    val offline: Boolean = false,
    /** Days after today, from the daily forecast. */
    val days: List<DayTemp> = emptyList(),
)

@Immutable
data class AgendaItem(val start: String, val title: String, val color: Long, val date: LocalDate? = null)

@Immutable
data class AlarmRinging(val label: String, val canSnooze: Boolean = true)

@Immutable
data class BigNotification(
    val key: String,
    val appLabel: String,
    val sender: String,
    val text: String,
    val icon: ImageBitmap?,
    /** Samsung/app colour for the icon plate. */
    val plateColor: Long = 0xFF34C759,
    /** 탭할 때만 미리보기: content hidden until tapped. */
    val hidden: Boolean = false,
    val postedAtEpochMs: Long,
)

/**
 * One notification in the shade cards. Redaction happens in the listener: when [hidden], [title] is the app name and
 * [text] is empty, so private text never reaches the UI state.
 */
@Immutable
data class NotifItem(
    val key: String,
    val appLabel: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtEpochMs: Long,
    /** Colour icon (sender avatar or app icon) for themes A and C. */
    val icon: ImageBitmap? = null,
    /** The app's small status-bar icon (alpha mask) for theme B's monochrome rings. */
    val glyph: ImageBitmap? = null,
    /** App colour for the letter plate when there is no icon. */
    val plateColor: Long = 0xFF8E8E93,
    val hidden: Boolean = false,
    /** false = the app forbids swiping it away (FLAG_NO_CLEAR): the row snaps back. */
    val clearable: Boolean = true,
)

@Immutable
data class LiveActivity(
    val key: String,
    val appLabel: String,
    val title: String, // "배달 중"
    val value: String, // "12분"
    val detail: String,
    val progress: Float?, // 0..1
    val steps: Int = 4,
)

enum class TimerMode { FOCUS, BREAK }

@Immutable
data class TimerState(val mode: TimerMode = TimerMode.FOCUS, val leftSec: Int = 25 * 60, val totalSec: Int = 25 * 60, val running: Boolean = false)

@Immutable
data class SleepState(val playing: Boolean = false, val minutesLeft: Int? = null)

@Immutable
data class Photo(val image: ImageBitmap, val place: String, val date: String, val yearsAgo: Int? = null, val uri: String? = null)

@Immutable
data class Permissions(
    val notifications: Boolean = true,
    val calendar: Boolean = true,
    val photos: Boolean = true,
)

/** What a tap or button on a StandBy screen asks the platform to do. */
interface StandbyActions {
    fun mediaPlayPause() {}
    fun mediaNext() {}
    fun mediaPrev() {}
    fun openMediaApp() {}
    /** The media card's second tap: the full-screen Now Playing (StandbyRoot owns it). */
    fun openNowPlaying() {}
    fun timerToggle(id: String) {}
    fun timerReset(id: String) {}
    fun sleepToggle() {}
    /** Sleep card: [sound] becomes the chosen one. [toggle] plays it (stops it if it is the one playing); else it only switches a playing sound. */
    fun sleepPick(sound: SleepSound, toggle: Boolean) {}
    /** Sleep card minutes chip: the next timer choice (a playing sound restarts its timer). */
    fun sleepMinutesNext() {}
    fun alarmSnooze() {}
    fun alarmStop() {}
    fun notificationTap(key: String) {}
    /** Closes the big notification overlay (it stays in the phone's shade). */
    fun notificationDismiss(key: String) {}
    /** Removes these notifications from the phone itself (shade cards: swipe, 모두 지우기). */
    fun notificationClear(keys: List<String>) {}
    fun liveToggle() {}
    fun retryFeeds() {}
    fun openSettings(section: String? = null) {}
    /** User touched the screen (wakes from display-off, resets the 20 s timer). */
    fun userActivity() {}
    fun pageChanged(page: Int) {}
    /** Photos page: caption tap opens the photo in the gallery (after unlock). */
    fun openPhoto(uri: String) {}
    /** Photos page: theme swipe; null = 추천, else the album name. */
    fun photoTheme(bucket: String?) {}
    /** Now Playing volume glyph: show the system volume panel. */
    fun openVolume() {}
    /** Brightness bar: 0..1 for the window, applied live. */
    fun setBrightness(value: Float) {}
    fun exit() {}

    companion object { val NONE = object : StandbyActions {} }
}
