package kr.dot.sb.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.FontId
import kr.dot.sb.ui.cards.Ic
import kr.dot.sb.ui.cards.IconView
import kr.dot.sb.ui.cards.Txt
import kr.dot.sb.ui.cards.capOf
import kr.dot.sb.ui.cards.nightFilter
import kr.dot.sb.ui.cards.tap
import kr.dot.sb.ui.state.AlarmRinging
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.LiveActivity
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalInk
import kr.dot.sb.ui.theme.Motion
import java.time.ZoneId
import kotlin.math.roundToInt

/** Digits on interrupt screens: Inter, like iPhone's SF time on these layers. */
private val TIME_FONT = FontId.BOLD
private const val TIME_WEIGHT = 600

/**
 * Interrupt layers over the pages (design §4.4) and the two pills that open them. One layer at a time by priority
 * ([topLayer]); each fades 350 ms; a lower one keeps its state and comes back when the higher one ends.
 */
@Composable
internal fun Overlays(
    state: StandbyState,
    config: AppConfig,
    actions: StandbyActions,
    layer: Layer,
    editing: Boolean,
    onNowPlaying: (Boolean) -> Unit,
) {
    val bare = layer == Layer.NONE && !editing
    Faded(bare && state.live != null, state.live) { LivePill(it, actions) }
    Faded(bare && state.media?.playing == true, state.media) { WavePill { onNowPlaying(true) } }
    Faded(layer == Layer.NOW_PLAYING, state.media) { NowPlaying(it, actions) { onNowPlaying(false) } }
    Faded(layer == Layer.LIVE, state.live) { LiveFull(it, actions) }
    Faded(layer == Layer.NOTIFICATION, state.notification) { NotificationScreen(it, it.hidden || config.previewOnTapOnly, state, actions) }
    Faded(layer == Layer.ALARM, state.alarm) { AlarmScreen(it, state, actions) }
}

/** 350 ms fade that keeps showing the last value while it fades out after the state went null. */
@Composable
private fun <T : Any> Faded(visible: Boolean, value: T?, content: @Composable (T) -> Unit) {
    var last by remember { mutableStateOf(value) }
    if (value != null && value != last) last = value
    AnimatedVisibility(
        visible && value != null,
        enter = fadeIn(tween(Motion.LAYER_FADE_MS)), exit = fadeOut(tween(Motion.LAYER_FADE_MS)),
    ) { last?.let { Box(Modifier.fillMaxSize()) { content(it) } } }
}

// ------------------------------------------------------------------------------------------------- alarm

/**
 * Alarm (design §4.4, frame duo-alarm): "알람" + icon top-left in acc, time top-right with its cap-top on the same
 * line, Snooze / Stop pills along the bottom (27 % H). Portrait: time under the header, pills stacked (13 % H).
 */
@Composable
private fun AlarmScreen(a: AlarmRinging, state: StandbyState, actions: StandbyActions) {
    val ink = LocalInk.current
    BoxWithConstraints(Modifier.fillMaxSize().background(ink.bg).tap {}) {
        val w = maxWidth
        val h = maxHeight
        val port = h > w
        val s = min(w, h)
        val pad = screenPadding(w, h)
        val label = max(20.dp, s * 0.06f)
        val time = clockText(state.now, state.use24h)
        val snooze = lerp(Color(0xFFF2A65E), Color(0xFF7A0000), ink.night)
        Box(Modifier.fillMaxSize().padding(pad)) {
            val cw = w - pad.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) * 2
            val title = if (a.label.isBlank() || a.label == "알람") "알람" else "알람 · ${a.label}"
            val header = @Composable { HeaderRow(Ic.ALARM, title, label, ink.acc, weight = 700) }
            if (!port) {
                Box(Modifier.align(Alignment.TopStart)) { header() }
                Txt(
                    time, h * 0.25f / capOf(TIME_FONT), ink.ink, Modifier.align(Alignment.TopEnd),
                    family = Fonts.clock(TIME_FONT, TIME_WEIGHT), cap = capOf(TIME_FONT), tnum = true, maxW = cw * 0.6f,
                )
                Row(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(h * 0.27f),
                    horizontalArrangement = Arrangement.spacedBy(w * 0.04f),
                ) {
                    if (a.canSnooze) AlarmPill("다시 알림", snooze, ink.ink, Modifier.weight(1f).fillMaxHeight(), actions::alarmSnooze)
                    AlarmPill("중단", ink.ink3, ink.ink, Modifier.weight(1f).fillMaxHeight(), actions::alarmStop)
                }
            } else {
                Column(Modifier.align(Alignment.TopStart)) {
                    header()
                    Spacer(Modifier.height(h * 0.04f))
                    Txt(
                        time, h * 0.16f / capOf(TIME_FONT), ink.ink,
                        family = Fonts.clock(TIME_FONT, TIME_WEIGHT), cap = capOf(TIME_FONT), tnum = true, maxW = cw,
                    )
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(h * 0.025f)) {
                    if (a.canSnooze) AlarmPill("다시 알림", snooze, ink.ink, Modifier.fillMaxWidth().height(h * 0.13f), actions::alarmSnooze)
                    AlarmPill("중단", ink.ink3, ink.ink, Modifier.fillMaxWidth().height(h * 0.13f), actions::alarmStop)
                }
            }
        }
    }
}

@Composable
private fun AlarmPill(text: String, bg: Color, fg: Color, modifier: Modifier, onClick: () -> Unit) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(50)).background(bg).tap(onClick), contentAlignment = Alignment.Center) {
        Txt(text, max(22.dp, maxHeight * 0.3f).coerceAtMost(maxHeight * 0.42f), fg, weight = 700, maxW = maxWidth * 0.8f)
    }
}

/** Icon (box = text size, centred on the cap) + one line of text. Height = the text's cap. */
@Composable
private fun HeaderRow(icon: Ic, text: String, size: Dp, color: Color, weight: Int = 600) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.height(size * 0.707f).wrapContentHeight(unbounded = true)) { IconView(icon, color, Modifier.size(size * 1.05f)) }
        Spacer(Modifier.width(size * 0.4f))
        Txt(text, size, color, weight = weight)
    }
}

// ------------------------------------------------------------------------------------------------- notification

/**
 * Two-step notification (design §2.5, §4.4): the plate pops in centred (Ø 30 % s), holds, then shrinks to its mini
 * spot (Ø 14 % s, top-leading of a 78 % W text block); only then the text fades in, so nothing ever overlaps the plate.
 * Closes itself after 6 s. Tap = open (after unlock).
 */
@Composable
private fun NotificationScreen(n: BigNotification, hidden: Boolean, state: StandbyState, actions: StandbyActions) {
    val ink = LocalInk.current
    val act by rememberUpdatedState(actions)
    val appear = remember(n.key) { Animatable(0f) }
    val morph = remember(n.key) { Animatable(0f) }
    val text = remember(n.key) { Animatable(0f) }
    LaunchedEffect(n.key) {
        appear.animateTo(1f, tween(Motion.NOTIF_ICON_IN_MS, easing = FastOutSlowInEasing))
        delay(Motion.NOTIF_HOLD_MS.toLong())
        morph.animateTo(1f, tween(Motion.NOTIF_SHRINK_MS, easing = FastOutSlowInEasing))
        text.animateTo(1f, tween(Motion.NOTIF_TEXT_MS))
        delay((Motion.NOTIF_CLOSE_MS - Motion.NOTIF_ICON_IN_MS - Motion.NOTIF_HOLD_MS - Motion.NOTIF_SHRINK_MS - Motion.NOTIF_TEXT_MS).toLong())
        act.notificationDismiss(n.key)
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var mini by remember { mutableStateOf<Offset?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().background(ink.bg).onPlaced { origin = it.positionInRoot() }.tap { act.notificationTap(n.key) }) {
        val w = maxWidth
        val h = maxHeight
        val s = min(w, h)
        val big = s * 0.30f
        val small = s * 0.14f
        Column(Modifier.align(Alignment.Center).width(w * 0.78f).graphicsLayer { alpha = text.value }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.size(small).onPlaced { mini = it.positionInRoot() })
                Spacer(Modifier.width(s * 0.045f))
                Column {
                    Txt(if (hidden) n.appLabel else n.sender, max(24.dp, s * 0.08f), ink.ink, weight = 700, maxW = w * 0.78f - small - s * 0.05f)
                    Spacer(Modifier.height(s * 0.028f))
                    Txt(if (hidden) "탭하여 보기" else "${n.appLabel} · ${ago(n.postedAtEpochMs, state)}", max(18.dp, s * 0.045f), ink.ink2, weight = 500)
                }
            }
            if (!hidden) {
                Spacer(Modifier.height(s * 0.05f))
                Txt(n.text, max(24.dp, s * 0.072f), ink.ink, weight = 700, lines = 3)
            }
        }
        // The plate, drawn at `big` and scaled; position and size interpolate centre → mini.
        val density = LocalDensity.current
        val bigPx = with(density) { big.toPx() }
        val smallPx = with(density) { small.toPx() }
        val wPx = with(density) { w.toPx() }
        val hPx = with(density) { h.toPx() }
        Box(
            Modifier.size(big)
                .offset {
                    val m = morph.value
                    val size = lerpF(bigPx * (0.6f + 0.4f * appear.value), smallPx, m)
                    val target = (mini ?: Offset((wPx - smallPx) / 2, (hPx - smallPx) / 2) + origin) - origin
                    val cx = lerpF(wPx / 2, target.x + smallPx / 2, m)
                    val cy = lerpF(hPx / 2, target.y + smallPx / 2, m)
                    IntOffset((cx - size / 2).roundToInt(), (cy - size / 2).roundToInt())
                }
                .graphicsLayer {
                    val m = morph.value
                    val sc = lerpF(0.6f + 0.4f * appear.value, smallPx / bigPx, m)
                    scaleX = sc; scaleY = sc
                    transformOrigin = TransformOrigin(0f, 0f)
                    alpha = appear.value
                }
                .clip(RoundedCornerShape(28))
                .background(ink.mapped(Color(n.plateColor))),
            contentAlignment = Alignment.Center,
        ) {
            val icon = n.icon
            if (icon != null) Image(icon, null, Modifier.fillMaxSize(0.62f), contentScale = ContentScale.Fit, colorFilter = nightFilter(ink.night))
            else Txt((n.sender.ifBlank { n.appLabel }).take(1), big * 0.42f, lerp(Color.White, Color.Black, ink.night), weight = 700)
        }
    }
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun ago(epochMs: Long, state: StandbyState): String {
    val now = state.now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val min = ((now - epochMs) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "방금"
        min < 60 -> "${min}분 전"
        else -> "${min / 60}시간 전"
    }
}

// ------------------------------------------------------------------------------------------------- live activity

/** Live Activity pill top-centre (design §4.4): ink4 plate, icon + short value in acc. Tap → 2× view. */
@Composable
private fun LivePill(l: LiveActivity, actions: StandbyActions) {
    val ink = LocalInk.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val s = min(maxWidth, maxHeight)
        val t = max(18.dp, s * 0.042f)
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = max(maxHeight * 0.03f, screenPadding(maxWidth, maxHeight).calculateTopPadding() - t * 1.2f))
                .clip(RoundedCornerShape(50)).background(ink.ink4).tap(actions::liveToggle)
                .padding(horizontal = t * 0.8f, vertical = t * 0.5f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderRow(Ic.TIMER, l.value, t, ink.acc, weight = 700)
        }
    }
}

/** Live Activity at 2× (design §4.4): header label + icon, value `display`, 4-step bar (acc / ink3), detail. */
@Composable
private fun LiveFull(l: LiveActivity, actions: StandbyActions) {
    val ink = LocalInk.current
    BoxWithConstraints(Modifier.fillMaxSize().background(ink.bg).tap(actions::liveToggle)) {
        val w = maxWidth
        val h = maxHeight
        val s = min(w, h)
        val pad = screenPadding(w, h)
        val label = max(20.dp, s * 0.055f)
        Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                HeaderRow(Ic.TIMER, l.appLabel, label, ink.ink2)
                Spacer(Modifier.height(s * 0.05f))
                Txt(l.title, max(24.dp, s * 0.09f), ink.ink, weight = 700)
            }
            Txt(
                l.value, s * 0.30f, ink.ink, family = Fonts.clock(TIME_FONT, TIME_WEIGHT), cap = capOf(TIME_FONT),
                tnum = true, maxW = w * 0.9f,
            )
            Column {
                l.progress?.let { p ->
                    val bar = max(6.dp, s * 0.022f)
                    val on = (p.coerceIn(0f, 1f) * l.steps).roundToInt().coerceAtLeast(if (p > 0f) 1 else 0)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(bar)) {
                        repeat(l.steps) {
                            Box(Modifier.weight(1f).height(bar).clip(RoundedCornerShape(50)).background(if (it < on) ink.acc else ink.ink3))
                        }
                    }
                    Spacer(Modifier.height(s * 0.06f))
                }
                Txt(l.detail, label, ink.ink2, weight = 500)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------- now playing

/** Waveform pill top-right while something plays. The bars step ~6×/s and only while this pill is composed. */
@Composable
private fun WavePill(onOpen: () -> Unit) {
    val ink = LocalInk.current
    var phase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { while (true) { delay(160); phase += 1f } }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val s = min(maxWidth, maxHeight)
        val d = max(40.dp, s * 0.1f)
        val pad = screenPadding(maxWidth, maxHeight)
        Box(
            Modifier.align(Alignment.TopEnd).padding(top = pad.calculateTopPadding() * 0.6f, end = pad.calculateTopPadding() * 0.6f)
                .size(d).clip(CircleShape).background(ink.ink4).tap(onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(d * 0.46f)) {
                val n = 4
                val bw = size.width / (n * 1.8f)
                val step = (size.width - bw) / (n - 1)
                for (i in 0 until n) {
                    val k = 0.35f + 0.65f * ((kotlin.math.sin(phase * 0.9f + i * 1.7f) + 1f) / 2f)
                    val bh = size.height * k
                    drawRoundRect(ink.acc, Offset(i * step, (size.height - bh) / 2), Size(bw, bh), CornerRadius(bw / 2))
                }
            }
        }
    }
}

/**
 * Now Playing (design §4.4, spec I11): art left x 9–42 %, title/artist, transport and progress in the right column
 * sharing the art's top and bottom lines; volume glyph top-right. Portrait: art on top. Swipe down or 5 s after
 * pause closes. Position is interpolated per second between the 5 s updates, only while this screen is up.
 */
@Composable
private fun NowPlaying(m: MediaInfo, actions: StandbyActions, onClose: () -> Unit) {
    val ink = LocalInk.current
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(m.playing) { if (!m.playing) { delay(5_000); close() } }
    var pos by remember { mutableLongStateOf(m.positionMs) }
    LaunchedEffect(m.positionMs, m.playing) {
        pos = m.positionMs
        while (m.playing && pos < m.durationMs) { delay(1_000); pos = (pos + 1_000).coerceAtMost(m.durationMs) }
    }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(ink.bg).tap {}.pointerInput(Unit) {
            var dy = 0f
            detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = { if (dy > size.height * 0.15f) close() }) { _, d -> dy += d }
        },
    ) {
        val w = maxWidth
        val h = maxHeight
        val s = min(w, h)
        val port = h > w
        val title = max(24.dp, s * 0.072f)
        val detail = max(18.dp, s * 0.05f)
        val small = max(16.dp, s * 0.036f)
        val pad = screenPadding(w, h)
        IconView(
            Ic.SOUND, ink.ink2,
            Modifier.align(Alignment.TopEnd).padding(top = pad.calculateTopPadding(), end = w * 0.06f).size(max(22.dp, s * 0.055f)),
        )
        val art: Dp
        val artX: Dp
        val artY: Dp
        if (port) {
            art = min(w * 0.82f, h * 0.48f)
            artX = (w - art) / 2
            artY = h * 0.11f
        } else {
            art = min(w * 0.33f, h * 0.69f)
            artX = w * 0.09f
            artY = h * 0.485f - art / 2
        }
        val shape = RoundedCornerShape(max(12.dp, art * 0.06f))
        Box(Modifier.offset(artX, artY).size(art).clip(shape).background(ink.ink4), contentAlignment = Alignment.Center) {
            val img = m.art
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = nightFilter(ink.night))
            else IconView(Ic.NOTE, ink.ink3, Modifier.fillMaxSize(0.4f), ink.ink4)
        }
        val col = if (port) Modifier.offset(artX, artY + art + h * 0.05f).size(art, h * 0.92f - artY - art - h * 0.05f)
        else Modifier.offset(w * 0.5f, artY).size(w * 0.40f, art)
        Column(col, verticalArrangement = Arrangement.SpaceBetween) {
            Column {
                Txt(m.title, title, ink.ink, weight = 700)
                Spacer(Modifier.height(s * 0.03f))
                Txt(m.artist, detail, ink.ink2, weight = 500)
            }
            val play = title * 2f
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                Btn(Ic.PREV, play * 0.62f, m.canPrev, ink, actions::mediaPrev)
                Btn(if (m.playing) Ic.PAUSE else Ic.PLAY, play, true, ink, actions::mediaPlayPause)
                Btn(Ic.NEXT, play * 0.62f, m.canNext, ink, actions::mediaNext)
            }
            Column {
                val frac = if (m.durationMs > 0) pos.toFloat() / m.durationMs else 0f
                val bar = max(5.dp, s * 0.014f)
                Box(Modifier.fillMaxWidth().height(bar).clip(RoundedCornerShape(50)).background(ink.ink3)) {
                    Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(bar).clip(RoundedCornerShape(50)).background(ink.ink))
                }
                Spacer(Modifier.height(s * 0.035f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Txt(mmss(pos), small, ink.ink2, weight = 500, tnum = true)
                    Txt("-" + mmss((m.durationMs - pos).coerceAtLeast(0)), small, ink.ink2, weight = 500, tnum = true)
                }
            }
        }
    }
}

@Composable
private fun Btn(ic: Ic, size: Dp, enabled: Boolean, ink: Ink, onClick: () -> Unit) {
    Box(Modifier.size(size).tap(if (enabled) onClick else null)) {
        IconView(ic, if (enabled) ink.ink else ink.ink3, Modifier.fillMaxSize())
    }
}

private fun mmss(ms: Long): String { val t = ms / 1000; return "%d:%02d".format(t / 60, t % 60) }
