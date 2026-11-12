package kr.dot.catchpet.billing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PurchaseTest {
    @Test fun lockedUnlessADebugTokenIsSet() {
        assertTrue(Purchase.locked(null))
        assertTrue(Purchase.locked(""))
        assertTrue(Purchase.locked("  "))
        assertFalse(Purchase.locked("x"))
        assertEquals(500, Purchase.PRICE_WON)
    }
}
