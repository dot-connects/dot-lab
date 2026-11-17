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
import kr.dot.sb.ui.theme.burnInBleed
import kotlin.math.abs

/** plan-r15 B1: the first chip, every picked folder; the others are the picked folders by name. */
private const val ALL = "전체"

/**
 * Photos page (design §4.4): photo fills, 1 s crossfade every `photoIntervalSec` (r10, default 15 s), Ken Burns to
 * 1.04 per photo by day (none at night); the photo and its scrim overscan for the burn-in shift; time top-right in
 * the photos time style; place + date bottom-right on a scrim; theme pill right-middle on a vertical swipe.
 * [active] = the page is settled and uncovered; nothing animates otherwise.
 */
@Composable
internal fun PhotosPage(state: StandbyState, config: AppConfig, actions: StandbyActions, active: Boolean) {
    val ink = LocalInk.current
    val act by rememberUpdatedState(actions)
    // plan-r15 B1: the chips are a view filter over the picked folders (config.photoFilter); the folder list stays
    val themes = remember(state.photoFolders) { listOf<Pair<String?, String>>(null to ALL) + state.photoFolders }
    val theme = themes.indexOfFirst { it.first == config.photoFilter }.coerceAtLeast(0)
    val cur by rememberUpdatedState(theme to themes)
    var pillKey by remember { mutableIntStateOf(0) }
    var pill by remember { mutableStateOf(false) }
    LaunchedEffect(pillKey) { if (pillKey > 0) { pill = true; delay(1_500); pill = false } }

    BoxWithConstraints(
        Modifier.fillMaxSize().pointerInput(Unit) {
            var dy = 0f
            detectVerticalDragGestures(
                onDragStart = { dy = 0f },
                onDragEnd = { if (abs(dy) > 48.dp.toPx() && cur.second.size > 2) {
                    val (t, list) = cur
                    val next = Math.floorMod(t + if (dy < 0) 1 else -1, list.size); pillKey++
                    act.photoTheme(list[next].first)
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
            photos == null -> Placeholder(Ic.PHOTO, state.photoHint ?: "폴더를 골라 주세요", "눌러서 고르기") { actions.openSettings("photos") }
            photos.isEmpty() -> Placeholder(Ic.PHOTO, "사진 없음", "사진이 있는 폴더를 고르면 여기 나와요") { actions.openSettings("photos") }
            else -> {
                var idx by remember { mutableIntStateOf(0) }
                val shuffleMs = config.photoIntervalSec * 1000L
                LaunchedEffect(photos.size, active, shuffleMs) {
                    while (active && photos.size > 1) { delay(shuffleMs); idx = (idx + 1) % photos.size }
                }
                // keyed by the photo itself: the set reload swaps photos under the same index and must still fade
                Crossfade(photos[idx % photos.size], Modifier.fillMaxSize().burnInBleed(), animationSpec = tween(1_000), label = "photo") { ph ->
                    KenBurns(ph, ink.night, active, shuffleMs)
                }
                // Scrim: transparent until 55 % height, then to 60 % black (design §2.1).
                Box(Modifier.fillMaxSize().burnInBleed().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f)))) // scrim
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
                Txt(themes[theme.coerceIn(0, themes.lastIndex)].second, t, ink.ink, weight = 700)
            }
        }
    }
}

@Composable
private fun KenBurns(p: Photo, night: Float, active: Boolean, shuffleMs: Long) {
    val k = remember { Animatable(1f) }
    // day only: at night the photo holds still, so the page draws nothing between swaps (no frame every vsync all night)
    val zoom = active && night < 0.5f
    LaunchedEffect(zoom) { if (zoom) k.animateTo(1.04f, tween((shuffleMs + 1_000).toInt(), easing = LinearEasing)) }
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
