package kr.dot.wherepark.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WireTest {
    @Test fun statePreservesOwnershipGroupsAndMaskedPark() {
        val state = Wire.state(JSONObject("""{"me":{"id":"me","name":"Me"},"groups":[{"id":"g","name":"Work","owner":true,"members":[]}],"cars":[{"id":"c_00000001","name":"Car","color":-1,"plate":null,"order":0,"ownerId":"me","ownerName":"Me","groups":["g"]},{"id":"c_00000002","name":"Other","color":-1,"plate":null,"order":1,"ownerId":"other","ownerName":"Other","groups":["g"]}],"parks":{"c_00000002":{"id":1,"atMs":1,"state":"LEFT","by":null,"byId":null,"byOther":true,"drivingOther":true,"leftAt":2}}}"""))
        assertEquals("me", state.me!!.id)
        assertEquals("g", state.groups.single().id)
        assertEquals("other", state.cars.last().ownerId)
        assertEquals(state.cars.first(), Wire.car(Wire.carJson(state.cars.first())))
        val park = state.parks.values.single()!!
        assertTrue(park.byOther)
        assertTrue(park.drivingOther)
        assertEquals(2L, park.leftAt)
        assertEquals(park, Wire.park(Wire.parkJson(park, true)))
    }

    @Test fun oldCredentialSurvives() {
        val ctx = Kit.ctx()
        Prefs.sp(ctx).edit().clear().commit()
        Prefs.put(ctx, "wp_family", JSONObject().put("memberId", "old").put("secret", "secret"))
        assertEquals("old.secret", GroupStore.auth(ctx))
    }
}
