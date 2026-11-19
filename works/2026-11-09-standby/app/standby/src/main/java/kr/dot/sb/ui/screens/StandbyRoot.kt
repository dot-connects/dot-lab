package kr.dot.sb.ui.screens

import kr.dot.sb.settings.Pages
import kr.dot.sb.ui.theme.BURN_IN_BLEED
import kr.dot.sb.ui.theme.ThemeBackground
import kr.dot.sb.ui.theme.burnInBleed
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
import kr.dot.sb.ui.cards.tap
import kr.dot.sb.ui.cards.LocalNotifOpen
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
import kr.dot.sb.data.MoodLatch
import kr.dot.sb.data.MoodPhase
import kr.dot.sb.data.MoodTimer
import kr.dot.sb.platform.Brightness
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.board.LastCard
import kr.dot.sb.settings.MyClocks
import androidx.compose.ui.platform.LocalContext
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalInk
import kr.dot.sb.ui.theme.LocalClockActive
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.ProvideTheme
import kr.dot.sb.ui.theme.Motion
import kr.dot.sb.ui.theme.Themes
import java.time.LocalDateTime
import kotlin.math.abs

/** The pull-down panel closes 4 s after the last touch (spec-r9 §1). */
internal const val BAR_HIDE_MS = 4_000L

/** Widgets, Photos, Clocks come first (spec §1c); user boards follow. */
internal const val FIXED_PAGES = 3

/** Full-screen layer on top of the pages, by interrupt priority: alarm > live > now playing. A new notification is a banner, not a layer (spec-r9 §1). */
internal enum class Layer { NONE, ALARM, LIVE, NOW_PLAYING }

internal fun topLayer(state: StandbyState, nowPlayingOpen: Boolean): Layer = when {
    state.alarm != null -> Layer.ALARM
    state.live != null && state.liveExpanded -> Layer.LIVE
    nowPlayingOpen && state.media != null -> Layer.NOW_PLAYING
    else -> Layer.NONE
}

/** The page whose board holds a notification card: the current one if it does, else the first (widgets page included). */
internal fun notifPage(config: AppConfig, current: Int): Int? {
    val pages = listOf(0 to listOf(config.widgetLeft, config.widgetRight)) + config.boards.mapIndexed { i, b -> FIXED_PAGES + i to b.slots }
    val hits = pages.filter { (_, slots) -> slots.any { stack -> stack.any { it.isNotif } } }.map { it.first }
        .filter { kr.dot.sb.settings.Pages.position(config, it) != null } // a page switched off is no target
    return if (current in hits) current else hits.firstOrNull()
}

/** Test hook: start on clock face [face] (null = the first of `myClocks`), in clock edit mode (optionally with the colour sheet), or with Now Playing open. */
internal data class RootStart(val edit: Boolean = false, val sheet: Boolean = false, val nowPlaying: Boolean = false, val face: FaceId? = null, val bar: Boolean = false)

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
) = ProvideTheme(config, state.now.toLocalTime()) {
    val act by rememberUpdatedState(actions)
    // r10: only the pages that are on, in the full order; pages are passed around by their full-order index
    val pages by rememberUpdatedState(Pages.visible(config))
    // Hoisted above the display-off branch so waking returns to the same page and clock face.
    val pager = rememberPagerState(Pages.start(config)) { pages.size }
    // plan-r15 E10: with rotation on the clocks page reopens on the face it showed last (settings previews pass NONE)
    val ctx = LocalContext.current
    val remembers = actions !== StandbyActions.NONE
    val clockPage = remember {
        mutableStateOf(start.face ?: if (remembers) LastCard.face(LastCard.read(ctx, LastCard.CLOCK_FACE), MyClocks.pages(config)) else null)
    }
    LaunchedEffect(clockPage.value) {
        if (remembers) clockPage.value?.let { LastCard.write(ctx, LastCard.CLOCK_FACE, it.name) }
    }
    var editing by remember { mutableStateOf(start.edit) }
    var npOpen by remember { mutableStateOf(start.nowPlaying) }
    // cards ask for Now Playing (and the banner for the notification card) through the actions; the root owns both
    val notifOpen = remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val cfg by rememberUpdatedState(config)
    val st by rememberUpdatedState(state)
    val boardActions = remember(actions) {
        object : StandbyActions by actions {
            override fun openNowPlaying() { npOpen = true }
            override fun openNotifCard(key: String) {
                val p = notifPage(cfg, pages.getOrNull(pager.currentPage)?.index ?: -1)?.let { Pages.position(cfg, it) } ?: return actions.notificationTap(key)
                actions.notificationDismiss(key)
                npOpen = false
                if (st.liveExpanded) actions.liveToggle()
                editing = false
                notifOpen.value = true
                scope.launch { pager.scrollToPage(p) }
            }
        }
    }
    val edits = remember { mutableStateMapOf<FaceId, ClockStyle>() }
    // unfolded + 무드등: the dream that survives the unfold turns into the glow in place (spec M1); a tap goes back to the board
    var moodOff by remember { mutableStateOf(false) }
    LaunchedEffect(state.unfolded) { if (!state.unfolded) moodOff = false }
    // auto-off ran out ([MoodLatch], reset by the holder on fold): no mood light until folded again
    val mood = state.unfolded && config.onUnfold == UnfoldAction.MOOD_LIGHT && !moodOff && !editing && !MoodLatch.done
    LaunchedEffect(mood) { act.moodShowing(mood) }

    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { p -> pages.getOrNull(p)?.let { act.pageChanged(it.index) } } }
    LaunchedEffect(state.unfolded, config.onUnfold) {
        if (state.unfolded && config.onUnfold == UnfoldAction.BOOK_BOARD) {
            val i = config.boards.indexOfFirst { it.id == "book" }
            if (i >= 0) Pages.position(config, FIXED_PAGES + i)?.let { pager.scrollToPage(it) }
        }
    }
    if (state.media == null && npOpen) npOpen = false

    // Pull-down panel: swipe down from the top edge opens it, 4 s after the last touch it closes (brightness = day value or night cap).
    val nightNow = state.night >= 0.5f
    var barOn by remember { mutableStateOf(start.bar) }
    var level by remember { mutableFloatStateOf(Brightness.shown(nightNow, config)) }
    var barPressed by remember { mutableStateOf(false) }
    var barTick by remember { mutableIntStateOf(0) }
    val canBar by rememberUpdatedState(!state.displayOff && !editing && topLayer(state, npOpen) == Layer.NONE)
    // the swipe that opens the bar keeps going as a slider: sideways travel moves the level from where it was (r8)
    var slideBase by remember { mutableFloatStateOf(0f) }
    // plan-r15 E8: over the mood light the bar is the mood light's brightness (the holder stores it there)
    val barOpen by rememberUpdatedState({ level = if (mood) config.mood.bright else Brightness.shown(nightNow, config); slideBase = level; barOn = true; barPressed = true; barTick++ })
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
        // operator 2026-11-18: the accent is the theme's (with its 강조 색 edit); a clock's own colour stays on that clock
        val th = LocalTheme.current
        val ink = remember(state.night, th) { Ink.of(state.night, null, th) }
        val layer = topLayer(state, npOpen)
        val widgets = remember(config.widgetLeft, config.widgetRight) {
            Board("widgets", "기본 페이지", Layout.TWO, listOf(config.widgetLeft, config.widgetRight))
        }
        // plan-r15 D2: a banner or live pill over a short screen shrinks the pages (pinned at the bottom) below it
        val band = if (h > w) 0.dp else overlayBand(state, config, layer, editing, w, h)
        val fit by animateFloatAsState(((h - band - 4.dp) / h).coerceAtMost(1f), tween(Motion.LAYER_FADE_MS), label = "band")
        CompositionLocalProvider(LocalInk provides ink, LocalNotifOpen provides notifOpen) {
            Box(Modifier.fillMaxSize().offset(state.shiftX.dp, state.shiftY.dp)) {
                // sleep wins over mood: at DONE the holder sleeps the display and a touch wakes to the board
                if (mood) MoodLight(config.mood, ink, config.moodAutoOffMin, state.alarm != null, { moodOff = true; act.moodDone() }) { moodOff = true }
                // the pager clips its bounds: it takes the burn-in bleed itself, each page is padded back in
                else HorizontalPager(
                    pager,
                    Modifier.fillMaxSize().graphicsLayer {
                        if (fit < 1f) { scaleX = fit; scaleY = fit; transformOrigin = TransformOrigin(0.5f, 1f) }
                    }.burnInBleed(),
                    userScrollEnabled = !editing,
                    flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 400f)),
                    key = { pages.getOrNull(it)?.let { p -> if (p.board == null) "fixed-${p.index}" else p.board.id } ?: "p$it" },
                ) { pos ->
                    val ref = pages.getOrNull(pos)
                    // r11: a page under an opaque overlay or off screen lets its clocks stop ticking
                    // the incoming page of a swipe ticks too ([targetPage]), so its clock is live as it slides in
                    val active = layer == Layer.NONE && (pager.currentPage == pos || pager.targetPage == pos)
                    CompositionLocalProvider(LocalClockActive provides active) { Box(Modifier.fillMaxSize().padding(BURN_IN_BLEED)) { when (ref?.index) {
                        0 -> BoardPage(widgets, state, config, boardActions)
                        1 -> PhotosPage(state, config, actions, active = pager.settledPage == pos && layer == Layer.NONE)
                        2 -> ClocksPage(
                            state, config, clockPage, editing, { editing = it }, start.sheet,
                            styleOf = { f -> edits[f] ?: config.clockStyle(f) },
                            onPick = { f, s -> edits[f] = s; onStyleChange(f, s) },
                            onPickPhotos = { actions.openSettings("photos") },
                        )
                        null -> {}
                        else -> ref.board?.let { BoardPage(it, state, config, boardActions) }
                    } } }
                }
                if (!editing && !mood) {
                    val s = min(w, h)
                    AutoHideDots(
                        pages.size, pager.currentPage, pager.isScrollInProgress, vertical = false,
                        dot = max(5.dp, s * 0.013f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Grid.margin(s) / 2 - max(5.dp, s * 0.013f) / 2),
                    )
                }
                Overlays(state, config, boardActions, layer, editing, onNowPlaying = { npOpen = it })
                // plan-r15 F10: one tip per session for the page on screen
                // operator 2026-11-19: 지금 보기 has one day/night button at the top right
                if (state.showNow && !editing && !mood && layer == Layer.NONE) {
                    val s = min(w, h)
                    val d = max(40.dp, s * 0.1f)
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(Grid.margin(s) / 2).size(d).clip(CircleShape)
                            .background(ink.ink.copy(alpha = 0.16f)).tap { act.toggleNight() }
                            .semantics { contentDescription = if (nightNow) "낮 화면으로" else "밤 화면으로" },
                        Alignment.Center,
                    ) { kr.dot.sb.ui.cards.IconView(if (nightNow) kr.dot.sb.ui.cards.Ic.SUN else kr.dot.sb.ui.cards.Ic.MOON, ink.ink2, Modifier.size(d * 0.55f)) }
                }
                TipOverlay(config, pages.getOrNull(pager.settledPage)?.let { p -> kr.dot.sb.settings.TipContext(p.index, (p.board ?: widgets.takeIf { p.index == 0 })?.slots.orEmpty()) }, editing || mood || npOpen || layer != Layer.NONE) { act.tipSeen(it) }
            }
            val a by animateFloatAsState(if (barOn) 1f else 0f, tween(200), label = "bar")
            if (a > 0f) Box(Modifier.fillMaxSize().alpha(a)) {
                TopPanel(
                    level, { v -> level = v; act.setBrightness(v) }, { barPressed = it; barTick++ }, { look -> act.setLook(look) },
                    state, config, w, h, Modifier.align(Alignment.TopCenter), onClose = { barOn = false },
                )
            }
            // keep the system back-swipe off the board pager's edges (spec E4/R7); the system honours ~200 dp per edge
            Box(Modifier.align(Alignment.CenterStart).size(24.dp, 200.dp).systemGestureExclusion())
            Box(Modifier.align(Alignment.CenterEnd).size(24.dp, 200.dp).systemGestureExclusion())
        }
    }
}

/**
 * Full-screen light (spec M1), one flat colour over the whole screen (plan-r15 E8, no gradient) with the picked effect
 * ([MoodFx], 20 frames/s only while an effect runs; 그대로 is static, so it costs nothing). Auto-off (r10,
 * [MoodTimer]): after [minutes] (0 = never) it fades to black over the last minute, then [onDone]. A tap during the fade
 * restarts the timer, otherwise it closes ([onTap]); a ringing [alarm] cancels the timer.
 */
@Composable
private fun MoodLight(m: kr.dot.sb.model.Mood, ink: Ink, minutes: Int, alarm: Boolean, onDone: () -> Unit, onTap: () -> Unit) {
    var fx by remember { mutableStateOf(m.color to 1f) }
    LaunchedEffect(m) {
        fx = m.color to 1f
        if (m.effect == kr.dot.sb.model.MoodEffect.STILL) return@LaunchedEffect
        val t0 = android.os.SystemClock.elapsedRealtime()
        while (true) { fx = kr.dot.sb.data.MoodFx.at(m, android.os.SystemClock.elapsedRealtime() - t0); delay(kr.dot.sb.data.MoodFx.FRAME_MS) }
    }
    val flat = ink.mapped(Color(fx.first or 0xFF000000L))
    var from by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    var k by remember { mutableFloatStateOf(1f) }
    var cancelled by remember { mutableStateOf(false) }
    LaunchedEffect(alarm) { if (alarm) cancelled = true }
    val done by rememberUpdatedState(onDone)
    LaunchedEffect(from, minutes, cancelled) {
        val m = if (cancelled) 0 else minutes
        while (true) {
            val el = android.os.SystemClock.elapsedRealtime() - from
            when (val p = MoodTimer.phase(el, m)) {
                MoodPhase.On -> { k = 1f; if (m <= 0) return@LaunchedEffect; delay(m * 60_000L - MoodTimer.FADE_MS - el) }
                is MoodPhase.Fade -> { k = p.alpha; delay(100) }
                MoodPhase.Done -> { k = 0f; done(); return@LaunchedEffect }
            }
        }
    }
    Box(
        Modifier.fillMaxSize().burnInBleed()
            .background(Color.Black) // literal-ok: the mood light fades to black, whatever the theme
            .background(flat.copy(alpha = k * fx.second))
            .tap { if (k < 1f) from = android.os.SystemClock.elapsedRealtime() else onTap() },
    )
}

/**
 * plan-r15 E7: the clock on the fold's outer screen while the inner screen is the mood light (tent). Shows `myClocks[0]`
 * in night colours; the top-edge pull opens the usual panel (brightness of this screen, volume, theme / 내 테마) with the
 * mood light's own controls below (E8). [level] / [onLevel] = this screen's brightness (bar position).
 */
@Composable
fun TentClock(
    state: StandbyState, config: AppConfig, level: Float, onLevel: (Float) -> Unit,
    onLook: (kr.dot.sb.ui.theme.Look) -> Unit, onMood: (kr.dot.sb.model.Mood) -> Unit, startBar: Boolean = false, onTipSeen: (String) -> Unit = {},
) = ProvideTheme(config, state.now.toLocalTime()) {
    val night = state.copy(night = 1f)
    var barOn by remember { mutableStateOf(startBar) }
    var barPressed by remember { mutableStateOf(false) }
    var barTick by remember { mutableIntStateOf(0) }
    var slideBase by remember { mutableFloatStateOf(0f) }
    val lvl by rememberUpdatedState(level)
    val set by rememberUpdatedState(onLevel)
    LaunchedEffect(barOn, barTick, barPressed) { if (barOn && !barPressed) { delay(BAR_HIDE_MS); barOn = false } }
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black) // literal-ok: night clock on the outer screen
            .pointerInput(Unit) {
                rootGestures({}, {}, { true }, { slideBase = lvl; barOn = true; barPressed = true; barTick++ }, { d -> set((slideBase + d).coerceIn(0f, 1f)) }, { barPressed = false; barTick++ })
            },
    ) {
        val th = LocalTheme.current
        val bw = maxWidth
        val bh = maxHeight
        val face = config.myClocks.firstOrNull() ?: FaceId.DIGITAL
        val ink = remember(th, config) { Ink.of(1f, null, th) }
        CompositionLocalProvider(LocalInk provides ink) {
            ThemeBackground(th, 1f)
            kr.dot.sb.ui.clock.ClockFace(face, config.clockStyle(face), night, Modifier.fillMaxSize().padding(BURN_IN_BLEED), analog = config.analogStyle)
            val a by animateFloatAsState(if (barOn) 1f else 0f, tween(200), label = "bar")
            if (a > 0f) Box(Modifier.fillMaxSize().alpha(a)) {
                TopPanel(
                    level, onLevel, { barPressed = it; barTick++ }, onLook, night, config, bw, bh, Modifier.align(Alignment.TopCenter),
                    extra = { MoodPanel(config.mood, onMood) }, onClose = { barOn = false },
                )
            }
            TipOverlay(config, kr.dot.sb.settings.TipContext(-1, tent = true), barOn, onTipSeen) // plan-r15 F10: first tent
        }
    }
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

/**
 * Screen margins: board `M` (or [base]: an overlay's own margin), widened symmetrically on the camera-hole axis to clear
 * the cutout (design §3.1). Without a cutout it is [base] all round.
 */
@Composable
internal fun screenPadding(w: Dp, h: Dp, base: Dp = Grid.margin(min(w, h))): PaddingValues {
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val cut = WindowInsets.displayCutout
    val cx = with(density) { maxOf(cut.getLeft(this, dir), cut.getRight(this, dir)).toDp() }
    val cy = with(density) { maxOf(cut.getTop(this), cut.getBottom(this)).toDp() }
    val m = base
    return PaddingValues(horizontal = if (cx > 0.dp) max(m, cx + 8.dp) else m, vertical = if (cy > 0.dp) max(m, cy + 8.dp) else m)
}

/** iPhone time format: 12 h without leading zero and without AM/PM; 24 h "07:05". */
internal fun clockText(t: LocalDateTime, use24h: Boolean): String =
    if (use24h) "%02d:%02d".format(t.hour, t.minute) else "%d:%02d".format((t.hour + 11) % 12 + 1, t.minute)
