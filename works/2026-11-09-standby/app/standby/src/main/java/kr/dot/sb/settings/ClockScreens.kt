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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AnalogTicks
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Background
import kr.dot.sb.model.ClockLayout
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.Numerals
import kr.dot.sb.ui.clock.ClockFace
import kr.dot.sb.ui.theme.Fonts

// ---- list of clocks ---------------------------------------------------------------------------------------------

@Composable
fun ClockListScreen(config: AppConfig, nav: Nav, back: () -> Unit) {
    val state = remember(config.styles, config.city, config.use24h) { Sample.state(config) }
    @Composable
    fun row(face: FaceId, sub: String? = null) {
        val st = config.styles.getValue(face)
        PageRow(
            thumb = { ClockFace(face, st, state, Modifier.fillMaxSize()) },
            title = face.ko, subtitle = sub ?: "${st.font.ko} · ${st.effect.ko}", start = false, onClick = { nav.push(Routes.clock(face)) },
        )
    }
    Page("시계 스타일", back) {
        Section("스탠바이 시계", footer = "시계 화면을 위아래로 넘기면 이 순서로 바뀌어요. 시계마다 서체, 색, 크기가 따로예요.") {
            FaceId.CLOCKS.forEach { f -> item { row(f) } }
        }
        Section("그 밖의 시계") {
            item { row(FaceId.DESK, "책상 화면의 큰 시계") }
            item { row(FaceId.PHOTOS_TIME, "사진 화면 오른쪽 위 시간") }
        }
    }
}

// ---- one clock's style ------------------------------------------------------------------------------------------

private val FONT_ORDER = FontId.entries

@Composable
fun ClockStyleScreen(face: FaceId, config: AppConfig, update: Update, back: () -> Unit) {
    val style = config.styles.getValue(face)
    val state = remember(config.styles, config.city, config.use24h) { Sample.state(config) }
    var devIdx by rememberSaveable { mutableIntStateOf(0) }
    var portrait by rememberSaveable { mutableStateOf(false) }
    val dev = PreviewDevice.entries[devIdx]
    fun edit(f: (ClockStyle) -> ClockStyle) = update { it.copy(styles = it.styles + (face to f(it.styles.getValue(face)))) }
    val analog = face == FaceId.ANALOG
    val world = face == FaceId.WORLD
    val solar = face == FaceId.SOLAR

    Page(
        face.ko, back,
        header = {
            PreviewFrame(dev.width(portrait), dev.height(portrait), Modifier.fillMaxWidth(), maxHeight = 190.dp) {
                ClockFace(face, style, state, Modifier.fillMaxSize())
            }
        },
    ) {
        Gap(8.dp)
        ChipFlow(PreviewDevice.entries.map { it.ko }, devIdx, { devIdx = it })
        Gap(8.dp)
        Segmented(listOf("가로", "세로"), if (portrait) 1 else 0, { portrait = it == 1 })

        Section("서체", footer = "스텐실과 레일은 같은 글꼴을 잘라서 그려요.") {
            item {
                ControlRow(null) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FONT_ORDER.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { f -> FontTile(f, f == style.font, Modifier.weight(1f)) { edit { s -> s.copy(font = f, weight = f.defaultWeight) } } }
                                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }

        Section("크기와 굵기") {
            item { SliderRow("굵기", "${style.weight}", style.weight.toFloat(), 100f..950f, { v -> edit { it.copy(weight = (v / 10).toInt() * 10) } }) }
            item { SliderRow("크기", "${style.sizePct}%", style.sizePct.toFloat(), 60f..130f, { v -> edit { it.copy(sizePct = v.toInt()) } }) }
            item { SliderRow("너비", "${style.widthPct}%", style.widthPct.toFloat(), 70f..130f, { v -> edit { it.copy(widthPct = v.toInt()) } }) }
        }

        if (analog) {
            Section("아날로그 색") {
                item { ColorRow("시침 · 분침", style.handColor) { c -> edit { it.copy(handColor = c) } } }
                item { ColorRow("초침", style.secondColor) { c -> edit { it.copy(secondColor = c) } } }
                item { ColorRow("요일", style.weekdayColor) { c -> edit { it.copy(weekdayColor = c) } } }
                item { ColorRow("숫자", style.numeralColor) { c -> edit { it.copy(numeralColor = c) } } }
                item { ColorRow("눈금", style.tickColor) { c -> edit { it.copy(tickColor = c) } } }
            }
            Section("다이얼") {
                item { ControlRow("숫자") { Segmented(AnalogNumbers.entries.map { it.ko }, style.analogNumbers.ordinal, { i -> edit { it.copy(analogNumbers = AnalogNumbers.entries[i]) } }) } }
                item { ControlRow("눈금") { Segmented(AnalogTicks.entries.map { it.ko }, style.analogTicks.ordinal, { i -> edit { it.copy(analogTicks = AnalogTicks.entries[i]) } }) } }
                item { SwitchRow("날짜와 요일 표시", style.showInfo, { v -> edit { it.copy(showInfo = v) } }) }
            }
        } else {
            Section("색") {
                if (solar) item {
                    ControlRow("솔라 팔레트") {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ClockStyle.SOLAR_SETS.forEach { set ->
                                val on = style.bgColor == set[0] && style.bgColor2 == set[1]
                                Box(
                                    Modifier.weight(1f).size(40.dp).clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(set[0].c(), set[1].c())))
                                        .border(if (on) 2.dp else 1.dp, if (on) androidx.compose.ui.graphics.Color.White else SK.line, CircleShape)
                                        .clickable { edit { it.copy(bgColor = set[0], bgColor2 = set[1], color = set[2], color2 = set[3], effect = Effect.TWO_COLOR, background = Background.GRADIENT) } },
                                )
                            }
                        }
                    }
                }
                item { ControlRow(if (world) "도시 색" else "글자 색") { ColorPalette(style.color, { c -> edit { it.copy(color = c) } }) } }
                if (style.effect == Effect.TWO_COLOR) item { ControlRow("두 번째 색") { ColorPalette(style.color2, { c -> edit { it.copy(color2 = c) } }) } }
                if (world) item { ColorRow("밝은 점", style.litDotColor) { c -> edit { it.copy(litDotColor = c) } } }
            }
            Section("효과", footer = "밤에는 모든 효과가 꺼져요.") {
                item { ControlRow(null) { Segmented(Effect.entries.map { it.ko }, style.effect.ordinal, { i -> edit { it.copy(effect = Effect.entries[i]) } }) } }
            }
        }

        Section("배경") {
            item { ControlRow(null) { Segmented(Background.entries.map { it.ko }, style.background.ordinal, { i -> edit { it.copy(background = Background.entries[i]) } }) } }
            if (style.background != Background.BLACK) item { ControlRow(if (style.background == Background.GRADIENT) "시작 색" else "배경 색") { ColorPalette(style.bgColor, { c -> edit { it.copy(bgColor = c) } }) } }
            if (style.background == Background.GRADIENT) item { ControlRow("끝 색") { ColorPalette(style.bgColor2, { c -> edit { it.copy(bgColor2 = c) } }) } }
        }

        Section("숫자와 배치") {
            item { ControlRow("숫자 모양", note = "서양 숫자가 기본이에요") { ChipFlow(Numerals.entries.map { it.ko }, style.numerals.ordinal, { i -> edit { it.copy(numerals = Numerals.entries[i]) } }) } }
            if (!analog && !world) {
                item { ControlRow("배치") { Segmented(ClockLayout.entries.map { it.ko }, style.layout.ordinal, { i -> edit { it.copy(layout = ClockLayout.entries[i]) } }) } }
                item { SwitchRow("날짜와 온도 표시", style.showInfo, { v -> edit { it.copy(showInfo = v) } }, subtitle = "시계 옆에 요일·날짜·기온·다음 알람을 보여요") }
            }
        }

        Gap(24.dp)
        ActionButton("아이폰처럼 되돌리기", { update { it.copy(styles = it.styles + (face to kr.dot.sb.ui.theme.Themes.of(it.theme).clockDefaults(face))) } }, Modifier.fillMaxWidth(), primary = false)
    }
}

@Composable
private fun FontTile(font: FontId, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val acc = LocalAccent.current
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(if (on) SK.surface2 else SK.bg).border(if (on) 2.dp else 1.dp, if (on) acc else SK.line, shape)
            .clickable(onClick = onClick).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.height(56.dp), Alignment.Center) {
            Text(
                "12", color = if (on) acc else SK.text, fontSize = 38.sp, fontFamily = Fonts.clock(font, font.defaultWeight),
                fontWeight = FontWeight(font.defaultWeight.coerceIn(100, 900)), maxLines = 1,
            )
        }
        T(font.ko, 12.sp, if (on) 700 else 500, if (on) SK.text else SK.muted, Modifier.padding(top = 2.dp), maxLines = 1, align = TextAlign.Center)
    }
}
