package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.core.Car
import org.json.JSONArray
import org.json.JSONObject

/** Credentials (Bearer memberId.secret), member list, pendingGroup (SyncWorker creates it), join carry-over, "removed" notice flag. */
object FamilyStore {
    private const val FAM = "wp_family"
    private const val PENDING = "wp_pending"
    private const val CARRY = "wp_carry"
    private const val REMOVED = "wp_removed"

    fun joined(ctx: Context): Boolean = auth(ctx) != null
    fun auth(ctx: Context): String? =
        Prefs.obj(ctx, FAM).let { f -> if (f.isNull("memberId")) null else "${f.getString("memberId")}.${f.getString("secret")}" }
    fun members(ctx: Context): List<Member> = Wire.members(Prefs.obj(ctx, FAM).optJSONArray("members"))
    fun me(ctx: Context): Member? = members(ctx).firstOrNull { it.me }
    fun groupName(ctx: Context): String? = Prefs.obj(ctx, FAM).str("groupName")

    fun save(ctx: Context, memberId: String, secret: String, groupName: String, members: List<Member>) = synchronized(SyncQueue) {
        Prefs.put(ctx, FAM, JSONObject().put("memberId", memberId).put("secret", secret).put("groupName", groupName)
            .put("members", JSONArray(members.map(Wire::memberJson))))
        Prefs.put(ctx, REMOVED, null)
    }

    fun setState(ctx: Context, groupName: String, members: List<Member>) = synchronized(SyncQueue) {
        val f = Prefs.obj(ctx, FAM)
        if (f.isNull("memberId")) return@synchronized
        Prefs.put(ctx, FAM, f.put("groupName", groupName).put("members", JSONArray(members.map(Wire::memberJson))))
    }

    fun clear(ctx: Context) = Prefs.put(ctx, FAM, null)

    fun pendingGroup(ctx: Context): Pair<String, String>? =
        Prefs.obj(ctx, PENDING).let { if (it.has("name")) it.getString("name") to it.getString("groupName") else null }

    fun setPendingGroup(ctx: Context, name: String, groupName: String) = synchronized(SyncQueue) {
        Prefs.put(ctx, PENDING, JSONObject().put("name", name).put("groupName", groupName))
    }

    fun clearPendingGroup(ctx: Context) = Prefs.put(ctx, PENDING, null)

    /** The "you left the family" band shows while this is true. */
    fun removed(ctx: Context): Boolean = Prefs.sp(ctx).getString(REMOVED, null) != null
    fun dismissRemoved(ctx: Context) = Prefs.put(ctx, REMOVED, null)

    /** 401 handling: only when [auth] is still the stored one. Local cars, parks and CarLinkStore stay. */
    fun dropIfSame(ctx: Context, auth: String?): Boolean = synchronized(SyncQueue) {
        if (auth == null || auth(ctx) != auth) return@synchronized false
        clear(ctx); clearPendingGroup(ctx); SyncQueue.clear(ctx)
        Prefs.put(ctx, REMOVED, "1")
        true
    }

    /** Voluntary leave: same wipe, no "removed" band. */
    fun leave(ctx: Context) = synchronized(SyncQueue) { clear(ctx); clearPendingGroup(ctx); SyncQueue.clear(ctx) }

    /** Join step 5: cars of a family that was only this phone, offered to the new family. */
    fun carryOver(ctx: Context): List<Car> = Prefs.arr(ctx, CARRY).toList(Wire::car)
    fun setCarryOver(ctx: Context, cars: List<Car>) = Prefs.put(ctx, CARRY, if (cars.isEmpty()) null else JSONArray(cars.map { Wire.carJson(it, 0) }))
    fun declineCarryOver(ctx: Context) = Prefs.put(ctx, CARRY, null)

    /** "Yes": same ids go up as PutCar (server key is (group, id)); records are not carried. */
    fun acceptCarryOver(ctx: Context) {
        synchronized(SyncQueue) {
            carryOver(ctx).forEach { CarStore.upsert(ctx, it); SyncQueue.add(ctx, Op.PutCar(it)) }
            declineCarryOver(ctx)
        }
        SyncQueue.kick(ctx)
    }
}
