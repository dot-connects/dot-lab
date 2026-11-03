package kr.dot.parkingfloor

import android.content.Context

/** Car and widget settings. */
object Settings {
    const val INK_AUTO = "auto"
    const val INK_LIGHT = "light"
    const val INK_DARK = "dark"

    private fun prefs(context: Context) = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun carAddress(context: Context): String? = prefs(context).getString("car", null)
    fun carName(context: Context): String? = prefs(context).getString("carName", null)
    fun setCar(context: Context, address: String, name: String) =
        prefs(context).edit().putString("car", address).putString("carName", name).apply()

    fun clearCar(context: Context) = prefs(context).edit().remove("car").remove("carName").apply()

    /** Light panel behind the widget, 0 = fully transparent. */
    fun panel(context: Context): Int = prefs(context).getInt("panel", 0)
    fun setPanel(context: Context, percent: Int) = prefs(context).edit().putInt("panel", percent.coerceIn(0, 100)).apply()

    fun ink(context: Context): String = prefs(context).getString("ink", INK_AUTO) ?: INK_AUTO
    fun setInk(context: Context, mode: String) = prefs(context).edit().putString("ink", mode).apply()
}
