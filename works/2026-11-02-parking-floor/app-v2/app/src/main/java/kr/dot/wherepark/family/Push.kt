package kr.dot.wherepark.family

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import java.util.concurrent.TimeUnit
import kr.dot.wherepark.BuildConfig
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.isMine
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.GroupStore
import kr.dot.wherepark.data.Merge
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.data.SyncEngine
import kr.dot.wherepark.data.SyncQueue
import kr.dot.wherepark.data.WidgetRefresh

/**
 * Group push. Firebase is set up by hand from BuildConfig FB_* (gitignored firebase.properties), so without those values
 * the app still builds and a 15-minute pull ("wp-pull") keeps the family in sync.
 */
object Push {
    const val PULL_WORK = "wp-pull"
    const val CH_PARK = "family_park"
    const val CH_DRIVE = "family_drive"
    const val CH_JOIN = "family_join"
    private const val KEY = "wp_notif"
    private const val SENT_KEY = "wp_fcm_sent"
    private const val SENT_AT_KEY = "wp_fcm_sent_at"

    @Volatile var post: (Context, String, Int, String) -> Unit = ::postNotification

    private val configured: Boolean get() = BuildConfig.FB_APP_ID.isNotEmpty()

    /** Setting keys: "park:<carId>", "drive:<carId>", "join". Default: every key on. */
    fun enabled(ctx: Context, key: String): Boolean =
        Prefs.obj(ctx, KEY).let { if (it.has(key)) it.getBoolean(key) else true }

    fun setEnabled(ctx: Context, key: String, on: Boolean) =
        synchronized(SyncQueue) { Prefs.put(ctx, KEY, Prefs.obj(ctx, KEY).put(key, on)) }

    const val RESEND_MS = 24 * 3_600_000L

    /** Same token+auth uploaded less than 24 h ago: the server has it; older uploads are repeated (a server-side loss would otherwise stay). */
    internal fun skipPut(sent: String?, stamp: String, sentAt: Long, now: Long) = sent == stamp && now - sentAt < RESEND_MS

    /** Background thread only (blocks on the token). Uploads the token, or falls back to the 15-minute pull when there is none. */
    fun start(ctx: Context) {
        val token = token(ctx)
        // same token for the same member as last time: the server already has it, no PUT on every app open
        val stamp = token?.let { t -> GroupStore.auth(ctx)?.let { "$t|$it" } }
        val sp = Prefs.sp(ctx)
        if (stamp != null && skipPut(sp.getString(SENT_KEY, null), stamp, sp.getLong(SENT_AT_KEY, 0), System.currentTimeMillis())) { cancelPull(ctx); return }
        if (token != null && Api.setFcmToken(ctx, token)) {
            stamp?.let { sp.edit().putString(SENT_KEY, it).putLong(SENT_AT_KEY, System.currentTimeMillis()).apply() }
            cancelPull(ctx)
        } else schedulePull(ctx)
    }

    fun token(ctx: Context): String? {
        if (!configured) return null
        return try {
            if (FirebaseApp.getApps(ctx).isEmpty()) {
                FirebaseApp.initializeApp(
                    ctx,
                    FirebaseOptions.Builder().setApiKey(BuildConfig.FB_API_KEY).setApplicationId(BuildConfig.FB_APP_ID)
                        .setProjectId(BuildConfig.FB_PROJECT_ID).setGcmSenderId(BuildConfig.FB_SENDER_ID).build(),
                )
            }
            Tasks.await(FirebaseMessaging.getInstance().token, 10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            Log.w("wp-push", "no token: ${e.javaClass.simpleName}")
            null
        }
    }

    fun schedulePull(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<PullWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PULL_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun cancelPull(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(PULL_WORK)
    }

    /** Pushes within 3 s coalesce into one pull ([PushPullWorker]); the first one wins (KEEP). */
    fun handle(ctx: Context, data: Map<String, String>) {
        val type = data["type"] ?: return
        if (type != "park" && type != "cars" && type != "members") return
        try {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "wp-pushpull", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PushPullWorker>().setInitialDelay(3, TimeUnit.SECONDS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
            )
        } catch (_: IllegalStateException) {
            // WorkManager not initialised (plain unit tests)
        }
    }

    /**
     * One generation-guarded pull, then widget refresh, then notices for the diff against the snapshot taken before it.
     * false = retry. // ponytail: one pull's diff; a pull merged elsewhere skips its notice
     */
    fun pullAndNotify(ctx: Context): Boolean {
        val gen = SyncQueue.generation(ctx)
        val oldParks = CarStore.cars(ctx).associate { it.id to ParkStore.latest(ctx, it.id) }
        val oldMembers = GroupStore.groups(ctx).flatMap { g -> g.members.map { it.id } }.toSet()
        val st = Api.state(ctx).getOrNull() ?: return false
        if (!Merge.state(ctx, st, gen)) return true  // queue changed: nothing merged, the next pull notifies
        WidgetRefresh.request(ctx)
        val meId = st.me?.id ?: GroupStore.me(ctx)?.id
        st.cars.filter { !it.isMine(meId) }.forEach { car ->
            val p = st.parks[car.id] ?: return@forEach
            val old = oldParks[car.id]
            if (old?.id == p.id && old.state == p.state) return@forEach  // an undo or a repeat: nothing new
            when (p.state) {
                ParkState.DRIVING -> if (enabled(ctx, "drive:${car.id}"))
                    post(ctx, CH_DRIVE, "d${car.id}".hashCode(), if (p.drivingOther) "다른 그룹에서 ${car.name}를 가져갔어요" else "${p.drivingBy ?: p.byName}가 운행을 시작했어요")
                ParkState.PARKED -> if (enabled(ctx, "park:${car.id}"))
                    post(ctx, CH_PARK, "p${car.id}".hashCode(), if (p.byOther) "다른 그룹에서 ${car.name}를 댔어요" else "${p.byName}가 ${car.name}를 댔어요")
                ParkState.LEFT -> {}
            }
        }
        if (oldMembers.isNotEmpty() && enabled(ctx, "join")) st.groups.forEach { g ->
            g.members.filter { it.id !in oldMembers && !it.me }.forEach { post(ctx, CH_JOIN, "j${it.id}".hashCode(), "${it.name}가 '${g.name}' 그룹에 들어왔어요") }
        }
        return true
    }

    /** Called once from App.onCreate. */
    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PARK, "그룹에서 댔을 때", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_DRIVE, "그룹에서 가져갔을 때", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(kr.dot.wherepark.car.ExitNotifier.CH_EXIT, "차에서 내리면 묻기", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_JOIN, "그룹 참여", NotificationManager.IMPORTANCE_DEFAULT))
    }

    internal fun postNotification(ctx: Context, channel: String, id: Int, text: String) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.notify(id, Notification.Builder(ctx, channel).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("어디댔어").setContentText(text).setAutoCancel(true).build())
        } catch (e: RuntimeException) {
            Log.w("wp-push", "notify failed: $e")
        }
    }
}

class PullWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        if (!GroupStore.joined(applicationContext)) return Result.success()
        return if (SyncEngine.pull(applicationContext)) Result.success() else Result.retry()  // pull() already refreshes the widgets
    }
}

class PushPullWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result =
        if (!GroupStore.joined(applicationContext) || Push.pullAndNotify(applicationContext)) Result.success() else Result.retry()
}

class PushService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        try {
            Push.handle(this, message.data)
        } catch (e: Exception) {
            Log.w("wp-push", "handle failed: ${e.javaClass.simpleName}")
        }
    }

    override fun onNewToken(token: String) {
        if (Api.setFcmToken(this, token)) Push.cancelPull(this) else Push.schedulePull(this)
    }
}
