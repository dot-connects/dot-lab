package kr.dot.sb.settings

import kr.dot.sb.ui.theme.withTheme
import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.TextSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.Theme
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.screens.StandbyRoot
import kr.dot.sb.ui.state.StandbyActions

/** The fixed first three pages (spec §1c) plus user boards, as the editor and the carousel list them. */
internal fun widgetsBoard(c: AppConfig) = BoardOps.fit(Board("widgets", "위젯", Layout.TWO, listOf(c.widgetLeft, c.widgetRight)))

@Composable
fun HomeScreen(config: AppConfig, nav: Nav, env: SettingsEnv, update: Update = {}) {
    var full by remember { mutableStateOf(false) }
    val missing = env.missing
    Box(Modifier.fillMaxSize()) {
        Page("스탠바이", null, side = {
            if (missing.isNotEmpty()) {
                PermBanner(missing.size, missing.first().ko) { nav.push(Routes.PERMS) }
            }
            Gap(16.dp)
            Carousel(config) { full = true }
        }) {
            ThemePicker(config, update)
            Section("배경 · 글자", footer = "배경을 고르면 카드 판이 반투명해져요. 밤에는 늘 검정이에요.") {
                item {
                    ControlRow("배경") {
                        ChipFlow(BoardBg.entries.map { it.ko }, config.boardBg.ordinal, { i -> update { it.copy(boardBg = BoardBg.entries[i]) } })
                        if (config.boardBg == BoardBg.SOLID) {
                            ColorPalette(config.boardBgColor, { c -> update { it.copy(boardBgColor = c) } }, BG_SWATCHES, Modifier.padding(top = 8.dp))
                        }
                    }
                }
                item {
                    ControlRow("글자 크기", note = "알림 · 일정처럼 읽는 글씨만 바뀌어요. 시계 숫자는 그대로예요.") {
                        Segmented(TextSize.entries.map { "${it.ko} ${(it.f * 100).toInt()}%" }, config.textSize.ordinal, { i -> update { it.copy(textSize = TextSize.entries[i]) } })
                    }
                }
            }
            Section("화면 구성") {
                item { NavRow("화면", { nav.push(Routes.BOARDS) }, subtitle = "위젯 · 사진 · 시계 + 내 화면 ${config.boards.size}개") }
                item { NavRow("시계 스타일", { nav.push(Routes.CLOCKS) }, subtitle = "서체, 색, 크기를 시계마다 따로") }
                item { NavRow("위젯 카드", { nav.push(Routes.WIDGETS) }, subtitle = "위젯 화면 왼쪽·오른쪽에 쌓을 카드") }
            }
            Section("동작") {
                item { NavRow("야간 모드 · 디스플레이", { nav.push(Routes.NIGHT) }, subtitle = "밝기, 꺼지는 시점, 알림") }
                if (env.foldable) item { NavRow("알람 · 무드등", { nav.push(Routes.ALARM) }, subtitle = "폴드 안쪽 화면을 부드럽게 밝혀요") }
            }
            Section("연결") {
                item { NavRow("데이터", { nav.push(Routes.DATA) }, subtitle = "날씨 지역, D-day, 수면 소리, 타이머") }
                item { NavRow("권한", { nav.push(Routes.PERMS) }, badge = if (missing.isEmpty()) null else "${missing.size}개 필요", value = if (missing.isEmpty()) "모두 켜짐" else null) }
                item { NavRow("정보", { nav.push(Routes.ABOUT) }) }
            }
        }
        if (full) {
            val none = remember { MutableInteractionSource() }
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black /* literal-ok: behind the root */).clickable(interactionSource = none, indication = null) { full = false }) {
                StandbyRoot(Sample.state(config), config, StandbyActions.NONE, Modifier.fillMaxSize())
                Box(Modifier.align(Alignment.BottomCenter).systemBarsPadding().padding(bottom = 16.dp).clip(RoundedCornerShape(50)).background(SK.scrim).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    T("화면을 누르면 닫혀요", 13.sp, 500, SK.text)
                }
            }
        }
    }
}

/** Dark picks for the "단색" board background (light ones would fight the dark themes' ink); the picker does the rest. */
private val BG_SWATCHES = listOf(0xFF1A1430, 0xFF101C2A, 0xFF13241A, 0xFF2A1A12, 0xFF26142A, 0xFF1E1E20)

/**
 * "테마" row (spec-r9 §2): every [Theme] as the real widgets page drawn in it, no names, in a horizontal scroll, so
 * what you tap is what StandBy shows. Tap = switch (saved like every other setting).
 */
@Composable
private fun ThemePicker(config: AppConfig, update: Update) {
    val state = remember(config.city, config.use24h) { Sample.state(config) }
    val board = remember(config.widgetLeft, config.widgetRight) { widgetsBoard(config) }
    val acc = LocalAccent.current
    val list = rememberLazyListState((Theme.entries.indexOf(config.theme) - 1).coerceAtLeast(0))
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        T("테마", 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, bottom = 8.dp))
        LazyRow(Modifier.fillMaxWidth(), list, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(Theme.entries) { th ->
                val on = th == config.theme
                val cfg = remember(config, th) { config.withTheme(th) }
                Box(
                    Modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).background(if (on) acc.copy(alpha = 0.16f) else SK.surface)
                        .then(if (on) Modifier.border(2.dp, acc, RoundedCornerShape(14.dp)) else Modifier).padding(8.dp),
                ) {
                    kr.dot.sb.ui.theme.ProvideTheme(cfg) {
                        PreviewFrame(PreviewDevice.FOLD_COVER.w, PreviewDevice.FOLD_COVER.h, Modifier.fillMaxWidth(), radius = 8.dp) {
                            BoardView(board, state, cfg, StandbyActions.NONE, Modifier.fillMaxSize())
                        }
                    }
                    Box(Modifier.matchParentSize().clickable { update { it.withTheme(th) } })
                }
            }
        }
    }
}

@Composable
private fun PermBanner(count: Int, first: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SK.surface).clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(SK.danger))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            T("허용이 필요한 항목이 ${count}개 있어요", 15.sp, 600)
            T("먼저 \"$first\"부터 켜 주세요", 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp))
        }
        Icon(Ic.CHEVRON, SK.muted, 20.dp)
    }
}

/** Swipeable live previews: the widgets page, the clock page, then every board, all drawn by the real composables. */
@Composable
private fun Carousel(config: AppConfig, onFull: () -> Unit) {
    val state = remember(config.styles, config.boards, config.widgetLeft, config.widgetRight, config.city, config.use24h) { Sample.state(config) }
    data class Pg(val name: String, val sub: String, val page: Int)
    val pages = listOf(Pg("위젯", "고정 · 카드를 쌓아 넘겨요", 0), Pg("시계", "고정 · 위아래로 스타일 전환", 2)) +
        config.boards.mapIndexed { i, b -> Pg(b.name, b.layout.ko, BoardOps.FIRST_BOARD_PAGE + i) }
    val initial = when (config.startPage) { 0, 1 -> 0; 2 -> 1; else -> config.startPage - 1 }.coerceIn(0, pages.lastIndex)
    val pager = rememberPagerState(initialPage = initial) { pages.size }
    val dev = PreviewDevice.FOLD_COVER
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        HorizontalPager(pager, contentPadding = PaddingValues(end = 36.dp), pageSpacing = 12.dp, verticalAlignment = Alignment.Top) { i ->
            Column(Modifier.fillMaxWidth()) {
                PreviewFrame(dev.w, dev.h, Modifier.fillMaxWidth()) {
                    when (i) {
                        0 -> BoardView(widgetsBoard(config), state, config, StandbyActions.NONE, Modifier.fillMaxSize())
                        1 -> ClockFace(FaceId.DIGITAL, config.clockStyle(FaceId.DIGITAL), state, Modifier.fillMaxSize())
                        else -> BoardView(config.boards[i - 2], state, config, StandbyActions.NONE, Modifier.fillMaxSize())
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(pages[i].name, 16.sp, 600, maxLines = 1)
                        T(pages[i].sub, 13.sp, 400, SK.muted, maxLines = 1)
                    }
                    if (pages[i].page == config.startPage) Pill("시작 화면", LocalAccent.current)
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        pages.indices.forEach { i ->
            Box(Modifier.padding(horizontal = 3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape).background(if (i == pager.currentPage) LocalAccent.current else SK.line))
        }
    }
    Box(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        ActionButton("전체 화면으로 미리보기", onFull, Modifier.fillMaxWidth(), primary = false)
    }
}
