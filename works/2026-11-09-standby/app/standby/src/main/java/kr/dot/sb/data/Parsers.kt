package kr.dot.sb.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kr.dot.sb.ui.state.DayTemp
import kr.dot.sb.ui.state.HourTemp
import kr.dot.sb.ui.state.WeatherCondition
import kr.dot.sb.ui.state.WeatherInfo
import org.json.JSONObject
import kotlin.math.roundToInt

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
            "&daily=temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset&timezone=auto&forecast_days=6"

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
        val hi = d.getJSONArray("temperature_2m_max"); val lo = d.getJSONArray("temperature_2m_min")
        val dTimes = d.optJSONArray("time"); val dCodes = d.optJSONArray("weather_code")
        fun sun(key: String): LocalTime? = d.optJSONArray(key)?.optString(0)?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDateTime.parse(it).toLocalTime() }.getOrNull() }
        // the next days after today (spec-r9 weather: up to 5 day rows); older payloads without codes give none
        val week = if (dTimes == null || dCodes == null) emptyList() else (1 until dTimes.length()).map {
            DayTemp(LocalDate.parse(dTimes.getString(it)), hi.getDouble(it).roundToInt(), lo.getDouble(it).roundToInt(), Wmo.condition(dCodes.getInt(it), true))
        }
        return WeatherInfo(
            city, cur.getDouble("temperature_2m").roundToInt(),
            Wmo.condition(cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1),
            hi.getDouble(0).roundToInt(), lo.getDouble(0).roundToInt(), next, fetchedAtSec, days = week,
            sunrise = sun("sunrise"), sunset = sun("sunset"),
        )
    }
}
