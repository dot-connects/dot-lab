package kr.dot.catchpet.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** The layout follows the widget's proportions, whatever the launcher's cell size (operator's Fold, 11-16). */
class WidgetShapeTest {
    @Test fun byProportions() {
        // a Fold-like launcher with tall cells: 1x1 ~ 95 x 115, 2x1 ~ 200 x 115
        assertEquals(PetWidgets.Shape.ONE, PetWidgets.shape(95f, 115f))
        assertEquals(PetWidgets.Shape.WIDE, PetWidgets.shape(200f, 115f))
        assertEquals(PetWidgets.Shape.TALL, PetWidgets.shape(95f, 240f))
        assertEquals(PetWidgets.Shape.BIG, PetWidgets.shape(200f, 240f))
        // a phone with small cells: 1x1 ~ 70 x 80, 2x1 ~ 150 x 80, 2x2 ~ 150 x 170
        assertEquals(PetWidgets.Shape.ONE, PetWidgets.shape(70f, 80f))
        assertEquals(PetWidgets.Shape.WIDE, PetWidgets.shape(150f, 80f))
        assertEquals(PetWidgets.Shape.BIG, PetWidgets.shape(150f, 170f))
    }
}
