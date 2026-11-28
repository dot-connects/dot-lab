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
    fun setOverlayOn(ctx: Context, on: Boolean) {
        if (overlayOn(ctx) == on) return
        prefs(ctx).edit().putBoolean("overlay", on).apply()
    }
    /** Where the overlay shows: only over the home screen, or always (over every app). Operator 11-14. */
    fun homeOnly(ctx: Context) = prefs(ctx).getBoolean("home_only", true)
    fun setHomeOnly(ctx: Context, on: Boolean) { prefs(ctx).edit().putBoolean("home_only", on).apply() }
    fun usageGranted(ctx: Context): Boolean =
        ctx.getSystemService(android.app.AppOpsManager::class.java)
            .unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), ctx.packageName) ==
            android.app.AppOpsManager.MODE_ALLOWED
    /** Pet box size in dp (slider 32..112, default one app icon). */
    fun size(ctx: Context) = prefs(ctx).getFloat("pet_size", 56f).coerceIn(32f, 112f)
    fun setSize(ctx: Context, dp: Float) { prefs(ctx).edit().putFloat("pet_size", dp).apply() }

    /** Stand line (dp) on the bottom row: just above the dock (plan R5). */
    fun floorY(hDp: Float) = hDp * 0.88f - 48f
    fun night(): Boolean = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { it >= 22 || it < 7 }

    // talk (spec-extras 1): what was said, the friend caught since, when the app was last opened
    fun talkState(ctx: Context) = prefs(ctx).let { Talk.State(it.getLong("talk_at", 0L), it.getLong("talk_day", -1L), it.getInt("talk_n", 0), it.getString("talk_last", null)) }
    fun setTalkState(ctx: Context, s: Talk.State) {
        prefs(ctx).edit().putLong("talk_at", s.at).putLong("talk_day", s.day).putInt("talk_n", s.count).putString("talk_last", s.last).apply()
    }
    /** A new dex friend the pet has not greeted yet (set when a catch grows the dex). */
    /** The pet's own lines (unlock, charger, home return) on or off; the double-tap bubble is separate (operator 11-16). */
    fun talkOn(ctx: Context) = prefs(ctx).getBoolean("talk_on", true)
    fun setTalkOn(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("talk_on", on).apply()
    fun friend(ctx: Context): String? = prefs(ctx).getString("talk_friend", null)
    fun setFriend(ctx: Context, name: String?) { prefs(ctx).edit().putString("talk_friend", name).apply() }
    fun openedAt(ctx: Context) = prefs(ctx).getLong("opened_at", 0L)
    fun setOpenedAt(ctx: Context, at: Long) { prefs(ctx).edit().putLong("opened_at", at).putBoolean("missed_said", false).apply() }
    fun missedSaid(ctx: Context) = prefs(ctx).getBoolean("missed_said", false)
    fun setMissedSaid(ctx: Context) { prefs(ctx).edit().putBoolean("missed_said", true).apply() }
}

/**
 * Foreground service holding the pet's overlay window (PetPrefs.size dp) on the bottom row or a side wall. PetBrain
 * decides; this draws, moves the window, and forwards touches: tap = smile, two taps within 320 ms = dance,
 * hold 380 ms or just drag = dangle, let go = PetBrain.drop picks the place (kept for next time).
 * "Home only": the window hides while any app but the launcher is on top (usage events, read every tick).
 * Ticks slow down while the pet sleeps or idles (battery review 11-13).
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
    private var size = 48f
    private var homeOnly = true
    private var homes: Set<String> = emptySet()
    private var top: String? = null
    private var eventsAt = 0L

    /** Is the launcher the app on top? The last resumed activity in the usage events since the previous read. */
    private fun launcherOnTop(): Boolean {
        val usm = getSystemService(android.app.usage.UsageStatsManager::class.java)
        val end = System.currentTimeMillis()
        if (end - eventsAt < 400L) return top == null || top in homes   // the pet ticks at 30 fps; read events ~2x/s
        val events = usm.queryEvents(if (eventsAt == 0L) end - 60 * 60_000L else eventsAt, end)
        eventsAt = end
        val e = android.app.usage.UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED) top = e.packageName
        }
        return top == null || top in homes
    }
    // the window context's density: the service's resources lag a fold / rotation switch, and a stale density put
    // the wall past the screen edge (operator 11-15: "only its head shows on the right side")
    private val density get() = (winCtx ?: this).resources.displayMetrics.density

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.uptimeMillis()
            if (now - chargeCheckedAt > 10_000) {
                chargeCheckedAt = now
                charging = getSystemService(BatteryManager::class.java).isCharging
            }
            val v = view
            if (now - screenCheckedAt > 1000) {
                screenCheckedAt = now
                if (!brainFits()) { onScreenChanged(); return }   // show() posted a fresh tick
            }
            val home = if (homeOnly || usageOk) launcherOnTop() else false
            val hidden = homeOnly && !home
            // operator 11-15 plan A: away from home it stays high on a wall, clear of any keyboard;
            // only when usage access tells home from other apps: without it the pet keeps its floor (review 11-16 H1)
            brain?.upHigh(usageOk && !homeOnly && !home)
            if (hidden || bubble?.expired() == true) closeBubble()
            if (hidden || talk?.expired() == true || brain?.upHigh == true) closeTalk()
            // coming back to home from another app also gives it a chance to talk (spec 1, review 11-16 M1)
            if (usageOk && home && !wasHome) wantTalkAt = now
            wasHome = home
            if (!hidden) tryTalk(now, home)
            if (bubble != null) brain?.ponder(true)   // a tap's smile is over: back to thinking
            v?.visibility = if (hidden) View.GONE else View.VISIBLE
            brain?.step(min(now - lastMs, 250L).toFloat(), (now - touchedAt).toFloat(), PetPrefs.night(), charging)
            lastMs = now
            if (!hidden) render()
            wxTick(now, home)
            handler.postDelayed(this, when {
                hidden -> 1000L   // hidden: check the top app once a second
                wx != null -> 42L   // the 12 fps weather act
                brain?.act == PetBrain.Act.SLEEP -> 200L
                brain?.act == PetBrain.Act.IDLE -> 66L
                else -> 50L   // walking: 20 fps is enough at pet size (heat review 11-15)
            })
        }
    }

    private var screenCheckedAt = 0L
    /** Usage access granted: we can tell home from other apps even in "always" mode. */
    private var usageOk = false

    private val screen = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            when (i.action) {
                Intent.ACTION_SCREEN_OFF -> { closeTalk(); pause() }
                Intent.ACTION_SCREEN_ON -> resume()
                Intent.ACTION_USER_PRESENT -> { wantTalkAt = SystemClock.uptimeMillis(); unlockAt = wantTalkAt }
                Intent.ACTION_POWER_CONNECTED -> { chargedAt = SystemClock.uptimeMillis(); wantTalkAt = chargedAt }
            }
        }
    }

    // --- talk (spec-extras 1) ---
    private var talk: TalkBubble? = null
    /** An unlock or a plug-in asked for a line; tried on the next ticks while the pet shows on home (5 s). */
    private var wantTalkAt = 0L
    private var chargedAt = 0L
    private var unlockAt = 0L
    private var petName = ""
    private var wasHome = true

    private fun tryTalk(now: Long, home: Boolean) {
        if (wantTalkAt == 0L) return
        if (!PetPrefs.talkOn(this)) { wantTalkAt = 0L; return }
        if (now - wantTalkAt > 5000) { wantTalkAt = 0L; return }
        val b = brain ?: return
        // on home only; without usage access we cannot tell, then a pet that is not up high counts as home
        val onHome = if (usageOk) home else !b.upHigh
        if (!onHome || b.upHigh || bubble != null || talk != null || wx != null || view?.visibility != View.VISIBLE) return
        if (b.act == PetBrain.Act.HANG || b.act == PetBrain.Act.FALL || b.act == PetBrain.Act.PONDER) { wantTalkAt = 0L; return }
        wantTalkAt = 0L
        val friend = PetPrefs.friend(this)
        val missed = !PetPrefs.missedSaid(this) && PetPrefs.openedAt(this).let { it > 0 && System.currentTimeMillis() - it > 3 * 86_400_000L }
        val why = when {
            friend != null -> Talk.Why.NewFriend(friend)
            chargedAt > 0 && now - chargedAt < 60_000 -> Talk.Why.Charging
            missed -> Talk.Why.Missed
            else -> null
        }
        val wall = System.currentTimeMillis()
        val day = java.time.LocalDate.now().toEpochDay()
        val st = PetPrefs.talkState(this)
        val line = Talk.pick(wall, day, Calendar.getInstance().get(Calendar.HOUR_OF_DAY), why, petName.ifEmpty { "나" }, st, sure = now - unlockAt < 6000, rnd = { Math.random().toFloat() }) ?: return
        PetPrefs.setTalkState(this, Talk.said(wall, day, st, line))
        when (why) { is Talk.Why.NewFriend -> PetPrefs.setFriend(this, null); Talk.Why.Missed -> PetPrefs.setMissedSaid(this); else -> {} }
        // the pet is usually asleep by the time the phone is unlocked or plugged in: it wakes to say it (review 11-16 H1)
        if (b.act == PetBrain.Act.SLEEP) { touchedAt = now; b.wake() }
        val v = TalkBubble(winCtx ?: this, line, b.zone) { closeTalk(); touchedAt = SystemClock.uptimeMillis(); brain?.smile() }
        val screen = wm.currentWindowMetrics.bounds
        runCatching { wm.addView(v, v.params(petRect(), screen.width(), screen.height())) }.onSuccess { talk = v }
    }

    private fun petRect(): android.graphics.Rect {
        val p = params ?: return android.graphics.Rect()
        val px = (size * density).roundToInt()
        return android.graphics.Rect(p.x, p.y, p.x + px, p.y + px)
    }

    private fun closeTalk() { talk?.let { runCatching { wm.removeView(it) } }; talk = null }

    /** The cloud follows the pet while it walks. */
    private fun moveTalk() {
        val v = talk ?: return
        val p = v.layoutParams as? WindowManager.LayoutParams ?: return
        val (x, y) = p.x to p.y
        val screen = wm.currentWindowMetrics.bounds
        v.place(p, petRect(), screen.width(), screen.height())
        if (p.x != x || p.y != y) runCatching { wm.updateViewLayout(v, p) }
    }

    override fun onBind(intent: Intent?) = null

    private var winCtx: Context? = null
    private val winCallbacks = object : android.content.ComponentCallbacks {
        override fun onConfigurationChanged(newConfig: Configuration) = onScreenChanged()
        @Deprecated("Deprecated in Java") override fun onLowMemory() {}
    }

    override fun onCreate() {
        super.onCreate()
        alive = true
        // a window context follows rotation and fold switches; the service's own configuration may lag (눈금 FloatService)
        val win = createWindowContext(getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplay(android.view.Display.DEFAULT_DISPLAY), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        winCtx = win
        win.registerComponentCallbacks(winCallbacks)
        wm = win.getSystemService(WindowManager::class.java)
        screenSize = wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        registerReceiver(screen, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_POWER_CONNECTED)
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
        if (intent?.action == ACTION_FADE_OUT) { fadeOut(); return START_NOT_STICKY }
        show()   // every start re-reads the pet and the place
        kr.dot.catchpet.widget.PetWidgets.refresh(this)   // the widget wakes up with it
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("pet", "떠 있는 녀석", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "pet")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("리얼뽑기")
            .setContentText("녀석이 화면을 돌아다니는 중")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun screenDp(): Pair<Float, Float> {
        val b = wm.maximumWindowMetrics.bounds
        return b.width() / density to b.height() / density
    }

    /** The brain was made for this screen (a missed or early config callback leaves it on the old one). */
    private fun brainFits(): Boolean {
        val b = brain ?: return true
        val (w, h) = screenDp()
        return kotlin.math.abs(b.w - w) < 1f && kotlin.math.abs(b.h - h) < 1f
    }

    private fun newBrain(): PetBrain {
        val (w, h) = screenDp()
        val z = PetPrefs.place(this).let { if (it == PetBrain.Zone.BEHIND) PetBrain.Zone.FLOOR else it }  // behind was the old wallpaper place
        return PetBrain(z, w, h, PetPrefs.floorY(h), size) { Math.random().toFloat() }
    }

    private fun show() {
        val old = brain   // another pet or size, or a new screen: it stays where it was
        hide()
        val cells = Frames.chosen(this)
        if (cells == null || !android.provider.Settings.canDrawOverlays(this)) { PetPrefs.setOverlayOn(this, false); stopSelf(); return }
        // a group (two or three together) is wider than one: a bigger box so each stays readable
        val group = getSharedPreferences("catchpet", MODE_PRIVATE).getString("pet", null)?.let { kr.dot.catchpet.dex.Dex.get(this, it) }?.kind?.contains('+') == true
        size = PetPrefs.size(this) * (if (group) 1.4f else 1f)
        petName = getSharedPreferences("catchpet", MODE_PRIVATE).getString("pet", null)?.let { kr.dot.catchpet.dex.Dex.get(this, it)?.name }.orEmpty()
        critter = getSharedPreferences("catchpet", MODE_PRIVATE).getString("pet", null)?.let { kr.dot.catchpet.dex.Dex.get(this, it)?.critterId }
        wxAsked = false
        usageOk = PetPrefs.usageGranted(this)
        homeOnly = PetPrefs.homeOnly(this) && usageOk
        // every installed launcher counts (the default one may be unset, i.e. the chooser); our own app hides the pet
        homes = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .filter { it.priority >= 0 }   // Settings FallbackHome sits at -1000
            .map { it.activityInfo.packageName }.filter { it != "android" && it != packageName }.toSet()
        eventsAt = 0L   // keep `top`: after an hour in one app a rotation found no RESUMED event and read it as home
        val px = (size * density).roundToInt()
        val p = WindowManager.LayoutParams(
            px, px, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // origin = the screen's corner, as the brain and the touch's rawX/rawY are (a cutout or status bar
            // otherwise shifts the window and a right-wall pet ends up past the edge, review 11-16)
            fitInsetsTypes = 0
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        val b = newBrain()
        old?.let(b::takeOver)
        val v = PetView(winCtx ?: this, PetLook(cells), b)
        attachTouch(v)
        brain = b
        params = p
        view = v
        touchedAt = SystemClock.uptimeMillis()
        windowAt(b, v)?.let { (x, y) -> p.x = x; p.y = y }
        v.alpha = 0f
        wm.addView(v, p)
        v.animate().alpha(1f).setDuration(FADE_MS).start()
        resume()
    }

    private var bubble: AppBubble? = null

    /** Two taps: the cloud of apps, while the pet stands and thinks (operator 11-15, 11-16). */
    private fun doubleTap(b: PetBrain) {
        closeBubble(); closeTalk()
        // operator 11-15: the cloud always carries [펫 설정 | 펫 끄기] under the chosen apps (mockup bubble C)
        val apps = PetApps.load(this)
        val p = params ?: return
        val v = AppBubble(winCtx ?: this, apps, b.zone) { pkg ->
            closeBubble()
            when (pkg) {
                null -> {}
                PetApps.OFF -> PetService.stop(this)
                PetApps.SETTINGS -> runCatching {
                    startActivity(Intent(this, MainActivity::class.java).putExtra(MainActivity.OPEN_PET, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
                else -> PetApps.open(this, pkg)
            }
        }
        val px = (size * density).roundToInt()
        val screen = wm.currentWindowMetrics.bounds
        runCatching { wm.addView(v, v.params(android.graphics.Rect(p.x, p.y, p.x + px, p.y + px), screen.width(), screen.height())) }
            .onSuccess { bubble = v }
        if (bubble != null) b.ponder(true)
    }

    private fun closeBubble() { bubble?.let { runCatching { wm.removeView(it) }; brain?.ponder(false) }; bubble = null }

    private fun hide() {
        endWx()
        closeBubble(); closeTalk()
        handler.removeCallbacksAndMessages(null)   // a pending long-press would grab the next brain (review 11-16)
        pause()
        view?.animate()?.cancel()   // a fade-out's end action must not stop a pet that was just turned on again
        view?.let { wm.removeView(it) }
        view = null
        brain = null
    }

    private fun pause() {
        endWx()   // screen off: nothing animates, the frames are freed
        running = false
        handler.removeCallbacks(tick)
    }

    private fun resume() {
        if (running || view == null) return
        running = true
        lastMs = SystemClock.uptimeMillis()
        handler.post(tick)
    }

    // --- weather act (phase C, mockup next7v4 wx.js) ---
    private var wx: WxActView? = null
    private var wxFrame = -1
    private var wxToken = 0      // a decode that ends after the act was dropped (screen off, pet changed) is thrown away
    private var wxCheckedAt = 0L
    private var wxTaps = 0
    private var wxAsked = false  // the frame zip was asked for once in this showing
    private var critter: String? = null

    /** Standing on the floor, at home, doing nothing special: the frames are upright, so no act on a wall. */
    private fun canAct(b: PetBrain, home: Boolean) =
        b.zone == PetBrain.Zone.FLOOR && !b.upHigh && (!usageOk || home) && bubble == null && view?.visibility == View.VISIBLE &&
            b.act in setOf(PetBrain.Act.IDLE, PetBrain.Act.WALK, PetBrain.Act.THINK, PetBrain.Act.SLEEP)

    /** Plays the act on a new weather state or on the first home visit of the day; drives a playing act. */
    private fun wxTick(now: Long, home: Boolean) {
        wx?.let { w ->
            if (w.done() || brain?.act != PetBrain.Act.WX || view?.visibility != View.VISIBLE) endWx()
            else { val f = w.frameAt(w.elapsed()); if (!w.framed || f != wxFrame) { wxFrame = f; w.invalidate() } }
            return
        }
        if (now - wxCheckedAt < 1000) return
        wxCheckedAt = now
        val st = Weather.now(this) ?: return
        val b = brain ?: return
        val today = java.time.LocalDate.now().toEpochDay()
        if (Weather.played(this) == (st.key to today) || !canAct(b, home)) return
        playWx(st) { Weather.setPlayed(this, st.key, today) }
    }

    /** A tap: usually the old smile / hop / wiggle; every third one plays today's weather act. */
    private fun tap(b: PetBrain) {
        if (wx != null) return
        val st = Weather.now(this)
        if (++wxTaps % 3 == 0 && st != null && b.zone == PetBrain.Zone.FLOOR && !b.upHigh && playWx(st) {}) return
        b.tap()
    }

    /** Decodes the frames off the main thread, then starts. False when a critter's frames are not here yet. */
    private fun playWx(st: Weather.State, onStart: () -> Unit): Boolean {
        val c = critter
        if (c != null && WxPack.ready(this, c) == null) {
            if (!wxAsked) { wxAsked = true; WxPack.request(this, c) }   // Wi-Fi by itself; settings has "지금 받기"
            return false
        }
        onStart()
        val my = ++wxToken
        Thread {
            val fr = c?.let { WxPack.frames(this, it, st.key) }
            handler.post {
                if (my != wxToken || wx != null || !running || brain == null || (c != null && fr == null)) {
                    fr?.let { (a, l) -> (a + l).forEach(android.graphics.Bitmap::recycle) }
                } else startWx(st, fr)
            }
        }.start()
        return true
    }

    private fun startWx(st: Weather.State, fr: Pair<List<android.graphics.Bitmap>, List<android.graphics.Bitmap>>?) {
        val b = brain ?: return
        val v = view ?: return
        val pet = petRect()
        val w = WxActView(winCtx ?: this, st, fr?.first.orEmpty(), fr?.second.orEmpty(), pet, v.look.frontHeight(pet.width().toFloat()))
        val p = w.params()
        if (runCatching { wm.addView(w, p) }.isFailure) { w.recycle(); return }
        wx = w; wxFrame = -1
        touchedAt = SystemClock.uptimeMillis()
        b.hold(w.lengthMs.toFloat())
        if (w.framed) v.alpha = 0f   // the frames are the pet while it acts
        closeTalk()
        if (PetPrefs.talkOn(this)) {
            val t = TalkBubble(winCtx ?: this, st.line, b.zone, w.lengthMs) { closeTalk() }
            val screen = wm.currentWindowMetrics.bounds
            // above the act's window: an umbrella or a cap reaches over the pet's box
            val over = if (w.framed) android.graphics.Rect(pet.left, p.y, pet.right, p.y + w.hPx) else pet
            runCatching { wm.addView(t, t.params(over, screen.width(), screen.height())) }.onSuccess { talk = t }
        }
    }

    private fun endWx() {
        wxToken++
        val w = wx ?: return
        wx = null
        runCatching { wm.removeViewImmediate(w) }
        w.recycle()
        view?.alpha = 1f
        closeTalk()
    }

    /** The window is the pet's 48 dp box; hopping moves the window, not the drawing (so nothing is clipped). */
    /** The window's corner (px) for the pet where its brain has it. */
    private fun windowAt(b: PetBrain, v: PetView, t: Float = animClock()): Pair<Int, Int>? {
        val lift = v.look.lift(b, t) * size
        val upright = v.look.rotation(b) == 0f
        val (left, top) = when {
            upright -> b.x - size / 2 to b.y - size - lift
            b.zone == PetBrain.Zone.LEFT -> b.x + lift + b.slideOffset() to b.y - size / 2
            else -> b.x - size - lift + b.slideOffset() to b.y - size / 2
        }
        return (left * density).roundToInt() to (top * density).roundToInt()
    }

    private fun render() {
        val b = brain ?: return
        val v = view ?: return
        val p = params ?: return
        val t = animClock()
        val (nx, ny) = windowAt(b, v, t) ?: return
        // moving the window is a call into the system: only when the pet really moved
        if (nx != p.x || ny != p.y) { p.x = nx; p.y = ny; wm.updateViewLayout(v, p); moveTalk() }
        v.t = t
        v.invalidate()
    }

    // fold / unfold / rotation: a fresh brain for the new screen
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onScreenChanged()
    }

    private var screenSize = 0 to 0

    /** Rotated or unfolded: a new brain for the new screen, on the same edge at the same share of it. */
    private fun onScreenChanged() {
        val b = wm.maximumWindowMetrics.bounds
        if (b.width() to b.height() == screenSize && brainFits()) return
        screenSize = b.width() to b.height()
        if (brain == null) return
        show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachTouch(v: PetView) {
        var downX = 0f
        var downY = 0f
        var holding = false
        var moved = false
        var lastTap = 0L
        var vt: android.view.VelocityTracker? = null
        val slop = 8 * density
        val hold = Runnable {
            holding = true
            brain?.grab(downX / density, downY / density + size / 2)
        }
        v.setOnTouchListener { _, e ->
            val b = brain ?: return@setOnTouchListener false
            touchedAt = SystemClock.uptimeMillis()
            if (e.actionMasked == MotionEvent.ACTION_DOWN) { vt?.recycle(); vt = android.view.VelocityTracker.obtain() }
            // raw coordinates: the window itself moves under the finger
            MotionEvent.obtain(e).let { m -> m.setLocation(e.rawX, e.rawY); vt?.addMovement(m); m.recycle() }
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    holding = false; moved = false
                    handler.postDelayed(hold, HOLD_MS)
                }
                MotionEvent.ACTION_MOVE -> {
                    // dragging right away picks the pet up too (operator 11-13: a plain drag did nothing)
                    if (!holding && !moved && hypot(e.rawX - downX, e.rawY - downY) > slop) {
                        moved = true; handler.removeCallbacks(hold); hold.run()
                    }
                    if (holding) b.drag(e.rawX / density, e.rawY / density + size / 2)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(hold)
                    if (holding) {
                        val px = e.rawX / density; val py = e.rawY / density + size / 2
                        vt?.computeCurrentVelocity(1)   // px per ms
                        val vx = (vt?.xVelocity ?: 0f) / density; val vy = (vt?.yVelocity ?: 0f) / density
                        if (e.actionMasked == MotionEvent.ACTION_UP && kotlin.math.hypot(vx, vy) > PetBrain.THROW_SPEED) b.fling(px, py, vx, vy)
                        else b.drop(px, py)
                        if (!b.upHigh) PetPrefs.setPlace(this, b.zone)
                    }
                    else if (!moved && e.actionMasked == MotionEvent.ACTION_UP) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastTap <= DOUBLE_MS) doubleTap(b) else tap(b)
                        lastTap = now
                    }
                    holding = false
                    vt?.recycle(); vt = null
                }
            }
            render()
            true
        }
    }

    /** Turned off: the pet fades out, then the service stops (operator 11-16 "smoothly"). */
    private fun fadeOut() {
        closeBubble(); closeTalk()
        val v = view ?: run { stopSelf(); return }
        v.animate().alpha(0f).setDuration(FADE_MS).withEndAction { stopSelf() }.start()
    }

    override fun onDestroy() {
        alive = false
        winCtx?.unregisterComponentCallbacks(winCallbacks)
        hide()
        unregisterReceiver(screen)
        kr.dot.catchpet.widget.PetWidgets.refresh(this)   // the widget falls asleep with it
        super.onDestroy()
    }

    companion object {
        const val HOLD_MS = 380L
        const val DOUBLE_MS = 320L

        fun start(ctx: Context) { PetPrefs.setOverlayOn(ctx, true); ctx.startForegroundService(Intent(ctx, PetService::class.java)) }
        fun stop(ctx: Context) {
            PetPrefs.setOverlayOn(ctx, false)
            // running: ask it to fade out first; a start call can be refused from the background, then stop at once
            if (!alive || runCatching { ctx.startService(Intent(ctx, PetService::class.java).setAction(ACTION_FADE_OUT)) }.isFailure)
                ctx.stopService(Intent(ctx, PetService::class.java))
        }

        /** The service is running in this process (the widget's truth for awake / asleep). */
        @Volatile var alive = false; private set
        private const val ACTION_FADE_OUT = "kr.dot.catchpet.FADE_OUT"
        private const val FADE_MS = 220L
    }
}

/** The pet's square box: the pet with its feet on the box edge that touches its line (bottom, or the wall side). */
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
