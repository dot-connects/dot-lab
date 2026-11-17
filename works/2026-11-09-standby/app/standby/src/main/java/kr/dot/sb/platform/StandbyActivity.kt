package kr.dot.sb.platform

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kr.dot.sb.data.HostCallbacks
import kr.dot.sb.data.StandbyStateHolder

/** Manual start / preview, and the unlocked hand-over target of the dream (same UI, system rotation). */
class StandbyActivity : ComponentActivity(), HostCallbacks {
    private lateinit var holder: StandbyStateHolder
    /** Started by the plug-in notification ([ChargeStart]): it lives only as long as the charger and the lock screen. */
    private var chargeStart = false
    private var endRx: BroadcastReceiver? = null
    /** r14 C1: an unlock this activity asked for (open an app): the unlock close below must not race its launch. */
    private var unlocking = false
    private val tent = Tent(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StandbyPresence.alive = true
        clearOldWidgetHost(this)
        chargeStart = intent.getBooleanExtra(ChargeStart.EXTRA, false)
        if (intent.getBooleanExtra(EXTRA_TILE, false)) {
            if (lowBattery(this)) { finish(); return }
            // without a charger the screen stays on: leave at LOW_BATTERY % (re-checked on every battery change)
            endRx = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, 100) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                    if (i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) == 0 && level <= LOW_BATTERY) finish()
                }
            }.also { registerReceiver(it, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED) }
        }
        if (chargeStart) {
            StandbyPresence.chargeActivity = java.lang.ref.WeakReference(this)
            ChargeStart.cancelNotification(this)
            val plugged = ChargeStart.plugged(this)
            ChargeStart.log(this, "화면 열림 · ${ChargeStart.sinceLaunch()} · plugged=$plugged")
            // r14 D: plugged, not isCharging (false at 85 % battery protection / while a pad starts)
            if (plugged == 0) { ChargeStart.log(this, "닫힘 · 이미 뽑힘"); finish(); return } // unplugged before we got here
            // unlock ends it (unplug: the state holder, for every start path); the exit swipe goes through HostActions.exit()
            endRx = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (unlocking) return
                    ChargeStart.log(c, "닫힘 · 잠금 해제")
                    finish()
                }
            }.also {
                registerReceiver(it, IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_NOT_EXPORTED)
            }
        }
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        holder = StandbyStateHolder(this, this, ownRotation = false)
        val base = HostActions(this, holder, this)
        // plan-r15 E7: the mood light on the inner screen brings the clock to the outer screen (where the phone allows it)
        val actions = object : kr.dot.sb.ui.state.StandbyActions by base {
            override fun moodShowing(on: Boolean) {
                base.moodShowing(on)
                if (on && kr.dot.sb.settings.ConfigStore.flow(this@StandbyActivity).value.tentClock) tent.start() else tent.stop()
            }
        }
        setContent { HostContent(holder, actions) }
        // plan-r15 C9: no POST request here any more (it popped over the lock screen); first run and 권한 ask for it
        PostureProbe.attach(this, "스탠바이")
    }

    override fun onStart() {
        super.onStart()
        StandbyPresence.activity = true
        holder.start()
        window.lowRefreshRate()
    }

    override fun onResume() {
        super.onResume()
        PendingUnlock.block?.let { PendingUnlock.block = null; unlockThen(it) }
    }

    override fun onStop() {
        StandbyPresence.activity = false
        StandbyPresence.lastHiddenMs = android.os.SystemClock.elapsedRealtime()
        holder.stop()
        unlocking = false // a lost dismiss callback must not keep the unlock close off for good
        // the side key turned the screen off: leave StandBy so the next wake shows the normal lock screen. Covered by our
        // own mood light is not that (plan-r15 C6): the glow ends us itself if the side key comes while it is on top
        if (chargeStart && !isChangingConfigurations && !isFinishing && !GlowPresence.covering) { ChargeStart.log(this, "닫힘 · 화면 꺼짐"); finish() }
        super.onStop()
    }

    override fun onDestroy() {
        tent.stop()
        StandbyPresence.alive = false
        if (StandbyPresence.chargeActivity?.get() === this) StandbyPresence.chargeActivity = null
        endRx?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        holder.onConfiguration(newConfig.smallestScreenWidthDp)
    }

    override fun setBrightness(b: Float) {
        window.attributes = window.attributes.apply { screenBrightness = b }
    }

    override fun unlockThen(block: () -> Unit) {
        unlocking = true
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() {
                    unlocking = false
                    block()
                    // a charge-started StandBy ends with the unlock (manual / tile StandBy stays)
                    if (chargeStart && !isFinishing) { ChargeStart.log(this@StandbyActivity, "닫힘 · 잠금 해제"); finish() }
                }
                override fun onDismissCancelled() { unlocking = false }
                override fun onDismissError() { unlocking = false }
            },
        )
    }

    override fun openGlow() { startActivity(Intent(this, GlowActivity::class.java).putExtra(GlowSource.EXTRA, GlowSource.UNFOLD.name)) }

    companion object {
        const val EXTRA_TILE = "kr.dot.sb.TILE"
        const val LOW_BATTERY = 20

        /** No charger and the battery at [LOW_BATTERY] % or less: a manual start would only drain it. */
        fun lowBattery(ctx: Context): Boolean =
            ChargeStart.plugged(ctx) == 0 && ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) <= LOW_BATTERY

        /**
         * plan-r15 C9: a manual start (tile, the coming 지금 보기 button) goes through the tile path and its low-battery
         * guard; refused = a toast that says why. True = started.
         */
        fun startNow(ctx: Context): Boolean {
            if (refused(ctx)) return false
            ctx.startActivity(intentNow(ctx))
            return true
        }

        /** The low-battery guard with its toast ("충전기에 꽂으면 켤 수 있어요"). */
        fun refused(ctx: Context): Boolean {
            if (!lowBattery(ctx)) return false
            android.widget.Toast.makeText(ctx, "충전기에 꽂으면 켤 수 있어요", android.widget.Toast.LENGTH_SHORT).show()
            return true
        }

        fun intentNow(ctx: Context): Intent = Intent(ctx, StandbyActivity::class.java).putExtra(EXTRA_TILE, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

private const val OLD_WIDGET_HOST_ID = 0x5B0A

/** r14 C1: the phone-widget card (r9-r13) is gone; its host's ids are released once (the system keeps them until then). */
internal fun clearOldWidgetHost(ctx: Context) {
    val p = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    if (p.getBoolean("widgetsCleared", false)) return
    runCatching { android.appwidget.AppWidgetHost(ctx.applicationContext, OLD_WIDGET_HOST_ID).deleteHost() }
    p.edit().putBoolean("widgetsCleared", true).apply()
}
