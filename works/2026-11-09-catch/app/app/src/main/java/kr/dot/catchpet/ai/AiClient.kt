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
class AiClient(private val base: String, private val deviceId: String) {
    /** code = the Worker's error field, or "net" when it couldn't be reached. */
    class Fail(val code: String, val status: Int) : Exception("$status $code") {
        val text get() = aiMessage(code, status)
    }

    /** image = the decoded picture bytes (webp), left = what the Worker says is left (previews today / sheet retries). */
    class Reply(val image: ByteArray, val mime: String, val left: Int?)

    suspend fun preview(png: ByteArray, kind: String, style: String): Result<Reply> =
        post("/transform", body(png, mapOf("style" to style, "kind" to kind, "device" to deviceId)), null)

    suspend fun sheet(png: ByteArray, kind: String, entry: String, token: String): Result<Reply> =
        post("/sheet", body(png, mapOf("kind" to kind, "device" to deviceId, "entry" to entry)), token)

    private suspend fun post(path: String, json: String, token: String?): Result<Reply> = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection)
            try {
                c.requestMethod = "POST"
                c.connectTimeout = 30_000
                c.readTimeout = 200_000
                c.doOutput = true
                c.setRequestProperty("content-type", "application/json")
                c.setRequestProperty("x-app", "catchpet")
                token?.let { c.setRequestProperty("x-purchase", it) }
                val status = try {
                    c.outputStream.use { it.write(json.toByteArray()) }
                    c.responseCode
                } catch (e: IOException) { throw Fail("net", 0) }
                val text = (if (status in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                val o = runCatching { JSONObject(text) }.getOrNull()
                if (status !in 200..299 || o == null || !o.has("image")) throw Fail(o?.optString("error")?.ifEmpty { null } ?: "http", status)
                Reply(Base64.decode(o.getString("image"), Base64.DEFAULT), o.optString("mime", "image/webp"), if (o.has("left")) o.getInt("left") else null)
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
