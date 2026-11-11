package kr.dot.sb.data

import android.content.Context
import kr.dot.sb.ui.state.UsageFeed
import kr.dot.sb.ui.state.UsageInfo

/** Public build: no usage card, no PC feed, no secrets (the personal flavor has the real ones). */
object UsageFeature {
    const val enabled = false

    @Suppress("UNUSED_PARAMETER")
    fun feed(ctx: Context): UsageFeed = object : UsageFeed {
        override suspend fun fetch(): UsageInfo? = null
    }
}
