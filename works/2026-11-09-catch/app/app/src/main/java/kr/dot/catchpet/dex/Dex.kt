package kr.dot.catchpet.dex

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.max
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The dex on disk: filesDir/dex/<id>/meta.json + <pose>.png (real things) + ai/ (previews, sheet).
 * Every file is written to a temp file and renamed, and meta.json goes last, so a crash leaves either the old
 * entry or a folder without meta (skipped). Ids are "e<no>", which also fits the Worker's entry regex.
 */
object Dex {
    /**
     * previews: drawn previews ("ai/preview-<style>-N.ext"; older ones have no style). sheets: bought pose sheets by
     * style ("fluff" | "figure"); sheet = the one the pet uses (petStyle), owned = any bought (operator 11-15).
     */
    data class AiState(
        val previews: List<String>, val sheet: String?, val owned: Boolean,
        val sheets: Map<String, String> = emptyMap(), val petStyle: String? = null,
    ) {
        /** The latest preview drawn in this style, or null. */
        fun previewFor(style: String): String? = previews.lastOrNull { it.startsWith("ai/preview-$style-") }
        /** Previews from before styles were recorded. */
        val oldPreviews get() = previews.filter { !it.startsWith("ai/preview-") }
    }

    data class Entry(
        val id: String,
        val kind: String,          // person | dog | cat | critter
        val name: String,
        val no: Int,
        val critterId: String?,
        val poses: List<String>,   // labels; real things have <label>.png in the entry folder
        val caughtAt: Long,
        val ai: AiState,
        /** The capsule colour it came in (spec-extras 3); null for entries caught before 0.5. */
        val cap: Int? = null,
    )

    private val ENTRY_ID = Regex("^e(\\d+)$")
    private val KIND_NAMES = mapOf(
        "dog" to "강아지", "cat" to "고양이", "person" to "사람",
        "bird" to "새", "cow" to "소", "horse" to "말", "sheep" to "양", "elephant" to "코끼리", "bear" to "곰",
        "zebra" to "얼룩말", "giraffe" to "기린",
        "hamster" to "햄스터", "rabbit" to "토끼", "turtle" to "거북이", "fish" to "물고기", "guinea pig" to "기니피그",
        "lizard" to "도마뱀",
    )

    /** Korean name of a dex kind ("hamster" -> "햄스터"); unknown kinds show as they are. */
    fun kindName(kind: String): String = if ('+' in kind) groupName(kind) else KIND_NAMES[kind] ?: kind

    /** A group caught together (operator 11-15, up to 3): "사람 둘", "사람과 고양이", "사람 둘과 강아지". */
    fun groupName(kind: String): String {
        val counts = kind.split('+').groupingBy { it }.eachCount()
        val parts = counts.map { (k, n) -> (KIND_NAMES[k] ?: k) + when (n) { 1 -> ""; 2 -> " 둘"; else -> " 셋" } }
        if (parts.size == 1) return parts[0]
        val head = parts.dropLast(1)
        return (head.dropLast(1) + withAnd(head.last())).joinToString(", ") + " " + parts.last()
    }

    /** "사람과" / "고양이와": the "and" particle after a Korean word. */
    private fun withAnd(word: String): String {
        val c = word.lastOrNull() ?: return word
        return word + if (c in '\uAC00'..'\uD7A3' && (c - '\uAC00') % 28 != 0) "과" else "와"
    }

    /** Does this kind (one or a group) have a person in it? People need consent. */
    fun hasPerson(kind: String) = kind.split('+').contains("person")

    /** "강아지를" / "곰을": the object particle after a Korean word (batchim decides). */
    fun withObject(word: String): String {
        val c = word.lastOrNull() ?: return word
        val batchim = c in '\uAC00'..'\uD7A3' && (c - '\uAC00') % 28 != 0
        return word + if (batchim) "을" else "를"
    }

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
    fun add(ctx: Context, kind: String, png: ByteArray?, critterId: String?, label: String, now: Long = System.currentTimeMillis(), name: String? = null, cap: Int? = null): Entry {
        val pose = poseOf(label)
        val all = list(ctx)
        if (critterId != null) {
            all.firstOrNull { it.critterId == critterId }?.let { old ->
                if (pose in old.poses) return old
                return write(ctx, old.copy(poses = old.poses + pose))
            }
        }
        val usedNos = (all.map { it.no } + (root(ctx).list()?.mapNotNull { ENTRY_ID.find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: emptyList()))
        // numbers only go up: a deleted e5 must not come back as a new e5 (review 11-15: an unfinished drawing,
        // a person's consent or the Worker's sheet slots of the old one would land on the new one)
        val seq = File(root(ctx), "next.txt")
        val no = max((usedNos.maxOrNull() ?: 0) + 1, runCatching { seq.readText().trim().toInt() }.getOrDefault(1))
        root(ctx).mkdirs(); atomicWrite(seq, (no + 1).toString().toByteArray())
        val id = "e$no"
        // the catch screen picks the name first so it can say it (operator 11-15); an existing one is never reused
        val used = all.map { it.name }.toSet()
        val name = if (critterId != null) critterName(ctx, critterId) else name?.takeIf { it !in used } ?: cuteName(used)
        dir(ctx, id).mkdirs()
        if (png != null) atomicWrite(file(ctx, id, "$pose.png"), png)
        return write(ctx, Entry(id, kind, name, no, critterId, listOf(pose), now, AiState(emptyList(), null, false), cap))
    }

    /** Every readable entry by number; a folder whose meta can't be read is skipped. */
    fun list(ctx: Context): List<Entry> =
        (root(ctx).listFiles() ?: emptyArray()).filter { it.isDirectory }.mapNotNull { read(it) }.sortedBy { it.no }

    fun get(ctx: Context, id: String): Entry? = if (ENTRY_ID.matches(id)) read(dir(ctx, id)) else null

    /** Bytes as the Worker sent them (webp); the name keeps the count so earlier previews stay. */
    @Synchronized
    fun savePreview(ctx: Context, id: String, img: ByteArray, ext: String = "webp", style: String = "fluff"): Entry {
        val e = get(ctx, id) ?: error("no entry $id")
        val name = "ai/preview-$style-${e.ai.previews.size + 1}.$ext"
        atomicWrite(file(ctx, id, name), img)
        return write(ctx, e.copy(ai = e.ai.copy(previews = e.ai.previews + name)))
    }

    /** The bought pose sheet: stored and the entry becomes owned (usable as a pet). */
    @Synchronized
    fun saveSheet(ctx: Context, id: String, img: ByteArray, ext: String = "webp", style: String = "fluff", twelve: Boolean = true): Entry {
        val e = get(ctx, id) ?: error("no entry $id")
        // "-v12" = 4 x 3 cells (Sheet.KEYS12), by what the Worker says it drew; plain "sheet*.ext" are 3 x 2
        val name = if (twelve) "ai/sheet-$style-v12.$ext" else "ai/sheet-$style.$ext"
        atomicWrite(file(ctx, id, name), img)
        val pet = e.ai.petStyle ?: style   // the first one bought is the pet's look
        val sheets = e.ai.sheets + (style to name)
        return write(ctx, e.copy(ai = e.ai.copy(sheets = sheets, owned = true, petStyle = pet, sheet = sheets[pet])))
    }

    /** Which bought look the pet uses. */
    @Synchronized
    fun setPetStyle(ctx: Context, id: String, style: String): Entry? {
        val e = get(ctx, id) ?: return null
        val s = e.ai.sheets[style] ?: return e
        return write(ctx, e.copy(ai = e.ai.copy(petStyle = style, sheet = s)))
    }

    /** Removes an entry and every file in it (photos, drawings, sheets). Renamed first so a crash leaves no half entry. */
    @Synchronized
    fun delete(ctx: Context, id: String): Boolean {
        if (!ENTRY_ID.matches(id)) return false
        val d = dir(ctx, id)
        if (!d.exists()) return false
        val gone = File(root(ctx), ".gone-$id-${System.nanoTime()}")
        return (if (d.renameTo(gone)) gone else d).deleteRecursively().also { kr.dot.catchpet.widget.PetWidgets.refresh(ctx) }
    }

    private var names: Map<String, String>? = null

    /** Every critter id in index.json order (the dex shows the ones not met yet as "?"). */
    fun critterIds(ctx: Context): List<String> { critterName(ctx, ""); return names.orEmpty().keys.toList() }

    fun critterName(ctx: Context, critterId: String): String {
        val m = names ?: runCatching {
            val a = JSONArray(ctx.assets.open("critters/index.json").bufferedReader().use { it.readText() })
            (0 until a.length()).associate { a.getJSONObject(it).let { o -> o.getString("id") to o.getString("name") } }
        }.getOrDefault(emptyMap()).also { names = it }
        return m[critterId] ?: "???"
    }

    /** Pet names for real catches (operator 11-14): an unused one from the pool, else the pool name + a number. */
    val NAMES = listOf(
        "보송이", "콩떡", "말랑이", "몽실이", "찹쌀이", "두부", "호떡", "꼬물이", "쿠키", "모찌",
        "단추", "감자", "콩이", "보리", "솜이", "구름이", "도토리", "꿀떡", "젤리", "땅콩",
        "호두", "밤비", "동글이", "토실이", "누룽지", "만두", "꽈배기", "붕어빵", "사탕", "별이",
        "방울이", "몽이", "뭉치", "초코", "라떼", "쿵이", "포동이", "꼬미", "깨비", "버터",
    )

    fun cuteName(used: Set<String>, rnd: kotlin.random.Random = kotlin.random.Random.Default): String =
        NAMES.filter { it !in used }.randomOrNull(rnd)
            ?: generateSequence(2) { it + 1 }.map { "${NAMES.random(rnd)} $it" }.first { it !in used }

    /** A new name from the detail screen; blank keeps the old one. */
    @Synchronized
    fun rename(ctx: Context, id: String, name: String): Entry? {
        val e = get(ctx, id) ?: return null
        val n = name.trim().take(12)
        return if (n.isEmpty() || n == e.name) e else write(ctx, e.copy(name = n))
    }

    private fun write(ctx: Context, e: Entry): Entry {
        val o = JSONObject()
            .put("id", e.id).put("kind", e.kind).put("name", e.name).put("no", e.no)
            .put("critterId", e.critterId ?: JSONObject.NULL)
            .put("poses", JSONArray(e.poses)).put("caughtAt", e.caughtAt)
            .put("ai", JSONObject().put("previews", JSONArray(e.ai.previews)).put("sheet", e.ai.sheet ?: JSONObject.NULL).put("owned", e.ai.owned)
                .put("sheets", JSONObject(e.ai.sheets)).put("petStyle", e.ai.petStyle ?: JSONObject.NULL))
            .put("cap", e.cap ?: JSONObject.NULL)
        atomicWrite(file(ctx, e.id, "meta.json"), o.toString().toByteArray())
        kr.dot.catchpet.widget.PetWidgets.refresh(ctx)   // a new name, look or first pet shows on the home widgets
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
            ai.let { a ->
                val sheet = if (a.isNull("sheet")) null else a.getString("sheet")
                // before 11-15 there was one sheet and it was always the fur-ball look
                val sheets = a.optJSONObject("sheets")?.let { s -> s.keys().asSequence().associateWith { s.getString(it) } }
                    ?: (if (sheet != null) mapOf("fluff" to sheet) else emptyMap())
                val pet = if (a.isNull("petStyle")) sheets.keys.firstOrNull() else a.optString("petStyle")
                AiState(a.getJSONArray("previews").strings(), sheet, a.getBoolean("owned"), sheets, pet)
            },
            if (o.isNull("cap")) null else o.optInt("cap"),
        )
    }.getOrNull()

    internal fun atomicWrite(f: File, bytes: ByteArray) {
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(bytes)
        Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
