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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kr.dot.sb.ui.screens.ago
import kr.dot.sb.ui.state.NotifItem
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Family
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.Surface
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/*
 * The two notification cards (mockup r6: 1 쌓기, 2 목록). One body per card for all three themes; the theme's surface
 * token decides how a notification sits on the board:
 *   A (Surface.NONE)  grey plates (ink.plate); the list is an iOS grouped list (big outer, small inner corners)
 *   B (Surface.RULE)  no plates, hairline rules, ring icons with the app's own mono glyph, tracked kickers
 *   C (Surface.PLATE) each notification on its own warm lamp plate (the slot itself gets none)
 * Tap = open the app (after unlock), swipe sideways = remove it from the phone. Nothing animates at rest: the swipe
 * offset lives in a graphics layer and only moves under the finger or while settling.
 */

private val Env.rule: Boolean get() = t.surface == Surface.RULE

/** Between stacked text lines: clears the upper line's descenders (Txt boxes end on the baseline). */
private val Env.lineGap: Dp get() = max(gap * 0.6f, label * 0.38f)
/** "12분 전" beside the sender only where the sender keeps room (narrow tall slots drop it). */
private val Env.roomForTime: Boolean get() = atLeastS && w >= label * 9f
/** Between the sender row and the message. */
private val Env.bodyGap: Dp get() = max(gap * 1.5f, label * 0.55f)

/** "Do it" ink: A system blue, B vermilion, C butter. */
private fun actionInk(e: Env): Color = when (e.t.family) {
    Family.IPHONE -> e.ink.blue
    Family.PAPER -> e.ink.weekday
    Family.LAMP -> e.ink.sun
}

/** One notification's ground in the theme: flat grey plate (A), lamp plate (C), nothing (B: rules are drawn apart). */
private fun Modifier.notifPlate(e: Env, shape: RoundedCornerShape, radius: Dp): Modifier = when (e.t.surface) {
    Surface.NONE -> clip(shape).background(e.ink.plate)
    Surface.PLATE -> lampPlate(e.ink, e.t.palette.fg2, radius) // neutral warm wash (mockup r6 C)
    Surface.RULE -> this
}

/** B hairlines along the top and/or bottom edge. */
private fun Modifier.hairlines(e: Env, top: Boolean, bottom: Boolean): Modifier = if (!e.rule) this else drawBehind {
    val sw = maxOf(1f, 1.dp.toPx())
    if (top) drawLine(e.ink.ink3, Offset(0f, sw / 2), Offset(size.width, sw / 2), sw)
    if (bottom) drawLine(e.ink.ink3, Offset(0f, size.height - sw / 2), Offset(size.width, size.height - sw / 2), sw)
}

/** Small grey line at the board label size (B: tracked kicker); never below the theme's minimum text. */
@Composable
private fun Small(e: Env, text: String, modifier: Modifier = Modifier, color: Color = e.ink.ink2) =
    Txt(text, e.label, color, modifier, weight = 500, num = true, tracking = e.t.labelTracking)

/** "12분 전" (B: the digits in the italic serif, like a newspaper dateline). */
@Composable
private fun When(e: Env, n: NotifItem, state: StandbyState) {
    val s = ago(n.postedAtEpochMs, state)
    if (e.rule) Txt(numSpans(s, Fonts.instrumentItalic, 400), e.label, e.ink.ink2, weight = 500)
    else Txt(s, e.label, e.ink.ink2, weight = 500, num = true)
}

/** App icon: A/C the colour icon on a rounded square (letter plate without one), B a hairline ring with the mono glyph. */
@Composable
private fun NotifIcon(e: Env, n: NotifItem, d: Dp) {
    val letter = n.appLabel.take(1)
    if (e.rule) {
        Box(Modifier.size(d).border(max(1.dp, d * 0.045f), e.ink.ink2, CircleShape), contentAlignment = Alignment.Center) {
            val g = n.glyph
            if (g != null) Image(g, null, Modifier.size(d * 0.54f), contentScale = ContentScale.Fit, colorFilter = ColorFilter.tint(e.ink.ink))
            else Txt(letter, d * 0.46f, e.ink.ink, weight = 600)
        }
        return
    }
    val shape = RoundedCornerShape(22) // percent: the iOS app-icon squircle
    val img = n.icon
    if (img != null) {
        Image(img, null, Modifier.size(d).clip(shape), contentScale = ContentScale.Crop, colorFilter = nightFilter(e.ink.night))
        return
    }
    val day = Color(n.plateColor)
    val fg = if (day.luminance() > 0.6f) Color(0xFF3A1D1D) else Color.White // dark letter on a yellow plate (KakaoTalk)
    Box(Modifier.size(d).clip(shape).background(e.ink.mapped(day)), contentAlignment = Alignment.Center) {
        Txt(letter, d * 0.46f, lerp(fg, Color.Black, e.ink.night), weight = 700)
    }
}

/**
 * Horizontal swipe that throws the element out past a third of its width (or on a fling), else springs back. The
 * offset is read only inside the graphics layer, so a drag redraws one layer and recomposes nothing. A notification
 * the app pins ([clearable] false) follows the finger reluctantly and always comes back.
 */
@Composable
private fun Modifier.swipeAway(key: String, clearable: Boolean, onClear: () -> Unit): Modifier {
    var dx by remember(key) { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val fling = with(LocalDensity.current) { 800.dp.toPx() }
    val clear by rememberUpdatedState(onClear)
    val drag = rememberDraggableState { d -> dx += if (clearable) d else d * 0.2f }
    return onSizeChanged { width = it.width.coerceAtLeast(1) }
        .draggable(
            drag, Orientation.Horizontal,
            onDragStopped = { v ->
                val w = width.toFloat()
                val out = clearable && (abs(dx) > w / 3f || (abs(v) > fling && sign(v) == sign(dx) && abs(dx) > w * 0.08f))
                if (out) {
                    animate(dx, sign(dx) * w * 1.1f, animationSpec = tween(170)) { x, _ -> dx = x }
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
}

/** Empty / no-access state: bell (or lock) and one line, centred on the theme's plate. */
@Composable
private fun NotifEmpty(e: Env, icon: Ic, text: String, onTap: (() -> Unit)?) {
    val r = e.round(e.s * 0.75f)
    Column(
        Modifier.fillMaxSize().notifPlate(e, RoundedCornerShape(r), r).tap(onTap).padding(e.pad),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconView(icon, e.ink.ink2, Modifier.size(min(e.label * 1.9f, e.ch * 0.4f)), e.ink.bg, stroke = true)
        Spacer(Modifier.height(e.gap * 1.6f))
        Txt(text, e.label, e.ink.ink2, weight = 500, align = TextAlign.Center, maxW = e.cw)
    }
}

/** Shared gate: no listener access → ask for it; nothing posted → bell. Returns true when it drew. */
@Composable
private fun gate(e: Env, state: StandbyState, actions: StandbyActions): Boolean {
    when {
        !state.permissions.notifications -> NotifEmpty(e, Ic.LOCK, "권한 필요 · 눌러서 설정") { actions.openSettings("notifications") }
        state.notifications.isEmpty() -> NotifEmpty(e, Ic.BELL, "알림 없음", null)
        else -> return false
    }
    return true
}

// ---------------------------------------------------------------- 1 · stack

/**
 * The newest notification big on a plate, the next two peeking under it as smaller, fainter slivers (B: rules only),
 * "알림 5 · 뒤에 4개" above. Swiping the front one away brings the next forward. Small slots: the front card alone.
 */
@Composable
internal fun NotifStackCard(e: Env, state: StandbyState, actions: StandbyActions) {
    if (gate(e, state, actions)) return
    val list = state.notifications
    val front = list.first()
    val compact = !e.atLeastM
    // the card that slides forward after a swipe grows in from the first sliver's size (once, not at rest)
    var promoted by remember { mutableStateOf(false) }
    val grow = remember(front.key) { mutableFloatStateOf(if (promoted) 0.93f else 1f) }
    LaunchedEffect(front.key) {
        if (grow.floatValue < 1f) animate(grow.floatValue, 1f, animationSpec = tween(220)) { v, _ -> grow.floatValue = v }
        promoted = false
    }
    val clear = { promoted = true; actions.notificationClear(listOf(front.key)) }
    val r = e.round(e.s * 0.75f)
    val pad = if (e.rule) e.label * 0.7f else max(e.label * 0.8f, e.pad * 0.75f)
    val padX = if (e.rule) e.label * 0.15f else pad
    val who = if (compact) e.label * 1.15f else max(e.label, min(e.sz.title, e.label * 1.3f))
    val body = e.label * 1.1f
    val icon = if (compact) e.label * 1.5f else e.label * 2.1f
    val appLine = !compact && !front.hidden
    val topRow = max(icon, (if (appLine) e.labelCap + e.lineGap else 0.dp) + who * CAP_UI)
    val bodyGap = e.bodyGap
    val lineH = body * 1.15f
    fun linesIn(space: Dp): Int = if (space < body * CAP_UI) 0 else (floor(((space - body * CAP_UI) / lineH).toDouble()).toInt() + 1)

    if (compact) {
        val lines = linesIn(e.h - pad * 2 - topRow - bodyGap).coerceAtMost(if (e.atLeastS) 2 else 1)
        FrontCard(e, state, actions, front, Modifier.fillMaxSize(), r, pad, padX, icon, who, body, lines, appLine, clear) { grow.floatValue }
        return
    }
    val peeks = if (e.rule) 0 else (list.size - 1).coerceAtMost(2)
    val p1 = e.label * 0.55f
    val p2 = e.label * 1.05f
    val peek = when (peeks) { 2 -> p2; 1 -> p1; else -> 0.dp }
    val headGap = e.gap * 2f
    val lines = linesIn(e.h - e.labelCap - headGap - peek - pad * 2 - topRow - bodyGap).coerceAtMost(3)
    val inset = if (e.rule) 0.dp else e.label * 0.25f
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = inset)) {
            Small(e, "알림 ${list.size}", Modifier.weight(1f))
            if (list.size > 1) Small(e, "뒤에 ${list.size - 1}개")
        }
        Spacer(Modifier.height(headGap))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().padding(bottom = peek)) {
                // slivers: narrower copies of the card dropped so exactly p1 / p2 shows under it, fading back
                if (peeks >= 2) Sliver(e, Modifier.matchParentSize(), e.cw * 0.07f, p2, 0.45f, r)
                if (peeks >= 1) Sliver(e, Modifier.matchParentSize(), e.cw * 0.035f, p1, 0.75f, r)
                FrontCard(e, state, actions, front, Modifier.fillMaxWidth(), r, pad, padX, icon, who, body, lines, appLine, clear) { grow.floatValue }
            }
        }
    }
}

/** A static sliver (no layer): inset by [inset] each side, [peek] lower, its plate blended [a] of the way from black. */
@Composable
private fun Sliver(e: Env, modifier: Modifier, inset: Dp, peek: Dp, a: Float, r: Dp) {
    val c = lerp(e.ink.bg, if (e.t.surface == Surface.PLATE) e.ink.plate2 else e.ink.plate, a)
    Box(modifier.offset(y = peek).padding(horizontal = inset).clip(RoundedCornerShape(r * 0.9f)).background(c))
}

@Composable
private fun FrontCard(
    e: Env, state: StandbyState, actions: StandbyActions, n: NotifItem, modifier: Modifier, r: Dp, pad: Dp, padX: Dp,
    icon: Dp, who: Dp, body: Dp, lines: Int, appLine: Boolean, onClear: () -> Unit, grow: () -> Float,
) {
    val text = if (n.hidden) "내용 숨김" else n.text
    Column(
        modifier
            .graphicsLayer { val g = grow(); scaleX = g; scaleY = g }
            .swipeAway(n.key, n.clearable, onClear)
            .tap { actions.notificationTap(n.key) }
            .notifPlate(e, RoundedCornerShape(r), r)
            .hairlines(e, top = true, bottom = true)
            .padding(horizontal = padX, vertical = pad),
        verticalArrangement = Arrangement.Center, // a slot-filling (compact) card centres its content
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NotifIcon(e, n, icon)
            Spacer(Modifier.width(e.label * 0.55f))
            Column(Modifier.weight(1f)) {
                if (appLine) {
                    Txt(n.appLabel, e.label, e.ink.ink2, weight = 500)
                    Spacer(Modifier.height(e.lineGap))
                }
                Txt(n.title, who, e.ink.ink, weight = 700)
            }
            if (e.roomForTime) {
                Spacer(Modifier.width(e.label * 0.4f))
                When(e, n, state)
            }
        }
        if (lines > 0 && text.isNotEmpty()) {
            Spacer(Modifier.height(e.bodyGap))
            Txt(text, body, if (n.hidden) e.ink.ink2 else e.ink.ink, weight = 500, lines = lines)
        }
    }
}

// ---------------------------------------------------------------- 2 · list

/**
 * The phone's shade inside the slot: "알림 5" + "모두 지우기", then one row per notification (icon, app · time,
 * sender bold, one line of text), scrolling. Small slots drop the app line. Removing a row slides the rest up.
 */
@Composable
internal fun NotifListCard(e: Env, state: StandbyState, actions: StandbyActions) {
    if (gate(e, state, actions)) return
    val list = state.notifications
    val compact = !e.atLeastM
    val head = max(e.label, min(e.sz.title, e.label * 1.2f))
    val inset = if (e.rule) 0.dp else e.label * 0.25f
    val outer = e.round(e.s * 0.75f)
    val inner = max(4.dp, e.label * 0.22f)
    val gapRow = when (e.t.surface) { Surface.NONE -> max(2.dp, e.label * 0.08f); Surface.RULE -> 0.dp; Surface.PLATE -> e.gap * 0.7f }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = inset), verticalAlignment = Alignment.CenterVertically) {
            Txt("알림 ${list.size}", head, e.ink.ink, weight = 700, num = true)
            Spacer(Modifier.weight(1f))
            val keys = list.filter { it.clearable }.map { it.key }
            if (keys.isNotEmpty()) {
                // the hit area reaches past the text; the visible text sits on the slot edge like the shade's
                Box(Modifier.tap { actions.notificationClear(keys) }.padding(start = e.label * 0.6f, end = inset, top = e.gap, bottom = e.gap)) {
                    Txt(if (e.w >= e.label * 9.5f) "모두 지우기" else "지우기", e.label, actionInk(e), weight = 600)
                }
            }
        }
        Spacer(Modifier.height(e.gap * 0.6f))
        val clip = if (e.t.surface == Surface.RULE) Modifier else Modifier.clip(RoundedCornerShape(outer))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().then(clip), verticalArrangement = Arrangement.spacedBy(gapRow)) {
            itemsIndexed(list, key = { _, n -> n.key }) { i, n ->
                val shape = when (e.t.surface) {
                    // iOS grouped list: the group has the big corners, rows inside it the small ones
                    Surface.NONE -> RoundedCornerShape(
                        topStart = if (i == 0) outer else inner, topEnd = if (i == 0) outer else inner,
                        bottomStart = if (i == list.lastIndex) outer else inner, bottomEnd = if (i == list.lastIndex) outer else inner,
                    )
                    else -> RoundedCornerShape(outer * 0.6f)
                }
                ShadeRow(
                    e, state, n, compact, shape, outer * 0.6f,
                    Modifier.animateItem().fillMaxWidth(), onTap = { actions.notificationTap(n.key) },
                    onClear = { actions.notificationClear(listOf(n.key)) },
                )
            }
        }
    }
}

@Composable
private fun ShadeRow(
    e: Env, state: StandbyState, n: NotifItem, compact: Boolean, shape: RoundedCornerShape, radius: Dp, modifier: Modifier,
    onTap: () -> Unit, onClear: () -> Unit,
) {
    val appLine = !compact && !n.hidden
    val who = max(e.label, min(e.sz.title, e.label * 1.12f))
    val icon = if (compact) e.label * 1.35f else e.label * 1.6f
    val text = if (n.hidden) "내용 숨김" else n.text
    Row(
        modifier
            .swipeAway(n.key, n.clearable, onClear)
            .tap(onTap)
            .notifPlate(e, shape, radius)
            .hairlines(e, top = true, bottom = false)
            .padding(horizontal = if (e.rule) e.label * 0.1f else e.label * 0.6f, vertical = e.label * 0.55f),
    ) {
        NotifIcon(e, n, icon)
        Spacer(Modifier.width(e.label * 0.5f))
        Column(Modifier.weight(1f)) {
            if (appLine) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Txt(n.appLabel, e.label, e.ink.ink2, Modifier.weight(1f), weight = 500)
                    Spacer(Modifier.width(e.label * 0.4f))
                    When(e, n, state)
                }
                Spacer(Modifier.height(e.lineGap))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Txt(n.title, who, e.ink.ink, Modifier.weight(1f), weight = 700)
                if (!appLine && e.roomForTime) {
                    Spacer(Modifier.width(e.label * 0.4f))
                    When(e, n, state)
                }
            }
            if (text.isNotEmpty()) {
                Spacer(Modifier.height(e.lineGap))
                Txt(text, e.label, e.ink.ink2, weight = 500)
            }
        }
    }
}
