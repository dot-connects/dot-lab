package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.core.ParkState
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
class MergeTest {
    private lateinit var ctx: Context
    private val net = FakeNet()

    private val notices = mutableListOf<String>()
    @Before fun setUp() { ctx = Kit.reset(net, notices) }

    private fun st(cars: List<kr.dot.wherepark.core.Car>, parks: Map<String, kr.dot.wherepark.core.Park?> = emptyMap()) =
        ApiState(Member("m1", "나", true, true, 5L), listOf(Group("g1", "서버 가족", true, listOf(Member("m1", "나", true, true, 5L)))), cars, parks)

    private fun merge(s: ApiState) = assertTrue(Merge.state(ctx, s, SyncQueue.generation(ctx)))

    @Test fun serverIsCanonEvenWhenAnOlderParkComesBack() {
        ParkStore.save(ctx, park("a", 1)); ParkStore.save(ctx, park("a", 2))
        merge(st(listOf(car("a")), mapOf("a" to park("a", 1))))  // server undo: old record is current again
        assertEquals(1L, ParkStore.latest(ctx, "a")!!.id)
        assertEquals("서버 가족", GroupStore.groups(ctx).single().name)
    }

    @Test fun serverRemovesLocalCarsAndParksNotInQueue() {
        CarStore.upsert(ctx, car("gone")); ParkStore.save(ctx, park("gone", 1))
        merge(st(listOf(car("a")), mapOf("a" to null)))
        assertEquals(listOf("a"), CarStore.cars(ctx).map { it.id }); assertNull(ParkStore.latest(ctx, "gone"))
    }

    @Test fun pendingParkOpKeepsLocalPark() {
        ParkStore.save(ctx, park("a", 5)); SyncQueue.add(ctx, Op.PutPark(park("a", 5)))
        ParkStore.save(ctx, park("b", 1))
        merge(st(listOf(car("a"), car("b")), mapOf("a" to park("a", 3), "b" to park("b", 9))))
        assertEquals(5L, ParkStore.latest(ctx, "a")!!.id); assertEquals(9L, ParkStore.latest(ctx, "b")!!.id)
    }

    @Test fun pendingStateOrUndoKeepsLocalPark() {
        ParkStore.save(ctx, park("a", 5)); SyncQueue.add(ctx, Op.Undo("a", 5))
        ParkStore.save(ctx, park("b", 2)); SyncQueue.add(ctx, Op.State("b", ParkState.DRIVING))
        merge(st(listOf(car("a"), car("b")), mapOf("a" to park("a", 7), "b" to park("b", 9))))
        assertEquals(5L, ParkStore.latest(ctx, "a")!!.id); assertEquals(2L, ParkStore.latest(ctx, "b")!!.id)
    }

    @Test fun pendingCarOpKeepsLocalCar() {
        SyncQueue.add(ctx, Op.PutCar(car("a").copy(name = "내 이름")))
        merge(st(listOf(car("a").copy(name = "서버 이름"), car("b", 1))))
        assertEquals(listOf("내 이름", "차 b"), CarStore.cars(ctx).map { it.name })
    }

    @Test fun pendingCarOpKeepsLocalCarMissingOnServer() {
        SyncQueue.add(ctx, Op.PutCar(car("new")))
        merge(st(emptyList()))
        assertEquals(listOf("new"), CarStore.cars(ctx).map { it.id })
    }

    @Test fun pendingDelCarIsNotRevived() {
        SyncQueue.add(ctx, Op.DelCar("a"))
        merge(st(listOf(car("a"), car("b", 1))))
        assertEquals(listOf("b"), CarStore.cars(ctx).map { it.id })
    }

    @Test fun carLinksUntouched() {
        CarLinkStore.set(ctx, "a", "AA:BB")
        merge(st(emptyList()))
        assertEquals("AA:BB", CarLinkStore.address(ctx, "a")); assertEquals("a", CarLinkStore.carOf(ctx, "AA:BB"))
    }

    @Test fun staleGenerationIsDiscarded() {
        val g = SyncQueue.generation(ctx)
        SyncQueue.add(ctx, Op.State("a", ParkState.DRIVING))
        assertFalse(Merge.state(ctx, st(listOf(car("a"))), g)); assertTrue(CarStore.cars(ctx).isEmpty())
    }

    @Test fun wireRoundTripKeepsNullsAndServerFields() {
        val j = org.json.JSONObject(Kit.serverPark(park("a", 3).copy(drivingBy = null)))
        val p = Wire.park(j, "a")
        assertNull(p.spot.pillar); assertNull(p.drivingBy); assertEquals("엄마", p.byName); assertEquals("m2", p.byId)
        assertFalse(Wire.parkJson(p, false).has("raw"))
    }

    @Test fun myQueuedCarWinsOthersAreServerAndPendingShareShows() {
        val mine = car("a"); CarStore.upsert(ctx, mine)
        SyncQueue.add(ctx, Op.PutCar(mine.copy(name = "내 수정"))); SyncQueue.add(ctx, Op.Share("a", "g1", true))
        val theirs = car("z", 1).copy(ownerId = "m2", ownerName = "아빠", groups = listOf("g1"))
        val groups = listOf(Group("g1", "우리", true, listOf(Member("m1", "나", true, true, null))))
        merge(ApiState(Kit.me, groups, listOf(car("a"), theirs), emptyMap()))
        assertEquals(listOf("내 수정", theirs.name), CarStore.cars(ctx).map { it.name })
        assertEquals(listOf("g1"), CarStore.cars(ctx).first().groups)
        merge(ApiState(Kit.me, groups, listOf(car("a")), emptyMap()))  // the other car left visibility
        assertEquals(listOf("a"), CarStore.cars(ctx).map { it.id })
    }

    @Test fun groupGoneWithoutLeavingNotifies() {
        merge(ApiState(Kit.me, emptyList(), emptyList(), emptyMap()))
        assertEquals(listOf("'우리' 그룹에서 나가게 됐어요"), notices)
    }
}
