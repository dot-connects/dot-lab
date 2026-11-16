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
        // a group selfie (operator 11-16, mockup widget-selfie): the floating pet big at the front left, the rest
        // fanned over its head and to the right; no outline, no date, only a soft shadow and a slight tilt
        val wide = w >= h * 1.6f
        val box = if (wide) 200f else 100f
        val slots = slots(pics.size, wide)
        val u = min(w / box, h / 100f) * 0.93f            // the slot box fitted in, bottom centred, room for the tilt
        val ox = (w - box * u) / 2f; val oy = h - 100f * u - h * 0.02f
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000; maskFilter = android.graphics.BlurMaskFilter(max(1f, 2.5f * u), android.graphics.BlurMaskFilter.Blur.NORMAL) }
        c.save()
        c.rotate(-5f, w / 2f, h / 2f)
        // farthest (smallest) first, the selfie taker last
        for (i in slots.indices.sortedWith(compareBy<Int>({ it == 0 }, { slots[it][2] }))) {
            val b = pics[i]; val (x, foot, size) = slots[i]
            val sz = size * u
            val sc = sz / max(b.width, b.height)
            val bw = b.width * sc; val bh = b.height * sc
            val r = RectF(ox + x * u - bw / 2, oy + foot * u - bh, ox + x * u + bw / 2, oy + foot * u)
            val a = b.extractAlpha()
            c.drawBitmap(a, null, RectF(r).apply { offset(0f, 1.6f * u) }, shadow)
            a.recycle()
            p.color = Color.BLACK   // opaque: a paint alpha would show through the pet (operator 11-16)
            c.drawBitmap(b, null, r, p)
        }
        c.restore()
        return bmp
    }

    /** Where each pet stands: x centre, foot y, size, in a 100 x 100 box (200 x 100 when wide); slot 0 = the selfie taker. */
    internal fun slots(n: Int, wide: Boolean): List<FloatArray> = (if (wide) WIDE else SQUARE)[n.coerceIn(1, 6) - 1]
        .map { floatArrayOf(it[0], it[1], it[2]) }

    // a farther pet's feet stay above every nearer pet's head, so no face is ever covered (operator 11-16)
    private val SQUARE = listOf(
        listOf(f(50, 99, 84)),
        listOf(f(34, 99, 64), f(78, 52, 40)),
        listOf(f(34, 99, 60), f(72, 48, 34), f(84, 93, 30)),
        listOf(f(32, 99, 58), f(46, 37, 28), f(77, 44, 30), f(84, 91, 28)),
        listOf(f(31, 99, 56), f(24, 38, 26), f(52, 31, 26), f(80, 41, 28), f(82, 88, 28)),
        listOf(f(30, 99, 54), f(18, 40, 24), f(42, 32, 24), f(66, 31, 24), f(87, 47, 24), f(81, 91, 28)),
    )
    private val WIDE = listOf(
        listOf(f(100, 99, 90)),
        listOf(f(64, 99, 84), f(140, 80, 60)),
        listOf(f(50, 99, 76), f(120, 62, 46), f(164, 97, 40)),
        listOf(f(46, 99, 74), f(110, 58, 42), f(154, 54, 38), f(150, 99, 38)),
        listOf(f(44, 99, 74), f(104, 60, 40), f(146, 50, 38), f(182, 60, 32), f(136, 99, 36)),
        listOf(f(42, 99, 74), f(100, 60, 40), f(140, 50, 38), f(178, 58, 34), f(116, 99, 34), f(156, 99, 34)),
    )
    private fun f(x: Int, foot: Int, size: Int) = floatArrayOf(x.toFloat(), foot.toFloat(), size.toFloat())
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
