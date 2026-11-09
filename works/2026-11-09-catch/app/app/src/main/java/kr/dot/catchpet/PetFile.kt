package kr.dot.catchpet

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/**
 * The .dotpet file the web app exports (site/js/petfile.js). Pure: no Android API, tested on the JVM against
 * the same petfile-vector.json the web test reads. Bitmap decoding stays outside (MainActivity).
 */
object PetFile {
    val LABELS = listOf("front", "side", "walk", "lying", "back")
    private val PNG_SIG = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

    class Pose(val label: String, val png: ByteArray, val w: Int, val h: Int)
    class Creature(val id: String, val name: String, val kind: String, val no: Int, val pokeId: Int?, val poses: List<Pose>)
    class Result(val creatures: List<Creature>, val droppedPoses: Int, val droppedCreatures: Int)

    /** Throws on non-JSON, a wrong format/version, or no creature list. Bad poses and empty creatures are dropped. */
    fun parse(text: String): Result {
        val o = JSONObject(text)
        val v = o.opt("v") as? Number
        val list = o.optJSONArray("creatures")
        require(o.opt("format") == "dotpet" && v?.toDouble() == 1.0 && list != null) { "not a dotpet v1 file" }
        var droppedPoses = 0
        var droppedCreatures = 0
        val creatures = mutableListOf<Creature>()
        for (i in 0 until list.length()) {
            val c = list.optJSONObject(i) ?: JSONObject()
            val seen = mutableSetOf<String>()
            val poses = mutableListOf<Pose>()
            val src = c.optJSONArray("poses") ?: JSONArray()
            for (j in 0 until src.length()) {
                val p = src.optJSONObject(j) ?: JSONObject()
                val label = p.optString("label")
                val png = pngOrNull(p.opt("png"))
                if (label !in LABELS || label in seen || png == null) { droppedPoses++; continue }
                seen += label
                poses += Pose(label, png, p.optInt("w"), p.optInt("h"))
            }
            if (poses.isEmpty()) { droppedCreatures++; continue }
            val pokeId = if (c.isNull("pokeId")) null else c.optInt("pokeId")
            creatures += Creature(c.optString("id"), c.optString("name"), c.optString("kind"), c.optInt("no"), pokeId, poses)
        }
        return Result(creatures, droppedPoses, droppedCreatures)
    }

    private fun pngOrNull(b64: Any?): ByteArray? {
        if (b64 !is String || b64.isEmpty()) return null
        val bytes = try { Base64.getDecoder().decode(b64) } catch (e: IllegalArgumentException) { return null }
        return if (bytes.size > 8 && PNG_SIG.indices.all { bytes[it] == PNG_SIG[it] }) bytes else null
    }

    /** Unlocked moves = union over the poses held (mirror of site/js/moves.js). */
    fun moves(labels: Collection<String>): List<String> {
        val has = labels.filter { it in LABELS }.toSet()
        if (has.isEmpty()) return emptyList()
        val m = mutableSetOf("bounce", "flinch")
        if ("side" in has) m += "walk"
        if ("side" in has && "walk" in has) m += "run"
        if ("lying" in has) m += "sleep"
        if ("back" in has) m += "hide"
        return listOf("bounce", "flinch", "walk", "run", "sleep", "hide").filter { it in m }
    }

    /** "모습 n/5" (a pokemon has one picture: n/1). */
    fun count(labels: Collection<String>, kind: String): String =
        "모습 ${labels.filter { it in LABELS }.toSet().size}/${if (kind == "pokemon") 1 else LABELS.size}"
}
