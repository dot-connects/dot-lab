package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.data.Kit.car
import kr.dot.wherepark.data.Kit.park
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Queue + settle rules against the stateful [FakeWorker]. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncV3Test {
    private lateinit var ctx: Context
    private lateinit var w: FakeWorker
    private lateinit var auth: String
    private lateinit var gid: String
    private val notices = mutableListOf<String>()
    private fun writes() = w.calls.filter { !it.startsWith("GET") }

    @Before fun setUp() {
        ctx = Kit.reset(FakeNet(), notices, joined = false)
        notices.clear()
        w = FakeWorker(); Api.transport = w
        val d = w.device("나"); auth = d.first
        gid = w.group(auth, "우리")
        val me = Member(d.second, "나", true, true, null)
        GroupStore.save(ctx, d.second, auth.substringAfter('.'), me, listOf(Group(gid, "우리", true, listOf(me))))
    }

    private fun other(carId: String = "c_00000009"): Pair<String, String> {
        val x = w.device("김대리"); w.addMember(gid, x.first)
        w.car(x.first, car(carId)); w.share(carId, gid)
        assertTrue(SyncEngine.pull(ctx))
        return x
    }

    @Test fun shareGoesAfterCarBeforePark() {
        val c = car("c_00000001"); CarStore.upsert(ctx, c)
        SyncQueue.add(ctx, Op.PutPark(park(c.id, 1))); SyncQueue.add(ctx, Op.Share(c.id, gid, true)); SyncQueue.add(ctx, Op.PutCar(c))
        assertTrue(SyncEngine.run(ctx))
        assertEquals(listOf("PUT /v3/cars/${c.id}", "PUT /v3/cars/${c.id}/groups/$gid", "PUT /v3/cars/${c.id}/park"), writes())
    }

    @Test fun sameKeyShareReplacesAndDelCarDropsIt() {
        val c = car("c_00000001"); CarStore.upsert(ctx, c)
        SyncQueue.add(ctx, Op.Share(c.id, gid, true)); SyncQueue.add(ctx, Op.Share(c.id, gid, false))
        assertEquals(listOf(Op.Share(c.id, gid, false, 2)), SyncQueue.ops(ctx))
        SyncQueue.add(ctx, Op.DelCar(c.id))
        assertEquals(listOf("car:${c.id}"), SyncQueue.ops(ctx).map { it.key })
    }

    @Test fun parkOnCarExcludedMeanwhileDropsCarAndNotifies() {
        other()
        val mine = car("c_00000001"); CarStore.upsert(ctx, mine); w.car(auth, mine)
        ParkStore.save(ctx, park("c_00000009", 3)); SyncQueue.add(ctx, Op.PutPark(park("c_00000009", 3)))
        SyncQueue.add(ctx, Op.PutPark(park(mine.id, 4)))
        w.exclude("c_00000009", gid)
        assertTrue(SyncEngine.run(ctx))
        assertEquals(listOf("이 차는 이제 그룹에 없어요. 저장하지 못했어요"), notices)
        assertEquals(listOf(mine.id), CarStore.cars(ctx).map { it.id })
        assertNull(ParkStore.latest(ctx, "c_00000009")); assertEquals(4L, ParkStore.latest(ctx, mine.id)?.id ?: 4L)
        assertTrue(SyncQueue.ops(ctx).isEmpty())
    }

    @Test fun carStillVisibleThroughAnotherGroupIsSaved() {
        val x = other()
        val gy = w.group(auth, "Y"); w.share("c_00000009", gy)
        w.exclude("c_00000009", gid)
        SyncQueue.add(ctx, Op.PutPark(park("c_00000009", 3)))
        assertTrue(SyncEngine.run(ctx))
        assertTrue(notices.isEmpty())
        assertEquals(3L, ParkStore.latest(ctx, "c_00000009")?.id)
        assertEquals(listOf("c_00000009"), CarStore.cars(ctx).map { it.id })
        assertNotNull(x)
    }

    @Test fun shareToLeftGroupIsDropped() {
        val c = car("c_00000001"); CarStore.upsert(ctx, c); w.car(auth, c)
        SyncQueue.add(ctx, Op.Share(c.id, "ffffffffffffffff", true))
        assertTrue(SyncEngine.run(ctx))
        assertTrue(SyncQueue.ops(ctx).isEmpty()); assertTrue(notices.isEmpty())
    }

    @Test fun staleParkIdWritesServerPark() {
        val c = car("c_00000001"); CarStore.upsert(ctx, c); w.car(auth, c); w.share(c.id, gid); w.parkAs(auth, park(c.id, 5))
        ParkStore.save(ctx, park(c.id, 3)); SyncQueue.add(ctx, Op.State(c.id, ParkState.DRIVING, 3))
        assertTrue(SyncEngine.run(ctx))
        assertEquals(5L, ParkStore.latest(ctx, c.id)?.id)
    }

    @Test fun v2EraStateSendsLatestParkId() {
        val c = car("c_00000001"); CarStore.upsert(ctx, c); w.car(auth, c); w.parkAs(auth, park(c.id, 5))
        ParkStore.save(ctx, park(c.id, 5)); SyncQueue.add(ctx, Op.State(c.id, ParkState.DRIVING))
        assertTrue(SyncEngine.run(ctx))
        assertTrue(SyncQueue.ops(ctx).isEmpty())
        assertEquals(ParkState.DRIVING, ParkStore.latest(ctx, c.id)?.state)
    }

    @Test fun maintenance503KeepsQueueAndLogin() {
        SyncQueue.add(ctx, Op.PutCar(car("c_00000001")))
        w.down = 503
        assertFalse(SyncEngine.run(ctx))
        assertEquals(1, SyncQueue.ops(ctx).size); assertEquals(auth, GroupStore.auth(ctx))
        assertEquals(auth, GroupStore.auth(ctx))
    }

    @Test fun createWithoutDeviceQueuesCarsAndParksBeforeShares() {
        GroupStore.leave(ctx)
        val a = car("c_0000000a"); val b = car("c_0000000b", 1)
        CarStore.upsert(ctx, a); CarStore.upsert(ctx, b); ParkStore.save(ctx, park(a.id, 1)); ParkStore.save(ctx, park(b.id, 2))
        val g = Api.createGroup(ctx, "새 그룹", "나").getOrThrow()
        assertTrue(GroupStore.joined(ctx))
        assertEquals(listOf("car:${a.id}", "car:${b.id}", "park:${a.id}", "park:${b.id}"), SyncQueue.ops(ctx).map { it.key }.let { k -> k })
        SyncQueue.add(ctx, Op.Share(a.id, g, true)); SyncQueue.add(ctx, Op.Share(b.id, g, true))
        w.calls.clear()
        assertTrue(SyncEngine.run(ctx))
        assertEquals(listOf(a.id, b.id).map { "PUT /v3/cars/$it" } + listOf(a.id, b.id).map { "PUT /v3/cars/$it/groups/$g" } + listOf(a.id, b.id).map { "PUT /v3/cars/$it/park" }, writes())
    }

    @Test fun nonOwnerCarEditsAreNeverQueued() {
        other()
        val theirs = CarStore.cars(ctx).single()
        SyncQueue.add(ctx, Op.PutCar(theirs.copy(name = "x"))); SyncQueue.add(ctx, Op.DelCar(theirs.id))
        assertTrue(SyncQueue.ops(ctx).isEmpty())
    }

    @Test fun unauthorizedDropsOthersCarsKeepsMineAndShowsBand() {
        other()
        val mine = car("c_00000001"); CarStore.upsert(ctx, mine); w.car(auth, mine)
        w.down = 401
        assertTrue(Api.state(ctx).isFailure)
        assertFalse(GroupStore.joined(ctx)); assertTrue(GroupStore.removed(ctx))
        assertEquals(listOf(mine.id), CarStore.cars(ctx).map { it.id })
        assertNull(CarStore.cars(ctx).single().ownerId)
    }
}
