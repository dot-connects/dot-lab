package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.clock.ClockRegistry

/** Edits of `AppConfig.myClocks` (the clocks page order). Pure; no duplicates, never empty. */
object MyClocks {
    fun add(l: List<FaceId>, f: FaceId): List<FaceId> = if (f in l) l else l + f

    /** The last clock stays. */
    fun remove(l: List<FaceId>, f: FaceId): List<FaceId> = if (l.size <= 1) l else l - f

    /** Moves the clock at [from] to [to], clamped into the list; a bad [from] changes nothing. */
    fun move(l: List<FaceId>, from: Int, to: Int): List<FaceId> {
        if (from !in l.indices) return l
        return l.toMutableList().apply { add(to.coerceIn(0, lastIndex), removeAt(from)) }
    }

    /** 이 시계 쓰기 (rotation on): [f] goes first, added if absent. */
    fun use(l: List<FaceId>, f: FaceId): List<FaceId> = listOf(f) + (l - f)

    /**
     * A tile tap with rotation off (plan-r15 E5): [f] becomes the shown clock `[0]`. A listed [f] moves to 0; otherwise
     * it replaces the old `[0]`, which stays (second) when a clock card draws it ([cardFaces]), so a card never changes
     * face silently. The list never grows from trying faces.
     */
    fun pick(l: List<FaceId>, f: FaceId, cardFaces: Set<FaceId>): List<FaceId> = when {
        f in l || l.isEmpty() -> use(l, f)
        l[0] in cardFaces -> listOf(f) + l
        else -> listOf(f) + l.drop(1)
    }

    /** The faces the clock cards of [c] draw now (boards and the widgets page), resolved like the cards resolve them. */
    fun cardFaces(c: AppConfig): Set<FaceId> {
        val cards = c.widgetLeft + c.widgetRight + c.boards.flatMap { it.slots.flatten() }
        return if (CardType.CLOCK in cards) setOf(ClockRegistry.cardFace(c.clockCardFace ?: FaceId.DIGITAL, c.myClocks)) else emptySet()
    }

    /** The clocks page always follows the whole list. */
    fun pages(c: AppConfig): List<FaceId> = ClockRegistry.pages(c.myClocks)

    /** Page to show in [pages]: [face]'s index, else [current] clamped (the shown clock was removed). */
    fun page(pages: List<FaceId>, face: FaceId?, current: Int): Int =
        pages.indexOf(face).takeIf { it >= 0 } ?: current.coerceIn(0, maxOf(0, pages.lastIndex))
}
