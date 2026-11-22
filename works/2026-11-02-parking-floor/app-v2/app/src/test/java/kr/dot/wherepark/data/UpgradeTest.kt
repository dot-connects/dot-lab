package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.data.Kit.car
import kr.dot.wherepark.data.Kit.park
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpgradeTest {
    @Test fun v2PrefsUpgradeShareOwnedCarsAfterFirstPull() {
        val ctx: Context = Kit.reset(FakeNet(), mutableListOf(), joined = false)
        val w = FakeWorker(); Api.transport = w
        val (auth, id) = w.device("나"); val gid = w.group(auth, "우리 가족")
        // v2 state: credential only (no me/groups), a queued PutCar, a State without parkId, old create leftovers
        Prefs.put(ctx, "wp_family", org.json.JSONObject().put("memberId", id).put("secret", auth.substringAfter('.')))
        Prefs.put(ctx, "wp_pending", "x"); Prefs.put(ctx, "wp_carry", "x"); Prefs.put(ctx, "wp_create_error", "x")
        val c = car("c_00000001"); CarStore.upsert(ctx, c); ParkStore.save(ctx, park(c.id, 5))
        SyncQueue.add(ctx, Op.PutCar(c)); SyncQueue.add(ctx, Op.PutPark(park(c.id, 5))); SyncQueue.add(ctx, Op.State(c.id, ParkState.DRIVING))
        Upgrade.run(ctx)
        assertTrue(GroupStore.joined(ctx))
        assertNull(Prefs.sp(ctx).getString("wp_pending", null)); assertNull(Prefs.sp(ctx).getString("wp_carry", null)); assertNull(Prefs.sp(ctx).getString("wp_create_error", null))
        assertTrue(SyncEngine.run(ctx))
        assertTrue(w.calls.contains("PUT /v3/cars/${c.id}"))
        assertEquals(listOf(Op.Share(c.id, gid, true, SyncQueue.ops(ctx).single().seq)), SyncQueue.ops(ctx))  // one Share per owned car
        assertTrue(SyncEngine.run(ctx))
        assertTrue(w.calls.contains("PUT /v3/cars/${c.id}/groups/$gid"))
        Upgrade.run(ctx)  // second start does nothing
        assertTrue(SyncQueue.ops(ctx).isEmpty())
    }
}
