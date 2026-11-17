package kr.dot.sb.settings

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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

// ---- 시계 (plan-r15 E5/F4): preview, six tiles, 모든 시계 보기 · 꾸미기 · 여러 시계 돌려 보기 ---------------------------

private val TILE_W = 118.dp
private val TILE_GAP = 10.dp

/** The main grid's six faces (plan-r15 F4); the rest are under 모든 시계 보기. */
private val MAIN_FACES = listOf(FaceId.DIGITAL, FaceId.FLIP, FaceId.ANALOG, FaceId.HANGUL, FaceId.DOT_MATRIX, FaceId.THIN)

/**
 * A tile tap on 시계 / 모든 시계 (E5): rotation on = preview only (이 시계 쓰기 commits); rotation off = the clock
 * becomes `myClocks[0]` at once ([MyClocks.pick]).
 */
private fun tapClock(f: FaceId, config: AppConfig, update: Update, select: (FaceId) -> Unit) {
    select(f)
    if (!config.rotateClocks) update { it.copy(myClocks = MyClocks.pick(it.myClocks, f, MyClocks.cardFaces(it))) }
}

@Composable
fun ClockScreen(config: AppConfig, update: Update, nav: Nav, back: () -> Unit) {
    var sel by rememberSaveable { mutableStateOf(config.myClocks.firstOrNull() ?: FaceId.DIGITAL) }
    val own = rememberOwnPhotos(config.photoFolders)
    val state = remember(config, own) { Sample.state(config).withPhotos(own) }
    val first = Slide(spec(sel).ko) { l -> ClockFace(sel, config.clockStyle(sel), l.state.withPhotos(own), Modifier.fillMaxSize(), analog = config.analogStyle) }
    var citySheet by remember { mutableStateOf(false) }
    val now = config.myClocks.firstOrNull() ?: FaceId.DIGITAL
    Box(Modifier.fillMaxSize()) {
    Page(
        "시계", back, preview = pagePreview(config, Pages.CLOCK, first, nav),
        miniActions = { ClockActions(sel, config, update, compact = true) },
    ) {
        ClockActions(sel, config, update, compact = false)
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            MAIN_FACES.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { f -> GalleryCard(f, f == sel, config, state, Modifier.weight(1f)) { tapClock(f, config, update) { sel = it } } }
                }
            }
        }
        Section(null) {
            item { NavRow("모든 시계 보기", { nav.push(Routes.CLOCKS_ALL) }, value = "${ClockRegistry.all.size}개") }
            item { NavRow("꾸미기", { nav.push(Routes.clock(now)) }, subtitle = spec(now).ko, value = "색 · 글꼴 · 크기") }
            item { NavRow("여러 시계 돌려 보기", { nav.push(Routes.CLOCKS_ROTATE) }, value = if (config.rotateClocks) "켬 · ${config.myClocks.size}개" else "끔") }
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
            WorldCitiesSection(config, update) { citySheet = true }
        }
    }
    if (citySheet) {
        CitySheet("세계 시계 도시", taken = config.worldCities.map { it.name }.toSet(), onDismiss = { citySheet = false }) { c ->
            update { s -> if (s.worldCities.any { it.name == c.name }) s else s.copy(worldCities = s.worldCities + c) }
            citySheet = false
        }
    }
    }
}

/** 모든 시계 (E5): every face by family, the same tap rule as the main grid. */
@Composable
fun AllClocksScreen(config: AppConfig, update: Update, back: () -> Unit) {
    var sel by rememberSaveable { mutableStateOf(config.myClocks.firstOrNull() ?: FaceId.DIGITAL) }
    var fam by rememberSaveable { mutableStateOf<Family?>(null) }
    val own = rememberOwnPhotos(config.photoFolders)
    val state = remember(config, own) { Sample.state(config).withPhotos(own) }
    val first = Slide(spec(sel).ko) { l -> ClockFace(sel, config.clockStyle(sel), l.state.withPhotos(own), Modifier.fillMaxSize(), analog = config.analogStyle) }
    Page("모든 시계", back, preview = Preview(config, listOf(first)), miniActions = { ClockActions(sel, config, update, compact = true) }) {
        ClockActions(sel, config, update, compact = false)
        Gap(16.dp)
        FamilyChips(fam) { fam = it }
        Gap(12.dp)
        ClockRegistry.all.filter { fam == null || spec(it).family == fam }.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { f -> GalleryCard(f, f == sel, config, state, Modifier.weight(1f)) { tapClock(f, config, update) { sel = it } } }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * 여러 시계 돌려 보기 (E5): the rotation switch, 쓰는 시계 (drag to reorder) and every face, picked ones dimmed with the
 * card picker's centred check. A tap on a picked face removes it (never the last one), on another adds it at the end.
 */
@Composable
fun RotateClocksScreen(config: AppConfig, update: Update, back: () -> Unit) {
    val state = remember(config) { Sample.state(config) }
    val my = config.myClocks
    var hint by remember { mutableIntStateOf(0) }
    LaunchedEffect(hint) { if (hint > 0) { kotlinx.coroutines.delay(3_000); hint = 0 } }
    Page("여러 시계 돌려 보기", back) {
        Section(null, footer = "켜면 Stand Screen 시계 페이지를 위아래로 넘겨 쓰는 시계를 차례로 볼 수 있어요. 끄면 첫 번째 시계만 보여요.") {
            item { SwitchRow("여러 시계 돌려 보기", config.rotateClocks, { v -> update { it.copy(rotateClocks = v) } }) }
        }
        Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
            T("쓰는 시계", 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, bottom = 8.dp))
            MyStrip(my, config, state, update)
            T("길게 눌러 옆으로 끌면 순서가 바뀌어요. 시계 카드도 이 시계들 중에서 골라요.", 13.sp, 400, SK.muted, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), lineHeight = 18.sp)
        }
        Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            T("모든 시계", 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, bottom = 8.dp))
            if (hint > 0) T("시계는 하나 이상 있어야 해요.", 14.sp, 600, LocalAccent.current, Modifier.padding(start = 16.dp, bottom = 8.dp))
            ClockRegistry.all.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { f ->
                        PickTile(f, f in my, config, state, Modifier.weight(1f)) {
                            when {
                                f !in my -> update { it.copy(myClocks = MyClocks.add(it.myClocks, f)) }
                                my.size <= 1 -> hint++
                                else -> update { it.copy(myClocks = MyClocks.remove(it.myClocks, f)) }
                            }
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** Name of the previewed clock + where it stands; with rotation on, [이 시계 쓰기] puts it first ([MyClocks.use]). [compact] = inside the mini preview. */
@Composable
private fun ClockActions(face: FaceId, config: AppConfig, update: Update, compact: Boolean) {
    val i = config.myClocks.indexOf(face)
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = if (compact) 0.dp else 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
            T(spec(face).ko, 15.sp, 700, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
            if (!compact) T(
                when { i == 0 -> "지금 쓰는 시계"; i > 0 && config.rotateClocks -> "돌려 보기 ${i + 1}번째"; else -> "둘러보는 중" },
                12.sp, 500, SK.muted, Modifier.padding(start = 8.dp), maxLines = 1,
            )
        }
        if (config.rotateClocks && i != 0) ActionButton("이 시계 쓰기", { update { it.copy(myClocks = MyClocks.use(it.myClocks, face)) } })
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

/** 쓰는 시계: long-press + drag sideways reorders ([MyClocks.move]); removing is a tap on the picked tile below. */
@Composable
private fun MyStrip(my: List<FaceId>, config: AppConfig, state: StandbyState, update: Update) {
    val step = with(LocalDensity.current) { (TILE_W + TILE_GAP).toPx() }
    var drag by remember { mutableStateOf<Pair<Int, Float>?>(null) } // index, dx px
    fun move(from: Int, to: Int) = update { it.copy(myClocks = MyClocks.move(it.myClocks, from, to)) }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        my.forEachIndexed { i, f ->
            val dx = drag?.takeIf { it.first == i }?.second
            Column(
                Modifier.width(TILE_W).zIndex(if (dx != null) 1f else 0f).graphicsLayer { translationX = dx ?: 0f; if (dx != null) { scaleX = 1.05f; scaleY = 1.05f } }
                    .clip(RoundedCornerShape(14.dp)).background(SK.surface)
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
                    .padding(8.dp),
            ) {
                Thumb(f, config, state, inSlot = false, border = null)
                T("${i + 1}. ${spec(f).ko}", 13.sp, 500, SK.text, Modifier.padding(top = 8.dp), maxLines = 1)
            }
        }
    }
}

@Composable
private fun GalleryCard(face: FaceId, on: Boolean, config: AppConfig, state: StandbyState, modifier: Modifier, onClick: () -> Unit) {
    val acc = LocalAccent.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(if (on) acc.copy(alpha = 0.16f) else SK.surface).clickable(onClick = onClick)
            .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
    ) {
        Thumb(face, config, state, inSlot = true, border = if (on) acc else null)
        T(spec(face).ko, 13.sp, if (on) 700 else 500, if (on) acc else SK.text, Modifier.padding(top = 8.dp), maxLines = 1)
    }
}

/** A face in 여러 시계 돌려 보기: picked = dimmed whole tile + 46 dp centred check (CardPickerSheet look). */
@Composable
private fun PickTile(face: FaceId, picked: Boolean, config: AppConfig, state: StandbyState, modifier: Modifier, onClick: () -> Unit) {
    val acc = LocalAccent.current
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(SK.surface)
            .clickable(onClickLabel = if (picked) "빼기" else "추가", onClick = onClick).padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
    ) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).then(if (picked) Modifier.border(2.dp, acc, RoundedCornerShape(8.dp)) else Modifier)) {
            Thumb(face, config, state, inSlot = true, border = null)
            if (picked) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)), Alignment.Center) { // literal-ok: the card picker's veil
                Box(Modifier.size(46.dp).clip(CircleShape).background(acc), Alignment.Center) { Icon(Ic.CHECK, SK.bg, 24.dp) }
            }
        }
        T(spec(face).ko, 13.sp, if (picked) 700 else 500, SK.text, Modifier.padding(top = 8.dp), maxLines = 1)
    }
}

// ---- 꾸미기 (mockup SCREENS.style): [색 조합 바꾸기][이전 조합][기본으로] under the preview, then spec(face).rows / more -----

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
    val own = rememberOwnPhotos(if (face == FaceId.PHOTOS_TIME) config.photoFolders else emptyList())
    val first = Slide(sp.ko) { l -> ClockFace(face, config.clockStyle(face), l.state.withPhotos(own), Modifier.fillMaxSize(), analog = config.analogStyle) }

    Page("${sp.ko} 꾸미기", back, preview = Preview(config, listOf(first))) {
        // 11-14: one row right under the preview, the same order in every editor: [색 조합 바꾸기] [이전 조합] [기본으로]
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shuffle) {
                ActionButton("색 조합 바꾸기", {
                    val cur = config.clockStyle(face)
                    prev = ColorShuffle.current(face, cur)
                    // toString, not hashCode: enum hashCodes are identity-based and change every process
                    val seed = cur.toString().hashCode() + presses++
                    edit { ColorShuffle.apply(face, it, ColorShuffle.next(face, it, seed)) }
                }, Modifier.weight(1.45f), primary = false) // the longest label: full width at 411 dp
                ActionButton("이전 조합", { prev?.let { p -> edit { ColorShuffle.apply(face, it, p) } }; prev = null }, Modifier.weight(1f), primary = false, enabled = prev != null)
            }
            ActionButton("기본으로", { prev = null; update { it.copy(styles = it.styles - face) } }, Modifier.weight(1f), primary = false, enabled = face in config.styles)
        }
        Section(null) {
            sp.rows.filter(visible).forEach { r -> item { StyleControl(r, face, style, config, update, ::edit, firstColour = r == firstColour) } }
        }
        val more = sp.more.filter(visible)
        if (more.isNotEmpty()) More { Section(null) { more.forEach { r -> item { StyleControl(r, face, style, config, update, ::edit, firstColour = false) } } } }
    }
}

/** One [StyleRow] as its Kit control (mockup `rowOf`). The first colour row shows its swatches, later ones fold into a dot. */
@Composable
private fun StyleControl(r: StyleRow, face: FaceId, style: ClockStyle, config: AppConfig, update: Update, edit: ((ClockStyle) -> ClockStyle) -> Unit, firstColour: Boolean) {
    val part = r.part
    if (r == StyleRow.FACE_COLOR && face in kr.dot.sb.ui.clock.Relief.FACES) {
        // the mockup's eight dials by name, plus 직접 고르기 (a custom dial derives its ink)
        ControlRow("다이얼 색") { NamedSwatches(kr.dot.sb.ui.clock.Relief.DIALS.map { it.ko to it.dial }, kr.dot.sb.ui.clock.Relief.dial(style), showNames = false, onChange = { v -> edit { it.copy(faceColor = v) } }) }
        return
    }
    if (part != null) {
        val c = r.color(style, face)!!
        if (firstColour) ControlRow(part) { ColorPalette(c, { v -> edit { r.withColor(it, v) } }) }
        else ColorRow(part, c) { v -> edit { r.withColor(it, v) } }
        return
    }
    when (r) {
        StyleRow.LED -> ControlRow("불빛 색") { NamedSwatches(LED_NAMES, style.ledColor ?: FaceDefaults.LEDS[0], onChange = { v -> edit { it.copy(ledColor = v) } }) }
        StyleRow.PAIR -> ControlRow("두 색 조합") { PresetSwatches(FaceDefaults.PAIRS, style.pairPreset ?: 0, { i -> edit { it.copy(pairPreset = i) } }) }
        StyleRow.TRIO -> ControlRow("색 조합") { PresetSwatches(FaceDefaults.TRIOS, style.trioPreset ?: 0, { i -> edit { it.copy(trioPreset = i) } }) }
        StyleRow.SOLAR -> ControlRow("색 조합") {
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
        ) {
            FontGrid(FONT_ORDER, style.font, "12") { f, w ->
                // 11-16: on 디지털, 둥근 굵은 글씨 is one tap to the old look (middle digit in accent + overlap); both stay switchable
                val look = face == FaceId.DIGITAL && f == FontId.ROUNDED_HEAVY
                edit { if (look) it.copy(font = f, weight = w, accentMiddle = true, overlap = true) else it.copy(font = f, weight = w) }
            }
        }
        StyleRow.KFONT -> ControlRow("서체", note = "한글을 쓸 수 있는 서체만 보여요.") { FontGrid(HANGUL_FONTS, style.font, "아홉") { f, w -> edit { it.copy(font = f, weight = w) } } }
        StyleRow.FX -> ControlRow("효과", note = "밤에는 모든 효과가 꺼져요.") {
            Segmented(Effect.entries.map { it.ko }, style.effect.ordinal, { i -> edit { it.copy(effect = Effect.entries[i]) } })
            if (style.effect == Effect.TWO_COLOR) {
                T("두 번째 색", 16.sp, 500, SK.text, Modifier.padding(top = 12.dp, bottom = 4.dp))
                ColorPalette(style.color2, { v -> edit { it.copy(color2 = v) } })
            }
        }
        StyleRow.BG -> ControlRow("시계 배경") {
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
        // r14: the iPhone side strip is gone; a stored IPHONE draws (and shows) as 가운데
        StyleRow.LAYOUT -> ControlRow("배치") {
            val ls = listOf(ClockLayout.CENTER, ClockLayout.TWO_LINES)
            Segmented(ls.map { it.ko }, if (style.layout == ClockLayout.TWO_LINES) 1 else 0, { i -> edit { it.copy(layout = ls[i]) } })
        }
        StyleRow.ACCENT_MIDDLE -> SwitchRow("가운데 숫자 색", style.accentMiddle, { v -> edit { it.copy(accentMiddle = v) } }, subtitle = "가운데 숫자 하나를 진한 색으로 칠해요 (한 줄일 때)")
        StyleRow.FILL -> SwitchRow("꽉 채우기", style.fill, { v -> edit { it.copy(fill = v) } }, subtitle = "숫자를 화면에 꽉 차게 키워요")
        StyleRow.OVERLAP -> SwitchRow("살짝 겹치기", style.overlap, { v -> edit { it.copy(overlap = v) } }, subtitle = "숫자끼리 조금 겹치고 살짝 기울어요")
        StyleRow.CLEAR_BG -> SwitchRow("배경 투명", style.clearBg, { v -> edit { it.copy(clearBg = v) } }, subtitle = "시계 판 없이 카드 배경 위에 그려요")
        StyleRow.SHAPE -> ControlRow("모양") { AnalogStylePicker(config, style, update) }
        StyleRow.ANALOG_NUMBERS -> {
            // DUO stays a decode-only alias of TWELVE (D7)
            val nums = AnalogNumbers.entries - AnalogNumbers.DUO
            val cur = if (style.analogNumbers == AnalogNumbers.DUO) AnalogNumbers.TWELVE else style.analogNumbers
            ControlRow("숫자") { Segmented(nums.map { it.ko }, nums.indexOf(cur), { i -> edit { it.copy(analogNumbers = nums[i]) } }) }
        }
        StyleRow.ANALOG_TICKS -> ControlRow("눈금") { Segmented(AnalogTicks.entries.map { it.ko }, style.analogTicks.ordinal, { i -> edit { it.copy(analogTicks = AnalogTicks.entries[i]) } }) }
        // r11 Part C (Relief faces)
        StyleRow.LUME_VIEW -> ControlRow("보기", note = "밤에는 밤 색으로 바뀌어요.") {
            Segmented(kr.dot.sb.model.LumeView.entries.map { it.ko }, style.lumeView.ordinal, { i -> edit { it.copy(lumeView = kr.dot.sb.model.LumeView.entries[i]) } })
        }
        StyleRow.MOVEMENT -> ControlRow("초침") { Segmented(listOf("똑딱", "부드럽게"), if (style.sweep) 1 else 0, { i -> edit { it.copy(sweep = i == 1) } }) }
        StyleRow.INDEX_ONLY -> ControlRow("숫자") {
            Segmented(listOf("숫자", "눈금만"), if (style.analogNumbers == AnalogNumbers.NONE) 1 else 0, { i -> edit { it.copy(analogNumbers = if (i == 1) AnalogNumbers.NONE else AnalogNumbers.TWELVE) } })
        }
        StyleRow.SQUARE_NUMERALS -> ControlRow("숫자") {
            Segmented(kr.dot.sb.model.SquareNumerals.entries.map { it.ko }, style.squareNumerals.ordinal, { i -> edit { it.copy(squareNumerals = kr.dot.sb.model.SquareNumerals.entries[i]) } })
        }
        // plan-r13 B4: named apart from the global 시간 표시, in its order
        StyleRow.HOURS -> ControlRow("이 시계만 시간 표시") {
            Segmented(listOf("폰 설정 따라", "12시간", "24시간"), when (style.h24) { null -> 0; false -> 1; true -> 2 }, { i -> edit { it.copy(h24 = when (i) { 0 -> null; 1 -> false; else -> true }) } })
        }
        StyleRow.GHOST -> SwitchRow("꺼진 획 보이기", style.ghost, { v -> edit { it.copy(ghost = v) } }, subtitle = "켜지지 않은 획이 다이얼에 옅게 새겨져요")
        StyleRow.DATE_WINDOW -> SwitchRow("날짜 창", style.showInfo, { v -> edit { it.copy(showInfo = v) } }, subtitle = "오른쪽 위에 요일과 날짜를 보여요")
        StyleRow.DATE_DAY -> SwitchRow("날짜·요일", style.showInfo, { v -> edit { it.copy(showInfo = v) } }, subtitle = "세워 두면 위아래 빈 곳에 요일과 날짜를 보여요")
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

/**
 * plan-r15 E4: one photo from the picked folders for the 사진 위 시간 previews, loaded off the main thread; null = no
 * folders or nothing readable (the previews keep the sample photo).
 */
@Composable
private fun rememberOwnPhotos(folders: List<String>): List<kr.dot.sb.ui.state.Photo>? {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    return androidx.compose.runtime.produceState<List<kr.dot.sb.ui.state.Photo>?>(null, folders) {
        if (folders.isEmpty()) return@produceState
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { kr.dot.sb.data.PhotoIndex.get(ctx, folders).let { kr.dot.sb.data.PhotoIndex.photos(ctx, it, null, 1) } }.getOrNull()
        }?.takeIf { it.isNotEmpty() }
    }.value
}

private fun StandbyState.withPhotos(own: List<kr.dot.sb.ui.state.Photo>?) = if (own == null) this else copy(photos = own)
