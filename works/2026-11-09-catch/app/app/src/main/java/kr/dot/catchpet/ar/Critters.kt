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
import kr.dot.catchpet.game.Life
import kr.dot.catchpet.game.Pose
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
    private class One(val id: String, val dir: Int, var az: Float, val feet01: Float, val size: Float, val pic: Bitmap, val cover: Yard.Thing? = null) {
        val rect = RectF()   // view px at rest (masks use it), updated each frame
        var onScreen = false
        val seed = Random.nextFloat()
        var clock = Random.nextFloat() * 20000f   // its own life clock (ms); stands still while the claw is over it
        var landAt = -1L                          // uptime ms of a landing after a drop, -1 = none
        var pose = Pose.REST
        var side: Array<Bitmap?>? = null          // dir-1, dir+1 pictures for looking aside, loaded on first look
    }

    private val ids: List<String> by lazy { Companion.ids(ctx) }
    private val live = mutableListOf<One>()
    private var lastCaughtAz: Float? = null
    private var spawns = 0
    private var yaw = 0f
    private var yawGoal = 0f
    private var lastRaw = Float.NaN
    private var pitchBase: Float? = null
    private var tilt = 0f   // degrees from the pitch the screen opened at
    private val sm = ctx.getSystemService(SensorManager::class.java)
    private val rot = FloatArray(9)
    private val remapped = FloatArray(9)
    private val ori = FloatArray(3)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val tmp = RectF()
    private var lastNow = 0L
    private var viewW = 1f
    private var wary: Float? = null
    private var heldId: String? = null

    private val prefs = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE)
    /** critter_scale x the web figure (a creature ~40% of the screen height): the nearest one at 0.25 is 10 % of it. */
    private val heightFrac get() = prefs.getFloat("critter_scale", 0.25f) * 0.4f

    override fun start() {
        pitchBase = null; lastRaw = Float.NaN
        sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun stop() { sm?.unregisterListener(this) }

    override fun onSensorChanged(e: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rot, e.values)
        // phone upright, camera looking out the back
        SensorManager.remapCoordinateSystem(rot, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapped)
        SensorManager.getOrientation(remapped, ori)
        val a = Math.toDegrees(ori[0].toDouble()).toFloat()
        if (scene) {
            // the drawn yard turns at half the phone's turn, smoothed more (operator 11-13 "너무 민감해")
            if (!lastRaw.isNaN()) yawGoal += diff(a, lastRaw) * SCENE_GAIN
            lastRaw = a
            yaw += (yawGoal - yaw) * 0.25f
        } else yaw += diff(a, yaw) * 0.5f   // light smoothing
        val p = Math.toDegrees(ori[1].toDouble()).toFloat()
        val base = pitchBase ?: p.also { pitchBase = it }
        tilt += ((p - base) - tilt) * 0.5f
    }

    /** Vertical shift of the floor, share of the view height (+ = down), from tilting since the screen opened. */
    val shift01: Float get() = (PITCH_SIGN * tilt * (if (scene) SCENE_GAIN else 1f) / VFOV_DEG).coerceIn(-0.3f, 0.3f)

    override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}

    override fun masks(map: ScreenMap): List<Mask> {
        fill()
        val now = SystemClock.uptimeMillis()
        val dt = if (lastNow == 0L) 16f else (now - lastNow).coerceIn(0L, 100L).toFloat()
        lastNow = now
        viewW = map.viewW
        val out = ArrayList<Mask>(live.size)
        for (c in live) {
            val h = map.viewH * heightFrac * perspective(c.feet01) * c.size * (if (scene) SCENE_SIZE else 1f)
            val w = h * c.pic.width / c.pic.height
            // life: idle (frozen and cringing while the claw comes down over it) or the landing after a drop
            val over = wary?.let { abs(it - c.rect.centerX()) < c.rect.width() * 0.6f } == true
            if (!over && c.id != heldId) c.clock += dt   // the one on the claw keeps still (its mask must not wander)
            c.pose = when {
                c.landAt >= 0 && now - c.landAt < Life.LAND_MS -> Life.land((now - c.landAt).toFloat())
                else -> { c.landAt = -1; Life.idle(c.clock, c.seed).let { if (over) it.copy(sy = it.sy * 0.94f, dy = 0f) else it } }
            }
            val cx = map.viewW / 2 + diff(c.az + c.pose.daz, yaw) / FOV_DEG * map.viewW
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

    override fun draw(cv: Canvas, map: ScreenMap, held: String?, wary: Float?) {
        this.wary = wary; heldId = held
        if (scene) return drawScene(cv, map, held)
        for (c in live.sortedBy { it.feet01 }) {   // far ones first
            if (!c.onScreen || c.id == held) continue
            critter(cv, null, c)
        }
    }

    /** Scene mode: the yard, then critters, the yard's standing things and the covers, far first. */
    private fun drawScene(cv: Canvas, map: ScreenMap, held: String?) {
        val v = Yard.View(map.viewW, map.viewH, yaw, shift01, SystemClock.uptimeMillis() / 1000f, Yard.palNow())
        Yard.back(cv, v)   // the yard is painted here, on the same canvas and frame as the critters (one redraw, never out of step)
        val things = ArrayList<Pair<Float, Any>>(Yard.standing.size + live.size * 2)
        for (th in Yard.standing) things += th.feet to th
        for (c in live) { if (c.id != held) things += c.feet01 + 0.0001f to c; c.cover?.let { things += it.feet to it } }
        things.sortBy { it.first }
        for ((_, o) in things) if (o is Yard.Thing) Yard.draw(cv, v, o) else critter(cv, v, o as One)
    }

    private fun shadowAt(cv: Canvas, v: Yard.View?, cx: Float, feet: Float, w: Float, h: Float, scale: Float) {
        if (v != null) Yard.groundShadow(cv, v, cx, feet, w * 0.55f * scale, h * 0.1f * scale, 0.38f * scale)
        else { shadow.alpha = (0x33 * scale).toInt(); cv.drawOval(cx - w * 0.4f * scale, feet - h * 0.06f * scale, cx + w * 0.4f * scale, feet + h * 0.06f * scale, shadow) }
    }

    /** A critter on the floor with its life pose (hiding ones bob up from behind their cover). */
    private fun critter(cv: Canvas, v: Yard.View?, c: One) {
        if (!c.onScreen) return
        val r = c.rect
        val p = c.pose
        shadowAt(cv, v, r.centerX(), r.bottom, r.width(), r.height(), Posed.shadowScale(-p.dy * r.height(), r.height()))
        val peek = if (c.cover != null && c.landAt < 0) (0.5f + 0.5f * kotlin.math.sin(c.clock / 1000f * 1.4f + c.az)) * 0.14f else 0f
        Posed.draw(cv, picOf(c, p.look), r, if (peek != 0f) p.copy(dy = p.dy + peek) else p, paint)
        if (c.landAt >= 0) Posed.dust(cv, r, (SystemClock.uptimeMillis() - c.landAt).toFloat(), v?.pal?.dark == true)
    }

    /** Its own picture, or the neighbouring direction while it looks aside. */
    private fun picOf(c: One, look: Int): Bitmap {
        if (look == 0) return c.pic
        val side = c.side ?: arrayOfNulls<Bitmap>(2).also { a ->
            c.side = a
            for ((i, d) in intArrayOf(-1, 1).withIndex()) a[i] = Frames.asset(ctx, c.id, "dir${(c.dir + d + 8) % 8}")?.let(Frames::trim)
        }
        return side[if (look < 0) 0 else 1] ?: c.pic
    }

    override fun drawHeld(cv: Canvas, map: ScreenMap, held: String, cx: Float, dy: Float, pose: Pose) {
        val c = live.firstOrNull { it.id == held } ?: return
        val r = c.rect
        val v = if (scene) Yard.View(map.viewW, map.viewH, yaw, shift01, SystemClock.uptimeMillis() / 1000f, Yard.palNow()) else null
        shadowAt(cv, v, cx, r.bottom, r.width(), r.height(), Posed.shadowScale(-dy, r.height()))
        tmp.set(r); tmp.offset(cx - r.centerX(), dy)
        Posed.draw(cv, c.pic, tmp, pose, paint)
    }

    override fun landed(id: String, cx: Float) {
        val c = live.firstOrNull { it.id == id } ?: return
        c.az = c.az + (cx - c.rect.centerX()) / viewW * FOV_DEG   // it lands where it fell
        c.landAt = SystemClock.uptimeMillis()
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
        const val SCENE_SPREAD_DEG = 60f   // in yard degrees: 120 degrees of real turning at SCENE_GAIN
        /** Camera-off yard: share of the phone's turn and tilt the yard follows (1 = like the camera). */
        const val SCENE_GAIN = 0.5f
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
