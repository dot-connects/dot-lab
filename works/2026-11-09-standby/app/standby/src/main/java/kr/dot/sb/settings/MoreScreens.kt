@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package kr.dot.sb.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.City
import kr.dot.sb.model.DDay
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.Layout
import kr.dot.sb.model.NightMode
import kr.dot.sb.model.SleepSound
import kr.dot.sb.model.UnfoldAction
import kr.dot.sb.ui.board.BoardView
import kr.dot.sb.ui.state.StandbyActions
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Collections

private fun <T> List<T>.swap(i: Int, d: Int): List<T> {
    val j = i + d
    if (i !in indices || j !in indices) return this
    return toMutableList().also { Collections.swap(it, i, j) }
}

// ---- widgets page stacks ----------------------------------------------------------------------------------------

@Composable
fun WidgetsScreen(config: AppConfig, update: Update, back: () -> Unit) {
    val state = remember(config.city, config.use24h) { Sample.state(config) }
    var pick by remember { mutableStateOf<Triple<Boolean, Int?, Unit>?>(null) } // (left?, index or null = append)
    val dev = PreviewDevice.FOLD_COVER
    val rects = LayoutGeo.slots(Layout.TWO, dev.w.toFloat(), dev.h.toFloat())
    fun editSide(left: Boolean, f: (List<CardType>) -> List<CardType>) = update { if (left) it.copy(widgetLeft = f(it.widgetLeft)) else it.copy(widgetRight = f(it.widgetRight)) }
    Box(Modifier.fillMaxSize()) {
        Page(
            "위젯 카드", back,
            header = { PreviewFrame(dev.w, dev.h, Modifier.fillMaxWidth(), maxHeight = 190.dp) { BoardView(widgetsBoard(config), state, config, StandbyActions.NONE, Modifier.fillMaxSize()) } },
        ) {
            val sides = listOf(true to config.widgetLeft, false to config.widgetRight)
            sides.forEach { (left, cards) ->
                CardStackSection(
                    title = if (left) "왼쪽 칸" else "오른쪽 칸",
                    cards = cards,
                    onReplace = { j -> pick = Triple(left, j, Unit) },
                    onAdd = { pick = Triple(left, null, Unit) },
                    onMove = { j, d -> editSide(left) { it.swap(j, d) } },
                    onRemove = { j -> editSide(left) { l -> l.filterIndexed { k, _ -> k != j } } },
                    footer = if (left) null else "카드가 둘 이상이면 그 칸을 위아래로 넘겨요. 첫 번째 카드가 처음에 보여요.",
                )
            }
        }
        val p = pick
        if (p != null) {
            val left = p.first
            val r = rects[if (left) 0 else 1]
            val stack = if (left) config.widgetLeft else config.widgetRight
            CardPickerSheet(
                title = (if (left) "왼쪽 칸" else "오른쪽 칸") + " · 카드 고르기",
                slotW = r.w.toInt(), slotH = r.h.toInt(), config = config, state = state, accent = null, face = kr.dot.sb.model.FaceId.DIGITAL,
                current = p.second?.let { stack.getOrNull(it) }, taken = stack.toSet(), onDismiss = { pick = null },
                onPick = { card ->
                    editSide(left) { l -> if (p.second == null) l + card else l.mapIndexed { k, c -> if (k == p.second) card else c } }
                    pick = null
                },
            )
        }
    }
}

// ---- night & display --------------------------------------------------------------------------------------------

@Composable
fun NightScreen(config: AppConfig, update: Update, env: SettingsEnv, back: () -> Unit) {
    val never = config.displayOff == DisplayOff.NEVER
    val displayNote = when (config.displayOff) {
        DisplayOff.AUTO -> "사용하지 않으며 방이 어두울 때 디스플레이가 꺼집니다."
        DisplayOff.AFTER_20S -> "20초 후에 디스플레이가 꺼집니다."
        DisplayOff.NEVER -> "스탠바이가 켜져 있으면 디스플레이가 계속 켜져 있습니다."
    }
    Page("야간 모드 · 디스플레이", back) {
        Section("디스플레이", footer = if (never) "'안 함'일 때는 야간 모드와 움직임으로 깨우기를 쓸 수 없어요." else null) {
            item {
                ControlRow(null, note = displayNote) {
                    Segmented(DisplayOff.entries.map { it.ko }, config.displayOff.ordinal, { i -> update { it.copy(displayOff = DisplayOff.entries[i]) } })
                }
            }
            if (config.displayOff == DisplayOff.AUTO) item {
                SwitchRow("움직임으로 깨우기", config.motionToWake, { v -> update { it.copy(motionToWake = v) } }, subtitle = "밤에 폰이 움직이면 화면이 켜져요")
            }
            item { SwitchRow("화면 위치 조금씩 옮기기", config.burnInShift, { v -> update { it.copy(burnInShift = v) } }, subtitle = "1분마다 몇 dp씩 움직여 화면 잔상을 막아요") }
        }

        if (!never) {
            Section("야간 모드") {
                item {
                    ControlRow(null, note = when (config.nightMode) {
                        NightMode.AUTO -> "저조도에서 화면 밝기를 낮추고 항목이 빨간색 틴트로 표시됩니다."
                        NightMode.SCHEDULE -> "정해 둔 시간에만 빨간 화면이 돼요."
                        NightMode.OFF -> "밤에도 낮과 같은 색으로 보여요."
                    }) { Segmented(listOf("자동", "시간 예약", "끔"), config.nightMode.ordinal, { i -> update { it.copy(nightMode = NightMode.entries[i]) } }) }
                }
                if (config.nightMode == NightMode.SCHEDULE) {
                    item {
                        SliderRow("시작", "%02d:00".format(config.nightStartHour), config.nightStartHour.toFloat(), 0f..23f, { v ->
                            update { it.copy(nightStartHour = v.toInt()) }
                        }, steps = 22)
                    }
                    item {
                        SliderRow("끝", "%02d:00".format(config.nightEndHour), config.nightEndHour.toFloat(), 0f..23f, { v ->
                            update { it.copy(nightEndHour = v.toInt()) }
                        }, steps = 22)
                    }
                }
                if (config.nightMode == NightMode.AUTO) {
                    item { LuxRow(env.lux, config) }
                }
                if (config.nightMode != NightMode.OFF) {
                    if (config.nightMode == NightMode.AUTO) {
                        item {
                            SliderRow("어두워졌다고 볼 밝기", "${config.nightEnterLux.toInt()} lx 이하", config.nightEnterLux, 1f..20f, { v ->
                                update { it.copy(nightEnterLux = v.toInt().toFloat(), nightExitLux = maxOf(it.nightExitLux, v.toInt() + 2f)) }
                            }, steps = 18, note = "이 밝기가 5초 이어지면 빨간 화면으로 바뀌어요")
                        }
                        item {
                            SliderRow("밝아졌다고 볼 밝기", "${config.nightExitLux.toInt()} lx 이상", config.nightExitLux, 3f..40f, { v ->
                                update { it.copy(nightExitLux = maxOf(v.toInt().toFloat(), it.nightEnterLux + 2f)) }
                            }, steps = 36, note = "이 밝기가 1.5초 이어지면 원래 색으로 돌아와요")
                        }
                    }
                    item { SliderRow("야간 밝기", "${(config.nightMaxBrightness * 100).toInt()}%", config.nightMaxBrightness, 0.05f..1f, { v -> update { it.copy(nightMaxBrightness = (v * 20).toInt() / 20f) } }, note = "밤에 화면이 넘지 않을 최대 밝기예요") }
                }
            }
        }

        Section("알림") {
            item { SwitchRow("알림 보기", config.showNotifications, { v -> update { it.copy(showNotifications = v) } }, subtitle = "받은 알림을 화면 가득 크게 보여줘요") }
            item { SwitchRow("탭할 때만 미리보기", config.previewOnTapOnly, { v -> update { it.copy(previewOnTapOnly = v) } }, subtitle = "누르기 전에는 앱 아이콘만 보여요", enabled = config.showNotifications) }
        }

        Section("시간 표시") {
            item {
                ControlRow(null) {
                    Segmented(listOf("시스템 설정", "12시간", "24시간"), when (config.use24h) { null -> 0; false -> 1; true -> 2 }, { i -> update { it.copy(use24h = when (i) { 1 -> false; 2 -> true; else -> null }) } })
                }
            }
        }
    }
}

@Composable
private fun LuxRow(lux: Float?, config: AppConfig) {
    val dark = lux != null && lux <= config.nightEnterLux
    val bright = lux != null && lux >= config.nightExitLux
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            T("지금 주변 밝기", 16.sp, 500)
            T("센서 값을 실시간으로 보여줘요", 13.sp, 400, SK.muted, Modifier.padding(top = 2.dp))
        }
        T(if (lux == null) "—" else "${if (lux < 10) "%.1f".format(lux) else lux.toInt().toString()} lx", 22.sp, 700, Modifier.padding(end = 10.dp))
        if (lux != null) Pill(if (dark) "밤 모드" else if (bright) "낮" else "중간", if (dark) LocalAccent.current else SK.muted)
    }
}

@Composable
private fun T(text: String, size: androidx.compose.ui.unit.TextUnit, weight: Int, modifier: Modifier) = T(text, size, weight, SK.text, modifier)

// ---- alarm & mood light -----------------------------------------------------------------------------------------

private val GLOWS = listOf("따뜻한 주황" to 0xFFFFB36BL, "호박" to 0xFFFFA81FL, "빨강" to 0xFFFF453AL, "민트" to 0xFF85D4BAL, "흰색" to 0xFFFFFFFFL)

@Composable
fun AlarmScreen(config: AppConfig, update: Update, env: SettingsEnv, back: () -> Unit) {
    val glow = config.glowColor.c()
    val offsets = listOf(0, 5, 10, 15)
    Page("알람 · 무드등", back) {
        Section("불빛 색", footer = "폴드를 펼쳤을 때와 알람이 울릴 때 안쪽 화면이 이 색으로 천천히 밝아져요.") {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(14.dp)).background(Brush.verticalGradient(listOf(glow, lerp(glow, Color.Black, 0.62f)))))
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        GLOWS.forEach { (name, c) ->
                            Column(Modifier.width(60.dp).clip(RoundedCornerShape(12.dp)).clickable { update { it.copy(glowColor = c) } }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(44.dp), Alignment.Center) {
                                    if (c == config.glowColor) Box(Modifier.size(42.dp).border(2.dp, Color.White, CircleShape))
                                    Box(Modifier.size(30.dp).clip(CircleShape).background(c.c()))
                                }
                                T(name, 11.sp, if (c == config.glowColor) 700 else 500, if (c == config.glowColor) SK.text else SK.muted, Modifier.padding(top = 2.dp), maxLines = 2, align = TextAlign.Center)
                            }
                        }
                    }
                }
            }
            item { ControlRow("직접 고르기") { ColorPalette(config.glowColor, { c -> update { it.copy(glowColor = c) } }) } }
        }
        Section("알람") {
            item {
                ControlRow(
                    "알람 전에 미리 밝히기",
                    note = if (env.granted[Perm.EXACT_ALARM] != true && config.glowStartMinBefore > 0) "정확한 알람 허용이 꺼져 있어 미리 밝히기가 동작하지 않아요. 권한 화면에서 켜 주세요." else "삼성 시계 앱의 다음 알람보다 먼저 서서히 밝아져요.",
                ) { Segmented(offsets.map { if (it == 0) "끔" else "${it}분" }, offsets.indexOf(config.glowStartMinBefore).coerceAtLeast(0), { i -> update { it.copy(glowStartMinBefore = offsets[i]) } }) }
            }
            item { SliderRow("밝아지는 시간", "${config.glowRampSec}초", config.glowRampSec.toFloat(), 2f..20f, { v -> update { it.copy(glowRampSec = v.toInt()) } }, steps = 17) }
        }
        Section("폴드를 펼쳤을 때") {
            item { ControlRow(null, note = if (config.onUnfold == UnfoldAction.MOOD_LIGHT) "스탠바이가 켜져 있는 동안 펼치면 안쪽 화면 전체가 무드등이 돼요." else "스탠바이가 켜져 있는 동안 펼치면 큰 칸 + 작은 칸의 '책 (펼침)' 화면을 보여줘요.") {
                Segmented(UnfoldAction.entries.map { it.ko }, config.onUnfold.ordinal, { i -> update { it.copy(onUnfold = UnfoldAction.entries[i]) } })
            } }
        }
        Gap(20.dp)
        ActionButton("무드등 테스트", env.onGlowTest, Modifier.fillMaxWidth())
        T("지금 설정으로 안쪽 화면을 잠깐 밝혀 봐요", 13.sp, 400, SK.muted, Modifier.padding(top = 8.dp, start = 4.dp))
    }
}

// ---- data sources -----------------------------------------------------------------------------------------------

@Composable
fun DataScreen(config: AppConfig, update: Update, back: () -> Unit) {
    val minutes = listOf(15, 30, 45, 60, 90)
    Page("데이터", back) {
        Section("날씨 지역", footer = "Open-Meteo에서 30분마다 받아와요.") {
            item { Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                T("지금 지역", 16.sp, 500, SK.text, Modifier.weight(1f)); T(config.city.name, 16.sp, 700, LocalAccent.current)
            } }
            item { CitySearch { c -> update { it.copy(city = c) } } }
        }
        Section("세계 시계 도시") {
            item {
                ControlRow(null) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        config.worldCities.forEach { c ->
                            Row(
                                Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).background(SK.surface2).clickable(enabled = config.worldCities.size > 1) { update { s -> s.copy(worldCities = s.worldCities.filter { it != c }) } }.padding(start = 16.dp, end = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) { T(c.name, 14.sp, 500, SK.text); if (config.worldCities.size > 1) Icon(Ic.CLOSE, SK.muted, 18.dp, Modifier.padding(start = 6.dp)) }
                        }
                    }
                }
            }
            item { CitySearch { c -> update { s -> if (s.worldCities.any { it.name == c.name }) s else s.copy(worldCities = s.worldCities + c) } } }
        }
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
        Section("수면 소리") {
            item { ControlRow("소리") { ChipFlow(SleepSound.entries.map { it.ko }, config.sleepSound.ordinal, { i -> update { it.copy(sleepSound = SleepSound.entries[i]) } }) } }
            item { ControlRow("꺼지는 시간") { ChipFlow(minutes.map { "${it}분" }, minutes.indexOf(config.sleepMinutes).let { if (it < 0) -1 else it }, { i -> update { it.copy(sleepMinutes = minutes[i]) } }) } }
        }
        Section("타이머 · 뽀모도로") {
            item { SliderRow("집중", "${config.focusMinutes}분", config.focusMinutes.toFloat(), 5f..60f, { v -> update { it.copy(focusMinutes = v.toInt()) } }, steps = 54) }
            item { SliderRow("휴식", "${config.breakMinutes}분", config.breakMinutes.toFloat(), 1f..30f, { v -> update { it.copy(breakMinutes = v.toInt()) } }, steps = 28) }
        }
        Section("사용량", footer = "Claude·Codex 사용량은 눈금 앱과 같은 피드(같은 와이파이의 PC, 안 되면 상태 봇)를 읽어요. 켜 두면 5분마다 가져와요.") {
            item { SwitchRow("Claude·Codex 사용량", config.usageEnabled, { v -> update { it.copy(usageEnabled = v) } }) }
        }
    }
}

private fun ddayText(date: LocalDate): String {
    val n = ChronoUnit.DAYS.between(LocalDate.now(), date)
    return if (n >= 0) "D-$n" else "D+${-n}"
}

@Composable
private fun CitySearch(onPick: (City) -> Unit) {
    var q by remember { mutableStateOf("") }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Field(q, { q = it }, "도시 이름으로 찾기")
        val results = Cities.search(q)
        if (results.isEmpty()) T("찾는 도시가 목록에 없어요", 13.sp, 400, SK.muted, Modifier.padding(8.dp))
        androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            results.forEach { c ->
                Box(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(50)).border(1.dp, SK.line, RoundedCornerShape(50)).clickable { onPick(c); q = "" }.padding(horizontal = 16.dp), Alignment.Center) {
                    T(c.name, 14.sp, 500, SK.text)
                }
            }
        }
    }
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

// ---- permissions ------------------------------------------------------------------------------------------------

@Composable
fun PermsScreen(env: SettingsEnv, back: () -> Unit) {
    val missing = env.missing.size
    Page("권한", back) {
        T(if (missing == 0) "필요한 항목이 모두 켜져 있어요" else "${missing}개를 켜면 스탠바이가 제대로 동작해요", 15.sp, 500, if (missing == 0) SK.ok else SK.muted, Modifier.padding(start = 4.dp, top = 4.dp))
        Section(null) {
            Perm.entries.forEach { p ->
                item {
                    val ok = env.granted[p] == true
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T(p.ko, 16.sp, 600, SK.text, Modifier.weight(1f))
                            Pill(if (ok) "켜짐" else "필요", if (ok) SK.ok else SK.danger)
                        }
                        T(p.why, 13.sp, 400, SK.muted, Modifier.padding(top = 4.dp, bottom = 12.dp), lineHeight = 18.sp)
                        ActionButton(if (ok) "설정 다시 보기" else p.button, { env.onPerm(p) }, Modifier.fillMaxWidth(), primary = !ok)
                    }
                }
            }
        }
    }
}

// ---- about ------------------------------------------------------------------------------------------------------

private val FONT_NAMES = mapOf("inter" to "Inter", "nunito" to "Nunito", "pretendard" to "Pretendard", "sourceserif4" to "Source Serif 4", "robotoslab" to "Roboto Slab", "sofiasansextracondensed" to "Sofia Sans Extra Condensed", "mplusrounded1c" to "M PLUS Rounded 1c")

@Composable
fun AboutScreen(env: SettingsEnv, back: () -> Unit) {
    Page("정보", back) {
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
