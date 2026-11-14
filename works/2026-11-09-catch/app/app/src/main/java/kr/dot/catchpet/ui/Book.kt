package kr.dot.catchpet.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * The menus' look (operator 11-14 picked mockup A, mockups/menus.html): the catch yard's sky and grass, paper cards
 * with a thick ink outline that sit on the page with a hard shadow, Jua for headings and Gowun Dodum for text
 * (Google Fonts through Play services, so the APK stays small; the system font until they arrive).
 */
object Book {
    val Sky = Color(0xFFBFE6FF)
    val Grass = Color(0xFFD9F2C9)
    val Paper = Color(0xFFFFFDF6)
    val Ink = Color(0xFF24331F)
    val Muted = Color(0xFF5D6B57)
    val Sun = Color(0xFFFFD84D)
    val Leaf = Color(0xFF7CC46B)
    val Field = Color(0xFFE6F0DC)
    val Warn = Color(0xFFB3261E)

    private val fonts = GoogleFont.Provider("com.google.android.gms.fonts", "com.google.android.gms", R.array.com_google_android_gms_fonts_certs)
    val Display = FontFamily(Font(GoogleFont("Jua"), fonts))
    val Body = FontFamily(Font(GoogleFont("Gowun Dodum"), fonts))

    val LINE = 2.5.dp
    val DROP = 4.dp
}

/** A menu screen: sky band over grass, safe-area padded, scrolling, Gowun Dodum ink text by default. */
@Composable
fun BookPage(scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Book.Sky, 0.28f to Book.Sky, 0.28f to Book.Grass, 1f to Book.Grass))) {
        CompositionLocalProvider(LocalTextStyle provides TextStyle(fontFamily = Book.Body, color = Book.Ink, fontSize = 15.sp)) {
            val m = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 18.dp, vertical = 8.dp)
            Column(if (scroll) m.verticalScroll(rememberScrollState()) else m, verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
        }
    }
}

/** A heading in Jua with a white lift, like the mockup's titles. */
@Composable
fun Heading(text: String, size: TextUnit = 30.sp, modifier: Modifier = Modifier) {
    Text(text, modifier, fontFamily = Book.Display, fontSize = size, color = Book.Ink,
        style = TextStyle(shadow = Shadow(Color.White, Offset(0f, 4f), 0f)))
}

@Composable
fun Note(text: String, modifier: Modifier = Modifier, color: Color = Book.Muted) {
    Text(text, modifier, fontSize = 13.sp, color = color, lineHeight = 19.sp)
}

/** Paper with an ink outline and a hard drop shadow; dashed = an empty slot (no shadow). */
fun Modifier.inkCard(radius: Dp = 18.dp, fill: Color = Book.Paper, dashed: Boolean = false, drop: Boolean = !dashed): Modifier =
    this.drawBehind {
        val r = CornerRadius(radius.toPx())
        val line = Book.LINE.toPx()
        if (drop) drawRoundRect(Book.Ink, topLeft = Offset(0f, Book.DROP.toPx()), size = size, cornerRadius = r)
        drawRoundRect(fill, size = size, cornerRadius = r)
        drawRoundRect(if (dashed) Book.Muted else Book.Ink, topLeft = Offset(line / 2, line / 2),
            size = size.copy(size.width - line, size.height - line), cornerRadius = r,
            style = Stroke(line, pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) else null))
    }

/** A panel: a card holding one group of settings, with an optional Jua title. */
@Composable
fun Panel(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(bottom = Book.DROP).inkCard(20.dp).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (title != null) Text(title, fontFamily = Book.Display, fontSize = 18.sp)
        content()
    }
}

/** A round ink pill (back, counters, small actions). */
@Composable
fun InkPill(text: String, fill: Color = Book.Paper, onClick: (() -> Unit)? = null) {
    Text(text, fontFamily = Book.Display, fontSize = 15.sp, color = Book.Ink,
        modifier = Modifier.padding(bottom = 2.dp).inkCard(999.dp, fill, drop = false)
            .let { if (onClick != null) it.clip(RoundedCornerShape(50)).clickable(onClick = onClick) else it }
            .padding(horizontal = 14.dp, vertical = 6.dp))
}

/** The big action button: yellow (primary) or paper; pressed-in look when disabled. */
@Composable
fun InkButton(text: String, enabled: Boolean = true, primary: Boolean = true, onClick: () -> Unit) {
    val fill = when { !enabled -> Book.Field; primary -> Book.Sun; else -> Book.Paper }
    Box(
        Modifier.fillMaxWidth().padding(bottom = Book.DROP).inkCard(16.dp, fill, drop = enabled)
            .clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, fontFamily = Book.Display, fontSize = 18.sp, color = if (enabled) Book.Ink else Book.Muted, textAlign = TextAlign.Center) }
}

/** Two or three choices in one rounded track, the chosen one yellow. */
@Composable
fun <T> Seg(options: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().inkCard(999.dp, Book.Field, drop = false).padding(4.dp)) {
        for ((label, v) in options) {
            val on = v == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .let { if (on) it.background(Book.Sun).border(2.dp, Book.Ink, RoundedCornerShape(50)) else it }
                    .clickable { onSelect(v) }.padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, fontFamily = Book.Display, fontSize = 16.sp) }
        }
    }
}

/** An ink switch: green track with the knob right when on. */
@Composable
fun InkSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Box(
        Modifier.width(54.dp).height(30.dp).clip(RoundedCornerShape(50))
            .background(if (checked) Book.Leaf else Book.Field).border(Book.LINE, Book.Ink, RoundedCornerShape(50))
            .clickable { onChange(!checked) },
    ) {
        Box(Modifier.align(Alignment.CenterStart).offset(x = if (checked) 27.dp else 3.dp).size(22.dp)
            .background(Color.White, CircleShape).border(2.dp, Book.Ink, CircleShape))
    }
}
