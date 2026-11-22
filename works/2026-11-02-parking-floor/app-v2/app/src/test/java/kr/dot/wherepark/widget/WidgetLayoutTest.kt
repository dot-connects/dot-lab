package kr.dot.wherepark.widget
import kr.dot.wherepark.data.Kit

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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Which cars a size shows, the text colour rule, and button slots at every size: inside the widget, mic >= 48 dp, no mic overlap. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WidgetLayoutTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val now = 1_763_600_000_000L
    private val cars = (0 until 4).map { Car("c$it", listOf("흰 차", "회색 차", "검정 차", "파란 차")[it], 0xFF8E9399.toInt(), null, it) }
    private val parks = cars.associate { it.id to Park(now - 60_000, it.id, Spot(Floor.Under(2), "C", 37, "12", "엘리베이터 옆 기둥 뒤 두번째 칸"), now - 60_000, "m", "아빠", null, false, ParkState.PARKED) }

    private fun out(shape: Shape, n: Int, size: SizeF, mic: Boolean = true, strength: Int = 100, main: String? = null, scale: Float = 1f,
                    parks: Map<String, Park?> = this.parks) =
        WidgetRenderer.draw(ctx, WidgetSpec(7, shape, null, strength, Palette.default(shape), null), cars.take(n), parks, size, 2f,
            listOf(0xFFA9CDE6.toInt()), now, mic, emptySet(), main, scale)

    private fun overlap(a: RectF, b: RectF) = maxOf(0f, minOf(a.right, b.right) - maxOf(a.left, b.left)) * maxOf(0f, minOf(a.bottom, b.bottom) - maxOf(a.top, b.top))

    @Test fun carsPerSize() {
        val ids = { p: Pair<List<Car>, Int> -> p.first.map { it.id } to p.second }
        assertEquals(listOf("c0") to 3, ids(WidgetRenderer.carsFor(cars, null, false)))           // one row: main + 외 3대
        assertEquals(listOf("c0") to 1, ids(WidgetRenderer.carsFor(cars.take(2), null, false)))
        assertEquals(listOf("c1", "c0") to 0, ids(WidgetRenderer.carsFor(cars.take(2), "c1", true)))   // two rows, two cars: both, main first
        assertEquals(listOf("c2") to 2, ids(WidgetRenderer.carsFor(cars.take(3), "c2", true)))     // three cars: main alone
        assertEquals(listOf("c0") to 0, ids(WidgetRenderer.carsFor(cars.take(1), null, true)))
        assertEquals("", WidgetRenderer.carsFor(emptyList(), null, true).first.single().id)       // placeholder car
    }

    @Test fun mainCarFallsBackToFirst() {
        val shuffled = listOf(cars[2], cars[0], cars[1])
        assertEquals("c0", WidgetRenderer.mainCar(shuffled, null)?.id)
        assertEquals("c0", WidgetRenderer.mainCar(shuffled, "deleted")?.id)
        assertEquals("c1", WidgetRenderer.mainCar(shuffled, "c1")?.id)
        assertNull(WidgetRenderer.mainCar(emptyList(), "c1"))
    }

    @Test fun rowsFromHeight() {
        assertFalse(WidgetRenderer.twoRow(100f)); assertFalse(WidgetRenderer.twoRow(130f))
        assertTrue(WidgetRenderer.twoRow(150f)); assertTrue(WidgetRenderer.twoRow(200f))
    }

    @Test fun staleAfterTwoDays() {
        val at = 1_763_564_400_000L + 10 * 3_600_000L   // 2025-11-20 10:00 KST
        assertNull(WidgetRenderer.staleLabel(at, at + 20 * 3_600_000L))   // next day: "어제 10:00" stays
        assertEquals("3일 전", WidgetRenderer.staleLabel(at, at + 3 * 86_400_000L))
    }

    @Test fun autoTextColour() {
        val row = Palette.default(Shape.ROW)          // light card, dark text
        val white = listOf(0xFFFFFFFF.toInt())
        fun light(c: Int) = (c shr 16 and 255) > 200
        assertTrue("auto under 50 % is white even on a white wallpaper", light(Look.pick(row, 30, white, "auto").fg))
        for (st in listOf(0, 30)) assertTrue("halo reaches 7:1 for small text at $st %", Look.pick(row, st, white, "auto").score >= Look.MIN_CR)
        assertEquals(1, Look.pick(row, 0, listOf(0xFF04141A.toInt()), "auto").haloLv)   // dark wallpaper: the softest halo
        assertFalse("auto from 50 % reads the card", light(Look.pick(row, 100, white, "auto").fg))
        assertTrue(Look.pick(Palette.default(Shape.DOT), 100, white, "auto").let { light(it.fg) && it.haloLv == 0 })
        assertFalse(light(Look.pick(row, 0, white, "black").fg))
        assertTrue(light(Look.pick(row, 100, white, "white").fg))
        assertEquals("forcing a text colour keeps the card colour", row.bg, Look.pick(row, 100, white, "white").bg)
        assertEquals(row.bg, Look.pick(row, 60, white, "black").bg)
    }

    @Test fun slotsPerSize() {
        val sizes = listOf(SizeF(164f, 100f), SizeF(246f, 100f), SizeF(328f, 100f), SizeF(164f, 200f), SizeF(246f, 200f), SizeF(328f, 200f),
            SizeF(200f, 80f), SizeF(400f, 230f), SizeF(180f, 150f), SizeF(110f, 50f), SizeF(164f, 60f), SizeF(420f, 150f), SizeF(600f, 600f))
        for (shape in Shape.values()) for (n in 1..4) for (size in sizes) for (mic in listOf(true, false)) for (st in listOf(0, 100)) for (scale in listOf(1f, 1.3f)) {
            val o = out(shape, n, size, mic, st, scale = scale)
            val h = o.rendered.hits
            val tag = "$shape n=$n $size mic=$mic st=$st scale=$scale"
            val shown = if (WidgetRenderer.twoRow(size.height) && n <= 2) n else 1
            assertEquals(tag, shown, h.count { it.kind == "MIC" }); assertEquals(tag, shown, h.count { it.kind == "ROW" })
            h.forEach { assertTrue("$tag ${it.rectDp} in bounds", it.rectDp.left >= -.01f && it.rectDp.top >= -.01f && it.rectDp.right <= size.width + .01f && it.rectDp.bottom <= size.height + .01f) }
            val mics = h.filter { it.kind == "MIC" }
            mics.forEach { assertTrue("$tag mic ${it.rectDp}", it.rectDp.width() >= 47.99f && it.rectDp.height() >= 47.99f) }
            for (i in mics.indices) for (j in i + 1 until mics.size) assertTrue("$tag mics overlap", overlap(mics[i].rectDp, mics[j].rectDp) < 1f)
            o.texts.forEach { assertTrue("$tag text '${it.text}' ${it.rect} out of the widget", it.rect.left >= -1f && it.rect.right <= size.width + 1f) }
        }
    }

    private fun inRounded(x: Float, y: Float, w: Float, h: Float, r: Float): Boolean {
        val cx = if (x < r) r else if (x > w - r) w - r else x
        val cy = if (y < r) r else if (y > h - r) h - r else y
        return (x - cx) * (x - cx) + (y - cy) * (y - cy) <= r * r + .01f
    }

    /** Max corner radius (34 dp): the radius is capped by the short side, and no text box corner or mic edge enters the corner curve. */
    @Test fun contentStaysOutOfTheCorners() {
        val sizes = listOf(SizeF(164f, 100f), SizeF(246f, 100f), SizeF(328f, 100f), SizeF(164f, 200f), SizeF(246f, 200f), SizeF(328f, 200f),
            SizeF(164f, 150f), SizeF(110f, 50f), SizeF(164f, 60f), SizeF(420f, 150f))
        val bad = ArrayList<String>()
        for (shape in Shape.values()) for (n in 1..3) for (size in sizes) for (mic in listOf(true, false)) for (st in listOf(30, 100)) for (scale in listOf(1f, 1.3f)) {
            val spec = WidgetSpec(7, shape, null, st, Palette.default(shape).copy(radiusDp = 34), null)
            val o = WidgetRenderer.draw(ctx, spec, cars.take(n), parks, size, 2f, listOf(0xFFA9CDE6.toInt()), now, mic, emptySet(), null, scale)
            val tag = "$shape n=$n $size mic=$mic st=$st scale=$scale"
            if (shape == Shape.TICKET) continue   // tickets round their own cell corners (<= 14 dp) and are not a widget-wide card
            val r = o.radius
            assertTrue("$tag radius $r", r <= Shapes.R_MAX * minOf(size.width, size.height) + .01f)
            o.texts.forEach { t ->
                for ((x, y) in listOf(t.rect.left to t.rect.top, t.rect.right to t.rect.top, t.rect.left to t.rect.bottom, t.rect.right to t.rect.bottom))
                    if (!inRounded(x, y, size.width, size.height, r)) bad += "$tag text '${t.text}' ($x,$y)"
            }
            o.mics.forEach { m ->
                for (i in 0 until 72) {
                    val a = Math.toRadians(i * 5.0)
                    val x = m.centerX() + m.width() / 2 * Math.cos(a).toFloat(); val y = m.centerY() + m.height() / 2 * Math.sin(a).toFloat()
                    if (!inRounded(x, y, size.width, size.height, r)) bad += "$tag mic $m ($x,$y)"
                }
            }
        }
        assertTrue("${bad.size} in the curve, e.g.\n" + bad.distinctBy { it.substringBefore(" n=") + it.substringAfter("text").take(12) }.take(25).joinToString("\n"), bad.isEmpty())
    }

    /** 2x1 with a long pillar: the memo still gets drawn next to it (pillar capped at 55 % of the line). */
    @Test fun memoShowsAtTwoByOne() {
        for (shape in Shape.values()) for (scale in listOf(1f, 1.3f)) {
            val o = out(shape, 1, SizeF(164f, 100f), scale = scale)
            val m = o.texts.firstOrNull { it.text.startsWith("엘리") }
            assertTrue("$shape scale=$scale memo drawn: ${o.texts.map { it.text }}", m != null && m.rect.width() > 30f)
        }
    }

    @Test fun oldPendingIsFailed() {
        val p = parks.getValue("c0")!!
        assertEquals(St.PEND, WidgetRenderer.line(cars[0], p, true, p.atMs + 60_000).st)
        assertEquals(St.FAIL, WidgetRenderer.line(cars[0], p, true, p.atMs + WidgetRenderer.FAIL_AFTER_MS + 1).st)
        assertEquals(St.OK, WidgetRenderer.line(cars[0], p, false, p.atMs + WidgetRenderer.FAIL_AFTER_MS + 1).st)
    }

    @Test fun failAlarmAtOldestUnsentPark() {
        val a = Kit.park("a", 1, atMs = 1_000L); val b = Kit.park("b", 2, atMs = 5_000L)
        assertEquals(1_000L + WidgetRenderer.FAIL_AFTER_MS + 1, WidgetRenderer.failAlarmAt(listOf(b, a), 2_000L))
        assertEquals(null, WidgetRenderer.failAlarmAt(listOf(a), 1_000L + WidgetRenderer.FAIL_AFTER_MS + 1))
        assertEquals(null, WidgetRenderer.failAlarmAt(emptyList(), 0L))
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

    /** Android's pick among sized RemoteViews (RemoteViews.findBestFitLayout): the closest size that fits, else the smallest. */
    private fun aospPick(sizes: List<SizeF>, actual: SizeF): SizeF {
        val fits = sizes.filter { Math.ceil(actual.width.toDouble()) + 1 > it.width && Math.ceil(actual.height.toDouble()) + 1 > it.height }
        fun d(s: SizeF) = (s.width - actual.width).let { it * it } + (s.height - actual.height).let { it * it }
        return fits.minByOrNull(::d) ?: sizes.minBy { it.width * it.height }
    }

    /** A host that lays the widget out smaller than it reports (0.6x seen 11-21) still gets a layout drawn close to its real size. */
    @Test fun layoutSizesFitSmallerHosts() {
        val reported = listOf(SizeF(380f, 262f), SizeF(520f, 190f))
        val s = WidgetRenderer.layoutSizes(reported)
        assertEquals(reported, s.take(2))
        assertTrue("${s.size} sizes", s.size <= 16)
        assertEquals(s.size, s.distinct().size)
        s.forEach { c -> assertTrue("$c keeps a reported aspect", reported.any { r -> Math.abs(c.width / c.height - r.width / r.height) < .01f }) }
        for (r in reported) for (f in listOf(1f, .99f, .97f, .9f, .8f, .7f, .6f, .53f)) {
            val actual = SizeF(r.width * f, r.height * f)
            val pick = aospPick(s, actual)
            assertTrue("$actual got $pick", pick.width <= actual.width + 1.01f && pick.height <= actual.height + 1.01f)
            assertTrue("$actual got $pick", pick.width >= actual.width * .87f && pick.height >= actual.height * .87f)
        }
        // only the reported sizes: the same 0.6x host got the smallest layout, bigger than its view (the bug)
        val old = aospPick(reported, SizeF(228f, 157f))
        assertTrue(old.width > 228f && old.height > 157f)
        assertEquals(1, WidgetRenderer.layoutSizes(listOf(SizeF(300f, 140f), SizeF(300f, 140f))).count { it == SizeF(300f, 140f) })
    }

    /** Over the memory cap the extra (smaller-host) sizes lose resolution before the reported ones. */
    @Test fun budgetKeepsReportedSizes() {
        val reported = listOf(SizeF(380f, 262f), SizeF(520f, 190f))
        val s = WidgetRenderer.layoutSizes(reported)
        val one = 380 * 3 * 262 * 3 * 4L
        val k = WidgetRenderer.budget(s, 3f, one * 5, reported.size)
        assertEquals(listOf(1f, 1f), k.take(2))
        assertTrue(k.drop(2).any { it < 1f })
    }

    @Test fun talkback() {
        val p = parks.getValue("c0").copy(atMs = 1_763_616_720_000L, spot = Spot(Floor.Under(2), "C", 37, null, null))   // 2025-11-20 14:32 KST
        assertEquals("흰 차 지하 2층 C구역 37번, 아빠 14시 32분", WidgetRenderer.describe(cars[0], p, false, p.atMs + 60_000))
        assertEquals("흰 차 기록 없음", WidgetRenderer.describe(cars[0], null, false, now))
        assertEquals("흰 차 운행 중, 엄마 출발", WidgetRenderer.describe(cars[0], p.copy(state = ParkState.DRIVING, drivingBy = "엄마"), false, now))
        assertTrue(WidgetRenderer.describe(cars[0], p, true, p.atMs).endsWith(", 저장 중"))
        assertTrue(WidgetRenderer.describe(cars[0], p, true, p.atMs + WidgetRenderer.FAIL_AFTER_MS + 1).endsWith(", 저장 실패"))
        assertEquals("흰 차 지하 2층 C구역 37번, 엘리베이터 옆, 아빠 14시 32분",
            WidgetRenderer.describe(cars[0], p.copy(spot = p.spot.copy(memo = "엘리베이터 옆")), false, p.atMs + 60_000))
        assertTrue(WidgetRenderer.describe(cars[0], p, false, p.atMs + 3 * 86_400_000L).endsWith(", 3일 전"))
        assertEquals("어제 9시 5분", WidgetRenderer.timeWords("어제 09:05"))
        assertEquals("14시", WidgetRenderer.timeWords("14:00"))
    }
}
