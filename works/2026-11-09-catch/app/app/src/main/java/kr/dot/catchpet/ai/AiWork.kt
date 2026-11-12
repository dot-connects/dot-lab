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
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * AI requests run here, on an app-wide scope (plan R16): leaving the detail screen doesn't cancel a 1-3 minute
 * drawing. The screen watches [busy] / [message] / [rev].
 */
object AiWork {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val busy = mutableStateMapOf<String, String>()      // entry id -> "preview" | "sheet"
    val message = mutableStateMapOf<String, String>()   // entry id -> last error, in Korean
    var rev by mutableIntStateOf(0); private set        // bumps when an entry got a new picture

    const val PER_DAY = 5
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE)
    private fun today() = LocalDate.now(ZoneOffset.ofHours(9)).toString()   // the Worker counts by KST day

    /** Previews left today as last heard from the Worker (5 on a new day). */
    fun leftToday(ctx: Context): Int {
        val p = prefs(ctx)
        return if (p.getString("ai_day", null) == today()) p.getInt("ai_left", PER_DAY) else PER_DAY
    }

    private fun setLeft(ctx: Context, n: Int) { prefs(ctx).edit().putString("ai_day", today()).putInt("ai_left", n).apply() }

    private fun client(ctx: Context) = AiClient(BuildConfig.WORKER, AiClient.deviceId(ctx))

    private fun cutOut(ctx: Context, e: Dex.Entry) = Dex.file(ctx, e.id, "${e.poses.first()}.png").readBytes()

    fun preview(ctx: Context, e: Dex.Entry, style: String) = run(ctx, e, "preview") { app ->
        val r = client(app).preview(cutOut(app, e), e.kind, style)
        r.onSuccess { rep -> rep.left?.let { setLeft(app, it) }; Dex.savePreview(app, e.id, rep.image, ext(rep.mime)) }
        r.onFailure { if ((it as? AiClient.Fail)?.code == "device") setLeft(app, 0) }
        r
    }

    fun buy(ctx: Context, e: Dex.Entry, token: String) = run(ctx, e, "sheet") { app ->
        client(app).sheet(cutOut(app, e), e.kind, e.id, token).onSuccess { Dex.saveSheet(app, e.id, it.image, ext(it.mime)) }
    }

    private fun ext(mime: String) = when (mime) { "image/png" -> "png"; "image/jpeg" -> "jpg"; else -> "webp" }

    private fun run(ctx: Context, e: Dex.Entry, what: String, work: suspend (Context) -> Result<*>) {
        if (busy.containsKey(e.id)) return
        val app = ctx.applicationContext
        busy[e.id] = what
        message.remove(e.id)
        scope.launch {
            val r = runCatching { work(app).getOrThrow() }
            r.exceptionOrNull()?.let { Log.w("AiWork", "$what failed: ${it.message}") }
            withContext(Dispatchers.Main) {
                busy.remove(e.id)
                r.exceptionOrNull()?.let { message[e.id] = (it as? AiClient.Fail)?.text ?: aiMessage(null, 0) }
                rev++
            }
        }
    }
}
