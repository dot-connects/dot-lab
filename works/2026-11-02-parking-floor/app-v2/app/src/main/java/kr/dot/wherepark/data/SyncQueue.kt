package kr.dot.wherepark.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.atomic.AtomicLong
import kr.dot.wherepark.core.isMine
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import org.json.JSONArray
import org.json.JSONObject

sealed interface Op {
    val key: String
    val seq: Long
    val carId: String
    fun withSeq(s: Long): Op

    data class PutCar(val car: Car, override val seq: Long = 0) : Op {
        override val key get() = "car:${car.id}"
        override val carId get() = car.id
        override fun withSeq(s: Long) = copy(seq = s)
    }
    data class DelCar(override val carId: String, override val seq: Long = 0) : Op {
        override val key get() = "car:$carId"
        override fun withSeq(s: Long) = copy(seq = s)
    }
    data class Share(override val carId: String, val groupId: String, val on: Boolean, override val seq: Long = 0) : Op {
        override val key get() = "share:$carId:$groupId"
        override fun withSeq(s: Long) = copy(seq = s)
    }
    data class PutPark(val park: Park, override val seq: Long = 0) : Op {
        override val key get() = "park:${park.carId}"
        override val carId get() = park.carId
        override fun withSeq(s: Long) = copy(seq = s)
    }
    data class Undo(override val carId: String, val parkId: Long, override val seq: Long = 0) : Op {
        override val key get() = "undo:$carId:$parkId"
        override fun withSeq(s: Long) = copy(seq = s)
    }
    data class State(override val carId: String, val state: ParkState, val parkId: Long? = null, val atMs: Long? = null, override val seq: Long = 0) : Op {
        override val key get() = "state:$carId"
        override fun withSeq(s: Long) = copy(seq = s)
    }
    data class PutPhoto(override val carId: String, val parkId: Long, val file: String, override val seq: Long = 0) : Op {
        override val key get() = "photo:$carId:$parkId"
        override fun withSeq(s: Long) = copy(seq = s)
    }
}

/** Processing order: car, undo, park, state, photo; then seq. */
private fun Op.kind() = when (this) {
    is Op.PutCar, is Op.DelCar -> 0
    is Op.Share -> 1
    is Op.Undo -> 2
    is Op.PutPark -> 3
    is Op.State -> 4
    is Op.PutPhoto -> 5
}

private fun Op.toJson(): JSONObject = JSONObject().put("seq", seq).also {
    when (this) {
        is Op.PutCar -> it.put("t", "putCar").put("car", Wire.carJson(car, 0))
        is Op.DelCar -> it.put("t", "delCar").put("carId", carId)
        is Op.Share -> it.put("t", "share").put("carId", carId).put("groupId", groupId).put("on", on)
        is Op.PutPark -> it.put("t", "putPark").put("park", Wire.parkJson(park, true))
        is Op.Undo -> it.put("t", "undo").put("carId", carId).put("parkId", parkId)
        is Op.State -> it.put("t", "state").put("carId", carId).put("state", state.name).put("parkId", parkId).put("atMs", atMs)
        is Op.PutPhoto -> it.put("t", "photo").put("carId", carId).put("parkId", parkId).put("file", file)
    }
}

private fun opOf(j: JSONObject): Op {
    val s = j.getLong("seq")
    return when (j.getString("t")) {
        "putCar" -> Op.PutCar(Wire.car(j.getJSONObject("car")), s)
        "delCar" -> Op.DelCar(j.getString("carId"), s)
        "share" -> Op.Share(j.getString("carId"), j.getString("groupId"), j.getBoolean("on"), s)
        "putPark" -> Op.PutPark(Wire.park(j.getJSONObject("park")), s)
        "undo" -> Op.Undo(j.getString("carId"), j.getLong("parkId"), s)
        "state" -> Op.State(j.getString("carId"), ParkState.valueOf(j.getString("state")), j.longOrNull("parkId"), j.longOrNull("atMs"), s)
        else -> Op.PutPhoto(j.getString("carId"), j.getLong("parkId"), j.getString("file"), s)
    }
}

/**
 * The offline op queue. Every read and write holds synchronized(SyncQueue) (one lock for all stores).
 * ponytail: whole queue is re-parsed per call; fine for a handful of ops, switch to a DB if it ever grows.
 */
object SyncQueue {
    private const val KEY = "wp_queue"
    private val gen = AtomicLong()

    /** (key, seq) of the op the worker is sending right now, for diagnostics and tests. */
    @Volatile var inFlight: Pair<String, Long>? = null

    private fun read(ctx: Context): List<Op> = Prefs.arr(ctx, KEY).toList(::opOf)
    private fun write(ctx: Context, ops: List<Op>) {
        Prefs.put(ctx, KEY, JSONArray(ops.map { it.toJson() }))
        gen.incrementAndGet()
    }

    fun ops(ctx: Context): List<Op> = synchronized(this) { read(ctx).sortedWith(compareBy({ it.kind() }, { it.seq })) }

    fun generation(ctx: Context): Long = gen.get()

    /** No family and no pendingGroup (after a 401) = new records stay local only; with a pendingGroup ops wait for SyncWorker to create it. */
    fun add(ctx: Context, op: Op) = synchronized(this) {
        if (!GroupStore.joined(ctx)) return@synchronized
        if ((op is Op.PutCar || op is Op.DelCar) && (if (op is Op.PutCar) op.car else CarStore.cars(ctx).firstOrNull { it.id == op.carId })?.isMine(GroupStore.me(ctx)?.id) == false) {
            android.util.Log.w("wp-sync", "ignored non-owner car operation")
            return@synchronized
        }
        val list = read(ctx).toMutableList()
        if (op !is Op.PutCar && op !is Op.DelCar && list.any { it is Op.DelCar && it.carId == op.carId }) return@synchronized
        var o = op
        when (op) {
            is Op.DelCar -> list.removeAll { it.carId == op.carId && it !is Op.PutCar && it !is Op.DelCar }
            is Op.PutPark -> list.removeAll { it.key == "state:${op.carId}" }
            is Op.State -> list.firstOrNull { it is Op.PutPark && it.carId == op.carId && (op.parkId == null || op.parkId == it.park.id) }?.let { pending ->
                o = (pending as Op.PutPark).let { it.copy(park = it.park.copy(state = op.state, drivingBy = null, leftAt = op.atMs)) }
            }
            else -> {}
        }
        val seq = Prefs.sp(ctx).getLong("wp_seq", 0) + 1
        Prefs.sp(ctx).edit().putLong("wp_seq", seq).apply()
        list.removeAll { it.key == o.key }
        list.add(o.withSeq(seq))
        write(ctx, list)
    }

    /** Drops [key] only if its current op still has [seq] (a newer op means the send is stale). */
    fun drop(ctx: Context, key: String, seq: Long): Boolean = synchronized(this) {
        val list = read(ctx)
        if (list.none { it.key == key && it.seq == seq }) return@synchronized false
        write(ctx, list.filter { it.key != key })
        true
    }

    internal fun dropKey(ctx: Context, key: String) = synchronized(this) {
        val list = read(ctx)
        if (list.any { it.key == key }) write(ctx, list.filter { it.key != key })
    }

    /** Runs [block] under the lock only while [op] is still the queue's op for its key. */
    fun <T> ifCurrent(ctx: Context, op: Op, block: () -> T): T? = synchronized(this) {
        if (read(ctx).any { it.key == op.key && it.seq == op.seq }) block() else null
    }

    internal fun dropCar(ctx: Context, carId: String) = synchronized(this) { write(ctx, read(ctx).filterNot { it.carId == carId }) }

    internal fun clear(ctx: Context) = synchronized(this) { write(ctx, emptyList()) }

    internal fun raw(ctx: Context): List<Op> = synchronized(this) { read(ctx) }

    /** Join rollback: put back what was removed, unless a newer op took the key meanwhile. */
    internal fun restoreMissing(ctx: Context, ops: List<Op>) = synchronized(this) {
        val list = read(ctx).toMutableList()
        ops.filter { b -> list.none { it.key == b.key } }.forEach { list.add(it) }
        write(ctx, list)
    }

    fun kick(ctx: Context) {
        try {
            WorkManager.getInstance(ctx).enqueueUniqueWork(
                "wp-sync", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build(),
            )
        } catch (_: IllegalStateException) {
            // WorkManager not initialised (plain unit tests)
        }
    }
}
