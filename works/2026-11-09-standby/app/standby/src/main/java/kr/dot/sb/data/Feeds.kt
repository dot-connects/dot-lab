package kr.dot.sb.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kr.dot.sb.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Network feeds (blocking: call from Dispatchers.IO). Fetch order copied from 눈금's Fetcher: home LAN first, else Telegram. */
object Feeds {
    fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** Raw status.json text. Throws when neither route works. */
    fun usageText(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("sb_feed", Context.MODE_PRIVATE)
        val lan = prefs.getString("lan", null)
        val errors = mutableListOf<String>()
        // token only over Wi-Fi to a private IPv4 literal (same rule as 눈금); never over mobile data
        if (lan != null && onWifi(ctx) && BuildConfig.LAN_TOKEN.isNotEmpty() && UsageParser.isPrivateHttp(lan)) {
            try { return get(lan, 1500, "Bearer " + BuildConfig.LAN_TOKEN) } catch (e: Exception) { errors += "lan:${e.javaClass.simpleName}" }
        }
        if (BuildConfig.STATUS_BOT_TOKEN.isEmpty()) throw IllegalStateException("no bot token " + errors.joinToString())
        try {
            val chat = get(
                "https://api.telegram.org/bot${BuildConfig.STATUS_BOT_TOKEN}/getChat?chat_id=" +
                    URLEncoder.encode(BuildConfig.STATUS_CHAT_ID, "UTF-8"), 8000, null,
            )
            val text = JSONObject(chat).getJSONObject("result").getJSONObject("pinned_message").getString("text")
            // remember the PC's home address, but only a private-network one: the token goes there
            UsageParser.lanUrl(text)?.takeIf { UsageParser.isPrivateHttp(it) }?.let { prefs.edit().putString("lan", it).apply() }
            return text
        } catch (e: Exception) {
            errors += "tg:${e.javaClass.simpleName}"
        }
        throw IllegalStateException(errors.joinToString(" "))
    }

    fun weatherText(lat: Double, lon: Double): String = get(WeatherParser.url(lat, lon), 8000, null)

    private fun get(url: String, timeoutMs: Int, auth: String?): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = false
        auth?.let { conn.setRequestProperty("Authorization", it) }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
