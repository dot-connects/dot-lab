package kr.dot.wherepark.core

sealed interface Tok
data class Num(val v: Int, val korean: Boolean = false, val digitsRead: Boolean = false, val alt: Char? = null) : Tok
data class Letter(val c: Char, val fromSyllable: String? = null) : Tok
data class Word(val s: String) : Tok

/** Spoken Korean text -> tokens (numbers, letters, words). Pure, no Android. */
object KoNum {
    private val KEYWORDS = listOf("아니다", "말고", "아니", "지하", "구역", "출구", "입구", "게이트", "기둥", "옥상", "층", "번")
    private val COLORS = mapOf(
        "빨강" to "빨강", "빨간" to "빨강", "주황" to "주황", "노랑" to "노랑", "노란" to "노랑",
        "초록" to "초록", "파랑" to "파랑", "파란" to "파랑", "보라" to "보라",
    ).entries.sortedByDescending { it.key.length }
    internal val ZONE_WORDS: Set<String> = COLORS.map { it.value }.toSet() + "가나다라마바".map { it.toString() }
    private val ENG = mapOf(
        "파이브" to 5, "쓰리" to 3, "세븐" to 7, "나인" to 9, "식스" to 6, "에잇" to 8, "원" to 1, "투" to 2, "포" to 4,
    ).entries.sortedByDescending { it.key.length }
    private val LETTERS = mapOf(
        "에이치" to 'H', "더블유" to 'W', "에이" to 'A', "에프" to 'F', "에스" to 'S', "엑스" to 'X',
        "제이" to 'J', "제트" to 'Z', "케이" to 'K', "브이" to 'V', "아이" to 'I', "와이" to 'Y', "비" to 'B', "씨" to 'C',
        "시" to 'C', "디" to 'D', "지" to 'G', "엘" to 'L', "엠" to 'M', "엔" to 'N', "피" to 'P', "큐" to 'Q',
        "알" to 'R', "티" to 'T', "유" to 'U',
    ).entries.sortedByDescending { it.key.length }
    private val NATIVE = Regex("(열|스물|스무|서른|마흔|쉰|예순|일흔|여든|아흔)?(하나|한|둘|두|셋|세|넷|네|다섯|여섯|일곱|여덟|아홉)?")
    private val TENS = mapOf("열" to 10, "스물" to 20, "스무" to 20, "서른" to 30, "마흔" to 40, "쉰" to 50, "예순" to 60, "일흔" to 70, "여든" to 80, "아흔" to 90)
    private val UNITS = mapOf(
        "하나" to 1, "한" to 1, "둘" to 2, "두" to 2, "셋" to 3, "세" to 3, "넷" to 4, "네" to 4,
        "다섯" to 5, "여섯" to 6, "일곱" to 7, "여덟" to 8, "아홉" to 9,
    )
    private const val SINO = "일이삼사오육칠팔구십백"
    private val SINO_DIGIT = mapOf('일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9)

    fun normalize(text: String): List<Tok> = Regex("[0-9]+|[A-Za-z]|[가-힣]+").findAll(text).flatMap { m ->
        val s = m.value
        when {
            s[0].isDigit() -> listOf(s.toIntOrNull()?.let { Num(it) } ?: Word(s))
            s[0] in 'A'..'z' -> listOf(Letter(s[0].uppercaseChar()))
            else -> seg(s, 0) ?: listOf(Word(s))
        }
    }.toList()

    private fun sino(run: String): List<Tok> {
        if ('십' in run || '백' in run) {
            var total = 0
            var cur = 0
            for (ch in run) when (ch) {
                '십' -> { total += (if (cur == 0) 1 else cur) * 10; cur = 0 }
                '백' -> { total += (if (cur == 0) 1 else cur) * 100; cur = 0 }
                else -> cur = SINO_DIGIT.getValue(ch)
            }
            return listOf(Num(total + cur, korean = true))
        }
        if (run.length == 1) return listOf(Num(SINO_DIGIT.getValue(run[0]), korean = true, alt = if (run == "이") 'E' else null))
        return run.map { Num(SINO_DIGIT.getValue(it), korean = true, digitsRead = true) }
    }

    /** Segment a Hangul run with the lexicon (first full cover in priority order); null = not a known phrase. */
    private fun seg(s: String, i: Int): List<Tok>? {
        if (i == s.length) return emptyList()
        fun next(n: Int, t: Tok): List<Tok>? = seg(s, i + n)?.let { listOf(t) + it }
        for (k in KEYWORDS) if (s.startsWith(k, i)) next(k.length, Word(k))?.let { return it }
        for ((k, v) in COLORS) if (s.startsWith(k, i)) next(k.length, Word(v))?.let { return it }
        for ((k, v) in ENG) if (s.startsWith(k, i)) next(k.length, Num(v))?.let { return it }
        for ((k, v) in LETTERS) if (s.startsWith(k, i)) next(k.length, Letter(v, k))?.let { return it }
        NATIVE.matchAt(s, i)?.takeIf { it.value.isNotEmpty() }?.let { m ->
            val v = (m.groupValues[1].takeIf { it.isNotEmpty() }?.let { TENS.getValue(it) } ?: 0) +
                (m.groupValues[2].takeIf { it.isNotEmpty() }?.let { UNITS.getValue(it) } ?: 0)
            next(m.value.length, Num(v, korean = true))?.let { return it }
        }
        if (s[i] in "가나다라마바") next(1, Word(s[i].toString()))?.let { return it }
        var j = i
        while (j < s.length && s[j] in SINO) j++
        for (e in j downTo i + 1) seg(s, e)?.let { return sino(s.substring(i, e)) + it }
        return null
    }
}
