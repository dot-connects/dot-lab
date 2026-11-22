package kr.dot.wherepark.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Spot
import org.json.JSONObject

/** Scripted Api.transport; the default answers 200 {} and fails GET /v3/state so a pull never rewrites local data. */
class FakeNet : Transport {
    companion object {
        val CAR_ID = Regex("^c_[0-9a-f]{8}$")
        val REQ_SECRET = Regex("^[0-9a-f]{32}$")
        val REQ_HASH = Regex("^[0-9a-f]{64}$")
        fun sha256(s: String): String = java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        private val CAR_ROUTE = Regex("^/v3/cars/([^/?]*)")
    }

    val calls = mutableListOf<String>()
    val auths = mutableListOf<String?>()
    var handler: (method: String, path: String, auth: String?, body: String?) -> Pair<Int, String?> = { m, p, _, _ ->
        if (m == "GET" && p == "/v3/state") 500 to null else 200 to "{}"
    }

    override fun send(method: String, path: String, auth: String?, body: ByteArray?, contentType: String?): Pair<Int, String?> {
        calls += "$method $path"; auths += auth
        // like the Worker: car routes match only ^c_[0-9a-f]{8}$, anything else is 404 "route"
        CAR_ROUTE.find(path)?.let { if (!CAR_ID.matches(it.groupValues[1])) return 404 to """{"error":"route"}""" }
        requestRoute(method, path, auth, body?.toString(Charsets.UTF_8))?.let { return it }
        return handler(method, path, auth, body?.toString(Charsets.UTF_8)).also { (s, _) ->
            // a successful join drops the requests that carried its code
            if (method == "POST" && path == "/v3/join" && s in 200..299) {
                val used = JSONObject(body!!.toString(Charsets.UTF_8)).optString("code")
                requests.values.removeAll { it == used }
            }
        }
    }

    /** Join requests held like the Worker's requests table: sha256(secret) (the link's hash) -> code. Tests may seed it. */
    val requests = mutableMapOf<String, String>()
    var nextCode = "K7QM-28XP-4HDA"
    /** Who accepted each request (poll's byName); PUT records [acceptor]. */
    val requestBy = mutableMapOf<String, String>()
    var acceptor = "엄마"
    var cancels = 0

    /**
     * Like the Worker: poll = POST /v3/requests/poll {secret ^[0-9a-f]{32}$} (else 400), looked up by sha256(secret), unknown 404;
     * cancel = POST /v3/requests/cancel {secret} (same check, always 200); PUT /v3/requests {req: 64-hex hash} needs auth, the
     * server makes the code ([nextCode]), a second accept 409. There is no GET-by-id route.
     */
    private fun requestRoute(method: String, path: String, auth: String?, body: String?): Pair<Int, String?>? {
        if (!path.startsWith("/v3/requests")) return null
        val j = runCatching { JSONObject(body ?: "") }.getOrNull()
        if (method == "POST" && path == "/v3/requests/poll") {
            val secret = j?.opt("secret") as? String
            if (secret == null || !REQ_SECRET.matches(secret)) return 400 to """{"error":"req"}"""
            val h = sha256(secret)
            return requests[h]?.let { 200 to JSONObject().put("code", it).put("byName", requestBy[h] ?: JSONObject.NULL).toString() } ?: (404 to """{"error":"req"}""")
        }
        if (method == "POST" && path == "/v3/requests/cancel") {
            val secret = j?.opt("secret") as? String
            if (secret == null || !REQ_SECRET.matches(secret)) return 400 to """{"error":"req"}"""
            requests.remove(sha256(secret)); cancels++
            return 200 to """{"ok":true}"""
        }
        if (method != "PUT" || path != "/v3/requests") return (if (auth == null) 401 else 404) to """{"error":"route"}"""
        if (auth == null) return 401 to """{"error":"auth"}"""
        val req = j?.optString("req").orEmpty()
        if (!REQ_HASH.matches(req)) return 400 to """{"error":"req"}"""
        if (req in requests) return 409 to """{"error":"taken"}"""
        requests[req] = nextCode; requestBy[req] = acceptor
        return 200 to """{"ok":true}"""
    }
}

object Kit {
    fun ctx(): Context = ApplicationProvider.getApplicationContext()
    val me = Member("m1", "나", true, true, null)

    /** Fresh prefs, a joined family (m1.s1) and a fake network. */
    fun reset(net: FakeNet, notices: MutableList<String>, joined: Boolean = true): Context {
        val c = ctx()
        Prefs.sp(c).edit().clear().commit()
        if (joined) GroupStore.save(c, "m1", "s1", me, listOf(Group("g1", "우리", true, listOf(me))))
        Api.transport = net
        SyncEngine.notify = { _, t -> notices += t }
        kr.dot.wherepark.family.JoinFlow.startPush = {}
        return c
    }

    fun saveGroup(c: Context, id: String, secret: String, groupName: String, members: List<Member>) =
        GroupStore.save(c, id, secret, members.firstOrNull { it.me }, listOf(Group("g1", groupName, true, members)))

    fun car(id: String, order: Int = 0) = Car(id, "차 $id", -1, null, order)

    fun park(carId: String, id: Long, atMs: Long = id, state: ParkState = ParkState.PARKED, floor: Int = 2) =
        Park(id, carId, Spot(Floor.Under(floor), "C", 37, null, null), atMs, "m1", "나", "raw $id", false, state)

    fun serverPark(p: Park, by: String = "엄마"): String = Wire.parkJson(p, false).put("by", by).put("byId", "m2").toString()

    /** Response of POST /v3/groups and /v3/join for a new device. */
    fun groupJson(memberId: String = "m9", secret: String = "s9", groupName: String = "우리집"): String {
        val me = JSONObject().put("id", memberId).put("name", "나")
        return JSONObject().put("groupId", "g1").put("deviceId", memberId).put("secret", secret).put("state", JSONObject().put("me", me)
            .put("groups", org.json.JSONArray().put(JSONObject().put("id", "g1").put("name", groupName).put("owner", true)
                .put("members", org.json.JSONArray().put(JSONObject().put("id", memberId).put("name", "나").put("me", true).put("owner", true).put("lastSeen", JSONObject.NULL)))))
            .put("cars", org.json.JSONArray()).put("parks", JSONObject())).toString()
    }
}
