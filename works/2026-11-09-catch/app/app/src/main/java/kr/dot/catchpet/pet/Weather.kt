package kr.dot.catchpet.pet

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * The home pet's weather (phase C, mockup next7v4 screen 7): coarse location, taken only while the app is open, and
 * Open-Meteo (no key) asked at most once every 3 hours by WorkManager. Six states; the last answer is cached in prefs.
 */
object Weather {
    /** key, label, the one-line bubble while the act plays (mockup wx.js STATES). */
    class State(val key: String, val label: String, val line: String)
    val STATES = listOf(
        State("sun", "맑음", "햇볕 좋다~ 광합성 중"), State("hot", "더움", "더워… 녹는다"), State("cold", "추움", "추워! 목도리 꽁꽁"),
        State("snow", "눈", "눈 온다! 뽀득뽀득"), State("rain", "비", "비 와! 우산 챙겨"), State("dust", "미세먼지", "미세먼지 나빠… 마스크!"))
    fun of(key: String?): State? = STATES.firstOrNull { it.key == key }

    const val EVERY_MS = 3 * 3_600_000L
    /** An answer older than this is not shown any more (the phone was off, or the network gone, for half a day). */
    const val FRESH_MS = 12 * 3_600_000L

    /**
     * WMO weather code + temperature (°C) + particulates (µg/m³, null when unknown) -> state. Falling snow or rain
     * first, then dust at the Korean "나쁨" line (PM10 81+, PM2.5 36+), then heat and cold, else sun (clouds too).
     */
    fun state(code: Int, tempC: Double, pm10: Double?, pm25: Double?): String = when {
        code in 71..77 || code == 85 || code == 86 -> "snow"
        code in 51..67 || code in 80..82 || code in 95..99 -> "rain"
        (pm25 ?: 0.0) >= 36.0 || (pm10 ?: 0.0) >= 81.0 -> "dust"
        tempC >= 28.0 -> "hot"
        tempC <= 5.0 -> "cold"
        else -> "sun"
    }

    /** Time to ask again: never asked, 3 hours since the last try, or the clock went backwards. */
    fun due(lastTryMs: Long, nowMs: Long): Boolean = lastTryMs <= 0L || nowMs < lastTryMs || nowMs - lastTryMs >= EVERY_MS

    /** Open-Meteo answers -> state; the air answer may be missing (it fails on its own). */
    fun parse(forecast: String, air: String?): String {
        val c = JSONObject(forecast).getJSONObject("current")
        val a = air?.let { runCatching { JSONObject(it).getJSONObject("current") }.getOrNull() }
        fun num(o: JSONObject?, k: String) = o?.takeIf { it.has(k) && !it.isNull(k) }?.getDouble(k)
        return state(c.getInt("weather_code"), c.getDouble("temperature_2m"), num(a, "pm10"), num(a, "pm2_5"))
    }

    // --- prefs ---
    private fun p(ctx: Context) = PetPrefs.prefs(ctx)
    fun on(ctx: Context) = p(ctx).getBoolean("wx_on", false)
    fun granted(ctx: Context) = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** On: schedule the 3-hour fetch and take the location now. Off: cancel and forget the location and the answer. */
    fun setOn(ctx: Context, on: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (on) {
            p(ctx).edit().putBoolean("wx_on", true).apply()
            val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
            wm.enqueueUniquePeriodicWork("wx", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<Fetch>(EVERY_MS, TimeUnit.MILLISECONDS).setConstraints(c).build())
            locate(ctx)
        } else {
            wm.cancelUniqueWork("wx"); wm.cancelUniqueWork("wx-now")
            p(ctx).edit().putBoolean("wx_on", false).remove("wx_lat").remove("wx_lon").remove("wx_state").remove("wx_at").remove("wx_try_at").apply()
        }
    }

    /** The cached state, or null when off, never fetched, or stale. */
    fun now(ctx: Context, nowMs: Long = System.currentTimeMillis()): State? {
        if (!on(ctx)) return null
        val at = p(ctx).getLong("wx_at", 0L)
        if (at <= 0L || nowMs - at > FRESH_MS || nowMs < at) return null
        return of(p(ctx).getString("wx_state", null))
    }

    /** What was last played and on which day (the act plays on a new state or on the first home visit of a day). */
    fun played(ctx: Context): Pair<String?, Long> = p(ctx).getString("wx_played", null) to p(ctx).getLong("wx_played_day", -1L)
    fun setPlayed(ctx: Context, key: String, day: Long) { p(ctx).edit().putString("wx_played", key).putLong("wx_played_day", day).apply() }

    /**
     * Takes the coarse location (only while the app is in front: no background location) and keeps it on the phone,
     * rounded to 0.01° (about 1 km). A moved location asks for the weather at once (still behind the 3-hour throttle).
     */
    @SuppressLint("MissingPermission")
    fun locate(ctx: Context) {
        if (!on(ctx) || !granted(ctx)) return
        val app = ctx.applicationContext
        val lm = app.getSystemService(LocationManager::class.java) ?: return
        val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return
        runCatching {
            lm.getCurrentLocation(provider, null, app.mainExecutor) { loc ->
                val l = loc ?: runCatching { lm.getLastKnownLocation(provider) }.getOrNull() ?: return@getCurrentLocation
                val lat = (l.latitude * 100).roundToInt() / 100f; val lon = (l.longitude * 100).roundToInt() / 100f
                p(app).edit().putFloat("wx_lat", lat).putFloat("wx_lon", lon).apply()
                fetchSoon(app)
            }
        }.onFailure { Log.w("Weather", "locate", it) }
    }

    private fun fetchSoon(ctx: Context) {
        val c = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("wx-now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<Fetch>().setConstraints(c).build())
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 10_000
        try {
            check(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    /** One Open-Meteo ask, if due. Skipped while the pet is not on screen (nobody would see it) or weather is off. */
    class Fetch(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
        override fun doWork(): Result {
            val ctx = applicationContext
            val pr = p(ctx)
            if (!on(ctx) || !PetPrefs.overlayOn(ctx) || !pr.contains("wx_lat")) return Result.success()
            val now = System.currentTimeMillis()
            if (!due(pr.getLong("wx_try_at", 0L), now)) return Result.success()
            pr.edit().putLong("wx_try_at", now).apply()   // a failure waits the 3 hours too
            val q = "latitude=${pr.getFloat("wx_lat", 0f)}&longitude=${pr.getFloat("wx_lon", 0f)}"
            runCatching {
                val f = get("https://api.open-meteo.com/v1/forecast?$q&current=temperature_2m,weather_code")
                val a = runCatching { get("https://air-quality-api.open-meteo.com/v1/air-quality?$q&current=pm10,pm2_5") }.getOrNull()
                pr.edit().putString("wx_state", parse(f, a)).putLong("wx_at", now).apply()
            }.onFailure { Log.w("Weather", "fetch", it) }
            return Result.success()
        }
    }
}
