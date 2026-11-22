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
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.Merge
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.data.SyncEngine
import kr.dot.wherepark.data.SyncQueue
import kr.dot.wherepark.data.WidgetRefresh

/**
 * Family push. Firebase is set up by hand from BuildConfig FB_* (gitignored firebase.properties), so without those values
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

    /** Setting keys: "park:<carId>", "drive:<carId>", "join". Defaults: park and join on, drive off. */
    fun enabled(ctx: Context, key: String): Boolean =
        Prefs.obj(ctx, KEY).let { if (it.has(key)) it.getBoolean(key) else !key.startsWith("drive:") }

    fun setEnabled(ctx: Context, key: String, on: Boolean) =
        synchronized(SyncQueue) { Prefs.put(ctx, KEY, Prefs.obj(ctx, KEY).put(key, on)) }

    const val RESEND_MS = 24 * 3_600_000L

    /** Same token+auth uploaded less than 24 h ago: the server has it; older uploads are repeated (a server-side loss would otherwise stay). */
    internal fun skipPut(sent: String?, stamp: String, sentAt: Long, now: Long) = sent == stamp && now - sentAt < RESEND_MS

    /** Background thread only (blocks on the token). Uploads the token, or falls back to the 15-minute pull when there is none. */
    fun start(ctx: Context) {
        val token = token(ctx)
        // same token for the same member as last time: the server already has it, no PUT on every app open
        val stamp = token?.let { t -> FamilyStore.auth(ctx)?.let { "$t|$it" } }
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

    /** Contracts section 3 pull (generation-guarded merge), then widget refresh, then notices for [data] (type park|cars|members). */
    fun handle(ctx: Context, data: Map<String, String>) {
        val type = data["type"] ?: return
        if (type != "park" && type != "cars" && type != "members") return
        val gen = SyncQueue.generation(ctx)
        val oldMembers = FamilyStore.members(ctx).map { it.id }.toSet()
        val carId = data["carId"]
        val oldPark = carId?.let { ParkStore.latest(ctx, it) }
        val st = Api.state(ctx).getOrNull() ?: return
        if (!Merge.state(ctx, st, gen)) return  // queue changed: nothing merged, the next pull notifies
        WidgetRefresh.request(ctx)
        when (type) {
            "park" -> {
                val car = st.cars.firstOrNull { it.id == carId } ?: return
                val p = st.parks[car.id] ?: return
                if (oldPark?.id == p.id && oldPark.state == p.state) return  // an undo or a repeat: nothing new
                if (p.state == ParkState.DRIVING) {
                    if (enabled(ctx, "drive:${car.id}")) post(ctx, CH_DRIVE, "d${car.id}".hashCode(), "${p.drivingBy ?: p.byName}가 운행을 시작했어요")
                } else if (enabled(ctx, "park:${car.id}")) {
                    post(ctx, CH_PARK, "p${car.id}".hashCode(), "${p.byName}가 ${car.name}를 댔어요")
                }
            }
            "members" -> if (oldMembers.isNotEmpty() && enabled(ctx, "join")) {
                st.members.filter { it.id !in oldMembers }.forEach { post(ctx, CH_JOIN, "j${it.id}".hashCode(), "${it.name}가 가족에 들어왔어요") }
            }
        }
    }

    /** Called once from App.onCreate. */
    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_PARK, "가족이 댔을 때", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_DRIVE, "운행 시작", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(kr.dot.wherepark.car.ExitNotifier.CH_EXIT, "차에서 내리면 묻기", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_JOIN, "가족 참여", NotificationManager.IMPORTANCE_DEFAULT))
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
        if (!FamilyStore.joined(applicationContext)) return Result.success()
        return if (SyncEngine.pull(applicationContext)) Result.success() else Result.retry()  // pull() already refreshes the widgets
    }
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
