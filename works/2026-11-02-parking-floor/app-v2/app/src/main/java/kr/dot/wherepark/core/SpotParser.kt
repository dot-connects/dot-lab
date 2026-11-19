package kr.dot.wherepark.core

/** Rule parser: spoken/typed parking spot -> Spot. Rule order is fixed (plan-v2 task 1). */
object SpotParser {
    private val FILLERS = setOf("음", "어", "으음")
    private val CORRECTIONS = setOf("아니", "아니다", "말고")
    private val EXITS = setOf("출구", "입구", "게이트")
    private val NOT_MEMO = setOf("층", "구역", "번", "지하", "기둥", "옥상") + CORRECTIONS
    private val PARTICLE = Regex("(.*[0-9층역번])(이요|이고|이야|이|요|에|은|는)")
    private val GLUED = Regex("[가나다라마바]([일이삼사오육칠팔구십]|[0-9])+")
    private val BEON = Word("번")

    private fun isZone(t: Tok) = t is Letter || (t is Word && t.s in KoNum.ZONE_WORDS)
    private fun zoneName(t: Tok) = if (t is Letter) t.c.toString() else (t as Word).s

    fun parse(raw: String, carNames: List<String> = emptyList()): ParseResult {
        val carName = carNames.firstOrNull { it.isNotBlank() && raw.contains(it) }
        // 1. drop particles and fillers before tokenizing
        val chunks = (if (carName != null) raw.replace(carName, " ") else raw)
            .trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .dropWhile { it in FILLERS }
            .map { c -> PARTICLE.matchEntire(c)?.groupValues?.get(1) ?: c }
        val l = chunks.flatMap { KoNum.normalize(it) }.toMutableList()
        val none = ParseResult(null, Ambiguity.NONE, emptyList(), carName)

        // 4. correction: the value after "아니/말고" replaces the previous value of the same kind
        var i = 0
        while (i < l.size) {
            val t = l[i]
            if (t is Word && t.s in CORRECTIONS) {
                val nxt = l.getOrNull(i + 1)
                l.removeAt(i)
                val p = when {
                    nxt is Num -> l.subList(0, i).indexOfLast { it is Num }
                    nxt != null && isZone(nxt) -> l.subList(0, i).indexOfLast { isZone(it) }
                    else -> -1
                }
                if (p >= 0) {
                    l.removeAt(p); i--
                    if (nxt is Num && l.getOrNull(p) == BEON) { l.removeAt(p); i-- }
                }
            } else i++
        }

        // 5. exit memo first: "3번 출구" is not a spot number
        i = 0
        while (i < l.size) {
            val t = l[i]
            if (t is Num) {
                var j = i + 1
                if (l.getOrNull(j) == BEON) j++
                val w = (l.getOrNull(j) as? Word)?.s
                if (w != null && w in EXITS) { l.subList(i, j + 1).clear(); l.add(i, Word("${t.v}번 $w")) }
            }
            i++
        }

        // 6. pillar: "기둥 X N" or "X N 기둥"
        var pillar: String? = null
        val k = l.indexOf(Word("기둥"))
        if (k >= 0) {
            fun pair(a: Int) = a >= 0 && a + 1 < l.size && isZone(l[a]) && l[a + 1] is Num
            val a = if (pair(k + 1)) k + 1 else if (pair(k - 2)) k - 2 else -1
            if (a >= 0) {
                pillar = "${zoneName(l[a])}-${(l[a + 1] as Num).v}".take(12)
                for (idx in listOf(a, a + 1, k).sortedDescending()) l.removeAt(idx)
            } else l.removeAt(k)
        }

        // 7. floor: the first floor keyword wins
        var floor: Floor? = null
        var amb = Ambiguity.NONE
        i = 0
        while (i < l.size && floor == null) {
            val t = l[i]
            val nxt = l.getOrNull(i + 1)
            var n = -1
            var used = 0
            when {
                t == Word("지하") && nxt is Num -> { n = nxt.v; used = if (l.getOrNull(i + 2) == Word("층")) 3 else 2; floor = Floor.Under(n) }
                t is Letter && (t.c == 'B' || t.c == 'P') && nxt is Num -> {
                    n = nxt.v; used = 2; floor = Floor.Under(n)
                    if (t.c == 'P') amb = Ambiguity.P_FLOOR
                }
                t == Word("옥상") -> { used = 1; floor = Floor.Roof }
                t is Num && nxt == Word("층") -> { n = t.v; used = 2; floor = Floor.Above(n); amb = Ambiguity.FLOOR_SIGN }
            }
            if (floor == null) i++ else {
                if (floor !is Floor.Roof && n !in 1..9) return none
                l.subList(i, i + used).clear()
            }
        }

        // 8. zone: letter / Hangul letter / colour word; a lone "이" before "구역" is E or 2
        var zone: String? = null
        var zoneAlt: String? = null
        val zi = l.indices.firstOrNull { x -> isZone(l[x]) || (l[x].let { it is Num && it.alt != null } && l.getOrNull(x + 1) == Word("구역")) }
        if (zi != null) {
            val t = l[zi]
            if (t is Num) {
                zone = "E"; zoneAlt = t.v.toString()
                if (amb == Ambiguity.NONE) amb = Ambiguity.ZONE_E_OR_2
            } else zone = zoneName(t)
            l.removeAt(zi)
            if (l.getOrNull(zi) == Word("구역")) l.removeAt(zi)
        }

        // 9. number 1..999 (digit-by-digit readings are concatenated)
        var number: Int? = null
        val ni = l.indexOfFirst { it is Num }
        if (ni >= 0) {
            val first = l.removeAt(ni) as Num
            var v = first.v
            while (first.digitsRead && (l.getOrNull(ni) as? Num)?.digitsRead == true) v = v * 10 + (l.removeAt(ni) as Num).v
            if (l.getOrNull(ni) == BEON) l.removeAt(ni)
            if (v in 1..999) number = v
        }

        // 10. zone letter and number glued into one Hangul chunk ("가삼")
        if (amb == Ambiguity.NONE && zone != null && number != null && chunks.any { GLUED.matches(it) }) amb = Ambiguity.JOINED

        // 11. leftover words = memo
        val memo = l.filterIsInstance<Word>().map { it.s }.filter { it !in NOT_MEMO }.joinToString(" ").take(40).ifEmpty { null }

        if (floor == null && pillar == null && !(amb == Ambiguity.JOINED && zone != null && number != null)) return none
        val spot = Spot(floor, zone, number, pillar, memo)
        val options = when (amb) {
            Ambiguity.FLOOR_SIGN -> listOf(spot.copy(floor = Floor.Under((floor as Floor.Above).n)), spot)
            Ambiguity.ZONE_E_OR_2 -> listOf(spot, spot.copy(zone = zoneAlt))
            Ambiguity.NONE -> emptyList()
            else -> listOf(spot)
        }
        return ParseResult(spot, amb, options, carName)
    }
}
