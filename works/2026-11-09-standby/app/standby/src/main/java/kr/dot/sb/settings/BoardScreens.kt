package kr.dot.sb.settings

import kr.dot.sb.data.UsageFeature
import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.draw.alpha
import kr.dot.sb.model.PHOTO_INTERVAL_SEC
import kr.dot.sb.model.PhotoSource
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import java.util.Collections

private fun <T> List<T>.moved(i: Int, d: Int): List<T> {
    val j = i + d
    if (i !in indices || j !in indices) return this
    return toMutableList().also { Collections.swap(it, i, j) }
}

// ---- 페이지 구성 ----------------------------------------------------------------------------------------------------

/** "위젯 · 사진 · 시계" + one line per kind of page (mockup `pages()`). */
private fun pageName(p: PageRef) = p.board?.name ?: listOf("위젯", "사진", "시계")[p.index]

/**
 * 페이지 구성 (mockup SCREENS.pages + operator 2026-11-13): 처음 보이는 페이지, then every page in order with its
 * thumbnail. A switch turns a page on or off (시계 always on; off = dimmed, out of the pager); boards move with ▲▼ and
 * open their editor; 위젯 opens its stacks, 사진 the photo screen, 시계 the clock screen. [페이지 추가] = 빈 페이지 or a
 * recommended page that is not in the list.
 */
@Composable
fun PagesScreen(config: AppConfig, update: Update, nav: Nav, back: () -> Unit) {
    val state = remember(config.styles, config.boards, config.widgetLeft, config.widgetRight, config.city, config.use24h) { Sample.state(config) }
    var adding by remember { mutableStateOf(false) }
    val visible = Pages.visible(config)
    val start = visible.getOrNull(Pages.start(config))
    val face = kr.dot.sb.ui.clock.ClockRegistry.pages(config.myClocks).first()
    Box(Modifier.fillMaxSize()) {
        Page("페이지 구성", back) {
            Section(null) { item { NavRow("처음 보이는 페이지", { nav.push(Routes.START) }, value = start?.let(::pageName)) } }
            Section("페이지 순서", footer = "위젯 · 사진 · 시계는 맨 앞에 고정돼요. 내 페이지는 ▲▼로 순서를 바꾸고, 누르면 고칠 수 있어요. 끈 페이지는 스탠바이에 나오지 않아요.") {
                val all = Pages.all(config)
                all.forEach { p ->
                    item {
                        val b = p.board
                        val i = p.index - BoardOps.FIRST_BOARD_PAGE
                        PageRow(
                            thumb = {
                                when (p.index) {
                                    0 -> BoardView(widgetsBoard(config), state, config, StandbyActions.NONE, Modifier.fillMaxSize())
                                    1 -> Box(Modifier.fillMaxSize().background(SK.surface2), Alignment.Center) { T("사진", 40.sp, 700, SK.muted) }
                                    Pages.CLOCK -> kr.dot.sb.ui.clock.ClockFace(face, config.clockStyle(face), state, Modifier.fillMaxSize(), analog = config.analogStyle)
                                    else -> BoardView(b!!, state, config, StandbyActions.NONE, Modifier.fillMaxSize())
                                }
                            },
                            title = pageName(p),
                            subtitle = when (p.index) { 0 -> "두 칸 · 카드 쌓기"; 1 -> config.photoSource.ko; Pages.CLOCK -> "내 시계 ${config.myClocks.size}개"; else -> b!!.layout.ko },
                            start = p == start,
                            on = Pages.isOn(config, p),
                            onToggle = if (p.index == Pages.CLOCK) null else { v -> update { Pages.setOn(it, p, v) } },
                            onClick = {
                                nav.push(pageRoute(p))
                            },
                            onMove = if (b == null) null else { d -> update { BoardOps.move(it, i, d) } },
                            canUp = i > 0, canDown = i < config.boards.lastIndex,
                        )
                    }
                }
            }
            Gap(16.dp)
            ActionButton("페이지 추가", { adding = true }, Modifier.fillMaxWidth())
        }
        if (adding) Sheet("페이지 추가", { adding = false }) {
            Group {
                item {
                    NavRow("빈 페이지", {
                        val id = BoardOps.add(config).boards.last().id
                        update { BoardOps.add(it) }
                        adding = false
                        nav.push(Routes.board(id))
                    }, subtitle = "칸 나누기와 카드를 직접 골라요")
                }
                BoardOps.missingPresets(config).forEach { b ->
                    item { NavRow(b.name, { update { BoardOps.addPreset(it, b) }; adding = false }, subtitle = "추천 · ${b.layout.ko}") }
                }
            }
        }
    }
}

/** One page in 페이지 구성: thumbnail, name (+ 처음), switch (none for 시계), ▲▼ for boards. Off = dimmed. */
@Composable
internal fun PageRow(
    thumb: @Composable () -> Unit,
    title: String,
    subtitle: String,
    start: Boolean,
    onClick: () -> Unit,
    on: Boolean = true,
    onToggle: ((Boolean) -> Unit)? = null,
    onMove: ((Int) -> Unit)? = null,
    canUp: Boolean = false,
    canDown: Boolean = false,
) {
    val dim = if (on) 1f else 0.4f
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).heightIn(min = 56.dp).clickable(onClick = onClick).alpha(dim), verticalAlignment = Alignment.CenterVertically) {
            PreviewFrame(751, 475, Modifier.width(76.dp), radius = 6.dp) { thumb() }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(title, 16.sp, 600, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (start && on) Pill("처음", LocalAccent.current, Modifier.padding(start = 6.dp))
                }
                T(if (on) subtitle else "꺼 둠", 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp), maxLines = 1)
            }
        }
        if (onToggle != null) Switch(
            on, onToggle, Modifier.padding(horizontal = 4.dp),
            colors = SwitchDefaults.colors(checkedThumbColor = SK.bg, checkedTrackColor = LocalAccent.current, checkedBorderColor = LocalAccent.current, uncheckedThumbColor = SK.muted, uncheckedTrackColor = SK.surface2, uncheckedBorderColor = SK.line),
        )
        if (onMove != null) {
            Box(Modifier.size(44.dp).clickable(enabled = canUp) { onMove(-1) }, Alignment.Center) { Icon(Ic.UP, if (canUp) SK.muted else SK.line) }
            Box(Modifier.size(44.dp).clickable(enabled = canDown) { onMove(1) }, Alignment.Center) { Icon(Ic.DOWN, if (canDown) SK.muted else SK.line) }
        }
    }
}

/** 처음 보이는 페이지: the pages that are on; the start page is set only here (consensus). */
@Composable
fun StartPageScreen(config: AppConfig, update: Update, back: () -> Unit) {
    val visible = Pages.visible(config)
    val start = visible.getOrNull(Pages.start(config))
    Page("처음 보이는 페이지", back) {
        Section(null, footer = "스탠바이가 켜질 때 처음 보이는 페이지예요. 끈 페이지는 여기에 없어요.") {
            visible.forEach { p ->
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { update { it.copy(startPage = p.index) } }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(pageName(p), 16.sp, 500, SK.text, Modifier.weight(1f))
                        if (p == start) Icon(Ic.CHECK, LocalAccent.current)
                    }
                }
            }
        }
    }
}

// ---- 위젯 (the widgets page stacks) --------------------------------------------------------------------------------

private fun <T> List<T>.swap(i: Int, d: Int): List<T> = moved(i, d)

/** A card as the picker offers it: clock cards come once per face (11-14: the clock card shows any of 내 시계). */
data class CardPick(val type: CardType, val face: FaceId? = null) {
    val ko: String get() = if (face != null) "${type.ko} · ${kr.dot.sb.ui.clock.spec(face).ko}" else type.ko
}

/** What stack [s] of [b] holds, a clock card with its effective face. */
internal fun picks(b: Board, s: Int): List<CardPick> = b.slots.getOrNull(s).orEmpty().mapIndexed { j, t ->
    CardPick(t, if (t == CardType.CLOCK) b.options["$s:$j"]?.face ?: b.clockFace else null)
}

internal fun withFace(b: Board, s: Int, j: Int, face: FaceId?): Board = BoardOps.withFace(b, s, j, face)

/** The faces a clock card can take: 내 시계 that draw in a slot, plus [keep] (a card's current face) if it left the list. */
internal fun cardFaces(config: AppConfig, keep: Collection<FaceId?>): List<FaceId> =
    (kr.dot.sb.ui.clock.ClockRegistry.pages(config.myClocks).filter { it in kr.dot.sb.ui.clock.ClockRegistry.boardFaces } + keep.filterNotNull()).distinct()

@Composable
fun WidgetsScreen(config: AppConfig, update: Update, nav: Nav, back: () -> Unit) {
    val state = remember(config.city, config.use24h) { Sample.state(config) }
    var pick by remember { mutableStateOf<Pair<Boolean, Int?>?>(null) } // (left?, index or null = append)
    val dev = PreviewDevice.FOLD_COVER
    val rects = LayoutGeo.slots(Layout.TWO, dev.w.toFloat(), dev.h.toFloat())
    /** Edit one side as a board stack: [moved] maps old index -> new (null = gone), options follow (11-14 per-card face). */
    fun editSide(left: Boolean, moved: (Int) -> Int?, f: (Board) -> Board = { it }, cards: (List<CardType>) -> List<CardType>) = update { c ->
        val s = if (left) 0 else 1
        val w = Board("widgets", "", Layout.TWO, listOf(c.widgetLeft, c.widgetRight), options = c.widgetOptions)
        val b = f(BoardOps.editStack(w, s, cards(w.slots[s]), moved))
        c.copy(widgetLeft = b.slots[0], widgetRight = b.slots[1], widgetOptions = b.options)
    }
    val first = Slide("위젯") { l -> BoardView(widgetsBoard(config), l.state, config, StandbyActions.NONE, Modifier.fillMaxSize()) }
    Box(Modifier.fillMaxSize()) {
        Page("위젯", back, preview = pagePreview(config, 0, first, nav)) {
            listOf(true to config.widgetLeft, false to config.widgetRight).forEach { (left, cards) ->
                val wb = widgetsBoard(config)
                CardStackSection(
                    title = if (left) "왼쪽 칸" else "오른쪽 칸",
                    cards = cards,
                    labels = picks(wb, if (left) 0 else 1).map { it.ko },
                    onReplace = { j -> pick = left to j },
                    onAdd = { pick = left to null },
                    onMove = { j, d -> editSide(left, { k -> when (k) { j -> j + d; j + d -> j; else -> k } }) { it.swap(j, d) } },
                    onRemove = { j -> editSide(left, { k -> if (k < j) k else if (k == j) null else k - 1 }) { l -> l.filterIndexed { k, _ -> k != j } } },
                    footer = if (left) null else "카드가 둘 이상이면 그 칸을 위아래로 넘겨요. 첫 번째 카드가 처음에 보여요.",
                )
            }
        }
        val p = pick
        if (p != null) {
            val (left, index) = p
            val r = rects[if (left) 0 else 1]
            val s = if (left) 0 else 1
            val have = picks(widgetsBoard(config), s)
            CardPickerSheet(
                title = (if (left) "왼쪽 칸" else "오른쪽 칸") + " · 카드 고르기",
                slotW = r.w.toInt(), slotH = r.h.toInt(), config = config, state = state, accent = null, face = FaceId.DIGITAL,
                current = index?.let { have.getOrNull(it) }, taken = have.toSet(), faces = cardFaces(config, have.map { it.face }), onDismiss = { pick = null },
                onPick = { card ->
                    val j = index ?: have.size
                    val stack = have.map { it.type }
                    editSide(left, { k -> k.takeIf { it != index || stack[it] == card.type } }, { b -> withFace(b, s, j, card.face) }) { l ->
                        if (index == null) l + card.type else l.mapIndexed { k, c -> if (k == index) card.type else c }
                    }
                    if (index != null) pick = null
                },
            )
        }
    }
}

// ---- 사진 (사진 고르기, operator 2026-11-13) --------------------------------------------------------------------------

@Composable
fun PhotosScreen(config: AppConfig, update: Update, nav: Nav, env: SettingsEnv, back: () -> Unit) {
    val folders by androidx.compose.runtime.produceState(emptyList<Pair<String, Int>>(), env.granted[Perm.PHOTOS]) {
        value = kotlinx.coroutines.withContext(env.io) { env.photoFolders() }
    }
    val first = pageSlides(config, include = 1).first { it.name == "사진" }
    Page("사진", back, preview = pagePreview(config, 1, first, nav)) {
        Section("사진 고르기") {
            PhotoSource.entries.forEach { s ->
                item { RadioRow(s.ko, null, config.photoSource == s, { update { it.copy(photoSource = s) } }) }
            }
            item { PermLine(Perm.PHOTOS) }
        }
        if (config.photoSource == PhotoSource.FOLDERS) Section("폴더", footer = "고른 폴더가 없으면 추천 사진을 보여줘요.") {
            if (folders.isEmpty()) item { T("사진이 있는 폴더가 없어요", 15.sp, 500, SK.muted, Modifier.padding(16.dp)) }
            folders.forEach { (name, count) ->
                item {
                    val on = name in config.photoFolders
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { update { it.copy(photoFolders = if (on) it.photoFolders - name else it.photoFolders + name) } }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            T(name, 16.sp, 500, SK.text, maxLines = 1)
                            T("사진 ${count}장", 13.sp, 400, SK.muted)
                        }
                        Box(
                            Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(if (on) LocalAccent.current else Color.Transparent)
                                .border(2.dp, if (on) LocalAccent.current else SK.muted, RoundedCornerShape(6.dp)),
                            Alignment.Center,
                        ) { if (on) Icon(Ic.CHECK, SK.bg, 18.dp) }
                    }
                }
            }
        }
        Section(null) {
            item {
                ControlRow("사진 넘김 간격") {
                    Segmented(PHOTO_INTERVAL_SEC.map { if (it < 60) "${it}초" else "${it / 60}분" }, PHOTO_INTERVAL_SEC.indexOf(config.photoIntervalSec), { i -> update { it.copy(photoIntervalSec = PHOTO_INTERVAL_SEC[i]) } })
                }
            }
        }
    }
}

// ---- page editor ------------------------------------------------------------------------------------------------------

private data class PickTarget(val slot: Int, val index: Int?) // index null = append to the stack

/**
 * One board (mockup SCREENS.edit): the preview is this page with tappable slots (tap = pick a card), then 칸 나누기, the
 * stacks per slot, and 자세히 (이름, 강조 색, 이 페이지의 시계, 복제, 지우기 — 작업 책상 is never deleted).
 */
@Composable
fun BoardEditorScreen(id: String, config: AppConfig, update: Update, nav: Nav, env: SettingsEnv, back: () -> Unit) {
    val idx = config.boards.indexOfFirst { it.id == id }
    val board = config.boards[idx]
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf<PickTarget?>(null) }
    var armed by remember { mutableStateOf(false) }
    // layouts too small for this phone's main screen are disabled (the preview's own toggles only change the picture)
    val dev = PreviewDevice.of(env.phone, inner = false)
    val dw = dev.w.toFloat()
    val dh = dev.h.toFloat()
    val state = remember(config.styles, config.city, config.use24h) { Sample.state(config) }
    val acc = LocalAccent.current
    fun edit(f: (Board) -> Board) = update { BoardOps.replace(it, id, f) }
    /** [moved]: old stack index -> new one (null = the card left); a widget's options and id follow it (D6). */
    fun editSlot(slot: Int, moved: (Int) -> Int?, f: (List<CardType>) -> List<CardType>) = edit { b ->
        BoardOps.editStack(b, slot, f(b.slots.getOrElse(slot) { emptyList() }), moved)
    }
    val first = Slide(board.name) { l ->
        BoardView(board, l.state, config, StandbyActions.NONE, Modifier.fillMaxSize())
        val w = l.device.width(l.portrait).toFloat()
        val h = l.device.height(l.portrait).toFloat()
        LayoutGeo.slots(board.layout, w, h, l.device.cover && l.portrait).forEachIndexed { i, r ->
            val on = i == selected
            Box(
                Modifier.offset(r.x.dp, r.y.dp).size(r.w.dp, r.h.dp)
                    .border(if (on) 4.dp else 2.dp, if (on) acc else Color.White.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .clickable(onClickLabel = "칸 ${i + 1} 카드 고르기") { selected = i; pick = PickTarget(i, 0) },
            ) {
                Box(Modifier.padding(12.dp).clip(RoundedCornerShape(50)).background(if (on) acc else SK.scrim).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    T("칸 ${i + 1}", 22.sp, 700, if (on) SK.bg else SK.text)
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Page(board.name, back, preview = pagePreview(config, BoardOps.FIRST_BOARD_PAGE + idx, first, nav, caption = "칸을 누르면 카드를 고를 수 있어요", full = false)) {
            Section("칸 나누기") {
                item {
                    ControlRow(null) {
                        val bad = Layout.entries.filter { LayoutGeo.disabled(it, dw, dh) }
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
                            val lines = bad.joinToString("\n") { "${it.ko}: 칸이 ${LayoutGeo.smallest(it, dw, dh).toInt()}dp밖에 안 돼서 이 폰에선 못 써요" }
                            T(lines, 13.sp, 400, SK.muted, Modifier.padding(top = 10.dp), lineHeight = 18.sp)
                        }
                    }
                }
            }

            board.slots.forEachIndexed { s, stack ->
                CardStackSection(
                    title = "칸 ${s + 1}" + if (stack.size > 1) " · 카드 ${stack.size}장 (위아래로 넘겨요)" else "",
                    cards = stack,
                    labels = picks(board, s).map { it.ko },
                    onReplace = { j -> selected = s; pick = PickTarget(s, j) },
                    onAdd = { selected = s; pick = PickTarget(s, null) },
                    onMove = { j, d -> editSlot(s, { k -> when (k) { j -> j + d; j + d -> j; else -> k } }) { it.moved(j, d) } },
                    onRemove = { j -> editSlot(s, { k -> if (k < j) k else if (k == j) null else k - 1 }) { l -> l.filterIndexed { k, _ -> k != j } } },
                    onSettings = { j -> nav.push(Routes.widget(id, s, j)) },
                )
            }

            More {
                Section(null) {
                    item {
                        var name by remember(id) { mutableStateOf(board.name) }
                        ControlRow("이름") { Field(name, { name = it; edit { b -> b.copy(name = it) } }, "페이지 이름") }
                    }
                    item {
                        ControlRow("강조 색", note = "시계 따라: 이 페이지의 시계 색을 같이 써요.") {
                            Segmented(listOf("시계 따라", "직접 고르기"), if (board.accent == null) 0 else 1, { i ->
                                edit { b -> b.copy(accent = if (i == 0) null else (b.accent ?: ClockStyle.SWATCHES[0])) }
                            })
                            if (board.accent != null) ColorPalette(board.accent, { c -> edit { b -> b.copy(accent = c) } }, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    item {
                        // 내 시계 that draw in a slot, plus the page's current face if it left the list
                        val faces = (kr.dot.sb.ui.clock.ClockRegistry.pages(config.myClocks).filter { it in kr.dot.sb.ui.clock.ClockRegistry.boardFaces } + board.clockFace).distinct()
                        ControlRow("이 페이지의 시계") {
                            ChipFlow(faces.map { kr.dot.sb.ui.clock.spec(it).ko }, faces.indexOf(board.clockFace), { i -> edit { b -> b.copy(clockFace = faces[i]) } })
                        }
                    }
                }
                Gap(16.dp)
                ActionButton("복제", { update { BoardOps.duplicate(it, idx) }; back() }, Modifier.fillMaxWidth(), primary = false)
                if (BoardOps.deletable(board)) {
                    Gap(10.dp)
                    ActionButton(if (armed) "한 번 더 누르면 지워져요" else "이 페이지 지우기", {
                        if (!armed) armed = true else { back(); update { BoardOps.delete(it, idx) } }
                    }, Modifier.fillMaxWidth(), danger = true)
                }
            }
        }

        val p = pick
        if (p != null) {
            val r = LayoutGeo.slots(board.layout, dw, dh).getOrNull(p.slot)
            CardPickerSheet(
                title = "칸 ${p.slot + 1} · 카드 고르기",
                slotW = (r?.w ?: 300f).toInt(), slotH = (r?.h ?: 300f).toInt(),
                config = config, state = state, accent = board.accent, face = board.clockFace,
                current = p.index?.let { picks(board, p.slot).getOrNull(it) },
                taken = picks(board, p.slot).toSet(),
                faces = cardFaces(config, picks(board, p.slot).map { it.face }),
                widget = true,
                onDismiss = { pick = null },
                onPick = { pk ->
                    val card = pk.type
                    val stack = board.slots.getOrNull(p.slot).orEmpty()
                    val j = p.index ?: stack.size
                    editSlot(p.slot, { k -> k.takeIf { it != p.index || stack[it] == card } }) { l -> if (p.index == null) l + card else l.mapIndexed { k, c -> if (k == p.index) card else c } }
                    edit { b -> withFace(b, p.slot, j, pk.face) }
                    if (p.index != null) pick = null
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
    /** Row names (a clock card with its face); defaults to the card names. */
    labels: List<String> = cards.map { it.ko },
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
                    T(labels.getOrElse(j) { card.ko }, 16.sp, 500, SK.text, Modifier.weight(1f), maxLines = 1)
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
    current: CardPick?,
    taken: Set<CardPick>,
    /** The faces the clock card is offered in (11-14: 내 시계). */
    faces: List<FaceId>,
    onDismiss: () -> Unit,
    /** The phone widget card is offered on boards only, never on the widgets page (D6). */
    widget: Boolean = false,
    onPick: (CardPick) -> Unit,
) {
    val acc = LocalAccent.current
    Sheet(title, onDismiss) {
        T("칸 크기(${slotW}×${slotH}dp)에서 보이는 모습이에요", 13.sp, 400, SK.muted, Modifier.padding(start = 4.dp, bottom = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // the media card plays video too (r8) and one notification card replaced stack + list (r9): the old types stay only where a board already has them
            CardType.entries.filter { ((it != CardType.USAGE || UsageFeature.enabled) && (it != CardType.WIDGET || widget)) || it == current?.type }
                .flatMap { t -> if (t == CardType.CLOCK) faces.map { CardPick(t, it) } else listOf(CardPick(t)) }
                .chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { card ->
                        val dup = card in taken && card != current
                        Column(Modifier.weight(1f).clickable(enabled = !dup) { onPick(card) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val shape = RoundedCornerShape(12.dp)
                            // border(0.dp) still draws a hairline: only the current tile gets one
                            Box(Modifier.fillMaxWidth().clip(shape).then(if (card == current) Modifier.border(2.dp, acc, shape) else Modifier)) {
                                PreviewFrame(slotW.coerceAtLeast(60), slotH.coerceAtLeast(60), Modifier.fillMaxWidth(), maxHeight = 150.dp, radius = 10.dp) {
                                    BoardView(Board("pick", "", Layout.ONE, listOf(listOf(card.type)), accent, card.face ?: face), state, config, StandbyActions.NONE, Modifier.fillMaxSize())
                                }
                                // the live card's own taps (media buttons, pager) swallowed the pick (11-14 "카드 추가가 안돼"): one catcher on top
                                Box(Modifier.matchParentSize().clickable(enabled = !dup) { onPick(card) })
                                if (dup) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape).background(acc), Alignment.Center) { Icon(Ic.CHECK, SK.bg, 20.dp) }
                            }
                            Row(Modifier.fillMaxWidth().padding(start = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                T(card.ko, 14.sp, if (card == current) 700 else 500, SK.text, Modifier.weight(1f, fill = false), maxLines = 1)
                                if (dup) T("추가됨", 14.sp, 700, acc, Modifier.padding(start = 6.dp), maxLines = 1)
                            }
                        }
                    }
                    if (row.size == 1) Box(Modifier.weight(1f))
                }
            }
        }
        // add mode stays open for several cards (11-14); replacing a card picks and closes
        if (current == null) ActionButton("완료", onDismiss, Modifier.fillMaxWidth().padding(top = 20.dp))
    }
}

// ---- phone widget options (r9 D6; a long press on the card opens this) -----------------------------------------------

/** Option [key] ("slot:index") of board [boardId] through [f]. */
private fun setWidget(update: Update, boardId: String, key: String, f: (SlotOptions) -> SlotOptions) =
    update { c -> BoardOps.replace(c, boardId) { b -> b.copy(options = b.options + (key to f(b.options[key] ?: SlotOptions()))) } }

/**
 * Provider pick + bind; the new id replaces the slot's old one, which the activity's update releases (removedWidgetIds).
 * The pick is async: when the card stopped being a phone widget meanwhile, nothing is written (the next process start's
 * sweep releases the unused id).
 */
private fun pickWidget(env: SettingsEnv, update: Update, boardId: String, key: String) =
    env.pickWidget { wid ->
        update { c ->
            val (slot, j) = key.split(':').map { it.toInt() }
            val b = c.boards.firstOrNull { it.id == boardId }
            if (b?.slots?.getOrNull(slot)?.getOrNull(j) != CardType.WIDGET) c
            else BoardOps.replace(c, boardId) { bb -> bb.copy(options = bb.options + (key to (bb.options[key] ?: SlotOptions()).copy(widgetId = wid))) }
        }
    }

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
