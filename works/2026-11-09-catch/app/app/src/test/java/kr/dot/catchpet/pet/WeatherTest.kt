package kr.dot.catchpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    @Test fun codesMapToStates() {
        assertEquals("snow", Weather.state(73, -2.0, null, null))
        assertEquals("snow", Weather.state(86, 1.0, 200.0, 90.0))      // falling snow beats dust
        assertEquals("rain", Weather.state(61, 12.0, null, null))
        assertEquals("rain", Weather.state(53, 12.0, null, null))      // drizzle
        assertEquals("rain", Weather.state(81, 30.0, null, null))      // showers beat heat
        assertEquals("rain", Weather.state(95, 24.0, null, null))      // thunder
        assertEquals("dust", Weather.state(2, 18.0, 81.0, 10.0))       // PM10 나쁨 starts at 81
        assertEquals("dust", Weather.state(0, 18.0, 20.0, 36.0))       // PM2.5 나쁨 starts at 36
        assertEquals("sun", Weather.state(0, 18.0, 80.0, 35.0))
        assertEquals("hot", Weather.state(1, 28.0, null, null))
        assertEquals("cold", Weather.state(3, 5.0, null, null))
        assertEquals("sun", Weather.state(3, 5.1, null, null))         // clouds count as sun
        assertEquals("sun", Weather.state(45, 15.0, null, null))       // fog too
    }

    @Test fun parsesOpenMeteo() {
        val f = """{"latitude":37.56,"current":{"time":"2026-11-28T15:00","interval":900,"temperature_2m":3.4,"weather_code":2}}"""
        assertEquals("cold", Weather.parse(f, null))
        assertEquals("dust", Weather.parse(f, """{"current":{"pm10":95.1,"pm2_5":40.2}}"""))
        assertEquals("cold", Weather.parse(f, """{"current":{"pm10":null,"pm2_5":null}}"""))
        assertEquals("cold", Weather.parse(f, "not json"))   // the air answer fails alone
    }

    @Test fun asksAtMostEveryThreeHours() {
        val h = 3_600_000L
        val t0 = 1_800_000_000_000L
        assertTrue(Weather.due(0L, t0))                      // never asked
        assertFalse(Weather.due(t0, t0 + 1))
        assertFalse(Weather.due(t0, t0 + 3 * h - 1))
        assertTrue(Weather.due(t0, t0 + 3 * h))
        assertTrue(Weather.due(t0, t0 - 1))                  // clock moved back
    }
}
