package kr.dot.wherepark.widget

import android.appwidget.AppWidgetManager
import android.util.SizeF
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.data.ParkStore
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A widget update with the same inputs renders nothing; a changed state renders again. The bitmap-limit fallback walks its three steps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRedrawTest {
    private val ctx get() = Kit.ctx()
    private val car = Car("c0", "흰 차", 0xFF8E9399.toInt(), null, 0)
    private fun park(state: ParkState) = Park(System.currentTimeMillis() - 60_000, "c0", Spot(Floor.Under(2), "C", 37, null, null), System.currentTimeMillis() - 60_000, "m", "아빠", null, false, state)

    @Before fun setUp() { Kit.reset(FakeNet(), mutableListOf()) }

    @Test fun unchangedInputsSkipTheRender() {
        CarStore.replaceFromServer(ctx, listOf(car)); ParkStore.setFromServer(ctx, "c0", park(ParkState.PARKED))
        val mgr = AppWidgetManager.getInstance(ctx)
        val id = shadowOf(mgr).createWidget(WidgetReceiver::class.java, kr.dot.wherepark.R.layout.w_root)
        val n0 = WidgetReceiver.drawCount.get()
        WidgetReceiver.update(ctx, mgr, id)
        assertEquals(n0 + 1, WidgetReceiver.drawCount.get())
        WidgetReceiver.update(ctx, mgr, id)
        assertEquals(n0 + 1, WidgetReceiver.drawCount.get())          // same inputs: nothing drawn
        ParkStore.setFromServer(ctx, "c0", park(ParkState.DRIVING))
        WidgetReceiver.update(ctx, mgr, id)
        assertEquals(n0 + 2, WidgetReceiver.drawCount.get())          // state changed: drawn
    }

    @Test fun fallbackWalksReportedThenHalfDensity() {
        val reported = listOf(SizeF(300f, 140f))
        val calls = ArrayList<Pair<Int, Float>>()
        WidgetReceiver.drawWithFallback(reported, 2f) { sizes, d -> calls += sizes.size to d; if (calls.size < 3) throw IllegalArgumentException("limit") }
        assertEquals(listOf(WidgetRenderer.layoutSizes(reported).size to 2f, 1 to 2f, 1 to 1f), calls)
    }
}
