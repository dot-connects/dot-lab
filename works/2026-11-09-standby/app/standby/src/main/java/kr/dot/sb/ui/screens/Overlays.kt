package kr.dot.sb.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import kr.dot.sb.data.LiveTimes
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.ui.cards.CardView
import kr.dot.sb.ui.cards.Ic
import kr.dot.sb.ui.cards.IconView
import kr.dot.sb.ui.cards.Txt
import kr.dot.sb.ui.cards.nightFilter
import kr.dot.sb.ui.cards.tap
import kr.dot.sb.ui.state.AlarmRinging
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.LiveActivity
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalInk
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.Motion
import kr.dot.sb.ui.theme.ThemeBackground
import kr.dot.sb.ui.theme.ThemedDigits
import kr.dot.sb.ui.theme.themedSurface
import java.time.ZoneId
import kotlin.math.roundToInt

/** New-notification banner (spec-r9 §1): on screen this long, then it slides away. */
internal const val BANNER_MS = 5_000L

private val WEEK = arrayOf("월", "화", "수", "목", "금", "토", "일")

/**
 * Full-screen layers over the pages (spec-r9 §3) by priority ([topLayer]), the Live Activity pill, and the
 * new-notification banner, which is not a layer: it sits over whatever shows (the board stays visible). One body per
 * surface for all ten themes; colours from [LocalInk], plates via [themedSurface], digits via [ThemedDigits].
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
    // the live pill owns the top centre; the heat pill comes back (message first) once it is gone
    Faded(bare && state.live == null && state.hot, if (state.hot) Unit else null) { HeatPill(config.textSize.f) }
    Faded(layer == Layer.NOW_PLAYING, state.media) { NowPlaying(it, state, config, actions) { onNowPlaying(false) } }
    // alarm and live glow in the theme accent, not the board clock's colour (accent-warm, spec-r9 §3)
    val night = LocalInk.current.night
    val th = LocalTheme.current
    val warm = remember(night, th) { Ink.of(night, null, th) }
    CompositionLocalProvider(LocalInk provides warm) {
        Faded(layer == Layer.LIVE, state.live) { LiveScreen(it, actions) }
        Faded(layer == Layer.ALARM, state.alarm) { AlarmScreen(it, state, actions) }
    }
    Banner(layer != Layer.ALARM && !editing && config.showNotifications, state, config, actions)
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

/** Opaque page under a full-screen surface: the theme background (candle night included), so pages never show through. */
@Composable
private fun Page(ink: Ink) = ThemeBackground(LocalTheme.current, ink.night)

/**
 * A static radial glow of [c]: an ellipse [rx] × [ry] (fractions of the page) centred at [at] (fractions), [c] at
 * full strength in the centre, gone at the rim. Off at night (spec: glow effects off).
 */
@Composable
internal fun Glow(c: Color, at: Offset, rx: Float, ry: Float, alpha: Float) {
    val k = 1f - LocalInk.current.night
    if (k <= 0f) return
    Canvas(Modifier.fillMaxSize()) {
        val ctr = Offset(at.x * size.width, at.y * size.height)
        val r = ry * size.height
        val sx = rx * size.width / r
        scale(sx, 1f, pivot = ctr) {
            drawRect(
                Brush.radialGradient(
                    0f to c.copy(alpha = alpha * k), 0.5f to c.copy(alpha = alpha * k * 0.35f), 1f to c.copy(alpha = 0f),
                    center = ctr, radius = r,
                ),
                topLeft = Offset(ctr.x - ctr.x / sx, 0f), size = size.copy(width = size.width / sx),
            )
        }
    }
}

/** Pill / button shape: round, square on themes without corners (PAPER). */
@Composable
private fun pillShape(): Shape = if (LocalTheme.current.tokens.radiusRatio == 0f) RectangleShape else RoundedCornerShape(50)

/** Icon (box = text size, centred on the cap) + one line of text. Height = the text's cap. */
@Composable
private fun HeaderRow(icon: Ic, text: String, size: Dp, color: Color, weight: Int = 600) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.height(size * 0.707f).wrapContentHeight(unbounded = true)) { IconView(icon, color, Modifier.size(size * 1.05f)) }
        Spacer(Modifier.width(size * 0.4f))
        Txt(text, size, color, weight = weight)
    }
}

// ------------------------------------------------------------------------------------------------- heat

/** Heat pill message time (features.html §2 A): then only the thermometer stays until the phone cools. */
internal const val HEAT_MSG_MS = 5_000L

/** Heat protection pill (features.html §2 A): black pill top-centre, "뜨거워서 화면을 어둡게 했어요" 5 s, then the glyph alone. */
@Composable
private fun HeatPill(textScale: Float) {
    val ink = LocalInk.current
    var msg by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { delay(HEAT_MSG_MS); msg = false }
    val fg = lerp(Color(0xFFFFB05A), ink.ink, ink.night) // mockup orange; candle ink at night
    val line = lerp(Color.White.copy(alpha = 0.18f), ink.ink.copy(alpha = 0.35f), ink.night) // literal-ok: hairline on the black pill
    val shape = RoundedCornerShape(50)
    Box(Modifier.fillMaxSize()) {
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 14.dp)
                .background(Color.Black, shape).border(1.dp, line, shape) // literal-ok: the pill is black in every theme (mockup)
                .padding(horizontal = if (msg) 18.dp else 9.dp, vertical = if (msg) 10.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconView(Ic.THERMO, fg, Modifier.size(20.dp))
            if (msg) {
                Spacer(Modifier.width(8.dp))
                Txt("뜨거워서 화면을 어둡게 했어요", 17.dp * textScale, fg, weight = 700)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------- alarm

/**
 * Alarm (spec-r9 §3, mockup 화면 전체 → 알람): a sunrise glow of the accent rising from the bottom, "⏰ 기상 · 월요일",
 * the time centred and big, weather + today's event count, then a big accent "다시 알림" pill and an outlined "중단" pill
 * (side by side in landscape, stacked in portrait). The snooze length is not in [AlarmRinging], so no "· 9분".
 */
@Composable
private fun AlarmScreen(a: AlarmRinging, state: StandbyState, actions: StandbyActions) {
    val ink = LocalInk.current
    val t = LocalTheme.current
    Box(Modifier.fillMaxSize().tap {}) {
        Page(ink)
        Glow(ink.acc, Offset(0.5f, 1.2f), 1.2f, 0.7f, 0.55f)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            val land = w > h
            val s = min(w, h)
            val lab = (s * 0.045f).coerceIn(14.dp, 20.dp)
            val bh = (s * 0.14f).coerceIn(56.dp, 84.dp)
            val bw = if (land) min(w * 0.3f, 300.dp) else w - s * 0.16f
            val title = if (a.label.isBlank() || a.label == "알람") "알람" else a.label
            val events = state.agenda?.size ?: 0
            val info = listOfNotNull(
                state.weather?.let { "${it.condition.ko} · ${it.tempC}°" },
                if (events > 0) "오늘 일정 ${events}개" else null,
            ).joinToString(" · ")
            Column(
                Modifier.fillMaxSize().padding(s * 0.07f),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(top = if (land) 0.dp else s * 0.08f),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(s * 0.03f),
                ) {
                    HeaderRow(Ic.ALARM, "$title · ${WEEK[state.now.dayOfWeek.value - 1]}요일", lab, ink.accText)
                    val th = min(if (land) h * 0.36f else w * 0.3f, 220.dp)
                    ThemedDigits(clockText(state.now, state.use24h), t, ink, Modifier.fillMaxWidth(0.86f).height(th), color = ink.ink)
                    if (info.isNotEmpty()) Txt(info, lab, ink.ink2, weight = 500, num = true)
                }
                val snooze = @Composable { m: Modifier -> AlarmPill(if (bw >= 170.dp) "다시 알림" else "다시", ink.acc, ink.onAcc, bh, m, actions::alarmSnooze) }
                val stop = @Composable { m: Modifier -> AlarmPill("중단", ink.ink.copy(alpha = 0.16f), ink.ink, bh, m, actions::alarmStop, outline = ink.ink.copy(alpha = 0.75f)) }
                if (land) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (a.canSnooze) snooze(Modifier.size(bw, bh))
                    stop(Modifier.size(bw, bh))
                } else Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (a.canSnooze) snooze(Modifier.size(bw, bh))
                    stop(Modifier.size(bw, bh))
                }
            }
        }
    }
}

@Composable
private fun AlarmPill(text: String, bg: Color, fg: Color, bh: Dp, modifier: Modifier, onClick: () -> Unit, outline: Color? = null) {
    val shape = pillShape()
    // features.html §5 A: 중단 keeps its size and place but gets a 2 dp outline (ink 75 %) over ink 16 %: at 12 % alone
    // it was 1.2 : 1 on black and vanished at night
    val edge = if (outline != null) Modifier.border(2.dp, outline, shape) else Modifier
    BoxWithConstraints(modifier.clip(shape).background(bg, shape).then(edge).tap(onClick), contentAlignment = Alignment.Center) {
        Txt(text, bh * 0.36f, fg, weight = 700, maxW = maxWidth * 0.84f)
    }
}

// ------------------------------------------------------------------------------------------------- live activity

/** Live Activity pill top-centre: the theme plate, timer glyph + short value in the amber role. Tap → full view. */
@Composable
private fun LivePill(l: LiveActivity, actions: StandbyActions) {
    val ink = LocalInk.current
    val th = LocalTheme.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val s = min(maxWidth, maxHeight)
        val t = max(18.dp, s * 0.05f)
        val shape = pillShape()
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = max(maxHeight * 0.03f, screenPadding(maxWidth, maxHeight).calculateTopPadding() - t * 1.2f))
                .background(ink.bg, shape).themedSurface(th, ink, shape).clip(shape).tap(actions::liveToggle)
                .padding(horizontal = t * 0.8f, vertical = t * 0.5f),
            verticalAlignment = Alignment.CenterVertically,
        ) { HeaderRow(Ic.TIMER, l.value, t, ink.amber, weight = 700) }
    }
}

private val STEPS = listOf("주문", "조리", "픽업", "도착")

/**
 * Live Activity full screen (spec-r9 §3, mockup 화면 전체 → 실시간 활동; r10 cleanup B3): app + detail header, the value
 * as a kicker ("배달 중") over a big number ("12분"), and a vertical timeline 주문 → 조리 → 픽업 → 도착 lit up to the
 * progress, each reached step with the time StandBy saw it and the arrival with "now + N분" ([LiveActivity.stepTimes],
 * [LiveActivity.etaAt]); a warm accent glow from the bottom-left corner. Landscape: header on top of the left column,
 * the number centred in the height under it, the timeline the full right column; portrait: stacked.
 */
@Composable
private fun LiveScreen(l: LiveActivity, actions: StandbyActions) {
    val ink = LocalInk.current
    val tk = LocalTheme.current.tokens
    Box(Modifier.fillMaxSize().tap(actions::liveToggle)) {
        Page(ink)
        Glow(ink.acc, Offset(0f, 1f), 0.8f, 0.9f, 0.3f)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val w = maxWidth
            val h = maxHeight
            val land = w > h
            val s = min(w, h)
            val pad = s * 0.06f
            val fs = (s * 0.042f).coerceIn(13.dp, 19.dp)
            val on = l.progress?.let { LiveTimes.reached(it, l.steps) }
            val header = @Composable {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(fs * 2.2f).clip(RoundedCornerShape(fs * 0.55f)).background(ink.ink4), contentAlignment = Alignment.Center) {
                        IconView(Ic.TIMER, ink.acc, Modifier.size(fs * 1.2f), ink.ink4)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Txt(l.appLabel, fs, ink.ink, weight = 700)
                        if (l.detail.isNotBlank()) Spacer(Modifier.height(fs * 0.5f))
                        if (l.detail.isNotBlank()) Txt(l.detail, fs - 2.dp, ink.ink2, weight = 500, num = true)
                    }
                }
            }
            val eta = @Composable { colW: Dp, cap: Dp ->
                Column {
                    if (l.title.isNotBlank()) Txt(l.title, fs, ink.accText, weight = 600)
                    Spacer(Modifier.height(fs * 0.9f))
                    // "12분": the number in the digit font, the unit small in the words font on the same baseline
                    val mt = Regex("^([0-9:.,%]+) *(.*)$").find(l.value)
                    val num = mt?.groupValues?.get(1) ?: l.value
                    val unit = mt?.groupValues?.get(2).orEmpty()
                    Row(verticalAlignment = Alignment.Bottom) {
                        Txt(
                            num, cap / tk.digitCap, ink.ink, weight = tk.digitWeight, family = tk.digitFont(tk.digitWeight),
                            cap = tk.digitCap, tnum = true, maxW = colW * if (unit.isEmpty()) 1f else 0.75f, maxCapH = cap,
                        )
                        if (unit.isNotEmpty()) { Spacer(Modifier.width(fs * 0.4f)); Txt(unit, cap * 0.4f, ink.ink2, weight = 500) }
                    }
                }
            }
            val timeline = @Composable { rowH: Dp, sfs: Dp ->
                if (on != null) Column {
                    val steps = if (l.steps == STEPS.size) STEPS else List(l.steps) { "${it + 1}단계" }
                    steps.forEachIndexed { i, name ->
                        // a reached step shows when it was seen; the last one, not reached yet, the expected arrival
                        val time = if (i < on) l.stepTimes.getOrNull(i)?.let(::hm)
                        else if (i == steps.size - 1) l.etaAt?.let { hm(it) + "쯤" } else null
                        StepRow(name, i < on, i == on - 1, i < steps.size - 1, i + 1 < on, rowH, sfs, time, bright = i == on - 1 || i >= on)
                    }
                }
            }
            if (land) Row(Modifier.fillMaxSize().padding(pad), horizontalArrangement = Arrangement.spacedBy(pad)) {
                val lw = w * 0.46f
                Column(Modifier.width(lw).fillMaxHeight()) {
                    header()
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        eta(lw, min((h - pad * 2 - fs * 5.5f) * 0.62f, lw * 0.55f))
                    }
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    timeline(((h - pad * 2 - 60.dp) / 4).coerceIn(28.dp, 88.dp), (s * 0.05f).coerceIn(15.dp, 24.dp))
                }
            } else Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.SpaceBetween) {
                header()
                eta(w - pad * 2, min(w * 0.34f, h * 0.2f) * 0.7f)
                timeline(((h - pad * 2 - min(w * 0.34f, h * 0.2f) - fs * 9) / 4).coerceIn(26.dp, 64.dp), fs)
            }
        }
    }
}

private val HM = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
private fun hm(epochMs: Long): String = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).format(HM)

/** One timeline step: dot (accent when done, ink2 ring when not) + connector down to the next, label, time on the right. */
@Composable
private fun StepRow(
    name: String, done: Boolean, current: Boolean, line: Boolean, lineLit: Boolean, rowH: Dp, fs: Dp, time: String?, bright: Boolean,
) {
    val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().height(rowH)) {
        Column(Modifier.width(14.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .then(if (done) Modifier.background(ink.acc) else Modifier.border(2.dp, ink.ink2, CircleShape)),
            )
            if (line) Box(Modifier.width(2.dp).weight(1f).background(if (lineLit) ink.acc else ink.ink3))
        }
        Spacer(Modifier.width(12.dp))
        Txt(name, if (current) fs * 1.1f else fs, if (done) ink.ink else ink.ink2, weight = if (current) 700 else 500)
        if (time != null) {
            Spacer(Modifier.weight(1f))
            Txt(time, fs * 0.95f, if (bright) ink.ink else ink.ink2, weight = if (current) 600 else 500, num = true)
        }
    }
}

// ------------------------------------------------------------------------------------------------- now playing

/**
 * Now Playing (spec-r9 §3): the media card's own body at screen size, so portrait screens get the tall body and
 * landscape ones the cover (tap reveals the controls for 3 s). ✕ (in the body) or a swipe down closes; so does 5 s
 * paused. Position is interpolated per second between the 5 s updates, only while this screen is up.
 */
@Composable
private fun NowPlaying(m: MediaInfo, state: StandbyState, config: AppConfig, actions: StandbyActions, onClose: () -> Unit) {
    val ink = LocalInk.current
    val close by rememberUpdatedState(onClose)
    LaunchedEffect(m.playing) { if (!m.playing) { delay(5_000); close() } }
    var pos by remember { mutableLongStateOf(m.positionMs) }
    LaunchedEffect(m.positionMs, m.playing) {
        pos = m.positionMs
        while (m.playing && pos < m.durationMs) { delay(1_000); pos = (pos + 1_000).coerceAtMost(m.durationMs) }
    }
    Box(
        Modifier.fillMaxSize().tap {}.pointerInput(Unit) {
            var dy = 0f
            detectVerticalDragGestures(onDragStart = { dy = 0f }, onDragEnd = { if (dy > size.height * 0.15f) close() }) { _, d -> dy += d }
        },
    ) {
        Page(ink)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val p = screenPadding(maxWidth, maxHeight)
            CardView(
                CardType.MUSIC, state.copy(media = m.copy(positionMs = pos)), config, actions, Modifier.fillMaxSize().padding(p),
                slotId = "now-playing", close = close,
            )
        }
    }
}

// ------------------------------------------------------------------------------------------------- banner

/**
 * New-notification banner (spec-r9 §1, mockup 새 알림): top centre, min(W − 2M, 520) × 64 on the theme plate, icon ·
 * sender · app · time and one line of text. [BANNER_MS] then it slides away; swipe up dismisses; tap opens the board's
 * notification card ([StandbyActions.openNotifCard]). Hidden notifications (or 탭할 때만 미리보기) show the app name only.
 */
@Composable
private fun Banner(allowed: Boolean, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val n = state.notification
    var last by remember { mutableStateOf(n) }
    if (n != null && n != last) last = n
    AnimatedVisibility(
        allowed && n != null,
        enter = slideInVertically(tween(Motion.LAYER_FADE_MS)) { -it } + fadeIn(tween(Motion.LAYER_FADE_MS)),
        exit = slideOutVertically(tween(Motion.LAYER_FADE_MS)) { -it } + fadeOut(tween(Motion.LAYER_FADE_MS)),
    ) { last?.let { NotifBanner(it, it.hidden || config.previewOnTapOnly, state, config.textSize.f, actions) } }
}

@Composable
private fun NotifBanner(n: BigNotification, hidden: Boolean, state: StandbyState, k: Float, actions: StandbyActions) {
    val ink = LocalInk.current
    val t = LocalTheme.current
    val act by rememberUpdatedState(actions)
    LaunchedEffect(n.key) { delay(BANNER_MS); act.notificationDismiss(n.key) }
    var dy by remember(n.key) { mutableFloatStateOf(0f) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val m = Grid.margin(min(maxWidth, maxHeight))
        val bw = min(maxWidth - m * 2, 520.dp)
        val shape = RoundedCornerShape(if (t.tokens.radiusRatio == 0f) 0.dp else 22.dp)
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = m * 0.5f).width(bw).height(64.dp)
                .graphicsLayer { translationY = dy.coerceAtMost(0f) }
                .background(ink.bg, shape).themedSurface(t, ink, shape).clip(shape)
                .pointerInput(n.key) {
                    detectVerticalDragGestures(
                        onDragStart = { dy = 0f },
                        onDragEnd = { if (dy < -24.dp.toPx()) act.notificationDismiss(n.key) else dy = 0f },
                        onDragCancel = { dy = 0f },
                    ) { _, d -> dy += d }
                }
                .tap { act.openNotifCard(n.key) }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BannerIcon(n, ink)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) { // cap boxes: bottom = baseline
                    Txt(if (hidden) n.appLabel else n.sender.ifBlank { n.appLabel }, 15.dp * k, ink.ink, Modifier.weight(1f), weight = 700)
                    Spacer(Modifier.width(8.dp))
                    val time = ago(n.postedAtEpochMs, state)
                    Txt(if (!hidden && bw >= 360.dp) "${n.appLabel} · $time" else time, 13.dp * k, ink.ink2, weight = 500, num = true)
                }
                if (!hidden && n.text.isNotBlank()) {
                    Spacer(Modifier.height(9.dp))
                    Txt(n.text, 15.dp * k, lerp(ink.ink2, ink.ink, 0.5f), weight = 500)
                }
            }
        }
    }
}

/** The app icon (or the sender photo) on its squircle; a letter on the app colour without one. */
@Composable
private fun BannerIcon(n: BigNotification, ink: Ink) {
    val shape = RoundedCornerShape(26) // percent: the app-icon squircle
    val img = n.icon
    if (img != null) {
        Image(img, null, Modifier.size(34.dp).clip(shape), contentScale = ContentScale.Crop, colorFilter = nightFilter(ink.night))
        return
    }
    val plate = lerp(Color(n.plateColor), Ink.NIGHT_DIM, ink.night)
    Box(Modifier.size(34.dp).clip(shape).background(plate), contentAlignment = Alignment.Center) {
        Txt(n.appLabel.take(1), 16.dp, ink.on(plate), weight = 700)
    }
}

/** "방금", "12분 전", "2시간 전", "3일 전" at the state's minute (the notification cards use it too). */
internal fun ago(epochMs: Long, state: StandbyState): String {
    val now = state.now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val min = ((now - epochMs) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "방금"
        min < 60 -> "${min}분 전"
        min < 24 * 60 -> "${min / 60}시간 전"
        else -> "${min / (24 * 60)}일 전"
    }
}
