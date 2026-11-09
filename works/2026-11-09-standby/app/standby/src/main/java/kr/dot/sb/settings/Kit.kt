@file:OptIn(ExperimentalLayoutApi::class)

package kr.dot.sb.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.sb.ui.theme.Fonts

/** Settings palette (design-system §6). Settings is its own dark UI, not a StandBy surface. */
object SK {
    val bg = Color(0xFF0E0F12)
    val surface = Color(0xFF17181C)
    val surface2 = Color(0xFF212228)
    val line = Color(0xFF2A2C32)
    val text = Color(0xFFEDEDEF)
    val muted = Color(0xFF9A9AA0)
    val danger = Color(0xFFFF6B5E)
    val ok = Color(0xFF6FDC86)
    val scrim = Color(0xB3000000)
}

val LocalAccent = compositionLocalOf { Color(0xFF76A6F3) }

fun Long.c(): Color = Color(this)
fun Color.toLongArgb(): Long = toArgb().toLong() and 0xFFFFFFFFL

@Composable
fun SettingsTheme(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = accent, onPrimary = SK.bg, background = SK.bg, surface = SK.surface, onSurface = SK.text),
    ) {
        CompositionLocalProvider(LocalAccent provides accent) { content() }
    }
}

/** Every string in settings goes through here so Samsung's system font never leaks in (Fonts.ui = Pretendard). */
@Composable
fun T(
    text: String,
    size: TextUnit = 16.sp,
    weight: Int = 500,
    color: Color = SK.text,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    align: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    Text(
        text, modifier, color = color, fontSize = size, fontFamily = Fonts.ui(weight), fontWeight = FontWeight(weight.coerceIn(100, 900)),
        maxLines = maxLines, overflow = TextOverflow.Ellipsis, textAlign = align, lineHeight = lineHeight,
    )
}

// ---- icons (drawn, 2 dp round strokes) ---------------------------------------------------------------------------

enum class Ic { BACK, CHEVRON, PLUS, CLOSE, UP, DOWN, CHECK }

@Composable
fun Icon(ic: Ic, tint: Color = SK.muted, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        fun p(vararg xy: Float) = Path().apply { xy.toList().chunked(2).forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * s, y * s) else lineTo(x * s, y * s) } }
        val path = when (ic) {
            Ic.BACK -> p(0.62f, 0.2f, 0.32f, 0.5f, 0.62f, 0.8f)
            Ic.CHEVRON -> p(0.38f, 0.2f, 0.68f, 0.5f, 0.38f, 0.8f)
            Ic.UP -> p(0.2f, 0.62f, 0.5f, 0.32f, 0.8f, 0.62f)
            Ic.DOWN -> p(0.2f, 0.38f, 0.5f, 0.68f, 0.8f, 0.38f)
            Ic.CHECK -> p(0.2f, 0.52f, 0.42f, 0.72f, 0.8f, 0.3f)
            Ic.PLUS -> Path().apply { moveTo(0.5f * s, 0.2f * s); lineTo(0.5f * s, 0.8f * s); moveTo(0.2f * s, 0.5f * s); lineTo(0.8f * s, 0.5f * s) }
            Ic.CLOSE -> Path().apply { moveTo(0.27f * s, 0.27f * s); lineTo(0.73f * s, 0.73f * s); moveTo(0.73f * s, 0.27f * s); lineTo(0.27f * s, 0.73f * s) }
        }
        drawPath(path, tint, style = Stroke(width = s / 11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun IconBtn(ic: Ic, onClick: () -> Unit, tint: Color = SK.muted, enabled: Boolean = true, modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), Alignment.Center) {
        Icon(ic, if (enabled) tint else SK.line)
    }
}

// ---- page, sections, rows -----------------------------------------------------------------------------------------

/** True inside the left (preview) pane of a two-pane page: previews may use the pane's full height. */
val LocalPreviewPane = compositionLocalOf { false }

/**
 * A settings page. [header] = sticky preview above the scrolling [content]; [side] = preview block that scrolls with
 * the content on a phone. Width ≥ 600 dp (Fold inner, review S1): two panes — [header] + [side] on the left (55 %,
 * sticky), [content] on the right (45 %), same components.
 */
@Composable
fun Page(
    title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    side: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(SK.bg).systemBarsPadding(), contentAlignment = Alignment.TopCenter) {
        val twoPane = maxWidth >= 600.dp && (header != null || side != null)
        Column(Modifier.widthIn(max = if (twoPane) 1200.dp else 720.dp).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(start = if (onBack != null) 4.dp else 20.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) { IconBtn(Ic.BACK, onBack, SK.text); Spacer(Modifier.width(4.dp)) }
                T(title, if (onBack != null) 22.sp else 30.sp, 700, maxLines = 1)
            }
            if (twoPane) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    CompositionLocalProvider(LocalPreviewPane provides true) {
                        Column(Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                            header?.invoke(this)
                            side?.invoke(this)
                        }
                    }
                    Column(Modifier.weight(0.45f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp)) { content() }
                }
            } else {
                if (header != null) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) { header() }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp)) {
                    side?.invoke(this)
                    content()
                }
            }
        }
    }
}

class GroupScope { internal val items = mutableListOf<@Composable () -> Unit>(); fun item(c: @Composable () -> Unit) { items += c } }

@Composable
fun Group(modifier: Modifier = Modifier, content: GroupScope.() -> Unit) {
    val scope = GroupScope().apply(content)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SK.surface)) {
        scope.items.forEachIndexed { i, c ->
            if (i > 0) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp).background(SK.line))
            c()
        }
    }
}

/** Section header (label 13 sp, muted) + a group of rows + optional footnote. */
@Composable
fun Section(title: String?, footer: String? = null, content: GroupScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        if (title != null) T(title, 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, bottom = 8.dp))
        Group(content = content)
        if (footer != null) T(footer, 13.sp, 400, SK.muted, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), lineHeight = 18.sp)
    }
}

@Composable
fun NavRow(title: String, onClick: () -> Unit, subtitle: String? = null, value: String? = null, badge: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClick = onClick).padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T(title, 16.sp, 500)
            if (subtitle != null) T(subtitle, 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp), maxLines = 2)
        }
        if (value != null) T(value, 14.sp, 400, SK.muted, Modifier.padding(start = 8.dp), maxLines = 1)
        if (badge != null) Pill(badge, SK.danger, Modifier.padding(start = 8.dp))
        Icon(Ic.CHEVRON, SK.muted.copy(alpha = 0.7f), 20.dp, Modifier.padding(start = 4.dp))
    }
}

@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier, filled: Boolean = false) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(if (filled) color else color.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        Alignment.Center,
    ) { T(text, 12.sp, 700, if (filled) SK.bg else color, maxLines = 1) }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, enabled: Boolean = true) {
    val acc = LocalAccent.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(title, 16.sp, 500, if (enabled) SK.text else SK.muted)
            if (subtitle != null) T(subtitle, 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp), lineHeight = 18.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked, null, enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = SK.bg, checkedTrackColor = acc, checkedBorderColor = acc,
                uncheckedThumbColor = SK.muted, uncheckedTrackColor = SK.surface2, uncheckedBorderColor = SK.line,
                disabledCheckedTrackColor = acc.copy(alpha = 0.4f), disabledUncheckedTrackColor = SK.surface2,
            ),
        )
    }
}

/** Title row with a body slot underneath (segmented control, chips, slider …). */
@Composable
fun ControlRow(title: String?, note: String? = null, value: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (title != null) Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            T(title, 16.sp, 500, Modifier.weight(1f))
            if (value != null) T(value, 14.sp, 500, SK.muted)
        }
        content()
        if (note != null) T(note, 13.sp, 400, SK.muted, Modifier.padding(top = 8.dp), lineHeight = 18.sp)
    }
}

@Composable
private fun T(text: String, size: TextUnit, weight: Int, modifier: Modifier) = T(text, size, weight, SK.text, modifier)

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val acc = LocalAccent.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(SK.surface2).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp)).background(if (on) acc else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(i) }.padding(horizontal = 4.dp),
                Alignment.Center,
            ) { T(o, 14.sp, if (on) 700 else 500, if (on) SK.bg else SK.muted, align = TextAlign.Center, maxLines = 1) }
        }
    }
}

@Composable
fun ChipFlow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, enabled: List<Boolean>? = null) {
    val acc = LocalAccent.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val ok = enabled?.getOrNull(i) ?: true
            Box(
                Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(if (on) acc else SK.surface2)
                    .clickable(enabled = ok) { onSelect(i) }.padding(horizontal = 16.dp),
                Alignment.Center,
            ) { T(o, 14.sp, if (on) 700 else 500, if (on) SK.bg else if (ok) SK.text else SK.muted.copy(alpha = 0.5f), maxLines = 1) }
        }
    }
}

@Composable
fun SliderControl(value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, steps: Int = 0) {
    val acc = LocalAccent.current
    Slider(
        value, onChange, valueRange = range, steps = steps,
        colors = SliderDefaults.colors(thumbColor = acc, activeTrackColor = acc, inactiveTrackColor = SK.surface2, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
    )
}

@Composable
fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, steps: Int = 0, note: String? = null) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = if (note != null) 12.dp else 4.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(title, 16.sp, 500, SK.text, Modifier.weight(1f))
            T(valueText, 14.sp, 500, SK.muted)
        }
        SliderControl(value, range, onChange, steps)
        if (note != null) T(note, 13.sp, 400, SK.muted, lineHeight = 18.sp)
    }
}

@Composable
fun ActionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = true, danger: Boolean = false, enabled: Boolean = true) {
    val acc = LocalAccent.current
    val bg = when { !enabled -> SK.surface2; danger -> SK.danger.copy(alpha = 0.16f); primary -> acc; else -> SK.surface2 }
    val fg = when { !enabled -> SK.muted; danger -> SK.danger; primary -> SK.bg; else -> SK.text }
    Box(modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(bg).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp), Alignment.Center) {
        T(text, 15.sp, 700, fg, maxLines = 1, align = TextAlign.Center)
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, singleLine: Boolean = true) {
    val acc = LocalAccent.current
    Box(modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(SK.surface2).padding(horizontal = 14.dp), Alignment.CenterStart) {
        if (value.isEmpty()) T(hint, 16.sp, 400, SK.muted)
        BasicTextField(
            value, onChange, singleLine = singleLine, cursorBrush = Brush.linearGradient(listOf(acc, acc)),
            textStyle = TextStyle(color = SK.text, fontSize = 16.sp, fontFamily = Fonts.ui(500), fontWeight = FontWeight(500)),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ---- colour ---------------------------------------------------------------------------------------------------------

@Composable
private fun Dot(color: Color, selected: Boolean, onClick: () -> Unit, content: (@Composable () -> Unit)? = null) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), Alignment.Center) {
        if (selected) Box(Modifier.size(40.dp).border(2.dp, Color.White, CircleShape))
        Box(Modifier.size(30.dp).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape), Alignment.Center) { content?.invoke() }
    }
}

/** Swatches + a "custom" dot that opens an HSV picker. [color] and the callback are ARGB Longs. */
@Composable
fun ColorPalette(color: Long, onChange: (Long) -> Unit, swatches: List<Long> = kr.dot.sb.model.ClockStyle.SWATCHES, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val custom = color !in swatches
    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(0.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            swatches.forEach { Dot(it.c(), it == color, { onChange(it) }) }
            Box(Modifier.size(44.dp).clip(CircleShape).clickable { open = !open }, Alignment.Center) {
                if (custom || open) Box(Modifier.size(40.dp).border(2.dp, Color.White, CircleShape))
                Canvas(Modifier.size(30.dp).clip(CircleShape)) {
                    drawCircle(Brush.sweepGradient(listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)))
                    drawCircle(Color.White, radius = size.minDimension * 0.18f)
                }
            }
        }
        if (open) HsvPicker(color, onChange, Modifier.padding(top = 8.dp))
    }
}

@Composable
fun HsvPicker(color: Long, onChange: (Long) -> Unit, modifier: Modifier = Modifier) {
    val init = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(color.toInt(), it) } }
    var h by remember { mutableFloatStateOf(init[0]) }
    var s by remember { mutableFloatStateOf(init[1]) }
    var v by remember { mutableFloatStateOf(init[2]) }
    fun emit() = onChange(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)).toLong() and 0xFFFFFFFFL)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) { detectTapGestures { o -> s = (o.x / size.width).coerceIn(0f, 1f); v = 1f - (o.y / size.height).coerceIn(0f, 1f); emit() } }
                .pointerInput(Unit) {
                    detectDragGestures { c, _ -> s = (c.position.x / size.width).coerceIn(0f, 1f); v = 1f - (c.position.y / size.height).coerceIn(0f, 1f); emit() }
                },
        ) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(h, 1f, 1f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val p = Offset(s * size.width, (1f - v) * size.height)
            drawCircle(Color.White, 11.dp.toPx(), p, style = Stroke(2.5.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.35f), 13.dp.toPx(), p, style = Stroke(1.dp.toPx()))
        }
        Canvas(
            Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(15.dp))
                .pointerInput(Unit) { detectTapGestures { o -> h = (o.x / size.width).coerceIn(0f, 1f) * 360f; emit() } }
                .pointerInput(Unit) { detectDragGestures { c, _ -> h = (c.position.x / size.width).coerceIn(0f, 1f) * 360f; emit() } },
        ) {
            drawRect(Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }))
            val x = (h / 360f).coerceIn(0f, 1f) * size.width
            drawCircle(Color.White, 12.dp.toPx(), Offset(x.coerceIn(12.dp.toPx(), size.width - 12.dp.toPx()), size.height / 2), style = Stroke(3.dp.toPx()))
        }
        T("#%06X".format(color.toInt() and 0xFFFFFF), 13.sp, 500, SK.muted)
    }
}

/** A labelled colour: dot + name; tapping expands the palette. For analog hand colours and the like. */
@Composable
fun ColorRow(title: String, color: Long, onChange: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = !open }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            T(title, 16.sp, 500, SK.text, Modifier.weight(1f))
            Box(Modifier.size(28.dp).clip(CircleShape).background(color.c()).border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape))
        }
        if (open) ColorPalette(color, onChange, modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 8.dp))
    }
}

// ---- sheet ----------------------------------------------------------------------------------------------------------

/** Bottom sheet drawn inside the screen (scrim + panel); simpler and more predictable than a window-based sheet. */
@Composable
fun Sheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val none = remember { MutableInteractionSource() }
    BoxWithConstraints(Modifier.fillMaxSize().background(SK.scrim).clickable(interactionSource = none, indication = null, onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.BottomCenter).widthIn(max = 720.dp).fillMaxWidth().heightIn(max = maxHeight * 0.86f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(SK.surface)
                .clickable(interactionSource = none, indication = null) {}.navigationBarsPadding(),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp).size(width = 36.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(SK.line))
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                T(title, 18.sp, 700, SK.text, Modifier.weight(1f))
                IconBtn(Ic.CLOSE, onDismiss, SK.muted)
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp)) { content() }
        }
    }
}

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))
