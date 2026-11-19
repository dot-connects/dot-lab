package kr.dot.wherepark.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** App colours: plan-v2 global constraints = mockups/v2/final --app-* tokens. */
object Wp {
    val BG = Color(0xFFE9E7E3)
    val SF = Color(0xFFF6F5F2)
    val FG = Color(0xFF161616)
    val MU = Color(0xFF6C6A66)
    val LN = Color(0xFFD5D2CC)
    val ACC = Color(0xFFFF6A13)
    val ON_ACC = Color(0xFF1A0F06)
    val OK = Color(0xFF3A8A4F)
    val KAKAO = Color(0xFFFEE500)
    val DANGER = Color(0xFFC2412D)
    /** The six car colours of 차 편집 (mockup section 7). */
    val CAR_COLORS = listOf(0xFFF4F1EA, 0xFF8E9399, 0xFF1C1C1C, 0xFF2D5DA8, 0xFFC2412D, 0xFF2F7D4F).map { it.toInt() }
}

/** Mockup body line-height 1.6 with CSS half-leading. */
fun ts(size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = Wp.FG, lh: Float = 1.6f, family: FontFamily? = null, ls: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontSize = size, fontWeight = weight, color = color, lineHeight = lh.em, fontFamily = family, letterSpacing = ls,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None))

/** Inter Tight (mockup --gro) at a fixed weight; Hangul falls back to the system face like the mockup does. */
@Composable
fun gro(weight: Int): FontFamily {
    val ctx = LocalContext.current
    return remember(weight) { AssetFonts.family(ctx, "Inter Tight", weight) }
}

/** Settings > 보기 > 글자 크게: 1.3x on top of the system font scale. */
val LocalBigText = staticCompositionLocalOf { false }

@Composable
fun WpTheme(bigText: Boolean, content: @Composable () -> Unit) {
    val d = LocalDensity.current
    val scaled = if (bigText) Density(d.density, d.fontScale * 1.3f) else d
    CompositionLocalProvider(LocalDensity provides scaled, LocalBigText provides bigText, content = content)
}

fun Modifier.lift(shape: Shape, e: Dp = 2.dp) = shadow(e, shape, ambientColor = Color(0x14000000), spotColor = Color(0x29000000))

/** Mockup .card: surface with a soft top highlight, 22 dp corners. */
@Composable
fun WCard(modifier: Modifier = Modifier, pad: Dp = 16.dp, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().lift(shape).clip(shape).background(Wp.SF)
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = .5f), .4f to Color.Transparent))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = pad, vertical = 14.dp),
        content = content,
    )
}

/** Mockup .scr under .sb: back arrow + h3 + optional round icon button. [scroll] for the long settings page. */
@Composable
fun WScreen(
    title: String?, onBack: (() -> Unit)? = null, action: (@Composable RowScope.() -> Unit)? = null, gap: Dp = 12.dp,
    scroll: Boolean = false, bg: Color = Wp.BG, content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(bg).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            if (title != null) TopBar(title, onBack, action)
            content()
        }
    }
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, action: (@Composable RowScope.() -> Unit)? = null, lead: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = if (onBack != null || action != null) 40.dp else 0.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (onBack != null) Box(Modifier.offset(x = (-8).dp).size(32.dp, 40.dp).clickable(onClickLabel = "뒤로", onClick = onBack), Alignment.Center) { Ic("back", 22.dp, stroke = 2f) }
        lead?.invoke()
        Text(title, Modifier.weight(1f).then(if (onBack != null) Modifier.offset(x = (-8).dp) else Modifier),
            style = ts(22.sp, FontWeight.ExtraBold, family = gro(800), ls = (-0.01).em, lh = 1.6f), maxLines = 1)
        action?.invoke(this)
    }
}

/** Mockup .ico: 40 dp round surface button (48 dp touch target). */
@Composable
fun RoundIcon(name: String, label: String, size: Dp = 20.dp, onClick: () -> Unit) {
    // lays out at the mockup's 40 dp; the 48 dp touch target overhangs it by 4 dp on each side
    Box(Modifier.size(40.dp), Alignment.Center) {
        Box(Modifier.requiredSize(48.dp).clickable(onClickLabel = label, onClick = onClick), Alignment.Center) {
            Box(Modifier.size(40.dp).lift(CircleShape).clip(CircleShape).background(Wp.SF), Alignment.Center) { Ic(name, size) }
        }
    }
}

enum class BtnKind { ACC, DARK, GHOST, NT, KAKAO }

/** Mockup .btn: 52 dp pill. ACC = the one orange primary per screen (dark text). */
@Composable
fun Btn(text: String, kind: BtnKind = BtnKind.GHOST, modifier: Modifier = Modifier, enabled: Boolean = true, fg: Color? = null, h: Dp = 52.dp,
        size: TextUnit = 15.5.sp, lead: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val (bg, f) = when (kind) {
        BtnKind.ACC -> Wp.ACC to Wp.ON_ACC
        BtnKind.DARK -> Wp.FG to Wp.SF
        BtnKind.GHOST -> Color.Transparent to Wp.FG
        BtnKind.NT -> Wp.SF to Wp.FG
        BtnKind.KAKAO -> Wp.KAKAO to Color(0xFF191919)
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = h)
            .then(if (kind == BtnKind.NT) Modifier.lift(CircleShape, 1.dp) else Modifier)
            .clip(CircleShape).background(if (enabled || kind == BtnKind.GHOST) bg else Wp.LN)
            .then(if (kind == BtnKind.GHOST) Modifier.border(BorderStroke(1.5.dp, Wp.LN), CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick).semantics { role = Role.Button }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        lead?.invoke()
        Text(text, style = ts(size, FontWeight.SemiBold, (fg ?: f).copy(alpha = if (enabled) 1f else .6f), lh = 1.3f), textAlign = TextAlign.Center)
    }
}

/** Mockup .sect: small group label over a .list. */
@Composable
fun Sect(text: String) = Text(text, Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp).layout { m, c ->
    // CSS margin-bottom: -4px pulls the list up under the label
    val p = m.measure(c); val pull = 4.dp.roundToPx()
    layout(p.width, p.height - pull) { p.place(0, 0) }
}, style = ts(12.5.sp, FontWeight.SemiBold, Wp.MU, ls = .02.em))

/** Mockup .list: rows on one surface, hairline dividers. */
@Composable
fun WList(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().lift(shape).clip(shape).background(Wp.SF), content = content)
}

/** Mockup .li: 52 dp row; [last] drops the divider. */
@Composable
fun Li(text: String, last: Boolean = false, right: String? = null, rightColor: Color = Wp.MU, rightBold: Boolean = false, textColor: Color = Wp.FG,
       onClick: (() -> Unit)? = null, lead: (@Composable () -> Unit)? = null, sub: String? = null, trail: (@Composable () -> Unit)? = null) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            lead?.invoke()
            Column(Modifier.weight(1f)) {
                Text(text, style = ts(14.5.sp, color = textColor))
                if (sub != null) Text(sub, style = ts(12.5.sp, color = Wp.MU))
            }
            if (right != null) Text(right, style = ts(13.5.sp, if (rightBold) FontWeight.SemiBold else FontWeight.Normal, rightColor), textAlign = TextAlign.End)
            trail?.invoke()
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(Wp.LN))
    }
}

/** Mockup .tg: 44x26 switch, green when on. The whole row toggles (48 dp). */
@Composable
fun Toggle(on: Boolean) {
    Box(Modifier.size(44.dp, 26.dp).clip(CircleShape).background(if (on) Wp.OK else Wp.LN)) {
        Box(Modifier.padding(start = if (on) 21.dp else 3.dp, top = 3.dp).size(20.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun ToggleLi(text: String, on: Boolean, last: Boolean = false, onChange: (Boolean) -> Unit) {
    Column(Modifier.semantics { role = Role.Switch; stateDescription = if (on) "켜짐" else "꺼짐" }) {
        Li(text, last = last, onClick = { onChange(!on) }, trail = { Toggle(on) })
    }
}

/** Car colour dot (mockup .sw). */
@Composable
fun Swatch(color: Int, size: Dp = 12.dp) =
    Box(Modifier.size(size).clip(CircleShape).background(Color(color)).border(1.dp, Color(0x33000000), CircleShape))

/** Mockup .memo: icon + line on the page colour inside a card. */
@Composable
fun MemoLine(icon: String, text: String, modifier: Modifier = Modifier, trail: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(10.dp)).background(Wp.BG).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic(icon, 16.dp, Wp.MU)
        Text(text, Modifier.weight(1f), style = ts(13.5.sp))
        trail?.invoke()
    }
}

/** Mockup .tag: small pill, white text. */
@Composable
fun Tag(text: String, bg: Color, icon: String? = null) {
    Row(Modifier.clip(CircleShape).background(bg).padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (icon != null) Ic(icon, 12.dp, Color.White, stroke = 2.4f)
        Text(text, style = ts(12.sp, FontWeight.SemiBold, Color.White, lh = 1.5f))
    }
}

/** Mockup .chips span: 44 dp pill on the page colour. */
@Composable
fun Chip(text: String?, onClick: () -> Unit, label: String? = null, icon: String? = null, iconTint: Color = Wp.ACC) {
    Row(
        Modifier.heightIn(min = 44.dp).then(if (text == null) Modifier.width(44.dp) else Modifier).clip(CircleShape).background(Wp.BG)
            .clickable(onClickLabel = label, onClick = onClick).padding(horizontal = if (text == null) 0.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Ic(icon, 20.dp, iconTint, stroke = 2f)
        if (text != null) Text(text, style = ts(13.sp))
    }
}

/** Card with a small label and a text input, value underlined (mockup onboarding .card fields). */
@Composable
fun FieldCard(label: String, value: String, onChange: (String) -> Unit, placeholder: String = "", size: TextUnit = 20.sp,
              keyboard: KeyboardOptions = KeyboardOptions.Default, underline: Boolean = true, valuePad: Dp = 0.dp) {
    WCard {
        Text(label, style = ts(13.sp, color = Wp.MU))
        BasicTextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = keyboard,
            textStyle = ts(size, FontWeight.SemiBold), cursorBrush = SolidColor(Wp.FG),
            decorationBox = { inner ->
                Column {
                    Box(Modifier.padding(vertical = valuePad)) { if (value.isEmpty()) Text(placeholder, style = ts(size, color = Wp.MU)); inner() }
                    if (underline) Box(Modifier.fillMaxWidth().height(2.dp).background(Wp.FG))
                }
            })
    }
}

/** Mockup .steps: 4 bars, done ones in ink. */
@Composable
fun Steps(n: Int) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    repeat(4) { i -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i < n) Wp.FG else Wp.LN)) }
}

/** The brand tile (mockups ic-min-braun.jpg). */
@Composable
fun Brand(size: Dp, radius: Dp) = androidx.compose.foundation.Image(
    androidx.compose.ui.res.painterResource(kr.dot.wherepark.R.drawable.brand), null,
    Modifier.size(size).clip(RoundedCornerShape(radius)),
)

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))
