package kr.dot.wherepark.data

import kr.dot.wherepark.core.*
import org.json.JSONArray
import org.json.JSONObject

/** Stateful v3 transport: authorization and visibility are evaluated for every request. */
class FakeWorker : Transport {
    private data class Device(val id: String, val secret: String, val name: String)
    private data class Room(val id: String, var name: String, val members: LinkedHashSet<String>)
    private val devices = linkedMapOf<String, Device>()
    private val groups = linkedMapOf<String, Room>()
    private val cars = linkedMapOf<String, Car>()
    private val links = mutableMapOf<String, MutableSet<String>>()
    private val parks = mutableMapOf<String, Park>()
    private val previous = mutableMapOf<String, Park?>()
    private val photos = mutableMapOf<String, ByteArray>()
    private val invites = mutableMapOf<String, String>()
    var down: Int? = null
    val calls = mutableListOf<String>()
    private var serial = 0
    fun device(name: String): Pair<String, String> {
        val id = "%016x".format(++serial)
        val secret = "%064x".format(serial)
        devices[id] = Device(id, secret, name)
        return "$id.$secret" to id
    }
    fun group(auth: String, name: String): String {
        val id = "%016x".format(++serial)
        groups[id] = Room(id, name, linkedSetOf(auth.substringBefore('.')))
        return id
    }
    fun addMember(groupId: String, auth: String) { groups.getValue(groupId).members.add(auth.substringBefore('.')) }
    fun car(auth: String, car: Car) { cars[car.id] = car.copy(ownerId = auth.substringBefore('.'), ownerName = devices[auth.substringBefore('.')]?.name) }
    fun share(carId: String, groupId: String) { links.getOrPut(carId) { linkedSetOf() }.add(groupId) }
    fun exclude(carId: String, groupId: String) { links[carId]?.remove(groupId) }
    fun parkAs(auth: String, p: Park) { parks[p.carId] = p.copy(byId = auth.substringBefore('.'), byName = devices.getValue(auth.substringBefore('.')).name) }
    private fun visible(car: Car, id: String) = car.ownerId == id || links[car.id].orEmpty().any { id in groups[it]?.members.orEmpty() }
    private fun present(p: Park, id: String): JSONObject {
        val known = groups.values.filter { id in it.members && it.id in links[p.carId].orEmpty() }.flatMap { it.members }.toSet() + id
        return Wire.parkJson(p.copy(byOther = p.byId !in known, byName = if (p.byId in known) p.byName else "", byId = if (p.byId in known) p.byId else "", drivingOther = p.drivingById != null && p.drivingById !in known, drivingBy = if (p.drivingById in known) p.drivingBy else null, drivingById = p.drivingById?.takeIf { it in known }), true)
    }
    private fun state(id: String): JSONObject {
        val mine = groups.values.filter { id in it.members }
        val cs = cars.values.filter { visible(it, id) }
        return JSONObject().put("me", JSONObject().put("id", id).put("name", devices.getValue(id).name))
            .put("groups", JSONArray(mine.map { g -> Wire.groupJson(Group(g.id, g.name, g.members.first() == id, g.members.map { m -> Member(m, devices.getValue(m).name, m == id, g.members.first() == m, 0) })) }))
            .put("cars", JSONArray(cs.map { c -> Wire.carJson(c.copy(groups = links[c.id].orEmpty().filter { c.ownerId == id || it in mine.map { g -> g.id } })) }))
            .put("parks", JSONObject().also { j -> cs.forEach { c -> j.put(c.id, parks[c.id]?.let { present(it, id) } ?: JSONObject.NULL) } })
    }
    override fun send(method: String, path: String, auth: String?, body: ByteArray?, contentType: String?): Pair<Int, String?> {
        calls += "$method $path"
        fun reply(status: Int = 200, j: JSONObject = JSONObject().put("ok", true)) = status to j.toString()
        fun fail(status: Int, error: String) = reply(status, JSONObject().put("error", error))
        down?.let { return fail(it, "down") }
        val b = body?.let { runCatching { JSONObject(it.toString(Charsets.UTF_8)) }.getOrNull() } ?: JSONObject()
        var id = auth?.substringBefore('.')
        if (auth != null && devices[id]?.let { "$id.${it.secret}" } != auth) return fail(401, "auth")
        if (method == "POST" && (path == "/v3/groups" || path == "/v3/join")) {
            if (id != null && groups.values.count { id in it.members } >= 3) return fail(409, "groups")
            var fresh: Pair<String, String>? = null
            if (id == null) {
                if (b.optString("name").isBlank()) return fail(400, "name")
                fresh = device(b.getString("name")); id = fresh.second
            }
            val gid = if (path.endsWith("join")) {
                val g = invites[b.optString("code")] ?: return fail(404, "code")
                if (id in groups.getValue(g).members) return fail(409, "member")
                if (groups.getValue(g).members.size >= 8) return fail(409, "full")
                groups.getValue(g).members.add(id!!); invites.entries.removeAll { it.value == g }; g
            } else {
                if (b.optString("groupName").isBlank() || b.optString("groupName").length > 40) return fail(400, "name")
                group(auth ?: fresh!!.first, b.getString("groupName"))
            }
            return reply(j = JSONObject().put("groupId", gid).put("state", state(id!!)).also { j -> fresh?.let { j.put("deviceId", it.second).put("secret", it.first.substringAfter('.')) } })
        }
        if (id == null || devices[id] == null) return fail(401, "auth")
        val me = id!!
        if (path == "/v3/state") return reply(j = state(me))
        if (path == "/v3/token") return reply()
        if (path == "/v3/me") { groups.values.forEach { it.members.remove(me) }; devices.remove(me); return reply() }
        val segments = path.substringBefore('?').split('/').filter { it.isNotEmpty() }
        if (segments.getOrNull(1) == "groups") {
            val gid = segments.getOrNull(2); val g = groups[gid] ?: return fail(404, "group")
            if (me !in g.members) return fail(404, "group")
            when (segments.getOrNull(3)) {
                "invites" -> { val code = "CODE-${++serial}"; invites[code] = g.id; return reply(j = JSONObject().put("code", code).put("expiresAt", Long.MAX_VALUE).put("link", "https://example.com/j#$code")) }
                "me" -> { g.members.remove(me); val gone = groups.values.none { me in it.members }; if (gone) devices.remove(me); return reply(j = JSONObject().put("device", if (gone) "gone" else "kept")) }
                "members" -> { if (g.members.first() != me) return fail(403, "owner"); g.members.remove(segments.getOrNull(4)); return reply() }
                else -> { if (g.members.first() != me) return fail(403, "owner"); g.name = b.optString("name"); return reply() }
            }
        }
        if (segments.getOrNull(1) != "cars") return fail(404, "route")
        val cid = segments.getOrNull(2) ?: return fail(404, "route")
        val c = cars[cid]
        val kind = segments.getOrNull(3)
        if (kind == null && method == "PUT") {
            if (c != null && !visible(c, me)) return fail(409, "id")
            if (c != null && c.ownerId != me) return fail(403, "owner")
            if (c == null && cars.values.count { it.ownerId == me } >= 4) return fail(409, "cars")
            car(auth!!, Wire.car(b.getJSONObject("car"))); return reply()
        }
        if (c == null || !visible(c, me)) return fail(404, "car")
        if (kind == null || kind == "groups") {
            if (c.ownerId != me) return fail(403, "owner")
            if (kind == null) { cars.remove(cid); parks.remove(cid); links.remove(cid); return reply() }
            val gid = segments.getOrNull(4) ?: return fail(404, "group")
            if (me !in groups[gid]?.members.orEmpty()) return fail(404, "group")
            if (method == "PUT") share(cid, gid) else exclude(cid, gid)
            return reply()
        }
        val current = parks[cid]
        fun parkReply(status: Int = 200, error: String? = null) = reply(status, JSONObject().put("park", parks[cid]?.let { present(it, me) } ?: JSONObject.NULL).also { if (error != null) it.put("error", error) })
        when (kind) {
            "park" -> {
                val p = Wire.park(b.getJSONObject("park"), cid)
                if (current != null && !ParkRules.accept(current, p)) return parkReply(409, "stale")
                if (current?.id != p.id) previous[cid] = current
                parks[cid] = p.copy(byId = if (current?.id == p.id) current.byId else me, byName = if (current?.id == p.id) current.byName else devices.getValue(me).name)
            }
            "state" -> {
                if (current == null || current.id != b.longOrNull("parkId")) return parkReply(409, "moved")
                parks[cid] = current.copy(state = ParkState.valueOf(b.getString("state")), drivingBy = devices.getValue(me).name, drivingById = me, leftAt = b.longOrNull("atMs"))
            }
            "undo" -> { if (current?.id == b.longOrNull("parkId")) { val old = previous[cid]; if (old == null) parks.remove(cid) else parks[cid] = old } }
            "photo" -> { val key = "$cid:${path.substringAfter("park=")}"; if (method == "PUT") photos[key] = body!! else return 200 to photos[key]?.toString(Charsets.ISO_8859_1) }
            else -> return fail(404, "route")
        }
        return parkReply()
    }
}
