package kr.dot.sb

import kr.dot.sb.ui.theme.Themes
import kr.dot.sb.ui.theme.ThemeBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.model.Theme
import kr.dot.sb.ui.theme.ProvideTheme
import kr.dot.sb.ui.state.AgendaItem
import kr.dot.sb.ui.state.HourTemp
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.MediaKind
import kr.dot.sb.ui.state.NotifItem
import kr.dot.sb.ui.state.Photo
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.state.TimerState
import kr.dot.sb.ui.state.UsageInfo
import kr.dot.sb.ui.state.UsageWindow
import kr.dot.sb.ui.state.WeatherCondition
import kr.dot.sb.ui.state.WeatherInfo
import org.robolectric.RuntimeEnvironment
import java.time.LocalDateTime

/**
 * Screenshot harness shared by all render tests. Each device is a Robolectric qualifier string; shots land in
 * standby/build/shots/<theme>/<group>/<name>-<device>.png (run with -Proborazzi.test.record=true).
 * Tests must use @GraphicsMode(NATIVE) and @Config(sdk = [34]).
 */
enum class Device(val qualifiers: String, val label: String) {
    FOLD_COVER_L("w751dp-h475dp-land-xhdpi", "fold-cover-land"),
    FOLD_COVER_P("w475dp-h751dp-port-xhdpi", "fold-cover-port"),
    FOLD_INNER("w932dp-h704dp-land-xhdpi", "fold-inner"),
    BAR_L("w780dp-h360dp-land-xxhdpi", "bar-land"),
    BAR_P("w360dp-h780dp-port-xxhdpi", "bar-port"),
    FLIP("w400dp-h362dp-land-xhdpi", "flip-cover"),
}

/**
 * Themes every render matrix runs under (one PNG tree per theme: build/shots/<theme>/<group>/…): every theme, or the
 * comma list in `-Pshot.themes=IPHONE,DAYPAPER` while iterating (r9: ten themes × six devices is slow).
 */
val THEMES: List<Theme> = System.getProperty("shot.themes").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
    .mapNotNull { n -> Theme.entries.firstOrNull { it.name.equals(n, ignoreCase = true) } }
    .ifEmpty { Theme.entries.toList() }

/** `-Pshot.group=board,screens` captures only those groups; the rest of a matrix still runs (cheap) but writes nothing. */
private val GROUPS: Set<String> = System.getProperty("shot.group").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
fun groupOn(group: String): Boolean = GROUPS.isEmpty() || group in GROUPS

fun shotPath(theme: Theme, group: String, name: String, device: Device? = null): String =
    "build/shots/${theme.name.lowercase()}/$group/$name${device?.let { "-" + it.label } ?: ""}.png"

/** Render [content] under [theme] (ambient for ClockFace; BoardView/StandbyRoot also take it from their AppConfig). */
fun ComposeContentTestRule.shot(group: String, name: String, device: Device, theme: Theme = Theme.IPHONE, night: Float = 0f, content: @Composable () -> Unit) {
    if (!groupOn(group)) return
    RuntimeEnvironment.setQualifiers(device.qualifiers)
    setContent { ProvideTheme(theme) { Box(Modifier.fillMaxSize()) { ThemeBackground(Themes.of(theme), night); content() } } }
    waitForIdle()
    onRoot().captureRoboImage(shotPath(theme, group, name, device))
}

/** Deterministic sample data: 9:41 on 2026-11-09 (Mon), with every feed filled. */
object Fixtures {
    val now: LocalDateTime = LocalDateTime.of(2026, 11, 9, 21, 41)

    fun gradient(w: Int, h: Int, top: Int, bottom: Int, ridge: Int): ImageBitmap {
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint()
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null; p.color = ridge
        val path = android.graphics.Path().apply {
            moveTo(0f, h.toFloat())
            for (x in 0..w step 8) lineTo(x.toFloat(), h * 0.68f - h * 0.09f * kotlin.math.sin(x / w.toFloat() * 7f))
            lineTo(w.toFloat(), h.toFloat()); close()
        }
        c.drawPath(path, p)
        return bmp.asImageBitmap()
    }

    val art: ImageBitmap by lazy { gradient(300, 300, 0xFF7FB3FF.toInt(), 0xFFF4B8D4.toInt(), 0xFFFFF1C4.toInt()) }
    val photo: ImageBitmap by lazy { gradient(900, 600, 0xFF3F6D8C.toInt(), 0xFFE8E2C8.toInt(), 0xFF2F4A36.toInt()) }

    fun full(night: Float = 0f): StandbyState = StandbyState(
        now = now,
        night = night,
        media = MediaInfo("YouTube Music", "com.google.android.apps.youtube.music", MediaKind.MUSIC, "Ditto", "NewJeans", art, true, 72_000, 186_000),
        usage = UsageInfo(UsageWindow(20, 1794230400), UsageWindow(49, 1794308400), UsageWindow(0, 1794192419), UsageWindow(9, 1794574925), 1794228000),
        weather = WeatherInfo(
            "서울", 7, WeatherCondition.CLOUDY, 11, 2,
            listOf(HourTemp(22, 6, WeatherCondition.CLOUDY), HourTemp(23, 5, WeatherCondition.CLOUDY), HourTemp(0, 5, WeatherCondition.NIGHT_CLEAR), HourTemp(1, 4, WeatherCondition.NIGHT_CLEAR), HourTemp(2, 4, WeatherCondition.FOG)),
            1794228000,
        ),
        agenda = listOf(AgendaItem("09:30", "팀 회의", 0xFF4D9BEF), AgendaItem("13:00", "병원 예약", 0xFFFF9C56), AgendaItem("19:00", "헬스", 0xFF6FDC86)),
        nextAlarm = LocalDateTime.of(2026, 11, 10, 6, 30),
        timers = mapOf("desk-1" to TimerState(leftSec = 18 * 60 + 24, running = true)),
        photos = listOf(Photo(photo, "설악산", "2025년 10월 18일")),
        memory = Photo(photo, "설악산", "2023년 11월 9일", yearsAgo = 3),
        notifications = notifs(),
    )

    private fun minutesAgo(m: Long) = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - m * 60_000

    /** Mockup r6 shade, newest first; icons null = the letter plates (B: rings). */
    fun notifs(): List<NotifItem> = listOf(
        NotifItem("k1", "카카오톡", "com.kakao.talk", "엄마", "저녁 먹었어? 내일 김장하러 올 수 있으면 연락 줘", minutesAgo(0), plateColor = 0xFFF5D000),
        NotifItem("k2", "쿠팡", "com.coupang.mobile", "배송 완료", "주문하신 상품이 문 앞에 도착했습니다", minutesAgo(12), plateColor = 0xFFE1393B),
        NotifItem("k3", "캘린더", "com.samsung.android.calendar", "팀 회의", "10:30 · 3층 회의실", minutesAgo(20), plateColor = 0xFF3478F6),
        NotifItem("k4", "메시지", "com.samsung.android.messaging", "민지", "내일 몇 시에 만나?", minutesAgo(60), plateColor = 0xFF34C759),
        NotifItem("k5", "토스", "viva.republica.toss", "입금 알림", "급여가 입금되었어요", minutesAgo(120), plateColor = 0xFF1B64DA),
    )

    /** The phone hides KakaoTalk's content on the lock screen: the listener hands over the app name only. */
    fun hiddenNotifs(): List<NotifItem> = notifs().let { l -> listOf(l[0].copy(title = "카카오톡", text = "", hidden = true)) + l.drop(1) }
}
