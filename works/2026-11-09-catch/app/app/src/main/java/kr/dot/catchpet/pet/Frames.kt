package kr.dot.catchpet.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import kr.dot.catchpet.ai.Sheet
import kr.dot.catchpet.dex.Dex
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin

/** Animation time in ms, kept small so Float keeps millisecond precision (wraps hourly). */
fun animClock(): Float = (android.os.SystemClock.uptimeMillis() % 3_600_000L).toFloat()

/** The six pet pictures of the chosen pet, trimmed to their opaque pixels. */
object Frames {
    /** What the pet draws; a 6-cell sheet or a critter fills the newer keys from its own (see [ALIAS]). */
    val CELLS = listOf("front", "smile", "walk1", "walk2", "walk3", "walk4", "sleep", "curl", "holdA", "holdB", "fall", "dazed")
    // a 6-cell sheet has two side steps: hold each for two beats (a "front" passing beat turned the pet to face us
    // every other step, operator 11-15)
    private val ALIAS = mapOf("walk1" to "walkA", "walk2" to "walkA", "walk3" to "walkB", "walk4" to "walkB",
        "holdA" to "smile", "holdB" to "front", "fall" to "sleep", "dazed" to "front")

    /** SharedPreferences "catchpet" / "pet" -> its six cells, or null (nothing chosen, or not usable as a pet). */
    fun chosen(ctx: Context): Map<String, Bitmap>? {
        val id = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getString("pet", null) ?: return null
        return Dex.get(ctx, id)?.let { load(ctx, it) }
    }

    /** Critter = baked assets; a real thing = its bought sheet cut in six. Missing cells fall back to front. */
    fun load(ctx: Context, e: Dex.Entry): Map<String, Bitmap>? {
        val raw: Map<String, Bitmap> = when {
            // the six old cells must load (front falls back); the newer six only where baked, else ALIAS
            e.critterId != null -> Sheet.KEYS.associateWith { asset(ctx, e.critterId, it) ?: return null } +
                Sheet.KEYS12.mapNotNull { k -> exact(ctx, "critters/${e.critterId}/$k.webp")?.let { k to it } }
            e.ai.owned && e.ai.sheet != null ->
                BitmapFactory.decodeFile(Dex.file(ctx, e.id, e.ai.sheet).path)?.let { Sheet.cells(it, twelve = e.ai.sheet.contains("-v12")) } ?: return null
            else -> return null
        }
        val front = raw["front"] ?: raw.values.firstOrNull() ?: return null
        fun src(k: String): Bitmap = raw[k] ?: raw[ALIAS[k]] ?: front
        // the walk steps share one cut: trimmed one by one, a raised leg or a narrower step moved the body left and
        // right every beat (operator 11-16: "arms and legs look awkward")
        val walk = WALK.map(::src)
        val walkBox = if (walk.all { it.width == walk[0].width && it.height == walk[0].height })
            walk.mapNotNull(::box).reduceOrNull { a, b -> intArrayOf(minOf(a[0], b[0]), minOf(a[1], b[1]), maxOf(a[2], b[2]), maxOf(a[3], b[3])) } else null
        val trimmed = HashMap<String, Bitmap>()
        fun cell(k: String): Bitmap = trimmed.getOrPut(k) {
            val b = src(k)
            if (k in WALK && walkBox != null) crop(b, walkBox) else trim(b)
        }
        return CELLS.associateWith { cell(it) }
    }

    private fun exact(ctx: Context, path: String): Bitmap? =
        runCatching { ctx.assets.open(path).use(BitmapFactory::decodeStream) }.getOrNull()

    /** critters/<id>/<name>.webp, else that critter's front, else the grey placeholder (plan R19). */
    fun asset(ctx: Context, critterId: String, name: String): Bitmap? =
        listOf("critters/$critterId/$name.webp", "critters/$critterId/front.webp", "critters/_placeholder/front.webp")
            .firstNotNullOfOrNull { p -> runCatching { ctx.assets.open(p).use(BitmapFactory::decodeStream) }.getOrNull() }

    private val WALK = listOf("walk1", "walk2", "walk3", "walk4")

    /** Cut away transparent margins so the pet fills its box. */
    fun trim(b: Bitmap): Bitmap = box(b)?.let { crop(b, it) } ?: b

    private fun box(b: Bitmap): IntArray? {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        return opaqueBox(px, b.width, b.height)
    }

    private fun crop(b: Bitmap, r: IntArray): Bitmap = Bitmap.createBitmap(b, r[0], r[1], r[2] - r[0] + 1, r[3] - r[1] + 1)

    /** [x0, y0, x1, y1] (inclusive) of pixels with alpha > minAlpha, or null when none. */
    internal fun opaqueBox(px: IntArray, w: Int, h: Int, minAlpha: Int = 16): IntArray? {
        var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
        for (y in 0 until h) for (x in 0 until w) if ((px[y * w + x] ushr 24) > minAlpha) {
            if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y
        }
        return if (x1 < 0) null else intArrayOf(x0, y0, x1, y1)
    }
}

/**
 * Draws a PetBrain's pet the way mockups/pet-moves.html does: feet on the foot point, turned into the wall,
 * mirrored by direction, breathing / hopping / swaying per act. One scale for all cells, so frames don't jump.
 */
class PetLook(private val cells: Map<String, Bitmap>) {
    private val unit = cells.values.maxOf { max(it.width, it.height) }.toFloat()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF33363D.toInt(); isFakeBoldText = true; textAlign = Paint.Align.CENTER }
    private val dst = RectF()

    /** The front picture's height (px) in a box of s px: the weather frames are scaled to match it. */
    fun frontHeight(s: Float): Float = cells.getValue("front").height * s / unit

    /** How far the pet hops off its line now (in units of its size). */
    fun lift(b: PetBrain, t: Float): Float = when (b.act) {
        PetBrain.Act.SMILE -> abs(sin(t % 700f / 700f * PI)).toFloat() * 0.2f
        PetBrain.Act.HOP -> sin((b.actMs / 650f).coerceIn(0f, 1f) * PI).toFloat() * 0.5f
        // one small bob per step, highest mid passing frame (walk2 / walk4 start at 130 ms of each 260 ms step)
        PetBrain.Act.WALK, PetBrain.Act.CLIMB -> abs(sin((t - 65f) / 260f * PI)).toFloat() * 0.025f
        else -> 0f
    }

    /** Rotation used for drawing: dangling and falling pets hang straight. */
    fun rotation(b: PetBrain): Float = if (b.act == PetBrain.Act.HANG || b.act == PetBrain.Act.FALL) 0f else b.rotationDeg()

    /** Pet of size s px with its feet at (fx, fy); lift in units of s (0 when the caller moves the window instead). */
    fun draw(c: Canvas, b: PetBrain, t: Float, fx: Float, fy: Float, s: Float, lift: Float) {
        val bmp = cells[b.frame(t)] ?: cells.getValue("front")
        var sx = 1f; var sy = 1f; var sway = 0f
        when (b.act) {
            PetBrain.Act.IDLE, PetBrain.Act.WX -> { val br = sin(t / 420f) * 0.035f; sx = 1 - br * 0.5f; sy = 1 + br }
            PetBrain.Act.SLEEP -> { val br = sin(t / 900f) * 0.03f; sx = 1 + br * 0.4f; sy = 1 - br }
            PetBrain.Act.THINK -> sway = sin(t / 300f) * 8f
            PetBrain.Act.WIGGLE -> sway = sin(b.actMs / 55f) * 14f * (1f - b.actMs / 900f).coerceAtLeast(0f)
            PetBrain.Act.HOP -> { val a = b.actMs; if (a < 90f) { sx = 1.12f; sy = 0.86f } }   // crouch, then up
            PetBrain.Act.DIZZY -> {
                // a squash on landing, then a wobble that dies out
                val a = b.actMs
                val land = (1f - a / 160f).coerceAtLeast(0f)
                sx = 1 + 0.16f * land; sy = 1 - 0.2f * land
                sway = sin(a / 95f) * 13f * (1f - a / PetBrain.DIZZY_MS).coerceAtLeast(0f)
            }
            PetBrain.Act.PONDER -> {
                // head tilted to one side and held, slow breath: no side to side (operator 11-16)
                sway = 9f * (b.actMs / 250f).coerceAtMost(1f)
                val br = sin(t / 700f) * 0.02f; sx = 1 - br * 0.5f; sy = 1 + br
            }
            else -> {}
        }
        val k = s / unit
        val bw = bmp.width * k; val bh = bmp.height * k
        c.save()
        c.translate(fx, fy)
        c.rotate(rotation(b) + sway)
        c.translate(0f, -lift * s)
        c.scale(if (b.flip()) -sx else sx, sy)
        dst.set(-bw / 2, -bh, bw / 2, 0f)
        c.drawBitmap(bmp, null, dst, paint)
        c.restore()
        // small marks inside the box: "?" while thinking, "z" asleep (rotated with the pet)
        if (b.act == PetBrain.Act.DIZZY && b.actMs > 120f) {
            // two little stars circling over its head
            c.save(); c.translate(fx, fy); c.rotate(rotation(b))
            label.textSize = s * 0.2f; label.alpha = (255 * (1f - b.actMs / PetBrain.DIZZY_MS).coerceIn(0f, 1f)).toInt()
            for (k in 0..1) {
                val a = t / 170f + k * PI.toFloat()
                c.drawText("\u2605", cos(a) * s * 0.28f, -s * 0.95f + sin(a) * s * 0.07f, label)
            }
            c.restore()
            return
        }
        if (b.act == PetBrain.Act.PONDER) {
            // thought dots over its head, one more every 400 ms
            c.save(); c.translate(fx, fy); c.rotate(rotation(b))
            label.textSize = s * 0.26f; label.alpha = 230
            val n = (b.actMs / 400f).toInt() % 4
            for (i in 0 until n) c.drawText("•", s * (0.18f + i * 0.13f), -s * (0.8f + i * 0.06f), label)
            c.restore()
            return
        }
        val mark = when (b.act) { PetBrain.Act.THINK -> "?"; PetBrain.Act.SLEEP -> "z"; else -> null } ?: return
        c.save()
        c.translate(fx, fy)
        c.rotate(rotation(b))
        label.textSize = s * 0.3f
        label.alpha = if (mark == "z") (255 * (1 - t / 2400f % 1f)).toInt() else 220
        c.drawText(mark, s * 0.36f, -s * 0.72f, label)
        c.restore()
    }
}
