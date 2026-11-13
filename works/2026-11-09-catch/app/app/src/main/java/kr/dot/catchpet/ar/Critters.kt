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
import android.os.SystemClock
import kr.dot.catchpet.camera.Yard
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
 * Fur critters, each pinned to a world azimuth (rotation-vector yaw): turning the phone pans them across the view,
 * tilting it up / down moves them down / up. Camera mode: three. Scene mode (camera off, SceneSource draws the Yard that
 * pans the same way, operator 11-13 "the AR feel stays"): six, a bit bigger (SCENE_SIZE), drawn in depth order with the
 * yard's trees, bushes and grass; every other spawn hides behind one of its own (a cover) and bobs up to look out.
 * Both use perspective (operator 11-13): the higher a critter stands (further away), the smaller it is, times a
 * random size each. Each spawn faces a random way (dir0..7, R16); a caught one leaves and another appears.
 */
class Critters(
    private val ctx: Context,
    private val scene: Boolean = false,
    private val rnd: Random = Random.Default,
) : CritterSource, SensorEventListener {
    private class One(val id: String, val dir: Int, val az: Float, val feet01: Float, val size: Float, val pic: Bitmap, val cover: Yard.Thing? = null) {
        val rect = RectF()   // view px, updated each frame
        var onScreen = false
    }

    private val ids: List<String> by lazy { Companion.ids(ctx) }
    private val live = mutableListOf<One>()
    private var lastCaughtAz: Float? = null
    private var spawns = 0
    private var yaw = 0f
    private var pitchBase: Float? = null
    private var tilt = 0f   // degrees from the pitch the screen opened at
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val rot = FloatArray(9)
    private val remapped = FloatArray(9)
    private val ori = FloatArray(3)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }

    /** critter_scale x the web figure (a creature ~40% of the screen height): the nearest one at 0.25 is 10 % of it. */
    private val heightFrac get() = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getFloat("critter_scale", 0.25f) * 0.4f

    override fun start() {
        pitchBase = null
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
        val p = Math.toDegrees(ori[1].toDouble()).toFloat()
        val base = pitchBase ?: p.also { pitchBase = it }
        tilt += ((p - base) - tilt) * 0.5f
    }

    /** Vertical shift of the floor, share of the view height (+ = down), from tilting since the screen opened. */
    val shift01: Float get() = (PITCH_SIGN * tilt / VFOV_DEG).coerceIn(-0.3f, 0.3f)

    /** Where the phone looks now: yaw (degrees) and the floor shift; the camera-off room pans with it. */
    fun view(): Pair<Float, Float> = yaw to shift01

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}

    override fun masks(map: ScreenMap): List<Mask> {
        fill()
        val out = ArrayList<Mask>(live.size)
        for (c in live) {
            val h = map.viewH * heightFrac * perspective(c.feet01) * c.size * (if (scene) SCENE_SIZE else 1f)
            val w = h * c.pic.width / c.pic.height
            val cx = map.viewW / 2 + diff(c.az, yaw) / FOV_DEG * map.viewW
            val feet = (c.feet01 + shift01) * map.viewH
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
        if (scene) return drawScene(cv, map, held, dy)
        for (c in live.sortedBy { it.feet01 }) {   // far ones first
            if (!c.onScreen) continue
            val r = c.rect
            if (c.id != held) cv.drawOval(r.left + r.width() * 0.1f, r.bottom - r.height() * 0.06f, r.right - r.width() * 0.1f, r.bottom + r.height() * 0.06f, shadow)
            val lift = if (c.id == held) dy else 0f
            cv.drawBitmap(c.pic, null, RectF(r.left, r.top + lift, r.right, r.bottom + lift), paint)
        }
    }

    /** Scene mode: critters, the yard's standing things and the covers, far first; the one on the claw on top. */
    private fun drawScene(cv: Canvas, map: ScreenMap, held: String?, dy: Float) {
        val v = Yard.View(map.viewW, map.viewH, yaw, shift01, SystemClock.uptimeMillis() / 1000f, Yard.palNow())
        val things = ArrayList<Pair<Float, Any>>(Yard.standing.size + live.size * 2)
        for (th in Yard.standing) things += th.feet to th
        for (c in live) { if (c.id != held) things += c.feet01 + 0.0001f to c; c.cover?.let { things += it.feet to it } }
        things.sortBy { it.first }
        for ((_, o) in things) if (o is Yard.Thing) Yard.draw(cv, v, o) else critter(cv, v, o as One, 0f)
        live.firstOrNull { it.id == held }?.let { critter(cv, v, it, dy) }
    }

    private fun critter(cv: Canvas, v: Yard.View, c: One, lift: Float) {
        if (!c.onScreen) return
        val r = c.rect
        if (lift == 0f) Yard.groundShadow(cv, v, r.centerX(), r.bottom, r.width() * 0.55f, r.height() * 0.1f, 0.38f)
        val peek = if (c.cover != null && lift == 0f) (0.5f + 0.5f * kotlin.math.sin(v.t * 1.4f + c.az)) * r.height() * 0.14f else 0f
        cv.drawBitmap(c.pic, null, RectF(r.left, r.top + lift + peek, r.right, r.bottom + lift + peek), paint)
    }

    override fun caught(critterId: String, map: ScreenMap): Pair<String, CutOut.Cut?> {
        val c = live.firstOrNull { it.id == critterId } ?: return "dir0" to null
        live.remove(c)
        lastCaughtAz = c.az
        return "dir${c.dir}" to cutOf(c.pic, c.rect, map)
    }

    /** Keep the stage full (camera mode 3, scene mode 6), at least 20 degrees apart and away from the one just caught. */
    private fun fill() {
        if (ids.isEmpty()) return
        val count = if (scene) SCENE_COUNT else COUNT
        while (live.size < count) {
            val free = ids.filter { id -> live.none { it.id == id } }.ifEmpty { return }
            val id = free[rnd.nextInt(free.size)]
            var az = yaw
            var feet = 0f
            for (i in 0 until 16) {
                feet = FEET_MIN + rnd.nextFloat() * (FEET_MAX - FEET_MIN)
                az = yaw + (rnd.nextFloat() * 2 - 1) * (if (scene) SCENE_SPREAD_DEG else SPREAD_DEG)
                val taken = live.map { it.az } + listOfNotNull(lastCaughtAz)
                if (taken.all { abs(diff(az, it)) >= 20f }) break
            }
            val dir = rnd.nextInt(8)
            val pic = Frames.asset(ctx, id, "dir$dir")?.let(Frames::trim) ?: return
            // scene mode: every other spawn hides behind a bush, a tree or a grass clump just in front of it
            val n = spawns++
            val cover = if (scene && n % 2 == 1) intArrayOf(Yard.BUSH, Yard.TREE, Yard.GRASS)[(n / 2) % 3].let { k ->
                Yard.Thing(k, az + if (k == Yard.TREE) 1.5f else 0.5f, feet + 0.025f, 1.1f, n.toFloat())
            } else null
            live += One(id, dir, az, feet, 0.8f + rnd.nextFloat() * 0.4f, pic, cover)
        }
    }

    companion object {
        const val COUNT = 3
        const val SCENE_COUNT = 6
        const val SCENE_SPREAD_DEG = 90f
        /** Scene-mode critter size vs camera mode: mockup x1.3, then x0.8 (operator 11-13). */
        const val SCENE_SIZE = 1.04f
        // ponytail: vertical field of view and tilt direction guessed from the Android docs; calibrate on the phone
        const val VFOV_DEG = 60f
        const val PITCH_SIGN = -1f
        /** Feet line range (share of the view height): FEET_MIN is the far edge of the floor. */
        const val FEET_MIN = 0.6f
        const val FEET_MAX = 0.93f

        /** Size by distance: 0.45 at the far edge .. 1.0 at the near edge. */
        fun perspective(feet01: Float): Float =
            0.45f + 0.55f * ((feet01 - FEET_MIN) / (FEET_MAX - FEET_MIN)).coerceIn(0f, 1f)
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
