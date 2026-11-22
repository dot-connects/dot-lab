package kr.dot.wherepark.data

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FakeWorkerTest {
    @Test fun authorizationTable() {
        val f = FakeWorker()
        val o = f.device("owner"); val x = f.device("x"); val y = f.device("y"); val s = f.device("stranger")
        val gx = f.group(o.first, "X"); val gy = f.group(o.first, "Y")
        f.addMember(gx, x.first); f.addMember(gy, y.first)
        val c = Kit.car("c_00000001"); f.car(o.first, c); f.share(c.id, gx); f.share(c.id, gy)
        f.parkAs(o.first, Kit.park(c.id, 1))
        for ((actor, owner, visible) in listOf(Triple(o.first, true, true), Triple(x.first, false, true), Triple(y.first, false, true), Triple(s.first, false, false))) {
            val put = JSONObject().put("car", Wire.carJson(c)).toString().toByteArray()
            assertEquals(if (owner) 200 else if (visible) 403 else 409, f.send("PUT", "/v3/cars/${c.id}", actor, put, null).first)
            assertEquals(if (visible) 200 else 404, f.send("POST", "/v3/cars/${c.id}/state", actor, "{\"state\":\"DRIVING\",\"parkId\":1}".toByteArray(), null).first)
            assertEquals(if (owner) 200 else if (visible) 403 else 404, f.send("PUT", "/v3/cars/${c.id}/groups/$gx", actor, null, null).first)
        }
        f.exclude(c.id, gx)
        assertEquals(404, f.send("POST", "/v3/cars/${c.id}/undo", x.first, "{\"parkId\":1}".toByteArray(), null).first)
        f.addMember(gy, x.first)
        assertEquals(200, f.send("POST", "/v3/cars/${c.id}/undo", x.first, "{\"parkId\":1}".toByteArray(), null).first)
        assertEquals(401, f.send("GET", "/v3/state", "invalid", null, null).first)
    }
}
