package kr.dot.sb.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.AppConfig
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
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.math.roundToInt

/** The screens a board can be previewed on (design-system §3.3). Sizes are landscape dp. */
enum class PreviewDevice(val ko: String, val w: Int, val h: Int, val cover: Boolean) {
    FOLD_COVER("폴드 커버", 751, 475, true),
    FOLD_INNER("폴드 펼침", 932, 704, false),
    BAR("바형", 780, 360, false),
    FLIP("플립", 400, 362, false);

    fun width(portrait: Boolean) = if (portrait) minOf(w, h) else maxOf(w, h)
    fun height(portrait: Boolean) = if (portrait) maxOf(w, h) else minOf(w, h)
}

/**
 * Draws [content] at the real device dp size and scales it to fit [maxHeight] / the available width, so a preview is
 * exactly the composable the dream shows. [overlay] is drawn on top in unscaled coordinates (multiply by `scale`).
 */
@Composable
fun PreviewFrame(
    w: Int,
    h: Int,
    modifier: Modifier = Modifier,
    maxHeight: Dp = Dp.Infinity,
    radius: Dp = 12.dp,
    overlay: @Composable BoxScope.(scale: Float) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        // two-pane page (review S1): the preview takes the pane width instead of the phone height cap
        val cap = if (LocalPreviewPane.current) Dp.Infinity else maxHeight
        val scale = min(maxWidth.value / w, cap.value / h)
        val shape = RoundedCornerShape(radius)
        Box(Modifier.size((w * scale).dp, (h * scale).dp).clip(shape).background(Color.Black).border(1.dp, SK.line, shape)) {
            Box(
                Modifier.layout { measurable, _ ->
                    val pw = w.dp.roundToPx()
                    val ph = h.dp.roundToPx()
                    val p = measurable.measure(Constraints.fixed(pw, ph))
                    layout((pw * scale).roundToInt(), (ph * scale).roundToInt()) {
                        p.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
                    }
                },
            ) { content() }
            Box(Modifier.size((w * scale).dp, (h * scale).dp)) { overlay(scale) }
        }
    }
}

/** A fixed, fully-populated state for previews (the real feeds are not needed in settings). */
object Sample {
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 9, 21, 41)

    private fun gradient(w: Int, h: Int, top: Int, bottom: Int, ridge: Int) = run {
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
        bmp.asImageBitmap()
    }

    private val art by lazy { gradient(240, 240, 0xFF7FB3FF.toInt(), 0xFFF4B8D4.toInt(), 0xFFFFF1C4.toInt()) }
    private val photo by lazy { gradient(640, 420, 0xFF3F6D8C.toInt(), 0xFFE8E2C8.toInt(), 0xFF2F4A36.toInt()) }

    fun state(c: AppConfig, night: Float = 0f): StandbyState = StandbyState(
        now = now,
        use24h = c.use24h ?: false,
        night = night,
        media = MediaInfo("YouTube Music", "com.google.android.apps.youtube.music", MediaKind.MUSIC, "Ditto", "NewJeans", art, true, 72_000, 186_000),
        usage = UsageInfo(UsageWindow(20, 1794230400), UsageWindow(49, 1794308400), UsageWindow(0, 1794192419), UsageWindow(9, 1794574925), 1794228000),
        weather = WeatherInfo(
            c.city.name, 7, WeatherCondition.CLOUDY, 11, 2,
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
