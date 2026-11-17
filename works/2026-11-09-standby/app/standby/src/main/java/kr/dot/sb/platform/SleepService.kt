package kr.dot.sb.platform

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.IBinder
import android.os.SystemClock
import kr.dot.sb.data.NoiseGen
import kr.dot.sb.data.SleepBus
import kr.dot.sb.model.SleepSound
import kr.dot.sb.ui.state.SleepState

/**
 * Foreground media-playback service, alive only while the sleep sound plays (the one allowed foreground service).
 * Noise is generated on the fly; stops by itself after the timer with a 10 s fade-out.
 */
class SleepService : Service() {
    @Volatile private var running = false
    @Volatile private var sound = SleepSound.RAIN
    @Volatile private var endAt = Long.MAX_VALUE
    private var thread: Thread? = null

    // plan-r15 C5: audio focus. Lost (or not given yet) = paused, a transient duck = quieter; the loop applies both.
    @Volatile private var paused = false
    @Volatile private var volume = 1f
    private var focus: AudioFocusRequest? = null
    private var noisyRx: BroadcastReceiver? = null

    private fun requestFocus() {
        if (focus != null) return
        val f = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(ATTRS)
            .setAcceptsDelayedFocusGain(true)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener { c ->
                when (c) {
                    AudioManager.AUDIOFOCUS_GAIN -> { paused = false; volume = 1f }
                    AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> paused = true
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> volume = DUCK
                }
            }
            .build()
        focus = f
        paused = getSystemService(AudioManager::class.java).requestAudioFocus(f) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        // headphones out: stop rather than go on from the speaker
        noisyRx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) { running = false; stopSelf() }
        }.also { registerReceiver(it, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), RECEIVER_NOT_EXPORTED) }
    }

    private fun releaseFocus() {
        focus?.let { f -> runCatching { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(f) } }
        focus = null
        noisyRx?.let { runCatching { unregisterReceiver(it) } }
        noisyRx = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { running = false; stopSelf(); return START_NOT_STICKY }
        val sound = runCatching { SleepSound.valueOf(intent?.getStringExtra(SOUND).orEmpty()) }.getOrDefault(SleepSound.RAIN)
        val minutes = intent?.getIntExtra(MINUTES, 45) ?: 45
        Channels.ensure(this)
        val stop = PendingIntent.getService(
            this, 0, Intent(this, SleepService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, Channels.SLEEP)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("수면 소리 · ${sound.ko}")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "끄기", stop).build())
            .build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        // a start while playing (the sleep card picked another sound or timer) swaps them in the running loop;
        // under the lock so a loop that just decided to end cannot swallow this start
        val g = synchronized(this) {
            this.sound = sound
            endAt = if (minutes > 0) SystemClock.elapsedRealtime() + minutes * 60_000L else Long.MAX_VALUE
            if (running) return START_NOT_STICKY
            running = true
            session.incrementAndGet()
        }
        requestFocus()
        thread = Thread({ play(g) }, "sleep-noise").also { it.start() }
        return START_NOT_STICKY
    }

    /** Time left, or -1 once the loop must end (stopped or timer over); ending clears [running] under the same lock. */
    private fun timeLeft(): Long = synchronized(this) {
        val left = endAt - SystemClock.elapsedRealtime()
        if (!running || left <= 0) { running = false; -1L } else left
    }

    /** A started track, or null when building or starting it failed (a half-built one is released). */
    private fun openTrack(): AudioTrack? {
        var t: AudioTrack? = null
        return try {
            val min = AudioTrack.getMinBufferSize(NoiseGen.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            t = AudioTrack.Builder()
                .setAudioAttributes(ATTRS)
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(NoiseGen.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(NoiseGen.bufferBytes(min))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            t.play()
            t
        } catch (e: Exception) {
            android.util.Log.w(TAG, "audio track failed to start", e)
            runCatching { t?.release() }
            null
        }
    }

    private fun play(g: Int) {
        var gen = NoiseGen(sound)
        val buf = ShortArray(NoiseGen.CHUNK)
        var track: AudioTrack? = null
        var fails = 0 // consecutive failures (start or write); a good chunk resets it
        var lastMin = -1
        val chunkMs = NoiseGen.CHUNK * 1000L / NoiseGen.RATE
        try {
            while (running) {
                // a dead or failed track is rebuilt with backoff; after the last try the service stops cleanly
                fun failed(): Boolean {
                    track?.let { t -> runCatching { t.release() } }
                    track = null
                    fails++
                    if (!TrackWrite.mayRetry(fails)) { android.util.Log.w(TAG, "audio track gave up after $fails tries"); return false }
                    Thread.sleep(TrackWrite.backoffMs(fails))
                    return true
                }
                val t = track ?: openTrack()?.also { track = it } ?: if (failed()) continue else break
                if (paused) {
                    // focus lost: hold the track (the timer keeps counting) until it comes back
                    if (t.playState == AudioTrack.PLAYSTATE_PLAYING) runCatching { t.pause() }
                    if (timeLeft() < 0) break
                    Thread.sleep(250)
                    continue
                }
                if (t.playState != AudioTrack.PLAYSTATE_PLAYING) try { t.play() } catch (e: IllegalStateException) { if (failed()) continue else break }
                runCatching { t.setVolume(volume) }
                if (gen.sound != sound) {
                    // a switch: drop what is buffered of the old sound so the two never overlap
                    gen = NoiseGen(sound)
                    // a dead track throws here instead of returning an error: same rebuild path
                    try { t.pause(); t.flush(); t.play() } catch (e: IllegalStateException) { if (failed()) continue else break }
                }
                val left = timeLeft()
                if (left < 0) break
                val m = if (endAt != Long.MAX_VALUE) ((left + 59_999) / 60_000).toInt() else null
                if ((m ?: -1) != lastMin) { lastMin = m ?: -1; SleepBus.state.value = SleepState(true, m) } // once a minute
                fun gain(ms: Long) = if (ms < FADE_MS) ms.coerceAtLeast(0).toFloat() / FADE_MS else 1f
                gen.fill(buf, gain(left), gain(left - chunkMs)) // per-sample ramp across the chunk
                // blocks until there is room: this is the only pacing, so a write that fails must never loop on
                val wrote = try { TrackWrite.writeAll(buf) { b, off, n -> t.write(b, off, n) } } catch (e: IllegalStateException) { false }
                if (wrote) fails = 0
                else if (failed()) continue else break
            }
        } catch (e: InterruptedException) {
            // only a backoff sleep can be interrupted; end like a stop
        } finally {
            // only this thread touches the track: pause + flush first, so the sound stops now rather than after the
            // 500 ms buffer plays out (a stop is noticed within one 250 ms chunk)
            track?.let { t -> runCatching { t.pause(); t.flush() }; runCatching { t.release() } }
            // a newer start (this service, or a new one after a quick stop+start) owns the card and the service now
            synchronized(this) {
                if (session.get() == g) {
                    running = false
                    SleepBus.state.value = SleepState(false, null)
                    stopSelf()
                }
            }
        }
    }

    override fun onDestroy() {
        running = false // the thread notices on its next chunk, pauses + flushes + releases the track and resets SleepBus
        releaseFocus()
        super.onDestroy()
    }

    companion object {
        private const val SOUND = "sound"
        private const val MINUTES = "minutes"
        private const val ACTION_STOP = "kr.dot.sb.SLEEP_STOP"
        private const val FADE_MS = 10_000L
        private const val DUCK = 0.2f
        private val ATTRS: AudioAttributes =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        private const val TAG = "SleepService"
        /** Playback session across service instances: only the latest loop may report "off" or stop the service. */
        private val session = java.util.concurrent.atomic.AtomicInteger()

        fun start(ctx: Context, sound: SleepSound, minutes: Int) {
            SleepBus.state.value = SleepState(true, minutes.takeIf { it > 0 })
            ctx.startForegroundService(Intent(ctx, SleepService::class.java).putExtra(SOUND, sound.name).putExtra(MINUTES, minutes))
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, SleepService::class.java)) }
    }
}

/** The sleep loop's AudioTrack writes and retry policy, kept pure so they can be tested without a device. */
internal object TrackWrite {
    /**
     * Writes all of [buf] through [write] (AudioTrack.write: count written, or a negative error such as
     * ERROR_DEAD_OBJECT). Partial writes continue where they stopped; an error or a zero write returns false, so the
     * caller never spins without the blocking write's pacing.
     */
    inline fun writeAll(buf: ShortArray, write: (ShortArray, Int, Int) -> Int): Boolean {
        var off = 0
        while (off < buf.size) {
            val n = write(buf, off, buf.size - off)
            if (n <= 0) return false
            off += n
        }
        return true
    }

    /** Up to three rebuilds after a failure, then the service stops. */
    fun mayRetry(fails: Int): Boolean = fails <= 3

    /** 250, 500, 1000 ms. */
    fun backoffMs(fails: Int): Long = 250L shl (fails - 1).coerceIn(0, 4)
}
