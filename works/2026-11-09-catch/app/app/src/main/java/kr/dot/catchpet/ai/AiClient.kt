package kr.dot.catchpet.ai

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/** The Worker (works/2026-11-09-catch/worker): /transform = AI preview, /sheet = bought pose sheet. */
/** [integrity]: request hash -> Play Integrity token for the release app (null in the dev app, which sends its token). */
class AiClient(private val base: String, private val deviceId: String, private val integrity: (suspend (String) -> String?)? = null) {
    /** code = the Worker's error field, or "net" when it couldn't be reached. */
    class Fail(val code: String, val status: Int) : Exception("$status $code") {
        val text get() = aiMessage(code, status)
    }

    /** image = the decoded picture bytes (webp), left = what the Worker says is left (previews today / sheet retries). */
    /** [twelve]: the Worker drew the 4 x 3 sheet (an older Worker answers without "layout" = 3 x 2). */
    class Reply(val image: ByteArray, val mime: String, val left: Int?, val twelve: Boolean = false, val freeLeft: Int? = null)

    /** [devToken]: the dev app's token, so the Worker skips the day limit. */
    suspend fun preview(png: ByteArray, kind: String, style: String, devToken: String? = null): Result<Reply> =
        post("/transform", body(png, mapOf("style" to style, "kind" to kind, "device" to deviceId)), devToken)

    /** [look] = the preview the player saw (operator 11-18: the pet is that picture in poses), null = none drawn. */
    /** [token]: the dev app's token; null = a free test sheet (plan-release Task 8 adds the Play purchase). */
    suspend fun sheet(png: ByteArray, kind: String, entry: String, token: String?, style: String = "fluff", look: ByteArray? = null): Result<Reply> =
        post("/sheet", body(png, mapOf("kind" to kind, "device" to deviceId, "entry" to entry, "style" to style, "layout" to "12") +
            (look?.let { mapOf("look" to Base64.encodeToString(it, Base64.NO_WRAP)) } ?: emptyMap())), token)

    /** What is left for this device: free previews and free pet sheets (operator 11-18: 10 and 5). */
    class Quota(val previewLeft: Int, val previewTotal: Int, val freeLeft: Int, val freeTotal: Int)

    suspend fun quota(): Result<Quota> = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL(base.trimEnd('/') + "/quota?device=" + deviceId).openConnection() as HttpURLConnection)
            try {
                c.connectTimeout = 15_000; c.readTimeout = 15_000
                c.setRequestProperty("x-app", "catchpet")
                if (c.responseCode != 200) throw Fail("http", c.responseCode)
                val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                Quota(o.optInt("preview_left", 0), o.optInt("preview_total", 0), o.optInt("free_left", 0), o.optInt("free_total", 0))
            } catch (e: IOException) { throw Fail("net", 0) } finally { c.disconnect() }
        }
    }

    /** Flags one AI picture (Play AI-content rule, spec-release 4): no image leaves the phone, only which picture and why. */
    suspend fun report(entry: String, kind: String, style: String, reason: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL(base.trimEnd('/') + "/report").openConnection() as HttpURLConnection)
            try {
                c.requestMethod = "POST"; c.connectTimeout = 15_000; c.readTimeout = 15_000; c.doOutput = true
                c.setRequestProperty("content-type", "application/json")
                c.setRequestProperty("x-app", "catchpet")
                val o = JSONObject().put("device", deviceId).put("entry", entry).put("kind", kind).put("style", style).put("reason", reason)
                c.outputStream.use { it.write(o.toString().toByteArray()) }
                c.responseCode == 200
            } finally { c.disconnect() }
        }.getOrDefault(false)
    }

    private suspend fun post(path: String, json: String, token: String?): Result<Reply> = withContext(Dispatchers.IO) {
        runCatching {
            // the Integrity token is bound to this exact body (the Worker hashes the text it received)
            val proof = if (token == null) integrity?.invoke(RequestHash.of(path, deviceId, json)) else null
            // no token at all is Play's or our trouble (offline Play services, not linked yet): don't blame the device
            if (token == null && integrity != null && proof == null) throw Fail("noproof", 0)
            val c = (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection)
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 30_000
                c.readTimeout = 200_000
                c.doOutput = true
                c.setRequestProperty("content-type", "application/json")
                c.setRequestProperty("x-app", "catchpet")
                token?.let { c.setRequestProperty("x-purchase", it) }
                proof?.let { c.setRequestProperty("x-integrity", it) }
                val status = try {
                    c.outputStream.use { it.write(json.toByteArray()) }
                    c.responseCode
                } catch (e: IOException) { throw Fail("net", 0) }
                val text = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                val o = runCatching { JSONObject(text) }.getOrNull()
                if (status !in 200..299 || o == null || !o.has("image")) throw Fail(o?.optString("error")?.ifEmpty { null } ?: "http", status)
                Reply(Base64.decode(o.getString("image"), Base64.DEFAULT), o.optString("mime", "image/webp"), if (o.has("left")) o.getInt("left") else null, o.optString("layout") == "12",
                    if (o.has("free_left")) o.getInt("free_left") else null)
            } catch (e: IOException) {
                throw Fail("net", 0)
            } finally { c.disconnect() }
        }
    }

    companion object {
        /** The JSON request body: the picture as one base64 line (the Worker's regex has no room for newlines). */
        fun body(png: ByteArray, fields: Map<String, String>): String {
            val o = JSONObject()
            for ((k, v) in fields) o.put(k, v)
            return o.put("image", Base64.encodeToString(png, Base64.NO_WRAP)).toString()
        }

        /** "dev-" + 24 of [a-z0-9], made once per install. */
        fun deviceId(ctx: Context): String {
            val p = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE)
            p.getString("device", null)?.let { return it }
            val abc = "abcdefghijklmnopqrstuvwxyz0123456789"
            val r = SecureRandom()
            val id = "dev-" + (1..24).map { abc[r.nextInt(abc.length)] }.joinToString("")
            p.edit().putString("device", id).apply()
            return id
        }
    }
}
