package kr.dot.standby

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Test screensaver: a big clock plus the display it landed on. Answers "does the dream show on the
 * folded cover screen" and "what happens to it when the phone is opened a little".
 */
class StandbyDream : DreamService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var clock: TextView
    private lateinit var info: TextView
    private val hm = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val tick = object : Runnable {
        override fun run() {
            clock.text = LocalTime.now().format(hm)
            val angle = LogService.currentAngle
            info.text = (shownOn()?.let { Probe.display(this@StandbyDream, it) } ?: "display=?") +
                "\nhinge=" + (if (angle.isNaN()) "기록 꺼짐" else "%.1f".format(angle)) +
                "\n" + Probe.battery(this@StandbyDream)
            handler.postDelayed(this, 1000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true
        clock = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 72f
            gravity = Gravity.CENTER
        }
        info = TextView(this).apply {
            setTextColor(Color.rgb(160, 160, 160))
            textSize = 12f
            gravity = Gravity.CENTER
        }
        val root = object : LinearLayout(this) {
            override fun onConfigurationChanged(c: Configuration) {
                super.onConfigurationChanged(c)
                logHere("dream_config", "w=${c.screenWidthDp}dp h=${c.screenHeightDp}dp")
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            addView(clock)
            addView(info)
        }
        setContentView(root)
        logHere("dream_attached", "")
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        logHere("dream_started", "")
        handler.post(tick)
    }

    override fun onDreamingStopped() {
        handler.removeCallbacks(tick)
        logHere("dream_stopped", "")
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(tick)
        logHere("dream_detached", "")
        super.onDetachedFromWindow()
    }

    // a DreamService is not a visual context; the window's view knows its display
    private fun shownOn() = window?.decorView?.display

    private fun logHere(kind: String, extra: String) {
        val ctx: Context = this
        val where = shownOn()?.let { Probe.display(ctx, it) } ?: "display=?"
        EventLog.write(ctx, kind, "$where $extra".trim())
    }
}
