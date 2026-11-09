package kr.dot.sb.ui.cards

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.MediaKind
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.state.TimerState
import kr.dot.sb.ui.state.UsageWindow
import kr.dot.sb.ui.state.WeatherInfo
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalInk
import kr.dot.sb.ui.theme.SizeClass
import kr.dot.sb.ui.theme.Sizes
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * One card filling its slot (design-system §4). Size class, shape and text sizes come from the slot size; [look]
 * carries the board-level rules (accent, hero, shared label size and pad). [slotId] keys per-slot state (timers).
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
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val ink = remember(state.night, look.accent) { Ink.of(state.night, look.accent) }
        val style = config.styles[look.clockFace] ?: ClockStyle.default(look.clockFace)
        val today = state.now.toLocalDate()
        val e = remember(w, h, ink, look, style.font, style.weight, slotId, today) {
            val own = Sizes.of(w, h)
            Env(
                w, h, own.copy(label = look.labelSize ?: own.label, pad = look.pad ?: own.pad), own.label, ink, look,
                Fonts.clock(style.font, style.weight), capOf(style.font), slotId, today,
            )
        }
        CompositionLocalProvider(LocalInk provides ink) {
            when (type) {
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

// ---------------------------------------------------------------- clocks

@Composable
private fun ClockCard(e: Env, state: StandbyState, config: AppConfig) {
    val face = e.look.clockFace
    val base = config.styles[face] ?: ClockStyle.default(face)
    // §5.1: hero digits carry the accent; a second clock on the board renders its numerals in ink (hero = false).
    // The date is this card's own BOT-line detail, so the face's in-slot date row is off (no double date).
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
    val base = config.styles[FaceId.ANALOG] ?: ClockStyle.default(FaceId.ANALOG)
    val acc = e.look.accent.argbLong()
    // Accent only on the second hand (hero or not); hands and numerals keep their own neutral colours.
    ClockFace(FaceId.ANALOG, base.copy(color = acc, secondColor = acc), state, Modifier.fillMaxSize(), inSlot = true)
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
private fun Art(e: Env, img: ImageBitmap?, placeholder: Ic, modifier: Modifier, radius: Dp) {
    val shape = RoundedCornerShape(radius)
    if (img != null) {
        Image(img, null, modifier.clip(shape), contentScale = ContentScale.Crop, colorFilter = nightFilter(e.ink.night))
    } else {
        Box(modifier.clip(shape).background(e.ink.ink4), contentAlignment = Alignment.Center) {
            IconView(placeholder, e.ink.ink3, Modifier.fillMaxSize(0.4f), e.ink.ink4)
        }
    }
}

@Composable
private fun Btn(e: Env, ic: Ic, size: Dp, enabled: Boolean = true, onClick: () -> Unit) {
    Box(Modifier.size(size).tap(if (enabled) onClick else null)) {
        IconView(ic, if (enabled) e.ink.ink else e.ink.ink3, Modifier.fillMaxSize())
    }
}

/** prev/next = label × 1.6, play = label × 2.4 (§4.2). S keeps play/pause only. */
@Composable
private fun Transport(e: Env, m: MediaInfo, actions: StandbyActions, full: Boolean, maxW: Dp) {
    // Scale the whole row down when the column is narrower than the nominal 7.4 labels (never clip "next").
    val u = e.label * kotlin.math.min(1f, maxW / (e.label * (if (full) 7.4f else 2.4f)))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(u * 0.9f)) {
        if (full) Btn(e, Ic.PREV, u * 1.6f, m.canPrev, actions::mediaPrev)
        Btn(e, if (m.playing) Ic.PAUSE else Ic.PLAY, u * 2.4f, onClick = actions::mediaPlayPause)
        if (full) Btn(e, Ic.NEXT, u * 1.6f, m.canNext, actions::mediaNext)
    }
}

private fun progress(m: MediaInfo) = if (m.durationMs > 0) m.positionMs.toFloat() / m.durationMs else 0f

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
            Art(e, m.art, ic, Modifier.size(e.cw), max(12.dp, e.cw * 0.06f))
            Btn(e, if (m.playing) Ic.PAUSE else Ic.PLAY, e.label * 2.4f, onClick = actions::mediaPlayPause)
        }
        return
    }
    Box(Modifier.fillMaxSize().tap(actions::openMediaApp)) {
        Art(e, m.art, ic, Modifier.fillMaxSize(), e.plateRadius)
        val d = e.label * 2.4f
        Box(
            Modifier.align(Alignment.BottomEnd).padding(e.pad * 0.7f).size(d).clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.6f)).tap(actions::mediaPlayPause),
            contentAlignment = Alignment.Center,
        ) { IconView(if (m.playing) Ic.PAUSE else Ic.PLAY, e.ink.ink, Modifier.size(d * 0.46f)) }
    }
}

@Composable
private fun MusicBody(e: Env, m: MediaInfo, actions: StandbyActions) {
    val full = e.atLeastM
    val playH = e.label * 2.4f
    val text: @Composable (Modifier, Dp, Boolean) -> Unit = { mod, textW, label ->
        Column(mod, verticalArrangement = Arrangement.SpaceBetween) {
            if (label) LabelRow(e, Ic.NOTE, m.appLabel)
            Column {
                Txt(m.title, e.sz.title, e.ink.ink, weight = 700)
                Spacer(Modifier.height(e.gap * 0.75f))
                Detail(e, m.artist)
            }
            Column {
                Bar(e, progress(m), e.ink.acc)
                Spacer(Modifier.height(e.gap))
                Transport(e, m, actions, full, textW)
            }
        }
    }
    // Art top (tall/square) or art left (wide), whichever gives the bigger plate — 4:3 slots get art left.
    val need = e.labelCap + e.sz.title * CAP_UI + e.labelCap + e.bar + playH + e.gap * 5.5f
    val aTop = min(e.ch - need, e.cw * (if (e.tall) 0.62f else 0.5f))
    // wide: the label spans the full width on the TOP line (review X5), art + text start below it
    val below = e.ch - e.labelCap - e.gap * 1.5f
    val aLeft = min(below, e.cw * 0.45f)
    if (!e.tall && aLeft > aTop) {
        Column(Modifier.fillMaxSize().padding(e.pad)) {
            LabelRow(e, Ic.NOTE, m.appLabel)
            Spacer(Modifier.height(e.gap * 1.5f))
            Row(Modifier.weight(1f)) {
                Art(e, m.art, Ic.NOTE, Modifier.size(aLeft), max(12.dp, aLeft * 0.06f))
                Spacer(Modifier.width(e.pad))
                text(Modifier.weight(1f).fillMaxHeight(), e.cw - aLeft - e.pad, false)
            }
        }
    } else {
        val a = aTop
        Column(Modifier.fillMaxSize().padding(e.pad)) {
            if (a >= e.label * 3) {
                Art(e, m.art, Ic.NOTE, Modifier.size(a), max(12.dp, a * 0.06f))
                Spacer(Modifier.height(e.gap * 1.5f))
            }
            text(Modifier.weight(1f).fillMaxWidth(), e.cw, true)
        }
    }
}

/** Video: the still fills the slot like a photo card (scrim top and bottom), text bottom-leading, transport trailing. */
@Composable
private fun VideoBody(e: Env, m: MediaInfo, actions: StandbyActions) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(e.plateRadius))) {
        Art(e, m.art, Ic.VIDEO, Modifier.fillMaxSize(), 0.dp)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    // text now stacks from mid-card (music order), so the bottom scrim starts higher
                    0f to Color.Black.copy(alpha = 0.5f), 0.25f to Color.Black.copy(alpha = 0.15f),
                    0.45f to Color.Black.copy(alpha = 0.4f), 1f to Color.Black.copy(alpha = 0.75f),
                ),
            ),
        )
        TextCard(e) {
            LabelRow(e, Ic.VIDEO, m.appLabel, color = e.ink.ink)
            // Music's order exactly (review X8): title → channel → progress → transport, transport at the leading edge.
            Column {
                Txt(m.title, e.sz.title, e.ink.ink, weight = 700)
                Spacer(Modifier.height(e.gap * 0.75f))
                Detail(e, m.artist, color = e.ink.ink.copy(alpha = 0.8f)) // on a photo: white like Memories
                Spacer(Modifier.height(e.gap * 1.5f))
                Bar(e, progress(m), e.ink.acc)
                Spacer(Modifier.height(e.gap))
                Transport(e, m, actions, e.atLeastM, e.cw)
            }
        }
    }
}

// ---------------------------------------------------------------- timer, sleep, d-day

@Composable
private fun TimerCard(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val total0 = config.focusMinutes * 60
    val t = state.timers[e.slotId] ?: TimerState(leftSec = total0, totalSec = total0)
    val frac = if (t.totalSec > 0) t.leftSec.toFloat() / t.totalSec else 0f
    val mode = if (t.mode == kr.dot.sb.ui.state.TimerMode.FOCUS) "집중" else "휴식"
    val hint = when {
        t.running -> "눌러서 멈춤"
        t.leftSec >= t.totalSec -> "눌러서 시작"
        else -> "눌러서 계속"
    }
    val digits = "%02d:%02d".format(t.leftSec / 60, t.leftSec % 60)
    val ink = e.ink
    if (!e.atLeastM) {
        // S/XS (review B2): mode label on the TOP line like every other card, ring + digits below, no hint
        Column(Modifier.fillMaxSize().padding(e.pad).tap { actions.timerToggle(e.slotId) }) {
            LabelRow(e, Ic.TIMER, mode)
            Spacer(Modifier.height(e.gap))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                val d = min(maxWidth, maxHeight)
                val sw = d * 0.05f
                Ring(d, sw, frac, ink)
                Txt(
                    digits, e.sz.display, if (e.look.hero) ink.acc else ink.ink, family = e.num, cap = e.numCap, tnum = true,
                    maxW = (d - sw * 2) * 0.74f, maxCapH = (d - sw * 2) * 0.3f,
                )
            }
        }
        return
    }
    val d = min(e.w, e.h) - e.s * 0.08f
    val sw = d * 0.05f
    Box(Modifier.fillMaxSize().tap { actions.timerToggle(e.slotId) }, contentAlignment = Alignment.Center) {
        Ring(d, sw, frac, ink)
        val inner = d - sw * 2
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Txt(mode, e.label, ink.acc)
            Spacer(Modifier.height(inner * 0.07f))
            Txt(
                digits, e.sz.display, if (e.look.hero) ink.acc else ink.ink, family = e.num, cap = e.numCap, tnum = true,
                maxW = inner * 0.74f, maxCapH = inner * 0.3f,
            )
            Spacer(Modifier.height(inner * 0.07f))
            Detail(e, hint)
        }
    }
}

@Composable
private fun Ring(d: Dp, sw: Dp, frac: Float, ink: Ink) {
    Canvas(Modifier.size(d)) {
        val swPx = sw.toPx()
        val tl = Offset(swPx / 2, swPx / 2)
        val sz = Size(size.width - swPx, size.height - swPx)
        drawArc(ink.ink3, 0f, 360f, false, tl, sz, style = Stroke(swPx))
        if (frac > 0f) drawArc(ink.acc, -90f, 360f * frac, false, tl, sz, style = Stroke(swPx, cap = StrokeCap.Round))
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
        // XS: no detail, so the wave is centred in the space below the label (review X9)
        Column(Modifier.fillMaxSize().padding(e.pad).tap(actions::sleepToggle), horizontalAlignment = Alignment.CenterHorizontally) {
            LabelRow(e, Ic.SOUND, config.sleepSound.ko, center = true)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { wave() }
        }
        return
    }
    TextCard(e, Modifier.tap(actions::sleepToggle), horizontal = Alignment.CenterHorizontally) {
        LabelRow(e, Ic.SOUND, config.sleepSound.ko, center = true)
        wave()
        Detail(e, if (on) "${state.sleep.minutesLeft ?: config.sleepMinutes}분 뒤 꺼짐" else "눌러서 틀기", align = TextAlign.Center)
    }
}

/** The only infinite animation on the board, composed only while the sound plays; the phase is read in draw only. */
@Composable
private fun AnimatedWave(e: Env, modifier: Modifier) {
    val phase = rememberInfiniteTransition(label = "wave").animateFloat(
        0f, (2 * Math.PI).toFloat(), infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "phase",
    )
    Wave(e, modifier, e.ink.acc, phase)
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
    // §4.2: accent only when no clock shares the board.
    val col = if (e.look.clockOnBoard) e.ink.ink else e.ink.acc
    val avail = e.ch - e.labelCap * (if (e.atLeastS) 2 else 1) - e.s * 0.08f
    TextCard(e) {
        LabelRow(e, Ic.CALENDAR, dd.name)
        Txt(txt, e.sz.display, col, family = e.num, cap = e.numCap, tnum = true, maxW = e.cw, maxCapH = avail)
        if (e.atLeastS) Detail(e, dateKo(dd.date))
    }
}

// ---------------------------------------------------------------- agenda, usage

@Composable
private fun AgendaCard(e: Env, state: StandbyState, actions: StandbyActions) {
    val items = state.agenda
    if (items == null) {
        StateCard(e, Ic.CALENDAR, "오늘 일정", Ic.LOCK, null, "권한 필요 · 눌러서 설정") { actions.openSettings("calendar") }
        return
    }
    if (items.isEmpty()) {
        StateCard(e, Ic.CALENDAR, "오늘", null, "일정 없음", null, null)
        return
    }
    if (e.cls == SizeClass.XS) {
        val now = "%02d:%02d".format(state.now.hour, state.now.minute)
        val next = items.firstOrNull { it.start >= now } ?: items.last()
        TextCard(e) {
            LabelRow(e, Ic.CALENDAR, "오늘 ${next.start}")
            Txt(next.title, e.loneValue, e.ink.ink, weight = 700, maxW = e.cw, maxCapH = e.ch - e.labelCap - e.gap * 2)
        }
        return
    }
    val rows = items.take(if (e.atLeastM) 3 else 2)
    val ev = max(e.label, min(e.sz.title, e.s * 0.10f))
    // review X9: fixed baseline pitch (1.9 × row title) under the label; leftover space stays below the list
    val pitch = min(ev * 1.9f, (e.ch - e.labelCap) / rows.size)
    Column(Modifier.fillMaxSize().padding(e.pad)) {
        LabelRow(e, Ic.CALENDAR, "오늘", trailing = "${items.size}개")
        rows.forEach { it ->
            Spacer(Modifier.height(pitch - ev * CAP_UI))
            Row(verticalAlignment = Alignment.Bottom) {
                Box(Modifier.width(max(4.dp, e.s * 0.02f)).height(ev * CAP_UI).clip(RoundedCornerShape(50)).background(e.ink.mapped(Color(it.color))))
                Spacer(Modifier.width(e.label * 0.6f))
                Txt(it.start, e.label, e.ink.ink2, weight = 500, tnum = true)
                Spacer(Modifier.width(e.label * 0.6f))
                Txt(it.title, ev, e.ink.ink, Modifier.weight(1f), weight = 700)
            }
        }
    }
}

private fun pctOf(w: UsageWindow?) = w?.usedPct?.let { "$it%" } ?: "–"

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
    fun tone(w: UsageWindow?, base: Color) = if ((w?.usedPct ?: 0) >= 90) e.ink.red else base
    val age = "${hhmm(u.fetchedAtEpochSec)} 기준"
    val tapMod = Modifier.tap(if (u.offline) actions::retryFeeds else null)
    val offlineLine: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(Modifier.size(e.labelCap * 0.6f).clip(CircleShape).background(e.ink.ink2))
            Spacer(Modifier.width(e.label * 0.4f))
            Detail(e, "$age · 연결 안 됨")
        }
    }
    if (e.cls == SizeClass.XS) {
        val all = listOf("Claude 5시간" to u.claude5h, "Claude 7일" to u.claude7d, "Codex 5시간" to u.codex5h, "Codex 7일" to u.codex7d)
        val (name, w) = all.maxByOrNull { it.second?.usedPct ?: -1 }!!
        TextCard(e, tapMod) {
            LabelRow(e, Ic.USAGE, name)
            Txt(pctOf(w), e.loneValue, tone(w, valCol), weight = 600, tnum = true, maxW = e.cw, maxCapH = e.ch - e.labelCap - e.gap * 2)
        }
        return
    }
    val rows = listOf(Triple("Claude", u.claude5h, u.claude7d), Triple("Codex", u.codex5h, u.codex7d))
    val detail = e.atLeastM
    val fixed = e.labelCap + (if (u.offline) e.labelCap + e.gap else 0.dp) + e.gap * 5
    val perRow = (e.ch - fixed) / 2 - e.bar - e.gap * 1.2f - (if (detail) e.labelCap + e.gap else 0.dp)
    // Two values share one card: 0.7 × value keeps "Claude 5시간" readable beside the number.
    val vSize = min(e.sz.value * 0.7f, perRow / CAP_UI)
    // review X9: rows stack from the label with a fixed gap; leftover space goes below (BOT binds only the offline line)
    Column(Modifier.fillMaxSize().padding(e.pad).then(tapMod)) {
        LabelRow(e, Ic.USAGE, "사용량", trailing = if (u.offline) null else age)
        rows.forEach { (name, h5, d7) ->
            Spacer(Modifier.height(e.gap * 2.5f))
            Column {
                // review X3: the value is measured first (no weight) and never ellipsizes; the name takes the rest
                Row(verticalAlignment = Alignment.Bottom) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                        Txt(name, e.label, e.ink.ink, Modifier.weight(1f, fill = false), weight = 700)
                        if (e.atLeastM) {
                            Spacer(Modifier.width(e.label * 0.4f))
                            Txt("5시간", e.label, e.ink.ink2, weight = 500)
                        }
                    }
                    Spacer(Modifier.width(e.label * 0.4f))
                    Txt(pctOf(h5), vSize, tone(h5, valCol), weight = 600, tnum = true)
                }
                Spacer(Modifier.height(e.gap * 1.2f))
                Bar(e, (h5?.usedPct ?: 0) / 100f, tone(h5, e.ink.ink))
                if (detail) {
                    Spacer(Modifier.height(e.gap))
                    val reset = h5?.resetsAtEpochSec?.let { " · ${hhmm(it)} 초기화" } ?: ""
                    Detail(e, "7일 ${pctOf(d7)}$reset")
                }
            }
        }
        if (u.offline) { Spacer(Modifier.weight(1f)); offlineLine() }
    }
}

// ---------------------------------------------------------------- weather, calendar, memories

private fun tempFont() = Fonts.clock(FontId.LIGHT, 300)
private const val INTER_CAP = 0.7275f

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
        val hoursH = e.labelCap + e.label * 1.5f + e.sz.title * CAP_UI + e.gap * 1.6f
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

@Composable
private fun WeatherMain(e: Env, wx: WeatherInfo, old: Boolean, modifier: Modifier, h: Dp, w: Dp) {
    val lines = when { !e.atLeastS -> 0; e.atLeastM -> 2; else -> 1 }
    val avail = h - e.labelCap * (1 + lines) - e.gap * (1 + lines)
    Column(modifier, verticalArrangement = Arrangement.SpaceBetween) {
        WeatherLabel(e, wx.city)
        Txt(temp(wx.tempC), e.loneDisplay, if (old) e.ink.ink2 else e.ink.ink, family = tempFont(), cap = INTER_CAP, tnum = true, maxW = w, maxCapH = avail)
        if (lines > 0) {
            Column {
                val off = if (wx.offline) "${hhmm(wx.fetchedAtEpochSec)} 기준 · 연결 안 됨" else null
                if (lines == 1 && off != null) {
                    Detail(e, off)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(Modifier.height(e.labelCap).wrapUnbounded()) { IconView(weatherIc(wx.condition), e.ink.ink, Modifier.size(e.label * 1.25f), e.ink.bg) }
                        Spacer(Modifier.width(e.label * 0.4f))
                        Txt(wx.condition.ko, e.label, e.ink.ink, weight = 600)
                    }
                }
                if (lines == 2) {
                    Spacer(Modifier.height(e.gap))
                    Detail(e, off ?: "최고 ${wx.highC}° 최저 ${wx.lowC}°")
                }
            }
        }
    }
}

/** Next hours: "22시" / icon / "6°" columns. [spread] = columns span TOP→BOT (wide side panel). */
@Composable
private fun Hours(e: Env, wx: WeatherInfo, modifier: Modifier, spread: Boolean) {
    // review B1: 5 equal columns, each centred; one temperature size for all columns that fits "-10°" in a column
    BoxWithConstraints(modifier) {
        val col = maxWidth / 5
        val tSize = min(e.sz.title, col * 0.9f / 2.1f)
        Row(Modifier.fillMaxSize()) {
            wx.hourly.take(5).forEach { hr ->
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = if (spread) Arrangement.SpaceBetween else Arrangement.spacedBy(e.gap * 0.8f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Txt("${hr.hour}시", min(e.label, col * 0.8f / 2.2f), e.ink.ink2, weight = 500, tnum = true)
                    IconView(weatherIc(hr.condition), e.ink.ink, Modifier.size(e.label * 1.6f), e.ink.bg)
                    Txt(temp(hr.tempC), tSize, e.ink.ink, weight = 600, tnum = true)
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
    val t = e.today
    val first = t.withDayOfMonth(1)
    val lead = first.dayOfWeek.value % 7 // Sunday first
    val days = t.lengthOfMonth()
    val weeks = (lead + days + 6) / 7
    val titleCap = e.sz.title * CAP_UI
    val gridH = e.ch - titleCap - e.gap * 1.5f
    val pitch = gridH / (weeks + 1)
    val cellW = e.cw / 7
    val f = min(e.ownLabel, min(pitch * 0.56f, cellW * 0.5f))
    if (!e.atLeastM || f < 18.dp) {
        DateWidget(e)
        return
    }
    val r = min(cellW, pitch) * 0.44f
    Column(Modifier.fillMaxSize().padding(e.pad)) {
        Txt("${t.monthValue}월", e.sz.title, e.ink.red, weight = 700)
        Spacer(Modifier.height(e.gap * 1.5f))
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth()) {
                listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Txt(it, f, e.ink.ink2, weight = 500) }
                }
            }
            for (wk in 0 until weeks) {
                Row(Modifier.fillMaxWidth()) {
                    for (dw in 0 until 7) {
                        val day = wk * 7 + dw - lead + 1
                        val isToday = day == t.dayOfMonth
                        val red = e.ink.red
                        Box(
                            Modifier.weight(1f).height(f * CAP_UI).then(
                                if (isToday) Modifier.drawBehind { drawCircle(red, r.toPx(), center) } else Modifier,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day in 1..days) Txt("$day", f, if (isToday) androidx.compose.ui.graphics.lerp(Color.White, Color.Black, e.ink.night) else e.ink.ink, weight = 600, tnum = true)
                        }
                    }
                }
            }
        }
    }
}

/** XS/S calendar: "월요일 11월" over a big day number (iPhone date widget). */
@Composable
private fun DateWidget(e: Env) {
    val t = e.today
    val top = max(e.label, e.sz.title)
    TextCard(e) {
        Row(verticalAlignment = Alignment.Bottom) {
            Txt("${dowKo(t)}요일", top, e.ink.red, weight = 700)
            Spacer(Modifier.width(top * 0.35f))
            Txt("${t.monthValue}월", top, e.ink.ink2, weight = 700)
        }
        Txt("${t.dayOfMonth}", e.s, e.ink.ink, weight = 600, tnum = true, maxW = e.cw, maxCapH = e.ch - top * CAP_UI - e.gap * 1.5f)
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
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
        Column(Modifier.align(Alignment.BottomStart).padding(e.pad)) {
            Txt(p.yearsAgo?.let { "${it}년 전 오늘" } ?: p.place, e.sz.title, e.ink.ink, weight = 700, maxW = e.cw)
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
