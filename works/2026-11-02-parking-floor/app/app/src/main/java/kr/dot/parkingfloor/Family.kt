package kr.dot.parkingfloor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Family sharing through the dot-lab-parking Worker: one car, a few phones. A phone uploads its park after
 * every change; the others are woken by FCM (when Firebase is configured) or pull when the app opens.
 * Only floor, pillar, colour and the building name travel; never coordinates.
 */
object Family {
    private val io = Executors.newSingleThreadExecutor()
    private const val CHANNEL = "family"

    data class Member(val id: String, val name: String, val me: Boolean)

    private fun prefs(context: Context) = context.getSharedPreferences("family", Context.MODE_PRIVATE)

    fun joined(context: Context): Boolean = prefs(context).getString("token", null) != null
    fun myName(context: Context): String? = prefs(context).getString("name", null)
    private fun token(context: Context): String? = prefs(context).getString("token", null)

    fun members(context: Context): List<Member> = try {
        val arr = org.json.JSONArray(prefs(context).getString("members", "[]"))
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { Member(it.getString("id"), it.getString("name"), it.optBoolean("me")) }
    } catch (_: Exception) {
        emptyList()
    }

    private class HttpError(val status: Int, val error: String) : Exception("$status $error")

    /** Authenticated call; a 401 means this phone was removed from the family, so the membership is dropped. */
    private fun authed(context: Context, method: String, path: String, body: JSONObject?): JSONObject {
        try {
            return call(method, path, body, token(context))
        } catch (e: HttpError) {
            if (e.status == 401) {
                prefs(context).edit().clear().apply()
                DriveLog.add(context, "family: removed, membership cleared")
            }
            throw e
        }
    }

    private fun call(method: String, path: String, body: JSONObject?, auth: String?): JSONObject {
        val c = URL(BuildConfig.SYNC_URL + path).openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("content-type", "application/json")
        if (auth != null) c.setRequestProperty("authorization", "Bearer $auth")
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
        c.disconnect()
        val json = if (text.isBlank()) JSONObject() else JSONObject(text)
        if (code !in 200..299) throw HttpError(code, json.optString("error"))
        return json
    }

    private fun saveJoin(context: Context, name: String, r: JSONObject) {
        prefs(context).edit().putString("token", r.getString("token")).putString("memberId", r.getString("memberId"))
            .putString("groupId", r.getString("groupId")).putString("name", name).apply()
    }

    /** Runs [work] off the main thread and reports a Korean message (or null on success) on the main thread. */
    private fun background(context: Context, work: () -> Unit, done: (String?) -> Unit) {
        val main = android.os.Handler(context.mainLooper)
        io.execute {
            val error = try {
                work()
                null
            } catch (e: HttpError) {
                when (e.error) {
                    "code" -> "초대 코드가 틀렸거나 만료됐어요"
                    "full" -> "가족이 이미 8명이에요"
                    "slow" -> "잠시 뒤에 다시 해 주세요"
                    "auth" -> "가족에서 빠진 상태예요"
                    else -> "서버 오류 (${e.status})"
                }
            } catch (_: Exception) {
                "인터넷 연결을 확인해 주세요"
            }
            main.post { done(error) }
        }
    }

    fun create(context: Context, name: String, done: (String?) -> Unit) = background(context, {
        saveJoin(context, name, call("POST", "/v1/groups", JSONObject().put("name", name), null))
        afterJoin(context)
    }, done)

    fun join(context: Context, code: String, name: String, done: (String?) -> Unit) = background(context, {
        saveJoin(context, name, call("POST", "/v1/join", JSONObject().put("code", code).put("name", name), null))
        afterJoin(context)
    }, done)

    /** Membership is already saved here; a failure below is caught up by the next pull or change. */
    private fun afterJoin(context: Context) = try {
        PushSetup.token(context)?.let { setTokenNow(context, it) }
        pullNow(context)
        ParkStore.latest(context)?.takeIf { !it.remote }?.let { pushNow(context, it) }
        Unit
    } catch (_: Exception) {
        prefs(context).edit().putBoolean("dirty", true).apply()
    }

    fun invite(context: Context, done: (String?, String?) -> Unit) {
        var code: String? = null
        background(context, { code = authed(context, "POST", "/v1/invites", null).getString("code") }) { err -> done(code, err) }
    }

    fun leave(context: Context, done: (String?) -> Unit) = background(context, {
        val me = prefs(context).getString("memberId", null)
        try {
            if (me != null) call("DELETE", "/v1/members/$me", null, token(context))
        } catch (_: HttpError) {
        }
        prefs(context).edit().clear().apply()
    }, done)

    fun setToken(context: Context, fcmToken: String) {
        if (!joined(context)) return
        io.execute { try { setTokenNow(context, fcmToken) } catch (_: Exception) { } }
    }

    private fun setTokenNow(context: Context, fcmToken: String) {
        authed(context, "PUT", "/v1/token", JSONObject().put("fcmToken", fcmToken))
    }

    /** Uploads the latest park after any change to it. Fire and forget; a later pull or change catches up. */
    fun changed(context: Context) {
        if (!joined(context)) return
        val app = context.applicationContext
        io.execute {
            try {
                ParkStore.latest(app)?.let { park ->
                    val push = pushNow(app, park).optJSONObject("push")
                    val woke = push?.optInt("sent") ?: 0
                    val failed = push?.optInt("failed") ?: 0
                    DriveLog.add(app, "family upload ok, woke $woke" + if (failed > 0) ", failed $failed (${push?.optString("reason")})" else "")
                }
                prefs(app).edit().putBoolean("dirty", false).apply()
            } catch (e: Exception) {
                if (joined(app)) prefs(app).edit().putBoolean("dirty", true).apply()
                DriveLog.add(app, "family upload failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun pushNow(context: Context, p: Park): JSONObject {
        val park = JSONObject().put("id", p.id).put("atMs", p.atMs).put("floor", p.floor).put("est", p.est).put("alt", p.alt)
            .put("confirmed", p.confirmed).put("pillar", p.pillar).put("color", p.color).put("placeName", p.placeName)
        return authed(context, "PUT", "/v1/park", JSONObject().put("park", park))
    }

    fun pull(context: Context, done: () -> Unit) {
        if (!joined(context)) return
        val app = context.applicationContext
        background(app, { pullNow(app) }) { done() }
    }

    /** Fetches the family's latest park and merges it. Returns the park when it is new or changed here. */
    fun pullNow(context: Context): Park? {
        // an edit that could not be uploaded goes up first, so the pull cannot overwrite it
        if (prefs(context).getBoolean("dirty", false)) {
            ParkStore.latest(context)?.let { pushNow(context, it) }
            prefs(context).edit().putBoolean("dirty", false).apply()
        }
        val r = authed(context, "GET", "/v1/park", null)
        r.optJSONArray("members")?.let { prefs(context).edit().putString("members", it.toString()).apply() }
        val o = r.optJSONObject("park") ?: return null
        fun int(k: String) = if (o.isNull(k)) null else o.getInt(k)
        val remote = Park(
            id = o.getLong("id"), atMs = o.getLong("atMs"), placeKey = null,
            placeName = if (o.isNull("placeName")) null else o.getString("placeName"),
            lat = null, lng = null, deltaM = null, est = int("est"), alt = int("alt"),
            confirmed = o.optBoolean("confirmed"), floor = int("floor"), pillar = o.optString("pillar"), color = o.optInt("color"),
            by = if (o.optBoolean("mine")) null else o.optString("by"), remote = !o.optBoolean("mine"),
        )
        val changed = ParkStore.merge(context, remote)
        if (changed) ParkWidget.refresh(context)
        return if (changed) remote else null
    }

    /** A short notification when someone else in the family parked or changed the park. */
    fun notify(context: Context, park: Park) {
        if (!park.remote) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "가족 주차", NotificationManager.IMPORTANCE_DEFAULT))
        val floor = park.floor?.let(FloorMath::labelOf) ?: "층 모름"
        val state = if (park.confirmed) floor else "${floor}쯤"
        val open = PendingIntent.getActivity(context, 20, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val note = android.app.Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("${park.by ?: "가족"} · $state ${park.pillar}".trim())
            .setContentText(park.placeName ?: "주차 위치가 바뀌었어요")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(21, note)
        } catch (_: SecurityException) {
        }
    }
}
