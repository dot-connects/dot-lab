package kr.dot.sb.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.ui.state.AgendaItem
import kr.dot.sb.ui.state.HourTemp
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.MediaKind
import kr.dot.sb.ui.state.Photo
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.state.TimerState
import kr.dot.sb.ui.state.UsageInfo
import kr.dot.sb.ui.state.UsageWindow
import kr.dot.sb.ui.state.WeatherCondition
import kr.dot.sb.ui.state.WeatherInfo
import java.time.LocalDateTime
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import kr.dot.sb.data.NightRules
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.clock.ClockRegistry
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.clockStyle
import kr.dot.sb.ui.theme.drawnTheme

/** This phone's kind: a hinge phone gets the preview's 커버|안쪽 toggle, a bar phone has one screen (r10: no device menu). */
enum class Phone { FOLD, FLIP, BAR }

/** The screens a board can be previewed on (design-system §3.3). Sizes are landscape dp. */
enum class PreviewDevice(val ko: String, val w: Int, val h: Int, val cover: Boolean) {
    FOLD_COVER("폴드 커버", 751, 475, true),
    FOLD_INNER("폴드 펼침", 932, 704, false),
    BAR("바형", 780, 360, false),
    FLIP("플립", 400, 362, false);

    fun width(portrait: Boolean) = if (portrait) minOf(w, h) else maxOf(w, h)
    fun height(portrait: Boolean) = if (portrait) maxOf(w, h) else minOf(w, h)

    companion object {
        /** The preview follows the phone: Fold 커버/안쪽, Flip 커버/안쪽 (the inner screen is a bar-sized screen), bar = itself. */
        fun of(phone: Phone, inner: Boolean) = when (phone) {
            Phone.FOLD -> if (inner) FOLD_INNER else FOLD_COVER
            Phone.FLIP -> if (inner) BAR else FLIP
            Phone.BAR -> BAR
        }
    }
}

/**
 * Draws [content] at the real device dp size and scales it to fit [maxHeight] / the available width, so a preview is
 * exactly the composable the dream shows. [overlay] is drawn on top in unscaled coordinates (multiply by `scale`).
 */
@Composable
fun PreviewFrame(
    w: Int,
    h: Int,
    modifier: Modifier = Modifier,
    maxHeight: Dp = Dp.Infinity,
    radius: Dp = 12.dp,
    /** Night fraction of the page behind [content] (the pull-down panel's previews follow the screen). */
    night: Float = 0f,
    overlay: @Composable BoxScope.(scale: Float) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        // two-pane page (review S1): the preview takes the pane width instead of the phone height cap
        val cap = if (LocalPreviewPane.current) Dp.Infinity else maxHeight
        val scale = min(maxWidth.value / w, cap.value / h)
        val shape = RoundedCornerShape(radius)
        Box(Modifier.size((w * scale).dp, (h * scale).dp).clip(shape).border(1.dp, SK.line, shape)) {
            val (bg, custom) = kr.dot.sb.ui.theme.LocalBoardBg.current
            kr.dot.sb.ui.theme.ThemeBackground(kr.dot.sb.ui.theme.LocalTheme.current, night, bg = bg, custom = custom)
            Box(
                Modifier.layout { measurable, _ ->
                    val pw = w.dp.roundToPx()
                    val ph = h.dp.roundToPx()
                    val p = measurable.measure(Constraints.fixed(pw, ph))
                    layout((pw * scale).roundToInt(), (ph * scale).roundToInt()) {
                        p.placeWithLayer(0, 0) { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0f) }
                    }
                },
            ) { content() }
            Box(Modifier.size((w * scale).dp, (h * scale).dp)) { overlay(scale) }
        }
    }
}

/** A fixed, fully-populated state for previews (the real feeds are not needed in settings). */
object Sample {
    private val now: LocalDateTime = LocalDateTime.of(2026, 11, 9, 21, 41)

    private fun gradient(w: Int, h: Int, top: Int, bottom: Int, ridge: Int) = run {
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        val p = android.graphics.Paint()
        p.shader = android.graphics.LinearGradient(0f, 0f, 0f, h.toFloat(), top, bottom, android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null; p.color = ridge
        val path = android.graphics.Path().apply {
            moveTo(0f, h.toFloat())
            for (x in 0..w step 8) lineTo(x.toFloat(), h * 0.68f - h * 0.09f * kotlin.math.sin(x / w.toFloat() * 7f))
            lineTo(w.toFloat(), h.toFloat()); close()
        }
        c.drawPath(path, p)
        bmp.asImageBitmap()
    }

    private val art by lazy { gradient(240, 240, 0xFF7FB3FF.toInt(), 0xFFF4B8D4.toInt(), 0xFFFFF1C4.toInt()) }
    private val photo by lazy { gradient(640, 420, 0xFF3F6D8C.toInt(), 0xFFE8E2C8.toInt(), 0xFF2F4A36.toInt()) }

    fun state(c: AppConfig, night: Float = 0f): StandbyState = StandbyState(
        now = now,
        use24h = c.use24h ?: false,
        night = night,
        media = MediaInfo("YouTube Music", "com.google.android.apps.youtube.music", MediaKind.MUSIC, "Ditto", "NewJeans", art, true, 72_000, 186_000),
        usage = UsageInfo(UsageWindow(20, 1794230400), UsageWindow(49, 1794308400), UsageWindow(0, 1794192419), UsageWindow(9, 1794574925), 1794228000),
        weather = WeatherInfo(
            c.city.name, 7, WeatherCondition.CLOUDY, 11, 2,
            listOf(HourTemp(22, 6, WeatherCondition.CLOUDY), HourTemp(23, 5, WeatherCondition.CLOUDY), HourTemp(0, 5, WeatherCondition.NIGHT_CLEAR), HourTemp(1, 4, WeatherCondition.NIGHT_CLEAR), HourTemp(2, 4, WeatherCondition.FOG)),
            1794228000,
        ),
        agenda = listOf(AgendaItem("09:30", "팀 회의", 0xFF4D9BEF), AgendaItem("13:00", "병원 예약", 0xFFFF9C56), AgendaItem("19:00", "헬스", 0xFF6FDC86)),
        nextAlarm = LocalDateTime.of(2026, 11, 10, 6, 30),
        timers = mapOf("desk-1" to TimerState(leftSec = 18 * 60 + 24, running = true)),
        photos = listOf(Photo(photo, "설악산", "2025년 10월 18일")),
        memory = Photo(photo, "설악산", "2023년 11월 9일", yearsAgo = 3),
        notifications = sampleNotifs(),
    )

    /** Picker preview of the notification cards (sample text, never the phone's own). */
    private fun sampleNotifs(): List<kr.dot.sb.ui.state.NotifItem> {
        val t = now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        return listOf(
            kr.dot.sb.ui.state.NotifItem("p1", "카카오톡", "com.kakao.talk", "엄마", "저녁 먹었어?", t, plateColor = 0xFFF5D000),
            kr.dot.sb.ui.state.NotifItem("p2", "쿠팡", "com.coupang.mobile", "배송 완료", "문 앞에 도착했습니다", t - 12 * 60_000, plateColor = 0xFFE1393B),
            kr.dot.sb.ui.state.NotifItem("p3", "캘린더", "com.samsung.android.calendar", "팀 회의", "10:30 · 3층 회의실", t - 20 * 60_000, plateColor = 0xFF3478F6),
        )
    }
}

// ---- r10 preview shell: one preview per screen, full at the top of the list, a 96 dp mini once it scrolls away --------

/** What a slide draws for: the device, its orientation, and the sample state (night already decided). */
class PreviewLook(val device: PreviewDevice, val portrait: Boolean, val night: Float, val state: StandbyState) {
    /** Night only: how much darker the night brightness cap makes the screen (mockup `0.4 + nBright/50`, dimming only). */
    var dim: Float = 0f
        internal set
}

/** One slide: [name] under the frame, [draw] = the StandBy content at real device size. [start] = the "처음" page (home). */
class Slide(val name: String, val start: Boolean = false, val route: String? = null, val draw: @Composable BoxScope.(PreviewLook) -> Unit)

/**
 * A screen's preview (mockup `pv()`): [slides] swipe sideways, the first is the screen's own view. [cards] = the home
 * pager (peeking cards with names, no mini). [fixed] = one device and no bar (폴드를 펼쳤을 때: "폴드 펼침").
 */
class Preview(
    val config: AppConfig,
    val slides: List<Slide>,
    val caption: String? = null,
    val cards: Boolean = false,
    val fixed: PreviewDevice? = null,
    /** The bar starts on 밤 (밤 화면). */
    val night: Boolean = false,
    /** false = taps reach the slide (the page editor's slots) instead of opening full screen. */
    val full: Boolean = true,
    /** The slide the pager opens on. */
    val initial: Int = 0,
    /** A page's own settings: the swipe settled on slide i (not [initial]), whose [Slide.route] opens instead. */
    val onSettle: ((Int) -> Unit)? = null,
)

/** The bar's choices, shared by the full preview and the mini (saved across rotation). */
@Stable
class PreviewState(night: Boolean = false, inner: Boolean = false, portrait: Boolean = false) {
    var night by mutableStateOf(night)
    var inner by mutableStateOf(inner)
    var portrait by mutableStateOf(portrait)

    companion object {
        val Saver = listSaver<PreviewState, Boolean>({ listOf(it.night, it.inner, it.portrait) }, { PreviewState(it[0], it[1], it[2]) })
    }
}

/** 밤 shows the night look only where the dream would: the holder's rule, with "it is dark and late". */
private fun AppConfig.nightApplies(): Boolean =
    NightRules.on(nightMode, displayOff, scheduled = true, auto = true, forceNight = drawnTheme().tokens.light)

@Composable
private fun rememberLook(p: Preview, st: PreviewState, phone: Phone): PreviewLook {
    val dev = p.fixed ?: PreviewDevice.of(phone, st.inner)
    val portrait = p.fixed == null && st.portrait
    val night = if (st.night && p.config.nightApplies()) 1f else 0f
    val state = remember(p.config, night) { Sample.state(p.config, night) }
    return PreviewLook(dev, portrait, night, state).also { if (night > 0f) it.dim = 1f - (0.4f + p.config.nightMaxBrightness * 2f).coerceAtMost(1f) }
}

private val PILL_BG = Color(0xD917181C)
private val PILL_LINE = Color(0x1FFFFFFF)

/** Two-way pill (낮|밤, 커버|안쪽, 가로|세로): 30 dp to the eye, 48 dp to the finger. */
@Composable
private fun Pick(a: String, b: String, second: Boolean, onPick: (Boolean) -> Unit) {
    val acc = LocalAccent.current
    Row(
        Modifier.height(48.dp).drawBehind {
            val h = 30.dp.toPx()
            val top = (size.height - h) / 2
            drawRoundRect(PILL_BG, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2))
            drawRoundRect(PILL_LINE, Offset(0f, top), Size(size.width, h), CornerRadius(h / 2), style = Stroke(1.dp.toPx()))
        }.padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(a to false, b to true).forEach { (t, v) ->
            val on = v == second
            Box(Modifier.fillMaxHeight().clickable { onPick(v) }, Alignment.Center) {
                Box(Modifier.height(26.dp).widthIn(min = 38.dp).clip(RoundedCornerShape(50)).background(if (on) acc else Color.Transparent).padding(horizontal = 10.dp), Alignment.Center) {
                    T(t, 12.sp, if (on) 700 else 600, if (on) SK.bg else SK.muted, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DayNight(st: PreviewState) = Pick("낮", "밤", st.night) { st.night = it }

/** Bar above the frame: [커버|안쪽] on a hinge phone, [가로|세로] on all, [낮|밤] on the right; a fixed device = its name. */
@Composable
private fun PreviewBar(p: Preview, st: PreviewState, phone: Phone) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if (p.fixed != null) {
            Box(Modifier.height(48.dp), Alignment.Center) {
                Box(Modifier.height(30.dp).clip(RoundedCornerShape(50)).background(PILL_BG).border(1.dp, PILL_LINE, RoundedCornerShape(50)).padding(horizontal = 12.dp), Alignment.Center) {
                    T(p.fixed.ko, 12.sp, 600, maxLines = 1)
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (phone != Phone.BAR) Pick("커버", "안쪽", st.inner) { st.inner = it }
                Pick("가로", "세로", st.portrait) { st.portrait = it }
            }
            DayNight(st)
        }
    }
}

/** One slide in its frame. In the phone list: landscape up to 250 dp tall, portrait 300 dp (mockup `.pv`). */
@Composable
private fun SlideFrame(s: Slide, look: PreviewLook, modifier: Modifier = Modifier, maxHeight: Dp = if (look.portrait) 300.dp else 250.dp, radius: Dp = 18.dp) {
    PreviewFrame(
        look.device.width(look.portrait), look.device.height(look.portrait), modifier, maxHeight = maxHeight, radius = radius, night = look.night,
        overlay = { if (look.dim > 0f) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = look.dim))) /* literal-ok: dims the preview */ },
    ) { s.draw(this, look) }
}

/** The full preview: bar, swipeable slides, caption. Tapping a slide asks for full screen ([onFull] gets its index). */
@Composable
internal fun FullPreview(p: Preview, st: PreviewState, pager: PagerState, phone: Phone, onFull: (Int) -> Unit) {
    val look = rememberLook(p, st, phone)
    val acc = LocalAccent.current
    Column(Modifier.fillMaxWidth()) {
        PreviewBar(p, st, phone)
        Gap(4.dp)
        HorizontalPager(
            pager, Modifier.fillMaxWidth(), contentPadding = PaddingValues(end = if (p.cards) 36.dp else 0.dp),
            pageSpacing = if (p.cards) 12.dp else 10.dp, verticalAlignment = Alignment.Top,
        ) { i ->
            val s = p.slides[i]
            Column(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth()) {
                    SlideFrame(s, look, Modifier.fillMaxWidth(), radius = if (p.cards) 16.dp else 18.dp)
                    if (p.full) Box(Modifier.matchParentSize().clickable(onClickLabel = "전체 화면") { onFull(i) })
                }
                if (p.cards) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(s.name, 15.sp, 600, maxLines = 1)
                        if (s.start) Pill("처음", acc, Modifier.padding(start = 8.dp))
                    }
                } else if (p.slides.size > 1) {
                    T(s.name, 12.sp, 500, SK.muted, Modifier.fillMaxWidth().padding(top = 4.dp), maxLines = 1, align = TextAlign.Center)
                }
            }
        }
        val night = when {
            !st.night || look.night > 0f -> null
            p.config.displayOff == DisplayOff.NEVER -> "지금은 밤 화면이 꺼져 있어요"
            else -> "밤에도 이렇게 보여요"
        }
        val tail = when {
            p.cards -> "옆으로 넘기면 다른 페이지가 보여요 · 누르면 전체 화면"
            p.onSettle != null && p.slides.size > 1 -> "옆으로 넘기면 다른 페이지 설정"
            p.slides.size > 1 -> "옆으로 넘기면 다른 페이지"
            else -> null
        }
        T(
            listOfNotNull(night, p.caption, tail).joinToString(" · "), 12.sp, 400, SK.muted,
            Modifier.fillMaxWidth().heightIn(min = 22.dp).padding(top = 6.dp, start = 4.dp, end = 4.dp), align = TextAlign.Center, lineHeight = 16.sp,
        )
    }
}

/**
 * The mini preview (mockup `.mini`): the current slide at 96 dp + 낮|밤 + [actions], over the top of the list. It only
 * overlays (no height change), so showing it never moves the content; a tap scrolls back to the full preview.
 */
@Composable
internal fun MiniPreview(p: Preview, st: PreviewState, pager: PagerState, phone: Phone, onTop: () -> Unit, actions: (@Composable () -> Unit)?) {
    val look = rememberLook(p, st, phone)
    val s = p.slides[pager.currentPage.coerceIn(0, p.slides.lastIndex)]
    val ratio = look.device.width(look.portrait).toFloat() / look.device.height(look.portrait)
    val none = remember { MutableInteractionSource() }
    Row(
        Modifier.fillMaxWidth().shadow(8.dp).background(SK.bg)
            .drawBehind { drawLine(SK.line, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx()) }
            .clickable(interactionSource = none, indication = null, onClickLabel = "미리보기로 올라가기", onClick = onTop)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size((96 * ratio).dp, 96.dp)) { SlideFrame(s, look, Modifier.matchParentSize(), maxHeight = 96.dp, radius = 10.dp) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (p.fixed == null) DayNight(st)
            actions?.invoke()
        }
    }
}

/** Full screen: the slide at the device's own proportions on black; any tap closes. */
@Composable
internal fun FullScreenPreview(p: Preview, st: PreviewState, phone: Phone, index: Int, onClose: () -> Unit) {
    val look = rememberLook(p, st, phone)
    val none = remember { MutableInteractionSource() }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black /* literal-ok: behind the preview */).clickable(interactionSource = none, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        SlideFrame(p.slides[index.coerceIn(0, p.slides.lastIndex)], look, Modifier.fillMaxWidth(), maxHeight = maxHeight, radius = 0.dp)
        Box(Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 16.dp).clip(RoundedCornerShape(50)).background(SK.scrim).padding(horizontal = 14.dp, vertical = 8.dp)) {
            T("화면을 누르면 닫혀요", 13.sp, 500, SK.text)
        }
    }
}

/**
 * The StandBy pages that are on ([Pages.visible]) as preview slides in pager order, the start page marked, each with
 * its settings route. [include] = a page index shown even when it is off (its own settings screen).
 */
fun pageSlides(config: AppConfig, include: Int? = null): List<Slide> {
    val widgets = widgetsBoard(config)
    val face = ClockRegistry.pages(config.myClocks).first()
    val pages = pagesWith(config, include)
    val start = Pages.visible(config).getOrNull(Pages.start(config))?.index
    return pages.map { p ->
        val r = pageRoute(p)
        when (p.index) {
            0 -> Slide("위젯", start == 0, r) { l -> BoardView(widgets, l.state, config, StandbyActions.NONE, Modifier.fillMaxSize()) }
            1 -> Slide("사진", start == 1, r) { l ->
                val ink = kr.dot.sb.ui.theme.Ink.of(l.night, androidx.compose.ui.graphics.Color(config.clockStyle(kr.dot.sb.model.FaceId.DIGITAL).color), kr.dot.sb.ui.theme.LocalTheme.current)
                androidx.compose.runtime.CompositionLocalProvider(kr.dot.sb.ui.theme.LocalInk provides ink) {
                    kr.dot.sb.ui.screens.PhotosPage(l.state, config, StandbyActions.NONE, active = false)
                }
            }
            Pages.CLOCK -> Slide("시계", start == Pages.CLOCK, r) { l -> ClockFace(face, config.clockStyle(face), l.state, Modifier.fillMaxSize(), analog = config.analogStyle) }
            else -> Slide(p.board!!.name, start == p.index, r) { l -> BoardView(p.board, l.state, config, StandbyActions.NONE, Modifier.fillMaxSize()) }
        }
    }
}

/** The pages that are on, plus page [include] even when it is off, in pager order. */
internal fun pagesWith(config: AppConfig, include: Int?): List<PageRef> = Pages.all(config).filter { Pages.isOn(config, it) || it.index == include }

/** Where 페이지 구성 (and a swipe on a page's settings preview) opens page [p]. */
internal fun pageRoute(p: PageRef): String = when (p.index) {
    0 -> Routes.WIDGETS
    1 -> Routes.PHOTOS
    Pages.CLOCK -> Routes.CLOCKS
    else -> Routes.board(p.board!!.id)
}

/**
 * A page's own settings preview (11-14): every page in pager order with [own] drawn for page [index] (shown even when
 * off), opening on it; a swipe that settles on another page opens that page's settings in place (no back step).
 */
internal fun pagePreview(config: AppConfig, index: Int, own: Slide, nav: Nav, caption: String? = null, full: Boolean = true): Preview {
    val all = pageSlides(config, include = index)
    val at = pagesWith(config, index).indexOfFirst { it.index == index }
    val slides = all.mapIndexed { i, s -> if (i == at) Slide(own.name, s.start, s.route, own.draw) else s }
    return Preview(config, slides, caption = caption, full = full, initial = at, onSettle = { i -> slides[i].route?.let(nav::replace) })
}
