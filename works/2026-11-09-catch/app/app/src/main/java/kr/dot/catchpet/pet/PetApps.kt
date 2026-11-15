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
 * to [MAX] chosen icons and a "펫 끄기" at the end; with none chosen the pet dances and the cloud holds only the [펫 설정 | 펫 끄기] bar.
 */
object PetApps {
    const val MAX = 8
    /** The cloud's bar: turn the floating pet off / open the pet settings. */
    const val OFF = "__pet_off__"
    const val SETTINGS = "__pet_settings__"
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
 * The thought cloud (mockup bubble C, operator 11-15): the chosen app icons over a dashed bottom bar
 * [펫 설정 | 펫 끄기], with two little puffs leading to the pet. On the floor it opens above the pet; on a wall it
 * opens toward the middle of the screen. A tap on an icon opens it; a tap anywhere else, or 6 s, closes the cloud.
 */
@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
class AppBubble(ctx: Context, private val apps: List<PetApps.App>, private val side: PetBrain.Zone, private val onPick: (String?) -> Unit) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val cols = min(4, apps.size).coerceAtLeast(1)
    private val rows = if (apps.isEmpty()) 0 else ceil(apps.size / cols.toFloat()).toInt()
    private val cell = 58 * d
    private val pad = 16 * d
    private val puff = 30 * d
    private val bar = 40 * d
    private val bodyW = maxOf(cols * cell + pad * 2, 200 * d)
    private val bodyH = (if (rows == 0) pad / 2 else rows * (cell + 12 * d) + pad) + bar
    private val wall = side == PetBrain.Zone.LEFT || side == PetBrain.Zone.RIGHT
    val wPx = (bodyW + if (wall) puff else 0f).roundToInt()
    val hPx = (bodyH + if (wall) 0f else puff).roundToInt()
    /** The body's left edge: on the left wall the puffs sit on the left. */
    private val ox = if (side == PetBrain.Zone.LEFT) puff else 0f
    private val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FFFDF6"); setShadowLayer(8 * d, 0f, 3 * d, 0x40000000) }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#24331F"); textSize = 10 * d; textAlign = Paint.Align.CENTER }
    private val barText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13 * d; textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E1E6DA"); style = Paint.Style.STROKE; strokeWidth = 2 * d
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(5 * d, 4 * d), 0f)
    }
    private val r = RectF()
    private val born = System.currentTimeMillis()

    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }   // shadow layers need software drawing

    override fun onDraw(c: Canvas) {
        r.set(ox + 4 * d, 4 * d, ox + bodyW - 4 * d, bodyH - 4 * d)
        c.drawRoundRect(r, 30 * d, 30 * d, cloud)
        when (side) {   // two puffs, the small one nearest the pet
            PetBrain.Zone.LEFT -> { c.drawCircle(puff - 9 * d, bodyH / 2, 8 * d, cloud); c.drawCircle(puff - 23 * d, bodyH / 2 + 6 * d, 4.5f * d, cloud) }
            PetBrain.Zone.RIGHT -> { c.drawCircle(bodyW + 9 * d, bodyH / 2, 8 * d, cloud); c.drawCircle(bodyW + 23 * d, bodyH / 2 + 6 * d, 4.5f * d, cloud) }
            else -> { c.drawCircle(width / 2f - 6 * d, bodyH + 5 * d, 8 * d, cloud); c.drawCircle(width / 2f + 2 * d, bodyH + 19 * d, 4.5f * d, cloud) }
        }
        for ((i, a) in apps.withIndex()) {
            val x = ox + rowX(i / cols) + (i % cols) * cell; val y = pad + (i / cols) * (cell + 12 * d)
            val s = 40 * d
            a.icon.setBounds((x + (cell - s) / 2).roundToInt(), y.roundToInt(), (x + (cell + s) / 2).roundToInt(), (y + s).roundToInt())
            a.icon.draw(c)
            val name = if (a.label.length > 5) a.label.take(5) + "…" else a.label
            c.drawText(name, x + cell / 2, y + s + 13 * d, label)
        }
        // the bar: a dashed line over it, a dashed divider, [펫 설정 | 펫 끄기]
        val top = bodyH - bar
        if (apps.isNotEmpty()) c.drawLine(ox + 14 * d, top, ox + bodyW - 14 * d, top, dash)
        val mid = ox + bodyW / 2
        c.drawLine(mid, top + 8 * d, mid, bodyH - 12 * d, dash)
        val ty = top + bar / 2 + 3 * d
        barText.color = Color.parseColor("#24331F"); c.drawText("펫 설정", ox + bodyW / 4, ty, barText)
        barText.color = Color.parseColor("#B3261E"); c.drawText("펫 끄기", ox + bodyW * 3 / 4, ty, barText)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) { onPick(null); return true }
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        val x = e.x - ox
        if (x < 0 || x > bodyW || e.y > bodyH) { onPick(null); return true }
        if (e.y >= bodyH - bar) { onPick(if (x < bodyW / 2) PetApps.SETTINGS else PetApps.OFF); return true }
        val row = ((e.y - pad) / (cell + 12 * d)).toInt()
        val gx = rowX(row)
        val col = ((x - gx) / cell).toInt()
        val i = row * cols + col
        onPick(if (x >= gx && e.y >= pad && col < cols && i in apps.indices) apps[i].pkg else null)
        return true
    }

    /** Left edge of a row of icons inside the body: every row centred, a short last row too. */
    private fun rowX(row: Int): Float = (bodyW - min(cols, apps.size - row * cols).coerceAtLeast(1) * cell) / 2

    fun expired() = System.currentTimeMillis() - born > 6000

    /** Window params beside the pet's window box (px): above it on the floor, toward the middle on a wall. */
    fun params(pet: android.graphics.Rect, screenW: Int, screenH: Int): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            wPx, hPx, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            fitInsetsTypes = 0   // same origin as the pet window
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            when (side) {
                PetBrain.Zone.LEFT -> { x = pet.right; y = pet.centerY() - hPx / 2 }
                PetBrain.Zone.RIGHT -> { x = pet.left - wPx; y = pet.centerY() - hPx / 2 }
                else -> { x = pet.centerX() - wPx / 2; y = pet.top - hPx }
            }
            x = x.coerceIn(0, (screenW - wPx).coerceAtLeast(0))
            y = y.coerceIn(0, (screenH - hPx).coerceAtLeast(0))
        }
}
