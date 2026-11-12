package kr.dot.catchpet.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.dot.catchpet.camera.CameraXSource
import kr.dot.catchpet.camera.FrameSource
import kr.dot.catchpet.game.Capsule
import kr.dot.catchpet.game.Claw
import kr.dot.catchpet.game.Mask
import kr.dot.catchpet.game.iou
import kr.dot.catchpet.seg.CutOut
import kr.dot.catchpet.seg.ScreenMap
import kr.dot.catchpet.seg.Segmenter
import java.util.concurrent.Future
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Where a catch goes (the Dex wires this). Called the moment the claw reaches the top (plan R6), before the capsule. */
interface CatchSink {
    /** png: the cut-out (real things) or null (critters). label: the pose. */
    fun onCaught(kind: Mask.Kind, png: Bitmap?, critterId: String?, label: String)
}

/** Stand-in sink until the Dex exists: counts catches. */
class CountingSink : CatchSink {
    var count by mutableIntStateOf(0); private set
    override fun onCaught(kind: Mask.Kind, png: Bitmap?, critterId: String?, label: String) { count++ }
}

/** Virtual critters on screen this frame, as masks on the segmenter grid (ar/Critters fills this in). */
fun interface CritterSource { fun masks(map: ScreenMap): List<Mask> }

val NoCritters = CritterSource { emptyList() }

@Composable
fun CatchScreen(sink: CatchSink, critters: CritterSource, dexCount: Int, onOpenDex: () -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }
    if (!granted) {
        Column(
            Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFF101114)).padding(24.dp),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("카메라를 켜야 녀석을 뽑을 수 있어요.", color = androidx.compose.ui.graphics.Color.White, fontSize = 17.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("카메라 켜기") }
        }
        return
    }

    val owner = LocalLifecycleOwner.current
    val src = remember { CameraXSource(ctx, owner) }
    val seg = remember { Segmenter(ctx.applicationContext) }
    val game = remember { CatchGame(src, seg, sink, critters) }
    DisposableEffect(Unit) {
        seg.start(src::latest)
        onDispose { seg.close(); src.close() }
    }
    val dens = LocalDensity.current
    game.d = dens.density
    game.topInset = WindowInsets.statusBars.getTop(dens) / dens.density
    game.bottomInset = WindowInsets.navigationBars.getBottom(dens) / dens.density
    game.dexCount = dexCount
    val openDex by rememberUpdatedState(onOpenDex)

    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var prev = 0L
        while (true) withFrameNanos { now ->
            val dt = if (prev == 0L) 16f else ((now - prev) / 1e6f).coerceIn(0f, 48f)
            prev = now
            game.step(dt)
            tick = now
        }
    }
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
        src.Preview(Modifier.fillMaxSize())
        Canvas(
            Modifier.fillMaxSize()
                .onSizeChanged { game.setSize(it.width / game.d, it.height / game.d) }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val x = down.position.x / game.d; val y = down.position.y / game.d
                        if (y < game.rail && x > game.w / 2) {          // the "도감 N" corner
                            if (waitForUpOrCancellation() != null) openDex()
                            return@awaitEachGesture
                        }
                        val c = game.claw ?: return@awaitEachGesture
                        if (c.st != Claw.St.AIM) return@awaitEachGesture
                        c.aim(x / game.w)
                        while (true) {
                            val ch = awaitPointerEvent().changes.first()
                            if (!ch.pressed) break
                            c.aim(ch.position.x / game.d / game.w)
                        }
                        c.release(); game.hint = false
                    }
                },
        ) {
            tick // redraw every frame
            drawIntoCanvas { game.render(it.nativeCanvas) }
        }
    }
}

/**
 * One catch screen's game: the Claw state machine, the GRIP cut-out, the capsule, toasts, and the drawing
 * (mockups/claw-endings.html, capsule ending). Everything is in dp; the view is scaled by [d] when drawn.
 */
private class CatchGame(
    private val src: FrameSource,
    private val seg: Segmenter,
    private val sink: CatchSink,
    private val critters: CritterSource,
) {
    var d = 1f
    var topInset = 0f
    var bottomInset = 0f
    var dexCount = 0
    var hint = true
    var w = 0f; private set
    var h = 0f; private set
    val rail get() = topInset + 54f
    var claw: Claw? = null; private set

    private class Toast(val text: String, val good: Boolean) { var t = 0f }
    private class Grip(val cut: CutOut.Cut?, val hole: Bitmap?, val opaque: Float)

    private var map: ScreenMap? = null
    private var targets: List<Mask> = emptyList()
    private var time = 0f
    private var lastX = 0.5f
    private var swing = 0f
    private var vsw = 0f
    private var pulse = 0f
    private var toast: Toast? = null
    private var frozen: Bitmap? = null
    private var grip: Future<Grip>? = null
    private var gripAt = 0f
    private var held: Grip? = null
    private var capsule: Capsule? = null
    private var capT = 0f
    private var capText = ""
    private var saved = false   // the catch is already in the dex but the capsule still rolls: show N-1 until GONE

    fun setSize(wDp: Float, hDp: Float) {
        if (wDp == w && hDp == h) return
        w = wDp; h = hDp
        claw = Claw(w, h - bottomInset, rail) { Random.nextFloat() }
        reset()
    }

    fun step(dt: Float) {
        var c = claw ?: return
        if (c.railY != rail && c.st == Claw.St.AIM) {   // insets arrived after the first layout
            c = Claw(w, h - bottomInset, rail) { Random.nextFloat() }; claw = c
        }
        time += dt
        map = src.map(w * d, h * d)
        val m = map
        targets = if (m == null) emptyList() else seg.masks + critters.masks(m)
        // sway (mockup): the hub lags behind fast aiming
        val acc = (c.x - lastX) * w; lastX = c.x
        vsw += (-swing * 0.012f - acc * 0.0009f) * dt
        vsw *= 0.992f.pow(dt)
        swing = (swing + vsw * dt * 0.06f).coerceIn(-0.3f, 0.3f)
        pulse = max(0f, pulse - dt / 500f)
        toast?.let { it.t += dt; if (it.t > 1400f) toast = null }
        grip?.let { if (held == null && it.isDone) held = runCatching { it.get() }.getOrNull() }

        val before = c.st
        c.step(dt, targets) { x, y -> m?.toGrid(x * d, y * d) ?: (-1 to -1) }
        if (before == Claw.St.DOWN && c.st == Claw.St.GRIP) onGrip(c.result!!)
        when (c.st) {
            Claw.St.CARRY -> if (capsule == null) onCarry(c)
            Claw.St.DONE -> if (capsule == null) {
                val r = c.result!!
                say(if (r.target == null) "헛집었다" else "아깝다! (${(r.p * 100).roundToInt()}%)", false)
                next()
            }
            else -> {}
        }
        capsule?.let { cap ->
            val was = cap.phase
            cap.step(dt); capT += dt
            if (was == Capsule.Phase.WRAP && cap.phase == Capsule.Phase.DROP) c.handedOff()   // claw lets go
            if (cap.phase == Capsule.Phase.GONE) { say(capText, true); pulse = 1f; next() }
        }
    }

    /** The tips touched something: freeze that frame and re-segment it on the model thread (plan R7, R16). */
    private fun onGrip(r: Claw.Result) {
        val target = r.target ?: return
        if (target.kind == Mask.Kind.CRITTER) return          // critters: no re-segment, no cut-out (R20)
        val f = src.latest() ?: return
        frozen = f
        gripAt = time
        grip = seg.onThread {
            val fresh = seg.run(f).filter { family(it.kind) == family(target.kind) }.maxByOrNull { iou(it, target) }
            makeGrip(f, fresh?.takeIf { iou(it, target) > 0f } ?: target)
        }
    }

    private fun makeGrip(f: Bitmap, m: Mask): Grip {
        val cut = CutOut.make(f, m)
        return Grip(cut, CutOut.hole(f, m), cut?.let { CutOut.opaque(it.bmp) } ?: 0f)
    }

    /** At the top with a win: wait for the GRIP cut (max 1.5 s, then the old mask), save, then the capsule. */
    private fun onCarry(c: Claw) {
        val target = c.result!!.target!!
        if (target.kind == Mask.Kind.CRITTER) {
            sink.onCaught(Mask.Kind.CRITTER, null, target.critterId, "front")
            startCapsule(c, "털 녀석을 뽑았다!")
            return
        }
        val g = grip
        if (held == null && g != null && !g.isDone && time - gripAt < 1500f) return
        if (held == null) {
            g?.cancel(false)
            held = frozen?.let { f -> runCatching { makeGrip(f, target) }.onFailure { Log.w(TAG, "cut failed", it) }.getOrNull() }
        }
        val got = held
        if (got?.cut == null || got.opaque < 0.05f) {
            c.handedOff(); next(); say("아깝다!", false)
            return
        }
        sink.onCaught(target.kind, got.cut.bmp, null, "front")
        startCapsule(c, when (target.kind) {
            Mask.Kind.CAT -> "고양이를 뽑았다!"
            Mask.Kind.DOG -> "강아지를 뽑았다!"
            else -> "사람을 뽑았다!"
        })
    }

    private fun startCapsule(c: Claw, text: String) {
        saved = true
        capText = text
        capT = 0f
        capsule = Capsule(c.x * w, c.y + 70f, floorY = h - bottomInset - 18f, screenW = w)
    }

    private fun next() {
        claw?.next()
        reset()
    }

    private fun reset() {
        frozen = null; grip = null; held = null; capsule = null; saved = false
    }

    private fun say(text: String, good: Boolean) { toast = Toast(text, good) }

    // ---------- drawing (dp) ----------
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val rect = RectF()

    fun render(cv: android.graphics.Canvas) {
        val c = claw ?: return
        cv.save()
        cv.scale(d, d)
        val m = map
        // the frozen GRIP frame over the live preview, with the hole the catch leaves once the cut is ready
        val f = frozen
        if (f != null && m != null && c.st != Claw.St.AIM) {
            imageRect(m)
            cv.drawBitmap(f, null, rect, bmpPaint)
            held?.hole?.let { cv.drawBitmap(it, null, rect, bmpPaint) }
        }
        // subtle glass
        fill.shader = LinearGradient(0f, 0f, w, h * 0.7f, intArrayOf(argb(.07f, 255, 255, 255), 0), floatArrayOf(0f, 0.4f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, fill); fill.shader = null

        val cx = c.x * w
        if (c.st == Claw.St.AIM) aimGuide(cv, c, cx, m)

        // the carried catch
        val cut = held?.cut
        if (cut != null && m != null && capsule == null && c.st in listOf(Claw.St.UP, Claw.St.FALL, Claw.St.CARRY)) {
            cutRect(m, cut)
            rect.offset(0f, c.carryDy)
            cv.drawBitmap(cut.bmp, null, rect, bmpPaint)
        }
        capsule?.let { drawCapsule(cv, it, cut, m) }
        drawClaw(cv, cx, c.y, c.open, if (c.st == Claw.St.AIM) swing else swing * 0.3f)
        topBar(cv)
        if (hint) {
            text.textSize = 18f; text.color = argb(.92f, 255, 255, 255); text.setShadowLayer(8f, 0f, 0f, Color.BLACK)
            cv.drawText("집게를 좌우로 끌다가 놓으세요", w / 2, h - bottomInset - 28f, text)
            text.clearShadowLayer()
        }
        drawToast(cv)
        cv.restore()
    }

    private fun imageRect(m: ScreenMap) {
        rect.set(m.left / d, m.top / d, (m.left + m.imgW * m.scale) / d, (m.top + m.imgH * m.scale) / d)
    }

    private fun cutRect(m: ScreenMap, cut: CutOut.Cut) {
        rect.set(
            m.imageToViewX(cut.src.left.toFloat()) / d, m.imageToViewY(cut.src.top.toFloat()) / d,
            m.imageToViewX(cut.src.right.toFloat()) / d, m.imageToViewY(cut.src.bottom.toFloat()) / d,
        )
    }

    private fun aimGuide(cv: android.graphics.Canvas, c: Claw, cx: Float, m: ScreenMap?) {
        var ly = h - bottomInset - 50f
        var hit = false
        if (m != null) {
            val col = m.toGrid(cx * d, 0f).first
            val row = targets.mapNotNull { it.firstRowInColumn(col) }.minOrNull()
            if (row != null) { ly = m.gridToViewY(row + 0.5f) / d; hit = true }
        }
        stroke.strokeWidth = 1.5f; stroke.color = argb(.55f, 255, 255, 255)
        stroke.pathEffect = android.graphics.DashPathEffect(floatArrayOf(3f, 7f), 0f)
        cv.drawLine(cx, c.y + 52f, cx, ly, stroke)
        stroke.pathEffect = null
        stroke.strokeWidth = 2f; stroke.color = if (hit) YELLOW else argb(.4f, 255, 255, 255)
        rect.set(cx - 13f, ly - 4.5f, cx + 13f, ly + 4.5f)
        cv.drawOval(rect, stroke)
    }

    private fun drawClaw(cv: android.graphics.Canvas, cx: Float, cy: Float, open: Float, sw: Float) {
        val top = rail
        cv.save()
        cv.translate(cx, top); cv.rotate(Math.toDegrees(sw.toDouble()).toFloat())
        val len = cy - top
        stroke.strokeWidth = 1.6f; stroke.color = argb(.9f, 230, 233, 240); stroke.strokeCap = Paint.Cap.BUTT
        cv.drawLine(0f, 0f, 0f, len, stroke)
        cv.translate(0f, len)
        fill.shader = LinearGradient(-16f, 0f, 16f, 0f, intArrayOf(0xFF80868F.toInt(), 0xFFF6F7FA.toInt(), 0xFF6D727C.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        cv.drawRoundRect(-15f, -7f, 15f, 10f, 6f, 6f, fill)
        fill.shader = null; fill.color = 0xFF2B2F36.toInt()
        cv.drawRect(-15f, 4f, 15f, 6f, fill)
        val sp = 8f + open * 20f
        stroke.strokeCap = Paint.Cap.ROUND
        for (k in intArrayOf(-1, 0, 1)) {
            if (k != 0) {
                stroke.shader = LinearGradient(k * sp - 4f, 0f, k * sp + 4f, 0f, intArrayOf(0xFF8F95A0.toInt(), Color.WHITE, 0xFF7F8590.toInt()), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
                stroke.strokeWidth = 5f
            } else {
                stroke.shader = null; stroke.color = argb(.7f, 210, 214, 222); stroke.strokeWidth = 3.5f
            }
            path.reset(); path.moveTo(k * 8f, 9f); path.quadTo(k * (sp + 10f), 28f, k * (sp - 5f), if (k != 0) 50f else 40f)
            cv.drawPath(path, stroke)
        }
        stroke.shader = null; stroke.strokeCap = Paint.Cap.BUTT
        cv.restore()
    }

    private fun drawCapsule(cv: android.graphics.Canvas, cap: Capsule, cut: CutOut.Cut?, m: ScreenMap?) {
        val r = cap.r
        cv.save()
        cv.translate(cap.x, cap.y); cv.rotate(Math.toDegrees(cap.rot.toDouble()).toFloat())
        if (r > 4f) {
            path.reset(); path.addCircle(0f, 0f, r, Path.Direction.CW); cv.clipPath(path)
            fill.color = argb(.12f, 255, 255, 255); cv.drawCircle(0f, 0f, r, fill)
        }
        if (cut != null && m != null) {
            // the catch shrinks into the capsule
            cutRect(m, cut)
            val bw = rect.width(); val bh = rect.height()
            val sc = if (r > 0f) min(1f, r * 1.5f / max(bw, bh)) else 1f
            val s2 = 1f + (sc - 1f) * min(1f, capT / 420f)
            cv.scale(s2, s2)
            cv.translate(-rect.centerX(), -rect.centerY())
            cv.drawBitmap(cut.bmp, null, rect, bmpPaint)
        }
        cv.restore()
        if (r <= 4f) return
        cv.save()
        cv.translate(cap.x, cap.y)
        // glass: faint body with a bright rim, plus a highlight up-left
        fill.shader = RadialGradient(0f, 0f, r, intArrayOf(argb(.06f, 255, 255, 255), argb(.06f, 255, 255, 255), argb(.55f, 255, 255, 255)), floatArrayOf(0f, .85f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(0f, 0f, r, fill)
        fill.shader = RadialGradient(-r * .35f, -r * .4f, r * .5f, intArrayOf(argb(.75f, 255, 255, 255), argb(.12f, 255, 255, 255), 0), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(0f, 0f, r, fill)
        fill.shader = null
        stroke.color = argb(.7f, 255, 255, 255); stroke.strokeWidth = 1.5f
        cv.drawCircle(0f, 0f, r, stroke)
        // seam
        cv.rotate(Math.toDegrees(cap.rot.toDouble()).toFloat())
        stroke.color = argb(.8f, 255, 207, 90); stroke.strokeWidth = 2f
        cv.drawLine(-r, 0f, r, 0f, stroke)
        cv.restore()
    }

    private fun topBar(cv: android.graphics.Canvas) {
        val r = rail
        fill.shader = LinearGradient(0f, 0f, 0f, r, argb(.88f, 10, 11, 14), argb(.55f, 10, 11, 14), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, r, fill)
        fill.shader = LinearGradient(0f, r - 6f, 0f, r + 3f, 0xFFF2F4F8.toInt(), 0xFF7D838F.toInt(), Shader.TileMode.CLAMP)
        cv.drawRect(0f, r - 5f, w, r + 1f, fill)
        fill.shader = null
        // dex counter, top right (pulses when a capsule lands)
        val sc = 1f + 0.25f * sin(min(1f, pulse) * Math.PI.toFloat())
        cv.save()
        cv.translate(w - 50f, r - 27f); cv.scale(sc, sc)
        fill.color = argb(.1f, 255, 255, 255)
        cv.drawRoundRect(-36f, -15f, 36f, 15f, 15f, 15f, fill)
        text.textSize = 17f; text.textAlign = Paint.Align.CENTER
        text.color = if (pulse > 0f) YELLOW else Color.WHITE
        cv.drawText("도감 ${dexCount - if (saved) 1 else 0}", 0f, 6f, text)
        cv.restore()
        text.textSize = 14f; text.textAlign = Paint.Align.LEFT; text.color = argb(.55f, 255, 255, 255)
        cv.drawText("우리 집 뽑기", 16f, r - 22f, text)
        text.textAlign = Paint.Align.CENTER
    }

    private fun drawToast(cv: android.graphics.Canvas) {
        val t = toast ?: return
        val a = when { t.t < 150f -> t.t / 150f; t.t > 1100f -> max(0f, 1f - (t.t - 1100f) / 300f); else -> 1f }
        if (a <= 0f) return
        text.textSize = 20f
        val tw = text.measureText(t.text) + 34f
        val y = rail + 34f - (1f - a) * 8f
        fill.color = if (t.good) argb(.95f * a, 255, 207, 90) else argb(.82f * a, 20, 22, 27)
        cv.drawRoundRect(w / 2 - tw / 2, y, w / 2 + tw / 2, y + 36f, 18f, 18f, fill)
        text.color = if (t.good) Color.argb((255 * a).toInt(), 0x2A, 0x1D, 0) else Color.argb((255 * a).toInt(), 255, 255, 255)
        cv.drawText(t.text, w / 2, y + 25f, text)
    }

    companion object {
        private const val TAG = "CatchGame"
        private val YELLOW = 0xFFFFCF5A.toInt()
        private fun argb(a: Float, r: Int, g: Int, b: Int) = Color.argb((a * 255).roundToInt().coerceIn(0, 255), r, g, b)
        private fun family(k: Mask.Kind) = if (k == Mask.Kind.CAT) Mask.Kind.DOG else k
    }
}
