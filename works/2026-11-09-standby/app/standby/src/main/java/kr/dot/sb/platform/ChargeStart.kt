package kr.dot.sb.platform

import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.PowerManager
import kr.dot.sb.settings.ConfigStore

/** Which StandBy host is on screen right now (same process as the job, so a plain flag is enough). */
object StandbyPresence {
    @Volatile var dream = false
    @Volatile var activity = false
}

/** Why plugging in did or did not bring StandBy up; the order of the checks is the order of the `when`. */
enum class ChargeVerdict { LAUNCH, OPTION_OFF, NOT_CHARGING, ALREADY_SHOWING, USING_PHONE, NO_NOTIFICATIONS, NO_FULL_SCREEN }

object ChargeRules {
    fun decide(
        optionOn: Boolean, charging: Boolean, interactive: Boolean, locked: Boolean,
        dreamRunning: Boolean, activityFront: Boolean, notificationsOk: Boolean, fullScreenOk: Boolean,
    ): ChargeVerdict = when {
        !optionOn -> ChargeVerdict.OPTION_OFF
        !charging -> ChargeVerdict.NOT_CHARGING
        dreamRunning || activityFront -> ChargeVerdict.ALREADY_SHOWING
        interactive && !locked -> ChargeVerdict.USING_PHONE // unlocked with the screen on: someone is using the phone
        !notificationsOk -> ChargeVerdict.NO_NOTIFICATIONS
        !fullScreenOk -> ChargeVerdict.NO_FULL_SCREEN
        else -> ChargeVerdict.LAUNCH
    }
}

/**
 * Start StandBy when the charger goes in, without a service. Android starts a dream only when a lit screen times
 * out while charging, so a plug-in with the screen off would show nothing. A persisted charging-constraint job is
 * woken by the system when power arrives (that ends Doze); it posts the same full-screen-intent notification the
 * mood light uses, which is the only way to put an activity over the lock screen from the background.
 *
 * JobScheduler has no "not charging" constraint, so a job re-armed while still plugged in would run again at once.
 * After each run the next job therefore waits [REARM_MS]; an unplug (dream/holder, [rearmNow]) re-arms it without
 * the wait, so the usual plug-in, unplug, plug-in cycle is never delayed.
 * ponytail: unplug while nothing of ours is alive, then plug in again within [REARM_MS] of the last run = up to
 * [REARM_MS] late. The upgrade is a dynamic power receiver inside the (already bound) notification listener.
 */
object ChargeStart {
    const val EXTRA = "kr.dot.sb.CHARGE_START"
    private const val JOB_ID = 4101
    private const val NOTIF_ID = 8
    private const val REARM_MS = 20 * 60_000L

    private fun on(ctx: Context) = ConfigStore.flow(ctx).value.startOnCharge

    private fun arm(ctx: Context, delayMs: Long, replace: Boolean) {
        val js = ctx.getSystemService(JobScheduler::class.java)
        if (!replace && js.getPendingJob(JOB_ID) != null) return
        val b = JobInfo.Builder(JOB_ID, ComponentName(ctx, ChargeJobService::class.java)).setRequiresCharging(true).setPersisted(true)
        if (delayMs > 0) b.setMinimumLatency(delayMs)
        js.schedule(b.build())
    }

    /** App start, boot, package update, option change: make the job exist iff the option is on. */
    fun sync(ctx: Context) {
        if (on(ctx)) arm(ctx, 0, replace = false) else ctx.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }

    /** The charger came out: the next plug-in must not wait for the rearm delay. */
    fun rearmNow(ctx: Context) { if (on(ctx)) arm(ctx, 0, replace = true) }

    fun cancelNotification(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)

    internal fun fire(ctx: Context) {
        if (!on(ctx)) return
        arm(ctx, REARM_MS, replace = true) // first, so a failure below cannot leave us unarmed
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val verdict = ChargeRules.decide(
            optionOn = true,
            charging = ctx.getSystemService(BatteryManager::class.java).isCharging,
            interactive = ctx.getSystemService(PowerManager::class.java).isInteractive,
            locked = ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked,
            dreamRunning = StandbyPresence.dream,
            activityFront = StandbyPresence.activity,
            notificationsOk = ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
            fullScreenOk = nm.canUseFullScreenIntent(),
        )
        if (verdict != ChargeVerdict.LAUNCH) return
        Channels.ensure(ctx)
        val open = PendingIntent.getActivity(
            ctx, 13, Intent(ctx, StandbyActivity::class.java).putExtra(EXTRA, true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        nm.notify(
            NOTIF_ID,
            Notification.Builder(ctx, Channels.CHARGE)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("StandBy")
                .setCategory(Notification.CATEGORY_ALARM)
                .setFullScreenIntent(open, true)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setTimeoutAfter(2 * 60_000L)
                .build(),
        )
    }
}

class ChargeJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean { ChargeStart.fire(this); return false }
    override fun onStopJob(params: JobParameters): Boolean = false
}

/** BOOT_COMPLETED / MY_PACKAGE_REPLACED: persisted jobs normally survive, this is the belt for the cases they do not. */
class ChargeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = ChargeStart.sync(context)
}
