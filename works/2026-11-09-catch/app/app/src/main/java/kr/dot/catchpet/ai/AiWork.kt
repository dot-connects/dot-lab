package kr.dot.catchpet.ai

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.dot.catchpet.BuildConfig
import kr.dot.catchpet.dex.Dex

/**
 * AI requests run here, on an app-wide scope (plan R16): leaving the detail screen doesn't cancel a 1-3 minute
 * drawing. The screen watches [busy] / [message] / [rev].
 */
object AiWork {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    // "<entry id>|<what>" -> what ("preview-fluff", "sheet-figure", ...): the two looks of one photo draw at once
    // (operator 11-17); Dex's writes are synchronized
    val busy = mutableStateMapOf<String, String>()
    fun busyOf(id: String): Set<String> = busy.keys.filter { it.startsWith("$id|") }.map { it.substringAfter('|') }.toSet()
    fun isBusy(id: String) = busy.keys.any { it.startsWith("$id|") }
    val message = mutableStateMapOf<String, String>()   // entry id -> last error, in Korean
    var rev by mutableIntStateOf(0); private set        // bumps when an entry got a new picture

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE)

    /** The dev app (debug build with the operator's token): unlimited previews and redraws. */
    val dev: Boolean get() = BuildConfig.DEBUG_PURCHASE.isNotEmpty()

    /**
     * Free previews / free pet sheets left on this device as last heard from the Worker (operator 11-18: 10 and 5 in
     * all). -1 = not heard yet. [refresh] asks the Worker; each answer of a call lowers the count too.
     */
    class Left(val preview: Int, val previewTotal: Int, val sheet: Int, val sheetTotal: Int)
    fun left(ctx: Context): Left {
        if (dev) return Left(99, 99, 99, 99)
        val p = prefs(ctx)
        return Left(p.getInt("q_preview", -1), p.getInt("q_preview_total", 10), p.getInt("q_sheet", -1), p.getInt("q_sheet_total", 5))
    }

    fun refresh(ctx: Context) {
        if (dev) return
        val app = ctx.applicationContext
        scope.launch {
            client(app).quota().onSuccess { q ->
                prefs(app).edit().putInt("q_preview", q.previewLeft).putInt("q_preview_total", q.previewTotal)
                    .putInt("q_sheet", q.freeLeft).putInt("q_sheet_total", q.freeTotal).apply()
                withContext(Dispatchers.Main) { rev++ }
            }
        }
    }

    // two looks can draw at once: an earlier answer arriving last must not raise the count again (Codex review 11-18)
    private fun lower(ctx: Context, key: String, n: Int) {
        val p = prefs(ctx)
        val old = p.getInt(key, n)
        p.edit().putInt(key, if (old < 0) n else minOf(n, old)).apply()
    }

    private fun client(ctx: Context): AiClient {
        val proof: suspend (String) -> String? = { hash -> Integrity.token(ctx, hash) }
        return AiClient(BuildConfig.WORKER, AiClient.deviceId(ctx), if (dev) null else proof)
    }

    /** The cut-out as sent: flattened on white (a transparent one broke the green key, Pixels.onWhite). */
    private fun cutOut(ctx: Context, e: Dex.Entry): ByteArray {
        val raw = Dex.file(ctx, e.id, "${e.poses.first()}.png").readBytes()
        val b = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
        val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val flat = android.graphics.Bitmap.createBitmap(kr.dot.catchpet.seg.Pixels.onWhite(px), b.width, b.height, android.graphics.Bitmap.Config.ARGB_8888)
        b.recycle()
        return java.io.ByteArrayOutputStream().use { o -> flat.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, o); flat.recycle(); o.toByteArray() }
    }

    fun preview(ctx: Context, e: Dex.Entry, style: String) = run(ctx, e, "preview-$style") { app ->
        val r = client(app).preview(cutOut(app, e), e.kind, style, if (dev) BuildConfig.DEBUG_PURCHASE else null)
        r.onSuccess { rep -> rep.left?.let { lower(app, "q_preview", it) }; Dex.savePreview(app, e.id, rep.image, ext(rep.mime), style) }
        r.onFailure { if ((it as? AiClient.Fail)?.code == "device") lower(app, "q_preview", 0) }
        r
    }

    /** [token]: the dev app's token; null = a free test sheet (the Worker counts it). */
    fun buy(ctx: Context, e: Dex.Entry, token: String?, style: String) = run(ctx, e, "sheet-$style") { app ->
        client(app).sheet(cutOut(app, e), e.kind, e.id, token, style, look(app, e, style)).onSuccess {
            it.freeLeft?.let { n -> lower(app, "q_sheet", n) }
            Dex.saveSheet(app, e.id, it.image, ext(it.mime), style, twelve = it.twelve)
            Done.notify(app, e.name, style)   // the 1-3 minutes may pass on another screen
        }.onFailure { if ((it as? AiClient.Fail)?.code == "free") lower(app, "q_sheet", 0) }
    }

    /** The preview shown for this look (the detail screen's rule), if one was drawn and fits the Worker's 800 k chars. */
    private fun look(ctx: Context, e: Dex.Entry, style: String): ByteArray? {
        val name = e.ai.previewFor(style) ?: e.ai.oldPreviews.getOrNull(if (style == "fluff") 0 else 1) ?: return null
        return runCatching { Dex.file(ctx, e.id, name).readBytes() }.getOrNull()?.takeIf { it.size < 580_000 }
    }

    private fun ext(mime: String) = when (mime) { "image/png" -> "png"; "image/jpeg" -> "jpg"; else -> "webp" }

    private fun run(ctx: Context, e: Dex.Entry, what: String, work: suspend (Context) -> Result<*>) {
        val key = "${e.id}|$what"
        if (busy.containsKey(key)) return
        val app = ctx.applicationContext
        busy[key] = what
        message.remove(e.id)
        scope.launch {
            val r = runCatching { work(app).getOrThrow() }
            r.exceptionOrNull()?.let { Log.w("AiWork", "$what failed: ${it.message}") }
            withContext(Dispatchers.Main) {
                busy.remove(key)
                r.exceptionOrNull()?.let { message[e.id] = (it as? AiClient.Fail)?.text ?: aiMessage(null, 0) }
                rev++
            }
        }
    }
}
