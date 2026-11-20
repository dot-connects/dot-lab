package kr.dot.wherepark.family

import android.content.Context
import android.net.Uri
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.Kit
import org.json.JSONObject
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
class RequestTest {
    private lateinit var ctx: Context
    private lateinit var net: FakeNet
    private val code = "K7QM-28XP-4HDA"
    private val joins = mutableListOf<String>()
    private var joinAnswer: Pair<Int, String?> = 200 to null
    private var peekAnswer: Pair<Int, String?> = 200 to """{"groupName":"민지네 가족","count":3}"""

    @Before fun setUp() {
        net = FakeNet(); ctx = Kit.reset(net, mutableListOf(), joined = false)
        joinAnswer = 200 to Kit.groupJson()
        net.handler = { m, p, _, body ->
            when {
                m == "POST" && p == "/v2/join" -> { joins += body!!; joinAnswer }
                m == "POST" && p == "/v2/peek" -> peekAnswer
                m == "GET" -> 500 to null
                else -> 200 to "{}"
            }
        }
    }

    private fun model() = JoinModel(ctx, bg = { it() }, main = { it() })

    // requester: pending-request lifecycle

    @Test fun startStoresAndExpiresAfter24h() {
        val now = 1_800_000_000_000L
        val r = JoinRequest.start(ctx, " 민준 ", now)
        assertTrue(JoinRequest.SECRET.matches(r.secret))
        assertEquals(PendingRequest(r.secret, "민준", now), JoinRequest.pending(ctx, now + JoinRequest.TTL_MS))
        // the link carries only sha256(secret), never the secret
        assertEquals("https://dot-lab-parking.dotconnects.workers.dev/r#${FakeNet.sha256(r.secret)}.%EB%AF%BC%EC%A4%80", JoinRequest.link(r))
        assertFalse(JoinRequest.link(r).contains(r.secret))
        assertNull(JoinRequest.pending(ctx, now + JoinRequest.TTL_MS + 1))
        assertNull(JoinRequest.pending(ctx, now))  // dropped, not just hidden
        assertTrue(JoinRequest.start(ctx, "a", now).secret != r.secret)
    }

    @Test fun pollNeverJoinsOrDrops() {
        val r = JoinRequest.start(ctx, "민준")
        assertEquals(JoinRequest.Poll.Waiting, JoinRequest.poll(ctx))
        assertEquals(listOf("POST /v2/requests/poll"), net.calls)
        assertEquals(listOf<String?>(null), net.auths)
        net.requests[r.hash] = code
        assertEquals(JoinRequest.Poll.Code(code, "민준", null), JoinRequest.poll(ctx))
        net.requestBy[r.hash] = "엄마"
        assertEquals(JoinRequest.Poll.Code(code, "민준", "엄마"), JoinRequest.poll(ctx))
        assertTrue(joins.isEmpty())  // no blind join, also not on app start (MainActivity only opens the confirm)
        assertNotNull(JoinRequest.pending(ctx))
        JoinRequest.cancel(ctx)
        assertEquals(JoinRequest.Poll.None, JoinRequest.poll(ctx))
    }

    @Test fun acceptedRequestAsksThenJoinsWithStoredNameAndClears() {
        val m = model()
        m.request("민준") {}
        val hash = m.waiting!!.hash
        m.poll()
        assertNotNull(m.waiting); assertFalse(m.fromRequest); assertFalse(m.busy)
        net.requests[hash] = code; net.requestBy[hash] = "엄마"
        m.poll()
        assertTrue(m.fromRequest)
        assertEquals("엄마", m.acceptedBy)  // the confirm shows "엄마님이 받아 줬어요"
        assertEquals("민지네 가족" to 3, m.peek)
        assertTrue(joins.isEmpty())  // asked, not joined
        assertEquals(listOf("POST /v2/requests/poll", "POST /v2/requests/poll", "POST /v2/peek"), net.calls)
        m.join()
        val sent = JSONObject(joins.single())
        assertEquals(code, sent.getString("code")); assertEquals("민준", sent.getString("name"))
        assertTrue(m.finished)
        assertNull(JoinRequest.pending(ctx)); assertNull(m.waiting)
        assertTrue(net.requests.isEmpty())  // the join used the code, the server dropped the row
    }

    @Test fun noOnTheConfirmWithdrawsOnTheServer() {
        val m = model()
        m.request("민준") {}
        net.requests[m.waiting!!.hash] = code
        m.poll()
        m.cancelRequest()
        assertEquals(1, net.cancels)
        assertTrue(net.requests.isEmpty())
        assertNull(JoinRequest.pending(ctx)); assertNull(m.waiting); assertNull(m.peek); assertFalse(m.fromRequest)
        assertTrue(joins.isEmpty())
    }

    @Test fun onlyARefusedCodeDropsTheRequest() {
        val m = model()
        m.request("민준") {}
        val hash = m.waiting!!.hash
        net.requests[hash] = code
        m.poll()
        joinAnswer = 409 to """{"error":"full"}"""
        m.join()
        assertEquals("가족이 꽉 찼어요", m.error); assertNotNull(JoinRequest.pending(ctx))
        joinAnswer = 429 to """{"error":"slow"}"""
        m.join()
        assertEquals("잠시 뒤 다시 확인할게요", m.error); assertNotNull(JoinRequest.pending(ctx))
        net.handler = { _, _, _, _ -> throw java.io.IOException("offline") }
        m.join()
        assertNotNull(JoinRequest.pending(ctx)); assertTrue(m.fromRequest)
        net.handler = { _, _, _, _ -> 404 to """{"error":"code"}""" }
        m.join()
        assertEquals("요청이 만료됐어요. 다시 보내 주세요", m.error)
        assertNull(JoinRequest.pending(ctx)); assertNull(m.waiting)
    }

    @Test fun deadCodeOnPeekDropsButBusyPeekKeeps() {
        val m = model()
        m.request("민준") {}
        net.requests[m.waiting!!.hash] = code
        peekAnswer = 429 to """{"error":"slow"}"""
        m.poll()
        assertEquals("잠시 뒤 다시 확인할게요", m.error); assertNotNull(m.waiting)
        peekAnswer = 404 to """{"error":"code"}"""
        m.poll()
        assertNull(m.waiting); assertNull(JoinRequest.pending(ctx))
    }

    @Test fun anySuccessfulJoinOrCreateEndsTheRequest() {
        JoinRequest.start(ctx, "민준")
        assertTrue(JoinFlow.join(ctx, "AAAA-BBBB-CCCC", "민준").isSuccess)  // the normal invite path
        assertNull(JoinRequest.pending(ctx))
        FamilyStore.clear(ctx)
        JoinRequest.start(ctx, "민준")
        net.handler = { _, _, _, _ -> 200 to Kit.groupJson() }
        assertTrue(Api.createGroup(ctx, "민준", "민준네 가족").isSuccess)
        assertNull(JoinRequest.pending(ctx))
    }

    @Test fun pollDoesNotDisableCodeEntry() {
        val jobs = ArrayDeque<() -> Unit>()
        val m = JoinModel(ctx, bg = { jobs.addLast(it) }, main = { it() })
        m.request("민준") {}
        m.poll(); m.poll()
        assertFalse(m.busy)
        assertEquals(1, jobs.size)  // one poll at a time
    }

    // member: the /r link and [받기]

    @Test fun requestLinkFromFragmentOrQuery() {
        val id = FakeNet.sha256("0123456789abcdef0123456789abcdef")
        assertEquals(id to "민준", JoinRequest.fromUri(Uri.parse("https://dot-lab-parking.dotconnects.workers.dev/r#$id.%EB%AF%BC%EC%A4%80")))
        assertEquals(id to "민준", JoinRequest.fromUri(Uri.parse("https://dot-lab-parking.dotconnects.workers.dev/r?q=" + Uri.encode("$id.%EB%AF%BC%EC%A4%80"))))
        assertEquals(id to "a.b", JoinRequest.fromUri(Uri.parse("https://x/r#$id.a.b")))
        assertNull(JoinRequest.fromUri(Uri.parse("https://x/r#${id.uppercase()}.민준")))
        assertNull(JoinRequest.fromUri(Uri.parse("https://x/r#$id.")))
        assertNull(JoinRequest.fromUri(Uri.parse("https://x/r#0123456789abcdef0123456789abcdef.민준")))  // a bare secret is not a link
        assertNull(JoinRequest.fromUri(Uri.parse("https://x/j#$id.민준")))
        assertNull(JoinRequest.fromUri(null))
    }

    @Test fun acceptIsOnePutWithTheHashOnly() {
        FamilyStore.save(ctx, "m1", "s1", "우리", listOf(Kit.me))
        val id = FakeNet.sha256("0123456789abcdef0123456789abcdef")
        assertTrue(JoinRequest.accept(ctx, id).isSuccess)
        assertEquals(listOf("PUT /v2/requests"), net.calls)  // no invite of mine is created or replaced
        assertEquals(listOf<String?>("m1.s1"), net.auths)
        assertEquals(code, net.requests[id])
        assertEquals(409, (JoinRequest.accept(ctx, id).exceptionOrNull() as kr.dot.wherepark.data.ApiException).status)  // first accept wins
        assertEquals(400, (JoinRequest.accept(ctx, "nope").exceptionOrNull() as kr.dot.wherepark.data.ApiException).status)
    }

    @Test fun acceptWithoutFamilyCallsNothing() {
        assertTrue(JoinRequest.accept(ctx, FakeNet.sha256("x")).isFailure)
        assertTrue(net.calls.isEmpty())
    }

    @Test fun ownRequestIsRecognised() {
        val r = JoinRequest.start(ctx, "민준")
        assertEquals(r.hash, JoinRequest.fromUri(Uri.parse(JoinRequest.link(r)))!!.first)  // AcceptScreen own = this match
    }

    @Test fun objectParticle() {
        assertEquals("엄마를", JoinRequest.objectOf("엄마"))
        assertEquals("민준을", JoinRequest.objectOf("민준"))
        assertEquals("Sam를", JoinRequest.objectOf("Sam"))
    }
}
