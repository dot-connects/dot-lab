package kr.dot.sb.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.clock.StyledTime
import kr.dot.sb.ui.screens.clockText
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.state.TimerMode
import kr.dot.sb.ui.state.TimerState
import kr.dot.sb.ui.state.WeatherInfo
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.SizeClass
import kr.dot.sb.ui.theme.clockStyle
import java.time.ZoneId

/*
 * Theme C "무드등" card bodies (mockup r5 C): each card sits on a warm plate washed with its function's pastel (clock
 * peach, calendar rose, weather sky, focus sage, usage lilac, music = album colour); heavy rounded numerals, soft
 * secondary ink, butter for "press me". Cards not listed in [lampBody] use the shared bodies on the plate.
 */

/** Draws [type] in the C layout; false = no C body, the caller draws the shared one. */
@Composable
internal fun lampBody(type: CardType, e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions): Boolean {
    when (type) {
        CardType.CLOCK -> if (e.look.clockFace == FaceId.DIGITAL) LampClock(e, state, config) else return false
        CardType.CALENDAR -> if (e.cls != SizeClass.L) LampDate(e) else return false
        CardType.WEATHER -> state.weather?.let { LampWeather(e, state, it, actions) } ?: return false
        CardType.TIMER -> LampTimer(e, state, config, actions)
        CardType.USAGE -> state.usage?.let { LampUsage(e, state, actions) } ?: return false
        CardType.AGENDA -> if (!state.agenda.isNullOrEmpty()) LampAgenda(e, state) else return false
        else -> return false
    }
    return true
}

/**
 * The C plate: rounded, lit from the top-left by [tint] (radial wash 22 % → 8 %), static (cached per size).
 * Night maps the tint to the candle night, so the plate sinks to the candle plate.
 */
internal fun Modifier.lampPlate(ink: Ink, tint: Color?, radius: Dp): Modifier = clip(RoundedCornerShape(radius)).drawWithCache {
    val t = tint?.let { ink.mapped(it) }
    val lit = if (t == null) ink.plate2 else lerp(ink.plate2, t, 0.22f)
    val deep = if (t == null) ink.plate else lerp(ink.plate, t, 0.08f)
    val brush = Brush.radialGradient(
        0f to lit, 0.7f to deep, 1f to deep,
        center = Offset(size.width * 0.15f, 0f), radius = maxOf(size.width, size.height) * 1.2f,
    )
    onDrawBehind { drawRect(brush) }
}

/** Dark text on a pastel fill (today's circle, butter buttons); black at night like the iPhone's red mode. */
private fun onPastel(e: Env) = lerp(Color(0xFF2B1D07), Color.Black, e.ink.night)

// ---------------------------------------------------------------- clock

/** Peach time centred on the plate, the date under it in the soft ink (mockup r5 C desk). */
@Composable
private fun LampClock(e: Env, state: StandbyState, config: AppConfig) {
    val style = config.clockStyle(FaceId.DIGITAL)
    val color = if (e.look.hero) e.look.accent else e.ink.pal.fg
    Column(Modifier.fillMaxSize().padding(e.pad), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(0.35f))
        StyledTime(
            clockText(state.now, state.use24h), style, e.ink, Modifier.fillMaxWidth().weight(1f), color = color,
            effects = e.look.hero,
        )
        if (e.atLeastS) {
            Spacer(Modifier.weight(0.25f))
            Txt(dateKo(e.today), e.label, e.ink.ink2, weight = 500, num = true, align = TextAlign.Center, maxW = e.cw)
        } else Spacer(Modifier.weight(0.35f))
    }
}

// ---------------------------------------------------------------- calendar

/** Rose weekday, big rose day, this week as one row with today circled (mockup r5 C widgets). */
@Composable
private fun LampDate(e: Env) {
    val t = e.today
    val rose = e.ink.weekday
    Column(Modifier.fillMaxSize().padding(e.pad)) {
        Txt("${dowKo(t)}요일", max(e.label, e.label * 1.1f), rose, weight = 500)
        Spacer(Modifier.height(e.gap * 2))
        FillText(AnnotatedString("${t.dayOfMonth}"), rose, e.num, e.numCap, Modifier.fillMaxWidth().weight(1f), weight = 900)
        if (e.atLeastS) {
            Spacer(Modifier.height(e.gap * 2))
            val d = min(e.label * 1.5f, e.cw / 7)
            val sunday = t.minusDays((t.dayOfWeek.value % 7).toLong())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, s ->
                    val today = sunday.plusDays(i.toLong()) == t
                    Box(
                        Modifier.size(d).clip(CircleShape).background(if (today) rose else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Txt(s, e.label, if (today) onPastel(e) else e.ink.ink2, weight = if (today) 700 else 500) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- weather

/** Sky city, colour glyph top-right, big temperature, condition + high/low (mockup r5 C widgets / modular). */
@Composable
private fun LampWeather(e: Env, state: StandbyState, wx: WeatherInfo, actions: StandbyActions) {
    val nowSec = state.now.atZone(ZoneId.systemDefault()).toEpochSecond()
    val old = nowSec - wx.fetchedAtEpochSec > 3 * 3600
    val tempCol = if (old) e.ink.ink2 else e.ink.ink
    val mod = Modifier.fillMaxSize().padding(e.pad).tap(if (wx.offline) actions::retryFeeds else null)
    val off = if (wx.offline) "${hhmm(wx.fetchedAtEpochSec)} 기준 · 연결 안 됨" else null
    // short plate (modular): temperature + glyph on one row, "서울 · 구름 조금" under it
    if (e.ch < e.label * 7) {
        Column(mod) {
            Row(Modifier.fillMaxWidth().weight(1f)) {
                FillText(temp(wx.tempC), tempCol, e.num, e.numCap, Modifier.weight(1f).fillMaxHeight().padding(start = e.gap * 1.5f, top = e.gap * 2), weight = 900, align = Alignment.TopStart)
                WxIcon(e, weatherIc(wx.condition), Modifier.size(min(e.ch * 0.45f, e.cw * 0.3f)))
            }
            Spacer(Modifier.height(e.gap * 2))
            Txt(off ?: "${wx.city} · ${wx.condition.ko}", e.label, e.ink.ink2, weight = 500, num = true, maxW = e.cw)
        }
        return
    }
    Column(mod) {
        // city top-left, the big glyph top-right, the temperature under the city (glyph and digits never share x)
        val g = min(e.ch * 0.3f, e.cw * 0.36f)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Txt(wx.city, e.label, e.ink.blue, Modifier.align(Alignment.TopStart), weight = 500)
            WxIcon(e, weatherIc(wx.condition), Modifier.align(Alignment.TopEnd).size(g))
            FillText(
                temp(wx.tempC), tempCol, e.num, e.numCap,
                Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(top = e.labelCap + e.gap * 4, end = g * 0.4f).fillMaxHeight(),
                weight = 900, align = Alignment.BottomStart,
            )
        }
        if (e.atLeastS) {
            Spacer(Modifier.height(e.gap * 3))
            Txt(off ?: wx.condition.ko, e.label, if (off != null) e.ink.ink2 else e.ink.ink, weight = 500, num = true, maxW = e.cw)
            Spacer(Modifier.height(e.gap * 1.5f))
            Txt("최고 ${wx.highC}° · 최저 ${wx.lowC}°", e.label, e.ink.ink2, weight = 500, num = true, maxW = e.cw)
        }
    }
}

// ---------------------------------------------------------------- timer

/** Sage progress ring (play/pause inside) + "집중" over the big time (mockup r5 C desk). */
@Composable
private fun LampTimer(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val total0 = config.focusMinutes * 60
    val t = state.timers[e.slotId] ?: TimerState(leftSec = total0, totalSec = total0)
    val mode = if (t.mode == TimerMode.FOCUS) "집중" else "휴식"
    val paused = !t.running && t.leftSec < t.totalSec
    val digits = "%02d:%02d".format(t.leftSec / 60, t.leftSec % 60)
    val frac = if (t.totalSec > 0) 1f - t.leftSec.toFloat() / t.totalSec else 0f
    val sage = e.ink.green
    val toggle = { actions.timerToggle(e.slotId) }
    val side = e.w >= e.h * 0.9f
    val ring = @Composable { d: Dp ->
        Box(Modifier.size(d).tap(toggle), contentAlignment = Alignment.Center) {
            val track = lerp(e.ink.plate, sage, 0.2f)
            Canvas(Modifier.fillMaxSize()) {
                val sw = size.minDimension * 0.15f
                val tl = Offset(sw / 2, sw / 2)
                val sz = Size(size.width - sw, size.height - sw)
                drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(sw))
                drawArc(sage, -90f, 360f * frac.coerceIn(0.005f, 1f), false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
            }
            IconView(if (t.running) Ic.PAUSE else Ic.PLAY, sage, Modifier.size(d * 0.3f))
        }
    }
    val text = @Composable { mod: Modifier, align: Alignment.Horizontal ->
        Column(mod, horizontalAlignment = align, verticalArrangement = Arrangement.Center) {
            Txt(if (paused) "$mode · 멈춤" else mode, e.label, sage, weight = 500)
            Spacer(Modifier.height(e.gap * 1.5f))
            Txt(digits, e.sz.value, e.ink.ink, weight = 900, family = e.num, cap = e.numCap, tnum = true, maxW = if (side) e.cw - min(e.ch, e.cw * 0.44f) - e.gap * 2.5f else e.cw)
            if (t.leftSec < t.totalSec && e.atLeastM) {
                Spacer(Modifier.height(e.gap * 2))
                Txt("처음으로", e.label, e.ink.ink2, Modifier.tap { actions.timerReset(e.slotId) }, weight = 500)
            }
        }
    }
    if (side) Row(Modifier.fillMaxSize().padding(e.pad), verticalAlignment = Alignment.CenterVertically) {
        ring(min(e.ch, e.cw * 0.44f))
        Spacer(Modifier.width(e.gap * 2.5f))
        text(Modifier.weight(1f), Alignment.Start)
    } else Column(Modifier.fillMaxSize().padding(e.pad), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
        ring(min(e.cw, e.ch * 0.5f))
        text(Modifier, Alignment.CenterHorizontally)
    }
}

// ---------------------------------------------------------------- usage

/** "Claude 20%" over a fat lilac bar, then Codex (mockup r5 C desk). ≥ 90 % turns rose. */
@Composable
private fun LampUsage(e: Env, state: StandbyState, actions: StandbyActions) {
    val u = state.usage!!
    val nowSec = state.now.atZone(ZoneId.systemDefault()).toEpochSecond()
    val old = nowSec - u.fetchedAtEpochSec > 3 * 3600
    val bar = max(10.dp, e.label * 0.6f)
    val rows = listOf("Claude" to u.claude5h, "Codex" to u.codex5h)
    val foot = e.atLeastM && e.ch > (e.labelCap + bar) * 2 + e.labelCap + e.gap * 14
    Column(Modifier.fillMaxSize().padding(e.pad).tap(if (u.offline) actions::retryFeeds else null), verticalArrangement = Arrangement.SpaceEvenly) {
        rows.forEach { (n, w) ->
            val pct = w?.usedPct ?: 0
            val col = when { pct >= 90 -> e.ink.weekday; old -> e.ink.ink2; else -> e.ink.lilac }
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Txt(n, e.label, e.ink.ink, Modifier.weight(1f), weight = 800, family = e.num, cap = e.numCap, maxW = e.cw * 0.6f)
                    Txt(pctOf(w), e.label, col, weight = 900, family = e.num, cap = e.numCap, tnum = true)
                }
                Spacer(Modifier.height(e.gap * 2))
                Box(Modifier.fillMaxWidth().height(bar).clip(RoundedCornerShape(50)).background(lerp(e.ink.plate, col, 0.18f))) {
                    Box(Modifier.fillMaxWidth((pct / 100f).coerceIn(0.03f, 1f)).height(bar).clip(RoundedCornerShape(50)).background(col))
                }
            }
        }
        if (foot) Txt(
            if (u.offline) "${hhmm(u.fetchedAtEpochSec)} 기준 · 연결 안 됨" else "5시간 사용량 · ${hhmm(u.fetchedAtEpochSec)}",
            e.label, e.ink.ink2, weight = 500, num = true, maxW = e.cw,
        )
    }
}

// ---------------------------------------------------------------- agenda

/** "09:30 ● 팀 회의" rows: rounded times, a dot in the calendar's colour, the title (mockup r5 C modular). */
@Composable
private fun LampAgenda(e: Env, state: StandbyState) {
    val items = state.agenda!!
    val step = e.labelCap + e.gap * 4.5f
    val fit = ((e.ch + e.gap * 4.5f) / step).toInt().coerceIn(1, 4)
    Column(Modifier.fillMaxSize().padding(e.pad), verticalArrangement = Arrangement.SpaceEvenly) {
        val narrow = e.cw < e.label * 8
        items.take(if (narrow) ((e.ch + e.gap * 4.5f) / (step + e.labelCap + e.gap)).toInt().coerceIn(1, 4) else fit).forEach {
            if (narrow) Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(e.label * 0.36f).clip(CircleShape).background(e.ink.mapped(Color(it.color))))
                    Spacer(Modifier.width(e.label * 0.35f))
                    Txt(it.start, e.label, e.ink.ink2, weight = 800, family = e.num, cap = e.numCap, tnum = true)
                }
                Spacer(Modifier.height(e.gap))
                Txt(it.title, e.label, e.ink.ink, weight = 500)
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                Txt(it.start, e.label, e.ink.ink2, weight = 800, family = e.num, cap = e.numCap, tnum = true)
                Spacer(Modifier.width(e.label * 0.4f))
                Box(Modifier.size(e.label * 0.36f).clip(CircleShape).background(e.ink.mapped(Color(it.color))))
                Spacer(Modifier.width(e.label * 0.4f))
                Txt(it.title, e.label, e.ink.ink, weight = 500)
            }
        }
    }
}
