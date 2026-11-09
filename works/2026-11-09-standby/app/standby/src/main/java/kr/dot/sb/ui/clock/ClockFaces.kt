package kr.dot.sb.ui.clock

/*
 * Public API of the clock area (OWNER: clock agent).
 *
 *   @Composable fun ClockFace(face: FaceId, style: ClockStyle, state: StandbyState, modifier: Modifier = Modifier, inSlot: Boolean = false)
 *       A whole face filling [modifier]'s box: full screen, or fitted into a board slot when [inSlot] (no side column,
 *       date row below in slot label size, no background except Solar's rounded plate; XS slot = digits only).
 *       Night = state.night (red ramp). displayOff = draws nothing and stops the analog ticker.
 *
 *   @Composable fun StyledTime(text: String, style: ClockStyle, ink: Ink, modifier: Modifier = Modifier,
 *                              color: Color = Color(style.color), effects: Boolean = true, widest: String? = null,
 *                              align: Alignment.Horizontal = Alignment.CenterHorizontally)
 *       Any digits ("9:41", "18:24", "D-10") in the user's clock font/weight/width/numerals, autosized to fit the box
 *       (width and height) and drawn centred vertically on the digit ink. [ink] gives the night mapping; [color] the
 *       day colour (accent for a hero, ink.ink day colour for white text). [effects] = false for non-hero cards
 *       (design-system §5.1: effects stay on the hero). [widest] = the string to size against so the size never
 *       changes per minute; null = every digit position takes the widest digit.
 *
 * Lightness: faces recompose only when their minute-level inputs change; layout is cached per size/style in
 * drawWithCache. Per-frame work happens only in the Float roll (400 ms) and the analog second hand (1 Hz, its own layer).
 */

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Ink
import java.time.LocalDateTime

/** Full-screen face, or the same face fitted into a board slot when [inSlot]. */
@Composable
fun ClockFace(face: FaceId, style: ClockStyle, state: StandbyState, modifier: Modifier = Modifier, inSlot: Boolean = false) {
    if (state.displayOff) { Box(modifier); return }
    val data = FaceData.of(state)
    val ink = remember(state.night, style.color) { Ink.of(state.night, Color(style.color)) }
    val m = modifier.semantics { contentDescription = "${data.ampm} ${data.h}:${data.m}" }
    when (face) {
        FaceId.DIGITAL -> DigitalFace(style, data, ink, inSlot, m)
        FaceId.DESK -> DeskFace(style, data, ink, inSlot, m)
        FaceId.SOLAR -> SolarFace(style, data, ink, inSlot, m)
        FaceId.FLOAT -> FloatFace(style, data, ink, inSlot, m)
        FaceId.ANALOG -> AnalogFace(style, data, ink, inSlot, m)
        FaceId.WORLD -> WorldFace(style, data, ink, inSlot, m)
        FaceId.PHOTOS_TIME -> StyledTime("${data.h}:${data.m}", style, ink, m, widest = if (data.use24h) "20:08" else "10:58")
    }
}

/** Minute-level inputs of the faces, so a face skips recomposition when unrelated state (media position…) changes. */
@Immutable
internal data class FaceData(
    val now: LocalDateTime,
    val h: String,
    val m: String,
    val use24h: Boolean,
    /** "오전" / "오후" (World only shows it). */
    val ampm: String,
    /** "월" */
    val weekday: String,
    val day: Int,
    val month: Int,
    val temp: String?,
    val weatherLine: String?,
    val city: String?,
    /** Next alarm time "6:30" / "06:30" and its day word "오늘" / "내일" / "11월 12일". */
    val alarm: String?,
    val alarmDay: String?,
    val alarmAmPm: String?,
    val mediaApp: String?,
    val mediaLine: String?,
) {
    val dateLong get() = "${month}월 ${day}일 ${weekday}요일"

    companion object {
        fun of(s: StandbyState): FaceData {
            val (h, m) = timeParts(s.now, s.use24h)
            val a = s.nextAlarm
            return FaceData(
                now = s.now.withSecond(0).withNano(0),
                h = h, m = m, use24h = s.use24h,
                ampm = if (s.now.hour < 12) "오전" else "오후",
                weekday = WEEKDAY_KO[s.now.dayOfWeek.value - 1],
                day = s.now.dayOfMonth, month = s.now.monthValue,
                temp = s.weather?.let { "${it.tempC}°" },
                weatherLine = s.weather?.let { "${it.tempC}° ${it.condition.ko}" },
                city = s.weather?.city,
                alarm = a?.let { timeParts(it, s.use24h).let { (ah, am) -> "$ah:$am" } },
                alarmDay = a?.let {
                    val d = it.toLocalDate(); val today = s.now.toLocalDate()
                    when (d) { today -> "오늘"; today.plusDays(1) -> "내일"; else -> "${d.monthValue}월 ${d.dayOfMonth}일" }
                },
                alarmAmPm = a?.let { if (s.use24h) "" else if (it.hour < 12) "오전 " else "오후 " },
                mediaApp = s.media?.appLabel,
                mediaLine = s.media?.let { if (it.artist.isBlank()) it.title else "${it.title} · ${it.artist}" },
            )
        }
    }
}

/** A time (or any digits) in the user's clock style, autosized to the box. See the file header. */
@Composable
fun StyledTime(
    text: String,
    style: ClockStyle,
    ink: Ink,
    modifier: Modifier = Modifier,
    color: Color = Color(style.color),
    effects: Boolean = true,
    widest: String? = null,
    align: Alignment.Horizontal = Alignment.CenterHorizontally,
) = StyledTimeImpl(text, style, ink, modifier, color, effects, widest, align)
