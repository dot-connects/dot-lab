package kr.dot.catchpet.ui

import kr.dot.catchpet.R

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import kr.dot.catchpet.ar.ArCritters
import kr.dot.catchpet.ar.Critters
import kr.dot.catchpet.camera.ArSource
import kr.dot.catchpet.camera.CameraXSource
import kr.dot.catchpet.camera.FrameSource
import kr.dot.catchpet.camera.SceneSource
import kr.dot.catchpet.dex.Dex
import kr.dot.catchpet.game.CapClose
import kr.dot.catchpet.game.Capsule
import kr.dot.catchpet.game.Claw
import kr.dot.catchpet.game.Ease
import kr.dot.catchpet.game.Mask
import kr.dot.catchpet.game.Pose
import kr.dot.catchpet.game.SlipShow
import kr.dot.catchpet.game.iou
import kr.dot.catchpet.game.pend
import kr.dot.catchpet.game.sameThing
import kr.dot.catchpet.seg.CutOut
import kr.dot.catchpet.seg.ScreenMap
import kr.dot.catchpet.seg.Segmenter
import java.util.concurrent.Future
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Where a catch goes (the Dex wires this). Called the moment the claw reaches the top (plan R6), before the capsule. */
interface CatchSink {
    /** png: the cut-out (real things) or null (critters). label: the pose. */
    fun onCaught(kind: String, png: Bitmap?, critterId: String?, label: String, cap: Int): Caught
}

/** [grew]: the dex count went up (a new one, not a critter met again); [name]: what it is called in the dex. */
data class Caught(val grew: Boolean, val name: String)

/** Virtual critters (ar/Critters): masks on the segmenter grid for the claw, drawn by the catch screen. */
interface CritterSource {
    fun masks(map: ScreenMap): List<Mask>
    /** Draw in view px, all but the critter on the claw ([held]); [wary] = the claw's x (view px) while it comes down. */
    fun draw(cv: android.graphics.Canvas, map: ScreenMap, held: String?, wary: Float?) {}
    /** The critter on the claw: centred at cx (view px), lifted dy px, posed (hanging, struggling, falling). */
    fun drawHeld(cv: android.graphics.Canvas, map: ScreenMap, held: String, cx: Float, dy: Float, pose: Pose) {}
    /** Its resting rect this frame (view px), null when it is not there. */
    fun rectOf(id: String): android.graphics.RectF? = null
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
        // camera on/off, like the AR switch in Pokémon GO (operator 11-13); drawn on the rail, top left
        val toggle = {
            when {
                cameraOff -> { cameraOff = false; prefs.edit().putBoolean("camera_off", false).apply(); if (!granted) ask.launch(Manifest.permission.CAMERA) }
                !granted -> ask.launch(Manifest.permission.CAMERA)
                else -> { cameraOff = true; prefs.edit().putBoolean("camera_off", true).apply() }
            }
        }
        if (!granted || cameraOff) SceneCatch(ctx, sink, dexCount, onOpenDex, toggle)
        else CameraCatch(ctx, sink, critters, dexCount, onOpenDex, toggle)
    }
}

@Composable
private fun SceneCatch(ctx: Context, sink: CatchSink, dexCount: Int, onOpenDex: () -> Unit, onCamera: () -> Unit) {
    val crit = remember { Critters(ctx.applicationContext, scene = true) }
    val src = remember { SceneSource() }
    key(src) { CatchView(ctx, src, crit, sink, dexCount, onOpenDex, onCamera, cameraOn = false) }
}

@Composable
private fun CameraCatch(ctx: Context, sink: CatchSink, critters: CritterSource, dexCount: Int, onOpenDex: () -> Unit, onCamera: () -> Unit) {
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
    key(src) { CatchView(ctx, src, crit, sink, dexCount, onOpenDex, onCamera, cameraOn = true) }
}

@Composable
private fun CatchView(
    ctx: Context, src: FrameSource, critters: CritterSource, sink: CatchSink, dexCount: Int, onOpenDex: () -> Unit,
    onCamera: () -> Unit, cameraOn: Boolean,
) {
    val seg = remember { Segmenter(ctx.applicationContext) }
    val game = remember { CatchGame(src, seg, sink, critters) }
    DisposableEffect(Unit) {
        seg.start(src::latest)
        critters.start()
        onDispose { Sfx.motor(0f); critters.stop(); seg.close(); src.close() }
    }
    // Home / screen off: stop the model loop and the sensors; back: start them again (review M3)
    val life = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(life) {
        var stopped = false
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> if (!stopped) { stopped = true; Sfx.motor(0f); seg.paused = true; critters.stop() }
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
    game.cameraOn = cameraOn
    remember {
        val p = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE)
        game.hint = !p.getBoolean("hinted", false)
        game.hinted = { p.edit().putBoolean("hinted", true).apply() }
    }
    remember { Sfx.init(ctx); FeltArt.load(ctx.resources) }
    val openDex by rememberUpdatedState(onOpenDex)
    val camera by rememberUpdatedState(onCamera)

    var tick by remember { mutableLongStateOf(0L) }
    var bragPerson by remember { mutableStateOf<Brag?>(null) }
    LaunchedEffect(Unit) {
        var prev = 0L
        while (true) withFrameNanos { now ->
            // heat: at most ~60 frames a second, also on 90/120 Hz screens
            if (prev != 0L && now - prev < (if (game.lively()) 15_000_000L else 32_000_000L)) return@withFrameNanos
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
                        if (y < game.rail) {          // the rail bar: "도감 N" on the right, the camera switch on the left
                            if (waitForUpOrCancellation() != null) {
                                if (x > game.w / 2) { if (game.idle) openDex() }
                                else if (x < game.camChip.right + 8f && game.idle) camera()   // not mid-round: a catch in flight would be lost
                            }
                            return@awaitEachGesture
                        }
                        val c = game.claw ?: return@awaitEachGesture
                        if (game.onButton(x, y)) {
                            game.pressed = true
                            val up = waitForUpOrCancellation()   // null = cancelled (a system swipe): no drop
                            game.pressed = false
                            if (up != null && game.onButton(up.position.x / game.d, up.position.y / game.d) && c.st == Claw.St.AIM) {
                                c.release(); if (game.hint) { game.hint = false; game.hinted() }
                            }
                            return@awaitEachGesture
                        }
                        if (c.st != Claw.St.AIM) return@awaitEachGesture
                        game.touching = true
                        c.aim(x / game.w)
                        try {
                            while (true) {
                                val ch = awaitPointerEvent().changes.first()
                                if (!ch.pressed) break
                                c.aim(ch.position.x / game.d / game.w)
                            }
                        } finally { game.touching = false }
                    }
                },
        ) {
            tick // redraw every frame
            drawIntoCanvas { game.render(it.nativeCanvas) }
        }
        if (game.askPerson != null) PersonConsent(PersonUse.DEX) { game.keepPerson(it) }
        // operator 11-16: the card stays on screen until [계속 뽑기] or [자랑하기]
        game.bragCard?.let { b ->
            BragCard(b, onClose = { game.bragCard = null }, onDex = { game.bragCard = null; openDex() }) {
                game.bragCard = null
                if (b.person) bragPerson = b else ShareCard.share(ctx, b)
            }
        }
        bragPerson?.let { b -> PersonConsent(PersonUse.SHARE) { ok -> bragPerson = null; if (ok) ShareCard.share(ctx, b) } }
    }
}

/**
 * One catch screen's game: the Claw state machine, the GRIP cut-out, the capsule, toasts, and the drawing. Everything is
 * in dp; the view is scaled by [d] when drawn.
 *
 * The look and motion are the felt mockup v5.2 (operator 11-28, mockups/next7v4 core.js / screens.js / world.js); the
 * rules stay the app's (Claw). A real thing is caught "잡자마자 작게": once the prongs shut its cut-out is sucked into
 * an open capsule under the claw and the lid snaps shut before the lift; a lost one shudders, pops open and the little
 * one hops back onto its body. A critter hangs from the tips and is wrapped in the capsule at the top; a lost one slides
 * and falls in the pose it hung in. Every position is computed from the claw's clock in [step], so the claw, the
 * capsule and the catch move together and nothing jumps a frame.
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
    var cameraOn = false
    var pressed = false

    // layout (felt mockup v5): the wooden rail, felt chips under it, the claw hanging from the rail, the button bottom right
    private val railVis get() = topInset + 12f
    private val chipY get() = topInset + 43f
    /** Taps above this line are the chips. */
    val rail get() = topInset + 62f
    /** The claw's rail for its rules: it rests with its picture just under the chips, like the mockup. */
    private val clawRail get() = rail + 26f
    private val btnL get() = w - 18f - FeltArt.BTN_W
    private val btnT get() = h - bottomInset - 46f - FeltArt.btnH
    private val btnX get() = btnL + FeltArt.BTN_W / 2
    private val btnY get() = btnT + FeltArt.btnH * 0.42f
    fun onButton(x: Float, y: Float) =
        x >= btnL - 8f && x <= btnL + FeltArt.BTN_W + 8f && y >= btnT - 8f && y <= btnT + FeltArt.btnH + 24f
    /** The camera chip (top left), set when drawn. */
    val camChip = RectF()
    /** Nothing in flight: aiming, no capsule, no person waiting for consent. */
    val idle get() = claw?.st == Claw.St.AIM && capsule == null && pendingPerson == null && askPerson == null
    var hint = true
    /** Remembers that the player has dropped the claw once (the first-run hint is gone for good). */
    var hinted: () -> Unit = {}
    private var railTickX = -1f
    var w = 0f; private set
    var h = 0f; private set
    var claw: Claw? = null; private set
    /** Haptic feedback (HapticFeedbackConstants), set by the view. */
    var buzz: (Int) -> Unit = {}

    private class Toast(val text: String, val good: Boolean) {
        var t = 0f
        val life get() = 1500f
    }
    /** What the next win toast offers to share, kept from the catch (the cut-out is gone after reset, review 11-16). */
    private var capBrag: Brag? = null
    /** The card shown over the game after a catch, until [계속 뽑기] or [자랑하기] (operator 11-16, mockup 2-A). */
    var bragCard by mutableStateOf<Brag?>(null)
    private class Grip(val cut: CutOut.Cut?, val opaque: Float, val kind: String? = null)

    private var map: ScreenMap? = null
    private var targets: List<Mask> = emptyList()
    private var time = 0f
    private var lastX = 0.5f
    private var swing = 0f
    private var vsw = 0f
    private var pulse = 0f
    private var toast: Toast? = null
    var touching = false

    /** Something on screen moves fast enough to need 60 fps (else 30: idle breathing and swaying read the same). */
    fun lively(): Boolean {
        val c = claw ?: return false
        return touching || c.st != Claw.St.AIM || capsule != null || faller != null || toast != null || pulse > 0f ||
            FeltArt.busy || abs(vsw) > 2e-4f || abs(swing) > 0.004f || abs(c.x - lastX) > 1e-4f ||
            abs(close) > 0.01f || abs(rigSwing) > 0.05f
    }
    private var frozen: Bitmap? = null
    private var grip: Future<Grip>? = null
    private var gripAt = 0f
    private var held: Grip? = null
    // a person's precise cut (face count + person model) can take seconds: the tracked mask's quick cut rides the
    // claw meanwhile, else the claw rose empty and the person stayed in the frame until the capsule (operator 11-17)
    private var quick: Future<Grip>? = null
    private var quickHeld: Grip? = null
    // the precise cut only when it is usable (Codex review 11-18: an empty one hid a good quick cut)
    private val shown get() = held?.takeIf { it.cut != null && it.opaque >= 0.05f } ?: quickHeld ?: held
    private var capsule: Capsule? = null
    private var capColor = CapColors.CAP_COLORS[0]
    private var capText = ""
    private var pendingPerson: Bitmap? = null
    private var pendingKind = "person"
    /** A caught person waiting for the player's "허락받았어요" (the game holds still meanwhile). */
    var askPerson by mutableStateOf<Bitmap?>(null); private set

    // ---------- the show (felt mockup), all dp / ms / degrees ----------
    private enum class Kind { NONE, AIR, REAL, CRITTER }
    private var kind = Kind.NONE
    private var close = 0f           // the prongs: 0 open .. 1 shut (1.32 snapped on air), eased every frame
    private var squash = 0f
    private var rigSwing = 0f        // the claw's pendulum on the rail
    private var rigDeg = 0f          // everything that turns the claw this frame
    private var tipX = 0f            // the prong tips this frame (turned with the claw)
    private var tipY = 0f
    private var swingT = 0f          // ms since the lift started: the catch's pendulum clock
    private var toasted = false      // this round's toast is said
    private var closedBuzz = false
    // a real catch: its cut-out comes out of the photo and into the capsule
    private var catchT = -1f         // ms since it started coming out (after the prongs shut), -1 = not yet
    private var waitCut = 0f
    private val heldFrom = RectF()   // where the cut-out was (dp)
    private var capClose: CapClose? = null
    private var capT = 0f            // ms since the capsule appeared
    private var flashT = -1f
    private var flashX = 0f
    private var flashY = 0f
    private var topT = -1f           // ms waiting at the top before the prongs let go
    private var released = false
    private var popped = false
    private val popAt = PointF()
    private val landAt = PointF()
    private var popRot = 0f
    // a critter: hangs by its top from the tips, gets wrapped at the top, or falls
    private val crit = RectF()       // its resting rect (dp) this frame
    private var critPic: Bitmap? = null
    private var critGone = false     // caught() took it out of the world
    private var critDropped = false
    private class Faller(val id: String, val x: Float, var top: Float, val grip: Float) { var vy = 0f; var a = 0f }
    private var faller: Faller? = null
    private val look = FeltArt.CapLook()
    // "○○ 찾았다!" over each real thing the camera sees (mockup: always shown, hidden while catching)
    private class Label(var x: Float, var y: Float, var text: String) { var a = 0f; var seen = true }
    private val labels = HashMap<String, Label>()

    fun keepPerson(keep: Boolean) {
        val b = askPerson ?: return
        askPerson = null
        if (!keep) return
        val r = sink.onCaught(pendingKind, b, null, "front", capColor)
        say(caughtText(pendingKind, r.name), true); bragCard = Brag(r.name, pendingKind, b, null, capColor); pulse = 1f; Sfx.play(R.raw.sfx_win, 0.6f)
    }

    /** "강아지 도토리를 뽑았다!": what it is and the name it got (operator 11-15). */
    private fun caughtText(kind: String, name: String) = Dex.withObject("${Dex.kindName(kind)} $name") + " 뽑았다!"

    fun setSize(wDp: Float, hDp: Float) {
        if (wDp == w && hDp == h) return
        w = wDp; h = hDp
        claw = Claw(w, h - bottomInset, clawRail) { Random.nextFloat() }
        reset()
    }

    private val floorY get() = h - bottomInset - 24f

    fun step(dt: Float) {
        if (askPerson != null) return
        var c = claw ?: return
        if (c.railY != clawRail && c.st == Claw.St.AIM) {   // insets arrived after the first layout
            c = Claw(w, h - bottomInset, clawRail) { Random.nextFloat() }; claw = c
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
        toast?.let { it.t += dt; if (it.t > it.life) toast = null }
        grip?.let { if (held == null && it.isDone) held = runCatching { it.get() }.getOrNull() }
        quick?.let { if (quickHeld == null && it.isDone) quickHeld = runCatching { it.get() }.getOrNull() }

        // the carriage ticks along the rail as it moves (aiming and riding home)
        val px = c.x * w
        if (c.st == Claw.St.AIM) { if (railTickX < 0f || abs(px - railTickX) > 22f) { if (railTickX >= 0f) Sfx.play(R.raw.sfx_rail, 0.25f); railTickX = px } }
        val before = c.st
        c.step(dt, targets) { x, y -> m?.toGrid(x * d, y * d) ?: (-1 to -1) }
        Sfx.motor(when { c.st == Claw.St.DOWN -> 0.85f; c.st == Claw.St.UP && !c.hold -> 1.15f; c.st == Claw.St.FALL && c.t > c.fallHold -> 1.15f; else -> 0f })
        if (before == Claw.St.AIM && c.st == Claw.St.DOWN) Sfx.play(R.raw.sfx_drop, 0.5f)
        if (before == Claw.St.DOWN && c.st == Claw.St.GRIP) {
            val r = c.result!!
            kind = when { r.target == null -> Kind.AIR; r.target.kind == Mask.Kind.CRITTER -> Kind.CRITTER; else -> Kind.REAL }
            onGrip(r)
            closedBuzz = false; toasted = false; catchT = -1f; waitCut = 0f; capClose = null; topT = -1f
            released = false; popped = false; critPic = null; critGone = false; critDropped = false; swingT = 0f
            if (kind == Kind.REAL) c.hold = true   // the capsule closes round it before the lift
            if (r.target != null) buzz(HapticFeedbackConstants.CLOCK_TICK)
        }
        if (c.st == Claw.St.GRIP && !closedBuzz && c.t > Claw.SETTLE_MS + Claw.CLOSE_MS) {
            closedBuzz = true; buzz(HapticFeedbackConstants.CONTEXT_CLICK); Sfx.play(R.raw.sfx_grip, 0.6f)
        }
        if (before == Claw.St.GRIP && c.st == Claw.St.UP) {
            Sfx.play(R.raw.sfx_lift, 0.45f)
            if (kind == Kind.AIR) { say(AIR.random(), false); toasted = true }
        }
        if (before != Claw.St.FALL && c.st == Claw.St.FALL) onFall(c)
        if ((c.st == Claw.St.UP && !c.hold) || c.st == Claw.St.CARRY || c.st == Claw.St.FALL) swingT += dt

        show(c, dt)
        when (kind) {
            Kind.REAL -> real(c, dt)
            Kind.CRITTER -> critter(c, dt)
            else -> {}
        }
        if (c.st == Claw.St.DONE && capsule == null) {
            val r = c.result!!
            if (!toasted) say(when { r.target == null -> AIR.random(); r.slipped -> SLIPPED.random(); else -> NEAR.random() }, false)
            if (before != Claw.St.FALL) Sfx.play(R.raw.sfx_miss, 0.4f)
            next()
        }
        capsule?.let { cap ->
            if (cap.phase == Capsule.Phase.HANG) return@let
            val b = cap.bounces
            cap.step(dt)
            if (cap.bounces > b && cap.bounces == 1) Sfx.play(R.raw.sfx_land, 0.4f)
            if (cap.phase == Capsule.Phase.GONE) {
                if (pendingPerson == null) { bragCard = capBrag; Sfx.play(R.raw.sfx_win, 0.6f) }
                capBrag = null
                next()
                pendingPerson?.let { askPerson = it; pendingPerson = null }
            }
        }
        faller?.let { fallStep(it, dt) }
        FeltArt.stepBits(dt)
        if (flashT >= 0f) { flashT += dt; if (flashT > 360f) flashT = -1f }
        stepLabels(c, dt, m)
    }

    /** The claw's prongs, squash and swing for this frame, then where its tips are (everything hangs from them). */
    private fun show(c: Claw, dt: Float) {
        val k = when (kind) { Kind.AIR -> 1.32f; Kind.CRITTER -> 0.72f; else -> 1f }
        var cl = 0f; var sq = 0f; var sw = 0f
        val shut = Claw.SETTLE_MS + Claw.CLOSE_MS
        when (c.st) {
            Claw.St.AIM, Claw.St.DONE -> {}
            Claw.St.DOWN -> sw = -0.8f * sin(PI.toFloat() * c.downK)
            Claw.St.GRIP -> {
                cl = (1f - c.open) * k
                val tc = c.t - shut
                if (kind == Kind.AIR) { if (tc in 0f..200f) { val q = tc / 200f; sw = sin(q * 30f) * 2.2f * (1f - q); sq = 0.05f * sin(PI.toFloat() * q) } }
                else if (c.t >= Claw.SETTLE_MS && tc < 140f) sq = 0.04f
            }
            Claw.St.UP -> {
                cl = k
                if (kind == Kind.AIR) { sw = pend(2.5f, c.t, 0.013f, 300f); if (c.t > c.riseMs - 160f) cl = 0f }
                else sw = pend(1.2f, swingT, 0.012f, 500f)
            }
            Claw.St.CARRY -> { cl = if (released) 0f else k; sw = 0.6f * pend(1.2f, swingT, 0.012f, 500f) }
            Claw.St.FALL -> {
                val t = c.t
                if (kind == Kind.REAL && capsule != null) {
                    cl = when { t < SlipShow.SHUDDER -> 1f; t < SlipShow.POP -> 1f - 0.5f * Ease.outQ(((t - SlipShow.SHUDDER) / 140f).coerceAtMost(1f)); else -> 0.5f }
                    if (t < SlipShow.SHUDDER) sw = SlipShow.shudder(t) * 0.12f
                } else cl = if (t < CRIT_SLIDE) 0.72f - 0.37f * Ease.inQ(t / CRIT_SLIDE) else 0.35f
                if (t > c.fallHold) { sw = pend(2.5f, t - c.fallHold, 0.013f, 300f); if (t > c.fallHold + c.riseMs - 160f) cl = 0f }
            }
        }
        val e = 1f - exp(-dt / 35f)
        close += (cl - close) * e; squash += (sq - squash) * e; rigSwing += (sw - rigSwing) * e
        rigDeg = Math.toDegrees((if (c.st == Claw.St.AIM) swing else swing * 0.6f).toDouble()).toFloat() + rigSwing
        val a = Math.toRadians(rigDeg.toDouble()); val l = c.y + Claw.TIP - railVis
        tipX = c.x * w - sin(a).toFloat() * l
        tipY = railVis + cos(a).toFloat() * l
    }

    /** The capsule's angle while it hangs from the tips. */
    private fun capAngle(c: Claw): Float = when {
        kind != Kind.REAL -> 0f
        c.st == Claw.St.UP -> pend(7f, swingT, 0.012f, 500f)
        c.st == Claw.St.CARRY -> pend(7f, swingT, 0.012f, 500f) + if (topT >= 0f) pend(-6f, topT, 0.016f, 260f) else 0f
        c.st == Claw.St.FALL -> pend(7f, swingT, 0.012f, 500f) + SlipShow.shudder(c.t)
        else -> 0f
    }

    private fun hangCapsule(c: Claw) {
        val cap = capsule ?: return
        val a = capAngle(c)
        val l = HANG + if (c.st == Claw.St.FALL && kind == Kind.REAL) SlipShow.slide(c.t) else 0f
        val r = Math.toRadians(a.toDouble())
        cap.hang(tipX - sin(r).toFloat() * l, tipY + cos(r).toFloat() * l, a)
    }

    /** A real thing: out of the photo into the capsule, the lift, the top, or the slip. */
    private fun real(c: Claw, dt: Float) {
        if (catchT < 0f && (c.st == Claw.St.UP || (c.st == Claw.St.GRIP && c.t >= Claw.SETTLE_MS + Claw.CLOSE_MS))) {
            // the cut-out is usually ready by now (quick cut); wait a little for it, then go on without
            waitCut += dt
            val cut = shown?.cut
            if (cut != null || waitCut > 600f) {
                catchT = 0f
                val m = map
                if (cut != null && m != null) cutRect(m, cut, heldFrom)
                else { val tg = c.result?.target?.box; if (tg != null && m != null) heldFrom.set(m.gridToViewX(tg.x0.toFloat()) / d, m.gridToViewY(tg.y0.toFloat()) / d, m.gridToViewX(tg.x1 + 1f) / d, m.gridToViewY(tg.y1 + 1f) / d) else heldFrom.set(tipX - 40f, tipY, tipX + 40f, tipY + 80f) }
                flashT = 0f; flashX = heldFrom.centerX(); flashY = heldFrom.centerY()
            }
        } else if (catchT >= 0f) catchT += dt
        if (catchT >= ANTICIPATE && capsule == null && !released && (c.st == Claw.St.GRIP || c.st == Claw.St.UP)) {
            capsule = Capsule(tipX, tipY + HANG, floorY, w); capClose = CapClose(300f)
            capColor = CapColors.random()   // a different capsule each catch (operator 11-15), stored with it
        }
        val cc = capClose
        if (capsule != null && cc != null) {
            capT = catchT - ANTICIPATE
            if (c.hold && capT >= cc.end + 70f) c.hold = false
        }
        if (c.st == Claw.St.CARRY && !released) {
            if (topT < 0f) topT = 0f else topT += dt
            if (topT >= 260f) carryReal(c)
        }
        if (c.st == Claw.St.FALL && capsule != null) {
            if (!popped && c.t >= SlipShow.POP) {
                popped = true
                val cap = capsule!!
                popAt.set(cap.x, cap.y); popRot = cap.rot
                say(if (c.result?.slipped == true) SLIPPED.random() else NEAR.random(), false); toasted = true
                FeltArt.puff(cap.x, cap.y, 10, 40f); FeltArt.sparkle(cap.x, cap.y - 20f, 3)
                Sfx.play(R.raw.sfx_land, 0.5f)
                // it hops back onto its body: where the camera sees it now, else where it was
                val tg = c.result?.target
                val now = tg?.let { t -> targets.firstOrNull { it.kind != Mask.Kind.CRITTER && sameThing(t, it) } }?.box
                val m = map
                if (now != null && m != null) landAt.set(m.gridToViewX((now.x0 + now.x1 + 1) / 2f) / d, m.gridToViewY((now.y0 + now.y1 + 1) / 2f) / d - 20f)
                else landAt.set(heldFrom.centerX(), heldFrom.centerY() - 20f)
            }
            if (c.t >= SlipShow.END) { FeltArt.puff(landAt.x, landAt.y + 30f, 7, 30f); capsule = null }
        }
        if (!popped) hangCapsule(c)
    }

    /** At the top with a real win: wait for the precise cut (max 1.5 s, then the quick one), save, let go. */
    private fun carryReal(c: Claw) {
        val target = c.result!!.target!!
        val g = grip
        if (held == null && g != null && !g.isDone && time - gripAt < 1500f) return
        if (held == null) {
            g?.cancel(false)
            // main thread: no model call here
            held = quickHeld ?: frozen?.let { f -> runCatching { makeGrip(f, target, hiRes = false) }.onFailure { Log.w(TAG, "cut failed", it) }.getOrNull() }
        }
        val got = shown
        if (got?.cut == null || got.opaque < 0.05f) {
            capsule = null; c.handedOff(); say(NEAR.random(), false); toasted = true; next()
            return
        }
        val kind = got.kind ?: target.dexKind   // a group: "person+cat"
        if (Dex.hasPerson(kind)) {
            // a person goes into the dex only after the consent dialog (shown when the capsule is gone); the
            // cheer waits for it too (review 11-15: it celebrated, then "버리기")
            pendingPerson = got.cut.bmp; pendingKind = kind
            letGo(c, "")
            return
        }
        val r = sink.onCaught(kind, got.cut.bmp, null, "front", capColor)
        capBrag = Brag(r.name, kind, got.cut.bmp, null, capColor)
        letGo(c, caughtText(kind, r.name))
    }

    /** The prongs let go: the capsule drops, bounces and rolls off; the toast says what it was. */
    private fun letGo(c: Claw, text: String) {
        released = true
        capsule?.drop()
        c.handedOff()
        buzz(HapticFeedbackConstants.CONFIRM)
        if (text.isNotEmpty()) { say(text, true); toasted = true; pulse = 1f }
    }

    /** A critter: hanging from the tips, wrapped at the top, or sliding out and falling. */
    private fun critter(c: Claw, dt: Float) {
        val id = c.result?.target?.critterId ?: return
        val m = map
        if (!critGone) critters.rectOf(id)?.let { r -> crit.set(r.left / d, r.top / d, r.right / d, r.bottom / d) }
        if (c.st == Claw.St.CARRY && capsule == null && !released && m != null) {
            // at the top: it goes into the capsule (core.js WRAP at the top)
            val (label, pic) = critters.caught(id, m)
            critGone = true; critPic = pic?.bmp
            capColor = CapColors.random()
            val r = sink.onCaught("critter", null, id, label, capColor)
            capBrag = Brag(r.name, "critter", null, id, capColor)   // its picture is read when shared
            capText = if (r.grew) "새 친구! ${r.name}" else "${r.name} 또 왔네!"
            capsule = Capsule(tipX, tipY + HANG, floorY, w); capClose = CapClose(260f); capT = 0f
        } else if (c.st == Claw.St.CARRY && !released) {
            capT += dt
            if (capT >= (capClose?.end ?: 0f) + 80f) letGo(c, capText)
        }
        if (c.st == Claw.St.FALL && !critDropped && c.t >= CRIT_SLIDE) {
            critDropped = true
            say(if (c.result?.slipped == true) SLIPPED.random() else NEAR.random(), false); toasted = true
            val g = gripDepth()
            faller = Faller(id, tipX, tipY - g - 6f + 22f, g)
        }
        if (capsule?.phase == Capsule.Phase.HANG) hangCapsule(c)
    }

    private fun gripDepth() = min(22f, crit.height() * 0.25f)

    private fun fallStep(f: Faller, dt: Float) {
        // it falls in the pose it hung in, tipping a little; the world takes it back where it lands (world.js)
        critters.rectOf(f.id)?.let { r -> crit.set(r.left / d, r.top / d, r.right / d, r.bottom / d) }
        f.vy += dt * Capsule.G
        f.top = min(crit.top, f.top + f.vy * dt)
        f.a = min(14f, f.a + dt * 0.03f)
        if (f.top >= crit.top) {
            faller = null
            critters.landed(f.id, f.x * d)
            FeltArt.puff(f.x, crit.bottom - 4f, 6, 28f)
            buzz(HapticFeedbackConstants.VIRTUAL_KEY); Sfx.play(R.raw.sfx_land, 0.6f)
        }
    }

    private fun onFall(c: Claw) {
        when {
            kind == Kind.REAL && capsule != null -> c.fallHold = SlipShow.END
            kind == Kind.CRITTER -> c.fallHold = CRIT_SLIDE
            else -> c.fallHold = 0f
        }
    }

    /** "○○ 찾았다!" labels follow the real things the camera sees; they fade out while the claw is busy. */
    private fun stepLabels(c: Claw, dt: Float, m: ScreenMap?) {
        for (l in labels.values) l.seen = false
        val on = c.st == Claw.St.AIM && capsule == null && m != null
        if (m != null) for ((i, t) in seg.masks.withIndex()) {
            if (t.kind == Mask.Kind.CRITTER) continue
            val b = t.box ?: continue
            val x = m.gridToViewX((b.x0 + b.x1 + 1) / 2f) / d
            val y = m.gridToViewY(b.y0.toFloat()) / d - 22f
            val key = "${t.kind}$i"
            val l = labels.getOrPut(key) { Label(x, y, "") }
            l.text = Dex.kindName(t.dexKind) + " 찾았다!"
            val k = 1f - exp(-dt / 120f)
            l.x += (x - l.x) * k; l.y += (max(rail + 20f, y) - l.y) * k; l.seen = true
        }
        val fade = 1f - exp(-dt / 70f)
        labels.values.removeAll { l -> l.a += ((if (on && l.seen) 1f else 0f) - l.a) * fade; !l.seen && l.a < 0.02f }
    }

    /** The tips touched something: freeze that frame and re-segment it on the model thread (plan R7, R16). */
    private fun onGrip(r: Claw.Result) {
        val target = r.target ?: return
        if (target.kind == Mask.Kind.CRITTER) return          // critters: no re-segment, no cut-out (R20)
        val f = src.latest() ?: return
        frozen = f
        gripAt = time
        quick = java.util.concurrent.ForkJoinPool.commonPool().submit(java.util.concurrent.Callable { makeGrip(f, target, hiRes = false) })
        // the claw's point in the mask grid: only the piece it touched is cut (review 11-15: jeans-only, two people)
        val (gx, gy) = map?.toGrid((claw?.x ?: 0.5f) * w * d, (claw?.y ?: rail) * d + Claw.TIP * d) ?: (target.w / 2 to target.h / 2)
        grip = seg.onThread {
            // the touched person already passed the face check; the frozen frame is not checked again (it dropped
            // the fresh mask and the cut fell back to a mask up to 1.2 s old)
            val all = seg.run(f, faces = false)
            val fresh = all.filter { family(it.kind) == family(target.kind) }.maxByOrNull { iou(it, target) }
            val m = (fresh?.takeIf { iou(it, target) > 0f } ?: target).pieceNear(gx, gy)
            val g = group(f, m, all)
            if (g == null) makeGrip(f, m, clawCell = gx to gy) else makeGrip(f, g.first, hiRes = g.second.split('+').all { it == "person" }, kind = g.second)
        }
    }

    /**
     * Touching people and animals are caught together, up to 3 (operator 11-15): the grabbed piece plus every piece of
     * the other masks that touches it; people in one piece are counted by their faces. null = one alone or more than 3.
     * Segmenter thread only.
     */
    private fun group(f: Bitmap, m: Mask, all: List<Mask>): Pair<Mask, String>? {
        var u = m
        val kinds = mutableListOf<String>()
        fun count(p: Mask) = if (p.kind == Mask.Kind.PERSON) List(seg.faces(f, p).coerceAtLeast(1)) { "person" } else listOf(p.dexKind)
        kinds += count(m)
        for (o in all) for (p in o.pieces()) {
            // review 11-15: touching the grabbed piece itself (not a chain), and not the grabbed piece again
            if (p.area < 6 || !p.touches(m) || (o.kind == m.kind && iouCells(p, m) > 0.5f)) continue
            u = u.plus(p); kinds += count(p)
            if (kinds.size > MAX_GROUP) return null
        }
        return if (kinds.size in 2..MAX_GROUP) u to kinds.joinToString("+") else null
    }
    private fun iouCells(a: Mask, b: Mask): Float {
        var i = 0; var n = 0
        for (k in a.bits.indices) { if (a.bits[k] && b.bits[k]) i++; if (a.bits[k] || b.bits[k]) n++ }
        return if (n == 0) 0f else i.toFloat() / n
    }

    private fun makeGrip(f: Bitmap, m: Mask, hiRes: Boolean = true, kind: String? = null, clawCell: Pair<Int, Int>? = null): Grip {
        val hi = if (hiRes && m.kind == Mask.Kind.PERSON) CutOut.srcOf(f, m, 0.15f)?.let { src ->
            runCatching { seg.personAlpha(f, src) }.onFailure { Log.w(TAG, "hi-res cut", it) }.getOrNull()?.let { a -> CutOut.make(f, src, a, clip = m).also { a.recycle() } }
        } else if (hiRes && kind == null) CutOut.srcOf(f, m, 0.15f)?.let { src ->
            // one animal (research/seg-quality-2026-11-27.md): magic touch + crop DeepLab, no grid clip (the box clips)
            val inner = CutOut.srcOf(f, m, 0f) ?: src
            val tip = clawCell?.let { (x, y) -> android.graphics.PointF((x + 0.5f) * f.width / m.w, (y + 0.5f) * f.height / m.h) }
            runCatching { seg.animalAlpha(f, src, inner, tip) }.onFailure { Log.w(TAG, "animal cut", it) }.getOrNull()?.let { a -> CutOut.make(f, src, a).also { a.recycle() } }
        } else null
        val cut = hi ?: CutOut.make(f, m)
        return Grip(cut, cut?.let { CutOut.opaque(it.bmp) } ?: 0f, kind)
    }

    private fun next() {
        claw?.next()
        reset()
    }

    private fun reset() {
        frozen = null; grip = null; held = null; capsule = null; capClose = null; catchT = -1f; kind = Kind.NONE
        quick?.cancel(false); quick = null; quickHeld = null
    }

    private fun say(text: String, good: Boolean) { toast = Toast(text, good) }

    // ---------- drawing (dp) ----------
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val text get() = FeltArt.text
    private val rect = RectF()

    fun render(cv: android.graphics.Canvas) {
        val c = claw ?: return
        cv.save()
        cv.scale(d, d)
        val m = map
        val f = faller
        val heldId = f?.id ?: c.result?.target?.takeIf {
            kind == Kind.CRITTER && !critGone && !critDropped && c.st in LIFTED
        }?.critterId
        if (m != null) {
            val wary = if (c.st == Claw.St.DOWN || c.st == Claw.St.GRIP) c.x * w * d else null
            cv.save(); cv.scale(1 / d, 1 / d)
            critters.draw(cv, m, heldId, wary)
            cv.restore()
        }
        FeltArt.rail(cv, w, railVis)
        // the claw's back (string and all three prongs), the catch, the capsule, then the middle prong over them
        val cx = c.x * w
        val top = c.y + Claw.TIP - FeltArt.CLAW_TIP
        cv.save(); cv.rotate(rigDeg, cx, railVis)
        FeltArt.string(cv, cx, railVis, top + 6f)
        FeltArt.claw(cv, cx, top, close, squash, front = false)
        cv.restore()
        drawCritter(cv, c, m)
        drawReal(cv, c)
        drawFlash(cv)
        drawLabels(cv)
        capsule?.let { drawCapsule(cv, c, it) }
        cv.save(); cv.rotate(rigDeg, cx, railVis)
        FeltArt.claw(cv, cx, top, close, squash, front = true)
        cv.restore()
        FeltArt.drawBits(cv)
        topBar(cv)
        FeltArt.button(cv, btnL, btnT, ready = c.st == Claw.St.AIM && capsule == null, pressed = pressed)
        if (hint) {
            // a slow ring around the button points at it, and the felt hint bottom left (mockup k-hint)
            val k = (time % 1400f) / 1400f
            stroke.pathEffect = null; stroke.strokeWidth = 3f; stroke.color = argb(.8f * (1f - k), 255, 216, 77)
            cv.drawCircle(btnX, btnY, 34f + 22f * k, stroke)
            text.textSize = 14f
            val tw = text.measureText(HINT) + 28f
            rect.set(16f, h - bottomInset - 44f - 32f, 16f + tw, h - bottomInset - 44f)
            FeltArt.pill(cv, rect, 0xE6FFFAF0.toInt())
            text.color = FeltArt.INK; cv.drawText(HINT, rect.centerX(), rect.bottom - 11f, text)
        }
        drawToast(cv)
        cv.restore()
    }

    private fun cutRect(m: ScreenMap, cut: CutOut.Cut, out: RectF) {
        out.set(
            m.imageToViewX(cut.src.left.toFloat()) / d, m.imageToViewY(cut.src.top.toFloat()) / d,
            m.imageToViewX(cut.src.right.toFloat()) / d, m.imageToViewY(cut.src.bottom.toFloat()) / d,
        )
    }

    /** A picture centred at (x, y), w x h, turned [rot] and faded to [a]. */
    private fun pic(cv: android.graphics.Canvas, b: Bitmap, x: Float, y: Float, pw: Float, ph: Float, rot: Float, a: Float) {
        cv.save(); cv.translate(x, y); cv.rotate(rot)
        rect.set(-pw / 2, -ph / 2, pw / 2, ph / 2)
        bmpPaint.alpha = (255 * a).toInt().coerceIn(0, 255)
        cv.drawBitmap(b, null, rect, bmpPaint)
        bmpPaint.alpha = 255
        cv.restore()
    }

    /** The real catch's cut-out: lifting out of the photo and sucked into the capsule, or hopping back after a pop. */
    private fun drawReal(cv: android.graphics.Canvas, c: Claw) {
        if (kind != Kind.REAL || catchT < 0f) return
        val b = shown?.cut?.bmp ?: return
        val bw = heldFrom.width(); val bh = heldFrom.height()
        val mini = FeltArt.contentScale(bw, bh)
        val cap = capsule
        val cc = capClose
        if (popped) {
            val r = ((c.t - SlipShow.POP) / SlipShow.HOP)
            if (r >= 1f) return
            val (x, y, s, a) = SlipShow.hop(r.coerceAtLeast(0f), popAt.x, popAt.y, mini, landAt.x, landAt.y, 0.36f)
            pic(cv, b, x, y, bw * s, bh * s, -10f * (1f - Ease.io(r.coerceAtLeast(0f))), a)
            return
        }
        if (cap != null && cc != null && capT >= cc.fillAt + cc.fillMs) return   // inside the capsule now
        val bx = heldFrom.centerX(); val by = heldFrom.centerY()
        if (cap == null || cc == null || capT < cc.fillAt) {
            val e = Ease.outQ((catchT / ANTICIPATE).coerceAtMost(1f))
            pic(cv, b, bx, by - 6f * e, bw * (1f + 0.05f * e), bh * (1f + 0.05f * e), 0f, 1f)
        } else {
            val r = cc.fill(capT); val st = sin(PI.toFloat() * r); val t = Ease.inC(r)
            val s = 1.05f + (mini - 1.05f) * t
            pic(cv, b, bx + (cap.x - bx) * t, (by - 6f) + (cap.y + 1.8f - by + 6f) * t - st * 18f,
                bw * s * (1f - 0.14f * st), bh * s * (1f + 0.2f * st), -10f * t, 1f)
        }
    }

    /** A critter hanging by its top from the tips (or falling in that pose), or shrinking into the capsule. */
    private fun drawCritter(cv: android.graphics.Canvas, c: Claw, m: ScreenMap?) {
        if (m == null || kind != Kind.CRITTER && faller == null) return
        val g = gripDepth()
        val f = faller
        if (f != null) { held(cv, m, f.id, f.x, f.top, f.a, g); return }
        val id = c.result?.target?.critterId ?: return
        if (critDropped) return
        if (critGone) {
            // into the capsule at the top (world.js: shrinks to the capsule's mouth while the lid waits)
            val b = critPic ?: return
            val cap = capsule ?: return
            val cc = capClose ?: return
            if (capT >= cc.fillAt + cc.fillMs) return
            val r = cc.fill(capT); val t = Ease.inC(r)
            val s = 1f + (FeltArt.contentScale(crit.width(), crit.height()) - 1f) * t
            val y0 = tipY - g - 6f + crit.height() / 2
            pic(cv, b, tipX + (cap.x - tipX) * t, y0 + (cap.y + 1.8f - y0) * t, crit.width() * s, crit.height() * s, 0f, 1f)
            return
        }
        val hangTop = tipY - g - 6f
        when (c.st) {
            Claw.St.GRIP -> {
                // from where it stands to hanging from the tips as the prongs shut, then a little tug
                val b = Ease.outC(((c.t - Claw.SETTLE_MS) / Claw.CLOSE_MS).coerceIn(0f, 1f))
                val tug = Ease.outQ(((c.t - Claw.SETTLE_MS - Claw.CLOSE_MS) / 120f).coerceIn(0f, 1f))
                val top = crit.top + (tipY - g - 6f * tug - crit.top) * b
                held(cv, m, id, crit.centerX() + (tipX - crit.centerX()) * b, top, 0f, g)
            }
            Claw.St.UP, Claw.St.CARRY -> held(cv, m, id, tipX, hangTop, pend(8f, swingT, 0.012f, 480f), g)
            Claw.St.FALL -> {
                val k = Ease.inQ((c.t / CRIT_SLIDE).coerceIn(0f, 1f))
                held(cv, m, id, tipX, hangTop + 22f * k, pend(8f, swingT, 0.012f, 480f) * (1f - k), g)
            }
            else -> {}
        }
    }

    private fun held(cv: android.graphics.Canvas, m: ScreenMap, id: String, x: Float, top: Float, rot: Float, g: Float) {
        if (crit.height() <= 0f) return
        cv.save(); cv.scale(1 / d, 1 / d)
        critters.drawHeld(cv, m, id, x * d, (top - crit.top) * d, Pose(rot = rot, pivot = g / crit.height()))
        cv.restore()
    }

    private fun drawCapsule(cv: android.graphics.Canvas, c: Claw, cap: Capsule) {
        val k = look
        val cc = capClose
        k.x = cap.x; k.y = cap.y; k.rot = cap.rot; k.color = capColor
        k.fromBottom = cap.phase != Capsule.Phase.HANG
        k.sx = cap.sx; k.sy = cap.sy
        k.floor = if (cap.phase == Capsule.Phase.HANG) Float.NaN else cap.floorY
        k.content = if (kind == Kind.CRITTER) critPic else shown?.cut?.bmp
        k.topDx = 0f; k.topDy = 0f; k.topRot = 0f; k.topA = 1f; k.botDx = 0f; k.botDy = 0f; k.botRot = 0f; k.botA = 1f
        if (cc != null && cap.phase == Capsule.Phase.HANG && !popped) {
            val (s, dy, a) = cc.pop(capT)
            k.popS = s; k.popDy = dy; k.popA = a
            k.lid = cc.lid(capT)
            val q = cc.squash(capT); k.sx = q; k.sy = 2f - q
            k.showContent = capT >= cc.fillAt + cc.fillMs
            if (c.st == Claw.St.FALL && c.t < SlipShow.SHUDDER) { val q2 = 1f + 0.03f * sin(c.t / SlipShow.SHUDDER * 40f); k.sx = q2; k.sy = 2f - q2 }
            if (capT >= cc.lidEnd && capT - cc.lidEnd < 30f && !lidPuffed) { lidPuffed = true; FeltArt.puff(cap.x, cap.y - 8f, 7, 34f); FeltArt.sparkle(cap.x, cap.y - 30f, 4) }
        } else { k.popS = 1f; k.popDy = 0f; k.popA = 1f; k.lid = 0f; k.showContent = true }
        if (cc != null && capT < cc.lidEnd) lidPuffed = false
        if (popped) {
            // it popped open: the lid flies up and away, the cup drops, nothing inside
            val t = c.t - SlipShow.POP
            val e1 = Ease.outQ((t / 520f).coerceIn(0f, 1f)); val e2 = Ease.inQ((t / 560f).coerceIn(0f, 1f))
            k.x = popAt.x; k.y = popAt.y; k.rot = popRot; k.showContent = false
            k.topDx = 34f * e1; k.topDy = -70f * e1; k.topRot = 150f * e1; k.topA = 1f - e1
            k.botDx = -14f * e2; k.botDy = 140f * e2; k.botRot = -40f * e2; k.botA = 1f - e2
        }
        FeltArt.capsule(cv, k)
    }
    private var lidPuffed = false

    private fun drawFlash(cv: android.graphics.Canvas) {
        val t = flashT
        if (t < 0f) return
        val a = if (t < 90f) t / 90f * 0.75f else 0.75f * (1f - (t - 90f) / 270f)
        val r = max(w, h) * 0.9f
        fill.color = Color.BLACK; fill.shader = RadialGradient(flashX, flashY, r, intArrayOf(argb(a, 255, 255, 255), argb(a * 0.53f, 255, 255, 255), 0), floatArrayOf(0f, 0.3f, 0.7f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, fill); fill.shader = null
    }

    private fun drawLabels(cv: android.graphics.Canvas) {
        for (l in labels.values) {
            if (l.a < 0.02f) continue
            text.textSize = 14f
            val tw = text.measureText(l.text) + 40f
            rect.set(l.x - tw / 2, l.y - 14f, l.x + tw / 2, l.y + 14f)
            fill.shader = null; fill.color = FeltArt.PAPER; fill.alpha = (255 * l.a).toInt()
            path.reset(); path.moveTo(l.x - 7f, rect.bottom - 1f); path.lineTo(l.x + 7f, rect.bottom - 1f); path.lineTo(l.x, rect.bottom + 7f); path.close()
            cv.drawPath(path, fill)
            FeltArt.pill(cv, rect, FeltArt.PAPER, stitch = false, a = l.a)
            val blink = 0.55f + 0.45f * (0.5f + 0.5f * cos(time / 1000f * 2f * PI.toFloat()))
            fill.color = 0xFFE2574C.toInt(); fill.alpha = (255 * l.a * blink).toInt()
            cv.drawCircle(rect.left + 15f, rect.centerY(), 4f, fill)
            text.color = Color.argb((255 * l.a).toInt(), 0x3B, 0x2E, 0x22)
            cv.drawText(l.text, l.x + 7f, rect.centerY() + 5f, text)
        }
        fill.alpha = 255
    }
    private val path = android.graphics.Path()

    private fun topBar(cv: android.graphics.Canvas) {
        // camera chip, top left: an icon and what tapping does
        val label = if (cameraOn) "카메라 끄기" else "카메라 켜기"
        text.textSize = 15f
        val tw = text.measureText(label)
        camChip.set(14f, chipY - 15f, 14f + tw + 52f, chipY + 15f)
        FeltArt.pill(cv, camChip, FeltArt.CREAM)
        cv.save(); cv.translate(camChip.left + 24f, chipY)
        stroke.strokeWidth = 1.6f; stroke.color = FeltArt.INK; stroke.pathEffect = null; stroke.strokeCap = Paint.Cap.ROUND
        cv.drawRoundRect(-9f, -5f, 9f, 7f, 2.5f, 2.5f, stroke)
        cv.drawCircle(0f, 1f, 3f, stroke)
        cv.drawLine(-3.5f, -5f, -2f, -7.5f, stroke); cv.drawLine(-2f, -7.5f, 2f, -7.5f, stroke); cv.drawLine(2f, -7.5f, 3.5f, -5f, stroke)
        if (cameraOn) cv.drawLine(-9f, -9f, 9f, 9f, stroke)
        cv.restore()
        text.color = FeltArt.INK
        cv.drawText(label, camChip.left + 36f + tw / 2, chipY + 5.5f, text)
        // dex counter, top right (pulses yellow when a catch lands)
        val dex = "도감 $dexCount"
        val dw = text.measureText(dex) + 32f
        val sc = 1f + 0.18f * sin(min(1f, pulse) * PI.toFloat())
        cv.save(); cv.scale(sc, sc, w - 14f - dw / 2, chipY)
        rect.set(w - 14f - dw, chipY - 15f, w - 14f, chipY + 15f)
        FeltArt.pill(cv, rect, if (pulse > 0f) FeltArt.SUN else FeltArt.CREAM)
        cv.drawText(dex, rect.centerX(), chipY + 5.5f, text)
        cv.restore()
    }

    private fun drawToast(cv: android.graphics.Canvas) {
        val t = toast ?: return
        val inK = (t.t / 300f).coerceIn(0f, 1f)
        val outK = ((t.t - (t.life - 220f)) / 220f).coerceIn(0f, 1f)
        val a = min((t.t / 200f).coerceAtMost(1f), 1f - outK)
        if (a <= 0f) return
        val back = 1f + 2.7f * (inK - 1f).pow(3) + 1.7f * (inK - 1f).pow(2)   // overshoots like the mockup's spring
        val s = 0.8f + 0.2f * back - 0.2f * outK
        text.textSize = 20f
        val tw = text.measureText(t.text) + 40f
        val cy = h - bottomInset - 150f - 22f
        cv.save(); cv.scale(s, s, w / 2, cy)
        rect.set(w / 2 - tw / 2, cy - 22f, w / 2 + tw / 2, cy + 22f)
        FeltArt.pill(cv, rect, if (t.good) FeltArt.SUN else FeltArt.PAPER, radius = 16f, a = a)
        text.color = Color.argb((255 * a).toInt(), 0x3B, 0x2E, 0x22)
        cv.drawText(t.text, w / 2, cy + 7f, text)
        cv.restore()
    }

    companion object {
        private const val TAG = "CatchGame"
        private fun argb(a: Float, r: Int, g: Int, b: Int) = Color.argb((a * 255).roundToInt().coerceIn(0, 255), r, g, b)
        private val LIFTED = setOf(Claw.St.GRIP, Claw.St.UP, Claw.St.FALL, Claw.St.CARRY)
        const val HANG = 30f            // the capsule's centre hangs this far below the tips (mockup)
        const val ANTICIPATE = 110f     // the cut-out lifts and swells toward the claw before the capsule appears
        const val CRIT_SLIDE = 260f     // a lost critter slides along the prongs before it falls (world.js)
        const val HINT = "끌어서 맞추고, 꾹!"
        const val MAX_GROUP = 3   // operator 11-15: at most 3 caught together
        // what the toast says on a miss: one voice, a few lines so it does not repeat word for word
        val AIR = listOf("헛집었다", "허공이었다", "아무것도 없었다")
        val NEAR = listOf("아깝다!", "아슬아슬했다", "한 끗 차이!")
        val SLIPPED = listOf("흔들려서 빠졌다", "미끄러졌다!")

        private fun family(k: Mask.Kind) = if (k == Mask.Kind.PERSON || k == Mask.Kind.CRITTER) k else Mask.Kind.DOG
    }
}
