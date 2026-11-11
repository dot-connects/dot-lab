package kr.dot.sb.ui.screens

import kr.dot.sb.ui.theme.ThemeBackground
import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.ui.graphics.Brush
import kr.dot.sb.ui.cards.tap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.UnfoldAction
import kr.dot.sb.platform.Brightness
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalInk
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.ProvideTheme
import kr.dot.sb.ui.theme.Motion
import kr.dot.sb.ui.theme.Themes
import java.time.LocalDateTime
import kotlin.math.abs

internal const val BAR_HIDE_MS = 2_500L

/** Widgets, Photos, Clocks come first (spec §1c); user boards follow. */
internal const val FIXED_PAGES = 3

/** Full-screen layer on top of the pages, by interrupt priority (spec §2): alarm > notification > live > now playing. */
internal enum class Layer { NONE, ALARM, NOTIFICATION, LIVE, NOW_PLAYING }

internal fun topLayer(state: StandbyState, config: AppConfig, nowPlayingOpen: Boolean): Layer = when {
    state.alarm != null -> Layer.ALARM
    state.notification != null && config.showNotifications -> Layer.NOTIFICATION
    state.live != null && state.liveExpanded -> Layer.LIVE
    nowPlayingOpen && state.media != null -> Layer.NOW_PLAYING
    else -> Layer.NONE
}

/** Test hook: start on clock face [face], in clock edit mode (optionally with the colour sheet), or with Now Playing open. */
internal data class RootStart(val edit: Boolean = false, val sheet: Boolean = false, val nowPlaying: Boolean = false, val face: Int = 0, val bar: Boolean = false)

/**
 * The whole StandBy surface: horizontal pages (widgets, photos, clocks, boards), the interrupt layers, night, the
 * display-off state, burn-in shift and the leave-StandBy edge swipe.
 * [onStyleChange] reports clock colour edits made in the clock edit mode (wire to ConfigStore.update).
 */
@Composable
fun StandbyRoot(
    state: StandbyState,
    config: AppConfig,
    actions: StandbyActions,
    modifier: Modifier = Modifier,
    onStyleChange: (FaceId, ClockStyle) -> Unit = { _, _ -> },
) = RootImpl(state, config, actions, modifier, onStyleChange, RootStart())

@Composable
internal fun RootImpl(
    state: StandbyState,
    config: AppConfig,
    actions: StandbyActions,
    modifier: Modifier,
    onStyleChange: (FaceId, ClockStyle) -> Unit,
    start: RootStart,
) = ProvideTheme(config.theme) {
    val act by rememberUpdatedState(actions)
    val count by rememberUpdatedState(FIXED_PAGES + config.boards.size)
    // Hoisted above the display-off branch so waking returns to the same page and clock face.
    val pager = rememberPagerState(config.startPage.coerceIn(0, count - 1)) { count }
    val clockPage = remember { mutableIntStateOf(start.face) }
    var editing by remember { mutableStateOf(start.edit) }
    var npOpen by remember { mutableStateOf(start.nowPlaying) }
    val edits = remember { mutableStateMapOf<FaceId, ClockStyle>() }
    // unfolded + 무드등: the dream that survives the unfold turns into the glow in place (spec M1); a tap goes back to the board
    var moodOff by remember { mutableStateOf(false) }
    LaunchedEffect(state.unfolded) { if (!state.unfolded) moodOff = false }

    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { act.pageChanged(it) } }
    LaunchedEffect(state.unfolded, config.onUnfold) {
        if (state.unfolded && config.onUnfold == UnfoldAction.BOOK_BOARD) {
            val i = config.boards.indexOfFirst { it.id == "book" }
            if (i >= 0) pager.scrollToPage(FIXED_PAGES + i)
        }
    }
    if (state.media == null && npOpen) npOpen = false

    // Brightness bar: swipe down from the top edge opens it, 2.5 s after the last touch it closes (day value or night cap).
    val nightNow = state.night >= 0.5f
    var barOn by remember { mutableStateOf(start.bar) }
    var level by remember { mutableFloatStateOf(Brightness.shown(nightNow, config)) }
    var barPressed by remember { mutableStateOf(false) }
    var barTick by remember { mutableIntStateOf(0) }
    val canBar by rememberUpdatedState(!state.displayOff && !editing && topLayer(state, config, npOpen) == Layer.NONE)
    // the swipe that opens the bar keeps going as a slider: sideways travel moves the level from where it was (r8)
    var slideBase by remember { mutableFloatStateOf(0f) }
    val barOpen by rememberUpdatedState({ level = Brightness.shown(nightNow, config); slideBase = level; barOn = true; barPressed = true; barTick++ })
    val barSlide by rememberUpdatedState({ d: Float ->
        val v = (slideBase + d).coerceIn(0f, 1f)
        if (v != level) { level = v; act.setBrightness(v) }
    })
    val barUp by rememberUpdatedState({ barPressed = false; barTick++ })
    LaunchedEffect(barOn, barTick, barPressed) { if (barOn && !barPressed) { delay(BAR_HIDE_MS); barOn = false } }

    BoxWithConstraints(
        modifier.fillMaxSize()
            .pointerInput(Unit) { rootGestures({ act.userActivity() }, { act.exit() }, { canBar }, { barOpen() }, { barSlide(it) }, { barUp() }) },
    ) {
        // Display off: pure black, nothing else composed; the root gesture above is the tap catcher (spec E14).
        if (state.displayOff) return@BoxWithConstraints
        ThemeBackground(
            LocalTheme.current, state.night, bg = config.boardBg, custom = Color(config.boardBgColor),
            photo = state.media?.art ?: state.memory?.image,
        )
        val w = maxWidth
        val h = maxHeight
        val accent = Color(config.clockStyle(FaceId.DIGITAL).color)
        val th = LocalTheme.current
        val ink = remember(state.night, accent, th) { Ink.of(state.night, accent, th) }
        val layer = topLayer(state, config, npOpen)
        val widgets = remember(config.widgetLeft, config.widgetRight) {
            Board("widgets", "위젯", Layout.TWO, listOf(config.widgetLeft, config.widgetRight))
        }
        val mood = state.unfolded && config.onUnfold == UnfoldAction.MOOD_LIGHT && !moodOff && !editing
        CompositionLocalProvider(LocalInk provides ink) {
            Box(Modifier.fillMaxSize().offset(state.shiftX.dp, state.shiftY.dp)) {
                if (mood) MoodLight(config.glowColor, ink) { moodOff = true } else HorizontalPager(
                    pager, Modifier.fillMaxSize(),
                    userScrollEnabled = !editing,
                    flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 400f)),
                    key = { if (it < FIXED_PAGES) "fixed-$it" else config.boards.getOrNull(it - FIXED_PAGES)?.id ?: "p$it" },
                ) { page ->
                    when (page) {
                        0 -> BoardPage(widgets, state, config, actions)
                        1 -> PhotosPage(state, config, actions, active = pager.settledPage == 1 && layer == Layer.NONE)
                        2 -> ClocksPage(
                            state, config, clockPage, editing, { editing = it }, start.sheet,
                            styleOf = { f -> edits[f] ?: config.clockStyle(f) },
                            onPick = { f, s -> edits[f] = s; onStyleChange(f, s) },
                        )
                        else -> config.boards.getOrNull(page - FIXED_PAGES)?.let { BoardPage(it, state, config, actions) }
                    }
                }
                if (!editing && !mood) {
                    val s = min(w, h)
                    AutoHideDots(
                        count, pager.currentPage, pager.isScrollInProgress, vertical = false,
                        dot = max(5.dp, s * 0.013f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Grid.margin(s) / 2 - max(5.dp, s * 0.013f) / 2),
                    )
                }
                Overlays(state, config, actions, layer, editing, onNowPlaying = { npOpen = it })
            }
            val a by animateFloatAsState(if (barOn) 1f else 0f, tween(200), label = "bar")
            if (a > 0f) BrightnessBar(
                level, { v -> level = v; barTick++; act.setBrightness(v) }, { barPressed = it }, w, h,
                Modifier.align(Alignment.TopCenter).alpha(a),
            )
            // keep the system back-swipe off the board pager's edges (spec E4/R7); the system honours ~200 dp per edge
            Box(Modifier.align(Alignment.CenterStart).size(24.dp, 200.dp).systemGestureExclusion())
            Box(Modifier.align(Alignment.CenterEnd).size(24.dp, 200.dp).systemGestureExclusion())
        }
    }
}

/** Warm full-screen light (spec M1), same colours as the glow screen: static, so it costs nothing. */
@Composable
private fun MoodLight(color: Long, ink: Ink, onTap: () -> Unit) {
    val top = ink.mapped(Color(color or 0xFF000000L))
    val bottom = Color(top.red * 0.45f, top.green * 0.3f, top.blue * 0.25f)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom))).tap(onTap))
}

/** A board (or the widgets page). Long press asks for the editor, which needs the unlocked phone (spec D2). */
@Composable
private fun BoardPage(board: Board, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val act by rememberUpdatedState(actions)
    Box(Modifier.fillMaxSize().pointerInput(board.id) { detectTapGestures(onLongPress = { act.openSettings(if (board.id == "widgets") "widgets" else "board:${board.id}") }) }) {
        BoardView(board, state, config, actions, Modifier.fillMaxSize())
    }
}

/**
 * Root gesture: every touch reports user activity; a swipe up that starts in the bottom 48 dp and travels more than
 * 30 % of the height leaves StandBy (spec E8). Runs in the Initial pass so it claims the swipe before pagers do.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.rootGestures(
    onTouch: () -> Unit, onExit: () -> Unit, canOpenBar: () -> Boolean, onOpenBar: () -> Unit,
    onBarSlide: (Float) -> Unit, onBarUp: () -> Unit,
) {
    val edge = 48.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onTouch()
        if (down.position.y < edge && canOpenBar()) {
            // top edge: a downward drag > 24 dp opens the brightness bar; claimed so the pager and card stacks stay still.
            // The same finger then slides it: sideways travel / bar width = change in level.
            var claimed = false
            var x0 = 0f
            val bw = barWidth(size.width.toDp()).toPx()
            while (true) {
                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed) break
                val dy = c.position.y - down.position.y
                if (!claimed && dy > 24.dp.toPx() && dy > abs(c.position.x - down.position.x)) { claimed = true; x0 = c.position.x; onOpenBar() }
                if (claimed) { c.consume(); onBarSlide((c.position.x - x0) / bw) }
            }
            if (claimed) onBarUp()
            return@awaitEachGesture
        }
        if (down.position.y < size.height - edge) return@awaitEachGesture
        var claimed = false
        while (true) {
            val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
            if (!c.pressed) break
            val dy = down.position.y - c.position.y
            if (!claimed && dy > viewConfiguration.touchSlop && dy > abs(c.position.x - down.position.x)) claimed = true
            if (claimed) {
                c.consume()
                if (dy > size.height * 0.3f) { onExit(); break }
            }
        }
    }
}

/** Page dots: fade in while scrolling, out 1.5 s after settling (design §2.5, §3.1). They reserve no space. */
@Composable
internal fun AutoHideDots(
    count: Int, current: Int, scrolling: Boolean, vertical: Boolean, dot: Dp, modifier: Modifier,
    always: Boolean = false, dim: Color? = null,
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling) {
        if (scrolling) show = true else if (show) { delay(Motion.DOTS_HIDE_MS.toLong()); show = false }
    }
    val a by animateFloatAsState(if (show || always) 1f else 0f, tween(300), label = "dots")
    if (a <= 0f || count < 2) return
    val ink = LocalInk.current
    val dots = @Composable {
        repeat(count) { Box(Modifier.size(dot).clip(CircleShape).background(if (it == current) ink.ink else dim ?: ink.ink3)) }
    }
    if (vertical) Column(modifier.alpha(a), verticalArrangement = Arrangement.spacedBy(dot * 1.3f)) { dots() }
    else Row(modifier.alpha(a), horizontalArrangement = Arrangement.spacedBy(dot * 1.3f)) { dots() }
}

/** Screen margins: board `M`, widened symmetrically on the camera-hole axis (design §3.1). */
@Composable
internal fun screenPadding(w: Dp, h: Dp): PaddingValues {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val cut = WindowInsets.displayCutout
    val cx = with(density) { maxOf(cut.getLeft(this, dir), cut.getRight(this, dir)).toDp() }
    val cy = with(density) { maxOf(cut.getTop(this), cut.getBottom(this)).toDp() }
    val m = Grid.margin(min(w, h))
    return PaddingValues(horizontal = if (cx > 0.dp) max(m, cx + 8.dp) else m, vertical = if (cy > 0.dp) max(m, cy + 8.dp) else m)
}

/** iPhone time format: 12 h without leading zero and without AM/PM; 24 h "07:05". */
internal fun clockText(t: LocalDateTime, use24h: Boolean): String =
    if (use24h) "%02d:%02d".format(t.hour, t.minute) else "%d:%02d".format((t.hour + 11) % 12 + 1, t.minute)
