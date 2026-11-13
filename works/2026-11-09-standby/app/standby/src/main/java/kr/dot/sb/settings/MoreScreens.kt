@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package kr.dot.sb.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.dot.sb.data.UsageFeature
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.City
import kr.dot.sb.model.DDay
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.MOOD_AUTO_OFF_MIN
import kr.dot.sb.model.NightMode
import kr.dot.sb.model.SLEEP_MINUTES
import kr.dot.sb.model.SleepSound
import kr.dot.sb.model.UnfoldAction
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.screens.RootImpl
import kr.dot.sb.ui.screens.RootStart
import kr.dot.sb.ui.state.AlarmRinging
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.drawnTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The other pages after the screen's own slide (mockup `pv()`: leaf previews swipe on to the pages). */
internal fun withPages(first: Slide, config: AppConfig, skip: String? = null) = listOf(first) + pageSlides(config).filter { it.name != skip }

/** The whole StandBy surface at [state] on the clock page (notification and alarm previews draw the real overlays). */
private fun rootSlide(name: String, config: AppConfig, state: (kr.dot.sb.ui.state.StandbyState) -> kr.dot.sb.ui.state.StandbyState) = Slide(name) { l ->
    RootImpl(state(l.state), config.copy(startPage = Pages.CLOCK), StandbyActions.NONE, Modifier.fillMaxSize(), { _, _ -> }, RootStart())
}

/** A stand-in where StandBy draws nothing of its own (mockup `.stand`). */
@Composable
private fun Stand(text: String) = Box(Modifier.fillMaxSize().background(SK.surface), Alignment.Center) { T(text, 40.sp, 600, SK.muted) }

private val SAMPLE_NOTIF = BigNotification("pv", "카카오톡", "엄마", "저녁 먹었어?", null, plateColor = 0xFFF5D000, postedAtEpochMs = 0)

// ---- 알림 표시 ------------------------------------------------------------------------------------------------------

@Composable
fun NotifScreen(config: AppConfig, update: Update, back: () -> Unit) {
    val first = if (config.showNotifications) rootSlide("알림", config) { it.copy(notification = SAMPLE_NOTIF) } else rootSlide("알림", config) { it }
    Page("알림 표시", back, preview = Preview(config, withPages(first, config), caption = if (config.showNotifications) null else "알림이 와도 화면은 그대로예요")) {
        Section(null) {
            item {
                Column {
                    SwitchRow("알림 보기", config.showNotifications, { v -> update { it.copy(showNotifications = v) } }, subtitle = "받은 알림을 화면 가득 크게 보여줘요")
                    PermLine(Perm.NOTIFICATIONS)
                }
            }
            item {
                SwitchRow(
                    "누를 때만 내용 보기", config.previewOnTapOnly, { v -> update { it.copy(previewOnTapOnly = v) } },
                    subtitle = "누르기 전에는 앱 아이콘만 보여요", enabled = config.showNotifications, why = "알림 보기를 켜면 쓸 수 있어요",
                )
            }
        }
    }
}

// ---- 밤 화면 --------------------------------------------------------------------------------------------------------

@Composable
fun NightScreen(config: AppConfig, update: Update, nav: Nav, env: SettingsEnv, back: () -> Unit) {
    val never = config.displayOff == DisplayOff.NEVER
    val th = config.drawnTheme()
    val light = th.tokens.light
    // a light theme always goes dark at night: a stored 안 씀 reads as 어두워지면 (the stored value comes back on a dark theme)
    val mode = if (light && config.nightMode == NightMode.OFF) NightMode.AUTO else config.nightMode
    val neverWhy = "화면 꺼짐이 \"계속 켜 둠\"이면 쓸 수 없어요"
    Page("밤 화면", back, preview = Preview(config, pageSlides(config), night = true)) {
        if (never) NeverBanner { nav.push(Routes.ONOFF) }
        Section(null, footer = "어두워지면 화면이 붉고 어둡게 바뀌어요.") {
            fun set(m: NightMode) = update { it.copy(nightMode = m) }
            item {
                RadioRow(
                    "안 씀", if (light && !never) null else "밤에도 낮과 같은 화면", mode == NightMode.OFF, { set(NightMode.OFF) },
                    enabled = !never && !light, why = if (never) null else "${config.theme.ko} 테마는 밝아서 밤에는 꼭 어두운 화면으로 바뀌어요",
                )
            }
            item { RadioRow("어두워지면", "방이 어두워지면 붉고 어둡게", mode == NightMode.AUTO, { set(NightMode.AUTO) }, enabled = !never) }
            item { RadioRow("정한 시간에", "${config.nightStartHour}시~${config.nightEndHour}시", mode == NightMode.SCHEDULE, { set(NightMode.SCHEDULE) }, enabled = !never) }
        }
        if (mode == NightMode.SCHEDULE) Section("시간") {
            item { HourSlider("시작", config.nightStartHour, !never, neverWhy) { h -> update { it.copy(nightStartHour = h) } } }
            item { HourSlider("끝", config.nightEndHour, !never, neverWhy) { h -> update { it.copy(nightEndHour = h) } } }
        }
        if (mode != NightMode.OFF) Section(null) {
            item {
                ControlRow("밤 밝기", value = "${(config.nightMaxBrightness * 100).toInt()}%", note = "밤에 화면이 넘지 않을 가장 밝은 정도예요", enabled = !never, why = neverWhy) {
                    SliderControl(config.nightMaxBrightness, 0.05f..1f, { v -> if (!never) update { it.copy(nightMaxBrightness = (v * 20).toInt() / 20f) } })
                }
            }
        }
        if (mode == NightMode.AUTO) More {
            Section(null) {
                item { LuxRow(env.lux, config) }
                item {
                    SliderRow("어두워졌다고 볼 밝기", "${config.nightEnterLux.toInt()} lx 이하", config.nightEnterLux, 1f..20f, { v ->
                        if (!never) update { it.copy(nightEnterLux = v.toInt().toFloat(), nightExitLux = maxOf(it.nightExitLux, v.toInt() + 2f)) }
                    }, steps = 18, note = "이 밝기가 5초 이어지면 밤 화면으로 바뀌어요")
                }
                item {
                    SliderRow("밝아졌다고 볼 밝기", "${config.nightExitLux.toInt()} lx 이상", config.nightExitLux, 3f..40f, { v ->
                        if (!never) update { it.copy(nightExitLux = maxOf(v.toInt().toFloat(), it.nightEnterLux + 2f)) }
                    }, steps = 36, note = "이 밝기가 1.5초 이어지면 원래 화면으로 돌아와요")
                }
            }
        }
    }
}

@Composable
private fun HourSlider(title: String, hour: Int, enabled: Boolean, why: String, onChange: (Int) -> Unit) =
    ControlRow(title, value = "%02d:00".format(hour), enabled = enabled, why = why) {
        SliderControl(hour.toFloat(), 0f..23f, { v -> if (enabled) onChange(v.toInt()) }, steps = 22)
    }

/** "지금은 밤 화면을 쓸 수 없어요" + link to 켜짐과 꺼짐 (mockup, displayOff = 계속 켜 둠). */
@Composable
private fun NeverBanner(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(SK.surface).clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T("지금은 밤 화면을 쓸 수 없어요", 15.sp, 700, SK.why)
            T("화면 꺼짐이 \"계속 켜 둠\"이에요 · 켜짐과 꺼짐에서 바꾸기", 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp))
        }
        Icon(Ic.CHEVRON, SK.muted, 20.dp)
    }
}

@Composable
private fun LuxRow(lux: Float?, config: AppConfig) {
    val dark = lux != null && lux <= config.nightEnterLux
    val bright = lux != null && lux >= config.nightExitLux
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T("지금 주변 밝기", 16.sp, 500)
            T("센서 값을 바로바로 보여줘요", 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp))
        }
        T(if (lux == null) "—" else "${if (lux < 10) "%.1f".format(lux) else lux.toInt().toString()} lx", 22.sp, 700, SK.text, Modifier.padding(end = 10.dp))
        if (lux != null) Pill(if (dark) "밤" else if (bright) "낮" else "중간", if (dark) LocalAccent.current else SK.muted)
    }
}

// ---- 켜짐과 꺼짐 ----------------------------------------------------------------------------------------------------

@Composable
fun OnOffScreen(config: AppConfig, update: Update, env: SettingsEnv, back: () -> Unit) {
    val never = config.displayOff == DisplayOff.NEVER
    val notes = listOf(
        "쓰지 않고 방이 어두우면 화면이 꺼져요.", "20초 뒤에 화면이 꺼져요.",
        "스탠바이가 켜져 있는 동안 화면이 계속 켜져 있어요. 이때는 밤 화면과 움직이면 켜기를 쓸 수 없어요.",
    )
    Page("켜짐과 꺼짐", back) {
        Section(null) {
            item {
                Column {
                    SwitchRow("충전하면 켜기", config.startOnCharge, { v -> update { it.copy(startOnCharge = v) } }, subtitle = "화면이 꺼져 있어도 충전을 시작하면 스탠바이가 떠요")
                    PermLine(Perm.DREAM)
                }
            }
            item {
                SwitchRow(
                    "무선 충전일 때만", config.chargeWirelessOnly, { v -> update { it.copy(chargeWirelessOnly = v) } },
                    subtitle = "케이블로 충전할 때는 뜨지 않아요", enabled = config.startOnCharge, why = "충전하면 켜기를 켜면 쓸 수 있어요",
                )
            }
            item {
                ControlRow("화면 꺼짐", note = notes[config.displayOff.ordinal]) {
                    Segmented(listOf("어두워지면", "20초 뒤", "계속 켜 둠"), config.displayOff.ordinal, { i -> update { it.copy(displayOff = DisplayOff.entries[i]) } })
                }
            }
            item {
                SwitchRow(
                    "움직이면 켜기", config.motionToWake && !never, { v -> update { it.copy(motionToWake = v) } },
                    subtitle = "밤에 폰이 움직이면 화면이 켜져요", enabled = !never, why = "화면 꺼짐이 \"계속 켜 둠\"이면 쓸 수 없어요",
                )
            }
            item {
                SwitchRow(
                    "밤에 알림이 와도 꺼진 화면 켜지 않기", config.nightNotifQuiet && !never, { v -> update { it.copy(nightNotifQuiet = v) } },
                    subtitle = "밤 화면일 때만요. 알람은 그대로 울려요.", enabled = !never, why = "화면이 꺼지지 않아서 쓸 일이 없어요",
                )
            }
        }
        More {
            Section(null) {
                item { SwitchRow("발열 보호", config.heatProtect, { v -> update { it.copy(heatProtect = v) } }, subtitle = "배터리가 42도를 넘으면 화면을 절반쯤 어둡게 해요. 식으면 돌아와요.") }
                item { SwitchRow("잔상 막기", config.burnInShift, { v -> update { it.copy(burnInShift = v) } }, subtitle = "1분마다 화면을 조금씩 옮겨서 잔상을 막아요") }
            }
            val trail = remember { env.chargeTrail() }
            Section("최근 켜짐 기록", footer = "충전기를 꽂으면 여기에 기록이 남아요.") {
                item {
                    T(if (trail.isEmpty()) "아직 기록이 없어요" else trail.joinToString("\n"), 14.sp, 400, SK.muted, Modifier.padding(16.dp), lineHeight = 22.sp)
                }
            }
        }
    }
}

// ---- 수면 소리 ------------------------------------------------------------------------------------------------------

@Composable
fun SoundScreen(config: AppConfig, update: Update, env: SettingsEnv, back: () -> Unit) {
    var playing by remember { mutableStateOf<SleepSound?>(null) }
    LaunchedEffect(playing) { if (playing != null) { delay(3_000); playing = null } }
    val minutes = SLEEP_MINUTES
    Page("수면 소리", back) {
        Section(null) {
            item {
                val acc = LocalAccent.current
                ControlRow("소리") {
                    ChipFlow(SleepSound.entries.map { it.ko }, config.sleepSound.ordinal, { i ->
                        val s = SleepSound.entries[i]
                        update { it.copy(sleepSound = s) }
                        env.playSample(s)
                        playing = s
                    })
                    val p = playing
                    T(if (p != null) "♪ ${p.ko} 3초 들려주는 중이에요" else "누르면 3초 동안 들려줘요.", 13.sp, 400, if (p != null) acc else SK.muted, Modifier.padding(top = 8.dp))
                }
            }
            item { ControlRow("꺼지는 시간") { ChipFlow(minutes.map { "${it}분" }, minutes.indexOf(config.sleepMinutes), { i -> update { it.copy(sleepMinutes = minutes[i]) } }) } }
        }
    }
}

// ---- 알람 · 폴드를 펼쳤을 때 ---------------------------------------------------------------------------------------

private val GLOWS = listOf("따뜻한 주황" to 0xFFFFB36BL, "호박" to 0xFFFFA81FL, "빨강" to 0xFFFF453AL, "민트" to 0xFF85D4BAL, "흰색" to 0xFFFFFFFFL)

@Composable
fun AlarmScreen(config: AppConfig, update: Update, nav: Nav, env: SettingsEnv, back: () -> Unit) {
    val fold = env.phone == Phone.FOLD
    val first = if (config.alarmScreen) rootSlide("알람", config) { it.copy(alarm = AlarmRinging("알람")) } else Slide("알람") { Stand("시계 앱의 알람 화면") }
    val offsets = listOf(0, 5, 10, 15)
    Page("알람", back, preview = Preview(config, listOf(first))) {
        Section(null, footer = "알람 소리와 끄기·다시 알림은 언제나 시계 앱이 맡아요.") {
            item {
                Column {
                    ControlRow("알람이 울릴 때", note = if (config.alarmScreen) "스탠바이가 켜져 있으면 큰 시계와 끄기·다시 알림 버튼을 보여줘요." else "시계 앱의 기본 알람 화면만 떠요.") {
                        Segmented(listOf("스탠바이 화면", "시계 앱 화면"), if (config.alarmScreen) 0 else 1, { i -> update { it.copy(alarmScreen = i == 0) } })
                    }
                    if (config.alarmScreen) PermLine(Perm.FULL_SCREEN)
                }
            }
            item {
                Column {
                    SwitchRow(
                        "안쪽 화면 무드등", config.alarmGlow && fold, { v -> update { it.copy(alarmGlow = v) } },
                        subtitle = if (fold) "알람이 울리면 펼친 안쪽 화면이 은은한 불빛이 돼요" else null, enabled = fold, why = FOLD_WHY,
                    )
                    if (fold) PermLine(Perm.FULL_SCREEN)
                }
            }
            item {
                Column {
                    ControlRow("알람 전에 미리 밝히기", note = "시계 앱의 다음 알람보다 먼저 서서히 밝아져요.", enabled = fold, why = FOLD_WHY) {
                        Segmented(offsets.map { if (it == 0) "끔" else "${it}분" }, offsets.indexOf(config.glowStartMinBefore).coerceAtLeast(0), { i -> update { it.copy(glowStartMinBefore = offsets[i]) } }, enabled = fold)
                    }
                    if (fold && config.glowStartMinBefore > 0) PermLine(Perm.EXACT_ALARM)
                }
            }
        }
        More {
            Section(null) {
                item {
                    ControlRow("밝아지는 시간", value = "${config.glowRampSec}초", enabled = fold, why = FOLD_WHY) {
                        SliderControl(config.glowRampSec.toFloat(), 2f..20f, { v -> if (fold) update { it.copy(glowRampSec = v.toInt()) } }, steps = 17)
                    }
                }
                item { NavRow("무드등 색", { nav.push(Routes.FOLD) }, value = GLOWS.firstOrNull { it.second == config.glowColor }?.first ?: "직접 고른 색", enabled = fold, why = FOLD_WHY) }
            }
        }
    }
}

@Composable
fun FoldScreen(config: AppConfig, update: Update, env: SettingsEnv, back: () -> Unit) {
    val book = config.boards.firstOrNull { it.id == "book" }
    val glow = config.glowColor.c()
    val first = if (config.onUnfold == UnfoldAction.BOOK_BOARD && book != null) Slide("펼친 화면") { l -> BoardView(book, l.state, config, StandbyActions.NONE, Modifier.fillMaxSize()) }
    else Slide("펼친 화면") { Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(glow, lerp(glow, Color.Black, 0.62f))))) } // literal-ok: glow fades to black
    Page("폴드를 펼쳤을 때", back, preview = Preview(config, listOf(first), fixed = PreviewDevice.FOLD_INNER)) {
        Section(null) {
            item {
                Column {
                    ControlRow("펼치면", note = if (config.onUnfold == UnfoldAction.MOOD_LIGHT) "펼친 안쪽 화면이 은은한 불빛이 돼요." else "스탠바이가 켜져 있는 동안 펼치면 \"책 (펼침)\" 페이지를 보여줘요.") {
                        Segmented(listOf("책 화면", "무드등"), config.onUnfold.ordinal, { i -> update { it.copy(onUnfold = UnfoldAction.entries[i]) } })
                    }
                    PermLine(Perm.FULL_SCREEN)
                }
            }
            item { ControlRow("무드등 색", note = "펼쳤을 때와 알람이 울릴 때 안쪽 화면이 이 색으로 천천히 밝아져요.") { NamedSwatches(GLOWS, config.glowColor) { c -> update { it.copy(glowColor = c) } } } }
            if (config.onUnfold == UnfoldAction.MOOD_LIGHT) item {
                ControlRow("무드등 자동 끄기", note = "시간이 되면 천천히 어두워지다 꺼져요.") {
                    Segmented(MOOD_AUTO_OFF_MIN.map { if (it == 0) "끄지 않음" else "${it}분" }, MOOD_AUTO_OFF_MIN.indexOf(config.moodAutoOffMin).coerceAtLeast(0), { i -> update { it.copy(moodAutoOffMin = MOOD_AUTO_OFF_MIN[i]) } })
                }
            }
        }
        Gap(20.dp)
        ActionButton("무드등 테스트", env.onGlowTest, Modifier.fillMaxWidth())
        T("지금 설정으로 안쪽 화면을 잠깐 밝혀 봐요.", 13.sp, 400, SK.muted, Modifier.padding(top = 8.dp, start = 4.dp))
    }
}

// ---- 날씨와 도시 ----------------------------------------------------------------------------------------------------

@Composable
fun WeatherScreen(config: AppConfig, update: Update, back: () -> Unit) {
    var sheet by remember { mutableStateOf<Boolean?>(null) } // true = 날씨 지역, false = 세계 시계 도시
    Box(Modifier.fillMaxSize()) {
        Page("날씨와 도시", back) {
            Section(null, footer = "날씨는 Open-Meteo에서 30분마다 받아와요.") { item { NavRow("날씨 지역", { sheet = true }, value = config.city.name) } }
            More {
                Section("세계 시계 도시", footer = "세계 시계에 보이는 도시예요.") {
                    item {
                        ControlRow(null) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val many = config.worldCities.size > 1
                                config.worldCities.forEach { c ->
                                    Row(
                                        Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(SK.surface2)
                                            .clickable(enabled = many) { update { s -> s.copy(worldCities = s.worldCities.filter { it != c }) } }.padding(start = 16.dp, end = if (many) 10.dp else 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) { T(c.name, 14.sp, 500, SK.text); if (many) Icon(Ic.CLOSE, SK.muted, 18.dp, Modifier.padding(start = 6.dp)) }
                                }
                                Row(
                                    Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).border(1.dp, SK.line, RoundedCornerShape(50)).clickable { sheet = false }.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) { Icon(Ic.PLUS, LocalAccent.current, 16.dp); T("도시 더하기", 14.sp, 600, LocalAccent.current, Modifier.padding(start = 6.dp)) }
                            }
                        }
                    }
                }
            }
        }
        sheet?.let { weather ->
            CitySheet(if (weather) "날씨 지역" else "세계 시계 도시", taken = if (weather) emptySet() else config.worldCities.map { it.name }.toSet(), onDismiss = { sheet = null }) { c ->
                update { s -> if (weather) s.copy(city = c) else if (s.worldCities.any { it.name == c.name }) s else s.copy(worldCities = s.worldCities + c) }
                sheet = null
            }
        }
    }
}

/** One city search sheet for both the weather city and the world-clock cities. */
@Composable
private fun CitySheet(title: String, taken: Set<String>, onDismiss: () -> Unit, onPick: (City) -> Unit) {
    var q by remember { mutableStateOf("") }
    Sheet(title, onDismiss) {
        Field(q, { q = it }, "도시 이름으로 찾기")
        val results = Cities.search(q)
        if (results.isEmpty()) T("찾는 도시가 목록에 없어요", 13.sp, 400, SK.muted, Modifier.padding(8.dp))
        Group(Modifier.padding(top = 12.dp)) {
            results.forEach { c ->
                item {
                    val dup = c.name in taken
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(enabled = !dup) { onPick(c) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        T(c.name + if (dup) " (이미 있어요)" else "", 16.sp, 500, if (dup) SK.muted else SK.text)
                    }
                }
            }
        }
    }
}

// ---- 카드에 쓰는 정보 -----------------------------------------------------------------------------------------------

@Composable
fun CardInfoScreen(config: AppConfig, update: Update, env: SettingsEnv, back: () -> Unit) {
    val calendars = remember(env.granted[Perm.CALENDAR]) { env.calendars() }
    Page("카드에 쓰는 정보", back) {
        Section("D-day", footer = "D-day 카드에 보여요. 날짜가 지나면 D+로 세요.") {
            config.ddays.forEachIndexed { i, d ->
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T(d.name, 16.sp, 500, SK.text, maxLines = 1)
                            T(d.date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")), 13.sp, 400, SK.muted)
                        }
                        T(ddayText(d.date), 16.sp, 700, LocalAccent.current)
                        IconBtn(Ic.CLOSE, { update { s -> s.copy(ddays = s.ddays.filterIndexed { k, _ -> k != i }) } })
                    }
                }
            }
            item { DDayAdd { d -> update { it.copy(ddays = it.ddays + d) } } }
        }
        Section("집중 · 휴식", footer = "타이머 카드의 한 번 집중하는 시간과 쉬는 시간이에요.") {
            item { SliderRow("집중", "${config.focusMinutes}분", config.focusMinutes.toFloat(), 5f..60f, { v -> update { it.copy(focusMinutes = v.toInt()) } }, steps = 54) }
            item { SliderRow("휴식", "${config.breakMinutes}분", config.breakMinutes.toFloat(), 1f..30f, { v -> update { it.copy(breakMinutes = v.toInt()) } }, steps = 28) }
        }
        Section("캘린더", footer = "끈 캘린더의 일정은 오늘 일정·달력 카드에 나오지 않아요.") {
            if (calendars == null) item { Column { T("캘린더를 읽으려면 허용이 필요해요", 15.sp, 500, SK.muted, Modifier.padding(16.dp)); PermLine(Perm.CALENDAR) } }
            else if (calendars.isEmpty()) item { T("폰에 캘린더가 없어요", 15.sp, 500, SK.muted, Modifier.padding(16.dp)) }
            else calendars.groupBy { it.accountName }.forEach { (account, list) ->
                item { T(account.ifBlank { "이 폰" }, 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp)) }
                list.forEach { cal ->
                    item {
                        SwitchRow(cal.name, cal.key !in config.excludedCalendars, { on ->
                            update { it.copy(excludedCalendars = if (on) it.excludedCalendars - cal.key else it.excludedCalendars + cal.key) }
                        })
                    }
                }
            }
        }
        if (UsageFeature.enabled) Section(null) {
            item { SwitchRow("Claude·Codex 사용량", config.usageEnabled, { v -> update { it.copy(usageEnabled = v) } }, subtitle = "사용량 카드에 보여요 · 개인용 빌드에만 있어요") }
        }
    }
}

private fun ddayText(date: LocalDate): String {
    val n = ChronoUnit.DAYS.between(LocalDate.now(), date)
    return if (n >= 0) "D-$n" else "D+${-n}"
}

@Composable
private fun DDayAdd(onAdd: (DDay) -> Unit) {
    var name by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().plusDays(30)) }
    var picking by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(name, { name = it }, "새 D-day 이름")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(SK.surface2).clickable { picking = true }.padding(horizontal = 14.dp), Alignment.CenterStart) {
                T(date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")), 16.sp, 500, SK.text)
            }
            ActionButton("추가", { onAdd(DDay(name.trim(), date)); name = "" }, enabled = name.isNotBlank())
        }
    }
    if (picking) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = { androidx.compose.material3.TextButton({ st.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; picking = false }) { androidx.compose.material3.Text("확인") } },
            dismissButton = { androidx.compose.material3.TextButton({ picking = false }) { androidx.compose.material3.Text("취소") } },
        ) { DatePicker(st) }
    }
}

// ---- 권한 -----------------------------------------------------------------------------------------------------------

/** Checklist (consensus): the missing ones first with a big button each, the granted ones as one line. */
@Composable
fun PermsScreen(env: SettingsEnv, back: () -> Unit) {
    val need = Perm.entries.filter { env.granted[it] != true }
    val ok = Perm.entries.filter { env.granted[it] == true }
    Page("권한", back) {
        T(if (need.isEmpty()) "필요한 항목이 모두 켜져 있어요" else "${need.size}개를 켜면 스탠바이가 제대로 움직여요", 15.sp, 500, if (need.isEmpty()) SK.ok else SK.muted, Modifier.padding(start = 4.dp, top = 4.dp))
        need.forEach { p ->
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(SK.surface).padding(16.dp)) {
                T(p.ko, 16.sp, 600)
                T(p.why, 13.sp, 400, SK.muted, Modifier.padding(top = 4.dp, bottom = 12.dp), lineHeight = 18.sp)
                ActionButton(p.button, { env.onPerm(p) }, Modifier.fillMaxWidth())
            }
        }
        if (ok.isNotEmpty()) Section("켜져 있어요") {
            ok.forEach { p ->
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { env.onPerm(p) }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.CHECK, SK.ok, 18.dp)
                        T(p.ko, 15.sp, 500, SK.text, Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
    }
}

// ---- 앱 정보 --------------------------------------------------------------------------------------------------------

private val FONT_NAMES = mapOf(
    "inter" to "Inter", "nunito" to "Nunito", "pretendard" to "Pretendard", "sourceserif4" to "Source Serif 4", "robotoslab" to "Roboto Slab",
    "sofiasansextracondensed" to "Sofia Sans Extra Condensed", "mplusrounded1c" to "M PLUS Rounded 1c", "bagelfatone" to "Bagel Fat One",
    "barlowcondensed" to "Barlow Condensed", "bigshouldersdisplay" to "Big Shoulders Display", "fredoka" to "Fredoka", "gowunbatang" to "Gowun Batang",
    "ibmplexmono" to "IBM Plex Mono", "instrumentserif" to "Instrument Serif", "jost" to "Jost", "jua" to "Jua", "nanumpenscript" to "Nanum Pen Script",
    "oswald" to "Oswald", "outfit" to "Outfit", "playfairdisplay" to "Playfair Display", "titanone" to "Titan One",
)

@Composable
fun AboutScreen(env: SettingsEnv, back: () -> Unit) {
    Page("앱 정보", back) {
        Section(null) {
            item { Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { T("버전", 16.sp, 500, SK.text, Modifier.weight(1f)); T(env.versionName, 16.sp, 500, SK.muted) } }
        }
        Section("글꼴 라이선스", footer = "스탠바이는 아래 오픈 글꼴을 앱에 넣어 써요. 항목을 누르면 라이선스 전문이 열려요.") {
            env.licenseNames.forEach { file ->
                item {
                    var open by remember { mutableStateOf(false) }
                    val key = file.removeSuffix(".txt").substringAfter('-').lowercase()
                    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T(FONT_NAMES[key] ?: key, 16.sp, 500, SK.text, Modifier.weight(1f))
                            Icon(if (open) Ic.UP else Ic.DOWN, SK.muted, 20.dp)
                        }
                        if (open) T(env.licenseText(file).trim(), 12.sp, 400, SK.muted, Modifier.padding(top = 10.dp), lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}
