package kr.dot.wherepark.car

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.data.CarLinkStore
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.data.SyncQueue
import kr.dot.wherepark.data.WidgetRefresh
import kr.dot.wherepark.family.Push
import kr.dot.wherepark.ui.ListenActivity
import org.json.JSONObject

/** What the exit notification shows; [actions] are the button labels in order. */
data class ExitNotice(val carId: String, val title: String, val text: String, val actions: List<String>)

/** "Where did you park?" prompt when a linked car's Bluetooth drops, and the driving state when it comes back. */
object ExitNotifier {
    const val CH_EXIT = "car_exit"
    const val A_SPEAK = "말하기"
    const val A_FLOOR = "층 고르기"
    const val A_NOT = "안 댔어요"
    const val A_OFF = "이 차 알림 끄기"
    const val ACT_NOT = "kr.dot.wherepark.EXIT_NOT"
    const val ACT_OFF = "kr.dot.wherepark.EXIT_OFF"
    const val THROTTLE_MS = 30 * 60_000L
    const val REAPPEAR_MS = 2 * 60_000L
    const val TIMEOUT_MS = 10 * 60_000L
    const val OFF_AFTER = 3
    private const val KEY = "wp_exit"

    @Volatile var now: () -> Long = System::currentTimeMillis
    @Volatile var post: (Context, ExitNotice) -> Unit = ::postNotice
    @Volatile var cancel: (Context, String) -> Unit = ::cancelNotice

    fun enabled(ctx: Context, carId: String) = Push.enabled(ctx, "exit:$carId")
    fun setEnabled(ctx: Context, carId: String, on: Boolean) = Push.setEnabled(ctx, "exit:$carId", on)

    private fun carOf(ctx: Context, address: String) = CarLinkStore.carOf(ctx, CarLink.norm(address))

    private fun slot(ctx: Context, carId: String): JSONObject = Prefs.obj(ctx, KEY).optJSONObject(carId) ?: JSONObject()
    private fun putSlot(ctx: Context, carId: String, s: JSONObject) =
        synchronized(SyncQueue) { Prefs.put(ctx, KEY, Prefs.obj(ctx, KEY).put(carId, s)) }

    fun onDisappeared(ctx: Context, address: String) {
        val carId = carOf(ctx, address) ?: return
        val t = now()
        val s = slot(ctx, carId).put("gone", t)
        val send = enabled(ctx, carId) && t - s.optLong("notified", -THROTTLE_MS) >= THROTTLE_MS
        if (send) s.put("notified", t)
        putSlot(ctx, carId, s)
        if (send) post(ctx, notice(ctx, carId))
    }

    fun onAppeared(ctx: Context, address: String) {
        val carId = carOf(ctx, address) ?: return
        val s = slot(ctx, carId)
        val gone = s.optLong("gone", 0)
        s.remove("gone")
        cancel(ctx, carId)
        if (gone != 0L && now() - gone < REAPPEAR_MS) {
            s.remove("notified")  // a flicker must not use up the 30 minutes
            return putSlot(ctx, carId, s)
        }
        putSlot(ctx, carId, s)
        val p = ParkStore.latest(ctx, carId) ?: return
        if (p.state == ParkState.DRIVING) return
        ParkStore.save(ctx, p.copy(state = ParkState.DRIVING, drivingBy = FamilyStore.me(ctx)?.name))
        SyncQueue.add(ctx, Op.State(carId, ParkState.DRIVING))
        SyncQueue.kick(ctx)
        WidgetRefresh.request(ctx)
    }

    /** "안 댔어요": counts in a row (a new park record resets the count). */
    fun notParked(ctx: Context, carId: String) {
        val s = slot(ctx, carId)
        val latest = ParkStore.latest(ctx, carId)?.id ?: 0L
        val n = (if (s.optLong("parkId", 0) == latest) s.optInt("nots", 0) else 0) + 1
        putSlot(ctx, carId, s.put("nots", n).put("parkId", latest))
        cancel(ctx, carId)
    }

    fun turnOff(ctx: Context, carId: String) {
        setEnabled(ctx, carId, false)
        cancel(ctx, carId)
    }

    private fun notice(ctx: Context, carId: String): ExitNotice {
        val s = slot(ctx, carId)
        val latest = ParkStore.latest(ctx, carId)?.id ?: 0L
        val nots = if (s.optLong("parkId", 0) == latest) s.optInt("nots", 0) else 0
        val name = CarStore.cars(ctx).firstOrNull { it.id == carId }?.name ?: "차"
        val actions = mutableListOf(A_SPEAK, A_FLOOR, A_NOT)
        if (nots >= OFF_AFTER) actions += A_OFF
        return ExitNotice(carId, "${name}에서 내렸어요", "어디에 댔어요?", actions)
    }

    /** The settings [알림 시험] button: ignores the toggle and the throttle. */
    fun testNotice(ctx: Context, carId: String) = post(ctx, notice(ctx, carId))

    private fun id(carId: String) = "x$carId".hashCode()

    internal fun cancelNotice(ctx: Context, carId: String) = ctx.getSystemService(NotificationManager::class.java).cancel(id(carId))

    internal fun postNotice(ctx: Context, n: ExitNotice) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        fun listen(manual: Boolean) = Intent(ctx, ListenActivity::class.java)
            .putExtra("bt_car_id", n.carId).putExtra("from", "notif").also { if (manual) it.putExtra("mode", "manual") }
        fun activity(label: String, i: Intent) = PendingIntent.getActivity(ctx, "$label${n.carId}".hashCode(), i, flags)
        fun broadcast(label: String, act: String) = PendingIntent.getBroadcast(
            ctx, "$label${n.carId}".hashCode(), Intent(ctx, ExitActionReceiver::class.java).setAction(act).putExtra("car_id", n.carId), flags,
        )
        try {
            val b = Notification.Builder(ctx, CH_EXIT).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(n.title).setContentText(n.text).setAutoCancel(true).setTimeoutAfter(TIMEOUT_MS)
                .setContentIntent(activity(A_SPEAK, listen(false)))
            n.actions.forEach { a ->
                val pi = when (a) {
                    A_SPEAK -> activity(a, listen(false))
                    A_FLOOR -> activity(a, listen(true))
                    A_NOT -> broadcast(a, ACT_NOT)
                    else -> broadcast(a, ACT_OFF)
                }
                b.addAction(Notification.Action.Builder(null, a, pi).build())
            }
            ctx.getSystemService(NotificationManager::class.java).notify(id(n.carId), b.build())
        } catch (e: RuntimeException) {
            Log.w("wp-exit", "notify failed: $e")
        }
    }
}

class ExitActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val carId = intent.getStringExtra("car_id") ?: return
        when (intent.action) {
            ExitNotifier.ACT_NOT -> ExitNotifier.notParked(context, carId)
            ExitNotifier.ACT_OFF -> ExitNotifier.turnOff(context, carId)
        }
    }
}
