package kr.dot.catchpet

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
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Foreground service holding the 96dp overlay window; Behaviour decides, this only draws and forwards touches. */
class PetService : Service() {
    private lateinit var wm: WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private var view: PetView? = null
    private var behaviour: Behaviour? = null
    private var params: WindowManager.LayoutParams? = null
    private var lastMs = 0L
    private var running = false
    private val density get() = resources.displayMetrics.density

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            behaviour?.step(min(now - lastMs, 100L))
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
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        show()
        return START_STICKY
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

    private fun show() {
        hide()
        val id = getSharedPreferences(Pets.PREFS, MODE_PRIVATE).getString(Pets.KEY_PET, null)
        val info = id?.let { Pets.load(this, it) }
        if (info == null) { stopSelf(); return }
        val pics = info.labels
            .mapNotNull { l -> BitmapFactory.decodeFile(File(info.dir, "$l.png").path)?.let { l to it } }.toMap()
        if (pics.isEmpty()) { stopSelf(); return }
        val (w, h) = screenDp()
        val b = Behaviour(PetFile.moves(pics.keys), w, h, System.currentTimeMillis())
        val px = (b.size * density).roundToInt()
        val p = WindowManager.LayoutParams(
            px, px, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val v = PetView(this, pics)
        attachTouch(v)
        behaviour = b
        params = p
        view = v
        wm.addView(v, p)
        resume()
    }

    private fun hide() {
        pause()
        view?.let { wm.removeView(it) }
        view = null
        behaviour = null
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

    private fun render() {
        val f = behaviour?.frame() ?: return
        val v = view ?: return
        val p = params ?: return
        p.x = (f.x * density).roundToInt()
        p.y = (f.y * density).roundToInt()
        wm.updateViewLayout(v, p)
        v.current = f
        v.invalidate()
    }

    // fold / unfold / rotation: new perimeter, pet to the nearest edge
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val (w, h) = screenDp()
        behaviour?.resize(w, h)
        render()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouch(v: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        var longFired = false
        val slop = 8 * density
        val longPress = Runnable {
            longFired = true
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        v.setOnTouchListener { _, e ->
            val b = behaviour ?: return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params?.x ?: 0; startY = params?.y ?: 0
                    dragging = false; longFired = false
                    handler.postDelayed(longPress, 500)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && !longFired && hypot(dx, dy) > slop) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) { b.drag((startX + dx) / density, (startY + dy) / density); render() }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) b.release()
                    else if (!longFired && e.actionMasked == MotionEvent.ACTION_UP) b.tap()
                    render()
                }
            }
            true
        }
    }

    override fun onDestroy() {
        hide()
        unregisterReceiver(screen)
        super.onDestroy()
    }
}

/** Current picture bottom-centred, mirrored when facing left, breathing; hop for bounce, shake for flinch, "z" asleep. */
class PetView(ctx: Context, private val pics: Map<String, Bitmap>) : View(ctx) {
    var current: Frame? = null
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF5B6BD8.toInt()
        isFakeBoldText = true
        textSize = 18 * ctx.resources.displayMetrics.density
    }
    private val dst = RectF()

    override fun onDraw(c: Canvas) {
        val f = current ?: return
        val bmp = pics[f.pose] ?: pics["front"] ?: pics.values.first()
        val sec = f.stateMs / 1000.0
        val box = width * 0.8f
        val s = min(box / bmp.width, box / bmp.height)
        val hop = if (f.move == Move.BOUNCE) abs(sin(sec * PI * 1.5)).toFloat() * height * 0.12f else 0f
        val shake = if (f.move == Move.FLINCH) sin(sec * 150).toFloat() * width * 0.05f else 0f
        val breathe = 1f + 0.03f * sin(SystemClock.uptimeMillis() / 1000.0 * 2 * PI / 2.4).toFloat()
        c.save()
        c.translate(width / 2f + shake, height * 0.97f - hop)
        c.scale(if (f.faceLeft) -1f else 1f, breathe)
        dst.set(-bmp.width * s / 2, -bmp.height * s, bmp.width * s / 2, 0f)
        c.drawBitmap(bmp, null, dst, paint)
        c.restore()
        if (f.move == Move.SLEEP) {
            c.drawText("z", width * 0.72f, height * 0.25f - (sec % 2).toFloat() * height * 0.05f, zPaint)
        }
    }
}
