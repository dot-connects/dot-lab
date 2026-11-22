package kr.dot.wherepark.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.data.ApiException
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.Group
import kr.dot.wherepark.data.GroupStore
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.data.Member
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.SyncQueue
import kr.dot.wherepark.family.AcceptScreen
import kr.dot.wherepark.family.AcceptState
import kr.dot.wherepark.family.AddGroupCopy
import kr.dot.wherepark.family.LeaveText
import kr.dot.wherepark.family.Push
import kr.dot.wherepark.widget.WidgetRenderer
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 4 screens: texts per state (spec section 5) and the pure helpers behind them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupScreensTest {
    @get:Rule val rule = createComposeRule()
    private lateinit var ctx: Context
    private val me = Kit.me
    private fun grp(id: String, name: String, vararg others: String) = Group(id, name, true, listOf(me) + others.map { Member(it, it, false, false, null) })
    private fun mine(id: String, name: String, vararg groups: String) = Kit.car(id).copy(name = name, groups = groups.toList())
    private fun theirs(id: String, name: String, owner: String, vararg groups: String) = Kit.car(id).copy(name = name, ownerId = "x", ownerName = owner, groups = groups.toList())
    private fun park(id: String, at: Long, byOther: Boolean = false, state: ParkState = ParkState.PARKED) =
        Park(id.hashCode().toLong(), id, Spot(Floor.Under(2), "C", 37, null, null), at, "x", "김대리", null, false, state, byOther = byOther, leftAt = if (state == ParkState.LEFT) at else null)

    @Before fun setUp() {
        org.robolectric.RuntimeEnvironment.setQualifiers("w360dp-h900dp-port-xhdpi")
        ctx = Kit.reset(FakeNet(), mutableListOf())
    }

    // U1 settings

    @Test fun settingsAloneExplainsAndOffersAddGroup() {
        GroupStore.leave(ctx)
        rule.setContent { SettingsScreen(SettingsActions()) }
        rule.onNodeWithText("아직 혼자 쓰고 있어요").assertExists()
        rule.onNodeWithText("＋ 그룹 추가").assertExists()
        rule.onNodeWithText("차마다 알림은 위 '내 차'에서 차를 눌러 바꿔요").assertExists()
        rule.onNodeWithText("문의").assertDoesNotExist()
        rule.onNodeWithText("차에서 내리면 물어보기").assertDoesNotExist()
    }

    @Test fun settingsGroupRowsShowCounts() {
        Kit.saveGroup(ctx, "m1", "s1", "회사 주차", listOf(me, Member("m2", "김대리", false, false, null)))
        CarStore.upsert(ctx, mine("a", "회색 차", "g1")); CarStore.upsert(ctx, theirs("b", "검은 차", "김대리", "g1"))
        rule.setContent { SettingsScreen(SettingsActions()) }
        rule.onNodeWithText("회사 주차 · 2명").assertExists()
        rule.onNodeWithText("내 차 1대 · 다른 사람 차 1대").assertExists()
        rule.onNodeWithText("＋ 그룹 추가").assertExists()
    }

    @Test fun settingsAddGroupHiddenAtThreeGroups() {
        GroupStore.setState(ctx, me, listOf(grp("g1", "A"), grp("g2", "B"), grp("g3", "C")))
        rule.setContent { SettingsScreen(SettingsActions()) }
        rule.onNodeWithText("＋ 그룹 추가").assertDoesNotExist()
    }

    // U2 car screen

    @Test fun myCarScreenSharesPerGroupAndAlarmsPersist() {
        GroupStore.setState(ctx, me, listOf(grp("g1", "회사 주차"), grp("g2", "우리 가족")))
        val car = mine("a", "회색 차", "g1", "g2"); CarStore.upsert(ctx, car)
        val shares = mutableListOf<Pair<String, Boolean>>()
        rule.setContent { CarEditScreen(CarDraft(car), car, isNew = false, linked = false, a = EditActions(share = { g, on -> shares += g to on })) }
        rule.onNodeWithText("두 그룹 모두 같은 자리를 봐요. 누가 대든 마지막 자리 하나로 맞춰져요.").performScrollTo().assertExists()
        rule.onNodeWithText("회사 주차").performScrollTo().performClick()
        assertEquals(listOf("g1" to false), shares)
        assertTrue(Push.enabled(ctx, "park:a"))
        rule.onNodeWithText("그룹에서 댔을 때").performScrollTo().performClick()
        rule.waitForIdle()
        assertFalse(Push.enabled(ctx, "park:a"))
        rule.onNodeWithText("이 차 지우기").performScrollTo().performClick()
        rule.onNodeWithText("회색 차를 지울까요? 그룹 폰에서도 사라져요.").assertExists()
    }

    @Test fun soloCarHasNoGroupRowsOrSharedDeleteWarning() {
        GroupStore.leave(ctx)
        val car = mine("a", "회색 차"); CarStore.upsert(ctx, car)
        rule.setContent { CarEditScreen(CarDraft(car), car, isNew = false, linked = false, a = EditActions()) }
        rule.onNodeWithText("이 차가 보이는 그룹").assertDoesNotExist()
        rule.onNodeWithText("그룹에서 댔을 때").assertDoesNotExist()
        rule.onNodeWithText("차에서 내리면 물어보기").assertExists()
    }

    @Test fun othersCarIsReadOnlyWithAlarmsAndBluetooth() {
        GroupStore.setState(ctx, me, listOf(grp("g1", "회사 주차")))
        val car = theirs("b", "검은 차", "김대리", "g1")
        rule.setContent { CarEditScreen(CarDraft(car), car, isNew = false, linked = false, a = EditActions()) }
        rule.onNodeWithText("회사 주차 그룹에서 같이 봐요. 이름·색·번호는 차 주인만 바꿀 수 있어요.").assertExists()
        rule.onNodeWithText("차 블루투스 연결").performScrollTo().assertExists()
        rule.onNodeWithText("그룹에서 가져갔을 때").performScrollTo().assertExists()
        rule.onNodeWithText("차에서 내리면 물어보기").assertDoesNotExist()  // only once linked
        rule.onNodeWithText("이 차 지우기").assertDoesNotExist()
        rule.onNodeWithText("이 차가 보이는 그룹").assertDoesNotExist()
    }

    @Test fun newCarIsSharedIntoEveryGroupIAmIn() {
        GroupStore.setState(ctx, me, listOf(grp("g1", "A"), grp("g2", "B")))
        CarStore.addMine(ctx, Kit.car("c_00000001"))
        assertEquals(listOf("g1", "g2"), CarStore.cars(ctx).single().groups)
        assertEquals(listOf("car:c_00000001", "share:c_00000001:g1", "share:c_00000001:g2"), SyncQueue.ops(ctx).map { it.key })
        assertTrue(SyncQueue.ops(ctx).filterIsInstance<Op.Share>().all { it.on })
    }

    // U3 group detail

    @Test fun leaveTextCombinations() {
        val a = grp("g1", "회사 주차"); val b = grp("g2", "우리 가족")
        val gray = mine("a", "회색 차", "g1", "g2"); val black = theirs("b", "검은 차", "김대리", "g1"); val blue = theirs("c", "파란 차", "박과장", "g1")
        assertEquals(listOf(
            "회사 주차에서 나갈까요?",
            "회색 차가 이 그룹에서 빠지고, 검은 차·파란 차는 내 홈에서 사라져요.",
            "회색 차와 기록은 이 폰에 남고, 우리 가족에서는 계속 보여요.",
        ), LeaveText.of(a, listOf(gray, black, blue), listOf(a, b), "m1"))
        assertEquals(listOf("회사 주차에서 나갈까요?", "검은 차는 내 홈에서 사라져요."), LeaveText.of(a, listOf(black), listOf(a, b), "m1"))
        assertEquals(listOf("회사 주차에서 나갈까요?", "회색 차가 이 그룹에서 빠져요.", "마지막 그룹이에요. 나가면 혼자 쓰는 상태가 돼요."), LeaveText.of(a, listOf(mine("a", "회색 차", "g1")), listOf(a), "m1"))
        assertEquals(listOf("회사 주차에서 나갈까요?"), LeaveText.of(a, emptyList(), listOf(a, b), "m1"))
    }

    // U4 add group

    @Test fun addGroupErrorCopy() {
        assertEquals("이미 들어가 있는 그룹이에요", AddGroupCopy.error(ApiException(409, "member")))
        assertEquals("그룹은 3개까지예요", AddGroupCopy.error(ApiException(409, "groups")))
        assertEquals("그룹이 꽉 찼어요", AddGroupCopy.error(ApiException(409, "full")))
        assertEquals("인터넷 연결을 확인해 주세요", AddGroupCopy.error(ApiException(0, "")))
        assertEquals("그룹은 3개까지 만들거나 들어갈 수 있어요 · 지금 2개", AddGroupCopy.footer(2))
    }

    @Test fun joinPickerSharesOnlyTheChosenCars() {
        CarStore.upsert(ctx, mine("c_00000001", "회색 차")); CarStore.upsert(ctx, mine("c_00000002", "흰 차"))
        AddGroupCopy.share(ctx, setOf("c_00000002"), "g1")
        assertEquals(listOf("share:c_00000002:g1"), SyncQueue.ops(ctx).map { it.key })
        assertEquals(listOf("g1"), CarStore.cars(ctx).first { it.id == "c_00000002" }.groups)
    }

    // U5 home

    @Test fun homeOrderMineFirstThenOthersByLatestParkParklessLast() {
        val g1 = grp("g1", "회사 주차"); val g2 = grp("g2", "우리 가족")
        val m1 = mine("a", "내 차 2").copy(order = 1); val m0 = mine("b", "내 차 1").copy(order = 0)
        val o1 = theirs("c", "검은 차", "김대리", "g1"); val o2 = theirs("d", "파란 차", "박과장", "g1", "g2"); val o3 = theirs("e", "빨간 차", "이사원", "g2"); val o4 = theirs("f", "초록 차", "최", "g2")
        val parks = mapOf("c" to park("c", 100), "d" to park("d", 200), "e" to null, "f" to null)
        val (mineCars, others) = HomeOrder.arrange(listOf(o1, m1, o3, o2, m0, o4), parks, listOf(g1, g2), "m1")
        assertEquals(listOf("b", "a"), mineCars.map { it.id })
        assertEquals(listOf("d", "c", "e", "f"), others.map { it.first.id })
        assertEquals("박과장님 차 · 회사 주차 외 1", others[0].second)
        assertEquals("김대리님 차 · 회사 주차", others[1].second)
        assertEquals("이사원님 차 · 우리 가족", others[2].second)
    }

    @Test fun maskedParkSaysOtherGroupAndLeftCardDependsOnWhoDrove() {
        val now = 10_000_000L
        assertEquals("다른 그룹", HomeCards.of(park("c", now - 60_000, byOther = true), false, true, false, now).who)
        val left = park("c", now - 3_600_000, state = ParkState.LEFT)
        val others = HomeCards.of(left, false, true, false, now, driver = false)
        assertEquals(CardKind.LEFT, others.kind); assertTrue(others.lineText!!.endsWith("내림 · 위치 미입력")); assertEquals(CardLine.MEMO, others.line)
        val driver = HomeCards.of(left, false, true, false, now, driver = true)
        assertTrue(driver.lineText!!.endsWith("내림 · 어디 댔는지 말해 주세요")); assertEquals(CardLine.LEFT, driver.line)
        assertTrue(left.copy(drivingById = "m1").iAmDriver("m1")); assertFalse(left.copy(drivingById = "x").iAmDriver("m1")); assertFalse(left.copy(drivingOther = true).iAmDriver("m1"))
        assertEquals("신호가 잡히면 그룹에 보내요", HomeCards.of(park("c", now - 5_000), pending = true, micReady = true, linked = false, nowMs = now).lineText)
    }

    @Test fun homeShowsThreeOthersThenMore() {
        val g = grp("g1", "회사 주차")
        val cars = (1..5).map { theirs("c$it", "차 $it", "김대리", "g1") }
        val d = HomeData(emptyList(), emptyMap(), emptySet(), emptySet(), true, 0L, false, null, HomeOrder.arrange(cars, emptyMap(), listOf(g), "m1").second, "m1")
        rule.setContent { HomeScreen(d, AppActions()) }
        rule.onNodeWithText("2대 더 보기").assertExists()
        rule.onNodeWithText("2대 더 보기").performScrollTo().performClick()
        rule.onNodeWithText("2대 더 보기").assertDoesNotExist()
    }

    @Test fun removedBandOffersAddGroup() {
        val d = HomeData(emptyList(), emptyMap(), emptySet(), emptySet(), true, 0L, true, null)
        rule.setContent { HomeScreen(d, AppActions()) }
        rule.onNodeWithText("그룹에서 나가게 됐어요").assertExists()
        rule.onNodeWithText("내 차와 기록은 이 폰에 남아 있어요").assertExists()
        rule.onNodeWithText("그룹 추가").assertExists()
    }

    // U6 onboarding and requests

    @Test fun groupStepOffersFourWays() {
        rule.setContent { StepGroups(joined = false, create = {}, join = {}, ask = {}, next = {}) }
        listOf("같이 볼까요?", "그룹 만들기", "초대 코드로 들어가기", "그룹에게 요청하기", "나중에").forEach { rule.onNodeWithText(it).assertExists() }
    }

    @Test fun carStepCanBeSkippedAndAsksNoName() {
        var skipped = false
        rule.setContent { StepCar(CarStep("c_00000001"), {}, { skipped = true }, {}) }
        rule.onNodeWithText("가족에게 보일 내 이름").assertDoesNotExist()
        rule.onNodeWithText("다음에 하기").performClick()
        assertTrue(skipped)
    }

    @Test fun acceptScreenOneGroup() {
        rule.setContent { AcceptScreen("민준", listOf(grp("g1", "우리 가족")), false, AcceptState.ASK, {}, {}) }
        rule.onNodeWithText("민준을 '우리 가족' 그룹에 받을까요?").assertExists()
    }

    @Test fun acceptScreenChooserGreysFullGroups() {
        val full = Group("g2", "꽉 찬 그룹", true, (1..8).map { Member("m$it", "m$it", false, false, null) })
        var picked: String? = null
        rule.setContent { AcceptScreen("민준", listOf(grp("g1", "회사 주차"), full), false, AcceptState.ASK, { picked = it }, {}) }
        rule.onNodeWithText("민준을 어느 그룹에 받을까요?").assertExists()
        rule.onNodeWithText("꽉 찼어요").assertExists()
        rule.onNodeWithText("꽉 찬 그룹").performClick()
        assertNull(picked)
        rule.onNodeWithText("회사 주차").performClick()
        assertEquals("g1", picked)
    }

    @Test fun acceptScreenWithoutGroupAsksToCreateOne() {
        rule.setContent { AcceptScreen("민준", emptyList(), false, AcceptState.ASK, {}, {}) }
        rule.onNodeWithText("먼저 그룹을 만들어야 받을 수 있어요").assertExists()
        rule.onNodeWithText("그룹 만들기").assertExists()
    }

    // U8 widget

    @Test fun widgetMainCarMayBeSomeoneElsesAndFallsBackToMine() {
        val mineLate = mine("a", "내 차").copy(order = 5); val b = theirs("b", "검은 차", "김대리").copy(order = 0)
        assertEquals("b", WidgetRenderer.mainCar(listOf(mineLate, b), "b", "m1")?.id)       // visible main, even someone else's
        assertEquals("a", WidgetRenderer.mainCar(listOf(mineLate, b), "gone", "m1")?.id)    // gone: my lowest order before any other
        assertEquals("b", WidgetRenderer.mainCar(listOf(b), "gone", "m1")?.id)               // none of mine: any visible car
        val c = theirs("c", "파란 차", "박").copy(order = 1)
        val (shown, more) = WidgetRenderer.carsFor(listOf(mineLate, b, c), "b", twoRow = true, meId = "m1")
        assertEquals(listOf("b", "a"), shown.map { it.id }); assertEquals(0, more)           // my cars + the main car only
    }
}
