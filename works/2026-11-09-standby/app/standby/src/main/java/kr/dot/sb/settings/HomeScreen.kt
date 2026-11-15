package kr.dot.sb.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.Layout
import kr.dot.sb.model.NightMode
import kr.dot.sb.model.TextSize
import kr.dot.sb.model.Theme
import kr.dot.sb.model.UnfoldAction
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.withBoardBg
import kr.dot.sb.ui.theme.withTheme
import java.time.LocalTime
import kr.dot.sb.ui.theme.withThemeEdit
import kr.dot.sb.ui.theme.withMyThemes
import kr.dot.sb.ui.theme.applyMyTheme
import kr.dot.sb.ui.theme.LocalThemeTime
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.model.MyTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectTapGestures

/** The fixed first three pages (spec §1c) plus user boards, as the editor and the carousel list them. */
internal fun widgetsBoard(c: AppConfig) = BoardOps.fit(Board(BoardOps.WIDGETS, "위젯", Layout.TWO, listOf(c.widgetLeft, c.widgetRight), options = c.widgetOptions))

/** "(F)" rows: what a bar phone or a Flip sees instead of the control. */
internal const val FOLD_WHY = "갤럭시 폴드처럼 안쪽 화면이 큰 폰에서만 쓸 수 있어요"

/**
 * Home (mockup SCREENS.home): the page pager (scrolls with the list), the permission banner while any of the eight
 * permissions is missing, then four groups of rows, each showing its current value.
 */
@Composable
fun HomeScreen(config: AppConfig, nav: Nav, env: SettingsEnv) {
    val missing = env.missing
    fun need(p: Perm) = if (p in missing) "설정 필요" else null
    Page("스탠바이", null, preview = Preview(config, pageSlides(config), cards = true)) {
        if (missing.isNotEmpty()) PermBanner(Perm.entries.size, Perm.entries.size - missing.size) { nav.push(Routes.PERMS) }
        // 11-14 tree: card settings (시계, 날씨, 알림, 수면 소리, 알람, 카드에 쓰는 정보) live under 카드
        Section("화면") {
            item { NavRow("테마와 배경", { nav.push(Routes.THEME) }, value = config.theme.ko) }
            item { NavRow("페이지 구성", { nav.push(Routes.PAGES) }, subtitle = "위젯 · 사진 · 시계 + 내 페이지 ${config.boards.size}개") }
            item { NavRow("카드", { nav.push(Routes.CARDS) }, subtitle = "시계 · 날씨 · 알림 · 수면 소리 · 알람 …", badge = need(Perm.NOTIFICATIONS)) }
        }
        Section("밤과 꺼짐") {
            item {
                val v = when (config.nightMode) {
                    NightMode.OFF -> "안 씀"
                    NightMode.AUTO -> "어두워지면"
                    NightMode.SCHEDULE -> "정한 시간에 %02d–%02d".format(config.nightStartHour, config.nightEndHour)
                }
                NavRow("밤 화면", { nav.push(Routes.NIGHT) }, value = v)
            }
            item {
                val badge = need(Perm.DREAM)
                val off = when (config.displayOff) { DisplayOff.AUTO -> "어두워지면 꺼짐"; DisplayOff.AFTER_20S -> "20초 뒤 꺼짐"; DisplayOff.NEVER -> "계속 켜 둠" }
                NavRow("켜짐과 꺼짐", { nav.push(Routes.ONOFF) }, badge = badge, value = if (badge != null) null else "${if (config.startOnCharge) "충전하면 켜짐" else "직접 켤 때만"} · $off")
            }
        }
        Section("폴드") {
            item {
                val fold = env.phone == Phone.FOLD
                NavRow(
                    "폴드를 펼쳤을 때", { nav.push(Routes.FOLD) }, value = if (config.onUnfold == UnfoldAction.MOOD_LIGHT) "무드등" else "책 화면",
                    enabled = fold, why = FOLD_WHY,
                )
            }
        }
        Section("정보") {
            item { NavRow("권한", { nav.push(Routes.PERMS) }, badge = if (missing.isEmpty()) "모두 켜짐" else "${missing.size}개 필요", badgeOk = missing.isEmpty()) }
            item { NavRow("앱 정보", { nav.push(Routes.ABOUT) }) }
        }
    }
}

/**
 * 테마와 배경 (mockup SCREENS.theme): the theme strip (내 테마 first), the theme editor (r11), 글자 크기, 배경. The preview
 * swipes on to every page (글자 크기 shows on 모듈러) and shows the editor's draft while a colour or slider is held.
 */
@Composable
fun ThemeScreen(config: AppConfig, update: Update, back: () -> Unit) {
    // a held colour/slider (never saved); a new saved edit or theme drops it
    var draft by remember(config.theme, config.themeEdits[config.theme]) { mutableStateOf<ThemeEdit?>(null) }
    // the 아침/낮/저녁 preview chip (minute of the day), only while 시간 따라 흐름 is on
    var previewAt by rememberSaveable(config.theme) { mutableStateOf<Int?>(null) }
    val shown = remember(config, draft) { draft?.let { config.withThemeEdit(config.theme, it) } ?: config }
    val at = previewAt?.let { LocalTime.of(it / 60, it % 60) } ?: LocalThemeTime.current ?: LocalTime.now()
    kr.dot.sb.ui.theme.ProvideTheme(shown, at) {
        Page("테마와 배경", back, preview = Preview(shown, pageSlides(shown))) {
            ThemePicker(config, update)
            ThemeEditorSection(config, update, draft, { draft = it }, previewAt.takeIf { shown.themeEdits[shown.theme]?.flow != null }, { previewAt = it })
            Section(null) {
                item {
                    ControlRow("글자 크기", note = "카드 글자와 시계 아래 날짜·정보 줄이 바뀌어요. 큰 숫자는 그대로예요. 미리보기를 옆으로 넘겨 확인해요.") {
                        Segmented(TextSize.entries.map { it.ko }, config.textSize.ordinal, { i -> update { it.copy(textSize = TextSize.entries[i]) } })
                    }
                }
            }
            Section("배경", footer = "배경을 고르면 카드 판이 반투명해져요. 밤에는 늘 검정이에요.") {
                item {
                    ControlRow(null) {
                        // 단색 is folded into the editor's colours: its chip shows only while it is the stored choice
                        val bgs = BoardBg.entries.filter { it != BoardBg.SOLID || it == config.boardBg }
                        ChipFlow(bgs.map { it.ko }, bgs.indexOf(config.boardBg), { i -> update { it.withBoardBg(bgs[i]) } })
                        if (config.boardBg == BoardBg.SOLID) {
                            ColorPalette(config.boardBgColor, { c -> update { it.withBoardBg(BoardBg.SOLID, c) } }, BG_SWATCHES, Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Dark picks for the "단색" board background (light ones would fight the dark themes' ink); the picker does the rest. */
internal val BG_SWATCHES = listOf(0xFF1A1430, 0xFF101C2A, 0xFF13241A, 0xFF2A1A12, 0xFF26142A, 0xFF1E1E20)

/**
 * "테마" row (spec-r9 §2): every [Theme] as the real widgets page drawn in it with its name under it (operator 11-14:
 * names on), in a horizontal scroll, so what you tap is what StandBy shows. Tap = switch (saved like every other setting).
 * r11: 내 테마 come first with their names (no selected mark); long-press one for 이름 바꾸기 / 지우기. Tapping one whose
 * theme carries an unsaved edit first asks (no silent loss).
 */
@Composable
private fun ThemePicker(config: AppConfig, update: Update) {
    val state = remember(config.city, config.use24h) { Sample.state(config) }
    val board = remember(config.widgetLeft, config.widgetRight) { widgetsBoard(config) }
    val acc = LocalAccent.current
    val list = rememberLazyListState((config.myThemes.size + Theme.entries.indexOf(config.theme) - 1).coerceAtLeast(0))
    var confirm by remember { mutableStateOf<MyTheme?>(null) }
    var menu by remember { mutableStateOf<MyTheme?>(null) }
    var renaming by remember { mutableStateOf<MyTheme?>(null) }
    fun tapMy(m: MyTheme) {
        menu = null
        val cur = config.themeEdits[m.base]
        // an edit that is in no saved 내 테마 would be replaced: ask first
        if (cur != null && cur != m.edit && config.myThemes.none { it.base == m.base && it.edit == cur }) confirm = m
        else update { it.applyMyTheme(m) }
    }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        T("테마", 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, bottom = 8.dp))
        LazyRow(Modifier.fillMaxWidth(), list, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(config.myThemes) { m ->
                val cfg = remember(config, m) { config.applyMyTheme(m) }
                ThemeThumb(cfg, board, state, m.name, false, acc, { tapMy(m) }, { confirm = null; menu = m })
            }
            items(Theme.entries) { th ->
                val cfg = remember(config, th) { config.withTheme(th) }
                ThemeThumb(cfg, board, state, th.ko, th == config.theme, acc, { update { it.withTheme(th) } })
            }
        }
        confirm?.let { m ->
            InlineAsk("지금 꾸민 색이 바뀌어요") {
                ActionButton("바꾸기", { confirm = null; update { it.applyMyTheme(m) } }, primary = false)
                ActionButton("먼저 저장", {
                    confirm = null
                    update { c ->
                        val keep = MyTheme(nextMyThemeName(c.myThemes), m.base, c.themeEdits[m.base] ?: ThemeEdit())
                        c.withMyThemes(c.myThemes + keep).applyMyTheme(m)
                    }
                }, primary = false)
            }
        }
        menu?.let { m ->
            InlineAsk(m.name) {
                ActionButton("이름 바꾸기", { menu = null; renaming = m }, primary = false)
                ActionButton("지우기", { menu = null; update { c -> c.withMyThemes(c.myThemes.toMutableList().also { l -> l.indexOf(m).takeIf { it >= 0 }?.let(l::removeAt) }) } }, primary = false, danger = true)
                ActionButton("닫기", { menu = null }, primary = false)
            }
        }
    }
    renaming?.let { m ->
        NameDialog("이름 바꾸기", m.name, { renaming = null }) { name ->
            renaming = null
            update { c -> c.withMyThemes(c.myThemes.toMutableList().also { l -> l.indexOf(m).takeIf { it >= 0 }?.let { i -> l[i] = m.copy(name = name) } }) }
        }
    }
}

/** One thumbnail of the strip: the widgets page drawn with [cfg], [name] under it. [onLong] = a 내 테마 (long-press menu). */
@Composable
private fun ThemeThumb(
    cfg: AppConfig, board: Board, state: kr.dot.sb.ui.state.StandbyState, name: String, on: Boolean, acc: androidx.compose.ui.graphics.Color,
    onTap: () -> Unit, onLong: (() -> Unit)? = null,
) {
    // the gesture detector outlives recompositions: it calls the latest callbacks
    val tapNow by androidx.compose.runtime.rememberUpdatedState(onTap)
    val long by androidx.compose.runtime.rememberUpdatedState(onLong)
    val tap = if (onLong == null) Modifier.clickable(onClick = onTap)
        else Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { long?.invoke() }, onTap = { tapNow() }) }
    Column(
        Modifier.width(132.dp).clip(RoundedCornerShape(14.dp)).background(if (on) acc.copy(alpha = 0.16f) else SK.surface)
            .then(if (on) Modifier.border(2.dp, acc, RoundedCornerShape(14.dp)) else Modifier)
            .then(tap).padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            kr.dot.sb.ui.theme.ProvideTheme(cfg) {
                PreviewFrame(PreviewDevice.FOLD_COVER.w, PreviewDevice.FOLD_COVER.h, Modifier.fillMaxWidth(), radius = 8.dp) {
                    BoardView(board, state, cfg, StandbyActions.NONE, Modifier.fillMaxSize())
                }
            }
            // the thumbnail's own cards must not take the tap
            Box(Modifier.matchParentSize().then(tap))
        }
        StripLabel(name, on, acc)
    }
}

/** An inline question under the strip: a line of text and its buttons. */
@Composable
private fun InlineAsk(text: String, buttons: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp)).background(SK.surface).padding(12.dp)) {
        T(text, 15.sp, 600, SK.text, Modifier.padding(bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
    }
}

/** "충전 중 스탠바이가 뜨려면 설정이 더 필요해요 · 8개 중 M개 완료" (mockup .banner), only while something is missing. */
@Composable
private fun PermBanner(total: Int, done: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(SK.surface).clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(SK.danger))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            T("충전 중 스탠바이가 뜨려면 설정이 더 필요해요", 15.sp, 700)
            T("${total}개 중 ${done}개 완료", 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp))
        }
        Icon(Ic.CHEVRON, SK.muted, 20.dp)
    }
}
