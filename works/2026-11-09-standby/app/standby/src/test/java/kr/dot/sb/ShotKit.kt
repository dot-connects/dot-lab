package kr.dot.sb

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
import kr.dot.sb.ui.state.AgendaItem
import kr.dot.sb.ui.state.HourTemp
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.MediaKind
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
 * standby/build/shots/<group>/<name>-<device>.png (run with -Proborazzi.test.record=true).
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

fun ComposeContentTestRule.shot(group: String, name: String, device: Device, content: @Composable () -> Unit) {
    RuntimeEnvironment.setQualifiers(device.qualifiers)
    setContent { Box(Modifier.fillMaxSize().background(Color.Black)) { content() } }
    waitForIdle()
    onRoot().captureRoboImage("build/shots/$group/$name-${device.label}.png")
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
    )
}
