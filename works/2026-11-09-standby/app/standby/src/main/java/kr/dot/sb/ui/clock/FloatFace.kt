package kr.dot.sb.ui.clock

import android.graphics.BlendMode
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kr.dot.sb.model.ClockLayout
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FontId
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.Motion
import kotlin.math.min

private class Holder(var v: String)

/**
 * Float (design-system §4.3, spec C7): heavy rounded digits overlapping with Screen blend, alternating tilt, centre
 * digit accDeep, colon = two discs on top. Changed digits roll for 400 ms (old up + tilt + fade, new from below).
 */
@Composable
internal fun FloatFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier, hero: Boolean = true) {
    val p = rememberTimePainter(style)
    val look = Look.of(style, ink)
    // A second clock on a board is plain ink: no deep centre digit (§5.1).
    val deep = if (hero && look.effect == Effect.SOLID) Look(Effect.SOLID, ink.accDeep.toArgb(), look.c2) else look
    val digits = d.h + d.m
    val last = remember { Holder(digits) }
    val from = remember(digits) { last.v.also { last.v = digits } }
    val roll = remember(digits) { Animatable(if (from == digits || from.length != digits.length) 1f else 0f) }
    LaunchedEffect(digits) { roll.animateTo(1f, tween(Motion.FLOAT_ROLL_MS, easing = FastOutSlowInEasing)) }
    val bgStyle = bgColors(style, ink)
    val lightPage = ink.tokens.light && ink.night < 0.5f
    val mix = if (lightPage) BlendMode.MULTIPLY else BlendMode.SCREEN
    val colon = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TimePainter.withAlpha(ink.ink2.toArgb(), 0.85f)
        blendMode = mix
    }
    val screen = Paint().apply { blendMode = if (style.font == FontId.RAILS) BlendMode.SRC_OVER else mix }
    val fade = Paint()

    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val two = (if (inSlot) h > w * 1.15f else w < h * 1.45f) || style.layout == ClockLayout.TWO_LINES
        val sizePct = style.sizePct / 100f
        val a = p.digitAdv.max()
        // In em: step inside a group overlaps by 0.12 em; across the colon the digits just touch (frame "5:22").
        val inStep = a - 0.12f; val crossStep = a + 0.02f
        // Width of the current hour-digit class (§2.3.1): 3 digits for 1:00-9:59, 4 otherwise; changes only with the class.
        val lineEm = if (two) (d.h.length.coerceAtLeast(2) - 1) * inStep + a else (digits.length - 2) * inStep + crossStep + a
        val maxCap = (if (inSlot) (if (two) 0.42f else 0.8f) * h else (if (two) 0.36f else 0.66f) * h) * sizePct
        // full screen: the board content width (margins M), like Digital (review X1)
        val maxW = if (inSlot) 0.9f * w else w - 2 * Grid.margin(min(w, h).toDp()).toPx()
        val fs = min(maxCap / p.capEm, maxW / lineEm)
        val cap = p.capEm * fs
        val gap = 0.02f * h
        val hl = d.h.length
        // slot left edges (px) and line of each digit
        val xs = FloatArray(digits.length); val line = IntArray(digits.length)
        if (two) {
            for ((li, s) in listOf(d.h, d.m).withIndex()) {
                val lw = ((s.length - 1) * inStep + a) * fs
                for (k in s.indices) { val i = if (li == 0) k else hl + k; xs[i] = (w - lw) / 2 + k * inStep * fs; line[i] = li }
            }
        } else {
            val lw = ((digits.length - 2) * inStep + crossStep + a) * fs
            var x = (w - lw) / 2
            for (i in digits.indices) { xs[i] = x; x += (if (i == hl - 1) crossStep else inStep) * fs }
        }
        val top = (h - (if (two) 2 * cap + gap else cap)) / 2
        val discR = min(0.025f * w, 0.07f * fs)
        val colonX = if (two) 0f else (xs[hl - 1] + a * fs + xs[hl]) / 2
        val rollDist = cap * 0.9f
        return@FaceCanvas {
            drawFaceBg(if (inSlot) null else bgStyle, w, h, 0f)
            saveLayer(0f, 0f, w, h, null)
            val pr = roll.value
            for (i in digits.indices) {
                val capTop = top + line[i] * (cap + gap)
                val base = capTop + cap
                val cx = xs[i] + a * fs / 2; val cy = capTop + cap / 2
                val tilt = if (i % 2 == 0) 3f else -3f
                val lk = if (i == 1 && !two) deep else look
                fun one(ch: Char, dy: Float, extra: Float, alpha: Float) {
                    if (alpha <= 0.01f) return
                    fade.alpha = (alpha * 255).toInt()
                    screen.alpha = fade.alpha
                    save()
                    rotate(tilt + extra, cx, cy + dy)
                    saveLayer(xs[i] - fs, capTop - fs + dy, xs[i] + 2 * fs, base + fs + dy, screen)
                    val s = ch.toString()
                    p.draw(this, s, cx - p.measure(s, fs) / 2, base + dy, fs, lk)
                    restore(); restore()
                }
                if (pr < 1f && from[i] != digits[i]) {
                    one(from[i], -rollDist * pr, -8f * pr, 1f - pr)
                    one(digits[i], rollDist * (1f - pr), 0f, pr)
                } else one(digits[i], 0f, 0f, 1f)
            }
            if (!two) {
                drawCircle(colonX, top + cap * 0.27f, discR, colon)
                drawCircle(colonX, top + cap * 0.71f, discR, colon)
            }
            restore()
        }
    }
}
