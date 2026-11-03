package kr.dot.parkingfloor

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One park. floor is the confirmed floor when confirmed, otherwise the estimate. Unknown height = deltaM null. */
data class Park(
    val id: Long,
    val atMs: Long,
    val placeKey: String?,
    val placeName: String?,
    val lat: Double?,
    val lng: Double?,
    val deltaM: Double?,
    val est: Int?,
    val alt: Int?,
    val confirmed: Boolean,
    val floor: Int?,
    val pillar: String,
    val color: Int,
    /** Family member who parked, null when it was this phone. */
    val by: String? = null,
    /** Came from the family server; such parks never teach a place on this phone. */
    val remote: Boolean = false,
)

/** A garage the app has seen: centre, name, and the confirmed (height, floor) pairs it learned from. */
data class Place(
    val key: String,
    val name: String?,
    val lat: Double,
    val lng: Double,
    val pairs: List<FloorModel.Pair>,
    val colors: Map<Int, Int>,
)

/** Everything the app keeps, in one JSON file in app storage. No server. */
object ParkStore {
    private const val FILE = "parks.json"
    private const val KEEP = 40

    data class State(val parks: List<Park>, val places: List<Place>)

    private fun file(context: Context) = File(context.filesDir, FILE)

    @Synchronized
    fun load(context: Context): State {
        val f = file(context)
        if (!f.exists()) return State(emptyList(), emptyList())
        return try {
            val root = JSONObject(f.readText())
            State(
                root.optJSONArray("parks").objects().map(::parkOf),
                root.optJSONArray("places").objects().map(::placeOf),
            )
        } catch (_: Exception) {
            // keep the damaged file for a look instead of overwriting it with an empty state
            f.renameTo(File(context.filesDir, "$FILE.bad.${System.currentTimeMillis()}"))
            State(emptyList(), emptyList())
        }
    }

    @Synchronized
    fun save(context: Context, state: State) {
        val root = JSONObject()
            .put("parks", JSONArray(state.parks.takeLast(KEEP).map(::jsonOf)))
            .put("places", JSONArray(state.places.map(::jsonOf)))
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file(context))) throw java.io.IOException("could not replace $FILE")
    }

    fun latest(context: Context): Park? = load(context).parks.lastOrNull()

    fun place(context: Context, key: String?): Place? = key?.let { k -> load(context).places.firstOrNull { it.key == k } }

    /** The park before the latest one at the same place, for the 4x3 widget. */
    fun previousAtSamePlace(context: Context): Park? {
        val parks = load(context).parks
        val last = parks.lastOrNull() ?: return null
        return parks.dropLast(1).lastOrNull { it.placeKey != null && it.placeKey == last.placeKey && it.floor != null }
    }

    @Synchronized
    fun add(context: Context, park: Park, place: Place?) {
        val s = load(context)
        val places = if (place == null || s.places.any { it.key == place.key }) s.places else s.places + place
        save(context, State(s.parks + park, places))
    }

    @Synchronized
    fun update(context: Context, id: Long, change: (Park) -> Park) {
        val s = load(context)
        save(context, s.copy(parks = s.parks.map { if (it.id == id) change(it) else it }))
    }

    @Synchronized
    fun updatePlace(context: Context, key: String, change: (Place) -> Place) {
        val s = load(context)
        save(context, s.copy(places = s.places.map { if (it.key == key) change(it) else it }))
    }

    /** Confirms a floor for a park and teaches its place, replacing an earlier lesson from the same park. */
    @Synchronized
    fun confirm(context: Context, id: Long, floor: Int) {
        val s = load(context)
        val park = s.parks.firstOrNull { it.id == id } ?: return
        val learnedColor = s.places.firstOrNull { it.key == park.placeKey }?.colors?.get(floor)
        val parks = s.parks.map {
            if (it.id == id) it.copy(confirmed = true, floor = floor, color = if (it.pillar.isEmpty()) learnedColor ?: 0 else it.color) else it
        }
        val places = s.places.map { place ->
            if (place.key != park.placeKey || park.deltaM == null) place
            else {
                val previous = if (park.confirmed && park.floor != null) FloorModel.Pair(park.deltaM, park.floor) else null
                val pairs = place.pairs.toMutableList()
                if (previous != null) pairs.remove(previous)
                pairs += FloorModel.Pair(park.deltaM, floor)
                place.copy(pairs = pairs.takeLast(20))
            }
        }
        save(context, State(parks, places))
    }

    /** Pillar text and the pillar colour (0 = none). The colour is also remembered for that floor of the place. */
    @Synchronized
    fun setPillar(context: Context, id: Long, text: String, color: Int) {
        val s = load(context)
        val park = s.parks.firstOrNull { it.id == id } ?: return
        val parks = s.parks.map { if (it.id == id) it.copy(pillar = text, color = if (color != 0) color else it.color) else it }
        val places = s.places.map { place ->
            if (place.key != park.placeKey || park.floor == null || color == 0) place
            else place.copy(colors = place.colors + (park.floor to color))
        }
        save(context, State(parks, places))
    }

    /**
     * Merges a park from the family server. Same id: the shared fields are updated (and a confirmation of a
     * park this phone measured teaches its place). New id: added only when newer than what is here.
     * Returns true when anything changed.
     */
    @Synchronized
    fun merge(context: Context, remote: Park): Boolean {
        val local = load(context).parks.firstOrNull { it.id == remote.id }
        if (local == null) {
            val latest = load(context).parks.lastOrNull()
            if (latest != null && latest.atMs >= remote.atMs) return false
            add(context, remote, null)
            return true
        }
        val same = local.floor == remote.floor && local.confirmed == remote.confirmed && local.pillar == remote.pillar &&
            local.color == remote.color && local.placeName == remote.placeName
        if (same) return false
        if (remote.confirmed && remote.floor != null && (!local.confirmed || local.floor != remote.floor)) confirm(context, local.id, remote.floor)
        update(context, local.id) {
            it.copy(floor = remote.floor, est = remote.est ?: it.est, alt = remote.alt ?: it.alt, confirmed = remote.confirmed,
                pillar = remote.pillar, color = remote.color, placeName = remote.placeName ?: it.placeName)
        }
        return true
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONObject.optDoubleOrNull(key: String): Double? = if (has(key) && !isNull(key)) getDouble(key) else null
    private fun JSONObject.optIntOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null

    private fun parkOf(o: JSONObject) = Park(
        id = o.getLong("id"),
        atMs = o.getLong("at"),
        placeKey = o.optStringOrNull("place"),
        placeName = o.optStringOrNull("name"),
        lat = o.optDoubleOrNull("lat"),
        lng = o.optDoubleOrNull("lng"),
        deltaM = o.optDoubleOrNull("delta"),
        est = o.optIntOrNull("est"),
        alt = o.optIntOrNull("alt"),
        confirmed = o.optBoolean("confirmed"),
        floor = o.optIntOrNull("floor"),
        pillar = o.optString("pillar"),
        color = o.optInt("color"),
        by = o.optStringOrNull("by"),
        remote = o.optBoolean("remote"),
    )

    private fun jsonOf(p: Park) = JSONObject()
        .put("id", p.id).put("at", p.atMs).put("place", p.placeKey).put("name", p.placeName)
        .put("lat", p.lat).put("lng", p.lng).put("delta", p.deltaM).put("est", p.est).put("alt", p.alt)
        .put("confirmed", p.confirmed).put("floor", p.floor).put("pillar", p.pillar).put("color", p.color)
        .put("by", p.by).put("remote", p.remote)

    private fun placeOf(o: JSONObject) = Place(
        key = o.getString("key"),
        name = o.optStringOrNull("name"),
        lat = o.getDouble("lat"),
        lng = o.getDouble("lng"),
        pairs = o.optJSONArray("pairs").objects().map { FloorModel.Pair(it.getDouble("d"), it.getInt("f")) },
        colors = o.optJSONObject("colors")?.let { c -> c.keys().asSequence().associate { it.toInt() to c.getInt(it) } }.orEmpty(),
    )

    private fun jsonOf(p: Place) = JSONObject()
        .put("key", p.key).put("name", p.name).put("lat", p.lat).put("lng", p.lng)
        .put("pairs", JSONArray(p.pairs.map { JSONObject().put("d", it.deltaM).put("f", it.floor) }))
        .put("colors", JSONObject().apply { p.colors.forEach { (k, v) -> put(k.toString(), v) } })
}
