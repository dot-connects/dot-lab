package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.data.Kit.car
import kr.dot.wherepark.data.Kit.park
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncQueueTest {
    private lateinit var ctx: Context
    private lateinit var net: FakeNet
    private val notices = mutableListOf<String>()

    @Before fun setUp() {
        net = FakeNet(); notices.clear(); ctx = Kit.reset(net, notices)
    }

    private fun keys() = SyncQueue.ops(ctx).map { it.key }

    // row: Op key + seq, same key replaced
    @Test fun sameKeyReplacedWithHigherSeq() {
        SyncQueue.add(ctx, Op.PutPark(park("a", 1)))
        val s1 = SyncQueue.ops(ctx).single().seq
        SyncQueue.add(ctx, Op.PutPark(park("a", 2)))
        val o = SyncQueue.ops(ctx).single() as Op.PutPark
        assertEquals(2L, o.park.id); assertTrue(o.seq > s1)
    }

    @Test fun keysFollowContract() {
        SyncQueue.add(ctx, Op.PutCar(car("a"))); SyncQueue.add(ctx, Op.Undo("b", 5)); SyncQueue.add(ctx, Op.PutPark(park("c", 1)))
        SyncQueue.add(ctx, Op.State("d", ParkState.DRIVING)); SyncQueue.add(ctx, Op.PutPhoto("e", 7, "/x.jpg"))
        assertEquals(listOf("car:a", "undo:b:5", "park:c", "state:d", "photo:e:7"), keys())
    }

    // row: processing order car -> undo -> park -> state -> photo, even when photo was added first
    @Test fun processingOrderIsByKindNotArrival() {
        SyncQueue.add(ctx, Op.PutPhoto("a", 1, "/none.jpg")); SyncQueue.add(ctx, Op.State("b", ParkState.DRIVING))
        SyncQueue.add(ctx, Op.PutPark(park("a", 1))); SyncQueue.add(ctx, Op.Undo("c", 3)); SyncQueue.add(ctx, Op.PutCar(car("a")))
        assertEquals(listOf("car:a", "undo:c:3", "park:a", "state:b", "photo:a:1"), keys())
        SyncEngine.run(ctx)
        assertEquals(listOf("PUT /v3/cars/a", "POST /v3/cars/c/undo", "PUT /v3/cars/a/park", "POST /v3/cars/b/state"), net.calls.filter { !it.startsWith("GET") })
    }

    // row: DelCar pending discards that car's undo/park/state/photo ops
    @Test fun delCarDiscardsOtherOpsOfThatCar() {
        SyncQueue.add(ctx, Op.PutPark(park("a", 1))); SyncQueue.add(ctx, Op.State("b", ParkState.DRIVING))
        SyncQueue.add(ctx, Op.DelCar("a"))
        assertEquals(listOf("car:a", "state:b"), keys())
        SyncQueue.add(ctx, Op.PutPark(park("a", 2)))
        assertEquals(listOf("car:a", "state:b"), keys())
    }

    // row: PutPark drops the car's pending state op
    @Test fun putParkDropsPendingState() {
        SyncQueue.add(ctx, Op.State("a", ParkState.DRIVING)); SyncQueue.add(ctx, Op.PutPark(park("a", 1)))
        assertEquals(listOf("park:a"), keys())
    }

    // row: State merges into a pending PutPark
    @Test fun stateMergesIntoPendingPutPark() {
        SyncQueue.add(ctx, Op.PutPark(park("a", 1))); val s = SyncQueue.ops(ctx).single().seq
        SyncQueue.add(ctx, Op.State("a", ParkState.DRIVING))
        val o = SyncQueue.ops(ctx).single() as Op.PutPark
        assertEquals(ParkState.DRIVING, o.park.state); assertTrue(o.seq > s)
    }

    @Test fun stateAloneBecomesStateOp() {
        SyncQueue.add(ctx, Op.State("a", ParkState.DRIVING))
        assertEquals(listOf("state:a"), keys())
    }

    // row: no family -> nothing queued
    @Test fun noFamilyNoPendingNothingQueued() {
        GroupStore.clear(ctx)
        SyncQueue.add(ctx, Op.PutPark(park("a", 1)))
        assertTrue(SyncQueue.ops(ctx).isEmpty())
    }

    // row: 2xx drops
    @Test fun successDropsOp() {
        SyncQueue.add(ctx, Op.PutPark(park("a", 1)))
        assertTrue(SyncEngine.run(ctx)); assertTrue(SyncQueue.ops(ctx).isEmpty())
    }

    // row: seq guard - a fix that lands during the send keeps its place
    @Test fun sentSeqNotCurrentKeepsOpAndLocalState() {
        ParkStore.save(ctx, park("c_0000000a", 1)); SyncQueue.add(ctx, Op.PutPark(park("c_0000000a", 1)))
        var n = 0
        net.handler = { m, _, _, _ ->
            if (m == "GET") 500 to null
            else if (n++ == 0) { SyncQueue.add(ctx, Op.PutPark(park("c_0000000a", 1, floor = 5))); 200 to "{}" } else 500 to null
        }
        assertFalse(SyncEngine.run(ctx))
        val o = SyncQueue.ops(ctx).single() as Op.PutPark
        assertEquals(kr.dot.wherepark.core.Floor.Under(5), o.park.spot.floor)
    }

    // row: 409 stale/moved -> drop + server park written locally
    @Test fun conflictWritesServerParkWhenSeqMatches() {
        ParkStore.save(ctx, park("c_0000000a", 10)); SyncQueue.add(ctx, Op.PutPark(park("c_0000000a", 10)))
        net.handler = { m, _, _, _ -> if (m == "GET") 500 to null else 409 to """{"error":"stale","park":${Kit.serverPark(park("c_0000000a", 20))}}""" }
        SyncEngine.run(ctx)
        assertTrue(SyncQueue.ops(ctx).isEmpty())
        val l = ParkStore.latest(ctx, "c_0000000a")!!
        assertEquals(20L, l.id); assertEquals("엄마", l.byName)
    }

    @Test fun conflictWithoutParkWritesNothing() {
        ParkStore.save(ctx, park("a", 10)); SyncQueue.add(ctx, Op.Undo("a", 10))
        net.handler = { m, _, _, _ -> if (m == "GET") 500 to null else 409 to """{"error":"moved"}""" }
        SyncEngine.run(ctx)
        assertTrue(SyncQueue.ops(ctx).isEmpty()); assertEquals(10L, ParkStore.latest(ctx, "a")!!.id)
    }

    // row: 409 response is applied only when seq matches
    @Test fun conflictIgnoredWhenSeqMoved() {
        ParkStore.save(ctx, park("c_0000000a", 10)); SyncQueue.add(ctx, Op.PutPark(park("c_0000000a", 10)))
        var n = 0
        net.handler = { m, _, _, _ ->
            if (m == "GET") 500 to null
            else if (n++ == 0) { SyncQueue.add(ctx, Op.PutPark(park("c_0000000a", 10, floor = 7))); 409 to """{"error":"stale","park":${Kit.serverPark(park("c_0000000a", 20))}}""" }
            else 500 to null
        }
        SyncEngine.run(ctx)
        assertEquals(10L, ParkStore.latest(ctx, "c_0000000a")!!.id); assertEquals(1, SyncQueue.ops(ctx).size)
    }

    // row: 409 cars -> drop + local car deleted + notice
    @Test fun carFullDeletesLocalCarAndNotifies() {
        CarStore.upsert(ctx, car("c_0000000a")); SyncQueue.add(ctx, Op.PutCar(car("c_0000000a")))
        net.handler = { m, _, _, _ -> if (m == "GET") 500 to null else 409 to """{"error":"cars"}""" }
        SyncEngine.run(ctx)
        assertTrue(CarStore.cars(ctx).isEmpty()); assertTrue(SyncQueue.ops(ctx).isEmpty())
        assertEquals(listOf("차는 4대까지예요"), notices)
    }

    // row: 400/404/413 -> drop
    @Test fun badRequestDrops() {
        SyncQueue.add(ctx, Op.State("a", ParkState.DRIVING))
        net.handler = { m, _, _, _ -> if (m == "GET") 500 to null else 404 to "{}" }
        assertTrue(SyncEngine.run(ctx)); assertTrue(SyncQueue.ops(ctx).isEmpty())
    }

    // row: other/network -> retry
    @Test fun serverErrorAndOfflineRetryKeepOp() {
        SyncQueue.add(ctx, Op.PutPark(park("c_0000000a", 1)))
        net.handler = { _, _, _, _ -> 503 to null }
        assertFalse(SyncEngine.run(ctx)); assertEquals(1, SyncQueue.ops(ctx).size)
        net.handler = { _, _, _, _ -> throw java.io.IOException("offline") }
        assertFalse(SyncEngine.run(ctx)); assertEquals(1, SyncQueue.ops(ctx).size)
    }

    // row: 401 -> queue + family + cleared, local data and links stay, removed band
    @Test fun unauthorizedClearsFamilyButKeepsLocalData() {
        CarStore.upsert(ctx, car("a")); ParkStore.save(ctx, park("a", 1)); CarLinkStore.set(ctx, "a", "AA:BB")
        SyncQueue.add(ctx, Op.PutPark(park("a", 1))); SyncQueue.add(ctx, Op.State("b", ParkState.DRIVING))
        net.handler = { _, _, _, _ -> 401 to """{"error":"auth"}""" }
        assertTrue(SyncEngine.run(ctx))
        assertFalse(GroupStore.joined(ctx)); assertTrue(SyncQueue.ops(ctx).isEmpty())
        assertTrue(GroupStore.removed(ctx))
        assertEquals(1, CarStore.cars(ctx).size); assertNotNull(ParkStore.latest(ctx, "a")); assertEquals("AA:BB", CarLinkStore.address(ctx, "a"))
        net.calls.clear(); assertTrue(SyncEngine.run(ctx)); assertTrue("no silent family", net.calls.isEmpty())
    }

    // row: 401 only if the request's auth is still the stored one (join in the middle)
    @Test fun staleAuth401DoesNotClearNewAuth() {
        SyncQueue.add(ctx, Op.PutPark(park("c_0000000a", 1)))
        var n = 0
        net.handler = { m, _, _, _ ->
            if (m == "GET") 500 to null
            else if (n++ == 0) { Kit.saveGroup(ctx, "m2", "s2", "새", listOf(Kit.me)); 401 to "{}" } else 500 to null
        }
        SyncEngine.run(ctx)
        assertEquals("m2.s2", GroupStore.auth(ctx)); assertEquals(1, SyncQueue.ops(ctx).size)
    }





    @Test fun undoBeforePickMeansPutParkIsNeverMarkedStarted() {
        ParkStore.save(ctx, park("a", 1)); ParkStore.save(ctx, park("a", 2)); SyncQueue.add(ctx, Op.PutPark(park("a", 2)))
        ParkStore.undo(ctx, "a")
        SyncEngine.run(ctx)
        assertFalse(ParkStore.sendStarted(ctx, 2)); assertTrue(ParkStore.sendStarted(ctx, 1))
    }

    @Test fun severalUndosAccumulateInSeqOrder() {
        SyncQueue.add(ctx, Op.Undo("a", 3)); SyncQueue.add(ctx, Op.Undo("a", 2)); SyncQueue.add(ctx, Op.PutPark(park("a", 1)))
        assertEquals(listOf("undo:a:3", "undo:a:2", "park:a"), keys())
    }



    // row: undo before sending = op replaced / dropped
    @Test fun undoBeforeSendRestoresPrevAndReplacesOp() {
        ParkStore.save(ctx, park("a", 1)); ParkStore.save(ctx, park("a", 2)); SyncQueue.add(ctx, Op.PutPark(park("a", 2)))
        assertEquals(1L, ParkStore.undo(ctx, "a")!!.id)
        assertEquals(1L, ParkStore.latest(ctx, "a")!!.id)
        assertEquals(1L, (SyncQueue.ops(ctx).single() as Op.PutPark).park.id)
    }

    @Test fun undoBeforeSendWithoutPrevDropsOp() {
        ParkStore.save(ctx, park("a", 1)); SyncQueue.add(ctx, Op.PutPark(park("a", 1)))
        assertNull(ParkStore.undo(ctx, "a")); assertTrue(SyncQueue.ops(ctx).isEmpty()); assertNull(ParkStore.latest(ctx, "a"))
    }

    // row: undo after the send started = Undo op, park op dropped (and the mark survives: it is read from prefs)
    @Test fun undoAfterSendStartedQueuesUndoOp() {
        ParkStore.save(ctx, park("a", 1)); ParkStore.save(ctx, park("a", 2)); SyncQueue.add(ctx, Op.PutPark(park("a", 2)))
        net.handler = { _, _, _, _ -> 503 to null }
        SyncEngine.run(ctx)  // starts the send of park 2, then retry
        assertTrue(ParkStore.sendStarted(ctx, 2))
        ParkStore.undo(ctx, "a")
        assertEquals(listOf(Op.Undo("a", 2, SyncQueue.ops(ctx).single().seq)), SyncQueue.ops(ctx))
        assertEquals(1L, ParkStore.latest(ctx, "a")!!.id)
    }

    // row: queue lock - two threads add, nothing lost
    @Test fun concurrentAddsLoseNothing() {
        val t = (0..1).map { w -> Thread { repeat(100) { SyncQueue.add(ctx, Op.PutPark(park("car$w-$it", 1))) } } }
        t.forEach { it.start() }; t.forEach { it.join() }
        val ops = SyncQueue.ops(ctx)
        assertEquals(200, ops.size); assertEquals(200, ops.map { it.seq }.toSet().size)
    }

    // row: merge discarded when the queue changed during the GET
    @Test fun pullDroppedWhenQueueChangedDuringGet() {
        CarStore.upsert(ctx, car("a"))
        net.handler = { m, _, _, _ ->
            if (m == "GET") { SyncQueue.add(ctx, Op.State("a", ParkState.DRIVING)); 200 to """{"groupName":"우리","members":[],"cars":[]}""" } else 200 to "{}"
        }
        assertFalse(SyncEngine.pull(ctx))
        assertEquals(1, CarStore.cars(ctx).size)
    }
}
