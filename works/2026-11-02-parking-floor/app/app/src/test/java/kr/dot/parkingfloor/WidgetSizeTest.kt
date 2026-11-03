package kr.dot.parkingfloor

import kr.dot.parkingfloor.ParkWidget.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {
    @Test
    fun classifiesLauncherCellSizes() {
        assertEquals(Size.S22, ParkWidget.classify(150f, 170f))
        assertEquals(Size.S32, ParkWidget.classify(220f, 170f))
        assertEquals(Size.S42, ParkWidget.classify(300f, 170f))
        assertEquals(Size.S43, ParkWidget.classify(300f, 260f))
    }

    @Test
    fun boundaries() {
        assertEquals(Size.S22, ParkWidget.classify(169f, 300f))
        assertEquals(Size.S32, ParkWidget.classify(170f, 229f))
        assertEquals(Size.S42, ParkWidget.classify(240f, 229f))
        assertEquals(Size.S43, ParkWidget.classify(170f, 230f))
    }
}
