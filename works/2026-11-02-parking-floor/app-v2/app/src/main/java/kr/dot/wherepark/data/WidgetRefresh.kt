package kr.dot.wherepark.data

import android.content.Context

/** Called after any data change so widgets redraw; App points [hook] at WidgetReceiver.requestAll (queued off the caller thread) (no-op in plain unit tests). */
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
