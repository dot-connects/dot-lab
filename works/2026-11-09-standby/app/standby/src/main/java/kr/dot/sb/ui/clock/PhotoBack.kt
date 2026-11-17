package kr.dot.sb.ui.clock

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import kr.dot.sb.ui.cards.Txt
import kr.dot.sb.ui.cards.nightFilter
import kr.dot.sb.ui.cards.tap
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.burnInBleed

/**
 * How [ClockFace] draws 사진 위 시간's photo (plan-r15 E4): the next one every [everySec] (the photos page interval;
 * 0 = hold the first); [onPick] = a tap on the "폴더를 골라 주세요" hint (opens the photos settings).
 */
class PhotoBack(val everySec: Int = 0, val onPick: (() -> Unit)? = null)

/**
 * A photo of `state.photos` filling the face (overscanned for the burn-in shift, night-filtered), crossfading 1 s to
 * the next, under a scrim that darkens towards the top where the time sits. No photos = the hint, centred.
 */
@Composable
internal fun PhotoBackdrop(state: StandbyState, back: PhotoBack, ink: Ink) {
    val photos = state.photos
    if (photos.isNullOrEmpty()) {
        BoxWithConstraints(Modifier.fillMaxSize().tap(back.onPick), contentAlignment = Alignment.Center) {
            val s = min(maxWidth, maxHeight)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Txt(if (photos == null) state.photoHint ?: "폴더를 골라 주세요" else "사진이 있는 폴더를 골라 주세요", max(18.dp, s * 0.06f), ink.ink2, weight = 700)
                if (back.onPick != null) {
                    Spacer(Modifier.height(s * 0.03f))
                    Txt("눌러서 고르기", max(14.dp, s * 0.04f), ink.ink3, weight = 500)
                }
            }
        }
        return
    }
    var idx by remember { mutableIntStateOf(0) }
    LaunchedEffect(photos.size, back.everySec) {
        while (back.everySec > 0 && photos.size > 1) { delay(back.everySec * 1000L); idx = (idx + 1) % photos.size }
    }
    Crossfade(photos[idx % photos.size], Modifier.fillMaxSize().burnInBleed(), animationSpec = tween(1_000), label = "photo") { p ->
        Image(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = nightFilter(state.night))
    }
    Box(Modifier.fillMaxSize().burnInBleed().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.5f to Color.Transparent))) // scrim
}
