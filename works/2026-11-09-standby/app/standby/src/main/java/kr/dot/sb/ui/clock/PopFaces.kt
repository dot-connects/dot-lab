package kr.dot.sb.ui.clock

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.theme.BURN_IN_BLEED
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.ThemeTokens
import kotlin.math.max
import kotlin.math.min

/* r10 팝 faces (clocks.html c-block, c-stick, c-ball; plan-r10 Task 6). Mockup px scale with the box ([k], [r]). */

private val NIGHT_INK = ThemeTokens.NIGHT_INK
private const val DARK = 0xFF15120E

private fun solid(c: Color) = Look(Effect.SOLID, c.toArgb(), 0)

/** Full-screen own background overscans the burn-in shift; a slot gets a rounded plate (like Solar). */
private fun Canvas.plate(w: Float, h: Float, bleed: Float, r: Float, paint: (Canvas, Float, Float, Float) -> Unit) {
    save()
    if (r > 0f) clipPath(Path().apply { addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW) })
    translate(-bleed, -bleed)
    paint(this, w + 2 * bleed, h + 2 * bleed, bleed)
    restore()
}

/* ------------------------------------------------------------------ 색 블록 */

/**
 * 색 블록 = the split skeleton on a [FaceDefaults.PAIRS] background (c-block): hour half 38 % (50 % for two-digit
 * hours), digits dark on a light half and light on a dark one, 오전/오후 bottom-left, "월요일 · 7°" bottom-right. Tall
 * boxes stack the halves. Night: both halves black with a night rule between them.
 */
@Composable
internal fun ColorBlockFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val ui = rememberUi(700)
    val n = ink.night
    val pair = FaceDefaults.PAIRS[(style.pairPreset ?: 0).coerceIn(0, FaceDefaults.PAIRS.lastIndex)].map { Color(it) }
    // night fades the plate out to the night page (its candle glow stays), mockup .n = black halves
    val halves = pair.map { it.copy(alpha = 1f - n).toArgb() }
    // dark digits unless the half is really dark (pairs 3 and 4 have a near-black half); the mockup's pink keeps dark
    val dayOn = pair.map { if (it.luminance() > 0.12f) Color(DARK) else Color(FaceDefaults.PAPER) }
    val on = dayOn.map { lerp(it, NIGHT_INK, n) }
    val capC = dayOn.map { lerp(it, Ink.NIGHT_DIM, n).toArgb() }
    val fallback = Look.of(style, ink)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        if (min(w, h) < COMPACT_MIN.toPx()) return@FaceCanvas compact(p, d, fallback)
        val tall = h > w
        val k = min(w, h) / 360f
        val f = if (d.h.length == 1) 0.38f else 0.5f
        val hourEm = p.hourEm(d.h.length, d.use24h) * 1.03f
        val minEm = p.minuteEm * 1.03f
        // the two half boxes (x, y, w, h)
        val a = if (tall) floatArrayOf(0f, 0f, w, f * h) else floatArrayOf(0f, 0f, f * w, h)
        val b = if (tall) floatArrayOf(0f, f * h, w, (1 - f) * h) else floatArrayOf(f * w, 0f, (1 - f) * w, h)
        val sizePct = style.sizePct / 100f
        val capMax = (if (tall) 0.7f * a[3] else 0.58f * h) * sizePct
        val fs = minOf(capMax / p.capEm, 0.8f * a[2] / hourEm, 0.8f * b[2] / minEm)
        val cap = p.capEm * fs
        ui.textSize = max(13.dp.toPx(), 20 * k)
        val c1 = if (d.use24h) null else d.ampm
        val c2 = if (style.showInfo) listOfNotNull("${d.weekday}요일", d.temp).joinToString(" · ") else null
        val bleed = if (inSlot) 0f else BURN_IN_BLEED.toPx()
        val r = if (inSlot) max(0.05f * min(w, h), 12.dp.toPx()) else 0f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val rule = Paint().apply { color = TimePainter.withAlpha(Ink.NIGHT_DIM.toArgb(), n); strokeWidth = 2.dp.toPx() }
        val pad = 26 * k; val low = h - 18 * k - 0.2f * ui.textSize
        return@FaceCanvas {
            plate(w, h, bleed, r) { c, pw, ph, o ->
                fill.color = halves[0]
                if (tall) c.drawRect(0f, 0f, pw, o + b[1], fill) else c.drawRect(0f, 0f, o + b[0], ph, fill)
                fill.color = halves[1]
                if (tall) c.drawRect(0f, o + b[1], pw, ph, fill) else c.drawRect(o + b[0], 0f, pw, ph, fill)
            }
            if (n > 0f) { if (tall) drawLine(0f, b[1], w, b[1], rule) else drawLine(b[0], 0f, b[0], h, rule) }
            for ((i, box) in listOf(a, b).withIndex()) {
                val s = if (i == 0) d.h else d.m
                p.draw(this, s, box[0] + (box[2] - p.measure(s, fs)) / 2, box[1] + (box[3] + cap) / 2, fs, solid(on[i]))
            }
            // captions sit on the half they belong to: tall = both on the minute half's bottom
            if (c1 != null) { ui.color = if (tall) capC[1] else capC[0]; drawText(c1, pad, low, ui) }
            if (c2 != null) { ui.color = capC[1]; drawText(c2, w - pad - ui.measureText(c2), low, ui) }
        }
    }
}

/* ------------------------------------------------------------------ 스티커 */

private val STICK_TILT = floatArrayOf(-5f, 4f, -3f)
private val STICK_DROP = floatArrayOf(0f, 6f, 0f)

/**
 * 스티커 (c-stick): one [FaceDefaults.TRIOS] colour per digit, tilted −5° / +4° / −3°, a 12 dp dark outline under the
 * fill and a 10 dp solid offset shadow (both at 268 dp digits, scaled with the size), on [ClockStyle.paperColor]; a
 * tilted dark pill "월요일 · 7°" bottom-right. Night: black page, plain night digits, no outline, shadow or pill plate.
 */
@Composable
internal fun StickerFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val ui = rememberUi(700)
    val n = ink.night
    val night = n >= 0.5f
    val trio = FaceDefaults.TRIOS[(style.trioPreset ?: 0).coerceIn(0, FaceDefaults.TRIOS.lastIndex)]
    val paperC = Color(style.paperColor ?: FaceDefaults.PAPER)
    val paper = paperC.copy(alpha = 1f - n).toArgb()
    val dark = Color(0xFF16130F)
    val fallback = Look.of(style, ink)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        if (min(w, h) < COMPACT_MIN.toPx()) return@FaceCanvas compact(p, d, fallback)
        val k = min(w, h) / 360f
        val digits = d.h + d.m
        val hl = d.h.length
        // width of the hour class: every digit at the widest advance, colon pulled in 6 dp each side
        val colonEm = p.colonAdv
        val widthEm = (digits.length * p.digitAdv.max()) + colonEm
        val fs = min(0.52f * h * style.sizePct / 100f / p.capEm, 0.82f * w / widthEm)
        val r = fs / 268f // one mockup px at this digit size
        val cap = p.capEm * fs
        val adv = digits.map { p.measure(it.toString(), fs) }
        val colonW = p.measure(":", fs) - 12 * r
        var x = (w - adv.sum() - colonW) / 2
        val xs = FloatArray(digits.length)
        for (i in digits.indices) { if (i == hl) x += colonW; xs[i] = x; x += adv[i] }
        val colonX = xs[hl] - colonW - 6 * r
        val capTop = (h - cap) / 2
        val pt = p.paint
        val bleed = if (inSlot) 0f else BURN_IN_BLEED.toPx()
        val rr = if (inSlot) max(0.05f * min(w, h), 12.dp.toPx()) else 0f
        val bg = Paint().apply { color = paper }
        val pill = if (inSlot || !style.showInfo) null else listOfNotNull("${d.weekday}요일", d.temp).joinToString(" · ")
        ui.textSize = max(13.dp.toPx(), 19 * k)
        val pillBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dark.toArgb() }
        fun glyph(c: Canvas, s: String, gx: Float, base: Float, color: Int) {
            pt.textSize = fs
            pt.shader = null
            if (!night) {
                pt.style = Paint.Style.FILL_AND_STROKE; pt.strokeJoin = Paint.Join.ROUND; pt.strokeWidth = 12 * r
                pt.color = dark.toArgb()
                c.drawText(s, gx + 10 * r, base + 10 * r, pt) // offset shadow (outline included, like text-shadow)
                pt.style = Paint.Style.STROKE
                c.drawText(s, gx, base, pt)
            }
            pt.style = Paint.Style.FILL
            pt.color = color
            c.drawText(s, gx, base, pt)
        }
        return@FaceCanvas {
            plate(w, h, bleed, rr) { c, pw, ph, _ -> c.drawRect(0f, 0f, pw, ph, bg) }
            for (i in digits.indices) {
                // mockup stacking: each sticker over the previous one, the colon over the hour
                if (i == hl) glyph(this, ":", colonX, capTop + cap - 10 * r, lerp(Color.White, NIGHT_INK, n).toArgb()) // literal-ok: sticker colon is white on its own paper (c-stick .cl)
                val cx = xs[i] + adv[i] / 2; val cy = capTop + cap / 2
                save(); rotate(STICK_TILT[i % 3], cx, cy)
                glyph(this, digits[i].toString(), xs[i], capTop + cap + STICK_DROP[i % 3] * r, lerp(Color(trio[i % 3]), NIGHT_INK, n).toArgb())
                restore()
            }
            if (pill != null) {
                val tw = ui.measureText(pill)
                val pw = tw + 32 * k; val ph = ui.textSize + 22 * k
                val px = w - 30 * k - pw; val py = h - 26 * k - ph
                save(); rotate(-5f, px + pw / 2, py + ph / 2)
                if (!night) drawRoundRect(px, py, px + pw, py + ph, ph / 2, ph / 2, pillBg)
                ui.color = if (night) Ink.NIGHT_DIM.toArgb() else paperC.toArgb()
                drawText(pill, px + 16 * k, py + (ph + ui.textSize * ui.capRatio()) / 2, ui)
                restore()
            }
        }
    }
}

/* ------------------------------------------------------------------ 공 숫자 */

/**
 * 공 숫자 (c-ball): one [FaceDefaults.TRIOS] circle per digit (214 dp at 360 dp height, smaller when four digits must
 * fit), dark rounded digits, a colon of two dots, the date line under the row. Draws on the page (no own background).
 * A circle under 48 dp falls back to [compact]. Night: outlined circles, night digits.
 */
@Composable
internal fun BallsFace(style: ClockStyle, d: FaceData, ink: Ink, inSlot: Boolean, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val ui = rememberUi(500)
    val n = ink.night
    val trio = FaceDefaults.TRIOS[(style.trioPreset ?: 0).coerceIn(0, FaceDefaults.TRIOS.lastIndex)]
    val digitC = lerp(Color(style.digitColor ?: FaceDefaults.digit(FaceId.BALLS)), NIGHT_INK, n).toArgb()
    val fallback = Look.of(style, ink)
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val k = min(w, h) / 360f
        val digits = d.h + d.m
        val hl = d.h.length
        val cap = if (style.showInfo) d.infoLine() else null
        ui.textSize = max(13.dp.toPx(), 18 * k)
        if (cap != null) ui.textSize *= min(1f, 0.9f * w / ui.measureText(cap))
        val capH = if (cap == null) 0f else 22 * k + ui.textSize + 22 * k
        val margin = if (inSlot) 0.04f * w else Grid.margin(min(w, h).toDp()).toPx() + 10.dp.toPx()
        // row = n circles + colon column (dot 22 dp) with n gaps of 16 dp, all in units of the 214 dp circle
        val units = digits.length + (digits.length * 16f + 22f) / 214f
        val dia = minOf(0.594f * h * style.sizePct / 100f, (w - 2 * margin) / units, 0.9f * h - capH)
        if (dia < 48.dp.toPx() || min(w, h) < COMPACT_MIN.toPx()) return@FaceCanvas compact(p, d, fallback)
        val u = dia / 214f
        val fs = minOf(158 * u, 0.55f * dia / p.capEm, 0.8f * dia / p.digitAdv.max())
        val cy = (h - capH) / 2
        var x = (w - units * dia) / 2
        val xs = FloatArray(digits.length); var colonCx = 0f
        for (i in digits.indices) {
            if (i == hl) { colonCx = x + 11 * u; x += 22 * u + 16 * u }
            xs[i] = x; x += dia + 16 * u
        }
        val ball = Paint(Paint.ANTI_ALIAS_FLAG)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE; strokeWidth = 3 * u; color = TimePainter.withAlpha(Ink.NIGHT_DIM.toArgb(), n)
        }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.ink.toArgb() }
        val look = solid(Color(digitC))
        val capY = h - 22 * k - 0.2f * ui.textSize
        return@FaceCanvas {
            for (i in digits.indices) {
                val bx = xs[i] + dia / 2
                ball.color = TimePainter.withAlpha(trio[i % 3].toInt(), 1f - n)
                drawCircle(bx, cy, dia / 2, ball)
                if (n > 0f) drawCircle(bx, cy, dia / 2 - 1.5f * u, ring)
                val s = digits[i].toString()
                p.draw(this, s, bx - p.measure(s, fs) / 2, cy + p.capEm * fs / 2, fs, look)
            }
            drawCircle(colonCx, cy - 33 * u, 11 * u, dot)
            drawCircle(colonCx, cy + 33 * u, 11 * u, dot)
            if (cap != null) { ui.color = ink.ink2.toArgb(); drawText(cap, (w - ui.measureText(cap)) / 2, capY, ui) }
        }
    }
}
