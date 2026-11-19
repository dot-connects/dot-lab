package kr.dot.wherepark.data

import android.content.Context
import android.content.SharedPreferences
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.core.floorOfWire
import kr.dot.wherepark.core.wire
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class Member(val id: String, val name: String, val me: Boolean, val owner: Boolean, val lastSeenMs: Long?)
data class Invite(val code: String, val expiresAt: Long, val link: String)
data class ApiState(val groupName: String, val members: List<Member>, val cars: List<Car>, val parks: Map<String, Park?>)

/** One SharedPreferences file; every value is a JSON string under a wp_* key. Writers hold synchronized(SyncQueue). */
object Prefs {
    fun sp(ctx: Context): SharedPreferences = ctx.applicationContext.getSharedPreferences("wherepark", Context.MODE_PRIVATE)

    fun obj(ctx: Context, key: String): JSONObject =
        try { JSONObject(sp(ctx).getString(key, null) ?: "{}") } catch (_: JSONException) { JSONObject() }

    fun arr(ctx: Context, key: String): JSONArray =
        try { JSONArray(sp(ctx).getString(key, null) ?: "[]") } catch (_: JSONException) { JSONArray() }

    fun put(ctx: Context, key: String, value: Any?, sync: Boolean = false) {
        val e = sp(ctx).edit()
        if (value == null) e.remove(key) else e.putString(key, value.toString())
        if (sync) e.commit() else e.apply()
    }
}

internal fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k)
internal fun JSONObject.intOrNull(k: String): Int? = if (isNull(k)) null else getInt(k)
internal fun JSONObject.longOrNull(k: String): Long? = if (isNull(k)) null else getLong(k)
internal fun <T> JSONArray.toList(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
private fun nz(v: Any?): Any = v ?: JSONObject.NULL

/** Wire (contracts section 1) and local JSON for the shared models. */
object Wire {
    /** v1 groups have no name (server sends null). */
    const val NO_GROUP_NAME = "우리 가족"

    fun carJson(c: Car, updated: Long = System.currentTimeMillis()): JSONObject = JSONObject()
        .put("id", c.id).put("name", c.name).put("color", c.color).put("plate", nz(c.plate)).put("order", c.order).put("updated", updated)

    fun car(j: JSONObject): Car = Car(j.getString("id"), j.getString("name"), j.getInt("color"), j.str("plate"), j.optInt("order", 0))

    /** [local] adds the fields the server never receives (raw, by, byId, drivingBy). */
    fun parkJson(p: Park, local: Boolean): JSONObject {
        val j = JSONObject().put("id", p.id).put("carId", p.carId).put("atMs", p.atMs)
            .put("floor", nz(p.spot.floor?.wire())).put("zone", nz(p.spot.zone)).put("number", nz(p.spot.number))
            .put("pillar", nz(p.spot.pillar)).put("memo", nz(p.spot.memo)).put("state", p.state.name).put("photo", p.photo)
        if (local) j.put("raw", nz(p.raw)).put("by", p.byName).put("byId", p.byId).put("drivingBy", nz(p.drivingBy))
        return j
    }

    fun park(j: JSONObject, carId: String? = null): Park = Park(
        id = j.getLong("id"), carId = j.str("carId") ?: carId ?: "",
        spot = Spot(floorOfWire(j.str("floor")), j.str("zone"), j.intOrNull("number"), j.str("pillar"), j.str("memo")),
        atMs = j.optLong("atMs", j.getLong("id")), byId = j.str("byId") ?: "", byName = j.str("by") ?: "",
        raw = j.str("raw"), photo = j.optBoolean("photo", false),
        state = runCatching { ParkState.valueOf(j.optString("state")) }.getOrDefault(ParkState.PARKED), drivingBy = j.str("drivingBy"),
    )

    fun member(j: JSONObject) = Member(j.getString("id"), j.getString("name"), j.optBoolean("me"), j.optBoolean("owner"), j.longOrNull("lastSeen"))

    fun memberJson(m: Member): JSONObject = JSONObject().put("id", m.id).put("name", m.name).put("me", m.me).put("owner", m.owner).put("lastSeen", nz(m.lastSeenMs))

    fun members(a: JSONArray?): List<Member> = a?.toList(::member).orEmpty()

    /** `parks` is absent in group/join responses. */
    fun state(j: JSONObject): ApiState {
        val parks = HashMap<String, Park?>()
        j.optJSONObject("parks")?.let { o -> o.keys().forEach { k -> parks[k] = o.optJSONObject(k)?.let { p -> park(p, k) } } }
        return ApiState(j.str("groupName") ?: Wire.NO_GROUP_NAME, members(j.optJSONArray("members")), j.optJSONArray("cars")?.toList(::car).orEmpty(), parks)
    }
}
