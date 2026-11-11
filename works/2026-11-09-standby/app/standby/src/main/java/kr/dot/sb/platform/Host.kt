package kr.dot.sb.platform

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kr.dot.sb.data.HostCallbacks
import kr.dot.sb.data.StandbyStateHolder
import kr.dot.sb.settings.SettingsActivity
import kr.dot.sb.ui.screens.StandbyRoot
import kr.dot.sb.ui.state.StandbyActions

/** A DreamService is not a LifecycleOwner: this gives its ComposeView the three owners Compose looks for. */
class ViewOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()

    init {
        saved.performAttach()
        saved.performRestore(null)
    }

    /** Owners go on the window's decor view so every view below finds them. */
    fun attachTo(root: View) {
        root.setViewTreeLifecycleOwner(this)
        root.setViewTreeSavedStateRegistryOwner(this)
        root.setViewTreeViewModelStoreOwner(this)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/** Set by a dream that must hand over to [StandbyActivity] before an action that needs the unlocked phone. */
object PendingUnlock {
    @Volatile var block: (() -> Unit)? = null
}

/** The one [StandbyActions] implementation, used by dream and activity. */
class HostActions(
    private val ctx: Context,
    private val holder: StandbyStateHolder,
    private val host: HostCallbacks,
) : StandbyActions {
    override fun mediaPlayPause() = holder.mediaPlayPause()
    override fun mediaNext() = holder.mediaNext()
    override fun mediaPrev() = holder.mediaPrev()
    override fun openMediaApp() {
        val pkg = holder.mediaPackage ?: return
        host.unlockThen { ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }
    override fun openPhoto(uri: String) {
        host.unlockThen {
            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(android.net.Uri.parse(uri), "image/*").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
        }
    }
    override fun photoTheme(bucket: String?) = holder.photoTheme(bucket)
    override fun openVolume() {
        ctx.getSystemService(android.media.AudioManager::class.java).adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, android.media.AudioManager.ADJUST_SAME, android.media.AudioManager.FLAG_SHOW_UI)
    }
    override fun timerToggle(id: String) = holder.timerToggle(id)
    override fun timerReset(id: String) = holder.timerReset(id)
    override fun sleepToggle() = holder.sleepToggle()
    override fun alarmSnooze() = ListenerBus.snooze(ctx)
    override fun alarmStop() = ListenerBus.stop(ctx)
    override fun notificationTap(key: String) {
        val pi = holder.notificationIntentOf(key)
        val pkg = holder.notificationPackageOf(key)
        holder.dismissNotification()
        if (pi == null && pkg == null) return
        // like the system shade: unlock first, then the app's own tap intent; an app without one just opens
        host.unlockThen {
            if (pi != null && ListenerBus.open(ctx, key, pi)) return@unlockThen
            pkg?.let { ctx.packageManager.getLaunchIntentForPackage(it) }?.let { runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }
    override fun notificationDismiss(key: String) = holder.dismissNotification()
    override fun notificationClear(keys: List<String>) = holder.clearNotifications(keys)
    override fun liveToggle() = holder.liveToggle()
    override fun retryFeeds() = holder.retryFeeds()
    override fun openSettings(section: String?) = host.unlockThen {
        ctx.startActivity(Intent(ctx, SettingsActivity::class.java).putExtra("section", section).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    override fun userActivity() = holder.touch()
    override fun pageChanged(page: Int) = holder.onPage(page)
    override fun setBrightness(value: Float) = holder.setUserBrightness(value)
    override fun exit() = host.finish()
}

/**
 * Own rotation (dreams cannot request an orientation, spec E4): the child gets width and height swapped for 90/270
 * and is turned about its centre; Compose maps touches through the layer, so hit testing follows.
 */
@Composable
fun RotatedBox(degrees: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, c ->
        val swap = degrees % 180 != 0
        val cc = if (swap) Constraints.fixed(c.maxHeight, c.maxWidth) else Constraints.fixed(c.maxWidth, c.maxHeight)
        val placeables = measurables.map { it.measure(cc) }
        layout(c.maxWidth, c.maxHeight) {
            placeables.forEach { it.placeWithLayer((c.maxWidth - it.width) / 2, (c.maxHeight - it.height) / 2) { rotationZ = degrees.toFloat() } }
        }
    }
}

@Composable
fun HostContent(holder: StandbyStateHolder, actions: StandbyActions) {
    val state by holder.state.collectAsState()
    val config by holder.uiConfig.collectAsState()
    val rotation by holder.rotation.collectAsState()
    RotatedBox(rotation, Modifier.fillMaxSize().background(Color.Black)) { // literal-ok: display off = pure black
        val ctx = androidx.compose.ui.platform.LocalContext.current
        // colour edits made in the clocks page edit mode persist like settings edits
        StandbyRoot(state, config, actions, Modifier.fillMaxSize()) { face, style ->
            kr.dot.sb.settings.ConfigStore.update(ctx) { it.copy(styles = it.styles + (face to style)) }
        }
    }
}
