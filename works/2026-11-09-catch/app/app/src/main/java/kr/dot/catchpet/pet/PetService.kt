package kr.dot.catchpet.pet

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kr.dot.catchpet.MainActivity
import java.util.Calendar
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/** Where the pet lives and whether the overlay is on: SharedPreferences "catchpet". */
object PetPrefs {
    fun prefs(ctx: Context) = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE)
    fun place(ctx: Context): PetBrain.Zone =
        runCatching { PetBrain.Zone.valueOf(prefs(ctx).getString("place", null)!!) }.getOrDefault(PetBrain.Zone.FLOOR)
    fun setPlace(ctx: Context, z: PetBrain.Zone) { prefs(ctx).edit().putString("place", z.name).apply() }
    fun overlayOn(ctx: Context) = prefs(ctx).getBoolean("overlay", false)
    fun setOverlayOn(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("overlay", on).apply() }

    /** Stand line (dp) on the bottom row: just above the dock (plan R5). */
    fun floorY(hDp: Float) = hDp * 0.88f - 48f
    fun night(): Boolean = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it >= 22 || it < 7 }
}

/**
 * Foreground service holding the pet's 48 dp overlay window on the bottom row or a side wall. PetBrain decides;
 * this draws, moves the window, and forwards touches: tap = smile, two taps within 320 ms = dance,
 * hold 380 ms then drag = dangle, let go = PetBrain.drop picks the place.
 */
class PetService : Service() {
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var view: PetView? = null
    private var brain: PetBrain? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastMs = 0L
    private var touchedAt = 0L
    private var charging = false
    private var chargeCheckedAt = 0L
    private var running = false
    private val density get() = resources.displayMetrics.density

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (now - chargeCheckedAt > 10_000) {
                chargeCheckedAt = now
                charging = getSystemService(BatteryManager::class.java).isCharging
            }
            brain?.step(min(now - lastMs, 100L).toFloat(), (now - touchedAt).toFloat(), PetPrefs.night(), charging)
            lastMs = now
            render()
            handler.postDelayed(this, 33)
        }
    }

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (i.action == Intent.ACTION_SCREEN_OFF) pause() else resume()
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }, RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // not sticky: a system restart from the background is refused on Android 15 before the overlay window shows
        // (review M2, 2026-11-13); the user turns the pet on again from the app
        try {
            startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (e: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        show()   // every start re-reads the pet and the place
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("pet", "떠 있는 녀석", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "pet")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("우리집 몬스터")
            .setContentText("녀석이 화면을 돌아다니는 중")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun screenDp(): Pair<Float, Float> {
        val b = wm.currentWindowMetrics.bounds
        return b.width() / density to b.height() / density
    }

    private fun newBrain(): PetBrain {
        val (w, h) = screenDp()
        val z = PetPrefs.place(this).let { if (it == PetBrain.Zone.BEHIND) PetBrain.Zone.FLOOR else it }  // behind = wallpaper
        return PetBrain(z, w, h, PetPrefs.floorY(h), SIZE) { Math.random().toFloat() }
    }

    private fun show() {
        hide()
        val cells = Frames.chosen(this)
        if (cells == null || !android.provider.Settings.canDrawOverlays(this)) { PetPrefs.setOverlayOn(this, false); stopSelf(); return }
        val px = (SIZE * density).roundToInt()
        val p = WindowManager.LayoutParams(
            px, px, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val b = newBrain()
        val v = PetView(this, PetLook(cells), b)
        attachTouch(v)
        brain = b
        params = p
        view = v
        touchedAt = SystemClock.uptimeMillis()
        wm.addView(v, p)
        resume()
    }

    private fun hide() {
        pause()
        view?.let { wm.removeView(it) }
        view = null
        brain = null
    }

    private fun pause() {
        running = false
        handler.removeCallbacks(tick)
    }

    private fun resume() {
        if (running || view == null) return
        running = true
        lastMs = SystemClock.uptimeMillis()
        handler.post(tick)
    }

    /** The window is the pet's 48 dp box; hopping moves the window, not the drawing (so nothing is clipped). */
    private fun render() {
        val b = brain ?: return
        val v = view ?: return
        val p = params ?: return
        val t = animClock()
        val lift = v.look.lift(b, t) * SIZE
        val upright = v.look.rotation(b) == 0f
        val (left, top) = when {
            upright -> b.x - SIZE / 2 to b.y - SIZE - lift
            b.zone == PetBrain.Zone.LEFT -> b.x + lift to b.y - SIZE / 2
            else -> b.x - SIZE - lift to b.y - SIZE / 2
        }
        p.x = (left * density).roundToInt()
        p.y = (top * density).roundToInt()
        wm.updateViewLayout(v, p)
        v.t = t
        v.invalidate()
    }

    // fold / unfold / rotation: a fresh brain for the new screen
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (view != null) show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouch(v: PetView) {
        var downX = 0f
        var downY = 0f
        var holding = false
        var moved = false
        var lastTap = 0L
        val slop = 8 * density
        val hold = Runnable {
            holding = true
            brain?.grab(downX / density, downY / density + SIZE / 2)
        }
        v.setOnTouchListener { _, e ->
            val b = brain ?: return@setOnTouchListener false
            touchedAt = SystemClock.uptimeMillis()
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    holding = false; moved = false
                    handler.postDelayed(hold, HOLD_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (holding) b.drag(e.rawX / density, e.rawY / density + SIZE / 2)
                    else if (!moved && hypot(e.rawX - downX, e.rawY - downY) > slop) { moved = true; handler.removeCallbacks(hold) }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(hold)
                    if (holding) b.drop(e.rawX / density, e.rawY / density + SIZE / 2)
                    else if (!moved && e.actionMasked == MotionEvent.ACTION_UP) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTap <= DOUBLE_MS) b.doubleTap() else b.tap()
                        lastTap = now
                    }
                    holding = false
                }
            }
            render()
            true
        }
    }

    override fun onDestroy() {
        hide()
        unregisterReceiver(screen)
        super.onDestroy()
    }

    companion object {
        const val SIZE = 48f       // dp, about one app icon
        const val HOLD_MS = 380L
        const val DOUBLE_MS = 320L

        fun start(ctx: Context) { PetPrefs.setOverlayOn(ctx, true); ctx.startForegroundService(Intent(ctx, PetService::class.java)) }
        fun stop(ctx: Context) { PetPrefs.setOverlayOn(ctx, false); ctx.stopService(Intent(ctx, PetService::class.java)) }
    }
}

/** The 48 dp box: the pet with its feet on the box edge that touches its line (bottom, or the wall side). */
class PetView(ctx: Context, val look: PetLook, private val brain: PetBrain) : View(ctx) {
    var t = 0f

    override fun onDraw(c: Canvas) {
        val s = width.toFloat()
        val (fx, fy) = when {
            look.rotation(brain) == 0f -> s / 2 to s
            brain.zone == PetBrain.Zone.LEFT -> 0f to s / 2
            else -> s to s / 2
        }
        look.draw(c, brain, t, fx, fy, s, 0f)
    }
}
