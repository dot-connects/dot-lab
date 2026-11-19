package kr.dot.wherepark.data

import android.content.Context
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.newCarId
import kr.dot.wherepark.ui.Onboarding
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Final-review fixes: contract car ids, refused PutCar, carried Bluetooth links, widget refresh after sync, invite reuse, null group name. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ContractFixTest {
    private lateinit var ctx: Context
    private val net = FakeNet()
    private val notices = mutableListOf<String>()
    private var refreshes = 0

    @Before fun setUp() {
        ctx = Kit.reset(net, notices)
        refreshes = 0
        WidgetRefresh.hook = { refreshes++ }
    }

    @After fun tearDown() { WidgetRefresh.hook = {} }

    /** A fake Worker that stores cars/parks like the real one (FakeNet already 404s non-contract car ids). */
    private fun server(): Pair<MutableMap<String, JSONObject>, MutableMap<String, JSONObject>> {
        val cars = linkedMapOf<String, JSONObject>(); val parks = linkedMapOf<String, JSONObject>()
        net.handler = { m, p, _, body ->
            val id = p.removePrefix("/v2/cars/").substringBefore('/')
            when {
                p == "/v2/groups" -> 200 to Kit.groupJson()
                m == "GET" && p == "/v2/state" -> 200 to JSONObject().put("groupName", JSONObject.NULL).put("members", JSONArray())
                    .put("cars", JSONArray(cars.values.toList())).put("parks", JSONObject().also { o -> parks.forEach { (k, v) -> o.put(k, v) } }).toString()
                m == "PUT" && p.endsWith("/park") -> { parks[id] = JSONObject(body!!).getJSONObject("park").put("by", "나").put("byId", "m9"); 200 to "{}" }
                m == "PUT" && p.startsWith("/v2/cars/") -> { cars[id] = JSONObject(body!!).getJSONObject("car"); 200 to "{}" }
                else -> 200 to "{}"
            }
        }
        return cars to parks
    }

    @Test fun newCarIdFollowsContract() {
        repeat(200) { assertTrue(FakeNet.CAR_ID.matches(newCarId())) }
    }

    // C1 regression: onboarding car -> createGroup -> PutCar -> pull keeps the car and its park
    @Test fun onboardingCarAndParkSurviveCreateGroupAndPull() {
        FamilyStore.leave(ctx)
        val (cars, parks) = server()
        val car = Car(newCarId(), "흰 차", -1, null, 0)
        Onboarding.saveCar(ctx, "나", car)
        ParkStore.save(ctx, Kit.park(car.id, 5)); SyncQueue.add(ctx, Op.PutPark(Kit.park(car.id, 5)))
        assertTrue(SyncEngine.run(ctx))
        assertTrue(car.id in cars); assertTrue(car.id in parks)
        assertEquals(listOf(car.id), CarStore.cars(ctx).map { it.id })
        assertEquals(5L, ParkStore.latest(ctx, car.id)!!.id)
        assertEquals("우리 가족", FamilyStore.groupName(ctx))  // M2: null group name from the server
        assertTrue(notices.isEmpty())
    }

    // C1 deferred item: a refused PutCar keeps the car and its history local and says so
    @Test fun refusedPutCarKeepsCarAndHistoryLocal() {
        val bad = Car("legacy-uuid", "옛 차", -1, null, 0)
        CarStore.upsert(ctx, bad); ParkStore.save(ctx, Kit.park(bad.id, 3)); ParkStore.save(ctx, Kit.park(bad.id, 4))
        SyncQueue.add(ctx, Op.PutCar(bad)); SyncQueue.add(ctx, Op.PutPark(Kit.park(bad.id, 4)))
        server()
        assertTrue(SyncEngine.run(ctx))
        assertTrue(SyncQueue.ops(ctx).isEmpty())
        assertEquals(listOf(bad.id), CarStore.cars(ctx).map { it.id })
        assertEquals(4L, ParkStore.latest(ctx, bad.id)!!.id); assertEquals(3L, ParkStore.prev(ctx, bad.id)!!.id)
        assertEquals(1, notices.size)
        assertTrue(SyncEngine.pull(ctx))  // a later pull still keeps it
        assertEquals(listOf(bad.id), CarStore.cars(ctx).map { it.id })
        CarStore.remove(ctx, bad.id); assertTrue(CarStore.localOnly(ctx).isEmpty())
    }

    @Test fun localOnlyLeavesOnceServerHasTheCar() {
        val c = Car(newCarId(), "흰 차", -1, null, 0)
        CarStore.upsert(ctx, c); CarStore.setLocalOnly(ctx, setOf(c.id))
        val (cars, _) = server(); cars[c.id] = Wire.carJson(c)
        assertTrue(SyncEngine.pull(ctx))
        assertTrue(CarStore.localOnly(ctx).isEmpty())
        assertEquals(listOf(c.id), CarStore.cars(ctx).map { it.id })
    }

    // I1: join keeps Bluetooth links of carried cars until the user answers
    @Test fun joinKeepsCarriedLinksUntilDeclined() {
        net.handler = { m, p, _, _ -> if (p == "/v2/join") 200 to Kit.groupJson() else if (m == "GET") 500 to null else 200 to "{}" }
        val a = Kit.car("c_0000000a"); val b = Kit.car("c_0000000b", 1)
        CarStore.upsert(ctx, a); CarStore.upsert(ctx, b)
        CarLinkStore.set(ctx, a.id, "AA:AA"); CarLinkStore.set(ctx, b.id, "BB:BB")
        assertTrue(Api.join(ctx, "K7QM-28XP-4HDA", "엄마").isSuccess)
        assertTrue(CarStore.cars(ctx).isEmpty())
        assertEquals("AA:AA", CarLinkStore.address(ctx, a.id)); assertEquals("BB:BB", CarLinkStore.address(ctx, b.id))
        FamilyStore.declineCarryOver(ctx)
        assertNull(CarLinkStore.address(ctx, a.id)); assertNull(CarLinkStore.address(ctx, b.id))
    }

    @Test fun acceptedCarryOverKeepsLinks() {
        net.handler = { m, p, _, _ -> if (p == "/v2/join") 200 to Kit.groupJson() else if (m == "GET") 500 to null else 200 to "{}" }
        val a = Kit.car("c_0000000a")
        CarStore.upsert(ctx, a); CarLinkStore.set(ctx, a.id, "AA:AA")
        Api.join(ctx, "K7QM-28XP-4HDA", "엄마")
        FamilyStore.acceptCarryOver(ctx)
        assertEquals("AA:AA", CarLinkStore.address(ctx, a.id))
        assertEquals(listOf(a.id), CarStore.cars(ctx).map { it.id })
    }

    // I2: widgets redraw after a merged pull and after a 409 local write
    @Test fun widgetRefreshAfterPullAndConflict() {
        server()
        assertTrue(SyncEngine.pull(ctx)); assertEquals(1, refreshes)
        val id = "c_0000000a"
        ParkStore.save(ctx, Kit.park(id, 10)); SyncQueue.add(ctx, Op.PutPark(Kit.park(id, 10)))
        net.handler = { m, _, _, _ -> if (m == "GET") 500 to null else 409 to """{"error":"stale","park":${Kit.serverPark(Kit.park(id, 20))}}""" }
        refreshes = 0
        SyncEngine.run(ctx)
        assertEquals(1, refreshes)
    }

    // I4: one invite reused until it nearly expires; [새 코드 만들기] forces a new one
    @Test fun inviteReusedUntilExpiry() {
        var n = 0
        val exp = 1_000_000 + 86_400_000L
        net.handler = { _, _, _, _ -> n++; 200 to """{"code":"CODE-$n","expiresAt":$exp,"link":"https://x/j#CODE-$n"}""" }
        assertEquals("CODE-1", Api.invite(ctx, now = 1_000_000).getOrThrow().code)
        assertEquals("CODE-1", Api.invite(ctx, now = 2_000_000).getOrThrow().code)
        assertEquals(1, n)
        assertEquals("CODE-2", Api.invite(ctx, fresh = true, now = 2_000_000).getOrThrow().code)
        assertEquals("CODE-3", Api.invite(ctx, now = exp - 30_000).getOrThrow().code)  // within a minute of expiry
        FamilyStore.save(ctx, "m2", "s2", "다른", listOf(Kit.me))
        assertEquals("CODE-4", Api.invite(ctx, now = 2_000_000).getOrThrow().code)  // another member never reuses it
    }

    // M2
    @Test fun peekNullGroupName() {
        net.handler = { _, _, _, _ -> 200 to """{"groupName":null,"count":2}""" }
        assertEquals("우리 가족" to 2, Api.peek("K7QM-28XP-4HDA").getOrThrow())
    }
}
