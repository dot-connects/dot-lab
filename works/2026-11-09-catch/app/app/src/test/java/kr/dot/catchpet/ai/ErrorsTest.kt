package kr.dot.catchpet.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorsTest {
    @Test fun workerCodesMapToKorean() {
        val busy = "지금은 그림을 못 그려요. 잠시 뒤 다시 눌러 주세요"
        assertEquals("무료 미리보기를 다 썼어요", aiMessage("device", 429))
        assertEquals("무료 펫 그림을 다 썼어요", aiMessage("free", 429))
        assertEquals("이 기기에서는 그림을 만들 수 없어요. Play 스토어에서 받은 앱인지 확인해 주세요", aiMessage("integrity", 403))
        for (c in listOf("all", "off", "model", "slow down", "http", null)) assertEquals(busy, aiMessage(c, 503))
        assertEquals("지금 그리는 사람이 많아요. 몇 분 뒤 다시 눌러 주세요", aiMessage("limit", 429))
        assertEquals("인터넷에 연결되지 않았어요. 와이파이나 데이터를 켜고 다시 눌러 주세요", aiMessage("net", 0))
        assertEquals("구매가 확인되지 않았어요. 돈은 나가지 않았어요", aiMessage("purchase", 402))
        assertEquals("구매가 확인되지 않았어요. 돈은 나가지 않았어요", aiMessage(null, 402))
        val bad = "이 사진은 그릴 수 없어요. 앱을 업데이트하거나 다른 녀석으로 해 보세요"
        for (c in listOf("bad request", "bad", null)) assertEquals(bad, aiMessage(c, 400))
    }
}
