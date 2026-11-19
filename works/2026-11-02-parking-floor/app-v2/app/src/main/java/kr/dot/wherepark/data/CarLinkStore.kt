package kr.dot.wherepark.data

import android.content.Context

/** Bluetooth address <-> car, phone-local only (the server never sees addresses). */
object CarLinkStore {
    private const val KEY = "wp_links"

    fun address(ctx: Context, carId: String): String? = Prefs.obj(ctx, KEY).let { if (it.has(carId)) it.getString(carId) else null }

    fun carOf(ctx: Context, address: String): String? {
        val o = Prefs.obj(ctx, KEY)
        return o.keys().asSequence().firstOrNull { o.getString(it) == address }
    }

    fun set(ctx: Context, carId: String, address: String?) = synchronized(SyncQueue) {
        val o = Prefs.obj(ctx, KEY)
        if (address != null) o.keys().asSequence().filter { o.getString(it) == address }.toList().forEach { o.remove(it) }
        if (address == null) o.remove(carId) else o.put(carId, address)
        Prefs.put(ctx, KEY, o)
    }
}
