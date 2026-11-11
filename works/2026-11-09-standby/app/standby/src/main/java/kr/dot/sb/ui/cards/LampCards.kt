package kr.dot.sb.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Ink
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
