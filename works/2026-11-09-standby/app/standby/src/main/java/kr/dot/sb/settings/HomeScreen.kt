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
import androidx.compose.foundation.layout.aspectRatio
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
import kr.dot.sb.model.Theme
import kr.dot.sb.model.UnfoldAction
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.withBoardBg
import kr.dot.sb.ui.theme.withTheme
import kr.dot.sb.ui.theme.withThemeEdit
import kr.dot.sb.ui.theme.applyMyTheme
import kr.dot.sb.ui.theme.Look
import kr.dot.sb.ui.theme.StripPick
import kr.dot.sb.ui.theme.switchLook
import kr.dot.sb.ui.theme.renameMyTheme
import kr.dot.sb.ui.theme.deleteMyTheme
import kr.dot.sb.model.MyTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.detectTapGestures

/** The fixed first three pages (spec §1c) plus user boards, as the editor and the carousel list them. */
internal fun widgetsBoard(c: AppConfig) = BoardOps.fit(Board(BoardOps.WIDGETS, "기본 페이지", Layout.TWO, listOf(c.widgetLeft, c.widgetRight)))

/** plan-r15 B2: the privacy policy, published from the dot-lab repo (GitHub Pages). */
internal const val PRIVACY_URL = "https://dot-connects.github.io/dot-lab/works/2026-11-09-standby/page/privacy.html"

/**
 * Home (plan-r15 F1, mockup r15 "설정 · 홈 다시 묶기"): large title, one preview of the pages that are on (dots) with
 * [지금 보기], the banner only while charge-start's screen saver is missing, then the seven rows by task, each with an
 * accent line icon and its current value ("설정 필요" when a default-on feature lacks its grant), then 팁 and 정보.
 */
@Composable
fun HomeScreen(config: AppConfig, nav: Nav, env: SettingsEnv) {
    val missing = env.missing
    val need = "설정 필요"
    Page("Stand Screen", null, preview = Preview(config, pageSlides(config), initial = Pages.start(config))) {
        ActionButton("지금 보기", env.onShowNow, Modifier.fillMaxWidth().padding(top = 12.dp))
        if (Perm.DREAM in missing) PermBanner { env.onPermWalkOf(Perm.CHARGE_START) }
        Section(null) {
            item { NavRow("시계", { nav.push(Routes.CLOCKS) }, value = kr.dot.sb.ui.clock.spec(config.myClocks.firstOrNull() ?: kr.dot.sb.model.FaceId.DIGITAL).ko, lead = { HomeIcon(kr.dot.sb.ui.cards.Ic.ALARM) }) }
            item {
                val look = (StripPick.of(config) as? Look.Mine)?.my?.name ?: if (StripPick.of(config) == StripPick.Custom) "직접 만든 테마" else config.theme.ko
                NavRow("테마", { nav.push(Routes.THEME) }, value = look, lead = { Icon(Ic.THEME, LocalAccent.current, 22.dp) })
            }
            item { NavRow("페이지", { nav.push(Routes.PAGES) }, value = "${Pages.visible(config).size}개", lead = { Icon(Ic.PAGES, LocalAccent.current, 22.dp) }) }
            item {
                // operator 2026-11-18: 사진 (the folders) moved into 카드 › 추억; its "설정 필요" shows on 카드
                val used = kr.dot.sb.data.Needs.photos(config) || kr.dot.sb.data.Needs.memory(config)
                // operator 2026-11-19: 알림·알람 live in 카드 now, so their missing permission shows here too
                val alerts = (config.showNotifications || config.alarmScreen) && Perm.NOTIFICATIONS in missing
                NavRow("카드", { nav.push(Routes.CARD_LIST) }, badge = if (alerts || used && config.photoFolders.isEmpty()) need else null, lead = { Icon(Ic.GEAR, LocalAccent.current, 22.dp) })
            }
            item { NavRow("밤 화면", { nav.push(Routes.NIGHT) }, value = nightValue(config), lead = { HomeIcon(kr.dot.sb.ui.cards.Ic.MOON) }) }
            item {
                val badge = if (config.startOnCharge && Perm.CHARGE_START.any { it in missing }) need else null
                val v = if (config.startOnCharge) "충전하면 켜짐" else "직접 켤 때만"
                NavRow("자동으로 켜기·끄기", { nav.push(Routes.ONOFF) }, value = if (badge != null) null else v, badge = badge, lead = { Icon(Ic.BOLT, LocalAccent.current, 22.dp) })
            }
        }
        Section(null) {
            item {
                val fresh = Tip.entries.count { it.name !in config.tipsSeen }
                NavRow("팁", { nav.push(Routes.TIPS) }, value = if (fresh > 0) "새 팁 ${fresh}개" else null, lead = { Icon(Ic.TIP, LocalAccent.current, 22.dp) })
            }
            // operator 2026-11-18: the version, plus "권한 n개 필요" when a permission still in use is missing
            item {
                val need = Perms.forConfig(config).count { env.granted[it] != true }
                NavRow(
                    "정보", { nav.push(Routes.ABOUT) }, value = env.versionName.ifEmpty { null }, badge = if (need > 0) "권한 ${need}개 필요" else null,
                    lead = { Icon(Ic.INFO, LocalAccent.current, 22.dp) },
                )
            }
        }
    }
}

/** 밤 화면's value on Home and in its own radios: 끔 / 방이 어두워지면 / 밤 10시~아침 7시. */
internal fun nightValue(c: AppConfig): String = when (c.nightMode) {
    NightMode.OFF -> "끔"
    NightMode.AUTO -> "방이 어두워지면"
    NightMode.SCHEDULE -> hourWords(c.nightStartHour) + "~" + hourWords(c.nightEndHour)
}

/** An hour in words (plan-r15 F7: 시간은 말로): 22 → 밤 10시, 7 → 아침 7시, 13 → 낮 1시, 0 → 밤 12시. */
internal fun hourWords(h: Int): String {
    val part = when (h) { in 5..9 -> "아침"; in 10..16 -> "낮"; in 17..19 -> "저녁"; else -> "밤" }
    val twelve = (h % 12).let { if (it == 0) 12 else it }
    return "$part ${twelve}시"
}

/** A cards-set glyph as a settings row icon (outlined, in the accent). */
@Composable
private fun HomeIcon(ic: kr.dot.sb.ui.cards.Ic) =
    kr.dot.sb.ui.cards.IconView(ic, LocalAccent.current, Modifier.size(22.dp), bg = SK.surface, stroke = true)

/**
 * 테마 (mockup SCREENS.theme, plan-r13 A4, plan-r15 F1): the theme strip (내 테마 · built-ins · ＋ 직접 만들기), 배경,
 * the pull-down tip. The preview swipes on to every page. Colours and shapes live in 직접 만들기.
 */
@Composable
fun ThemeScreen(config: AppConfig, update: Update, nav: Nav, back: () -> Unit) {
    Page("테마", back, preview = Preview(config, pageSlides(config), initial = Pages.start(config))) {
        ThemePicker(config, update, { nav.push(Routes.THEME_CUSTOM) }) { name -> nav.push(Routes.themeEdit(name)) }
        TipLine(Tip.PULL_DOWN.line)
        Section("배경", footer = "배경을 고르면 카드가 비쳐 보여요. 밤에는 늘 검정이에요.") {
            item {
                ControlRow(null) {
                    // 단색 is folded into 직접 만들기's colours: its chip shows only while it is the stored choice
                    val bgs = BoardBg.entries.filter { it != BoardBg.SOLID || it == config.boardBg }
                    ChipFlow(bgs.map { it.ko }, bgs.indexOf(config.boardBg), { i -> update { it.withBoardBg(bgs[i]) } })
                    // plan-r15 B1: 내 사진 흐리게 draws the picked folders' photos; none picked = the plain ground
                    if (config.boardBg == BoardBg.PHOTO_BLUR && config.photoFolders.isEmpty()) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            T("폴더를 골라 주세요", 14.sp, 500, SK.muted, Modifier.weight(1f))
                            ActionButton("고르기", { nav.push(Routes.PHOTOS) }, primary = false)
                        }
                    }
                    if (config.boardBg == BoardBg.SOLID) {
                        ColorPalette(config.boardBgColor, { c -> update { it.withBoardBg(BoardBg.SOLID, c) } }, BG_SWATCHES, Modifier.padding(top = 8.dp))
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
 * names on), in a horizontal scroll, so what you tap is what StandBy shows. r11: 내 테마 come first with their names.
 * plan-r15 F9: each 내 테마 has a visible ⋯ (이름 바꾸기 / 지우기) instead of a long-press menu. r13 (plan-r13 A1): the
 * marked tile is [StripPick]; built-in tiles show the theme unedited; [＋ 직접 만들기] at the end ([onCustom]) is marked
 * "저장 전" while the look holds an edit saved nowhere, and every tap first asks then ([UnsavedGuard]). r14 B5: the marked
 * 내 테마 wears a veil and a pencil; tapping it opens 직접 만들기 in 꾸미기 mode for it ([onEdit]).
 */
@Composable
private fun ThemePicker(config: AppConfig, update: Update, onCustom: () -> Unit, onEdit: (String) -> Unit) {
    val state = remember(config.city, config.use24h) { Sample.state(config) }
    val board = remember(config.widgetLeft, config.widgetRight) { widgetsBoard(config) }
    val acc = LocalAccent.current
    val pick = StripPick.of(config)
    // opens with the marked tile in view (one tile of context before it)
    val at = when (pick) {
        is Look.Mine -> config.myThemes.indexOf(pick.my)
        is Look.Builtin -> config.myThemes.size + Theme.entries.indexOf(pick.theme)
        else -> config.myThemes.size + Theme.entries.size
    }
    val list = rememberLazyListState((at - 1).coerceAtLeast(0))
    var guard by remember { mutableStateOf<Look?>(null) }
    var menu by remember { mutableStateOf<MyTheme?>(null) }
    var renaming by remember { mutableStateOf<MyTheme?>(null) }
    var deleting by remember { mutableStateOf<MyTheme?>(null) }
    fun tap(to: Look) {
        menu = null
        if (pick == StripPick.Custom) guard = to else update { it.switchLook(to) }
    }
    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        T("테마", 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, bottom = 8.dp))
        LazyRow(Modifier.fillMaxWidth(), list, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(config.myThemes) { m ->
                val cfg = remember(config, m) { config.applyMyTheme(m) }
                val on = pick == Look.Mine(m)
                ThemeThumb(cfg, board, state, m.name, on, acc, { if (on) { menu = null; onEdit(m.name) } else tap(Look.Mine(m)) }, onMore = { menu = if (menu == m) null else m }, pencil = on)
            }
            items(Theme.entries) { th ->
                val cfg = remember(config, th) { config.withTheme(th).withThemeEdit(th, null) }
                ThemeThumb(cfg, board, state, th.ko, pick == Look.Builtin(th), acc, { tap(Look.Builtin(th)) })
            }
            item {
                val custom = pick == StripPick.Custom
                ThemeThumb(if (custom) config else null, board, state, "＋ 직접 만들기", custom, acc, onCustom, badge = if (custom) "저장 전" else null)
            }
        }
        menu?.let { m ->
            InlineAsk(m.name) {
                ActionButton("이름 바꾸기", { menu = null; renaming = m }, primary = false)
                ActionButton("지우기", { menu = null; deleting = m }, primary = false, danger = true)
                ActionButton("닫기", { menu = null }, primary = false)
            }
        }
    }
    guard?.let { to -> UnsavedGuard(config, update, to, { guard = null }) }
    deleting?.let { m -> DeleteDialog("'${m.name}' 테마를 지울까요?", { deleting = null }) { update { c -> c.deleteMyTheme(m) } } }
    renaming?.let { m ->
        NameDialog("이름 바꾸기", m.name, { renaming = null }, taken = config.myThemes - m, unique = true) { name ->
            renaming = null
            update { c -> c.renameMyTheme(m, name) }
        }
    }
}

/**
 * One thumbnail of the strip: the widgets page drawn with [cfg] (null = a "＋" box), [name] under it, [badge] over the
 * bottom of the picture. [onMore] = a 내 테마: a ⋯ button beside the name opens its menu. [pencil]: a dark veil and a
 * pencil over the picture.
 */
@Composable
internal fun ThemeThumb(
    cfg: AppConfig?, board: Board, state: kr.dot.sb.ui.state.StandbyState, name: String, on: Boolean, acc: androidx.compose.ui.graphics.Color,
    onTap: () -> Unit, onMore: (() -> Unit)? = null, badge: String? = null, pencil: Boolean = false,
    /** First run: the tile takes its cell's width. */
    wide: Boolean = false,
) {
    Column(
        (if (wide) Modifier.fillMaxWidth() else Modifier.width(132.dp)).clip(RoundedCornerShape(14.dp)).background(if (on) acc.copy(alpha = 0.16f) else SK.surface)
            .then(if (on) Modifier.border(2.dp, acc, RoundedCornerShape(14.dp)) else Modifier)
            .clickable(onClick = onTap).padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth()) {
            if (cfg != null) kr.dot.sb.ui.theme.ProvideTheme(cfg) {
                PreviewFrame(PreviewDevice.FOLD_COVER.w, PreviewDevice.FOLD_COVER.h, Modifier.fillMaxWidth(), radius = 8.dp) {
                    BoardView(board, state, cfg, StandbyActions.NONE, Modifier.fillMaxSize())
                }
            } else Box(
                Modifier.fillMaxWidth().aspectRatio(PreviewDevice.FOLD_COVER.w.toFloat() / PreviewDevice.FOLD_COVER.h).clip(RoundedCornerShape(8.dp))
                    .background(SK.surface2),
                Alignment.Center,
            ) { T("＋", 28.sp, 500, SK.muted) }
            if (badge != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)) { Pill(badge, acc, filled = true) }
            if (pencil) Box(Modifier.matchParentSize().clip(RoundedCornerShape(8.dp)).background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)), Alignment.Center) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(acc), Alignment.Center) { Icon(Ic.EDIT, SK.bg, 20.dp) }
            }
            // the thumbnail's own cards must not take the tap
            Box(Modifier.matchParentSize().clickable(onClick = onTap))
        }
        if (onMore == null) StripLabel(name, on, acc)
        else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { StripLabel(name, on, acc) }
            Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClickLabel = "$name 메뉴 열기", onClick = onMore), Alignment.Center) { Icon(Ic.MORE, SK.muted, 20.dp) }
        }
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

/** plan-r15 F1: "충전할 때 자동으로 켜지게 하려면 한 가지만 더 해 주세요 · 켜러 가기", only while the screen saver is missing. */
@Composable
private fun PermBanner(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).clip(RoundedCornerShape(16.dp)).background(SK.surface)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(SK.danger))
        T("충전할 때 자동으로 켜지게 하려면 한 가지만 더 해 주세요", 15.sp, 700, SK.text, Modifier.weight(1f).padding(start = 12.dp, end = 8.dp), lineHeight = 20.sp)
        ActionButton("켜러 가기", onClick, padH = 14.dp)
    }
}

