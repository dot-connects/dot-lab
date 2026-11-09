package kr.dot.sb.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
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
) {
    /** Map any day colour (calendar colours, brand glyphs, user colours) to its night twin. */
    fun mapped(day: Color): Color = lerp(day, NIGHT_INK2, night)

    /** User accent through the night ramp: full red at night. */
    fun accent(day: Color): Color = lerp(day, NIGHT_INK, night)

    companion object {
        val NIGHT_INK = Color(0xFFFC0000)
        val NIGHT_INK2 = Color(0xFFA80000)
        val NIGHT_INK3 = Color(0xFF3A0000)
        val NIGHT_INK4 = Color(0xFF1A0000)

        fun of(night: Float, accent: Color): Ink {
            val n = night.coerceIn(0f, 1f)
            return Ink(
                night = n,
                bg = Color.Black,
                ink = lerp(Color.White, NIGHT_INK, n),
                ink2 = lerp(Color(0xFF98989F), NIGHT_INK2, n),
                ink3 = lerp(Color(0xFF3A3A3C), NIGHT_INK3, n),
                ink4 = lerp(Color(0xFF1C1C1E), NIGHT_INK4, n),
                red = lerp(Color(0xFFFF453A), NIGHT_INK2, n),
                acc = lerp(accent, NIGHT_INK, n),
                accDeep = lerp(Hsl.deep(accent), NIGHT_INK2, n),
                accLight = lerp(Hsl.light(accent), NIGHT_INK, n),
                accDim = lerp(accent.copy(alpha = 0.45f), NIGHT_INK3, n),
            )
        }
    }
}

val LocalInk = compositionLocalOf { Ink.of(0f, Color(0xFF76A6F3)) }

/** Derived accent shades (design-system §2.1: accDeep = S+0.35, L−0.25 floor 0.15; accLight = L+0.12). */
object Hsl {
    private fun toHsl(c: Color): FloatArray {
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
        fun of(w: Dp, h: Dp, labelOverride: Dp? = null): Sizes {
            val s = min(w, h)
            return Sizes(
                s = s,
                display = max(s * 0.56f, 64.dp).coerceAtMost(s * 0.9f),
                value = max(s * 0.30f, 40.dp).coerceAtMost(s * 0.6f),
                title = max(s * 0.13f, 22.dp).coerceAtMost(s * 0.3f),
                label = labelOverride ?: max(s * 0.085f, 18.dp).coerceAtMost(s * 0.2f),
                pad = max(s * 0.07f, 12.dp),
            )
        }
    }
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
