package kr.dot.wherepark.core

/**
 * Sentence-first parser (1.1.1, mockups/v5-listen): the spoken sentence, cleaned, is the record ([Spot.memo]);
 * the floor and the zone/number are picked out only when they are clear. Pure, no Android.
 * Steps: words normalised (letters, Korean numerals, "다시" -> "-") -> corrections -> trailing fillers -> chips.
 */
object SpotParser {
    private val LEAD_FILLERS = setOf("음", "어", "으음", "그", "저")
    private val CORRECTIONS = setOf("아니", "아니다", "말고", "잠깐")
    private val END_WORDS = setOf("있어", "있음", "댔어", "이야", "요", "에", "있어요", "댔어요", "있다")
    private val END_SUFFIX = Regex("(이에요|예요|이야|이요|요|에)$")
    private val EXITS = listOf("출구", "입구", "게이트")
    private const val ZL = "가나다라마바사아자차카타파하"
    private val LETTER_TAILS = listOf("타워", "동", "블록", "존", "라인")
    private val COLOR = mapOf("빨간" to "빨강", "노란" to "노랑", "파란" to "파랑")
    private val SINO_RUN = Regex("[일이삼사오육칠팔구십백공]+")
    private val SINO_UNIT = Regex("([일이삼사오육칠팔구십백공]+)(?:점([일이삼사오육칠팔구]))?(층|번(?!째))(.*)")
    private const val PARTICLES = "(?:이요|이고|이야|이랑|랑|이|요|에|은|는|,)?"
    private val NUM = Regex("(\\d{1,3})(번)?$PARTICLES")
    private val ZONE_GLUED = Regex("([A-Z])(\\d{2,3})(번)?$PARTICLES")
    private val DASH = Regex("([A-Z$ZL]|\\d{1,3})-(\\d{1,3})(번)?$PARTICLES")
    private val PILLAR = Regex("[A-Z$ZL]?\\d{1,3}|[A-Z${ZL}0-9]{1,3}-\\d{1,3}|\\d{1,3}번")
    private val UP_FLOOR = Regex("([1-9])(\\.5)?층.*")

    fun parse(raw: String, carNames: List<String> = emptyList()): ParseResult {
        val hit = carNames.filter { it.isNotBlank() }.firstNotNullOfOrNull { n ->
            Regex(n.trim().split(Regex("\\s+")).joinToString("\\s*") { Regex.escape(it) }).find(raw)?.let { n to it }
        }
        val text = (hit?.let { raw.removeRange(it.second.range) } ?: raw).uppercase()
            .replace(Regex("지하(?=[0-9일이삼사오육칠팔구])"), "지하 ")
            .replace(Regex("(랑|하고)(?=[A-Z0-9])"), "$1 ")
            .replace(Regex("(?<=\\S)다시(?=\\S)"), " 다시 ")
        val chunks = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.dropWhile { it in LEAD_FILLERS }
        var ezone = false
        val words = mutableListOf<String>()
        for ((i, c) in chunks.withIndex()) {
            val n = chunks.getOrNull(i + 1)
            if (c == "이" && n?.startsWith("구역") == true) { words += "E"; ezone = true; continue }
            if (c.startsWith("이구역")) { words += "E" + c.drop(1); ezone = true; continue }
            words += word(c, words.lastOrNull(), n)
        }
        val t = glue(words)
        corrections(t)
        trimEnd(t)
        for (i in 0 until t.size - 1) if (Regex("\\d층에").containsMatchIn(t[i]) && t[i].endsWith("층에")) t[i] = t[i].dropLast(1) + ","
        val memo = t.joinToString(" ").take(80).trim()
        if (memo.isEmpty()) return ParseResult(null, Ask.NONE, emptyList(), hit?.first)
        return chips(t, memo, ezone, hit?.first)
    }

    // ---------- words ----------

    private fun hangul(s: String) = s.all { it in '가'..'힣' }
    private fun numeric(s: String?) = s != null && (s.all(Char::isDigit) || SINO_RUN.matches(s) || SINO_UNIT.matches(s) || Regex("\\d+(층|번).*").matches(s))
    private fun zoneWord(s: String?) = s != null && (Regex("[A-Z]").matches(s) || s in KoNum.ZONE_WORDS || s in COLOR)
    private fun letterCtx(n: String?) = numeric(n) || n == "다시" || n?.startsWith("구역") == true || n?.startsWith("기둥") == true

    /** One spoken chunk -> its written form; [prev] is the already written word before it, [next] the raw chunk after it. */
    private fun word(c: String, prev: String?, next: String?): String {
        Regex("([가-힣]+)(\\d+)").matchEntire(c)?.let { m ->   // "비2"
            (KoNum.normalize(m.groupValues[1]).singleOrNull() as? Letter)?.let { return "${it.c}${m.groupValues[2]}" }
        }
        if (!hangul(c)) return c
        SINO_UNIT.matchEntire(c)?.takeIf { !c.startsWith("이번") }?.let { m ->
            val (num, half, unit, rest) = m.destructured
            val v = sino(num) ?: return c
            return v + (if (half.isNotEmpty()) "." + sino(half) else "") + unit + rest
        }
        if (SINO_RUN.matches(c)) {
            val ctx = zoneWord(prev) || prev == "다시" || next in setOf("층", "번", "다시", "기둥", "구역") ||
                next?.startsWith("층") == true || (next?.startsWith("번") == true && !next.startsWith("번째"))
            return if (ctx) sino(c) ?: c else c
        }
        val toks = KoNum.normalize(c)
        if (next == "번" && toks.size == 1 && toks[0] is Num) return (toks[0] as Num).v.toString()   // "서른일곱 번"
        val first = toks.firstOrNull() as? Letter ?: return letterTail(c) ?: c
        if (toks.size == 1) return if (letterCtx(next) || (c.length >= 2 && c != "아이")) first.c.toString() else c
        if (toks.size == 2 && toks[1] is Num) return "${first.c}${(toks[1] as Num).v}"                    // 비투, 에이치원
        if (toks.size == 2 && toks[1] in listOf(Word("구역"), Word("층"))) return "${first.c}${(toks[1] as Word).s}"   // 비구역
        return letterTail(c) ?: c
    }

    /** "비타워" -> "B타워": a letter name glued to a place word. */
    private fun letterTail(c: String): String? = LETTER_TAILS.firstNotNullOfOrNull { tail ->
        c.removeSuffix(tail).takeIf { it != c && it.isNotEmpty() }?.let { KoNum.normalize(it).singleOrNull() as? Letter }?.let { "${it.c}$tail" }
    }

    /** Sino-Korean digits: positional with 십/백, else read digit by digit ("일공삼" = 103). */
    private fun sino(s: String): String? {
        val d = mapOf('공' to 0, '일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9)
        if ('십' !in s && '백' !in s) return s.map { d.getValue(it) }.joinToString("").trimStart('0').ifEmpty { "0" }
        var total = 0
        var cur = 0
        for (ch in s) when (ch) {
            '십' -> { total += (if (cur == 0) 1 else cur) * 10; cur = 0 }
            '백' -> { total += (if (cur == 0) 1 else cur) * 100; cur = 0 }
            '공' -> return null
            else -> cur = d.getValue(ch)
        }
        return (total + cur).toString()
    }

    /** "2 층" -> "2층", "C 구역" -> "C구역", "3 다시 7" -> "3-7", "G 7 기둥" -> "G7 기둥", "근데" -> ",". */
    private fun glue(w: List<String>): MutableList<String> {
        val t = mutableListOf<String>()
        for (s in w) {
            val last = t.lastOrNull()
            when {
                last != null && last.all(Char::isDigit) && (s == "층" || s == "번" || s.startsWith("층") || (s.startsWith("번") && !s.startsWith("번째"))) -> t[t.lastIndex] = last + s
                last != null && Regex("[A-Z]").matches(last) && s.startsWith("구역") -> t[t.lastIndex] = last + s
                s == "근데" -> if (last != null) t[t.lastIndex] = last.trimEnd(',') + ","
                else -> t += s
            }
        }
        var i = 1
        while (i < t.size - 1) {
            if (t[i] == "다시" && Regex("[A-Z$ZL]|\\d{1,3}").matches(t[i - 1]) && Regex("\\d{1,3}(번)?.*").matches(t[i + 1])) {
                t[i - 1] = t[i - 1] + "-" + t[i + 1]; t.removeAt(i); t.removeAt(i)
            } else i++
        }
        for (k in 0 until t.size - 2) if (Regex("[A-Z]").matches(t[k]) && t[k + 1].all(Char::isDigit) && t[k + 2].startsWith("기둥")) {
            t[k] = t[k] + t[k + 1]; t.removeAt(k + 1); break
        }
        return t
    }

    // ---------- corrections ----------

    private enum class Kind { FLOOR, PAIR, NUM, ZONE }

    private fun floorTok(s: String) = Regex("B[1-9](\\.5)?,?|중2층.*|[MG]층.*|[LP][1-9],?|옥상.*").matches(s) || UP_FLOOR.matches(s)
    private fun zoneTok(s: String?): String? {
        if (s == null) return null
        val b = s.trimEnd(',')
        Regex("([A-Z]|[$ZL]|빨강|주황|노랑|초록|파랑|보라)구역.*").matchEntire(b)?.let { return it.groupValues[1] }
        return if (zoneWord(b)) COLOR[b] ?: b else null
    }
    private fun numTok(s: String?): Int? = s?.let { NUM.matchEntire(it) }?.groupValues?.get(1)?.toInt()

    private fun kind(t: List<String>, j: Int): Kind? = when {
        zoneTok(t[j]) != null && numTok(t.getOrNull(j + 1)) != null -> Kind.PAIR
        floorTok(t[j]) -> Kind.FLOOR
        numTok(t[j]) != null -> Kind.NUM
        Regex(".+구역.*").matches(t[j]) && zoneTok(t[j]) != null -> Kind.ZONE
        else -> null
    }

    /** "X 아니 Y": Y replaces the last X of the same kind; the corrected words leave the sentence too ("지하" stays). */
    private fun corrections(t: MutableList<String>) {
        var i = 0
        while (i < t.size) {
            if (t[i] !in CORRECTIONS) { i++; continue }
            val from = if (t[i] == "잠깐" && t.getOrNull(i - 1) == "아") i - 1 else i
            val k = if (i + 1 < t.size) kind(t, i + 1) else null
            val j = if (k == null) -1 else (from - 1 downTo 0).firstOrNull { kind(t, it) == k } ?: -1
            val start = if (j >= 0) j else from
            repeat(i - start + 1) { t.removeAt(start) }
            i = start
        }
    }

    private fun trimEnd(t: MutableList<String>) {
        while (t.isNotEmpty() && t.last() in END_WORDS) t.removeAt(t.lastIndex)
        if (t.isNotEmpty() && t.last().last() in '가'..'힣' && t.last().length >= 2 && !UP_FLOOR.matches(t.last())) t[t.lastIndex] = t.last().replace(END_SUFFIX, "")
        while (t.isNotEmpty() && t.last().isEmpty()) t.removeAt(t.lastIndex)
        if (t.isNotEmpty()) t[t.lastIndex] = t.last().trimEnd(',')
    }

    // ---------- chips ----------

    /** [raw] keeps the commas ("D 15, 기둥 뒤": 15 is not the pillar). */
    private fun chips(raw: List<String>, memo: String, ezone: Boolean, car: String?): ParseResult {
        val t = raw.map { it.trimEnd(',') }
        val used = mutableSetOf<Int>()
        // floors
        val floors = mutableListOf<Pair<Floor, Boolean>>()   // floor, said with 지상/지하 (no basement question)
        var i = 0
        while (i < t.size) {
            val s = t[i]
            val sure = t.getOrNull(i - 1) == "지상"
            val f: Floor? = when {
                s == "지하" -> {
                    val k = if (t.getOrNull(i + 1) == "주차장") i + 2 else i + 1
                    UP_FLOOR.matchEntire(t.getOrNull(k) ?: "")?.let { m -> used += k; i = k; Floor.Under(m.groupValues[1].toInt(), m.groupValues[2].isNotEmpty()) }
                }
                Regex("B([1-9])(\\.5)?").matches(s) -> Floor.Under(s[1] - '0', s.endsWith(".5"))
                s.startsWith("중2층") -> Floor.Named("MZ2")
                s.startsWith("M층") -> Floor.Named("M")
                s.startsWith("G층") -> Floor.Named("G")
                Regex("[LP][1-9]").matches(s) -> Floor.Named(s)
                s.startsWith("옥상") -> Floor.Roof
                else -> UP_FLOOR.matchEntire(s)?.let { m -> Floor.Above(m.groupValues[1].toInt(), m.groupValues[2].isNotEmpty()) }
            }
            if (f != null) { used += i; floors += f to (s == "지하" || sure) }
            i++
        }
        val distinct = floors.map { it.first }.distinct()
        val floor = distinct.singleOrNull()
        var ask = when {
            distinct.size >= 2 -> Ask.WHICH_FLOOR
            floor is Floor.Above && !floor.half && floors.none { it.second } -> Ask.BASEMENT
            else -> Ask.NONE
        }
        // pillar: "G7 기둥", "3-7 기둥", "4번 기둥", or "기둥 가 3"
        var pillar: String? = null
        val k = t.indexOfFirst { it.startsWith("기둥") }
        if (k > 0 && k - 1 !in used && !raw[k - 1].endsWith(",") && PILLAR.matches(t[k - 1])) { pillar = t[k - 1]; used += k - 1 }
        else if (k >= 0 && zoneTok(t.getOrNull(k + 1)) != null && t.getOrNull(k + 2)?.all(Char::isDigit) == true) {
            pillar = zoneTok(t[k + 1]) + t[k + 2]; used += k + 1; used += k + 2
        }
        // zone + number, only when side by side and clear
        fun freeNum(j: Int) = j !in used && numTok(t[j]) != null &&
            t.getOrNull(j - 1) !in EXITS && EXITS.none { t.getOrNull(j + 1)?.startsWith(it) == true } && (raw[j].endsWith(",") || t.getOrNull(j + 1)?.startsWith("기둥") != true)
        var zone: String? = null
        var number: Int? = null
        for (j in t.indices) {
            if (j in used) continue
            DASH.matchEntire(t[j])?.let { m -> zone = m.groupValues[1]; number = m.groupValues[2].toInt() }
                ?: ZONE_GLUED.matchEntire(t[j])?.let { m -> zone = m.groupValues[1]; number = m.groupValues[2].toInt() }
                ?: zoneTok(t[j])?.takeIf { j + 1 < t.size && freeNum(j + 1) }?.let { z -> zone = z; number = numTok(t[j + 1]) }
            if (zone != null) break
        }
        if (zone == null) {
            zone = t.indices.firstNotNullOfOrNull { j ->
                if (j in used) null
                else if (Regex(".+구역.*").matches(t[j])) zoneTok(t[j])
                else if (t.getOrNull(j + 1)?.startsWith("구역") == true) zoneTok(t[j])
                else null
            }
            number = t.indices.firstOrNull { freeNum(it) }?.let { numTok(t[it]) }
        }
        if (number != null && number !in 1..999) number = null
        if (ask == Ask.NONE && ezone && zone == "E") ask = Ask.ZONE_2
        val spot = Spot(floor, zone, number, pillar?.take(12), memo)
        val options = when (ask) {
            Ask.BASEMENT -> listOf(spot.copy(floor = Floor.Under((floor as Floor.Above).n)))
            Ask.ZONE_2 -> listOf(spot.copy(zone = "2"))
            Ask.WHICH_FLOOR -> distinct.take(2).map { spot.copy(floor = it) }
            Ask.NONE -> emptyList()
        }
        return ParseResult(spot, ask, options, car)
    }
}
