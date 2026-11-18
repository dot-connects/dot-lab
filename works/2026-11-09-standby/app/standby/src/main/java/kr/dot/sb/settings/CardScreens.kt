package kr.dot.sb.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.sb.data.UsageFeature
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.Layout
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions

/** Shared settings destinations for card types. */
internal fun cardRoute(t: CardType): String = when (t) {
    CardType.NOTIF, CardType.ALARM -> Routes.ALERTS
    CardType.MEMORIES -> Routes.PHOTOS
    // 오늘 일정 and 달력 share one screen (the calendar picker)
    CardType.AGENDA -> Routes.card(CardType.CALENDAR)
    else -> Routes.card(t)
}

/** Cards with settings are enabled in the card list. */
internal fun hasCardSettings(t: CardType): Boolean = when (t) {
    CardType.APPS -> true
    CardType.USAGE -> UsageFeature.enabled
    CardType.CLOCK, CardType.WEATHER, CardType.NOTIF, CardType.ALARM, CardType.SLEEP, CardType.AGENDA, CardType.CALENDAR,
    CardType.DDAY, CardType.TIMER, CardType.MUSIC, CardType.MEMORIES -> true
    else -> false
}

/** Validate saved per-instance app routes before opening the shared settings. */
internal fun legacyCardRoute(route: String, config: AppConfig): String? {
    val parts = route.split(':')
    if (parts.size != 4 || parts[0] != "apps") return null
    val slots = if (parts[1] == BoardOps.WIDGETS) listOf(config.widgetLeft, config.widgetRight)
        else config.boards.firstOrNull { it.id == parts[1] }?.slots ?: return null
    val slot = parts[2].toIntOrNull() ?: return null
    val index = parts[3].toIntOrNull() ?: return null
    return Routes.card(CardType.APPS).takeIf { slots.getOrNull(slot)?.getOrNull(index) == CardType.APPS }
}

@Composable
fun CardListScreen(config: AppConfig, nav: Nav, back: () -> Unit) {
    Page("카드", back) {
        Section(null) {
            CardType.entries.forEach { type ->
                item {
                    val enabled = hasCardSettings(type)
                    // operator 2026-11-18: 추억 is where the photo folders live (photos page, 사진 위 시간, 사진 흐림 use them too)
                    val photos = type == CardType.MEMORIES
                    val needPhotos = photos && config.photoFolders.isEmpty() && (kr.dot.sb.data.Needs.photos(config) || kr.dot.sb.data.Needs.memory(config))
                    NavRow(if (photos) "추억 · 사진 폴더" else type.ko, { nav.push(Routes.card(type)) }, enabled = enabled,
                        subtitle = if (!enabled) "설정할 게 없어요" else if (photos) "사진 페이지와 사진 위 시간도 이 폴더를 써요" else null,
                        value = if (photos && !needPhotos) "폴더 ${config.photoFolders.size}개" else null,
                        badge = if (needPhotos) "설정 필요" else null,
                        lead = {
                            val icon = when (type) {
                                CardType.CLOCK, CardType.ALARM -> kr.dot.sb.ui.cards.Ic.ALARM
                                CardType.MUSIC -> kr.dot.sb.ui.cards.Ic.NOTE
                                CardType.TIMER -> kr.dot.sb.ui.cards.Ic.TIMER
                                CardType.SLEEP -> kr.dot.sb.ui.cards.Ic.SOUND
                                CardType.DDAY, CardType.AGENDA, CardType.CALENDAR -> kr.dot.sb.ui.cards.Ic.CALENDAR
                                CardType.USAGE -> kr.dot.sb.ui.cards.Ic.USAGE
                                CardType.WEATHER -> kr.dot.sb.ui.cards.Ic.SUN
                                CardType.MEMORIES -> kr.dot.sb.ui.cards.Ic.PHOTO
                                CardType.NOTIF -> kr.dot.sb.ui.cards.Ic.BELL
                                CardType.APPS -> null
                            }
                            if (icon == null) Icon(Ic.PAGES, LocalAccent.current, 22.dp)
                            else kr.dot.sb.ui.cards.IconView(icon, LocalAccent.current, Modifier.size(22.dp), bg = SK.surface, stroke = true)
                        })
                }
            }
        }
    }
}

/** One card's settings (route "card:TYPE"): the card alone as the preview, then its sections. */
@Composable
fun CardScreen(type: CardType, config: AppConfig, update: Update, nav: Nav, env: SettingsEnv, back: () -> Unit) {
    val title = if (type == CardType.CALENDAR) "오늘 일정 · 달력" else type.ko
    val slide = Slide(type.ko) { l -> BoardView(Board("card", type.ko, Layout.ONE, listOf(listOf(type))), l.state, config, StandbyActions.NONE, Modifier.fillMaxSize()) }
    // D-day: the add row's date and its wheel sheet (over the page, not inside its scroll)
    var date by rememberSaveable { mutableStateOf(env.today()) }
    var picking by rememberSaveable { mutableStateOf(false) }
    var city by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
    Page(title, back, preview = Preview(config, listOf(slide))) {
        when (type) {
            CardType.CLOCK -> Section(null, footer = "모든 시계 카드에 적용돼요.") {
                item {
                    val faces = kr.dot.sb.ui.clock.ClockRegistry.boardPages(config.myClocks)
                    val selected = kr.dot.sb.ui.clock.ClockRegistry.cardFace(config.clockCardFace ?: kr.dot.sb.model.FaceId.DIGITAL, config.myClocks)
                    ControlRow("시계 모양") {
                        ChipFlow(faces.map { kr.dot.sb.ui.clock.spec(it).ko }, faces.indexOf(selected), { i -> update { it.copy(clockCardFace = faces[i]) } })
                    }
                }
                item { NavRow("시계 꾸미기", { nav.push(Routes.clock(kr.dot.sb.ui.clock.ClockRegistry.cardFace(config.clockCardFace ?: kr.dot.sb.model.FaceId.DIGITAL, config.myClocks))) }) }
            }
            CardType.TIMER -> FocusSection(config, update)
            CardType.DDAY -> DDaySection(config, update, env, date) { picking = true }
            CardType.CALENDAR, CardType.AGENDA -> CalendarSection(config, update, env)
            CardType.USAGE -> UsageSection(config, update)
            CardType.SLEEP -> SoundSection(config, update, env)
            CardType.WEATHER -> Section(null, footer = "시계의 날씨 줄과 하루 막대의 해 뜨는 시간에도 쓰여요.") {
                item { NavRow("날씨 지역", { city = true }, value = config.city.name) }
            }
            CardType.MUSIC -> Section(null, footer = "음악이나 영상을 틀면 이 카드에 나와요.") { item { PermLine(Perm.NOTIFICATIONS) } }
            else -> T("이 카드는 바로 쓰면 돼요.", 15.sp, 500, SK.muted, Modifier.fillMaxWidth().padding(top = 24.dp, start = 16.dp))
        }
    }
    if (picking) DDayDateSheet(env.today(), date, { picking = false }) { date = it; picking = false }
    if (city) CitySheet("날씨 지역", taken = emptySet(), onDismiss = { city = false }) { c -> update { it.copy(city = c) }; city = false }
    }
}
