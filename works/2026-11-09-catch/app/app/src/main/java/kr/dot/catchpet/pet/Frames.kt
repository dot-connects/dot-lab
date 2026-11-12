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
import kotlin.math.sin

/** Animation time in ms, kept small so Float keeps millisecond precision (wraps hourly). */
fun animClock(): Float = (android.os.SystemClock.uptimeMillis() % 3_600_000L).toFloat()

/** The six pet pictures of the chosen pet, trimmed to their opaque pixels. */
object Frames {
    val CELLS = listOf("front", "smile", "walkA", "walkB", "sleep", "curl")

    /** SharedPreferences "catchpet" / "pet" -> its six cells, or null (nothing chosen, or not usable as a pet). */
    fun chosen(ctx: Context): Map<String, Bitmap>? {
        val id = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getString("pet", null) ?: return null
        return Dex.get(ctx, id)?.let { load(ctx, it) }
    }

    /** Critter = baked assets; a real thing = its bought sheet cut in six. Missing cells fall back to front. */
    fun load(ctx: Context, e: Dex.Entry): Map<String, Bitmap>? {
        val raw: Map<String, Bitmap> = when {
            e.critterId != null -> CELLS.associateWith { asset(ctx, e.critterId, it) ?: return null }
            e.ai.owned && e.ai.sheet != null ->
                BitmapFactory.decodeFile(Dex.file(ctx, e.id, e.ai.sheet).path)?.let { Sheet.cells(it) } ?: return null
            else -> return null
        }
        val front = raw["front"] ?: raw.values.firstOrNull() ?: return null
        return CELLS.associateWith { trim(raw[it] ?: front) }
    }

    /** critters/<id>/<name>.png, else that critter's front, else the grey placeholder (plan R19). */
    fun asset(ctx: Context, critterId: String, name: String): Bitmap? =
        listOf("critters/$critterId/$name.png", "critters/$critterId/front.png", "critters/_placeholder/front.png")
            .firstNotNullOfOrNull { p -> runCatching { ctx.assets.open(p).use(BitmapFactory::decodeStream) }.getOrNull() }

    /** Cut away transparent margins so the pet fills its box. */
    fun trim(b: Bitmap): Bitmap {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val r = opaqueBox(px, b.width, b.height) ?: return b
        return Bitmap.createBitmap(b, r[0], r[1], r[2] - r[0] + 1, r[3] - r[1] + 1)
    }

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

    /** How far the pet hops off its line now (in units of its size). */
    fun lift(b: PetBrain, t: Float): Float = when (b.act) {
        PetBrain.Act.SMILE -> abs(sin(t % 700f / 700f * PI)).toFloat() * 0.2f
        PetBrain.Act.DANCE -> sin(t / 360f % 1f * PI).toFloat() * 0.12f
        PetBrain.Act.WALK, PetBrain.Act.CLIMB -> abs(sin(t / 170f * PI)).toFloat() * 0.04f
        else -> 0f
    }

    /** Rotation used for drawing: dangling and falling pets hang straight. */
    fun rotation(b: PetBrain): Float = if (b.act == PetBrain.Act.HANG || b.act == PetBrain.Act.FALL) 0f else b.rotationDeg()

    /** Pet of size s px with its feet at (fx, fy); lift in units of s (0 when the caller moves the window instead). */
    fun draw(c: Canvas, b: PetBrain, t: Float, fx: Float, fy: Float, s: Float, lift: Float) {
        val bmp = cells[b.frame(t)] ?: cells.getValue("front")
        var sx = 1f; var sy = 1f; var sway = 0f
        when (b.act) {
            PetBrain.Act.IDLE -> { val br = sin(t / 420f) * 0.035f; sx = 1 - br * 0.5f; sy = 1 + br }
            PetBrain.Act.SLEEP -> { val br = sin(t / 900f) * 0.03f; sx = 1 + br * 0.4f; sy = 1 - br }
            PetBrain.Act.THINK -> sway = sin(t / 300f) * 8f
            PetBrain.Act.DANCE -> { val beat = t / 360f; sway = (if (beat.toInt() % 2 == 1) 10f else -10f) * sin(beat % 1f * PI).toFloat() }
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
