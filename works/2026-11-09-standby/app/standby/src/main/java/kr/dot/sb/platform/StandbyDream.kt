package kr.dot.sb.platform

import android.content.Intent
import android.content.res.Configuration
import android.service.dreams.DreamService
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import kr.dot.sb.data.HostCallbacks
import kr.dot.sb.data.StandbyStateHolder
import kr.dot.sb.model.UnfoldAction
import kr.dot.sb.settings.ConfigStore

/** Screen saver while charging: hosts the whole StandBy UI in a ComposeView over the lock screen. */
class StandbyDream : DreamService(), HostCallbacks {
    private val owner = ViewOwner()
    private var holder: StandbyStateHolder? = null
    private var refused = false
    private var dreaming = false

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = true
        isFullscreen = true
        isScreenBright = true
        val h = StandbyStateHolder(this, this, ownRotation = true, automatic = true).also { holder = it }
        val actions = HostActions(this, h, this)
        owner.attachTo(window.decorView)
        // the view reports fold changes itself (the probe confirmed this path reaches a dream's view)
        val box = object : FrameLayout(this) {
            override fun onConfigurationChanged(newConfig: Configuration) {
                super.onConfigurationChanged(newConfig)
                h.onConfiguration(newConfig.smallestScreenWidthDp)
            }
        }
        // no-flash rule (operator 11-18): the clock is drawn only after the charger rule passed; until then the dream
        // is plain black, so a refused dream looks like the screen simply going off
        content = { box.addView(ComposeView(this).apply { setContent { HostContent(h, actions) } }, FrameLayout.LayoutParams(-1, -1)) }
        box.setBackgroundColor(android.graphics.Color.BLACK)
        setContentView(box)
    }

    private var content: (() -> Unit)? = null

    override fun onDreamingStarted() {
        super.onDreamingStarted()
        refused = false
        dreaming = true
        val cfg = ConfigStore.flow(this).value
        ChargeRules.allowed(cfg.startOnCharge, ChargeStart.plugged(this), cfg.chargeWirelessOnly)?.let {
            refuse(it)
            return
        }
        // same charger rule as the plug-in start (무선 충전일 때만 / 가로로 놓였을 때만 켜기); off = the screen just sleeps.
        // Landscape needs ~1.2 s of samples: the dream stays black until then and shows the clock once, or never.
        ChargeStart.checkDream(this, ::refuse) {
            if (refused || !dreaming) return@checkDream // woken or refused during the landscape check
            StandbyPresence.dream = true
            if (Measure.on) Measure.log(this, "dream 시작 · plugged=${ChargeStart.plugged(this)} · ${Measure.grants(this)} · ${Measure.charge(this)}")
            window?.lowRefreshRate()
            content?.invoke(); content = null
            holder?.start()
        }
    }

    private fun refuse(reason: String) {
        ChargeStart.log(this, "화면 보호기 · $reason")
        finishForChargeRule(reason)
    }

    override fun finishForChargeRule(reason: String) {
        refused = true
        if (Measure.on) Measure.log(this, "dream 거부 · $reason · plugged=${ChargeStart.plugged(this)} · ${Measure.charge(this)}")
        finish()
    }

    override fun onDreamingStopped() {
        dreaming = false
        StandbyPresence.dream = false
        if (!getSystemService(android.os.PowerManager::class.java).isInteractive) StandbyPresence.lastHiddenMs = android.os.SystemClock.elapsedRealtime()
        holder?.stop()
        super.onDreamingStopped()
    }

    override fun onDetachedFromWindow() {
        val h = holder
        // unfolded while dreaming and the dream is going away: light the inner screen through a full-screen intent
        if (!refused && h != null && h.state.value.unfolded && h.uiConfig.value.onUnfold == UnfoldAction.MOOD_LIGHT) GlowReceiver.post(this, GlowSource.UNFOLD)
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

    override fun openGlow() = GlowReceiver.post(this, GlowSource.UNFOLD)
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
