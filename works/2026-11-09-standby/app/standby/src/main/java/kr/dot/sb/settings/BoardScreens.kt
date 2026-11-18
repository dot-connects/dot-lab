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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.unit.Dp
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
import kr.dot.sb.ui.theme.drawnTheme
import androidx.compose.material3.Switch
import androidx.compose.ui.draw.alpha
import kr.dot.sb.model.PHOTO_INTERVAL_SEC
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import java.util.Collections
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import kotlin.math.roundToInt

private fun <T> List<T>.moved(i: Int, d: Int): List<T> {
    val j = i + d
    if (i !in indices || j !in indices) return this
    return toMutableList().also { Collections.swap(it, i, j) }
}

// ---- 페이지와 카드 --------------------------------------------------------------------------------------------------

/** "기본 페이지 · 사진 · 시계" + one line per kind of page (mockup `pages()`). */
internal fun pageName(p: PageRef) = p.board?.name ?: listOf(WIDGETS_PAGE, "사진", "시계")[p.index]

/** The fixed first page as settings names it (plan-r15 F7: 위젯 only for home-screen widgets). */
internal const val WIDGETS_PAGE = "기본 페이지"

/**
 * plan-r15 F5 slot names by place instead of number ("왼쪽 칸", "오른쪽 위 칸"), read off the layout's landscape
 * rectangles: a slot narrower than the screen is 왼쪽 / 가운데 / 오른쪽, one shorter than it 위 / 아래.
 */
internal fun slotNames(layout: Layout): List<String> {
    val w = 800f
    val h = 480f
    return LayoutGeo.slots(layout, w, h).map { r ->
        val cx = r.x + r.w / 2
        val cy = r.y + r.h / 2
        val across = if (r.w > w * 0.7f) null else if (cx < w * 0.4f) "왼쪽" else if (cx > w * 0.6f) "오른쪽" else "가운데"
        val up = if (r.h > h * 0.7f) null else if (cy < h / 2) "위" else "아래"
        (listOfNotNull(across, up) + "칸").joinToString(" ")
    }
}

/**
 * 페이지와 카드 (plan-r15 F5): 처음 보이는 페이지, then every page as thumbnail + name + chevron; [편집] turns the rows
 * into on/off switches (시계 always on) and drag handles for my pages (the fixed three stay first). [페이지 추가] =
 * 빈 페이지 or a recommended page that is not in the list.
 */
@Composable
fun PagesScreen(config: AppConfig, update: Update, nav: Nav, back: () -> Unit) {
    val state = remember(config.styles, config.boards, config.widgetLeft, config.widgetRight, config.city, config.use24h) { Sample.state(config) }
    var adding by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    val visible = Pages.visible(config)
    val start = visible.getOrNull(Pages.start(config))
    val face = kr.dot.sb.ui.clock.ClockRegistry.pages(config.myClocks).first()
    Box(Modifier.fillMaxSize()) {
        Page("페이지와 카드", back) {
            Section(null) { item { NavRow("처음 보이는 페이지", { starting = true }, value = start?.let(::pageName)) } }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                T("페이지", 13.sp, 600, SK.muted, Modifier.weight(1f).padding(start = 16.dp))
                Box(Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable { editing = !editing }.padding(horizontal = 14.dp), Alignment.Center) {
                    T(if (editing) "완료" else "편집", 15.sp, 700, LocalAccent.current)
                }
            }
            val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { 88.dp.toPx() }
            Group {
                Pages.all(config).forEach { p ->
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
                            subtitle = when (p.index) {
                                0 -> "카드 ${config.widgetLeft.size + config.widgetRight.size}장"
                                1 -> if (config.photoFolders.isEmpty()) "폴더를 골라 주세요" else "폴더 ${config.photoFolders.size}개"
                                Pages.CLOCK -> "쓰는 시계 ${config.myClocks.size}개"
                                else -> "카드 ${b!!.slots.sumOf { it.size }}장"
                            },
                            start = p == start,
                            on = Pages.isOn(config, p),
                            editing = editing,
                            onToggle = if (p.index == Pages.CLOCK) null else { v -> update { Pages.setOn(it, p, v) } },
                            onClick = { nav.push(pageRoute(p)) },
                            onMove = if (b == null) null else { steps -> update { c -> moveBoard(c, i, steps) } },
                            canUp = b != null && i > 0, canDown = b != null && i < config.boards.lastIndex,
                            rowPx = rowPx,
                        )
                    }
                }
            }
            T("끈 페이지는 보이지 않아요.", 13.sp, 400, SK.muted, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), lineHeight = 18.sp)
            TipLine(Tip.BOARD_HOLD.line)
            Gap(8.dp)
            ActionButton("페이지 추가", { adding = true }, Modifier.fillMaxWidth())
        }
        if (starting) StartPageSheet(config, update) { starting = false }
        if (adding) Sheet("페이지 추가", { adding = false }) {
            Group {
                item {
                    NavRow("빈 페이지", {
                        val id = BoardOps.add(config).boards.last().id
                        update { BoardOps.add(it) }
                        adding = false
                        nav.push(Routes.board(id))
                    }, subtitle = "칸 나누기와 카드를 직접 골라요.")
                }
                BoardOps.missingPresets(config).forEach { b ->
                    item { NavRow(b.name, { update { BoardOps.addPreset(it, b) }; adding = false }, subtitle = "추천 · ${b.layout.ko}") }
                }
            }
        }
    }
}

/** Board [i] moved [steps] places (negative = up), one swap at a time so the start page follows ([BoardOps.move]). */
internal fun moveBoard(c: AppConfig, i: Int, steps: Int): AppConfig {
    var cfg = c
    var at = i
    val d = if (steps < 0) -1 else 1
    repeat(kotlin.math.abs(steps)) { val n = BoardOps.move(cfg, at, d); if (n === cfg) return cfg; cfg = n; at += d }
    return cfg
}

/**
 * One page in 페이지와 카드: thumbnail, name (+ 처음), chevron. In [editing]: a switch (none for 시계) and, for my pages,
 * a drag handle (long travel = several places; a11y actions move one place). Off = dimmed.
 */
@Composable
internal fun PageRow(
    thumb: @Composable () -> Unit,
    title: String,
    subtitle: String,
    start: Boolean,
    onClick: () -> Unit,
    on: Boolean = true,
    editing: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    onMove: ((Int) -> Unit)? = null,
    canUp: Boolean = false,
    canDown: Boolean = false,
    rowPx: Float = 0f,
) {
    val dim = if (on) 1f else 0.4f
    var dy by remember { mutableStateOf(0f) }
    val move by androidx.compose.runtime.rememberUpdatedState(onMove)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).zIndex(if (dy != 0f) 1f else 0f).graphicsLayer { translationY = dy }
            .then(if (dy != 0f) Modifier.background(SK.surface2) else Modifier)
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).heightIn(min = 56.dp).clickable(enabled = !editing, onClick = onClick).alpha(dim), verticalAlignment = Alignment.CenterVertically) {
            PreviewFrame(751, 475, Modifier.width(76.dp), radius = 6.dp) { thumb() }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T(title, 16.sp, 600, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                    if (start && on) Pill("처음", LocalAccent.current, Modifier.padding(start = 6.dp))
                }
                T(if (on) subtitle else "끔", 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp), maxLines = 1)
            }
        }
        if (!editing) Icon(Ic.CHEVRON, SK.muted.copy(alpha = 0.7f), 20.dp, Modifier.padding(end = 8.dp))
        else {
            if (onToggle != null) Switch(on, onToggle, Modifier.padding(horizontal = 4.dp), colors = accentSwitch())
            if (onMove != null) Box(
                Modifier.size(48.dp)
                    .pointerInput(rowPx) {
                        detectDragGestures(
                            onDragEnd = { val s = (dy / rowPx).roundToInt(); dy = 0f; if (s != 0) move?.invoke(s) },
                            onDragCancel = { dy = 0f },
                        ) { c, d -> c.consume(); dy += d.y }
                    }
                    .semantics {
                        contentDescription = "$title 순서 바꾸기"
                        customActions = listOfNotNull(
                            if (canUp) CustomAccessibilityAction("위로 옮기기") { move?.invoke(-1); true } else null,
                            if (canDown) CustomAccessibilityAction("아래로 옮기기") { move?.invoke(1); true } else null,
                        )
                    },
                Alignment.Center,
            ) { Icon(Ic.HANDLE, SK.muted) }
            else if (onToggle != null || canUp || canDown) Box(Modifier.size(48.dp))
        }
    }
}

/** 처음 보이는 페이지 (plan-r13 B8: a sheet): the pages that are on; a tap sets the start page and closes. */
@Composable
private fun StartPageSheet(config: AppConfig, update: Update, onDismiss: () -> Unit) {
    val visible = Pages.visible(config)
    val start = visible.getOrNull(Pages.start(config))
    Sheet("처음 보이는 페이지", onDismiss) {
        Group {
            visible.forEach { p ->
                item {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { update { it.copy(startPage = p.index) }; onDismiss() }.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        T(pageName(p), 16.sp, 500, SK.text, Modifier.weight(1f))
                        if (p == start) Icon(Ic.CHECK, LocalAccent.current)
                    }
                }
            }
        }
        T("Stand Screen이 켜질 때 처음 보이는 페이지예요. 끈 페이지는 여기에 없어요.", 13.sp, 400, SK.muted, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), lineHeight = 18.sp)
    }
}

// ---- 기본 페이지 (the widgets page stacks) ----------------------------------------------------------------------------

private fun <T> List<T>.swap(i: Int, d: Int): List<T> = moved(i, d)

@Composable
fun WidgetsScreen(config: AppConfig, update: Update, nav: Nav, back: () -> Unit) {
    val state = remember(config.city, config.use24h) { Sample.state(config) }
    var pick by remember { mutableStateOf<Pair<Boolean, Int?>?>(null) } // (left?, index or null = append)
    var undo by remember { mutableStateOf<Pair<String, AppConfig>?>(null) }
    val dev = PreviewDevice.FOLD_COVER
    val rects = LayoutGeo.slots(Layout.TWO, dev.w.toFloat(), dev.h.toFloat())
    /** Edit one side as a board stack: [moved] maps old index -> new (null = gone), options follow (11-14 per-card face). */
    fun editSide(left: Boolean, moved: (Int) -> Int?, f: (Board) -> Board = { it }, cards: (List<CardType>) -> List<CardType>) = update { c ->
        val s = if (left) 0 else 1
        val w = Board("widgets", "", Layout.TWO, listOf(c.widgetLeft, c.widgetRight), options = c.widgetOptions)
        val b = f(BoardOps.editStack(w, s, cards(w.slots[s]), moved))
        c.copy(widgetLeft = b.slots[0], widgetRight = b.slots[1], widgetOptions = b.options)
    }
    fun removeAt(left: Boolean, j: Int) {
        val card = (if (left) config.widgetLeft else config.widgetRight).getOrNull(j)
        if (card != null) undo = "${card.ko} 뺐어요" to config
        editSide(left, { k -> if (k < j) k else if (k == j) null else k - 1 }) { l -> l.filterIndexed { k, _ -> k != j } }
    }
    val first = Slide(WIDGETS_PAGE) { l -> BoardView(widgetsBoard(config), l.state, config, StandbyActions.NONE, Modifier.fillMaxSize()) }
    Box(Modifier.fillMaxSize()) {
        Page(WIDGETS_PAGE, back, preview = pagePreview(config, 0, first, nav)) {
            listOf(true to config.widgetLeft, false to config.widgetRight).forEach { (left, cards) ->
                CardStackSection(
                    title = (if (left) "왼쪽 칸" else "오른쪽 칸") + if (cards.size > 1) " · ${cards.size}장" else "",
                    cards = cards,
                    onReplace = { j -> pick = left to j },
                    onAdd = { pick = left to null },
                    onMove = { j, d -> editSide(left, { k -> when (k) { j -> j + d; j + d -> j; else -> k } }) { it.swap(j, d) } },
                    onRemove = { j -> removeAt(left, j) },
                    onGear = { j -> nav.push(cardRoute(cards[j])) },
                    footer = if (left) null else "카드가 여러 장이면 위아래로 넘겨 봐요. 맨 위 카드가 먼저 보여요.",
                )
            }
        }
        undo?.let { (text, prev) -> Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { UndoBar(text, prev, { update { prev } }) { undo = null } } }
        val p = pick
        if (p != null) {
            val (left, index) = p
            val r = rects[if (left) 0 else 1]
            val stack = if (left) config.widgetLeft else config.widgetRight
            CardPickerSheet(
                title = (if (left) "왼쪽 칸" else "오른쪽 칸") + " · 카드 고르기",
                slotW = r.w.toInt(), slotH = r.h.toInt(), config = config, state = state, accent = null, face = FaceId.DIGITAL,
                current = index?.let { stack.getOrNull(it) }, taken = stack.toSet(), onDismiss = { pick = null },
                onRemove = if (stack.size > 1) { t -> removeAt(left, stack.indexOf(t)) } else null,
                onPick = { card ->
                    // a replaced card's options (clock face) stay only when the type is unchanged; a new clock card shows the page's clock
                    editSide(left, { k -> k.takeIf { it != index || stack[it] == card } }) { l ->
                        if (index == null) l + card else l.mapIndexed { k, c -> if (k == index) card else c }
                    }
                    if (index != null) pick = null
                },
            )
        }
    }
}

// ---- 사진 (plan-r15 B1: picked folders, system folder picker) ------------------------------------------------------------

@Composable
fun PhotosScreen(config: AppConfig, update: Update, nav: Nav, env: SettingsEnv, back: () -> Unit) {
    // names first from the URI, then the index's names, counts and broken folders (slow: a full folder read)
    val folders by androidx.compose.runtime.produceState(config.photoFolders.map { kr.dot.sb.data.PhotoFolder(it, kr.dot.sb.data.PhotoIndex.fallbackName(it), null, false) }, config.photoFolders) {
        value = kotlinx.coroutines.withContext(env.io) { env.photoFolders(config.photoFolders) }
    }
    val first = pageSlides(config, include = 1).first { it.name == "사진" }
    // plan-r15 F2: only the photos page (no swipe to the other pages' settings)
    Page("사진", back, preview = Preview(config, listOf(first))) {
        Section("폴더", footer = "고른 폴더의 사진이 사진 페이지, 추억 카드, 흐린 사진 배경에 나와요. 카메라로 찍은 사진은 DCIM 안의 Camera 폴더에 있어요. 맨 위 폴더와 Download 폴더는 고를 수 없어요.") {
            if (folders.isEmpty()) item { T("폴더를 골라 주세요", 15.sp, 500, SK.muted, Modifier.padding(16.dp)) }
            folders.forEach { f ->
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(f.name, 16.sp, 500, SK.text, maxLines = 1)
                            val sub = if (f.broken) "폴더를 못 읽었어요 · 다시 골라 주세요" else f.count?.let { "사진 ${it}장" }
                            if (sub != null) T(sub, 13.sp, 400, if (f.broken) SK.danger else SK.muted)
                        }
                        ActionButton("빼기", { env.removeFolder(f.uri) }, primary = false)
                    }
                }
            }
            item { ActionButton(if (folders.isEmpty()) "고르기" else "폴더 추가", env.pickFolder, Modifier.padding(16.dp)) }
        }
        TipLine(Tip.PHOTOS_SWIPE.line)
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
    var deleting by remember { mutableStateOf(false) }
    var undo by remember { mutableStateOf<Pair<String, AppConfig>?>(null) }
    val names = slotNames(board.layout)
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
    fun removeAt(slot: Int, j: Int) {
        board.slots.getOrNull(slot)?.getOrNull(j)?.let { undo = "${it.ko} 뺐어요" to config }
        editSlot(slot, { k -> if (k < j) k else if (k == j) null else k - 1 }) { l -> l.filterIndexed { k, _ -> k != j } }
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
                    .clickable(onClickLabel = "${names.getOrElse(i) { "칸" }} 카드 고르기") { selected = i; pick = PickTarget(i, 0) },
            ) {
                // plan-r15 D5: the badge straddles the slot's top edge (centred), clear of the card's own header
                Box(Modifier.align(Alignment.TopCenter).offset(y = (-17).dp).clip(RoundedCornerShape(50)).background(if (on) acc else SK.scrim).padding(horizontal = 16.dp, vertical = 4.dp)) {
                    T(names.getOrElse(i) { "칸" }, 22.sp, 700, if (on) SK.bg else SK.text, maxLines = 1)
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
                            T(bad.joinToString(" · ") { it.ko } + ": 이 폰에서는 칸이 작아서 잘 안 보여요. 칸 수를 줄여 보세요.", 13.sp, 400, SK.muted, Modifier.padding(top = 10.dp), lineHeight = 18.sp)
                        }
                    }
                }
            }

            board.slots.forEachIndexed { s, stack ->
                CardStackSection(
                    title = names.getOrElse(s) { "칸" } + if (stack.size > 1) " · ${stack.size}장" else "",
                    cards = stack,
                    onReplace = { j -> selected = s; pick = PickTarget(s, j) },
                    onAdd = { selected = s; pick = PickTarget(s, null) },
                    onMove = { j, d -> editSlot(s, { k -> when (k) { j -> j + d; j + d -> j; else -> k } }) { it.moved(j, d) } },
                    onRemove = { j -> removeAt(s, j) },
                    onSettings = { j -> nav.push(Routes.apps(id, s, j)) },
                    onGear = { j -> nav.push(cardRoute(stack[j])) },
                )
            }
            if (board.slots.any { it.size > 1 }) TipLine(Tip.STACK_SWIPE.line)

            More {
                Section(null) {
                    item {
                        var name by remember(id) { mutableStateOf(board.name) }
                        ControlRow("이름") { Field(name, { name = it; edit { b -> b.copy(name = it) } }, "페이지 이름") }
                    }
                    item {
                        ControlRow("강조 색", note = "\"시계 따라\"로 두면 이 페이지 시계와 같은 색을 써요.") {
                            Segmented(listOf("시계 따라", "직접 고르기"), if (board.accent == null) 0 else 1, { i ->
                                edit { b -> b.copy(accent = if (i == 0) null else (b.accent ?: ClockStyle.SWATCHES[0])) }
                            })
                            if (board.accent != null) ColorPalette(board.accent, { c -> edit { b -> b.copy(accent = c) } }, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                    item {
                        // 내 시계 that draw in a slot; a face that left the list shows as the one the card falls back to
                        val faces = kr.dot.sb.ui.clock.ClockRegistry.boardPages(config.myClocks)
                        ControlRow("이 페이지의 시계") {
                            ChipFlow(faces.map { kr.dot.sb.ui.clock.spec(it).ko }, faces.indexOf(kr.dot.sb.ui.clock.ClockRegistry.cardFace(board.clockFace, config.myClocks)), { i -> edit { b -> b.copy(clockFace = faces[i]) } })
                        }
                    }
                }
                Gap(16.dp)
                ActionButton("복사", { update { BoardOps.duplicate(it, idx) }; back() }, Modifier.fillMaxWidth(), primary = false)
                if (BoardOps.deletable(board)) {
                    Gap(10.dp)
                    ActionButton("이 페이지 지우기", { deleting = true }, Modifier.fillMaxWidth(), danger = true)
                }
            }
        }
        if (deleting) DeleteDialog("'${board.name}' 페이지를 지울까요?", { deleting = false }) { back(); update { BoardOps.delete(it, idx) } }
        undo?.let { (text, prev) -> Box(Modifier.align(Alignment.BottomCenter).padding(16.dp)) { UndoBar(text, prev, { update { prev } }) { undo = null } } }

        val p = pick
        if (p != null) {
            val r = LayoutGeo.slots(board.layout, dw, dh).getOrNull(p.slot)
            val stack = board.slots.getOrNull(p.slot).orEmpty()
            CardPickerSheet(
                title = "${names.getOrElse(p.slot) { "칸" }} · 카드 고르기",
                slotW = (r?.w ?: 300f).toInt(), slotH = (r?.h ?: 300f).toInt(),
                config = config, state = state, accent = board.accent, face = board.clockFace,
                current = p.index?.let { stack.getOrNull(it) },
                taken = stack.toSet(),
                apps = true,
                onDismiss = { pick = null },
                onRemove = if (stack.size > 1) { t -> removeAt(p.slot, stack.indexOf(t)) } else null,
                onPick = { card ->
                    val j = p.index ?: stack.size
                    editSlot(p.slot, { k -> k.takeIf { it != p.index || stack[it] == card } }) { l -> if (p.index == null) l + card else l.mapIndexed { k, c -> if (k == p.index) card else c } }
                    // a new 앱 바로가기 card opens 앱 고르기 right away and closes this sheet
                    if (card == CardType.APPS && stack.getOrNull(j) != card) { pick = null; nav.push(Routes.apps(id, p.slot, j)) }
                    else if (p.index != null) pick = null
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

/** One slot's stack: rows of cards (tap = swap), a gear for the card's settings, reorder, remove, and an add row. */
@Composable
fun CardStackSection(
    title: String,
    cards: List<CardType>,
    /** Row names (a clock card with its face); defaults to the card names. */
    onReplace: (Int) -> Unit,
    onAdd: () -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    footer: String? = null,
    /** 앱 바로가기 rows get a 앱 고르기 button (boards only). */
    onSettings: ((Int) -> Unit)? = null,
    /** plan-r15 F5: a gear on cards with settings ([hasGear]) opens them. */
    onGear: ((Int) -> Unit)? = null,
) {
    Section(title, footer) {
        cards.forEachIndexed { j, card ->
            item {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onReplace(j) }.padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    T(card.ko, 16.sp, 500, SK.text, Modifier.weight(1f), maxLines = 1)
                    if (card == CardType.APPS && onSettings != null) {
                        Box(Modifier.heightIn(min = 48.dp).clickable { onSettings(j) }.padding(horizontal = 12.dp), Alignment.Center) { T("앱 고르기", 14.sp, 600, LocalAccent.current) }
                    }
                    if (onGear != null && hasGear(card)) {
                        Box(Modifier.size(48.dp).clickable(onClickLabel = "${card.ko} 설정 열기") { onGear(j) }, Alignment.Center) { Icon(Ic.GEAR, LocalAccent.current, 22.dp) }
                    }
                    if (cards.size > 1) {
                        Box(Modifier.size(48.dp).clickable(enabled = j > 0) { onMove(j, -1) }, Alignment.Center) { Icon(Ic.UP, if (j > 0) SK.muted else SK.line) }
                        Box(Modifier.size(48.dp).clickable(enabled = j < cards.lastIndex) { onMove(j, 1) }, Alignment.Center) { Icon(Ic.DOWN, if (j < cards.lastIndex) SK.muted else SK.line) }
                        Box(Modifier.size(48.dp).clickable(onClickLabel = "${card.ko} 빼기") { onRemove(j) }, Alignment.Center) { Icon(Ic.CLOSE, SK.muted) }
                    } else {
                        T("바꾸기", 13.sp, 500, SK.muted, Modifier.padding(end = 12.dp))
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onAdd).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Ic.PLUS, LocalAccent.current, 20.dp)
                T("카드 추가", 16.sp, 600, LocalAccent.current, Modifier.padding(start = 10.dp))
            }
        }
    }
}

/**
 * "카드 고르기": every card rendered by the real BoardView at the slot's own size. One 시계 tile (operator 11-15 "시계가
 * 너무 많아"): a placed clock card shows the page's clock and swipes through 내 시계 on StandBy. Add mode ([current] null):
 * an added card shows a check, and a second tap takes it out again ([onRemove]; null = the slot's last card, kept).
 */
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
    /** 앱 바로가기 is offered on boards only, never on the widgets page (its options live on boards). */
    apps: Boolean = false,
    onRemove: ((CardType) -> Unit)? = null,
    onPick: (CardType) -> Unit,
) {
    val acc = LocalAccent.current
    Sheet(title, onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // the media card plays video too (r8) and one notification card replaced stack + list (r9): the old types stay only where a board already has them
            CardType.entries.filter { ((it != CardType.USAGE || UsageFeature.enabled) && (it != CardType.APPS || apps)) || it == current }
                .chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { card ->
                        val added = card in taken && card != current
                        val tap: (() -> Unit)? = when {
                            !added -> ({ onPick(card) })
                            current == null && onRemove != null -> ({ onRemove(card) })
                            else -> null
                        }
                        Column(Modifier.weight(1f).clickable(enabled = tap != null) { tap?.invoke() }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val shape = RoundedCornerShape(12.dp)
                            // border(0.dp) still draws a hairline: only the current tile gets one
                            Box(Modifier.fillMaxWidth().clip(shape).then(if (card == current || added) Modifier.border(2.dp, acc, shape) else Modifier)) {
                                PreviewFrame(slotW.coerceAtLeast(60), slotH.coerceAtLeast(60), Modifier.fillMaxWidth(), maxHeight = 150.dp, radius = 10.dp) {
                                    BoardView(Board("pick", "", Layout.ONE, listOf(listOf(card)), accent, face), state, config, StandbyActions.NONE, Modifier.fillMaxSize())
                                }
                                // the live card's own taps (media buttons, pager) swallowed the pick (11-14 "카드 추가가 안돼"): one catcher on top
                                Box(Modifier.matchParentSize().clickable(enabled = tap != null) { tap?.invoke() })
                                if (added) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)), Alignment.Center) {
                                    Box(Modifier.size(46.dp).clip(CircleShape).background(acc), Alignment.Center) { Icon(Ic.CHECK, SK.bg, 24.dp) }
                                }
                            }
                            T(card.ko, 14.sp, if (card == current) 700 else 500, SK.text, Modifier.padding(start = 2.dp), maxLines = 1)
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

// ---- 앱 고르기 (plan-r14 C1) ------------------------------------------------------------------------------------------

/** Route "apps:<board>:<slot>:<index>"; null when that card is no longer 앱 바로가기 (the caller shows Home). */
@Composable
fun AppPickerScreen(route: String, config: AppConfig, update: Update, env: SettingsEnv, back: () -> Unit): Unit? {
    val parts = route.split(':')
    val board = config.boards.firstOrNull { it.id == parts.getOrNull(1) } ?: return null
    val slot = parts.getOrNull(2)?.toIntOrNull() ?: return null
    val j = parts.getOrNull(3)?.toIntOrNull() ?: return null
    if (board.slots.getOrNull(slot)?.getOrNull(j) != CardType.APPS) return null
    val key = "$slot:$j"
    val all by androidx.compose.runtime.produceState<List<kr.dot.sb.ui.cards.AppEntry>?>(null) {
        value = kotlinx.coroutines.withContext(env.io) { env.launcherApps() }
    }
    val opts = board.options[key] ?: SlotOptions()
    fun edit(f: (SlotOptions) -> SlotOptions) =
        update { c -> BoardOps.replace(c, board.id) { b -> b.copy(options = b.options + (key to f(b.options[key] ?: SlotOptions()))) } }
    AppPicker(all, opts.apps, back, top = { AppsLook(opts, config) { st -> edit { it.copy(iconStyle = st) } } }) { picked -> edit { it.copy(apps = picked) } }
    return Unit
}

/** plan-r15 E6: 아이콘 색 (원래 색 / 테마 색) above a live preview of this 앱 바로가기 card. */
@Composable
private fun AppsLook(opts: SlotOptions, config: AppConfig, onStyle: (kr.dot.sb.model.IconStyle) -> Unit) {
    val state = remember(config) { Sample.state(config) }
    val styles = kr.dot.sb.model.IconStyle.entries
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        T("아이콘 색", 16.sp, 500, SK.text, Modifier.padding(start = 4.dp, bottom = 8.dp))
        Segmented(listOf("원래 색", "테마 색"), styles.indexOf(opts.iconStyle), { i -> onStyle(styles[i]) })
        if (opts.iconStyle == kr.dot.sb.model.IconStyle.THEMED) {
            T("테마 색을 지원하지 않는 앱은 원래 색으로 보여요.", 13.sp, 400, SK.muted, Modifier.padding(top = 8.dp, start = 4.dp), lineHeight = 18.sp)
        }
        Spacer(Modifier.height(12.dp))
        PreviewFrame(376, 238, Modifier.fillMaxWidth(), radius = 12.dp) {
            BoardView(Board("apps", "", Layout.ONE, listOf(listOf(CardType.APPS)), options = mapOf("0:0" to opts)), state, config, StandbyActions.NONE, Modifier.fillMaxSize())
        }
    }
}

/** Launcher apps four a row with a search field; picked ones wear the card picker's veil + check, in tap order. */
@Composable
fun AppPicker(all: List<kr.dot.sb.ui.cards.AppEntry>?, picked: List<String>, back: () -> Unit, top: @Composable () -> Unit = {}, onPicked: (List<String>) -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var full by remember { mutableStateOf(false) }
    val acc = LocalAccent.current
    // 11-16 (r14 picks 5-A): the picked apps in tap order, pinned above the search; a tap there unpicks
    Page("앱 고르기", back, sticky = { PickedStrip(all, picked, full, acc) { p -> full = false; onPicked(picked - p) } }, after = {
        Field(q, { q = it }, "앱 이름")
        Spacer(Modifier.height(14.dp))
        val list = all?.filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }
        when {
            list == null -> T("앱 목록을 불러오는 중이에요.", 15.sp, 500, SK.muted, Modifier.padding(16.dp))

            list.isEmpty() -> T("그런 이름의 앱이 없어요. 다른 이름으로 찾아보세요.", 15.sp, 500, SK.muted, Modifier.padding(16.dp))
            else -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                list.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { a ->
                            val on = a.pkg in picked
                            Column(
                                Modifier.weight(1f).clickable {
                                    full = !on && picked.size >= kr.dot.sb.model.MAX_APPS
                                    if (on) onPicked(picked - a.pkg) else if (!full) onPicked(picked + a.pkg)
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)), Alignment.Center) {
                                    AppIcon(a, 56.dp)
                                    if (on) Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.55f)), Alignment.Center) {
                                        Box(Modifier.size(32.dp).clip(CircleShape).background(acc), Alignment.Center) { Icon(Ic.CHECK, SK.bg, 18.dp) }
                                    }
                                }
                                T(a.label, 12.sp, if (on) 700 else 500, SK.text, maxLines = 1, align = TextAlign.Center)
                            }
                        }
                        repeat(4 - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }) { top() }
}

@Composable
private fun AppIcon(a: kr.dot.sb.ui.cards.AppEntry?, size: Dp, fallback: String = a?.label.orEmpty()) {
    val bmp = a?.icon
    if (bmp != null) androidx.compose.foundation.Image(bmp, null, Modifier.size(size))
    else Box(Modifier.size(size).clip(RoundedCornerShape(size / 4)).background(SK.surface2), Alignment.Center) { T(fallback.take(1), (size.value * 0.4f).sp, 700, SK.muted) }
}

/** Caption + one row of the picked icons (order badge, x badge) and dashed slots up to MAX_APPS; scrolls sideways when narrow. */
@Composable
private fun PickedStrip(all: List<kr.dot.sb.ui.cards.AppEntry>?, picked: List<String>, full: Boolean, acc: Color, onUnpick: (String) -> Unit) {
    val max = kr.dot.sb.model.MAX_APPS
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SK.surface).padding(12.dp)) {
        T(if (full) "${max}개까지 고를 수 있어요." else "고른 앱 ${picked.size}/$max · 누르면 빼요", 13.sp, 500, if (full) acc else SK.muted, Modifier.padding(bottom = 4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp, start = 6.dp, end = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            picked.forEachIndexed { i, p ->
                val a = all?.firstOrNull { it.pkg == p }
                Box(Modifier.size(48.dp).clickable { onUnpick(p) }) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))) { AppIcon(a, 48.dp, a?.label ?: p.substringAfterLast('.')) }
                    Box(Modifier.align(Alignment.TopStart).offset((-6).dp, (-6).dp).clip(RoundedCornerShape(8.dp)).background(acc).padding(horizontal = 5.dp)) { T("${i + 1}", 11.sp, 800, SK.bg) }
                    Box(Modifier.align(Alignment.TopEnd).offset(6.dp, (-6).dp).size(18.dp).clip(CircleShape).background(SK.line), Alignment.Center) { Icon(Ic.CLOSE, SK.text, 12.dp) }
                }
            }
            repeat(max - picked.size) { DashedSlot() }
        }
    }
}

@Composable
private fun DashedSlot() {
    val c = SK.line
    androidx.compose.foundation.Canvas(Modifier.size(48.dp)) {
        val w = 1.5.dp.toPx()
        drawRoundRect(
            c, topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2), size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(w, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
        )
    }
}
