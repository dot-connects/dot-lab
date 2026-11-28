package kr.dot.catchpet.ui

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kr.dot.catchpet.R
import kr.dot.catchpet.game.Capsule
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The felt look of the catch screens (operator 11-28, mockups/next7v4 v5.2): the wool claw, the red felt button, the
 * pink gashapon tinted per catch, the wooden rail, felt pills (chips, toast, "찾았다" label), wool puffs, and the felt
 * panorama of the camera-off world. Pictures are decoded once ([load]); everything draws in dp on the caller's canvas.
 * Main thread only (shared paints).
 */
object FeltArt {
    private var claw: Bitmap? = null
    private var cap: Bitmap? = null
    private var button: Bitmap? = null
    private var pano: Bitmap? = null
    private var tex: Bitmap? = null
    private var skyC = 0xFFA9D3EE.toInt()
    private var grassC = 0xFF9CC25A.toInt()

    /** Decode the pictures once (idempotent). */
    fun load(res: Resources) {
        if (claw != null) return
        val o = BitmapFactory.Options().apply { inScaled = false }
        claw = BitmapFactory.decodeResource(res, R.drawable.felt_claw, o)
        cap = BitmapFactory.decodeResource(res, R.drawable.felt_cap, o)
        button = BitmapFactory.decodeResource(res, R.drawable.felt_button, o)
        tex = BitmapFactory.decodeResource(res, R.drawable.felt_tex, o)
    }

    /** The panorama is big (3980 x 1000): only the camera-off world loads it, 16-bit. */
    fun loadPano(res: Resources) {
        if (pano != null) return
        val o = BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = Bitmap.Config.RGB_565 }
        pano = BitmapFactory.decodeResource(res, R.drawable.felt_pano, o)?.also { p ->
            skyC = p.getPixel(p.width / 3, 4); grassC = p.getPixel(p.width / 3, p.height - 4)
        }
    }

    private val bmp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val src = Rect()
    private val dst = RectF()
    private val path = Path()

    val INK = 0xFF3B2E22.toInt()
    val CREAM = 0xFFFBF3E2.toInt()
    val PAPER = 0xFFFFFAF0.toInt()
    val SUN = 0xFFF6C744.toInt()
    private val THREAD = 0x886E5640.toInt()

    // ---------------- claw ----------------

    const val CLAW_W = 96f
    /** Claw picture top -> prong tips at CLAW_W (mockup TIP 108 at 96 px). */
    const val CLAW_TIP = 108f
    val clawH get() = claw?.let { CLAW_W * it.height / it.width } ?: 123.5f

    /** The string from the rail down to the claw picture's top. */
    fun string(cv: Canvas, cx: Float, top: Float, bottom: Float) {
        if (bottom <= top) return
        fill.color = Color.BLACK   // a shader still takes the paint's alpha
        fill.shader = LinearGradient(cx - 1.5f, 0f, cx + 1.5f, 0f, intArrayOf(0xFFD8C7A5.toInt(), 0xFFFFFAF0.toInt(), 0xFFCDB995.toInt()), null, Shader.TileMode.CLAMP)
        cv.drawRect(cx - 1.5f, top, cx + 1.5f, bottom, fill); fill.shader = null
    }

    /**
     * The claw picture with its top at [top]: [close] squeezes it sideways (1 = shut, 1.32 = snapped on air), [squash]
     * flattens it. [front] draws only the middle prong (the one facing us) so a catch can sit behind it and in front of
     * the side prongs (mockup clawFront: x 39-61 %, y below 64 %).
     */
    fun claw(cv: Canvas, cx: Float, top: Float, close: Float, squash: Float, front: Boolean) {
        val b = claw ?: return
        val h = clawH
        cv.save()
        cv.scale(1f - 0.16f * close, 1f - squash, cx, top + h * 0.28f)
        if (front) cv.clipRect(cx - CLAW_W / 2 + CLAW_W * 0.39f, top + h * 0.64f, cx - CLAW_W / 2 + CLAW_W * 0.61f, top + h)
        dst.set(cx - CLAW_W / 2, top, cx + CLAW_W / 2, top + h)
        cv.drawBitmap(b, null, dst, bmp)
        cv.restore()
    }

    // ---------------- capsule ----------------

    /** How one capsule looks this frame; the game fills it in (reused, no allocation per frame). */
    class CapLook {
        var x = 0f; var y = 0f; var rot = 0f; var sx = 1f; var sy = 1f
        var fromBottom = false          // squash about its bottom (bouncing) instead of its centre
        var color = 0
        var lid = 0f                    // lid angle on its hinge, degrees (0 = shut)
        var popS = 1f; var popDy = 0f; var popA = 1f
        var floor = Float.NaN           // shadow on this floor line, NaN = none
        var content: Bitmap? = null
        var showContent = true
        // the halves flying apart when it pops open (slip)
        var topDx = 0f; var topDy = 0f; var topRot = 0f; var topA = 1f
        var botDx = 0f; var botDy = 0f; var botRot = 0f; var botA = 1f
    }

    private val hues = HashMap<Int, ColorMatrixColorFilter>()

    /** CSS hue-rotate from the picture's pink to the catch's colour (the dex stores the colour). */
    private fun hueOf(color: Int): ColorMatrixColorFilter = hues.getOrPut(color) {
        val hsv = FloatArray(3); Color.colorToHSV(color, hsv)
        val a = Math.toRadians((hsv[0] - CAP_HUE).toDouble())
        val c = cos(a).toFloat(); val s = sin(a).toFloat()
        ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            .213f + c * .787f - s * .213f, .715f - c * .715f - s * .715f, .072f - c * .072f + s * .928f, 0f, 0f,
            .213f - c * .213f + s * .143f, .715f + c * .285f + s * .140f, .072f - c * .072f - s * .283f, 0f, 0f,
            .213f - c * .213f - s * .787f, .715f - c * .715f + s * .715f, .072f + c * .928f + s * .072f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
    }
    private const val CAP_HUE = 335f

    /** The content box inside the capsule (mockup .k-con: inset 20 % 21 % 16 %), relative to its centre. */
    fun contentBox(out: RectF) = out.set(-Capsule.W / 2 + Capsule.W * 0.21f, -Capsule.H / 2 + Capsule.H * 0.2f,
        Capsule.W / 2 - Capsule.W * 0.21f, Capsule.H / 2 - Capsule.H * 0.16f)

    /** Scale that fits a w x h picture into the content box. */
    fun contentScale(w: Float, h: Float): Float = min(Capsule.W * 0.58f / max(1f, w), Capsule.H * 0.64f / max(1f, h))

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1D140C.toInt(); maskFilter = BlurMaskFilter(4f, BlurMaskFilter.Blur.NORMAL) }

    fun capsule(cv: Canvas, k: CapLook) {
        val b = cap ?: return
        val w = Capsule.W; val h = Capsule.H
        if (!k.floor.isNaN()) {
            val hh = ((k.floor - (k.y + h / 2)) / 260f).coerceIn(0f, 1f)
            val s = 1f - hh * 0.5f
            shadowPaint.alpha = (255 * 0.55f * (1f - hh * 0.8f)).toInt()
            val sy = k.floor - 5f
            cv.drawOval(k.x - 35f * s, sy - 7f * s, k.x + 35f * s, sy + 7f * s, shadowPaint)
        }
        cv.save()
        cv.translate(k.x, k.y)
        cv.rotate(k.rot)
        if (k.fromBottom) cv.scale(k.sx, k.sy, 0f, h / 2) else cv.scale(k.sx, k.sy)
        // pop-in moves and scales both halves together
        cv.translate(0f, k.popDy); cv.scale(k.popS, k.popS)
        val con = k.content
        if (con != null && k.showContent) {
            contentBox(dst)
            val s = min(dst.width() / con.width, dst.height() / con.height)
            val cw = con.width * s; val ch = con.height * s
            val cx = dst.centerX(); val cy = dst.centerY()
            dst.set(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2)
            cv.drawBitmap(con, null, dst, bmp)
        }
        bmp.colorFilter = hueOf(k.color)
        // the solid bottom half (below 52 %)
        cv.save()
        cv.translate(k.botDx, k.botDy); cv.rotate(k.botRot)
        cv.clipRect(-w / 2, -h / 2 + h * 0.52f, w / 2, h / 2)
        bmp.alpha = (255 * k.popA * k.botA).toInt()
        dst.set(-w / 2, -h / 2, w / 2, h / 2); cv.drawBitmap(b, null, dst, bmp)
        cv.restore()
        // the see-through lid (above 54 %), hinged on the right rim
        cv.save()
        cv.translate(k.topDx, k.topDy); cv.rotate(k.topRot)
        cv.rotate(k.lid, -w / 2 + w * 0.95f, -h / 2 + h * 0.53f)
        cv.clipRect(-w / 2, -h / 2, w / 2, -h / 2 + h * 0.54f)
        bmp.alpha = (255 * 0.72f * k.popA * k.topA).toInt()
        cv.drawBitmap(b, null, dst, bmp)
        cv.restore()
        bmp.alpha = 255; bmp.colorFilter = null
        cv.restore()
    }

    // ---------------- button ----------------

    const val BTN_W = 104f
    val btnH get() = button?.let { BTN_W * it.height / it.width } ?: 98f
    private val grey = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0.15f); postConcat(ColorMatrix().apply { setScale(0.88f, 0.88f, 0.88f, 1f) }) })

    /** The red felt button with its top-left at (x, y) and the "집기" felt tag under it; grey while the claw is busy. */
    fun button(cv: Canvas, x: Float, y: Float, ready: Boolean, pressed: Boolean) {
        val b = button ?: return
        val h = btnH
        cv.save()
        if (pressed && ready) { cv.translate(0f, 4f); cv.scale(0.93f, 0.93f, x + BTN_W / 2, y + h / 2) }
        shadowPaint.alpha = 70
        cv.drawOval(x + 10f, y + h - 18f, x + BTN_W - 10f, y + h + 4f, shadowPaint)
        if (!ready) bmp.colorFilter = grey
        dst.set(x, y, x + BTN_W, y + h); cv.drawBitmap(b, null, dst, bmp)
        bmp.colorFilter = null
        cv.restore()
        text.textSize = 14f
        val tw = text.measureText("집기") + 24f
        dst.set(x + BTN_W / 2 - tw / 2, y + h - 6f, x + BTN_W / 2 + tw / 2, y + h + 16f)
        pill(cv, dst, CREAM, 10f, stitch = false)
        text.color = INK; cv.drawText("집기", dst.centerX(), dst.bottom - 6f, text)
    }

    // ---------------- felt pills, rail ----------------

    private var texShader: BitmapShader? = null

    /** A felt pill: soft drop, the colour, wool texture (soft light), a dashed stitch inside. [a] fades it all. */
    fun pill(cv: Canvas, r: RectF, color: Int, radius: Float = r.height() / 2, stitch: Boolean = true, a: Float = 1f) {
        val al = (255 * a).toInt().coerceIn(0, 255)
        fill.shader = null
        fill.color = 0x336E5640; fill.alpha = (0x33 * a).toInt()
        cv.drawRoundRect(r.left, r.top + 2f, r.right, r.bottom + 2f, radius, radius, fill)
        fill.color = color; fill.alpha = al * Color.alpha(color) / 255
        cv.drawRoundRect(r, radius, radius, fill)
        tex?.let { t ->
            val sh = texShader ?: BitmapShader(t, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).also {
                it.setLocalMatrix(Matrix().apply { setScale(150f / t.width, 150f / t.width) }); texShader = it
            }
            fill.shader = sh; fill.blendMode = BlendMode.SOFT_LIGHT; fill.alpha = (al * 0.9f).toInt()
            cv.drawRoundRect(r, radius, radius, fill)
            fill.shader = null; fill.blendMode = null
        }
        if (stitch) {
            stroke.strokeWidth = 1.4f; stroke.color = THREAD; stroke.alpha = (0x88 * a).toInt()
            stroke.pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
            val i = 3.5f
            cv.drawRoundRect(r.left + i, r.top + i, r.right - i, r.bottom - i, max(0f, radius - i), max(0f, radius - i), stroke)
            stroke.pathEffect = null
        }
    }

    /** The wooden rail across the top, centred on [y]. */
    fun rail(cv: Canvas, w: Float, y: Float) {
        fill.shader = null; fill.color = 0x555A3A1A
        cv.drawRoundRect(-10f, y - 8f + 3f, w + 10f, y + 8f + 3f, 8f, 8f, fill)
        fill.color = Color.BLACK
        fill.shader = LinearGradient(0f, y - 8f, 0f, y + 8f, intArrayOf(0xFFE7C79A.toInt(), 0xFFC99A62.toInt(), 0xFFA87A47.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRoundRect(-10f, y - 8f, w + 10f, y + 8f, 8f, 8f, fill); fill.shader = null
        stroke.strokeWidth = 2f; stroke.color = 0x807A5530.toInt(); stroke.pathEffect = DashPathEffect(floatArrayOf(6f, 8f), 0f)
        cv.drawLine(14f, y - 1f, w - 14f, y - 1f, stroke); stroke.pathEffect = null
        stroke.strokeWidth = 1f; stroke.color = 0x66FFFFFF
        cv.drawLine(0f, y - 7.5f, w, y - 7.5f, stroke)
    }

    // ---------------- world panorama ----------------

    /**
     * The felt panorama as a 360° ring: [x0] is the screen x of azimuth 0, [period] the px of a full turn, its horizon
     * (52 % of the picture) on [hz]. Sky and grass colours fill whatever tilting uncovers.
     */
    fun pano(cv: Canvas, w: Float, h: Float, x0: Float, period: Float, hz: Float) {
        val p = pano ?: return
        val ph = period * p.height / p.width
        val top = hz - 0.52f * ph
        fill.shader = null
        if (top > 0f) { fill.color = skyC; cv.drawRect(0f, 0f, w, top, fill) }
        if (top + ph < h) { fill.color = grassC; cv.drawRect(0f, top + ph, w, h, fill) }
        val k = p.width / period
        var start = ((x0 % period) + period) % period - period
        while (start < w) {
            val l = max(0f, start); val r = min(w, start + period)
            if (r > l) {
                src.set(((l - start) * k).toInt(), 0, ((r - start) * k).toInt().coerceAtMost(p.width), p.height)
                dst.set(l, top, start + src.right / k, top + ph)
                cv.drawBitmap(p, src, dst, bmp)
            }
            start += period
        }
    }

    // ---------------- wool puffs and stars ----------------

    private class Bit(val x: Float, val y: Float, val dx: Float, val dy: Float, val s: Float, val dur: Float, val star: Boolean, val d2x: Float = 0f, val d2y: Float = 0f) { var t = 0f }
    private val bits = ArrayList<Bit>()
    private val rnd = Random(5)

    /** Wool puffs bursting from (x, y) (core.js puff). */
    fun puff(x: Float, y: Float, n: Int = 8, spread: Float = 30f) {
        for (i in 0 until n) {
            val a = i.toFloat() / n * 6.283f + rnd.nextFloat() * 0.4f; val d = spread * (0.7f + rnd.nextFloat() * 0.5f)
            bits += Bit(x, y, cos(a) * d, sin(a) * d * 0.7f, 0.6f + rnd.nextFloat() * 0.8f, 480f + rnd.nextFloat() * 220f, false)
        }
    }

    /** Little yellow stars fanning up from (x, y) (core.js sparkle). */
    fun sparkle(x: Float, y: Float, n: Int = 5) {
        for (i in 0 until n) {
            val a = -PI.toFloat() / 2 + (i - (n - 1) / 2f) * 0.55f; val d = 34f + rnd.nextFloat() * 18f
            bits += Bit(x, y, cos(a) * d, sin(a) * d, 1f, 700f, true, cos(a) * d * 1.15f, sin(a) * d * 1.15f)
        }
    }

    val busy get() = bits.isNotEmpty()

    fun stepBits(dt: Float) { bits.removeAll { it.t += dt; it.t >= it.dur } }

    fun clearBits() = bits.clear()

    fun drawBits(cv: Canvas) {
        for (b in bits) {
            val r = (b.t / b.dur).coerceIn(0f, 1f)
            if (!b.star) {
                val e = 1f - (1f - r) * (1f - r) * (1f - r)
                val s = b.s * (0.5f + 0.8f * e)
                fill.shader = null; fill.color = 0xFFF8F0E0.toInt(); fill.alpha = (242 * (1f - r)).toInt()
                cv.drawCircle(b.x + b.dx * e, b.y + b.dy * e, 6.5f * s, fill)
            } else {
                val e = 1f - (1f - r) * (1f - r)
                val (px, py, s, rot, a) = if (e < 0.6f) { val k = e / 0.6f; floatArrayOf(b.dx * k, b.dy * k, 0.2f + 0.8f * k, 90f * k, 1f) }
                    else { val k = (e - 0.6f) / 0.4f; floatArrayOf(b.dx + (b.d2x - b.dx) * k, b.dy + (b.d2y - b.dy) * k, 1f - 0.6f * k, 90f + 70f * k, 1f - k) }
                cv.save(); cv.translate(b.x + px, b.y + py); cv.rotate(rot); cv.scale(s, s)
                path.reset(); path.moveTo(0f, -6f); path.lineTo(1.5f, -1.5f); path.lineTo(6f, 0f); path.lineTo(1.5f, 1.5f)
                path.lineTo(0f, 6f); path.lineTo(-1.5f, 1.5f); path.lineTo(-6f, 0f); path.lineTo(-1.5f, -1.5f); path.close()
                fill.shader = null; fill.color = 0xFFFFD84D.toInt(); fill.alpha = (255 * a).toInt()
                cv.drawPath(path, fill); cv.restore()
            }
        }
        fill.alpha = 255
    }
}
