package kr.dot.sb.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import kr.dot.sb.data.BurnIn
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.Border
import kr.dot.sb.model.Texture
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FontId
import kr.dot.sb.ui.clock.StyledTime
import kr.dot.sb.ui.clock.rememberTimePainter

/*
 * r9 primitives (plan-r9 Task 5): the only readers of the token style switches (PlateStyle, DigitStyle, BgSpec).
 * Cards, faces and overlays call these instead of branching on the theme. Values follow the mockup page's "테마 전체"
 * and "배경 · 글자" tabs (spec-r9.md).
 */

/** Plates on a user-picked board background are translucent so the background shows through (mockup: white 7 %). */
private val PLATE_ON_CUSTOM = Color(0x12FFFFFF)

/** Dark wash over a blurred photo background, so plates and ink keep their contrast. */
val SCRIM = Color(0x73000000)

/**
 * The theme's plate under a card. FILL/GLASS = filled plate, RULE = hairline on top (DAYPAPER, TERMINAL), OUTLINE = 1.5 dp line,
 * with a soft glow when the digits glow (NEON). [custom] = the board has its own background (BoardBg ≠ THEME): plates
 * turn translucent. Night ≥ 0.5 = the candle plate, effects off.
 */
fun Modifier.themedSurface(t: StandbyTheme, ink: Ink, shape: Shape, custom: Boolean = false): Modifier {
    val tk = t.tokens
    if (ink.night >= 0.5f) return background(ink.plate, shape)
    // r13 effects (border, plate texture, a set text glow) fade with the dusk ramp: plan-r13 A3
    val fade = effectFade(ink.night)
    val plate = when (tk.plateStyle) {
        PlateStyle.FILL, PlateStyle.GLASS -> background(if (custom) PLATE_ON_CUSTOM else ink.plate, shape)
        PlateStyle.RULE -> drawBehind {
            drawLine(tk.rule ?: ink.ink.copy(alpha = 0.25f), Offset.Zero, Offset(size.width, 0f), maxOf(1f, 1.dp.toPx()))
        }
        PlateStyle.OUTLINE -> {
            val glow = tk.digitStyle == DigitStyle.GLOW
            // NEON's tube line: ink at 90 % (plan-r13 Part D)
            val line = tk.borderColor ?: if (glow) ink.ink.copy(alpha = 0.9f) else ink.ink.copy(alpha = 0.35f)
            // the theme's own halo (NEON) as before; a set text glow also fades with night
            val amt = tk.haloAmt * (if (tk.textGlow != null) fade else 1f)
            val halo = if (amt <= 0f) this else drawBehind {
                val o = shape.createOutline(size, layoutDirection, this)
                drawOutline(o, line.copy(alpha = (0.08f * amt).coerceIn(0f, 1f)), style = Stroke(10.dp.toPx()))
                drawOutline(o, line.copy(alpha = (0.16f * amt).coerceIn(0f, 1f)), style = Stroke(5.dp.toPx()))
            }
            halo.border(1.5.dp, line, shape)
        }
    }
    val textured = plate.texture(tk.cardTexture, (tk.cardTextureAmt ?: tk.textureAmt) * fade, shape)
    if (tk.border == Border.NONE || tk.plateStyle == PlateStyle.OUTLINE) return textured
    val c = tk.borderColor ?: ink.ink.copy(alpha = 0.35f)
    return textured.border(if (tk.border == Border.THICK) 2.5.dp else 1.dp, c.copy(alpha = c.alpha * fade), shape)
}

/** r13: how much of a day-only effect shows at [night]: 1 − night, gone from 0.5 (the candle night). */
internal fun effectFade(night: Float): Float = if (night >= 0.5f) 0f else 1f - night.coerceIn(0f, 1f)

/** [k] tiled over this element at [alpha], clipped to [shape] (plan-r13 A3); unchanged for NONE or alpha 0. */
private fun Modifier.texture(k: Texture, alpha: Float, shape: Shape): Modifier {
    if (k == Texture.NONE || alpha <= 0f) return this
    return drawWithCache {
        val brush = Textures.brush(k, density)!!
        val clip = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawWithCache)) }
        onDrawBehind { clipPath(clip) { drawRect(brush, alpha = alpha) } }
    }
}

/**
 * Hero digits in the theme's treatment. [style] null = the theme default (FontId.THEME at the theme weight); GLOW
 * applies only when the style is the theme font with a plain effect (D4b), and never at night ≥ 0.5.
 * [twoLines] stacks hours over minutes ("9:41" → "9" / "41"). Autosized to the modifier's box; takes only the height
 * the digits need, so a wrap-height caller can put a line right under them.
 */
@Composable
fun ThemedDigits(
    text: String,
    t: StandbyTheme,
    ink: Ink,
    modifier: Modifier = Modifier,
    style: ClockStyle? = null,
    color: Color? = null,
    twoLines: Boolean = false,
) {
    val tk = t.tokens
    val base = style ?: ClockStyle(FontId.THEME, tk.digitWeight, color = 0xFFFFFFFF) // colour unused: [c] below
    val themed = base.font == FontId.THEME && base.effect == Effect.SOLID && ink.night < 0.5f
    val st = if (themed && tk.digitStyle == DigitStyle.GLOW) base.copy(effect = Effect.GLOW) else base
    val c = color ?: style?.let { Color(it.color) } ?: ink.acc
    val lines = if (twoLines) text.split(':').filter { it.isNotEmpty() } else listOf(text)
    val widest = lines.maxBy { it.length }
    val p = rememberTimePainter(st, tnum = true)
    // as tall as the digits need (StyledTime's fit), centred: a line under a wrap-height caller sits right below them
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val lineH = minOf(maxHeight / lines.size, maxWidth / p.widestEm(widest) * (p.capEm + p.descEm) / 0.9f)
        Column(Modifier.fillMaxWidth().height(lineH * lines.size)) {
            lines.forEach { StyledTime(it, st, ink, Modifier.fillMaxWidth().weight(1f), c, widest = widest, glow = tk.digitGlow) }
        }
    }
}

/** FLIP: each digit on its own split tile (mockup: tile .62 × .95 em, gap .06 em, colon .35 em in ink2). */
@Composable
internal fun FlipDigits(
    lines: List<String>, st: ClockStyle, ink: Ink, tk: ThemeTokens, c: Color, modifier: Modifier,
    plate: Color? = null, animate: Boolean = false,
) =
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        fun emW(l: String) = l.sumOf { if (it == ':') 0.35 else 0.62 }.toFloat() + (l.length - 1) * 0.06f
        val fs = minOf(maxWidth / lines.maxOf { emW(it) }, maxHeight / (lines.size * 0.95f + (lines.size - 1) * 0.06f))
        val top = plate ?: tk.plate2
        val bottom = if (plate == null) lerp(tk.plate, tk.plate2, 0.5f) else plate
        val split = if (plate == null) tk.bg.base else ink.bg
        val tile = Brush.verticalGradient(
            0f to top, 0.494f to top, 0.494f to split, 0.506f to split, 0.506f to bottom, 1f to bottom,
        )
        Column(verticalArrangement = Arrangement.spacedBy(fs * 0.06f), horizontalAlignment = Alignment.CenterHorizontally) {
            lines.forEach { l ->
                Row(horizontalArrangement = Arrangement.spacedBy(fs * 0.06f), verticalAlignment = Alignment.CenterVertically) {
                    l.forEachIndexed { index, ch ->
                        if (ch == ':') StyledTime(":", st, ink, Modifier.size(fs * 0.3f, fs * 0.5f), ink.ink2)
                        else {
                            val shown = remember(lines.size, index) { mutableStateOf(ch) }
                            val turn = remember(lines.size, index) { Animatable(0f) }
                            LaunchedEffect(ch, animate) {
                                if (animate && shown.value != ch) {
                                    turn.snapTo(0f)
                                    turn.animateTo(0.5f, tween(250))
                                    shown.value = ch
                                    turn.animateTo(1f, tween(250))
                                } else shown.value = ch
                            }
                            val angle = if (turn.value < 0.5f) turn.value * 180f else (turn.value - 1f) * 180f
                            Box(Modifier.size(fs * 0.62f, fs * 0.95f).graphicsLayer { rotationX = angle }.background(tile, RoundedCornerShape(fs * 0.1f))) {
                                StyledTime(shown.value.toString(), st, ink, Modifier.fillMaxSize().padding(fs * 0.05f, fs * 0.1f), c)
                            }
                        }
                    }
                }
            }
        }
    }

/** One radial glow: CSS `radial-gradient(rx ry at x y, stops)`, everything as fractions of the page. */
private class Glow(val at: Offset, val size: Offset, val stops: List<Pair<Float, Color>>)

/** A page background: a vertical gradient of [stops] (one stop = solid) plus glows on top. */
private class Ground(val stops: List<Color>, val glows: List<Glow> = emptyList())

private fun c(v: Long) = Color(v)

/** Board background presets (mockup "배경 · 글자" → BGS); THEME, PHOTO_BLUR and SOLID are built in [ground]. */
private val PRESETS: Map<BoardBg, Ground> = mapOf(
    BoardBg.BLACK to Ground(listOf(c(0xFF000000))),
    BoardBg.GRAPHITE to Ground(listOf(c(0xFF08080A)), listOf(Glow(Offset(.5f, 0f), Offset(1.2f, .9f), listOf(0f to c(0xFF2A2A2E), .55f to c(0xFF111113), 1f to c(0xFF08080A))))),
    BoardBg.MIDNIGHT to Ground(listOf(c(0xFF03040C)), listOf(Glow(Offset(.2f, 0f), Offset(1.1f, 1f), listOf(0f to c(0xFF1D2B5C), .45f to c(0xFF0A1028), 1f to c(0xFF03040C))))),
    BoardBg.AMBER to Ground(listOf(c(0xFF080504)), listOf(Glow(Offset(1f, 1f), Offset(.9f, .8f), listOf(0f to c(0xFF5A2A0A), .4f to c(0xFF24110A), .8f to c(0xFF080504))))),
    BoardBg.AURORA to Ground(
        listOf(c(0xFF05060A)),
        listOf(
            Glow(Offset(.15f, .2f), Offset(.6f, .7f), listOf(0f to c(0x5930B0C7), .7f to c(0x0030B0C7))),
            Glow(Offset(.85f, .8f), Offset(.6f, .7f), listOf(0f to c(0x59AF52DE), .7f to c(0x00AF52DE))),
        ),
    ),
    // ponytail: one dusk set (r12 SKY's stops, frozen when r13 changed SKY); dawn/day/night sets once the operator picks them
    BoardBg.TIME_OF_DAY to Ground(listOf(c(0xFF1B2A4A), c(0xFF31355A), c(0xFF552D32))),
)

private fun BgSpec.ground(): Ground {
    fun glowOf(c: Color, at: Offset, size: Offset) = Glow(at, size, listOf(0f to c, .7f to c.copy(alpha = 0f)))
    // TWIN: the mirror in the first colour; r13 glow2: its own colour, place and size
    val first = glow?.let { g -> listOfNotNull(glowAt, glow2At.takeIf { glow2 == null }).map { glowOf(g, it, glowSize) } } ?: emptyList()
    val second = glow2?.let { listOf(glowOf(it, glow2At ?: glowAt, glow2Size ?: glowSize)) } ?: emptyList()
    return Ground(stops, first + second)
}

private fun ground(t: StandbyTheme, bg: BoardBg, custom: Color): Ground = when (bg) {
    BoardBg.THEME -> t.tokens.bg.ground()
    BoardBg.SOLID -> Ground(listOf(custom))
    // the photo is drawn as an image; this is what shows while there is none
    BoardBg.PHOTO_BLUR -> PRESETS.getValue(BoardBg.GRAPHITE)
    else -> PRESETS.getValue(bg)
}

private fun DrawScope.draw(g: Ground, alpha: Float = 1f) {
    if (g.stops.size == 1) drawRect(g.stops[0], alpha = alpha) else if (g.stops.size > 1) drawRect(Brush.verticalGradient(g.stops), alpha = alpha)
    for (gl in g.glows) {
        val ctr = Offset(gl.at.x * size.width, gl.at.y * size.height)
        val rx = gl.size.x * size.width
        val ry = gl.size.y * size.height
        val sx = rx / ry
        // an ellipse = a circle of radius ry stretched by rx/ry around the centre; the rect still covers the page
        scale(sx, 1f, pivot = ctr) {
            val brush = Brush.radialGradient(*gl.stops.toTypedArray(), center = ctr, radius = ry)
            drawRect(brush, topLeft = Offset(ctr.x - ctr.x / sx, 0f), size = size.copy(width = size.width / sx), alpha = alpha)
        }
    }
}

/** How far the burn-in shift moves the content layer ([BurnIn.MAX], r10). */
val BURN_IN_BLEED: Dp = BurnIn.MAX.dp

/**
 * Overscan for the burn-in shift (r10): the child is measured [BURN_IN_BLEED] larger on every side and placed back by
 * that much, so a full-bleed background moved ±8 dp never shows a moving band. Needs a parent that does not clip:
 * pagers clip their bounds, so a pager gets it itself and pads its pages back in by [BURN_IN_BLEED].
 */
fun Modifier.burnInBleed(): Modifier = layout { m, c ->
    val b = BURN_IN_BLEED.roundToPx()
    val p = m.measure(c.offset(2 * b, 2 * b))
    layout(c.constrainWidth(p.width - 2 * b), c.constrainHeight(p.height - 2 * b)) { p.place(-b, -b) }
}

/**
 * The page behind everything: the theme's bg (or the board background [bg] / [custom] colour / blurred [photo]), with
 * the candle night ([ThemeTokens.NIGHT_BG]) faded in by [night]; light themes step to it at 0.5. [candle] false = the
 * night is plain black, without the candle's glow (the clocks page).
 */
@Composable
fun ThemeBackground(
    t: StandbyTheme,
    night: Float,
    modifier: Modifier = Modifier,
    bg: BoardBg = BoardBg.THEME,
    custom: Color = Color(0xFF1A1430),
    photo: ImageBitmap? = null,
    candle: Boolean = true,
) {
    val n = if (t.tokens.light && bg == BoardBg.THEME) (if (night >= 0.5f) 1f else 0f) else night.coerceIn(0f, 1f)
    Box(modifier.fillMaxSize().burnInBleed()) {
        if (n < 1f) {
            if (bg == BoardBg.PHOTO_BLUR && photo != null) {
                Image(photo, null, Modifier.fillMaxSize().blur(48.dp), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(SCRIM))
            } else {
                val g = ground(t, bg, custom)
                Canvas(Modifier.fillMaxSize()) { draw(g) }
                // r13 page texture on the theme page, faded with the dusk ramp (plan-r13 A3)
                val tex = t.tokens.bgTexture
                val a = t.tokens.textureAmt * effectFade(n)
                if (bg == BoardBg.THEME && tex != Texture.NONE && a > 0f) Box(Modifier.fillMaxSize().texture(tex, a, RectangleShape))
            }
        }
        if (n > 0f) {
            val g = if (candle) ThemeTokens.NIGHT_BG.ground() else Ground(ThemeTokens.NIGHT_BG.stops)
            Canvas(Modifier.fillMaxSize()) { draw(g, n) }
        }
    }
}
