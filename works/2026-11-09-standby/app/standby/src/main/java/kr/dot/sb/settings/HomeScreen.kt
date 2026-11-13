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
import kr.dot.sb.ui.theme.withTheme

/** The fixed first three pages (spec §1c) plus user boards, as the editor and the carousel list them. */
internal fun widgetsBoard(c: AppConfig) = BoardOps.fit(Board("widgets", "위젯", Layout.TWO, listOf(c.widgetLeft, c.widgetRight)))

/** "(F)" rows: what a bar phone or a Flip sees instead of the control. */
internal const val FOLD_WHY = "갤럭시 폴드처럼 안쪽 화면이 큰 폰에서만 쓸 수 있어요"

/**
 * Home (mockup SCREENS.home): the page pager (scrolls with the list), the permission banner while any of the eight
 * permissions is missing, then four groups of rows, each showing its current value. Rows open the pre-r10 screens until
 * Task 11 ports them.
 */
@Composable
fun HomeScreen(config: AppConfig, nav: Nav, env: SettingsEnv) {
    val missing = env.missing
    fun need(p: Perm) = if (p in missing) "설정 필요" else null
    Page("스탠바이", null, preview = Preview(config, pageSlides(config), cards = true)) {
        if (missing.isNotEmpty()) PermBanner(Perm.entries.size, Perm.entries.size - missing.size) { nav.push(Routes.PERMS) }
        Section("화면") {
            item { NavRow("테마와 배경", { nav.push(Routes.THEME) }, value = config.theme.ko) }
            item { NavRow("페이지 구성", { nav.push(Routes.BOARDS) }, subtitle = "위젯 · 사진 · 시계 + 내 페이지 ${config.boards.size}개") }
            item { NavRow("시계", { nav.push(Routes.CLOCKS) }, value = "내 시계 ${config.myClocks.size}개") }
            item {
                val badge = need(Perm.NOTIFICATIONS)
                NavRow("알림 표시", { nav.push(Routes.NIGHT) }, badge = badge, value = if (badge != null) null else if (config.showNotifications) "켜짐" else "꺼짐")
            }
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
                NavRow("켜짐과 꺼짐", { nav.push(Routes.NIGHT) }, badge = badge, value = if (badge != null) null else "${if (config.startOnCharge) "충전하면 켜짐" else "직접 켤 때만"} · $off")
            }
            item { NavRow("수면 소리", { nav.push(Routes.DATA) }, value = "${config.sleepSound.ko} · ${config.sleepMinutes}분") }
        }
        Section("알람과 폴드") {
            item { NavRow("알람", { nav.push(Routes.ALARM) }, value = if (config.alarmScreen) "스탠바이 화면" else "시계 앱 화면") }
            item {
                val fold = env.phone == Phone.FOLD
                NavRow(
                    "폴드를 펼쳤을 때", { nav.push(Routes.ALARM) }, value = if (config.onUnfold == UnfoldAction.MOOD_LIGHT) "무드등" else "책 화면",
                    enabled = fold, why = FOLD_WHY,
                )
            }
        }
        Section("정보와 권한") {
            item { NavRow("날씨와 도시", { nav.push(Routes.DATA) }, value = config.city.name) }
            item { NavRow("카드에 쓰는 정보", { nav.push(Routes.DATA) }) }
            item { NavRow("권한", { nav.push(Routes.PERMS) }, badge = if (missing.isEmpty()) "모두 켜짐" else "${missing.size}개 필요", badgeOk = missing.isEmpty()) }
            item { NavRow("앱 정보", { nav.push(Routes.ABOUT) }) }
        }
    }
}

/** 테마와 배경 until Task 11 ports it: the theme strip and the 배경 · 글자 rows that used to sit on home. */
@Composable
fun ThemeScreen(config: AppConfig, update: Update, back: () -> Unit) {
    Page("테마와 배경", back, preview = Preview(config, pageSlides(config))) {
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
