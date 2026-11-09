package kr.dot.sb.platform

import kr.dot.sb.data.AlarmActions
import kr.dot.sb.data.BurnIn
import kr.dot.sb.data.DisplayOffMachine
import kr.dot.sb.data.MediaKinds
import kr.dot.sb.data.MotionDetector
import kr.dot.sb.data.LuxMedian
import kr.dot.sb.data.NightRamp
import kr.dot.sb.data.NightRules
import kr.dot.sb.data.NightSwitch
import kr.dot.sb.data.NoiseGen
import kr.dot.sb.data.NotifKind
import kr.dot.sb.data.NotifRules
import kr.dot.sb.data.PomodoroTimer
import kr.dot.sb.data.RotationTracker
import kr.dot.sb.data.UsageParser
import kr.dot.sb.data.WeatherParser
import kr.dot.sb.data.Wmo
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.NightMode
import kr.dot.sb.model.SleepSound
import kr.dot.sb.ui.state.MediaKind
import kr.dot.sb.ui.state.TimerMode
import kr.dot.sb.ui.state.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicTest {
    @Test fun nightNeedsFiveDarkSecondsAndLeavesAfterOneAndAHalfBright() {
        val n = NightSwitch()
        assertFalse(n.update(0, 2f))
        assertEquals(5000L - 3000, n.pendingMs(3000))
        assertFalse(n.update(4999, 2f))
        assertTrue(n.update(5000, 2f))
        assertTrue(n.update(6000, 5f))      // between thresholds: stays night
        assertTrue(n.update(7000, 9f))
        assertFalse(n.update(8500, 9f))
        assertFalse(n.update(9000, 5f))
    }

    @Test fun aBrightBlipResetsTheNightHold() {
        val n = NightSwitch()
        n.update(0, 1f); n.update(3000, 20f)
        assertFalse(n.update(6000, 1f))      // hold restarted at 6000
        assertTrue(n.update(11000, 1f))
    }

    @Test fun nightRampTakesOneSecondInAndPointSixOut() {
        assertEquals(0.5f, NightRamp.fraction(0f, 1f, 500), 1e-4f)
        assertEquals(1f, NightRamp.fraction(0f, 1f, 5000), 0f)
        assertEquals(0.5f, NightRamp.fraction(1f, 0f, 300), 1e-4f)
        assertTrue(NightRamp.scheduledNight(23)); assertTrue(NightRamp.scheduledNight(6)); assertFalse(NightRamp.scheduledNight(7))
    }

    @Test fun autoOffNeedsIdleAndDarkAndWakesOnLight() {
        val d = DisplayOffMachine()
        d.touch(0)
        assertFalse(d.evaluate(19_999, DisplayOff.AUTO, 1f, 3f, 8f))
        assertFalse(d.evaluate(20_000, DisplayOff.AUTO, 50f, 3f, 8f))   // bright room: stays on
        assertTrue(d.evaluate(20_000, DisplayOff.AUTO, 2f, 3f, 8f))
        assertTrue(d.evaluate(30_000, DisplayOff.AUTO, 5f, 3f, 8f))     // between: stays off
        assertFalse(d.evaluate(31_000, DisplayOff.AUTO, 9f, 3f, 8f))    // lux >= 8 wakes
    }

    @Test fun after20sIgnoresLightAndNeverNeverTurnsOff() {
        val d = DisplayOffMachine(); d.touch(0)
        assertTrue(d.evaluate(20_000, DisplayOff.AFTER_20S, 500f, 3f, 8f))
        d.touch(25_000)
        assertFalse(d.evaluate(60_000, DisplayOff.NEVER, 0f, 3f, 8f))
        assertEquals(15_000L, d.idleLeftMs(30_000))
    }

    @Test fun burnInWalksTheGridWithoutJumps() {
        val pts = (0..40).map { BurnIn.at(it) }
        assertEquals(9, pts.toSet().size)
        pts.zipWithNext().forEach { (a, b) ->
            assertTrue(Math.abs(a.first - b.first) <= 3 && Math.abs(a.second - b.second) <= 3)
            assertTrue(a != b)
        }
        assertTrue(pts.all { it.first in -3..3 && it.second in -3..3 })
    }

    @Test fun pickUpNeedsThreeHundredMsOfShaking() {
        val m = MotionDetector()
        var t = 0L
        repeat(50) { m.update(t, 0f, 0f, 9.8f); t += 60 }
        assertFalse(m.pickedUp)
        m.update(t, 12f, 0f, 9.8f); t += 100
        assertFalse(m.pickedUp)
        m.update(t, 12f, 0f, 9.8f); t += 150
        m.update(t, 12f, 0f, 9.8f); t += 100
        m.update(t, 12f, 0f, 9.8f)
        assertTrue(m.pickedUp)
    }

    @Test fun flatNeedsOneSecondAndUprightNeedsOneSecond() {
        val m = MotionDetector()
        var t = 0L
        repeat(40) { m.update(t, 0f, 0f, 9.8f); t += 50 }
        assertTrue(m.isFlat)
        repeat(60) { m.update(t, 0f, 9.8f, 0f); t += 50 }
        assertFalse(m.isFlat)
    }

    @Test fun rotationFollowsGravityAfterDebounce() {
        val r = RotationTracker()
        assertFalse(r.update(0, 0f, 9.8f, 0f))
        assertFalse(r.update(100, 9.8f, 0f, 0f))             // right side up: candidate 90
        assertFalse(r.update(600, 9.8f, 0f, 0f))
        assertTrue(r.update(700, 9.8f, 0f, 0f))
        assertEquals(90, r.degrees)
        assertFalse(r.update(2000, 9.8f, 6f, 0f))            // 58 deg from upright... only 32 off current: no change
        assertFalse(r.update(3000, 0f, 0f, 9.8f))            // flat: keep
        assertEquals(90, r.degrees)
    }

    @Test fun pomodoroRunsPausesAndFlipsToBreak() {
        val p = PomodoroTimer(25 * 60, 5 * 60)
        p.toggle(0)
        assertEquals(25 * 60 - 10, p.state(10_000).leftSec)
        p.toggle(10_000)
        assertEquals(25 * 60 - 10, p.state(500_000).leftSec)
        p.toggle(500_000)
        assertFalse(p.finishIfDone(500_000 + 1000))
        assertTrue(p.finishIfDone(500_000 + 25 * 60_000))
        assertEquals(TimerMode.BREAK, p.mode)
        assertEquals(5 * 60, p.state(0).leftSec)
        assertFalse(p.state(0).running)
        p.reset()
        assertEquals(TimerMode.FOCUS, p.mode)
    }

    @Test fun wmoMapsCodes() {
        assertEquals(WeatherCondition.CLEAR, Wmo.condition(0, true))
        assertEquals(WeatherCondition.NIGHT_CLEAR, Wmo.condition(1, false))
        assertEquals(WeatherCondition.RAIN, Wmo.condition(63, true))
        assertEquals(WeatherCondition.RAIN, Wmo.condition(81, true))
        assertEquals(WeatherCondition.SNOW, Wmo.condition(73, true))
        assertEquals(WeatherCondition.FOG, Wmo.condition(45, true))
        assertEquals(WeatherCondition.THUNDER, Wmo.condition(96, true))
        assertEquals(WeatherCondition.PARTLY, Wmo.condition(2, true))
    }

    @Test fun weatherTakesTheNextFiveHoursAfterNow() {
        val times = (13..23).joinToString(",") { "\"2026-11-09T$it:00\"" }
        val temps = (13..23).joinToString(",") { "${it - 3}.4" }
        val codes = (13..23).joinToString(",") { "3" }
        val days = (13..23).joinToString(",") { if (it < 18) "1" else "0" }
        val body = """{"current":{"time":"2026-11-09T14:15","temperature_2m":9.6,"weather_code":0,"is_day":1},
            "hourly":{"time":[$times],"temperature_2m":[$temps],"weather_code":[$codes],"is_day":[$days]},
            "daily":{"temperature_2m_max":[11.2,12],"temperature_2m_min":[2.4,3]}}"""
        val w = WeatherParser.parse(body, "서울", 100)
        assertEquals(10, w.tempC); assertEquals(11, w.highC); assertEquals(2, w.lowC)
        assertEquals(WeatherCondition.CLEAR, w.condition)
        assertEquals(listOf(15, 16, 17, 18, 19), w.hourly.map { it.hour })
        assertEquals(WeatherCondition.CLOUDY, w.hourly[0].condition)
    }

    @Test fun usageReadsUsedShareAndReset() {
        val u = UsageParser.parse(
            """{"lan":"http://192.168.0.12:8765/status.json","limits":{"claude":{"5h":{"pct":52.4,"reset":100},"7d":{"pct":70,"reset":null}},"codex":{"5h":{"pct":null}}}}""", 5,
        )
        assertEquals(52, u.claude5h!!.usedPct); assertEquals(100L, u.claude5h!!.resetsAtEpochSec)
        assertEquals(70, u.claude7d!!.usedPct); assertNull(u.claude7d!!.resetsAtEpochSec)
        assertNull(u.codex5h); assertNull(u.codex7d)
        assertEquals("http://192.168.0.12:8765/status.json", UsageParser.lanUrl("""{"lan":"http://192.168.0.12:8765/status.json"}"""))
        assertNull(UsageParser.lanUrl("{}"))
    }

    @Test fun onlyPrivateAddressesGetTheToken() {
        assertTrue(UsageParser.isPrivateHttp("http://192.168.0.12:8765/status.json"))
        assertTrue(UsageParser.isPrivateHttp("http://10.0.0.5:8765/status.json"))
        assertTrue(UsageParser.isPrivateHttp("http://172.20.1.1/x"))
        assertFalse(UsageParser.isPrivateHttp("http://172.40.1.1/x"))
        assertFalse(UsageParser.isPrivateHttp("http://8.8.8.8:8765/status.json"))
        assertFalse(UsageParser.isPrivateHttp("http://evil.example:8765/status.json"))
        assertFalse(UsageParser.isPrivateHttp("https://192.168.0.12/status.json"))
    }

    @Test fun mediaKindByPackage() {
        assertEquals(MediaKind.VIDEO, MediaKinds.kind("com.google.android.youtube"))
        assertEquals(MediaKind.VIDEO, MediaKinds.kind("net.cj.cjhv.gs.tving"))
        assertEquals(MediaKind.VIDEO, MediaKinds.kind("com.netflix.mediaclient"))
        assertEquals(MediaKind.MUSIC, MediaKinds.kind("com.google.android.apps.youtube.music"))
        assertEquals(MediaKind.MUSIC, MediaKinds.kind("com.spotify.music"))
    }

    @Test fun alarmActionTitles() {
        listOf("다시 알림", "Snooze", "스누즈 10분").forEach { assertTrue(it, AlarmActions.isSnooze(it)); assertFalse(it, AlarmActions.isStop(it)) }
        listOf("해제", "중지", "끄기", "Dismiss", "STOP").forEach { assertTrue(it, AlarmActions.isStop(it)); assertFalse(it, AlarmActions.isSnooze(it)) }
        assertFalse(AlarmActions.isStop("열기"))
        assertTrue(AlarmActions.isAlarm("com.sec.android.app.clockpackage", "alarm", false))
        assertTrue(AlarmActions.isAlarm("com.sec.android.app.clockpackage", null, true))
        assertFalse(AlarmActions.isAlarm("com.sec.android.app.clockpackage", null, false))
        assertFalse(AlarmActions.isAlarm("com.kakao.talk", "alarm", true))
    }

    private fun kind(
        pkg: String = "com.kakao.talk", category: String? = "msg", ongoing: Boolean = false, summary: Boolean = false,
        importance: Int = 4, progress: Boolean = false, media: Boolean = false, filter: Boolean = true,
    ) = NotifRules.classify(pkg, "kr.dot.standby", category, ongoing, summary, importance, false, progress, media, false, filter)

    @Test fun notificationClassification() {
        assertEquals(NotifKind.BIG, kind())
        assertEquals(NotifKind.IGNORE, kind(pkg = "kr.dot.standby"))
        assertEquals(NotifKind.IGNORE, kind(summary = true))
        assertEquals(NotifKind.IGNORE, kind(importance = 2))
        assertEquals(NotifKind.IGNORE, kind(filter = false))
        assertEquals(NotifKind.IGNORE, kind(media = true))
        assertEquals(NotifKind.IGNORE, kind(ongoing = true))
        assertEquals(NotifKind.LIVE, kind(ongoing = true, progress = true))
        assertEquals(NotifKind.ALARM, kind(pkg = "com.sec.android.app.clockpackage", category = "alarm"))
    }

    @Test fun noiseStaysInRangeAndIsNotSilent() {
        for (s in SleepSound.entries) {
            val g = NoiseGen(s, 1)
            val buf = ShortArray(22050 * 2)
            g.fill(buf, 1f)
            assertTrue(s.name, buf.any { Math.abs(it.toInt()) > 500 })
            val quiet = ShortArray(1000); g.fill(quiet, 0f)
            assertTrue(quiet.all { it.toInt() == 0 })
        }
    }

    @Test fun turningThePhoneIsNotAPickUp() {
        // portrait -> landscape: gravity moves from y to x over ~0.5 s, |a| stays 9.81 (spec E4/E7)
        val m = MotionDetector()
        var t = 0L
        repeat(50) { m.update(t, 0f, 9.81f, 0f); t += 60 }
        for (i in 0..10) { val a = Math.toRadians(i * 9.0); m.update(t, (9.81 * Math.sin(a)).toFloat(), (9.81 * Math.cos(a)).toFloat(), 0f); t += 50 }
        repeat(40) { m.update(t, 9.81f, 0f, 0f); t += 60 }
        assertFalse(m.pickedUp)
        assertTrue(m.linear < 0.5f)
    }

    @Test fun luxMedianIgnoresOneFlash() {
        val l = LuxMedian()
        assertEquals(2f, l.add(0, 2f), 0f)
        l.add(300, 2f)
        assertEquals(2f, l.add(600, 400f), 0f)    // a phone-screen flash among dark readings
        assertEquals(2f, l.add(1700, 2f), 0f)     // old readings expire after 1 s
    }

    @Test fun nightIsOffUnderNeverAndAlarmKeepsTheScreenOn() {
        assertFalse(NightRules.on(NightMode.AUTO, DisplayOff.NEVER, scheduled = true, auto = true))
        assertTrue(NightRules.on(NightMode.AUTO, DisplayOff.AUTO, scheduled = false, auto = true))
        assertTrue(NightRules.on(NightMode.SCHEDULE, DisplayOff.AFTER_20S, scheduled = true, auto = false))
        assertFalse(NightRules.on(NightMode.OFF, DisplayOff.AUTO, scheduled = true, auto = true))
        assertFalse(NightRules.displayOff(wanted = true, alarm = true, notification = false))
        assertFalse(NightRules.displayOff(wanted = true, alarm = false, notification = true))
        assertTrue(NightRules.displayOff(wanted = true, alarm = false, notification = false))
    }

    @Test fun notificationThatWokeTheScreenSendsItBackOff() {
        val m = DisplayOffMachine()
        m.touch(0); m.forceOff()
        assertTrue(m.off)
        assertTrue(m.evaluate(7_000, DisplayOff.AFTER_20S, null, 3f, 8f)) // stays off, idle rule does not wake it
        assertEquals(0L, m.lastTouch)
    }
}
