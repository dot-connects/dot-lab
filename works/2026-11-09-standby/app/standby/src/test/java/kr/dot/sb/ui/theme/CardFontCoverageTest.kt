package kr.dot.sb.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.Charset

/** plan-r13 A3: every card font maps all 2,350 KS X 1001 syllables (a notification in any card font has no tofu). */
class CardFontCoverageTest {
    private fun ksx1001(): Set<Int> {
        val euc = Charset.forName("EUC-KR")
        val out = HashSet<Int>()
        for (hi in 0xB0..0xC8) for (lo in 0xA1..0xFE) {
            val s = String(byteArrayOf(hi.toByte(), lo.toByte()), euc)
            if (s.length == 1 && s[0] in '가'..'힣') out += s[0].code
        }
        return out
    }

    /** Code points with a glyph in the font's Unicode cmap (format 4 or 12 subtable). */
    private fun cmap(f: File): Set<Int> {
        val b = ByteBuffer.wrap(f.readBytes())
        val tables = b.getShort(4).toInt() and 0xFFFF
        val off = (0 until tables).map { 12 + it * 16 }.first { String(ByteArray(4) { i -> b.get(it + i) }) == "cmap" }
            .let { b.getInt(it + 8) }
        val subs = (0 until (b.getShort(off + 2).toInt() and 0xFFFF)).map { i ->
            val r = off + 4 + i * 8
            off + b.getInt(r + 4)
        }
        val out = HashSet<Int>()
        val f12 = subs.firstOrNull { (b.getShort(it).toInt() and 0xFFFF) == 12 }
        if (f12 != null) {
            val n = b.getInt(f12 + 12)
            for (g in 0 until n) {
                val p = f12 + 16 + g * 12
                for (c in b.getInt(p)..b.getInt(p + 4)) out += c
            }
            return out
        }
        val f4 = subs.first { (b.getShort(it).toInt() and 0xFFFF) == 4 }
        val segs = (b.getShort(f4 + 6).toInt() and 0xFFFF) / 2
        val ends = f4 + 14
        val starts = ends + segs * 2 + 2
        val deltas = starts + segs * 2
        val ranges = deltas + segs * 2
        for (i in 0 until segs) {
            val end = b.getShort(ends + i * 2).toInt() and 0xFFFF
            val start = b.getShort(starts + i * 2).toInt() and 0xFFFF
            val delta = b.getShort(deltas + i * 2).toInt()
            val ro = b.getShort(ranges + i * 2).toInt() and 0xFFFF
            for (c in start..end) {
                if (c == 0xFFFF) continue
                val glyph = if (ro == 0) (c + delta) and 0xFFFF
                else (b.getShort(ranges + i * 2 + ro + (c - start) * 2).toInt() and 0xFFFF).let { if (it == 0) 0 else (it + delta) and 0xFFFF }
                if (glyph != 0) out += c
            }
        }
        return out
    }

    @Test fun everyCardFontCoversKsX1001() {
        val ks = ksx1001()
        assertEquals(2350, ks.size)
        for (name in listOf("card_serif", "card_round", "card_pen")) {
            val have = cmap(File("src/main/res/font/$name.ttf"))
            val missing = ks - have
            assertTrue("$name misses ${missing.size}: ${missing.take(10).map { it.toChar() }}", missing.isEmpty())
        }
    }
}
