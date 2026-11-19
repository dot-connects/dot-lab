package kr.dot.wherepark.family

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val BG = Color(0xFFE9E7E3)
internal val SURF = Color(0xFFF6F5F2)
internal val FG = Color(0xFF161616)
internal val MU = Color(0xFF6C6A66)
internal val LINE = Color(0xFFD5D2CC)
internal val ACC = Color(0xFFFF6A13)
internal val ON_ACC = Color(0xFF1A0F06)
internal val KAKAO = Color(0xFFFEE500)

@Composable
internal fun Screen(title: String?, onBack: (() -> Unit)?, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(BG).statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (title != null) Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Box(Modifier.size(48.dp).clickable(onClick = onBack), Alignment.Center) { Ic(kr.dot.wherepark.R.drawable.ic_back, 22) }
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FG)
        }
        content()
    }
}

@Composable
internal fun Ic(@DrawableRes id: Int, size: Int, tint: Color = FG) = Icon(painterResource(id), null, Modifier.size(size.dp), tint = tint)

/** [kind]: "acc" (one per screen), "plain" (outlined), "fill" (custom [bg]/[fg]). */
@Composable
internal fun Btn(text: String, kind: String = "plain", bg: Color = Color.Transparent, fg: Color = FG, enabled: Boolean = true, onClick: () -> Unit) {
    val (b, f) = when (kind) { "acc" -> ACC to ON_ACC; "fill" -> bg to fg; else -> Color.Transparent to FG }
    Box(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp)).background(b.copy(alpha = if (enabled) 1f else .4f))
            .then(if (kind == "plain") Modifier.border(BorderStroke(1.5.dp, FG), RoundedCornerShape(14.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        Alignment.Center,
    ) { Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = f, textAlign = TextAlign.Center) }
}

@Composable
internal fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) =
    Box(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SURF).padding(16.dp)) { content() }
