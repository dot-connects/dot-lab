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
import kr.dot.sb.data.StopRule
import kr.dot.sb.settings.ConfigStore

/** The one StandBy host: charge start, manual start (tile) and the 지금 보기 preview. */
class StandbyActivity : ComponentActivity(), HostCallbacks {
    private lateinit var holder: StandbyStateHolder
    /** Started by the plug-in notification ([ChargeStart]): it lives only as long as the charger and the lock screen. */
    private var chargeStart = false
    private var endRx: BroadcastReceiver? = null
    private var unlockRx: BroadcastReceiver? = null
    /** The session began with a charger in: an unplug missed while stopped ends it at the next start. */
    private var beganPlugged = false
    private var resumedOnce = false
    /** r14 C1: an unlock this activity asked for (open an app): the unlock close below must not race its launch. */
    private var unlocking = false
    private val tent = Tent(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StandbyPresence.alive = true
        StandbyPresence.session = java.lang.ref.WeakReference(this)
        clearOldWidgetHost(this)
        beganPlugged = ChargeStart.plugged(this) != 0
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
            ChargeStart.cancelNotification(this)
            ChargeStart.log(this, "화면 열림 · plugged=${ChargeStart.plugged(this)}")
            if (!checkChargeRule()) return
        }
        // plan-r16 T4: unlock ends every session (unplug: the state holder); the exit swipe goes through HostActions.exit()
        unlockRx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                // review H1: a screen-off while covered (dream stub, alarm, call) brings no onStop: end here too
                if (i.action == Intent.ACTION_SCREEN_OFF) {
                    if (!GlowPresence.covering && !isFinishing) { ChargeStart.log(c, "닫힘 · 화면 꺼짐(가려진 채)"); finish() }
                    return
                }
                if (unlocking) return
                ChargeStart.log(c, "닫힘 · 잠금 해제")
                finish()
            }
        }.also {
            registerReceiver(it, IntentFilter(Intent.ACTION_USER_PRESENT).apply { addAction(Intent.ACTION_SCREEN_OFF) }, Context.RECEIVER_NOT_EXPORTED)
        }
        setShowWhenLocked(true)
        // plan-r16 T4: light the panel for the first show only; a later resume or finish must never turn it on
        setTurnScreenOn(true)
        // FLAG_KEEP_SCREEN_ON follows the holder (setKeepScreenOn): lit = on, black = the phone's timeout runs
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        holder = StandbyStateHolder(this, this, ownRotation = false, automatic = chargeStart, showNow = intent.getBooleanExtra(EXTRA_SHOW_NOW, false))
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
        if (!checkChargeRule() || !::holder.isInitialized || isFinishing) return
        if (beganPlugged && ChargeStart.plugged(this) == 0) { ChargeStart.log(this, "닫힘 · 충전기 뽑힘"); finish(); return }
        StandbyPresence.activity = true
        holder.start()
        window.lowRefreshRate()
    }

    override fun onResume() {
        super.onResume()
        // r16b (review): the panel wakes after resume, so the flag may only go once the screen is really on
        if (!resumedOnce && getSystemService(android.os.PowerManager::class.java).isInteractive) { resumedOnce = true; setTurnScreenOn(false) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !resumedOnce && getSystemService(android.os.PowerManager::class.java).isInteractive) { resumedOnce = true; setTurnScreenOn(false) }
    }

    /** plan-r16 T4: a second start (another plug-in, the tile) brings this task forward; a different mode starts over. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        fun mode(i: Intent) = listOf(ChargeStart.EXTRA, EXTRA_TILE, EXTRA_SHOW_NOW).map { i.getBooleanExtra(it, false) }
        val differs = mode(intent) != mode(getIntent())
        setIntent(intent)
        if (intent.getBooleanExtra(ChargeStart.EXTRA, false)) ChargeStart.cancelNotification(this)
        if (differs) recreate()
    }

    override fun onStop() {
        StandbyPresence.activity = false
        val off = ::holder.isInitialized && holder.state.value.displayOff
        if (::holder.isInitialized) holder.stop()
        unlocking = false // a lost dismiss callback must not keep the unlock close off for good
        // plan-r16 T4 (operator Q1): any screen-off ends the session, so the next wake is the phone's lock screen; our
        // own mood light on top is not that (plan-r15 C6): the glow ends us itself if the side key comes while it is on top
        val interactive = getSystemService(android.os.PowerManager::class.java).isInteractive
        val rule = StopRule.onStop(
            interactive = interactive, keyguardLocked = getSystemService(KeyguardManager::class.java).isKeyguardLocked,
            glowCovering = GlowPresence.covering, changingConfig = isChangingConfigurations,
        )
        if (rule == StopRule.FINISH && !isFinishing) {
            if (interactive) ChargeStart.log(this, "닫힘 · 다른 화면")
            else {
                ChargeStart.log(this, "닫힘 · 화면 꺼짐")
                Measure.log(this, "화면 꺼짐 · 세션 종료(${if (off) "꺼짐 뒤 시간 초과" else "측면 버튼"})")
            }
            finish()
        }
        super.onStop()
    }

    override fun onDestroy() {
        tent.stop()
        if (StandbyPresence.session?.get() === this) { StandbyPresence.session = null; StandbyPresence.alive = false }
        endRx?.let { unregisterReceiver(it) }
        unlockRx?.let { unregisterReceiver(it) }
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::holder.isInitialized) holder.onConfiguration(newConfig.smallestScreenWidthDp)
    }

    private fun checkChargeRule(): Boolean {
        if (!chargeStart) return true
        val cfg = ConfigStore.flow(this).value
        val reason = ChargeRules.allowed(cfg.startOnCharge, ChargeStart.plugged(this), cfg.chargeWirelessOnly) ?: return true
        ChargeStart.log(this, "닫힘 · 규칙 · $reason")
        finishForChargeRule(reason)
        return false
    }

    override fun finishForChargeRule(reason: String) { tent.stop(); finish() }

    override fun setBrightness(b: Float) {
        window.attributes = window.attributes.apply { screenBrightness = b }
    }

    override fun setKeepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        /** The settings' 지금 보기 button (not the tile): the phone's brightness, a day/night button. */
        const val EXTRA_SHOW_NOW = "kr.dot.sb.SHOW_NOW"
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
            ctx.startActivity(intentNow(ctx).putExtra(EXTRA_SHOW_NOW, true))
            return true
        }

        /** The low-battery guard with its toast ("충전기에 꽂으면 켤 수 있어요"). */
        fun refused(ctx: Context): Boolean {
            if (!lowBattery(ctx)) return false
            android.widget.Toast.makeText(ctx, "충전기에 꽂으면 켤 수 있어요.", android.widget.Toast.LENGTH_SHORT).show()
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
