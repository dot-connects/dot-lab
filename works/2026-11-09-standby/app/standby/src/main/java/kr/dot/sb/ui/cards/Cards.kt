package kr.dot.sb.ui.cards

import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
) = ProvideTheme(config.theme) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val t = Themes.of(config.theme)
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
        val surface = if (type.isNotif) Modifier else plate
        CompositionLocalProvider(LocalInk provides ink, LocalSlotLabel provides e.label) {
            Box(Modifier.fillMaxSize().then(surface)) {
                val drawn = when (t.family) {
                    Family.PAPER -> paperBody(type, e, state, config, actions)
                    Family.LAMP -> lampBody(type, e, state, config, actions)
                    Family.IPHONE -> false
                }
                if (!drawn) when (type) {
                    CardType.CLOCK -> ClockCard(e, state, config)
                    CardType.ANALOG -> AnalogCard(e, state)
                    CardType.MUSIC -> MediaCard(e, state, actions, MediaKind.MUSIC)
                    CardType.VIDEO -> MediaCard(e, state, actions, MediaKind.VIDEO)
                    CardType.TIMER -> TimerCard(e, state, config, actions)
                    CardType.SLEEP -> SleepCard(e, state, config, actions)
                    CardType.DDAY -> DdayCard(e, config)
                    CardType.AGENDA -> AgendaCard(e, state, actions)
                    CardType.USAGE -> UsageCard(e, state, actions)
                    CardType.WEATHER -> WeatherCard(e, state, config, actions)
                    CardType.CALENDAR -> CalendarCard(e, state, actions)
                    CardType.MEMORIES -> MemoriesCard(e, state, actions)
                    // r9: stack and list merged into one card (boards saved with the stack still draw it)
                    CardType.NOTIF_STACK, CardType.NOTIF_LIST -> NotifCard(e, state, actions, plate)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- clocks

@Composable
private fun ClockCard(e: Env, state: StandbyState, config: AppConfig) {
    val face = e.look.clockFace
    if (face == FaceId.DIGITAL) return DigitalCard(e, state, config)
    // other faces picked for the board keep their own slot rendering
    val style = config.clockStyle(face).copy(color = e.look.accent.argbLong(), showInfo = false)
    Column(Modifier.fillMaxSize().padding(e.pad), horizontalAlignment = Alignment.CenterHorizontally) {
        ClockFace(face, style, state, Modifier.fillMaxWidth().weight(1f), inSlot = true, hero = e.look.hero)
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
 * over the theme's. A second, smaller clock on the board draws its digits in ink.
 */
@Composable
private fun DigitalCard(e: Env, state: StandbyState, config: AppConfig) {
    val oneLine = e.w / e.h >= 1.05f || e.short < 150.dp
    val dl = (e.short * 0.055f).coerceIn(13.dp, 20.dp)
    val own = config.styles[FaceId.DIGITAL]?.copy(color = e.look.accent.argbLong())
    Column(
        Modifier.fillMaxSize().padding((e.short * 0.08f).coerceIn(10.dp, 32.dp)),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        ThemedDigits(
            clockText(state.now, state.use24h), e.t, e.ink, Modifier.fillMaxWidth().weight(1f, fill = false),
            style = own, color = if (e.look.hero) null else e.ink.ink, twoLines = !oneLine,
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
                Spacer(Modifier.height(big * 0.1f))
                Txt("${e.today.monthValue}월 ${e.today.dayOfMonth}일", big, e.ink.ink, weight = 700, maxW = tw)
                state.weather?.let {
                    Spacer(Modifier.height(big * 0.15f))
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
private fun MediaCard(e: Env, state: StandbyState, actions: StandbyActions, kind: MediaKind) {
    // r8 (operator "음악이랑 유튜브를 하나로"): one media card shows whatever plays; [kind] only picks the idle look
    val m = state.media
    if (m == null) {
        val ic = if (kind == MediaKind.MUSIC) Ic.NOTE else Ic.VIDEO
        StateCard(e, ic, "미디어", ic, null, "폰에서 재생하면 여기 나와요", actions::openMediaApp)
        return
    }
    // r9 (operator, YouTube Music / YouTube refs): one look for music and video in every theme, picked by the slot's shape
    when (mediaShape(e.w.value, e.h.value)) {
        MediaShape.TALL -> MediaTall(e, m, actions)
        MediaShape.COVER -> MediaCover(e, m, actions)
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
private fun MediaTime(ms: Long, c: Color) = Txt(mmss(ms), 12.dp, c, num = true)

/**
 * Tall slot (w/h <= 0.82), YouTube Music portrait: art centred on top (video: 16:9 still), title and artist
 * left-aligned under it, bar + times, then prev / ink play disc / next. Always shown. Tap opens Now Playing.
 */
@Composable
private fun MediaTall(e: Env, m: MediaInfo, actions: StandbyActions) {
    val w = e.w
    val pad = (w * 0.075f).coerceIn(12.dp, 32.dp)
    val ts = (w * 0.068f).coerceIn(15.dp, 26.dp)
    val play = (w * 0.19f).coerceIn(52.dp, 80.dp)
    val gap = (w * 0.04f).coerceIn(8.dp, 18.dp)
    val video = m.kind == MediaKind.VIDEO
    val ink = e.ink.ink
    Column(Modifier.fillMaxSize().padding(pad).tap(actions::openNowPlaying)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            val ah = min(maxHeight, if (video) maxWidth * (9f / 16f) else maxWidth)
            Art(e, m.art, if (video) Ic.VIDEO else Ic.NOTE, Modifier.size(if (video) maxWidth else ah, ah), (ah * 0.05f).coerceIn(8.dp, 18.dp))
        }
        Spacer(Modifier.height(gap))
        Txt(m.title, ts, ink, weight = 700, lines = if (video) 2 else 1)
        Spacer(Modifier.height(4.dp))
        Txt(m.artist, ts * 0.78f, e.readInk2, weight = 500)
        Spacer(Modifier.height(gap))
        Bar(e, progress(m), ink)
        if (w >= 180.dp) Row(Modifier.fillMaxWidth().padding(top = 5.dp)) {
            MediaTime(m.positionMs, e.readInk2)
            Spacer(Modifier.weight(1f))
            MediaTime(m.durationMs, e.readInk2)
        }
        Spacer(Modifier.height(gap * 1.4f))
        // play disc = ink, glyph = the page colour (mockup: white disc + black glyph on the iPhone theme)
        MediaTransport(
            e, m, actions, play, max(48.dp, play * 0.7f), ink, Color.Transparent, ink, e.ink.bg, Arrangement.SpaceBetween,
            Modifier.fillMaxWidth().padding(horizontal = w * 0.06f),
        )
    }
}

/**
 * Wide or square slot, YouTube landscape: the art / still fills the slot. At rest only the picture; a tap brings the
 * scrim, title top-leading, transport centred and bar + times along the bottom for [MEDIA_CONTROLS_MS] (any button
 * keeps them up); a tap while they show opens Now Playing. Everything over the picture is white + scrim.
 */
@Composable
private fun MediaCover(e: Env, m: MediaInfo, actions: StandbyActions) {
    val s = min(e.w, e.h)
    var shown by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(shown, tick) { if (shown) { delay(MEDIA_CONTROLS_MS); shown = false } }
    val a by animateFloatAsState(if (shown) 1f else 0f, tween(200), label = "media")
    val video = m.kind == MediaKind.VIDEO
    val fg = overPicture(e)
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(e.plateRadius)).tap { if (shown) actions.openNowPlaying() else shown = true }) {
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
            Column(Modifier.fillMaxWidth().padding(pad)) {
                Txt(m.title, ts, fg, weight = 700, lines = if (video && s >= 200.dp) 2 else 1)
                if (s >= 150.dp) {
                    Spacer(Modifier.height(3.dp))
                    Txt(m.artist, ts * 0.8f, fg.copy(alpha = 0.75f), weight = 500)
                }
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
                if (times) { MediaTime(m.positionMs, tc); Spacer(Modifier.width(10.dp)) }
                Bar(e, progress(m), fg, Modifier.weight(1f), track = fg.copy(alpha = 0.25f))
                if (times) { Spacer(Modifier.width(10.dp)); MediaTime(m.durationMs, tc) }
            }
        }
    }
}

/** How long a tap keeps the cover's controls up. */
internal const val MEDIA_CONTROLS_MS = 3_000L

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

// ---------------------------------------------------------------- agenda, usage

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
    Column(Modifier.fillMaxSize().padding(p).tap(if (items == null) ({ actions.openSettings("calendar") }) else null)) {
        val head = buildAnnotatedString {
            withStyle(SpanStyle(color = e.ink.accText)) { append("${dowKo(e.today)}요일") }
            append(" ${e.today.dayOfMonth}")
            if (!items.isNullOrEmpty()) withStyle(SpanStyle(color = e.readInk2, fontWeight = FontWeight.Medium)) { append(" · 남은 일정 ${items.size}") }
        }
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

/** Weather glyph through Ink roles: cloud in ink, sun / rain in the sun / blue roles; mono-icon themes draw it in ink. */
@Composable
internal fun WxIcon(e: Env, ic: Ic, modifier: Modifier) {
    val c2 = if (e.t.tokens.monoIcons) e.ink.ink else if (ic == Ic.RAIN) e.ink.blue else e.ink.sun
    IconView(ic, e.ink.ink, modifier, e.ink.bg, color2 = c2)
}

/** Weather / calendar inset (mockup P(s)): 7 % of the short side, 12–28 dp. */
private val Env.p7: Dp get() = (min(w, h) * 0.07f).coerceIn(12.dp, 28.dp)
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
private fun Hero(e: Env, text: AnnotatedString, cap: Dp, color: Color, maxW: Dp) {
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
private fun TempRow(e: Env, wx: WeatherInfo, cap: Dp, maxW: Dp, color: Color) {
    val ico = cap * 0.4f
    val deg = cap * 0.2f
    Row {
        Hero(e, AnnotatedString("${wx.tempC}"), cap, color, maxW - ico - deg - cap * 0.2f)
        Spacer(Modifier.width(cap * 0.12f)) // italic digits lean past their advance
        Canvas(Modifier.size(deg)) { drawCircle(color, size.minDimension * 0.38f, style = Stroke(size.minDimension * 0.22f)) }
        Spacer(Modifier.width(cap * 0.08f))
        WxIcon(e, weatherIc(wx.condition), Modifier.size(ico))
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

private val DOW_SUN = listOf("일", "월", "화", "수", "목", "금", "토")

/**
 * [weeks] rows of days from [start] (a Sunday) under a weekday header: today on an accent disc, Sundays in the red
 * role, a dot in the event colour under days with events. [month] blanks days outside it. Numbers fit the cell.
 */
@Composable
private fun DayGrid(e: Env, start: LocalDate, weeks: Int, events: List<AgendaItem>, modifier: Modifier, month: Int? = null) {
    val dots = remember(events, e.ink) { events.filter { it.date != null }.groupBy { it.date!! }.mapValues { eventInk(e, it.value.first().color) } }
    val onAcc = if (Contrast.ratio(e.ink.ink, e.ink.acc) >= Contrast.ratio(e.ink.bg, e.ink.acc)) e.ink.ink else e.ink.bg
    BoxWithConstraints(modifier) {
        // rows never taller than wide (a one-week strip stays compact); the grid sits centred in its box
        val rowH = min(maxHeight / (weeks + 0.8f), maxWidth / 7 * 1.15f)
        val d = min(rowH, maxWidth / 7) * 0.86f
        val fs = (d * 0.5f).coerceIn(11.dp, 20.dp)
        Column(Modifier.fillMaxWidth().align(Alignment.Center)) {
            Row(Modifier.fillMaxWidth().height(rowH * 0.8f), verticalAlignment = Alignment.CenterVertically) {
                DOW_SUN.forEachIndexed { i, s ->
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Txt(s, fs * 0.85f, if (i == 0) e.ink.weekday else e.readInk2, weight = 600)
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
