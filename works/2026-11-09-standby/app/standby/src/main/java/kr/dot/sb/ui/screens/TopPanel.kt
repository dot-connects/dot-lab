package kr.dot.sb.ui.screens

import android.media.AudioManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Theme
import kr.dot.sb.settings.PreviewDevice
import kr.dot.sb.settings.PreviewFrame
import kr.dot.sb.settings.widgetsBoard
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.cards.LocalLiveWidgets
import kr.dot.sb.ui.cards.tap
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.LocalInk
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.ProvideTheme
import kr.dot.sb.ui.theme.SCRIM
import kr.dot.sb.ui.theme.themedSurface
import kr.dot.sb.ui.theme.withTheme
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** The pull-and-slide maps sideways travel over 60 % of the width (at most 480 dp) to the whole brightness range. */
internal fun barWidth(w: Dp): Dp = min(w * 0.6f, 480.dp)

/** Finger x (dp from the left of the screen) to 0..1 along [barWidth], centred. */
internal fun barFraction(xDp: Float, screenWDp: Float): Float {
    val bw = minOf(screenWDp * 0.6f, 480f)
    return ((xDp - (screenWDp - bw) / 2f) / bw).coerceIn(0f, 1f)
}

/**
 * Pull-down panel (spec-r9 §1, mockup "내리기 패널"): brightness, media volume and theme previews (shapes only, three in
 * view, the rest by scrolling) on the theme plate over a dim wash. Ink is the board's, so night is the candle night and
 * the previews are drawn at night too. [onPress] reports any finger on the panel so the owner keeps it open.
 */
@Composable
internal fun TopPanel(
    level: Float, onLevel: (Float) -> Unit, onPress: (Boolean) -> Unit, onTheme: (Theme) -> Unit,
    state: StandbyState, config: AppConfig, w: Dp, h: Dp, modifier: Modifier = Modifier,
) {
    val ink = LocalInk.current
    val t = LocalTheme.current
    val m = Grid.margin(min(w, h))
    val pw = min(w - m * 2, 520.dp)
    val pad = 14.dp
    val shape = RoundedCornerShape(28.dp)
    val ctx = LocalContext.current
    val audio = remember { ctx.getSystemService(AudioManager::class.java) }
    val maxVol = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC) / maxVol.toFloat()) }
    Box(Modifier.fillMaxSize().background(SCRIM))
    Column(
        modifier.padding(top = m * 0.6f).width(pw)
            .background(ink.bg.copy(alpha = 0.94f), shape).themedSurface(t, ink, shape).padding(pad)
            .pointerInput(Unit) {
                // any finger anywhere on the panel holds it open; the 4 s start when the last one lifts
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    onPress(true)
                    while (awaitPointerEvent(PointerEventPass.Initial).changes.any { it.pressed }) Unit
                    onPress(false)
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PanelSlider(level, onLevel) { c -> sun(c) }
        PanelSlider(vol, { v ->
            vol = v
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (v * maxVol).roundToInt(), 0)
        }) { c -> speaker(c) }
        val chipW = (pw - pad * 2 - 16.dp) / 3
        val list = rememberLazyListState((Theme.entries.indexOf(config.theme) - 1).coerceAtLeast(0))
        LazyRow(Modifier.fillMaxWidth().padding(top = 2.dp), list, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(Theme.entries) { th ->
                val chip = RoundedCornerShape(16.dp)
                val on = th == config.theme
                val cfg = remember(config, th) { config.withTheme(th) }
                Box(
                    Modifier.width(chipW).clip(chip)
                        .then(if (on) Modifier.border(2.dp, ink.ink, chip) else Modifier.background(ink.ink.copy(alpha = 0.06f)))
                        .padding(6.dp),
                ) {
                    // previews never host a live phone widget (one view per widget id, D6)
                    CompositionLocalProvider(LocalLiveWidgets provides false) {
                        ProvideTheme(cfg) {
                            PreviewFrame(PreviewDevice.FOLD_COVER.w, PreviewDevice.FOLD_COVER.h, Modifier.fillMaxWidth(), radius = 10.dp, night = state.night) {
                                BoardView(widgetsBoard(cfg), state, cfg, StandbyActions.NONE, Modifier.fillMaxSize())
                            }
                        }
                    }
                    Box(Modifier.matchParentSize().testTag("theme-${th.name}").tap { onTheme(th) })
                }
            }
        }
    }
}

/** A 48 dp pill slider: track ink 16 %, fill ink, glyph at the left end (page colour on the fill). Touch or drag sets it. */
@Composable
private fun PanelSlider(value: Float, onValue: (Float) -> Unit, glyph: DrawScope.(Color) -> Unit) {
    val ink = LocalInk.current
    Box(
        Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(ink.ink.copy(alpha = 0.16f))
            .pointerInput(Unit) {
                val widthPx = size.width.toFloat()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onValue((down.position.x / widthPx).coerceIn(0f, 1f))
                    down.consume()
                    while (true) {
                        val c = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                        if (!c.pressed) break
                        onValue((c.position.x / widthPx).coerceIn(0f, 1f))
                        c.consume()
                    }
                }
            },
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0f, 1f)).background(ink.ink))
        val c = if (value > 0.12f) ink.on(ink.ink) else ink.ink
        Canvas(Modifier.align(Alignment.CenterStart).padding(start = 14.dp).size(20.dp)) { glyph(c) }
    }
}

private fun DrawScope.sun(color: Color) {
    val c = Offset(size.width / 2, size.height / 2)
    val r = size.minDimension
    drawCircle(color, r * 0.2f, c)
    for (i in 0 until 8) {
        val a = Math.PI / 4 * i
        val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
        drawLine(color, Offset(c.x + dx * r * 0.33f, c.y + dy * r * 0.33f), Offset(c.x + dx * r * 0.48f, c.y + dy * r * 0.48f), 1.6.dp.toPx(), StrokeCap.Round)
    }
}

/** Speaker + two waves (mockup glyph, 24-unit grid). */
private fun DrawScope.speaker(color: Color) {
    val u = size.minDimension / 24f
    drawPath(
        Path().apply {
            moveTo(3.5f * u, 9f * u); lineTo(7.5f * u, 9f * u); lineTo(12.5f * u, 4.5f * u)
            lineTo(12.5f * u, 19.5f * u); lineTo(7.5f * u, 15f * u); lineTo(3.5f * u, 15f * u); close()
        },
        color,
    )
    for (r in listOf(5f, 8.5f)) {
        drawArc(color, -45f, 90f, false, Offset((12f - r) * u, (12f - r) * u), Size(2 * r * u, 2 * r * u), style = Stroke(2f * u, cap = StrokeCap.Round))
    }
}
