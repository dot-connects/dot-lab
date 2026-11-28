package kr.dot.catchpet.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.catchpet.R

/**
 * The menus' look: felt (operator 11-28, mockups/next7v4 v5.2 screens 4-6). Sky over grass under a wool texture,
 * felt pills and panels with a dashed stitch (FeltArt.pill), Jua for headings and Gowun Dodum for text (Google Fonts
 * through Play services, so the APK stays small; the system font until they arrive).
 */
object Book {
    val Sky = Color(0xFFB9DCF2)
    val Grass = Color(0xFFA9CF78)
    val Paper = Color(0xFFFFFAF0)
    val Cream = Color(0xFFFBF3E2)
    val Ink = Color(0xFF3B2E22)
    val Muted = Color(0xFF7A6A58)
    val Sun = Color(0xFFF6C744)
    val Leaf = Color(0xFF8FBF5E)
    val Field = Color(0xFFEFE6D2)
    val Warn = Color(0xFFB3261E)

    private val fonts = GoogleFont.Provider("com.google.android.gms.fonts", "com.google.android.gms", R.array.com_google_android_gms_fonts_certs)
    val Display = FontFamily(Font(GoogleFont("Jua"), fonts))
    val Body = FontFamily(Font(GoogleFont("Gowun Dodum"), fonts))

    val LINE = 2.5.dp
    val DROP = 4.dp
}

/** A felt shape behind a composable (FeltArt.pill): the colour, wool texture, a dashed stitch inside. */
fun Modifier.felt(color: Color, radius: Dp = 18.dp, stitch: Boolean = true): Modifier = drawBehind {
    drawIntoCanvas { c ->
        val cv = c.nativeCanvas
        cv.save(); cv.scale(density, density)
        val w = size.width / density; val h = size.height / density
        FeltArt.pill(cv, android.graphics.RectF(0f, 0f, w, h), color.toArgb(), radius = minOf(radius.value, h / 2), stitch = stitch)
        cv.restore()
    }
}

/** A menu screen: sky band over grass under wool, safe-area padded, scrolling, Gowun Dodum ink text by default. */
@Composable
fun BookPage(scroll: Boolean = true, sky: Float = 0.28f, content: @Composable ColumnScope.() -> Unit) {
    val res = LocalContext.current.resources
    remember { FeltArt.load(res) }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Book.Sky, sky to Book.Sky, sky to Book.Grass, 1f to Book.Grass))
        .drawBehind { drawIntoCanvas { c -> FeltArt.cloth(c.nativeCanvas, size.width, size.height, 180f * density) } }) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = Book.Body, color = Book.Ink, fontSize = 15.sp)) {
            val m = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 16.dp, vertical = 8.dp)
            Column(if (scroll) m.verticalScroll(rememberScrollState()) else m, verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

/** A heading in Jua with a cream lift, like the mockup's titles. */
@Composable
fun Heading(text: String, size: TextUnit = 38.sp, modifier: Modifier = Modifier) {
    Text(text, modifier, fontFamily = Book.Display, fontSize = size, color = Book.Ink,
        style = TextStyle(shadow = Shadow(Book.Paper, Offset(0f, 5f), 0f)))
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier, color: Color = Book.Muted) {
    Text(text, modifier, fontSize = 12.5.sp, color = color, lineHeight = 19.sp)
}

/** A felt card; dashed = an empty slot (plain felt, no stitch). [drop] is ignored since the felt redesign. */
@Suppress("UNUSED_PARAMETER")
fun Modifier.inkCard(radius: Dp = 18.dp, fill: Color = Book.Paper, dashed: Boolean = false, drop: Boolean = !dashed): Modifier =
    if (dashed) felt(Book.Field, radius, stitch = false) else felt(fill, radius)

/** A panel: a felt card holding one group of settings, with an optional Jua title. */
@Composable
fun Panel(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().felt(Book.Paper, 22.dp).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null) Text(title, fontFamily = Book.Display, fontSize = 18.sp)
        content()
    }
}

/** A round felt chip (back, counters, small actions). */
@Composable
fun InkPill(text: String, fill: Color = Book.Cream, onClick: (() -> Unit)? = null) {
    Text(text, fontFamily = Book.Display, fontSize = 15.sp, color = Book.Ink,
        modifier = Modifier.felt(fill, 999.dp)
            .let { if (onClick != null) it.clip(RoundedCornerShape(50)).clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 6.dp))
}

/** The big felt button: sun (primary) or paper; paper with muted text when disabled. */
@Composable
fun InkButton(text: String, enabled: Boolean = true, primary: Boolean = true, onClick: () -> Unit) {
    val fill = if (enabled && primary) Book.Sun else Book.Paper
    Box(
        Modifier.fillMaxWidth().felt(fill, 999.dp).clip(RoundedCornerShape(50)).clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 13.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontFamily = Book.Display, fontSize = 18.sp, color = if (enabled) Book.Ink else Book.Muted, textAlign = TextAlign.Center) }
}

/** Two or three choices in one felt track, the chosen one on a sun felt thumb. */
@Composable
fun <T> Seg(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().felt(Book.Field, 999.dp, stitch = false).padding(4.dp)) {
        for ((label, v) in options) {
            val on = v == selected
            Box(
                Modifier.weight(1f).let { if (on) it.felt(Book.Sun, 999.dp, stitch = false) else it }
                    .clip(RoundedCornerShape(50)).clickable { onSelect(v) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontFamily = Book.Display, fontSize = 15.sp, color = if (on) Book.Ink else Book.Muted) }
        }
    }
}

/** A felt switch: green track with the cream knob right when on. */
@Composable
fun InkSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val x by animateDpAsState(if (checked) 25.dp else 3.dp, label = "knob")
    Box(
        Modifier.width(52.dp).height(30.dp).clip(RoundedCornerShape(50))
            .background(if (checked) Book.Leaf else Color(0xFFDDD2BD)).clickable { onChange(!checked) },
    ) {
        Box(Modifier.align(Alignment.CenterStart).offset(x = x).size(24.dp)
            .background(Book.Paper, CircleShape).border(1.dp, Color(0x226E5640), CircleShape))
    }
}
