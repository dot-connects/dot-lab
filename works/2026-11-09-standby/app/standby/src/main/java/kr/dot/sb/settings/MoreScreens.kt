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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.dot.sb.data.UsageFeature
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.City
import kr.dot.sb.model.DDay
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.MOOD_AUTO_OFF_MIN
import kr.dot.sb.model.MOOD_COLORS
import kr.dot.sb.model.Mood
import kr.dot.sb.model.MoodEffect
import kr.dot.sb.model.NightMode
import kr.dot.sb.model.SLEEP_MINUTES
import kr.dot.sb.model.SleepSound
import kr.dot.sb.model.TimerAlert
import kr.dot.sb.model.UnfoldAction
import kr.dot.sb.ui.screens.RootImpl
import kr.dot.sb.ui.screens.RootStart
import kr.dot.sb.ui.state.AlarmRinging
import kr.dot.sb.ui.state.BigNotification
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.baseTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


/** The whole StandBy surface at [state] on page [page] (notification and alarm previews draw the real overlays). */
private fun rootSlide(name: String, config: AppConfig, page: Int = Pages.CLOCK, state: (kr.dot.sb.ui.state.StandbyState) -> kr.dot.sb.ui.state.StandbyState) = Slide(name) { l ->
    RootImpl(state(l.state), config.copy(startPage = page), StandbyActions.NONE, Modifier.fillMaxSize(), { _, _ -> }, RootStart())
}

/** A stand-in where StandBy draws nothing of its own (mockup `.stand`). */
@Composable
private fun Stand(text: String) = Box(Modifier.fillMaxSize().background(SK.surface), Alignment.Center) { T(text, 40.sp, 600, SK.muted) }

private val SAMPLE_NOTIF = BigNotification("pv", "카카오톡", "엄마", "저녁 먹었어?", null, plateColor = 0xFFF5D000, postedAtEpochMs = 0)

// ---- 알림과 알람 (plan-r15 F1/F1b: the old 알림 and 알람 screens, plus the four Fold alarm rows) ---------------------

private val GLOWS = listOf("주황" to 0xFFFFB36BL, "호박빛" to 0xFFFFA81FL, "빨강" to 0xFFFF453AL, "민트" to 0xFF85D4BAL, "흰색" to 0xFFFFFFFFL)

@Composable
fun AlertsScreen(config: AppConfig, update: Update, env: SettingsEnv, back: () -> Unit, alarm: Boolean) {
    val fold = env.phone == Phone.FOLD
    val startPage = Pages.visible(config).getOrNull(Pages.start(config))?.index ?: Pages.CLOCK
    val notif = rootSlide("알림", config, startPage) { st ->
        // posted "now" in the sample's clock (epoch 0 read "20766일 전")
        val at = st.now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (config.showNotifications) st.copy(notification = SAMPLE_NOTIF.copy(postedAtEpochMs = at)) else st
    }
    val alarmSlide = if (config.alarmScreen) rootSlide("알람", config) { it.copy(alarm = AlarmRinging("알람")) } else Slide("알람") { Stand("시계 앱의 알람 화면") }
    val offsets = listOf(0, 5, 10, 15)
    // operator 2026-11-19: 알림 and 알람 are separate screens (카드 › 알림, 카드 › 알람)
    Page(if (alarm) "알람" else "알림", back,
        preview = if (alarm) Preview(config, listOf(alarmSlide)) else Preview(config, listOf(notif), caption = if (config.showNotifications) null else "알림이 와도 화면은 그대로예요.")) {
        if (!alarm) Section(null) {
            item {
                Column {
                    SwitchRow("알림 보기", config.showNotifications, { v -> update { it.copy(showNotifications = v) } }, subtitle = "새 알림이 오면 화면 위쪽에 잠깐 보여줘요.")
                    if (config.showNotifications) PermLine(Perm.NOTIFICATIONS)
                }
            }
            item {
                SwitchRow(
                    "누를 때만 내용 보기", config.previewOnTapOnly, { v -> update { it.copy(previewOnTapOnly = v) } },
                    subtitle = "누르기 전에는 앱 아이콘만 보여요.", enabled = config.showNotifications, why = "알림 보기를 켜면 쓸 수 있어요.",
                )
            }
        }
        if (alarm) Section(null, footer = "알람 시간과 소리는 시계 앱에서 정해요.") {
            item {
                Column {
                    ControlRow("알람이 울릴 때", note = if (config.alarmScreen) "Stand Screen이 켜져 있으면 큰 시계와 끄기·다시 알림 버튼을 보여줘요." else "시계 앱의 원래 알람 화면이 나와요.") {
                        Segmented(listOf("큰 알람 화면", "원래 알람 화면"), if (config.alarmScreen) 0 else 1, { i -> update { it.copy(alarmScreen = i == 0) } })
                    }
                    // plan-r15 F3: the alarm is seen through the listener (once, when 알림 보기 does not already show it)
                    if (config.alarmScreen && !config.showNotifications) PermLine(Perm.NOTIFICATIONS)
                    // one line for both users of it (the alarm screen and the inner-screen glow)
                    if (config.alarmScreen || (fold && config.alarmGlow)) PermLine(Perm.FULL_SCREEN)
                }
            }
            // plan-r13 B3: the inner-screen rows exist only on a Fold
            if (fold) item {
                SwitchRow("안쪽 화면 무드등", config.alarmGlow, { v -> update { it.copy(alarmGlow = v) } }, subtitle = "알람이 울리면 펼친 안쪽 화면이 은은하게 밝아져요.")
            }
            if (fold) item {
                Column {
                    ControlRow("미리 밝히기", note = "알람이 울리기 전부터 화면이 서서히 밝아져요.") {
                        Segmented(offsets.map { if (it == 0) "끔" else "${it}분" }, offsets.indexOf(config.glowStartMinBefore).coerceAtLeast(0), { i -> update { it.copy(glowStartMinBefore = offsets[i]) } })
                    }
                    if (config.glowStartMinBefore > 0) PermLine(Perm.EXACT_ALARM)
                }
            }
        }
        if (alarm && fold) More {
            Section(null) {
                item {
                    ControlRow("밝아지는 시간", value = "${config.glowRampSec}초") {
                        SliderControl(config.glowRampSec.toFloat(), 2f..20f, { v -> update { it.copy(glowRampSec = v.toInt()) } }, steps = 17)
                    }
                }
                // plan-r15 E8: the unfold mood light has its own colour (자동으로 켜기·끄기); this one is the alarm glow's
                item { ControlRow("무드등 색", note = "알람이 울릴 때 안쪽 화면이 이 색으로 켜져요.") { NamedSwatches(GLOWS, config.glowColor) { c -> update { it.copy(glowColor = c) } } } }
            }
        }
    }
}

// ---- 밤 화면 --------------------------------------------------------------------------------------------------------

/** plan-r15 F6 밤 감지: one slider; the exit threshold follows at enter + 2 lx (the old second slider is gone). */
internal object NightDetect {
    fun set(c: AppConfig, level: Float): AppConfig = DarkLevel.lux(level).let { lux -> c.copy(nightEnterLux = lux, nightExitLux = lux + 2f) }
}

@Composable
fun NightScreen(config: AppConfig, update: Update, nav: Nav, env: SettingsEnv, back: () -> Unit) {
    val never = config.displayOff == DisplayOff.NEVER
    val th = config.baseTheme()
    val light = th.tokens.light
    // a light theme always goes dark at night: a stored 끔 reads as 방이 어두워지면 (the stored value comes back on a dark theme)
    val mode = if (light && config.nightMode == NightMode.OFF) NightMode.AUTO else config.nightMode
    val neverWhy = "화면을 끄지 않기로 해 두면 쓸 수 없어요."
    Page("밤 화면", back, preview = Preview(config, pageSlides(config), night = true, initial = Pages.start(config))) {
        if (never) NeverBanner { nav.push(Routes.ONOFF) }
        Section(null, footer = "밤에는 눈이 편하게 화면이 붉고 어두워져요.") {
            fun set(m: NightMode) = update { it.copy(nightMode = m) }
            item {
                RadioRow(
                    "끔", if (light && !never) null else "밤에도 낮과 같은 화면이에요.", mode == NightMode.OFF, { set(NightMode.OFF) },
                    enabled = !never && !light, why = if (never) null else "${config.theme.ko} 테마는 밝아서 밤에는 꼭 어두운 화면으로 바뀌어요.",
                )
            }
            item { RadioRow("방이 어두워지면", "불을 끄면 알아서 밤 화면으로 바뀌어요.", mode == NightMode.AUTO, { set(NightMode.AUTO) }, enabled = !never) }
            item { RadioRow("정한 시간에", hourWords(config.nightStartHour) + "~" + hourWords(config.nightEndHour), mode == NightMode.SCHEDULE, { set(NightMode.SCHEDULE) }, enabled = !never) }
        }
        if (mode == NightMode.SCHEDULE) Section("시간") {
            item { HourSlider("시작", config.nightStartHour, !never, neverWhy) { h -> update { it.copy(nightStartHour = h) } } }
            item { HourSlider("끝", config.nightEndHour, !never, neverWhy) { h -> update { it.copy(nightEndHour = h) } } }
        }
        if (mode != NightMode.OFF) Section(null) {
            item {
                WordSlider("밤 밝기", "어둡게", "밝게", config.nightMaxBrightness, { v -> if (!never) update { it.copy(nightMaxBrightness = (v * 20).toInt() / 20f) } },
                    range = 0.05f..1f, note = "밤에는 이보다 밝아지지 않아요.", enabled = !never, why = neverWhy)
            }
            if (mode == NightMode.AUTO) {
                item { LuxRow(env.lux, config) }
                // plan-r13 B4 scale (right = darker); the stored lux keep their meaning
                item {
                    WordSlider("얼마나 어두우면 밤으로 볼까요", "조금만 어두워도", "캄캄할 때만", DarkLevel.of(config.nightEnterLux), { v -> if (!never) update { NightDetect.set(it, v) } },
                        range = DarkLevel.of(20f)..20f, enabled = !never, why = neverWhy)
                }
            }
        }
    }
}

@Composable
private fun HourSlider(title: String, hour: Int, enabled: Boolean, why: String, onChange: (Int) -> Unit) =
    ControlRow(title, value = hourWords(hour), enabled = enabled, why = why) {
        SliderControl(hour.toFloat(), 0f..23f, { v -> if (enabled) onChange(v.toInt()) }, steps = 22)
    }

/** "지금은 밤 화면을 쓸 수 없어요" + link to 자동으로 켜기·끄기 (mockup, 화면 끄기 = 끄지 않기). */
@Composable
private fun NeverBanner(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(16.dp)).background(SK.surface).clickable(onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T("지금은 밤 화면을 쓸 수 없어요", 15.sp, 700, SK.why)
            T("화면을 끄지 않기로 해 두었어요 · 자동으로 켜기·끄기에서 바꾸기", 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp))
        }
        Icon(Ic.CHEVRON, SK.muted, 20.dp)
    }
}

/** 지금 방 밝기: only 밤 / 낮 / 중간 against the current threshold (plan-r15 F6: no number). */
@Composable
private fun LuxRow(lux: Float?, config: AppConfig) {
    val dark = lux != null && lux <= config.nightEnterLux
    val bright = lux != null && lux >= config.nightExitLux
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        T("지금 방 밝기", 16.sp, 500, SK.text, Modifier.weight(1f))
        if (lux == null) T("—", 16.sp, 500, SK.muted)
        else Pill(if (dark) "밤" else if (bright) "낮" else "중간", if (dark) LocalAccent.current else SK.muted)
    }
}

// ---- 자동으로 켜기·끄기 (plan-r15 F1b: with 펼쳤을 때 on a Fold) ----------------------------------------------------

@Composable
fun OnOffScreen(config: AppConfig, update: Update, nav: Nav, env: SettingsEnv, back: () -> Unit) {
    val never = config.displayOff == DisplayOff.NEVER
    // plan-r16: off = the panel really goes off (at the phone's own screen timeout), so say how it comes back
    val wake = " 꺼진 화면은 폰에서 두 번 탭하거나 측면 버튼을 눌러 켜요. 그다음엔 잠금 화면이 나와요."
    val notes = listOf(
        "방이 어두우면 20초 뒤, 밝아도 10분 동안 만지지 않으면 화면을 꺼요.$wake", "20초 동안 만지지 않으면 화면을 꺼요.$wake",
        "스탠드 스크린이 켜져 있는 동안 화면을 끄지 않아요. 이때는 밤 화면을 쓸 수 없어요.",
    )
    Page("자동으로 켜기·끄기", back) {
        Section(null) {
            item {
                Column {
                    SwitchRow("충전하면 자동으로 켜기", config.startOnCharge, { v -> update { it.copy(startOnCharge = v) } }, subtitle = "화면이 꺼져 있어도 충전을 시작하면 Stand Screen이 켜져요.")
                    // plan-r15 B4/F3: the four grants charge-start needs, asked in turn from one line
                    if (config.startOnCharge) PermLine(Perm.CHARGE_START)
                }
            }
            item {
                SwitchRow(
                    "무선 충전일 때만", config.chargeWirelessOnly, { v -> update { it.copy(chargeWirelessOnly = v) } },
                    subtitle = "케이블로 충전할 때는 켜지지 않아요.", enabled = config.startOnCharge, why = "충전하면 자동으로 켜기를 켜면 쓸 수 있어요.",
                )
            }
            item {
                // plan-r15 C2: a separate check, only under 무선 충전일 때만
                SwitchRow(
                    "가로로 놓였을 때만 켜기", config.startLandscapeOnly, { v -> update { it.copy(startLandscapeOnly = v) } },
                    subtitle = "폰을 세로로 두면 켜지지 않아요.", enabled = config.startOnCharge && config.chargeWirelessOnly,
                    why = "무선 충전일 때만을 켜면 쓸 수 있어요.",
                )
            }
            item {
                ControlRow("화면 끄기", note = notes[config.displayOff.ordinal]) {
                    Segmented(listOf("방이 어두우면", "20초 뒤", "끄지 않기"), config.displayOff.ordinal, { i -> update { it.copy(displayOff = DisplayOff.entries[i]) } })
                }
            }
            // plan-r16 T5: 움직이면 켜기 is gone (a sensor offset kept the screen on all night); motionToWake stays in the codec
            item {
                SwitchRow(
                    "밤에도 알림 오면 화면 켜기", NightWake.shown(config), { v -> update { NightWake.set(it, v) } },
                    subtitle = "꺼 두면 밤에는 알림이 와도 조용해요.", enabled = !never, why = "화면을 끄지 않을 때는 필요 없어요.",
                )
            }
            // plan-r15 F3: battery optimisation lives here (a stopped app misses the plug-in)
            if (env.granted[Perm.BATTERY] != true) item { PermLine(Perm.BATTERY) }
        }
        TipLine(Tip.SIDE_KEY.line)
        // operator 2026-11-18: the fold rows live one level down so this screen stays short
        if (env.phone == Phone.FOLD) Section(null) {
            item { NavRow("펼쳤을 때", { nav.push(Routes.FOLD) }, value = if (config.onUnfold == UnfoldAction.MOOD_LIGHT) "무드등" else "펼친 화면") }
        }
        More {
            Section(null) {
                item { SwitchRow("뜨거워지면 화면 어둡게", config.heatProtect, { v -> update { it.copy(heatProtect = v) } }, subtitle = "폰이 식으면 다시 밝아져요.") }
                item { SwitchRow("화면 자국 막기", config.burnInShift, { v -> update { it.copy(burnInShift = v) } }, subtitle = "같은 그림이 오래 남지 않게 조금씩 움직여요.") }
            }
        }
    }
}

/** 펼쳤을 때, one level under 자동으로 켜기·끄기 (operator 2026-11-18). */
@Composable
fun FoldScreen(config: AppConfig, update: Update, env: SettingsEnv, back: () -> Unit) {
    Page("펼쳤을 때", back) { UnfoldSection(config, update, env) }
}

/** 펼쳤을 때 (the old 폴드를 펼쳤을 때 screen, plan-r15 F1b): 펼친 화면 / 무드등 and the mood light's options. */
@Composable
private fun UnfoldSection(config: AppConfig, update: Update, env: SettingsEnv) {
    val moodOn = config.onUnfold == UnfoldAction.MOOD_LIGHT
    Section(null) {
        item {
            Column {
                ControlRow("펼치면", note = if (moodOn) "90° 넘게 펼치면 안쪽 화면이 무드등으로 켜져요. 켜진 뒤에는 다시 좁혀서 텐트처럼 세워도 계속 켜져 있어요." else "Stand Screen이 켜져 있을 때 펼치면 \"펼친 화면\" 페이지를 보여줘요.") {
                    Segmented(listOf("펼친 화면", "무드등"), config.onUnfold.ordinal, { i -> update { it.copy(onUnfold = UnfoldAction.entries[i]) } })
                }
                PermLine(Perm.FULL_SCREEN)
            }
        }
        if (moodOn) {
            val m = config.mood
            fun mood(f: (Mood) -> Mood) = update { it.copy(mood = f(it.mood)) }
            item { WordSlider("밝기", "어둡게", "밝게", m.bright, { v -> mood { it.copy(bright = v) } }) }
            item { ControlRow("색") { NamedSwatches(MOOD_COLORS, m.color) { c -> mood { it.copy(color = c) } } } }
            item {
                Column {
                    ControlRow("효과") { Segmented(MoodEffect.entries.map { it.ko }, m.effect.ordinal, { i -> mood { it.copy(effect = MoodEffect.entries[i]) } }) }
                    if (m.effect != MoodEffect.STILL) WordSlider("빠르기", "천천히", "빠르게", m.speed, { v -> mood { it.copy(speed = v) } })
                }
            }
            // E7: dual screen exists only on a Fold
            item {
                SwitchRow("무드등 켤 때 바깥 화면에 시계", config.tentClock, { v -> update { it.copy(tentClock = v) } }, subtitle = "텐트처럼 세우면 바깥 화면에 시계가 보여요.")
            }
            item {
                ControlRow("무드등 자동 끄기", note = "시간이 되면 천천히 어두워지다 꺼져요.") {
                    Segmented(MOOD_AUTO_OFF_MIN.map { if (it == 0) "끄지 않기" else "${it}분" }, MOOD_AUTO_OFF_MIN.indexOf(config.moodAutoOffMin).coerceAtLeast(0), { i -> update { it.copy(moodAutoOffMin = MOOD_AUTO_OFF_MIN[i]) } })
                }
            }
        }
    }
    if (moodOn) TipLine(Tip.TENT.line)
    Gap(8.dp)
    ActionButton("무드등 켜 보기", env.onGlowTest, Modifier.fillMaxWidth(), primary = false)
    T("지금 설정으로 안쪽 화면을 잠깐 밝혀 봐요.", 13.sp, 400, SK.muted, Modifier.padding(top = 8.dp, start = 4.dp))
}

// ---- 수면 소리 (card gear) ------------------------------------------------------------------------------------------

@Composable
internal fun SoundSection(config: AppConfig, update: Update, env: SettingsEnv) {
    var playing by remember { mutableStateOf<SleepSound?>(null) }
    LaunchedEffect(playing) { if (playing != null) { delay(3_000); playing = null } }
    val minutes = SLEEP_MINUTES
    Section(null, footer = "모든 수면 소리 카드에 적용돼요.") {
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
                T(if (p != null) "♪ ${p.ko.obj()} 잠깐 들려주는 중이에요." else "누르면 잠깐 들려줘요.", 13.sp, 400, if (p != null) acc else SK.muted, Modifier.padding(top = 8.dp))
            }
        }
        item { ControlRow("꺼지는 시간") { ChipFlow(minutes.map { "${it}분" }, minutes.indexOf(config.sleepMinutes), { i -> update { it.copy(sleepMinutes = minutes[i]) } }) } }
    }
}

// ---- 날씨와 도시 ----------------------------------------------------------------------------------------------------

/** 세계 시계 도시 (11-14: on 시계): chips with ✕ and [도시 추가], which asks [onAdd] for the city sheet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorldCitiesSection(config: AppConfig, update: Update, onAdd: () -> Unit) =
    Section("세계 시계 도시", footer = "세계 시계에 보이는 도시예요.") {
        item {
            ControlRow(null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val many = config.worldCities.size > 1
                    config.worldCities.forEach { c ->
                        Row(
                            Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).background(SK.surface2).padding(start = 16.dp, end = if (many) 0.dp else 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            T(c.name, 14.sp, 500, SK.text)
                            if (many) Box(
                                Modifier.size(48.dp).clip(CircleShape).clickable(onClickLabel = "${c.name} 빼기") { update { s -> s.copy(worldCities = s.worldCities.filter { it != c }) } },
                                Alignment.Center,
                            ) { Icon(Ic.CLOSE, SK.muted, 18.dp) }
                        }
                    }
                    Row(
                        Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(50)).border(1.dp, SK.line, RoundedCornerShape(50)).clickable(onClick = onAdd).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Icon(Ic.PLUS, LocalAccent.current, 16.dp); T("도시 추가", 14.sp, 600, LocalAccent.current, Modifier.padding(start = 6.dp)) }
                }
            }
        }
    }

/** One city search sheet for both the weather city and the world-clock cities. */
@Composable
internal fun CitySheet(title: String, taken: Set<String>, onDismiss: () -> Unit, onPick: (City) -> Unit) {
    var q by remember { mutableStateOf("") }
    Sheet(title, onDismiss) {
        Field(q, { q = it }, "도시 이름으로 찾기")
        val results = Cities.search(q)
        if (results.isEmpty()) T("목록에 없는 도시예요. 가까운 큰 도시를 찾아보세요.", 13.sp, 400, SK.muted, Modifier.padding(8.dp))
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

// ---- card settings sections (a card's gear in 페이지와 카드) ------------------------------------------------------

/** D-day 카드: the list + add row. [date] = the add row's date; [onDate] opens the date sheet. Delete asks once (F5). */
@Composable
internal fun DDaySection(config: AppConfig, update: Update, env: SettingsEnv, date: LocalDate, onDate: () -> Unit) {
    var deleting by remember { mutableStateOf<DDay?>(null) }
    Section("D-day", footer = "날짜가 지나면 D+로 세요. 모든 D-day 카드에 적용돼요.") {
        config.ddays.forEach { d ->
            item {
                Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        T(d.name, 16.sp, 500, SK.text, maxLines = 1)
                        T(d.date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")), 13.sp, 400, SK.muted)
                    }
                    T(ddayText(env.today(), d.date), 16.sp, 700, LocalAccent.current)
                    IconBtn(Ic.CLOSE, { deleting = d }, modifier = Modifier.semantics { contentDescription = "${d.name} 지우기" })
                }
            }
        }
        item { DDayAdd(date, onDate) { d -> update { it.copy(ddays = it.ddays + d) } } }
    }
    deleting?.let { d -> DeleteDialog("'${d.name}' D-day를 지울까요?", { deleting = null }) { update { s -> s.copy(ddays = s.ddays - d) } } }
}

/** 집중 타이머 카드: 집중 · 휴식 minutes, and the end alert (r15 §5; a tap also plays it once). */
@Composable
internal fun FocusSection(config: AppConfig, update: Update) {
    Section("집중 · 휴식", footer = "한 번에 집중하는 시간과 쉬는 시간이에요. 모든 집중 타이머 카드에 적용돼요.") {
        item { SliderRow("집중", "${config.focusMinutes}분", config.focusMinutes.toFloat(), 5f..60f, { v -> update { it.copy(focusMinutes = v.toInt()) } }, steps = 54) }
        item { SliderRow("휴식", "${config.breakMinutes}분", config.breakMinutes.toFloat(), 1f..30f, { v -> update { it.copy(breakMinutes = v.toInt()) } }, steps = 28) }
    }
    TipLine(Tip.TIMER_HOLD.line)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val player = remember { kr.dot.sb.platform.DeviceTimerAlerts(ctx) }
    Section("끝나면 알리기", footer = "타이머가 끝나면 소리로 알려요. 무음·진동이나 방해 금지일 때는 진동만 울려요. 누르면 들어 볼 수 있어요.") {
        for (a in TimerAlert.entries) item {
            val sub = when (a) {
                TimerAlert.TWO_TONE -> "딩-동 · 진동 짧게 두 번"
                TimerAlert.BELL -> "길게 울리는 종소리 · 진동 길게 한 번"
                TimerAlert.RISING -> "도-미-솔 · 휴식이 끝나면 솔-미-도"
                TimerAlert.VIBRATE -> "소리 없이 진동만"
                TimerAlert.OFF -> null
            }
            RadioRow(a.ko, sub, config.timerAlert == a, { update { it.copy(timerAlert = a) }; player.stop(); player.play(a, falling = false, times = 1) })
        }
    }
}

/** The group a calendar is listed under (plan-r15 F12): this phone, each account, or subscribed (holidays, birthdays). */
internal fun calendarGroup(c: kr.dot.sb.data.CalendarInfo): String {
    val n = c.name.lowercase()
    return when {
        listOf("공휴일", "휴일", "holiday", "생일", "birthday", "contacts").any { it in n } -> "구독한 캘린더"
        c.accountName.isBlank() || c.accountType.equals("LOCAL", ignoreCase = true) -> "이 폰"
        else -> c.accountName
    }
}

private val DAY = DateTimeFormatter.ofPattern("M/d (E)", java.util.Locale.KOREAN)
private val HM = DateTimeFormatter.ofPattern("HH:mm")

/**
 * 오늘 일정 · 달력 카드 (plan-r15 F12, option B): one line "켠 캘린더의 이번 주 일정 N개", calendars grouped by account
 * with a colour dot, name, "이번 주 N개" and a switch; a row opens that calendar's next 7 days; 모두 끄기/켜기 per group.
 */
@Composable
internal fun CalendarSection(config: AppConfig, update: Update, env: SettingsEnv) {
    val granted = env.granted[Perm.CALENDAR]
    val calendars = remember(granted) { env.calendars() }
    val week by androidx.compose.runtime.produceState<List<kr.dot.sb.data.WeekEvent>?>(null, granted) {
        value = kotlinx.coroutines.withContext(env.io) { env.weekEvents() }
    }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    if (calendars == null) {
        Section("캘린더") { item { Column { T("캘린더를 읽으려면 허용이 필요해요.", 15.sp, 500, SK.muted, Modifier.padding(16.dp)); PermLine(Perm.CALENDAR) } } }
        return
    }
    val byCal = week.orEmpty().groupBy { it.calendarId }
    val onCount = calendars.filter { it.key !in config.excludedCalendars }.sumOf { byCal[it.id]?.size ?: 0 }
    T("켠 캘린더의 이번 주 일정 ${onCount}개", 15.sp, 600, SK.text, Modifier.padding(start = 4.dp, top = 24.dp))
    if (calendars.isEmpty()) Section(null) { item { T("폰에 캘린더가 없어요.", 15.sp, 500, SK.muted, Modifier.padding(16.dp)) } }
    calendars.groupBy(::calendarGroup).forEach { (group, list) ->
        val allOn = list.all { it.key !in config.excludedCalendars }
        Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                T(group, 13.sp, 600, SK.muted, Modifier.weight(1f), maxLines = 1)
                Box(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(12.dp)).clickable {
                    val keys = list.map { it.key }.toSet()
                    update { it.copy(excludedCalendars = if (allOn) it.excludedCalendars + keys else it.excludedCalendars - keys) }
                }.padding(horizontal = 12.dp), Alignment.Center) { T(if (allOn) "모두 끄기" else "모두 켜기", 13.sp, 600, LocalAccent.current) }
            }
            Group {
                list.forEach { cal ->
                    item {
                        val on = cal.key !in config.excludedCalendars
                        val events = byCal[cal.id].orEmpty()
                        Column {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable(onClickLabel = "${cal.name} 이번 주 일정 보기") { open = if (open == cal.id) null else cal.id }
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(cal.color or 0xFF000000)))
                                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                                    T(cal.name, 16.sp, 500, if (on) SK.text else SK.muted, maxLines = 1)
                                    T(if (week == null) " " else "이번 주 ${events.size}개", 13.sp, 400, SK.muted)
                                }
                                androidx.compose.material3.Switch(on, { v ->
                                    update { it.copy(excludedCalendars = if (v) it.excludedCalendars - cal.key else it.excludedCalendars + cal.key) }
                                }, colors = accentSwitch())
                            }
                            if (open == cal.id) Column(Modifier.fillMaxWidth().padding(start = 40.dp, end = 16.dp, bottom = 12.dp)) {
                                if (events.isEmpty()) T("이번 주에는 일정이 없어요.", 13.sp, 400, SK.muted)
                                events.forEach { e ->
                                    T("${e.at.format(DAY)} · ${e.title} · ${if (e.allDay) "종일" else e.at.format(HM)}", 13.sp, 400, if (on) SK.text else SK.muted, Modifier.padding(vertical = 3.dp), maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    T("끈 캘린더의 일정은 오늘 일정과 달력 카드에 나오지 않아요. 모든 오늘 일정·달력 카드에 적용돼요.", 13.sp, 400, SK.muted, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), lineHeight = 18.sp)
}

/** Claude·Codex 사용량 카드 (personal build only). */
@Composable
internal fun UsageSection(config: AppConfig, update: Update) {
    if (UsageFeature.enabled) Section(null) {
        item { SwitchRow("Claude·Codex 사용량", config.usageEnabled, { v -> update { it.copy(usageEnabled = v) } }, subtitle = "사용량 카드에 보여요 · 개인용 빌드에만 있어요") }
    }
}

private fun ddayText(today: LocalDate, date: LocalDate): String {
    val n = ChronoUnit.DAYS.between(today, date)
    return if (n >= 0) "D-$n" else "D+${-n}"
}

@Composable
private fun DDayAdd(date: LocalDate, onDate: () -> Unit, onAdd: (DDay) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Field(name, { name = it }, "새 D-day 이름")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(SK.surface2).clickable(onClick = onDate).padding(horizontal = 14.dp), Alignment.CenterStart) {
                T(date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")), 16.sp, 500, SK.text, maxLines = 1)
            }
            ActionButton("추가", { onAdd(DDay(name.trim(), date)); name = "" }, enabled = name.isNotBlank())
        }
    }
}

/** [day] in [year]/[month], pulled back to the month's last day when it has fewer (1/31 → 2월 = 2/28, 2/29 in a leap year). */
internal fun ddayDate(year: Int, month: Int, day: Int): LocalDate =
    java.time.YearMonth.of(year, month).let { it.atDay(day.coerceIn(1, it.lengthOfMonth())) }

/**
 * plan-r13 C2: the D-day date as year (this year −30 … +10, so a past date counts up as D+) / month / day wheels (the alarm card's wheel), the day clamped when
 * the month or year moves. Wide sheets put the date and [확인] beside the wheels so a 320 dp landscape phone shows both.
 */
@Composable
internal fun DDayDateSheet(today: LocalDate, initial: LocalDate, onDismiss: () -> Unit, onDone: (LocalDate) -> Unit) {
    var d by rememberSaveable { mutableStateOf(initial) }
    val y0 = today.year - 30
    Sheet("D-day 날짜", onDismiss) {
        val wheels = @Composable { m: Modifier ->
            Row(m, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val item = 44.dp
                kr.dot.sb.ui.cards.NumberWheel(41, (d.year - y0).coerceIn(0, 40), item, SK.surface2, Modifier.weight(1.3f), wrap = false,
                    onPick = { y -> d = ddayDate(y0 + y, d.monthValue, d.dayOfMonth) }) { v, on -> WheelText("${y0 + v}년", on) }
                kr.dot.sb.ui.cards.NumberWheel(12, d.monthValue - 1, item, SK.surface2, Modifier.weight(1f),
                    onPick = { mo -> d = ddayDate(d.year, mo + 1, d.dayOfMonth) }) { v, on -> WheelText("${v + 1}월", on) }
                // a month of another length is a new wheel, opened on the clamped day
                androidx.compose.runtime.key(d.lengthOfMonth()) {
                    kr.dot.sb.ui.cards.NumberWheel(d.lengthOfMonth(), d.dayOfMonth - 1, item, SK.surface2, Modifier.weight(1f),
                        onPick = { dd -> d = ddayDate(d.year, d.monthValue, dd + 1) }) { v, on -> WheelText("${v + 1}일", on) }
                }
            }
        }
        val done = @Composable { m: Modifier ->
            Column(m, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T(d.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")) + " · " + ddayText(today, d), 15.sp, 600, LocalAccent.current, maxLines = 2)
                ActionButton("확인", { onDone(d) }, Modifier.fillMaxWidth())
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            if (maxWidth >= 480.dp) Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                wheels(Modifier.weight(2f))
                done(Modifier.weight(1f))
            } else Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                wheels(Modifier.fillMaxWidth())
                done(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun WheelText(text: String, on: Boolean) = T(text, 17.sp, if (on) 700 else 500, if (on) SK.text else SK.muted, maxLines = 1)

// ---- 권한 (정보 › 권한) ---------------------------------------------------------------------------------------------

/** Checklist (consensus): the missing ones first with a big button each and a plain reason, the allowed ones as one line. */
@Composable
fun PermsScreen(config: AppConfig, env: SettingsEnv, back: () -> Unit) {
    val perms = Perms.forConfig(config)
    val need = perms.filter { env.granted[it] != true }
    val ok = perms.filter { env.granted[it] == true }
    Page("권한", back) {
        T(if (need.isEmpty()) "모두 허용했어요." else "아래를 허용하면 모든 기능을 쓸 수 있어요.", 15.sp, 500, if (need.isEmpty()) SK.ok else SK.muted, Modifier.padding(start = 4.dp, top = 4.dp))
        // plan-r13 B1: one button walks every missing one in turn (each system page or question once)
        if (need.isNotEmpty()) {
            ActionButton("차례로 허용하기", { env.onPermWalkOf(perms) }, Modifier.fillMaxWidth().padding(top = 16.dp))
            T("하나씩 열어 드려요. 허용하고 돌아오면 다음으로 넘어가요.", 13.sp, 400, SK.muted, Modifier.padding(start = 4.dp, top = 8.dp))
        }
        need.forEach { p ->
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(SK.surface).padding(16.dp)) {
                T(p.ko, 16.sp, 600)
                T(p.why, 13.sp, 400, SK.muted, Modifier.padding(top = 4.dp, bottom = 12.dp), lineHeight = 18.sp)
                ActionButton(p.button, { env.onPerm(p) }, Modifier.fillMaxWidth())
            }
        }
        if (ok.isNotEmpty()) Section("허용했어요") {
            ok.forEach { p ->
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { env.onPerm(p) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ic.CHECK, SK.ok, 18.dp)
                        Column(Modifier.padding(start = 10.dp)) {
                            T(p.ko, 15.sp, 500, SK.text)
                            T(p.why, 12.sp, 400, SK.muted, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---- 정보 (plan-r15 F8) ---------------------------------------------------------------------------------------------

private val FONT_NAMES = mapOf(
    "inter" to "Inter", "nunito" to "Nunito", "pretendard" to "Pretendard", "sourceserif4" to "Source Serif 4", "robotoslab" to "Roboto Slab",
    "sofiasansextracondensed" to "Sofia Sans Extra Condensed", "mplusrounded1c" to "M PLUS Rounded 1c", "bagelfatone" to "Bagel Fat One",
    "barlowcondensed" to "Barlow Condensed", "bigshouldersdisplay" to "Big Shoulders Display", "fredoka" to "Fredoka", "gowunbatang" to "Gowun Batang",
    "ibmplexmono" to "IBM Plex Mono", "jost" to "Jost", "jua" to "Jua", "nanumpenscript" to "Nanum Pen Script",
    "oswald" to "Oswald", "outfit" to "Outfit", "playfairdisplay" to "Playfair Display", "titanone" to "Titan One",
)

/** 정보: 권한, 개인정보 처리방침, 버전, (personal) 문제 해결, 글꼴 라이선스, then 처음 설정으로 behind one question. */
@Composable
fun AboutScreen(config: AppConfig, nav: Nav, env: SettingsEnv, back: () -> Unit) {
    var resetting by rememberSaveable { mutableStateOf(false) }
    val missing = Perms.forConfig(config).filter { env.granted[it] != true }
    Page("정보", back) {
        Section(null, footer = "알림 내용은 폰 밖으로 나가지 않아요.") {
            item { NavRow("권한", { nav.push(Routes.PERMS) }, badge = if (missing.isEmpty()) "모두 허용" else "${missing.size}개 필요", badgeOk = missing.isEmpty()) }
            item { val uri = androidx.compose.ui.platform.LocalUriHandler.current; NavRow("개인정보 처리방침", { runCatching { uri.openUri(PRIVACY_URL) } }) }
            item { Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) { T("버전", 16.sp, 500, SK.text, Modifier.weight(1f)); T(env.versionName, 16.sp, 500, SK.muted) } }
        }
        // r14 B8: the trail is for the operator (no adb); the public build writes it but does not show it
        if (kr.dot.sb.BuildConfig.FLAVOR == "personal") {
            Section("문제 해결", footer = "충전기를 꽂으면 최근 켜짐 기록이 여기에 남아요.") {
                item {
                    val trail = remember { env.chargeTrail() }
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        T("최근 켜짐 기록", 16.sp, 500, SK.text)
                        T(if (trail.isEmpty()) "아직 기록이 없어요" else trail.joinToString("\n"), 14.sp, 400, SK.muted, Modifier.padding(top = 6.dp), lineHeight = 22.sp)
                    }
                }
                // plan-r15 Order step 2: the measurement round's log, read-only, newest first; 복사 for sending it on
                item {
                    val lines = remember { env.measureLog() }
                    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T("측정 기록", 16.sp, 500, SK.text, Modifier.weight(1f))
                            if (lines.isNotEmpty()) ActionButton("복사", { clip.setText(androidx.compose.ui.text.AnnotatedString(lines.joinToString("\n"))) }, primary = false)
                        }
                        T(if (lines.isEmpty()) "아직 기록이 없어요" else lines.joinToString("\n"), 12.sp, 400, SK.muted, Modifier.padding(top = 6.dp), lineHeight = 17.sp)
                    }
                }
            }
        }
        Section("글꼴 라이선스", footer = "Stand Screen은 아래 공개 글꼴을 써요. 누르면 라이선스 전문이 열려요.") {
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
        Gap(24.dp)
        ActionButton("처음 설정으로", { resetting = true }, Modifier.fillMaxWidth(), danger = true)
    }
    if (resetting) androidx.compose.material3.AlertDialog(
        onDismissRequest = { resetting = false },
        containerColor = SK.surface,
        title = { T("처음 설정으로 돌아갈까요?", 18.sp, 700) },
        text = { T("고른 사진 폴더와 허용한 항목을 다시 정해야 해요.", 15.sp, 400, SK.muted, lineHeight = 21.sp) },
        confirmButton = { ActionButton("돌아가기", { resetting = false; env.onReset() }, danger = true) },
        dismissButton = { ActionButton("취소", { resetting = false }, primary = false) },
    )
}

/** plan-r13 B5 "밤에도 알림 오면 화면 켜기": the inverse of the stored [AppConfig.nightNotifQuiet]; off while 끄지 않기. */
internal object NightWake {
    fun shown(c: AppConfig): Boolean = !c.nightNotifQuiet && c.displayOff != DisplayOff.NEVER
    fun set(c: AppConfig, on: Boolean): AppConfig = c.copy(nightNotifQuiet = !on)
}

/** plan-r13 B4 어두움 정도: a lux threshold on a 1 (bright, [HI] lx) .. 20 (dark, [LO] lx) log scale; stored as lux. */
internal object DarkLevel {
    const val LO = 1f
    const val HI = 40f
    private val span = kotlin.math.ln(HI / LO)

    fun of(lux: Float): Float = 1f + 19f * kotlin.math.ln(HI / lux.coerceIn(LO, HI)) / span

    /** Rounded to 0.1 lx (the stored value). */
    fun lux(level: Float): Float = (HI * kotlin.math.exp(-span * (level.coerceIn(1f, 20f) - 1f) / 19f) * 10f).let { kotlin.math.round(it) / 10f }
}
