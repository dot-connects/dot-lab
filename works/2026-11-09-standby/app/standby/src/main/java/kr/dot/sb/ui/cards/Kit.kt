package kr.dot.sb.ui.cards

import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.background
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.StandbyTheme
import kr.dot.sb.ui.theme.Themes
import kr.dot.sb.ui.theme.SizeClass
import kr.dot.sb.ui.theme.Sizes
import androidx.compose.ui.graphics.lerp
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * What a card needs from its board (design-system §5): the board accent, whether this card is the hero, the
 * board-wide label size and inner pad (so labels match and rows share TOP/BOT lines), and the clock face whose
 * font carries the board's numerals.
 */
@Immutable
data class CardLook(
    val accent: Color,
    val labelSize: Dp? = null,
    val pad: Dp? = null,
    val hero: Boolean = true,
    val clockOnBoard: Boolean = false,
    val clockFace: FaceId = FaceId.DIGITAL,
    /** Text-size setting (0.9 / 1 / 1.15): scales reading text only, never hero numbers (spec §0). */
    val textScale: Float = 1f,
) {
    companion object {
        /** A card alone (no board): follows the given clock face's accent. */
        fun of(config: AppConfig, face: FaceId = FaceId.DIGITAL): CardLook =
            CardLook(Color(config.clockStyle(face).color), textScale = config.textSize.f)
    }
}

/** Cap height / em from OS/2 sCapHeight (design-system §2.3); used to put cap-tops exactly on the TOP line. */
internal const val CAP_UI = 0.707f // Pretendard

/** Everything a card body reads, computed once per slot size. */
@Immutable
internal class Env(
    val w: Dp,
    val h: Dp,
    val sz: Sizes,
    /** Slot's own label size (grid numbers etc. scale per slot). */
    val ownLabel: Dp,
    val ink: Ink,
    val look: CardLook,
    /** Widget numerals family (theme): "9", "7°", "20%", "D-10". */
    val num: FontFamily,
    val numCap: Float,
    val slotId: String,
    val today: LocalDate,
    val t: StandbyTheme = Themes.IPHONE,
) {
    val s: Dp get() = sz.s
    val pad: Dp get() = sz.pad
    val label: Dp get() = sz.label
    val cls: SizeClass = SizeClass.of(sz.s)
    val wide: Boolean = w / h >= 1.5f
    val tall: Boolean = h / w >= 1.5f
    val cw: Dp get() = w - pad * 2
    val ch: Dp get() = h - pad * 2
    /** Minimum gaps between template rows (§2.4). */
    val gap: Dp get() = s * 0.04f
    val labelCap: Dp get() = label * CAP_UI
    /** Reading roles (spec §0, "두 단계 작게"): message 15, sender/header 18, app/time 13 dp × the text-size setting. */
    val read: Dp get() = 15.dp * look.textScale
    val meta: Dp get() = 13.dp * look.textScale
    val head: Dp get() = 18.dp * look.textScale
    /** Secondary reading lines: night red ink2 on a plate is ~2.6:1, so message lines take the primary ink at night. */
    val readInk2: Color get() = lerp(ink.ink2, ink.ink, ink.night)
    val bar: Dp get() = max(4.dp, s * 0.025f)
    /** Slot plate corner: the theme ratio of the short side, floored (PAPER: square). */
    val plateRadius: Dp get() = if (t.tokens.radiusRatio == 0f) 0.dp else max(t.tokens.radiusMin, s * t.tokens.radiusRatio)
    /** Corner radius for something [of] long: the theme radius with a floor; B (radius 0) stays square. */
    fun round(of: Dp, floor: Dp = 12.dp, k: Float = 1f): Dp = if (t.radius == 0f) 0.dp else max(floor, of * t.radius * k)
    /** A lone value in a tall slot sizes from the long side too (review X9), so it fills instead of floating. */
    val loneDisplay: Dp get() = if (tall) max(sz.display, h * 0.6f * 0.56f) else sz.display
    val loneValue: Dp get() = if (tall) max(sz.value, h * 0.6f * 0.30f) else sz.value
    val atLeastS: Boolean get() = cls >= SizeClass.S
    val atLeastM: Boolean get() = cls >= SizeClass.M
}

/** Cap-top → last-baseline box: the reported height starts at the first line's cap-top and ends on the last baseline. */
internal fun Modifier.capBox(cap: Float, size: Dp): Modifier = layout { m, c ->
    val p = m.measure(c.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val fb = p[FirstBaseline]
    val lb = p[LastBaseline]
    if (fb == androidx.compose.ui.layout.AlignmentLine.Unspecified) return@layout layout(p.width, p.height) { p.place(0, 0) }
    val top = (fb - cap * size.toPx()).roundToInt().coerceAtLeast(0)
    layout(p.width, (lb - top).coerceAtLeast(0)) { p.place(0, -top) }
}

/**
 * Board text. [size] is the font size in dp (slot-derived, ignores font scale). [maxW]/[maxCapH] shrink the size
 * so a value never clips or ellipsizes (§1.5); labels/details just ellipsize at the end.
 */
@Composable
internal fun Txt(
    text: String,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    weight: Int = 600,
    family: FontFamily? = null,
    cap: Float = CAP_UI,
    lines: Int = 1,
    align: TextAlign = TextAlign.Start,
    tnum: Boolean = false,
    maxW: Dp? = null,
    maxCapH: Dp? = null,
    num: Boolean = false,
    tracking: Float = 0f,
    leading: Float = 1.15f,
) = Txt(if (num) numSpans(text, LocalTheme.current.numerals, weight) else AnnotatedString(text), size, color, modifier, weight, family, cap, lines, align, tnum, maxW, maxCapH, tracking, leading)

/** Runs of Latin/digits ("11°", "9:30", "C") in the theme's numeral font; Hangul stays in the words font. */
internal fun numSpans(text: String, numerals: FontFamily, weight: Int): AnnotatedString = buildAnnotatedString {
    append(text)
    Regex("[!-~°·–]+").findAll(text).forEach { m ->
        addStyle(SpanStyle(fontFamily = numerals, fontWeight = FontWeight(weight.coerceIn(1, 1000))), m.range.first, m.range.last + 1)
    }
}

/** Korean never breaks inside a word (review X4): phrase word-break on the heading strategy. */
private val PHRASE = LineBreak.Heading.copy(wordBreak = LineBreak.WordBreak.Phrase)

@Composable
internal fun Txt(
    text: AnnotatedString,
    size: Dp,
    color: Color,
    modifier: Modifier = Modifier,
    weight: Int = 600,
    family: FontFamily? = null,
    cap: Float = CAP_UI,
    lines: Int = 1,
    align: TextAlign = TextAlign.Start,
    tnum: Boolean = false,
    maxW: Dp? = null,
    maxCapH: Dp? = null,
    /** Letter spacing in em (B kickers). */
    tracking: Float = 0f,
    /** Line height / size; multi-line Hangul sentences read better at 1.35 (r7). */
    leading: Float = 1.15f,
) {
    val fam = family ?: LocalTheme.current.words(weight)
    val density = LocalDensity.current
    fun styleOf(d: Dp) = TextStyle(
        color = color, fontSize = (d.value / density.fontScale).sp, fontFamily = fam,
        // static multi-weight families (rounded numerals) pick by weight; never fake bold
        fontWeight = FontWeight(weight.coerceIn(1, 1000)), fontSynthesis = FontSynthesis.None,
        lineHeight = (d.value * leading / density.fontScale).sp, textAlign = align,
        fontFeatureSettings = if (tnum) "tnum" else null,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        lineBreak = PHRASE, letterSpacing = tracking.em,
    )
    var fs = size
    if (maxCapH != null) fs = min(fs, maxCapH / cap)
    if (maxW != null) {
        val measurer = rememberTextMeasurer(cacheSize = 2)
        val maxPx = with(density) { maxW.toPx() }
        val f0 = fs
        fs = remember(text, f0, maxPx, fam, tnum) {
            val wPx = measurer.measure(text, styleOf(f0), maxLines = 1, softWrap = false).size.width
            if (wPx > maxPx && wPx > 0) f0 * (maxPx / wPx) * 0.97f else f0
        }
    }
    BasicText(
        text, modifier.capBox(cap, fs), style = styleOf(fs), maxLines = lines,
        // Fitted values never ellipsize (§1.5): they were already shrunk to [maxW].
        overflow = if (maxW != null) TextOverflow.Visible else TextOverflow.Ellipsis, softWrap = lines > 1 && maxW == null,
    )
}

/** "7°" with the degree pulled in by 0.12 em so it touches the digit like iPhone's "74°" (review X7). */
internal fun temp(c: Int): AnnotatedString = buildAnnotatedString {
    val n = c.toString()
    append(n.dropLast(1))
    withStyle(SpanStyle(letterSpacing = (-0.12).em)) { append(n.last()) }
    append("°")
}

/** Whether [text] at [size] (Pretendard [weight]) fits [maxW] on one line. */
@Composable
internal fun fitsOneLine(text: String, size: Dp, weight: Int, maxW: Dp): Boolean {
    val measurer = rememberTextMeasurer(cacheSize = 2)
    val density = LocalDensity.current
    return remember(text, size, weight, maxW) {
        val style = TextStyle(fontSize = (size.value / density.fontScale).sp, fontFamily = Fonts.ui(weight))
        measurer.measure(text, style, maxLines = 1, softWrap = false).size.width <= with(density) { maxW.toPx() }
    }
}

/** Tap without ripple (board surfaces never flash). */
@Composable
internal fun Modifier.tap(onClick: (() -> Unit)?): Modifier =
    if (onClick == null) this else clickable(remember { MutableInteractionSource() }, null, onClick = onClick)

/** Lets a child (icon) overflow a cap-height row without changing the row's height; centred on the cap. */
internal fun Modifier.wrapUnbounded(): Modifier = wrapContentHeight(unbounded = true)

/** Label row: icon (box = label size, centred on the cap) + text, optional trailing text. Height = label cap. */
@Composable
internal fun LabelRow(
    e: Env, icon: Ic?, text: String, color: Color = e.ink.ink2, trailing: String? = null, trailingColor: Color = e.ink.ink2,
    center: Boolean = false, iconEnd: Boolean = false, weight: Int = 600,
) {
    if (!e.t.labelIcons) {
        // B: a bare kicker in the secondary ink, tracked out (mockup r5 B "오늘", "날씨", "재생 중")
        Kicker(e, if (trailing != null) "$text · $trailing" else text, center = center)
        return
    }
    val iconBox = @Composable {
        Box(Modifier.height(e.labelCap).wrapUnbounded()) { IconView(icon!!, color, Modifier.size(e.label), e.ink.bg) }
    }
    Row(
        if (center) Modifier else Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
    ) {
        if (icon != null && !iconEnd) { iconBox(); Spacer(Modifier.width(e.label * 0.35f)) }
        // icon-after labels ("서울 ➤") take their own width first, so a narrow slot never ellipsizes the city
        Txt(text, e.label, color, if (iconEnd) Modifier else Modifier.weight(1f, fill = !center), weight = weight)
        if (icon != null && iconEnd) { Spacer(Modifier.width(e.label * 0.35f)); iconBox(); Spacer(Modifier.weight(1f)) }
        if (trailing != null) {
            Spacer(Modifier.width(e.label * 0.5f))
            Txt(trailing, e.label, trailingColor, weight = 500, tnum = true)
        }
    }
}

/** B kicker: small tracked label in ink2 (digits in the numeral serif), one line, height = its cap. */
@Composable
internal fun Kicker(e: Env, text: String, color: Color = e.ink.ink2, center: Boolean = false, modifier: Modifier = Modifier) =
    Txt(
        text, e.label, color, if (center) modifier else modifier.fillMaxWidth(), weight = 500, num = true,
        tracking = e.t.labelTracking, align = if (center) TextAlign.Center else TextAlign.Start,
    )

@Composable
internal fun Detail(e: Env, text: String, color: Color = e.ink.ink2, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) =
    Txt(text, e.label, color, modifier, weight = 500, tnum = true, align = align)

/** Progress / usage bar (§2.4): full radius, track ink3. */
@Composable
internal fun Bar(e: Env, frac: Float, fill: Color, modifier: Modifier = Modifier, track: Color = e.ink.ink3) {
    Box(modifier.fillMaxWidth().height(e.bar).clip(RoundedCornerShape(50)).background(track)) {
        Box(Modifier.fillMaxWidth(frac.coerceIn(0f, 1f)).height(e.bar).clip(RoundedCornerShape(50)).background(fill))
    }
}

/** Standard text card: content box inside the pad, first cap-top on TOP, last baseline on BOT, space-between. */
@Composable
internal fun TextCard(e: Env, modifier: Modifier = Modifier, horizontal: Alignment.Horizontal = Alignment.Start, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxSize().padding(e.pad),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = horizontal,
    ) { content() }
}

/** §5.4 empty / permission / no-data state in the same template: label, icon or text value, detail. */
@Composable
internal fun StateCard(e: Env, icon: Ic, label: String, valueIcon: Ic?, valueText: String?, detail: String?, onTap: (() -> Unit)?) {
    TextCard(e, Modifier.tap(onTap)) {
        LabelRow(e, icon, label)
        if (valueIcon != null) IconView(valueIcon, e.ink.ink3, Modifier.size(min(e.sz.value, e.ch * 0.4f)), e.ink.bg, stroke = true)
        if (valueText != null) Txt(valueText, e.sz.title, e.ink.ink2, weight = 700, maxW = e.cw)
        if (detail != null && e.atLeastS) Detail(e, detail)
    }
}

/** Loading (first fetch): real label + static ink4 bar the size of the value. No spinner (§5.4). */
@Composable
internal fun LoadingCard(e: Env, icon: Ic, label: String, labelRow: (@Composable () -> Unit)? = null) {
    TextCard(e) {
        // the loaded card's own label composable when given, so loading and loaded never differ (review X6)
        if (labelRow != null) labelRow() else LabelRow(e, icon, label)
        Box(Modifier.width(e.cw * 0.45f).height(min(e.sz.value * CAP_UI, e.ch * 0.3f)).clip(RoundedCornerShape(50)).background(e.ink.ink4))
    }
}

/** Night photo mapping (candle night): luminance tinted to the candle ink #B9A48C, lerped by the night fraction. No red. */
internal fun nightFilter(n: Float): ColorFilter? {
    if (n <= 0f) return null
    val c = kr.dot.sb.ui.theme.ThemeTokens.NIGHT_INK
    val lum = floatArrayOf(0.2126f, 0.7152f, 0.0722f)
    val m = FloatArray(20)
    for (row in 0..2) {
        val tint = when (row) { 0 -> c.red; 1 -> c.green; else -> c.blue }
        for (col in 0..2) m[row * 5 + col] = (if (row == col) 1f else 0f) * (1f - n) + lum[col] * tint * n
    }
    m[18] = 1f
    return ColorFilter.colorMatrix(ColorMatrix(m))
}

internal fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

private val DOW = arrayOf("월", "화", "수", "목", "금", "토", "일")
internal fun dowKo(d: LocalDate) = DOW[d.dayOfWeek.value - 1]
internal fun dateKo(d: LocalDate) = "${d.monthValue}월 ${d.dayOfMonth}일 ${dowKo(d)}요일"
internal fun hhmm(epochSec: Long): String {
    val t = java.time.Instant.ofEpochSecond(epochSec).atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(t.hour, t.minute)
}
