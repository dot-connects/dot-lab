package kr.dot.wherepark.data

import android.content.Context

/** Called after a merge so widgets redraw. Task 7 fills [hook] with the AppWidgetManager update; until then it is a no-op. */
object WidgetRefresh {
    @Volatile var hook: (Context) -> Unit = {}

    fun request(ctx: Context) {
        try {
            hook(ctx)
        } catch (_: RuntimeException) {
            // a widget failure must not break a pull
        }
    }
}
