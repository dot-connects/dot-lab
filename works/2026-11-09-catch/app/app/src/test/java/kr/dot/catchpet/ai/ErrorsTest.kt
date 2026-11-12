package kr.dot.catchpet.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorsTest {
    @Test fun workerCodesMapToKorean() {
        val busy = "AI 그림을 잠시 못 그려요"
        assertEquals("오늘 AI 미리보기 5번을 다 썼어요", aiMessage("device", 429))
        for (c in listOf("all", "off", "model", "limit", "slow down", "http", null)) assertEquals(busy, aiMessage(c, 503))
        assertEquals("인터넷 연결을 확인해 주세요", aiMessage("net", 0))
        assertEquals("구매가 확인되지 않았어요", aiMessage("purchase", 402))
        assertEquals("구매가 확인되지 않았어요", aiMessage(null, 402))
        assertEquals("앱을 업데이트해 주세요", aiMessage("bad request", 400))
        assertEquals("앱을 업데이트해 주세요", aiMessage("bad", 400))
        assertEquals("앱을 업데이트해 주세요", aiMessage(null, 400))
    }
}
