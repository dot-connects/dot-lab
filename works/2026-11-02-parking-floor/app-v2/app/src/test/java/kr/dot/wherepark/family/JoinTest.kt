package kr.dot.wherepark.family

import android.content.Context
import android.net.Uri
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.data.Kit.car
import kr.dot.wherepark.data.Member
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.SyncQueue
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
class JoinTest {
    private lateinit var ctx: Context
    private lateinit var net: FakeNet
    private var pushStarts = 0

    @Before fun setUp() {
        net = FakeNet(); ctx = Kit.reset(net, mutableListOf())
        pushStarts = 0
        JoinFlow.startPush = { pushStarts++ }
        net.handler = { m, p, _, _ -> if (m == "POST" && p == "/v2/join") 200 to Kit.groupJson() else if (m == "GET") 500 to null else 200 to "{}" }
    }

    @Test fun codeFromFragmentOrQuery() {
        assertEquals("K7QM-28XP-4HDA", JoinCode.fromUri(Uri.parse("https://dot-lab-parking.dotconnects.workers.dev/j#K7QM-28XP-4HDA")))
        assertEquals("K7QM-28XP-4HDA", JoinCode.fromUri(Uri.parse("https://dot-lab-parking.dotconnects.workers.dev/j?c=k7qm28xp4hda")))
        assertEquals("K7QM-28XP-4HDA", JoinCode.fromUri(Uri.parse("https://x/j?c=ignored#K7QM28XP4HDA")))
        assertNull(JoinCode.fromUri(Uri.parse("https://x/j#nope")))
        assertNull(JoinCode.fromUri(Uri.parse("https://x/j?c=K7QM-28XP-4HD1")))  // 0/1/I-style chars are outside the alphabet
        assertNull(JoinCode.fromUri(null))
    }

    @Test fun codeFromClipboardTextAndReferrer() {
        assertEquals("K7QM-28XP-4HDA", JoinCode.fromText("초대 코드는 K7QM-28XP-4HDA 입니다"))
        assertEquals("K7QM-28XP-4HDA", JoinCode.fromText("k7qm28xp4hda"))
        assertNull(JoinCode.fromText("hello world"))
        assertNull(JoinCode.fromText(null))
        assertEquals("K7QM-28XP-4HDA", Referrer.parse("utm_source=invite&code=K7QM28XP4HDA"))
        assertNull(Referrer.parse("utm_source=google-play&utm_medium=organic"))
        assertNull(Referrer.parse(null))
    }

    @Test fun peekIsAnonymousAndOnlyGroupNameAndCount() {
        net.handler = { _, _, _, _ -> 200 to """{"groupName":"민지네 가족","count":3}""" }
        val r = JoinFlow.peek("K7QM-28XP-4HDA")
        assertEquals("민지네 가족" to 3, r.getOrNull())
        assertEquals(listOf("POST /v2/peek"), net.calls)
        assertEquals(listOf<String?>(null), net.auths)
        assertEquals("m1", FamilyStore.me(ctx)?.id)  // nothing about the other family was stored
        assertEquals(1, FamilyStore.members(ctx).size)
    }

    @Test fun joinOrderNewJoinThenDeleteWithOldAuth() {
        SyncQueue.add(ctx, Op.PutCar(car("a")))
        val r = JoinFlow.join(ctx, "K7QM-28XP-4HDA", " 엄마 ")
        assertTrue(r.isSuccess)
        assertEquals(listOf("POST /v2/join", "DELETE /v2/me"), net.calls)
        assertEquals(listOf<String?>(null, "m1.s1"), net.auths)
        assertEquals("m9.s9", FamilyStore.auth(ctx))
        assertTrue(SyncQueue.ops(ctx).isEmpty())
        assertEquals(1, pushStarts)
    }

    @Test fun carryOverKeepsSameCarIds() {
        CarStore.upsert(ctx, car("a")); CarStore.upsert(ctx, car("b", 1))
        JoinFlow.join(ctx, "K7QM-28XP-4HDA", "엄마")
        assertEquals(listOf("a", "b"), JoinFlow.carryOver(ctx).map { it.id })
        FamilyStore.acceptCarryOver(ctx)
        assertEquals(listOf("a", "b"), CarStore.cars(ctx).map { it.id })
        assertEquals(listOf("a", "b"), SyncQueue.ops(ctx).filterIsInstance<Op.PutCar>().map { it.car.id })
        assertTrue(JoinFlow.carryOver(ctx).isEmpty())
    }

    @Test fun noCarryOverWhenOldFamilyHadOthers() {
        FamilyStore.save(ctx, "m1", "s1", "우리", listOf(Kit.me, Member("m2", "아빠", false, false, null)))
        CarStore.upsert(ctx, car("a"))
        JoinFlow.join(ctx, "K7QM-28XP-4HDA", "엄마")
        assertTrue(JoinFlow.carryOver(ctx).isEmpty())
    }

    @Test fun failedJoinRestoresQueueAndPendingAndKeepsOldFamily() {
        SyncQueue.add(ctx, Op.PutCar(car("a")))
        FamilyStore.setPendingGroup(ctx, "나", "우리집")
        net.handler = { _, _, _, _ -> 404 to """{"error":"code"}""" }
        val r = JoinFlow.join(ctx, "K7QM-28XP-4HDA", "엄마")
        assertTrue(r.isFailure)
        assertEquals(listOf("POST /v2/join"), net.calls)  // old family never deleted
        assertEquals("m1.s1", FamilyStore.auth(ctx))
        assertEquals(listOf("a"), SyncQueue.ops(ctx).filterIsInstance<Op.PutCar>().map { it.car.id })
        assertEquals("나" to "우리집", FamilyStore.pendingGroup(ctx))
        assertEquals(0, pushStarts)
    }

    @Test fun joinWithoutFamilyOffersLocalCars() {
        Prefs_clearFamily()
        CarStore.upsert(ctx, car("a"))
        assertTrue(JoinFlow.join(ctx, "K7QM-28XP-4HDA", "엄마").isSuccess)
        assertEquals(listOf("POST /v2/join"), net.calls)  // no old auth, nothing to delete
        assertEquals(listOf("a"), JoinFlow.carryOver(ctx).map { it.id })
    }

    @Test fun removeRules() {
        val now = 100L * 24 * 3600 * 1000
        val owner = Member("m1", "아빠", true, true, now)
        val fresh = Member("m2", "엄마", false, false, now - 1000)
        val old = Member("m3", "옛날 폰", false, false, now - 41L * 24 * 3600 * 1000)
        val plain = Member("m4", "민지", true, false, now)
        assertTrue(FamilyRules.canRemove(owner, fresh, now))
        assertFalse(FamilyRules.canRemove(plain, fresh, now))
        assertTrue(FamilyRules.canRemove(plain, old, now))
        assertFalse(FamilyRules.canRemove(owner, owner, now))
    }

    private fun Prefs_clearFamily() { FamilyStore.clear(ctx); assertFalse(FamilyStore.joined(ctx)); Api.transport = net }
}
