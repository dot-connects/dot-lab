package kr.dot.sb.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.delay
import kr.dot.sb.model.AppConfig
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import java.time.Duration
import java.time.LocalDateTime

private val WEEKDAYS = arrayOf("월", "화", "수", "목", "금", "토", "일")

/** "내일 화요일 · 8시간 49분 뒤" (11-14 #8 option A): day word, weekday, time left (seconds dropped). */
internal fun alarmLine(now: LocalDateTime, alarm: LocalDateTime): String {
    val d = alarm.toLocalDate()
    val today = now.toLocalDate()
    val day = when (d) {
        today -> "오늘"
        today.plusDays(1) -> "내일"
        else -> "${d.monthValue}월 ${d.dayOfMonth}일"
    }
    val min = Duration.between(now.withSecond(0).withNano(0), alarm.withSecond(0).withNano(0)).toMinutes().coerceAtLeast(0)
    val left = when {
        min < 1 -> "곧 울려요"
        else -> listOfNotNull(
            (min / (24 * 60)).takeIf { it > 0 }?.let { "${it}일" },
            (min / 60 % 24).takeIf { it > 0 }?.let { "${it}시간" },
            (min % 60).takeIf { it > 0 && min < 24 * 60 }?.let { "${it}분" },
        ).joinToString(" ") + " 뒤"
    }
    return "$day ${WEEKDAYS[d.dayOfWeek.value - 1]}요일 · $left"
}

/** The big time: ("오전", "6:30") in 12 h, (null, "06:30") in 24 h. */
internal fun alarmTime(a: LocalDateTime, use24h: Boolean): Pair<String?, String> =
    if (use24h) null to "%02d:%02d".format(a.hour, a.minute)
    else (if (a.hour < 12) "오전" else "오후") to "${(a.hour + 11) % 12 + 1}:%02d".format(a.minute)

/**
 * Alarm card (11-14 #8, mockup section 2 A): "다음 알람" kicker, the big time, "내일 화요일 · 8시간 49분 뒤", then
 * [빠른 알람] [알람 앱 열기] filling the bottom. A tap anywhere else opens the alarm app. Small slots drop the buttons.
 * [빠른 알람] opens the presets over the card; one tap sets the alarm and a pill confirms it.
 */
@Composable
internal fun AlarmCard(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val a = state.nextAlarm
    val small = !e.atLeastS || min(e.w, e.h) < 150.dp
    var sheet by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(done) { if (done != null) { delay(3_000); done = null } }
    val ink = e.ink
    Box(Modifier.fillMaxSize().tap(actions::openAlarmApp)) {
        Column(Modifier.fillMaxSize().padding(e.pad)) {
            LabelRow(e, Ic.ALARM, "다음 알람", color = ink.accText)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomStart) {
                if (a == null) Txt("켜진 알람이 없어요", e.head, e.readInk2, weight = 600, maxW = e.cw)
                else {
                    val (ap, hm) = alarmTime(a, state.use24h)
                    val boxH = maxHeight
                    Column {
                        Row(verticalAlignment = Alignment.Bottom) {
                            val cap = min(boxH * 0.55f, 96.dp)
                            val apSize = (cap * 0.42f).coerceAtLeast(e.meta)
                            if (ap != null) {
                                Txt(ap, apSize, ink.ink2, weight = 600)
                                Spacer(Modifier.width(cap * 0.12f))
                            }
                            // the digits get what 오전/오후 leaves (two Hangul ≈ 2.1 em), so a narrow slot shrinks them, never clips
                            val room = e.cw - if (ap != null) apSize * 2.1f + cap * 0.12f else 0.dp
                            Hero(e, AnnotatedString(hm), cap, ink.ink, room)
                        }
                        Spacer(Modifier.height(e.gap * 1.5f))
                        Txt(alarmLine(state.now, a), e.read, e.readInk2, weight = 500, maxW = e.cw, num = true)
                    }
                }
            }
            if (!small) {
                Spacer(Modifier.height(e.gap * 2))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(e, "빠른 알람", ink.acc, Modifier.weight(1f)) { sheet = true }
                    Pill(e, "알람 앱 열기", ink.ink.copy(alpha = 0.12f), Modifier.weight(1f), actions::openAlarmApp)
                }
            }
        }
        if (sheet) QuickSheet(e, config, state) { q ->
            sheet = false
            if (q != null) {
                val t = q.target(state.now)
                actions.setQuickAlarm(t)
                val (ap, hm) = alarmTime(t, state.use24h)
                done = listOfNotNull(ap, hm).joinToString(" ") + " 알람을 맞췄어요"
            }
        }
        done?.let { msg ->
            Box(Modifier.align(Alignment.TopCenter).padding(top = e.pad).clip(RoundedCornerShape(50)).background(ink.acc).padding(horizontal = 14.dp, vertical = 6.dp)) {
                Txt(msg, e.meta, ink.on(ink.acc), weight = 600, maxW = e.cw - 28.dp)
            }
        }
    }
}

/** A 48 dp button: [bg] plate, its text in whichever ink reads on it. */
@Composable
private fun Pill(e: Env, text: String, bg: Color, modifier: Modifier, onClick: () -> Unit) {
    val solid = bg.alpha == 1f
    Box(modifier.height(48.dp).clip(RoundedCornerShape(50)).background(bg).tap(onClick), Alignment.Center) {
        Txt(text, e.meta, if (solid) e.ink.on(bg) else e.ink.ink, weight = 600, align = TextAlign.Center, maxW = 200.dp)
    }
}

/**
 * The 빠른 알람 sheet over the card: exactly +5분 · +30분 · +1시간 · 직접 정하기 (operator 11-14). 직접 정하기 turns the
 * sheet into a 24 h setter (hour and minute wheels, 1-minute steps) starting on the next half hour. null = closed.
 */
@Composable
private fun QuickSheet(e: Env, config: AppConfig, state: StandbyState, onPick: (kr.dot.sb.model.QuickAlarm?) -> Unit) {
    val ink = e.ink
    val soft = ink.ink.copy(alpha = 0.12f)
    var setting by remember { mutableStateOf<java.time.LocalTime?>(null) }
    // a tap on the empty part of the sheet closes it; its buttons take their own taps
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(e.plateRadius)).background(ink.bg).tap { onPick(null) }) {
        Column(Modifier.fillMaxSize().padding(e.pad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val t = setting
            LabelRow(e, Ic.ALARM, if (t == null) "빠른 알람" else "시간 정하기", color = ink.accText)
            Spacer(Modifier.weight(1f))
            if (t == null) {
                val p = kr.dot.sb.model.QuickAlarm.PRESETS
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(e, p[0].label, soft, Modifier.weight(1f)) { onPick(p[0]) }
                    Pill(e, p[1].label, soft, Modifier.weight(1f)) { onPick(p[1]) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(e, p[2].label, soft, Modifier.weight(1f)) { onPick(p[2]) }
                    Pill(e, "직접 정하기", soft, Modifier.weight(1f)) { setting = kr.dot.sb.model.QuickAlarm.nextHalfHour(state.now) }
                }
            } else {
                // 24 h wheels (operator 11-14) side by side with [맞추기], so even a short slot has room
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val item = min(44.dp, (maxWidth / 6))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Wheel(e, 24, t.hour, item, Modifier.weight(1f)) { h -> setting = setting?.withHour(h) }
                        Txt(":", e.head, ink.ink, weight = 700)
                        Wheel(e, 60, t.minute, item, Modifier.weight(1f)) { m -> setting = setting?.withMinute(m) }
                        Pill(e, "맞추기", ink.acc, Modifier.weight(1.2f)) { setting?.let { onPick(kr.dot.sb.model.QuickAlarm(at = it)) } }
                    }
                }
            }
        }
    }
}

/** A wheel item index as its value: the list repeats 0..[size]-1, so both directions wrap. */
internal fun wheelValue(index: Int, size: Int): Int = index.mod(size)

/** The start index for [value]: the middle of the long list, so a fling either way never hits an end. */
internal fun wheelStart(value: Int, size: Int): Int = size * (WHEEL_LAPS / 2) + value

private const val WHEEL_LAPS = 200

/** Number wheel 00..[size]-1 (vertical drag/fling, snapping, wrapping): three rows tall, the middle one picked. */
@Composable
private fun Wheel(e: Env, size: Int, value: Int, item: androidx.compose.ui.unit.Dp, modifier: Modifier, onPick: (Int) -> Unit) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState(wheelStart(value, size))
    val fling = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(state)
    val pick by androidx.compose.runtime.rememberUpdatedState(onPick)
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }.collect { (moving, i) -> if (!moving) pick(wheelValue(i, size)) }
    }
    val ink = e.ink
    Box(modifier.height(item * 3), Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(item).clip(RoundedCornerShape(12.dp)).background(ink.ink.copy(alpha = 0.10f)))
        androidx.compose.foundation.lazy.LazyColumn(
            Modifier.fillMaxSize(), state = state, flingBehavior = fling,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = item),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(size * WHEEL_LAPS) { i ->
                val on = i == state.firstVisibleItemIndex
                Box(Modifier.height(item).fillMaxWidth(), Alignment.Center) {
                    Txt("%02d".format(wheelValue(i, size)), item * 0.62f, if (on) ink.ink else ink.ink3, weight = if (on) 700 else 500, tnum = true, num = true)
                }
            }
        }
    }
}
