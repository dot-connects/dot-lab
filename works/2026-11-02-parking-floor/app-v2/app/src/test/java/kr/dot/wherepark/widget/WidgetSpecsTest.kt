package kr.dot.wherepark.widget

import android.content.Intent
import kr.dot.wherepark.core.Palette
import kr.dot.wherepark.core.Shape
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Settings store: reads never write, app-pinned settings go to exactly one new widget, our broadcasts carry the token. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetSpecsTest {
    private val ctx get() = Kit.ctx()

    @Before fun setUp() { Kit.reset(FakeNet(), mutableListOf()) }

    @Test fun getIsReadOnly() {
        assertEquals(Shape.ROW, WidgetSpecs.get(ctx, 42).shape)
        assertFalse(Prefs.obj(ctx, "wp_widgets").has("42"))
    }

    @Test fun claimNextMovesOnce() {
        WidgetSpecs.putNext(ctx, WidgetSpec(0, Shape.TICKET, "w", 30, Palette.default(Shape.TICKET), null))
        assertEquals(Shape.ROW, WidgetSpecs.get(ctx, 7).shape)          // an older default widget does not take it
        assertTrue(WidgetSpecs.claimNext(ctx, 8))
        assertEquals(Shape.TICKET, WidgetSpecs.get(ctx, 8).shape); assertEquals(30, WidgetSpecs.get(ctx, 8).strength)
        assertFalse(WidgetSpecs.claimNext(ctx, 9))
        assertEquals(Shape.ROW, WidgetSpecs.get(ctx, 9).shape)
    }

    /** The launcher may run the config screen before the pin callback arrives: the saved settings stay, NEXT is dropped. */
    @Test fun claimNextKeepsConfigSavedSpec() {
        WidgetSpecs.put(ctx, WidgetSpec(5, Shape.PILLAR, null, 60, Palette.default(Shape.PILLAR), null))
        WidgetSpecs.putNext(ctx, WidgetSpec(0, Shape.TICKET, "w", 30, Palette.default(Shape.TICKET), null))
        assertFalse(WidgetSpecs.claimNext(ctx, 5))
        assertEquals(Shape.PILLAR, WidgetSpecs.get(ctx, 5).shape); assertEquals(60, WidgetSpecs.get(ctx, 5).strength)
        assertFalse(Prefs.obj(ctx, "wp_widgets").has("next"))
        assertFalse(WidgetSpecs.claimNext(ctx, 6))
    }

    @Test fun claimNextUnderConcurrency() {
        WidgetSpecs.putNext(ctx, WidgetSpec(0, Shape.DOT, null, 100, Palette.default(Shape.DOT), null))
        val wins = java.util.concurrent.atomic.AtomicInteger()
        (1..8).map { id -> Thread { if (WidgetSpecs.claimNext(ctx, id)) wins.incrementAndGet() } }.onEach { it.start() }.forEach { it.join() }
        assertEquals(1, wins.get())
    }

    /** 스텐실 is gone: a widget saved with it reads as 고딕; 글자 색 survives a save. */
    @Test fun decodeDropsStencilKeepsTextColour() {
        WidgetSpecs.put(ctx, WidgetSpec(3, Shape.ROW, null, 40, Palette.default(Shape.ROW).copy(font = "Big Shoulders Stencil Display"),
            Palette.default(Shape.ROW).copy(font = "Big Shoulders Stencil Display"), "black"))
        val s = WidgetSpecs.get(ctx, 3)
        assertEquals("Inter Tight", s.style.font); assertEquals("Inter Tight", s.prevStyle?.font)
        assertEquals("black", s.text)
        WidgetSpecs.put(ctx, s.copy(style = s.style.copy(font = "Chakra Petch")))
        assertEquals("Chakra Petch", WidgetSpecs.get(ctx, 3).style.font)
        assertEquals("auto", WidgetSpecs.get(ctx, 99).text)
    }

    /** 포스터 is gone: a widget saved with it reads as 줄 목록 and keeps its colours. */
    @Test fun decodePosterAsRow() {
        val st = Palette.default(Shape.ROW)
        WidgetSpecs.put(ctx, WidgetSpec(4, Shape.ROW, null, 70, st, null))
        val all = Prefs.obj(ctx, "wp_widgets")
        all.getJSONObject("4").put("shape", "POSTER")
        Prefs.put(ctx, "wp_widgets", all)
        val s = WidgetSpecs.get(ctx, 4)
        assertEquals(Shape.ROW, s.shape); assertEquals(70, s.strength); assertEquals(st, s.style)
    }

    @Test fun midnightNeedsToken() {
        val bare = Intent(WidgetReceiver.ACTION_MIDNIGHT)
        assertFalse(WidgetReceiver.ours(ctx, bare))
        assertFalse(WidgetReceiver.ours(ctx, Intent(bare).putExtra("wp_widget_token", "guess")))
        assertTrue(WidgetReceiver.ours(ctx, Intent(bare).putExtra("wp_widget_token", WidgetReceiver.token(ctx))))
    }
}
