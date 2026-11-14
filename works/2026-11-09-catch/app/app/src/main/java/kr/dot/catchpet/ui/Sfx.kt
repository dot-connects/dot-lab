package kr.dot.catchpet.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import kr.dot.catchpet.R

/**
 * The claw machine's sounds (Kenney "Interface Sounds", CC0, res/raw/sfx_*). Media volume, so the phone's silent
 * switch and volume keys behave as in any game. Loaded once; a sound that is not loaded yet is skipped.
 */
object Sfx {
    private var pool: SoundPool? = null
    private val ids = HashMap<Int, Int>()

    fun init(ctx: Context) {
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(4)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        for (r in intArrayOf(R.raw.sfx_rail, R.raw.sfx_drop, R.raw.sfx_grip, R.raw.sfx_lift, R.raw.sfx_land, R.raw.sfx_win, R.raw.sfx_miss))
            ids[r] = p.load(ctx.applicationContext, r, 1)
        pool = p
    }

    fun play(res: Int, vol: Float = 0.6f, rate: Float = 1f) {
        val id = ids[res] ?: return
        pool?.play(id, vol, vol, 1, 0, rate)
    }
}
