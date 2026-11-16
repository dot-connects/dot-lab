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
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
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
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Home-screen widgets (spec-extras 2, operator 11-16: all three sizes): the shown pet in its yard, by time of day.
 * A = 2×2 yard, B = 4×2 yard + name + the hour's line + [뽑기][도감], C = 1×1 the pet alone. Still pictures: the
 * system redraws them every 30 min (a slot change can show up to 30 min late), and we redraw on any dex change.
 */
object PetWidgets {
    enum class Kind(val layout: Int, val defW: Float, val defH: Float) {
        YARD(R.layout.widget_image, 110f, 110f), WIDE(R.layout.widget_wide, 250f, 110f), STICKER(R.layout.widget_image, 57f, 57f)
    }

    private val work = Executors.newSingleThreadExecutor()
    private val providers = listOf(Kind.YARD to Yard::class.java, Kind.WIDE to Wide::class.java, Kind.STICKER to Sticker::class.java)

    /** Redraw every placed widget (a catch, a deleted entry, another pet or look, a new name). Any thread. */
    fun refresh(ctx: Context) {
        val app = ctx.applicationContext
        work.execute {
            // Dex.write calls this on every save: never let a widget problem throw on this thread (review 11-16)
            runCatching {
                val mgr = AppWidgetManager.getInstance(app) ?: return@runCatching
                for ((kind, cls) in providers) for (id in mgr.getAppWidgetIds(ComponentName(app, cls))) draw(app, mgr, id, kind)
            }.onFailure { Log.w("PetWidgets", "refresh", it) }
        }
    }

    internal fun update(ctx: Context, ids: IntArray, kind: Kind, done: () -> Unit) {
        val app = ctx.applicationContext
        work.execute {
            try { runCatching { val mgr = AppWidgetManager.getInstance(app); for (id in ids) draw(app, mgr, id, kind) } } finally { done() }
        }
    }

    private fun draw(ctx: Context, mgr: AppWidgetManager, id: Int, kind: Kind) {
        runCatching {
            val o = mgr.getAppWidgetOptions(id)
            // portrait: min width x max height, landscape: max width x min height; nothing yet = the default cell size
            val land = ctx.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val wDp = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH else AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 }?.toFloat() ?: kind.defW
            val hDp = o.getInt(if (land) AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT else AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT).takeIf { it > 0 }?.toFloat() ?: kind.defH
            val d = ctx.resources.displayMetrics.density
            val k = min(d, 600f / max(wDp, hDp))   // keeps the bitmap small enough for RemoteViews
            val art = render(ctx, kind, wDp, hDp, k)
            val v = RemoteViews(ctx.packageName, kind.layout)
            v.setImageViewBitmap(R.id.art, art)
            v.setOnClickPendingIntent(R.id.art, open(ctx, "catch", 1))
            if (kind == Kind.WIDE) {
                v.setOnClickPendingIntent(R.id.btn_catch, open(ctx, "catch", 2))
                v.setOnClickPendingIntent(R.id.btn_dex, open(ctx, "dex", 3))
            }
            mgr.updateAppWidget(id, v)
        }.onFailure { Log.w("PetWidgets", "draw $id", it) }
    }

    private fun open(ctx: Context, to: String, code: Int): PendingIntent =
        PendingIntent.getActivity(ctx, code, Intent(ctx, MainActivity::class.java).putExtra(MainActivity.OPEN, to)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private class Look(val sky: Int, val grass: Int, val cell: String, val nightText: Boolean)
    private fun look(slot: Talk.Slot) = when (slot) {
        Talk.Slot.MORNING -> Look(0xFFFFE7B0.toInt(), 0xFFD9F2C9.toInt(), "front", false)
        Talk.Slot.NOON -> Look(0xFFBFE6FF.toInt(), 0xFFD9F2C9.toInt(), "smile", false)
        Talk.Slot.EVENING -> Look(0xFFF6B98F.toInt(), 0xFFCFE3B8.toInt(), "front", false)
        Talk.Slot.NIGHT -> Look(0xFF2C3A5C.toInt(), 0xFF566B4C.toInt(), "sleep", true)
    }

    private val INK = 0xFF24331F.toInt()
    private val MUTED = 0xFF5D6B57.toInt()

    /** The widget picture, [wDp] x [hDp] at [k] px per dp. */
    internal fun render(ctx: Context, kind: Kind, wDp: Float, hDp: Float, k: Float): Bitmap {
        val w = (wDp * k).roundToInt().coerceAtLeast(1); val h = (hDp * k).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val lk = look(Talk.slot(hour))
        val id = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getString("pet", null)
        val entry = id?.let { Dex.get(ctx, it) }
        val cells = entry?.let { Frames.load(ctx, it) }
        val pet = cells?.let { it[lk.cell] ?: it["front"] }
        val name = entry?.name.orEmpty()
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

        fun yard(r: RectF, radius: Float) {
            val clip = Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) }
            c.save(); c.clipPath(clip)
            val horizon = r.top + r.height() * 0.58f
            p.shader = LinearGradient(0f, r.top, 0f, horizon, lk.sky, lk.sky, Shader.TileMode.CLAMP); c.drawRect(r.left, r.top, r.right, horizon, p)
            p.shader = null; p.color = lk.grass; c.drawRect(r.left, horizon, r.right, r.bottom, p)
            c.restore()
        }
        fun drawPet(cx: Float, foot: Float, size: Float) {
            if (pet == null) {
                // nothing chosen yet: a grey fur ball outline
                p.color = 0xAAFFFDF6.toInt(); c.drawCircle(cx, foot - size * 0.42f, size * 0.36f, p)
                return
            }
            val s = size / max(pet.width, pet.height)
            val bw = pet.width * s; val bh = pet.height * s
            p.color = 0x33000000; c.drawOval(cx - bw * 0.38f, foot - size * 0.05f, cx + bw * 0.38f, foot + size * 0.04f, p)
            c.drawBitmap(pet, null, RectF(cx - bw / 2, foot - bh, cx + bw / 2, foot), p)
        }

        when (kind) {
            Kind.YARD -> {
                val r = RectF(0f, 0f, w.toFloat(), h.toFloat()); val rad = min(w, h) * 0.2f
                yard(r, rad)
                drawPet(w / 2f, h * 0.9f, min(w, h) * 0.56f)
                t.color = if (lk.nightText) Color.WHITE else INK; t.textSize = min(w, h) * 0.12f
                c.fitText(if (pet == null) "털뭉치를 뽑아 주세요" else name, w * 0.1f, h * 0.17f, w * 0.84f, t)
            }
            Kind.WIDE -> {
                val r = RectF(0f, 0f, w.toFloat(), h.toFloat()); val rad = h * 0.2f
                val clip = Path().apply { addRoundRect(r, rad, rad, Path.Direction.CW) }
                c.save(); c.clipPath(clip)
                p.color = 0xFFFFFDF6.toInt(); c.drawRect(r, p)
                c.restore()
                val side = h.toFloat()
                c.save(); c.clipPath(clip); yard(RectF(0f, 0f, side * 0.78f, side), 0f); c.restore()
                drawPet(side * 0.39f, h * 0.88f, side * 0.56f)
                val x = side * 0.78f + h * 0.09f
                t.color = INK; t.textSize = h * 0.15f
                c.fitText(if (pet == null) "리얼뽑기" else name, x, h * 0.24f, w - x - h * 0.08f, t)
                t.typeface = Typeface.DEFAULT; t.color = MUTED; t.textSize = h * 0.11f
                val line = if (pet == null) "털뭉치를 뽑아 주세요" else Talk.slotLine(hour, name, cal.get(Calendar.DAY_OF_YEAR))
                c.fitText(line, x, h * 0.44f, w - x - h * 0.08f, t)
                // the bottom right stays empty: the layout's real [뽑기][도감] buttons sit there
            }
            Kind.STICKER -> {
                drawPet(w / 2f, h * 0.96f, min(w, h) * 0.92f)
                if (pet == null) { t.color = INK; t.textAlign = Paint.Align.CENTER; t.textSize = min(w, h) * 0.4f; c.drawText("?", w / 2f, h * 0.68f, t) }
            }
        }
        return bmp
    }

    /** Draw [s] at ([x], [y]), shrinking it (down to 70 %) and then cutting it with "…" to fit [maxW] px. */
    private fun Canvas.fitText(s: String, x: Float, y: Float, maxW: Float, t: Paint) {
        val size = t.textSize
        while (t.measureText(s) > maxW && t.textSize > size * 0.7f) t.textSize -= 1f
        var v = s
        while (v.length > 1 && t.measureText(v) > maxW) v = v.dropLast(2) + "…"
        drawText(v, x, y, t)
        t.textSize = size
    }
}

abstract class PetWidget(private val kind: PetWidgets.Kind) : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        val r = goAsync()
        PetWidgets.update(ctx, ids, kind) { r.finish() }
    }

    override fun onAppWidgetOptionsChanged(ctx: Context, mgr: AppWidgetManager, id: Int, o: Bundle) {
        val r = goAsync()
        PetWidgets.update(ctx, intArrayOf(id), kind) { r.finish() }
    }
}

class Yard : PetWidget(PetWidgets.Kind.YARD)
class Wide : PetWidget(PetWidgets.Kind.WIDE)
class Sticker : PetWidget(PetWidgets.Kind.STICKER)
