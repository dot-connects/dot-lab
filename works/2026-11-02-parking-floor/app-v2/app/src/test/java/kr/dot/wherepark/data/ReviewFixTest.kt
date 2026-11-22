package kr.dot.wherepark.data

import kr.dot.wherepark.data.Kit.car
import kr.dot.wherepark.family.JoinRequest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Review fixes for app v3: legacy share survives an early pull, join/create bookkeeping for an already-joined device. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReviewFixTest {
    @Test fun legacyShareSurvivesAPullBeforeTheQueuedCarIsSent() {
        val ctx = Kit.reset(FakeNet(), mutableListOf(), joined = false)
        val w = FakeWorker(); Api.transport = w
        val (auth, id) = w.device("나"); val gid = w.group(auth, "우리 가족")
        Prefs.put(ctx, "wp_family", org.json.JSONObject().put("memberId", id).put("secret", auth.substringAfter('.')))
        val c = car("c_00000001"); CarStore.upsert(ctx, c)
        SyncQueue.add(ctx, Op.PutCar(c))
        Upgrade.run(ctx)
        assertTrue(SyncEngine.pull(ctx))  // e.g. a push pull: the server does not have the car yet
        assertTrue(SyncEngine.run(ctx))   // PutCar goes up, next pull sees it
        assertEquals(listOf(Op.Share(c.id, gid, true, SyncQueue.ops(ctx).single().seq)), SyncQueue.ops(ctx))
    }

    @Test fun staleCredentialOnJoinIsDropped() {
        val net = FakeNet(); val ctx = Kit.reset(net, mutableListOf())
        net.handler = { _, p, _, _ -> if (p == "/v3/join") 401 to """{"error":"auth"}""" else 200 to "{}" }
        assertTrue(Api.join(ctx, "K7QM28XP4HDA", null).isFailure)
        assertFalse(GroupStore.joined(ctx))
        assertTrue(GroupStore.removed(ctx))
    }

    @Test fun joiningAsAMemberEndsTheStoredRequestAndInvalidatesInFlightPulls() {
        val net = FakeNet(); val ctx = Kit.reset(net, mutableListOf())
        JoinRequest.start(ctx, "나")
        net.handler = { _, p, _, _ -> if (p == "/v3/join") 200 to Kit.groupJson() else 200 to "{}" }
        val gen = SyncQueue.generation(ctx)
        assertTrue(Api.join(ctx, "K7QM28XP4HDA", null).isSuccess)
        assertNull(JoinRequest.pending(ctx))
        assertNotEquals(gen, SyncQueue.generation(ctx))
    }
}
