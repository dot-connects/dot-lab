package kr.dot.sb.data

import kr.dot.sb.model.AppSource
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageAppsTest {
    @Test fun filtersUnlaunchableAndExcludedApps() {
        val stats = listOf(Usage("kept", 10, 10), Usage("hidden", 30, 30), Usage("home", 20, 20))
        for (source in listOf(AppSource.RECENT, AppSource.FREQUENT)) {
            assertEquals(listOf("kept"), UsageApps.rank(stats, source, setOf("kept", "home"), setOf("home"), 8))
        }
    }

    @Test fun recentUsesLatestUseAcrossDuplicates() {
        val stats = listOf(Usage("a", 10, 900), Usage("b", 20, 10), Usage("a", 30, 1), Usage("c", 15, 1000))
        assertEquals(listOf("a", "b", "c"), UsageApps.rank(stats, AppSource.RECENT, setOf("a", "b", "c"), emptySet(), 8))
    }

    @Test fun frequentSumsDuplicatesThenBreaksTiesByLatestUse() {
        val stats = listOf(Usage("a", 5, 30), Usage("b", 20, 50), Usage("a", 30, 30), Usage("c", 10, 50))
        assertEquals(listOf("a", "b", "c"), UsageApps.rank(stats, AppSource.FREQUENT, setOf("a", "b", "c"), emptySet(), 8))
    }

    @Test fun zeroEntriesDependOnTheSource() {
        val stats = listOf(Usage("never", 0, 0), Usage("recent", 20, 0), Usage("long", 0, 50), Usage("both", 10, 10))
        val launchable = stats.map { it.pkg }.toSet()
        assertEquals(listOf("recent", "both"), UsageApps.rank(stats, AppSource.RECENT, launchable, emptySet(), 8))
        assertEquals(listOf("long", "both"), UsageApps.rank(stats, AppSource.FREQUENT, launchable, emptySet(), 8))
    }

    @Test fun limitAppliesAfterFilteringMergingAndSorting() {
        val stats = listOf(Usage("hidden", 100, 100), Usage("a", 30, 30), Usage("a", 20, 20), Usage("b", 10, 10), Usage("c", 5, 5))
        for (source in listOf(AppSource.RECENT, AppSource.FREQUENT)) {
            assertEquals(listOf("a", "b"), UsageApps.rank(stats, source, setOf("a", "b", "c"), emptySet(), 2))
            assertEquals(emptyList<String>(), UsageApps.rank(stats, source, setOf("a", "b", "c"), emptySet(), 0))
            assertEquals(emptyList<String>(), UsageApps.rank(emptyList(), source, emptySet(), emptySet(), 4))
        }
    }
}
