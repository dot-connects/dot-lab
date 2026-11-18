package kr.dot.catchpet.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class RequestHashTest {
    // the same vector as worker/test/verdicts.test.js SHARED_VECTOR: app and Worker must hash a request alike
    @Test fun matchesTheWorker() =
        assertEquals("f21c85072b81e7a3ba8879de2a6583872088957123dd8aa5ac9252cb9407bfec", RequestHash.of("/sheet", "dev-aaaaaaaa", "{\"a\":1}"))
}
