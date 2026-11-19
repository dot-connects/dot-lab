package kr.dot.wherepark.ui

import android.content.Context
import android.speech.SpeechRecognizer
import kr.dot.wherepark.core.Ambiguity
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.data.Kit.car
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.data.SyncQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListenModelTest {
    private lateinit var ctx: Context
    private var t = 1_000L
    private var closed = 0
    private var relistened = 0

    @Before fun setUp() {
        ctx = Kit.reset(FakeNet(), mutableListOf())
        CarStore.upsert(ctx, car("a", 0).copy(name = "흰 차"))
        CarStore.upsert(ctx, car("b", 1).copy(name = "회색 차"))
    }

    private fun model(carId: String? = null, from: String = "widget") =
        ListenModel(ctx, carId, null, from, now = { t++ }).also { m -> m.close = { closed++ }; m.relisten = { relistened++ } }

    private fun parkOps() = SyncQueue.ops(ctx).filterIsInstance<Op.PutPark>()

    @Test fun clearResultSavesQueuesAndShowsPending() {
        val m = model("a")
        m.result("지하 2층 C 37번 엘리베이터 옆")
        val s = m.state as Sheet.Saved
        assertEquals(Floor.Under(2), s.park.spot.floor); assertEquals("C", s.park.spot.zone); assertEquals(37, s.park.spot.number)
        assertEquals("엘리베이터 옆", s.park.spot.memo)
        assertEquals(s.park, ParkStore.latest(ctx, "a"))
        assertEquals(s.park.id, parkOps().single().park.id)
        assertEquals(ListenModel.PENDING, s.sent)
        SyncQueue.drop(ctx, "park:a", parkOps().single().seq)
        m.refreshSent()
        assertEquals(ListenModel.SENT, (m.state as Sheet.Saved).sent)
    }

    @Test fun noFamilyMeansNoSendLine() {
        ctx = Kit.reset(FakeNet(), mutableListOf(), joined = false)
        CarStore.upsert(ctx, car("a"))
        val m = model("a")
        m.result("지하 2층 C 37")
        assertNull((m.state as Sheet.Saved).sent)
        assertTrue(parkOps().isEmpty())
    }

    @Test fun spokenCarNamePicksThatCar() {
        val m = model()
        m.result("회색 차 지하 3층 D 12")
        assertEquals("b", (m.state as Sheet.Saved).park.carId)
        assertEquals("b", m.car!!.id)
    }

    @Test fun ambiguousShowsCardsThenRemembersTheChoice() {
        val m = model("a")
        m.result("2층 C구역 5번")
        val a = m.state as Sheet.Ambiguous
        assertEquals(Ambiguity.FLOOR_SIGN, a.ambiguity)
        assertEquals(listOf(Floor.Under(2), Floor.Above(2)), a.options.map { it.floor })
        assertTrue(ParkStore.history(ctx, "a").isEmpty())
        m.pick(a.options[0])
        assertEquals(Floor.Under(2), (m.state as Sheet.Saved).park.spot.floor)
        val m2 = model("a")
        m2.result("3층 D구역")
        assertEquals(Floor.Under(3), (m2.state as Sheet.Saved).park.spot.floor)
    }

    @Test fun singleOptionAmbiguityAsksToConfirm() {
        val m = model("a")
        m.result("가삼")
        val s = m.state as Sheet.Manual
        assertEquals(ManualWhy.CONFIRM, s.why); assertEquals("가", s.prefill!!.zone); assertEquals(3, s.prefill!!.number)
        m.saveManual(s.prefill!!)
        assertEquals("가삼", (m.state as Sheet.Saved).park.raw)
    }

    @Test fun firstFailureRelistensSecondGoesManual() {
        val m = model("a")
        m.error(SpeechRecognizer.ERROR_NO_MATCH)
        assertTrue((m.state as Sheet.Listening).again); assertEquals(1, relistened)
        m.result("음")
        assertEquals(ManualWhy.FAILED, (m.state as Sheet.Manual).why)
        assertEquals(1, relistened)
    }

    @Test fun manualShowsFloorHistoryOnlyWhenThereIsOne() {
        val m = model("a")
        m.error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT); m.error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertTrue((m.state as Sheet.Manual).floors.isEmpty())
        ParkStore.save(ctx, Kit.park("a", 1, floor = 3)); ParkStore.save(ctx, Kit.park("a", 2, floor = 2)); ParkStore.save(ctx, Kit.park("a", 3, floor = 3))
        val m2 = model("a")
        m2.error(SpeechRecognizer.ERROR_NO_MATCH); m2.error(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf(Floor.Under(3), Floor.Under(2)), (m2.state as Sheet.Manual).floors)
    }

    @Test fun practiceNeverSaves() {
        val m = model("a", from = "practice")
        m.result("지하 2층 C37")
        assertEquals(Floor.Under(2), (m.state as Sheet.Practice).result.spot!!.floor)
        m.again(); m.error(SpeechRecognizer.ERROR_NO_MATCH); m.error(SpeechRecognizer.ERROR_NO_MATCH)
        assertNull((m.state as Sheet.Practice).result.spot)
        m.manual(); m.saveManual(kr.dot.wherepark.core.Spot(Floor.Under(1), null, 5, null, null))
        assertTrue(ParkStore.history(ctx, "a").isEmpty()); assertTrue(SyncQueue.ops(ctx).isEmpty())
        assertEquals(1, closed)
    }

    @Test fun undoBeforeSendRestoresPrevInTheQueue() {
        ParkStore.save(ctx, Kit.park("a", 1))
        val m = model("a")
        m.result("지하 4층 A 1")
        m.undo()
        assertEquals(1L, ParkStore.latest(ctx, "a")!!.id)
        assertEquals(1L, parkOps().single().park.id)
        assertTrue(SyncQueue.ops(ctx).none { it is Op.Undo })
        assertEquals(1, closed)
    }

    @Test fun undoBeforeSendWithNoPrevDropsTheOp() {
        val m = model("a")
        m.result("지하 4층 A 1")
        m.undo()
        assertNull(ParkStore.latest(ctx, "a")); assertTrue(SyncQueue.ops(ctx).isEmpty())
    }

    @Test fun undoAfterSendStartedQueuesUndo() {
        ParkStore.save(ctx, Kit.park("a", 1))
        val m = model("a")
        m.result("지하 4층 A 1")
        val id = (m.state as Sheet.Saved).park.id
        ParkStore.markSendStarted(ctx, id)
        m.undo()
        assertEquals(1L, ParkStore.latest(ctx, "a")!!.id)
        assertTrue(parkOps().isEmpty())
        assertEquals(Op.Undo("a", id).key, SyncQueue.ops(ctx).single().key)
    }

    @Test fun undoLeavesANewerRecordAlone() {
        val m = model("a")
        m.result("지하 4층 A 1")
        ParkStore.save(ctx, Kit.park("a", 99_999))
        m.undo()
        assertEquals(99_999L, ParkStore.latest(ctx, "a")!!.id)
    }

    @Test fun switchCarMovesTheRecord() {
        val m = model("a")
        m.result("지하 4층 A 1")
        m.switchCar("b")
        val s = m.state as Sheet.Saved
        assertEquals("b", s.park.carId)
        assertNull(ParkStore.latest(ctx, "a")); assertEquals(s.park.id, ParkStore.latest(ctx, "b")!!.id)
        assertEquals(listOf("b"), parkOps().map { it.park.carId })
    }

    @Test fun fixKeepsTheParkId() {
        val m = model("a")
        m.result("지하 4층 A 1")
        val id = (m.state as Sheet.Saved).park.id
        m.fix()
        assertEquals(ManualWhy.EDIT, (m.state as Sheet.Manual).why)
        m.saveManual(kr.dot.wherepark.core.Spot(Floor.Under(5), "A", 1, null, null))
        assertEquals(id, (m.state as Sheet.Saved).park.id)
        assertEquals(1, ParkStore.history(ctx, "a").size); assertEquals(Floor.Under(5), ParkStore.latest(ctx, "a")!!.spot.floor)
        assertEquals(Floor.Under(5), parkOps().single().park.spot.floor)
    }

    @Test fun timeoutClosesUnlessHeld() {
        val m = model("a")
        m.result("지하 4층 A 1")
        m.timeout((m.state as Sheet.Saved).park.id)
        assertEquals(1, closed)
        val m2 = model("a")
        m2.result("지하 4층 A 2")
        m2.hold(); m2.timeout((m2.state as Sheet.Saved).park.id)
        assertEquals(1, closed)
    }

    @Test fun micDenialsAndMissingPackGoManual() {
        val m = model("a")
        m.micDenied(1); assertTrue(m.state is Sheet.AskMic)
        m.micDenied(2); assertEquals(ManualWhy.NO_MIC, (m.state as Sheet.Manual).why)
        m.error(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE); assertEquals(ManualWhy.NO_PACK, (m.state as Sheet.Manual).why)
        m.downloading(); assertTrue((m.state as Sheet.Manual).downloading)
    }

    @Test fun noCarYetMakesOneLocalCar() {
        ctx = Kit.reset(FakeNet(), mutableListOf())
        val m = model()
        m.result("지하 1층 B 2")
        val c = CarStore.cars(ctx).single()
        assertEquals("내 차", c.name); assertEquals(c.id, (m.state as Sheet.Saved).park.carId)
        assertFalse(SyncQueue.ops(ctx).none { it is Op.PutCar })
    }
}
