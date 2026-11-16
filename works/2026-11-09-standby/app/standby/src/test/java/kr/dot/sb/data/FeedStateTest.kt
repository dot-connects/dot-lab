package kr.dot.sb.data

import kr.dot.sb.ui.state.FeedError
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.state.UsageInfo
import kr.dot.sb.ui.state.WeatherCondition
import kr.dot.sb.ui.state.WeatherInfo
import kr.dot.sb.ui.state.usageFailed
import kr.dot.sb.ui.state.usageLoaded
import kr.dot.sb.ui.state.weatherFailed
import kr.dot.sb.ui.state.weatherLoaded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** plan-r14 C2: a failed first fetch shows why (no endless loading), a failed refresh keeps the data dimmed. */
class FeedStateTest {
    private val w = WeatherInfo("서울", 7, WeatherCondition.CLEAR, 11, 2, emptyList(), 1_000)
    private val u = UsageInfo(null, null, null, null, 1_000)
    private val s0 = StandbyState(now = java.time.LocalDateTime.of(2026, 11, 9, 9, 0))

    @Test fun noDataAndNoNetworkIsNoNetwork() {
        assertEquals(FeedError.NO_NETWORK, s0.weatherFailed(FeedError.NO_NETWORK).weatherError)
        assertEquals(FeedError.NO_NETWORK, s0.usageFailed(FeedError.NO_NETWORK).usageError)
    }

    @Test fun noDataAndFetchFailedIsFailed() {
        val s = s0.weatherFailed(FeedError.FAILED).usageFailed(FeedError.FAILED)
        assertEquals(FeedError.FAILED, s.weatherError)
        assertEquals(FeedError.FAILED, s.usageError)
        assertNull(s.weather)
    }

    @Test fun successClearsTheError() {
        val s = s0.weatherFailed(FeedError.FAILED).weatherLoaded(w).usageFailed(FeedError.NO_NETWORK).usageLoaded(u)
        assertNull(s.weatherError)
        assertNull(s.usageError)
        assertEquals(w, s.weather)
        assertFalse(s.weather!!.offline)
    }

    @Test fun dataThenFailureIsOfflineNotError() {
        val s = s0.weatherLoaded(w).weatherFailed(FeedError.NO_NETWORK).usageLoaded(u).usageFailed(FeedError.FAILED)
        assertNull(s.weatherError)
        assertNull(s.usageError)
        assertTrue(s.weather!!.offline)
        assertTrue(s.usage!!.offline)
    }
}
