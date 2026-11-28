package kr.dot.catchpet.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * A critter's 3D-baked weather frames (tools/pack-wx3d.py): not in the APK, one zip per critter on the dot-lab site,
 * fetched on first need into files/wx3d/<critter>/ and checked against the manifest's size and sha256.
 */
object WxPack {
    const val BASE = "https://dot-connects.github.io/dot-lab/works/2026-11-09-catch/wx3d/"
    const val FPS = 12

    /** One critter's zip; [states] = state -> (act frames, loop frames). */
    class Pack(val zip: String, val size: Long, val sha256: String, val states: Map<String, Pair<Int, Int>>)

    fun parse(json: String): Map<String, Pack> {
        val cs = JSONObject(json).getJSONObject("critters")
        return cs.keys().asSequence().associateWith { k ->
            val o = cs.getJSONObject(k)
            val st = o.getJSONObject("states")
            Pack(o.getString("zip"), o.getLong("size"), o.getString("sha256").lowercase(),
                st.keys().asSequence().associateWith { s -> st.getJSONObject(s).let { it.getInt("act") to it.getInt("loop") } })
        }
    }

    /** The file is exactly what the manifest says. */
    fun verify(f: File, size: Long, sha256: String): Boolean {
        if (!f.isFile || f.length() != size) return false
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val buf = ByteArray(64 * 1024); while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) } == sha256.lowercase()
    }

    private fun dir(ctx: Context, critter: String) = File(ctx.filesDir, "wx3d/$critter")

    /** Unpacked and checked: state -> (act, loop) counts, else null. */
    fun ready(ctx: Context, critter: String): Map<String, Pair<Int, Int>>? {
        val f = File(dir(ctx, critter), "counts.json").takeIf { it.isFile } ?: return null
        return runCatching {
            val o = JSONObject(f.readText())
            o.keys().asSequence().associateWith { s -> o.getJSONObject(s).let { it.getInt("act") to it.getInt("loop") } }
        }.getOrNull()
    }

    /**
     * Ask for the critter's zip: on Wi-Fi (unmetered) by itself, or on any network when the user tapped "지금 받기"
     * (that replaces a waiting Wi-Fi-only request).
     */
    fun request(ctx: Context, critter: String, anyNetwork: Boolean = false) {
        if (ready(ctx, critter) != null) return
        val c = Constraints.Builder().setRequiredNetworkType(if (anyNetwork) NetworkType.CONNECTED else NetworkType.UNMETERED).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("wx3d-$critter", if (anyNetwork) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<Get>().setConstraints(c).setInputData(workDataOf("critter" to critter)).build())
    }

    /**
     * Decodes one state's frames (only while the act is about to play; the caller recycles them after).
     * Returns (act, loop), or null when a file is missing.
     */
    fun frames(ctx: Context, critter: String, state: String): Pair<List<Bitmap>, List<Bitmap>>? {
        val (na, nl) = ready(ctx, critter)?.get(state) ?: return null
        val d = dir(ctx, critter)
        fun load(kind: String, n: Int): List<Bitmap>? {
            val out = ArrayList<Bitmap>(n)
            for (i in 0 until n) {
                val b = BitmapFactory.decodeFile(File(d, "$critter-$state-$kind-%02d.webp".format(i)).path)
                if (b == null) { out.forEach(Bitmap::recycle); return null }
                out += b
            }
            return out
        }
        val act = load("act", na) ?: return null
        val loop = load("loop", nl) ?: run { act.forEach(Bitmap::recycle); return null }
        return act to loop
    }

    private fun get(url: String, to: File? = null): String? {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000; c.readTimeout = 30_000
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            if (to == null) return c.inputStream.bufferedReader().use { it.readText() }
            c.inputStream.use { i -> to.outputStream().use { i.copyTo(it) } }
            return null
        } finally { c.disconnect() }
    }

    /** Manifest -> zip -> size + sha256 -> unzip only the expected frame names -> counts.json last (= ready). */
    class Get(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
        override fun doWork(): Result {
            val ctx = applicationContext
            val critter = inputData.getString("critter")?.takeIf { it.matches(Regex("[a-z]+")) } ?: return Result.failure()
            if (ready(ctx, critter) != null) return Result.success()
            val tmp = File(ctx.cacheDir, "wx3d-$critter.zip")
            val stage = File(ctx.filesDir, "wx3d/.$critter")
            return try {
                val pack = parse(get(BASE + "manifest.json")!!)[critter] ?: return Result.failure()   // a critter without frames
                get(BASE + pack.zip, tmp)
                if (!verify(tmp, pack.size, pack.sha256)) { Log.w("WxPack", "$critter: size/hash mismatch"); return Result.retry() }
                stage.deleteRecursively(); stage.mkdirs()
                val name = Regex("$critter-(sun|hot|cold|snow|rain|dust)-(act|loop)-\\d\\d\\.webp")
                ZipInputStream(tmp.inputStream().buffered()).use { z ->
                    while (true) {
                        val e = z.nextEntry ?: break
                        if (!e.isDirectory && e.name.matches(name)) File(stage, e.name).outputStream().use { z.copyTo(it) }
                    }
                }
                val counts = JSONObject()
                for ((s, n) in pack.states) counts.put(s, JSONObject().put("act", n.first).put("loop", n.second))
                File(stage, "counts.json").writeText(counts.toString())
                val d = dir(ctx, critter)
                d.deleteRecursively()
                check(stage.renameTo(d)) { "rename" }
                Result.success()
            } catch (e: Exception) {
                Log.w("WxPack", "get $critter", e)
                stage.deleteRecursively()
                Result.retry()
            } finally { tmp.delete() }
        }
    }
}
