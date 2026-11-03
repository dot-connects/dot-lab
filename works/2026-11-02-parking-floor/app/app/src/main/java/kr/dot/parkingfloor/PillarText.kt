package kr.dot.parkingfloor

/** Picks the pillar code out of text read from a pillar photo: "C-12", "가-07", "D03". */
object PillarText {
    private val CODE = Regex("""([A-Za-z가-힣])\s*[-–—.]?\s*(\d{1,3})""")

    /** A text block read by OCR with its on-screen size; bigger paint is more likely the code. */
    data class Block(val text: String, val area: Int)

    fun best(blocks: List<Block>, floorLabel: String?): String? {
        val candidates = blocks.flatMap { b -> CODE.findAll(b.text).map { m -> normalize(m.groupValues[1], m.groupValues[2]) to b.area }.toList() }
        val floor = floorLabel?.uppercase()
        return candidates
            .filterNot { (code, _) -> floor != null && code.replace("-", "").equals(floor, ignoreCase = true) }
            .filterNot { (code, _) -> isFloorWord(code) }
            .maxByOrNull { (code, area) -> area * (if (code.first() != 'B') 2 else 1) }
            ?.first
    }

    fun normalize(letter: String, digits: String): String = "${letter.uppercase()}-$digits"

    /** "B-2" and "B-10" read off a pillar are floor marks, not codes. */
    private fun isFloorWord(code: String): Boolean = code.startsWith("B-") && code.removePrefix("B-").length == 1
}
