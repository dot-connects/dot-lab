package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.core.isMine
import org.json.JSONArray

/** One-shot v2 -> v3 prefs upgrade (`wp_schema` = 3). */
object Upgrade {
    private const val LEGACY = "wp_legacy_share"

    fun run(ctx: Context) = synchronized(SyncQueue) {
        val sp = Prefs.sp(ctx)
        if (sp.getInt("wp_schema", 0) >= 3) return@synchronized
        val ids = SyncQueue.raw(ctx).filterIsInstance<Op.PutCar>().map { it.car.id }
        sp.edit().remove("wp_pending").remove("wp_carry").remove("wp_create_error").putInt("wp_schema", 3).apply()
        if (ids.isNotEmpty()) Prefs.put(ctx, LEGACY, JSONArray(ids))
    }

    /** After a pull that left exactly one group (the migrated family): share the cars that were queued under v2. */
    fun afterPull(ctx: Context, st: ApiState) = synchronized(SyncQueue) {
        val ids = Prefs.arr(ctx, LEGACY).let { a -> (0 until a.length()).map(a::getString) }
        if (ids.isEmpty() || st.groups.size != 1) return@synchronized
        val meId = st.me?.id
        val group = st.groups.single().id
        val onServer = st.cars.map { it.id }.toSet()
        st.cars.filter { it.id in ids && it.isMine(meId) }.forEach { SyncQueue.add(ctx, Op.Share(it.id, group, true)) }
        // a car whose PutCar is still queued is not on the server yet: keep its id for the next pull
        val local = CarStore.cars(ctx).map { it.id }.toSet()
        val left = ids.filter { it !in onServer && it in local }
        Prefs.put(ctx, LEGACY, if (left.isEmpty()) null else JSONArray(left))
    }
}
