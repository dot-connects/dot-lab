package kr.dot.wherepark.widget

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.SpeechRecognizer
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId
import kr.dot.wherepark.MainActivity
import kr.dot.wherepark.R
import kr.dot.wherepark.core.Palette
import kr.dot.wherepark.core.Shape
import kr.dot.wherepark.core.Style
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.ui.ListenActivity
import org.json.JSONObject

/** Per-widget settings (spec 8.1): shape, which car, strength, shuffled colours + one undo step. */
object WidgetSpecs {
    private const val KEY = "wp_widgets"
    private const val NEXT = "next"

    private fun styleJson(s: Style) = JSONObject().put("bg", s.bg).put("fg", s.fg).put("mu", s.mu).put("acc", s.acc).put("onAcc", s.onAcc)
        .put("fl1", s.fl1).put("fl2", s.fl2).put("r", s.radiusDp).put("family", s.family).put("material", s.material).put("font", s.font)

    private fun style(j: JSONObject) = Style(j.getInt("bg"), j.getInt("fg"), j.getInt("mu"), j.getInt("acc"), j.getInt("onAcc"), j.getInt("fl1"),
        j.getInt("fl2"), j.getInt("r"), j.getString("family"), j.getString("material"), j.getString("font"))

    fun default(id: Int) = WidgetSpec(id, Shape.ROW, null, 100, Palette.default(Shape.ROW), null)

    private fun spec(id: Int, j: JSONObject?): WidgetSpec? = j?.let {
        runCatching {
            WidgetSpec(id, Shape.valueOf(j.getString("shape")), if (j.isNull("carId")) null else j.getString("carId"), j.getInt("strength"),
                style(j.getJSONObject("style")), j.optJSONObject("prev")?.let(::style))
        }.getOrNull()
    }

    /** Read-only: the widget's saved settings, else the defaults. Only the config screen and [claimNext] write. */
    fun get(ctx: Context, id: Int): WidgetSpec = spec(id, Prefs.obj(ctx, KEY).optJSONObject(id.toString())) ?: default(id)

    /** Settings for the widget the app is about to pin (requestPinAppWidget gives no id up front). */
    fun putNext(ctx: Context, s: WidgetSpec) = put(ctx, s, NEXT)

    /** The pin callback hands the new id: the pending settings move to it, read and written under one lock. */
    fun claimNext(ctx: Context, id: Int): Boolean = synchronized(this) {
        val all = Prefs.obj(ctx, KEY)
        val next = all.optJSONObject(NEXT) ?: return@synchronized false
        all.put(id.toString(), next); all.remove(NEXT)
        Prefs.put(ctx, KEY, all)
        true
    }

    fun put(ctx: Context, s: WidgetSpec, key: String = s.appWidgetId.toString()) = synchronized(this) {
        val all = Prefs.obj(ctx, KEY)
        all.put(key, JSONObject().put("shape", s.shape.name).put("carId", s.carId ?: JSONObject.NULL).put("strength", s.strength)
            .put("style", styleJson(s.style)).put("prev", s.prevStyle?.let(::styleJson) ?: JSONObject.NULL))
        Prefs.put(ctx, KEY, all)
    }

    fun remove(ctx: Context, ids: IntArray) = synchronized(this) {
        val all = Prefs.obj(ctx, KEY); ids.forEach { all.remove(it.toString()) }; Prefs.put(ctx, KEY, all)
    }

    /** The listen sheet borrows numeral face + accent from a widget showing that car (one-car widget first). */
    fun styleFor(ctx: Context, carId: String): Style? {
        val specs = WidgetReceiver.ids(ctx).map { get(ctx, it) }
        return (specs.firstOrNull { it.carId == carId } ?: specs.firstOrNull { it.carId == null })?.style
    }
}

/**
 * The home-screen widget (spec 8): one bitmap per launcher size, transparent button slots over each car's mic and row.
 * Redrawn on every data change (WidgetRefresh), at 00:01 (dates roll over) and after boot.
 */
class WidgetReceiver : AppWidgetProvider() {
    companion object {
        const val ACTION_MIDNIGHT = "kr.dot.wherepark.WIDGET_MIDNIGHT"
        const val ACTION_PINNED = "kr.dot.wherepark.WIDGET_PINNED"
        private const val TOKEN = "wp_widget_token"

        /** Per-install secret on our own PendingIntents: the receiver is exported (launcher updates), our actions are not public. */
        internal fun token(ctx: Context): String = synchronized(this) {
            Prefs.sp(ctx).getString(TOKEN, null) ?: java.util.UUID.randomUUID().toString().also { Prefs.sp(ctx).edit().putString(TOKEN, it).commit() }
        }

        internal fun ours(ctx: Context, i: Intent) = i.getStringExtra(TOKEN) == token(ctx)

        /** Broadcast the system fires after requestPinAppWidget succeeds; it adds EXTRA_APPWIDGET_ID (so the intent is mutable). */
        fun pinnedCallback(ctx: Context): PendingIntent = PendingIntent.getBroadcast(ctx, 2,
            Intent(ctx, WidgetReceiver::class.java).setAction(ACTION_PINNED).putExtra(TOKEN, token(ctx)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        private val MIC_SLOTS = intArrayOf(R.id.m0, R.id.m1, R.id.m2, R.id.m3)
        private val ROW_SLOTS = intArrayOf(R.id.r0, R.id.r1, R.id.r2, R.id.r3)
        private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")

        /** Same value at every size so each size's slot opens the same PendingIntent. */
        fun requestCode(appWidgetId: Int, carIndex: Int, kind: String): Int = appWidgetId * 16 + carIndex * 2 + (if (kind == "MIC") 0 else 1)

        fun ids(ctx: Context): IntArray = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, WidgetReceiver::class.java))

        fun refreshAll(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            ids(ctx).forEach { update(ctx, mgr, it) }
            armMidnight(ctx)
        }

        /** Mic usable from the widget: permission granted, on-device recognizer present, language pack not known missing. */
        fun micReady(ctx: Context): Boolean =
            ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx) && Prefs.sp(ctx).getString("wp_no_pack", null) == null  // ListenActivity.NO_PACK

        /** Wallpaper colours, re-read on every refresh (ACTION_WALLPAPER_CHANGED is not delivered to apps). */
        fun wallpaper(ctx: Context): List<Int> {
            val wc = runCatching { WallpaperManager.getInstance(ctx).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }.getOrNull()
                ?: return listOf(0xFF808080.toInt())
            return listOfNotNull(wc.primaryColor, wc.secondaryColor, wc.tertiaryColor).map { it.toArgb() }
        }

        fun sizes(mgr: AppWidgetManager, id: Int): List<SizeF> {
            val o: Bundle = mgr.getAppWidgetOptions(id)
            o.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)?.takeIf { it.isNotEmpty() }?.let { return it }
            val minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH); val maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
            val maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH); val minH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
            return if (minW > 0 && maxH > 0) listOf(SizeF(minW.toFloat(), maxH.toFloat()), SizeF(maxW.toFloat(), minH.toFloat())).distinct()
            else listOf(SizeF(300f, 140f))
        }

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val spec = WidgetSpecs.get(ctx, id)
            val cars = CarStore.cars(ctx)
            val parks = cars.associate { it.id to ParkStore.latest(ctx, it.id) }
            val dm = ctx.resources.displayMetrics
            val sizes = sizes(mgr, id)
            val scale = WidgetRenderer.budget(sizes, dm.density, (1.5 * dm.widthPixels * dm.heightPixels * 4).toLong())
            val pend = WidgetRenderer.pending(ctx)
            val wall = wallpaper(ctx); val mic = micReady(ctx); val now = System.currentTimeMillis()
            val (shown, _) = WidgetRenderer.carsFor(spec, cars)
            val bitmaps = ArrayList<android.graphics.Bitmap>()
            try {
                val views = sizes.mapIndexed { i, size ->
                    val out = WidgetRenderer.draw(ctx, spec, cars, parks, size, dm.density * scale[i], wall, now, mic, pend)
                    bitmaps += out.rendered.bitmap
                    size to views(ctx, id, out.rendered, shown) { car -> WidgetRenderer.describe(car, parks[car.id], car.id in pend, now) }
                }.toMap()
                mgr.updateAppWidget(id, RemoteViews(views))   // parcels the bitmaps; ours are free after it returns
            } finally {
                bitmaps.forEach { it.recycle() }
            }
        }

        private fun views(ctx: Context, id: Int, r: Rendered, shown: List<kr.dot.wherepark.core.Car>, describe: (kr.dot.wherepark.core.Car) -> String): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.w_root)
            v.setImageViewBitmap(R.id.w_img, r.bitmap)
            (MIC_SLOTS + ROW_SLOTS).forEach { v.setViewVisibility(it, android.view.View.GONE) }
            r.hits.forEach { h ->
                val idx = shown.indexOfFirst { it.id == h.carId }.coerceIn(0, 3)
                val shownCar = shown[idx]
                val slot = if (h.kind == "MIC") MIC_SLOTS[idx] else ROW_SLOTS[idx]
                v.setViewVisibility(slot, android.view.View.VISIBLE)
                v.setViewLayoutMargin(slot, RemoteViews.MARGIN_LEFT, h.rectDp.left, TypedValue.COMPLEX_UNIT_DIP)
                v.setViewLayoutMargin(slot, RemoteViews.MARGIN_TOP, h.rectDp.top, TypedValue.COMPLEX_UNIT_DIP)
                v.setViewLayoutWidth(slot, h.rectDp.width(), TypedValue.COMPLEX_UNIT_DIP)
                v.setViewLayoutHeight(slot, h.rectDp.height(), TypedValue.COMPLEX_UNIT_DIP)
                val car = h.carId.takeIf { it.isNotEmpty() }
                val intent = if (h.kind == "MIC") Intent(ctx, ListenActivity::class.java).putExtra("from", "widget")
                else Intent(ctx, MainActivity::class.java)
                car?.let { intent.putExtra("car_id", it) }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                v.setOnClickPendingIntent(slot, PendingIntent.getActivity(ctx, requestCode(id, idx, h.kind), intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                v.setContentDescription(slot, if (h.kind == "MIC") "${shownCar.name} 위치 말하기" else describe(shownCar))
            }
            return v
        }

        /** Next 00:01 (dates roll over: "어제", then a date); inexact, re-armed by every refresh and after boot. */
        fun armMidnight(ctx: Context) {
            val am = ctx.getSystemService(AlarmManager::class.java) ?: return
            val at = LocalDate.now(ZONE).plusDays(1).atTime(0, 1).atZone(ZONE).toInstant().toEpochMilli()
            val pi = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, WidgetReceiver::class.java).setAction(ACTION_MIDNIGHT).putExtra(TOKEN, token(ctx)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
        }
    }

    /** Drawing every size takes longer than the main thread should: all work runs on a worker, the broadcast kept alive by goAsync. */
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                when (intent.action) {
                    Intent.ACTION_BOOT_COMPLETED -> refreshAll(context)
                    ACTION_MIDNIGHT -> if (ours(context, intent)) refreshAll(context)
                    ACTION_PINNED -> if (ours(context, intent)) {
                        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && WidgetSpecs.claimNext(context, id)) update(context, AppWidgetManager.getInstance(context), id)
                    }
                    else -> super.onReceive(context, intent)   // onUpdate / onAppWidgetOptionsChanged / onDeleted, on this worker
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(context, mgr, it) }
        armMidnight(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, mgr: AppWidgetManager, id: Int, newOptions: Bundle) = update(context, mgr, id)

    override fun onDeleted(context: Context, ids: IntArray) = WidgetSpecs.remove(context, ids)
}
