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
        mute = muted(ctx)
        if (pool != null) return
        val p = SoundPool.Builder().setMaxStreams(4)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        for (r in intArrayOf(R.raw.sfx_rail, R.raw.sfx_drop, R.raw.sfx_grip, R.raw.sfx_lift, R.raw.sfx_land, R.raw.sfx_win, R.raw.sfx_miss, R.raw.sfx_motor))
            ids[r] = p.load(ctx.applicationContext, r, 1)
        pool = p
    }

    private var mute = false

    /** The "뽑기 소리" switch on the pet screen. */
    fun muted(ctx: Context) = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getBoolean("mute", false)
    fun setMuted(ctx: Context, on: Boolean) { mute = on; ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).edit().putBoolean("mute", on).apply() }

    private var motorStream = 0
    private var motorRate = 0f

    /** The claw's motor while it goes down (rate < 1) or up (> 1); 0 stops it (operator 11-16). */
    fun motor(rate: Float) {
        if (rate == motorRate) return
        val p = pool ?: return
        if (motorStream != 0) { p.stop(motorStream); motorStream = 0 }
        motorRate = rate
        if (rate <= 0f || mute) return
        val id = ids[R.raw.sfx_motor] ?: return
        motorStream = p.play(id, 0.22f, 0.22f, 0, -1, rate)
    }

    fun play(res: Int, vol: Float = 0.6f, rate: Float = 1f) {
        if (mute) return
        val id = ids[res] ?: return
        pool?.play(id, vol, vol, 1, 0, rate)
    }
}
