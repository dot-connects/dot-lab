package kr.dot.sb.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AnalogStyle
import kr.dot.sb.model.AnalogTicks
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Background
import kr.dot.sb.model.ClockLayout
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.clock.ClockRegistry
import kr.dot.sb.ui.clock.ColorShuffle
import kr.dot.sb.ui.clock.FaceDefaults
import kr.dot.sb.ui.clock.Family
import kr.dot.sb.ui.clock.StyleRow
import kr.dot.sb.ui.clock.spec
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.clockStyle
import kotlin.math.roundToInt

// ---- 시계 (mockup SCREENS.clock): preview + 넣기/꾸미기/빼기, 내 시계 strip, 둘러보기 gallery ---------------------------

/** The "우리만" badge (mockup `.uq`). */
private val OURS = Color(0xFFFFAD5C)
private val TILE_W = 118.dp
private val TILE_GAP = 10.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClockScreen(config: AppConfig, update: Update, nav: Nav, back: () -> Unit) {
    var sel by rememberSaveable { mutableStateOf(config.myClocks.firstOrNull() ?: FaceId.DIGITAL) }
    var fam by rememberSaveable { mutableStateOf<Family?>(null) }
    val state = remember(config) { Sample.state(config) }
    val gallery = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val first = Slide(spec(sel).ko) { l -> ClockFace(sel, config.clockStyle(sel), l.state, Modifier.fillMaxSize(), analog = config.analogStyle) }
    Page(
        "시계", back, preview = Preview(config, withPages(first, config, skip = "시계")),
        miniActions = { ClockActions(sel, config, update, nav, compact = true) },
    ) {
        ClockActions(sel, config, update, nav, compact = false)

        val my = config.myClocks
        Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
            T("내 시계 · ${my.size}개", 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, bottom = 8.dp))
            MyStrip(my, sel, config, state, update, { sel = it }) { scope.launch { gallery.bringIntoView() } }
            T(
                "스탠바이에서 시계 페이지를 위아래로 넘기면 이 순서로 바뀌어요. 누르면 위 미리보기에 보이고, 길게 눌러 옆으로 끌면 순서가 바뀌어요.",
                13.sp, 400, SK.muted, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), lineHeight = 18.sp,
            )
        }

        Column(Modifier.fillMaxWidth().padding(top = 24.dp).bringIntoViewRequester(gallery)) {
            T("둘러보기 · ${ClockRegistry.all.size}개", 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, bottom = 8.dp))
            FamilyChips(fam) { fam = it }
            Gap(12.dp)
            ClockRegistry.all.filter { fam == null || spec(it).family == fam }.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { f -> GalleryCard(f, f == sel, f in my, config, state, Modifier.weight(1f)) { sel = f } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            T("누르면 위 미리보기에 보여요. 마음에 들면 \"내 시계에 넣기\"를 눌러요.", 13.sp, 400, SK.muted, Modifier.padding(horizontal = 16.dp), lineHeight = 18.sp)
        }

        More {
            Section(null) {
                item {
                    ControlRow("시간 표시") {
                        Segmented(
                            listOf("폰 설정 따라", "12시간", "24시간"), when (config.use24h) { null -> 0; false -> 1; true -> 2 },
                            { i -> update { it.copy(use24h = when (i) { 0 -> null; 1 -> false; else -> true }) } },
                        )
                    }
                }
            }
        }
    }
}

/** Name of the previewed clock + [꾸미기][빼기] or [내 시계에 넣기] (mockup `clockAct`); [compact] = inside the mini preview. */
@Composable
private fun ClockActions(face: FaceId, config: AppConfig, update: Update, nav: Nav, compact: Boolean) {
    val my = config.myClocks
    val i = my.indexOf(face)
    Row(Modifier.fillMaxWidth().padding(top = if (compact) 0.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            T(spec(face).ko, 15.sp, 700, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (!compact) T(if (i >= 0) "내 시계 ${i + 1}번째" else "둘러보는 중", 12.sp, 500, SK.muted, Modifier.padding(start = 8.dp), maxLines = 1)
        }
        if (i >= 0) {
            ActionButton("꾸미기", { nav.push(Routes.clock(face)) }, primary = false)
            ActionButton("빼기", { update { it.copy(myClocks = MyClocks.remove(it.myClocks, face)) } }, primary = false, enabled = my.size > 1)
        } else {
            ActionButton("내 시계에 넣기", { update { it.copy(myClocks = MyClocks.add(it.myClocks, face)) } })
        }
    }
}

/** A clock drawn at the fold cover's size and scaled to the width; [inSlot] = the slot form (gallery). */
@Composable
private fun Thumb(face: FaceId, config: AppConfig, state: StandbyState, inSlot: Boolean, border: Color?) {
    val shape = RoundedCornerShape(8.dp)
    // the slot form is drawn at half the cover so it is a card-sized slot, not the compact fallback
    val (w, h) = if (inSlot) 376 to 238 else PreviewDevice.FOLD_COVER.w to PreviewDevice.FOLD_COVER.h
    Box(Modifier.fillMaxWidth().then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier)) {
        PreviewFrame(w, h, Modifier.fillMaxWidth(), radius = 8.dp) {
            // 사진 위 시간 sits on a photo in StandBy; show it on the sample one
            if (face == FaceId.PHOTOS_TIME) state.photos?.firstOrNull()?.let { Image(it.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ClockFace(face, config.clockStyle(face), state, Modifier.fillMaxSize(), inSlot = inSlot, analog = config.analogStyle)
        }
    }
}

/** 전체 + the five families in one sideways-scrolling line (mockup `.famchips`). */
@Composable
private fun FamilyChips(fam: Family?, onPick: (Family?) -> Unit) {
    val acc = LocalAccent.current
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (listOf(null) + Family.entries).forEach { f ->
            val on = f == fam
            Box(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).clickable { onPick(f) },
                Alignment.Center,
            ) {
                Box(Modifier.heightIn(min = 38.dp).clip(RoundedCornerShape(50)).background(if (on) acc else SK.surface2).padding(horizontal = 14.dp), Alignment.Center) {
                    T(f?.ko ?: "전체", 14.sp, if (on) 700 else 500, if (on) SK.bg else SK.text, maxLines = 1)
                }
            }
        }
    }
}

/** 내 시계: tap selects, long-press + drag sideways reorders ([MyClocks.move]), ✕ removes, + scrolls to 둘러보기. */
@Composable
private fun MyStrip(my: List<FaceId>, sel: FaceId, config: AppConfig, state: StandbyState, update: Update, onSelect: (FaceId) -> Unit, onAdd: () -> Unit) {
    val acc = LocalAccent.current
    val step = with(LocalDensity.current) { (TILE_W + TILE_GAP).toPx() }
    var drag by remember { mutableStateOf<Pair<Int, Float>?>(null) } // index, dx px
    fun move(from: Int, to: Int) = update { it.copy(myClocks = MyClocks.move(it.myClocks, from, to)) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        my.forEachIndexed { i, f ->
            val on = f == sel
            val dx = drag?.takeIf { it.first == i }?.second
            Column(
                Modifier.width(TILE_W).zIndex(if (dx != null) 1f else 0f).graphicsLayer { translationX = dx ?: 0f; if (dx != null) { scaleX = 1.05f; scaleY = 1.05f } }
                    .clip(RoundedCornerShape(14.dp)).background(if (on) acc.copy(alpha = 0.16f) else SK.surface)
                    .pointerInput(f, i, my.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { drag = i to 0f },
                            onDrag = { c, d -> c.consume(); drag = drag?.let { it.first to it.second + d.x } },
                            onDragEnd = { drag?.let { (from, x) -> move(from, from + (x / step).roundToInt()) }; drag = null },
                            onDragCancel = { drag = null },
                        )
                    }
                    .semantics {
                        customActions = listOfNotNull(
                            if (i > 0) CustomAccessibilityAction("앞으로 옮기기") { move(i, i - 1); true } else null,
                            if (i < my.lastIndex) CustomAccessibilityAction("뒤로 옮기기") { move(i, i + 1); true } else null,
                        )
                    }
                    .clickable(onClickLabel = "미리보기에 보기") { onSelect(f) }.padding(8.dp),
            ) {
                Box {
                    Thumb(f, config, state, inSlot = false, border = if (on) acc else null)
                    if (my.size > 1) Box(
                        Modifier.align(Alignment.TopEnd).offset(x = 14.dp, y = (-14).dp).size(48.dp).clip(CircleShape)
                            .clickable(onClickLabel = "빼기") { update { it.copy(myClocks = MyClocks.remove(it.myClocks, f)) } },
                        Alignment.Center,
                    ) {
                        Box(Modifier.size(24.dp).clip(CircleShape).background(SK.surface2).border(1.dp, SK.line, CircleShape), Alignment.Center) { Icon(Ic.CLOSE, SK.text, 16.dp) }
                    }
                }
                T("${i + 1}. ${spec(f).ko}", 13.sp, if (on) 700 else 500, if (on) acc else SK.text, Modifier.padding(top = 8.dp), maxLines = 1)
            }
        }
        val line = SK.line
        Column(
            Modifier.width(TILE_W).height(104.dp).clip(RoundedCornerShape(14.dp))
                .drawBehind {
                    val px = 1.5.dp.toPx()
                    drawRoundRect(line, style = Stroke(px, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))), cornerRadius = CornerRadius(14.dp.toPx()))
                }
                .clickable(onClick = onAdd),
            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Ic.PLUS, acc)
            T("시계 넣기", 13.sp, 600, acc, Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun GalleryCard(face: FaceId, on: Boolean, mine: Boolean, config: AppConfig, state: StandbyState, modifier: Modifier, onClick: () -> Unit) {
    val acc = LocalAccent.current
    val sp = spec(face)
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (on) acc.copy(alpha = 0.16f) else SK.surface).clickable(onClick = onClick)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
    ) {
        Box {
            Thumb(face, config, state, inSlot = true, border = if (on) acc else null)
            if (mine) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(acc), Alignment.Center) { Icon(Ic.CHECK, SK.bg, 16.dp) }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            T(sp.ko, 13.sp, if (on) 700 else 500, if (on) acc else SK.text, Modifier.weight(1f, fill = false), maxLines = 1)
            if (sp.ours) Box(Modifier.padding(start = 6.dp).border(1.dp, OURS, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
                T("우리만", 10.sp, 700, OURS, maxLines = 1)
            }
        }
    }
}

// ---- 꾸미기 (mockup SCREENS.style): rows from spec(face).rows / more, 색 조합 바꾸기 on top of the colours ------------------

private val FONT_ORDER = FontId.entries.filter { !it.hangul }
private val HANGUL_FONTS = FontId.entries.filter { it.hangul }
private val LED_NAMES = listOf("빨강", "초록", "파랑", "호박").zip(FaceDefaults.LEDS)

@Composable
fun ClockStyleScreen(face: FaceId, config: AppConfig, update: Update, back: () -> Unit) {
    val sp = spec(face)
    val style = config.clockStyle(face)
    fun edit(f: (ClockStyle) -> ClockStyle) = update { it.copy(styles = it.styles + (face to f(it.clockStyle(face)))) }
    // [이전 조합]: the colours from before the last shuffle (one level)
    var prev by rememberSaveable { mutableStateOf<Map<ColorShuffle.Field, Long>?>(null) }
    // seed = the current colours + a press counter: each press differs, and shots stay reproducible
    var presses by rememberSaveable { mutableIntStateOf(0) }
    val shuffle = ColorShuffle.supports(face)
    val visible = { r: StyleRow ->
        // numerals and ticks shape only the analog dials that carry them (the wall clock always has them)
        face != FaceId.ANALOG || (r != StyleRow.ANALOG_NUMBERS && r != StyleRow.ANALOG_TICKS) || config.analogStyle == AnalogStyle.ROUND || config.analogStyle == AnalogStyle.RECT
    }
    val firstColour = sp.rows.firstOrNull { it.part != null || it.preset }
    val first = Slide(sp.ko) { l -> ClockFace(face, config.clockStyle(face), l.state, Modifier.fillMaxSize(), analog = config.analogStyle) }

    Page("${sp.ko} 꾸미기", back, preview = Preview(config, listOf(first))) {
        Section(null) {
            sp.rows.filter(visible).forEach { r ->
                if (shuffle && r == firstColour) item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("색 조합 바꾸기", {
                            val cur = config.clockStyle(face)
                            prev = ColorShuffle.current(face, cur)
                            // toString, not hashCode: enum hashCodes are identity-based and change every process
                            val seed = cur.toString().hashCode() + presses++
                            edit { ColorShuffle.apply(face, it, ColorShuffle.next(face, it, seed)) }
                        }, Modifier.weight(1f), primary = false)
                        ActionButton("이전 조합", { prev?.let { p -> edit { ColorShuffle.apply(face, it, p) } }; prev = null }, Modifier.weight(1f), primary = false, enabled = prev != null)
                    }
                }
                item { StyleControl(r, face, style, config, update, ::edit, firstColour = r == firstColour) }
            }
        }
        val more = sp.more.filter(visible)
        if (more.isNotEmpty()) More { Section(null) { more.forEach { r -> item { StyleControl(r, face, style, config, update, ::edit, firstColour = false) } } } }
        Gap(24.dp)
        ActionButton("테마 기본값으로 되돌리기", { prev = null; update { it.copy(styles = it.styles - face) } }, Modifier.fillMaxWidth(), primary = false)
    }
}

/** One [StyleRow] as its Kit control (mockup `rowOf`). The first colour row shows its swatches, later ones fold into a dot. */
@Composable
private fun StyleControl(r: StyleRow, face: FaceId, style: ClockStyle, config: AppConfig, update: Update, edit: ((ClockStyle) -> ClockStyle) -> Unit, firstColour: Boolean) {
    val part = r.part
    if (part != null) {
        val c = r.color(style, face)!!
        if (firstColour) ControlRow(part) { ColorPalette(c, { v -> edit { r.withColor(it, v) } }) }
        else ColorRow(part, c) { v -> edit { r.withColor(it, v) } }
        return
    }
    when (r) {
        StyleRow.LED -> ControlRow("불빛 색") { NamedSwatches(LED_NAMES, style.ledColor ?: FaceDefaults.LEDS[0], { v -> edit { it.copy(ledColor = v) } }) }
        StyleRow.PAIR -> ControlRow("두 색 조합") { PresetSwatches(FaceDefaults.PAIRS, style.pairPreset ?: 0, { i -> edit { it.copy(pairPreset = i) } }) }
        StyleRow.TRIO -> ControlRow("색 조합") { PresetSwatches(FaceDefaults.TRIOS, style.trioPreset ?: 0, { i -> edit { it.copy(trioPreset = i) } }) }
        StyleRow.SOLAR -> ControlRow("솔라 팔레트") {
            val sets = ClockStyle.SOLAR_SETS
            PresetSwatches(sets.map { it.take(2) }, sets.indexOfFirst { it[0] == style.bgColor && it[1] == style.bgColor2 }, { i ->
                val set = sets[i]
                edit { it.copy(bgColor = set[0], bgColor2 = set[1], color = set[2], color2 = set[3], effect = Effect.TWO_COLOR, background = Background.GRADIENT) }
            }, blend = true)
        }
        StyleRow.SIZE -> SliderRow("크기", "${style.sizePct}%", style.sizePct.toFloat(), 60f..130f, { v -> edit { it.copy(sizePct = v.toInt()) } })
        StyleRow.WEIGHT -> SliderRow("굵기", "${style.weight}", style.weight.toFloat(), 100f..950f, { v -> edit { it.copy(weight = (v / 50).roundToInt() * 50) } })
        StyleRow.INFO -> SwitchRow("날짜와 날씨 표시", style.showInfo, { v -> edit { it.copy(showInfo = v) } }, subtitle = "요일·날짜·기온·다음 알람을 같이 보여요")
        StyleRow.FONT, StyleRow.DIAL_FONT -> ControlRow(
            when { r == StyleRow.DIAL_FONT -> "시계판 숫자 서체"; face == FaceId.FLIP -> "판 위 숫자 서체"; else -> "서체" },
        ) { FontGrid(FONT_ORDER, style.font, "12") { f, w -> edit { it.copy(font = f, weight = w) } } }
        StyleRow.KFONT -> ControlRow("서체", note = "한글을 쓸 수 있는 서체만 보여요.") { FontGrid(HANGUL_FONTS, style.font, "아홉") { f, w -> edit { it.copy(font = f, weight = w) } } }
        StyleRow.FX -> ControlRow("효과", note = "밤에는 모든 효과가 꺼져요.") {
            Segmented(Effect.entries.map { it.ko }, style.effect.ordinal, { i -> edit { it.copy(effect = Effect.entries[i]) } })
            if (style.effect == Effect.TWO_COLOR) {
                T("두 번째 색", 16.sp, 500, SK.text, Modifier.padding(top = 12.dp, bottom = 4.dp))
                ColorPalette(style.color2, { v -> edit { it.copy(color2 = v) } })
            }
        }
        StyleRow.BG -> ControlRow("배경") {
            Segmented(Background.entries.map { it.ko }, style.background.ordinal, { i -> edit { it.copy(background = Background.entries[i]) } })
            if (style.background != Background.BLACK) {
                T(if (style.background == Background.GRADIENT) "시작 색" else "배경 색", 16.sp, 500, SK.text, Modifier.padding(top = 12.dp, bottom = 4.dp))
                ColorPalette(style.bgColor, { v -> edit { it.copy(bgColor = v) } })
            }
            if (style.background == Background.GRADIENT) {
                T("끝 색", 16.sp, 500, SK.text, Modifier.padding(top = 12.dp, bottom = 4.dp))
                ColorPalette(style.bgColor2, { v -> edit { it.copy(bgColor2 = v) } })
            }
        }
        StyleRow.LAYOUT -> ControlRow("배치") { Segmented(ClockLayout.entries.map { it.ko }, style.layout.ordinal, { i -> edit { it.copy(layout = ClockLayout.entries[i]) } }) }
        StyleRow.SHAPE -> ControlRow("모양") { AnalogStylePicker(config, style, update) }
        StyleRow.ANALOG_NUMBERS -> {
            // DUO stays a decode-only alias of TWELVE (D7)
            val nums = AnalogNumbers.entries - AnalogNumbers.DUO
            val cur = if (style.analogNumbers == AnalogNumbers.DUO) AnalogNumbers.TWELVE else style.analogNumbers
            ControlRow("숫자") { Segmented(nums.map { it.ko }, nums.indexOf(cur), { i -> edit { it.copy(analogNumbers = nums[i]) } }) }
        }
        StyleRow.ANALOG_TICKS -> ControlRow("눈금") { Segmented(AnalogTicks.entries.map { it.ko }, style.analogTicks.ordinal, { i -> edit { it.copy(analogTicks = AnalogTicks.entries[i]) } }) }
        else -> Unit // colour rows are handled above
    }
}

/** Font tiles, three per line, each drawn in its own font ([sample] = "12" for digits, "아홉" for Hangul). */
@Composable
private fun FontGrid(fonts: List<FontId>, selected: FontId, sample: String, onPick: (FontId, Int) -> Unit) {
    val themeW = kr.dot.sb.ui.theme.LocalTheme.current.tokens.digitWeight
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fonts.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { f -> FontTile(f, f == selected, sample, Modifier.weight(1f)) { onPick(f, if (f == FontId.THEME) themeW else f.defaultWeight) } }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun FontTile(font: FontId, on: Boolean, sample: String, modifier: Modifier, onClick: () -> Unit) {
    val acc = LocalAccent.current
    val shape = RoundedCornerShape(14.dp)
    val th = kr.dot.sb.ui.theme.LocalTheme.current
    val w = if (font == FontId.THEME) th.tokens.digitWeight else font.defaultWeight
    Column(
        modifier.clip(shape).background(if (on) SK.surface2 else SK.bg).border(if (on) 2.dp else 1.dp, if (on) acc else SK.line, shape)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(56.dp), Alignment.Center) {
            Text(
                sample, color = if (on) acc else SK.text, fontSize = if (font.hangul) 30.sp else 38.sp, fontFamily = th.digits(font, w),
                fontWeight = FontWeight(w.coerceIn(100, 900)), maxLines = 1,
            )
        }
        T(font.ko, 12.sp, if (on) 700 else 500, if (on) SK.text else SK.muted, Modifier.padding(top = 2.dp), maxLines = 1, align = TextAlign.Center)
    }
}

/** The five full-screen analog styles as live thumbnails (no theme names); a tap sets `AppConfig.analogStyle`. */
@Composable
private fun AnalogStylePicker(config: AppConfig, style: ClockStyle, update: Update) {
    val acc = LocalAccent.current
    val state = remember(config) { Sample.state(config) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AnalogStyle.entries.forEach { k ->
            val on = k == config.analogStyle
            Column(
                Modifier.width(136.dp).clip(RoundedCornerShape(14.dp)).background(if (on) acc.copy(alpha = 0.16f) else SK.surface)
                    .clickable { update { it.copy(analogStyle = k) } }.padding(8.dp),
            ) {
                PreviewFrame(PreviewDevice.FOLD_COVER.w, PreviewDevice.FOLD_COVER.h, Modifier.fillMaxWidth(), radius = 8.dp) {
                    ClockFace(FaceId.ANALOG, style, state, Modifier.fillMaxSize(), analog = k)
                }
                T(k.ko, 13.sp, if (on) 700 else 500, if (on) acc else SK.text, Modifier.padding(top = 8.dp, start = 2.dp), maxLines = 1)
            }
        }
    }
}
