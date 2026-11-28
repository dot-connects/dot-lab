package kr.dot.catchpet.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * After a catch (felt mockup v5.2 screen 2, operator 11-28): "새로 뽑았다!" ribbon, the 자랑하기 card itself, the
 * buttons right under the card ([자랑하기] felt, [계속 뽑기] as text), then "도감에 넣었어요 · 펫으로 만들 수 있어요 ›"
 * (opens the dex), all centred over the dimmed game. Taps outside do nothing: it stays until a button is pressed.
 */
@Composable
fun BragCard(b: Brag, onClose: () -> Unit, onDex: () -> Unit, onShare: () -> Unit) {
    val ctx = LocalContext.current
    val card by produceState<ImageBitmap?>(null, b) {
        value = withContext(Dispatchers.Default) { runCatching { ShareCard.render(b.ready(ctx)).asImageBitmap() }.getOrNull() }
    }
    // entrance (mockup firstCard): the dim, the card springs up, the ribbon drops in, the buttons rise
    val fade = remember { Animatable(0f) }
    val cardK = remember { Animatable(0f) }
    val ribK = remember { Animatable(0f) }
    val btnK = remember { Animatable(0f) }
    LaunchedEffect(b) {
        launch { fade.animateTo(1f, tween(260)) }
        launch { cardK.animateTo(1f, tween(620, 60, CubicBezierEasing(.25f, 1f, .5f, 1f))) }
        launch { ribK.animateTo(1f, tween(560, 160, CubicBezierEasing(.3f, 1.2f, .5f, 1f))) }
        launch { btnK.animateTo(1f, tween(320, 760)) }
    }
    Box(
        Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.background(Color(0xBF1D150F))
            // swallow every tap so the claw underneath does not move
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.safeDrawingPadding().padding(horizontal = 24.dp, vertical = 16.dp).widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.graphicsLayer {
                val k = ribK.value
                alpha = k.coerceIn(0f, 1f); translationY = (1f - k) * -50.dp.toPx(); rotationZ = -2f - 12f * (1f - k)
            }.felt(0xFFE0794A.toInt(), 12f).padding(horizontal = 24.dp, vertical = 6.dp)) {
                Text("새로 뽑았다!", fontFamily = Book.Display, fontSize = 26.sp, color = Color(0xFFFFFAF0))
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(0.82f).aspectRatio(4f / 5f).graphicsLayer {
                val k = cardK.value
                alpha = (k * 1.6f).coerceIn(0f, 1f); translationY = (1f - k) * 40.dp.toPx()
                val s = 0.72f + 0.28f * k; scaleX = s; scaleY = s; rotationZ = -6f * (1f - k)
            }.clip(RoundedCornerShape(18.dp)).background(Book.Grass)) {
                card?.let { Image(it, "자랑 카드", Modifier.fillMaxSize()) }
            }
            Column(Modifier.fillMaxWidth(0.86f).padding(top = 18.dp).graphicsLayer {
                alpha = btnK.value; translationY = (1f - btnK.value) * 14.dp.toPx()
            }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).felt(FeltArt.SUN, 999f).clickable(onClick = onShare)
                    .padding(vertical = 13.dp), contentAlignment = Alignment.Center) {
                    Text("자랑하기", fontFamily = Book.Display, fontSize = 18.sp, color = Color(0xFF3B2E22))
                }
                Text("계속 뽑기", Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClose).padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 14.sp, color = Color(0xD9FFFAF0), textDecoration = TextDecoration.Underline)
                Text(
                    "도감에 넣었어요 · 펫으로 만들 수 있어요 ›",
                    Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onDex).padding(4.dp),
                    fontSize = 13.sp, color = Color(0xEBFFFAF0),
                )
            }
        }
    }
}

/** The felt pill (FeltArt) behind a composable: colour, wool texture, dashed stitch. */
private fun Modifier.felt(color: Int, radiusDp: Float) = drawBehind {
    drawIntoCanvas { c ->
        val cv = c.nativeCanvas
        cv.save(); cv.scale(density, density)
        val w = size.width / density; val h = size.height / density
        FeltArt.pill(cv, android.graphics.RectF(0f, 0f, w, h), color, radius = minOf(radiusDp, h / 2))
        cv.restore()
    }
}
