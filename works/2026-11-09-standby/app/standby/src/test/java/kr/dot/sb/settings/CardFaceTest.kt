package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.FaceId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Codec v17 (operator 2026-11-18): the clock card's arrows set the one clock-card face every page shares. */
class CardFaceTest {
    @Test fun setsTheSharedFaceOnly() {
        val c = AppConfig()
        val out = BoardOps.setFace(c, FaceId.FLIP)
        assertEquals(FaceId.FLIP, out.clockCardFace)
        assertEquals(c.copy(clockCardFace = FaceId.FLIP), out)
    }
}
