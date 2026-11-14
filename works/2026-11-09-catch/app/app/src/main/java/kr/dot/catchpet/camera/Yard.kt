package kr.dot.catchpet.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kr.dot.catchpet.ar.Critters
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * The camera-off world (operator 11-13, mockup mockups/yard.html "C1 그림책 언덕 · 입체감"): a picture-book yard ringed
 * around the player. Everything is pinned to world azimuths, so the gyro pans it like the camera view. [back] paints
 * the sky, hills, fence and floor under the critters; the [standing] trees, bushes and grass clumps are drawn by
 * Critters in depth order with the critters, so a critter can hide behind one. Sizes follow the 540 px wide mockup.
 * Main thread only (shared paints).
 */
object Yard {
    class Pal(
        val sky0: Int, val sky1: Int, val far2: Int, val far: Int, val mid: Int, val grass0: Int, val grass1: Int,
        val tuft: Int, val ink: Int, val sun: Int, val cloud: Int, val patch0: Int, val patch1: Int, val haze: Int,
        val dark: Boolean = false,
    )

    private fun c(v: Long) = v.toInt()
    val DAY = Pal(c(0xFF6EC1FF), c(0xFFD2EEFF), c(0xFFCFE3EC), c(0xFFBFE6A8), c(0xFF97D27B), c(0xFFA6D987), c(0xFF6FB352),
        c(0xFF5E9E45), c(0xFF3D2B1F), c(0xFFFFD447), c(0xFFFFFFFF), c(0x29FFFFFF), c(0x21286E28), c(0xFFEAF6FF))
    val DUSK = Pal(c(0xFFFF8E6E), c(0xFFFFD8A3), c(0xFFE9C2AE), c(0xFFE9B98C), c(0xFFB8C56C), c(0xFFBFCB7A), c(0xFF8AA847),
        c(0xFF6E8A37), c(0xFF4A2A1E), c(0xFFFFE78A), c(0xFFFFE3D3), c(0x2EFFE6BE), c(0x215A4614), c(0xFFFFE3C4))
    val NIGHT = Pal(c(0xFF1C2356), c(0xFF46529A), c(0xFF2E3B6E), c(0xFF344A78), c(0xFF2F6A55), c(0xFF4A8462), c(0xFF2F6545),
        c(0xFF25553A), c(0xFF0E1326), c(0xFFFFF2B8), c(0xFF8C96C9), c(0x1496C8FF), c(0x29000000), c(0xFF4C5A9E), dark = true)

    /** The yard follows the clock: day 6-17, dusk 17-19, night otherwise. */
    fun palNow(): Pal = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 6..16 -> DAY
        in 17..18 -> DUSK
        else -> NIGHT
    }

    /** One frame's view: maps world (azimuth, feet line share of the height) to view px. */
    class View(val w: Float, val h: Float, private val yaw: Float, shift: Float, val t: Float, val pal: Pal) {
        val k = w / 540f
        val dy = shift * h
        val hz = h * SceneSource.HORIZON + dy
        val degW = w / Critters.FOV_DEG
        fun x(az: Float) = w / 2 + Critters.diff(az, yaw) / Critters.FOV_DEG * w
        fun azAt(px: Float) = yaw + (px - w / 2) / w * Critters.FOV_DEG
        fun on(cx: Float, r: Float) = cx > -r && cx < w + r
    }

    const val TREE = 0
    const val BUSH = 1
    const val GRASS = 2

    /** Something standing on the floor: [feet] is its foot line (share of the view height, like a critter's). */
    class Thing(val kind: Int, val az: Float, val feet: Float, val size: Float, val phase: Float)

    val standing: List<Thing> = Random(77).let { r ->
        // spread evenly round the ring (with jitter) so no view gets a forest and none gets nothing
        fun add(kind: Int, n: Int, f0: Float, f1: Float, off: Float) = List(n) { i -> Thing(kind, (i + off + r.nextFloat() * 0.6f) * 360f / n, f0 + r.nextFloat() * (f1 - f0), 0.8f + r.nextFloat() * 0.45f, r.nextFloat() * 6f) }
        add(TREE, 7, 0.56f, 0.7f, 0f) + add(BUSH, 10, 0.6f, 0.93f, 0.3f) + add(GRASS, 14, 0.6f, 1.0f, 0.6f) + add(GRASS, 8, 0.9f, 1.04f, 0.15f)
    }

    private class Spot(val kind: Int, val az: Float, val f: Float, val c: Float)
    private val decor = Random(11).let { r -> List(90) { Spot(intArrayOf(0, 0, 1, 2)[r.nextInt(4)], r.nextFloat() * 360f - 180f, 0.55f + r.nextFloat() * 0.55f, r.nextFloat()) } }
    private val patches = Random(65).let { r -> List(45) { Spot(r.nextInt(2), r.nextFloat() * 360f - 180f, 0.55f + r.nextFloat() * 0.55f, r.nextFloat()) } }
    private val stars = Random(7).let { r -> List(60) { floatArrayOf(r.nextFloat() * 360f, 0.15f + r.nextFloat() * 0.5f, 2f + r.nextFloat() * 2f) } }

    private const val OUT = 0.4f   // outline width share (mockup round 3: thin outlines)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private val rf = RectF()

    private fun ink(v: View, lw: Float): Paint = ink.apply { color = v.pal.ink; strokeWidth = lw * OUT * v.k; shader = null }
    private fun solid(color: Int): Paint = fill.apply { this.color = color; shader = null; style = Paint.Style.FILL }
    private fun circle(x: Float, y: Float, r: Float) = Path().apply { addCircle(x, y, r.coerceAtLeast(1f), Path.Direction.CW) }
    private fun oval(x: Float, y: Float, rx: Float, ry: Float) = Path().apply { addOval(RectF(x - rx, y - ry, x + rx, y + ry), Path.Direction.CW) }
    private fun rrect(x: Float, y: Float, w: Float, h: Float, r: Float) = Path().apply { addRoundRect(RectF(x, y, x + w, y + h), r, r, Path.Direction.CW) }

    /** Cartoon union: outline every piece at double width first, then fill them all, so only the outer edge is inked. */
    private fun blobs(cv: Canvas, v: View, parts: List<Path>, color: Int, lw: Float = 5f) {
        val p = ink(v, lw * 2)
        for (s in parts) cv.drawPath(s, p)
        val f = solid(color)
        for (s in parts) cv.drawPath(s, f)
    }

    // ---------------- background: sky, hills, fence, floor ----------------

    fun back(cv: Canvas, v: View) {
        val p = v.pal
        fill.shader = LinearGradient(0f, v.hz - v.h * 0.62f, 0f, v.hz, p.sky0, p.sky1, Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, v.w, v.hz.coerceAtLeast(0f), fill); fill.shader = null
        if (p.dark) for ((i, s) in stars.withIndex()) {
            val sx = v.x(s[0]); if (!v.on(sx, 4f)) continue
            solid(0xFFFFF6CF.toInt()).alpha = ((0.6f + 0.4f * sin(v.t * 2 + i)) * 255).toInt()
            cv.drawCircle(sx, v.hz - v.h * s[1], s[2] * v.k, fill)
        }
        sun(cv, v)
        for (cl in CLOUDS) cloud(cv, v, cl[0], cl[1], cl[2])
        hill(cv, v, 0.12f, 0.08f, 120f, 0.4f, p.far2, p.far2, 3f)
        hill(cv, v, 0.07f, 0.1f, 90f, 0f, p.far, p.mid, 4f)
        hill(cv, v, 0.02f, 0.07f, 60f, 1.3f, p.mid, p.grass0, 5f)
        for (tr in FAR_TREES) { val cx = v.x(tr[0]); if (v.on(cx, 60 * v.k)) farTree(cv, v, cx, v.hz, v.h * 0.045f * tr[1]) }
        v.x(55f).let { if (v.on(it, 160 * v.k)) house(cv, v, it, v.hz, v.h * 0.07f) }
        fence(cv, v, -20f, 40f, 2.4f, 0.045f)
        // floor: lighter far, darker near, soft flat patches
        fill.shader = LinearGradient(0f, v.hz, 0f, v.h + v.dy.coerceAtLeast(0f), p.grass0, p.grass1, Shader.TileMode.CLAMP)
        cv.drawRect(0f, v.hz, v.w, v.h, fill); fill.shader = null
        for (d in patches) {
            val cx = v.x(d.az); val y = d.f * v.h + v.dy; val kk = (d.f - 0.52f) / 0.48f
            val rx = (40 + 70 * d.c) * (0.3f + kk) * v.k; val ry = rx * (0.12f + 0.2f * kk)
            if (y < v.hz || y - ry > v.h || !v.on(cx, rx)) continue
            cv.drawOval(cx - rx, y - ry, cx + rx, y + ry, solid(if (d.kind == 0) p.patch0 else p.patch1))
        }
        // haze where the sky meets the ground
        val top = v.hz - v.h * 0.09f; val bot = v.hz + v.h * 0.07f; val hz0 = p.haze and 0xFFFFFF
        fill.shader = LinearGradient(0f, top, 0f, bot, intArrayOf(hz0, hz0 or (0xAA shl 24), hz0), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, top, v.w, bot, fill); fill.shader = null
        for (d in decor) spot(cv, v, d)
    }

    private val CLOUDS = arrayOf(floatArrayOf(-30f, 0.42f, 1.1f), floatArrayOf(70f, 0.3f, 0.8f), floatArrayOf(150f, 0.4f, 1f), floatArrayOf(230f, 0.34f, 0.9f), floatArrayOf(300f, 0.44f, 0.7f))
    private val FAR_TREES = arrayOf(floatArrayOf(-50f, 0.9f), floatArrayOf(15f, 1.1f), floatArrayOf(100f, 0.8f), floatArrayOf(200f, 1f), floatArrayOf(260f, 0.9f), floatArrayOf(-120f, 0.8f), floatArrayOf(150f, 0.7f))

    private fun sun(cv: Canvas, v: View) {
        val cx = v.x(25f); val cy = v.hz - v.h * 0.36f; val r = 46 * v.k
        if (!v.on(cx, r * 2)) return
        val p = v.pal
        if (p.dark) {   // moon
            blobs(cv, v, listOf(circle(cx, cy, r * 0.8f)), p.sun, 4f)
            solid(0x14000000)
            for (cr in arrayOf(floatArrayOf(-0.3f, -0.2f, 0.18f), floatArrayOf(0.25f, 0.2f, 0.14f), floatArrayOf(0.1f, -0.35f, 0.09f))) cv.drawCircle(cx + cr[0] * r, cy + cr[1] * r, cr[2] * r, fill)
        } else {
            for (i in 0 until 12) {
                val a = i / 12f * 2 * PI.toFloat() + v.t * 0.25f
                val ray = Path().apply {
                    moveTo(cx + cos(a - 0.12f) * r * 1.08f, cy + sin(a - 0.12f) * r * 1.08f); lineTo(cx + cos(a) * r * 1.5f, cy + sin(a) * r * 1.5f)
                    lineTo(cx + cos(a + 0.12f) * r * 1.08f, cy + sin(a + 0.12f) * r * 1.08f); close()
                }
                cv.drawPath(ray, solid(p.sun)); cv.drawPath(ray, ink(v, 4f))
            }
            blobs(cv, v, listOf(circle(cx, cy, r)), p.sun, 4f)
        }
        // face: closed happy eyes, smile, cheeks
        val e = r * 0.14f
        for (s in intArrayOf(-1, 1)) { rf.set(cx + s * r * 0.32f - e, cy - r * 0.08f - e, cx + s * r * 0.32f + e, cy - r * 0.08f + e); cv.drawArc(rf, 198f, 144f, false, ink(v, 4f)) }
        rf.set(cx - r * 0.3f, cy + r * 0.12f - r * 0.3f, cx + r * 0.3f, cy + r * 0.12f + r * 0.3f); cv.drawArc(rf, 27f, 126f, false, ink(v, 4f))
        solid(0x8CFF7878.toInt())
        for (s in intArrayOf(-1, 1)) cv.drawOval(cx + s * r * 0.55f - r * 0.14f, cy + r * 0.18f - r * 0.09f, cx + s * r * 0.55f + r * 0.14f, cy + r * 0.18f + r * 0.09f, fill)
    }

    private fun cloud(cv: Canvas, v: View, az: Float, alt: Float, s: Float) {
        val cx = v.x(az + v.t * 0.6f); val cy = v.hz - v.h * alt; val r = 26 * s * v.k
        if (!v.on(cx, r * 4)) return
        blobs(cv, v, listOf(circle(cx, cy, r * 1.15f), circle(cx - r * 1.2f, cy + r * 0.35f, r * 0.85f), circle(cx + r * 1.25f, cy + r * 0.3f, r * 0.9f),
            rrect(cx - r * 1.9f, cy + r * 0.2f, r * 3.8f, r * 0.95f, r * 0.45f)), v.pal.cloud, 4f)
        cv.drawOval(cx - r * 0.65f, cy - r * 0.63f, cx + r * 0.05f, cy - r * 0.27f, solid(0x8CFFFFFF.toInt()))
    }

    /** Rolling hill band pinned to azimuth; [period] in degrees must divide 360. */
    private fun hill(cv: Canvas, v: View, base: Float, amp: Float, period: Float, phase: Float, top: Int, bottom: Int, lw: Float) {
        val path = Path().apply {
            moveTo(-10f, v.hz + 4)
            var px = -10f
            while (px <= v.w + 10) {
                val az = v.azAt(px)
                lineTo(px, v.hz - v.h * base - v.h * amp * (0.5f + 0.5f * sin(az / period * 2 * PI.toFloat() + phase)))
                px += 6
            }
            lineTo(v.w + 10, v.hz + 4); close()
        }
        fill.shader = LinearGradient(0f, v.hz - v.h * (base + amp), 0f, v.hz, top, bottom, Shader.TileMode.CLAMP)
        fill.style = Paint.Style.FILL
        cv.drawPath(path, fill); fill.shader = null
        cv.drawPath(path, ink(v, lw))
    }

    private fun farTree(cv: Canvas, v: View, cx: Float, base: Float, s: Float) {
        blobs(cv, v, listOf(rrect(cx - s * 0.09f, base - s, s * 0.18f, s, 5 * v.k)), 0xFFA06A43.toInt(), 4f)
        blobs(cv, v, listOf(circle(cx, base - s * 1.25f, s * 0.5f), circle(cx - s * 0.36f, base - s * 0.98f, s * 0.38f), circle(cx + s * 0.38f, base - s * 1.0f, s * 0.38f)),
            if (v.pal.dark) 0xFF3E8A5E.toInt() else 0xFF6CC07A.toInt(), 4f)
        cv.drawOval(cx - s * 0.34f, base - s * 1.53f, cx - s * 0.02f, base - s * 1.37f, solid(0x59FFFFFF))
    }

    private fun house(cv: Canvas, v: View, cx: Float, base: Float, s: Float) {
        val dark = v.pal.dark
        blobs(cv, v, listOf(rrect(cx - s * 0.6f, base - s * 0.8f, s * 1.2f, s * 0.8f, 6 * v.k)), if (dark) 0xFFC9B7A6.toInt() else 0xFFFFF0DC.toInt(), 4f)
        val roof = Path().apply { moveTo(cx - s * 0.78f, base - s * 0.76f); lineTo(cx, base - s * 1.42f); lineTo(cx + s * 0.78f, base - s * 0.76f); close() }
        cv.drawPath(roof, solid(0xFFE8594A.toInt())); cv.drawPath(roof, ink(v, 4f))
        blobs(cv, v, listOf(rrect(cx - s * 0.14f, base - s * 0.46f, s * 0.28f, s * 0.46f, 5 * v.k)), 0xFFB9774F.toInt(), 3f)
        val lit = if (dark) 0xFFFFE08A.toInt() else 0xFFBFE4FF.toInt()
        blobs(cv, v, listOf(rrect(cx + s * 0.22f, base - s * 0.62f, s * 0.26f, s * 0.24f, 4 * v.k)), lit, 3f)
        blobs(cv, v, listOf(rrect(cx - s * 0.48f, base - s * 0.62f, s * 0.26f, s * 0.24f, 4 * v.k)), lit, 3f)
    }

    private fun fence(cv: Canvas, v: View, from: Float, to: Float, step: Float, hgt: Float) {
        var az = ceil(from / step) * step
        while (az <= to) {
            val fx = v.x(az)
            if (v.on(fx, 20 * v.k)) {
                val pw = v.degW * step * 0.45f; val top = v.hz - v.h * hgt
                val post = Path().apply {
                    moveTo(fx - pw / 2, v.hz); lineTo(fx - pw / 2, top + pw * 0.5f); lineTo(fx, top); lineTo(fx + pw / 2, top + pw * 0.5f); lineTo(fx + pw / 2, v.hz); close()
                }
                cv.drawPath(post, solid(if (v.pal.dark) 0xFF8E97BF.toInt() else 0xFFFFFDF6.toInt())); cv.drawPath(post, ink(v, 3f))
            }
            az += step
        }
    }

    /** Small flat things on the floor (tufts, flowers, stones), under the critters. */
    private fun spot(cv: Canvas, v: View, d: Spot) {
        val cx = v.x(d.az); if (!v.on(cx, 40 * v.k)) return
        val y = d.f * v.h + v.dy
        if (y < v.hz + 2 || y > v.h + 30) return
        val s = (0.35f + 0.9f * min(1.1f, (d.f - 0.52f) / 0.48f)) * v.k
        when (d.kind) {
            0 -> { val p = ink.apply { color = v.pal.tuft; strokeWidth = (2.5f + 1.5f * s / v.k) * v.k; shader = null }
                for (o in -1..1) cv.drawLine(cx + o * 7 * s, y, cx + o * 11 * s, y - (if (o != 0) 13 else 18) * s, p) }
            1 -> { val cols = intArrayOf(0xFFFF7A8A.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFD447.toInt(), 0xFFB79CFF.toInt())
                blobs(cv, v, List(5) { i -> val a = i / 5f * 2 * PI.toFloat(); circle(cx + cos(a) * 6 * s, y - 8 * s + sin(a) * 6 * s, 5 * s) }, cols[(d.c * cols.size).toInt().coerceAtMost(3)], 2f)
                blobs(cv, v, listOf(circle(cx, y - 8 * s, 3.5f * s)), 0xFFFFB23A.toInt(), 1.5f) }
            else -> blobs(cv, v, listOf(oval(cx, y - 4 * s, 11 * s, 6 * s)), if (v.pal.dark) 0xFF7A7F99.toInt() else 0xFFD8D2C6.toInt(), 2.5f)
        }
    }

    // ---------------- standing things: shaded, with soft ground shadows ----------------

    // Heat (operator 11-13 "살짝 뜨거워"): the shaded balls and soft shadows are drawn from small cached sprites
    // instead of a new gradient shader per ball every frame.
    private val sprites = HashMap<String, Bitmap>()
    private val spritePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    private fun shadowSprite(): Bitmap = sprites.getOrPut("shadow") {
        Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).also { b ->
            Canvas(b).drawCircle(32f, 32f, 32f, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(32f, 32f, 32f, 0xFF142D14.toInt(), 0x00142D14, Shader.TileMode.CLAMP) })
        }
    }

    fun groundShadow(cv: Canvas, v: View, cx: Float, fy: Float, rx: Float, ry: Float, a: Float = 0.28f) {
        if (rx < 1f) return
        spritePaint.alpha = ((if (v.pal.dark) a + 0.1f else a) * 255).toInt().coerceAtMost(255)
        rf.set(cx - rx, fy - ry, cx + rx, fy + ry)
        cv.drawBitmap(shadowSprite(), null, rf, spritePaint)
        spritePaint.alpha = 255
    }

    /** A ball lit from the upper left (the sun side). */
    private fun sphere(cv: Canvas, v: View, cx: Float, cy: Float, r: Float, c0: Int, c1: Int, c2: Int) {
        val b = sprites.getOrPut("ball$c0,$c1,$c2") {
            Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888).also { bm ->
                // cel shading like the fence and hills: a flat body, one shadow crescent, one small highlight
                val cvb = Canvas(bm)
                val p = Paint(Paint.ANTI_ALIAS_FLAG)
                p.color = c2; cvb.drawCircle(64f, 64f, 63f, p)
                cvb.save(); cvb.clipPath(Path().apply { addCircle(64f, 64f, 63f, Path.Direction.CW) })
                p.color = c1; cvb.drawCircle(56f, 54f, 60f, p)
                cvb.restore()
                p.color = c0; cvb.drawOval(30f, 26f, 56f, 40f, p)
            }
        }
        rf.set(cx - r, cy - r, cx + r, cy + r)
        cv.drawBitmap(b, null, rf, spritePaint)
        cv.drawCircle(cx, cy, r, ink(v, 3f))
    }

    private fun leaf(v: View) = if (v.pal.dark) intArrayOf(0xFF7FC09A.toInt(), 0xFF3E8A5E.toInt(), 0xFF1F4F38.toInt())
        else intArrayOf(0xFFA3DB8A.toInt(), 0xFF6CC07A.toInt(), 0xFF3F8A52.toInt())

    /** Draw one standing thing at its place; the size follows the same perspective as the critters. */
    fun draw(cv: Canvas, v: View, th: Thing) {
        val cx = v.x(th.az); val fy = th.feet * v.h + v.dy; val p = Critters.perspective(th.feet) * th.size
        if (!v.on(cx, 260 * p * v.k)) return
        val (a, b, c) = leaf(v).let { Triple(it[0], it[1], it[2]) }
        when (th.kind) {
            TREE -> {
                val s = v.h * 0.2f * p
                groundShadow(cv, v, cx + s * 0.3f, fy, s * 0.8f, s * 0.17f)
                val trunk = Path().apply { moveTo(cx - s * 0.1f, fy); lineTo(cx - s * 0.06f, fy - s * 0.9f); lineTo(cx + s * 0.06f, fy - s * 0.9f); lineTo(cx + s * 0.1f, fy); close() }
                fill.shader = LinearGradient(cx - s * 0.1f, 0f, cx + s * 0.1f, 0f, 0xFFC99366.toInt(), 0xFF6E4528.toInt(), Shader.TileMode.CLAMP)
                cv.drawPath(trunk, fill); fill.shader = null; cv.drawPath(trunk, ink(v, 3f))
                sphere(cv, v, cx + s * 0.36f, fy - s * 0.98f, s * 0.36f, b, c, c)
                sphere(cv, v, cx - s * 0.38f, fy - s * 1.0f, s * 0.38f, a, b, c)
                sphere(cv, v, cx, fy - s * 1.3f, s * 0.5f, a, b, c)
                sphere(cv, v, cx + s * 0.14f, fy - s * 0.86f, s * 0.28f, a, b, c)
            }
            BUSH -> {
                val s = v.h * 0.075f * p
                groundShadow(cv, v, cx + s * 0.15f, fy, s * 0.95f, s * 0.2f)
                sphere(cv, v, cx - s * 0.48f, fy - s * 0.3f, s * 0.38f, a, b, c)
                sphere(cv, v, cx + s * 0.48f, fy - s * 0.32f, s * 0.4f, b, c, c)
                sphere(cv, v, cx, fy - s * 0.48f, s * 0.5f, a, b, c)
            }
            else -> {
                val s = v.h * 0.14f * p
                groundShadow(cv, v, cx, fy, s * 0.6f, s * 0.1f, 0.2f)
                val sw = sin(v.t * 2.2f + th.phase) * s * 0.08f
                val lo = if (v.pal.dark) 0xFF1F4A33.toInt() else 0xFF3F8E3E.toInt()
                val hi = if (v.pal.dark) 0xFF6FB08A.toInt() else 0xFFB4E886.toInt()
                fill.shader = LinearGradient(0f, fy, 0f, fy - s * 0.8f, lo, hi, Shader.TileMode.CLAMP)   // one per clump
                for (i in -5..5) {
                    val bx = cx + i * s * 0.09f
                    val hh = s * (0.8f - kotlin.math.abs(i) * 0.05f) * (0.8f + 0.4f * (((i + 5) * 7) % 5) / 5f)
                    val blade = Path().apply {
                        moveTo(bx - s * 0.05f, fy); quadTo(bx, fy - hh * 0.6f, bx + i * s * 0.03f + sw, fy - hh)
                        quadTo(bx + s * 0.02f, fy - hh * 0.5f, bx + s * 0.05f, fy); close()
                    }
                    cv.drawPath(blade, fill)
                }
                fill.shader = null
            }
        }
    }
}
