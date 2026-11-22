package kr.dot.wherepark.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.GroupStore
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.data.SyncQueue
import kr.dot.wherepark.family.Referrer
import kr.dot.wherepark.widget.WidgetSpecs
import kr.dot.wherepark.widget.preview

sealed interface Route {
    data class Join(val code: String) : Route
    /** [joined] = came in through an invite: the car step is skipped (the family's cars arrive with the join). */
    data class Steps(val joined: Boolean) : Route
    data object Home : Route
}

/** First run (spec 10). Step 0 is invisible: an invite code (Install Referrer or link) goes straight to joining. */
object Onboarding {
    fun done(ctx: Context): Boolean = Prefs.sp(ctx).getString("wp_onboarded", null) != null
    fun finish(ctx: Context) = Prefs.put(ctx, "wp_onboarded", "1")

    fun route(ctx: Context, linkCode: String?): Route {
        if (done(ctx)) return Route.Home
        val joined = GroupStore.joined(ctx)
        val code = linkCode ?: Referrer.pendingCode(ctx)
        return if (!joined && code != null) Route.Join(code) else Route.Steps(joined)
    }

    /** Car step end: stored, queued, and shared into every group I already belong to (nothing is created otherwise). */
    fun saveCar(ctx: Context, car: Car) { CarStore.addMine(ctx, car) }
}

/** Car step inputs; the car id exists before saving so the Bluetooth link can be stored against it. */
class CarStep(val carId: String) {
    var car by mutableStateOf("")
    var plate by mutableStateOf("")
    var linked by mutableStateOf(false)
    var packDownloading by mutableStateOf(false)
    val ready get() = car.isNotBlank()
}

/** Practice step: what the practice sheet reported. [pack] null = not checked yet. */
data class Practice(val mic: Boolean, val pack: Boolean?, val raw: String?, val spot: Spot?)

@Composable
private fun Head(text: String) = Text(text, Modifier.trimLine(31.2.sp), style = ts(26.sp, FontWeight.ExtraBold, lh = 1.2f, family = gro(800), ls = (-0.02).em))

@Composable
private fun Para(text: String, modifier: Modifier = Modifier, center: Boolean = false) =
    Text(text, modifier, style = ts(15.sp, color = Wp.MU), textAlign = if (center) TextAlign.Center else TextAlign.Start)

@Composable
private fun Caption(text: String, color: Color = Wp.MU, size: Float = 13f) =
    Text(text, Modifier.fillMaxWidth(), style = ts(size.sp, color = color), textAlign = TextAlign.Center)

@Composable
fun Step1(start: () -> Unit) = WScreen(null, gap = 18.dp) {
    Steps(1)
    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Brand(120.dp, 32.dp)
            Text("차를 대고\n말로 남기면", Modifier.padding(top = 22.dp).trimLine(31.2.sp, 2), style = ts(26.sp, FontWeight.ExtraBold, lh = 1.2f, family = gro(800), ls = (-0.02).em), textAlign = TextAlign.Center)
            Para("그룹 폰 위젯에도 바로 떠요.\n\"지하 2층 C37\" 한마디면 돼요.", Modifier.padding(top = 10.dp), center = true)
        }
    }
    Btn("시작하기", BtnKind.ACC, onClick = start)
    Caption("차에서 내리면 물어볼 수 있게 알림을 허용해 주세요", size = 12.5f)
}

@Composable
fun StepCar(s: CarStep, link: () -> Unit, skip: () -> Unit, next: () -> Unit) = WScreen(null, gap = 14.dp) {
    Steps(3)
    Head("어느 차예요?")
    FieldCard("별명", s.car, { s.car = it.take(10) }, placeholder = "흰 차")
    FieldCard("차량번호 (선택)", s.plate, { s.plate = it.take(12) }, placeholder = "12가 3456", underline = false)
    WCard(onClick = link) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Wp.BG), Alignment.Center) { Ic(if (s.linked) "check" else "bt", 22.dp, if (s.linked) Wp.OK else Wp.FG) }
            Column {
                Text(if (s.linked) "차 블루투스 연결됨" else "차 블루투스 연결", style = ts(15.sp, FontWeight.SemiBold))
                Text("내리면 \"어디에 댔어요?\" 하고 물어봐요", style = ts(13.sp, color = Wp.MU))
            }
        }
    }
    if (s.packDownloading) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Wp.OK))
        Text("지하에서도 알아듣게 음성팩 받는 중 · Wi-Fi", style = ts(13.sp, color = Wp.OK))
    }
    Spacer(Modifier.weight(1f))
    Btn("다음", BtnKind.ACC, enabled = s.ready, onClick = next)
    Btn("다음에 하기", BtnKind.GHOST, onClick = skip)
    Caption("차는 나중에 더 넣을 수 있어요")
}

@Composable
private fun OkRow(ok: Boolean?, text: String, right: String, last: Boolean) =
    Li(text, last = last, right = right, lead = {
        if (ok == true) Ic("check", 18.dp, Wp.OK, stroke = 2.4f) else Box(Modifier.size(18.dp), Alignment.Center) { Box(Modifier.size(8.dp, 2.dp).background(Wp.MU)) }
    })

@Composable
fun StepPractice(p: Practice, speak: () -> Unit, pin: () -> Unit, next: () -> Unit) = WScreen(null, gap = 12.dp) {
    Steps(4)
    Head("한번 말해 보세요")
    Para("주차장에 가기 전에 여기서 한 번만요. \"지하 2층 C37\"처럼 말하면 돼요.")
    WList {
        OkRow(p.mic, "마이크 권한", if (p.mic) "허용됨" else "아직이에요", false)
        OkRow(p.pack, "한국어 음성팩", when (p.pack) { true -> "받았어요"; false -> "없어요"; null -> "확인 전" }, true)
    }
    if (p.raw != null) WCard {
        Text("이렇게 들렸어요 · \"${p.raw}\"", style = ts(13.sp, color = Wp.MU))
        val sp = p.spot
        if (sp != null) {
            val (a, b) = sp.big()
            SpotLine(a, b)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Ic("check", 16.dp, Wp.OK, stroke = 2.4f)
                Text("잘 들려요. 지하에서도 이렇게 돼요", style = ts(14.sp, FontWeight.SemiBold, Wp.OK))
            }
        } else Text("잘 못 알아들었어요. 층과 칸을 또박또박 말해 보세요", style = ts(14.sp, FontWeight.SemiBold))
    }
    Text("말소리는 폰 안에서 글자로 바꿔요. 음성팩을 못 받았거나 잘 안 들리면 층·구역을 손으로 골라 넣을 수 있어요.", style = ts(13.sp, color = Wp.MU))
    Spacer(Modifier.weight(1f))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(60.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(Wp.ACC).clickable(onClick = speak)
            .semantics { contentDescription = "말해 보기" }, Alignment.Center) { Ic("micw", 24.dp, Color.White) }
        Text(if (p.raw == null) "말해 보기" else "다시 말해 보기", style = ts(13.5.sp, color = Wp.MU))
    }
    Btn("위젯 놓고 끝내기", BtnKind.ACC, onClick = pin)
    Btn("끝내기", BtnKind.GHOST, onClick = next)
}

private val SKY = Brush.linearGradient(listOf(Color(0xFFA9CDE6), Color(0xFFCFE3F2), Color(0xFFE7D2B4), Color(0xFFD8A978)))

/** "같이 볼까요?": nothing is created unless a button is used; [나중에] (or [다음] once in a group) moves on. */
@Composable
fun StepGroups(joined: Boolean, create: () -> Unit, join: () -> Unit, ask: () -> Unit, next: () -> Unit) = WScreen(null, gap = 14.dp) {
    Steps(2)
    Head("같이 볼까요?")
    Para("누가 차를 댔든 내 위젯에 떠요.")
    Spacer(Modifier.weight(1f))
    Btn("그룹 만들기", BtnKind.ACC, onClick = create)
    Btn("초대 코드로 들어가기", BtnKind.GHOST, onClick = join)
    Btn("그룹에게 요청하기", BtnKind.GHOST, onClick = ask)
    Btn(if (joined) "다음" else "나중에", BtnKind.GHOST, onClick = next)
}
