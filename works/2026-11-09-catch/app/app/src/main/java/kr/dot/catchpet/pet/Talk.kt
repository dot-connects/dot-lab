package kr.dot.catchpet.pet

/**
 * What the pet says now and then on the home screen (operator 11-16, spec-extras 1). Pure Kotlin: the service asks
 * [pick] on an unlock, the widget asks [slotLine]. Lines come only from this table.
 */
object Talk {
    enum class Slot { MORNING, NOON, EVENING, NIGHT }

    /** Why it speaks now, ahead of the time-of-day lines. */
    sealed class Why {
        data class NewFriend(val friend: String) : Why()
        object Missed : Why()
        object Charging : Why()
    }

    /** What was said before: when, how many on which day (epoch day), and the last line. */
    data class State(val at: Long = 0L, val day: Long = -1L, val count: Int = 0, val last: String? = null)

    const val GAP_MS = 10 * 60_000L
    const val DAY_MAX = 12
    const val CHANCE = 0.3f
    const val SHOW_MS = 4000L

    fun slot(hour: Int): Slot = when (hour) {
        in 5..10 -> Slot.MORNING
        in 11..16 -> Slot.NOON
        in 17..21 -> Slot.EVENING
        else -> Slot.NIGHT
    }

    private val SLOT_LINES = mapOf(
        Slot.MORNING to listOf("좋은 아침!", "아직 졸려…", "기지개 쭉", "물 한 잔 마셔", "오늘 뭐 뽑을 거야?"),
        Slot.NOON to listOf("밥 먹었어?", "심심해…", "햇빛 좋다", "나 좀 봐 줘", "{name} 여기 있어"),
        Slot.EVENING to listOf("오늘 수고했어", "배고파…", "같이 쉬자", "뭐 먹을 거야?"),
        Slot.NIGHT to listOf("이제 잘 시간이야", "내일 또 놀자", "폰 그만 보고 자", "졸려…"),
    )

    private fun lines(why: Why?, hour: Int): List<String> = when (why) {
        is Why.NewFriend -> listOf("새 친구 생겼다!", "${why.friend} 반가워!")
        Why.Missed -> listOf("보고 싶었어", "요즘 뭐 뽑았어?")
        Why.Charging -> listOf("밥 먹는 중이구나", "나도 충전할래")
        null -> SLOT_LINES.getValue(slot(hour))
    }

    /** One line of the hour for the widget (no situation, no pacing), [i] picks which. */
    fun slotLine(hour: Int, name: String, i: Int): String =
        SLOT_LINES.getValue(slot(hour)).let { it[Math.floorMod(i, it.size)] }.replace("{name}", name)

    /** Whether pacing allows a line now: 10 min since the last one, at most [DAY_MAX] a day. */
    fun allowed(now: Long, day: Long, st: State): Boolean =
        now - st.at >= GAP_MS && (day != st.day || st.count < DAY_MAX)

    /**
     * The line to say now, or null. A situation or an unlock ([sure]) always speaks when pacing allows; a home return only by [CHANCE]
     * (operator 11-16: the 30 % roll on unlock read as "it never talks").
     * Never the same line twice in a row.
     */
    fun pick(now: Long, day: Long, hour: Int, why: Why?, name: String, st: State, rnd: () -> Float, sure: Boolean = false): String? {
        if (!allowed(now, day, st)) return null
        if (why == null && !sure && rnd() >= CHANCE) return null
        val pool = lines(why, hour).map { it.replace("{name}", name) }.filter { it != st.last }
        if (pool.isEmpty()) return null
        return pool[(rnd() * pool.size).toInt().coerceIn(0, pool.size - 1)]
    }

    /** The state after saying [line]. */
    fun said(now: Long, day: Long, st: State, line: String): State =
        State(now, day, if (day == st.day) st.count + 1 else 1, line)
}
