package kr.dot.catchpet.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.catchpet.ai.AiWork
import kr.dot.catchpet.billing.Purchase
import kr.dot.catchpet.dex.Dex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val POSE_NAMES = mapOf("front" to "앞", "side" to "옆", "back" to "뒤")
private val STYLES = listOf("fluff" to "털뭉치", "figure" to "피규어")

/** The chosen pet: a dex entry id in SharedPreferences "catchpet" / "pet" (the pet overlay reads it). */
fun petId(ctx: Context): String? = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getString("pet", null)
fun setPet(ctx: Context, id: String) { ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).edit().putString("pet", id).apply() }

/** One entry: its poses; critters can be the pet; real things get AI previews and (unlocked) a bought pose sheet. */
@Composable
fun DetailScreen(id: String, onPet: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rev = AiWork.rev
    val e = remember(id, rev) { Dex.get(ctx, id) }
    Column(Modifier.fillMaxSize().background(BG).safeDrawingPadding().verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("‹ 도감", color = DIM, fontSize = 16.sp) }
        if (e == null) {
            Text("이 녀석을 찾지 못했어요.", color = DIM, fontSize = 17.sp, modifier = Modifier.padding(24.dp))
            return@Column
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(e.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("No.${e.no} · ${SimpleDateFormat("yyyy.M.d", Locale.KOREA).format(Date(e.caughtAt))} 뽑음", color = DIM, fontSize = 14.sp)
            Spacer(Modifier.height(20.dp))
            Section("모습 ${e.poses.size}")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (p in e.poses) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Capsule(Modifier.width(104.dp).height(140.dp)) {
                        rememberPose(e, p)?.let { Image(it, p, Modifier.fillMaxSize().padding(12.dp), contentScale = ContentScale.Fit) }
                    }
                    Text(POSE_NAMES[p] ?: p, color = DIM, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            if (e.critterId != null || e.ai.owned) PetButton(ctx, e, onPet)
            if (e.critterId == null) RealAi(ctx, e)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun PetButton(ctx: Context, e: Dex.Entry, onPet: () -> Unit) {
    var current by remember(e.id) { mutableStateOf(petId(ctx) == e.id) }
    if (current) {
        Text("지금 펫이에요", color = GOLD, fontSize = 16.sp)
        TextButton(onClick = onPet) { Text("펫 설정 열기", color = Color.White) }
    } else {
        Button(
            onClick = { setPet(ctx, e.id); current = true },
            colors = ButtonDefaults.buttonColors(containerColor = GOLD, contentColor = Color(0xFF2A1D00)),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("펫으로 쓰기", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    }
    Spacer(Modifier.height(20.dp))
}

@Composable
private fun RealAi(ctx: Context, e: Dex.Entry) {
    val busy = AiWork.busy[e.id]
    // a person's photo leaves the phone only after "허락받았어요" (asked once per dex entry)
    val prefs = remember { ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE) }
    var asking by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun guarded(go: () -> Unit) {
        if (e.kind != "person" || prefs.getBoolean("ai_ok_${e.id}", false)) go() else asking = go
    }
    asking?.let { go ->
        PersonConsent(PersonUse.AI) { ok ->
            asking = null
            if (ok) { prefs.edit().putBoolean("ai_ok_${e.id}", true).apply(); go() }
        }
    }
    val left = remember(AiWork.rev) { AiWork.leftToday(ctx) }
    var style by rememberSaveable { mutableStateOf("fluff") }
    Section("AI 미리보기")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((k, label) in STYLES) FilterChip(
            selected = style == k, onClick = { style = k }, label = { Text(label) },
            colors = FilterChipDefaults.filterChipColors(labelColor = DIM, selectedContainerColor = GOLD, selectedLabelColor = Color(0xFF2A1D00)),
        )
        Spacer(Modifier.width(4.dp))
        Text("오늘 ${left}번 남음", color = DIM, fontSize = 13.sp)
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { guarded { AiWork.preview(ctx, e, style) } }, enabled = busy == null && left > 0,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0x26FFFFFF), contentColor = Color.White,
            disabledContainerColor = Color(0x14FFFFFF), disabledContentColor = DIM),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) { Text(if (left > 0) "AI 미리보기 그리기" else "내일 다시 그릴 수 있어요") }
    if (busy != null) Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), color = GOLD, strokeWidth = 2.dp)
        Text(if (busy == "sheet") "  펫 그림을 그리는 중이에요 (1~3분)" else "  그리는 중이에요 (1분쯤)", color = DIM, fontSize = 14.sp)
    }
    AiWork.message[e.id]?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp)) }
    if (e.ai.previews.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (p in e.ai.previews.asReversed()) rememberFile(e.id, p)?.let {
                Image(it, "AI 미리보기", Modifier.size(150.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
            }
        }
    }
    Spacer(Modifier.height(28.dp))
    if (e.ai.owned) {
        e.ai.sheet?.let { s -> rememberFile(e.id, s)?.let {
            Section("펫 그림")
            Image(it, "펫 그림", Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.FillWidth)
        } }
        return
    }
    val token = remember { Purchase.token(ctx) }
    val locked = Purchase.locked(token)
    Button(
        onClick = { token?.let { t -> guarded { AiWork.buy(ctx, e, t) } } }, enabled = !locked && busy == null,
        colors = ButtonDefaults.buttonColors(containerColor = GOLD, contentColor = Color(0xFF2A1D00),
            disabledContainerColor = Color(0x14FFFFFF), disabledContentColor = DIM),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text("펫으로 사기 · ${Purchase.PRICE_WON}원", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
    if (locked) Text("출시 후 열려요", color = DIM, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
}
