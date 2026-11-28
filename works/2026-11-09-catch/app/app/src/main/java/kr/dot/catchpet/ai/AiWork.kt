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
import kotlinx.coroutines.async
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

    /** Reports one AI picture of [e] (spec-release 4); true = the Worker took it. */
    suspend fun report(ctx: Context, e: Dex.Entry, style: String, reason: String): Boolean = client(ctx).report(e.id, e.kind, style, reason)

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

    /**
     * Both looks from one request (operator 11-28: one free sheet, or one 700-won purchase). [token]: the dev app's
     * token; null = a free sheet. An older Worker that does not know style "both" (400, or 413 for two looks) gets the two single requests.
     */
    fun buyBoth(ctx: Context, e: Dex.Entry, token: String?) = run(ctx, e, "sheet-both") { app ->
        val png = cutOut(app, e)
        val looks = STYLES.mapNotNull { s -> look(app, e, s)?.let { s to it } }.toMap()
        val r = client(app).sheetBoth(png, e.kind, e.id, token, looks)
        val f = r.exceptionOrNull() as? AiClient.Fail
        if (f != null && (f.status == 400 && f.code == "bad request" || f.status == 413)) {   // 413: the old body cap had room for one look
            // ponytail: an older Worker spends two free sheets here; delete once the "both" Worker is deployed
            for (s in STYLES) client(app).sheet(png, e.kind, e.id, token, s, looks[s]).onSuccess { keep(app, e, s, it) }
                .onFailure { if ((it as? AiClient.Fail)?.code == "free") lower(app, "q_sheet", 0) }.getOrThrow()
            Done.notify(app, e.name, "both")
            Result.success(Unit)
        } else r.onSuccess { m ->
            for (s in STYLES) keep(app, e, s, m.getValue(s))   // fluff first: it becomes the pet's look
            Done.notify(app, e.name, "both")
        }.onFailure { if ((it as? AiClient.Fail)?.code == "free") lower(app, "q_sheet", 0) }
    }

    /**
     * One paid sheet_both purchase -> both sheets of [entryId], saved before this returns success (the caller consumes
     * only then). No fallback to single sheets: the purchase belongs to style "both".
     */
    suspend fun buyPaid(ctx: Context, entryId: String, purchase: String): Result<Unit> {
        val e = Dex.get(ctx, entryId) ?: return Result.failure(IllegalStateException("no entry"))
        val done = kotlinx.coroutines.CompletableDeferred<Result<Unit>>()
        val started = run(ctx, e, "sheet-both", { r -> done.complete(r.map { }) }) { app ->
            val looks = STYLES.mapNotNull { s -> look(app, e, s)?.let { s to it } }.toMap()
            client(app).sheetBoth(cutOut(app, e), e.kind, e.id, null, looks, purchase).onSuccess { m ->
                for (s in STYLES) keep(app, e, s, m.getValue(s))
                Done.notify(app, e.name, "both")
            }
        }
        return if (started) done.await() else Result.failure(IllegalStateException("busy"))
    }

    private val STYLES = listOf("fluff", "figure")

    private fun keep(app: Context, e: Dex.Entry, style: String, rep: AiClient.Reply) {
        rep.freeLeft?.let { n -> lower(app, "q_sheet", n) }
        Dex.saveSheet(app, e.id, rep.image, ext(rep.mime), style, twelve = rep.twelve)
    }

    private val carded = java.util.Collections.synchronizedSet(HashSet<String>())

    /**
     * The AI card line for a fresh real catch (spec-release 10), saved on its dex entry. Asked once per catch; null on
     * any failure, and the card keeps its fixed line (never retried in a loop).
     */
    suspend fun card(ctx: Context, name: String, kind: String, pic: android.graphics.Bitmap): Pair<String, List<String>>? {
        if (!carded.add(name)) return null
        val app = ctx.applicationContext
        // the app scope: closing the card does not drop an answer on its way
        return scope.async {
            val r = client(app).card(jpeg512(pic), kind, if (dev) BuildConfig.DEBUG_PURCHASE else null)
                .onFailure { Log.w("AiWork", "card failed: ${it.message}") }.getOrNull() ?: return@async null
            // the dex writes on its own thread: the entry may land a moment after the catch
            repeat(10) {
                Dex.list(app).firstOrNull { it.name == name && it.critterId == null }?.let { Dex.saveCard(app, it.id, r.first, r.second); return@async r }
                kotlinx.coroutines.delay(500)
            }
            r
        }.await()
    }

    /** At most 512 px on its long side, on white (JPEG has no alpha). */
    private fun jpeg512(pic: android.graphics.Bitmap): ByteArray {
        val k = minOf(1f, 512f / maxOf(pic.width, pic.height))
        val w = maxOf(1, (pic.width * k).toInt()); val h = maxOf(1, (pic.height * k).toInt())
        val out = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(out).apply {
            drawColor(android.graphics.Color.WHITE)
            drawBitmap(pic, null, android.graphics.Rect(0, 0, w, h), android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        }
        return java.io.ByteArrayOutputStream().use { o -> out.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, o); out.recycle(); o.toByteArray() }
    }

    /** The preview shown for this look (the detail screen's rule), if one was drawn and fits the Worker's 800 k chars. */
    private fun look(ctx: Context, e: Dex.Entry, style: String): ByteArray? {
        val name = e.ai.previewFor(style) ?: e.ai.oldPreviews.getOrNull(if (style == "fluff") 0 else 1) ?: return null
        return runCatching { Dex.file(ctx, e.id, name).readBytes() }.getOrNull()?.takeIf { it.size < 580_000 }
    }

    private fun ext(mime: String) = when (mime) { "image/png" -> "png"; "image/jpeg" -> "jpg"; else -> "webp" }

    /** false = the same drawing is already running. [done] gets the result once the screen has it. */
    private fun run(ctx: Context, e: Dex.Entry, what: String, done: (Result<*>) -> Unit = {}, work: suspend (Context) -> Result<*>): Boolean {
        val key = "${e.id}|$what"
        if (busy.containsKey(key)) return false
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
            done(r)   // after the screen's message, so a caller may replace it
        }
        return true
    }
}
