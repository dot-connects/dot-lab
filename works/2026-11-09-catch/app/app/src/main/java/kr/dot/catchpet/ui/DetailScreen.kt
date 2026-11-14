package kr.dot.catchpet.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
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

/** One entry (mockup A): its poses; critters can be the pet; real things get drawings and (unlocked) a pose sheet. */
@Composable
fun DetailScreen(id: String, onPet: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rev = AiWork.rev
    val e = remember(id, rev) { Dex.get(ctx, id) }
    BookPage {
        Row { InkPill("‹ 도감", onClick = onBack) }
        if (e == null) {
            Text("이 녀석을 찾지 못했어요.", fontSize = 17.sp)
            return@BookPage
        }
        var renaming by remember { mutableStateOf(false) }
        var shown by remember(e.id, e.name) { mutableStateOf(e.name) }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Heading(shown, 32.sp)
                if (e.critterId == null) InkPill("이름 바꾸기", onClick = { renaming = true })
            }
            Note("${if (e.critterId != null) "털뭉치" else Dex.kindName(e.kind)} · No.${e.no} · ${SimpleDateFormat("yyyy.M.d", Locale.KOREA).format(Date(e.caughtAt))} 뽑음")
        }
        if (renaming) RenameDialog(shown, onDone = { n -> renaming = false; if (n != null) Dex.rename(ctx, e.id, n)?.let { shown = it.name } })
        Panel("모습 ${e.poses.size}") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (p in e.poses) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.width(96.dp).height(118.dp).background(Book.Field, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        rememberPose(e, p)?.let { Image(it, p, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit) }
                    }
                    Note(POSE_NAMES[p] ?: p, Modifier.padding(top = 4.dp))
                }
            }
        }
        if (e.critterId != null || e.ai.owned) PetButton(ctx, e, onPet)
        if (e.critterId == null) RealAi(ctx, e)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PetButton(ctx: Context, e: Dex.Entry, onPet: () -> Unit) {
    var current by remember(e.id) { mutableStateOf(petId(ctx) == e.id) }
    if (current) Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("지금 펫이에요", fontFamily = Book.Display, fontSize = 17.sp, modifier = Modifier.weight(1f))
            InkPill("펫 설정 열기", Book.Sun, onClick = onPet)
        }
    } else InkButton("펫으로 쓰기") { setPet(ctx, e.id); current = true }
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
    Panel("그림으로 그려 보기") {
        Seg(STYLES.map { (k, label) -> label to k }, style) { style = it }
        Note("오늘 ${left}번 더 그릴 수 있어요")
        InkButton(if (left > 0) "그려 보기" else "내일 다시 그릴 수 있어요", enabled = busy == null && left > 0, primary = false) {
            guarded { AiWork.preview(ctx, e, style) }
        }
        if (busy != null) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), color = Book.Ink, strokeWidth = 2.dp)
            Note(if (busy == "sheet") "  펫 그림을 그리는 중이에요 (1~3분)" else "  그리는 중이에요 (1분쯤)")
        }
        AiWork.message[e.id]?.let { Note(it, color = Book.Warn) }
        if (e.ai.previews.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (p in e.ai.previews.asReversed()) rememberFile(e.id, p)?.let {
                Image(it, "그려 본 그림", Modifier.size(140.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, Book.Ink, RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop)
            }
        }
    }
    if (e.ai.owned) {
        e.ai.sheet?.let { s -> rememberFile(e.id, s)?.let {
            Panel("펫 그림") {
                Image(it, "펫 그림", Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.FillWidth)
            }
        } }
        return
    }
    val token = remember { Purchase.token(ctx) }
    val locked = Purchase.locked(token)
    val free = kr.dot.catchpet.BuildConfig.DEBUG_PURCHASE.isNotEmpty()
    InkButton(if (free) "펫 그림 만들기 · 개발판 무료" else "펫으로 사기 · ${Purchase.PRICE_WON}원", enabled = !locked && busy == null) {
        token?.let { t -> guarded { AiWork.buy(ctx, e, t) } }
    }
    if (locked) Note("출시 후 열려요")
}

@Composable
private fun RenameDialog(old: String, onDone: (String?) -> Unit) {
    var text by remember { mutableStateOf(old) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onDone(null) },
        containerColor = Book.Paper,
        title = { Text("이름 바꾸기", fontFamily = Book.Display, color = Book.Ink) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = text, onValueChange = { text = it.take(12) }, singleLine = true,
                supportingText = { Text("${text.length}/12") },
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Book.Ink, unfocusedBorderColor = Book.Muted, cursorColor = Book.Ink,
                    focusedTextColor = Book.Ink, unfocusedTextColor = Book.Ink),
            )
        },
        confirmButton = { InkPill("바꾸기", Book.Sun, onClick = { onDone(text) }) },
        dismissButton = { InkPill("그대로 두기", onClick = { onDone(null) }) },
    )
}
