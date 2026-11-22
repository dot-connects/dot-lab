package kr.dot.wherepark.family

import android.Manifest
import android.content.Context
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.GroupStore
import kr.dot.wherepark.data.Kit.car
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.data.SyncQueue
import kr.dot.wherepark.data.Kit.park
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.data.Wire
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PushTest {
    private lateinit var ctx: Context
    private lateinit var net: FakeNet
    private val posted = mutableListOf<Pair<String, String>>()

    @Before fun setUp() {
        net = FakeNet(); ctx = Kit.reset(net, mutableListOf())
        posted.clear()
        Push.post = { _, ch, _, text -> posted += ch to text }
    }

    private fun serverState(parkId: Long, memberNames: List<String> = listOf("나"), byOther: Boolean = false): String = JSONObject()
        .put("me", JSONObject().put("id", "m1").put("name", "나"))
        .put("groups", JSONArray().put(JSONObject().put("id", "g1").put("name", "우리").put("owner", true)
            .put("members", JSONArray(memberNames.mapIndexed { i, n -> JSONObject().put("id", "m${i + 1}").put("name", n).put("me", i == 0).put("owner", i == 0).put("lastSeen", JSONObject.NULL) }))))
        .put("cars", JSONArray().put(Wire.carJson(car("a").copy(ownerId = "m2", ownerName = "아빠", groups = listOf("g1")), 0)))
        .put("parks", JSONObject().put("a", JSONObject(Kit.serverPark(park("a", parkId), "아빠")).put("byOther", byOther))).toString()

    private fun serve(body: String) { net.handler = { m, p, _, _ -> if (m == "GET" && p == "/v3/state") 200 to body else 200 to "{}" } }

    @Test fun tokenPutIsRepeatedAfter24Hours() {
        assertTrue(Push.skipPut("t|a", "t|a", 1_000L, 1_000L + Push.RESEND_MS - 1))
        assertFalse(Push.skipPut("t|a", "t|a", 1_000L, 1_000L + Push.RESEND_MS))
        assertFalse(Push.skipPut("t|a", "t2|a", 1_000L, 2_000L))
    }

    @Test fun parkMessageMergesAndNotifiesWhenEnabled() {
        serve(serverState(100))
        Push.pullAndNotify(ctx)
        assertEquals(100L, ParkStore.latest(ctx, "a")?.id)  // merged
        assertEquals(listOf(Push.CH_PARK to "아빠가 차 a를 댔어요"), posted)
    }

    @Test fun parkMessageSilentWhenDisabled() {
        Push.setEnabled(ctx, "park:a", false)
        serve(serverState(100))
        Push.pullAndNotify(ctx)
        assertNotNull(ParkStore.latest(ctx, "a"))  // still merged
        assertTrue(posted.isEmpty())
    }

    @Test fun repeatOfSameParkIsNotNotifiedTwice() {
        serve(serverState(100))
        Push.pullAndNotify(ctx)
        Push.pullAndNotify(ctx)
        assertEquals(1, posted.size)
    }

    @Test fun membersMessageNotifiesNewMemberOnly() {
        serve(serverState(100, listOf("나", "민지")))
        Push.pullAndNotify(ctx)
        assertEquals(listOf(Push.CH_JOIN to "민지가 '우리' 그룹에 들어왔어요"), posted.filter { it.first == Push.CH_JOIN })
    }

    @Test fun firstPullWithNoKnownMembersDoesNotNotify() {
        net.handler = { m, p, _, _ -> if (m == "GET" && p == "/v3/state") 200 to serverState(100, listOf("나", "민지")) else 200 to "{}" }
        Prefs.sp(ctx).edit().clear().commit()  // nothing known locally yet, but still joined below
        GroupStore.save(ctx, "m1", "s1", null, emptyList())
        Push.pullAndNotify(ctx)
        assertTrue(posted.none { it.first == Push.CH_JOIN })
    }


    @Test fun driveNotifiesByDefaultAndStaysSilentWhenOff() {
        val driving = JSONObject(serverState(100))
        driving.getJSONObject("parks").put("a", JSONObject(Kit.serverPark(park("a", 100, state = ParkState.DRIVING), "아빠")).put("drivingBy", "아빠"))
        Push.setEnabled(ctx, "drive:a", false)
        serve(driving.toString())
        Push.pullAndNotify(ctx)
        assertTrue(posted.isEmpty())
        Push.setEnabled(ctx, "drive:a", true)
        ParkStore.clearCar(ctx, "a")  // the first pull already stored it; a new drive must look new
        Push.pullAndNotify(ctx)
        assertEquals(listOf(Push.CH_DRIVE to "아빠가 운행을 시작했어요"), posted)
    }

    @Test fun everyAlarmDefaultsOn() {
        assertTrue(Push.enabled(ctx, "drive:a") && Push.enabled(ctx, "park:a") && Push.enabled(ctx, "exit:a") && Push.enabled(ctx, "join"))
    }

    @Test fun maskedParkAndDriveSayOtherGroup() {
        serve(serverState(100, byOther = true))
        Push.pullAndNotify(ctx)
        assertEquals(listOf(Push.CH_PARK to "다른 그룹에서 차 a를 댔어요"), posted)
        posted.clear()
        val driving = JSONObject(serverState(101))
        driving.getJSONObject("parks").put("a", JSONObject(Kit.serverPark(park("a", 101, state = ParkState.DRIVING), "아빠")).put("drivingOther", true))
        serve(driving.toString())
        Push.pullAndNotify(ctx)
        assertEquals(listOf(Push.CH_DRIVE to "다른 그룹에서 차 a를 가져갔어요"), posted)
    }

    @Test fun myOwnCarsNeverNotify() {
        val st = JSONObject(serverState(100))
        st.getJSONArray("cars").put(0, Wire.carJson(car("a"), 0))
        serve(st.toString())
        Push.pullAndNotify(ctx)
        assertTrue(posted.isEmpty())
    }

    @Test fun discardedMergeDoesNotNotify() {
        net.handler = { m, p, _, _ ->
            if (m == "GET" && p == "/v3/state") { SyncQueue.add(ctx, Op.PutCar(car("z"))); 200 to serverState(100) } else 200 to "{}"
        }
        Push.pullAndNotify(ctx)
        assertTrue(posted.isEmpty())
    }

    @Test fun tokenConfirmedOnlyOn2xxWhileJoined() {
        assertTrue(Api.setFcmToken(ctx, "t"))
        net.handler = { _, _, _, _ -> 500 to null }
        assertFalse(Api.setFcmToken(ctx, "t"))
        Prefs.sp(ctx).edit().clear().commit()
        assertFalse(Api.setFcmToken(ctx, "t"))
    }

    @Test fun realPostSkipsSilentlyWithoutPermission() {
        Push.post = { c, ch, id, t -> Push.postNotification(c, ch, id, t) }
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        serve(serverState(100))
        Push.pullAndNotify(ctx)
        assertEquals(0, shadowOf(ctx.getSystemService(android.app.NotificationManager::class.java)).size())
    }
}
