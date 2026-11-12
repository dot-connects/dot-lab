package kr.dot.catchpet.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kr.dot.catchpet.game.Box
import kr.dot.catchpet.game.Mask
import kr.dot.catchpet.pet.Frames
import kr.dot.catchpet.seg.CutOut
import kr.dot.catchpet.seg.ScreenMap
import kr.dot.catchpet.ui.CritterSource
import org.json.JSONArray
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Gyro critters (no AR): three fur critters, each pinned to a world azimuth (rotation-vector yaw) in the lower
 * third of the screen. Turning the phone pans them across the view. Each spawn faces a random way (dir0..7, R16);
 * a caught one leaves and another appears at a new azimuth.
 */
class Critters(private val ctx: Context, private val rnd: Random = Random.Default) : CritterSource, SensorEventListener {
    private class One(val id: String, val dir: Int, val az: Float, val feet01: Float, val pic: Bitmap) {
        val rect = RectF()   // view px, updated each frame
        var onScreen = false
    }

    private val ids: List<String> by lazy { Companion.ids(ctx) }
    private val live = mutableListOf<One>()
    private var lastCaughtAz: Float? = null
    private var yaw = 0f
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val rot = FloatArray(9)
    private val remapped = FloatArray(9)
    private val ori = FloatArray(3)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }

    /** critter_scale x the web figure (a creature ~40% of the screen height): 1/4 -> ~10% of the height. */
    private val heightFrac get() = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getFloat("critter_scale", 0.25f) * 0.4f

    override fun start() {
        sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun stop() { sm?.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        // phone upright, camera looking out the back
        SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
        SensorManager.getOrientation(remapped, ori)
        val a = Math.toDegrees(ori[0].toDouble()).toFloat()
        yaw += diff(a, yaw) * 0.5f   // light smoothing
    }

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}

    override fun masks(map: ScreenMap): List<Mask> {
        fill()
        val h = map.viewH * heightFrac
        val out = ArrayList<Mask>(live.size)
        for (c in live) {
            val w = h * c.pic.width / c.pic.height
            val cx = map.viewW / 2 + diff(c.az, yaw) / FOV_DEG * map.viewW
            val feet = c.feet01 * map.viewH
            c.rect.set(cx - w / 2, feet - h, cx + w / 2, feet)
            c.onScreen = c.rect.right > 0 && c.rect.left < map.viewW
            if (!c.onScreen) continue
            val (x0, y0) = map.toGrid(c.rect.left, c.rect.top)
            val (x1, y1) = map.toGrid(c.rect.right, c.rect.bottom)
            out += Mask.ellipseIn(map.gridW, map.gridH, Box(x0, y0, x1, y1), Mask.Kind.CRITTER, c.id)
        }
        return out
    }

    override fun draw(cv: Canvas, map: ScreenMap, held: String?, dy: Float) {
        for (c in live) {
            if (!c.onScreen) continue
            val r = c.rect
            if (c.id != held) cv.drawOval(r.left + r.width() * 0.1f, r.bottom - r.height() * 0.06f, r.right - r.width() * 0.1f, r.bottom + r.height() * 0.06f, shadow)
            val lift = if (c.id == held) dy else 0f
            cv.drawBitmap(c.pic, null, RectF(r.left, r.top + lift, r.right, r.bottom + lift), paint)
        }
    }

    override fun caught(critterId: String, map: ScreenMap): Pair<String, CutOut.Cut?> {
        val c = live.firstOrNull { it.id == critterId } ?: return "dir0" to null
        live.remove(c)
        lastCaughtAz = c.az
        return "dir${c.dir}" to cutOf(c.pic, c.rect, map)
    }

    /** Keep three on stage, at least 20 degrees apart (and away from the one just caught). */
    private fun fill() {
        if (ids.isEmpty()) return
        while (live.size < COUNT) {
            val free = ids.filter { id -> live.none { it.id == id } }.ifEmpty { return }
            val id = free[rnd.nextInt(free.size)]
            var az = yaw
            for (i in 0 until 12) {
                az = yaw + (rnd.nextFloat() * 2 - 1) * SPREAD_DEG
                val taken = live.map { it.az } + listOfNotNull(lastCaughtAz)
                if (taken.all { abs(diff(az, it)) >= 20f }) break
            }
            val dir = rnd.nextInt(8)
            val pic = Frames.asset(ctx, id, "dir$dir")?.let(Frames::trim) ?: return
            live += One(id, dir, az, 0.78f + rnd.nextFloat() * 0.12f, pic)
        }
    }

    companion object {
        const val COUNT = 3
        // ponytail: fixed horizontal field of view of the portrait preview; calibrate on the real phone
        const val FOV_DEG = 40f
        const val SPREAD_DEG = 60f

        /** The baked critter ids (assets critters/index.json), empty when missing. */
        fun ids(ctx: Context): List<String> = runCatching {
            val a = JSONArray(ctx.assets.open("critters/index.json").bufferedReader().use { it.readText() })
            (0 until a.length()).map { a.getJSONObject(it).getString("id") }
        }.getOrDefault(emptyList())

        /** The picture placed in upright-image px, so the capsule can shrink it like a real cut-out. */
        fun cutOf(pic: Bitmap, r: RectF, map: ScreenMap) = CutOut.Cut(pic, Rect(
            ((r.left - map.left) / map.scale).roundToInt(), ((r.top - map.top) / map.scale).roundToInt(),
            ((r.right - map.left) / map.scale).roundToInt(), ((r.bottom - map.top) / map.scale).roundToInt(),
        ))

        /** a - b wrapped into -180..180. */
        fun diff(a: Float, b: Float): Float = ((a - b) % 360f + 540f) % 360f - 180f
    }
}
