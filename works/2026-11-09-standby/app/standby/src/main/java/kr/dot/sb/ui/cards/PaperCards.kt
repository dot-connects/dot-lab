package kr.dot.sb.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
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
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.SizeClass
import java.time.ZoneId

/*
 * Theme B "신문 1면" card bodies (mockup r5 B): no plates, a tracked grey kicker on top of every card, big serif
 * numerals, bold Korean headlines, one vermilion accent (ink.weekday). Rules between cards are drawn by the board.
 * Cards not listed in [paperBody] use the shared bodies in the theme's tokens.
 */

/** Draws [type] in the B layout; false = no B body, the caller draws the shared one. */
@Composable
internal fun paperBody(type: CardType, e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions): Boolean {
    when (type) {
        CardType.TIMER -> PaperTimer(e, state, config, actions)
        CardType.USAGE -> state.usage?.let { PaperUsage(e, state, actions) } ?: return false
        else -> return false
    }
    return true
}

/** Text (digits) as large as the box allows: cap = box height, shrunk to the box width. */
@Composable
internal fun FillText(
    text: AnnotatedString, color: Color, family: FontFamily, cap: Float, modifier: Modifier, weight: Int = 400,
    align: Alignment = Alignment.CenterStart, maxCap: Dp = Dp.Unspecified,
) {
    BoxWithConstraints(modifier, contentAlignment = align) {
        val c = if (maxCap == Dp.Unspecified) maxHeight else min(maxHeight, maxCap)
        Txt(text, c / cap, color, weight = weight, family = family, cap = cap, tnum = true, maxW = maxWidth, maxCapH = c)
    }
}

private fun headSize(e: Env): Dp = max(e.label, min(e.sz.title, e.label * 1.2f))

// ---------------------------------------------------------------- timer

/** "집중 · 남은 시간" / big serif mm:ss / accent bar on a rule track (mockup r5 B desk). Tap = start/pause. */
@Composable
private fun PaperTimer(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val total0 = config.focusMinutes * 60
    val t = state.timers[e.slotId] ?: TimerState(leftSec = total0, totalSec = total0)
    val mode = if (t.mode == TimerMode.FOCUS) "집중" else "휴식"
    val paused = !t.running && t.leftSec < t.totalSec
    val digits = "%02d:%02d".format(t.leftSec / 60, t.leftSec % 60)
    val frac = if (t.totalSec > 0) 1f - t.leftSec.toFloat() / t.totalSec else 0f
    val toggle = { actions.timerToggle(e.slotId) }
    Column(Modifier.fillMaxSize().padding(e.pad)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            val roomy = e.cw >= e.label * 7.5f
            val started = t.leftSec < t.totalSec && roomy
            Kicker(e, if (paused) "$mode · 멈춤" else if (started || !roomy) mode else "$mode · 남은 시간", modifier = Modifier.weight(1f))
            // reset as a word, like the B transport ("이전 멈춤 다음")
            if (started) Txt("처음으로", e.label, e.ink.ink2, Modifier.tap { actions.timerReset(e.slotId) }, weight = 500, tracking = e.t.labelTracking)
        }
        Spacer(Modifier.height(e.gap * 2))
        FillText(
            AnnotatedString(digits), if (paused) e.ink.ink2 else e.ink.ink, e.num, e.numCap,
            Modifier.fillMaxWidth().weight(1f).tap(toggle),
        )
        Spacer(Modifier.height(e.gap * 2.5f))
        val bar = max(4.dp, e.s * 0.03f)
        Box(Modifier.fillMaxWidth().height(bar).background(e.ink.ink3).tap(toggle)) {
            Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(bar).background(e.ink.weekday))
        }
    }
}

// ---------------------------------------------------------------- usage

/** "사용량 · 5시간 창" / "Claude ······ 20%" table rows with dotted leaders / "21:40 기준" (mockup r5 B desk). */
@Composable
private fun PaperUsage(e: Env, state: StandbyState, actions: StandbyActions) {
    val u = state.usage!!
    val nowSec = state.now.atZone(ZoneId.systemDefault()).toEpochSecond()
    val old = nowSec - u.fetchedAtEpochSec > 3 * 3600
    val rows = listOf("Claude" to u.claude5h, "Codex" to u.codex5h)
    val name = headSize(e)
    val tall = e.cls >= SizeClass.S
    val narrow = e.cw < e.label * 9
    Column(Modifier.fillMaxSize().padding(e.pad).tap(if (u.offline) actions::retryFeeds else null)) {
        if (tall) {
            Kicker(e, if (narrow) "사용량" else if (u.offline) "사용량 · 연결 안 됨" else "사용량 · 5시간 창")
            Spacer(Modifier.height(e.gap * 2))
        }
        Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            rows.forEach { (n, w) ->
                val pct = w?.usedPct
                val hot = (pct ?: 0) >= 90
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Txt(n, if (narrow) e.label else name, e.ink.ink, weight = 700, maxW = e.cw * 0.5f)
                    if (narrow) Spacer(Modifier.weight(1f)) else Leader(e, Modifier.weight(1f).padding(horizontal = e.label * 0.3f))
                    Txt(
                        pctOf(w), (if (narrow) e.label * 1.6f else name * 1.9f), if (hot) e.ink.weekday else if (old) e.ink.ink2 else e.ink.ink,
                        family = e.num, cap = e.numCap, weight = 400, maxW = e.cw * 0.45f,
                    )
                }
            }
        }
        if (tall) {
            Spacer(Modifier.height(e.gap * 2))
            Kicker(e, "${hhmm(u.fetchedAtEpochSec)} 기준")
        }
    }
}

/** Dotted leader on the baseline of a table row. */
@Composable
private fun Leader(e: Env, modifier: Modifier) {
    val c = e.ink.ink2
    Canvas(modifier.height(max(2.dp, e.label * 0.12f))) {
        val r = size.height / 2
        var x = r
        while (x < size.width) { drawCircle(c, r * 0.7f, Offset(x, size.height / 2)); x += r * 4f }
    }
}

/** Latin runs of a title in the italic serif (Hangul stays in Pretendard): "Ditto" set like a cover line. */
internal fun italicSpans(text: String): AnnotatedString = numSpans(text, Fonts.instrumentItalic, 400)
