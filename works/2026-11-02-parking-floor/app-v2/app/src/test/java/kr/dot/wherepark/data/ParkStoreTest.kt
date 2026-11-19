package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.data.Kit.car
import kr.dot.wherepark.data.Kit.park
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
class ParkStoreTest {
    private lateinit var ctx: Context
    private val net = FakeNet()

    @Before fun setUp() { ctx = Kit.reset(net, mutableListOf()) }

    @Test fun saveLatestPrevUndo() {
        ParkStore.save(ctx, park("a", 1)); ParkStore.save(ctx, park("a", 2))
        assertEquals(2L, ParkStore.latest(ctx, "a")!!.id); assertEquals(1L, ParkStore.prev(ctx, "a")!!.id)
        assertEquals("a", ParkStore.lastUsedCarId(ctx))
        assertEquals(1L, ParkStore.undo(ctx, "a")!!.id); assertEquals(1L, ParkStore.latest(ctx, "a")!!.id)
        assertNull(ParkStore.undo(ctx, "a")); assertNull(ParkStore.latest(ctx, "a"))
    }

    @Test fun sameIdIsAFixNotANewRecord() {
        ParkStore.save(ctx, park("a", 1)); ParkStore.save(ctx, park("a", 1, floor = 5))
        assertEquals(1, ParkStore.history(ctx, "a").size)
    }

    @Test fun historyCapsAtFifty() {
        (1L..60L).forEach { ParkStore.save(ctx, park("a", it)) }
        val h = ParkStore.history(ctx, "a")
        assertEquals(50, h.size); assertEquals(60L, h.first().id)
    }

    @Test fun sendStartedIsPerParkAndPersisted() {
        assertFalse(ParkStore.sendStarted(ctx, 7)); ParkStore.markSendStarted(ctx, 7)
        assertTrue(ParkStore.sendStarted(ctx, 7)); assertFalse(ParkStore.sendStarted(ctx, 8))
    }

    @Test fun carStoreRoundTrip() {
        CarStore.upsert(ctx, car("b", 1)); CarStore.upsert(ctx, car("a", 0)); CarStore.upsert(ctx, car("b", 1).copy(name = "x"))
        assertEquals(listOf("a", "b"), CarStore.cars(ctx).map { it.id }); assertEquals("x", CarStore.cars(ctx)[1].name)
    }

    // join (contracts section 3): success keeps the alone family's cars as an offer, old auth is deleted
    @Test fun joinSuccessCarriesCarsAndDeletesOldMembership() {
        CarStore.upsert(ctx, car("c_1")); SyncQueue.add(ctx, Op.PutCar(car("c_1")))
        net.handler = { _, p, _, _ -> if (p == "/v2/join") 200 to Kit.groupJson("m5", "s5", "남의 집") else 200 to "{}" }
        val st = Api.join(ctx, "ABC", "나").getOrThrow()
        assertEquals("남의 집", st.groupName); assertEquals("m5.s5", FamilyStore.auth(ctx))
        assertTrue(SyncQueue.ops(ctx).isEmpty())
        assertEquals("m1.s1", net.auths.last()); assertEquals("DELETE /v2/me", net.calls.last())
        assertEquals(listOf("c_1"), FamilyStore.carryOver(ctx).map { it.id })
        FamilyStore.acceptCarryOver(ctx)
        assertEquals(listOf("car:c_1"), SyncQueue.ops(ctx).map { it.key }); assertTrue(FamilyStore.carryOver(ctx).isEmpty())
    }

    @Test fun joinFailureRestoresQueueAndPendingGroup() {
        SyncQueue.add(ctx, Op.PutCar(car("c_1"))); FamilyStore.setPendingGroup(ctx, "나", "가족")
        net.handler = { _, _, _, _ -> 404 to """{"error":"code"}""" }
        assertTrue(Api.join(ctx, "BAD", "나").isFailure)
        assertEquals("m1.s1", FamilyStore.auth(ctx)); assertEquals(listOf("car:c_1"), SyncQueue.ops(ctx).map { it.key })
        assertEquals("가족" , FamilyStore.pendingGroup(ctx)!!.second); assertTrue(FamilyStore.carryOver(ctx).isEmpty())
    }
}
