package kr.dot.wherepark.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import java.time.ZoneId
import java.time.ZonedDateTime
import kr.dot.wherepark.car.ExitNotifier
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.GroupStore
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.data.SyncEngine
import kr.dot.wherepark.data.SyncQueue
import kr.dot.wherepark.family.JoinFlow
import kr.dot.wherepark.family.JoinModel
import kr.dot.wherepark.family.Push
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppScreensTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var ctx: Context
    private lateinit var net: FakeNet

    @Before fun setUp() { net = FakeNet(); ctx = Kit.reset(net, mutableListOf(), joined = false) }

    // onboarding routing (spec 10, contracts section 3)

    @Test fun referrerCodeGoesStraightToJoin() {
        Prefs.put(ctx, "wp_referrer_code", "K7QM-28XP-4HDA")
        assertEquals(Route.Join("K7QM-28XP-4HDA"), Onboarding.route(ctx, null))
    }

    @Test fun linkCodeGoesStraightToJoin() = assertEquals(Route.Join("AAAA-BBBB-CCCC"), Onboarding.route(ctx, "AAAA-BBBB-CCCC"))

    @Test fun noCodeRunsTheFourSteps() = assertEquals(Route.Steps(joined = false), Onboarding.route(ctx, null))

    @Test fun afterAJoinTheCarStepIsSkippedAndCodesIgnored() {
        GroupStore.save(ctx, "m1", "s1", Kit.me, listOf(kr.dot.wherepark.data.Group("g1", "우리", true, listOf(Kit.me))))
        Prefs.put(ctx, "wp_referrer_code", "K7QM-28XP-4HDA")
        assertEquals(Route.Steps(joined = true), Onboarding.route(ctx, null))
    }

    @Test fun finishedOnboardingIsHome() {
        Onboarding.finish(ctx)
        assertEquals(Route.Home, Onboarding.route(ctx, "AAAA-BBBB-CCCC"))
    }

    @Test fun carStepStaysSoloUntilExplicitGroupCreation() {
        val car = Kit.car("c_00000001").copy(name = "흰 차")
        Onboarding.saveCar(ctx, car)
        assertEquals(listOf(car.id), CarStore.cars(ctx).map { it.id })
        assertTrue(SyncQueue.ops(ctx).isEmpty())
        assertFalse(GroupStore.joined(ctx))
        SyncEngine.run(ctx)
        assertTrue(net.calls.isEmpty())
    }

    // home card states (spec 8.3)

    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 11, 20, 16, 36, 0, 0, zone).toInstant().toEpochMilli()
    private fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()
    private fun park(atMs: Long, memo: String? = "엘리베이터 옆", state: ParkState = ParkState.PARKED) =
        Park(atMs, "w", Spot(Floor.Under(2), "C", 37, null, memo), atMs, "m2", "아빠", null, false, state, if (state == ParkState.DRIVING) "엄마" else null)

    @Test fun cardNone() {
        val c = HomeCards.of(null, pending = false, micReady = true, linked = false, nowMs = now)
        assertEquals(CardKind.NONE, c.kind); assertNull(c.tag); assertNull(c.line)
    }

    @Test fun cardOkShowsElapsedAndMemo() {
        val c = HomeCards.of(park(at(20, 14, 32)), false, true, false, now)
        assertEquals(CardKind.OK, c.kind); assertEquals("2시간 4분째", c.tag); assertEquals("오늘 14:32", c.whenText)
        assertEquals(CardLine.MEMO, c.line); assertEquals("엘리베이터 옆", c.lineText); assertEquals("아빠", c.who)
    }

    @Test fun cardPendingBeatsOldAndSaysJustNow() {
        val c = HomeCards.of(park(now - 20_000), pending = true, micReady = true, linked = false, nowMs = now)
        assertEquals(CardKind.PENDING, c.kind); assertEquals("전송 대기", c.tag); assertEquals("방금", c.whenText)
        assertEquals(CardLine.PENDING, c.line); assertEquals("신호가 잡히면 그룹에 보내요", c.lineText)
    }

    @Test fun cardDrivingNamesWhoAndBluetoothOnlyWhenLinked() {
        val p = park(at(19, 19, 5), memo = null, state = ParkState.DRIVING)
        val linked = HomeCards.of(p, true, true, true, now)
        assertEquals(CardKind.DRIVING, linked.kind); assertEquals("엄마 운행 중", linked.tag); assertEquals("어제 19:05", linked.whenText)
        assertEquals(CardLine.BT, linked.line)
        assertNull(HomeCards.of(p, false, true, false, now).line)
    }

    @Test fun cardOldAfterTwoCalendarDays() {
        val c = HomeCards.of(park(at(16, 14, 32), memo = null), false, true, false, now)
        assertEquals(CardKind.OLD, c.kind); assertEquals("4일 전", c.tag); assertEquals("11월 16일 14:32", c.whenText)
        assertEquals(CardKind.OK, HomeCards.of(park(at(19, 9, 0)), false, true, false, now).kind)  // yesterday is not old yet
    }

    @Test fun cardNoMicReplacesMemoLine() {
        val c = HomeCards.of(park(at(20, 14, 32)), false, micReady = false, linked = false, nowMs = now)
        assertEquals(CardLine.NOMIC, c.line); assertEquals(HomeCards.NOMIC, c.lineText)
        assertEquals(CardLine.NOMIC, HomeCards.of(null, false, false, false, now).line)
    }

    // settings toggles persist in the stores the notifiers read


    // join screen model (Task 9 review: double tap, stale peek, error wording)

    private val jobs = ArrayDeque<() -> Unit>()
    private fun model() = JoinModel(ctx, bg = { jobs.addLast(it) }, main = { it() })
    private fun drain() { while (jobs.isNotEmpty()) jobs.removeFirst()() }

    @Test fun doubleTapJoinsOnce() {
        net.handler = { m, p, _, _ ->
            when { p == "/v3/peek" -> 200 to """{"groupName":"민지네 가족","count":3}"""; p == "/v3/join" -> 200 to Kit.groupJson(); m == "GET" -> 500 to null; else -> 200 to "{}" }
        }
        val m = model()
        m.useCode("AAAA-BBBB-CCCC"); drain()
        m.name = "엄마"
        m.join(); m.join()
        assertTrue(m.busy)
        drain()
        assertEquals(1, net.calls.count { it == "POST /v3/join" })
        assertTrue(m.finished)
    }

    @Test fun stalePeekAnswerIsDropped() {
        net.handler = { _, _, _, body -> 200 to (if (body!!.contains("AAAA")) """{"groupName":"옛 가족","count":1}""" else """{"groupName":"새 가족","count":2}""") }
        val m = model()
        m.useCode("AAAA-AAAA-AAAA")
        m.useCode("BBBB-BBBB-BBBB")
        val first = jobs.removeFirst()
        drain()        // B answers first
        first()        // then the old A answer arrives
        assertEquals("새 가족" to 2, m.peek)
        assertEquals("BBBB-BBBB-BBBB", m.code)
    }

    @Test fun peekErrorsSayWhatWentWrong() {
        net.handler = { _, _, _, _ -> throw java.io.IOException("offline") }
        val m = model()
        m.useCode("AAAA-AAAA-AAAA"); drain()
        assertEquals("인터넷 연결을 확인해 주세요", m.error)
        net.handler = { _, _, _, _ -> 404 to """{"error":"not found"}""" }
        m.useCode("BBBB-BBBB-BBBB"); drain()
        assertEquals("코드가 만료됐거나 잘못됐어요", m.error)
        assertNull(m.code)
    }
}
