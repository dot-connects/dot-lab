package kr.dot.sb.ui.theme

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kr.dot.sb.model.Texture
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * r13 textures (plan-r13 A3, ported from the mockup's `makeTex`): one [N] px tile per kind, drawn once per process with
 * a fixed seed (same pixels on every run and device), repeated by a shader scaled so one tile spans [TILE_DP] dp.
 * Colours are white / black specks and lines as ARGB ints, so a texture reads on any page colour.
 */
object Textures {
    private const val N = 192
    private const val TILE_DP = 96f
    private const val WHITE = 0xFFFFFF
    private const val BLACK = 0x000000
    private val tiles = HashMap<Texture, Bitmap>()

    /** The tile of [k] (not [Texture.NONE]), made on first use. */
    fun tile(k: Texture): Bitmap = synchronized(tiles) { tiles.getOrPut(k) { make(k) } }

    private val brushes = HashMap<Pair<Texture, Float>, ShaderBrush>()

    /** A repeating brush of [k] at [density] (px per dp), made once per pair; null for [Texture.NONE]. */
    fun brush(k: Texture, density: Float): ShaderBrush? {
        if (k == Texture.NONE) return null
        return synchronized(brushes) {
            brushes.getOrPut(k to density) {
                val sh = BitmapShader(tile(k), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                val s = TILE_DP * density / N
                sh.setLocalMatrix(Matrix().apply { setScale(s, s) })
                ShaderBrush(sh)
            }
        }
    }

    private fun argb(alpha: Float, rgb: Int): Int = ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24) or rgb

    private fun make(k: Texture): Bitmap {
        val bmp = Bitmap.createBitmap(N, N, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // Park-Miller, as in the mockup
        var seed = k.ordinal * 977L + 13L
        fun r(): Float { seed = seed * 16807L % 2147483647L; return seed / 2147483647f }
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        fun rect(x: Int, y: Int, w: Int, h: Int, color: Int) { p.color = color; c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p) }
        // per-pixel white/black specks, alpha up to [maxA]/255, composited over what is there
        fun specks(maxA: Int) {
            val px = IntArray(N * N) { val rgb = if (r() > 0.5f) WHITE else BLACK; ((r() * maxA).toInt() shl 24) or rgb }
            c.drawBitmap(Bitmap.createBitmap(px, N, N, Bitmap.Config.ARGB_8888), 0f, 0f, null)
        }
        when (k) {
            Texture.NONE -> {}
            Texture.GRAIN -> specks(70)
            Texture.PAPER -> {
                specks(28)
                p.style = Paint.Style.STROKE
                p.strokeWidth = 0.6f
                repeat(160) {
                    val al = 0.06f + r() * 0.1f
                    p.color = argb(al, if (r() > 0.5f) WHITE else BLACK)
                    val a = r() * 6.28f; val l = 4f + r() * 12f; val sx = r() * N; val sy = r() * N
                    c.drawLine(sx, sy, sx + cos(a) * l, sy + sin(a) * l, p)
                }
            }
            Texture.LINEN -> {
                for (y in 0 until N step 2) rect(0, y, N, 1, argb(0.05f + r() * 0.12f, BLACK))
                for (x in 0 until N step 2) rect(x, 0, 1, N, argb(0.03f + r() * 0.08f, WHITE))
            }
            Texture.METAL -> for (y in 0 until N) { val rgb = if (r() > 0.5f) WHITE else BLACK; rect(0, y, N, 1, argb(r() * 0.14f, rgb)) }
            Texture.FROST -> {
                // r13 step 4: the mockup's 90 blobs of 3–12 px at ≤ 16 % read as dirty glass on 무드등's cards (big
                // specks); many small soft blobs at ≤ 7 % read as frost
                p.maskFilter = BlurMaskFilter(2f, BlurMaskFilter.Blur.NORMAL)
                repeat(220) {
                    p.color = argb(r() * 0.07f, WHITE)
                    val x = r() * N; val y = r() * N
                    val rad = 1.5f + r() * 3.5f
                    // drawn wrapped (r13 Part D): a blob cut at the tile edge showed the tile grid on 무드등's cards
                    for (dx in -1..1) for (dy in -1..1) c.drawCircle(x + dx * N, y + dy * N, rad, p)
                }
                p.maskFilter = null
                specks(10)
            }
            Texture.SCANLINE -> for (y in 0 until N step 3) rect(0, y, N, 1, argb(0.45f, BLACK))
        }
        return bmp
    }
}
