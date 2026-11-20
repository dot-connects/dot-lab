package kr.dot.wherepark.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.layout
import androidx.compose.runtime.mutableIntStateOf
import kotlin.math.roundToInt
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.dot.wherepark.R
import kr.dot.wherepark.core.Ambiguity
import kr.dot.wherepark.core.Contrast
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.core.label

// Colours: plan-v2 global constraints (mockups/v2/final --app-*)
private val BG = Color(0xFFE9E7E3)
private val SF = Color(0xFFF6F5F2)
private val FG = Color(0xFF161616)
private val MU = Color(0xFF6C6A66)
private val LN = Color(0xFFD5D2CC)
private val ACC = Color(0xFFFF6A13)
private val ON_ACC = Color(0xFF1A0F06)
private val OK = Color(0xFF3A8A4F)
private val GRAB = Color(0x80808080)
private val SCRIM = Color(0x6B000000)

/* ts(): Theme.kt (same values) */

/** Compose keeps a line at least as tall as the font; the mockup's CSS line-height under ~1.2 (.big .f .9, .heard 1.25) trims it. */
private fun Modifier.tight(lineHeight: TextUnit, lines: Int = 1) = layout { m, c ->
    val p = m.measure(c)
    val trim = ((p.height - lines * lineHeight.toPx()) / 2).roundToInt().coerceAtLeast(0)
    layout(p.width, p.height - 2 * trim) { p.place(0, -trim) }
}

/** "B2" and "C37" for the big readout; a digit zone reads "2-37". */
fun Spot.big(): Pair<String, String> {
    val zn = when {
        zone != null && number != null -> if (zone.first().isDigit()) "$zone-$number" else "$zone$number"
        else -> zone ?: number?.toString()
    }
    val rest = listOfNotNull(zn, pillar?.let { "기둥 $it" }).joinToString(" ")
    return floor?.label()?.let { it to rest } ?: (rest to "")
}

fun Spot.short(): String = big().let { (a, b) -> listOf(a, b).filter { it.isNotEmpty() }.joinToString(" · ") }

/** Callbacks only the Activity can run (system dialogs, camera, settings). */
class SheetActions(
    val askMic: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val downloadPack: () -> Unit = {},
    val photo: () -> Unit = {},
)

@Composable
fun ListenSheet(m: ListenModel, acts: SheetActions, behind: @Composable () -> Unit = {}) {
    val ctx = LocalContext.current
    val style = m.style
    val wnum = remember(style.font) { AssetFonts.family(ctx, style.font, 900) }
    val wnum8 = remember(style.font) { AssetFonts.family(ctx, style.font, 800) }
    val gro9 = remember { AssetFonts.family(ctx, "Inter Tight", 900) }
    val gro8 = remember { AssetFonts.family(ctx, "Inter Tight", 800) }
    // mockup: the widget accent only if it reads on the sheet (>= 1.6 : 1), else the brand orange
    val wacc = if (Contrast.ratio(style.acc, 0xFFE9E7E3.toInt()) >= 1.6) Color(style.acc) else ACC
    val f = Faces(wnum, wnum8, gro9, gro8, style.font == "Doto", wacc)

    Box(Modifier.fillMaxSize()) {
        behind()
        Box(Modifier.fillMaxSize().background(SCRIM).clickable(remember { MutableInteractionSource() }, null) { m.close() })
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)).background(BG)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .navigationBarsPadding().imePadding(),
        ) {
            Box(Modifier.padding(top = if (m.state is Sheet.Ambiguous) 22.dp else 8.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(GRAB).align(Alignment.CenterHorizontally))
            when (val s = m.state) {
                is Sheet.Listening -> Listening(m, s, f)
                is Sheet.AskMic -> AskMic(m, s, acts)
                is Sheet.Saved -> Saved(m, s, f, acts)
                is Sheet.Ambiguous -> Ambiguous(m, s, f)
                is Sheet.Manual -> Manual(m, s, f, acts)
                is Sheet.Practice -> Practice(m, s, f)
            }
        }
    }
}

private class Faces(val wnum: FontFamily, val wnum8: FontFamily, val gro9: FontFamily, val gro8: FontFamily, val doto: Boolean, val acc: Color)

@Composable
private fun Body(top: Dp = 12.dp, bottom: Dp = 20.dp, gap: Dp = 12.dp, content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = top, bottom = bottom), verticalArrangement = Arrangement.spacedBy(gap), content = content)

@Composable
private fun Ic(@DrawableRes id: Int, size: Dp, tint: Color) = Icon(painterResource(id), null, Modifier.size(size), tint = tint)

@Composable
private fun Pill(text: String, onClick: () -> Unit, h: Dp = 44.dp, padH: Dp = 14.dp, size: TextUnit = 14.sp, bg: Color = SF, fg: Color = FG,
                 modifier: Modifier = Modifier, lead: (@Composable () -> Unit)? = null) {
    Row(modifier.heightIn(min = h).clip(CircleShape).background(bg).clickable(onClick = onClick).padding(horizontal = padH),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
        lead?.invoke()
        Text(text, style = ts(size, FontWeight.SemiBold, fg))
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit, primary: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier.fillMaxWidth().height(52.dp).clip(CircleShape)
            .then(if (primary) Modifier.background(if (enabled) ACC else LN) else Modifier.border(BorderStroke(1.5.dp, LN), CircleShape))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = ts(15.5.sp, FontWeight.SemiBold, if (primary) ON_ACC else FG)) }
}

@Composable
private fun CarTo(m: ListenModel) {
    val c = m.car
    Box(Modifier.size(12.dp).clip(CircleShape).background(Color(c?.color ?: 0xFFF4F1EA.toInt())).border(1.dp, Color(0x33000000), CircleShape))
    Text(buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(c?.name ?: "내 차") }
        append("에 저장")
    }, style = ts(14.sp))
}

@Composable
private fun Header(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), content = content)

// 1. listening

private const val BARS = 30

@Composable
private fun Listening(m: ListenModel, s: Sheet.Listening, f: Faces) = Body {
    Header {
        CarTo(m)
        Spacer(Modifier.weight(1f))
        Pill("취소", m.close)
    }
    // rms (dB, about -2..10) feeds a scrolling waveform: each tick the newest level enters on the right
    val target by rememberUpdatedState(((s.rms + 2f) / 12f).coerceIn(0f, 1f))
    val bars = remember { mutableStateListOf<Float>().apply { repeat(BARS) { i -> add(target * (5 + (i * 53) % 31) / 35f) } } }
    LaunchedEffect(Unit) {
        var cur = target
        var t = 0
        while (true) {
            cur += (target - cur) * (if (target > cur) 0.6f else 0.25f)  // fast attack, slow release
            bars.removeAt(0)
            bars.add(cur * (0.7f + 0.3f * kotlin.math.abs(kotlin.math.sin(t++ * 1.3f))))
            kotlinx.coroutines.delay(50)
        }
    }
    Row(Modifier.fillMaxWidth().height(40.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        bars.forEach { v -> Box(Modifier.weight(1f).height((4f + 36f * v).dp).clip(RoundedCornerShape(2.dp)).background(f.acc)) }
    }
    var lines by remember { mutableIntStateOf(1) }
    Text(
        buildAnnotatedString { append(s.partial); appendInlineContent("cur", "|") },
        Modifier.tight(37.5.sp, lines), onTextLayout = { lines = it.lineCount },
        style = ts(30.sp, FontWeight.Bold, lh = 1.25f, ls = (-0.02).em),
        inlineContent = mapOf("cur" to InlineTextContent(Placeholder(5.sp, 28.sp, PlaceholderVerticalAlign.TextCenter)) {
            Box(Modifier.fillMaxSize().padding(start = 3.dp).background(f.acc))
        }),
    )
    Text(if (s.again) "다시 말해 주세요" else "듣고 있어요 · 다 말하면 저장돼요", style = ts(13.5.sp, color = MU))
}

// mic permission reason

@Composable
private fun AskMic(m: ListenModel, s: Sheet.AskMic, acts: SheetActions) = Body {
    Header {
        CarTo(m)
        Spacer(Modifier.weight(1f))
        Pill("취소", m.close)
    }
    Text("마이크를 켜 주세요", style = ts(18.sp, FontWeight.Bold))
    Text("말로 저장하려면 마이크가 필요해요. 소리는 폰 안에서만 글자로 바꿔요.", style = ts(14.sp, color = MU))
    BigButton(if (s.denied == 0) "마이크 켜기" else "다시 켜기", acts.askMic, primary = true)
    BigButton("직접 넣기", m::manual, primary = false)
}

// 2. saved

@Composable
private fun BigSpot(spot: Spot, f: Faces) {
    val (a, b) = spot.big()
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(fsplit(a, f, 84.sp), Modifier.alignByBaseline().tight(75.6.sp), style = ts(84.sp, FontWeight.Black, lh = 0.9f, family = f.wnum, ls = (-0.03).em), maxLines = 1)
        if (b.isNotEmpty()) Text(fsplit(b, f, 40.sp), Modifier.alignByBaseline(), style = ts(40.sp, FontWeight.ExtraBold, family = f.wnum8, ls = (-0.02).em), maxLines = 1)
    }
}

/** Doto's dotted B reads as 8: the leading letters fall back to Inter Tight at .86 (mockup .bs.kA .fa). */
private fun fsplit(t: String, f: Faces, size: TextUnit): AnnotatedString = buildAnnotatedString {
    val n = t.takeWhile { it.isLetter() && it.code < 128 }.length
    if (f.doto && n > 0) {
        withStyle(SpanStyle(fontFamily = f.gro9, fontSize = size * 0.86f)) { append(t.take(n)) }
        append(t.drop(n))
    } else append(t)
}

@Composable
private fun Saved(m: ListenModel, s: Sheet.Saved, f: Faces, acts: SheetActions) {
    var left by remember(s.park.id) { mutableLongStateOf(ListenModel.UNDO_MS) }
    LaunchedEffect(s.park.id) {
        while (left > 0) { delay(250); left -= 250; m.refreshSent() }
        m.timeout(s.park.id)
    }
    Body {
        Header {
            Ic(R.drawable.ic_check, 20.dp, OK)
            Text("저장했어요", style = ts(17.sp, FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            val cars = m.cars
            if (cars.size >= 2) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                cars.forEach { c ->
                    val on = c.id == s.park.carId
                    Box(Modifier.heightIn(min = 36.dp).clip(CircleShape).background(if (on) FG else SF).clickable { m.switchCar(c.id) }.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text(c.name, style = ts(13.sp, if (on) FontWeight.SemiBold else FontWeight.Normal, if (on) SF else MU))
                    }
                }
            }
        }
        BigSpot(s.park.spot, f)
        s.park.spot.memo?.let { memo ->
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(BG).padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.ic_note, 16.dp, MU)
                Text(memo, style = ts(13.5.sp))
            }
        }
        s.sent?.let { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (line == ListenModel.SENT) Ic(R.drawable.ic_check_s, 16.dp, OK)
                Text(line, style = ts(14.sp, color = if (line == ListenModel.SENT) FG else MU))
            }
        }
        if (left > 0 && !m.held) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(SF).clickable(onClick = m::undo).padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("되돌리기", Modifier.weight(1f), style = ts(14.5.sp, FontWeight.Bold))
                Text("${(left + 999) / 1000}초", style = ts(13.5.sp, color = MU))
            }
            Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(LN)) {
                Box(Modifier.fillMaxWidth(left / ListenModel.UNDO_MS.toFloat()).fillMaxHeight().background(f.acc))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActButton("고치기", R.drawable.ic_pen, 17.dp, m::fix, Modifier.weight(1f))
            ActButton(if (s.photo) "사진 다시" else "사진", R.drawable.ic_cam, 18.dp, { m.hold(); acts.photo() }, Modifier.weight(1f))
            ActButton("확인", R.drawable.ic_check_s, 17.dp, m.close, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ActButton(text: String, @DrawableRes icon: Int, iconSize: Dp, onClick: () -> Unit, modifier: Modifier) {
    Row(modifier.height(48.dp).shadow(1.dp, CircleShape).clip(CircleShape).background(SF).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        Ic(icon, iconSize, FG)
        Text(text, style = ts(14.5.sp, FontWeight.SemiBold))
    }
}

// 4. ambiguous

@Composable
private fun Ambiguous(m: ListenModel, s: Sheet.Ambiguous, f: Faces) = Body(top = 12.dp, bottom = 22.dp) {
    Text("\"${s.raw}\"", style = ts(14.sp, color = MU))
    Text(if (s.ambiguity == Ambiguity.FLOOR_SIGN) "지하예요, 지상이에요?" else "구역이 어느 쪽이에요?", style = ts(18.sp, FontWeight.Bold))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        s.options.forEach { o ->
            Box(
                Modifier.weight(1f).shadow(2.dp, RoundedCornerShape(22.dp)).clip(RoundedCornerShape(22.dp)).background(SF)
                    .then(if (o == s.primary) Modifier.border(2.dp, FG, RoundedCornerShape(22.dp)) else Modifier)
                    .clickable { m.pick(o) }.padding(vertical = 14.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) { Text(o.short(), style = ts(26.sp, FontWeight.Black, family = f.gro9), maxLines = 1, textAlign = TextAlign.Center) }
        }
    }
    Text("한 번 고르면 이 주차장에선 다시 묻지 않아요", style = ts(13.sp, color = MU))
    BigButton("다시 말하기", m::again, primary = false)
}

// 3. manual entry (two failures, confirm, edit, no mic, no language pack)

private val FLOORS: List<Floor> = (7 downTo 1).map { Floor.Under(it) } + (1..9).map { Floor.Above(it) } + Floor.Roof
private val ZONES: List<String> = ('A'..'Z').map { it.toString() } + "가나다라마바사아자차카타파하".map { it.toString() }

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun Manual(m: ListenModel, s: Sheet.Manual, f: Faces, acts: SheetActions) {
    var floor by remember(s) { mutableStateOf(s.prefill?.floor) }
    var zone by remember(s) { mutableStateOf(s.prefill?.zone) }
    var num by remember(s) { mutableStateOf(s.prefill?.number?.toString().orEmpty()) }
    var memo by remember(s) { mutableStateOf(s.prefill?.memo.orEmpty()) }
    val spot = Spot(floor, zone, num.toIntOrNull(), s.prefill?.pillar, memo.trim().ifEmpty { null })
    val (title, sub) = when (s.why) {
        ManualWhy.FAILED -> "잘 안 들렸어요" to "골라서 넣어 주세요"
        ManualWhy.CONFIRM -> "이렇게 저장할까요?" to "맞는지 확인해 주세요"
        ManualWhy.EDIT -> "고치기" to "바꿀 곳을 골라 주세요"
        ManualWhy.NO_MIC -> "마이크가 꺼져 있어요" to "골라서 넣어 주세요"
        ManualWhy.NO_PACK -> "음성팩이 없어요" to "골라서 넣어 주세요"
        ManualWhy.NO_RECOGNIZER -> "이 폰에선 말로 저장할 수 없어요" to "골라서 넣어 주세요"
    }
    Body(top = 10.dp, bottom = 18.dp, gap = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = ts(17.sp, FontWeight.Bold))
                Text(sub, style = ts(13.5.sp, color = MU))
            }
            when (s.why) {
                ManualWhy.NO_MIC -> Pill("설정에서 켜기", acts.openSettings, padH = 12.dp, size = 13.5.sp)
                ManualWhy.NO_RECOGNIZER, ManualWhy.NO_PACK -> {}
                else -> Pill("다시 말하기", m::again, padH = 12.dp, size = 13.5.sp, lead = { Ic(R.drawable.ic_mic, 16.dp, ACC) })
            }
        }
        if (s.why == ManualWhy.NO_PACK) {
            if (s.downloading) Text("다 받으면 다시 열어 주세요", style = ts(13.5.sp, color = MU))
            else Pill("Wi-Fi에서 음성팩 받기", acts.downloadPack, size = 13.5.sp, modifier = Modifier.fillMaxWidth())
        }
        if (s.floors.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("이 차가 썼던 층", style = ts(12.5.sp, color = MU))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.floors.forEach { fl ->
                    val on = fl == floor
                    Box(Modifier.heightIn(min = 44.dp).clip(CircleShape).background(if (on) FG else SF).clickable { floor = fl }.padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center) { Text(fl.label(), style = ts(13.sp, color = if (on) SF else FG)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Dropdown("층", floor?.label() ?: "-", listOf<Floor?>(null) + FLOORS, { it?.label() ?: "없음" }, { floor = it }, f, Modifier.weight(1f))
            Dropdown("구역", zone ?: "-", listOf<String?>(null) + ZONES, { it ?: "없음" }, { zone = it }, f, Modifier.weight(1f))
            NumberField(num, { v -> num = v.filter(Char::isDigit).take(3).trimStart('0') }, s.why == ManualWhy.FAILED, f, Modifier.weight(1f))
        }
        BasicTextField(
            memo, { memo = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true, textStyle = ts(14.sp), cursorBrush = SolidColor(FG),
            decorationBox = { inner ->
                Row(Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(16.dp)).background(SF).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Ic(R.drawable.ic_note, 16.dp, MU)
                    Box(Modifier.weight(1f)) { if (memo.isEmpty()) Text("메모 (엘리베이터 옆)", style = ts(14.sp, color = MU)); inner() }
                }
            },
        )
        val any = spot.floor != null || spot.zone != null || spot.number != null
        BigButton(if (m.practice) "확인" else if (any) "${spot.short()} 저장" else "저장", { m.saveManual(spot) }, primary = true, enabled = any || m.practice)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(label: String, value: String, items: List<T>, name: (T) -> String, pick: (T) -> Unit, f: Faces, modifier: Modifier) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(open, { open = it }, modifier) {
        Fx(label, Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable), focused = false) {
            Text(value, Modifier.weight(1f), style = ts(20.sp, FontWeight.ExtraBold, family = f.gro8, ls = (-0.01).em), maxLines = 1)
            Ic(R.drawable.ic_chev, 18.dp, MU)
        }
        ExposedDropdownMenu(open, { open = false }, containerColor = SF) {
            items.forEach { v -> DropdownMenuItem({ Text(name(v), style = ts(16.sp)) }, { pick(v); open = false }) }
        }
    }
}

@Composable
private fun NumberField(value: String, change: (String) -> Unit, autoFocus: Boolean, f: Faces, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val fr = remember { FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    BasicTextField(
        value, change, modifier.focusRequester(fr).onFocusChanged { focused = it.isFocused }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = ts(20.sp, FontWeight.ExtraBold, family = f.gro8), cursorBrush = SolidColor(FG),
        decorationBox = { inner -> Fx("번호", Modifier, focused) { Box(Modifier.weight(1f)) { inner() } } },
    )
}

/** Mockup .fx: 60 dp field, small label over a big value; focused = 2 dp ink outline. */
@Composable
private fun Fx(label: String, modifier: Modifier, focused: Boolean, value: @Composable RowScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().height(60.dp).shadow(1.dp, RoundedCornerShape(16.dp)).clip(RoundedCornerShape(16.dp)).background(SF)
            .then(if (focused) Modifier.border(2.dp, FG, RoundedCornerShape(16.dp)) else Modifier).padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Text(label, style = ts(12.sp, color = MU))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, content = value)
    }
}

// practice (onboarding step 3): shows what was understood, never saves

@Composable
private fun Practice(m: ListenModel, s: Sheet.Practice, f: Faces) = Body {
    Header {
        Text("연습이에요 · 저장하지 않아요", style = ts(14.sp, color = MU))
        Spacer(Modifier.weight(1f))
        Pill("닫기", m.close)
    }
    if (s.raw.isNotEmpty()) Text("\"${s.raw}\"", style = ts(14.sp, color = MU))
    val spot = s.result.spot
    if (spot != null) {
        BigSpot(spot, f)
        Text("이렇게 말하면 돼요", style = ts(14.sp))
    } else {
        Text("잘 안 들렸어요", style = ts(22.sp, FontWeight.Bold))
        Text("\"지하 2층 C37\"처럼 층과 칸을 말해 보세요", style = ts(14.sp, color = MU))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ActButton("다시 말하기", R.drawable.ic_mic, 16.dp, m::again, Modifier.weight(1f))
        Box(Modifier.weight(1f).height(48.dp).clip(CircleShape).background(ACC).clickable(onClick = m.close), contentAlignment = Alignment.Center) {
            Text("확인", style = ts(14.5.sp, FontWeight.SemiBold, ON_ACC))
        }
    }
}
