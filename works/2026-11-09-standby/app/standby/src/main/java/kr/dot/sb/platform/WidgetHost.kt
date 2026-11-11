package kr.dot.sb.platform

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.widget.RemoteViews
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

    @Synchronized
    fun get(ctx: Context): AppWidgetHost = host ?: object : AppWidgetHost(ctx.applicationContext, HOST_ID) {
        override fun onCreateView(context: Context, id: Int, info: AppWidgetProviderInfo?): AppWidgetHostView = WidgetView(context)
    }.also { host = it }

    @Synchronized
    fun start(ctx: Context) { if (refs++ == 0) get(ctx).startListening() }

    @Synchronized
    fun stop() { if (refs > 0 && --refs == 0) host?.stopListening() }

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
