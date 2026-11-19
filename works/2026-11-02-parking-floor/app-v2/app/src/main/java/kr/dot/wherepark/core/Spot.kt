package kr.dot.wherepark.core

sealed interface Floor {
    data class Under(val n: Int) : Floor
    data class Above(val n: Int) : Floor
    data object Roof : Floor
}

fun Floor.label(): String = when (this) {
    is Floor.Under -> "B$n"
    is Floor.Above -> "${n}F"
    Floor.Roof -> "옥상"
}

fun Floor.wire(): String = when (this) {
    is Floor.Under -> "B$n"
    is Floor.Above -> "${n}F"
    Floor.Roof -> "ROOF"
}

fun floorOfWire(s: String?): Floor? = when {
    s == null -> null
    s == "ROOF" -> Floor.Roof
    Regex("B[1-9]").matches(s) -> Floor.Under(s[1] - '0')
    Regex("[1-9]F").matches(s) -> Floor.Above(s[0] - '0')
    else -> null
}

data class Spot(val floor: Floor?, val zone: String?, val number: Int?, val pillar: String?, val memo: String?)

enum class Ambiguity { NONE, FLOOR_SIGN, ZONE_E_OR_2, P_FLOOR, JOINED }

data class ParseResult(val spot: Spot?, val ambiguity: Ambiguity, val options: List<Spot>, val carName: String?)
