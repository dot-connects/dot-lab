package kr.dot.sb.platform

import kr.dot.sb.model.AppConfig

/** Window brightness rules: which value applies now, how far the on-screen bar may go, where an edit is stored. */
object Brightness {
    /** WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE: let the system auto brightness decide. */
    const val AUTO = -1f
    /** Display-off frame: as dark as the panel goes without the system treating it as screen off. */
    const val OFF = 0.01f
    /** The bar never reaches 0 (a black screen with no way back); a tap wakes it anyway. */
    const val MIN = 0.02f

    fun clamp(v: Float): Float = if (v.isNaN()) MIN else v.coerceIn(MIN, 1f)

    /** Heat protection's ceiling for the applied window value (r10); stored values stay as the user set them. */
    const val HOT_MAX = 0.35f

    fun target(off: Boolean, night: Boolean, c: AppConfig, hot: Boolean = false): Float = when {
        off -> OFF
        night -> applied(c.nightMaxBrightness, c, hot)
        else -> applied(c.dayBrightness?.let(::clamp) ?: AUTO, c, hot)
    }

    /** [v] as the window gets it: capped at [HOT_MAX] while hot and protected (system AUTO becomes an explicit value). */
    fun applied(v: Float, c: AppConfig, hot: Boolean): Float =
        if (c.heatProtect && hot && (v == AUTO || v > HOT_MAX)) HOT_MAX else v

    /** What the bar shows first: the stored value, or a middle value while day brightness follows auto. */
    fun shown(night: Boolean, c: AppConfig): Float = if (night) c.nightMaxBrightness else c.dayBrightness ?: 0.5f

    /** An edit from the bar lands in the night cap at night, in the day value otherwise. */
    fun store(c: AppConfig, night: Boolean, v: Float): AppConfig =
        if (night) c.copy(nightMaxBrightness = clamp(v)) else c.copy(dayBrightness = clamp(v))
}
