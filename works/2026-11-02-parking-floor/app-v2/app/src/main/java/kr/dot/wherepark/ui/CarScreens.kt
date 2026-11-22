package kr.dot.wherepark.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.io.File
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState

/** 차 상세 (mockup section 5): spot with [고치기], who/when/elapsed, memo, photo, plate + photo buttons, 내 기록, [가져갔어요]. */
class DetailActions(
    val back: () -> Unit = {}, val edit: () -> Unit = {}, val fix: () -> Unit = {}, val speak: () -> Unit = {}, val plate: () -> Unit = {},
    val photo: () -> Unit = {}, val history: () -> Unit = {}, val took: () -> Unit = {},
)

@Composable
fun DetailScreen(car: Car, park: Park?, photo: File?, now: Long, a: DetailActions, leftAt: Long? = null) =
    WScreen(car.name, onBack = a.back, action = { RoundIcon("gear", "차 편집", 19.dp, a.edit) }) {
        WCard {
            if (park == null) {
                SpotLine("–", "", color = Wp.MU, trailing = { EditPill("말하기", "mic", a.speak) })
                Text("아직 기록이 없어요", style = ts(14.5.sp, color = Wp.MU))
            } else {
                val (x, y) = park.spot.big()
                Box(Modifier.semantics(mergeDescendants = true) { role = Role.Button; contentDescription = "층과 칸 고치기" }.clickable(onClick = a.fix)) {
                    SpotLine(x, y, trailing = { EditPill("고치기", "pen", a.fix) })
                }
                WhoLine(park.byName.ifBlank { null }, HomeCards.whenFull(park.atMs, now) + if (park.state == ParkState.DRIVING) (if (leftAt != null) " · ${HomeCards.hm(leftAt)} 내림 · 어디 댔는지 말해 주세요" else " · 운행 중") else " · " + HomeCards.ago(park.atMs, now))
                park.spot.memo?.let { MemoLine("note", it) }
                if (photo != null) {
                    val bmp by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, photo.path, photo.lastModified()) {
                        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
                        }
                    }
                    bmp?.let { b -> Image(b, "주차 사진", Modifier.padding(top = 10.dp).fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(if (car.plate.isNullOrBlank()) "차량번호 넣기" else car.plate!!, BtnKind.NT, Modifier.weight(1f), onClick = if (car.plate.isNullOrBlank()) a.edit else a.plate)
            if (park != null) Btn(if (photo != null) "사진 바꾸기" else "사진 넣기", BtnKind.NT, Modifier.weight(1f), onClick = a.photo)
        }
        WList { Li("내 기록", last = true, right = "나만 보여요 ›", onClick = a.history) }
        Spacer(Modifier.weight(1f))
        if (park != null) {
            val driving = park.state == ParkState.DRIVING
            if (driving && leftAt != null) Btn("위치 말하기", BtnKind.GHOST, onClick = a.speak)
            else Btn(if (driving) "운행 중이에요" else "가져갔어요", BtnKind.GHOST, enabled = !driving, onClick = a.took)
        }
    }

/** Mockup .spot .edit: 44 dp pill on the page colour. */
@Composable
private fun EditPill(text: String, icon: String, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 44.dp).clip(CircleShape).background(Wp.BG).clickable(onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Ic(icon, 15.dp, Wp.MU)
        Text(text, style = ts(13.sp, color = Wp.MU))
    }
}

/** "12가 3456" -> "3456" (most garage kiosks take the last four digits). */
fun plateTail(plate: String): String = plate.filter(Char::isDigit).takeLast(4)

/** 차량번호 크게 (mockup section 5): white page, plate box, last four digits huge, [번호 복사]. The Activity keeps the screen on; system back leaves. */
@Composable
fun PlateScreen(car: Car, copied: Boolean, copy: () -> Unit) {
    val plate = car.plate.orEmpty()
    WScreen(null, bg = Color.White, gap = 18.dp) {
        Spacer(Modifier.weight(1f))
        Text("${car.name} · 기계에 이 번호를 넣으세요", Modifier.fillMaxWidth(), style = ts(15.sp, color = Color(0xFF555555)), textAlign = TextAlign.Center)
        Text(plate, Modifier.fillMaxWidth().border(3.dp, Color(0xFF111111), RoundedCornerShape(12.dp)).padding(vertical = 10.dp),
            style = ts(46.sp, FontWeight.ExtraBold, Color(0xFF111111), family = gro(800), ls = .02.em), textAlign = TextAlign.Center, maxLines = 1)
        Text(plateTail(plate), Modifier.fillMaxWidth().trimLine(99.sp), style = ts(110.sp, FontWeight.Black, lh = .9f, family = gro(900), ls = (-0.03).em), textAlign = TextAlign.Center, maxLines = 1)
        Text("뒤 네 자리만 넣는 기계가 많아요", Modifier.fillMaxWidth(), style = ts(14.sp, color = Color(0xFF555555)), textAlign = TextAlign.Center)
        Box(Modifier.fillMaxWidth().height(52.dp).clip(CircleShape).background(Color(0xFF111111)).clickable(onClick = copy), Alignment.Center) {
            Text(if (copied) "복사했어요" else "번호 복사", style = ts(15.5.sp, FontWeight.SemiBold, Color.White))
        }
        Text("화면이 꺼지지 않게 밝게 켜 둘게요", Modifier.fillMaxWidth(), style = ts(13.sp, color = Color(0xFF777777)), textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
    }
}

/** 내 기록: this phone's last 50 records of the car (never sent anywhere as a list). */
@Composable
fun HistoryScreen(car: Car, history: List<Park>, now: Long, back: () -> Unit) = WScreen("내 기록", onBack = back) {
    Text("${car.name} · 이 폰에만 남아요", style = ts(13.5.sp, color = Wp.MU))
    if (history.isEmpty()) Text("아직 기록이 없어요", style = ts(14.5.sp, color = Wp.MU))
    else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
        WList {
            history.forEachIndexed { i, p ->
                Li(p.spot.short(), last = i == history.lastIndex, sub = listOfNotNull(p.byName.ifBlank { null }, HomeCards.whenFull(p.atMs, now), p.spot.memo).joinToString(" · "))
            }
        }
    }
}

/** 차 편집 (mockup section 7): nickname, six colours, plate, this car's Bluetooth, delete. Saved when leaving. */
class EditActions(val back: () -> Unit = {}, val link: () -> Unit = {}, val unlink: () -> Unit = {}, val delete: () -> Unit = {})

class CarDraft(car: Car) {
    var name by mutableStateOf(car.name)
    var color by mutableStateOf(car.color)
    var plate by mutableStateOf(car.plate.orEmpty())
}

@Composable
fun CarEditScreen(draft: CarDraft, isNew: Boolean, linked: Boolean, a: EditActions) {
    var confirm by remember { mutableStateOf(false) }
    WScreen(if (isNew) "차 추가" else draft.name.ifBlank { "차" }, onBack = a.back) {
        FieldCard("별명", draft.name, { draft.name = it.take(10) }, placeholder = "흰 차", underline = false)
        WCard {
            Text("색", Modifier.padding(bottom = 8.dp), style = ts(13.sp, color = Wp.MU))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Wp.CAR_COLORS.forEach { c ->
                    val on = c == draft.color
                    Box(Modifier.weight(1f).aspectRatio(1f).semantics { selected = on; role = Role.RadioButton; contentDescription = "차 색" }
                        .clickable { draft.color = c }, Alignment.Center) {
                        if (on) Box(Modifier.fillMaxSize().border(2.dp, Wp.FG, CircleShape))
                        Box(Modifier.fillMaxSize().padding(if (on) 4.dp else 0.dp).clip(CircleShape).background(Color(c)).border(1.dp, Color(0x33000000), CircleShape))
                    }
                }
            }
        }
        FieldCard("차량번호 (선택)", draft.plate, { draft.plate = it.take(12) }, placeholder = "12가 3456", underline = false,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Text))
        WCard {
            Text("이 차의 블루투스", style = ts(13.sp, color = Wp.MU))
            Text(if (linked) "연결됨" else "연결 안 함", style = ts(17.sp, FontWeight.SemiBold))
            Text("끊기면 \"어디에 댔어요?\" 알림을 보내고, 다시 연결되면 \"운행 중\"으로 바꿔요", style = ts(12.5.sp, color = Wp.MU))
            Box(Modifier.padding(top = 8.dp)) {
                if (linked) Btn("연결 끊기", BtnKind.GHOST, h = 44.dp, size = 14.sp, onClick = a.unlink)
                else Btn("차 블루투스 연결", BtnKind.NT, h = 44.dp, size = 14.sp, lead = { Ic("bt", 18.dp) }, onClick = a.link)
            }
        }
        Spacer(Modifier.weight(1f))
        if (!isNew) Btn("이 차 지우기", BtnKind.GHOST, fg = Wp.DANGER, onClick = { confirm = true })
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        text = { Text("${draft.name}를 지울까요? 가족 폰에서도 사라져요.") },
        confirmButton = { TextButton({ confirm = false; a.delete() }) { Text("지우기", color = Wp.DANGER) } },
        dismissButton = { TextButton({ confirm = false }) { Text("취소", color = Wp.FG) } },
        containerColor = Wp.SF,
    )
}
