package kr.dot.sb.ui.cards

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import kr.dot.sb.data.NotifGroup
import kr.dot.sb.data.notifGroups
import kr.dot.sb.ui.screens.ago
import kr.dot.sb.ui.state.NotifItem
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.themedSurface
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.floor

/*
 * The notification card (spec-r9 §1 "Notifications (stack + list merged)", mockup tab 알림). One body for all ten
 * themes. Closed (the usual look): one plate for the whole card, quiet rows without boxes, same app + conversation
 * merged with a count, overflow as overlapped icons + "+N", a dot + bold for anything under 10 minutes old. Layout by
 * [notifTier]. "내용 열기" or a row → open: one plate per notification (sender 18 + message 15), scrolls; "접기" or
 * 10 s without a touch closes it. r15 §3: sideways drags turn the page unless 밀어서 지우기 is on; then a row or plate
 * swiped left is removed from the phone (no "모두 지우기").
 */

private const val IDLE_CLOSE_MS = 10_000L

/** r15 §3: 밀어서 지우기 turns itself off after this long without a dismiss. */
internal const val SWIPE_IDLE_MS = 30_000L

/**
 * 밀어서 지우기 (r15 §3), transient UI state (never saved). Off: sideways drags on the card belong to the page, rows only
 * open. On: rows swipe left to clear and the card keeps every sideways drag. [uses] counts dismisses, so each one
 * restarts the [SWIPE_IDLE_MS] wait.
 */
@androidx.compose.runtime.Immutable
internal data class SwipeClear(val on: Boolean = false, val uses: Int = 0) {
    fun toggle(): SwipeClear = if (on) SwipeClear() else SwipeClear(on = true)
    fun dismissed(): SwipeClear = if (on) copy(uses = uses + 1) else this
    /** Idle timeout, screen off, or the card left the screen. */
    fun off(): SwipeClear = SwipeClear()
}

/** Set by the root when the new-notification banner is tapped; the first notification card composed opens and clears it. */
internal val LocalNotifOpen = compositionLocalOf<MutableState<Boolean>?> { null }
private const val FRESH_MS = 10 * 60_000L
/** Message line height / size. */
private const val LEADING = 1.35f

/** Light or dark ink for a mark on [c] (letter plates, badges); the page colour at night (candle plates are dim). */
private fun Env.on(c: Color): Color {
    val (dark, light) = if (ink.ink.luminance() > ink.bg.luminance()) ink.bg to ink.ink else ink.ink to ink.bg
    return lerp(if (c.luminance() > 0.5f) dark else light, ink.bg, ink.night)
}

/** App icon: the colour icon on a squircle (letter plate without one); mono themes a ring with the app's own glyph. */
@Composable
private fun NotifIcon(e: Env, n: NotifItem, d: Dp) {
    val letter = n.appLabel.take(1)
    if (e.t.tokens.monoIcons) {
        Box(Modifier.size(d).border(max(1.dp, d * 0.045f), e.ink.ink2, CircleShape), contentAlignment = Alignment.Center) {
            val g = n.glyph
            if (g != null) Image(g, null, Modifier.size(d * 0.54f), contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(e.ink.ink))
            else Txt(letter, d * 0.46f, e.ink.ink, weight = 600)
        }
        return
    }
    val shape = RoundedCornerShape(26) // percent: the app-icon squircle
    val img = n.icon
    if (img != null) {
        Image(img, null, Modifier.size(d).clip(shape), contentScale = ContentScale.Crop, colorFilter = nightFilter(e.ink.night))
        return
    }
    // the app's own colour, dimmed at night; no contrast floor (Ink.mapped would turn KakaoTalk yellow into ink on a
    // light page): the letter on it carries the meaning
    val plate = lerp(Color(n.plateColor), Ink.NIGHT_DIM, e.ink.night)
    Box(Modifier.size(d).clip(shape).background(plate), contentAlignment = Alignment.Center) {
        Txt(letter, d * 0.48f, e.on(plate), weight = 700)
    }
}

/** Icon with the group's count on its corner (tier A). */
@Composable
private fun BadgedIcon(e: Env, g: NotifGroup, d: Dp) {
    Box {
        NotifIcon(e, g.head, d)
        if (g.count > 1) {
            val b = max(16.dp, d * 0.46f)
            val text = if (g.count > 99) "99+" else g.count.toString()
            Box(
                Modifier.align(Alignment.TopEnd).offset(x = b * 0.35f, y = -b * 0.3f)
                    .height(b).widthIn(min = b).clip(RoundedCornerShape(50)).background(e.ink.red).padding(horizontal = b * 0.22f),
                contentAlignment = Alignment.Center,
            ) { Txt(text, b * 0.62f, e.on(e.ink.red), weight = 700, num = true) }
        }
    }
}

/**
 * A row (closed) or plate (open). With 밀어서 지우기 off it is just [content] (taps only, sideways drags go to the page).
 * On: a 3 dp accent bar on its left, and a swipe left throws it out past a third of its width (or on a fling), else it
 * springs back; the strip it uncovers is the warn colour with "지우기". Rightwards it only gives a little. The offset is
 * read only in draw / graphics layers, so a drag recomposes nothing. A notification the app pins ([clearable] false)
 * follows the finger reluctantly and always comes back.
 */
@Composable
private fun SwipeRow(
    e: Env, key: String, on: Boolean, clearable: Boolean, onClear: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    if (!on) return content(modifier)
    var dx by remember(key) { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val fling = with(LocalDensity.current) { 800.dp.toPx() }
    val clear by rememberUpdatedState(onClear)
    val drag = rememberDraggableState { d -> dx += if (clearable && dx + d < 0f) d else d * 0.2f }
    val warn = e.ink.red
    val acc = e.ink.acc
    Box(modifier.onSizeChanged { width = it.width.coerceAtLeast(1) }) {
        // the uncovered strip: warn colour + "지우기", clipped to what the row has left
        Box(
            Modifier.matchParentSize().drawWithContent {
                if (dx < 0f) clipRect(left = size.width + dx) {
                    drawRect(warn)
                    this@drawWithContent.drawContent()
                }
            },
            contentAlignment = Alignment.CenterEnd,
        ) { Txt("지우기", e.meta, e.on(warn), Modifier.padding(end = 14.dp), weight = 800) }
        content(
            Modifier.fillMaxSize()
                .draggable(
                    drag, Orientation.Horizontal,
                    onDragStopped = { v ->
                        val w = width.toFloat()
                        val out = clearable && dx < 0f && (-dx > w / 3f || (v < -fling && -dx > w * 0.08f))
                        if (out) {
                            animate(dx, -w * 1.1f, animationSpec = tween(170)) { x, _ -> dx = x }
                            clear()
                        } else {
                            animate(dx, 0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) { x, _ -> dx = x }
                        }
                    },
                )
                .graphicsLayer {
                    translationX = dx
                    alpha = 1f - (abs(dx) / width).coerceIn(0f, 1f) * 0.8f
                }
                // one bar per row (rows touch: a full-height bar would run into the next one)
                .drawWithContent {
                    drawContent()
                    val bw = 3.dp.toPx()
                    drawRoundRect(acc, Offset(0f, size.height * 0.15f), androidx.compose.ui.geometry.Size(bw, size.height * 0.7f), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                },
        )
    }
}

/**
 * The 밀어서 지우기 switch (r15 §3): an outlined pill with "⇠ 밀어서 지우기" when off, filled with the accent when on;
 * [iconOnly] keeps just the arrow (small slots). The touch target is 48 dp tall and at least 48 wide.
 */
@Composable
private fun SwipeToggle(e: Env, on: Boolean, iconOnly: Boolean, onClick: () -> Unit) {
    val fg = if (on) e.on(e.ink.acc) else e.ink.ink2
    val shape = RoundedCornerShape(50)
    Box(
        Modifier.requiredHeight(48.dp).widthIn(min = 48.dp)
            .semantics { contentDescription = "밀어서 지우기"; stateDescription = if (on) "켜짐" else "꺼짐"; role = Role.Switch }
            .tap(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.clip(shape).then(if (on) Modifier.background(e.ink.acc) else Modifier.border(1.5.dp, e.ink.ink4, shape))
                .padding(horizontal = if (iconOnly) 8.dp else 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DashArrow(fg, Modifier.size(e.meta * 1.1f, e.meta))
            if (!iconOnly) Txt("밀어서 지우기", e.meta, fg, weight = 700)
        }
    }
}

/** "⇠": a dashed leftwards arrow, drawn (the card fonts have no such glyph). */
@Composable
private fun DashArrow(color: Color, modifier: Modifier) = androidx.compose.foundation.Canvas(modifier) {
    val w = size.width
    val h = size.height
    val sw = h * 0.13f
    val y = h / 2
    val head = h * 0.36f
    drawLine(color, Offset(sw, y), Offset(sw + head, y - head), sw, StrokeCap.Round)
    drawLine(color, Offset(sw, y), Offset(sw + head, y + head), sw, StrokeCap.Round)
    var x = sw
    while (x < w - sw) {
        drawLine(color, Offset(x, y), Offset(minOf(x + w * 0.2f, w - sw), y), sw, StrokeCap.Round)
        x += w * 0.32f
    }
}

/** "알림을 왼쪽으로 밀면 지워져요 · 버튼을 다시 누르면 끝" under the rows while 밀어서 지우기 is on. */
@Composable
private fun SwipeHint(e: Env, modifier: Modifier = Modifier) =
    Txt("알림을 왼쪽으로 밀면 지워져요 · 버튼을 다시 누르면 끝", e.meta * 0.85f, e.ink.ink2, modifier.fillMaxWidth(), weight = 500, align = TextAlign.Center)

/** Empty / no-access state: bell (or lock) and one line, centred on the card plate. */
@Composable
private fun NotifEmpty(e: Env, plate: Modifier, icon: Ic, text: String, onTap: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().then(plate).tap(onTap).padding(e.pad),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconView(icon, e.ink.ink2, Modifier.size(min(e.label * 1.9f, e.ch * 0.4f)), stroke = true)
        Spacer(Modifier.height(e.gap * 1.6f))
        Txt(text, e.meta, e.ink.ink2, weight = 500, align = TextAlign.Center, maxW = e.cw)
    }
}

/** A small text button: the visible text stays small, the hit area is 48 dp tall. */
@Composable
private fun TextBtn(e: Env, text: String, color: Color, onClick: () -> Unit) {
    Box(Modifier.requiredHeight(48.dp).tap(onClick).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Txt(text, e.meta, color, weight = 600)
    }
}

/**
 * The notification card. [plate] = the theme's card plate (CardView), worn only while closed: open, each
 * notification sits on its own plate.
 */
@Composable
internal fun NotifCard(e: Env, state: StandbyState, actions: StandbyActions, plate: Modifier) {
    var open by remember { mutableStateOf(false) }
    var touched by remember { mutableIntStateOf(0) }
    LaunchedEffect(open, touched) { if (open) { delay(IDLE_CLOSE_MS); open = false } }
    val req = LocalNotifOpen.current
    LaunchedEffect(req?.value) { if (req?.value == true) { req.value = false; open = true; touched++ } }
    when {
        !state.permissions.notifications -> return NotifEmpty(e, plate, Ic.LOCK, "권한 필요 · 눌러서 설정") { actions.openSettings("notifications") }
        state.notifications.isEmpty() -> return NotifEmpty(e, plate, Ic.BELL, "알림 없음", null)
    }
    val groups = remember(state.notifications) { notifGroups(state.notifications) }
    // r15 §3 밀어서 지우기: off after 30 s without a dismiss, when the screen goes off (stop) or the card leaves the screen
    var sc by remember { mutableStateOf(SwipeClear()) }
    LaunchedEffect(sc) { if (sc.on) { delay(SWIPE_IDLE_MS); sc = sc.off() } }
    // the display dims off while the activity stays started (no ON_STOP)
    LaunchedEffect(state.displayOff) { if (state.displayOff) sc = sc.off() }
    val life = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(life) {
        val o = LifecycleEventObserver { _, ev -> if (ev == Lifecycle.Event.ON_STOP) sc = sc.off() }
        life.lifecycle.addObserver(o)
        onDispose { life.lifecycle.removeObserver(o) }
    }
    val swipe = Swipe(sc.on, { sc = sc.toggle() }) { keys -> sc = sc.dismissed(); actions.notificationClear(keys) }
    val card = Modifier.fillMaxSize()
        .onGloballyPositioned { c ->
            val r = c.boundsInWindow()
            if (sc.on && r.width * r.height < c.size.width * c.size.height * 0.5f) sc = sc.off()
        }
        // on: the card keeps the sideways drags its rows leave, so the page never turns under a swipe
        .draggable(rememberDraggableState { }, Orientation.Horizontal, enabled = sc.on)
    if (open) {
        Box(
            card.pointerInput(Unit) {
                awaitPointerEventScope { while (true) { awaitPointerEvent(PointerEventPass.Initial); touched++ } }
            },
        ) { NotifOpen(e, state, actions, swipe) { open = false } }
    } else {
        Box(card) { NotifClosed(e, state, groups, plate, swipe) { open = true } }
    }
}

/** What rows need from 밀어서 지우기: whether it is [on], the switch, and the clear (which counts as a use). */
private class Swipe(val on: Boolean, val toggle: () -> Unit, val clear: (List<String>) -> Unit)

// ---------------------------------------------------------------- closed

@Composable
private fun NotifClosed(
    e: Env, state: StandbyState, groups: List<NotifGroup>, plate: Modifier, swipe: Swipe, onOpen: () -> Unit,
) {
    val tier = notifTier(e.w.value, e.h.value)
    val low = e.h < 240.dp
    val pad = if (low) 10.dp else 14.dp
    val total = groups.sumOf { it.count }
    val nowMs = state.now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    Box(Modifier.fillMaxSize().then(plate).tap(onOpen).padding(pad)) { // one target: rows are under 48 dp
        val ih = e.h - pad * 2
        when (tier) {
            NotifTier.A -> IconGrid(e, groups, total, ih)
            NotifTier.S -> Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(44.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                    Txt(total.toString(), e.head * 1.1f, e.ink.ink, weight = 700, num = true)
                    SwipeToggle(e, swipe.on, iconOnly = true, swipe.toggle)
                }
                Spacer(Modifier.width(12.dp))
                val rowH = 36.dp
                Column(Modifier.weight(1f)) {
                    groups.take(floor(ih / rowH).toInt().coerceAtLeast(1)).forEach { GroupRow(e, state, swipe, it, tier, rowH, nowMs) }
                }
            }
            else -> Column(Modifier.fillMaxSize()) {
                val headH = if (low) 32.dp else 40.dp
                Row(Modifier.fillMaxWidth().height(headH), verticalAlignment = Alignment.CenterVertically) {
                    Txt("알림 $total", e.meta, e.ink.ink, Modifier.weight(1f), weight = 600, num = true)
                    if (tier == NotifTier.D || tier == NotifTier.E) {
                        TextBtn(e, "내용 열기", e.ink.accText, onOpen)
                        SwipeToggle(e, swipe.on, iconOnly = false, swipe.toggle)
                    } else SwipeToggle(e, swipe.on, iconOnly = true, swipe.toggle)
                }
                val rowH = (if (low) 36.dp else if (tier == NotifTier.B) 44.dp else 40.dp)
                val room = ih - headH
                val cols = if (tier == NotifTier.E) 2 else 1
                val fit = floor(room / rowH).toInt() * cols
                val n = if (groups.size <= fit) groups.size else (floor((room - 32.dp) / rowH).toInt() * cols).coerceAtLeast(1)
                val shown = groups.take(n)
                val row = @Composable { g: NotifGroup -> GroupRow(e, state, swipe, g, tier, rowH, nowMs) }
                if (cols == 2) {
                    val per = (n + 1) / 2
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(Modifier.weight(1f)) { shown.take(per).forEach { row(it) } }
                        Column(Modifier.weight(1f)) { shown.drop(per).forEach { row(it) } }
                    }
                } else shown.forEach { row(it) }
                if (groups.size > n) Cluster(e, groups.drop(n))
                // the hint only where the rows leave room for it
                if (swipe.on && room - (if (groups.size > n) 32.dp else 0.dp) - rowH * ((n + cols - 1) / cols) >= 28.dp) {
                    Spacer(Modifier.weight(1f))
                    SwipeHint(e)
                }
            }
        }
    }
}

/** One quiet row: icon, sender (bold + dot when fresh), count, app name (wide), time. B: time under the sender. */
@Composable
private fun GroupRow(
    e: Env, state: StandbyState, swipe: Swipe, g: NotifGroup, tier: NotifTier, rowH: Dp, nowMs: Long,
) = SwipeRow(e, g.head.key, swipe.on, g.items.all { it.clearable }, { swipe.clear(g.items.map { it.key }) }, Modifier.fillMaxWidth().height(rowH)) { m ->
    val n = g.head
    val fresh = nowMs - n.postedAtEpochMs < FRESH_MS
    val count = @Composable { if (g.count > 1) Txt(g.count.toString(), e.meta, e.ink.ink2, weight = 500, num = true) }
    Row(
        m.padding(start = if (swipe.on) 9.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NotifIcon(e, n, if (tier == NotifTier.B) 22.dp else 20.dp)
        if (tier == NotifTier.B) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Txt(n.title, e.read, e.ink.ink, weight = if (fresh) 700 else 500)
                Txt(ago(n.postedAtEpochMs, state), e.meta, e.ink.ink2, weight = 500, num = true)
            }
            count()
            return@Row
        }
        Txt(n.title, e.read, e.ink.ink, Modifier.weight(1f), weight = if (fresh) 700 else 500)
        count()
        if (fresh) Box(Modifier.size(6.dp).clip(CircleShape).background(e.ink.ink))
        if (tier == NotifTier.E || (tier == NotifTier.D && e.w >= 400.dp)) Txt(n.appLabel, e.meta, e.ink.ink2, Modifier.widthIn(max = 96.dp), weight = 500)
        Txt(ago(n.postedAtEpochMs, state), e.meta, e.ink.ink2, weight = 500, num = true)
    }
}

/** Overflow: up to five icons overlapped (each ringed in the plate colour) + "+N". */
@Composable
private fun Cluster(e: Env, rest: List<NotifGroup>) {
    val ring = e.ink.plate.compositeOver(e.ink.bg)
    val icons = rest.take(5)
    Row(Modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        // 20 dp icons in a 2 dp ring, each 6 dp over the one before
        Box(Modifier.width(24.dp + 18.dp * (icons.size - 1)).height(24.dp)) {
            icons.forEachIndexed { i, g ->
                Box(Modifier.offset(x = 18.dp * i).clip(RoundedCornerShape(30)).background(ring).padding(2.dp)) { NotifIcon(e, g.head, 20.dp) }
            }
        }
        Spacer(Modifier.width(8.dp))
        Txt("+${rest.sumOf { it.count }}", e.meta, e.ink.ink2, weight = 500, num = true)
    }
}

/** Tier A: "알림 N" on top, then a two-column grid of badged icons; the last cell turns into "+N" when they overflow. */
@Composable
private fun IconGrid(e: Env, groups: List<NotifGroup>, total: Int, ih: Dp) {
    val cell = 44.dp
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.height(24.dp), contentAlignment = Alignment.CenterStart) { Txt("알림 $total", e.meta, e.ink.ink, weight = 600, num = true) } // plan-r15 D3: never a bare number
        val rows = floor((ih - 24.dp) / cell).toInt().coerceAtLeast(1)
        val slots = rows * 2
        val many = groups.size > slots
        val shown = groups.take(if (many) slots - 1 else groups.size)
        val cells: List<(@Composable () -> Unit)> = shown.map { g -> @Composable { BadgedIcon(e, g, 34.dp) } } +
            if (many) listOf(@Composable { Txt("+${groups.drop(shown.size).sumOf { it.count }}", e.meta, e.ink.ink2, weight = 600, num = true) }) else emptyList()
        cells.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().height(cell)) {
                pair.forEach { c -> Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) { c() } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ---------------------------------------------------------------- open

/**
 * Open: header ("알림 N", "접기", "모두 지우기"), then every notification on its own plate like the lock screen:
 * icon + app + time, sender (head), message (newest 4 lines, others 2). Small slots put the sender beside the icon.
 * Tap a plate = open the app; with 밀어서 지우기 on, swipe left = remove.
 */
@Composable
private fun NotifOpen(e: Env, state: StandbyState, actions: StandbyActions, swipe: Swipe, onClose: () -> Unit) {
    val list = state.notifications
    val compact = min(e.w, e.h) < 176.dp
    val pad = if (compact) 12.dp else 16.dp
    val shape = RoundedCornerShape(max(e.plateRadius * 0.85f, if (e.plateRadius == 0.dp) 0.dp else 10.dp))
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Txt("알림 ${list.size}", e.head, e.ink.ink, Modifier.weight(1f), weight = 700, num = true)
            TextBtn(e, "접기", e.ink.accText, onClose)
            SwipeToggle(e, swipe.on, iconOnly = e.w < 300.dp, swipe.toggle)
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().fadeBottom(36.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(list, key = { _, n -> n.key }) { i, n ->
                val text = if (n.hidden) "내용 숨김" else n.text
                val time = @Composable { if (e.w >= 200.dp) Txt(ago(n.postedAtEpochMs, state), e.meta, e.ink.ink2, weight = 500, num = true) }
                SwipeRow(e, n.key, swipe.on, n.clearable, { swipe.clear(listOf(n.key)) }, Modifier.animateItem().fillMaxWidth().then(if (swipe.on) Modifier.clip(shape) else Modifier)) { m ->
                Column(
                    m.fillMaxWidth()
                        .tap { actions.notificationTap(n.key) }
                        .themedSurface(e.t, e.ink, shape)
                        .padding(pad),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        NotifIcon(e, n, if (compact) 24.dp else 26.dp)
                        if (compact) Txt(n.title, e.read * 1.07f, e.ink.ink, Modifier.weight(1f), weight = 700)
                        else Txt(n.appLabel, e.meta, e.ink.ink2, Modifier.weight(1f), weight = 500)
                        time()
                    }
                    if (!compact) {
                        Spacer(Modifier.height(10.dp))
                        Txt(n.title, e.head, e.ink.ink, weight = 700)
                    }
                    if (text.isNotEmpty()) {
                        Spacer(Modifier.height(if (compact) 8.dp else 7.dp))
                        val lines = if (i == 0) (if (compact) 3 else 4) else 2
                        Txt(text, e.read, if (n.hidden) e.ink.ink2 else e.ink.ink, weight = 500, lines = lines, leading = LEADING)
                    }
                }
                }
            }
        }
        if (swipe.on && e.h >= 240.dp) SwipeHint(e, Modifier.padding(top = 6.dp))
    }
}

/** The open list fades out over its last [h] (mockup: mask-image to transparent over 36 px), so cut plates read as "more". */
private fun Modifier.fadeBottom(h: Dp): Modifier = graphicsLayer { alpha = 0.99f } // < 1 = own layer, so DstIn masks only the list (the Offscreen strategy drew no fade in Robolectric)
    .drawWithContent {
        drawContent()
        val f = (1f - h.toPx() / size.height).coerceIn(0f, 1f)
        drawRect(Brush.verticalGradient(0f to Color.Black, f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn) // literal-ok: alpha mask
    }
