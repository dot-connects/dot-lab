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
        assertEquals(700, Purchase.PRICE_WON)
    }

    // one button makes both looks: free uses while any are left, then the price (operator 11-28)
    @Test fun makeLabelCountsFreeUsesThenThePrice() {
        assertEquals("펫 만들기 · 무료 5장", Purchase.makeLabel(5, dev = false))
        assertEquals("펫 만들기 · 무료 1장", Purchase.makeLabel(1, dev = false))
        assertEquals("펫 만들기 · 700원", Purchase.makeLabel(0, dev = false))
        assertEquals("펫 만들기 · 무료", Purchase.makeLabel(-1, dev = false))   // not heard from the Worker yet
        assertEquals("펫 만들기 · 개발판 무료", Purchase.makeLabel(0, dev = true))
    }
}
