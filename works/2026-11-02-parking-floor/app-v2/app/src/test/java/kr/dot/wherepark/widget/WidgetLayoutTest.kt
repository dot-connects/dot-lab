package kr.dot.wherepark.widget

import android.graphics.RectF
import android.util.SizeF
import androidx.test.core.app.ApplicationProvider
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Palette
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Shape
import kr.dot.wherepark.core.Spot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Button slots per size: inside the widget, mic >= 44 dp, no slot overlap, the same requestCode at every size. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetLayoutTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val now = 1_763_600_000_000L
    private val cars = (0 until 4).map { Car("c$it", listOf("흰 차", "회색 차", "검정 차", "파란 차")[it], 0xFF8E9399.toInt(), null, it) }
    private val parks = cars.associate { it.id to Park(now - 60_000, it.id, Spot(Floor.Under(2), "C", 37, null, null), now - 60_000, "m", "아빠", null, false, ParkState.PARKED) }

    private fun hits(shape: Shape, n: Int, one: Boolean, size: SizeF, mic: Boolean = true): List<Hit> {
        val spec = WidgetSpec(7, shape, if (one) "c0" else null, 100, Palette.default(shape), null)
        return WidgetRenderer.draw(ctx, spec, cars.take(n), parks, size, 2f, listOf(0xFFA9CDE6.toInt()), now, mic, emptySet()).rendered.hits
    }

    private fun overlap(a: RectF, b: RectF) = maxOf(0f, minOf(a.right, b.right) - maxOf(a.left, b.left)) * maxOf(0f, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))

    @Test fun slotsPerSize() {
        val sizes = listOf(SizeF(292f, 135f), SizeF(360f, 200f), SizeF(250f, 110f), SizeF(150f, 60f), SizeF(520f, 120f), SizeF(170f, 130f))
        for (shape in Shape.values()) for (one in listOf(false, true)) for (n in (if (one) listOf(1) else listOf(1, 2, 3, 4))) for (size in sizes) for (mic in listOf(true, false)) {
            val h = hits(shape, n, one, size, mic)
            val tag = "$shape one=$one n=$n $size mic=$mic"
            val expected = if (one) 1 else n
            assertEquals(tag, expected, h.count { it.kind == "MIC" }); assertEquals(tag, expected, h.count { it.kind == "ROW" })
            h.forEach { assertTrue("$tag ${it.rectDp} in bounds", it.rectDp.left >= -.01f && it.rectDp.top >= -.01f && it.rectDp.right <= size.width + .01f && it.rectDp.bottom <= size.height + .01f) }
            val (nw, nh) = Shapes.natural(shape, one, n, mic)
            val full = size.width >= nw + 12f && size.height >= nh + 16f   // card at 1x: every mic keeps its full 44 dp
            h.filter { it.kind == "MIC" }.forEach { assertTrue("$tag mic ${it.rectDp}", it.rectDp.width() >= 43.99f && (!full || it.rectDp.height() >= 43.99f)) }
            val mics = h.filter { it.kind == "MIC" }
            for (i in mics.indices) for (j in i + 1 until mics.size) assertTrue("$tag mics overlap", overlap(mics[i].rectDp, mics[j].rectDp) < 1f || size.height < 70f && n >= 3)  // 3-4 cars in a 1-row cell cannot hold separate targets
            for (m in mics) assertTrue("$tag mic/row", overlap(m.rectDp, h.first { it.kind == "ROW" && it.carId == m.carId }.rectDp) < 0.2f * m.rectDp.width() * m.rectDp.height())
        }
    }

    /** At its natural size the ROW widget puts the mic where the mockup does (card at MX/MT, mic at x 226, rows 57 apart). */
    @Test fun naturalRowMatchesMockup() {
        val h = hits(Shape.ROW, 2, false, SizeF(280f + 2 * WidgetRenderer.MX, 119f + WidgetRenderer.MT + WidgetRenderer.MB))
        val m = h.filter { it.kind == "MIC" }.map { it.rectDp }
        assertEquals(RectF(6f + 226f, 4f + 9f, 6f + 270f, 4f + 53f), m[0])
        assertEquals(4f + 66f, m[1].top, .01f)
    }

    @Test fun smallSizeKeepsMicTouchTarget() {
        hits(Shape.ROW, 1, false, SizeF(200f, 50f)).filter { it.kind == "MIC" }.forEach { assertTrue(it.rectDp.width() >= 44f && it.rectDp.height() >= 44f) }
    }

    @Test fun requestCodesUnique() {
        val codes = HashSet<Int>()
        for (id in 1..300) for (car in 0..3) for (kind in listOf("MIC", "ROW")) assertTrue(codes.add(WidgetReceiver.requestCode(id, car, kind)))
        assertEquals(7 * 16 + 2 * 2 + 1, WidgetReceiver.requestCode(7, 2, "ROW"))
        assertEquals(7 * 16 + 3 * 2, WidgetReceiver.requestCode(7, 3, "MIC"))
    }

    @Test fun budgetShrinksBiggestFirst() {
        val sizes = listOf(SizeF(300f, 140f), SizeF(600f, 400f))
        val limit = 3_000_000L
        val k = WidgetRenderer.budget(sizes, 3f, limit)
        assertTrue(k[1] < 1f && k[0] == 1f)
        val bytes = sizes.indices.sumOf { (sizes[it].width * 3 * k[it]).toLong() * (sizes[it].height * 3 * k[it]).toLong() * 4 }
        assertTrue(bytes <= limit * 1.01)
        assertEquals(listOf(1f, 1f), WidgetRenderer.budget(sizes, 1f, 100_000_000L))
    }

    @Test fun talkback() {
        val p = parks.getValue("c0").copy(atMs = 1_763_616_720_000L)   // 2025-11-20 14:32 KST
        assertEquals("흰 차 지하 2층 C구역 37번, 아빠 14시 32분", WidgetRenderer.describe(cars[0], p, false, p.atMs + 60_000))
        assertEquals("흰 차 기록 없음", WidgetRenderer.describe(cars[0], null, false, now))
        assertEquals("흰 차 운행 중, 엄마 출발", WidgetRenderer.describe(cars[0], p.copy(state = ParkState.DRIVING, drivingBy = "엄마"), false, now))
        assertTrue(WidgetRenderer.describe(cars[0], p, true, p.atMs).endsWith(", 전송 대기"))
        assertEquals("어제 9시 5분", WidgetRenderer.timeWords("어제 09:05"))
        assertEquals("14시", WidgetRenderer.timeWords("14:00"))
    }
}
