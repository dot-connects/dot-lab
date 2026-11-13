package kr.dot.sb.settings

import kr.dot.sb.model.FaceId

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

    /** Page to show in [pages]: [face]'s index, else [current] clamped (the shown clock was removed). */
    fun page(pages: List<FaceId>, face: FaceId?, current: Int): Int =
        pages.indexOf(face).takeIf { it >= 0 } ?: current.coerceIn(0, maxOf(0, pages.lastIndex))
}
