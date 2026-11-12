package kr.dot.sb.platform

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
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
        thread = Thread({ play(g) }, "sleep-noise").also { it.start() }
        return START_NOT_STICKY
    }

    /** Time left, or -1 once the loop must end (stopped or timer over); ending clears [running] under the same lock. */
    private fun timeLeft(): Long = synchronized(this) {
        val left = endAt - SystemClock.elapsedRealtime()
        if (!running || left <= 0) { running = false; -1L } else left
    }

    private fun play(g: Int) {
        var gen = NoiseGen(sound)
        val buf = ShortArray(2048)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(NoiseGen.RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(buf.size * 2 * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track.play()
        var lastMin = -1
        try {
            while (running) {
                if (gen.sound != sound) gen = NoiseGen(sound)
                val left = timeLeft()
                if (left < 0) break
                val m = if (endAt != Long.MAX_VALUE) ((left + 59_999) / 60_000).toInt() else null
                if ((m ?: -1) != lastMin) { lastMin = m ?: -1; SleepBus.state.value = SleepState(true, m) } // once a minute
                gen.fill(buf, if (left < FADE_MS) left.toFloat() / FADE_MS else 1f)
                track.write(buf, 0, buf.size) // blocks until the buffer drains: this is the only pacing
            }
        } finally {
            track.stop(); track.release()
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
        running = false // the thread notices on its next buffer, releases the track and resets SleepBus
        super.onDestroy()
    }

    companion object {
        private const val SOUND = "sound"
        private const val MINUTES = "minutes"
        private const val ACTION_STOP = "kr.dot.sb.SLEEP_STOP"
        private const val FADE_MS = 10_000L
        /** Playback session across service instances: only the latest loop may report "off" or stop the service. */
        private val session = java.util.concurrent.atomic.AtomicInteger()

        fun start(ctx: Context, sound: SleepSound, minutes: Int) {
            SleepBus.state.value = SleepState(true, minutes.takeIf { it > 0 })
            ctx.startForegroundService(Intent(ctx, SleepService::class.java).putExtra(SOUND, sound.name).putExtra(MINUTES, minutes))
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, SleepService::class.java)) }
    }
}
