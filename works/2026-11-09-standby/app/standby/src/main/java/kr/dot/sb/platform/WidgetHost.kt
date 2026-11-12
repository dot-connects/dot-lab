package kr.dot.sb.platform

import android.app.KeyguardManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.RemoteViews
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * The one AppWidgetHost of the process (r9 D6). Activity and dream share it: [start]/[stop] are ref-counted, and each
 * widget id has at most one view ([view]) — a second `createView` for an id would take the first one's updates.
 */
object WidgetHost {
    private const val HOST_ID = 0x5B0A
    private var host: AppWidgetHost? = null
    private var refs = 0
    private val views = HashMap<Int, WidgetView>()

    /**
     * The current screen host's `unlockThen`. StandBy shows over the keyguard, so a widget tap must not fire the
     * provider's PendingIntent before the user unlocks; every tap goes through this.
     */
    @Volatile var unlock: (() -> Unit) -> Unit = {}

    /**
     * Bumped on every [start]. A widget view lives in one parent at a time, so when the dream took it the activity's
     * slot is empty; the composition keys on this and takes the view back when its host starts again.
     */
    val epoch = mutableIntStateOf(0)
    private var swept = false

    @Synchronized
    fun get(ctx: Context): AppWidgetHost = host ?: object : AppWidgetHost(ctx.applicationContext, HOST_ID) {
        override fun onCreateView(context: Context, id: Int, info: AppWidgetProviderInfo?): AppWidgetHostView = WidgetView(context)
    }.also { host = it }

    @Synchronized
    fun start(ctx: Context, unlockThen: (() -> Unit) -> Unit) {
        unlock = unlockThen
        epoch.intValue++
        if (refs++ == 0) get(ctx).startListening()
    }

    /** [unlockThen] = the one passed to [start]; dropping it lets a finished activity or dream be collected. */
    @Synchronized
    fun stop(unlockThen: (() -> Unit) -> Unit) {
        if (unlock == unlockThen) unlock = {}
        if (refs > 0 && --refs == 0) host?.stopListening()
    }

    /**
     * Once per process: release bound ids no board references (a stack shortened on load, a pick that landed after its
     * card was removed). ponytail: an id whose provider config screen is open in this very process start is lost too;
     * rare, the user picks again.
     */
    @Synchronized
    fun sweep(ctx: Context, used: Set<Int>) {
        if (swept) return
        swept = true
        get(ctx).appWidgetIds.filter { it !in used }.forEach { delete(ctx, it) }
    }

    /** The view for [id], inflated with the night/day uiMode [dark] (best effort: the provider decides). Null = not bound. */
    @Synchronized
    fun view(ctx: Context, id: Int, dark: Boolean): WidgetView? {
        views[id]?.takeIf { it.dark == dark }?.let { return it }
        val info = AppWidgetManager.getInstance(ctx).getAppWidgetInfo(id) ?: return null
        val conf = Configuration(ctx.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        val v = get(ctx).createView(ctx.applicationContext.createConfigurationContext(conf), id, info) as WidgetView
        v.dark = dark
        v.setOnLightBackground(!dark)
        views[id] = v
        return v
    }

    @Synchronized
    fun delete(ctx: Context, id: Int) {
        views.remove(id)
        get(ctx).deleteAppWidgetId(id)
    }
}

/** A hosted widget that knows whether it is drawn light (D13: mean luminance of a 16×16 downscale, after each update). */
class WidgetView(ctx: Context) : AppWidgetHostView(ctx) {
    var dark = false
    val light = mutableStateOf(false)

    /** Over the keyguard a tap only asks for unlock: the provider's PendingIntent must not fire while locked. */
    private fun locked() = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = locked() || super.onInterceptTouchEvent(ev)

    // ponytail: accessibility-service clicks on descendants are not gated (such a service already controls the device)
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!locked()) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_UP && (event.keyCode == KeyEvent.KEYCODE_ENTER || event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER)) {
            WidgetHost.unlock {}
        }
        return true
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (!locked()) return super.onTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP && ev.eventTime - ev.downTime < ViewConfiguration.getLongPressTimeout()) {
            WidgetHost.unlock {}
        }
        return true
    }

    override fun updateAppWidget(remoteViews: RemoteViews?) {
        super.updateAppWidget(remoteViews)
        post { sample() }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        post { sample() }
    }

    private fun sample() {
        if (width == 0 || height == 0) return
        val b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        // a widget holding hardware bitmaps cannot draw to a software canvas: keep the last answer then
        runCatching { Canvas(b).apply { scale(16f / width, 16f / height) }.let { draw(it) } }.onFailure { return }
        var sum = 0.0
        for (p in IntArray(256).also { b.getPixels(it, 0, 16, 0, 0, 16, 16) }) {
            val a = (p ushr 24) / 255.0 // transparent = the dark page behind it
            sum += a * (0.2126 * (p shr 16 and 255) + 0.7152 * (p shr 8 and 255) + 0.0722 * (p and 255)) / 255.0
        }
        b.recycle()
        light.value = sum / 256 > 0.5
    }
}
