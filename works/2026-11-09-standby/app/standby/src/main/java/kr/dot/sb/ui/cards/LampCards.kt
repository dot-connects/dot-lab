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
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.state.TimerMode
import kr.dot.sb.ui.state.TimerState
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.SizeClass
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
        CardType.TIMER -> LampTimer(e, state, config, actions)
        CardType.USAGE -> state.usage?.let { LampUsage(e, state, actions) } ?: return false
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
