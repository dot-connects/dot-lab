package kr.dot.catchpet.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import kr.dot.catchpet.MainActivity
import kr.dot.catchpet.R
import kr.dot.catchpet.dex.Dex
import kr.dot.catchpet.pet.Frames
import kr.dot.catchpet.pet.Talk
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * The one home widget (operator 11-16, mockup widget-sizes v2): the chosen pet alone, no background, at 1x1, 2x1,
 * 1x2 or 2x2 (the layout by its proportions, [shape]). A tap on the pet floats it or puts it down ([toggle]);
 * from 2x1 up, [도감] and [뽑기] sit beside or under it. Awake with a sky dot while floating, asleep with "z z" when
 * not. Redrawn every 30 min (the pose follows the hour) and whenever the pet or its on/off changes.
 */
object PetWidgets {
    private val work = Executors.newSingleThreadExecutor()

    /** Redraw every placed widget. Any thread. */
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        work.execute {
            // Dex.write calls this on every save: never let a widget problem throw on this thread (review 11-16)
            runCatching {
                val mgr = AppWidgetManager.getInstance(app) ?: return@runCatching
                for (id in mgr.getAppWidgetIds(ComponentName(app, Yard::class.java))) mgr.updateAppWidget(id, views(app, mgr, id))
            }.onFailure { Log.w("PetWidgets", "refresh", it) }
        }
    }

    internal fun update(ctx: Context, ids: IntArray, done: () -> Unit) {
        val app = ctx.applicationContext
        work.execute {
            try { runCatching { val mgr = AppWidgetManager.getInstance(app); for (id in ids) mgr.updateAppWidget(id, views(app, mgr, id)) }.onFailure { Log.w("PetWidgets", "update", it) } }
            finally { done() }
        }
    }

    enum class Shape(val layout: Int, val buttons: Boolean) {
        ONE(R.layout.widget_1x1, false), WIDE(R.layout.widget_2x1, true), TALL(R.layout.widget_1x2, true), BIG(R.layout.widget_2x2, true)
    }

    /**
     * Which layout for a widget [w] x [h] dp. By its proportions, not fixed dp: launcher cells run from ~70 to
     * ~120 dp, and the size-map picked 1x2 for a 1x1 and 2x2 for a 2x1 on the operator's Fold (11-16).
     */
    internal fun shape(w: Float, h: Float): Shape = when {
        w >= h * 1.5f -> Shape.WIDE
        h >= w * 1.5f -> Shape.TALL
        minOf(w, h) >= 150f -> Shape.BIG
        else -> Shape.ONE
    }

    private fun views(ctx: Context, mgr: AppWidgetManager, id: Int): RemoteViews {
        val o = mgr.getAppWidgetOptions(id)
        // portrait: min width x max height, landscape: max width x min height; nothing yet = 2x1
        val land = ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val w = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 }?.toFloat() ?: 110f
        val h = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }?.toFloat() ?: 60f
        val sh = shape(w, h)
        // the service's own state, not the saved switch: right after adding the widget it shows what is on screen
        val on = kr.dot.catchpet.pet.PetService.alive
        return RemoteViews(ctx.packageName, sh.layout).apply {
            setImageViewBitmap(R.id.art, render(ctx, on))
            // a broadcast, not an activity: no window flashes and the pet starts at once (operator 11-16)
            val toggle = PendingIntent.getBroadcast(ctx, 1, Intent(ctx, Yard::class.java).setAction(Yard.TOGGLE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            setOnClickPendingIntent(R.id.root, toggle)
            setOnClickPendingIntent(R.id.art, toggle)
            if (sh.buttons) {
                setOnClickPendingIntent(R.id.btn_dex, open(ctx, "dex", 2))
                setOnClickPendingIntent(R.id.btn_catch, open(ctx, "catch", 3))
            }
        }
    }

    /** A tap on the pet: put it down, or float it (a widget tap may start the foreground service). */
    internal fun toggle(ctx: Context) {
        val app = ctx.applicationContext
        when {
            kr.dot.catchpet.pet.PetService.alive -> kr.dot.catchpet.pet.PetService.stop(app)
            app.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getString("pet", null) == null ->
                android.widget.Toast.makeText(app, "앱의 펫 화면에서 띄울 녀석을 먼저 골라 주세요", android.widget.Toast.LENGTH_LONG).show()
            !android.provider.Settings.canDrawOverlays(app) ->
                android.widget.Toast.makeText(app, "앱의 펫 화면에서 '다른 앱 위에 표시'를 먼저 허용해 주세요", android.widget.Toast.LENGTH_LONG).show()
            else -> runCatching { kr.dot.catchpet.pet.PetService.start(app) }.onFailure { Log.w("PetWidgets", "start", it) }
        }
    }

    private fun open(ctx: Context, to: String, code: Int): PendingIntent =
        PendingIntent.getActivity(ctx, code, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.OPEN, to)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private const val PX = 256   // one square picture for every size; the layouts fit it in

    /** The pet, transparent around it: awake + a sky dot while floating, asleep + "z z" when not. */
    internal fun render(ctx: Context, on: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val ink = 0xFF24331F.toInt()
        val cell = if (!on) "sleep" else if (Talk.slot(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) == Talk.Slot.NOON) "smile" else "front"
        val e = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getString("pet", null)?.let { Dex.get(ctx, it) }
        val pic = e?.let { Frames.load(ctx, it) }?.let { it[cell] ?: it["front"] }
        if (pic == null) {
            // nothing chosen yet: a paper ball with "?" (opaque, so it reads on any wallpaper)
            val r = PX * 0.34f
            p.color = 0xFFFFFDF6.toInt(); c.drawCircle(PX / 2f, PX / 2f, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.08f; p.color = ink; c.drawCircle(PX / 2f, PX / 2f, r, p)
            p.style = Paint.Style.FILL
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = ink; textAlign = Paint.Align.CENTER; textSize = r }
            c.drawText("?", PX / 2f, PX / 2f + r * 0.36f, t)
            return bmp
        }
        val size = PX * 0.86f
        val s = size / max(pic.width, pic.height)
        val bw = pic.width * s; val bh = pic.height * s
        val foot = PX * 0.95f
        p.color = 0x40000000; c.drawOval(PX / 2f - bw * 0.36f, foot - size * 0.05f, PX / 2f + bw * 0.36f, foot + size * 0.04f, p)
        p.color = Color.BLACK   // opaque again: the shadow left the paint at 25 % alpha (operator 11-16 "too see-through")
        c.drawBitmap(pic, null, RectF(PX / 2f - bw / 2, foot - bh, PX / 2f + bw / 2, foot), p)
        if (on) {
            p.color = 0xFF9FD8FF.toInt(); c.drawCircle(PX * 0.86f, PX * 0.12f, PX * 0.06f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = PX * 0.016f; p.color = ink; c.drawCircle(PX * 0.86f, PX * 0.12f, PX * 0.06f, p)
            p.style = Paint.Style.FILL
        } else {
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT; textSize = PX * 0.13f }
            t.style = Paint.Style.STROKE; t.strokeWidth = PX * 0.025f; t.color = ink; c.drawText("z z", PX * 0.97f, PX * 0.16f, t)
            t.style = Paint.Style.FILL; t.color = Color.WHITE; c.drawText("z z", PX * 0.97f, PX * 0.16f, t)
        }
        return bmp
    }
}

/** The one widget's receiver (the class name stays: widgets placed before keep working). */
class Yard : AppWidgetProvider() {
    override fun onReceive(ctx: Context, i: Intent) {
        if (i.action == TOGGLE) PetWidgets.toggle(ctx) else super.onReceive(ctx, i)
    }

    companion object { const val TOGGLE = "kr.dot.catchpet.WIDGET_TOGGLE" }

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val r = goAsync()
        PetWidgets.update(ctx, ids) { r.finish() }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) {
        val r = goAsync()
        PetWidgets.update(ctx, intArrayOf(id)) { r.finish() }
    }
}
