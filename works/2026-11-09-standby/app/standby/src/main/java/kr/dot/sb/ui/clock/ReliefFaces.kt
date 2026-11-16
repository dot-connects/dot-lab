package kr.dot.sb.ui.clock

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LongState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.LumeView
import kr.dot.sb.model.SquareNumerals
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalClockActive
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.ThemeTokens
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * r11 Part C (spec-relief-clocks.md): three relief watch-dial faces from the approved mockup
 * (settings-r10/relief-clocks.html, viewBox 216 × 100). Every measurement below is in mockup units (u); the dial
 * fills the whole face.
 *
 * Performance (StandBy runs for hours on a charger):
 *  - everything static (dial, light, grain, tracks, numerals, capsules, ghost segments and their shadows) is drawn once
 *    into a bitmap kept with `remember`, keyed by size + palette + style. It is never rebuilt in draw, so a board that
 *    recomposes (a running pomodoro) does not redraw it;
 *  - per second only the hands (A, B) or the seconds capsule (C) draw; the lit digits (C) are a second bitmap rebuilt
 *    once a minute;
 *  - shadows are blurred once: static parts inside the static bitmap, each hand into an ALPHA_8 bitmap (half size)
 *    drawn rotated with the hand at a fixed screen offset. No per-frame blur, no per-frame allocation.
 */

/** The three faces' dials, palettes and options. */
object Relief {
    /** A dial preset (mockup `DIALS`): dial, ink (track, numerals), accent (second hand), lume (capsule fill). */
    class Dial(val ko: String, val dial: Long, val ink: Long, val acc: Long, val lume: Long)

    val DIALS = listOf(
        Dial("아이스", 0xFFDDE7E6, 0xFF1D2626, 0xFF3A4040, 0xFFF3F8F2),
        Dial("베이지", 0xFFE9DCC6, 0xFF25211B, 0xFF3D3A35, 0xFFFBF7EE),
        Dial("크림", 0xFFF0ECE2, 0xFF1F1E1B, 0xFFC94A1C, 0xFFFBFAF4),
        Dial("로즈", 0xFFEFD2CB, 0xFF2A1D1A, 0xFF8A2C22, 0xFFFDF3EF),
        Dial("스카이", 0xFFCFE2EC, 0xFF14283A, 0xFFC94A1C, 0xFFF3F9FC),
        Dial("테니스", 0xFF2E6A4F, 0xFFEEF1E8, 0xFFF4C84A, 0xFFE6F1DC),
        Dial("옐로", 0xFFF0D873, 0xFF2A2410, 0xFF2A2410, 0xFFFFFBE6),
        Dial("코얼", 0xFF2A2C2F, 0xFFECEDE9, 0xFFF08A3C, 0xFFE3F0DD),
    )
    val ICE = DIALS[0]
    val COAL = DIALS[7]

    val FACES = setOf(FaceId.RELIEF, FaceId.RELIEF_SQUARE, FaceId.RELIEF_DIGITAL)

    /** The 야광 view by day (mockup `LUME`). */
    private val LUME = Dial("야광", 0xFF0E1718, 0xFF6FE0CF, 0xFF6FE0CF, 0xFF7FF2DF)

    fun dial(s: ClockStyle): Long = s.faceColor ?: ICE.dial

    /** The dial's preset, or one derived from a custom dial: a dark dial gets light ink (mockup `shuffled`). */
    fun preset(s: ClockStyle): Dial {
        val d = dial(s)
        DIALS.firstOrNull { it.dial == d }?.let { return it }
        val (h, _, _) = kr.dot.sb.ui.theme.Hsl.toHsl(Color(d))
        return if (Color(d).luminance() < 0.18f) Dial("", d, 0xFFEEEFE9, hsl((h + 180f) % 360f, 0.85f, 0.62f), 0xFFE6F1DC)
        else Dial("", d, hsl(h, 0.22f, 0.12f), hsl(h, 0.12f, 0.24f), 0xFFFBFAF3)
    }

    fun accent(s: ClockStyle): Long = s.accentColor ?: preset(s).acc

    private fun hsl(h: Float, s: Float, l: Float): Long = Color.hsl(h, s, l).toArgb().toLong() and 0xFFFFFFFFL

    /**
     * What draws now. Night (>= 0.5) follows the app's night rule: night ink on the night page, no grain/light/shadows;
     * the 야광 views keep their glowing capsules there, 낮 shows them as outlines.
     */
    internal fun palette(s: ClockStyle, nightBg: Int?): RlPal {
        if (nightBg != null) {
            val ni = ThemeTokens.NIGHT_INK.toArgb()
            return RlPal(nightBg, ni, ni, ni, glow = s.lumeView != LumeView.DAY, night = true)
        }
        val p = if (s.lumeView == LumeView.LUME) LUME else preset(s)
        val acc = if (s.lumeView == LumeView.LUME) LUME.acc else accent(s)
        return RlPal(p.dial.toInt(), p.ink.toInt(), acc.toInt(), p.lume.toInt(), glow = s.lumeView == LumeView.LUME, night = false)
    }
}

/** The Jost weights the static layer draws with. */
internal class RlFonts(val regular: Typeface, val medium: Typeface, val thin: Typeface, val bold: Typeface)

/** Resolved colours (ARGB ints). [glow] = lume look (glowing capsules, no cast shadows); [night] = flat night drawing. */
internal data class RlPal(val dial: Int, val ink: Int, val acc: Int, val lume: Int, val glow: Boolean, val night: Boolean) {
    val shadows: Boolean get() = !glow && !night
}

private enum class RlKind { ROUND, SQUARE, CAPSULE }

@Composable
internal fun ReliefFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) = ReliefDial(RlKind.ROUND, style, d, ink, inSlot, modifier)

@Composable
internal fun ReliefSquareFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) = ReliefDial(RlKind.SQUARE, style, d, ink, inSlot, modifier)

@Composable
internal fun ReliefDigitalFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) = ReliefDial(RlKind.CAPSULE, style, d, ink, inSlot, modifier)

@Composable
private fun ReliefDial(kind: RlKind, style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    // keyed on night on/off + the theme's final night page (not ink.bg, which lerps during the ramp): the static bitmap
    // is built once per transition, not on every ramp step
    val nightBg = if (ink.night >= 0.5f) LocalTheme.current.tokens.night().bg.base.toArgb() else null
    val pal = remember(style, nightBg) { Relief.palette(style, nightBg) }
    val tf = rememberClockTypeface(FontId.JOST, 400)
    val tfDate = rememberClockTypeface(FontId.JOST, 500)
    val tfThin = rememberClockTypeface(FontId.JOST, 300)
    val tfBold = rememberClockTypeface(FontId.JOST, 600)
    val radius = with(LocalDensity.current) { if (inSlot) 14f * density else 0f }
    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        if (w <= 0 || h <= 0) return@BoxWithConstraints
        if (min(w, h) < with(LocalDensity.current) { COMPACT_MIN.toPx() }) {
            val p = rememberTimePainter(style)
            FaceCanvas(Modifier) { compact(p, d, Look(kr.dot.sb.model.Effect.SOLID, pal.ink, pal.ink)) }
            return@BoxWithConstraints
        }
        val geo = remember(kind, w, h) { RlGeo.of(kind, w, h) }
        val use24 = style.h24 ?: d.use24h
        val hh = if (use24) d.now.hour else (d.now.hour + 11) % 12 + 1
        val digits = String.format(java.util.Locale.ROOT, "%02d%02d", hh, d.now.minute).let { if (!use24 && hh < 10) " " + it.drop(1) else it }
        // 12 h with a one-digit hour and no engraved segments: the row moves half a digit left so it stays centred
        val shift = if (kind == RlKind.CAPSULE && !geo.portrait && digits[0] == ' ' && !style.ghost) -(RlStatic.W + RlStatic.GAP) / 2 else 0f
        // static bitmap: built once per size + palette + style, off the main thread (blurs and gradients over the whole
        // face); until it is ready the previous one is stretched over the face, or on the first build the plain dial
        // colour shows. It is the face's own size (the slot), never larger.
        val build = {
            RlStatic.render(kind, geo, pal, numerals = style.analogNumbers != AnalogNumbers.NONE, squareNumerals = style.squareNumerals, ghost = style.ghost, date = style.showInfo, radius = radius, fonts = RlFonts(tf, tfDate, tfThin, tfBold), shift = shift).asImageBitmap()
        }
        val keys = arrayOf<Any?>(kind, w, h, pal, style.analogNumbers, style.squareNumerals, style.ghost, style.showInfo, radius, tf, tfDate, tfThin, tfBold, shift)
        // shots (fixed second) build in composition, so a capture never races the background build
        val static: State<ImageBitmap?> = if (fixedSecond != null) remember(*keys) { mutableStateOf(build()) }
            else produceState<ImageBitmap?>(null, *keys) { value = withContext(Dispatchers.Default) { build() } }
        Spacer(
            Modifier.fillMaxSize().drawBehind {
                val img = static.value
                if (img != null) drawImage(img, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                else drawRoundRect(Color(pal.dial), cornerRadius = CornerRadius(radius))
            },
        )
        val second = rememberSecond()
        if (kind == RlKind.CAPSULE) {
            val day = "${WEEKDAYS_EN[d.now.dayOfWeek.value - 1]} ${d.now.dayOfMonth}"
            // lit digits: two capsule bitmaps (made once per size + palette) stamped per segment; no per-minute bitmap
            val lit = remember(geo, pal, tfDate) { RlLit(geo, pal, tfDate) }
            val marker = remember(geo, pal) { RlMarker(geo, pal) }
            val dayText = if (style.showInfo) day else null
            Spacer(
                Modifier.fillMaxSize().drawBehind {
                    drawIntoCanvas { lit.draw(it.nativeCanvas, digits, dayText, shift); marker.draw(it.nativeCanvas, second.intValue.toFloat()) }
                },
            )
        } else {
            val hands = remember(geo, pal) { RlHands(geo, pal) }
            val sweep = if (style.sweep) rememberSweep() else null
            val hr = d.now.hour % 12; val mn = d.now.minute
            Spacer(
                Modifier.fillMaxSize().drawBehind {
                    // no boxing per frame: plain branches, primitives only
                    val s = if (sweep != null) sweep.longValue / 1000f else second.intValue.toFloat()
                    drawIntoCanvas { hands.draw(it.nativeCanvas, hr, mn, s) }
                },
            )
        }
    }
}

private val WEEKDAYS_EN = arrayOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN") // literal-ok: the dial's date window (mockup)

/** 기계식 스윕: milliseconds into the minute, at most 30 times a second while composed (shots: the fixed second). */
@Composable
private fun rememberSweep(): LongState {
    fixedSecond?.let { s -> return remember { mutableLongStateOf(s * 1000L) } }
    val ms = remember { mutableLongStateOf(System.currentTimeMillis() % 60_000) }
    val active = LocalClockActive.current
    LaunchedEffect(active) {
        // a plain delay loop (requesting every vsync for hours would keep the display at 60-120 Hz); paused while the
        // clock is covered or its page is off screen
        while (active) { ms.longValue = System.currentTimeMillis() % 60_000; delay(33) }
    }
    return ms
}

/**
 * Mockup units on screen: [u] px per unit, the dial centre ([cx], [cy]). [portrait] (C on an upright screen, r14 mockup
 * `relPortrait`): hours over minutes, the colon studs side by side between the rows, date window above, scale under.
 */
private class RlGeo(val kind: RlKind, val w: Int, val h: Int, val u: Float, val cx: Float, val cy: Float, val portrait: Boolean = false) {
    /** Half width / height in units (the short one is 50 for the analog faces). */
    val hw = w / 2f / u
    val hh = h / 2f / u
    fun x(v: Float) = cx + v * u
    fun y(v: Float) = cy + v * u

    // C layout in units from the centre: each digit's left / top, the two colon studs (x, y, x, y), the seconds scale
    val digitX: FloatArray
    val digitY: FloatArray
    val studs: FloatArray
    val scaleHalf: Float
    val scaleY: Float
    init {
        val dw = RlStatic.W; val gap = RlStatic.GAP
        if (portrait) {
            val x0 = -(2 * dw + gap) / 2
            digitX = floatArrayOf(x0, x0 + dw + gap, x0, x0 + dw + gap)
            digitY = floatArrayOf(-PORT_TOP, -PORT_TOP, PORT_ROW_GAP / 2, PORT_ROW_GAP / 2)
            studs = floatArrayOf(-8f, 0f, 8f, 0f)
            scaleHalf = (2 * dw + gap) / 2 + 4f
            scaleY = PORT_TOP + 11f
        } else {
            digitX = FloatArray(4) { RlStatic.digitX(it) }
            digitY = FloatArray(4) { -RlStatic.H / 2 }
            val colon = (RlStatic.digitX(1) + dw + RlStatic.digitX(2)) / 2
            studs = floatArrayOf(colon, -8f, colon, 8f)
            scaleHalf = RlStatic.SCALE_HALF
            scaleY = RlStatic.SCALE_Y
        }
    }

    companion object {
        /** Portrait C: rows 17 units apart, the block (2 × 46 + 17) centred on [cy], so its top is 54.5 units up. */
        const val PORT_ROW_GAP = 17f
        const val PORT_TOP = RlStatic.H + PORT_ROW_GAP / 2

        fun of(kind: RlKind, w: Int, h: Int): RlGeo = when (kind) {
            // the round dials need 100 units across the short side
            RlKind.ROUND, RlKind.SQUARE -> RlGeo(kind, w, h, min(w, h) / 100f, w / 2f, h / 2f)
            // upright: the digit pair takes 74 % of the width (capped so date window .. scale labels, ~145 units, fit
            // the height); the block sits a little below the middle
            RlKind.CAPSULE -> if (h > w * 1.15f) min(w * 0.74f / (2 * RlStatic.W + RlStatic.GAP), h / 160f).let { u -> RlGeo(kind, w, h, u, w / 2f, h * 0.53f, portrait = true) }
            // the digits + seconds scale need 150 units across, 100 down; the row sits 6 units above the middle
            else min(w / 150f, h / 100f).let { u -> RlGeo(kind, w, h, u, w / 2f, h / 2f - 6f * u) }
        }
    }
}

// ---- shared drawing -------------------------------------------------------------------------------------------------

private fun dir(deg: Float): Pair<Float, Float> { val a = deg * PI.toFloat() / 180f; return sin(a) to -cos(a) }

/** Point on a rounded square's edge along [deg] (mockup `rectPoint`). */
private fun rectPoint(deg: Float, hw: Float, hh: Float): Pair<Float, Float> {
    val (dx, dy) = dir(deg)
    val t = min(hw / max(abs(dx), 1e-6f), hh / max(abs(dy), 1e-6f))
    return dx * t to dy * t
}

private fun shade(c: Int, d: Float): Int {
    fun f(v: Int) = (v + 255 * d).toInt().coerceIn(0, 255)
    return android.graphics.Color.argb(255, f(android.graphics.Color.red(c)), f(android.graphics.Color.green(c)), f(android.graphics.Color.blue(c)))
}

private fun alpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a * 255).toInt().coerceIn(0, 255) shl 24)

private const val WHITE = -0x1 // 0xFFFFFFFF
private const val BLACK = -0x1000000 // 0xFF000000

/** Silver bezel gradient across a box (mockup `-silver`, diagonal). */
private fun silver(l: Float, t: Float, r: Float, b: Float, glow: Boolean): Shader =
    if (glow) LinearGradient(l, t, r, b, intArrayOf(0xFF34413F.toInt(), 0xFF1A2322.toInt()), null, Shader.TileMode.CLAMP)
    else LinearGradient(l, t, r, b, intArrayOf(WHITE, 0xFFAEB2B8.toInt(), 0xFFF7F8F9.toInt(), 0xFF8A8E94.toInt(), 0xFFC9CCD0.toInt()), floatArrayOf(0f, 0.3f, 0.55f, 0.8f, 1f), Shader.TileMode.CLAMP)

/** Lume fill across a box (mockup `-lume`, horizontal). */
private fun lumeFill(l: Float, r: Float, p: RlPal): Shader =
    if (p.glow) LinearGradient(l, 0f, r, 0f, p.lume, p.lume, Shader.TileMode.CLAMP)
    else LinearGradient(l, 0f, r, 0f, intArrayOf(shade(p.lume, -0.06f), WHITE, shade(p.lume, -0.1f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)

/** A two-layer cast shadow (tight dark contact + wide soft) in mockup units: dx, dy, blur, opacity (mockup `sh`). */
private class RlShadow(val dx: Float, val dy: Float, val b: Float, val o: Float)
private val S_I = RlShadow(0.55f, 0.85f, 0.45f, 0.34f)
private val S_H = RlShadow(1.1f, 1.7f, 0.7f, 0.3f)
private val S_M = RlShadow(1.6f, 2.5f, 0.9f, 0.28f)
private val S_S = RlShadow(2.1f, 3.2f, 1f, 0.26f)

/** Draws [shape] twice under itself as the two shadow layers (software canvas: once, into a cached bitmap). */
private inline fun castShadow(c: Canvas, u: Float, s: RlShadow, shape: (Paint) -> Unit) {
    val wide = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(BLACK, s.o * 0.8f); maskFilter = BlurMaskFilter(max(0.5f, s.b * 1.6f * u * 2f), BlurMaskFilter.Blur.NORMAL) }
    val tight = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(BLACK, min(1f, s.o * 1.3f)); maskFilter = BlurMaskFilter(max(0.5f, s.b * 0.35f * u * 2f), BlurMaskFilter.Blur.NORMAL) }
    c.save(); c.translate(s.dx * u, s.dy * u); shape(wide); c.restore()
    c.save(); c.translate(s.dx * 0.35f * u, s.dy * 0.35f * u); shape(tight); c.restore()
}

/**
 * An applied lume capsule (mockup `capsule`): silver bezel, lume fill, a fine inner line and a light edge, cast shadow.
 * [x], [y] centre in px, [deg] rotation, [len], [w] in units.
 */
private fun capsule(c: Canvas, g: RlGeo, p: RlPal, x: Float, y: Float, deg: Float, len: Float, w: Float, shadow: RlShadow = S_I) {
    val u = g.u
    c.save(); c.translate(x, y); c.rotate(deg)
    val outer = RectF(-w / 2 * u, -len / 2 * u, w / 2 * u, len / 2 * u)
    val rr = w / 2 * u
    if (p.night) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.lume }
        if (p.glow) { paint.alpha = 230; c.drawRoundRect(outer, rr, rr, paint) }
        else { paint.style = Paint.Style.STROKE; paint.strokeWidth = max(1f, 0.25f * u); c.drawRoundRect(outer, rr, rr, paint) }
        c.restore(); return
    }
    if (p.shadows) castShadow(c, u, shadow) { sp -> c.drawRoundRect(outer, rr, rr, sp) }
    val r = min(0.5f, w * 0.2f)
    val inner = RectF((-w / 2 + r) * u, (-len / 2 + r) * u, (w / 2 - r) * u, (len / 2 - r) * u)
    val ir = (w - 2 * r) / 2 * u
    if (p.glow) {
        // 야광: the lume glows (one blur, into the static bitmap) instead of casting a shadow
        val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(p.lume, 0.55f); maskFilter = BlurMaskFilter(max(1f, 0.7f * u * 2f), BlurMaskFilter.Blur.NORMAL) }
        c.drawRoundRect(inner, ir, ir, halo)
    }
    c.drawRoundRect(outer, rr, rr, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = silver(outer.left, outer.top, outer.right, outer.bottom, p.glow) })
    c.drawRoundRect(inner, ir, ir, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = lumeFill(inner.left, inner.right, p) })
    if (!p.glow) {
        c.drawRoundRect(inner, ir, ir, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.12f * u; color = alpha(BLACK, 0.16f) })
        val hl = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 0.16f * u; color = WHITE }
        c.drawLine((-w / 2 + 0.32f) * u, (-len / 2 + w / 2) * u, (-w / 2 + 0.32f) * u, (len / 2 - w / 2) * u, hl)
    }
    c.restore()
}

// ---- static layer ---------------------------------------------------------------------------------------------------

private object RlStatic {
    /** Matte grain: one 96 px noise tile (mockup `feTurbulence`, 7 % alpha), made once, tiled. */
    private val grain: Bitmap by lazy {
        val n = 96
        val rnd = java.util.Random(7)
        val px = IntArray(n * n) { val v = rnd.nextInt(256); android.graphics.Color.argb((rnd.nextFloat() * 0.07f * 255 * 2).toInt().coerceAtMost(255), v, v, v) }
        Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888)
    }

    fun render(kind: RlKind, g: RlGeo, p: RlPal, numerals: Boolean, squareNumerals: SquareNumerals, ghost: Boolean, date: Boolean, radius: Float, fonts: RlFonts, shift: Float = 0f): Bitmap {
        val tf = fonts.regular; val tfDate = fonts.medium
        val bmp = Bitmap.createBitmap(g.w, g.h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val full = RectF(0f, 0f, g.w.toFloat(), g.h.toFloat())
        val clip = Path().apply { addRoundRect(full, radius, radius, Path.Direction.CW) }
        c.clipPath(clip)
        background(c, g, p, full)
        when (kind) {
            RlKind.ROUND -> round(c, g, p, numerals, tf)
            RlKind.SQUARE -> square(c, g, p, squareNumerals, fonts)
            RlKind.CAPSULE -> capsuleStatic(c, g, p, ghost, date, tf, shift)
        }
        return bmp
    }

    /** Dial colour, the soft top-left light with edge falloff, grain (none at night). */
    private fun background(c: Canvas, g: RlGeo, p: RlPal, full: RectF) {
        c.drawRect(full, Paint().apply { color = p.dial })
        if (p.night) return
        // mockup `-l`: radial from (36 %, 30 %), radius 85 % of each side (an ellipse on a wide screen)
        val light = RadialGradient(0f, 0f, 1f, intArrayOf(alpha(WHITE, if (p.glow) 0.03f else 0.18f), alpha(WHITE, 0f), alpha(BLACK, if (p.glow) 0.35f else 0.14f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        light.setLocalMatrix(Matrix().apply { setScale(0.85f * g.w, 0.85f * g.h); postTranslate(0.36f * g.w, 0.30f * g.h) })
        c.drawRect(full, Paint().apply { shader = light })
        c.drawRect(full, Paint().apply { shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) })
    }

    /** Recessed step (mockup `stepCircle`): shade on the upper lip, light on the lower one. */
    private fun stepCircle(c: Canvas, g: RlGeo, p: RlPal, r: Float) {
        val u = g.u
        if (p.night || p.glow) {
            c.drawCircle(g.cx, g.cy, r * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, 0.25f * u); color = alpha(p.ink, 0.18f) })
            return
        }
        val top = g.cy - r * u; val bot = g.cy + r * u
        val lip = LinearGradient(0f, top, 0f, bot, intArrayOf(alpha(BLACK, 0.26f), alpha(BLACK, 0f), alpha(WHITE, 0f), alpha(WHITE, 0.7f)), floatArrayOf(0f, 0.5f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(g.cx, g.cy, r * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.55f * u; shader = lip })
        c.drawCircle(g.cx, g.cy, (r - 0.5f) * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(BLACK, 0.025f) })
    }

    private fun text(c: Canvas, x: Float, y: Float, t: String, size: Float, color: Int, tf: Typeface, alphaF: Float = 1f, rotate: Float = 0f) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size; this.color = alpha(color, alphaF); textAlign = Paint.Align.CENTER }
        val fm = paint.fontMetrics
        val base = y - (fm.ascent + fm.descent) / 2
        if (rotate != 0f) { c.save(); c.rotate(rotate, x, y) }
        c.drawText(t, x, base, paint)
        if (rotate != 0f) c.restore()
    }

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) =
        c.drawLine(x1, y1, x2, y2, Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = w; this.color = color })

    /** A. 둥근 눈금 (mockup `round`): minute track R 43.6, rotated 5-minute numerals, step r 36.3, capsules, 1–12. */
    private fun round(c: Canvas, g: RlGeo, p: RlPal, numerals: Boolean, tf: Typeface) {
        val u = g.u; val R = 43.6f
        stepCircle(c, g, p, 36.3f)
        for (i in 0 until 60) {
            if (i % 5 == 0) continue
            val (dx, dy) = dir(i * 6f)
            line(c, g.x(dx * R), g.y(dy * R), g.x(dx * (R - 2.6f)), g.y(dy * (R - 2.6f)), 0.55f * u, p.ink)
        }
        for (i in 5..60 step 5) {
            val (dx, dy) = dir(i * 6f); val r = R + 2.6f
            text(c, g.x(dx * r), g.y(dy * r), String.format(java.util.Locale.ROOT, "%02d", i), 2.3f * u, p.ink, tf, 0.85f, rotate = i * 6f)
        }
        for (h in 0 until 12) { val (dx, dy) = dir(h * 30f); val r = R - 3.3f; capsule(c, g, p, g.x(dx * r), g.y(dy * r), h * 30f, 6.6f, 2.15f) }
        if (numerals) for (h in 1..12) { val (dx, dy) = dir(h * 30f); text(c, g.x(dx * 29.6f), g.y(dy * 29.6f), h.toString(), 6.6f * u, p.ink, tf) }
    }

    /**
     * B. 입체 네모: everything follows the face's own rounded rectangle at the slot's aspect (no circle). From the edge in:
     * the minute track band (ticks perpendicular to the edge, 60/15/30/45 in the band), the 12 lume capsules where each
     * hour ray meets the capsule ring, 12/3/6/9 just inside them, a recessed rounded-rect step. Units: short side = 100.
     */
    const val SQ_EDGE = 3f // face edge -> track outer line
    private const val SQ_BAND = 3.4f // track band depth
    const val SQ_RING = SQ_EDGE + SQ_BAND + 4.5f // face edge -> capsule centres
    private const val SQ_STEP = 25f // face edge -> recessed step
    private const val SQ_R = 9f // track corner radius (inner rects shrink with their inset)

    /** Where the ray at [deg] meets the rounded rect (half [hw] x [hh], corner [r]); returns x, y and the outward normal. */
    private fun rrHit(deg: Float, hw: Float, hh: Float, r: Float): FloatArray {
        val (dx, dy) = dir(deg)
        val tx = hw / max(abs(dx), 1e-6f); val ty = hh / max(abs(dy), 1e-6f)
        val t = min(tx, ty)
        val px = dx * t; val py = dy * t
        if (r > 0f && abs(px) > hw - r && abs(py) > hh - r) {
            val ccx = sign(px) * (hw - r); val ccy = sign(py) * (hh - r)
            val dc = dx * ccx + dy * ccy
            val tc = dc + sqrt(max(0f, dc * dc - (ccx * ccx + ccy * ccy - r * r)))
            val qx = dx * tc; val qy = dy * tc
            return floatArrayOf(qx, qy, (qx - ccx) / r, (qy - ccy) / r)
        }
        return if (tx <= ty) floatArrayOf(px, py, sign(dx), 0f) else floatArrayOf(px, py, 0f, sign(dy))
    }

    /** Recessed rounded-rect step: same lip as [stepCircle] (shade above, light below). */
    private fun stepRect(c: Canvas, g: RlGeo, p: RlPal, hw: Float, hh: Float, r: Float) {
        val u = g.u
        val rect = RectF(g.x(-hw), g.y(-hh), g.x(hw), g.y(hh))
        if (p.night || p.glow) {
            c.drawRoundRect(rect, r * u, r * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, 0.25f * u); color = alpha(p.ink, 0.18f) })
            return
        }
        val lip = LinearGradient(0f, rect.top, 0f, rect.bottom, intArrayOf(alpha(BLACK, 0.26f), alpha(BLACK, 0f), alpha(WHITE, 0f), alpha(WHITE, 0.7f)), floatArrayOf(0f, 0.5f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(rect, r * u, r * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(BLACK, 0.045f) })
        c.drawRoundRect(rect, r * u, r * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.8f * u; shader = lip })
        // soft inner shadow under the lip (mockup `blur` 0.6)
        val inner = RectF(rect.left + 0.6f * u, rect.top + 0.6f * u, rect.right - 0.6f * u, rect.bottom - 0.6f * u)
        val ir = max(1.5f, r - 0.6f) * u
        c.drawRoundRect(inner, ir, ir, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.2f * u; color = alpha(BLACK, 0.08f)
            maskFilter = BlurMaskFilter(max(0.5f, 1.2f * u), BlurMaskFilter.Blur.NORMAL)
        })
    }

    private fun square(c: Canvas, g: RlGeo, p: RlPal, nums: SquareNumerals, fonts: RlFonts) {
        val tf = fonts.regular
        val u = g.u
        // capsule/numeral unit: a tall slot gets slightly bigger capsules and numerals than its short side alone gives
        // capped at 1.3 x the layout unit, so a very long slot cannot push 3/9 into the centre
        val kc = min(1.3f, max(min(g.hw, g.hh), max(g.hw, g.hh) * 0.42f) / 50f)
        val tw = g.hw - SQ_EDGE; val th = g.hh - SQ_EDGE
        val iw = tw - SQ_BAND; val ih = th - SQ_BAND; val ir = SQ_R - SQ_BAND
        val outerR = RectF(g.x(-tw), g.y(-th), g.x(tw), g.y(th))
        val innerR = RectF(g.x(-iw), g.y(-ih), g.x(iw), g.y(ih))
        // recessed track band: the ring between the frame lines a touch darker, a lip on its outer edge, a light line
        // under its inner edge
        if (!p.night) {
            val ring = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addRoundRect(outerR, SQ_R * u, SQ_R * u, Path.Direction.CW); addRoundRect(innerR, ir * u, ir * u, Path.Direction.CW)
            }
            c.drawPath(ring, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(BLACK, 0.06f) })
            if (!p.glow) {
                val lipR = RectF(outerR.left + 0.3f * u, outerR.top + 0.3f * u, outerR.right - 0.3f * u, outerR.bottom - 0.3f * u)
                val lip = LinearGradient(0f, lipR.top, 0f, lipR.bottom, intArrayOf(alpha(BLACK, 0.26f), alpha(BLACK, 0f), alpha(WHITE, 0f), alpha(WHITE, 0.7f)), floatArrayOf(0f, 0.5f, 0.5f, 1f), Shader.TileMode.CLAMP)
                c.drawRoundRect(lipR, SQ_R * u, SQ_R * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.5f * u; shader = lip })
                val hl = RectF(innerR).apply { offset(0f, 0.25f * u) }
                c.drawRoundRect(hl, ir * u, ir * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.35f * u; color = alpha(WHITE, 0.55f) })
            }
        }
        // frame: the track's outer and inner lines
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.25f * u; color = alpha(p.ink, 0.55f) }
        c.drawRoundRect(outerR, SQ_R * u, SQ_R * u, frame)
        frame.strokeWidth = 0.2f * u; frame.color = alpha(p.ink, 0.45f)
        c.drawRoundRect(innerR, ir * u, ir * u, frame)
        // minute ticks perpendicular to the edge; the quarter labels take the 0/15/30/45 places
        for (i in 0 until 60) {
            val q = rrHit(i * 6f, tw, th, SQ_R)
            if (i % 15 == 0) {
                val rot = when (i) { 15 -> 90f; 45 -> -90f; else -> 0f }
                text(c, g.x(q[0] - q[2] * SQ_BAND / 2), g.y(q[1] - q[3] * SQ_BAND / 2), if (i == 0) "60" else i.toString(), 2.6f * u, p.ink, tf, 0.85f, rotate = rot)
                continue
            }
            val five = i % 5 == 0
            val len = if (five) SQ_BAND else 2f
            line(c, g.x(q[0]), g.y(q[1]), g.x(q[0] - q[2] * len), g.y(q[1] - q[3] * len), (if (five) 0.55f else 0.3f) * u, p.ink)
        }
        // deeper centre step (under the capsules and numerals, so their shadows fall on it)
        val sw = g.hw - SQ_STEP; val sh = g.hh - SQ_STEP
        if (sw > 2f && sh > 2f) stepRect(c, g, p, sw, sh, min(6f, min(sw, sh)))
        // hour capsules on the ring, perpendicular to its edge; 12/3/6/9 per [nums] (숫자 없음: 12 doubled, 3/6/9 bigger)
        val rw = g.hw - SQ_RING; val rh = g.hh - SQ_RING; val rr = max(1f, SQ_R - (SQ_RING - SQ_EDGE))
        for (h in 0 until 12) {
            val q = rrHit(h * 30f, rw, rh, rr)
            val deg = (atan2(q[2], -q[3]) * 180f / PI.toFloat())
            val quarter = h % 3 == 0
            when {
                nums == SquareNumerals.NONE && h == 0 -> for (side in floatArrayOf(-1.6f, 1.6f)) {
                    // along the edge (the normal turned 90°)
                    capsule(c, g, p, g.x(q[0] - q[3] * side * kc), g.y(q[1] + q[2] * side * kc), deg, 6.4f * kc, 2.25f * kc)
                }
                nums == SquareNumerals.NONE && quarter -> capsule(c, g, p, g.x(q[0]), g.y(q[1]), deg, 8f * kc, 3f * kc)
                else -> capsule(c, g, p, g.x(q[0]), g.y(q[1]), deg, 6.4f * kc, 2.25f * kc)
            }
            if (quarter && nums != SquareNumerals.NONE) {
                val k = 7.8f * kc // mockup: numeral centres 7.8 kc inside the capsule ring
                val x = g.x(q[0] - q[2] * k); val y = g.y(q[1] - q[3] * k); val t = (if (h == 0) 12 else h).toString()
                if (nums == SquareNumerals.THIN) printedNumeral(c, g, p, x, y, t, 11.5f * kc, fonts.thin)
                else cutNumeral(c, g, p, x, y, t, kc, fonts.bold)
            }
        }
        // faint diagonal glass gloss over the whole face (off at night)
        if (!p.night) {
            val gloss = LinearGradient(0f, 0f, g.w.toFloat(), g.h.toFloat(),
                intArrayOf(alpha(WHITE, 0.14f), alpha(WHITE, 0f), alpha(WHITE, 0f), alpha(WHITE, 0.05f), alpha(WHITE, 0f)),
                floatArrayOf(0f, 0.35f, 0.62f, 0.7f, 0.78f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, g.w.toFloat(), g.h.toFloat(), Paint().apply { shader = gloss })
        }
    }

    /** Baseline that centres [t]'s glyphs (digits have no descender) on [y]. */
    private fun glyphBase(paint: Paint, t: String, y: Float): Float =
        y - android.graphics.Rect().also { paint.getTextBounds(t, 0, t.length, it) }.exactCenterY()

    /** 가는 숫자: Jost 300 printed in the dial's ink, no shadow. */
    private fun printedNumeral(c: Canvas, g: RlGeo, p: RlPal, x: Float, y: Float, t: String, size: Float, tf: Typeface) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = size * g.u; color = p.ink; textAlign = Paint.Align.CENTER }
        c.drawText(t, x, glyphBase(fill, t, y), fill)
    }

    /**
     * 파낸 야광 (mockup `sandwich`): the numeral cut through the dial onto a lume plate below. Inside the cut the dial's
     * wall throws a soft dark crescent (offset down-right); the cut edge is a darker line of the dial colour, with a thin
     * lit lower lip. Night: a plain fill in the night ink.
     */
    private fun cutNumeral(c: Canvas, g: RlGeo, p: RlPal, x: Float, y: Float, t: String, kc: Float, tf: Typeface) {
        val u = g.u
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = tf; textSize = 10f * kc * u; textAlign = Paint.Align.CENTER; color = p.ink }
        val base = glyphBase(paint, t, y)
        if (p.night) { c.drawText(t, x, base, paint); return }
        val dark = Color(p.dial).luminance() < 0.18f
        val glyph = Path().also { paint.getTextPath(t, 0, t.length, x, base, it) }
        c.save()
        c.clipPath(glyph)
        c.drawPath(glyph, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (p.glow) p.lume else if (dark) 0xFFD7E6CF.toInt() else 0xFFEEF3E6.toInt() })
        c.save(); c.translate(0.45f * kc * u, 0.7f * kc * u)
        c.drawPath(glyph, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            // a narrow, lightly blurred wall: wider/softer fills the thin strokes and greys the lume out
            style = Paint.Style.STROKE; strokeWidth = 0.7f * kc * u; color = alpha(BLACK, 0.5f)
            maskFilter = BlurMaskFilter(max(0.5f, 0.25f * kc * u), BlurMaskFilter.Blur.NORMAL)
        })
        c.restore()
        c.restore()
        c.drawPath(glyph, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.45f * kc * u; color = shade(p.dial, if (dark) -0.08f else -0.32f) })
        if (!p.glow) {
            c.save(); c.translate(0.18f * kc * u, 0.28f * kc * u)
            c.drawPath(glyph, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 0.2f * kc * u; color = alpha(WHITE, 0.55f) })
            c.restore()
        }
    }

    // ---- C. 캡슐 디지털 ----

    /** Segment layout (mockup `digital`): 4 digits W 24 × H 46, stroke T 4.6, gap 5.2, colon 9, centred. */
    class Seg(val x: Float, val y: Float, val deg: Float, val len: Float)

    const val W = 24f; const val H = 46f; const val T = 4.6f; const val GAP = 5.2f; const val COLON = 9f
    private const val TOTAL = 4 * W + 3 * GAP + COLON - GAP

    fun digitX(i: Int): Float {
        val x0 = -TOTAL / 2
        return when (i) { 0 -> x0; 1 -> x0 + W + GAP; 2 -> x0 + 2 * W + GAP + COLON; else -> x0 + 3 * W + 2 * GAP + COLON }
    }

    /** The seven segments of the digit at unit x [x], top [top] (mockup `segGeom`), keyed a..g. */
    fun segments(x: Float, top: Float): Map<Char, Seg> {
        val h2 = (H - T) / 2
        return mapOf(
            'a' to Seg(x + W / 2, top + T / 2, 90f, W - T - 0.8f), 'g' to Seg(x + W / 2, top + H / 2, 90f, W - T - 0.8f),
            'd' to Seg(x + W / 2, top + H - T / 2, 90f, W - T - 0.8f),
            'f' to Seg(x + T / 2, top + T / 2 + h2 / 2, 0f, h2 - 0.8f), 'b' to Seg(x + W - T / 2, top + T / 2 + h2 / 2, 0f, h2 - 0.8f),
            'e' to Seg(x + T / 2, top + H / 2 + h2 / 2, 0f, h2 - 0.8f), 'c' to Seg(x + W - T / 2, top + H / 2 + h2 / 2, 0f, h2 - 0.8f),
        )
    }

    val SEGS = mapOf('0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc", '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcfgd", ' ' to "")

    /** Seconds scale 0..60 under the digits, ±70 units, 42 units below the row centre (mockup y 86). */
    const val SCALE_HALF = 70f
    const val SCALE_Y = 42f

    /**
     * Date window (mockup x 176, y 9 of 216 × 100): the top-right corner, but never farther from the digits than in the
     * mockup (68 units right, 35 up of the row centre), so a tall screen keeps it just above them. Portrait: centred,
     * 9 units above the hours.
     */
    fun dateBox(g: RlGeo): RectF {
        val u = g.u
        if (g.portrait) { val b = g.y(-RlGeo.PORT_TOP - 9f); return RectF(g.cx - 11f * u, b - 7f * u, g.cx + 11f * u, b) }
        val l = min(g.w - 40f * u, g.cx + 68f * u); val t = max(9f * u, g.cy - 35f * u)
        return RectF(l, t, l + 22f * u, t + 7f * u)
    }

    private fun capsuleStatic(c: Canvas, g: RlGeo, p: RlPal, ghost: Boolean, date: Boolean, tf: Typeface, shift: Float) {
        val u = g.u
        if (ghost) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (p.glow || p.night) alpha(0xFF6FE0CF.toInt(), 0.05f) else alpha(BLACK, 0.035f) }
            val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = max(1f, 0.14f * u); color = alpha(p.ink, if (p.glow || p.night) 0.25f else 0.16f) }
            for (i in 0 until 4) for (s in segments(g.digitX[i], g.digitY[i]).values) {
                c.save(); c.translate(g.x(s.x), g.y(s.y)); c.rotate(s.deg)
                val r = RectF(-T / 2 * u, -s.len / 2 * u, T / 2 * u, s.len / 2 * u)
                c.drawRoundRect(r, T / 2 * u, T / 2 * u, fill); c.drawRoundRect(r, T / 2 * u, T / 2 * u, edge)
                c.restore()
            }
        }
        // colon: two lume studs
        for (k in 0 until 2) {
            val x = g.x(g.studs[2 * k] + shift); val y = g.y(g.studs[2 * k + 1])
            if (p.night) { c.drawCircle(x, y, 2f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.lume; if (!p.glow) { style = Paint.Style.STROKE; strokeWidth = max(1f, 0.25f * u) } }); continue }
            if (p.shadows) castShadow(c, u, S_I) { sp -> c.drawCircle(x, y, 2f * u, sp) }
            c.drawCircle(x, y, 2f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = silver(x - 2f * u, y - 2f * u, x + 2f * u, y + 2f * u, p.glow) })
            c.drawCircle(x, y, 1.45f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = lumeFill(x - 1.45f * u, x + 1.45f * u, p) })
        }
        // seconds scale
        for (i in 0..60) {
            val x = g.x(-g.scaleHalf + 2 * g.scaleHalf * i / 60f); val y = g.y(g.scaleY)
            val ten = i % 10 == 0; val five = i % 5 == 0
            line(c, x, y, x, y - (if (ten) 3f else if (five) 2f else 1.2f) * u, (if (ten) 0.45f else 0.25f) * u, alpha(p.ink, 0.85f))
            if (ten) text(c, x, y + 2.6f * u, i.toString(), 2.2f * u, p.ink, tf, 0.8f)
        }
        // date window frame, cut into the dial
        if (date) {
            val b = dateBox(g)
            if (p.shadows) castShadow(c, u, S_I) { sp -> c.drawRoundRect(b, u, u, sp) }
            c.drawRoundRect(b, u, u, Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = silver(b.left, b.top, b.right, b.bottom, p.glow || p.night) })
            val inner = RectF(b.left + 0.5f * u, b.top + 0.5f * u, b.right - 0.5f * u, b.bottom - 0.5f * u)
            c.drawRoundRect(inner, 0.7f * u, 0.7f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (p.glow || p.night) 0xFF081010.toInt() else 0xFFFBFAF6.toInt() })
            if (p.shadows) c.drawRoundRect(RectF(inner.left, inner.top, inner.right, inner.top + 1.6f * u), 0.7f * u, 0.7f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = alpha(BLACK, 0.14f) })
        }
    }
}

// ---- per-tick layers ------------------------------------------------------------------------------------------------

/**
 * A/B hands (mockup `hands`): rimmed silver + lume hour/minute hands with a centre ridge, the accent second hand with a
 * counterweight, a silver dome pivot. Paths, shaders and shadow bitmaps are made once per size + palette; [draw] only
 * rotates the canvas.
 */
private class RlHands(private val g: RlGeo, private val p: RlPal) {
    private val lens = when (g.kind) {
        // 입체 네모: minute to just short of the capsule ring on the short axis, hour 60 % of it, second into the track
        RlKind.SQUARE -> min(g.hw, g.hh).let { sh -> val m = sh - RlStatic.SQ_RING - 3f - 1.5f; Triple(0.6f * m, m, sh - RlStatic.SQ_EDGE - 2f) }
        else -> Triple(21.5f, 36.5f, 41f)
    }

    private inner class Hand(len: Float, w: Float, tail: Float, val shadow: RlShadow) {
        val u = g.u; val cx = g.cx; val cy = g.cy
        val outer = Path().apply {
            moveTo(cx - w * 0.45f * u, cy + tail * u); lineTo(cx - w / 2 * u, cy - 3 * u); lineTo(cx - w * 0.42f * u, cy - (len - w) * u)
            quadTo(cx, cy - (len + w * 0.9f) * u, cx + w * 0.42f * u, cy - (len - w) * u)
            lineTo(cx + w / 2 * u, cy - 3 * u); lineTo(cx + w * 0.45f * u, cy + tail * u); close()
        }
        private val iw = w - 1
        val inner = Path().apply {
            moveTo(cx - iw * 0.42f * u, cy - 4.5f * u); lineTo(cx - iw * 0.38f * u, cy - (len - w - 0.3f) * u)
            quadTo(cx, cy - (len + iw * 0.4f) * u, cx + iw * 0.38f * u, cy - (len - w - 0.3f) * u)
            lineTo(cx + iw * 0.42f * u, cy - 4.5f * u); close()
        }
        val silverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            if (p.night) { color = p.ink; style = if (p.glow) Paint.Style.FILL else Paint.Style.STROKE; strokeWidth = max(1f, 0.3f * u) }
            else shader = silver(cx - w / 2 * u, cy - len * u, cx + w / 2 * u, cy + tail * u, p.glow)
        }
        val lumePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { if (p.night) color = p.lume else shader = lumeFill(cx - iw / 2 * u, cx + iw / 2 * u, p) }
        val ridge = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 0.14f * u; color = alpha(WHITE, 0.65f) }
        val ridgeY0 = cy + (tail - 0.4f) * u; val ridgeY1 = cy - (len + w * 0.55f) * u
        val shade = if (p.shadows) RlShadowBitmap(g, shadow) { c, paint -> c.drawPath(outer, paint) } else null
        val glow = if (p.glow && !p.night) RlShadowBitmap(g, null, glowColor = p.lume) { c, paint -> c.drawPath(inner, paint) } else null

        fun draw(c: Canvas, deg: Float) {
            shade?.draw(c, deg)
            glow?.draw(c, deg)
            c.save(); c.rotate(deg, cx, cy)
            c.drawPath(outer, silverPaint)
            if (!(p.night && !p.glow)) c.drawPath(inner, lumePaint)
            if (p.shadows) c.drawLine(cx, ridgeY0, cx, ridgeY1, ridge)
            c.restore()
        }
    }

    private val hour = Hand(lens.first, 2.8f, 3.5f, S_H)
    private val minute = Hand(lens.second, 2.2f, 4.5f, S_M)

    private val u = g.u; private val cx = g.cx; private val cy = g.cy
    private val accPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.acc; strokeWidth = 0.34f * u; strokeCap = Paint.Cap.ROUND }
    private val secShade = if (p.shadows) RlShadowBitmap(g, S_S) { c, paint -> secondShape(c, paint) } else null
    private val dome = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = if (p.night) null else RadialGradient(cx - 0.6f * u, cy - 0.9f * u, 1.75f * u,
            if (p.glow) intArrayOf(0xFF3A4A47.toInt(), 0xFF121A1A.toInt()) else intArrayOf(WHITE, 0xFFC3C6CB.toInt(), 0xFF55595F.toInt()),
            if (p.glow) null else floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        if (p.night) color = p.ink
    }
    private val pin = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (p.glow || p.night) p.ink else 0xFF6F747A.toInt() }
    private val glint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (p.glow || p.night) 0xFF0E1718.toInt() else 0xFFF4F5F6.toInt() }

    private fun secondShape(c: Canvas, paint: Paint) {
        val s = Paint(paint).apply { strokeWidth = 0.34f * u; strokeCap = Paint.Cap.ROUND }
        c.drawLine(cx, cy + 8 * u, cx, cy - lens.third * u, s)
        c.drawCircle(cx, cy + 6.2f * u, 0.9f * u, paint)
        c.drawCircle(cx, cy, 1.7f * u, paint)
    }

    /** [h] 0..11, [m] 0..59, [s] seconds (fractional when sweeping). */
    fun draw(c: Canvas, h: Int, m: Int, s: Float) {
        val sec = s % 60f
        hour.draw(c, (h + (m + sec / 60f) / 60f) * 30f)
        minute.draw(c, (m + sec / 60f) * 6f)
        val sd = sec * 6f
        secShade?.draw(c, sd)
        c.save(); c.rotate(sd, cx, cy)
        c.drawLine(cx, cy + 8 * u, cx, cy - lens.third * u, accPaint)
        c.drawCircle(cx, cy + 6.2f * u, 0.9f * u, accPaint)
        c.drawCircle(cx, cy, 1.7f * u, accPaint)
        c.restore()
        c.drawCircle(cx, cy, 2.5f * u, dome)
        c.drawCircle(cx, cy, 1.1f * u, pin)
        c.drawCircle(cx - 0.35f * u, cy - 0.4f * u, 0.35f * u, glint)
    }
}

/**
 * A hand's cast shadow (two layers) or lume glow, blurred once into an ALPHA_8 bitmap at half resolution around the
 * pivot. [draw] rotates it with the hand and offsets it by a fixed screen distance (the light does not turn).
 */
private class RlShadowBitmap(private val g: RlGeo, private val s: RlShadow?, glowColor: Int = 0, shape: (Canvas, Paint) -> Unit) {
    private val scale = 0.5f
    private val half = (min(g.w, g.h) / 2f + 8f * g.u)
    private val size = (2 * half * scale).toInt().coerceAtLeast(1)
    private val wide: Bitmap
    private val tight: Bitmap?
    private val widePaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val tightPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val m = Matrix()

    init {
        fun render(blur: Float): Bitmap {
            val b = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8)
            val c = Canvas(b)
            c.scale(scale, scale); c.translate(half - g.cx, half - g.cy)
            shape(c, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BLACK; maskFilter = BlurMaskFilter(max(0.5f, blur), BlurMaskFilter.Blur.NORMAL) })
            return b
        }
        if (s != null) {
            wide = render(s.b * 1.6f * g.u * 2f); widePaint.color = alpha(BLACK, s.o * 0.8f)
            tight = render(s.b * 0.35f * g.u * 2f); tightPaint.color = alpha(BLACK, min(1f, s.o * 1.3f))
        } else {
            wide = render(0.7f * g.u * 2f); widePaint.color = alpha(glowColor, 0.6f); tight = null
        }
    }

    fun draw(c: Canvas, deg: Float) {
        val dx = s?.dx ?: 0f; val dy = s?.dy ?: 0f
        layer(c, wide, widePaint, deg, dx, dy)
        tight?.let { layer(c, it, tightPaint, deg, dx * 0.35f, dy * 0.35f) }
    }

    private fun layer(c: Canvas, b: Bitmap, paint: Paint, deg: Float, dx: Float, dy: Float) {
        m.reset()
        m.setScale(1 / scale, 1 / scale)
        m.postTranslate(g.cx - half, g.cy - half)
        m.postRotate(deg, g.cx, g.cy)
        m.postTranslate(dx * g.u, dy * g.u)
        c.drawBitmap(b, m, paint)
    }
}

/**
 * C's lit segments: one horizontal and one vertical capsule (with its shadow or glow) rendered once per size + palette,
 * stamped at each lit segment. The date text uses one cached paint.
 */
private class RlLit(private val g: RlGeo, private val p: RlPal, tfDate: Typeface) {
    private val pad = (4 * g.u).toInt()
    private fun stamp(deg: Float, len: Float): Bitmap {
        val vertical = deg == 0f
        val bw = ((if (vertical) RlStatic.T else len) * g.u).toInt() + 2 * pad
        val bh = ((if (vertical) len else RlStatic.T) * g.u).toInt() + 2 * pad
        return Bitmap.createBitmap(bw.coerceAtLeast(1), bh.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also {
            capsule(Canvas(it), g, p, bw / 2f, bh / 2f, deg, len, RlStatic.T)
        }
    }
    private val segs = Array(4) { RlStatic.segments(g.digitX[it], g.digitY[it]) }
    private val horiz = segs[0].getValue('a').let { stamp(it.deg, it.len) }
    private val vert = segs[0].getValue('b').let { stamp(it.deg, it.len) }
    private val paint = Paint()
    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = tfDate; textSize = 3.2f * g.u; textAlign = Paint.Align.CENTER
        color = if (p.glow || p.night) p.ink else 0xFF1D1D1D.toInt()
    }
    private val dateBox = RlStatic.dateBox(g)
    private val dateBase = dateBox.top + 3.7f * g.u - datePaint.fontMetrics.let { (it.ascent + it.descent) / 2 }

    /** [digits] = 4 chars (' ' = blank), [day] null = no date. */
    fun draw(c: Canvas, digits: String, day: String?, shift: Float) {
        for (i in 0 until 4) {
            val lit = RlStatic.SEGS[digits[i]] ?: continue
            for (k in lit) {
                val s = segs[i].getValue(k)
                val b = if (s.deg == 0f) vert else horiz
                c.drawBitmap(b, Math.round(g.x(s.x + shift) - b.width / 2f).toFloat(), Math.round(g.y(s.y) - b.height / 2f).toFloat(), paint)
            }
        }
        if (day != null) c.drawText(day, dateBox.centerX(), dateBase, datePaint)
    }
}

/** C's seconds capsule on the scale (accent), its shadow made once; [draw] only moves it. */
private class RlMarker(private val g: RlGeo, private val p: RlPal) {
    private val u = g.u
    private val pad = (4 * u).toInt()
    private val bw = (1.6f * u).toInt() + 2 * pad; private val bh = (5.2f * u).toInt() + 2 * pad
    private val bmp: Bitmap = Bitmap.createBitmap(bw.coerceAtLeast(1), bh.coerceAtLeast(1), Bitmap.Config.ARGB_8888).also { b ->
        val c = Canvas(b)
        val r = RectF(pad.toFloat(), pad.toFloat(), pad + 1.6f * u, pad + 5.2f * u)
        if (p.shadows) castShadow(c, u, S_M) { sp -> c.drawRoundRect(r, 0.8f * u, 0.8f * u, sp) }
        c.drawRoundRect(r, 0.8f * u, 0.8f * u, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.acc })
    }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun draw(c: Canvas, s: Float) {
        val x = g.x(-g.scaleHalf + 2 * g.scaleHalf * (s % 60f) / 60f) - 0.8f * u - pad
        val y = g.y(g.scaleY - 4.4f) - 2.6f * u - pad
        c.drawBitmap(bmp, x, y, paint)
    }
}
