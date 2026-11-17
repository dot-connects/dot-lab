package kr.dot.sb.platform

import android.text.format.DateFormat
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.window.area.WindowAreaCapability
import androidx.window.area.WindowAreaController
import androidx.window.area.WindowAreaInfo
import androidx.window.area.WindowAreaPresentationSessionCallback
import androidx.window.area.WindowAreaSessionPresenter
import androidx.window.core.ExperimentalWindowApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kr.dot.sb.settings.ConfigStore
import kr.dot.sb.ui.screens.TentClock
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.switchFromPanel
import java.time.LocalDateTime

/**
 * plan-r15 E7 텐트: while the mood light is on the inner screen, the clock on the outer (rear) screen through Jetpack
 * WindowManager dual-screen ([WindowAreaController.presentContentOnWindowArea]). Measured on the operator's Fold:
 * PRESENT_ON_AREA is AVAILABLE whenever the app is on the inner display. Not available (bar phone, cover display, an OS
 * without it) = nothing happens. The session ends with [stop] (the mood light ends) or by the system (the user closes
 * it, the phone folds): then it stays off until the next [start].
 */
@OptIn(ExperimentalWindowApi::class)
class Tent(private val activity: ComponentActivity) {
    private var job: Job? = null
    private var session: WindowAreaSessionPresenter? = null
    private var view: ComposeView? = null

    fun start() {
        if (job != null || session != null) return
        job = activity.lifecycleScope.launch {
            val controller = runCatching { WindowAreaController.getOrCreate() }.getOrNull() ?: return@launch
            val rear = runCatching {
                controller.windowAreaInfos.first { infos -> infos.any(::available) }.first(::available)
            }.getOrNull() ?: return@launch
            ChargeStart.log(activity, "텐트 · 바깥 화면 시계 요청")
            controller.presentContentOnWindowArea(rear.token, activity, activity.mainExecutor, callback)
        }
    }

    fun stop() {
        job?.cancel(); job = null
        runCatching { session?.close() }
        session = null; view = null
    }

    private fun available(i: WindowAreaInfo) = i.type == WindowAreaInfo.Type.TYPE_REAR_FACING &&
        i.getCapability(WindowAreaCapability.Operation.OPERATION_PRESENT_ON_AREA)?.status == WindowAreaCapability.Status.WINDOW_AREA_STATUS_AVAILABLE

    private val callback = object : WindowAreaPresentationSessionCallback {
        override fun onSessionStarted(session: WindowAreaSessionPresenter) {
            if (job == null) { runCatching { session.close() }; return } // stopped while the system asked
            this@Tent.session = session
            val v = ComposeView(session.context).apply {
                setViewTreeLifecycleOwner(activity)
                setViewTreeSavedStateRegistryOwner(activity)
                setViewTreeViewModelStoreOwner(activity)
                setContent { Content() }
            }
            view = v
            session.setContentView(v)
            ChargeStart.log(activity, "텐트 · 바깥 화면 시계 켜짐")
        }

        override fun onSessionEnded(t: Throwable?) {
            ChargeStart.log(activity, "텐트 · 바깥 화면 시계 꺼짐" + (t?.let { " · ${it.javaClass.simpleName}" } ?: ""))
            session = null; view = null
        }

        override fun onContainerVisibilityChanged(isVisible: Boolean) {}
    }

    /** The outer screen's own brightness = the night cap (its clock is in night colours), applied to its window. */
    private fun setBrightness(p: Float) {
        val v = Brightness.fromBar(p)
        ConfigStore.update(activity) { Brightness.store(it, true, v) }
        val root = view?.rootView ?: return
        val lp = root.layoutParams as? WindowManager.LayoutParams ?: return
        lp.screenBrightness = v
        runCatching { root.context.getSystemService(WindowManager::class.java).updateViewLayout(root, lp) }
    }

    @androidx.compose.runtime.Composable
    private fun Content() {
        val config by ConfigStore.flow(activity).collectAsState()
        val now by produceState(LocalDateTime.now().withSecond(0).withNano(0)) {
            while (true) { delay(60_000L - System.currentTimeMillis() % 60_000L); value = LocalDateTime.now().withSecond(0).withNano(0) }
        }
        val state = StandbyState(now = now, use24h = config.use24h ?: DateFormat.is24HourFormat(activity), night = 1f)
        androidx.compose.runtime.LaunchedEffect(Unit) { setBrightness(Brightness.toBar(config.nightMaxBrightness)) }
        TentClock(
            state, config, Brightness.toBar(config.nightMaxBrightness), ::setBrightness,
            onLook = { look -> ConfigStore.update(activity) { it.switchFromPanel(look) } },
            onMood = { m -> ConfigStore.update(activity) { it.copy(mood = m) } },
        )
    }
}
