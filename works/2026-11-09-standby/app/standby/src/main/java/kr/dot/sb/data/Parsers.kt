package kr.dot.sb.data

import kr.dot.sb.ui.state.HourTemp
import kr.dot.sb.ui.state.UsageInfo
import kr.dot.sb.ui.state.UsageWindow
import kr.dot.sb.ui.state.WeatherCondition
import kr.dot.sb.ui.state.WeatherInfo
import org.json.JSONObject
import kotlin.math.roundToInt

/** status.json of ai-hub (shape copied from 눈금's Status.kt; "pct" is the share USED). */
object UsageParser {
    fun parse(text: String, fetchedAtSec: Long): UsageInfo {
        val limits = JSONObject(text).optJSONObject("limits")
        fun win(acct: String, key: String): UsageWindow? {
            val w = limits?.optJSONObject(acct)?.optJSONObject(key) ?: return null
            if (!w.has("pct") || w.isNull("pct")) return null
            val reset = if (!w.has("reset") || w.isNull("reset")) null else w.getLong("reset")
            return UsageWindow(w.getDouble("pct").roundToInt().coerceIn(0, 100), reset)
        }
        return UsageInfo(win("claude", "5h"), win("claude", "7d"), win("codex", "5h"), win("codex", "7d"), fetchedAtSec)
    }

    /** The PC's home address advertised in the status text ("lan"), or null. */
    fun lanUrl(text: String): String? = JSONObject(text).optString("lan").ifEmpty { null }

    /** http://<private IPv4 literal>:port/... only: the LAN token goes there. */
    fun isPrivateHttp(url: String): Boolean {
        val m = Regex("""^http://(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(:\d+)?/""").find(url) ?: return false
        val p = m.groupValues.drop(1).take(4).map { it.toInt() }
        if (p.any { it > 255 }) return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }
}

object Wmo {
    fun condition(code: Int, isDay: Boolean): WeatherCondition = when (code) {
        0, 1 -> if (isDay) WeatherCondition.CLEAR else WeatherCondition.NIGHT_CLEAR
        2 -> WeatherCondition.PARTLY
        3 -> WeatherCondition.CLOUDY
        45, 48 -> WeatherCondition.FOG
        in 51..67, in 80..82 -> WeatherCondition.RAIN
        in 71..77, 85, 86 -> WeatherCondition.SNOW
        in 95..99 -> WeatherCondition.THUNDER
        else -> WeatherCondition.CLOUDY
    }
}

object WeatherParser {
    fun url(lat: Double, lon: Double) =
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=2"

    fun parse(body: String, city: String, fetchedAtSec: Long): WeatherInfo {
        val j = JSONObject(body)
        val cur = j.getJSONObject("current")
        val h = j.getJSONObject("hourly")
        val times = h.getJSONArray("time"); val temps = h.getJSONArray("temperature_2m")
        val codes = h.getJSONArray("weather_code"); val days = h.getJSONArray("is_day")
        val now = cur.getString("time") // local "2026-11-09T14:15", same zone as hourly (timezone=auto)
        val next = (0 until times.length()).filter { times.getString(it) > now }.take(5).map {
            HourTemp(
                times.getString(it).substring(11, 13).toInt(), temps.getDouble(it).roundToInt(),
                Wmo.condition(codes.getInt(it), days.getInt(it) == 1),
            )
        }
        val d = j.getJSONObject("daily")
        return WeatherInfo(
            city, cur.getDouble("temperature_2m").roundToInt(),
            Wmo.condition(cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1),
            d.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            d.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(), next, fetchedAtSec,
        )
    }
}
