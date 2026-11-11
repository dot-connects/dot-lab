package kr.dot.sb.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.max as fmax
import kotlin.math.min as fmin

/**
 * design-system.md §2 as code. Every colour on a StandBy surface comes from [Ink] (day/night twins lerped by the
 * single night fraction); every size from [TypeScale] (slot-relative with floors). Do not invent colours or sizes
 * in screens/cards — add a token here instead.
 */
@Immutable
data class Ink(
    val night: Float,
    val bg: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val red: Color,
    /** Board accent (hero clock colour) after night mapping. */
    val acc: Color,
    val accDeep: Color,
    val accLight: Color,
    val accDim: Color,
    /** The theme's legacy palette (family code only; goes in Task 22). */
    val pal: Palette = Palette.IPHONE,
    /** Day tokens of the theme; role colours below lerp from them to the candle night. */
    val tokens: ThemeTokens = ThemeTokens.IPHONE,
    /** What colours bypassing [Look] are checked against (D4): the page stops, or a clock's own background. */
    val page: List<Color> = listOf(bg),
) {
    private val nt: ThemeTokens by lazy { tokens.night() }
    private fun role(day: Color, nightC: Color) = lerp(day, nightC, night)
    private fun step(day: Color, nightC: Color) =
        if (tokens.light) (if (night >= 0.5f) nightC else day) else lerp(day, nightC, night)

    val weekday: Color get() = safe(role(tokens.roles.red, nt.roles.red))
    val orange: Color get() = role(tokens.roles.orange, nt.roles.orange)
    val sun: Color get() = role(tokens.roles.yellow, nt.roles.yellow)
    val green: Color get() = role(tokens.roles.green, nt.roles.green)
    val blue: Color get() = role(tokens.roles.blue, nt.roles.blue)
    val amber: Color get() = role(tokens.roles.amber, nt.roles.amber)
    val snooze: Color get() = role(tokens.roles.snooze, nt.roles.snooze)
    val music: Color get() = role(tokens.roles.music, nt.roles.music)
    val lilac: Color get() = role(tokens.roles.lilac, nt.roles.lilac)
    /** Plates step to the candle plate at night ≥ 0.5 on light themes (a lerp would pass through mid grey). */
    val plate: Color get() = step(tokens.plate, nt.plate)
    val plate2: Color get() = step(tokens.plate2, nt.plate2)
    /** Text accent (D12) through the night ramp. */
    val accText: Color get() = safe(lerp(tokens.accentText, nt.accentText, night))

    /** Map any day colour (calendar colours, brand glyphs, user colours) to its night twin. */
    fun mapped(day: Color): Color = safe(lerp(day, NIGHT_DIM, night))

    /** User accent through the night ramp: full candle ink at night. */
    fun accent(day: Color): Color = safe(lerp(day, ThemeTokens.NIGHT_INK, night))

    /** Below 3:1 on the weakest page stop → the theme ink (D4 safety net for colours that bypass [Look]). */
    fun safe(c: Color): Color = Contrast.safe(c, page, ink)

    companion object {
        /** Candle ink at the night ink2 alpha over black: dim marks and big areas at night. */
        val NIGHT_DIM = ThemeTokens.NIGHT_INK.copy(alpha = 0.58f).compositeOver(Color.Black)
        /** Candle plate over black (opaque): clock backgrounds at night. */
        val NIGHT_PLATE = ThemeTokens.NIGHT_INK.copy(alpha = 0x0F / 255f).compositeOver(Color.Black)

        /**
         * Colours of [theme] at night fraction [night]. Dark themes lerp to the candle night; light themes lerp ink but
         * step bg/plates to black at 0.5. [accent] null = the theme accent; [page] overrides the contrast backdrop
         * (a clock with its own background).
         */
        fun of(night: Float, accent: Color? = null, theme: StandbyTheme = Themes.IPHONE, page: List<Color>? = null): Ink {
            val n = night.coerceIn(0f, 1f)
            val t = theme.tokens
            val nt = t.night()
            val a = accent ?: t.accent
            val dark = t.light && n >= 0.5f
            val bg = if (t.light) (if (dark) nt.bg.base else t.bg.base) else lerp(t.bg.base, nt.bg.base, n)
            val stops = if (t.light) (if (dark) nt.bg.stops else t.bg.stops) else t.bg.stops.map { lerp(it, nt.bg.base, n) }
            val inkC = lerp(t.ink, nt.ink, n)
            fun alpha(al: Float) = inkC.copy(alpha = al).compositeOver(bg)
            val plate2 = if (t.light) (if (dark) nt.plate2 else t.plate2) else lerp(t.plate2, nt.plate2, n)
            return Ink(
                night = n,
                bg = bg,
                ink = inkC,
                ink2 = alpha(t.ink2 + (nt.ink2 - t.ink2) * n),
                ink3 = alpha(t.ink3 + (nt.ink3 - t.ink3) * n),
                ink4 = plate2.compositeOver(bg),
                red = lerp(t.roles.red, nt.roles.red, n),
                acc = lerp(a, nt.accent, n),
                accDeep = lerp(Hsl.deep(a), NIGHT_DIM, n),
                accLight = lerp(Hsl.light(a), nt.accent, n),
                accDim = lerp(a.copy(alpha = 0.45f), nt.ink.copy(alpha = 0.3f), n),
                pal = theme.palette,
                tokens = t,
                page = page ?: stops,
            )
        }
    }
}

val LocalInk = compositionLocalOf { Ink.of(0f) }

/** The board-wide label size of the slot a clock face sits in (null = not in a card): its date line uses it. */
val LocalSlotLabel = compositionLocalOf<Dp?> { null }

/** Derived accent shades (design-system §2.1: accDeep = S+0.35, L−0.25 floor 0.15; accLight = L+0.12). */
object Hsl {
    internal fun toHsl(c: Color): FloatArray {
        val r = c.red; val g = c.green; val b = c.blue
        val mx = fmax(r, fmax(g, b)); val mn = fmin(r, fmin(g, b))
        val l = (mx + mn) / 2f
        var h = 0f; var s = 0f
        if (mx != mn) {
            val d = mx - mn
            s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
            h = when (mx) { r -> (g - b) / d + (if (g < b) 6f else 0f); g -> (b - r) / d + 2f; else -> (r - g) / d + 4f } / 6f
        }
        return floatArrayOf(h * 360f, s, l)
    }

    private fun of(h: Float, s: Float, l: Float, a: Float) = Color.hsl(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f), a)

    fun deep(c: Color): Color { val (h, s, l) = toHsl(c); return of(h, s + 0.35f, fmax(0.15f, l - 0.25f), c.alpha) }
    fun light(c: Color): Color { val (h, s, l) = toHsl(c); return of(h, s, l + 0.12f, c.alpha) }
}

/**
 * Text roles sized from the slot short side `s` (design-system §2.3). Use [Sizes.of] with the slot size.
 * StandBy surfaces ignore the system font scale: convert dp to sp with [dpToSp].
 */
@Immutable
data class Sizes(
    val s: Dp,
    val display: Dp,
    val value: Dp,
    val title: Dp,
    val label: Dp,
    val pad: Dp,
) {
    companion object {
        fun of(w: Dp, h: Dp, labelOverride: Dp? = null, r: Ramp = Ramp.IPHONE): Sizes {
            val s = min(w, h)
            return Sizes(
                s = s,
                display = max(s * r.display, 64.dp).coerceAtMost(s * 0.9f),
                value = max(s * r.value, 40.dp).coerceAtMost(s * 0.6f),
                title = max(s * r.title, 22.dp).coerceAtMost(s * 0.3f),
                label = labelOverride ?: max(s * r.label, 18.dp).coerceAtMost(s * 0.2f),
                pad = max(s * r.pad, 12.dp),
            )
        }
    }
}

/**
 * Reading text (r7, operator 2026-11-11 "글씨가 너무 커"): sentences in notifications and media titles are sized from
 * the screen short side [screenShort], not the slot, so they stay phone-reading sized while glance text (clock date,
 * weather, D-day) keeps the slot ramp. Hangul fills ~0.88 em against 0.71 for a Latin cap, so the iPhone ratios read
 * about 25 % bigger in Korean; these are the corrected sizes.
 */
object Reading {
    /** Body sentences: 0.04 × short side, 16–20 dp (r8 "아직도 크네": one more step down). */
    fun read(screenShort: Dp): Dp = (screenShort * 0.04f).coerceIn(16.dp, 20.dp)
    /** App name, time: never above [read]. */
    fun meta(read: Dp): Dp = max(min(14.dp, read), read * 0.83f)
    /** Sender, list header: one step up from [read], bold. */
    fun head(read: Dp): Dp = read * 1.2f
}

/** Full-screen overlays (Now Playing, big notification), sized from the screen short side [s] (r7). */
object OverlayType {
    fun title(s: Dp): Dp = max(18.dp, s * 0.052f)
    fun sub(s: Dp): Dp = max(15.dp, s * 0.04f)
    fun meta(s: Dp): Dp = max(13.dp, s * 0.034f)
    fun body(s: Dp): Dp = max(16.dp, s * 0.044f)
    /** Play button: from the screen, not from the title, so a smaller title keeps a big target. */
    fun play(s: Dp): Dp = max(52.dp, s * 0.13f)
}

/** Size class of a slot (design-system §4.1). */
enum class SizeClass { XS, S, M, L;
    companion object {
        fun of(s: Dp): SizeClass = when {
            s < 176.dp -> XS
            s < 280.dp -> S
            s < 400.dp -> M
            else -> L
        }
    }
}

/** dp → sp that ignores the user's font scale (StandBy text is slot-sized). */
@Composable
fun dpToSp(dp: Dp): TextUnit = with(LocalDensity.current) { (dp.value / fontScale).sp }

/** Board margin and gutter from the screen short side (design-system §2.4). */
object Grid {
    private fun round4(v: Float) = (Math.round(v / 4f) * 4).toFloat()
    fun margin(screenShort: Dp): Dp = fmax(16f, round4(screenShort.value * 0.06f)).dp
    fun gutter(screenShort: Dp): Dp = fmax(8f, round4(screenShort.value * 0.04f)).dp
}

/** Motion constants (design-system §2.5). */
object Motion {
    const val FLOAT_ROLL_MS = 400
    const val NOTIF_ICON_IN_MS = 300
    const val NOTIF_HOLD_MS = 900
    const val NOTIF_SHRINK_MS = 350
    const val NOTIF_TEXT_MS = 300
    const val NOTIF_CLOSE_MS = 6000
    const val LAYER_FADE_MS = 350
    const val DOTS_HIDE_MS = 1500
    const val NIGHT_IN_MS = 1000
    const val NIGHT_OUT_MS = 600
}
