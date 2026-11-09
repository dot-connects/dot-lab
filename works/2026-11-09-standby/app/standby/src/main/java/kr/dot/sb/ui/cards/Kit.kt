package kr.dot.sb.ui.cards

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
import androidx.compose.ui.text.TextStyle
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
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.SizeClass
import kr.dot.sb.ui.theme.Sizes
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
) {
    companion object {
        /** A card alone (no board): follows the given clock face's accent. */
        fun of(config: AppConfig, face: FaceId = FaceId.DIGITAL): CardLook =
            CardLook(Color(config.styles[face]?.color ?: 0xFF76A6F3))
    }
}

/** Cap height / em from OS/2 sCapHeight (design-system §2.3); used to put cap-tops exactly on the TOP line. */
internal const val CAP_UI = 0.707f // Pretendard
internal fun capOf(font: FontId): Float = when (font) {
    FontId.CONDENSED -> 0.81f
    FontId.ROUNDED, FontId.ROUNDED_HEAVY -> 0.705f
    FontId.SERIF, FontId.SERIF_HEAVY -> 0.67f
    FontId.SLAB -> 0.711f
    else -> 0.7275f
}

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
    val num: FontFamily,
    val numCap: Float,
    val slotId: String,
    val today: LocalDate,
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
    val bar: Dp get() = max(4.dp, s * 0.025f)
    val plateRadius: Dp get() = max(12.dp, s * 0.05f)
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
) {
    val fam = family ?: Fonts.ui(weight)
    val density = LocalDensity.current
    fun styleOf(d: Dp) = TextStyle(
        color = color, fontSize = (d.value / density.fontScale).sp, fontFamily = fam,
        lineHeight = (d.value * 1.15f / density.fontScale).sp, textAlign = align,
        fontFeatureSettings = if (tnum) "tnum" else null,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
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
    center: Boolean = false, iconEnd: Boolean = false,
) {
    val iconBox = @Composable {
        Box(Modifier.height(e.labelCap).wrapUnbounded()) { IconView(icon!!, color, Modifier.size(e.label), e.ink.bg) }
    }
    Row(
        if (center) Modifier else Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start,
    ) {
        if (icon != null && !iconEnd) { iconBox(); Spacer(Modifier.width(e.label * 0.35f)) }
        Txt(text, e.label, color, Modifier.weight(1f, fill = !center && !iconEnd), weight = 600)
        if (icon != null && iconEnd) { Spacer(Modifier.width(e.label * 0.35f)); iconBox(); Spacer(Modifier.weight(1f)) }
        if (trailing != null) {
            Spacer(Modifier.width(e.label * 0.5f))
            Txt(trailing, e.label, trailingColor, weight = 500, tnum = true)
        }
    }
}

@Composable
internal fun Detail(e: Env, text: String, color: Color = e.ink.ink2, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Start) =
    Txt(text, e.label, color, modifier, weight = 500, tnum = true, align = align)

/** Progress / usage bar (§2.4): full radius, track ink3. */
@Composable
internal fun Bar(e: Env, frac: Float, fill: Color, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(e.bar).clip(RoundedCornerShape(50)).background(e.ink.ink3)) {
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
internal fun LoadingCard(e: Env, icon: Ic, label: String) {
    TextCard(e) {
        LabelRow(e, icon, label)
        Box(Modifier.width(e.cw * 0.45f).height(min(e.sz.value * CAP_UI, e.ch * 0.3f)).clip(RoundedCornerShape(50)).background(e.ink.ink4))
    }
}

/** Night photo mapping (§2.2): luminance → red ×0.5, G = B = 0, lerped by the night fraction. */
internal fun nightFilter(n: Float): ColorFilter? {
    if (n <= 0f) return null
    fun l(a: Float, b: Float) = a + (b - a) * n
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                l(1f, 0.15f), l(0f, 0.295f), l(0f, 0.055f), 0f, 0f,
                0f, 1f - n, 0f, 0f, 0f,
                0f, 0f, 1f - n, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

internal fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

private val DOW = arrayOf("월", "화", "수", "목", "금", "토", "일")
internal fun dowKo(d: LocalDate) = DOW[d.dayOfWeek.value - 1]
internal fun dateKo(d: LocalDate) = "${d.monthValue}월 ${d.dayOfMonth}일 ${dowKo(d)}요일"
internal fun hhmm(epochSec: Long): String {
    val t = java.time.Instant.ofEpochSecond(epochSec).atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(t.hour, t.minute)
}
