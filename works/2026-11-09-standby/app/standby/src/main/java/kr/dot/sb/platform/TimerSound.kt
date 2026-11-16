package kr.dot.sb.platform

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.VibratorManager
import kr.dot.sb.model.TimerAlert
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/** Plays a timer end alert. An interface so tests never reach AudioTrack. */
fun interface TimerAlerts {
    /** [alert] [times] rounds; [falling] = a break ended (오르는 세 음 goes down). */
    fun play(alert: TimerAlert, falling: Boolean, times: Int)
}

/** r15 §5 recipes (the mockup's Web Audio JS): sine tones, 10 ms exponential attack, exponential fade; a vibration pattern. */
object TimerSound {
    class Tone(val hz: Double, val at: Double, val dur: Double, val vol: Double = 0.35)
    class Recipe(val tones: List<Tone>, val vibration: LongArray) {
        val length: Double get() = tones.maxOf { it.at + it.dur }
    }

    /** Seconds of silence between rounds. */
    const val GAP = 0.6
    const val RATE = 44_100

    fun recipe(a: TimerAlert, falling: Boolean): Recipe = when (a) {
        TimerAlert.BELL -> Recipe(listOf(Tone(523.0, 0.0, 1.5, 0.4), Tone(1046.0, 0.0, 0.9, 0.12), Tone(1567.0, 0.0, 0.5, 0.06)), longArrayOf(400))
        TimerAlert.RISING -> {
            val f = if (falling) listOf(784.0, 659.0, 523.0) else listOf(523.0, 659.0, 784.0)
            Recipe(listOf(Tone(f[0], 0.0, 0.35), Tone(f[1], 0.25, 0.35), Tone(f[2], 0.5, 0.6)), longArrayOf(60, 60, 60, 60, 140))
        }
        // 맑은 두 음; 진동만 uses its pattern
        else -> Recipe(listOf(Tone(1318.0, 0.0, 0.45), Tone(1046.0, 0.3, 0.6)), longArrayOf(90, 80, 90))
    }

    /** [pattern] (on, off, …, on) [times] times, a round starting every [periodMs]: createWaveform timings (off first). */
    fun waveform(pattern: LongArray, periodMs: Long, times: Int): LongArray {
        val out = mutableListOf(0L)
        repeat(times) { k ->
            out += pattern.toList()
            if (k < times - 1) out += periodMs - pattern.sum()
        }
        return out.toLongArray()
    }

    /** Mono 16-bit PCM of [r] played [times] times, [GAP] apart. */
    fun pcm(r: Recipe, times: Int): ShortArray {
        val period = r.length + GAP
        val n = ((period * (times - 1) + r.length) * RATE).toInt()
        val mix = FloatArray(n)
        repeat(times) { k ->
            for (t in r.tones) {
                val start = ((k * period + t.at) * RATE).toInt()
                for (i in 0 until (t.dur * RATE).toInt()) {
                    if (start + i >= n) break
                    val s = i.toDouble() / RATE
                    val env = if (s < 0.01) 0.0001 * (t.vol / 0.0001).pow(s / 0.01) else t.vol * (0.0001 / t.vol).pow((s - 0.01) / (t.dur - 0.01))
                    mix[start + i] += (env * sin(2 * PI * t.hz * s)).toFloat()
                }
            }
        }
        return ShortArray(n) { (mix[it].coerceIn(-1f, 1f) * 32767).toInt().toShort() }
    }
}

/** The device player: vibration always (bar 끄기); tones only in normal ringer mode outside Do Not Disturb. */
class DeviceTimerAlerts(ctx: Context) : TimerAlerts {
    private val app = ctx.applicationContext
    private var track: AudioTrack? = null

    override fun play(alert: TimerAlert, falling: Boolean, times: Int) {
        if (alert == TimerAlert.OFF) return
        val r = TimerSound.recipe(alert, falling)
        val periodMs = ((r.length + TimerSound.GAP) * 1000).toLong()
        runCatching {
            val v = app.getSystemService(VibratorManager::class.java).defaultVibrator
            if (v.hasVibrator()) v.vibrate(
                VibrationEffect.createWaveform(TimerSound.waveform(r.vibration, periodMs, times), -1),
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        }
        if (alert == TimerAlert.VIBRATE || quiet()) return
        thread(isDaemon = true, name = "timer-alert") { runCatching { playPcm(TimerSound.pcm(r, times)) } }
    }

    private fun quiet(): Boolean {
        val am = app.getSystemService(AudioManager::class.java)
        val f = app.getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return am.ringerMode != AudioManager.RINGER_MODE_NORMAL ||
            (f != NotificationManager.INTERRUPTION_FILTER_ALL && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
    }

    private fun playPcm(pcm: ShortArray) {
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(TimerSound.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
        t.write(pcm, 0, pcm.size)
        // a newer alert (a second preview tap) cuts the one still playing
        synchronized(this) { track?.let { runCatching { it.stop() } }; track = t }
        t.play()
        Thread.sleep(pcm.size * 1000L / TimerSound.RATE + 200)
        synchronized(this) { if (track === t) track = null }
        runCatching { t.stop() }
        t.release()
    }
}
