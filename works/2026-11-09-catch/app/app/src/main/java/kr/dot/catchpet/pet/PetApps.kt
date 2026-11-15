package kr.dot.catchpet.pet

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Apps the pet opens on a double tap (operator 11-15): a thought-cloud bubble over the pet (mockup allmenus C) with up
 * to [MAX] chosen icons and a "펫 끄기" at the end; with none chosen the pet dances and the cloud holds only "펫 끄기".
 */
object PetApps {
    const val MAX = 8
    /** The cloud's last item: turn the floating pet off. */
    const val OFF = "__pet_off__"
    private const val KEY = "pet_apps"

    class App(val pkg: String, val label: String, val icon: Drawable)

    fun chosen(ctx: Context): List<String> =
        PetPrefs.prefs(ctx).getString(KEY, "").orEmpty().split(',').filter { it.isNotBlank() }

    fun setChosen(ctx: Context, pkgs: List<String>) { PetPrefs.prefs(ctx).edit().putString(KEY, pkgs.take(MAX).joinToString(",")).apply() }

    /** Every app with a launcher icon, by name (our own left out). */
    fun installed(ctx: Context): List<App> {
        val pm = ctx.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { App(it.activityInfo.packageName, it.loadLabel(pm).toString(), it.loadIcon(pm)) }
            .sortedBy { it.label }
    }

    /** The chosen apps still installed, in the chosen order. */
    fun load(ctx: Context): List<App> {
        val pm = ctx.packageManager
        return chosen(ctx).mapNotNull { pkg ->
            runCatching { val ai = pm.getApplicationInfo(pkg, 0); App(pkg, pm.getApplicationLabel(ai).toString(), pm.getApplicationIcon(ai)) }.getOrNull()
        }
    }

    fun open(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(i) }.isSuccess
    }
}

/**
 * The thought cloud: an overlay window above the pet holding the app icons, with two little puffs leading down to
 * the pet. A tap on an icon opens it; a tap anywhere else, or 6 s, closes the cloud.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class AppBubble(ctx: Context, private val apps: List<PetApps.App>, private val onPick: (String?) -> Unit) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val cols = min(4, apps.size)
    private val rows = ceil(apps.size / cols.toFloat()).toInt()
    private val cell = 58 * d
    private val pad = 16 * d
    private val puff = 30 * d
    val wPx = (cols * cell + pad * 2).roundToInt()
    val hPx = (rows * (cell + 12 * d) + pad * 2 + puff).roundToInt()
    private val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFDF6"); setShadowLayer(8 * d, 0f, 3 * d, 0x40000000) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#24331F"); textSize = 10 * d; textAlign = Paint.Align.CENTER }
    private val r = RectF()
    private val born = System.currentTimeMillis()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }   // shadow layers need software drawing

    override fun onDraw(c: Canvas) {
        val body = height - puff
        r.set(4 * d, 4 * d, width - 4 * d, body)
        c.drawRoundRect(r, 34 * d, 34 * d, cloud)
        c.drawCircle(width / 2f - 6 * d, body + 9 * d, 8 * d, cloud)
        c.drawCircle(width / 2f + 2 * d, body + 23 * d, 4.5f * d, cloud)
        for ((i, a) in apps.withIndex()) {
            val x = pad + (i % cols) * cell; val y = pad + (i / cols) * (cell + 12 * d)
            val s = 40 * d
            a.icon.setBounds((x + (cell - s) / 2).roundToInt(), y.roundToInt(), (x + (cell + s) / 2).roundToInt(), (y + s).roundToInt())
            a.icon.draw(c)
            val name = if (a.label.length > 5) a.label.take(5) + "…" else a.label
            c.drawText(name, x + cell / 2, y + s + 13 * d, label)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) { onPick(null); return true }
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        val col = ((e.x - pad) / cell).toInt(); val row = ((e.y - pad) / (cell + 12 * d)).toInt()
        val i = row * cols + col
        onPick(if (e.x >= pad && e.y >= pad && col < cols && i in apps.indices) apps[i].pkg else null)
        return true
    }

    fun expired() = System.currentTimeMillis() - born > 6000

    /** Window params: above the pet's box at (petX, petTop) px, kept on screen. */
    fun params(petCx: Int, petTop: Int, screenW: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            wPx, hPx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (petCx - wPx / 2).coerceIn(0, (screenW - wPx).coerceAtLeast(0))
            y = (petTop - hPx).coerceAtLeast(0)
        }
}
