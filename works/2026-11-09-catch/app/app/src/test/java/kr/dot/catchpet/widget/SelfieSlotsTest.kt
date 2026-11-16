package kr.dot.catchpet.widget

import org.junit.Assert.assertTrue
import org.junit.Test

/** The selfie layout's promise (operator 11-16): bodies may hide, faces never do. */
class SelfieSlotsTest {
    // a pet's box from its slot; its face = the upper half
    private fun box(s: FloatArray) = floatArrayOf(s[0] - s[2] / 2, s[1] - s[2], s[0] + s[2] / 2, s[1])

    private fun overlaps(a: FloatArray, b: FloatArray) = a[0] < b[2] && b[0] < a[2] && a[1] < b[3] && b[1] < a[3]

    @Test fun noFaceIsCovered() {
        for (wide in listOf(false, true)) for (n in 1..6) {
            val s = PetWidgets.slots(n, wide)
            // drawn later = nearer: the selfie taker last, otherwise bigger later
            val order = s.indices.sortedWith(compareBy<Int>({ it == 0 }, { s[it][2] }))
            for ((k, far) in order.withIndex()) for (near in order.drop(k + 1)) {
                val f = box(s[far]); val face = floatArrayOf(f[0], f[1], f[2], f[1] + (f[3] - f[1]) / 2)
                assertTrue("wide=$wide n=$n: pet $far's face under pet $near", !overlaps(face, box(s[near])))
            }
            val w = if (wide) 200f else 100f
            for (x in s) { val b = box(x); assertTrue("wide=$wide n=$n off the box", b[0] >= 0f && b[2] <= w && b[1] >= 0f) }
        }
    }
}
