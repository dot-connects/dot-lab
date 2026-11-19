package kr.dot.wherepark.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result = if (SyncEngine.run(applicationContext)) Result.success() else Result.retry()
}

/** Contracts section 3 in one place; SyncWorker is a thin shell so tests can call [run] with a fake Api.transport. */
object SyncEngine {
    @Volatile var notify: (Context, String) -> Unit = ::postNotice

    /** true = done (also when there is nothing to do), false = retry later. The only place a family is created (from pendingGroup). */
    fun run(ctx: Context): Boolean {
        if (!FamilyStore.joined(ctx)) {
            val pg = FamilyStore.pendingGroup(ctx) ?: return true
            val r = Api.createGroup(
                ctx, pg.first, pg.second,
                guard = { !FamilyStore.joined(ctx) && FamilyStore.pendingGroup(ctx) == pg },
                onSaved = {
                    CarStore.cars(ctx).forEach { c ->
                        SyncQueue.add(ctx, Op.PutCar(c))
                        ParkStore.latest(ctx, c.id)?.let { SyncQueue.add(ctx, Op.PutPark(it)) }
                    }
                },
            )
            if (r.isSuccess) kr.dot.wherepark.family.JoinFlow.startPush(ctx)  // token upload in the background, like after a join
            val e = r.exceptionOrNull()
            if (e is ApiException && e.status in 400..499 && e.status != 429) {
                Log.w("wp-sync", "createGroup rejected: $e")
                synchronized(SyncQueue) {
                    if (FamilyStore.pendingGroup(ctx) == pg) { FamilyStore.setGroupCreateError(ctx, e.error.ifEmpty { "${e.status}" }); FamilyStore.clearPendingGroup(ctx) }
                }
                return true
            }
            if (e != null && e !== Discarded) return false
        }
        var guard = 0
        while (guard++ < 1000) {
            val op = SyncQueue.ops(ctx).firstOrNull() ?: break
            val auth = FamilyStore.auth(ctx) ?: break
            // pick and mark under one lock: an undo that lands first replaces the op and we skip this one
            if (op is Op.PutPark && SyncQueue.ifCurrent(ctx, op) { ParkStore.markSendStarted(ctx, op.park.id); true } == null) continue
            SyncQueue.inFlight = op.key to op.seq
            val (status, body) = try { Api.exec(ctx, op, auth) } finally { SyncQueue.inFlight = null }
            if (!settle(ctx, op, auth, status, body)) return false
        }
        if (!FamilyStore.joined(ctx)) return true
        pull(ctx)
        if (SyncQueue.ops(ctx).isNotEmpty()) SyncQueue.kick(ctx)  // something was added while we ran
        return true
    }

    /** GET state, merge unless the queue changed meanwhile. */
    fun pull(ctx: Context): Boolean {
        val gen = SyncQueue.generation(ctx)
        val st = Api.state(ctx).getOrNull() ?: return false
        return Merge.state(ctx, st, gen).also { if (it) WidgetRefresh.request(ctx) }
    }

    /** Applies one response. Drops and local writes happen only while [op] is still the queue's op for its key. false = retry. */
    private fun settle(ctx: Context, op: Op, auth: String, status: Int, body: org.json.JSONObject?): Boolean {
        var notice: String? = null
        var refresh = false
        when {
            status in 200..299 -> SyncQueue.ifCurrent(ctx, op) { SyncQueue.drop(ctx, op.key, op.seq) }
            status == 409 -> SyncQueue.ifCurrent(ctx, op) {
                SyncQueue.drop(ctx, op.key, op.seq)
                if (op is Op.PutCar && body?.str("error") == "full") {
                    CarStore.remove(ctx, op.car.id); ParkStore.clearCar(ctx, op.car.id)
                    notice = "차는 4대까지예요"
                } else {
                    body?.optJSONObject("park")?.let { ParkStore.setFromServer(ctx, op.carId, Wire.park(it, op.carId)); refresh = true }
                }
            }
            status == 401 -> FamilyStore.dropIfSame(ctx, auth)
            status == 400 || status == 404 || status == 413 -> {
                Log.w("wp-sync", "dropped ${op.key}: $status")
                SyncQueue.ifCurrent(ctx, op) {
                    SyncQueue.drop(ctx, op.key, op.seq)
                    // the server never stored this car: keep it (and its history) on this phone, say so
                    if (op is Op.PutCar && CarStore.cars(ctx).any { it.id == op.car.id }) {
                        CarStore.setLocalOnly(ctx, CarStore.localOnly(ctx) + op.car.id)
                        notice = "가족과 나누지 못한 차가 있어요 (${op.car.name}). 이 폰에만 남아 있어요"
                    }
                }
            }
            else -> return false
        }
        notice?.let { notify(ctx, it) }
        if (refresh) WidgetRefresh.request(ctx)
        return true
    }

    private fun postNotice(ctx: Context, text: String) {
        try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("sync", "동기화", NotificationManager.IMPORTANCE_DEFAULT))
            nm.notify(
                text.hashCode(),
                android.app.Notification.Builder(ctx, "sync").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("어디댔어").setContentText(text).build(),
            )
        } catch (e: RuntimeException) {
            Log.w("wp-sync", "notice failed: $e")
        }
    }
}
