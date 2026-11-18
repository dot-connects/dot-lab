package kr.dot.sb.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Background
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import kr.dot.sb.ui.board.BoardGrid
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.cards.Ic
import kr.dot.sb.ui.cards.IconView
import kr.dot.sb.ui.cards.Txt
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.clock.ClockRegistry
import kr.dot.sb.ui.screens.Layer
import kr.dot.sb.ui.screens.MoodPanel
import kr.dot.sb.ui.screens.Overlays
import kr.dot.sb.ui.screens.TopPanel
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.Photo
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.state.TimerMode
import kr.dot.sb.ui.state.TimerState
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalClockActive
import kr.dot.sb.ui.theme.LocalInk
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.ProvideTheme
import kr.dot.sb.ui.theme.clockStyle
import kr.dot.sb.ui.theme.themedSurface

/** A fixed frame for screenshot tests; null runs the scene only while expanded and STARTED. */
internal val LocalTipDemoProgress = compositionLocalOf<Float?> { null }

private fun duration(tip: Tip) = if (tip == Tip.CLOCK_SWIPE || tip == Tip.TIMER_HOLD) 6f else 4.2f
private fun ramp(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun ease(t: Float, from: Float, to: Float): Float = ramp(t, from, to).let { it * it * (3 - 2 * it) }
private fun visible(t: Float, enter: Float, leave: Float) = ramp(t, enter, enter + 0.12f) * (1f - ramp(t, leave, leave + 0.2f))

@Composable
private fun sceneProgress(tip: Tip, active: Boolean): Float {
    val fixed = LocalTipDemoProgress.current
    var progress by remember(tip) { mutableFloatStateOf(0f) }
    val life = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tip, active, fixed, life) {
        if (active && fixed == null) life.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val start = System.nanoTime()
            progress = 0f
            while (true) {
                delay(32)
                progress = ((System.nanoTime() - start) / 1_000_000_000.0 % duration(tip)).toFloat() / duration(tip)
            }
        }
    }
    return fixed?.coerceIn(0f, 1f) ?: progress
}

@Composable
internal fun TipDemo(tip: Tip, config: AppConfig, active: Boolean, modifier: Modifier = Modifier) {
    val progress = sceneProgress(tip, active)
    val window = LocalConfiguration.current
    val device = if (tip == Tip.TENT) PreviewDevice.FOLD_COVER else
        windowDevice(LocalSettingsEnv.current.phone, window.screenWidthDp, window.screenHeightDp).first
    val state = remember(config) { Sample.state(config) }
    val faces = remember(config.myClocks) {
        ClockRegistry.pages(config.myClocks).takeIf { it.size > 1 } ?: listOf(FaceId.DIGITAL, FaceId.FLIP, FaceId.ANALOG)
    }
    ProvideTheme(config) {
        val ink = Ink.of(0f, null, LocalTheme.current)
        // Sample clocks never tick independently; the demo owns every animated position.
        CompositionLocalProvider(LocalInk provides ink, LocalClockActive provides false) {
            PreviewFrame(device.w, device.h, modifier, radius = 18.dp) {
                Box(Modifier.fillMaxSize().clipToBounds().clearAndSetSemantics { contentDescription = "${tip.title} 미리보기" }) {
                    Scene(tip, config, state, faces, device, progress * duration(tip))
                    // TopPanel contains a real volume slider. A demo must never send touches to live controls.
                    Box(Modifier.matchParentSize().pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                        }
                    })
                }
            }
        }
    }
}

@Composable
private fun BoxScope.Scene(tip: Tip, config: AppConfig, state: StandbyState, faces: List<FaceId>, device: PreviewDevice, t: Float) {
    val w = device.w.toFloat()
    val h = device.h.toFloat()
    val face = faces.first()
    when (tip) {
        Tip.PULL_DOWN, Tip.TENT -> {
            if (tip == Tip.TENT) Box(Modifier.fillMaxSize().background(Color.Black))
            DemoClock(face, config, state, black = tip == Tip.TENT)
            if (tip == Tip.TENT) Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.radialGradient(listOf(Color(config.mood.color).copy(alpha = 0.4f), Color.Transparent), center = Offset(size.width / 2, size.height), radius = size.width * 0.65f))
            }
            val panel = ease(t, 0.3f, 1.1f) * (1f - ease(t, 2.6f, 3.1f))
            if (panel > 0f) Box(Modifier.fillMaxSize().alpha(panel)) {
                TopPanel(
                    0.65f, {}, {}, {}, state, config, w.dp, h.dp,
                    Modifier.align(Alignment.TopCenter).graphicsLayer { translationY = -(1f - panel) * h.dp.toPx() },
                    extra = { if (tip == Tip.TENT) MoodPanel(config.mood, {}) },
                )
            }
            Touch(0.5f, 0.06f + 0.62f * ease(t, 0.3f, 1.1f), visible(t, 0.12f, 1.2f))
        }
        Tip.CLOCK_SWIPE -> {
            val step = (t / 2f).toInt().coerceAtMost(2)
            val local = t - step * 2f
            val move = ease(local, 0.35f, 0.85f)
            VerticalSwap(move) { next -> DemoClock(faces[(step + next) % faces.size], config, state) }
            Dots(faces.size, (step + if (move >= 0.5f) 1 else 0) % faces.size, Modifier.align(Alignment.CenterEnd).padding(end = 18.dp))
            Touch(0.5f, 0.77f - 0.52f * move, visible(local, 0.12f, 0.9f))
        }
        Tip.CLOCK_HOLD -> {
            DemoClock(face, config, state)
            val sheet = ease(t, 1.15f, 1.45f) * (1f - ease(t, 2.8f, 3.15f))
            if (sheet > 0f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f * sheet)))
                val ink = LocalInk.current
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.8f).height((h * 0.45f).dp)
                        .graphicsLayer { translationY = (1f - sheet) * size.height }
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(ink.bg).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Txt(face.ko, 24.dp, ink.ink, weight = 700)
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        listOf(ink.acc, ink.green, ink.blue, ink.ink).forEach { c -> Box(Modifier.size(34.dp).clip(CircleShape).background(c)) }
                    }
                }
            }
            Touch(0.5f, 0.5f, visible(t, 0.2f, 1.18f), hold = ramp(t, 0.3f, 1.1f))
        }
        Tip.PHOTOS_SWIPE -> PhotoDemo(config, state, device, t)
        Tip.BOARD_HOLD -> {
            BoardView(widgetsBoard(config), state, config, StandbyActions.NONE, Modifier.fillMaxSize())
            val show = ease(t, 1.1f, 1.4f) * (1f - ease(t, 2.7f, 3.1f))
            if (show > 0f) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f * show)))
                Box(Modifier.align(Alignment.Center).alpha(show).clip(RoundedCornerShape(24.dp)).background(LocalInk.current.bg).padding(horizontal = 42.dp, vertical = 28.dp)) {
                    Txt("페이지 설정", 26.dp, LocalInk.current.ink, weight = 700)
                }
            }
            Touch(0.5f, 0.5f, visible(t, 0.2f, 1.18f), hold = ramp(t, 0.3f, 1.1f))
        }
        Tip.BANNER_UP -> {
            DemoClock(face, config, state)
            val enter = ease(t, 0.15f, 0.5f)
            val swipe = ease(t, 1.4f, 1.95f)
            if (t < 2.2f) Box(Modifier.fillMaxSize().graphicsLayer { translationY = -(1f - enter + swipe) * 100.dp.toPx() }.alpha(1f - swipe)) {
                // Same sample as AlertsScreen; Overlays keeps the banner floating above the full-size clock.
                val at = state.now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val sample = BigNotification("pv", "카카오톡", "엄마", "저녁 먹었어?", null, plateColor = 0xFFF5D000, postedAtEpochMs = at)
                Overlays(state.copy(notification = sample), config.copy(showNotifications = true, previewOnTapOnly = false), StandbyActions.NONE, Layer.NONE, false, {})
            }
            Touch(0.5f, 48f / h - 0.3f * swipe, visible(t, 1.15f, 1.95f))
        }
        Tip.SIDE_KEY -> {
            DemoClock(face, config, state)
            val dark = ease(t, 0.8f, 1.2f) * (1f - ease(t, 3f, 3.5f))
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dark)))
            val lock = visible(t, 1.65f, 2.8f)
            Column(Modifier.align(Alignment.Center).alpha(lock), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                IconView(Ic.LOCK, Color.White, Modifier.size(42.dp))
                Txt("잠금 화면", 24.dp, Color.White, weight = 600)
            }
            val pulse = visible(t, 0.35f, 1f)
            Canvas(Modifier.fillMaxSize()) {
                val x = size.width - 5.dp.toPx()
                drawLine(Color.White.copy(alpha = 0.3f + pulse * 0.7f), Offset(x, size.height * 0.32f), Offset(x, size.height * 0.52f), (4f + pulse * 3f).dp.toPx(), StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = 0.15f * pulse), 30.dp.toPx(), Offset(x, size.height * 0.42f))
            }
        }
        Tip.TIMER_HOLD -> TimerDemo(config, state, device, t)
        Tip.NOTIF_SWIPE -> NotificationDemo(config, state, device, t)
        Tip.STACK_SWIPE -> StackDemo(config, state, device, t)
        Tip.CLOCK_CARD -> {
            val arrows = visible(t, 0.75f, 3.4f)
            val move = ease(t, 1.9f, 2.4f)
            Box(Modifier.fillMaxSize().clipToBounds()) {
                repeat(2) { i ->
                    Box(Modifier.fillMaxSize().graphicsLayer { translationX = (i - move) * size.width }) {
                        BoardView(demoBoard(CardType.CLOCK), state, config.copy(myClocks = faces, clockCardFace = faces[i % faces.size]), StandbyActions.NONE, Modifier.fillMaxSize())
                    }
                }
            }
            DemoArrows(arrows)
            Touch(0.5f, 0.5f, visible(t, 0.25f, 0.75f))
            Touch(0.9f, 0.5f, visible(t, 1.4f, 2.0f), hold = ramp(t, 1.6f, 1.9f))
        }
    }
}

private fun demoBoard(card: CardType) = Board("tip", "", Layout.ONE, listOf(listOf(card)))

/** The clock card's side arrows (FaceArrows look: dim round plates with a chevron), faded by [alpha]. */
@Composable
private fun BoxScope.DemoArrows(alpha: Float) {
    if (alpha <= 0f) return
    val ink = LocalInk.current
    listOf(Alignment.CenterStart to "‹", Alignment.CenterEnd to "›").forEach { (side, c) ->
        Box(
            Modifier.align(side).padding(horizontal = 28.dp).size(56.dp).graphicsLayer { this.alpha = alpha }
                .clip(androidx.compose.foundation.shape.CircleShape).background(ink.ink.copy(alpha = 0.22f)),
            Alignment.Center,
        ) { Txt(c, 34.dp, ink.ink, weight = 700) }
    }
}

@Composable
private fun DemoClock(face: FaceId, config: AppConfig, state: StandbyState, black: Boolean = false) {
    val style = config.clockStyle(face).let { if (black) it.copy(background = Background.BLACK) else it }
    ClockFace(face, style, state, Modifier.fillMaxSize(), analog = config.analogStyle)
}

/** Both pages stay mounted, so their own remembered state never adds a second animation clock. */
@Composable
private fun VerticalSwap(move: Float, content: @Composable BoxScope.(Int) -> Unit) {
    Box(Modifier.fillMaxSize().clipToBounds()) {
        repeat(2) { i ->
            Box(Modifier.fillMaxSize().graphicsLayer { translationY = (i - move) * size.height }) { content(i) }
        }
    }
}

@Composable
private fun Dots(count: Int, selected: Int, modifier: Modifier) {
    val ink = LocalInk.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i -> Box(Modifier.size(7.dp).clip(CircleShape).background(if (i == selected) ink.acc else ink.ink3)) }
    }
}

@Composable
private fun BoxScope.PhotoDemo(config: AppConfig, state: StandbyState, device: PreviewDevice, t: Float) {
    val photos = remember(state) {
        state.photos.orEmpty().take(1) + listOfNotNull(state.media?.art?.let { Photo(it, "바다", "2026년 8월 16일") })
    }
    val slide = remember(config) { pageSlides(config, include = 1).first { it.name == "사진" } }
    val move = ease(t, 0.5f, 1.15f)
    VerticalSwap(move) { i ->
        Box(Modifier.fillMaxSize().alpha(if (i == 0) 1f - move else move)) {
            slide.draw(this, PreviewLook(device, false, 0f, state.copy(photos = listOf(photos[i % photos.size]))))
        }
    }
    val folders = remember(config.photoFolders) { folderNames(config.photoFolders) }
    Box(Modifier.align(Alignment.CenterEnd).padding(end = 26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 18.dp, vertical = 10.dp)) {
        Txt(folders[if (move < 0.5f) 0 else 1], 18.dp, Color.White, weight = 600)
    }
    Touch(0.5f, 0.77f - 0.52f * move, visible(t, 0.2f, 1.2f))
}

private fun folderNames(folders: List<String>): List<String> = if (folders.isEmpty()) listOf("여행", "바다") else
    List(2) { i -> kr.dot.sb.data.PhotoIndex.fallbackName(folders[i % folders.size]) }

@Composable
private fun BoxScope.TimerDemo(config: AppConfig, state: StandbyState, device: PreviewDevice, t: Float) {
    val second = t >= 3f
    val local = t % 3f
    val flip = ease(local, 1.1f, 1.3f)
    val rest = if (second) 1f - flip else flip
    val bump = 1f - 0.035f * kotlin.math.sin(flip * Math.PI).toFloat()
    repeat(2) { i ->
        val seconds = if (i == 0) 25 * 60 else 5 * 60
        val timer = TimerState(if (i == 0) TimerMode.FOCUS else TimerMode.BREAK, seconds, seconds)
        BoardView(demoBoard(CardType.TIMER), state.copy(timers = mapOf("tip-0" to timer)), config, StandbyActions.NONE,
            Modifier.fillMaxSize().graphicsLayer { scaleX = bump; scaleY = bump; alpha = if (i == 0) 1f - rest else rest })
    }
    // The real timer's wide layout puts its dial left; square screens centre it above the controls.
    val x = if (device.w.toFloat() / device.h >= 1.35f) 0.3f else 0.5f
    Touch(x, 0.5f, visible(local, 0.2f, 1.18f), hold = ramp(local, 0.3f, 1.1f), ripple = ramp(local, 1.1f, 1.65f))
}

@Composable
private fun BoxScope.StackDemo(config: AppConfig, state: StandbyState, device: PreviewDevice, t: Float) {
    val widgets = widgetsBoard(config)
    val board = widgets.copy(slots = listOf(listOf(CardType.CLOCK, CardType.WEATHER)) + widgets.slots.drop(1))
    val left = BoardGrid.slots(board.layout, device.w.toFloat(), device.h.toFloat()).first()
    val move = ease(t, 0.5f, 1.1f)
    // Keep the right slot stationary; clip two real boards to the left slot to animate the stack without a live pager.
    BoardView(board, state, config, StandbyActions.NONE, Modifier.fillMaxSize())
    Box(Modifier.fillMaxSize().drawWithContent {
        clipRect(left.x.dp.toPx(), left.y.dp.toPx(), (left.x + left.w).dp.toPx(), (left.y + left.h).dp.toPx()) { this@drawWithContent.drawContent() }
    }) {
        Box(Modifier.fillMaxSize().background(LocalInk.current.bg))
        repeat(2) { i ->
            BoardView(board.copy(slots = listOf(listOf(if (i == 0) CardType.CLOCK else CardType.WEATHER)) + board.slots.drop(1)), state, config, StandbyActions.NONE,
                Modifier.fillMaxSize().graphicsLayer { translationY = (i - move) * left.h.dp.toPx() })
        }
    }
    Dots(2, if (move < 0.5f) 0 else 1, Modifier.align(Alignment.CenterStart).padding(start = 7.dp))
    Touch((left.x + left.w / 2) / device.w, (left.y + left.h * (0.77f - 0.52f * move)) / device.h, visible(t, 0.2f, 1.15f))
}

@Composable
private fun BoxScope.NotificationDemo(config: AppConfig, state: StandbyState, device: PreviewDevice, t: Float) {
    val board = demoBoard(CardType.NOTIF)
    BoardView(board, state, config, StandbyActions.NONE, Modifier.fillMaxSize())
    val r = BoardGrid.slots(Layout.ONE, device.w.toFloat(), device.h.toFloat()).first()
    val ink = LocalInk.current
    val theme = LocalTheme.current
    val armed = t >= 0.75f
    val swipe = ease(t, 1.5f, 2.2f)
    val close = ease(t, 2.2f, 2.5f)
    val rowH = ((r.h - 80f) / 3).coerceIn(48f, 80f)
    val shape = RoundedCornerShape(if (theme.tokens.radiusRatio == 0f) 0.dp else 22.dp)
    // The live notification row owns gesture state privately. Use its themed plate and sample content for the demo.
    Column(Modifier.offset(r.x.dp, r.y.dp).size(r.w.dp, r.h.dp).clip(shape).background(ink.bg).themedSurface(theme, ink, shape).padding(14.dp)) {
        Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("알림 ${if (close < 1f) 3 else 2}개", 20.dp, ink.ink, Modifier.weight(1f), weight = 600)
            Box(Modifier.clip(RoundedCornerShape(50)).background(if (armed) ink.acc else Color.Transparent).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Txt(if (armed) "지우는 중" else "밀어서 지우기", 16.dp, if (armed) ink.on(ink.acc) else ink.accText, weight = 600)
            }
        }
        state.notifications.take(3).forEachIndexed { i, n ->
            Box(Modifier.fillMaxWidth().height((rowH * if (i == 0) 1f - close else 1f).dp).clipToBounds()) {
                Row(
                    Modifier.fillMaxWidth().height(rowH.dp).graphicsLayer { if (i == 0) { translationX = -swipe * size.width; alpha = 1f - swipe } },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(26)).background(Color(n.plateColor)), Alignment.Center) {
                        Txt(n.appLabel.take(1), 16.dp, ink.on(Color(n.plateColor)), weight = 700)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Txt(n.title, 18.dp, ink.ink, weight = 700, lines = 1)
                        Txt(n.text, 14.dp, ink.ink2, lines = 1)
                    }
                    Txt(n.appLabel, 14.dp, ink.ink2)
                }
            }
        }
    }
    Touch((r.x + r.w - 80f) / device.w, (r.y + 34f) / device.h, visible(t, 0.25f, 0.75f), hold = ramp(t, 0.45f, 0.75f))
    Touch(0.72f - 0.62f * swipe, (r.y + 14f + 40f + rowH / 2) / device.h, visible(t, 1.3f, 2.25f))
}

/** Style B: translucent contact circle, thin white ring, soft halo; no finger silhouette. */
@Composable
private fun Touch(x: Float, y: Float, opacity: Float, hold: Float = 0f, ripple: Float = 0f) {
    val accent = LocalInk.current.acc
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(x * size.width, y * size.height)
        val radius = (size.minDimension * 0.045f).coerceIn(16.dp.toPx(), 24.dp.toPx())
        if (ripple > 0f && ripple < 1f) drawCircle(Color.White.copy(alpha = (1f - ripple) * 0.3f), radius * (1f + ripple * 3f), center)
        if (opacity > 0f) {
            drawCircle(Color.White.copy(alpha = 0.1f * opacity), radius * 1.4f, center)
            drawCircle(Color.White.copy(alpha = 0.32f * opacity), radius, center)
            drawCircle(Color.White.copy(alpha = 0.85f * opacity), radius, center, style = Stroke(1.5.dp.toPx()))
            if (hold > 0f) {
                val ring = radius * 1.65f
                drawArc(accent.copy(alpha = opacity), -90f, 360f * hold, false, center - Offset(ring, ring), Size(ring * 2, ring * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
}
