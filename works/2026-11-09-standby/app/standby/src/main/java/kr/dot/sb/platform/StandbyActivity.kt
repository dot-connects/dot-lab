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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        chargeStart = intent.getBooleanExtra(ChargeStart.EXTRA, false)
        if (intent.getBooleanExtra(EXTRA_TILE, false)) {
            val bm = getSystemService(BatteryManager::class.java)
            if (!bm.isCharging && bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) <= LOW_BATTERY) { finish(); return }
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
            ChargeStart.log(this, "화면 열림")
            if (!getSystemService(BatteryManager::class.java).isCharging) { ChargeStart.log(this, "닫힘 · 이미 뽑힘"); finish(); return } // unplugged before we got here
            // unlock ends it (unplug: the state holder, for every start path); the exit swipe goes through HostActions.exit()
            endRx = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
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
        val actions = HostActions(this, holder, this)
        setContent { HostContent(holder, actions) }
        // manual start only: a dream hand-over (PendingUnlock) must not pop a dialog over the lock screen
        if (PendingUnlock.block == null && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onStart() {
        super.onStart()
        StandbyPresence.activity = true
        holder.start()
    }

    override fun onResume() {
        super.onResume()
        PendingUnlock.block?.let { PendingUnlock.block = null; unlockThen(it) }
    }

    override fun onStop() {
        StandbyPresence.activity = false
        holder.stop()
        // the side key turned the screen off: leave StandBy so the next wake shows the normal lock screen
        if (chargeStart && !isChangingConfigurations && !isFinishing) { ChargeStart.log(this, "닫힘 · 화면 꺼짐"); finish() }
        super.onStop()
    }

    override fun onDestroy() {
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
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(
            this,
            object : KeyguardManager.KeyguardDismissCallback() {
                override fun onDismissSucceeded() = block()
            },
        )
    }

    override fun openGlow() { startActivity(Intent(this, GlowActivity::class.java)) }

    companion object {
        const val EXTRA_TILE = "kr.dot.sb.TILE"
        const val LOW_BATTERY = 20
    }
}
