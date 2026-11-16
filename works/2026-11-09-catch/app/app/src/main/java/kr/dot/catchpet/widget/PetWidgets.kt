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
import kr.dot.catchpet.R
import kr.dot.catchpet.dex.Dex
import kr.dot.catchpet.pet.Frames
import kr.dot.catchpet.pet.PetPrefs
import kr.dot.catchpet.pet.Talk
import java.util.Calendar
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The one home widget (operator 11-16, mockup widget-family A): the widget family standing together, no background
 * and no buttons, at any size. A tap opens [WidgetMenu]. 1x1 = the floating pet alone; a low widget = one row of
 * up to three; otherwise two rows of up to six. Still pictures: redrawn every 30 min (the pose follows the hour)
 * and on any dex or family change.
 */
object PetWidgets {
    private val work = Executors.newSingleThreadExecutor()

    /** Redraw every placed widget (a catch, a deleted entry, another pet, a family change). Any thread. */
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        work.execute {
            // Dex.write calls this on every save: never let a widget problem throw on this thread (review 11-16)
            runCatching {
                val mgr = AppWidgetManager.getInstance(app) ?: return@runCatching
                for (id in mgr.getAppWidgetIds(ComponentName(app, Yard::class.java))) draw(app, mgr, id)
            }.onFailure { Log.w("PetWidgets", "refresh", it) }
        }
    }

    internal fun update(ctx: Context, ids: IntArray, done: () -> Unit) {
        val app = ctx.applicationContext
        work.execute {
            try { runCatching { val mgr = AppWidgetManager.getInstance(app); for (id in ids) draw(app, mgr, id) } } finally { done() }
        }
    }

    private fun draw(ctx: Context, mgr: AppWidgetManager, id: Int) {
        runCatching {
            val o = mgr.getAppWidgetOptions(id)
            // portrait: min width x max height, landscape: max width x min height; nothing yet = 2x2
            val land = ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val wDp = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 }?.toFloat() ?: 110f
            val hDp = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }?.toFloat() ?: 110f
            val d = ctx.resources.displayMetrics.density
            val k = min(d, 600f / max(wDp, hDp))   // keeps the bitmap small enough for RemoteViews
            val v = RemoteViews(ctx.packageName, R.layout.widget_image)
            v.setImageViewBitmap(R.id.art, render(ctx, wDp, hDp, k))
            v.setOnClickPendingIntent(R.id.art, PendingIntent.getActivity(ctx, 1,
                Intent(ctx, WidgetMenu::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
            mgr.updateAppWidget(id, v)
        }.onFailure { Log.w("PetWidgets", "draw $id", it) }
    }

    /** Who stands on a widget: 1x1 = the floating pet; else the family (the floating pet first), or the pet alone. */
    internal fun cast(ctx: Context, max: Int): List<Dex.Entry> {
        val pet = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getString("pet", null)?.let { Dex.get(ctx, it) }
        val fam = Family.entries(ctx)
        if (max == 1 || fam.isEmpty()) return listOfNotNull(pet ?: fam.firstOrNull())
        val first = fam.firstOrNull { it.id == pet?.id && PetPrefs.overlayOn(ctx) }
        return (listOfNotNull(first) + fam.filter { it !== first }).take(max)
    }

    /** The widget picture, [wDp] x [hDp] at [k] px per dp, transparent around the pets. */
    internal fun render(ctx: Context, wDp: Float, hDp: Float, k: Float): Bitmap {
        val w = (wDp * k).roundToInt().coerceAtLeast(1); val h = (hDp * k).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cell = when (Talk.slot(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))) {
            Talk.Slot.NOON -> "smile"; Talk.Slot.NIGHT -> "sleep"; else -> "front"
        }
        val one = wDp < 90f && hDp < 90f
        val low = hDp < 90f
        val who = cast(ctx, if (one) 1 else if (low) 3 else 6)
        val pics = who.mapNotNull { e -> Frames.load(ctx, e)?.let { it[cell] ?: it["front"] } }

        fun pet(b: Bitmap, cx: Float, foot: Float, size: Float) {
            val s = size / max(b.width, b.height)
            val bw = b.width * s; val bh = b.height * s
            p.color = 0x40000000; c.drawOval(cx - bw * 0.36f, foot - size * 0.05f, cx + bw * 0.36f, foot + size * 0.04f, p)
            p.color = Color.BLACK   // opaque again: the shadow left the paint at 25 % alpha (operator 11-16 "too see-through")
            c.drawBitmap(b, null, RectF(cx - bw / 2, foot - bh, cx + bw / 2, foot), p)
        }

        if (pics.isEmpty()) {
            // nothing to show yet: a paper ball with "?" (opaque, so it reads on any wallpaper)
            val r = min(w, h) * 0.34f
            p.color = 0xFFFFFDF6.toInt(); c.drawCircle(w / 2f, h / 2f, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = r * 0.08f; p.color = 0xFF24331F.toInt(); c.drawCircle(w / 2f, h / 2f, r, p)
            p.style = Paint.Style.FILL
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = 0xFF24331F.toInt(); textAlign = Paint.Align.CENTER; textSize = r }
            c.drawText("?", w / 2f, h / 2f + r * 0.36f, t)
            return bmp
        }
        val (back, front) = rows(pics.size)
        val foot = h * 0.97f
        val frontSize = min(w * 0.98f / front, h * (if (back > 0) 0.66f else 0.94f))
        val backSize = frontSize * 0.86f
        // back row first (it sits behind), then the front; the floating pet (first) stands front middle
        val frontPics = middleFirst(pics.take(front))
        val backPics = pics.drop(front)
        for (i in backPics.indices) pet(backPics[i], w * (i + 1f) / (back + 1f), foot - frontSize * 0.42f, backSize)
        for (i in frontPics.indices) pet(frontPics[i], w * (i + 0.5f) / front, foot, frontSize)
        return bmp
    }

    /** How many stand in the back and front rows for [n] pets (mockup widget-family A). */
    internal fun rows(n: Int): Pair<Int, Int> = when (n) {
        in 0..3 -> 0 to n
        4 -> 2 to 2
        5 -> 2 to 3
        else -> 3 to 3
    }

    /** [list]'s first item moved to the middle slot: 1 2 3 -> 2 1 3. */
    internal fun <T> middleFirst(list: List<T>): List<T> {
        if (list.size < 3) return list
        val rest = list.drop(1).toMutableList()
        rest.add(list.size / 2, list[0])
        return rest
    }
}

/** The one widget's receiver (the class name stays: widgets placed before keep working). */
class Yard : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val r = goAsync()
        PetWidgets.update(ctx, ids) { r.finish() }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) {
        val r = goAsync()
        PetWidgets.update(ctx, intArrayOf(id)) { r.finish() }
    }
}
