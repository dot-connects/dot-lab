package kr.dot.wherepark.ui

import android.content.Context
import android.speech.SpeechRecognizer
import kr.dot.wherepark.core.Ask
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
    private var halted = 0

    @Before fun setUp() {
        ctx = Kit.reset(FakeNet(), mutableListOf())
        CarStore.upsert(ctx, car("a", 0).copy(name = "흰 차"))
        CarStore.upsert(ctx, car("b", 1).copy(name = "회색 차"))
    }

    private fun model(carId: String? = null, from: String = "widget") =
        ListenModel(ctx, carId, null, from, now = { t++ }).also { m -> m.close = { closed++ }; m.relisten = { relistened++ }; m.halt = { halted++ } }

    /** One recognizer session, then 정지. */
    private fun ListenModel.say(text: String) { result(text); stop() }

    private fun parkOps() = SyncQueue.ops(ctx).filterIsInstance<Op.PutPark>()

    @Test fun clearResultSavesQueuesAndShowsPending() {
        val m = model("a")
        m.say("지하 2층 C 37번 엘리베이터 옆")
        val s = m.state as Sheet.Saved
        assertEquals(Floor.Under(2), s.park.spot.floor); assertEquals("C", s.park.spot.zone); assertEquals(37, s.park.spot.number)
        assertEquals("지하 2층 C 37번 엘리베이터 옆", s.park.spot.memo)
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
        m.say("지하 2층 C 37")
        assertNull((m.state as Sheet.Saved).sent)
        assertTrue(parkOps().isEmpty())
    }

    @Test fun spokenCarNamePicksThatCar() {
        val m = model()
        m.say("회색 차 지하 3층 D 12")
        assertEquals("b", (m.state as Sheet.Saved).park.carId)
        assertEquals("b", m.car!!.id)
    }

    @Test fun listensUntilStopAndJoinsSessions() {
        val m = model("a")
        m.result("지하 2층")
        assertEquals("지하 2층", (m.state as Sheet.Listening).heard); assertEquals(1, relistened)
        m.listening(); m.partial("C 37")
        assertEquals("C 37", (m.state as Sheet.Listening).partial)
        m.stop()
        assertEquals(1, halted)
        val s = m.state as Sheet.Saved
        assertEquals("지하 2층 C 37", s.park.spot.memo); assertEquals("C", s.park.spot.zone); assertEquals("지하 2층 C 37", s.park.raw)
    }

    @Test fun silenceKeepsListeningStopWithNothingGoesManual() {
        val m = model("a")
        repeat(3) { m.error(SpeechRecognizer.ERROR_NO_MATCH) }
        m.error(SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        assertTrue(m.state is Sheet.Listening); assertEquals(4, relistened)
        m.stop()
        assertEquals(ManualWhy.FAILED, (m.state as Sheet.Manual).why)
    }

    @Test fun otherErrorsRetryTwiceThenStopWithWhatWasHeard() {
        val m = model("a")
        m.result("지하 3층")
        m.error(SpeechRecognizer.ERROR_NETWORK); m.error(SpeechRecognizer.ERROR_NETWORK)
        assertTrue(m.state is Sheet.Listening)
        m.error(SpeechRecognizer.ERROR_NETWORK)
        assertEquals(Floor.Under(3), (m.state as Sheet.Saved).park.spot.floor)
    }

    @Test fun askSavesTheDefaultThenOneTapFixesTheSameRecord() {
        val m = model("a")
        m.say("2층 C구역 5번")
        val s = m.state as Sheet.Saved
        assertEquals(Floor.Above(2), s.park.spot.floor); assertEquals(Ask.BASEMENT, s.ask)
        assertEquals(s.park.id, ParkStore.latest(ctx, "a")!!.id)
        m.timeout(s.park.id)
        assertEquals(0, closed)   // the question keeps the sheet up
        m.answer(s.options.single())
        val n = m.state as Sheet.Saved
        assertEquals(s.park.id, n.park.id); assertEquals(Floor.Under(2), n.park.spot.floor); assertEquals(Ask.NONE, n.ask)
        assertEquals(1, ParkStore.history(ctx, "a").size); assertEquals(Floor.Under(2), parkOps().single().park.spot.floor)
        assertEquals(1, closed)   // undo window was over: answering closes
    }

    @Test fun keepDropsTheQuestion() {
        val m = model("a")
        m.say("지하 2층 이 구역 이 번")
        assertEquals(Ask.ZONE_2, (m.state as Sheet.Saved).ask)
        m.keep()
        assertEquals(Ask.NONE, (m.state as Sheet.Saved).ask); assertEquals("E", ParkStore.latest(ctx, "a")!!.spot.zone)
    }

    @Test fun manualShowsFloorHistoryOnlyWhenThereIsOne() {
        val m = model("a")
        m.stop()
        assertTrue((m.state as Sheet.Manual).floors.isEmpty())
        ParkStore.save(ctx, Kit.park("a", 1, floor = 3)); ParkStore.save(ctx, Kit.park("a", 2, floor = 2)); ParkStore.save(ctx, Kit.park("a", 3, floor = 3))
        val m2 = model("a")
        m2.stop()
        assertEquals(listOf(Floor.Under(3), Floor.Under(2)), (m2.state as Sheet.Manual).floors)
    }

    @Test fun practiceNeverSaves() {
        val m = model("a", from = "practice")
        m.say("지하 2층 C37")
        assertEquals(Floor.Under(2), (m.state as Sheet.Practice).result.spot!!.floor)
        m.again(); m.stop()
        assertNull((m.state as Sheet.Practice).result.spot)
        m.manual(); m.saveManual(kr.dot.wherepark.core.Spot(Floor.Under(1), null, 5, null, null))
        assertTrue(ParkStore.history(ctx, "a").isEmpty()); assertTrue(SyncQueue.ops(ctx).isEmpty())
        assertEquals(1, closed)
    }

    @Test fun undoBeforeSendRestoresPrevInTheQueue() {
        ParkStore.save(ctx, Kit.park("a", 1))
        val m = model("a")
        m.say("지하 4층 A 1")
        m.undo()
        assertEquals(1L, ParkStore.latest(ctx, "a")!!.id)
        assertEquals(1L, parkOps().single().park.id)
        assertTrue(SyncQueue.ops(ctx).none { it is Op.Undo })
        assertEquals(1, closed)
    }

    @Test fun undoBeforeSendWithNoPrevDropsTheOp() {
        val m = model("a")
        m.say("지하 4층 A 1")
        m.undo()
        assertNull(ParkStore.latest(ctx, "a")); assertTrue(SyncQueue.ops(ctx).isEmpty())
    }

    @Test fun undoAfterSendStartedQueuesUndo() {
        ParkStore.save(ctx, Kit.park("a", 1))
        val m = model("a")
        m.say("지하 4층 A 1")
        val id = (m.state as Sheet.Saved).park.id
        ParkStore.markSendStarted(ctx, id)
        m.undo()
        assertEquals(1L, ParkStore.latest(ctx, "a")!!.id)
        assertTrue(parkOps().isEmpty())
        assertEquals(Op.Undo("a", id).key, SyncQueue.ops(ctx).single().key)
    }

    @Test fun undoLeavesANewerRecordAlone() {
        val m = model("a")
        m.say("지하 4층 A 1")
        ParkStore.save(ctx, Kit.park("a", 99_999))
        m.undo()
        assertEquals(99_999L, ParkStore.latest(ctx, "a")!!.id)
    }

    @Test fun switchCarMovesTheRecord() {
        val m = model("a")
        m.say("지하 4층 A 1")
        m.switchCar("b")
        val s = m.state as Sheet.Saved
        assertEquals("b", s.park.carId)
        assertNull(ParkStore.latest(ctx, "a")); assertEquals(s.park.id, ParkStore.latest(ctx, "b")!!.id)
        assertEquals(listOf("b"), parkOps().map { it.park.carId })
    }

    @Test fun fixKeepsTheParkId() {
        val m = model("a")
        m.say("지하 4층 A 1")
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
        m.say("지하 4층 A 1")
        m.timeout((m.state as Sheet.Saved).park.id)
        assertEquals(1, closed)
        val m2 = model("a")
        m2.say("지하 4층 A 2")
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
        m.say("지하 1층 B 2")
        val c = CarStore.cars(ctx).single()
        assertEquals("내 차", c.name); assertEquals(c.id, (m.state as Sheet.Saved).park.carId)
        assertFalse(SyncQueue.ops(ctx).none { it is Op.PutCar })
    }

    private fun withPhoto(m: ListenModel): kr.dot.wherepark.core.Park {
        m.say("지하 4층 A 1")
        val p = (m.state as Sheet.Saved).park
        val f = m.photoFile(p)
        android.graphics.Bitmap.createBitmap(40, 30, android.graphics.Bitmap.Config.ARGB_8888)
            .let { b -> f.outputStream().use { b.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, it) } }
        m.hold()
        m.photoTaken(p, f)
        assertTrue((m.state as Sheet.Saved).photo)
        assertEquals(1, SyncQueue.ops(ctx).filterIsInstance<Op.PutPhoto>().size)
        return p
    }

    @Test fun undoDropsThePhotoOpAndFile() {
        val m = model("a")
        val p = withPhoto(m)
        m.undo()
        assertTrue(SyncQueue.ops(ctx).none { it is Op.PutPhoto })
        assertFalse(m.photoFile(p).exists())
    }

    @Test fun switchCarMovesThePhoto() {
        val m = model("a")
        val p = withPhoto(m)
        m.switchCar("b")
        val n = (m.state as Sheet.Saved)
        assertTrue(n.photo)
        val op = SyncQueue.ops(ctx).filterIsInstance<Op.PutPhoto>().single()
        assertEquals("b", op.carId); assertEquals(n.park.id, op.parkId); assertEquals(m.photoFile(n.park).path, op.file)
        assertTrue(java.io.File(op.file).exists()); assertFalse(m.photoFile(p).exists())
    }

    @Test fun switchCarHoldsTheSheet() {
        val m = model("a")
        m.say("지하 4층 A 1")
        m.switchCar("b")
        assertTrue(m.held)
        m.timeout((m.state as Sheet.Saved).park.id)
        assertEquals(0, closed)
    }
}
