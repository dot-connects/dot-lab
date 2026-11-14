package kr.dot.sb.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
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
 * 카드 (11-14 settings tree): every card the picker offers, each row opening that card's settings. Cards with their own
 * screen keep it (시계, 날씨, 알림, 수면 소리, 알람); the rest open [CardScreen].
 */
internal fun cardRoute(t: CardType): String = when (t) {
    CardType.CLOCK -> Routes.CLOCKS
    CardType.WEATHER -> Routes.WEATHER
    CardType.NOTIF -> Routes.NOTIF
    CardType.SLEEP -> Routes.SOUND
    CardType.ALARM -> Routes.ALARM
    // 오늘 일정 and 달력 share one screen (the calendar picker)
    CardType.AGENDA -> Routes.card(CardType.CALENDAR)
    else -> Routes.card(t)
}

/** The cards the list shows, in the picker's order (사용량 in the personal build only). */
internal fun listedCards(): List<CardType> = CardType.entries.filter { it != CardType.USAGE || UsageFeature.enabled }

@Composable
fun CardsScreen(config: AppConfig, nav: Nav, env: SettingsEnv, back: () -> Unit) {
    Page("카드", back) {
        Section(null, footer = "페이지에 넣는 카드마다 쓰는 정보를 여기서 정해요.") {
            listedCards().forEach { t ->
                item {
                    val badge = if (t == CardType.NOTIF && Perm.NOTIFICATIONS in env.missing) "설정 필요" else null
                    NavRow(t.ko, { nav.push(cardRoute(t)) }, value = if (badge != null) null else cardValue(t, config), badge = badge)
                }
            }
        }
    }
}

/** The small value at the row's end, like the home rows. */
private fun cardValue(t: CardType, c: AppConfig): String? = when (t) {
    CardType.CLOCK -> "내 시계 ${c.myClocks.size}개"
    CardType.WEATHER -> c.city.name
    CardType.NOTIF -> if (c.showNotifications) "켜짐" else "꺼짐"
    CardType.SLEEP -> "${c.sleepSound.ko} · ${c.sleepMinutes}분"
    CardType.TIMER -> "집중 ${c.focusMinutes}분 · 휴식 ${c.breakMinutes}분"
    CardType.DDAY -> if (c.ddays.isEmpty()) "없음" else "${c.ddays.size}개"
    CardType.AGENDA, CardType.CALENDAR -> if (c.excludedCalendars.isEmpty()) "모든 캘린더" else "캘린더 ${c.excludedCalendars.size}개 끔"
    CardType.USAGE -> if (c.usageEnabled) "켜짐" else "꺼짐"
    CardType.ALARM -> if (c.alarmScreen) "스탠바이 화면" else "시계 앱 화면"
    else -> null
}

/** One card's settings (route "card:TYPE"): the card alone as the preview, then its sections or "설정할 게 없는 카드예요". */
@Composable
fun CardScreen(type: CardType, config: AppConfig, update: Update, env: SettingsEnv, back: () -> Unit) {
    val title = if (type == CardType.CALENDAR) "오늘 일정 · 달력" else type.ko
    val slide = Slide(type.ko) { l -> BoardView(Board("card", type.ko, Layout.ONE, listOf(listOf(type))), l.state, config, StandbyActions.NONE, Modifier.fillMaxSize()) }
    Page(title, back, preview = Preview(config, listOf(slide))) {
        when (type) {
            CardType.TIMER -> FocusSection(config, update)
            CardType.DDAY -> DDaySection(config, update, env)
            CardType.CALENDAR, CardType.AGENDA -> CalendarSection(config, update, env)
            CardType.USAGE -> UsageSection(config, update)
            else -> T("설정할 게 없는 카드예요", 15.sp, 500, SK.muted, Modifier.fillMaxWidth().padding(top = 24.dp, start = 16.dp))
        }
    }
}
