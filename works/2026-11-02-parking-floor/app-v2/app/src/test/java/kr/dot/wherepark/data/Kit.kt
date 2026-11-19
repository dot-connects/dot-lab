package kr.dot.wherepark.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Spot
import org.json.JSONObject

/** Scripted Api.transport; the default answers 200 {} and fails GET /v2/state so a pull never rewrites local data. */
class FakeNet : Transport {
    companion object {
        val CAR_ID = Regex("^c_[0-9a-f]{8}$")
        private val CAR_ROUTE = Regex("^/v2/cars/([^/?]*)")
    }

    val calls = mutableListOf<String>()
    val auths = mutableListOf<String?>()
    var handler: (method: String, path: String, auth: String?, body: String?) -> Pair<Int, String?> = { m, p, _, _ ->
        if (m == "GET" && p == "/v2/state") 500 to null else 200 to "{}"
    }

    override fun send(method: String, path: String, auth: String?, body: ByteArray?, contentType: String?): Pair<Int, String?> {
        calls += "$method $path"; auths += auth
        // like the Worker: car routes match only ^c_[0-9a-f]{8}$, anything else is 404 "route"
        CAR_ROUTE.find(path)?.let { if (!CAR_ID.matches(it.groupValues[1])) return 404 to """{"error":"route"}""" }
        return handler(method, path, auth, body?.toString(Charsets.UTF_8))
    }
}

object Kit {
    fun ctx(): Context = ApplicationProvider.getApplicationContext()
    val me = Member("m1", "나", true, true, null)

    /** Fresh prefs, a joined family (m1.s1) and a fake network. */
    fun reset(net: FakeNet, notices: MutableList<String>, joined: Boolean = true): Context {
        val c = ctx()
        Prefs.sp(c).edit().clear().commit()
        if (joined) FamilyStore.save(c, "m1", "s1", "우리", listOf(me))
        Api.transport = net
        SyncEngine.notify = { _, t -> notices += t }
        kr.dot.wherepark.family.JoinFlow.startPush = {}
        return c
    }

    fun car(id: String, order: Int = 0) = Car(id, "차 $id", -1, null, order)

    fun park(carId: String, id: Long, atMs: Long = id, state: ParkState = ParkState.PARKED, floor: Int = 2) =
        Park(id, carId, Spot(Floor.Under(floor), "C", 37, null, null), atMs, "m1", "나", "raw $id", false, state)

    fun serverPark(p: Park, by: String = "엄마"): String = Wire.parkJson(p, false).put("by", by).put("byId", "m2").toString()

    fun groupJson(memberId: String = "m9", secret: String = "s9", groupName: String = "우리집"): String = JSONObject()
        .put("groupId", "g1").put("memberId", memberId).put("secret", secret).put("groupName", groupName)
        .put("members", org.json.JSONArray().put(JSONObject().put("id", memberId).put("name", "나").put("me", true).put("owner", true).put("lastSeen", JSONObject.NULL)))
        .put("cars", org.json.JSONArray()).toString()
}
