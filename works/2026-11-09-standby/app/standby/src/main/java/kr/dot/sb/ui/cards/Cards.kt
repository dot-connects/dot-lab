package kr.dot.sb.ui.cards

import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kr.dot.sb.model.SleepSound
import kr.dot.sb.ui.icons.SoundIcon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
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
import kr.dot.sb.ui.state.WeatherInfo
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalInk
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.LocalSlotLabel
import kr.dot.sb.ui.theme.ProvideTheme
import kr.dot.sb.ui.theme.SizeClass
import kr.dot.sb.ui.theme.Sizes
import kr.dot.sb.ui.theme.themedSurface
import kr.dot.sb.model.BoardBg
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import kr.dot.sb.ui.clock.rememberSecond
import kr.dot.sb.ui.screens.clockText
import kr.dot.sb.ui.theme.ThemedDigits
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import kr.dot.sb.ui.theme.Themes
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.AnnotatedString
import kr.dot.sb.ui.state.AgendaItem
import kr.dot.sb.ui.state.DayTemp
import kr.dot.sb.ui.state.HourTemp
import kr.dot.sb.ui.theme.Contrast
import androidx.compose.ui.graphics.compositeOver
import java.time.LocalDate
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
    /** This card's [kr.dot.sb.model.SlotOptions] and its settings route (phone widget only). */
    options: kr.dot.sb.model.SlotOptions? = null,
    menu: String? = null,
    /** Full-screen Now Playing: no plate, the media body shows ✕ (calls this) and stays put on tap. */
    close: (() -> Unit)? = null,
    /** Clock card: a tap shows arrows through 내 시계, the pick is saved through this (null = no arrows). */
    onFace: ((FaceId) -> Unit)? = null,
) = ProvideTheme(config) {
    // a clock card's own face (11-14) wins over the board's; while the arrows are up, the face being browsed wins
    val saved = options?.face ?: look.clockFace
    val sw = rememberFaceSwitch(saved)
    @Suppress("NAME_SHADOWING") val look = (sw.face ?: sw.pending ?: saved).let { if (it != look.clockFace) look.copy(clockFace = it) else look }
    if (type == CardType.CLOCK && onFace != null) {
        val commit by androidx.compose.runtime.rememberUpdatedState(onFace)
        // 3 s without a tap: the arrows go and the browsed face is saved to this card
        LaunchedEffect(sw.touches) {
            val f = sw.face ?: return@LaunchedEffect
            delay(FACE_ARROWS_MS)
            sw.face = null
            if (f != saved) { sw.pending = f; commit(f) }
        }
        // until the saved config comes back with it (or 2 s), keep showing the pick: no flash of the old face
        LaunchedEffect(sw.pending, saved) { if (sw.pending != null) { if (saved == sw.pending) sw.pending = null else { delay(2_000); sw.pending = null } } }
    }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val t = LocalTheme.current
        val ink = remember(state.night, look.accent, t) { Ink.of(state.night, look.accent, t) }
        val today = state.now.toLocalDate()
        val e = remember(w, h, ink, look, slotId, today, t) {
            val own = Sizes.of(w, h, r = t.ramp)
            Env(
                w, h, own.copy(label = look.labelSize ?: own.label, pad = look.pad ?: own.pad), own.label, ink, look,
                t.numerals, t.numeralsCap, slotId, today, t,
            )
        }
        // every card sits on the theme's plate (mockup "테마 전체"); the notification card wears it only while closed
        val plate = Modifier.themedSurface(t, ink, RoundedCornerShape(e.plateRadius), custom = config.boardBg != BoardBg.THEME)
        val surface = if (type.isNotif || close != null) Modifier else plate
        CompositionLocalProvider(LocalInk provides ink, LocalSlotLabel provides e.label) {
            val arrows = type == CardType.CLOCK && onFace != null
            Box(Modifier.fillMaxSize().then(surface).tap(if (arrows) { { sw.touch() } } else null)) {
                when (type) {
                    CardType.CLOCK -> ClockCard(e, state, config)
                    CardType.MUSIC -> MediaCard(e, state, actions, close)
                    CardType.TIMER -> TimerCard(e, state, config, actions)
                    CardType.SLEEP -> SleepCard(e, state, config, actions)
                    CardType.DDAY -> DdayCard(e, config)
                    CardType.AGENDA -> AgendaCard(e, state, actions)
                    CardType.USAGE -> UsageCard(e, state, actions)
                    CardType.WEATHER -> WeatherCard(e, state, config, actions)
                    CardType.CALENDAR -> CalendarCard(e, state, actions)
                    CardType.MEMORIES -> MemoriesCard(e, state, actions)
                    CardType.WIDGET -> PhoneWidget(e, options, actions, menu)
                    // r9: stack and list merged into one card (boards saved with the stack still draw it)
                    CardType.NOTIF -> NotifCard(e, state, actions, plate)
                    CardType.ALARM -> AlarmCard(e, state, config, actions)
                }
                if (arrows && sw.face != null) FaceArrows(e, faceCycle(config, saved), sw)
            }
        }
    }
}

// ---------------------------------------------------------------- clocks

/** How long the clock card's arrows stay after the last tap (11-14 #7, mockup option A). */
private const val FACE_ARROWS_MS = 3_000L

/** The clock card's arrows: [face] = the face being browsed (null = hidden), [pending] = saved, not yet back from the config. */
@androidx.compose.runtime.Stable
internal class FaceSwitch(private val saved: () -> FaceId) {
    var face by mutableStateOf<FaceId?>(null)
    var pending by mutableStateOf<FaceId?>(null)
    var touches by mutableIntStateOf(0)
        private set

    /** A tap on the card: show the arrows (or keep them up 3 s longer). */
    fun touch() { if (face == null) face = pending ?: saved(); touches++ }

    fun step(faces: List<FaceId>, d: Int) {
        val i = faces.indexOf(face).coerceAtLeast(0)
        face = faces[(i + d + faces.size) % faces.size]; touches++
    }
}

@Composable
internal fun rememberFaceSwitch(saved: FaceId): FaceSwitch {
    val cur by androidx.compose.runtime.rememberUpdatedState(saved)
    return remember { FaceSwitch { cur } }
}

/** The faces the arrows cycle: 내 시계 that draw in a slot, the card's own face first if it is not one of them. */
private fun faceCycle(config: AppConfig, saved: FaceId): List<FaceId> {
    val my = kr.dot.sb.ui.clock.ClockRegistry.pages(config.myClocks).filter { it in kr.dot.sb.ui.clock.ClockRegistry.boardFaces }
    return if (saved in my) my else listOf(saved) + my
}

/** Mockup option A: "<name> · n / N" pill on top, round chevrons inside the left and right edges, dots at the bottom. */
@Composable
private fun FaceArrows(e: Env, faces: List<FaceId>, sw: FaceSwitch) {
    val f = sw.face ?: return
    val i = faces.indexOf(f)
    val ink = e.ink
    val d = min(48.dp, e.short * 0.24f)
    val inset = max(6.dp, e.pad * 0.4f)
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.align(Alignment.TopCenter).padding(top = inset).clip(RoundedCornerShape(50)).background(ink.ink.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Txt("${kr.dot.sb.ui.clock.spec(f).ko} · ${i + 1} / ${faces.size}", e.meta, ink.ink, weight = 600, num = true)
        }
        listOf(-1 to Alignment.CenterStart, 1 to Alignment.CenterEnd).forEach { (dir, at) ->
            // 48 dp to the finger, [d] to the eye
            Box(Modifier.align(at).padding(horizontal = inset).size(max(d, 48.dp)).clickable(onClickLabel = if (dir < 0) "이전 시계" else "다음 시계") { sw.step(faces, dir) }, Alignment.Center) {
                Box(Modifier.size(d).clip(CircleShape).background(ink.ink.copy(alpha = 0.14f)))
                Canvas(Modifier.size(d * 0.5f)) {
                    val w = size.width; val h = size.height
                    val p = androidx.compose.ui.graphics.Path().apply {
                        if (dir < 0) { moveTo(w * 0.62f, h * 0.2f); lineTo(w * 0.32f, h * 0.5f); lineTo(w * 0.62f, h * 0.8f) }
                        else { moveTo(w * 0.38f, h * 0.2f); lineTo(w * 0.68f, h * 0.5f); lineTo(w * 0.38f, h * 0.8f) }
                    }
                    drawPath(p, ink.ink, style = Stroke(width = w * 0.11f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
                }
            }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = inset), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            faces.indices.forEach { k -> Box(Modifier.size(if (k == i) 14.dp else 6.dp, 6.dp).clip(RoundedCornerShape(50)).background(if (k == i) ink.ink else ink.ink3)) }
        }
    }
}

@Composable
private fun ClockCard(e: Env, state: StandbyState, config: AppConfig) {
    val face = e.look.clockFace
    if (face == FaceId.DIGITAL) return DigitalCard(e, state, config)
    // the old analog card (merged in 11-14) keeps its look until the user styles the analog face
    if (face == FaceId.ANALOG && face !in config.styles) return AnalogCard(e, state)
    // other faces picked for the board keep their own slot rendering. 내 시계 (11-14): a face the user styled draws as
    // styled (no accent, no second-clock ink); theme defaults keep the board accent
    val custom = face in config.styles
    val style = config.clockStyle(face).let { if (custom) it else it.copy(color = e.look.accent.argbLong()) }.copy(showInfo = false)
    Column(Modifier.fillMaxSize().padding(e.pad), horizontalAlignment = Alignment.CenterHorizontally) {
        ClockFace(face, style, state, Modifier.fillMaxWidth().weight(1f), inSlot = true, hero = e.look.hero || custom)
        if (e.atLeastS) {
            Spacer(Modifier.height(e.gap))
            Detail(e, dateKo(e.today), align = TextAlign.Center)
        }
    }
}

/** Short side of the slot; < 150 dp drops the date (spec §1 clock). */
private val Env.short: Dp get() = min(w, h)

/** "월요일 11월 9일 · 7°", weekday in the text accent (ink on a second clock), [size] × the text-size setting. */
@Composable
private fun DateLine(e: Env, state: StandbyState, size: Dp) {
    val d = e.today
    val rest = " ${d.monthValue}월 ${d.dayOfMonth}일" + (state.weather?.let { " · ${it.tempC}°" } ?: "")
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = if (e.look.hero) e.ink.accText else e.ink.ink)) { append("${dowKo(d)}요일") }
        append(rest)
    }
    Txt(text, size * e.look.textScale, e.readInk2, weight = 500, align = TextAlign.Center, maxW = e.w)
}

/**
 * Digital clock card (spec §1): theme digits fill the slot, one line "9:41" when w/h ≥ 1.05 or the short side < 150,
 * else hours over minutes; the date line (13–20 dp) right under them. The user's own clock style (font/colour) wins
 * over the theme's, as styled (내 시계, 11-14). With theme defaults a second, smaller clock on the board draws its
 * digits in ink.
 */
@Composable
private fun DigitalCard(e: Env, state: StandbyState, config: AppConfig) {
    val oneLine = e.w / e.h >= 1.05f || e.short < 150.dp
    val dl = (e.short * 0.055f).coerceIn(13.dp, 20.dp)
    val own = config.styles[FaceId.DIGITAL]
    Column(
        Modifier.fillMaxSize().padding((e.short * 0.08f).coerceIn(10.dp, 32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        ThemedDigits(
            clockText(state.now, state.use24h), e.t, e.ink, Modifier.fillMaxWidth().weight(1f, fill = false),
            style = own, color = if (e.look.hero || own != null) null else e.ink.ink, twoLines = !oneLine,
        )
        if (e.short >= 150.dp) {
            Spacer(Modifier.height(dl * 0.6f))
            DateLine(e, state, dl)
        }
    }
}

/**
 * Analog clock card (spec §1, mockup "아날로그"): the iPhone widget dial in theme ink with an accent second hand; wide
 * slots (w/h ≥ 1.45) put a date block right of the dial, tall ones (≤ 0.7) a date line under it, others the dial only.
 */
@Composable
private fun AnalogCard(e: Env, state: StandbyState) {
    val pad = (e.short * 0.07f).coerceIn(8.dp, 28.dp)
    val r = e.w / e.h
    when {
        r >= 1.45f && e.h >= 140.dp -> Row(Modifier.fillMaxSize().padding(pad), verticalAlignment = Alignment.CenterVertically) {
            val d = e.h - pad * 2
            Dial(e, state, Modifier.size(d))
            Spacer(Modifier.width(pad))
            val tw = e.w - d - pad * 3
            val big = min(e.h * 0.2f, tw * 0.2f).coerceIn(18.dp, 44.dp)
            Column {
                Txt("${dowKo(e.today)}요일", big * 0.55f, if (e.look.hero) e.ink.accText else e.ink.ink, maxW = tw)
                Spacer(Modifier.height(big * 0.28f))
                Txt("${e.today.monthValue}월 ${e.today.dayOfMonth}일", big, e.ink.ink, weight = 700, maxW = tw)
                state.weather?.let {
                    Spacer(Modifier.height(big * 0.3f))
                    Txt("${it.condition.ko} · ${it.tempC}°", big * 0.5f, e.readInk2, weight = 500, maxW = tw)
                }
            }
        }
        r <= 0.7f && e.w >= 140.dp -> Column(
            Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val dl = (e.w * 0.07f).coerceIn(13.dp, 20.dp)
            Dial(e, state, Modifier.size(min(e.w - pad * 2, e.h - pad * 2 - dl * 3)))
            Spacer(Modifier.height(dl))
            DateLine(e, state, dl)
        }
        else -> Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
            Dial(e, state, Modifier.size(e.short - pad * 2))
        }
    }
}

/** The widget dial (mockup `face`): 60 ticks, 12 numerals, ink hour/minute hands, accent second hand on its own layer. */
@Composable
private fun Dial(e: Env, state: StandbyState, modifier: Modifier) {
    val ink = e.ink
    val sec = if (e.look.hero) ink.acc else ink.ink
    val tm = rememberTextMeasurer()
    val numStyle = TextStyle(fontFamily = e.num, fontWeight = FontWeight.Bold, color = ink.ink)
    val second = rememberSecond()
    val hr = state.now.hour % 12 + state.now.minute / 60f
    val mn = state.now.minute.toFloat()
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension; val rr = d / 2; val c = center
            fun pt(deg: Float, len: Float) = Math.toRadians(deg.toDouble()).let { Offset(c.x + sin(it).toFloat() * len, c.y - cos(it).toFloat() * len) }
            for (i in 0 until 60) {
                val big = i % 5 == 0
                drawLine(if (big) ink.ink2 else ink.ink3, pt(i * 6f, rr * 0.97f), pt(i * 6f, rr * if (big) 0.87f else 0.92f), d * if (big) 0.012f else 0.006f, StrokeCap.Round)
            }
            val ns = numStyle.copy(fontSize = (d * 0.1f).toSp())
            for (n in 1..12) {
                val l = tm.measure(n.toString(), ns)
                drawText(l, topLeft = pt(n * 30f, rr * 0.72f) - Offset(l.size.width / 2f, l.size.height / 2f))
            }
            drawLine(ink.ink, c, pt(hr * 30f, rr * 0.5f), d * 0.035f, StrokeCap.Round)
            drawLine(ink.ink, c, pt(mn * 6f, rr * 0.78f), d * 0.028f, StrokeCap.Round)
        }
        Canvas(Modifier.fillMaxSize()) {
            val d = size.minDimension; val rr = d / 2; val c = center
            val a = Math.toRadians(second.intValue * 6.0)
            val dir = Offset(sin(a).toFloat(), -cos(a).toFloat())
            drawLine(sec, c - dir * (rr * 0.12f), c + dir * (rr * 0.85f), d * 0.008f, StrokeCap.Round)
            drawCircle(ink.bg, d * 0.018f, c)
            drawCircle(sec, d * 0.018f, c, style = Stroke(d * 0.006f))
        }
    }
}

// ---------------------------------------------------------------- media

@Composable
private fun MediaCard(e: Env, state: StandbyState, actions: StandbyActions, close: (() -> Unit)?) {
    // r8 (operator "음악이랑 유튜브를 하나로"): one media card shows whatever plays
    val m = state.media
    if (m == null) {
        StateCard(e, Ic.NOTE, "미디어", Ic.NOTE, null, "폰에서 재생하면 여기 나와요", actions::openMediaApp)
        return
    }
    // r9 (operator, YouTube Music / YouTube refs): one look for music and video in every theme, picked by the slot's shape
    when (mediaShape(e.w.value, e.h.value)) {
        MediaShape.TALL -> MediaTall(e, m, actions, close)
        MediaShape.COVER -> MediaCover(e, m, actions, close)
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

internal fun progress(m: MediaInfo) = if (m.durationMs > 0) m.positionMs.toFloat() / m.durationMs else 0f

private fun mmss(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%d:%02d".format(s / 60, s % 60) }

/** Text and marks over the art (cover controls): white in every theme, the candle ink at night. */
private fun overPicture(e: Env): Color = lerp(Color.White, e.ink.ink, e.ink.night)

/** Dark see-through disc behind the cover's buttons. */
private val scrimDisc = Color.Black.copy(alpha = 0.42f)

/** One transport button: [fill] disc behind the glyph; a 48 dp target at least. */
@Composable
private fun MediaBtn(ic: Ic, d: Dp, fill: Color, fg: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(d).clip(CircleShape).background(fill).tap(if (enabled) onClick else null),
        contentAlignment = Alignment.Center,
    ) { IconView(ic, if (enabled) fg else fg.copy(alpha = 0.4f), Modifier.size(d * 0.5f)) }
}

/**
 * Prev / play-pause / next. [fg] glyph of the side buttons on [sideFill]; the play button is [playFg] on [playFill].
 * Slots under 140 dp keep play-pause alone. [onUse] runs on any press (keeps a revealed overlay up).
 */
@Composable
private fun MediaTransport(
    e: Env, m: MediaInfo, actions: StandbyActions, play: Dp, side: Dp, fg: Color, sideFill: Color, playFill: Color, playFg: Color,
    arrangement: Arrangement.Horizontal, modifier: Modifier, onUse: () -> Unit = {},
) {
    val one = e.w < 140.dp
    Row(modifier, horizontalArrangement = if (one) Arrangement.Center else arrangement, verticalAlignment = Alignment.CenterVertically) {
        if (!one) MediaBtn(Ic.PREV, side, sideFill, fg, m.canPrev) { onUse(); actions.mediaPrev() }
        MediaBtn(if (m.playing) Ic.PAUSE else Ic.PLAY, play, playFill, playFg, true) { onUse(); actions.mediaPlayPause() }
        if (!one) MediaBtn(Ic.NEXT, side, sideFill, fg, m.canNext) { onUse(); actions.mediaNext() }
    }
}

@Composable
private fun MediaTime(e: Env, ms: Long, c: Color) = Txt(mmss(ms), 12.dp * e.look.textScale, c, num = true)

/**
 * Tall slot (w/h <= 0.82), YouTube Music portrait: art centred on top (video: 16:9 still), title and artist
 * left-aligned under it, bar + times, then prev / ink play disc / next. Always shown. Tap opens Now Playing.
 */
@Composable
private fun MediaTall(e: Env, m: MediaInfo, actions: StandbyActions, close: (() -> Unit)?) {
    val w = e.w
    val pad = (w * 0.075f).coerceIn(12.dp, 32.dp)
    val ts = (w * 0.068f).coerceIn(15.dp, 26.dp)
    val play = min((w * 0.19f).coerceIn(52.dp, 80.dp), w - pad * 2 - 96.dp).coerceAtLeast(48.dp) // prev + next keep 48 dp
    val gap = (w * 0.04f).coerceIn(8.dp, 18.dp)
    val video = m.kind == MediaKind.VIDEO
    val ink = e.ink.ink
    Column(Modifier.fillMaxSize().padding(pad).tap(if (close == null) actions::openNowPlaying else null)) {
        if (close != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { MediaBtn(Ic.CLOSE, 48.dp, Color.Transparent, e.readInk2, true, close) }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val ah = min(maxHeight, if (video) maxWidth * (9f / 16f) else maxWidth)
            Art(e, m.art, if (video) Ic.VIDEO else Ic.NOTE, Modifier.size(if (video) maxWidth else ah, ah), (ah * 0.05f).coerceIn(8.dp, 18.dp))
        }
        Spacer(Modifier.height(gap))
        Txt(m.title, ts, ink, weight = 700, lines = if (video) 2 else 1)
        // cap boxes end at the baseline: Hangul descends below it and rises above the cap, so leave the line's room
        Spacer(Modifier.height(ts * 0.4f))
        Txt(m.artist, ts * 0.78f, e.readInk2, weight = 500)
        Spacer(Modifier.height(gap))
        Bar(e, progress(m), ink)
        if (w >= 180.dp) Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
            MediaTime(e, m.positionMs, e.readInk2)
            Spacer(Modifier.weight(1f))
            MediaTime(e, m.durationMs, e.readInk2)
        }
        Spacer(Modifier.height(gap * 1.4f))
        // play disc = ink, glyph = the page colour (mockup: white disc + black glyph on the iPhone theme)
        val side = max(48.dp, play * 0.7f)
        MediaTransport(
            e, m, actions, play, side, ink, Color.Transparent, ink, e.ink.bg, Arrangement.SpaceBetween,
            // the inset gives way before the three buttons would squeeze the last one under 48 dp
            Modifier.fillMaxWidth().padding(horizontal = ((w - pad * 2 - play - side * 2) / 2).coerceIn(0.dp, w * 0.06f)),
        )
    }
}

/**
 * Wide or square slot, YouTube landscape: the art / still fills the slot. At rest only the picture; a tap brings the
 * scrim, title top-leading, transport centred and bar + times along the bottom for [MEDIA_CONTROLS_MS] (any button
 * keeps them up); a tap while they show opens Now Playing. Everything over the picture is white + scrim.
 */
@Composable
private fun MediaCover(e: Env, m: MediaInfo, actions: StandbyActions, close: (() -> Unit)?) {
    val s = min(e.w, e.h)
    var shown by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(shown, tick) { if (shown) { delay(MEDIA_CONTROLS_MS); shown = false } }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "media")
    val video = m.kind == MediaKind.VIDEO
    val fg = overPicture(e)
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(e.plateRadius)).tap { if (!shown) shown = true else if (close == null) actions.openNowPlaying() else shown = false }) {
        Art(e, m.art, if (video) Ic.VIDEO else Ic.NOTE, Modifier.fillMaxSize(), 0.dp)
        if (a > 0f) Box(Modifier.fillMaxSize().alpha(a)) {
            val pad = (s * 0.07f).coerceIn(12.dp, 32.dp)
            val ts = (s * 0.07f).coerceIn(13.dp, 26.dp)
            val play = (s * 0.24f).coerceIn(48.dp, 96.dp)
            val side = max(48.dp, play * 0.72f)
            val gap = ((e.w - play - side * 2) * 0.12f).coerceIn(0.dp, play * 0.9f)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.62f), 0.35f to Color.Black.copy(alpha = 0.38f), // scrim
                        0.65f to Color.Black.copy(alpha = 0.38f), 1f to Color.Black.copy(alpha = 0.66f), // scrim
                    ),
                ),
            )
            Row(Modifier.fillMaxWidth().padding(pad)) {
                Column(Modifier.weight(1f)) {
                    Txt(m.title, ts, fg, weight = 700, lines = if (video && s >= 200.dp) 2 else 1)
                    if (s >= 150.dp) {
                        Spacer(Modifier.height(ts * 0.4f)) // Hangul overhangs the cap box (see MediaTall)
                        Txt(m.artist, ts * 0.8f, fg.copy(alpha = 0.75f), weight = 500)
                    }
                }
                if (close != null) MediaBtn(Ic.CLOSE, 48.dp, scrimDisc, fg, true) { close() }
            }
            MediaTransport(
                e, m, actions, play, side, fg, scrimDisc, scrimDisc, fg, Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                Modifier.fillMaxWidth().align(Alignment.Center).offset(y = s * 0.04f), onUse = { tick++ },
            )
            val times = s >= 180.dp
            val tc = fg.copy(alpha = 0.7f)
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = pad).padding(bottom = pad * 0.8f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (times) { MediaTime(e, m.positionMs, tc); Spacer(Modifier.width(10.dp)) }
                Bar(e, progress(m), fg, Modifier.weight(1f), track = fg.copy(alpha = 0.25f))
                if (times) { Spacer(Modifier.width(10.dp)); MediaTime(e, m.durationMs, tc) }
            }
        }
    }
}

/** How long a tap keeps the cover's controls up. */
internal const val MEDIA_CONTROLS_MS = 3_000L

// ---------------------------------------------------------------- timer, sleep, d-day

/**
 * Timer · pomodoro (spec §1): accent arc on an ink3 ring, digits in the theme's digit font. Square/tall: digits + mode
 * inside the ring, pause/stop under it; wide (w/h ≥ 1.35): ring left, "집중 · 18:45에 휴식" + digits + buttons right;
 * short side < 150 (or a ring under 120): mode, digits, one pause button. [TimerState] has no session count, so the mode stands alone
 * ("집중", not the mockup's "집중 2/4").
 */
@Composable
private fun TimerCard(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val total0 = config.focusMinutes * 60
    val t = state.timers[e.slotId] ?: TimerState(leftSec = total0, totalSec = total0)
    val mode = if (t.mode == TimerMode.FOCUS) "집중" else "휴식"
    val paused = !t.running && t.leftSec < t.totalSec
    val label = if (paused) "$mode · 멈춤" else mode
    // 11-14 #10: while not running, a drag along the ring sets the minutes (shown live, the ring filled to them)
    var drag by remember(e.slotId) { mutableStateOf<Int?>(null) }
    val ring = RingDrag(!t.running, drag ?: (t.leftSec + 59) / 60, { drag = it }) { m -> drag = null; actions.timerSet(e.slotId, m) }
    val dl = drag
    val digits = AnnotatedString(if (dl != null) "%02d:00".format(dl) else "%02d:%02d".format(t.leftSec / 60, t.leftSec % 60))
    val dc = if (paused && dl == null) e.ink.ink2 else e.ink.ink
    val frac = if (dl != null) dl / 60f else if (t.totalSec > 0) 1f - t.leftSec.toFloat() / t.totalSec else 0f
    val p = e.p7
    val iw = e.w - p * 2
    val ih = e.h - p * 2
    val toggle = @Composable { d: Dp -> MediaBtn(if (t.running) Ic.PAUSE else Ic.PLAY, d, e.ink.acc, e.onAcc, true) { actions.timerToggle(e.slotId) } }
    val stop = @Composable { d: Dp -> MediaBtn(Ic.CLOSE, d, e.ink.ink4, e.ink.ink, true) { actions.timerReset(e.slotId) } }
    when {
        // short side < 150, or a ring that would come out smaller than its buttons
        e.short < 150.dp || (e.w / e.h < 1.35f && min(iw, ih - 80.dp) < 120.dp) -> Column(
            Modifier.fillMaxSize().padding(p),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Txt(label, e.meta, e.ink.accText, weight = 700, maxW = iw)
            Hero(e, digits, ih - 48.dp - e.meta - 16.dp, dc, iw)
            toggle(48.dp)
        }
        e.w / e.h >= 1.35f -> Row(Modifier.fillMaxSize().padding(p), verticalAlignment = Alignment.CenterVertically) {
            val d = min(ih, iw * 0.45f)
            val tw = iw - d - p
            val next = state.now.plusSeconds(t.leftSec.toLong()).let { "%d:%02d에 %s".format(it.hour, it.minute, if (t.mode == TimerMode.FOCUS) "휴식" else "집중") }
            val gap = e.short * 0.05f
            TimerRing(e, d, frac, ring) { Txt(mode, (d * 0.16f).coerceAtLeast(e.meta), e.ink.accText, weight = 700, maxW = d * 0.7f) }
            Spacer(Modifier.width(p))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gap)) {
                Txt(if (t.running) "$mode · $next" else label, e.meta, e.readInk2, weight = 500, maxW = tw, num = true)
                Hero(e, digits, ih - e.meta * 1.4f - 52.dp - gap * 2, dc, tw) // as wide as the column allows
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { toggle(52.dp); stop(52.dp) }
            }
        }
        else -> Column(
            Modifier.fillMaxSize().padding(p),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            val d = min(iw, ih - 80.dp)
            TimerRing(e, d, frac, ring) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Hero(e, digits, d * 0.155f, dc, d * 0.72f)
                    Spacer(Modifier.height(6.dp))
                    Txt(label, if (d >= 200.dp) e.read else e.meta, e.ink.accText, weight = 700, maxW = d * 0.72f)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { toggle(56.dp); stop(48.dp) }
        }
    }
}

/**
 * Ring drag (11-14 #10, a Time Timer): the touch at ([dx], [dy]) from the centre (y down) as minutes, 12 o'clock on top,
 * clockwise, a full circle = 60. Snaps to the minute, clamped 1..60; crossing 12 o'clock holds the end the drag came
 * from ([prev] near 60 stays 60, near 1 stays 1), so it never jumps across the top.
 */
internal fun ringMinutes(dx: Float, dy: Float, prev: Int): Int {
    var deg = Math.toDegrees(kotlin.math.atan2(dx.toDouble(), (-dy).toDouble()))
    if (deg < 0) deg += 360.0
    val m = (deg / 6).roundToInt()
    return when {
        prev >= 45 && (m <= 15 || m >= 60) -> 60
        prev <= 15 && (m >= 45 || m <= 0) -> 1
        else -> m.coerceIn(1, 60)
    }
}

/** The ring's drag: [on] (not running), [minutes] now, [onMove] while dragging, [onDone] on release. */
private class RingDrag(val on: Boolean, val minutes: Int, val onMove: (Int) -> Unit, val onDone: (Int) -> Unit)

/** Progress ring [d] wide: ink3 track, accent arc from 12 o'clock for the elapsed [frac], [inner] centred. */
@Composable
private fun TimerRing(e: Env, d: Dp, frac: Float, drag: RingDrag? = null, inner: @Composable () -> Unit) {
    val track = e.ink.ink3
    val arc = e.ink.acc
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val cur by androidx.compose.runtime.rememberUpdatedState(drag)
    val input = if (drag?.on != true) Modifier else Modifier.pointerInput(Unit) {
        // only a drag that starts on the ring (±15 % of the radius) is the ring's; elsewhere the stack swipe keeps it
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.width / 2f
            val dist = (down.position - c).getDistance()
            if (kotlin.math.abs(dist - r * 0.93f) > r * 0.15f) return@awaitEachGesture
            val g = cur ?: return@awaitEachGesture
            down.consume()
            var m = g.minutes
            fun at(p: Offset) {
                val n = ringMinutes(p.x - c.x, p.y - c.y, m)
                if (n != m && n % 5 == 0) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                m = n; g.onMove(n)
            }
            at(down.position)
            while (true) {
                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                at(ch.position); ch.consume()
            }
            g.onDone(m)
        }
    }
    val label = if (drag?.on == true) Modifier.semantics { contentDescription = "타이머 링: 돌려서 시간 정하기" } else Modifier
    Box(Modifier.size(d).then(input).then(label), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = maxOf(6.dp.toPx(), size.minDimension * 0.065f)
            val tl = Offset(sw / 2, sw / 2)
            val sz = Size(size.width - sw, size.height - sw)
            drawArc(track, 0f, 360f, false, tl, sz, style = Stroke(sw))
            if (frac > 0f) drawArc(arc, -90f, 360f * frac.coerceAtMost(1f), false, tl, sz, style = Stroke(sw, cap = StrokeCap.Round))
        }
        inner()
    }
}

/**
 * Sleep sounds (spec §1, mockup "수면 소리" v3): six Control-Center tiles (plate2, a circular glyph on ink3; the playing
 * sound on the accent) in the column count with the biggest tiles, names when a tile is ≥ 78 dp; footer play/pause ·
 * "45분 뒤 꺼짐" · minutes chip. A tap plays that sound (again = stop). Short side < 150 or tiles < 48 dp: [SleepOne].
 */
@Composable
private fun SleepCard(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val on = state.sleep.playing
    val cur = config.sleepSound
    val p = e.p7
    val iw = e.w - p * 2
    val ih = e.h - p * 2
    val foot = 48.dp
    val g = tileGrid(SleepSound.entries.size, iw.value, (ih - foot - 10.dp).value)
    val t = minOf(g.tileW, g.tileH).dp
    if (e.short < 150.dp || t < 48.dp) return SleepOne(e, state, config, actions)
    val th = minOf(g.tileH, g.tileW * 1.15f).dp
    val named = t >= 78.dp
    val shape = RoundedCornerShape(if (e.plateRadius == 0.dp) 0.dp else min(22.dp, t * 0.25f))
    Column(Modifier.fillMaxSize().padding(p), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)) {
            SleepSound.entries.chunked(g.cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { s ->
                        val lit = on && s == cur
                        Column(
                            Modifier.width(g.tileW.dp).height(th).clip(shape).background(e.ink.plate2).tap { actions.sleepPick(s, true) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                        ) {
                            SoundDisc(e, s, t * if (named) 0.5f else 0.62f, lit)
                            if (named) Txt(s.ko, e.meta, if (s == cur) e.ink.ink else e.readInk2, weight = if (lit) 700 else 500, maxW = g.tileW.dp - 8.dp)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(foot), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaBtn(if (on) Ic.PAUSE else Ic.PLAY, foot, e.ink.ink, e.ink.bg, true, actions::sleepToggle)
            Txt(sleepLine(state, config, iw >= 320.dp), e.meta, e.readInk2, Modifier.weight(1f), weight = 500, num = true)
            Box(Modifier.height(foot).tap(actions::sleepMinutesNext), contentAlignment = Alignment.Center) {
                Box(Modifier.clip(RoundedCornerShape(if (e.plateRadius == 0.dp) 0 else 50)).background(e.ink.plate2).padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Txt("${config.sleepMinutes}분", e.meta, e.ink.ink, weight = 600, num = true)
                }
            }
        }
    }
}

/** "빗소리 · 45분 뒤 꺼짐" while playing (the name only when [named]), "멈춤" when stopped. */
private fun sleepLine(state: StandbyState, config: AppConfig, named: Boolean): String {
    val tail = if (state.sleep.playing) "${state.sleep.minutesLeft ?: config.sleepMinutes}분 뒤 꺼짐" else "멈춤"
    return if (named) "${config.sleepSound.ko} · $tail" else tail
}

/** Glyph disc [d] wide: ink glyph on ink3, or the accent with an [Env.onAcc] glyph when [lit]. */
@Composable
private fun SoundDisc(e: Env, s: SleepSound, d: Dp, lit: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(d).clip(CircleShape).background(if (lit) e.ink.acc else e.ink.ink3), contentAlignment = Alignment.Center) {
        SoundIcon(s, if (lit) e.onAcc else e.ink.ink, Modifier.size(d * 0.56f))
    }
}

/** Small sleep card: "수면 소리 1/6", ◀ the chosen sound's disc + name ▶ (arrows switch, the disc plays/stops), the timer line. */
@Composable
private fun SleepOne(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val all = SleepSound.entries
    val cur = config.sleepSound
    val i = cur.ordinal
    val p = e.p7
    val ih = e.h - p * 2
    // slots narrower than two arrows + the disc keep the disc alone (it still plays; the sound is picked in settings)
    val arrows = e.w - p * 2 >= 144.dp
    val arrow = @Composable { ic: Ic, to: SleepSound ->
        Box(Modifier.size(48.dp).tap { actions.sleepPick(to, false) }, contentAlignment = Alignment.Center) { IconView(ic, e.ink.ink2, Modifier.size(16.dp)) }
    }
    Column(Modifier.fillMaxSize().padding(p), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        if (ih >= 110.dp) Txt("수면 소리 ${i + 1}/${all.size}", e.meta, e.readInk2, weight = 500, num = true)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (arrows) Arrangement.SpaceBetween else Arrangement.Center) {
            if (arrows) arrow(Ic.PREV, all[(i + all.size - 1) % all.size])
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                SoundDisc(e, cur, min(56.dp, max(48.dp, ih * 0.4f)), state.sleep.playing, Modifier.tap { actions.sleepPick(cur, true) })
                Txt(cur.ko, e.meta, e.ink.ink, weight = 600, maxW = e.w - p * 2 - if (arrows) 96.dp else 0.dp)
            }
            if (arrows) arrow(Ic.NEXT, all[(i + 1) % all.size])
        }
        Txt(sleepLine(state, config, false), e.meta, e.readInk2, weight = 500, num = true)
    }
}

/**
 * D-day (spec §1, mockup "D-day"): the name in the text accent, "D-10" in the theme's digit font as big as the slot
 * allows, the date (short side ≥ 150) and a progress bar with "시작 · 지남 · 남음" (≥ 176). Wide slots align left.
 */
@Composable
private fun DdayCard(e: Env, config: AppConfig) {
    val dd = config.ddays.filter { !it.date.isBefore(e.today) }.minByOrNull { it.date } ?: config.ddays.firstOrNull()
    if (dd == null) {
        StateCard(e, Ic.CALENDAR, "D-day", null, "일정 없음", null, null)
        return
    }
    val n = ChronoUnit.DAYS.between(e.today, dd.date)
    val txt = when { n > 0 -> "D-$n"; n == 0L -> "D-DAY"; else -> "D+${-n}" }
    val p = e.p7
    val iw = e.w - p * 2
    val showDate = e.short >= 150.dp
    val showBar = e.short >= 176.dp
    val readCap = e.read * CAP_UI
    val texts = readCap + LG * 2 + (if (showDate) readCap + LG * 2 else 0.dp) + (if (showBar) p * 0.8f + 6.dp + LG + e.metaCap else 0.dp)
    // mockup: font min(0.4 h, w / 2.7) up to 180 → cap ≈ 0.3 h, ≤ 130
    val cap = minOf(e.h - p * 2 - texts, e.h * 0.3f, 130.dp)
    Column(
        Modifier.fillMaxSize().padding(p), verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (e.w / e.h >= 1.6f) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        Txt(dd.name, e.read, e.ink.accText, weight = 700)
        Spacer(Modifier.height(LG * 2))
        Hero(e, AnnotatedString(txt), cap, e.ink.ink, iw)
        if (showDate) {
            Spacer(Modifier.height(LG * 2))
            Txt(dateKo(dd.date), e.read, e.readInk2, weight = 500)
        }
        if (showBar) {
            // ponytail: a fixed 100-day countdown window (DDay has no start date); store one if the operator wants it
            val start = dd.date.minusDays(100)
            val left = if (n > 0) "${(100 - n).coerceAtLeast(0)}일 지남 · ${n}일 남음" else if (n == 0L) "오늘" else "${-n}일 지남"
            val from = "시작 ${start.monthValue}월 ${start.dayOfMonth}일"
            Spacer(Modifier.height(p * 0.8f))
            Bar(e, (100 - n) / 100f, e.ink.acc, Modifier.height(6.dp))
            Spacer(Modifier.height(LG))
            Row(Modifier.fillMaxWidth()) {
                if (fitsOneLine("$from    $left", e.meta, 500, iw)) {
                    Txt(from, e.meta, e.readInk2, weight = 500)
                    Spacer(Modifier.weight(1f))
                }
                Txt(left, e.meta, e.readInk2, weight = 500)
            }
        }
    }
}

// ---------------------------------------------------------------- agenda

/**
 * Today's agenda (spec §1, mockup "일정"): "월요일 9 · 남은 일정 N", then colour-bar rows (title over time) as many as
 * fit, two columns when wide, "외 N개" for the rest. No permission = a prompt in the list area; the card taps to settings.
 */
@Composable
private fun AgendaCard(e: Env, state: StandbyState, actions: StandbyActions) {
    val items = state.agenda
    val p = e.p7
    val cols = if (e.w / e.h >= 1.6f || (e.w >= 560.dp && e.w / e.h >= 1.2f)) 2 else 1
    val room = e.h - p * 2 - e.metaCap - LG * 2 + 12.dp // the last row needs no gap under it
    var rows = (room / e.evStep).toInt().coerceAtLeast(1)
    if (items != null && items.size > rows * cols) rows = ((room - e.metaCap - LG) / e.evStep).toInt().coerceAtLeast(1)
    val shown = items.orEmpty().take(rows * cols)
    val more = items.orEmpty().size - shown.size
    val head = buildAnnotatedString {
        withStyle(SpanStyle(color = e.ink.accText)) { append("${dowKo(e.today)}요일") }
        append(" ${e.today.dayOfMonth}")
        if (!items.isNullOrEmpty()) withStyle(SpanStyle(color = e.readInk2, fontWeight = FontWeight.Medium)) { append(" · 남은 일정 ${items.size}") }
    }
    if (!items.isNullOrEmpty() && e.h < 220.dp) return AgendaOne(e, items, head)
    Column(Modifier.fillMaxSize().padding(p).tap(if (items == null) ({ actions.openSettings("calendar") }) else null)) {
        Txt(head, e.meta, e.ink.ink, weight = 700)
        Spacer(Modifier.height(LG * 2))
        when {
            items == null -> Txt("캘린더 권한 필요 · 눌러서 설정", e.read, e.readInk2, weight = 500, lines = 2)
            items.isEmpty() -> Txt("남은 일정 없음", e.read, e.readInk2, weight = 500)
            else -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shown.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(p)) {
                        row.forEach { Box(Modifier.weight(1f)) { EventRow(e, it, it.start) } }
                        if (row.size < cols) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (more > 0) {
            Spacer(Modifier.weight(1f))
            Txt("외 ${more}개", e.meta, e.readInk2, weight = 500)
        }
    }
}

/**
 * A short agenda slot (< 220 dp, r10 B2): rows of 15 dp text were unreadable at 1 m, so the next event alone goes large
 * (time as a [Hero], title 26 dp) and the rest folds into "다음 13:00 병원 예약 · 외 3개".
 */
@Composable
private fun AgendaOne(e: Env, items: List<AgendaItem>, head: AnnotatedString) {
    val p = e.p7
    val ev = items.first()
    val ih = e.h - p * 2
    val title = min(26.dp, ih * 0.15f)
    val foot = items.getOrNull(1)?.let { n -> "다음 ${n.start} ${n.title}" + if (items.size > 2) " · 외 ${items.size - 2}개" else "" }
    val cap = min(ih - e.metaCap * (if (foot != null) 2 else 1) - LG * 4 - title * 1.4f - 8.dp, ih * 0.3f)
    val maxW = e.w - p * 2 - 16.dp
    Column(Modifier.fillMaxSize().padding(p)) {
        Txt(head, e.meta, e.ink.ink, weight = 700, maxW = maxW + 16.dp)
        Spacer(Modifier.weight(1f))
        Row {
            Box(Modifier.width(4.dp).height(cap * 1.25f + 8.dp + title * 1.3f).clip(RoundedCornerShape(50)).background(eventInk(e, ev.color)))
            Spacer(Modifier.width(12.dp))
            Column {
                if (ev.start.first().isDigit()) Hero(e, AnnotatedString(ev.start), cap, e.ink.ink, maxW)
                else Txt(ev.start, cap / CAP_UI, e.ink.ink, weight = 700, maxW = maxW)
                Spacer(Modifier.height(8.dp))
                Txt(ev.title, title, e.ink.ink, weight = 700, maxW = maxW)
            }
        }
        Spacer(Modifier.weight(1f))
        if (foot != null) Txt(foot, e.meta, e.readInk2, weight = 500, num = true, maxW = maxW + 16.dp)
    }
}

// ---------------------------------------------------------------- weather, calendar, memories

/** Weather glyph through Ink roles: cloud in ink, sun / rain in the sun / blue roles; mono-icon themes draw it in ink. */
@Composable
internal fun WxIcon(e: Env, ic: Ic, modifier: Modifier) {
    val c2 = if (e.t.tokens.monoIcons) e.ink.ink else if (ic == Ic.RAIN) e.ink.blue else e.ink.sun
    IconView(ic, e.ink.ink, modifier, e.ink.bg, color2 = c2)
}

/** Weather / calendar inset (mockup P(s)): 7 % of the short side, 12–28 dp. */
internal val Env.p7: Dp get() = (min(w, h) * 0.07f).coerceIn(12.dp, 28.dp)
/** One meta line (cap height) and the gap that separates header lines from the hero number. */
private val Env.metaCap: Dp get() = meta * CAP_UI
private val LG = 8.dp
/** Hour column (label, glyph, temperature) and the per-row pitch of hour / day / event lists. */
private val Env.hourColH: Dp get() = metaCap + read * 1.6f + read * CAP_UI + 12.dp
private val Env.rowStep: Dp get() = read * 1.4f + 9.dp
private val Env.evH: Dp get() = read * CAP_UI + 8.dp + metaCap
private val Env.evStep: Dp get() = evH + 12.dp

/** Hero number in the theme's digit font, [cap] dp tall, shrunk to [maxW]. Slot-sized, never text-scaled (spec §0). */
@Composable
internal fun Hero(e: Env, text: AnnotatedString, cap: Dp, color: Color, maxW: Dp) {
    val tk = e.t.tokens
    val c = cap.coerceAtLeast(8.dp)
    Txt(text, c / tk.digitCap, color, weight = tk.digitWeight, family = tk.digitFont(tk.digitWeight), cap = tk.digitCap, tnum = true, maxW = maxW, maxCapH = c)
}

@Composable
private fun WeatherCard(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val wx = state.weather
    if (wx == null) {
        LoadingCard(e, Ic.ARROW, config.city.name) { WeatherLabel(e, config.city.name) }
        return
    }
    val nowSec = state.now.atZone(ZoneId.systemDefault()).toEpochSecond()
    val tc = if (nowSec - wx.fetchedAtEpochSec > 3 * 3600) e.ink.ink2 else e.ink.ink
    val p = e.p7
    val iw = e.w - p * 2
    val ih = e.h - p * 2
    val head = e.metaCap * 2 + LG * 2 // city line + high/low line around the number
    val hours = wx.hourly.take(5)
    val mod = Modifier.fillMaxSize().padding(p).tap(if (wx.offline) actions::retryFeeds else null)
    val now = @Composable { cap: Dp, maxW: Dp, sky: Boolean ->
        Column {
            CityLine(e, wx, sky)
            Spacer(Modifier.height(LG))
            TempRow(e, wx, cap, maxW, tc)
            Spacer(Modifier.height(LG))
            Txt("최고 ${wx.highC}° · 최저 ${wx.lowC}°", e.meta, e.readInk2, weight = 500, num = true)
        }
    }
    when (weatherShape(e.w.value, e.h.value)) {
        WeatherShape.TINY -> Column(mod, verticalArrangement = Arrangement.SpaceBetween) {
            CityLine(e, wx, sky = false)
            TempRow(e, wx, ih - head, iw, tc)
            Txt("${wx.highC}° / ${wx.lowC}°", e.meta, e.readInk2, weight = 500, num = true)
        }
        // temperature left, 4–5 hours right (fewer than 3 hours fit: the number takes the width)
        WeatherShape.SHORT_WIDE -> {
            val hw = min(iw * 0.58f, 56.dp * hours.size)
            val n = minOf(hours.size, (hw / 44.dp).toInt()).let { if (it < 3) 0 else it }
            Row(mod, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { now(ih - head, if (n > 0) iw - hw - p else iw, n == 0) }
                if (n > 0) {
                    Spacer(Modifier.width(p))
                    Box(Modifier.width(hw)) { HourRow(e, hours.take(n)) }
                }
            }
        }
        // narrow and tall: number, then the next hours as a list; what the list leaves goes to the number
        WeatherShape.SMALL -> {
            val step = e.read * 1.4f + 6.dp
            val n = minOf(hours.size, ((ih - head - min(iw * 0.6f, (ih - head) * 0.5f) - 12.dp) / step).toInt().coerceAtLeast(0))
            Column(mod, verticalArrangement = Arrangement.SpaceBetween) {
                now(ih - head - (if (n > 0) 12.dp + step * n else 0.dp), iw, false)
                if (n > 0) HourList(e, hours.take(n))
            }
        }
        // r10 B2: city line, the number as tall as the slot allows, high/low; no hours
        WeatherShape.BIG_TEMP -> Column(mod, verticalArrangement = Arrangement.Center) { now((ih - head) * 0.9f, iw, true) }
        // now on the left at full height, hours on the right, five days under them when tall enough
        WeatherShape.WIDE -> {
            val lw = min(iw * 0.42f, 260.dp)
            val n = minOf(hours.size, ((iw - lw - p) / 46.dp).toInt())
            val days = if (ih >= 280.dp) minOf(5, wx.days.size + 1, ((ih - e.hourColH - 30.dp) / e.rowStep).toInt()) else 0
            Row(mod) {
                Column(Modifier.width(lw).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    CityLine(e, wx, true)
                    TempRow(e, wx, min(ih - head, lw * 0.75f), lw, tc)
                    Txt("최고 ${wx.highC}° · 최저 ${wx.lowC}°", e.meta, e.readInk2, weight = 500, num = true)
                }
                Spacer(Modifier.width(p))
                Column(
                    Modifier.weight(1f).fillMaxHeight(),
                    verticalArrangement = if (days > 0) Arrangement.SpaceEvenly else Arrangement.Center,
                ) {
                    if (n > 0) HourRow(e, hours.take(n))
                    if (days > 0) DayRows(e, wx, days)
                }
            }
        }
        // square / tall: hours row + day rows as they fit, the leftover goes to the number (mockup weather v3)
        WeatherShape.STACK -> {
            val tMax = min(180.dp, iw * 0.8f)
            var rest = ih - head
            var t = min(tMax, rest * 0.55f)
            rest -= t
            val n = minOf(hours.size, (iw / 44.dp).toInt())
            val row = n >= 3 && rest >= e.hourColH + 12.dp
            if (row) rest -= e.hourColH + 12.dp
            val days = ((rest - 10.dp) / e.rowStep).toInt().coerceIn(0, minOf(5, wx.days.size + 1))
            rest -= e.rowStep * days
            t = min(tMax, t + rest - 4.dp)
            Column(mod, verticalArrangement = Arrangement.SpaceBetween) {
                now(t, iw, true)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (row) HourRow(e, hours.take(n))
                    if (days > 0) DayRows(e, wx, days)
                }
            }
        }
    }
}

/** "서울 · 대체로 흐림" (offline: "서울 · 21:40 기준 · 연결 안 됨"), city bold, the rest in the secondary ink. */
@Composable
private fun CityLine(e: Env, wx: WeatherInfo, sky: Boolean) {
    val rest = if (wx.offline) " · ${hhmm(wx.fetchedAtEpochSec)} 기준 · 연결 안 됨" else if (sky) " · ${wx.condition.ko}" else ""
    val text = buildAnnotatedString {
        append(wx.city)
        withStyle(SpanStyle(color = e.readInk2, fontWeight = FontWeight.Medium)) { append(rest) }
    }
    Txt(text, e.meta, e.ink.ink, weight = 700)
}

/**
 * The temperature as big as [cap] (and [maxW]) allow, the sky glyph top-right of it. The degree is a drawn ring at the
 * cap top: digit fonts draw "°" anywhere from a dot to a cap-high "o".
 */
@Composable
private fun TempRow(e: Env, wx: WeatherInfo, capH: Dp, maxW: Dp, color: Color) {
    val cap = min(capH, maxW * 0.8f) // tall narrow slots: the glyph + ring alone would take the whole width
    val ico = cap * 0.4f
    val deg = cap * 0.28f
    Layout({
        Hero(e, AnnotatedString("${wx.tempC}"), cap, color, maxW - ico - deg - cap * 0.16f)
        Canvas(Modifier.fillMaxSize()) { val sw = size.minDimension * 0.17f; drawCircle(color, (size.minDimension - sw) / 2, style = Stroke(sw)) }
        WxIcon(e, weatherIc(wx.condition), Modifier.size(ico))
    }) { ms, _ ->
        // the ring is a superscript "°" of the digits as drawn (Hero shrinks to its width): 0.28 of their cap, top-aligned
        val hero = ms[0].measure(Constraints())
        val d = (hero.height * 0.28f).roundToInt().coerceAtLeast(1)
        val gap = (hero.height * 0.08f).roundToInt() // italic digits lean past their advance
        val ring = ms[1].measure(Constraints.fixed(d, d))
        val icon = ms[2].measure(Constraints())
        val ix = hero.width + gap + d + (cap * 0.08f).roundToPx()
        layout(ix + icon.width, maxOf(hero.height, icon.height)) {
            hero.place(0, 0); ring.place(hero.width + gap, 0); icon.place(ix, 0)
        }
    }
}

/** Next hours as columns: "22시" / glyph / "6°". */
@Composable
private fun HourRow(e: Env, hours: List<HourTemp>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        hours.forEach { hr ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Txt("${hr.hour}시", e.meta, e.readInk2, weight = 500, num = true)
                WxIcon(e, weatherIc(hr.condition), Modifier.size(e.read * 1.6f))
                Txt(temp(hr.tempC), e.read, e.ink.ink, weight = 700, family = e.num, cap = e.numCap, tnum = true)
            }
        }
    }
}

/** Next hours as rows: "22시 ☁ 6°" (narrow tall slots). */
@Composable
private fun HourList(e: Env, hours: List<HourTemp>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        hours.forEach { hr ->
            Row(Modifier.fillMaxWidth().height(e.read * 1.4f), verticalAlignment = Alignment.CenterVertically) {
                Txt("${hr.hour}시", e.meta, e.readInk2, Modifier.weight(1f), weight = 500, num = true)
                WxIcon(e, weatherIc(hr.condition), Modifier.size(e.read * 1.3f))
                Txt(temp(hr.tempC), e.read, e.ink.ink, Modifier.width(e.read * 2.2f), weight = 700, family = e.num, cap = e.numCap, tnum = true, align = TextAlign.End)
            }
        }
    }
}

/** Today + the next days: "화 ☀ 0° ━━━ 9°", the range bar placed on the shared low–high scale. */
@Composable
private fun DayRows(e: Env, wx: WeatherInfo, n: Int) {
    val days = (listOf(DayTemp(e.today, wx.highC, wx.lowC, wx.condition)) + wx.days).take(n)
    val lo = days.minOf { it.lowC }
    val span = (days.maxOf { it.highC } - lo).coerceAtLeast(1).toFloat()
    val track = e.ink.ink3
    val fill = if (e.t.tokens.monoIcons) Brush.horizontalGradient(listOf(e.ink.ink2, e.ink.ink)) else Brush.horizontalGradient(listOf(e.ink.blue, e.ink.sun))
    val numW = e.read * 2.4f
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        days.forEach { d ->
            Row(Modifier.fillMaxWidth().height(e.read * 1.4f), verticalAlignment = Alignment.CenterVertically) {
                Txt(if (d.date == e.today) "오늘" else dowKo(d.date), e.read, e.ink.ink, Modifier.width(e.read * 2.6f), weight = 600)
                WxIcon(e, weatherIc(d.condition), Modifier.size(e.read * 1.3f))
                Txt("${d.lowC}°", e.read, e.readInk2, Modifier.width(numW), weight = 500, num = true, align = TextAlign.End)
                Spacer(Modifier.width(8.dp))
                Canvas(Modifier.weight(1f).height(4.dp)) {
                    val r = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = r)
                    val a = (d.lowC - lo) / span * size.width
                    val b = (d.highC - lo) / span * size.width
                    drawRoundRect(fill, Offset(a, 0f), Size((b - a).coerceAtLeast(size.height), size.height), cornerRadius = r)
                }
                Spacer(Modifier.width(8.dp))
                Txt("${d.highC}°", e.read, e.ink.ink, Modifier.width(numW), weight = 600, num = true)
            }
        }
    }
}

/** Weather label = city then arrow in every state (review X6), bold `ink` like iPhone's "Holland ➤" (review B3). */
@Composable
private fun WeatherLabel(e: Env, city: String) = LabelRow(e, Ic.ARROW, city, color = e.ink.ink, iconEnd = true, weight = 700)

/**
 * Calendar card (spec §1, mockup "달력" v3): the day number fills its block; small slots list the coming events
 * (`state.upcoming`), big ones draw the month. Without calendar permission the events area asks for it (tap = settings).
 */
@Composable
private fun CalendarCard(e: Env, state: StandbyState, actions: StandbyActions) {
    val p = e.p7
    val iw = e.w - p * 2
    val ih = e.h - p * 2
    val denied = state.agenda == null
    val events = if (denied) emptyList() else state.upcoming
    val mod = Modifier.fillMaxSize().padding(p).tap(if (denied) ({ actions.openSettings("calendar") }) else null)
    val tiny = @Composable {
        Column(mod, verticalArrangement = Arrangement.SpaceBetween) {
            CalHead(e, e.meta)
            Hero(e, AnnotatedString("${e.today.dayOfMonth}"), ih - e.metaCap - LG, e.ink.ink, iw)
        }
    }
    when (calendarShape(e.w.value, e.h.value)) {
        CalendarShape.TINY -> tiny()
        // big day left, this week + the next 1–2 events right
        CalendarShape.SHORT_WIDE -> {
            val cap = ih - e.metaCap - LG
            val lw = min(iw * 0.35f, cap * 1.6f)
            if (iw - lw - p < 18.dp * 7) return tiny()
            Row(mod) {
                Column(Modifier.width(lw).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    CalHead(e, e.meta)
                    Hero(e, AnnotatedString("${e.today.dayOfMonth}"), cap, e.ink.ink, lw)
                }
                Spacer(Modifier.width(p))
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    val n = if (ih >= 170.dp) 2 else 1
                    DayGrid(e, e.today.minusDays((e.today.dayOfWeek.value % 7).toLong()), 1, events, Modifier.fillMaxWidth().weight(1f))
                    Spacer(Modifier.height(LG))
                    EventList(e, events, n, denied)
                }
            }
        }
        CalendarShape.NARROW -> Column(mod) { DayAndEvents(e, iw, ih, events, denied, e.meta, 5) }
        // big day + events left, the month right
        CalendarShape.WIDE_MONTH -> {
            val cell = (ih - e.head * 1.6f) / 6.2f
            val gw = min(cell * 7 * 1.3f, e.w * 0.6f)
            Row(mod) {
                Column(Modifier.weight(1f).fillMaxHeight()) { DayAndEvents(e, e.w - gw - p * 3, ih, events, denied, e.head, 4) }
                Spacer(Modifier.width(p))
                MonthGrid(e, events, Modifier.width(gw).fillMaxHeight())
            }
        }
        // the month, then "다가오는 날" in what is left
        CalendarShape.MONTH -> {
            val headH = e.head * CAP_UI
            val cell = min(iw / 7 / 1.1f, (ih - headH - 40.dp) / 6.4f)
            val left = ih - headH - LG * 2 - cell * 6.4f - e.metaCap - 10.dp
            val n = if (events.isEmpty() && !denied) 0 else ((left - 10.dp) / e.evStep).toInt().coerceIn(0, 5)
            Column(mod) {
                val text = buildAnnotatedString {
                    withStyle(SpanStyle(color = e.ink.accText)) { append("${e.today.monthValue}월") }
                    withStyle(SpanStyle(color = e.readInk2)) { append(" ${e.today.year}") }
                }
                Txt(text, e.head, e.ink.ink, weight = 700)
                Spacer(Modifier.height(LG * 2))
                MonthGrid(e, events, Modifier.fillMaxWidth().weight(1f))
                if (n > 0) {
                    Spacer(Modifier.height(LG * 2))
                    Txt("다가오는 날", e.meta, e.readInk2, weight = 600)
                    Spacer(Modifier.height(10.dp))
                    EventList(e, events, n, denied)
                }
            }
        }
    }
}

/** "월요일 11월": weekday in the text accent, month in the secondary ink. */
@Composable
private fun CalHead(e: Env, size: Dp) {
    val text = buildAnnotatedString {
        withStyle(SpanStyle(color = e.ink.accText)) { append("${dowKo(e.today)}요일") }
        withStyle(SpanStyle(color = e.readInk2)) { append(" ${e.today.monthValue}월") }
    }
    Txt(text, size, e.ink.ink, weight = 700)
}

/** Head, the big day, then up to [max] coming events under it; whatever the events leave goes to the day. */
@Composable
private fun ColumnScope.DayAndEvents(e: Env, w: Dp, h: Dp, events: List<AgendaItem>, denied: Boolean, headSize: Dp, max: Int) {
    val headH = headSize * CAP_UI + LG
    val fit = ((h - headH - h * 0.3f - 20.dp) / e.evStep).toInt().coerceIn(1, max)
    val n = minOf(fit, events.size.coerceAtLeast(1))
    val cap = h - headH - 20.dp - e.evStep * n
    CalHead(e, headSize)
    Spacer(Modifier.height(LG))
    Hero(e, AnnotatedString("${e.today.dayOfMonth}"), cap, e.ink.ink, w)
    Spacer(Modifier.weight(1f))
    EventList(e, events, n, denied)
}

/** Coming events: colour bar (the calendar's colour, contrast-floored) + title over "오늘 22:30" / "11/19 (목)". */
@Composable
private fun EventList(e: Env, events: List<AgendaItem>, n: Int, denied: Boolean) {
    if (denied || events.isEmpty()) {
        Txt(if (denied) "캘린더 권한 필요 · 눌러서 설정" else "다가오는 일정 없음", e.meta, e.readInk2, weight = 500)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        events.take(n).forEach { EventRow(e, it, whenKo(e, it)) }
    }
}

/** One event: colour bar + title over [time]. */
@Composable
private fun EventRow(e: Env, ev: AgendaItem, time: String) = Row {
    Box(Modifier.width(4.dp).height(e.evH).clip(RoundedCornerShape(50)).background(eventInk(e, ev.color)))
    Spacer(Modifier.width(8.dp))
    Column {
        Txt(ev.title, e.read, e.ink.ink, weight = 600)
        Spacer(Modifier.height(8.dp))
        Txt(time, e.meta, e.readInk2, weight = 500, num = true)
    }
}

/**
 * The calendar's own event colour, its lightness stepped until it reaches 3:1 on the page and plate (pastels darken on
 * light themes instead of falling back to ink); night maps it to the candle like every role.
 */
private fun eventInk(e: Env, argb: Long): Color {
    val light = e.t.tokens.light && e.ink.night < 0.5f
    return e.ink.mapped(Contrast.fit(Color(argb), listOf(e.ink.bg, e.ink.plate.compositeOver(e.ink.bg)), light))
}

private fun whenKo(e: Env, ev: AgendaItem): String {
    val d = ev.date ?: e.today
    return if (d == e.today) "오늘 ${ev.start}" else "${d.monthValue}/${d.dayOfMonth} (${dowKo(d)})" + if (ev.start != "종일") " ${ev.start}" else ""
}

/** This month, Sunday first, inside its own box. */
@Composable
private fun MonthGrid(e: Env, events: List<AgendaItem>, modifier: Modifier) {
    val first = e.today.withDayOfMonth(1)
    val lead = first.dayOfWeek.value % 7
    DayGrid(e, first.minusDays(lead.toLong()), (lead + e.today.lengthOfMonth() + 6) / 7, events, modifier, month = e.today.monthValue)
}

/** Ink or page colour, whichever reads better on an accent disc. */
private val Env.onAcc: Color get() = ink.onAcc

private val DOW_SUN = listOf("일", "월", "화", "수", "목", "금", "토")

/**
 * [weeks] rows of days from [start] (a Sunday) under a weekday header: today on an accent disc, Sundays in the red
 * role, a dot in the event colour under days with events. [month] blanks days outside it. Numbers fit the cell.
 */
@Composable
private fun DayGrid(e: Env, start: LocalDate, weeks: Int, events: List<AgendaItem>, modifier: Modifier, month: Int? = null) {
    val dots = remember(events, e.ink) { events.filter { it.date != null }.groupBy { it.date!! }.mapValues { eventInk(e, it.value.first().color) } }
    val onAcc = e.onAcc
    BoxWithConstraints(modifier) {
        // rows never taller than wide (a one-week strip stays compact); the grid sits centred in its box
        val rowH = min(maxHeight / (weeks + 0.8f), maxWidth / 7 * 1.15f)
        val d = min(rowH, maxWidth / 7) * 0.86f
        val fs = (d * 0.5f).coerceIn(11.dp, 20.dp)
        Column(Modifier.fillMaxWidth().align(Alignment.Center)) {
            Row(Modifier.fillMaxWidth().height(rowH * 0.8f), verticalAlignment = Alignment.CenterVertically) {
                DOW_SUN.forEachIndexed { i, s ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Txt(s, fs * 0.85f * e.look.textScale, if (i == 0) e.ink.weekday else e.readInk2, weight = 600) // weekday header = a label
                    }
                }
            }
            for (wk in 0 until weeks) Row(Modifier.fillMaxWidth().height(rowH)) {
                for (dw in 0 until 7) {
                    val day = start.plusDays((wk * 7 + dw).toLong())
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        if (month != null && day.monthValue != month) return@Box
                        val today = day == e.today
                        if (today) Box(Modifier.size(d).clip(CircleShape).background(e.ink.acc))
                        Txt(
                            "${day.dayOfMonth}", fs, if (today) onAcc else if (dw == 0) e.ink.weekday else e.ink.ink,
                            weight = if (today) 700 else 500, family = e.num, cap = e.numCap, tnum = true,
                        )
                        val dot = dots[day]
                        if (dot != null && !today) Box(Modifier.offset(y = d * 0.36f).size(max(4.dp, d * 0.1f)).clip(CircleShape).background(dot))
                    }
                }
            }
        }
    }
}

/**
 * Memories (spec §1): the photo fills the slot; "3년 전 오늘" over place · date along the bottom on a scrim, white in
 * every theme (candle ink at night). The mockup's "1 / 12" needs a photo count the state does not carry (one photo).
 */
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
    val fg = overPicture(e)
    val pad = (e.short * 0.06f).coerceIn(10.dp, 22.dp)
    val cw = e.w - pad * 2
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(e.plateRadius))) {
        Image(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = nightFilter(e.ink.night))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)))) // scrim
        Column(Modifier.align(Alignment.BottomStart).padding(pad)) {
            Txt(p.yearsAgo?.let { "${it}년 전 오늘" } ?: p.place, e.head, fg, weight = 700, maxW = cw, num = true)
            if (e.short >= 150.dp) {
                Spacer(Modifier.height(8.dp))
                // one line; the short date ("설악산 · 2023.11.9") when the long one does not fit
                val size = if (e.short >= 200.dp) e.read else e.meta
                val long = listOf(p.place, p.date).filter { it.isNotBlank() }.joinToString(" · ")
                val digits = Regex("\\d+").findAll(p.date).map { it.value }.toList()
                val short = if (digits.size == 3) listOf(p.place, digits.joinToString(".")).filter { it.isNotBlank() }.joinToString(" · ") else long
                Txt(if (fitsOneLine(long, size, 500, cw)) long else short, size, fg.copy(alpha = 0.8f), weight = 500, tnum = true, maxW = cw)
            }
        }
    }
}
