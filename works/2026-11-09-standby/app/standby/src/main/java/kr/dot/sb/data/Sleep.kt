package kr.dot.sb.data

import kotlinx.coroutines.flow.MutableStateFlow
import kr.dot.sb.model.SleepSound
import kr.dot.sb.ui.state.SleepState
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

/** Process-wide sleep sound state: the service writes, the state holder reads. */
object SleepBus {
    val state = MutableStateFlow(SleepState())
}

/** Procedural noise, mono 22.05 kHz (no audio assets). Pure: the service only pipes it to an AudioTrack. */
class NoiseGen(val sound: SleepSound, seed: Long = System.nanoTime()) {
    private val rnd = Random(seed)
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f; private var b3 = 0f; private var b4 = 0f; private var b5 = 0f; private var b6 = 0f
    private var brown = 0f
    private var prevWhite = 0f
    private var drop = 0f
    private var crack = 0f
    private var n = 0L

    private fun white() = rnd.nextFloat() * 2f - 1f

    /** Paul Kellet's economy pink filter. */
    private fun pink(): Float {
        val w = white()
        b0 = 0.99886f * b0 + w * 0.0555179f; b1 = 0.99332f * b1 + w * 0.0750759f
        b2 = 0.96900f * b2 + w * 0.1538520f; b3 = 0.86650f * b3 + w * 0.3104856f
        b4 = 0.55000f * b4 + w * 0.5329522f; b5 = -0.7616f * b5 - w * 0.0168980f
        val out = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f) * 0.11f
        b6 = w * 0.115926f
        return out
    }

    private fun brown(): Float { brown = (brown + 0.02f * white()) / 1.02f; return brown * 3.5f }

    private fun sample(): Float = when (sound) {
        SleepSound.WHITE -> brown() * 0.9f   // r10: the one "백색소음" is the old brown noise
        SleepSound.RAIN -> {
            val w = white()
            if (rnd.nextFloat() < 0.0006f) drop = 0.5f + rnd.nextFloat() * 0.4f
            drop *= 0.97f
            val hiss = (w - prevWhite) * 0.12f // crude high-pass: the sizzle of rain
            prevWhite = w
            pink() * 0.45f + hiss + drop * w
        }
        SleepSound.FIRE -> {
            // D10: low brown rumble + a quiet hiss + short white-noise crackles (~9 a second, 1-3 ms each)
            if (rnd.nextFloat() < 0.0004f) crack = 0.3f + rnd.nextFloat() * 0.7f
            crack *= 0.94f
            val w = white()
            val hiss = (w - prevWhite) * 0.04f
            prevWhite = w
            brown() * 0.7f + hiss + crack * w
        }
        SleepSound.WAVES -> {
            val swell = 0.5f + 0.5f * sin(2.0 * PI * n / (RATE * 9.0)).toFloat() // one wave every 9 s
            (brown() * 0.55f + pink() * 0.35f) * (0.25f + 0.75f * swell)
        }
    }

    /** Fills [out] with signed 16-bit samples scaled by [gain] (0..1, the fade-out). */
    fun fill(out: ShortArray, gain: Float) {
        for (i in out.indices) {
            n++
            out[i] = (sample().coerceIn(-1f, 1f) * gain * 0.6f * 32767f).toInt().toShort()
        }
    }

    companion object { const val RATE = 22050 }
}
