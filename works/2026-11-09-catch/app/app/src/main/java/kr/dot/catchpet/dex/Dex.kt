package kr.dot.catchpet.dex

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The dex on disk: filesDir/dex/<id>/meta.json + <pose>.png (real things) + ai/ (previews, sheet).
 * Every file is written to a temp file and renamed, and meta.json goes last, so a crash leaves either the old
 * entry or a folder without meta (skipped). Ids are "e<no>", which also fits the Worker's entry regex.
 */
object Dex {
    data class AiState(val previews: List<String>, val sheet: String?, val owned: Boolean)

    data class Entry(
        val id: String,
        val kind: String,          // person | dog | cat | critter
        val name: String,
        val no: Int,
        val critterId: String?,
        val poses: List<String>,   // labels; real things have <label>.png in the entry folder
        val caughtAt: Long,
        val ai: AiState,
    )

    private val ENTRY_ID = Regex("^e(\\d+)$")
    private val KIND_NAMES = mapOf("dog" to "강아지", "cat" to "고양이", "person" to "사람")

    fun root(ctx: Context) = File(ctx.filesDir, "dex")
    fun dir(ctx: Context, id: String) = File(root(ctx), id)
    fun file(ctx: Context, id: String, name: String) = File(dir(ctx, id), name)

    /** dir0/1/7 -> front, 2/6 -> side, 3/4/5 -> back (plan R6); any other label stays as it is. */
    fun poseOf(label: String): String = when (label) {
        "dir0", "dir1", "dir7" -> "front"
        "dir2", "dir6" -> "side"
        "dir3", "dir4", "dir5" -> "back"
        else -> label
    }

    /**
     * Real things: always a new entry ("강아지 N" = the N-th of that kind). Critters: the same critterId is the
     * same entry and a new label adds a pose. png = the cut-out for real things, null for critters.
     */
    @Synchronized
    fun add(ctx: Context, kind: String, png: ByteArray?, critterId: String?, label: String, now: Long = System.currentTimeMillis()): Entry {
        val pose = poseOf(label)
        val all = list(ctx)
        if (critterId != null) {
            all.firstOrNull { it.critterId == critterId }?.let { old ->
                if (pose in old.poses) return old
                return write(ctx, old.copy(poses = old.poses + pose))
            }
        }
        val usedNos = (all.map { it.no } + (root(ctx).list()?.mapNotNull { ENTRY_ID.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: emptyList()))
        val no = (usedNos.maxOrNull() ?: 0) + 1
        val id = "e$no"
        val name = if (critterId != null) critterName(ctx, critterId)
        else "${KIND_NAMES[kind] ?: kind} ${all.count { it.kind == kind } + 1}"
        dir(ctx, id).mkdirs()
        if (png != null) atomicWrite(file(ctx, id, "$pose.png"), png)
        return write(ctx, Entry(id, kind, name, no, critterId, listOf(pose), now, AiState(emptyList(), null, false)))
    }

    /** Every readable entry by number; a folder whose meta can't be read is skipped. */
    fun list(ctx: Context): List<Entry> =
        (root(ctx).listFiles() ?: emptyArray()).filter { it.isDirectory }.mapNotNull { read(it) }.sortedBy { it.no }

    fun get(ctx: Context, id: String): Entry? = if (ENTRY_ID.matches(id)) read(dir(ctx, id)) else null

    /** Bytes as the Worker sent them (webp); the name keeps the count so earlier previews stay. */
    @Synchronized
    fun savePreview(ctx: Context, id: String, img: ByteArray, ext: String = "webp"): Entry {
        val e = get(ctx, id) ?: error("no entry $id")
        val name = "ai/preview${e.ai.previews.size + 1}.$ext"
        atomicWrite(file(ctx, id, name), img)
        return write(ctx, e.copy(ai = e.ai.copy(previews = e.ai.previews + name)))
    }

    /** The bought pose sheet: stored and the entry becomes owned (usable as a pet). */
    @Synchronized
    fun saveSheet(ctx: Context, id: String, img: ByteArray, ext: String = "webp"): Entry {
        val e = get(ctx, id) ?: error("no entry $id")
        val name = "ai/sheet.$ext"
        atomicWrite(file(ctx, id, name), img)
        return write(ctx, e.copy(ai = e.ai.copy(sheet = name, owned = true)))
    }

    private var names: Map<String, String>? = null

    private fun critterName(ctx: Context, critterId: String): String {
        val m = names ?: runCatching {
            val a = JSONArray(ctx.assets.open("critters/index.json").bufferedReader().use { it.readText() })
            (0 until a.length()).associate { a.getJSONObject(it).let { o -> o.getString("id") to o.getString("name") } }
        }.getOrDefault(emptyMap()).also { names = it }
        return m[critterId] ?: critterId
    }

    private fun write(ctx: Context, e: Entry): Entry {
        val o = JSONObject()
            .put("id", e.id).put("kind", e.kind).put("name", e.name).put("no", e.no)
            .put("critterId", e.critterId ?: JSONObject.NULL)
            .put("poses", JSONArray(e.poses)).put("caughtAt", e.caughtAt)
            .put("ai", JSONObject().put("previews", JSONArray(e.ai.previews)).put("sheet", e.ai.sheet ?: JSONObject.NULL).put("owned", e.ai.owned))
        atomicWrite(file(ctx, e.id, "meta.json"), o.toString().toByteArray())
        return e
    }

    private fun read(d: File): Entry? = runCatching {
        val o = JSONObject(File(d, "meta.json").readText())
        val ai = o.getJSONObject("ai")
        fun JSONArray.strings() = (0 until length()).map { getString(it) }
        Entry(
            o.getString("id").also { require(it == d.name) }, o.getString("kind"), o.getString("name"), o.getInt("no"),
            if (o.isNull("critterId")) null else o.getString("critterId"),
            o.getJSONArray("poses").strings(), o.getLong("caughtAt"),
            AiState(ai.getJSONArray("previews").strings(), if (ai.isNull("sheet")) null else ai.getString("sheet"), ai.getBoolean("owned")),
        )
    }.getOrNull()

    internal fun atomicWrite(f: File, bytes: ByteArray) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
