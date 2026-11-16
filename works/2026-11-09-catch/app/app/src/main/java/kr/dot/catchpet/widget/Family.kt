package kr.dot.catchpet.widget

import android.content.Context
import kr.dot.catchpet.dex.Dex
import kr.dot.catchpet.pet.Frames

/**
 * The widget family (operator 11-16): dex entries put on the home widget from their detail page, up to [MAX].
 * Kept as ids in SharedPreferences "catchpet" / "family", oldest first; deleted entries simply drop out on read.
 */
object Family {
    const val MAX = 6
    private const val KEY = "family"

    fun ids(ctx: Context): List<String> =
        prefs(ctx).getString(KEY, null)?.split(',')?.filter { it.isNotEmpty() }.orEmpty()

    /** Entries still in the dex and usable as a pet. */
    fun entries(ctx: Context): List<Dex.Entry> = ids(ctx).mapNotNull { Dex.get(ctx, it) }.filter { canJoin(it) }

    /** A critter, or a real thing with a bought pet picture: the ones that can stand and float. */
    fun canJoin(e: Dex.Entry) = e.critterId != null || (e.ai.owned && e.ai.sheet != null)

    fun has(ctx: Context, id: String) = id in ids(ctx)

    /** Put in or take out; false when the family is already full. */
    fun toggle(ctx: Context, id: String): Boolean {
        val now = ids(ctx).filter { Dex.get(ctx, it) != null }
        val next = when {
            id in now -> now - id
            now.size >= MAX -> return false
            else -> now + id
        }
        prefs(ctx).edit().putString(KEY, next.joinToString(",")).commit()
        PetWidgets.refresh(ctx)
        return true
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE)
}
