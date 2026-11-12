package kr.dot.catchpet.ar

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import kr.dot.catchpet.camera.ArSource
import kr.dot.catchpet.game.Box
import kr.dot.catchpet.game.Mask
import kr.dot.catchpet.pet.Frames
import kr.dot.catchpet.seg.CutOut
import kr.dot.catchpet.seg.ScreenMap
import kr.dot.catchpet.ui.CritterSource
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.hypot
import kotlin.random.Random

/**
 * AR critters (plan Task 6 "ARCore 일 때"): three fur critters anchored on a detected floor plane 1-3 m away,
 * ~8 cm tall at critter_scale 1/4. Each is an upright sprite facing the camera, showing the baked view (dir0..7)
 * for the angle it is seen from. World work runs on the GL thread ([ArSource.onFrame]); the UI thread only reads
 * the published view rects, which become Mask.ellipseIn(CRITTER) for the claw like the gyro critters.
 */
class ArCritters(private val ctx: Context, private val ar: ArSource, private val rnd: Random = Random.Default) : CritterSource {
    private class One(val id: String, val facing: Float, val anchor: Anchor, val pics: List<Bitmap>)
    private class Shown(val id: String, val dir: Int, val pic: Bitmap, val rect: RectF)

    private val ids: List<String> by lazy { Critters.ids(ctx) }
    // GL thread only
    private val live = mutableListOf<One>()
    private var lastCaught: FloatArray? = null
    private var planeFrames = 0
    private val vp = FloatArray(16)
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private var heightM = ArMath.heightM(0.25f)
    // GL -> UI
    @Volatile private var shown: List<Shown> = emptyList()
    @Volatile private var seenFloor = false
    private val gone = ConcurrentLinkedQueue<String>()

    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER; color = Color.WHITE
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
    }

    override fun start() {
        heightM = ArMath.heightM(ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getFloat("critter_scale", 0.25f))
        ar.onFrame = ::update
    }

    override fun stop() { ar.onFrame = null }

    override fun masks(map: ScreenMap): List<Mask> = shown.filter { visible(it.rect, map) }.map { s ->
        val (x0, y0) = map.toGrid(s.rect.left, s.rect.top)
        val (x1, y1) = map.toGrid(s.rect.right, s.rect.bottom)
        Mask.ellipseIn(map.gridW, map.gridH, Box(x0, y0, x1, y1), Mask.Kind.CRITTER, s.id)
    }

    override fun draw(cv: Canvas, map: ScreenMap, held: String?, dy: Float) {
        val now = shown
        if (!seenFloor) {
            hint.textSize = map.viewW * 0.045f
            cv.drawText("바닥을 천천히 비춰 주세요", map.viewW / 2, map.viewH * 0.6f, hint)
        }
        // far ones first, so near ones overlap them
        for (c in now.sortedBy { it.rect.bottom }) {
            if (!visible(c.rect, map)) continue
            val r = c.rect
            if (c.id != held) cv.drawOval(r.left + r.width() * 0.1f, r.bottom - r.height() * 0.06f, r.right - r.width() * 0.1f, r.bottom + r.height() * 0.06f, shadow)
            val lift = if (c.id == held) dy else 0f
            cv.drawBitmap(c.pic, null, RectF(r.left, r.top + lift, r.right, r.bottom + lift), paint)
        }
    }

    override fun caught(critterId: String, map: ScreenMap): Pair<String, CutOut.Cut?> {
        val c = shown.firstOrNull { it.id == critterId } ?: return "dir0" to null
        shown = shown.filter { it.id != critterId }
        gone += critterId
        return "dir${c.dir}" to Critters.cutOf(c.pic, c.rect, map)
    }

    private fun visible(r: RectF, map: ScreenMap) = r.right > 0 && r.left < map.viewW && r.bottom > 0 && r.top < map.viewH

    // ---------- GL thread ----------
    private fun update(s: Session, f: Frame, vw: Int, vh: Int) {
        while (true) {
            val id = gone.poll() ?: break
            live.firstOrNull { it.id == id }?.let {
                lastCaught = floatArrayOf(it.anchor.pose.tx(), it.anchor.pose.tz())
                it.anchor.detach(); live.remove(it)
            }
        }
        live.removeAll { c -> (c.anchor.trackingState == TrackingState.STOPPED).also { if (it) c.anchor.detach() } }
        val cam = f.camera
        if (cam.trackingState != TrackingState.TRACKING) { shown = emptyList(); return }
        val eye = cam.displayOrientedPose
        if (live.size < Critters.COUNT) spawn(s, eye)

        cam.getProjectionMatrix(proj, 0, 0.05f, 50f)
        cam.getViewMatrix(view, 0)
        android.opengl.Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        val out = ArrayList<Shown>(live.size)
        for (c in live) {
            if (c.anchor.trackingState != TrackingState.TRACKING) continue
            val p = c.anchor.pose
            val foot = ArMath.project(vp, p.tx(), p.ty(), p.tz(), vw.toFloat(), vh.toFloat()) ?: continue
            val top = ArMath.project(vp, p.tx(), p.ty() + heightM, p.tz(), vw.toFloat(), vh.toFloat()) ?: continue
            val hPx = foot[1] - top[1]
            if (hPx < 2f) continue
            val dir = ArMath.dirIndex(c.facing, ArMath.bearing(p.tx(), p.tz(), eye.tx(), eye.tz()), DIR_SIGN)
            val pic = c.pics[dir]
            val wPx = hPx * pic.width / pic.height
            out += Shown(c.id, dir, pic, RectF(foot[0] - wPx / 2, foot[1] - hPx, foot[0] + wPx / 2, foot[1]))
        }
        shown = out
    }

    /** One new critter on a floor plane below the camera, 1-3 m ahead within +-25 degrees, apart from the others. */
    private fun spawn(s: Session, eye: Pose) {
        val planes = s.getAllTrackables(Plane::class.java).filter {
            it.type == Plane.Type.HORIZONTAL_UPWARD_FACING && it.trackingState == TrackingState.TRACKING &&
                it.subsumedBy == null && eye.ty() - it.centerPose.ty() > 0.3f
        }
        if (planes.isEmpty()) return
        seenFloor = true
        planeFrames++
        val id = ids.filter { i -> live.none { it.id == i } }.ifEmpty { return }.let { it[rnd.nextInt(it.size)] }
        // heading: where the camera looks, flattened; looking straight down, the screen's up
        val z = eye.zAxis; val y = eye.yAxis
        var hx = -z[0]; var hz = -z[2]
        if (hypot(hx, hz) < 0.3f) { hx = y[0]; hz = y[2] }
        repeat(12) {
            val pl = planes[rnd.nextInt(planes.size)]
            val (x, zz) = ArMath.ground(eye.tx(), eye.tz(), hx, hz, 1f + rnd.nextFloat() * 2f, (rnd.nextFloat() * 2f - 1f) * 25f)
            val pose = Pose.makeTranslation(x, pl.centerPose.ty(), zz)
            val inside = pl.isPoseInPolygon(pose)
            // ponytail: after ~3 s on a small plane, accept its extension (rooms rarely map fully); tune on a phone
            if (!inside && planeFrames < 90) return@repeat
            if (live.any { ArMath.flatDist(it.anchor.pose.tx(), it.anchor.pose.tz(), x, zz) < 0.4f }) return@repeat
            lastCaught?.let { if (ArMath.flatDist(it[0], it[1], x, zz) < 0.5f) return@repeat }
            val pics = (0 until 8).map { d -> Frames.asset(ctx, id, "dir$d")?.let(Frames::trim) ?: return }
            val anchor = if (inside) pl.createAnchor(pose) else s.createAnchor(pose)
            live += One(id, rnd.nextFloat() * 360f, anchor, pics)
            return
        }
    }

    private companion object {
        // calibration knob: flip to -1 if critters turn the wrong way as you walk around them
        const val DIR_SIGN = 1
    }
}
