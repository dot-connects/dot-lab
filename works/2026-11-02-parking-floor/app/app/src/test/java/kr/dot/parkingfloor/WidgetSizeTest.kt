package kr.dot.parkingfloor

import kr.dot.parkingfloor.ParkWidget.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {
    @Test
    fun classifiesLauncherCellSizes() {
        assertEquals(Size.TINY, ParkWidget.classify(57f, 74f))
        assertEquals(Size.WIDE2, ParkWidget.classify(130f, 74f))
        assertEquals(Size.WIDE4, ParkWidget.classify(276f, 74f))
        assertEquals(Size.SQUARE, ParkWidget.classify(130f, 160f))
        assertEquals(Size.BIG, ParkWidget.classify(276f, 160f))
        assertEquals(Size.XL, ParkWidget.classify(276f, 250f))
    }

    @Test
    fun boundariesBelongToTheLargerSize() {
        assertEquals(Size.WIDE2, ParkWidget.classify(100f, 99f))
        assertEquals(Size.SQUARE, ParkWidget.classify(99f, 100f))
        assertEquals(Size.WIDE4, ParkWidget.classify(230f, 60f))
        assertEquals(Size.BIG, ParkWidget.classify(170f, 229f))
        assertEquals(Size.XL, ParkWidget.classify(170f, 230f))
    }
}
