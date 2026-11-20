package kr.dot.sb.platform

import android.service.dreams.DreamService
import android.view.View

/**
 * plan-r16 T0: a black stub. The dream is no longer a way into StandBy: a dream cannot tell why the screen is on, and
 * ending it from the lock screen wakes the phone again (the all-night loop). It stays in the manifest only because a
 * missing component hands existing users the default screen saver. It never ends itself and never keeps the panel bright;
 * settings ask the user to turn the screen saver off so a timeout can turn the panel off for real.
 */
class StandbyDream : DreamService() {
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        setContentView(View(this).apply { setBackgroundColor(android.graphics.Color.BLACK) })
        window.attributes = window.attributes.apply { screenBrightness = Brightness.OFF }
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        Measure.log(this, "dream 대기 · 화면 보호기가 켜져 있음")
    }
}

/**
 * Lowest refresh rate of the current resolution: a still clock needs no 120 Hz (plan rule 7).
 */
internal fun android.view.Window.lowRefreshRate() {
    val d = decorView.display ?: return
    val cur = d.mode
    val low = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
        .minByOrNull { it.refreshRate } ?: return
    attributes = attributes.apply { preferredDisplayModeId = low.modeId }
}
