package kr.dot.sb.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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

/**
 * plan-r15 F1b: the 카드 list is gone; a card's gear in 페이지와 카드 opens its settings here. Cards whose settings live
 * on a home screen open that screen (시계, 알림과 알람, 사진); type-wide settings say "모든 ○○ 카드에 적용돼요".
 */
internal fun cardRoute(t: CardType): String = when (t) {
    CardType.CLOCK -> Routes.CLOCKS
    CardType.NOTIF, CardType.ALARM -> Routes.ALERTS
    CardType.MEMORIES -> Routes.PHOTOS
    // 오늘 일정 and 달력 share one screen (the calendar picker)
    CardType.AGENDA -> Routes.card(CardType.CALENDAR)
    else -> Routes.card(t)
}

/** Cards with something to set get a gear (앱 바로가기 has its own 앱 고르기; 사용량 in the personal build only). */
internal fun hasGear(t: CardType): Boolean = when (t) {
    CardType.APPS -> false
    CardType.USAGE -> UsageFeature.enabled
    CardType.CLOCK, CardType.WEATHER, CardType.NOTIF, CardType.ALARM, CardType.SLEEP, CardType.AGENDA, CardType.CALENDAR,
    CardType.DDAY, CardType.TIMER, CardType.MUSIC, CardType.MEMORIES -> true
    else -> false
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
