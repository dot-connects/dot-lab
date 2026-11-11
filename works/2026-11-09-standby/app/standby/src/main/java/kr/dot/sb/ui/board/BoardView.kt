package kr.dot.sb.ui.board

import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import kr.dot.sb.ui.cards.CardLook
import kr.dot.sb.ui.cards.CardView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalInk
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.ProvideTheme
import kr.dot.sb.ui.theme.StandbyTheme
import kr.dot.sb.ui.theme.Motion
import kr.dot.sb.ui.theme.Sizes
import kr.dot.sb.ui.theme.Reading
import kr.dot.sb.ui.theme.Surface
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import kotlin.math.max
import kotlin.math.min

/** A slot rectangle in dp, board coordinates (margins included). */
data class SlotRect(val x: Float, val y: Float, val w: Float, val h: Float) {
    val short: Float get() = min(w, h)
}

/** design-system §3: margins, gutters, the six layouts (portrait = transpose), camera-hole symmetry. */
object BoardGrid {
    /**
     * [cutX]/[cutY]: the largest cutout extent on a horizontal / vertical edge (dp, 0 = none). The side holding it gets
     * `max(M, extent + 8)` and the opposite side the same, so the composition stays centred.
     * ponytail: Fold-inner hinge snap (§3.1) not done; add when a vertical FoldingFeature is wired into BoardView.
     */
    fun slots(layout: Layout, w: Float, h: Float, cutX: Float = 0f, cutY: Float = 0f): List<SlotRect> {
        val short = min(w, h)
        val m = Grid.margin(short.dp).value
        val g = Grid.gutter(short.dp).value
        val mx = if (cutX > 0f) max(m, cutX + 8f) else m
        val my = if (cutY > 0f) max(m, cutY + 8f) else m
        val cw = w - 2 * mx
        val ch = h - 2 * my
        var rects = if (h > w) land(layout, ch, cw, g).map { SlotRect(it.y, it.x, it.h, it.w) } else land(layout, cw, ch, g)
        // A transposed 2×2 would read column-first; keep reading order (top-left, top-right, bottom-left, bottom-right).
        if (h > w && layout == Layout.FOUR) rects = listOf(rects[0], rects[2], rects[1], rects[3])
        return rects.map { it.copy(x = it.x + mx, y = it.y + my) }
    }

    private fun land(layout: Layout, w: Float, h: Float, g: Float): List<SlotRect> {
        val half = (w - g) / 2
        val rowH = (h - g) / 2
        val big = (w - g) * 0.6f
        val small = w - g - big
        return when (layout) {
            Layout.ONE -> listOf(SlotRect(0f, 0f, w, h))
            Layout.TWO -> listOf(SlotRect(0f, 0f, half, h), SlotRect(half + g, 0f, half, h))
            Layout.BIG2 -> listOf(SlotRect(0f, 0f, big, h), SlotRect(big + g, 0f, small, rowH), SlotRect(big + g, rowH + g, small, rowH))
            Layout.TWO2 -> listOf(SlotRect(0f, 0f, small, rowH), SlotRect(0f, rowH + g, small, rowH), SlotRect(small + g, 0f, big, h))
            Layout.FOUR -> listOf(
                SlotRect(0f, 0f, half, rowH), SlotRect(half + g, 0f, half, rowH),
                SlotRect(0f, rowH + g, half, rowH), SlotRect(half + g, rowH + g, half, rowH),
            )
            Layout.THREE -> {
                val c = (w - 2 * g) / 3
                listOf(SlotRect(0f, 0f, c, h), SlotRect(c + g, 0f, c, h), SlotRect(2 * (c + g), 0f, c, h))
            }
        }
    }

    private val CLOCKS = setOf(CardType.CLOCK, CardType.ANALOG)
    private val CLOCK_LIKE = CLOCKS + setOf(CardType.TIMER, CardType.DDAY)

    /**
     * §5.1 hero: the clock-like card in the largest slot. Ties: a real clock beats timer/D-day (so a D-day next to
     * an equal clock never steals the accent), then the leading/top slot. Returns -1 when no clock-like card.
     */
    fun hero(slots: List<SlotRect>, cards: List<CardType?>): Int =
        slots.indices.filter { cards.getOrNull(it) in CLOCK_LIKE }
            .sortedWith(
                compareByDescending<Int> { Math.round(slots[it].w * slots[it].h) }
                    .thenByDescending { cards[it] in CLOCKS }
                    .thenBy { it },
            ).firstOrNull() ?: -1

    fun hasClock(cards: List<CardType?>) = cards.any { it in CLOCKS }

    /**
     * Gutter rules (theme B) as (x0, y0, x1, y1) in dp: for every two slots that face each other across a gutter, a
     * line down (or across) the gutter's middle over the stretch both slots share.
     */
    fun rules(slots: List<SlotRect>): List<FloatArray> {
        val out = ArrayList<FloatArray>()
        val gap = 64f // a gutter is at most this wide; slots further apart do not face each other
        for (a in slots) for (b in slots) {
            if (a === b) continue
            val dx = b.x - (a.x + a.w)
            val y0 = max(a.y, b.y); val y1 = min(a.y + a.h, b.y + b.h)
            if (dx in 0f..gap && y1 - y0 > 1f) out += floatArrayOf(a.x + a.w + dx / 2, y0, a.x + a.w + dx / 2, y1)
            val dy = b.y - (a.y + a.h)
            val x0 = max(a.x, b.x); val x1 = min(a.x + a.w, b.x + b.w)
            if (dy in 0f..gap && x1 - x0 > 1f) out += floatArrayOf(x0, a.y + a.h + dy / 2, x1, a.y + a.h + dy / 2)
        }
        return out
    }
}

/**
 * Board-level look: accent, hero slot, and the shared label size / pad (§5.3, §3.4). The label is floored at the
 * theme's minimum text size of the screen ([screenShort] × minText, r5: never phone-app sized), capped by the smallest
 * slot so it still fits.
 */
private fun looks(board: Board, config: AppConfig, slots: List<SlotRect>, screenShort: Float, t: StandbyTheme): List<CardLook> {
    val firsts = board.slots.map { it.firstOrNull() }
    val hero = BoardGrid.hero(slots, firsts)
    val heroFace = when (firsts.getOrNull(hero)) {
        CardType.ANALOG -> FaceId.ANALOG
        null -> null
        else -> board.clockFace
    }
    val accent = board.accent ?: heroFace?.let { config.clockStyle(it).color } ?: 0xFF76A6F3
    val sizes = slots.map { Sizes.of(it.w.dp, it.h.dp, r = t.ramp) }
    val floor = (screenShort * t.minText).dp
    // r7: also capped at 6.2 % of the screen, so a lone full-size slot never gets 40 dp labels
    val label = (sizes.minOfOrNull { it.label } ?: 18.dp).coerceAtLeast(floor)
        .coerceAtMost((slots.minOfOrNull { it.short } ?: 200f).dp * 0.16f).coerceAtMost((screenShort * 0.054f).dp)
    val read = Reading.read(screenShort.dp)
    val pad = sizes.minOfOrNull { it.pad } ?: 12.dp
    val clock = BoardGrid.hasClock(firsts)
    return slots.indices.map { CardLook(Color(accent), label, pad, it == hero, clock, board.clockFace, read) }
}

/** OWNER: board agent. One board: layout grid, slots (stacks), cards, shared label size, hero accent. */
@Composable
fun BoardView(board: Board, state: StandbyState, config: AppConfig, actions: StandbyActions, modifier: Modifier = Modifier) = ProvideTheme(config.theme) {
    val t = LocalTheme.current
    val density = LocalDensity.current
    val dir = LocalLayoutDirection.current
    val cut = WindowInsets.displayCutout
    val cutX = with(density) { max(cut.getLeft(this, dir), cut.getRight(this, dir)).toDp().value }
    val cutY = with(density) { max(cut.getTop(this), cut.getBottom(this)).toDp().value }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val w = maxWidth.value
        val h = maxHeight.value
        val slots = remember(board.layout, w, h, cutX, cutY) { BoardGrid.slots(board.layout, w, h, cutX, cutY) }
        val looks = remember(board, config.styles, slots, t) { looks(board, config, slots, min(w, h), t) }
        val ink = Ink.of(state.night, looks.firstOrNull()?.accent ?: Color(0xFF76A6F3), t.palette)
        CompositionLocalProvider(LocalInk provides ink) {
            if (t.surface == Surface.RULE) {
                // B: one hairline in the middle of every gutter between two filled slots (mockup r5 B columns)
                val rules = remember(slots, board.slots) { BoardGrid.rules(slots.filterIndexed { i, _ -> board.slots.getOrNull(i).orEmpty().isNotEmpty() }) }
                val c = ink.ink3
                Canvas(Modifier.fillMaxSize()) {
                    val sw = maxOf(1f, 1.dp.toPx())
                    for (r in rules) drawLine(c, Offset(r[0].dp.toPx(), r[1].dp.toPx()), Offset(r[2].dp.toPx(), r[3].dp.toPx()), sw)
                }
            }
            slots.forEachIndexed { i, r ->
                val cards = board.slots.getOrNull(i).orEmpty()
                if (cards.isEmpty()) return@forEachIndexed
                SlotStack(
                    cards, state, config, actions,
                    Modifier.offset(r.x.dp, r.y.dp).size(r.w.dp, r.h.dp),
                    slotId = "${board.id}-$i", look = looks[i], dotsAtStart = r.x + r.w / 2 < w / 2,
                )
            }
        }
    }
}

/**
 * One slot: a single card, or a vertical stack (iPhone widget stack, spec W3) with page dots on the slot's outer edge
 * that fade in on change and out 1.5 s after it settles. No ticking while idle.
 */
@Composable
fun SlotStack(
    cards: List<CardType>,
    state: StandbyState,
    config: AppConfig,
    actions: StandbyActions,
    modifier: Modifier = Modifier,
    slotId: String = "stack",
    look: CardLook = CardLook.of(config),
    dotsAtStart: Boolean = false,
) {
    if (cards.size <= 1) {
        cards.firstOrNull()?.let { CardView(it, state, config, actions, modifier, slotId, look) }
        return
    }
    val pager = rememberPagerState { cards.size }
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(pager.settledPage, pager.isScrollInProgress) {
        if (pager.isScrollInProgress) { show = true; return@LaunchedEffect }
        if (show) { delay(Motion.DOTS_HIDE_MS.toLong()); show = false }
    }
    val a by animateFloatAsState(if (show) 1f else 0f, tween(300), label = "dots")
    Box(modifier) {
        VerticalPager(pager, Modifier.fillMaxSize()) { p ->
            CardView(cards[p], state, config, actions, Modifier.fillMaxSize(), "$slotId-$p", look)
        }
        if (a > 0f) {
            val ink = LocalInk.current
            val d: Dp = (look.labelSize ?: 18.dp) * 0.35f
            Column(
                Modifier.align(if (dotsAtStart) Alignment.CenterStart else Alignment.CenterEnd)
                    .offset(x = if (dotsAtStart) -d * 2 else d * 2).alpha(a),
                verticalArrangement = Arrangement.spacedBy(d * 0.8f),
            ) {
                repeat(cards.size) {
                    Box(Modifier.size(d).clip(CircleShape).background(if (it == pager.currentPage) ink.ink else ink.ink3))
                }
            }
        }
    }
}
