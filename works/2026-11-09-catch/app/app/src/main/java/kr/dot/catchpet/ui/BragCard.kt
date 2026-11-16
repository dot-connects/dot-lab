package kr.dot.catchpet.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * After a catch: the 자랑하기 card itself over a dimmed game, with [닫기] and [자랑하기] (operator 11-16, mockup
 * widget-brag 2-A). Taps outside do nothing: it stays until one of the two is pressed.
 */
@Composable
fun BragCard(b: Brag, onClose: () -> Unit, onShare: () -> Unit) {
    val ctx = LocalContext.current
    val card by produceState<ImageBitmap?>(null, b) {
        value = withContext(Dispatchers.Default) { runCatching { ShareCard.render(b.ready(ctx)).asImageBitmap() }.getOrNull() }
    }
    Box(
        Modifier.fillMaxSize().background(Color(0xA8121810))
            // swallow every tap so the claw underneath does not move
            .clickable(interactionSource = remember0(), indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.safeDrawingPadding().padding(24.dp).widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(Modifier.fillMaxWidth(0.86f).aspectRatio(4f / 5f).clip(RoundedCornerShape(18.dp)).background(Book.Grass)) {
                card?.let { Image(it, "자랑 카드", Modifier.fillMaxSize()) }
            }
            Row(Modifier.fillMaxWidth(0.86f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)) { InkButton("닫기", primary = false, onClick = onClose) }
                Box(Modifier.weight(1f)) { InkButton("자랑하기", onClick = onShare) }
            }
        }
    }
}

@Composable
private fun remember0() = androidx.compose.runtime.remember { MutableInteractionSource() }
