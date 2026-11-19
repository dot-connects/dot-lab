package kr.dot.wherepark.car

import android.content.Context
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.data.CarLinkStore
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.data.SyncQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExitNotifierTest {
    private lateinit var c: Context
    private var t = 1_000_000L
    private val posted = mutableListOf<ExitNotice>()
    private val cancelled = mutableListOf<String>()

    @Before fun setUp() {
        c = Kit.reset(FakeNet(), mutableListOf())
        CarStore.upsert(c, Kit.car("a"))
        CarLinkStore.set(c, "a", "AA:AA")
        posted.clear(); cancelled.clear(); t = 1_000_000L
        ExitNotifier.now = { t }
        ExitNotifier.post = { _, n -> posted += n }
        ExitNotifier.cancel = { _, id -> cancelled += id }
    }

    private fun min(m: Int) { t += m * 60_000L }

    @Test fun throttlesToOncePer30Minutes() {
        ExitNotifier.onDisappeared(c, "AA:AA")
        min(10); ExitNotifier.onDisappeared(c, "AA:AA")
        assertEquals(1, posted.size)
        assertEquals("차 a에서 내렸어요", posted[0].title)
        min(21); ExitNotifier.onDisappeared(c, "AA:AA")
        assertEquals(2, posted.size)
    }

    @Test fun disabledCarGetsNoNotice() {
        ExitNotifier.setEnabled(c, "a", false)
        ExitNotifier.onDisappeared(c, "AA:AA")
        assertTrue(posted.isEmpty())
    }

    @Test fun reappearWithinTwoMinutesCancelsAndKeepsStateAndThrottleFree() {
        ParkStore.save(c, Kit.park("a", 1))
        ExitNotifier.onDisappeared(c, "AA:AA")
        min(1); ExitNotifier.onAppeared(c, "AA:AA")
        assertEquals(listOf("a"), cancelled)
        assertEquals(ParkState.PARKED, ParkStore.latest(c, "a")!!.state)
        assertTrue(SyncQueue.ops(c).isEmpty())
        min(1); ExitNotifier.onDisappeared(c, "AA:AA")
        assertEquals(2, posted.size)
    }

    @Test fun threeNotParkedAddsTurnOffAction() {
        repeat(3) { ExitNotifier.notParked(c, "a") }
        ExitNotifier.onDisappeared(c, "AA:AA")
        assertEquals(listOf("말하기", "층 고르기", "안 댔어요", "이 차 알림 끄기"), posted[0].actions)
        ExitNotifier.turnOff(c, "a")
        assertFalse(ExitNotifier.enabled(c, "a"))
    }

    @Test fun twoNotParkedStaysThreeActions() {
        repeat(2) { ExitNotifier.notParked(c, "a") }
        ExitNotifier.onDisappeared(c, "AA:AA")
        assertEquals(3, posted[0].actions.size)
    }

    @Test fun newParkResetsNotParkedCount() {
        repeat(3) { ExitNotifier.notParked(c, "a") }
        ParkStore.save(c, Kit.park("a", 5))
        ExitNotifier.onDisappeared(c, "AA:AA")
        assertEquals(3, posted[0].actions.size)
    }

    @Test fun appearAfterTwoMinutesSetsDrivingAndQueuesState() {
        ParkStore.save(c, Kit.park("a", 1))
        ExitNotifier.onDisappeared(c, "AA:AA")
        min(5); ExitNotifier.onAppeared(c, "AA:AA")
        val p = ParkStore.latest(c, "a")!!
        assertEquals(ParkState.DRIVING, p.state)
        assertEquals("나", p.drivingBy)
        assertEquals(listOf<Op>(Op.State("a", ParkState.DRIVING, 1)), SyncQueue.ops(c))
    }

    @Test fun unknownAddressIgnored() {
        ExitNotifier.onDisappeared(c, "ZZ:ZZ")
        assertTrue(posted.isEmpty())
    }

    @Test fun testNoticeIgnoresToggleAndThrottle() {
        ExitNotifier.setEnabled(c, "a", false)
        ExitNotifier.testNotice(c, "a")
        assertEquals(1, posted.size)
    }

    @Test fun unlinkKeepsOtherCarsAssociations() {
        CarLinkStore.set(c, "b", "BB:BB")
        val gone = mutableListOf<Int>()
        val stopped = mutableListOf<String>()
        val cdm = object : Cdm {
            override fun associations() = listOf(1 to "AA:AA", 2 to "BB:BB")
            override fun disassociate(id: Int) { gone += id }
            override fun stopObserving(address: String) { stopped += address }
        }
        CarLink.unlink(c, "a", cdm)
        assertEquals(listOf(1), gone)
        assertEquals(listOf("AA:AA"), stopped)
        assertEquals(null, CarLinkStore.address(c, "a"))
        assertEquals("BB:BB", CarLinkStore.address(c, "b"))
    }
}
