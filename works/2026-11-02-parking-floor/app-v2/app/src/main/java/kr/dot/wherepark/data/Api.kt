package kr.dot.wherepark.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kr.dot.wherepark.BuildConfig
import kr.dot.wherepark.core.Car
import org.json.JSONArray
import org.json.JSONObject

class ApiException(val status: Int, val error: String) : Exception("$status $error")

/** The only network seam: tests plug a fake. [auth] is "memberId.secret" (Bearer is added here). Throws IOException offline. */
interface Transport {
    fun send(method: String, path: String, auth: String?, body: ByteArray?, contentType: String?): Pair<Int, String?>

    /** GET of a binary body (photos). The default maps a text body back to bytes as ISO-8859-1 (enough for fakes). */
    fun bytes(path: String, auth: String?): Pair<Int, ByteArray?> =
        send("GET", path, auth, null, null).let { (s, t) -> s to t?.toByteArray(Charsets.ISO_8859_1) }
}

object HttpTransport : Transport {
    override fun send(method: String, path: String, auth: String?, body: ByteArray?, contentType: String?): Pair<Int, String?> {
        val c = URL(BuildConfig.SYNC_URL + path).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 8000
            c.readTimeout = if (body != null && body.size > 10_000) 30_000 else 8000
            if (auth != null) c.setRequestProperty("authorization", "Bearer $auth")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("content-type", contentType ?: "application/json")
                c.setFixedLengthStreamingMode(body.size)
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }
            return code to text
        } finally {
            c.disconnect()
        }
    }

    override fun bytes(path: String, auth: String?): Pair<Int, ByteArray?> {
        val c = URL(BuildConfig.SYNC_URL + path).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 30_000
            if (auth != null) c.setRequestProperty("authorization", "Bearer $auth")
            val code = c.responseCode
            return code to (if (code in 200..299) c.inputStream.use { it.readBytes() } else null)
        } finally {
            c.disconnect()
        }
    }
}

/** JPEG under 380 KB: quality 80, 60, then long side 1280, 1024 at quality 60. The smallest try is used if none fits. */
object Photo {
    const val LIMIT = 380_000

    fun shrink(src: Bitmap, limit: Int = LIMIT): ByteArray {
        var last = ByteArray(0)
        for ((side, q) in listOf(0 to 80, 0 to 60, 1280 to 60, 1024 to 60)) {
            val long = maxOf(src.width, src.height)
            val bmp = if (side in 1 until long) Bitmap.createScaledBitmap(src, src.width * side / long, src.height * side / long, true) else src
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
            last = out.toByteArray()
            if (last.size < limit) return last
        }
        return last
    }

    fun bytes(path: String): ByteArray? {
        val f = File(path)
        if (!f.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val out = shrink(bmp)
        if (out.size >= LIMIT) { android.util.Log.w("wp-sync", "photo still ${out.size} B at 1024 px, dropped"); return null }  // never send near the 400 KB cap
        return out
    }
}

object Api {
    @Volatile var transport: Transport = HttpTransport

    private fun call(method: String, path: String, auth: String?, json: JSONObject? = null, bytes: ByteArray? = null, ct: String? = null): Pair<Int, JSONObject?> =
        try {
            val (s, t) = transport.send(method, path, auth, bytes ?: json?.toString()?.toByteArray(), ct)
            s to (t?.let { runCatching { JSONObject(it) }.getOrNull() })
        } catch (_: IOException) {
            0 to null
        }

    private fun ok(r: Pair<Int, JSONObject?>): Result<JSONObject> =
        if (r.first in 200..299) Result.success(r.second ?: JSONObject()) else Result.failure(ApiException(r.first, r.second?.str("error") ?: ""))

    fun state(ctx: Context): Result<ApiState> {
        val auth = GroupStore.auth(ctx) ?: return Result.failure(ApiException(401, "no family"))
        val r = call("GET", "/v3/state", auth)
        if (r.first == 401) GroupStore.dropIfSame(ctx, auth)
        return ok(r).mapCatching { Wire.state(it) }
    }

    fun peek(code: String): Result<Pair<String, Int>> =
        ok(call("POST", "/v3/peek", null, JSONObject().put("code", code))).map { (it.str("groupName") ?: Wire.NO_GROUP_NAME) to it.getInt("count") }

    fun createGroup(ctx: Context, groupName: String, name: String?): Result<String> = enter(ctx, "/v3/groups", JSONObject().put("groupName", groupName).put("name", name))
    fun join(ctx: Context, code: String, name: String?): Result<String> = enter(ctx, "/v3/join", JSONObject().put("code", code).put("name", name))

    private fun enter(ctx: Context, path: String, body: JSONObject): Result<String> {
        val auth = GroupStore.auth(ctx)
        val r = call("POST", path, auth, body)
        if (r.first == 401) GroupStore.dropIfSame(ctx, auth)
        val j = ok(r).getOrElse { return Result.failure(it) }
        val st = Wire.state(j.getJSONObject("state"))
        synchronized(SyncQueue) {
            if (path == "/v3/join" || auth == null) kr.dot.wherepark.family.JoinRequest.cancel(ctx)
            if (auth == null) {
                GroupStore.save(ctx, j.getString("deviceId"), j.getString("secret"), st.me, st.groups)
                CarStore.cars(ctx).forEach { car ->
                    SyncQueue.add(ctx, Op.PutCar(car))
                    ParkStore.latest(ctx, car.id)?.let { SyncQueue.add(ctx, Op.PutPark(it)) }
                }
            }
            Merge.write(ctx, st)
            SyncQueue.invalidatePulls()
        }
        SyncQueue.kick(ctx)
        WidgetRefresh.request(ctx)
        return Result.success(j.getString("groupId"))
    }

    /**
     * A new invite drops the family's previous code on the server, so the current one is kept in prefs (per member) and
     * reused until a minute before it expires; [fresh] = the explicit [새 코드 만들기].
     */
    fun invite(ctx: Context, groupId: String, fresh: Boolean = false, now: Long = System.currentTimeMillis()): Result<Invite> {
        val auth = GroupStore.auth(ctx) ?: return Result.failure(ApiException(401, "no family"))
        val member = "$auth|$groupId"
        val c = Prefs.obj(ctx, "wp_invite")
        if (!fresh && c.str("member") == member && c.optLong("expiresAt") > now + 60_000)
            return Result.success(Invite(c.getString("code"), c.getLong("expiresAt"), c.getString("link")))
        return ok(authed(ctx, auth, "POST", "/v3/groups/$groupId/invites")).map { Invite(it.getString("code"), it.getLong("expiresAt"), it.getString("link")) }
            .onSuccess { Prefs.put(ctx, "wp_invite", JSONObject().put("member", member).put("code", it.code).put("expiresAt", it.expiresAt).put("link", it.link)) }
    }

    /** Join request poll (POST /v3/requests/poll {secret}, no auth): the invite code once a family accepted, null while nobody has (404). */
    fun requestCode(secret: String): Result<Pair<String, String?>?> {
        val r = call("POST", "/v3/requests/poll", null, JSONObject().put("secret", secret))
        return if (r.first == 404) Result.success(null) else ok(r).map { it.getString("code") to it.str("byName") }  // byName = who accepted
    }

    /** Accepts a join request ([id] = the link's sha256 of the requester's secret); the server makes its invite. 409 = accepted already. */
    fun putRequest(ctx: Context, id: String, groupId: String): Result<Unit> {
        val auth = GroupStore.auth(ctx) ?: return Result.failure(ApiException(401, "no family"))
        return ok(authed(ctx, auth, "PUT", "/v3/requests", JSONObject().put("req", id).put("groupId", groupId))).map { }
    }

    /** The requester withdraws its request (row + its invite); best effort, the result is ignored by callers. */
    fun cancelRequest(secret: String): Result<Unit> = ok(call("POST", "/v3/requests/cancel", null, JSONObject().put("secret", secret))).map { }

    /** true only when the server stored the token (2xx while joined); callers keep the pull fallback otherwise. */
    fun setFcmToken(ctx: Context, token: String?): Boolean {
        val auth = GroupStore.auth(ctx) ?: return false
        if (token == null) return false
        return authed(ctx, auth, "PUT", "/v3/token", JSONObject().put("fcmToken", token)).first in 200..299
    }

    /** A family member's photo for [parkId] (GET /v3/cars/{id}/photo?park=). null = none (404), offline or refused. */
    fun photo(ctx: Context, carId: String, parkId: Long): ByteArray? {
        val auth = GroupStore.auth(ctx) ?: return null
        val (status, body) = try { transport.bytes("/v3/cars/$carId/photo?park=$parkId", auth) } catch (_: IOException) { return null }
        if (status == 401) GroupStore.dropIfSame(ctx, auth)
        return if (status in 200..299) body?.takeIf { it.isNotEmpty() } else null
    }

    fun removeMember(ctx: Context, groupId: String, id: String): Result<Unit> {
        val auth = GroupStore.auth(ctx) ?: return Result.failure(ApiException(401, "no family"))
        return ok(authed(ctx, auth, "DELETE", "/v3/groups/$groupId/members/$id")).map { }
    }

    fun leaveGroup(ctx: Context, groupId: String): Result<Boolean> {
        val auth = GroupStore.auth(ctx) ?: return Result.failure(ApiException(401, "auth"))
        return ok(authed(ctx, auth, "DELETE", "/v3/groups/$groupId/me")).map { j ->
            val gone = j.optString("device") == "gone"
            if (gone) GroupStore.leave(ctx) else {
                GroupStore.setState(ctx, GroupStore.me(ctx), GroupStore.groups(ctx).filterNot { it.id == groupId })
                SyncQueue.invalidatePulls()
                SyncEngine.pull(ctx)
            }
            gone
        }
    }
    fun renameGroup(ctx: Context, groupId: String, name: String): Result<Unit> {
        val auth = GroupStore.auth(ctx) ?: return Result.failure(ApiException(401, "auth"))
        return ok(authed(ctx, auth, "PUT", "/v3/groups/$groupId", JSONObject().put("name", name))).map { }
    }

    /** Leaves the family; on success the local family state and queue are wiped (cars and records stay). */
    fun deleteMe(ctx: Context): Result<Unit> {
        val auth = GroupStore.auth(ctx) ?: return Result.failure(ApiException(401, "no family"))
        val r = ok(call("DELETE", "/v3/me", auth)).map { }
        if (r.isSuccess) GroupStore.leave(ctx)
        return r
    }

    private fun authed(ctx: Context, auth: String, method: String, path: String, json: JSONObject? = null): Pair<Int, JSONObject?> {
        val r = call(method, path, auth, json)
        if (r.first == 401) GroupStore.dropIfSame(ctx, auth)
        return r
    }

    fun exec(ctx: Context, op: Op): Pair<Int, JSONObject?> = exec(ctx, op, GroupStore.auth(ctx))

    /** Sends one op with the given [auth] (the worker passes the auth it captured, so a 401 can be checked against it). Status 0 = offline. */
    fun exec(ctx: Context, op: Op, auth: String?): Pair<Int, JSONObject?> {
        val c = "/v3/cars/${op.carId}"
        return when (op) {
            is Op.PutCar -> call("PUT", c, auth, JSONObject().put("car", Wire.carJson(op.car, local = false)))
            is Op.DelCar -> call("DELETE", c, auth)
            is Op.PutPark -> call("PUT", "$c/park", auth, JSONObject().put("park", Wire.parkJson(op.park, false)))
            is Op.Undo -> call("POST", "$c/undo", auth, JSONObject().put("parkId", op.parkId))
            is Op.Share -> call(if (op.on) "PUT" else "DELETE", "$c/groups/${op.groupId}", auth)
            is Op.State -> call("POST", "$c/state", auth, JSONObject().put("state", op.state.name).put("parkId", op.parkId ?: ParkStore.latest(ctx, op.carId)?.id).also { j -> op.atMs?.let { j.put("atMs", it) } })
            is Op.PutPhoto -> {
                val bytes = Photo.bytes(op.file) ?: return 404 to null
                call("PUT", "$c/photo?park=${op.parkId}", auth, bytes = bytes, ct = "image/jpeg")
            }
        }
    }
}
