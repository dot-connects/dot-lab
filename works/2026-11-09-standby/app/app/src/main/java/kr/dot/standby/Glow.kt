package kr.dot.standby

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import kotlinx.coroutines.launch

/**
 * Mood-light-on-alarm test: an exact alarm of our own fires in N seconds, posts a full-screen-intent
 * notification, and the system should open GlowActivity over the lock screen. The log shows which
 * screen it lands on and whether the full-screen intent was allowed.
 */
object GlowTest {
    fun schedule(ctx: Context, seconds: Int): String {
        val am = ctx.getSystemService(AlarmManager::class.java)
        if (!am.canScheduleExactAlarms()) return "정확한 알람 권한이 없습니다"
        val at = System.currentTimeMillis() + seconds * 1000L
        val fire = PendingIntent.getBroadcast(
            ctx, 0, Intent(ctx, GlowAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val show = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire)
        EventLog.write(ctx, "glow_scheduled", "in=${seconds}s")
        return "${seconds}초 뒤에 무드등이 켜집니다. 지금 화면을 끄거나 접어 두세요."
    }
}

class GlowAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        EventLog.write(ctx, "glow_fire", "canFullScreen=${nm.canUseFullScreenIntent()}")
        val open = PendingIntent.getActivity(
            ctx, 2, Intent(ctx, GlowActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(ctx, App.CH_GLOW)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("무드등 테스트")
            .setCategory(Notification.CATEGORY_ALARM)
            .setFullScreenIntent(open, true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(7, n)
    }
}

class GlowActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.attributes = window.attributes.apply { screenBrightness = 0.6f }
        val label = TextView(this).apply {
            text = "무드등 테스트\n탭하면 닫힘"
            setTextColor(Color.argb(140, 60, 30, 0))
            textSize = 16f
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(255, 214, 150), Color.rgb(255, 160, 90)),
            )
            setOnClickListener { finish() }
        }
        setContentView(label)
        log("glow_shown", "")
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WindowInfoTracker.getOrCreate(this@GlowActivity).windowLayoutInfo(this@GlowActivity).collect { info ->
                    log("glow_fold", Fold.describe(info.displayFeatures.filterIsInstance<FoldingFeature>()))
                }
            }
        }
    }

    override fun onConfigurationChanged(c: Configuration) {
        super.onConfigurationChanged(c)
        log("glow_config", "w=${c.screenWidthDp}dp h=${c.screenHeightDp}dp")
    }

    override fun onDestroy() {
        log("glow_closed", "")
        super.onDestroy()
    }

    private fun log(kind: String, extra: String) {
        val where = display?.let { Probe.display(this, it) } ?: "display=?"
        EventLog.write(this, kind, "$where $extra".trim())
    }
}

object Fold {
    fun describe(list: List<FoldingFeature>): String =
        if (list.isEmpty()) "fold=none" else list.joinToString(" ") {
            "fold=${it.state} orientation=${it.orientation} separating=${it.isSeparating} bounds=${it.bounds}"
        }
}
