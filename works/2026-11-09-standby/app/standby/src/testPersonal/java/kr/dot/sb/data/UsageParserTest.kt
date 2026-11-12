package kr.dot.sb.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Personal build only: the status.json parser (moved from LogicTest with the feed, plan-r9 Task 17). */
class UsageParserTest {
    @Test fun usageReadsUsedShareAndReset() {
        val u = UsageParser.parse(
            """{"lan":"http://192.168.0.12:8765/status.json","limits":{"claude":{"5h":{"pct":52.4,"reset":100},"7d":{"pct":70,"reset":null}},"codex":{"5h":{"pct":null}}}}""", 5,
        )
        assertEquals(52, u.claude5h!!.usedPct); assertEquals(100L, u.claude5h!!.resetsAtEpochSec)
        assertEquals(70, u.claude7d!!.usedPct); assertNull(u.claude7d!!.resetsAtEpochSec)
        assertNull(u.codex5h); assertNull(u.codex7d)
        assertEquals("http://192.168.0.12:8765/status.json", UsageParser.lanUrl("""{"lan":"http://192.168.0.12:8765/status.json"}"""))
        assertNull(UsageParser.lanUrl("{}"))
        // older feeds: no sessions / work = four "off" dots and two "off" bots
        assertEquals(listOf("dev", "dev2", "sync", "lab"), u.sessions.map { it.name })
        assertTrue(u.sessions.all { it.state == "off" && it.ctx == null })
        assertEquals(listOf("content" to "off", "trading" to "off"), u.bots)
    }

    /** A real ai-hub snapshot (trimmed, tools/check_fixture.py): pace, sessions in 눈금's order, bots from "work". */
    @Test fun readsTheAiHubSnapshot() {
        val text = javaClass.getResource("/status.json")!!.readText()
        val u = UsageParser.parse(text, 7)
        assertEquals(21, u.claude5h!!.usedPct); assertEquals(0.367, u.claude5h!!.pace!!, 1e-9)
        assertEquals(28, u.codex7d!!.usedPct); assertEquals(1794574925L, u.codex7d!!.resetsAtEpochSec)
        assertEquals(listOf("dev" to "idle", "dev2" to "busy", "sync" to "off", "lab" to "idle"), u.sessions.map { it.name to it.state })
        assertEquals(20, u.sessions[1].ctx); assertEquals(1794431998L, u.sessions[1].cacheUntil)
        assertNull(u.sessions[0].ctx)
        assertEquals(listOf("content" to "idle", "trading" to "idle"), u.bots)
        // an errored "work" section (one broken source) reads as off, not as a crash
        assertEquals(listOf("content" to "off", "trading" to "off"), UsageParser.bots(JSONObject("""{"work":{"error":"OSError"}}""")))
        // a session state a newer PC feed adds reads as off (the card's state words would throw on it)
        assertEquals("off", UsageParser.dots(JSONObject("""{"sessions":[{"name":"dev","state":"starting"}]}"""))[0].state)
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
}
