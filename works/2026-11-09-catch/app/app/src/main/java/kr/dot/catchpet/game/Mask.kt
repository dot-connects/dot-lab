package kr.dot.catchpet.game

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Inclusive box in mask pixels. */
data class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val w get() = x1 - x0 + 1
    val h get() = y1 - y0 + 1
}

/**
 * A low-resolution 1-bit mask of one thing on screen (the segmenter's 90x160 grid, or a critter's ellipse).
 * ANIMAL = any other real animal, named by [species] (an English word the AI prompt can use: "bird", "hamster" ...).
 */
class Mask(
    val w: Int, val h: Int, val bits: BooleanArray, val kind: Kind,
    val critterId: String? = null, val species: String? = null,
) {
    enum class Kind { PERSON, DOG, CAT, ANIMAL, CRITTER }

    /** The dex kind string: person | dog | cat | critter | the species. */
    val dexKind: String get() = species ?: kind.name.lowercase()

    init { require(bits.size == w * h) { "bits ${bits.size} != $w x $h" } }

    val area: Int = bits.count { it }

    val box: Box? = run {
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if (bits[y * w + x]) {
            x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y)
        }
        if (x1 < 0) null else Box(x0, y0, x1, y1)
    }

    fun at(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h && bits[y * w + x]

    /** Does any cell of this mask touch (or sit next to) a cell of [o]? Same grid. */
    fun touches(o: Mask): Boolean {
        for (i in bits.indices) if (bits[i]) {
            val x = i % w; val y = i / w
            for (dy in -1..1) for (dx in -1..1) if (o.at(x + dx, y + dy)) return true
        }
        return false
    }

    /** Every 4-connected piece of this mask, largest first. */
    fun pieces(): List<Mask> {
        val seen = BooleanArray(bits.size)
        val out = mutableListOf<Mask>()
        for (s in bits.indices) if (bits[s] && !seen[s]) {
            val p = BooleanArray(bits.size); val st = ArrayDeque<Int>(); st.add(s); seen[s] = true; p[s] = true
            while (st.isNotEmpty()) {
                val i = st.removeLast(); val x = i % w; val y = i / w
                for ((nx, ny) in arrayOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                    if (nx !in 0 until w || ny !in 0 until h) continue
                    val j = ny * w + nx
                    if (bits[j] && !seen[j]) { seen[j] = true; p[j] = true; st.add(j) }
                }
            }
            out += Mask(w, h, p, kind, critterId, species)
        }
        return out.sortedByDescending { it.area }
    }

    /** This mask's cells plus [o]'s, keeping this mask's kind. */
    fun plus(o: Mask): Mask = Mask(w, h, BooleanArray(bits.size) { bits[it] || o.bits[it] }, kind, critterId, species)

    /** The 4-connected piece of this mask nearest (cx, cy), as its own mask (the whole mask if empty). */
    fun pieceNear(cx: Int, cy: Int): Mask {
        var seed = -1; var best = Long.MAX_VALUE
        for (i in bits.indices) if (bits[i]) {
            val dx = (i % w - cx).toLong(); val dy = (i / w - cy).toLong()
            val d = dx * dx + dy * dy
            if (d < best) { best = d; seed = i }
        }
        if (seed < 0) return this
        val out = BooleanArray(bits.size)
        val stack = ArrayDeque<Int>(); stack.add(seed); out[seed] = true
        while (stack.isNotEmpty()) {
            val i = stack.removeLast(); val x = i % w; val y = i / w
            for ((nx, ny) in arrayOf(x - 1 to y, x + 1 to y, x to y - 1, x to y + 1)) {
                if (nx !in 0 until w || ny !in 0 until h) continue
                val j = ny * w + nx
                if (bits[j] && !out[j]) { out[j] = true; stack.add(j) }
            }
        }
        return Mask(w, h, out, kind, critterId, species)
    }

    /** First row (top down) with a set pixel in columns cx-halfWidth..cx+halfWidth, or null. */
    fun firstRowInColumn(cx: Int, halfWidth: Int = 2): Int? {
        for (y in 0 until h) for (dx in -halfWidth..halfWidth) if (at(cx + dx, y)) return y
        return null
    }

    /** min..max x of set pixels in rows y0 until y0+rows, or null when that band is empty. */
    fun spanInBand(y0: Int, rows: Int): IntRange? {
        var a = w; var b = -1
        for (y in max(0, y0) until min(h, y0 + rows)) for (x in 0 until w) if (bits[y * w + x]) { a = min(a, x); b = max(b, x) }
        return if (b < 0) null else a..b
    }

    companion object {
        /** The ellipse inscribed in box b: stands in when the segmenter misses but the detector or a critter gives a box. */
        fun ellipseIn(w: Int, h: Int, b: Box, kind: Kind, critterId: String? = null, species: String? = null): Mask {
            val bits = BooleanArray(w * h)
            val cx = (b.x0 + b.x1) / 2f; val cy = (b.y0 + b.y1) / 2f
            val rx = max(0.5f, b.w / 2f); val ry = max(0.5f, b.h / 2f)
            for (y in max(0, b.y0)..min(h - 1, b.y1)) for (x in max(0, b.x0)..min(w - 1, b.x1)) {
                val u = (x - cx) / rx; val v = (y - cy) / ry
                if (u * u + v * v <= 1f) bits[y * w + x] = true
            }
            return Mask(w, h, bits, kind, critterId, species)
        }
    }
}

/** Intersection over union of two masks on the same grid. */
fun iou(a: Mask, b: Mask): Float {
    require(a.w == b.w && a.h == b.h)
    var i = 0; var u = 0
    for (k in a.bits.indices) { val p = a.bits[k]; val q = b.bits[k]; if (p && q) i++; if (p || q) u++ }
    return if (u == 0) 0f else i.toFloat() / u
}

/**
 * Holds the last non-empty mask through up to `hold` missed frames (the model drops frames during fast moves:
 * research/seg-compare.md). One tracker per thing kind.
 */
class Tracker(private val hold: Int = 4) {
    private var last: Mask? = null
    private var misses = 0

    fun update(m: Mask?): Mask? {
        if (m != null && m.area > 0) { last = m; misses = 0; return m }
        misses++
        if (misses > hold) last = null
        return last
    }
}

/** 0 at the centre of a span, 1 at (or past) its edge. */
internal fun offCentre(cx: Int, span: IntRange): Float {
    val mid = (span.first + span.last) / 2f
    val half = max(3f, (span.last - span.first) / 2f)
    return min(1f, abs(cx - mid) / half)
}
