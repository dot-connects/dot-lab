package kr.dot.catchpet

import android.content.Context
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File

/** Imported pets on disk: filesDir/pets/<id>/<label>.png + meta.json. */
object Pets {
    const val PREFS = "catchpet"
    const val KEY_PET = "pet"

    class Info(val id: String, val name: String, val kind: String, val no: Int, val labels: List<String>, val dir: File)

    private fun root(ctx: Context) = File(ctx.filesDir, "pets")

    // the id comes from an outside file: keep it a plain folder name
    private fun dirOf(ctx: Context, id: String) =
        File(root(ctx), id.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "_" })

    /** Writes one creature (same id overwrites). Pictures Android can't decode are skipped; false if none left. */
    fun save(ctx: Context, c: PetFile.Creature): Boolean {
        val dir = dirOf(ctx, c.id)
        dir.deleteRecursively()
        dir.mkdirs()
        var n = 0
        for (p in c.poses) {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(p.png, 0, p.png.size, o)
            if (o.outWidth <= 0) continue
            File(dir, "${p.label}.png").writeBytes(p.png)
            n++
        }
        if (n == 0) { dir.deleteRecursively(); return false }
        File(dir, "meta.json").writeText(
            JSONObject().put("id", c.id).put("name", c.name).put("kind", c.kind).put("no", c.no).toString())
        return true
    }

    fun load(ctx: Context, id: String): Info? = read(dirOf(ctx, id))

    fun list(ctx: Context): List<Info> =
        (root(ctx).listFiles() ?: emptyArray()).mapNotNull { read(it) }.sortedBy { it.no }

    private fun read(dir: File): Info? {
        val meta = File(dir, "meta.json").takeIf { it.isFile } ?: return null
        val m = JSONObject(meta.readText())
        val labels = PetFile.LABELS.filter { File(dir, "$it.png").isFile }
        return Info(m.optString("id"), m.optString("name"), m.optString("kind"), m.optInt("no"), labels, dir)
    }
}
