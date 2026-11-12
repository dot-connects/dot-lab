package kr.dot.sb.ui.screens

import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.cards.Ic
import kr.dot.sb.ui.cards.IconView
import kr.dot.sb.ui.cards.Txt
import kr.dot.sb.ui.cards.nightFilter
import kr.dot.sb.ui.cards.tap
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.state.Photo
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.LocalInk
import kotlin.math.abs

/** Photo themes (spec P1, P0 part): 추천 + the user's albums; on-device labels (자연, 반려동물 …) are P1. */
private const val RECOMMENDED = "추천"
private const val SHUFFLE_MS = 15_000L

/**
 * Photos page (design §4.4): photo fills, 1 s crossfade every 15 s, Ken Burns to 1.04 per photo; time top-right in
 * the photos time style; place + date bottom-right on a scrim; theme pill right-middle on a vertical swipe.
 * [active] = the page is settled and uncovered; nothing animates otherwise.
 */
@Composable
internal fun PhotosPage(state: StandbyState, config: AppConfig, actions: StandbyActions, active: Boolean) {
    val ink = LocalInk.current
    val act by rememberUpdatedState(actions)
    val themes = remember(state.photoBuckets) { listOf(RECOMMENDED) + state.photoBuckets }
    var theme by remember { mutableIntStateOf(0) }
    var pillKey by remember { mutableIntStateOf(0) }
    var pill by remember { mutableStateOf(false) }
    LaunchedEffect(pillKey) { if (pillKey > 0) { pill = true; delay(1_500); pill = false } }

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var dy = 0f
            detectVerticalDragGestures(
                onDragStart = { dy = 0f },
                onDragEnd = { if (abs(dy) > 48.dp.toPx()) {
                    theme = Math.floorMod(theme + if (dy < 0) 1 else -1, themes.size); pillKey++
                    act.photoTheme(themes.getOrNull(theme)?.takeIf { theme > 0 })
                } },
            ) { _, d -> dy += d }
        },
    ) {
        val w = maxWidth
        val h = maxHeight
        val port = h > w
        val s = min(w, h)
        val photos = state.photos
        when {
            photos == null -> Placeholder(Ic.LOCK, "사진 접근 권한 필요", "눌러서 설정") { actions.openSettings("photos") }
            photos.isEmpty() -> Placeholder(Ic.PHOTO, "사진 없음", "앨범을 추가하면 여기 나와요", null)
            else -> {
                var idx by remember { mutableIntStateOf(0) }
                LaunchedEffect(photos.size, active) {
                    while (active && photos.size > 1) { delay(SHUFFLE_MS); idx = (idx + 1) % photos.size }
                }
                // keyed by the photo itself: the 45 s reload swaps photos under the same index and must still fade
                Crossfade(photos[idx % photos.size], animationSpec = tween(1_000), label = "photo") { ph ->
                    KenBurns(ph, ink.night, active)
                }
                // Scrim: transparent until 55 % height, then to 60 % black (design §2.1).
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)))) // scrim
                val p = photos[idx % photos.size]
                // caption over the photo: white in every theme (candle ink at night), like the memories card
                val fg = lerp(Color.White, ink.ink, ink.night) // overPicture
                Column(
                    Modifier.align(Alignment.BottomEnd).padding(end = w * 0.08f, bottom = h * if (port) 0.1f else 0.14f)
                        .tap(p.uri?.let { u -> { act.openPhoto(u) } }), // spec P5: caption tap opens the gallery
                    horizontalAlignment = Alignment.End,
                ) {
                    Txt(p.place, s * 0.09f, fg, weight = 700, maxW = w * if (port) 0.8f else 0.5f, align = TextAlign.End)
                    Spacer(Modifier.height(s * 0.035f))
                    Txt(p.date, max(18.dp, s * 0.048f), fg, weight = 600, tnum = true, align = TextAlign.End)
                }
            }
        }
        // Time top-right (landscape x 72–92 %, y 10–28 %); portrait gets a wider, shorter box.
        val style = config.clockStyle(FaceId.PHOTOS_TIME)
        // the PHOTOS_TIME face places the time itself over the whole page (clock area owns the geometry)
        ClockFace(FaceId.PHOTOS_TIME, style, state, Modifier.fillMaxSize())
        AnimatedVisibility(
            pill, Modifier.align(Alignment.CenterEnd).padding(end = w * 0.04f),
            enter = fadeIn(tween(300)), exit = fadeOut(tween(300)),
        ) {
            val t = max(18.dp, s * 0.045f)
            Box(Modifier.clip(RoundedCornerShape(50)).background(ink.ink4.copy(alpha = 0.85f)).padding(horizontal = t * 0.9f, vertical = t * 0.6f)) {
                Txt(themes[theme.coerceIn(0, themes.lastIndex)], t, ink.ink, weight = 700)
            }
        }
    }
}

@Composable
private fun KenBurns(p: Photo, night: Float, active: Boolean) {
    val k = remember { Animatable(1f) }
    LaunchedEffect(active) { if (active) k.animateTo(1.04f, tween((SHUFFLE_MS + 1_000).toInt(), easing = LinearEasing)) }
    Image(
        p.image, null,
        Modifier.fillMaxSize().graphicsLayer { scaleX = k.value; scaleY = k.value },
        contentScale = ContentScale.Crop, colorFilter = nightFilter(night),
    )
}

/** §5.4 empty / permission state as a whole page: outlined icon, title, detail; centred on the page. */
@Composable
internal fun Placeholder(icon: Ic, title: String, detail: String?, onTap: (() -> Unit)?) {
    val ink = LocalInk.current
    BoxWithConstraints(Modifier.fillMaxSize().tap(onTap), contentAlignment = Alignment.Center) {
        val s = min(maxWidth, maxHeight)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            IconView(icon, ink.ink3, Modifier.size(s * 0.16f), ink.bg, stroke = true)
            Spacer(Modifier.height(s * 0.06f))
            Txt(title, max(22.dp, s * 0.06f), ink.ink2, weight = 700)
            if (detail != null) {
                Spacer(Modifier.height(s * 0.035f))
                Txt(detail, max(18.dp, s * 0.04f), ink.ink2, weight = 500)
            }
        }
    }
}
