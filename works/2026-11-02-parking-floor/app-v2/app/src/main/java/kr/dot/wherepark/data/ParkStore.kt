package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import org.json.JSONArray

/** Per car: newest-first history (max 50) of this phone's view. latest = history[0], prev = history[1]. */
object ParkStore {
    private const val KEY = "wp_parks"
    private const val SENT = "wp_sent"
    private const val MAX = 50

    fun history(ctx: Context, carId: String): List<Park> =
        Prefs.obj(ctx, KEY).optJSONArray(carId)?.toList { Wire.park(it, carId) }.orEmpty()

    fun latest(ctx: Context, carId: String): Park? = history(ctx, carId).firstOrNull()
    fun prev(ctx: Context, carId: String): Park? = history(ctx, carId).getOrNull(1)
    fun lastUsedCarId(ctx: Context): String? = Prefs.sp(ctx).getString("wp_last_car", null)
    fun carIds(ctx: Context): List<String> = Prefs.obj(ctx, KEY).keys().asSequence().toList()

    private fun write(ctx: Context, carId: String, h: List<Park>) {
        val o = Prefs.obj(ctx, KEY)
        if (h.isEmpty()) o.remove(carId) else o.put(carId, JSONArray(h.take(MAX).map { Wire.parkJson(it, true) }))
        Prefs.put(ctx, KEY, o)
    }

    /** Same id as the latest = a fix, replaces it; otherwise a new record on top. */
    fun save(ctx: Context, p: Park) = synchronized(SyncQueue) {
        val h = history(ctx, p.carId).toMutableList()
        if (h.firstOrNull()?.id == p.id) h[0] = p else h.add(0, p)
        write(ctx, p.carId, h)
        if (p.state != ParkState.DRIVING) kr.dot.wherepark.car.ExitNotifier.clearDriving(ctx, p.carId)
        Prefs.put(ctx, "wp_last_car", p.carId)
    }

    /** Server value is canon: an id already in the history rolls the newer entries off, a new id goes on top. */
    fun setFromServer(ctx: Context, carId: String, p: Park?) = synchronized(SyncQueue) {
        val h = history(ctx, carId)
        if (p == null) return@synchronized write(ctx, carId, emptyList())
        val i = h.indexOfFirst { it.id == p.id }
        val keep = if (i >= 0) p.copy(raw = h[i].raw) else p
        write(ctx, carId, listOf(keep) + (if (i >= 0) h.drop(i + 1) else h))
        if (keep.state != ParkState.DRIVING) kr.dot.wherepark.car.ExitNotifier.clearDriving(ctx, carId)
    }

    fun clearCar(ctx: Context, carId: String) = synchronized(SyncQueue) { write(ctx, carId, emptyList()) }

    /** Local undo plus the matching queue change (contracts section 3, "undo"). Returns the restored park or null. */
    fun undo(ctx: Context, carId: String): Park? = synchronized(SyncQueue) {
        val h = history(ctx, carId)
        val undone = h.firstOrNull() ?: return@synchronized null
        val prev = h.getOrNull(1)
        write(ctx, carId, h.drop(1))
        SyncQueue.dropKey(ctx, "park:$carId")
        if (!sendStarted(ctx, undone.id)) {
            if (prev != null) SyncQueue.add(ctx, Op.PutPark(prev))
        } else {
            SyncQueue.add(ctx, Op.Undo(carId, undone.id))
        }
        prev
    }

    /** Called right before a PutPark goes out; committed synchronously so a restart still knows. */
    fun markSendStarted(ctx: Context, parkId: Long) = synchronized(SyncQueue) {
        val a = Prefs.arr(ctx, SENT)
        if (!sentIn(a, parkId)) {
            val t = JSONArray()
            for (i in maxOf(0, a.length() - 199) until a.length()) t.put(a.get(i))
            t.put(parkId)
            Prefs.put(ctx, SENT, t, sync = true)
        }
    }

    fun sendStarted(ctx: Context, parkId: Long): Boolean = sentIn(Prefs.arr(ctx, SENT), parkId)

    private fun sentIn(a: JSONArray, id: Long) = (0 until a.length()).any { a.getLong(it) == id }
}
