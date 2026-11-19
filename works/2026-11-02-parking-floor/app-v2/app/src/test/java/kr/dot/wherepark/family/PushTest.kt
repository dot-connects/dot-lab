package kr.dot.wherepark.family

import android.Manifest
import android.content.Context
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.data.Kit.car
import kr.dot.wherepark.data.Kit.park
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.data.Wire
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

    private fun serverState(parkId: Long, memberNames: List<String> = listOf("나")): String = JSONObject()
        .put("groupName", "우리")
        .put("members", JSONArray(memberNames.mapIndexed { i, n -> JSONObject().put("id", "m${i + 1}").put("name", n).put("me", i == 0).put("owner", i == 0).put("lastSeen", JSONObject.NULL) }))
        .put("cars", JSONArray().put(Wire.carJson(car("a"), 0)))
        .put("parks", JSONObject().put("a", JSONObject(Kit.serverPark(park("a", parkId), "아빠")))).toString()

    private fun serve(body: String) { net.handler = { m, p, _, _ -> if (m == "GET" && p == "/v2/state") 200 to body else 200 to "{}" } }

    @Test fun parkMessageMergesAndNotifiesWhenEnabled() {
        serve(serverState(100))
        Push.handle(ctx, mapOf("type" to "park", "carId" to "a"))
        assertEquals(100L, ParkStore.latest(ctx, "a")?.id)  // merged
        assertEquals(listOf(Push.CH_PARK to "아빠가 차 a를 댔어요"), posted)
    }

    @Test fun parkMessageSilentWhenDisabled() {
        Push.setEnabled(ctx, "park:a", false)
        serve(serverState(100))
        Push.handle(ctx, mapOf("type" to "park", "carId" to "a"))
        assertNotNull(ParkStore.latest(ctx, "a"))  // still merged
        assertTrue(posted.isEmpty())
    }

    @Test fun repeatOfSameParkIsNotNotifiedTwice() {
        serve(serverState(100))
        Push.handle(ctx, mapOf("type" to "park", "carId" to "a"))
        Push.handle(ctx, mapOf("type" to "park", "carId" to "a"))
        assertEquals(1, posted.size)
    }

    @Test fun membersMessageNotifiesNewMemberOnly() {
        serve(serverState(100, listOf("나", "민지")))
        Push.handle(ctx, mapOf("type" to "members"))
        assertEquals(listOf(Push.CH_JOIN to "민지가 가족에 들어왔어요"), posted)
    }

    @Test fun realPostSkipsSilentlyWithoutPermission() {
        Push.post = { c, ch, id, t -> Push.postNotification(c, ch, id, t) }
        shadowOf(RuntimeEnvironment.getApplication()).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        serve(serverState(100))
        Push.handle(ctx, mapOf("type" to "park", "carId" to "a"))
        assertEquals(0, shadowOf(ctx.getSystemService(android.app.NotificationManager::class.java)).size())
    }
}
