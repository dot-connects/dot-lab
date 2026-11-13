package kr.dot.sb.data

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherSunTest {
    private val fixture = """{"current":{"time":"2026-11-09T14:15","temperature_2m":10,"weather_code":0,"is_day":1},
        "hourly":{"time":[],"temperature_2m":[],"weather_code":[],"is_day":[]},
        "daily":{"time":["2026-11-09"],"temperature_2m_max":[11],"temperature_2m_min":[2],
        "weather_code":[0],"sunrise":["2026-11-09T07:05"],"sunset":["2026-11-09T17:25"]}}"""

    @Test fun sunriseAndSunsetComeFromToday() {
        val weather = WeatherParser.parse(fixture, "Seoul", 100)
        assertEquals(LocalTime.of(7, 5), weather.sunrise)
        assertEquals(LocalTime.of(17, 25), weather.sunset)
    }

    @Test fun missingSunTimesStayNull() {
        val body = fixture.replace(",\"sunrise\":[\"2026-11-09T07:05\"],\"sunset\":[\"2026-11-09T17:25\"]", "")
        val weather = WeatherParser.parse(body, "Seoul", 100)
        assertNull(weather.sunrise)
        assertNull(weather.sunset)
    }
}
