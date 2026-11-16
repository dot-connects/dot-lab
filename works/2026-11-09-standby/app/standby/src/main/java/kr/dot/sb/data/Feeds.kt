package kr.dot.sb.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL

/** Network feeds (blocking: call from Dispatchers.IO). The PC usage feed lives in the personal flavor (UsageFeeds). */
object Feeds {
    fun onWifi(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    /** An active network that reached the internet (Wi-Fi or mobile data): the feeds' NO_NETWORK test. */
    fun online(ctx: Context): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /** Skip a fetch as NO_NETWORK? A feed with a home-LAN route ([lan]) still tries it on Wi-Fi that did not validate. */
    fun noNetwork(validated: Boolean, wifi: Boolean, lan: Boolean): Boolean = !validated && !(lan && wifi)

    fun weatherText(lat: Double, lon: Double): String = get(WeatherParser.url(lat, lon), 8000, null)

    internal fun get(url: String, timeoutMs: Int, auth: String?): String {
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
