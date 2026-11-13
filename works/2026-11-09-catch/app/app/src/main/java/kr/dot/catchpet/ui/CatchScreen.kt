package kr.dot.catchpet.ui

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.key
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
import kr.dot.catchpet.ar.ArCritters
import kr.dot.catchpet.camera.ArSource
import kr.dot.catchpet.camera.CameraXSource
import kr.dot.catchpet.camera.FrameSource
import kr.dot.catchpet.camera.SceneSource
import kr.dot.catchpet.ar.Critters
import kr.dot.catchpet.dex.Dex
import kr.dot.catchpet.game.Capsule
import android.view.HapticFeedbackConstants
import kr.dot.catchpet.ar.Posed
import kr.dot.catchpet.game.Claw
import kr.dot.catchpet.game.Dangle
import kr.dot.catchpet.game.Pose
import kr.dot.catchpet.game.sameThing
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
    /** png: the cut-out (real things) or null (critters). label: the pose. True when the dex count grows. */
    fun onCaught(kind: String, png: Bitmap?, critterId: String?, label: String): Boolean
}

/** Virtual critters (ar/Critters): masks on the segmenter grid for the claw, drawn by the catch screen. */
interface CritterSource {
    fun masks(map: ScreenMap): List<Mask>
    /** Draw in view px, all but the critter on the claw ([held]); [wary] = the claw's x (view px) while it comes down. */
    fun draw(cv: android.graphics.Canvas, map: ScreenMap, held: String?, wary: Float?) {}
    /** The critter on the claw: centred at cx (view px), lifted dy px, posed (hanging, struggling, falling). */
    fun drawHeld(cv: android.graphics.Canvas, map: ScreenMap, held: String, cx: Float, dy: Float, pose: Pose) {}
    /** A dropped critter hit the floor at cx (view px): it lands there (squash, bounce, shake). */
    fun landed(id: String, cx: Float) {}
    /** It was caught: the pose label it showed (dirN) and its picture for the capsule. Another one appears. */
    fun caught(critterId: String, map: ScreenMap): Pair<String, CutOut.Cut?> = "front" to null
    fun start() {}
    fun stop() {}
}

object NoCritters : CritterSource { override fun masks(map: ScreenMap): List<Mask> = emptyList() }

@Composable
fun CatchScreen(sink: CatchSink, critters: CritterSource, dexCount: Int, onOpenDex: () -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE) }
    var granted by remember { mutableStateOf(ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var cameraOff by remember { mutableStateOf(prefs.getBoolean("camera_off", false)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; if (it) cameraOff = false }
    LaunchedEffect(Unit) { if (!granted && !cameraOff) ask.launch(Manifest.permission.CAMERA) }
    Box(Modifier.fillMaxSize()) {
        if (!granted || cameraOff) SceneCatch(ctx, sink, dexCount, onOpenDex)
        else CameraCatch(ctx, sink, critters, dexCount, onOpenDex)
        // camera on/off, like the AR switch in Pokémon GO (operator 11-13)
        Text(
            if (!granted || cameraOff) "카메라 켜기" else "카메라 끄기",
            color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp,
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(start = 12.dp, top = 10.dp)
                .background(androidx.compose.ui.graphics.Color(0x66000000), RoundedCornerShape(50))
                .clickable {
                    when {
                        cameraOff -> { cameraOff = false; prefs.edit().putBoolean("camera_off", false).apply(); if (!granted) ask.launch(Manifest.permission.CAMERA) }
                        !granted -> ask.launch(Manifest.permission.CAMERA)
                        else -> { cameraOff = true; prefs.edit().putBoolean("camera_off", true).apply() }
                    }
                }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun SceneCatch(ctx: Context, sink: CatchSink, dexCount: Int, onOpenDex: () -> Unit) {
    val crit = remember { Critters(ctx.applicationContext, scene = true) }
    val src = remember { SceneSource() }
    key(src) { CatchView(ctx, src, crit, sink, dexCount, onOpenDex) }
}

@Composable
private fun CameraCatch(ctx: Context, sink: CatchSink, critters: CritterSource, dexCount: Int, onOpenDex: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    // developer switch "AR 털 녀석": ARCore when it is installed and a session opens; any failure -> CameraX + gyro
    val wantAr = remember { ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getBoolean("ar", false) }
    var arFailed by remember { mutableStateOf(false) }
    val ar = remember(arFailed) { if (wantAr && !arFailed) ArSource.open(ctx, owner) { arFailed = true } else null }
    val src: FrameSource = remember(ar) { ar ?: CameraXSource(ctx, owner) }
    val crit: CritterSource = remember(ar) { ar?.let { ArCritters(ctx.applicationContext, it) } ?: critters }
    // the developer switch must show whether AR really runs (operator 11-13: "AR 을 켜도 바뀌는 게 없어")
    LaunchedEffect(ar, arFailed) {
        if (!wantAr) return@LaunchedEffect
        val msg = if (ar != null) "AR 켜짐: 털 녀석이 바닥에 붙어요" else "일반 모드: " + (ArSource.note ?: "AR 이 멈췄어요")
        android.widget.Toast.makeText(ctx, msg, android.widget.Toast.LENGTH_LONG).show()
    }
    key(src) { CatchView(ctx, src, crit, sink, dexCount, onOpenDex) }
}

@Composable
private fun CatchView(ctx: Context, src: FrameSource, critters: CritterSource, sink: CatchSink, dexCount: Int, onOpenDex: () -> Unit) {
    val seg = remember { Segmenter(ctx.applicationContext) }
    val game = remember { CatchGame(src, seg, sink, critters) }
    DisposableEffect(Unit) {
        seg.start(src::latest)
        critters.start()
        onDispose { critters.stop(); seg.close(); src.close() }
    }
    // Home / screen off: stop the model loop and the sensors; back: start them again (review M3)
    val life = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(life) {
        var stopped = false
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> if (!stopped) { stopped = true; seg.paused = true; critters.stop() }
                androidx.lifecycle.Lifecycle.Event.ON_START -> if (stopped) { stopped = false; seg.paused = false; critters.start() }
                else -> {}
            }
        }
        life.lifecycle.addObserver(obs)
        onDispose { life.lifecycle.removeObserver(obs) }
    }
    val dens = LocalDensity.current
    val hostView = androidx.compose.ui.platform.LocalView.current
    game.buzz = { hostView.performHapticFeedback(it) }
    game.d = dens.density
    game.topInset = WindowInsets.statusBars.getTop(dens) / dens.density
    game.bottomInset = WindowInsets.navigationBars.getBottom(dens) / dens.density
    game.dexCount = dexCount
    val openDex by rememberUpdatedState(onOpenDex)

    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var prev = 0L
        while (true) withFrameNanos { now ->
            // heat: at most ~60 frames a second, also on 90/120 Hz screens
            if (prev != 0L && now - prev < 15_000_000L) return@withFrameNanos
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
        if (game.askPerson != null) PersonConsent(PersonUse.DEX) { game.keepPerson(it) }
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
    /** Haptic feedback (HapticFeedbackConstants), set by the view. */
    var buzz: (Int) -> Unit = {}
    // feel (operator 11-13, Fable design note): the hanging catch, the claw's jolt on contact, a dropped photo's landing
    private val dangle = Dangle { Random.nextFloat() }
    private var heldPose = Pose.REST
    private var arms = ClawArt.idle(1f)
    private var hugTop = 0f          // dp, the grabbed body's top below the hub (fixed once it is lifted)
    private var heldCx = 0f          // dp, where the held thing's centre is drawn
    private var grabOff = 0f         // dp, thing centre minus claw x when it was grabbed (swings in under the claw)
    private var joltT = -1f          // ms since the tips touched, -1 = none
    private var closedBuzz = false
    private var landT = -1f          // ms since a dropped photo landed, -1 = none
    private var landFrozen: Bitmap? = null
    private var landGrip: Grip? = null

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
    private var pendingPerson: Bitmap? = null
    /** A caught person waiting for the player's "허락받았어요" (the game holds still meanwhile). */
    var askPerson by mutableStateOf<Bitmap?>(null); private set

    fun keepPerson(keep: Boolean) {
        val b = askPerson ?: return
        askPerson = null
        if (keep) sink.onCaught("person", b, null, "front")
    }

    fun setSize(wDp: Float, hDp: Float) {
        if (wDp == w && hDp == h) return
        w = wDp; h = hDp
        claw = Claw(w, h - bottomInset, rail) { Random.nextFloat() }
        reset()
    }

    fun step(dt: Float) {
        if (askPerson != null) return
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
        vsw += (-swing * 0.012f + acc * 0.0009f) * dt   // + : a positive swing tilts the claw left (canvas rotate), so it trails the move
        vsw *= 0.992f.pow(dt)
        swing = (swing + vsw * dt * 0.06f).coerceIn(-0.3f, 0.3f)
        pulse = max(0f, pulse - dt / 500f)
        toast?.let { it.t += dt; if (it.t > 1400f) toast = null }
        grip?.let { if (held == null && it.isDone) held = runCatching { it.get() }.getOrNull() }

        val before = c.st
        c.step(dt, targets) { x, y -> m?.toGrid(x * d, y * d) ?: (-1 to -1) }
        if (before == Claw.St.DOWN && c.st == Claw.St.GRIP) {
            onGrip(c.result!!)
            closedBuzz = false; dangle.reset()
            if (c.result?.target != null) { joltT = 0f; buzz(HapticFeedbackConstants.CLOCK_TICK) }
        }
        if (joltT >= 0f) { joltT += dt; if (joltT > 200f) joltT = -1f }
        if (c.st == Claw.St.GRIP && !closedBuzz && c.t > Claw.DIG_MS + Claw.CLOSE_MS) { closedBuzz = true; buzz(HapticFeedbackConstants.CONTEXT_CLICK) }
        feel(c, before, dt, m)
        if (landT >= 0f) { landT += dt; if (landT > 320f) { landT = -1f; landFrozen = null; landGrip = null } }
        when (c.st) {
            Claw.St.CARRY -> if (capsule == null) onCarry(c)
            Claw.St.DONE -> if (capsule == null) {
                val r = c.result!!
                if (before == Claw.St.FALL) landedAfterFall(r)
                say(when {
                    r.target == null -> "헛집었다"
                    r.slipped -> "놓쳤다! 흔들려서 빠졌어요"
                    else -> "아깝다! (${(r.p * 100).roundToInt()}%)"
                }, false)
                next()
            }
            else -> {}
        }
        capsule?.let { cap ->
            val was = cap.phase
            cap.step(dt); capT += dt
            if (was == Capsule.Phase.WRAP && cap.phase == Capsule.Phase.DROP) c.handedOff()   // claw lets go
            if (cap.phase == Capsule.Phase.GONE) {
                say(capText, true); pulse = 1f; next()
                pendingPerson?.let { askPerson = it; pendingPerson = null }
            }
        }
    }

    /** The held thing's pose and place this frame: contact squash, swing, struggle, slide, slow fall. */
    private fun feel(c: Claw, before: Claw.St, dt: Float, m: ScreenMap?) {
        val r = c.result
        val tg = r?.target
        if (tg == null || m == null || c.st !in LIFTED) { heldPose = Pose.REST; arms = ClawArt.idle(c.open); return }
        val critter = tg.kind == Mask.Kind.CRITTER
        val now = targets.firstOrNull { sameThing(tg, it) }?.box
        val thingX = now?.let { m.gridToViewX((it.x0 + it.x1 + 1) / 2f) / d }
        val clawX = c.x * w
        if (before == Claw.St.GRIP && c.st == Claw.St.UP) { dangle.reset(); grabOff = (thingX ?: clawX) - clawX; dangle.kick(-grabOff * 0.002f) }
        if (before == Claw.St.UP && c.st == Claw.St.CARRY) dangle.kick(if (dangle.rot >= 0f) 0.03f else -0.03f)   // the stop at the top
        val cap = if (critter) Dangle.CAP_CRITTER else Dangle.CAP_PHOTO
        // the arms: spread past the body's sides as the claw settles on it, then close in under its widest point
        if (c.st == Claw.St.GRIP) now?.let { hugTop = m.gridToViewY(it.y0.toFloat()) / d - c.y }
        if (c.bodyW > 0f) {
            val o = ((c.open - c.minOpen) / max(1e-3f, 1f - c.minOpen)).coerceIn(0f, 1f)
            val on = ClawArt.on(ClawArt.Body(c.bodyW / 2f, hugTop, c.bodyH), o)
            arms = if (c.st == Claw.St.GRIP && c.t < Claw.DIG_MS) ClawArt.mix(ClawArt.idle(1f), on, c.t / Claw.DIG_MS) else on
        } else arms = ClawArt.idle(c.open)
        when (c.st) {
            Claw.St.GRIP -> {
                // on the floor: squashed by the tips, then a wriggle once the prongs bite
                heldCx = thingX ?: clawX
                val k = (1f - c.t / 120f).coerceAtLeast(0f)
                val (sr, sdx, ssq) = if (critter && c.t > Claw.DIG_MS) dangle.also { it.step(dt, 0f, cap) }.struggle(0.5f) else Triple(0f, 0f, 0f)
                heldPose = if (critter) Pose(dx = sdx, rot = sr, sx = 1f + 0.06f * k - ssq * 0.5f, sy = 1f - 0.10f * k + ssq) else Pose.REST
            }
            Claw.St.UP, Claw.St.CARRY -> {
                dangle.step(dt, (thingX ?: clawX) - clawX, cap)
                val lifted = -c.carryDy
                val win = r.win
                val amp = if (win && c.slip01 == 0f) 1f - 0.6f * (lifted / max(1f, lifted + (c.y - c.restY))) else 1f + 0.6f * c.slip01
                val (sr, sdx, ssq) = if (critter) dangle.struggle(amp) else Triple(0f, 0f, 0f)
                val side = if (win && thingX != null) Math.signum(thingX - clawX) * 0.25f * c.bodyW * c.slip01 else 0f
                heldCx = clawX + grabOff * kotlin.math.exp(-c.t / 250f) + side
                val stretch = if (critter) 0.05f + 0.03f * (1f - c.t / 90f).coerceAtLeast(0f) else 0f
                heldPose = Pose(dx = sdx, dy = if (!win) 0.08f * c.slip01 else 0f, rot = sr * 0.7f,
                    sx = 1f - stretch * 0.8f - ssq * 0.5f, sy = 1f + stretch + ssq, pivot = 0.6f)
            }
            Claw.St.FALL -> {
                dangle.step(dt, 0f, cap)
                heldPose = Pose(rot = dangle.rot, sx = if (critter) 0.98f else 1f, sy = if (critter) 1.03f else 1f, pivot = 0.5f)
            }
            else -> {}
        }
    }

    /** A dropped thing reached the floor: a critter lands where it fell, a photo bounces once before it fades. */
    private fun landedAfterFall(r: Claw.Result) {
        val tg = r.target ?: return
        buzz(HapticFeedbackConstants.VIRTUAL_KEY)
        if (tg.kind == Mask.Kind.CRITTER) tg.critterId?.let { critters.landed(it, heldCx * d) }
        else if (held?.cut != null) { landT = 0f; landFrozen = frozen; landGrip = held }
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
            val id = target.critterId ?: ""
            val (label, pic) = map?.let { critters.caught(id, it) } ?: ("front" to null)
            held = Grip(pic, null, 1f)
            val grew = sink.onCaught("critter", null, id, label)
            startCapsule(c, "털 녀석을 뽑았다!", grew)
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
        val text = Dex.withObject(Dex.kindName(target.dexKind)) + " 뽑았다!"
        if (target.kind == Mask.Kind.PERSON) {
            // a person goes into the dex only after the consent dialog (shown when the capsule is gone)
            pendingPerson = got.cut.bmp
            startCapsule(c, grew = false, text = text)
            return
        }
        val grew = sink.onCaught(target.dexKind, got.cut.bmp, null, "front")
        startCapsule(c, grew = grew, text = text)
    }

    private fun startCapsule(c: Claw, text: String, grew: Boolean) {
        buzz(HapticFeedbackConstants.CONFIRM)
        saved = grew
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
        canvas = cv
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
        // a dropped photo bouncing once in its hole before the live view takes over
        val lf = landFrozen; val lg = landGrip
        if (lf != null && lg?.cut != null && m != null && c.st == Claw.St.AIM) {
            imageRect(m); cv.drawBitmap(lf, null, rect, bmpPaint)
            cutRect(m, lg.cut)
            val k = (landT / 320f).coerceIn(0f, 1f)
            rect.offset(0f, -0.08f * rect.height() * 4f * k * (1f - k))
            cv.drawBitmap(lg.cut.bmp, null, rect, bmpPaint)
        }
        val heldId = c.result?.target?.takeIf { it.kind == Mask.Kind.CRITTER && c.st in LIFTED }?.critterId
        if (m != null) {
            val wary = if (c.st == Claw.St.DOWN || c.st == Claw.St.GRIP) c.x * w * d else null
            cv.save(); cv.scale(1 / d, 1 / d)
            critters.draw(cv, m, heldId, wary)
            cv.restore()
        }
        // subtle glass
        fill.shader = LinearGradient(0f, 0f, w, h * 0.7f, intArrayOf(argb(.07f, 255, 255, 255), 0), floatArrayOf(0f, 0.4f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, fill); fill.shader = null

        val cx = c.x * w
        if (c.st == Claw.St.AIM) aimGuide(cv, c, cx, m)

        // the claw's back half (cable, hub, back prong), then the catch, then the front arms around it. Claw and
        // catch swing together from the rail (a crane's pendulum); a falling catch tumbles on its own.
        val sw = (if (c.st == Claw.St.AIM) swing else swing * 0.6f) + jolt()
        val hang = if (c.st == Claw.St.UP || c.st == Claw.St.CARRY) dangle.rot * 0.35f else 0f
        val deg = Math.toDegrees(sw.toDouble()).toFloat() + hang
        cv.save(); cv.rotate(deg, cx, rail)
        ClawArt.back(cv, cx, c.y, rail, arms)
        val hanging = c.st == Claw.St.UP || c.st == Claw.St.CARRY   // on the floor (GRIP) or falling it is not on the cable
        if (hanging) drawCatch(c, heldId, m)
        cv.restore()
        if (!hanging) drawCatch(c, heldId, m)
        capsule?.let { drawCapsule(cv, it, held?.cut, m) }
        cv.save(); cv.rotate(deg, cx, rail)
        ClawArt.front(cv, cx, c.y, arms)
        cv.restore()
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

    /** The catch on the claw: a critter (Critters draws it) or the cut-out photo. */
    private fun drawCatch(c: Claw, heldId: String?, m: ScreenMap?) {
        if (m == null || capsule != null) return
        val cv = canvas ?: return
        if (heldId != null) {
            cv.save(); cv.scale(1 / d, 1 / d)
            critters.drawHeld(cv, m, heldId, heldCx * d, c.carryDy * d, heldPose)
            cv.restore()
        }
        val cut = held?.cut
        if (cut != null && c.st in listOf(Claw.St.UP, Claw.St.FALL, Claw.St.CARRY)) {
            cutRect(m, cut)
            rect.offset(0f, c.carryDy)
            Posed.draw(cv, cut.bmp, rect, heldPose, bmpPaint)
        }
    }
    private var canvas: android.graphics.Canvas? = null

    /** The hub's shake for 120 ms after the tips touch (radians). */
    private fun jolt(): Float = if (joltT < 0f) 0f else
        Math.toRadians(4.0).toFloat() * sin(joltT / 1000f * 14f * 2f * Math.PI.toFloat()) * max(0f, 1f - joltT / 120f)

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
        cv.drawText("리얼뽑기", 16f, r - 22f, text)
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
        private val LIFTED = setOf(Claw.St.GRIP, Claw.St.UP, Claw.St.FALL, Claw.St.CARRY)
        private fun family(k: Mask.Kind) = if (k == Mask.Kind.PERSON || k == Mask.Kind.CRITTER) k else Mask.Kind.DOG
    }
}
