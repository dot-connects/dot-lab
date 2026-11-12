package kr.dot.sb.ui.clock

/*
 * Public API of the clock area (OWNER: clock agent).
 *
 *   @Composable fun ClockFace(face: FaceId, style: ClockStyle, state: StandbyState, modifier: Modifier = Modifier,
 *                             inSlot: Boolean = false, hero: Boolean = true, analog: AnalogStyle = ROUND)
 *       A whole face filling [modifier]'s box: full screen, or fitted into a board slot when [inSlot] (no side column,
 *       date row below in slot label size, no background except Solar's rounded plate; XS slot = digits only).
 *       [analog] = `AppConfig.analogStyle` (full screen only; a slot always gets the round dial).
 *       [hero] = false: a second clock on a board (design-system §5.1) — digits in white `ink`, no effects.
 *       PHOTOS_TIME is given the whole photo screen and places the time itself (right edge 92 % W, cap-top 10 % H,
 *       cap 15 % H, autosized inside x 72-92 %, y 10-28 %).
 *       Night = state.night (candle night). displayOff = draws nothing and stops the analog ticker.
 *       Hero size depends only on the hour-digit count (§2.3.1): it changes at 9:59→10:00 and 12:59→1:00 with a
 *       400 ms crossfade, never per minute.
 *
 *   @Composable fun StyledTime(text: String, style: ClockStyle, ink: Ink, modifier: Modifier = Modifier,
 *                              color: Color = Color(style.color), effects: Boolean = true, widest: String? = null,
 *                              align: Alignment.Horizontal = Alignment.CenterHorizontally)
 *       Any digits ("9:41", "18:24", "D-10") in the user's clock font/weight/width/numerals, autosized to fit the box
 *       (width and height) and drawn centred vertically on the digit ink. [ink] gives the night mapping; [color] the
 *       day colour (accent for a hero, ink.ink day colour for white text). [effects] = false for non-hero cards
 *       (design-system §5.1: effects stay on the hero). [widest] = the string to size against so the size never
 *       changes per minute; null = every digit position takes the widest digit. Tabular digits (board numerals).
 *
 * Lightness: faces recompose only when their minute-level inputs change; layout is cached per size/style in
 * drawWithCache. Per-frame work happens only in the Float roll (400 ms), the twice-a-day size crossfade (400 ms) and
 * the analog second hand (1 Hz, its own layer).
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kr.dot.sb.model.AnalogStyle
import kr.dot.sb.model.City
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Background
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.Motion
import java.time.LocalDateTime
import java.time.ZoneId

/** Full-screen face, or the same face fitted into a board slot when [inSlot]. */
@Composable
fun ClockFace(
    face: FaceId, style: ClockStyle, state: StandbyState, modifier: Modifier = Modifier, inSlot: Boolean = false, hero: Boolean = true,
    analog: AnalogStyle = AnalogStyle.ROUND,
) {
    if (state.displayOff) { Box(modifier); return }
    val data = FaceData.of(state)
    // §5.1: a second clock draws its numerals in ink (white by day) without effects; Analog keeps its own colours.
    val st = if (hero || face == FaceId.ANALOG) style else style.copy(color = 0xFFFFFFFF, effect = Effect.SOLID)
    val th = LocalTheme.current
    // D4: a clock with its own background checks bypassing colours against it, not the page
    val own = when (st.background) {
        Background.BLACK -> null
        Background.COLOR -> listOf(Color(st.bgColor))
        Background.GRADIENT -> listOf(Color(st.bgColor), Color(st.bgColor2))
    }
    val ink = remember(state.night, st, th) { Ink.of(state.night, Color(st.color), th, own) }
    val m = modifier.semantics { contentDescription = "${data.ampm} ${data.h}:${data.m}" }
    val body: @Composable (FaceData, Modifier) -> Unit = { d, mod ->
        when (face) {
            // r10 faces draw as DIGITAL until their renderer lands (plan-r10 Tasks 6-9)
            FaceId.DIGITAL, FaceId.THIN, FaceId.SPLIT, FaceId.COLOR_BLOCK, FaceId.STICKER, FaceId.BALLS, FaceId.FLIP, FaceId.LED,
            FaceId.LCD, FaceId.DOT_MATRIX, FaceId.WALL, FaceId.CALENDAR, FaceId.HANGUL, FaceId.DAY_BAR, FaceId.RIM,
            -> DigitalFace(st, d, ink, inSlot, mod)
            FaceId.DESK -> DeskFace(st, d, ink, inSlot, mod)
            FaceId.SOLAR -> SolarFace(st, d, ink, inSlot, mod)
            FaceId.FLOAT -> FloatFace(st, d, ink, inSlot, mod, hero)
            FaceId.ANALOG -> AnalogFace(st, d, ink, inSlot, mod, analog)
            FaceId.WORLD -> WorldFace(st, d, ink, inSlot, mod)
            FaceId.PHOTOS_TIME -> PhotosTime(st, d, ink, mod)
        }
    }
    // Analog and World never change size with the hour; the others crossfade only when the hour-digit class changes.
    if (face == FaceId.ANALOG || face == FaceId.WORLD) body(data, m)
    else {
        // Crossfade only when the hour-digit class changes (twice a day); every other minute is a plain recomposition.
        val cls = data.h.length
        val last = remember { arrayOfNulls<FaceData>(1) }
        val from = remember(cls) { last[0]?.takeIf { it.h.length != cls } }
        last[0] = data
        val fade = remember(cls) { Animatable(if (from == null) 1f else 0f) }
        LaunchedEffect(cls) { fade.animateTo(1f, tween(Motion.FLOAT_ROLL_MS)) }
        Box(m) {
            if (from != null && fade.value < 1f) body(from, Modifier.graphicsLayer { alpha = 1f - fade.value })
            body(data, Modifier.graphicsLayer { alpha = fade.value })
        }
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
    /** Weather condition (theme C pills draw its glyph). */
    val cond: kr.dot.sb.ui.state.WeatherCondition? = null,
    val weatherLine: String?,
    val city: String?,
    /** Next alarm time "6:30" / "06:30" and its day word "오늘" / "내일" / "11월 12일" (Desk's full form). */
    val alarm: String?,
    val alarmDay: String?,
    val alarmAmPm: String?,
    /** Compact alarm (review X2), drawn after the alarm glyph: 12 h "6:30" (no 오전/오후), 24 h "H:mm". */
    val alarmShort: String?,
    val mediaApp: String?,
    val mediaLine: String?,
    /** World: the city chosen in settings and the time there. */
    val worldCity: City,
    val worldTime: String,
    val worldAmPm: String,
) {
    val dateLong get() = "${month}월 ${day}일 ${weekday}요일"

    companion object {
        fun of(s: StandbyState): FaceData {
            val (h, m) = timeParts(s.now, s.use24h)
            val a = s.nextAlarm
            val wt = s.worldZoneId?.let { z ->
                runCatching { s.now.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of(z)).toLocalDateTime() }.getOrNull()
            } ?: s.now
            return FaceData(
                now = s.now.withSecond(0).withNano(0),
                h = h, m = m, use24h = s.use24h,
                ampm = if (s.now.hour < 12) "오전" else "오후",
                weekday = WEEKDAY_KO[s.now.dayOfWeek.value - 1],
                day = s.now.dayOfMonth, month = s.now.monthValue,
                temp = s.weather?.let { "${it.tempC}°" },
                cond = s.weather?.condition,
                weatherLine = s.weather?.let { "${it.tempC}° ${it.condition.ko}" },
                city = s.weather?.city,
                alarm = a?.let { timeParts(it, s.use24h).let { (ah, am) -> "$ah:$am" } },
                alarmDay = a?.let {
                    val d = it.toLocalDate(); val today = s.now.toLocalDate()
                    when (d) { today -> "오늘"; today.plusDays(1) -> "내일"; else -> "${d.monthValue}월 ${d.dayOfMonth}일" }
                },
                alarmAmPm = a?.let { if (s.use24h) "" else if (it.hour < 12) "오전 " else "오후 " },
                alarmShort = a?.let { if (s.use24h) "${it.hour}:${timeParts(it, true).second}" else timeParts(it, false).let { (ah, am) -> "$ah:$am" } },
                mediaApp = s.media?.appLabel,
                mediaLine = s.media?.let { if (it.artist.isBlank()) it.title else "${it.title} · ${it.artist}" },
                worldCity = s.worldCity,
                worldTime = timeParts(wt, s.use24h).let { (wh, wm) -> "$wh:$wm" },
                worldAmPm = if (wt.hour < 12) "오전" else "오후",
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
