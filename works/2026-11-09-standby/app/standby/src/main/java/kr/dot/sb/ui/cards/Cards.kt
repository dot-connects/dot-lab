package kr.dot.sb.ui.cards

import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.MediaKind
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.state.TimerMode
import kr.dot.sb.ui.state.TimerState
import kr.dot.sb.ui.state.UsageWindow
import kr.dot.sb.ui.state.WeatherInfo
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalInk
import kr.dot.sb.ui.theme.LocalSlotLabel
import kr.dot.sb.ui.theme.ProvideTheme
import kr.dot.sb.ui.theme.SizeClass
import kr.dot.sb.ui.theme.Sizes
import kr.dot.sb.ui.theme.Surface
import kr.dot.sb.ui.theme.Family
import kr.dot.sb.ui.screens.artColor
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.ui.theme.Themes
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One card filling its slot (design-system §4, mockup r5). Size class, shape and text sizes come from the slot size;
 * [look] carries the board-level rules (accent, hero, shared label size and pad). [slotId] keys per-slot state (timers).
 * Colours, fonts, sizes and the card surface come from the theme ([config].theme).
 */
@Composable
fun CardView(
    type: CardType,
    state: StandbyState,
    config: AppConfig,
    actions: StandbyActions,
    modifier: Modifier = Modifier,
    slotId: String = type.name,
    look: CardLook = CardLook.of(config),
) = ProvideTheme(config.theme) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val t = Themes.of(config.theme)
        val ink = remember(state.night, look.accent, t) { Ink.of(state.night, look.accent, t.palette) }
        val today = state.now.toLocalDate()
        val e = remember(w, h, ink, look, slotId, today, t) {
            val own = Sizes.of(w, h, r = t.ramp)
            Env(
                w, h, own.copy(label = look.labelSize ?: own.label, pad = look.pad ?: own.pad), own.label, ink, look,
                t.numerals, t.numeralsCap, slotId, today, t,
            )
        }
        // C: the music plate takes the album colour (computed once per cover), every other card its function pastel
        val art = state.media?.art?.takeIf { type == CardType.MUSIC && t.surface == Surface.PLATE }
        val artTint = remember(art) { art?.let(::artColor) }
        val surface = when (t.surface) {
            // B's hairlines live in the gutters, drawn once by the board (BoardView)
            Surface.NONE, Surface.RULE -> Modifier
            Surface.PLATE -> Modifier.lampPlate(ink, artTint ?: t.tint(type), min(e.plateRadius, 44.dp))
        }
        CompositionLocalProvider(LocalInk provides ink, LocalSlotLabel provides e.label) {
            Box(Modifier.fillMaxSize().then(surface)) {
                val drawn = when (t.family) {
                    Family.PAPER -> paperBody(type, e, state, config, actions)
                    Family.LAMP -> lampBody(type, e, state, config, actions)
                    Family.IPHONE -> false
                }
                if (!drawn) when (type) {
                    CardType.CLOCK -> ClockCard(e, state, config)
                    CardType.ANALOG -> AnalogCard(e, state, config)
                    CardType.MUSIC -> MediaCard(e, state, actions, MediaKind.MUSIC)
                    CardType.VIDEO -> MediaCard(e, state, actions, MediaKind.VIDEO)
                    CardType.TIMER -> TimerCard(e, state, config, actions)
                    CardType.SLEEP -> SleepCard(e, state, config, actions)
                    CardType.DDAY -> DdayCard(e, config)
                    CardType.AGENDA -> AgendaCard(e, state, actions)
                    CardType.USAGE -> UsageCard(e, state, actions)
                    CardType.WEATHER -> WeatherCard(e, state, config, actions)
                    CardType.CALENDAR -> CalendarCard(e)
                    CardType.MEMORIES -> MemoriesCard(e, state, actions)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- clocks

@Composable
private fun ClockCard(e: Env, state: StandbyState, config: AppConfig) {
    val face = e.look.clockFace
    val base = config.clockStyle(face)
    // §5.1: hero digits carry the accent; a second clock on the board renders its numerals in ink (hero = false).
    if (face == FaceId.DIGITAL) {
        // Digital draws its own date line right under the digits (mockup r5 desk: "월요일 11월 9일 · 7°"), so the
        // date never drifts to the slot bottom.
        ClockFace(face, base.copy(color = e.look.accent.argbLong(), showInfo = e.atLeastS), state, Modifier.fillMaxSize().padding(e.pad), inSlot = true, hero = e.look.hero)
        return
    }
    val style = base.copy(color = e.look.accent.argbLong(), showInfo = false)
    Column(Modifier.fillMaxSize().padding(e.pad), horizontalAlignment = Alignment.CenterHorizontally) {
        ClockFace(face, style, state, Modifier.fillMaxWidth().weight(1f), inSlot = true, hero = e.look.hero)
        if (e.atLeastS) {
            Spacer(Modifier.height(e.gap))
            Detail(e, dateKo(e.today), align = TextAlign.Center)
        }
    }
}

@Composable
private fun AnalogCard(e: Env, state: StandbyState, config: AppConfig) {
    // Per-widget colours (r5): the analog keeps its own second-hand colour instead of the board accent.
    val def = e.t.clockDefaults(FaceId.ANALOG)
    val base = config.clockStyle(FaceId.ANALOG)
    // Untouched default = the iPhone analog *widget* (frame ex-analogwidget): white numerals, orange second hand.
    // The Duo palette belongs to the full-screen face; a user-picked style is drawn as picked.
    val pal = e.t.palette
    val st = if (base == def && def.analogNumbers == AnalogNumbers.DUO) base.copy(numeralColor = pal.fg.argbLong(), secondColor = pal.orange.argbLong(), tickColor = pal.fg2.argbLong()) else base
    ClockFace(FaceId.ANALOG, st, state, Modifier.fillMaxSize(), inSlot = true)
}

// ---------------------------------------------------------------- media

@Composable
private fun MediaCard(e: Env, state: StandbyState, actions: StandbyActions, kind: MediaKind) {
    val m = state.media?.takeIf { it.kind == kind }
    val ic = if (kind == MediaKind.MUSIC) Ic.NOTE else Ic.VIDEO
    if (m == null) {
        StateCard(e, ic, if (kind == MediaKind.MUSIC) "음악" else "영상", ic, null, "폰에서 재생하면 여기 나와요", actions::openMediaApp)
        return
    }
    when {
        e.cls == SizeClass.XS -> XsPlate(e, m, ic, actions)
        kind == MediaKind.VIDEO -> VideoBody(e, m, actions)
        else -> MusicBody(e, m, actions)
    }
}

@Composable
internal fun Art(e: Env, img: ImageBitmap?, placeholder: Ic, modifier: Modifier, radius: Dp) {
    val shape = RoundedCornerShape(radius)
    if (img != null) {
        Image(img, null, modifier.clip(shape), contentScale = ContentScale.Crop, colorFilter = nightFilter(e.ink.night))
    } else {
        Box(modifier.clip(shape).background(e.ink.ink4), contentAlignment = Alignment.Center) {
            IconView(placeholder, e.ink.ink3, Modifier.fillMaxSize(0.4f), e.ink.ink4)
        }
    }
}

/** iPhone widget transport button: icon on a 14 % ink disc (mockup r5 modular). */
@Composable
private fun Disc(e: Env, ic: Ic, d: Dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(d).clip(CircleShape).background(e.ink.ink.copy(alpha = 0.14f)).tap(if (enabled) onClick else null),
        contentAlignment = Alignment.Center,
    ) { IconView(ic, if (enabled) e.ink.ink else e.ink.ink3, Modifier.size(d * 0.44f)) }
}

/** Prev / play / next discs spread over [width]; only play/pause when three do not fit. */
@Composable
private fun Transport(e: Env, m: MediaInfo, actions: StandbyActions, width: Dp, d0: Dp) {
    val full = width >= d0 * 3.4f
    val d = if (full) d0 else min(d0, width)
    Row(Modifier.width(width), horizontalArrangement = if (full) Arrangement.SpaceBetween else Arrangement.Start) {
        if (full) Disc(e, Ic.PREV, d, m.canPrev, actions::mediaPrev)
        Disc(e, if (m.playing) Ic.PAUSE else Ic.PLAY, d, onClick = actions::mediaPlayPause)
        if (full) Disc(e, Ic.NEXT, d, m.canNext, actions::mediaNext)
    }
}

internal fun progress(m: MediaInfo) = if (m.durationMs > 0) m.positionMs.toFloat() / m.durationMs else 0f

/** XS: art plate fills the slot, play/pause disc bottom-trailing; tap elsewhere opens the player. */
@Composable
private fun XsPlate(e: Env, m: MediaInfo, ic: Ic, actions: StandbyActions) {
    if (e.tall) {
        // Tall XS (Flip halves): square art on TOP, play/pause on BOT instead of a stretched crop.
        Column(
            Modifier.fillMaxSize().padding(e.pad).tap(actions::openMediaApp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Art(e, m.art, ic, Modifier.size(e.cw), e.round(e.cw))
            Disc(e, if (m.playing) Ic.PAUSE else Ic.PLAY, e.label * 2f, onClick = actions::mediaPlayPause)
        }
        return
    }
    Box(Modifier.fillMaxSize().tap(actions::openMediaApp)) {
        Art(e, m.art, ic, Modifier.fillMaxSize(), e.plateRadius)
        val d = e.label * 2.2f
        Box(
            Modifier.align(Alignment.BottomEnd).padding(e.pad * 0.7f).size(d).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)).tap(actions::mediaPlayPause),
            contentAlignment = Alignment.Center,
        ) { IconView(if (m.playing) Ic.PAUSE else Ic.PLAY, e.ink.ink, Modifier.size(d * 0.46f)) }
    }
}

/**
 * Music (mockup r5 modular, iPhone Duo widget): art top-left with the music glyph top-right, title + artist under the
 * art, three transport discs along the bottom. Wide slots put the art on the left, full height.
 */
@Composable
private fun MusicBody(e: Env, m: MediaInfo, actions: StandbyActions) {
    val title = min(e.sz.title, e.label * 1.4f)
    val disc = max(40.dp, e.label * 1.7f)
    val glyph = e.label * 1.1f
    val names = @Composable { mod: Modifier ->
        Column(mod) {
            Txt(m.title, title, e.ink.ink, weight = 700)
            Spacer(Modifier.height(e.gap))
            Detail(e, m.artist)
        }
    }
    val noteGlyph = @Composable { IconView(Ic.NOTE, e.ink.music, Modifier.size(glyph)) }
    if (e.w > e.h * 1.15f) {
        val a = min(e.ch, e.cw * 0.45f)
        Row(Modifier.fillMaxSize().padding(e.pad).tap(actions::openMediaApp)) {
            Art(e, m.art, Ic.NOTE, Modifier.size(a), e.round(a, 10.dp, 0.8f))
            Spacer(Modifier.width(e.pad))
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                Row(Modifier.fillMaxWidth()) {
                    names(Modifier.weight(1f))
                    noteGlyph()
                }
                Transport(e, m, actions, e.cw - a - e.pad, disc)
            }
        }
        return
    }
    val need = title * CAP_UI + e.gap + e.labelCap + disc + e.gap * 4
    val a = min(e.ch - need, e.cw - glyph - e.gap)
    Column(Modifier.fillMaxSize().padding(e.pad).tap(actions::openMediaApp)) {
        if (a >= e.label * 3) {
            Row(Modifier.fillMaxWidth()) {
                Art(e, m.art, Ic.NOTE, Modifier.size(a), e.round(a, 10.dp, 0.8f))
                Spacer(Modifier.weight(1f))
                noteGlyph()
            }
            Spacer(Modifier.height(e.gap * 2))
            names(Modifier.fillMaxWidth())
        } else {
            Row(Modifier.fillMaxWidth()) { names(Modifier.weight(1f)); noteGlyph() }
        }
        Spacer(Modifier.weight(1f))
        Transport(e, m, actions, e.cw, disc)
    }
}

/** Video: the still fills the slot like a photo card (scrim top and bottom), text bottom-leading, transport discs. */
@Composable
private fun VideoBody(e: Env, m: MediaInfo, actions: StandbyActions) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(e.plateRadius))) {
        Art(e, m.art, Ic.VIDEO, Modifier.fillMaxSize(), 0.dp)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.5f), 0.25f to Color.Black.copy(alpha = 0.15f),
                    0.45f to Color.Black.copy(alpha = 0.4f), 1f to Color.Black.copy(alpha = 0.75f),
                ),
            ),
        )
        TextCard(e) {
            LabelRow(e, Ic.VIDEO, m.appLabel, color = e.ink.ink)
            Column {
                Txt(m.title, min(e.sz.title, e.label * 1.4f), e.ink.ink, weight = 700)
                Spacer(Modifier.height(e.gap * 0.75f))
                Detail(e, m.artist, color = e.ink.ink.copy(alpha = 0.8f))
                Spacer(Modifier.height(e.gap * 1.5f))
                Bar(e, progress(m), e.ink.ink)
                Spacer(Modifier.height(e.gap * 1.5f))
                Transport(e, m, actions, min(e.cw, e.label * 7f), max(40.dp, e.label * 1.7f))
            }
        }
    }
}

// ---------------------------------------------------------------- timer, sleep, d-day

/**
 * Timer as the iPhone timer Live Activity (mockup r5 desk): dark plate, amber strip with pause/close discs, mode label
 * top-trailing in grey, white digits bottom-trailing. Tall slots put the strip on top.
 */
@Composable
private fun TimerCard(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val total0 = config.focusMinutes * 60
    val t = state.timers[e.slotId] ?: TimerState(leftSec = total0, totalSec = total0)
    val mode = if (t.mode == TimerMode.FOCUS) "집중" else "휴식"
    val label = if (!t.running && t.leftSec < t.totalSec) "$mode · 멈춤" else mode
    val digits = "%02d:%02d".format(t.leftSec / 60, t.leftSec % 60)
    val ink = e.ink
    val side = e.w >= e.h * 0.9f
    val strip = if (side) min(e.h * 0.5f, e.w * 0.32f) else min(e.w * 0.5f, e.h * 0.3f)
    val btn = min(strip * 0.72f, (if (side) e.h else e.w) * 0.32f)
    val btnBg = lerp(Color.Black, ink.amber, 0.55f)
    val toggle = { actions.timerToggle(e.slotId) }
    val buttons = @Composable {
        Box(Modifier.size(btn).clip(CircleShape).background(btnBg).tap(toggle), contentAlignment = Alignment.Center) {
            IconView(if (t.running) Ic.PAUSE else Ic.PLAY, ink.amber, Modifier.size(btn * 0.42f))
        }
        Box(Modifier.size(btn).clip(CircleShape).background(btnBg).tap { actions.timerReset(e.slotId) }, contentAlignment = Alignment.Center) {
            IconView(Ic.CLOSE, ink.ink, Modifier.size(btn * 0.46f))
        }
    }
    val body = @Composable { mod: Modifier, w: Dp, h: Dp ->
        Column(mod.padding(e.pad), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
            Txt(label, e.label, ink.ink2, weight = 600, maxW = w - e.pad * 2)
            Txt(
                digits, e.sz.display, ink.ink, weight = 600, family = e.t.time, cap = e.t.timeCap, tnum = true,
                maxW = w - e.pad * 2, maxCapH = h - e.pad * 2 - e.labelCap - e.gap * 2,
            )
        }
    }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(e.plateRadius)).background(ink.plate).tap(toggle)) {
        if (side) Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.width(strip).fillMaxHeight().background(ink.amber),
                verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally,
            ) { buttons() }
            body(Modifier.weight(1f).fillMaxHeight(), e.w - strip, e.h)
        } else Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.height(strip).fillMaxWidth().background(ink.amber),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) { buttons() }
            body(Modifier.weight(1f).fillMaxWidth(), e.w, e.h - strip)
        }
    }
}

private val WAVE = floatArrayOf(0.35f, 0.6f, 0.85f, 0.55f, 1f, 0.7f, 0.45f, 0.8f, 0.4f)

@Composable
private fun SleepCard(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val on = state.sleep.playing
    val waveH = min(e.sz.value * 1.3f, (e.ch - e.labelCap * 2 - e.gap * 3) * 0.85f)
    val mod = Modifier.width(min(e.cw, waveH * 2.4f)).height(waveH)
    val wave = @Composable { if (on) AnimatedWave(e, mod) else Wave(e, mod, e.ink.ink2, null) }
    if (!e.atLeastS) {
        Column(Modifier.fillMaxSize().padding(e.pad).tap(actions::sleepToggle), horizontalAlignment = Alignment.CenterHorizontally) {
            LabelRow(e, Ic.SOUND, config.sleepSound.ko, color = e.ink.blue, center = true, weight = 700)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { wave() }
        }
        return
    }
    TextCard(e, Modifier.tap(actions::sleepToggle), horizontal = Alignment.CenterHorizontally) {
        LabelRow(e, Ic.SOUND, config.sleepSound.ko, color = e.ink.blue, center = true, weight = 700)
        wave()
        Txt(if (on) "${state.sleep.minutesLeft ?: config.sleepMinutes}분 뒤 꺼짐" else "눌러서 틀기", e.label, e.ink.ink2, weight = 500, align = TextAlign.Center, num = true)
    }
}

/** The only infinite animation on the board, composed only while the sound plays; the phase is read in draw only. */
@Composable
private fun AnimatedWave(e: Env, modifier: Modifier) {
    val phase = rememberInfiniteTransition(label = "wave").animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "phase",
    )
    Wave(e, modifier, e.ink.blue, phase)
}

@Composable
private fun Wave(e: Env, modifier: Modifier, color: Color, phase: State<Float>?) {
    Canvas(modifier) {
        val n = WAVE.size
        val bw = size.width / (n * 1.9f)
        val step = (size.width - bw) / (n - 1)
        val p = phase?.value
        for (i in 0 until n) {
            val k = if (p == null) WAVE[i] else WAVE[i] * (0.6f + 0.4f * kotlin.math.sin(p + i * 0.9f).toFloat())
            val bh = (size.height * k).coerceAtLeast(bw)
            drawRoundRect(color, Offset(i * step, (size.height - bh) / 2), Size(bw, bh), CornerRadius(bw / 2))
        }
    }
}

@Composable
private fun DdayCard(e: Env, config: AppConfig) {
    val dd = config.ddays.filter { !it.date.isBefore(e.today) }.minByOrNull { it.date } ?: config.ddays.firstOrNull()
    if (dd == null) {
        StateCard(e, Ic.CALENDAR, "D-day", null, "일정 없음", null, null)
        return
    }
    val n = ChronoUnit.DAYS.between(e.today, dd.date)
    val txt = when { n > 0 -> "D-$n"; n == 0L -> "D-DAY"; else -> "D+${-n}" }
    val avail = e.ch - e.labelCap * (if (e.atLeastS) 2 else 1) - e.s * 0.08f
    TextCard(e) {
        LabelRow(e, Ic.CALENDAR, dd.name, color = e.ink.orange, weight = 700)
        Txt(txt, e.sz.display, e.ink.ink, weight = 700, family = e.num, cap = e.numCap, tnum = true, maxW = e.cw, maxCapH = avail)
        if (e.atLeastS) Txt(dateKo(dd.date), e.label, e.ink.ink2, weight = 500, num = true)
    }
}

// ---------------------------------------------------------------- agenda, usage

/** iPhone Calendar widget (mockup r5 modular): "월요일 9" header, events as colour bar + title over the time. */
@Composable
private fun AgendaCard(e: Env, state: StandbyState, actions: StandbyActions) {
    val items = state.agenda
    if (items == null) {
        StateCard(e, Ic.CALENDAR, "오늘 일정", Ic.LOCK, null, "권한 필요 · 눌러서 설정") { actions.openSettings("calendar") }
        return
    }
    val header = @Composable {
        Row(verticalAlignment = Alignment.Bottom) {
            Txt("${dowKo(e.today)}요일", e.label, e.ink.weekday, weight = 700)
            Spacer(Modifier.width(e.label * 0.35f))
            Txt("${e.today.dayOfMonth}", e.label, e.ink.ink, weight = 700, num = true)
        }
    }
    if (items.isEmpty()) {
        TextCard(e) { header(); Txt("일정 없음", min(e.sz.title, e.label * 1.4f), e.ink.ink2, weight = 700) }
        return
    }
    val now = "%02d:%02d".format(state.now.hour, state.now.minute)
    if (e.cls == SizeClass.XS) {
        val next = items.firstOrNull { it.start >= now } ?: items.last()
        TextCard(e) {
            header()
            Column {
                Txt(next.title, e.label, e.ink.ink, weight = 700)
                Spacer(Modifier.height(e.gap))
                Txt(next.start, e.label, e.ink.ink2, weight = 500, num = true)
            }
        }
        return
    }
    val ev = max(e.label, min(e.sz.title, e.label * 1.15f))
    val sub = e.label
    val block = ev * CAP_UI + e.gap * 0.9f + sub * CAP_UI
    val step = block + e.gap * 3f
    val fit = ((e.ch - e.labelCap - e.gap * 2f + e.gap * 3f) / step).toInt().coerceIn(1, 3)
    Column(Modifier.fillMaxSize().padding(e.pad)) {
        header()
        items.take(fit).forEach { it ->
            Spacer(Modifier.height(e.gap * 3f))
            Row {
                Box(Modifier.width(max(4.dp, e.label * 0.2f)).height(block).clip(RoundedCornerShape(50)).background(e.ink.mapped(Color(it.color))))
                Spacer(Modifier.width(e.label * 0.5f))
                Column(Modifier.weight(1f)) {
                    Txt(it.title, ev, e.ink.ink, weight = 700)
                    Spacer(Modifier.height(e.gap * 0.9f))
                    Txt(it.start, sub, e.ink.ink2, weight = 500, num = true, tnum = true)
                }
            }
        }
    }
}

internal fun pctOf(w: UsageWindow?) = w?.usedPct?.let { "$it%" } ?: "–"

/** Battery-widget ring: track 25 % of the colour, arc from 12 o'clock, letter centred (rounded numerals). */
@Composable
private fun UsageRing(e: Env, d: Dp, frac: Float, color: Color, letter: String) {
    val track = lerp(Color.Black, color, 0.28f)
    Box(Modifier.size(d), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = size.minDimension * 0.125f
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(sw))
            drawArc(color, -90f, 360f * frac.coerceIn(0.005f, 1f), false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        Txt(letter, d * 0.4f, e.ink.ink, weight = 800, family = e.num, cap = e.numCap)
    }
}

@Composable
private fun UsageCard(e: Env, state: StandbyState, actions: StandbyActions) {
    val u = state.usage
    if (u == null) {
        LoadingCard(e, Ic.USAGE, "사용량")
        return
    }
    val nowSec = state.now.atZone(ZoneId.systemDefault()).toEpochSecond()
    val old = nowSec - u.fetchedAtEpochSec > 3 * 3600
    val valCol = if (old) e.ink.ink2 else e.ink.ink
    fun ringCol(w: UsageWindow?) = when { (w?.usedPct ?: 0) >= 90 -> e.ink.weekday; old -> e.ink.ink2; else -> e.ink.green }
    val tapMod = Modifier.tap(if (u.offline) actions::retryFeeds else null)
    val pctSize = e.label * 1.1f
    if (e.cls == SizeClass.XS) {
        val all = listOf("C" to u.claude5h, "C" to u.claude7d, "X" to u.codex5h, "X" to u.codex7d)
        val (name, w) = all.maxByOrNull { it.second?.usedPct ?: -1 }!!
        Column(Modifier.fillMaxSize().padding(e.pad).then(tapMod), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            val d = min(e.cw, e.ch - pctSize * e.numCap - e.gap * 2)
            UsageRing(e, d, (w?.usedPct ?: 0) / 100f, ringCol(w), name)
            Spacer(Modifier.height(e.gap * 1.5f))
            Txt(pctOf(w), pctSize, valCol, weight = 700, family = e.num, cap = e.numCap, tnum = true)
        }
        return
    }
    val stack = e.tall
    val detail = e.atLeastM && (stack || e.cw / 2 >= e.label * 4.5f)
    val caption = if (u.offline) "${hhmm(u.fetchedAtEpochSec)} 기준 · 연결 안 됨" else "5시간 사용량 · ${hhmm(u.fetchedAtEpochSec)}"
    val below = e.gap * 1.5f + pctSize * e.numCap + (if (detail) e.gap * 1.2f + e.labelCap else 0.dp)
    val d = if (stack) min(((e.ch - e.labelCap - e.gap * 3f) / 2f - below - e.gap * 2f), e.cw * 0.8f)
    else min((e.ch - e.labelCap - e.gap * 3f - below), (e.cw - e.gap * 3f) / 2f)
    val rings = @Composable {
        listOf(Triple("C", u.claude5h, u.claude7d), Triple("X", u.codex5h, u.codex7d)).forEach { (letter, h5, d7) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                UsageRing(e, d, (h5?.usedPct ?: 0) / 100f, ringCol(h5), letter)
                Spacer(Modifier.height(e.gap * 1.5f))
                Txt(pctOf(h5), pctSize, valCol, weight = 700, family = e.num, cap = e.numCap, tnum = true)
                if (detail) {
                    Spacer(Modifier.height(e.gap * 1.2f))
                    Txt("7일 ${pctOf(d7)}", e.label, e.ink.ink2, weight = 500, num = true)
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(e.pad).then(tapMod)) {
        if (stack) Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) { rings() }
        else Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) { rings() }
        Spacer(Modifier.height(e.gap * 3f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
            if (u.offline) {
                Box(Modifier.size(e.labelCap * 0.6f).clip(CircleShape).background(e.ink.ink2))
                Spacer(Modifier.width(e.label * 0.4f))
            }
            Txt(caption, e.label, e.ink.ink2, weight = 500, num = true, maxW = e.cw)
        }
    }
}

// ---------------------------------------------------------------- weather, calendar, memories

/** Weather glyph in theme colours: white cloud, yellow sun, blue rain (night: the red ramp). */
@Composable
internal fun WxIcon(e: Env, ic: Ic, modifier: Modifier) {
    val c2 = if (!e.t.colorIcons) e.ink.ink else if (ic == Ic.RAIN) e.ink.blue else e.ink.sun
    IconView(ic, e.ink.ink, modifier, e.ink.bg, color2 = c2)
}

@Composable
private fun WeatherCard(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val wx = state.weather
    if (wx == null) {
        LoadingCard(e, Ic.ARROW, config.city.name) { WeatherLabel(e, config.city.name) }
        return
    }
    val nowSec = state.now.atZone(ZoneId.systemDefault()).toEpochSecond()
    val old = nowSec - wx.fetchedAtEpochSec > 3 * 3600
    val hours = wx.hourly.take(5)
    val showHours = hours.isNotEmpty() && (e.cls == SizeClass.L || (e.wide && e.atLeastM))
    val mod = Modifier.fillMaxSize().padding(e.pad).tap(if (wx.offline) actions::retryFeeds else null)
    if (showHours && e.wide) {
        Row(mod) {
            WeatherMain(e, wx, old, Modifier.weight(0.42f).fillMaxHeight(), e.ch, (e.cw - e.pad) * 0.42f)
            Spacer(Modifier.width(e.pad))
            Hours(e, wx, Modifier.weight(0.58f).fillMaxHeight(), spread = true)
        }
    } else {
        val hoursH = e.labelCap + e.label * 1.6f + e.label * CAP_UI + e.gap * 1.6f
        Column(mod) {
            val mainH = if (showHours) e.ch - hoursH - e.gap * 2 else e.ch
            WeatherMain(e, wx, old, Modifier.fillMaxWidth().weight(1f), mainH, e.cw)
            if (showHours) {
                Spacer(Modifier.height(e.gap * 2))
                Hours(e, wx, Modifier.fillMaxWidth().height(hoursH), spread = false)
            }
        }
    }
}

/**
 * iPhone weather widget (mockup r5): "서울 ➤" over a big rounded "7°", then the coloured glyph, the condition and
 * "최고:11° 최저:2°". Small slots put the glyph beside the city.
 */
@Composable
private fun WeatherMain(e: Env, wx: WeatherInfo, old: Boolean, modifier: Modifier, h: Dp, w: Dp) {
    val lines = if (e.atLeastS) 2 else 0
    val iconTop = !e.atLeastM || h < e.s * 0.8f
    val ico = e.label * 1.5f
    val bottom = if (lines == 0) 0.dp else e.labelCap * 2 + e.gap + (if (iconTop) 0.dp else ico + e.gap)
    val avail = h - e.labelCap - bottom - e.gap * (if (lines == 0) 2f else 3.5f)
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Box(Modifier.weight(1f)) { WeatherLabel(e, wx.city) }
            if (iconTop) Box(Modifier.height(e.labelCap).wrapUnbounded()) { WxIcon(e, weatherIc(wx.condition), Modifier.size(ico)) }
        }
        Txt(
            temp(wx.tempC), e.loneDisplay, if (old) e.ink.ink2 else e.ink.ink, weight = 500, family = e.num, cap = e.numCap,
            tnum = true, maxW = w, maxCapH = avail,
        )
        if (lines > 0) {
            Column {
                if (!iconTop) {
                    WxIcon(e, weatherIc(wx.condition), Modifier.size(ico))
                    Spacer(Modifier.height(e.gap))
                }
                val off = if (wx.offline) "${hhmm(wx.fetchedAtEpochSec)} 기준 · 연결 안 됨" else null
                Txt(off ?: wx.condition.ko, e.label, if (off != null) e.ink.ink2 else e.ink.ink, weight = 600, num = true, maxW = w)
                Spacer(Modifier.height(e.gap))
                Txt("최고:${wx.highC}° 최저:${wx.lowC}°", e.label, e.ink.ink, weight = 500, num = true, maxW = w)
            }
        }
    }
}

/** Next hours: "22시" / glyph / "6°" columns. [spread] = columns span TOP→BOT (wide side panel). */
@Composable
private fun Hours(e: Env, wx: WeatherInfo, modifier: Modifier, spread: Boolean) {
    BoxWithConstraints(modifier) {
        val col = maxWidth / 5
        val tSize = min(e.label * 1.1f, col * 0.9f / 1.9f)
        Row(Modifier.fillMaxSize()) {
            wx.hourly.take(5).forEach { hr ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.spacedBy(e.gap * 0.8f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Txt("${hr.hour}시", min(e.label, col * 0.8f / 2.2f), e.ink.ink2, weight = 600, num = true)
                    WxIcon(e, weatherIc(hr.condition), Modifier.size(e.label * 1.6f))
                    Txt(temp(hr.tempC), tSize, e.ink.ink, weight = 700, family = e.num, cap = e.numCap, tnum = true)
                }
            }
        }
    }
}

/** Weather label = city then arrow in every state (review X6), bold `ink` like iPhone's "Holland ➤" (review B3). */
@Composable
private fun WeatherLabel(e: Env, city: String) = LabelRow(e, Ic.ARROW, city, color = e.ink.ink, iconEnd = true, weight = 700)

@Composable
private fun CalendarCard(e: Env) {
    // r5: no month grid in small slots, only in L (iPhone's default calendar widget is "Mon Aug / 19").
    if (e.cls != SizeClass.L) {
        DateWidget(e)
        return
    }
    val t = e.today
    val first = t.withDayOfMonth(1)
    val lead = first.dayOfWeek.value % 7 // Sunday first
    val days = t.lengthOfMonth()
    val weeks = (lead + days + 6) / 7
    val titleCap = e.sz.title * CAP_UI
    val gridH = e.ch - titleCap - e.gap * 1.5f
    val pitch = gridH / (weeks + 1)
    val cellW = e.cw / 7
    val f = min(e.ownLabel * 1.2f, min(pitch * 0.56f, cellW * 0.5f))
    if (f < 18.dp) {
        DateWidget(e)
        return
    }
    val r = min(cellW, pitch) * 0.44f
    Column(Modifier.fillMaxSize().padding(e.pad)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Txt("${dowKo(t)}요일", e.sz.title, e.ink.weekday, weight = 700)
            Spacer(Modifier.width(e.sz.title * 0.35f))
            Txt("${t.monthValue}월", e.sz.title, e.ink.ink2, weight = 700, num = true)
        }
        Spacer(Modifier.height(e.gap * 1.5f))
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Txt(it, f, e.ink.ink2, weight = 600) }
                }
            }
            for (wk in 0 until weeks) {
                Row(Modifier.fillMaxWidth()) {
                    for (dw in 0 until 7) {
                        val day = wk * 7 + dw - lead + 1
                        val isToday = day == t.dayOfMonth
                        val red = e.ink.weekday
                        Box(
                            Modifier.weight(1f).height(f * e.numCap).then(
                                if (isToday) Modifier.drawBehind { drawCircle(red, r.toPx(), center) } else Modifier,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day in 1..days) Txt(
                                "$day", f, if (isToday) lerp(Color.White, Color.Black, e.ink.night) else e.ink.ink,
                                weight = 700, family = e.num, cap = e.numCap, tnum = true,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** iPhone date widget (mockup r5): red "월요일" + grey "11월" over a big rounded day number. */
@Composable
private fun DateWidget(e: Env) {
    val t = e.today
    val top = max(e.label, e.sz.title)
    Column(Modifier.fillMaxSize().padding(e.pad)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Txt("${dowKo(t)}요일", top, e.ink.weekday, weight = 700)
            Spacer(Modifier.width(top * 0.3f))
            Txt("${t.monthValue}월", top, e.ink.ink2, weight = 700, num = true)
        }
        Spacer(Modifier.height(e.gap * 2f))
        Txt(
            "${t.dayOfMonth}", e.s, e.ink.ink, weight = 700, family = e.num, cap = e.numCap, tnum = true,
            maxW = e.cw, maxCapH = e.ch - top * CAP_UI - e.gap * 2f,
        )
    }
}

@Composable
private fun MemoriesCard(e: Env, state: StandbyState, actions: StandbyActions) {
    val p = state.memory
    if (p == null) {
        if (!state.permissions.photos) {
            StateCard(e, Ic.PHOTO, "추억", Ic.LOCK, null, "권한 필요 · 눌러서 설정") { actions.openSettings("photos") }
        } else {
            StateCard(e, Ic.PHOTO, "추억", null, "추억 없음", null, null)
        }
        return
    }
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(e.plateRadius))) {
        Image(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = nightFilter(e.ink.night))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.65f))))
        Column(Modifier.align(Alignment.BottomStart).padding(e.pad)) {
            Txt(p.yearsAgo?.let { "${it}년 전 오늘" } ?: p.place, min(e.sz.title, e.label * 1.4f), e.ink.ink, weight = 700, maxW = e.cw, num = true)
            if (e.atLeastS) {
                Spacer(Modifier.height(e.gap))
                // review X4: one line; the short date ("설악산 · 2023.11.9") when the long one does not fit
                val long = "${p.place} · ${p.date}"
                val digits = Regex("\\d+").findAll(p.date).map { it.value }.toList()
                val short = if (digits.size == 3) "${p.place} · ${digits.joinToString(".")}" else long
                Txt(if (fitsOneLine(long, e.label, 500, e.cw)) long else short, e.label, e.ink.ink, weight = 500, tnum = true)
            }
        }
    }
}
