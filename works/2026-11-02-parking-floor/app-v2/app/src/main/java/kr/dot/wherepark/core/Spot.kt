package kr.dot.wherepark.core

sealed interface Floor {
    data class Under(val n: Int, val half: Boolean = false) : Floor
    data class Above(val n: Int, val half: Boolean = false) : Floor
    data object Roof : Floor
    /** Sign floors said as is: M (M층), MZ2 (중2층), G (G층), L1..9, P1..9. */
    data class Named(val code: String) : Floor
}

private fun Boolean.h() = if (this) ".5" else ""

fun Floor.label(): String = when (this) {
    is Floor.Under -> "B$n${half.h()}"
    is Floor.Above -> "$n${half.h()}층"
    Floor.Roof -> "옥상"
    is Floor.Named -> when (code) { "M" -> "M층"; "MZ2" -> "중2층"; "G" -> "G층"; else -> code }
}

fun Floor.wire(): String = when (this) {
    is Floor.Under -> "B$n${half.h()}"
    is Floor.Above -> "$n${half.h()}F"
    Floor.Roof -> "ROOF"
    is Floor.Named -> code
}

/** Same set as the Worker's cleanPark; anything else (a newer app's floor) reads as no floor. */
private val WIRE = Regex("B([1-9])(\\.5)?|([1-9])(\\.5)?F|M|MZ2|G|L[1-9]|P[1-9]|ROOF")

fun floorOfWire(s: String?): Floor? {
    val m = s?.let { WIRE.matchEntire(it) } ?: return null
    val (b, bh, a, ah) = m.destructured
    return when {
        b.isNotEmpty() -> Floor.Under(b.toInt(), bh.isNotEmpty())
        a.isNotEmpty() -> Floor.Above(a.toInt(), ah.isNotEmpty())
        s == "ROOF" -> Floor.Roof
        else -> Floor.Named(s)
    }
}

/** [memo] = the spoken sentence, cleaned (1.1.1); older records hold only the leftover words. */
data class Spot(val floor: Floor?, val zone: String?, val number: Int?, val pillar: String?, val memo: String?)

/** Zone/number chip next to the floor: "C 37", "A-7" (as said), "3-12", "37번", "G7 기둥"; null = none. */
fun Spot.chip(): String? {
    val zn = when {
        zone != null && number != null ->
            if (zone.first().isDigit() || memo?.contains("$zone-$number") == true) "$zone-$number" else "$zone $number"
        number != null -> if (memo?.contains("${number}번") == true) "${number}번" else "$number"
        zone != null -> "${zone}구역"
        else -> null
    }
    return listOfNotNull(zn, pillar?.let { "$it 기둥" }).joinToString(" ").ifEmpty { null }
}

/** The sentence with the floor words taken out (widget: the floor is already big beside it). */
fun Spot.restAfterFloor(): String {
    val m = memo?.trim().orEmpty()
    val f = floor ?: return m
    val words = when (f) {
        is Floor.Under -> "지하 (?:주차장 )?${f.n}${f.half.h().replace(".", "\\.")}층|B${f.n}${f.half.h().replace(".", "\\.")}(?![0-9.])"
        is Floor.Above -> "(?:지상 )?${f.n}${f.half.h().replace(".", "\\.")}층"
        Floor.Roof -> "옥상"
        is Floor.Named -> Regex.escape(f.label())
    }
    return m.replace(Regex("(?<![^\\s,])(?:$words),?"), " ").replace(Regex("\\s+"), " ").trim().trim(',').trim()
}

/** One gentle question on the saved sheet; the default is already saved. */
enum class Ask { NONE, BASEMENT, ZONE_2, WHICH_FLOOR }

data class ParseResult(val spot: Spot?, val ask: Ask, val options: List<Spot>, val carName: String?)
