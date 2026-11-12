package kr.dot.sb.platform

import android.content.Intent
import android.content.res.Configuration
import android.service.dreams.DreamService
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import kr.dot.sb.data.HostCallbacks
import kr.dot.sb.data.StandbyStateHolder
import kr.dot.sb.model.UnfoldAction

/** Screen saver while charging: hosts the whole StandBy UI in a ComposeView over the lock screen. */
class StandbyDream : DreamService(), HostCallbacks {
    private val owner = ViewOwner()
    private var holder: StandbyStateHolder? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        val h = StandbyStateHolder(this, this, ownRotation = true).also { holder = it }
        val actions = HostActions(this, h, this)
        owner.attachTo(window.decorView)
        // the view reports fold changes itself (the probe confirmed this path reaches a dream's view)
        val box = object : FrameLayout(this) {
            override fun onConfigurationChanged(newConfig: Configuration) {
                super.onConfigurationChanged(newConfig)
                h.onConfiguration(newConfig.smallestScreenWidthDp)
            }
        }
        box.addView(ComposeView(this).apply { setContent { HostContent(h, actions) } }, FrameLayout.LayoutParams(-1, -1))
        setContentView(box)
    }

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        StandbyPresence.dream = true
        window?.lowRefreshRate()
        holder?.start()
        WidgetHost.start(this, ::unlockThen)
    }

    override fun onDreamingStopped() {
        StandbyPresence.dream = false
        StandbyPresence.lastHiddenMs = android.os.SystemClock.elapsedRealtime()
        holder?.stop()
        WidgetHost.stop(::unlockThen)
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        val h = holder
        // unfolded while dreaming and the dream is going away: light the inner screen through a full-screen intent
        if (h != null && h.state.value.unfolded && h.uiConfig.value.onUnfold == UnfoldAction.MOOD_LIGHT) GlowReceiver.post(this)
        h?.stop()
        holder = null
        owner.destroy()
        super.onDetachedFromWindow()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        holder?.onConfiguration(newConfig.smallestScreenWidthDp)
    }

    // ---- HostCallbacks ----
    override fun setBrightness(b: Float) {
        val w = window ?: return
        w.attributes = w.attributes.apply { screenBrightness = b }
    }

    override fun unlockThen(block: () -> Unit) {
        PendingUnlock.block = block
        startActivity(Intent(this, StandbyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    override fun openGlow() = GlowReceiver.post(this)
}

/**
 * Lowest refresh rate of the current resolution: a still clock needs no 120 Hz (plan rule 7). Both hosts, since the
 * activity is the one that stays on all night on a charger.
 */
internal fun android.view.Window.lowRefreshRate() {
    val d = decorView.display ?: return
    val cur = d.mode
    val low = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
        .minByOrNull { it.refreshRate } ?: return
    attributes = attributes.apply { preferredDisplayModeId = low.modeId }
}
