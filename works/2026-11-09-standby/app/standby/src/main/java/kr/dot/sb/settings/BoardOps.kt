package kr.dot.sb.settings

import androidx.compose.ui.unit.dp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.ui.theme.Grid

/**
 * Board list edits. Pages are: 0 widgets, 1 photos, 2 clocks, then [AppConfig.boards] from 3 (see AppConfig.startPage),
 * so every list edit has to move startPage with the board it pointed at.
 */
object BoardOps {
    const val FIRST_BOARD_PAGE = 3
    private val PAD = listOf(CardType.WEATHER, CardType.CALENDAR, CardType.AGENDA, CardType.MUSIC)

    /** Make the slot list match the layout's slot count: keep what is there, pad with plain cards, drop extras. */
    fun fit(b: Board): Board {
        val slots = (0 until b.layout.slots).map { i -> b.slots.getOrNull(i)?.takeIf { it.isNotEmpty() } ?: listOf(PAD[i % PAD.size]) }
        return if (slots == b.slots) b else b.copy(slots = slots)
    }

    fun withLayout(b: Board, layout: Layout): Board = fit(b.copy(layout = layout))

    private fun freshId(c: AppConfig): String {
        var n = c.boards.size + 1
        while (c.boards.any { it.id == "b$n" }) n++
        return "b$n"
    }

    fun add(c: AppConfig): AppConfig =
        c.copy(boards = c.boards + Board(freshId(c), "새 화면", Layout.TWO, listOf(listOf(CardType.CLOCK), listOf(CardType.WEATHER))))

    fun duplicate(c: AppConfig, i: Int): AppConfig {
        val src = c.boards.getOrNull(i) ?: return c
        val copy = src.copy(id = freshId(c), name = src.name + " 복사")
        val start = if (c.startPage > FIRST_BOARD_PAGE + i) c.startPage + 1 else c.startPage
        return c.copy(boards = c.boards.toMutableList().apply { add(i + 1, copy) }, startPage = start)
    }

    fun delete(c: AppConfig, i: Int): AppConfig {
        if (i !in c.boards.indices) return c
        val page = FIRST_BOARD_PAGE + i
        val start = when { c.startPage == page -> 2; c.startPage > page -> c.startPage - 1; else -> c.startPage }
        return c.copy(boards = c.boards.filterIndexed { j, _ -> j != i }, startPage = start)
    }

    /** Move board [i] by [delta] (-1 up, +1 down); startPage follows the board it pointed at. */
    fun move(c: AppConfig, i: Int, delta: Int): AppConfig {
        val j = i + delta
        if (i !in c.boards.indices || j !in c.boards.indices) return c
        val list = c.boards.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
        val start = when (c.startPage) { FIRST_BOARD_PAGE + i -> FIRST_BOARD_PAGE + j; FIRST_BOARD_PAGE + j -> FIRST_BOARD_PAGE + i; else -> c.startPage }
        return c.copy(boards = list, startPage = start)
    }

    fun replace(c: AppConfig, id: String, change: (Board) -> Board): AppConfig =
        c.copy(boards = c.boards.map { if (it.id == id) change(it) else it })
}

/** Rectangle in device dp. */
data class RectDp(val x: Float, val y: Float, val w: Float, val h: Float)

/** Slot geometry of a board on a screen (design-system §3.1-3.3), used for the editor's tap targets and disabled layouts. */
object LayoutGeo {
    const val MIN_SLOT = 120f

    /** [w]x[h] is the screen in its current orientation. [coverPortrait] = Fold cover held upright (top/bottom margin 48). */
    fun slots(layout: Layout, w: Float, h: Float, coverPortrait: Boolean = false): List<RectDp> {
        val short = minOf(w, h)
        val m = Grid.margin(short.dp).value
        val g = Grid.gutter(short.dp).value
        val portrait = h > w
        val mv = if (coverPortrait) maxOf(m, 48f) else m
        // lay out in landscape space, then transpose for portrait (columns become rows)
        val lw = if (portrait) h - 2 * mv else w - 2 * m
        val lh = if (portrait) w - 2 * m else h - 2 * m
        val r = landscape(layout, lw, lh, g)
        return if (portrait) r.map { RectDp(m + it.y, mv + it.x, it.h, it.w) } else r.map { RectDp(m + it.x, m + it.y, it.w, it.h) }
    }

    private fun landscape(layout: Layout, w: Float, h: Float, g: Float): List<RectDp> = when (layout) {
        Layout.ONE -> listOf(RectDp(0f, 0f, w, h))
        Layout.TWO -> { val cw = (w - g) / 2; listOf(RectDp(0f, 0f, cw, h), RectDp(cw + g, 0f, cw, h)) }
        Layout.BIG2 -> { val big = (w - g) * 0.6f; val sw = w - g - big; val sh = (h - g) / 2
            listOf(RectDp(0f, 0f, big, h), RectDp(big + g, 0f, sw, sh), RectDp(big + g, sh + g, sw, sh)) }
        Layout.TWO2 -> { val big = (w - g) * 0.6f; val sw = w - g - big; val sh = (h - g) / 2
            listOf(RectDp(0f, 0f, sw, sh), RectDp(0f, sh + g, sw, sh), RectDp(sw + g, 0f, big, h)) }
        Layout.FOUR -> { val cw = (w - g) / 2; val ch = (h - g) / 2
            listOf(RectDp(0f, 0f, cw, ch), RectDp(cw + g, 0f, cw, ch), RectDp(0f, ch + g, cw, ch), RectDp(cw + g, ch + g, cw, ch)) }
        Layout.THREE -> { val cw = (w - 2 * g) / 3; (0..2).map { RectDp(it * (cw + g), 0f, cw, h) } }
    }

    fun smallest(layout: Layout, w: Float, h: Float, coverPortrait: Boolean = false): Float =
        slots(layout, w, h, coverPortrait).minOf { minOf(it.w, it.h) }

    fun disabled(layout: Layout, w: Float, h: Float, coverPortrait: Boolean = false) = smallest(layout, w, h, coverPortrait) < MIN_SLOT
}
