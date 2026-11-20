package kr.dot.wherepark.core

import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

enum class Shape { ROW, POSTER, PILLAR, DOT, TICKET }

data class Style(
    val bg: Int, val fg: Int, val mu: Int, val acc: Int, val onAcc: Int, val fl1: Int, val fl2: Int,
    val radiusDp: Int, val family: String, val material: String, val font: String,
)

object Contrast {
    const val WHITE = 0xFFFFFFFF.toInt()
    const val INK = 0xFF111111.toInt()

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * ch(c shr 16 and 255) + 0.7152 * ch(c shr 8 and 255) + 0.0722 * ch(c and 255)
    }

    fun ratio(a: Int, b: Int): Double { val x = lum(a); val y = lum(b); return (max(x, y) + 0.05) / (min(x, y) + 0.05) }

    /** [top] at [alpha] over opaque [under], result opaque. */
    fun blend(top: Int, alpha: Double, under: Int): Int {
        fun m(s: Int) = Math.round((top shr s and 255) * alpha + (under shr s and 255) * (1 - alpha)).toInt()
        return (0xFF shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    fun readableOn(bg: Int): Int = if (ratio(WHITE, bg) >= ratio(INK, bg)) WHITE else INK
}

object Palette {
    private const val DEFAULT_FL1 = 0xFF2F7D4F.toInt()
    private const val DEFAULT_FL2 = 0xFF2D5DA8.toInt()
    private val FAMILIES = listOf("pastel", "bold", "deep", "mono", "comp")
    private val MATERIALS = listOf("matte", "gloss", "paper", "metal", "soft")
    private val RADII = listOf(10, 16, 22, 28, 34)
    private const val DOTO = "Doto"
    private val FONTS = listOf("Inter Tight", "Big Shoulders Display", "IBM Plex Mono", "Playfair Display", "Chakra Petch")

    // Values from mockups/v2/round2/src.html: .wx base (ROW), .wx.kT, .wx.kF, .wx.kA, .wx.kP
    fun default(shape: Shape): Style = when (shape) {
        Shape.ROW -> d(0xFFF4F3F0, 0xFF161616, 0xFF5D5B57, 0xFFFF6A13, "Inter Tight", 22)
        Shape.POSTER -> d(0xFF121212, 0xFFF5F2EC, 0xFFACA79E, 0xFFFF5A1F, "Inter Tight", 20)
        Shape.PILLAR -> d(0xFFE9E7E2, 0xFF161616, 0xFF5D5B57, 0xFF161616, "Big Shoulders Display", 22)
        Shape.DOT -> d(0xFF1C1C1F, 0xFFF2F2F2, 0xFFB2B2B2, 0xFF2A2A2E, DOTO, 18)
        Shape.TICKET -> d(0xFFF5F0E4, 0xFF1E1B16, 0xFF645D50, 0xFF1E1B16, "IBM Plex Mono", 10)
    }

    private fun d(bg: Long, fg: Long, mu: Long, acc: Long, font: String, r: Int) =
        Style(bg.toInt(), fg.toInt(), mu.toInt(), acc.toInt(), Contrast.readableOn(acc.toInt()), DEFAULT_FL1, DEFAULT_FL2, r, "default", "matte", font)

    private fun hsl(h: Double, s0: Double, l0: Double): Int {
        val s = s0 / 100; val l = l0 / 100; val a = s * min(l, 1 - l)
        fun f(n: Int): Int { val k = (n + h / 30) % 12; val v = l - a * max(-1.0, min(min(k - 3, 9 - k), 1.0)); return Math.round(v * 255).toInt() }
        return (0xFF shl 24) or (f(0) shl 16) or (f(8) shl 8) or f(4)
    }

    private fun <T> pickNot(l: List<T>, cur: T?, r: Random): T { val o = l.filter { it != cur }; return o[r.nextInt(o.size)] }
    private fun Random.range(a: Double, b: Double) = a + nextDouble() * (b - a)

    fun shuffle(shape: Shape, prev: Style?, rnd: Random): Style {
        val fam = pickNot(FAMILIES, prev?.family, rnd)
        repeat(300) {
            val h = rnd.range(0.0, 360.0)
            val bg: Int
            val acc: Int
            when (fam) {
                "pastel" -> { bg = hsl(h, rnd.range(35.0, 75.0), rnd.range(82.0, 92.0)); acc = hsl((h + rnd.range(150.0, 210.0)) % 360, rnd.range(55.0, 85.0), rnd.range(42.0, 55.0)) }
                "bold" -> { bg = hsl(h, rnd.range(60.0, 95.0), rnd.range(42.0, 58.0)); acc = if (rnd.nextDouble() < .5) Contrast.INK else Contrast.WHITE }
                "deep" -> { bg = hsl(h, rnd.range(35.0, 75.0), rnd.range(12.0, 24.0)); acc = hsl((h + rnd.range(20.0, 60.0)) % 360, rnd.range(70.0, 95.0), rnd.range(55.0, 68.0)) }
                "mono" -> { bg = hsl(h, rnd.range(0.0, 6.0), if (rnd.nextDouble() < .5) rnd.range(88.0, 97.0) else rnd.range(8.0, 18.0)); acc = if (rnd.nextDouble() < .6) 0xFFFF6A13.toInt() else hsl(rnd.range(0.0, 360.0), rnd.range(70.0, 90.0), rnd.range(48.0, 58.0)) }
                else -> { bg = hsl(h, rnd.range(45.0, 80.0), rnd.range(30.0, 70.0)); acc = hsl((h + 180) % 360, rnd.range(60.0, 90.0), rnd.range(45.0, 65.0)) }
            }
            val fg = Contrast.readableOn(bg)
            val mu = Contrast.blend(fg, if (fg == Contrast.WHITE) .86 else .76, bg)
            val fl1 = hsl(rnd.range(0.0, 360.0), rnd.range(45.0, 80.0), rnd.range(28.0, 40.0))
            val fl2 = hsl(rnd.range(0.0, 360.0), rnd.range(45.0, 80.0), rnd.range(28.0, 40.0))
            if (Contrast.ratio(fg, bg) < 4.5 || Contrast.ratio(mu, bg) < 4.5 || Contrast.ratio(acc, bg) < (if (shape == Shape.POSTER) 3.0 else 1.6) ||
                Contrast.ratio(Contrast.WHITE, fl1) < 4.5 || Contrast.ratio(Contrast.WHITE, fl2) < 4.5) return@repeat
            val mat = pickNot(MATERIALS, prev?.material, rnd)
            // Doto is DOT-only (a dotted B reads as 8); other shapes never get it
            val font = if (shape == Shape.DOT) DOTO else pickNot(FONTS, prev?.font, rnd)
            return Style(bg, fg, mu, acc, Contrast.readableOn(acc), fl1, fl2, RADII[rnd.nextInt(RADII.size)], fam, mat, font)
        }
        return default(shape)
    }
}
