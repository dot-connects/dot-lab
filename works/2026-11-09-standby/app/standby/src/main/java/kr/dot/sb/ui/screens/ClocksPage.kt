package kr.dot.sb.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.cards.Txt
import kr.dot.sb.ui.cards.tap
import kr.dot.sb.settings.MyClocks
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.clock.ClockRegistry
import kr.dot.sb.ui.clock.FaceDefaults
import kr.dot.sb.ui.clock.StyleRow
import kr.dot.sb.ui.clock.spec
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.BURN_IN_BLEED
import kr.dot.sb.ui.theme.CandleLight
import kr.dot.sb.ui.theme.LocalTheme
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.burnInBleed
import kr.dot.sb.ui.theme.Ink
import kr.dot.sb.ui.theme.LocalInk

/** iPhone edit mode shrinks the face to 67 % (frames solar-edit, float-colormenu). */
private const val EDIT_SCALE = 0.67f

/**
 * Clocks page: vertical pager over the built faces of `AppConfig.myClocks` (plan-r10), each face with its own style.
 * Long press = iPhone edit mode (spec C8): face card at 67 %, neighbour names above/below, dots right, colour circle →
 * bottom sheet for the face's first colour or preset row ([kr.dot.sb.ui.clock.ClockSpec.sheetRow]), 완료 top-right;
 * vertical swipes still change the face. [page] (the shown face; null = the first) is hoisted so display-off keeps it.
 */
@Composable
internal fun ClocksPage(
    state: StandbyState,
    config: AppConfig,
    page: MutableState<FaceId?>,
    editing: Boolean,
    onEdit: (Boolean) -> Unit,
    sheetStart: Boolean,
    styleOf: (FaceId) -> ClockStyle,
    onPick: (FaceId, ClockStyle) -> Unit,
) {
    val faces = remember(config.myClocks) { ClockRegistry.pages(config.myClocks) }
    val pager = rememberPagerState(MyClocks.page(faces, page.value, 0)) { faces.size }
    val shown by rememberUpdatedState(faces)
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { i -> shown.getOrNull(i)?.let { page.value = it } } }
    // the list changed while showing: follow the face, else stay on the slot (clamped)
    LaunchedEffect(faces) {
        val i = MyClocks.page(faces, page.value, pager.currentPage)
        page.value = faces[i]
        if (i != pager.currentPage) pager.scrollToPage(i)
    }
    var sheet by remember { mutableStateOf(sheetStart && editing) }
    if (!editing && sheet) sheet = false
    val edit by rememberUpdatedState(onEdit)
    val k by animateFloatAsState(if (editing) 1f else 0f, tween(350, easing = FastOutSlowInEasing), label = "edit")
    val ink = LocalInk.current

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // own page colour (edit mode dims it), overscanned for the burn-in shift
        val theme = LocalTheme.current
        Box(Modifier.fillMaxSize().burnInBleed().background(lerp(ink.bg, ink.ink4, k))) { CandleLight(theme, maxOf(ink.night, k)) }
        val w = maxWidth
        val h = maxHeight
        val s = min(w, h)
        val m = Grid.margin(s)
        VerticalPager(
            pager,
            // pagers clip: the bleed goes on the pager, each page is padded back in (a full-bleed face draws past it)
            Modifier.fillMaxSize().burnInBleed().pointerInput(editing) { if (!editing) detectTapGestures(onLongPress = { edit(true) }) },
            flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 400f)),
        ) { p ->
            val face = faces.getOrElse(p) { faces.last() }
            Box(
                Modifier.fillMaxSize().padding(BURN_IN_BLEED).graphicsLayer {
                    val sc = 1f - (1f - EDIT_SCALE) * k
                    scaleX = sc; scaleY = sc
                    // Rounded card only while editing: a full-screen Solar must keep its square corners.
                    if (k > 0f) { clip = true; shape = RoundedCornerShape(s * 0.06f) }
                }.background(ink.bg),
            ) {
                CandleLight(theme, ink.night) // the page paints ink.bg over ThemeBackground: the candle light goes on it too
                ClockFace(face, styleOf(face), state, Modifier.fillMaxSize(), analog = config.analogStyle)
            }
        }

        val cur = pager.currentPage.coerceIn(0, faces.lastIndex)
        if (k > 0f) {
            val band = h * (1f - EDIT_SCALE) / 2
            val cap = max(16.dp, s * 0.04f)
            // Neighbour names above / below the card.
            faces.getOrNull(cur - 1)?.let {
                Box(Modifier.fillMaxWidth().height(band).alpha(k), contentAlignment = Alignment.Center) { Txt(spec(it).ko, cap, ink.ink, weight = 600) }
            }
            faces.getOrNull(cur + 1)?.let {
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(band).alpha(k), contentAlignment = Alignment.Center) {
                    Txt(spec(it).ko, cap, ink.ink, weight = 600)
                }
            }
            // 완료 pill, centred in the top band, right edge on the margin.
            val pillH = max(36.dp, s * 0.085f)
            Box(
                Modifier.align(Alignment.TopEnd).padding(top = (band - pillH) / 2, end = m)
                    .alpha(k).height(pillH).clip(RoundedCornerShape(50)).background(ink.ink3).tap { edit(false) }
                    .padding(horizontal = pillH * 0.5f),
                contentAlignment = Alignment.Center,
            ) { Txt("완료", pillH * 0.42f, ink.ink, weight = 700) }
            // Face dots right of the card.
            val dot = max(6.dp, s * 0.018f)
            AutoHideDots(
                faces.size, cur, false, vertical = true, dot = dot, always = true, dim = ink.ink2.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.CenterStart).offset(x = w * (1f + EDIT_SCALE) / 2 + (w * (1f - EDIT_SCALE) / 2 - dot) * 0.3f).alpha(k),
            )
            // White colour circle in the card's bottom-right corner (a face without a colour row has none).
            if (spec(faces[cur]).sheetRow != null && !sheet) {
                val d = s * EDIT_SCALE * 0.19f
                val right = w * (1f + EDIT_SCALE) / 2
                val bottom = h * (1f + EDIT_SCALE) / 2
                Box(
                    Modifier.offset(right - d * 1.3f, bottom - d * 1.4f).size(d).alpha(k).clip(CircleShape)
                        .background(ink.ink.copy(alpha = 0.28f)).tap { sheet = true },
                    contentAlignment = Alignment.Center,
                ) { Box(Modifier.size(d * 0.62f).clip(CircleShape).background(ink.ink)) }
            }
        } else {
            AutoHideDots(
                faces.size, cur, pager.isScrollInProgress, vertical = true, dot = max(5.dp, s * 0.013f),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = m / 2),
            )
        }

        AnimatedVisibility(
            sheet && editing, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(350)),
            exit = slideOutVertically(tween(350, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(350)),
        ) {
            val face = faces[cur]
            spec(face).sheetRow?.let { row ->
                ColorSheet(row, styleOf(face), ink, s, if (h > w) w - m * 2 else w * 0.74f, { onPick(face, it) }) { sheet = false }
            }
        }
    }
}

/** Bottom colour sheet (frame float-colormenu): "색상" centred, ✕ right, one row of swatches (scrolls when narrow). */
@Composable
private fun ColorSheet(row: StyleRow, style: ClockStyle, ink: Ink, s: Dp, width: Dp, onPick: (ClockStyle) -> Unit, onClose: () -> Unit) {
    val pad = max(16.dp, s * 0.05f)
    val d = max(30.dp, min(46.dp, s * 0.085f))
    Column(
        Modifier.width(width).clip(RoundedCornerShape(topStart = pad * 1.4f, topEnd = pad * 1.4f)).background(ink.ink3)
            .tap {}.padding(top = pad * 0.9f, bottom = pad * 1.3f),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = pad)) {
            Txt("색상", max(20.dp, s * 0.05f), ink.ink, Modifier.align(Alignment.Center), weight = 700)
            Box(
                Modifier.align(Alignment.CenterEnd).size(d * 0.8f).clip(CircleShape).background(ink.ink4).tap(onClose),
                contentAlignment = Alignment.Center,
            ) { Cross(ink.ink2, d * 0.32f) }
        }
        Spacer(Modifier.height(pad * 1.1f))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = pad),
            horizontalArrangement = Arrangement.spacedBy(d * 0.42f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            fun grad(cs: List<Long>): Brush = Brush.linearGradient(cs.map { ink.accent(Color(it)) })
            fun solid(c: Long): Brush = androidx.compose.ui.graphics.SolidColor(ink.accent(Color(c)))
            when (row) {
                StyleRow.SOLAR -> ClockStyle.SOLAR_SETS.forEach { set ->
                    Swatch(d, style.bgColor == set[0], ink, grad(set.take(2))) {
                        onPick(style.copy(bgColor = set[0], bgColor2 = set[1], color = set[2], color2 = set[3]))
                    }
                }
                StyleRow.PAIR -> FaceDefaults.PAIRS.forEachIndexed { i, set ->
                    Swatch(d, (style.pairPreset ?: 0) == i, ink, grad(set)) { onPick(style.copy(pairPreset = i)) }
                }
                StyleRow.TRIO -> FaceDefaults.TRIOS.forEachIndexed { i, set ->
                    Swatch(d, (style.trioPreset ?: 0) == i, ink, grad(set)) { onPick(style.copy(trioPreset = i)) }
                }
                StyleRow.LED -> FaceDefaults.LEDS.forEach { c ->
                    Swatch(d, (style.ledColor ?: FaceDefaults.LEDS[0]) == c, ink, solid(c)) { onPick(style.copy(ledColor = c)) }
                }
                else -> ClockStyle.SWATCHES.forEach { c ->
                    Swatch(d, row.color(style) == c, ink, solid(c)) { onPick(row.withColor(style, c)) }
                }
            }
        }
    }
}

@Composable
private fun Swatch(d: Dp, selected: Boolean, ink: Ink, fill: Brush, onClick: () -> Unit) {
    Box(
        Modifier.size(d * 1.24f).then(if (selected) Modifier.border(2.dp, ink.ink, CircleShape) else Modifier).tap(onClick),
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.size(d).clip(CircleShape).background(fill)) }
}

/** ✕ glyph drawn as two rounded bars (no icon for it in the card set). */
@Composable
private fun Cross(color: Color, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        listOf(45f, -45f).forEach { r ->
            Box(Modifier.width(size * 1.25f).height(2.dp).graphicsLayer { rotationZ = r }.clip(RoundedCornerShape(50)).background(color))
        }
    }
}
