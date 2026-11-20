package kr.dot.wherepark.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.CarResolver
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.data.CarLinkStore
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.data.SyncQueue

enum class CardKind { NONE, PENDING, OK, DRIVING, OLD }
enum class CardLine { MEMO, PENDING, BT, NOMIC }

/** One home card's content (spec 8.3 "앱 홈 카드" column). */
data class CardUi(val kind: CardKind, val tag: String?, val who: String?, val whenText: String?, val line: CardLine?, val lineText: String?)

object HomeCards {
    private val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
    const val EMPTY = "아직 기록이 없어요. 마이크를 눌러 말해 보세요"
    const val NOMIC = "마이크 권한 · 음성팩이 필요해요"

    fun days(atMs: Long, nowMs: Long): Long =
        ChronoUnit.DAYS.between(Instant.ofEpochMilli(atMs).atZone(ZONE).toLocalDate(), Instant.ofEpochMilli(nowMs).atZone(ZONE).toLocalDate())

    /** "오늘 14:32" · "어제 19:05" · "11월 16일 14:32". */
    fun whenFull(atMs: Long, nowMs: Long): String {
        val at = Instant.ofEpochMilli(atMs).atZone(ZONE)
        val hm = "%d:%02d".format(at.hour, at.minute)
        return when (days(atMs, nowMs)) {
            0L -> "오늘 $hm"
            1L -> "어제 $hm"
            else -> "${at.monthValue}월 ${at.dayOfMonth}일 $hm"
        }
    }

    /** "2시간 4분째"; the app and detail only (widgets never show elapsed time). */
    fun ago(atMs: Long, nowMs: Long): String {
        val m = ((nowMs - atMs) / 60_000).coerceAtLeast(0)
        return when {
            m < 1 -> "방금"
            m < 60 -> "${m}분째"
            m < 1440 -> if (m % 60 == 0L) "${m / 60}시간째" else "${m / 60}시간 ${m % 60}분째"
            else -> "${m / 1440}일째"
        }
    }

    /** Order: no record, driving, waiting to send, 2+ calendar days old, normal. The line under it: pending, bluetooth, mic, memo. */
    fun of(park: Park?, pending: Boolean, micReady: Boolean, linked: Boolean, nowMs: Long): CardUi {
        val nomic = if (micReady) null else CardLine.NOMIC
        if (park == null) return CardUi(CardKind.NONE, null, null, null, nomic, if (nomic != null) NOMIC else null)
        val kind = when {
            park.state == ParkState.DRIVING -> CardKind.DRIVING
            pending -> CardKind.PENDING
            days(park.atMs, nowMs) >= 2 -> CardKind.OLD
            else -> CardKind.OK
        }
        val tag = when (kind) {
            CardKind.DRIVING -> listOf(park.drivingBy ?: park.byName).filter { it.isNotBlank() }.joinToString("") { "$it " } + "운행 중"
            CardKind.PENDING -> "전송 대기"
            CardKind.OLD -> "${days(park.atMs, nowMs)}일 전"
            else -> ago(park.atMs, nowMs)
        }
        val whenText = if (kind == CardKind.PENDING && nowMs - park.atMs < 60_000) "방금" else whenFull(park.atMs, nowMs)
        val (line, text) = when {
            kind == CardKind.PENDING -> CardLine.PENDING to "신호가 잡히면 가족에게 보내요"
            kind == CardKind.DRIVING && linked -> CardLine.BT to "블루투스가 다시 연결됐어요"
            nomic != null -> nomic to NOMIC
            park.spot.memo != null -> CardLine.MEMO to park.spot.memo
            else -> null to null
        }
        return CardUi(kind, tag, park.byName.ifBlank { null }, whenText, line, text)
    }
}

/** Everything the home screen shows, read from the stores in one go. */
data class HomeData(
    val cars: List<Car>, val parks: Map<String, Park?>, val pending: Set<String>, val linked: Set<String>,
    val micReady: Boolean, val now: Long, val removed: Boolean, val createError: String?, val sayTo: Car?,
) {
    companion object {
        fun load(ctx: Context, now: Long, micReady: Boolean): HomeData {
            val cars = CarStore.cars(ctx)
            val pend = if (FamilyStore.joined(ctx) || FamilyStore.pendingGroup(ctx) != null)
                SyncQueue.ops(ctx).filterIsInstance<Op.PutPark>().map { it.carId }.toSet() else emptySet()
            return HomeData(
                cars, cars.associate { it.id to ParkStore.latest(ctx, it.id) }, pend,
                cars.filter { CarLinkStore.address(ctx, it.id) != null }.map { it.id }.toSet(), micReady, now,
                FamilyStore.removed(ctx), FamilyStore.groupCreateError(ctx),
                CarResolver.resolve(null, null, null, cars, ParkStore.lastUsedCarId(ctx)),
            )
        }
    }
}

/** Callbacks the Activity runs (navigation, system screens). Defaults do nothing so screenshots can render screens alone. */
class AppActions(
    val speak: (String?) -> Unit = {},
    val openCar: (String) -> Unit = {},
    val plate: (String) -> Unit = {},
    val settings: () -> Unit = {},
    val fixMic: () -> Unit = {},
    val newFamily: () -> Unit = {},
    val joinByCode: () -> Unit = {},
    val retryGroup: () -> Unit = {},
)

/** Compose keeps a line as tall as the font; CSS line-height under 1 (.spot .f .95) trims it. */
fun Modifier.trimLine(lineHeight: TextUnit, lines: Int = 1) = layout { m, c ->
    val p = m.measure(c)
    val trim = ((p.height - lines * lineHeight.toPx()) / 2).roundToInt().coerceAtLeast(0)
    layout(p.width, p.height - 2 * trim) { p.place(0, -trim) }
}

/** Mockup .spot: floor 64/900 + rest 32/800 on one baseline (44/24 in the state grid). */
@Composable
fun SpotLine(a: String, b: String, big: Boolean = true, color: Color = Wp.FG, trailing: (@Composable () -> Unit)? = null) {
    val fs = if (big) 64.sp else 44.sp
    val ss = if (big) 32.sp else 24.sp
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(a, Modifier.alignByBaseline().trimLine(fs * .95f), style = ts(fs, FontWeight.Black, color, lh = .95f, family = gro(900), ls = (-0.03).em), maxLines = 1)
        if (b.isNotEmpty()) Text(b, Modifier.alignByBaseline(), style = ts(ss, FontWeight.ExtraBold, color, family = gro(800), ls = (-0.02).em), maxLines = 1)
        if (trailing != null) { Spacer(Modifier.weight(1f)); Box(Modifier.align(Alignment.CenterVertically)) { trailing() } }
    }
}

@Composable
fun WhoLine(who: String?, whenText: String?) = Text(buildAnnotatedString {
    if (who != null) { withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(who) }; append(" ") }
    withStyle(SpanStyle(color = Wp.MU)) { append(whenText.orEmpty()) }
}, style = ts(14.5.sp))

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeCard(car: Car, park: Park?, ui: CardUi, acts: AppActions, big: Boolean = true, chips: Boolean = true) {
    WCard(onClick = { acts.openCar(car.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Swatch(car.color)
            Text(car.name, Modifier.weight(1f), style = ts(13.5.sp, color = Wp.MU))
            ui.tag?.let { t -> Tag(t, if (ui.kind == CardKind.OK) Wp.OK else Wp.MU, icon = if (ui.kind == CardKind.PENDING) "up" else null) }
        }
        if (park == null) {
            SpotLine("–", "", big, Wp.MU)
            Text(HomeCards.EMPTY, style = ts(14.5.sp, color = Wp.MU))
        } else {
            val (a, b) = park.spot.big()
            Box(Modifier.alpha(if (ui.kind == CardKind.DRIVING) .4f else 1f)) { SpotLine(a, b, big) }
            WhoLine(ui.who, ui.whenText)
        }
        ui.line?.let { l ->
            val icon = when (l) { CardLine.PENDING -> "up"; CardLine.BT -> "bt"; CardLine.NOMIC -> "mic"; CardLine.MEMO -> "note" }
            MemoLine(icon, ui.lineText.orEmpty(), trail = if (l == CardLine.NOMIC) ({
                Text("켜기", Modifier.clickable(onClick = acts.fixMic).padding(horizontal = 4.dp), style = ts(13.5.sp, FontWeight.Bold))
            }) else null)
        }
        if (chips) FlowRow(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Chip(null, { acts.speak(car.id) }, label = "${car.name}에 말로 저장", icon = "mic")
            if (!car.plate.isNullOrBlank()) Chip(car.plate, { acts.plate(car.id) }, label = "차량번호 크게", icon = "car", iconTint = Wp.FG)
        }
    }
}

/** A top band (mockup has none; contracts section 3 asks for it): text + up to two actions. */
@Composable
fun Band(text: String, sub: String?, actions: List<Pair<String, () -> Unit>>) = WCard {
    Text(text, style = ts(15.sp, FontWeight.Bold))
    if (sub != null) Text(sub, style = ts(13.sp, color = Wp.MU))
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        actions.forEachIndexed { i, (t, f) -> Btn(t, if (i == 0) BtnKind.DARK else BtnKind.GHOST, Modifier.weight(1f), h = 44.dp, size = 13.5.sp, onClick = f) }
    }
}

/** Mockup section 3 "홈 (확정 · 카드 목록)": one card per car, a big orange mic, "흰 차에 저장" under it. */
@Composable
fun HomeScreen(d: HomeData, acts: AppActions) = WScreen(null, gap = 10.dp) {
    TopBar("어디댔어", null, action = { RoundIcon("gear", "설정", onClick = acts.settings) }, lead = { Brand(34.dp, 10.dp) })
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (d.removed) Band("가족에서 나가게 됐어요", "내 차와 기록은 이 폰에 남아 있어요", listOf("새 가족 만들기" to acts.newFamily, "초대 코드로 들어가기" to acts.joinByCode))
        else if (d.createError != null) Band("가족을 만들지 못했어요", "잠시 뒤 다시 해 주세요", listOf("다시 해 보기" to acts.retryGroup, "초대 코드로 들어가기" to acts.joinByCode))
        d.cars.forEach { c ->
            val p = d.parks[c.id]
            HomeCard(c, p, HomeCards.of(p, c.id in d.pending, d.micReady, c.id in d.linked, d.now), acts)
        }
    }
    val to = d.sayTo
    Box(
        Modifier.align(Alignment.CenterHorizontally).size(84.dp).shadow(5.dp, CircleShape, ambientColor = Wp.ACC, spotColor = Color(0x55000000))
            .clip(CircleShape).background(Wp.ACC).clickable { acts.speak(null) }
            .semantics { contentDescription = "${to?.name ?: "내 차"}에 말로 저장" },
        Alignment.Center,
    ) { Ic("micw", 30.dp, Color.White) }
    Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Swatch(to?.color ?: 0xFFF4F1EA.toInt())
        Text(buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(to?.name ?: "내 차") }
            append(" 에 저장")
        }, style = ts(13.5.sp))
    }
}
