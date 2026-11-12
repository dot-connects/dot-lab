package kr.dot.sb.settings

import kr.dot.sb.data.UsageFeature
import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.model.WidgetMode
import kotlin.math.roundToInt
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import java.util.Collections

private fun <T> List<T>.moved(i: Int, d: Int): List<T> {
    val j = i + d
    if (i !in indices || j !in indices) return this
    return toMutableList().also { Collections.swap(it, i, j) }
}

private val PAGE_NAMES_FIXED = listOf("위젯", "사진", "시계")

// ---- boards list ------------------------------------------------------------------------------------------------

@Composable
fun BoardsScreen(config: AppConfig, update: Update, nav: Nav, back: () -> Unit) {
    val state = remember(config.styles, config.boards, config.widgetLeft, config.widgetRight, config.city, config.use24h) { Sample.state(config) }
    val pageNames = PAGE_NAMES_FIXED + config.boards.map { it.name }
    Page("화면", back) {
        Section("시작 화면", footer = "스탠바이가 켜질 때 처음 보이는 화면이에요. 처음에는 시계예요.") {
            item { ControlRow(null) { ChipFlow(pageNames, config.startPage.coerceIn(0, pageNames.lastIndex), { i -> update { it.copy(startPage = i) } }) } }
        }
        Section("화면 순서", footer = "위젯 · 사진 · 시계는 아이폰처럼 맨 앞에 고정돼요. 내 화면은 ▲▼로 순서를 바꿀 수 있어요.") {
            item {
                PageRow(
                    thumb = { BoardView(widgetsBoard(config), state, config, StandbyActions.NONE, Modifier.fillMaxSize()) },
                    title = "위젯", subtitle = "두 칸 · 카드 쌓기", start = config.startPage == 0, onClick = { nav.push(Routes.WIDGETS) },
                )
            }
            item {
                PageRow(thumb = { Box(Modifier.fillMaxSize().background(Color(0xFF1C1C1E)), Alignment.Center) { T("사진", 28.sp, 700, SK.muted) } },
                    title = "사진", subtitle = "앨범 사진을 번갈아", start = config.startPage == 1, onClick = null)
            }
            item {
                PageRow(
                    thumb = { kr.dot.sb.ui.clock.ClockFace(FaceId.DIGITAL, config.clockStyle(FaceId.DIGITAL), state, Modifier.fillMaxSize()) },
                    title = "시계", subtitle = "디지털 · 아날로그 · 세계 · 솔라 · 플로트", start = config.startPage == 2, onClick = { nav.push(Routes.CLOCKS) },
                )
            }
            config.boards.forEachIndexed { i, b ->
                item {
                    PageRow(
                        thumb = { BoardView(b, state, config, StandbyActions.NONE, Modifier.fillMaxSize()) },
                        title = b.name, subtitle = b.layout.ko, start = config.startPage == BoardOps.FIRST_BOARD_PAGE + i,
                        onClick = { nav.push(Routes.board(b.id)) },
                        trailing = {
                            Icon(Ic.UP, if (i > 0) SK.muted else SK.line, 22.dp, Modifier.clickable(enabled = i > 0) { update { BoardOps.move(it, i, -1) } }.padding(10.dp).size(22.dp))
                            Icon(Ic.DOWN, if (i < config.boards.lastIndex) SK.muted else SK.line, 22.dp, Modifier.clickable(enabled = i < config.boards.lastIndex) { update { BoardOps.move(it, i, 1) } }.padding(10.dp).size(22.dp))
                        },
                    )
                }
            }
        }
        Gap(16.dp)
        ActionButton("화면 추가", {
            val id = BoardOps.add(config).boards.last().id
            update { BoardOps.add(it) }
            nav.push(Routes.board(id))
        }, Modifier.fillMaxWidth())
    }
}

@Composable
internal fun PageRow(
    thumb: @Composable () -> Unit,
    title: String,
    subtitle: String,
    start: Boolean,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PreviewFrame(751, 475, Modifier.width(104.dp), radius = 8.dp) { thumb() }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
            T(title, 16.sp, 600, maxLines = 1)
            T(subtitle, 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp), maxLines = 2)
            if (start) Pill("시작", LocalAccent.current, Modifier.padding(top = 6.dp))
        }
        if (trailing != null) trailing() else if (onClick != null) Icon(Ic.CHEVRON, SK.muted.copy(alpha = 0.7f), 20.dp, Modifier.padding(end = 8.dp))
    }
}

// ---- board editor -----------------------------------------------------------------------------------------------

private data class PickTarget(val slot: Int, val index: Int?) // index null = append to the stack

@Composable
fun BoardEditorScreen(id: String, config: AppConfig, update: Update, nav: Nav, env: SettingsEnv, back: () -> Unit) {
    val idx = config.boards.indexOfFirst { it.id == id }
    val board = config.boards[idx]
    var devIdx by rememberSaveable { mutableIntStateOf(0) }
    var portrait by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf<PickTarget?>(null) }
    var armed by remember { mutableStateOf(false) }
    val dev = PreviewDevice.entries[devIdx]
    val dw = dev.width(portrait).toFloat()
    val dh = dev.height(portrait).toFloat()
    val coverPortrait = dev.cover && portrait
    val rects = LayoutGeo.slots(board.layout, dw, dh, coverPortrait)
    val state = remember(config.styles, config.city, config.use24h) { Sample.state(config) }
    val acc = LocalAccent.current
    fun edit(f: (Board) -> Board) = update { BoardOps.replace(it, id, f) }
    /** [moved]: old stack index -> new one (null = the card left); a widget's options and id follow it (D6). */
    fun editSlot(slot: Int, moved: (Int) -> Int?, f: (List<CardType>) -> List<CardType>) = edit { b ->
        BoardOps.editStack(b, slot, f(b.slots.getOrElse(slot) { emptyList() }), moved)
    }

    Box(Modifier.fillMaxSize()) {
        Page(board.name, back, side = {
            PreviewFrame(
                dev.width(portrait), dev.height(portrait), Modifier.fillMaxWidth(), maxHeight = 280.dp,
                overlay = { scale ->
                    rects.forEachIndexed { i, r ->
                        val on = i == selected
                        Box(
                            Modifier.offset((r.x * scale).dp, (r.y * scale).dp).size((r.w * scale).dp, (r.h * scale).dp)
                                .border(if (on) 2.dp else 1.dp, if (on) acc else Color.White.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                .clickable { selected = i; pick = PickTarget(i, 0) },
                        ) {
                            Box(Modifier.padding(6.dp).clip(RoundedCornerShape(50)).background(if (on) acc else SK.scrim).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                T("${i + 1}", 11.sp, 700, if (on) SK.bg else SK.text)
                            }
                        }
                    }
                },
            ) { BoardView(board, state, config, StandbyActions.NONE, Modifier.fillMaxSize()) }
            Gap(12.dp)
            ChipFlow(PreviewDevice.entries.map { it.ko }, devIdx, { devIdx = it })
            Gap(8.dp)
            Segmented(listOf("가로", "세로"), if (portrait) 1 else 0, { portrait = it == 1 })
            T("칸을 누르면 카드를 고를 수 있어요", 13.sp, 400, SK.muted, Modifier.padding(top = 8.dp, start = 4.dp))
        }) {

            Section("이름") {
                item {
                    var name by remember(id) { mutableStateOf(board.name) }
                    Box(Modifier.padding(12.dp)) { Field(name, { name = it; edit { b -> b.copy(name = it) } }, "화면 이름") }
                }
            }

            Section("칸 나누기") {
                item {
                    ControlRow(null) {
                        val bad = Layout.entries.filter { LayoutGeo.disabled(it, dw, dh, coverPortrait) }
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Layout.entries.chunked(3).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { l ->
                                        LayoutThumb(l, l == board.layout, l in bad, Modifier.weight(1f)) {
                                            edit { b -> BoardOps.withLayout(b, l) }
                                            selected = 0
                                        }
                                    }
                                }
                            }
                        }
                        if (bad.isNotEmpty()) {
                            val lines = bad.joinToString("\n") { "${it.ko}: 칸이 ${LayoutGeo.smallest(it, dw, dh, coverPortrait).toInt()}dp밖에 안 돼서 ${dev.ko}${if (portrait) " 세로" else ""}에선 못 써요" }
                            T(lines, 13.sp, 400, SK.muted, Modifier.padding(top = 10.dp), lineHeight = 18.sp)
                        }
                        if (LayoutGeo.disabled(board.layout, dw, dh, coverPortrait)) {
                            T("지금 고른 칸 나누기는 이 화면에선 칸이 너무 작아요. 다른 기기에서만 보여요.", 13.sp, 500, SK.danger, Modifier.padding(top = 8.dp), lineHeight = 18.sp)
                        }
                    }
                }
            }

            board.slots.forEachIndexed { s, stack ->
                CardStackSection(
                    title = "칸 ${s + 1}" + if (stack.size > 1) " · 카드 ${stack.size}장 (위아래로 넘겨요)" else "",
                    cards = stack,
                    onReplace = { j -> selected = s; pick = PickTarget(s, j) },
                    onAdd = { selected = s; pick = PickTarget(s, null) },
                    onMove = { j, d -> editSlot(s, { k -> when (k) { j -> j + d; j + d -> j; else -> k } }) { it.moved(j, d) } },
                    onRemove = { j -> editSlot(s, { k -> if (k < j) k else if (k == j) null else k - 1 }) { l -> l.filterIndexed { k, _ -> k != j } } },
                    onSettings = { j -> nav.push(Routes.widget(id, s, j)) },
                )
            }

            Section("강조 색", footer = "시계 따라: 이 화면의 시계 색을 같이 써요. 한 화면에는 강조 색을 하나만 써요.") {
                item {
                    ControlRow(null) {
                        Segmented(listOf("시계 따라", "직접 고르기"), if (board.accent == null) 0 else 1, { i ->
                            edit { b -> b.copy(accent = if (i == 0) null else (b.accent ?: ClockStyle.SWATCHES[0])) }
                        })
                        if (board.accent != null) ColorPalette(board.accent, { c -> edit { b -> b.copy(accent = c) } }, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            Section("시계 스타일", footer = "이 화면의 시계 카드가 쓰는 스타일이에요.") {
                val faces = listOf(FaceId.DIGITAL, FaceId.ANALOG, FaceId.WORLD, FaceId.SOLAR, FaceId.FLOAT, FaceId.DESK)
                item { ControlRow(null) { ChipFlow(faces.map { it.ko }, faces.indexOf(board.clockFace).coerceAtLeast(0), { i -> edit { b -> b.copy(clockFace = faces[i]) } }) } }
                item { NavRow("${board.clockFace.ko} 스타일 편집", { nav.push(Routes.clock(board.clockFace)) }) }
            }

            Section("이 화면") {
                item {
                    val isStart = config.startPage == BoardOps.FIRST_BOARD_PAGE + idx
                    SwitchRow("시작 화면으로 설정", isStart, { on -> update { it.copy(startPage = if (on) BoardOps.FIRST_BOARD_PAGE + idx else 2) } })
                }
                item { NavRow("복제", { update { BoardOps.duplicate(it, idx) }; back() }, subtitle = "같은 구성으로 하나 더 만들어요") }
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable {
                        if (!armed) armed = true else { back(); update { BoardOps.delete(it, idx) } }
                    }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(if (armed) "한 번 더 누르면 지워져요" else "이 화면 지우기", 16.sp, 600, SK.danger)
                    }
                }
            }
        }

        val p = pick
        if (p != null) {
            val r = rects.getOrNull(p.slot)
            CardPickerSheet(
                title = "칸 ${p.slot + 1} · 카드 고르기",
                slotW = (r?.w ?: 300f).toInt(), slotH = (r?.h ?: 300f).toInt(),
                config = config, state = state, accent = board.accent, face = board.clockFace,
                current = p.index?.let { board.slots.getOrNull(p.slot)?.getOrNull(it) },
                taken = board.slots.getOrNull(p.slot).orEmpty().toSet(),
                widget = true,
                onDismiss = { pick = null },
                onPick = { card ->
                    val stack = board.slots.getOrNull(p.slot).orEmpty()
                    val j = p.index ?: stack.size
                    editSlot(p.slot, { k -> k.takeIf { it != p.index || stack[it] == card } }) { l -> if (p.index == null) l + card else l.mapIndexed { k, c -> if (k == p.index) card else c } }
                    pick = null
                    // a new phone widget card asks for its widget right away (a cancel leaves the "pick" placeholder)
                    if (card == CardType.WIDGET && stack.getOrNull(j) != card) pickWidget(env, update, id, "${p.slot}:$j")
                },
            )
        }
    }
}

@Composable
private fun LayoutThumb(layout: Layout, on: Boolean, disabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val acc = LocalAccent.current
    val fill = if (disabled) SK.line else if (on) acc else SK.muted
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (on) SK.surface2 else Color.Transparent)
            .border(if (on) 2.dp else 1.dp, if (on) acc else SK.line, RoundedCornerShape(12.dp))
            .clickable(enabled = !disabled, onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.6f)) {
            val rects = LayoutGeo.slots(layout, 200f, 125f)
            rects.forEach { r ->
                drawRoundRect(fill.copy(alpha = if (disabled) 0.5f else 0.9f), Offset(r.x / 200f * size.width, r.y / 125f * size.height), Size(r.w / 200f * size.width, r.h / 125f * size.height), CornerRadius(4.dp.toPx()))
            }
        }
        T(layout.ko, 12.sp, if (on) 700 else 500, if (disabled) SK.line else if (on) SK.text else SK.muted, Modifier.padding(top = 8.dp).heightIn(min = 32.dp), maxLines = 2, align = TextAlign.Center)
    }
}

// ---- stacks and the card picker (shared with the widgets screen) ------------------------------------------------------

/** One slot's stack: rows of cards (tap = swap), reorder, remove, and an add row. */
@Composable
fun CardStackSection(
    title: String,
    cards: List<CardType>,
    onReplace: (Int) -> Unit,
    onAdd: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    footer: String? = null,
    /** Phone widget rows get a settings button (boards only). */
    onSettings: ((Int) -> Unit)? = null,
) {
    Section(title, footer) {
        cards.forEachIndexed { j, card ->
            item {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onReplace(j) }.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(card.ko, 16.sp, 500, SK.text, Modifier.weight(1f), maxLines = 1)
                    if (card == CardType.WIDGET && onSettings != null) {
                        Box(Modifier.heightIn(min = 48.dp).clickable { onSettings(j) }.padding(horizontal = 12.dp), Alignment.Center) { T("설정", 14.sp, 600, LocalAccent.current) }
                    }
                    if (cards.size > 1) {
                        Box(Modifier.size(44.dp).clickable(enabled = j > 0) { onMove(j, -1) }, Alignment.Center) { Icon(Ic.UP, if (j > 0) SK.muted else SK.line) }
                        Box(Modifier.size(44.dp).clickable(enabled = j < cards.lastIndex) { onMove(j, 1) }, Alignment.Center) { Icon(Ic.DOWN, if (j < cards.lastIndex) SK.muted else SK.line) }
                        Box(Modifier.size(44.dp).clickable { onRemove(j) }, Alignment.Center) { Icon(Ic.CLOSE, SK.muted) }
                    } else {
                        T("바꾸기", 13.sp, 500, SK.muted, Modifier.padding(end = 12.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onAdd).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.PLUS, LocalAccent.current, 20.dp)
                T("카드 더하기", 16.sp, 600, LocalAccent.current, Modifier.padding(start = 10.dp))
            }
        }
    }
}

/** "카드 고르기": every card rendered by the real BoardView at the slot's own size. */
@Composable
fun CardPickerSheet(
    title: String,
    slotW: Int,
    slotH: Int,
    config: AppConfig,
    state: kr.dot.sb.ui.state.StandbyState,
    accent: Long?,
    face: FaceId,
    current: CardType?,
    taken: Set<CardType>,
    onDismiss: () -> Unit,
    /** The phone widget card is offered on boards only, never on the widgets page (D6). */
    widget: Boolean = false,
    onPick: (CardType) -> Unit,
) {
    val acc = LocalAccent.current
    Sheet(title, onDismiss) {
        T("칸 크기(${slotW}×${slotH}dp)에서 보이는 모습이에요", 13.sp, 400, SK.muted, Modifier.padding(start = 4.dp, bottom = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // the media card plays video too (r8) and one notification card replaced stack + list (r9): the old types stay only where a board already has them
            CardType.entries.filter { ((it != CardType.USAGE || UsageFeature.enabled) && (it != CardType.WIDGET || widget)) || it == current }.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { card ->
                        val dup = card in taken && card != current
                        Column(Modifier.weight(1f).clickable(enabled = !dup) { onPick(card) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val shape = RoundedCornerShape(12.dp)
                            Box(Modifier.fillMaxWidth().clip(shape).border(if (card == current) 2.dp else 0.dp, acc, shape)) {
                                PreviewFrame(slotW.coerceAtLeast(60), slotH.coerceAtLeast(60), Modifier.fillMaxWidth(), maxHeight = 150.dp, radius = 10.dp) {
                                    BoardView(Board("pick", "", Layout.ONE, listOf(listOf(card)), accent, face), state, config, StandbyActions.NONE, Modifier.fillMaxSize())
                                }
                            }
                            T(card.ko + if (dup) " (이미 있어요)" else "", 14.sp, if (card == current) 700 else 500, if (dup) SK.muted else SK.text, Modifier.padding(start = 2.dp), maxLines = 1)
                        }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
    }
}

// ---- phone widget options (r9 D6; a long press on the card opens this) -----------------------------------------------

/** Option [key] ("slot:index") of board [boardId] through [f]. */
private fun setWidget(update: Update, boardId: String, key: String, f: (SlotOptions) -> SlotOptions) =
    update { c -> BoardOps.replace(c, boardId) { b -> b.copy(options = b.options + (key to f(b.options[key] ?: SlotOptions()))) } }

/** Provider pick + bind; the new id replaces the slot's old one, which the activity's update releases (removedWidgetIds). */
private fun pickWidget(env: SettingsEnv, update: Update, boardId: String, key: String) =
    env.pickWidget { wid -> setWidget(update, boardId, key) { it.copy(widgetId = wid) } }

/** Route "widget:<board>:<slot>:<index>"; null when that card is no longer a phone widget (the caller shows Home). */
@Composable
fun WidgetOptionsScreen(route: String, config: AppConfig, update: Update, env: SettingsEnv, back: () -> Unit): Unit? {
    val parts = route.split(':')
    val board = config.boards.firstOrNull { it.id == parts.getOrNull(1) } ?: return null
    val slot = parts.getOrNull(2)?.toIntOrNull() ?: return null
    val j = parts.getOrNull(3)?.toIntOrNull() ?: return null
    if (board.slots.getOrNull(slot)?.getOrNull(j) != CardType.WIDGET) return null
    val key = "$slot:$j"
    val o = board.options[key] ?: SlotOptions()
    val set = { f: (SlotOptions) -> SlotOptions -> setWidget(update, board.id, key, f) }
    val id = o.widgetId
    Page("폰 위젯", back) {
        Section(null) {
            item { NavRow(if (id == null) "위젯 고르기" else "위젯 바꾸기", { pickWidget(env, update, board.id, key) }, subtitle = id?.let(env.widgetLabel) ?: "홈 화면 위젯 가운데 하나") }
            if (id != null) item { NavRow("위젯 설정", { env.configureWidget(id) }, subtitle = "위젯 앱이 가진 설정 화면을 열어요") }
        }
        Section("StandBy에서 보이는 모습", footer = "다크·라이트는 위젯 앱이 지원할 때만 바뀌어요. 밤에는 밝은 위젯을 뒤집어 어둡게 보여 줘요.") {
            item { SliderRow("투명도", "${(o.opacity * 100).roundToInt()}%", o.opacity, 0f..1f, { v -> set { it.copy(opacity = v) } }) }
            item { ControlRow("다크·라이트") { Segmented(WidgetMode.entries.map { it.ko }, o.widgetMode.ordinal, { i -> set { it.copy(widgetMode = WidgetMode.entries[i]) } }) } }
            item { SliderRow("모서리", "${o.radiusDp}dp", o.radiusDp.toFloat(), 0f..40f, { v -> set { it.copy(radiusDp = v.roundToInt()) } }) }
            item { SwitchRow("밤에 숨기기", o.hideAtNight, { on -> set { it.copy(hideAtNight = on) } }) }
        }
    }
    return Unit
}
