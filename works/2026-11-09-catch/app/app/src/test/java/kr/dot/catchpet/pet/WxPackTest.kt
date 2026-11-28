package kr.dot.catchpet.pet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The published manifest (wx3d/, tools/pack-wx3d.py) against its own zips, and a tampered copy. */
class WxPackTest {
    private val root = File("../../wx3d")

    @Test fun manifestMatchesZips() {
        val m = WxPack.parse(File(root, "manifest.json").readText())
        assertEquals(16, m.size)
        for ((critter, p) in m) {
            assertEquals("$critter.zip", p.zip)
            assertEquals(setOf("sun", "hot", "cold", "snow", "rain", "dust"), p.states.keys)
            assertTrue(p.states.values.all { (a, l) -> a > 0 && l > 0 })
            assertTrue("$critter hash", WxPack.verify(File(root, p.zip), p.size, p.sha256))
        }
    }

    @Test fun rejectsWrongSizeOrHash() {
        val p = WxPack.parse(File(root, "manifest.json").readText()).getValue("monggeul")
        val src = File(root, p.zip)
        assertFalse(WxPack.verify(src, p.size + 1, p.sha256))
        assertFalse(WxPack.verify(src, p.size, "0".repeat(64)))
        assertFalse(WxPack.verify(File(root, "missing.zip"), p.size, p.sha256))
        val bad = File.createTempFile("wxpack", ".zip")
        try {
            val b = src.readBytes(); b[b.size / 2] = (b[b.size / 2] + 1).toByte(); bad.writeBytes(b)   // same size, one byte off
            assertFalse(WxPack.verify(bad, p.size, p.sha256))
        } finally { bad.delete() }
    }
}
