package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.core.isMine
import org.json.JSONArray
import org.json.JSONObject

object GroupStore {
    private const val KEY = "wp_family"
    fun auth(ctx: Context): String? = Prefs.obj(ctx, KEY).let { f ->
        f.str("memberId")?.let { id -> f.str("secret")?.let { "$id.$it" } }
    }
    fun joined(ctx: Context) = auth(ctx) != null
    fun me(ctx: Context): Member? = Prefs.obj(ctx, KEY).let { f ->
        f.optJSONObject("me")?.let(Wire::member) ?: Wire.members(f.optJSONArray("members")).firstOrNull { it.me }
    }
    fun groups(ctx: Context): List<Group> = Prefs.obj(ctx, KEY).optJSONArray("groups")?.toList(Wire::group).orEmpty()
    fun group(ctx: Context, id: String) = groups(ctx).firstOrNull { it.id == id }
    fun save(ctx: Context, deviceId: String, secret: String, me: Member?, groups: List<Group>) = synchronized(SyncQueue) {
        Prefs.put(ctx, KEY, JSONObject().put("memberId", deviceId).put("secret", secret))
        setState(ctx, me, groups)
        dismissRemoved(ctx)
    }
    fun setState(ctx: Context, me: Member?, groups: List<Group>) = synchronized(SyncQueue) {
        if (joined(ctx)) Prefs.put(ctx, KEY, Prefs.obj(ctx, KEY)
            .put("me", me?.let(Wire::memberJson) ?: JSONObject.NULL).put("groups", JSONArray(groups.map(Wire::groupJson))))
    }
    fun clear(ctx: Context) = Prefs.put(ctx, KEY, null)
    fun removed(ctx: Context) = Prefs.sp(ctx).getString("wp_removed", null) != null
    fun dismissRemoved(ctx: Context) = Prefs.put(ctx, "wp_removed", null)

    fun toSolo(ctx: Context) = synchronized(SyncQueue) {
        val meId = me(ctx)?.id ?: auth(ctx)?.substringBefore('.')
        val cars = CarStore.cars(ctx)
        cars.filterNot { it.isMine(meId) }.forEach { CarStore.remove(ctx, it.id); ParkStore.clearCar(ctx, it.id) }
        cars.filter { it.isMine(meId) }.forEach { CarStore.upsert(ctx, it.copy(ownerId = null, ownerName = null, groups = emptyList())) }
        clear(ctx)
        SyncQueue.clear(ctx)
    }
    fun dropIfSame(ctx: Context, auth: String?): Boolean = synchronized(SyncQueue) {
        if (auth == null || auth(ctx) != auth) return@synchronized false
        toSolo(ctx)
        Prefs.put(ctx, "wp_removed", "1")
        true
    }
    fun leave(ctx: Context) = synchronized(SyncQueue) { toSolo(ctx); dismissRemoved(ctx) }
}
