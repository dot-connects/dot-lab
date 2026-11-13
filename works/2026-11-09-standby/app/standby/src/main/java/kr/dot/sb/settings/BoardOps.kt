package kr.dot.sb.settings

import androidx.compose.ui.unit.dp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.model.PAGE_PHOTOS
import kr.dot.sb.model.PAGE_WIDGETS
import kr.dot.sb.model.Presets
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
        val options = b.options.filterKeys { k -> key(k)?.let { (s, j) -> j < (slots.getOrNull(s)?.size ?: 0) && slots[s] == b.slots.getOrNull(s) } == true }
        return if (slots == b.slots && options == b.options) b else b.copy(slots = slots, options = options)
    }

    private fun key(k: String): Pair<Int, Int>? = k.split(':').takeIf { it.size == 2 }?.let { (a, c) -> a.toIntOrNull()?.let { s -> c.toIntOrNull()?.let { s to it } } }

    /**
     * Edit stack [slot] of [b]: [newStack] replaces it and [moved] maps an old stack index to its new one (null =
     * removed). Options follow their card; removed cards drop theirs (r9 D6).
     */
    fun editStack(b: Board, slot: Int, newStack: List<CardType>, moved: (Int) -> Int?): Board {
        if (slot !in b.slots.indices) return b
        val options = b.options.mapNotNull { (k, v) ->
            val (s, j) = key(k) ?: return@mapNotNull null
            if (s != slot) k to v else moved(j)?.takeIf { it in newStack.indices }?.let { "$slot:$it" to v }
        }.toMap()
        return b.copy(slots = b.slots.mapIndexed { i, l -> if (i == slot) newStack else l }, options = options)
    }

    /** Widget ids present in [old] but gone from [new]; the caller releases them (`AppWidgetHost.deleteAppWidgetId`). */
    fun removedWidgetIds(old: AppConfig, new: AppConfig): List<Int> = (widgetIds(old) - widgetIds(new)).toList()

    /** Every widget id the boards hold. */
    fun widgetIds(c: AppConfig): Set<Int> = c.boards.flatMap { b -> b.options.values.mapNotNull { it.widgetId } }.toSet()

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
        // a hosted widget belongs to one slot: the copy gets the options without the id (it asks for its own)
        val copy = src.copy(id = freshId(c), name = src.name + " 복사", options = src.options.mapValues { it.value.copy(widgetId = null) })
        val start = if (c.startPage > FIRST_BOARD_PAGE + i) c.startPage + 1 else c.startPage
        return c.copy(boards = c.boards.toMutableList().apply { add(i + 1, copy) }, startPage = start)
    }

    /** The 작업 책상 board is fixed like 위젯 · 사진 · 시계 (r10): it can be switched off, never deleted. */
    const val DESK = "desk"

    fun deletable(b: Board) = b.id != DESK

    /** Recommended boards that are not in the list (페이지 추가 offers them again after a delete). */
    fun missingPresets(c: AppConfig): List<Board> = Presets.boards().filter { p -> c.boards.none { it.id == p.id } }

    fun addPreset(c: AppConfig, b: Board): AppConfig = if (c.boards.any { it.id == b.id }) c else c.copy(boards = c.boards + b)

    fun delete(c: AppConfig, i: Int): AppConfig {
        if (i !in c.boards.indices || !deletable(c.boards[i])) return c
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

/**
 * One StandBy page. [index] = its place in the full order (0 위젯, 1 사진, 2 시계, then the boards from 3), which is what
 * `AppConfig.startPage` and `StandbyActions.pageChanged` carry, so switching a page off never re-points the start page.
 */
data class PageRef(val index: Int, val board: Board? = null) {
    val id: String get() = board?.id ?: FIXED[index]

    companion object { val FIXED = listOf(PAGE_WIDGETS, PAGE_PHOTOS, "clock") }
}

/** The pager's pages: one rule for StandBy, 처음 보이는 페이지 and the settings preview (r10 Pages on/off). */
object Pages {
    const val CLOCK = 2

    fun all(c: AppConfig): List<PageRef> = (0 until BoardOps.FIRST_BOARD_PAGE).map { PageRef(it) } +
        c.boards.mapIndexed { i, b -> PageRef(BoardOps.FIRST_BOARD_PAGE + i, b) }

    fun isOn(c: AppConfig, p: PageRef): Boolean = p.board?.on ?: (p.index == CLOCK || p.id !in c.pagesOff)

    fun visible(c: AppConfig): List<PageRef> = all(c).filter { isOn(c, it) }

    /** Pager position of the start page; one that is off or gone falls back to the clock page. */
    fun start(c: AppConfig): Int = visible(c).let { v -> v.indexOfFirst { it.index == c.startPage }.takeIf { it >= 0 } ?: v.indexOfFirst { it.index == CLOCK } }

    /** Pager position of full-order [index], or null when that page is off or gone. */
    fun position(c: AppConfig, index: Int): Int? = visible(c).indexOfFirst { it.index == index }.takeIf { it >= 0 }

    /** Switch a page on or off (the clock page stays on). */
    fun setOn(c: AppConfig, p: PageRef, on: Boolean): AppConfig = when {
        p.board != null -> BoardOps.replace(c, p.board.id) { it.copy(on = on) }
        p.index == CLOCK -> c
        else -> c.copy(pagesOff = if (on) c.pagesOff - p.id else c.pagesOff + p.id)
    }
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
