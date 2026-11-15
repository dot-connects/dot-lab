package kr.dot.catchpet.ui

import androidx.compose.foundation.clickable
import kr.dot.catchpet.pet.PetPrefs
import kr.dot.catchpet.pet.PetService
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

/** One entry (confirmed mockup allmenus 11-15): poses, one preview per look, each look bought apart, pet, delete. */
@Composable
fun DetailScreen(id: String, onPet: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rev = AiWork.rev
    var tick by remember { mutableStateOf(0) }
    val e = remember(id, rev, tick) { Dex.get(ctx, id) }
    var deleting by remember { mutableStateOf(false) }
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
        if (e.critterId != null) CritterPet(ctx, e) { tick++ }
        else RealAi(ctx, e) { tick++ }
        Spacer(Modifier.height(8.dp))
        Text("도감에서 지우기", fontFamily = Book.Display, fontSize = 15.sp, color = Book.Warn,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50)).clickable { deleting = true }.padding(12.dp))
        Spacer(Modifier.height(16.dp))
    }
    if (deleting && e != null) DeleteDialog(listOf(e), onDone = { yes -> deleting = false; if (yes) { removeEntries(ctx, listOf(e.id)); onBack() } })
}

/** Makes [id] the pet and, when the pet is on screen, swaps it at once (operator 11-15: it did not change). */
internal fun usePet(ctx: Context, id: String) {
    setPet(ctx, id)
    if (PetPrefs.overlayOn(ctx)) PetService.start(ctx)
}

/** Deletes entries; the pet among them turns the floating pet off. */
internal fun removeEntries(ctx: Context, ids: List<String>) {
    val pet = petId(ctx)
    for (id in ids) Dex.delete(ctx, id)
    if (pet != null && pet in ids) {
        ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).edit().remove("pet").apply()
        PetService.stop(ctx)
    }
}

@Composable
internal fun DeleteDialog(es: List<Dex.Entry>, onDone: (Boolean) -> Unit) {
    val who = if (es.size == 1) Dex.withObject(es[0].name) else "${es.size}마리를"
    val bought = es.any { it.ai.owned }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onDone(false) },
        containerColor = Book.Paper,
        title = { Text("$who 지울까요?", fontFamily = Book.Display, color = Book.Ink) },
        text = {
            Text("사진과 그려 본 그림이 이 폰에서 지워져요." + (if (bought) " 산 펫 그림도 함께 지워지고" else "") + " 되돌릴 수 없어요.",
                fontFamily = Book.Body, color = Book.Ink)
        },
        confirmButton = { InkPill("지우기", Color(0xFFFF8A80), onClick = { onDone(true) }) },
        dismissButton = { InkPill("그대로 두기", onClick = { onDone(false) }) },
    )
}

@Composable
private fun CritterPet(ctx: Context, e: Dex.Entry, changed: () -> Unit) {
    if (petId(ctx) == e.id) Panel { Text("펫이에요", fontFamily = Book.Display, fontSize = 17.sp) }
    else InkButton("펫으로 쓰기") { usePet(ctx, e.id); changed() }
}

private val LOOKS = listOf("fluff" to "털뭉치", "figure" to "피규어")

@Composable
private fun RealAi(ctx: Context, e: Dex.Entry, changed: () -> Unit) {
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
    // one preview per look: drawn = shown and the button is done (operator 11-15)
    Panel("미리 그려 보기") {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((style, label) in LOOKS) {
                val drawn = e.ai.previewFor(style)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(112.dp).clip(RoundedCornerShape(14.dp)).background(Book.Field).border(2.dp, Book.Ink, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center) {
                        if (drawn != null) rememberFile(e.id, drawn, keyGreen = true)?.let { Image(it, label, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit) }
                        else if (busy == "preview-$style") CircularProgressIndicator(Modifier.size(22.dp), color = Book.Ink, strokeWidth = 2.dp)
                        else Text("?", fontFamily = Book.Display, fontSize = 28.sp, color = Book.Muted)
                    }
                    if (drawn != null) Note("$label 그렸어요")
                    else InkPill("$label 그려 보기", onClick = if (busy == null && left > 0) ({ guarded { AiWork.preview(ctx, e, style) } }) else null)
                }
            }
        }
        Note(if (left > 0) "스타일마다 한 번씩 그려 볼 수 있어요. 오늘 ${left}번 남았어요." else "오늘은 다 그렸어요. 내일 다시 그릴 수 있어요.")
        if (e.ai.oldPreviews.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in e.ai.oldPreviews.asReversed()) rememberFile(e.id, p, keyGreen = true)?.let {
                Image(it, "그려 본 그림", Modifier.size(72.dp), contentScale = ContentScale.Fit)
            }
        }
    }
    val token = remember { Purchase.token(ctx) }
    val locked = Purchase.locked(token)
    val free = kr.dot.catchpet.BuildConfig.DEBUG_PURCHASE.isNotEmpty()
    val isPet = petId(ctx) == e.id
    Panel("펫으로 사기") {
        for ((style, label) in LOOKS) {
            val bought = e.ai.sheets.containsKey(style)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(48.dp).background(Book.Field, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    e.ai.previewFor(style)?.let { p -> rememberFile(e.id, p, keyGreen = true)?.let { Image(it, label, Modifier.fillMaxSize().padding(4.dp), contentScale = ContentScale.Fit) } }
                }
                Text(label, fontFamily = Book.Display, fontSize = 16.sp, modifier = Modifier.weight(1f))
                when {
                    busy == "sheet-$style" -> Note("그리는 중 (1~3분)")
                    bought && isPet && e.ai.petStyle == style -> InkPill("펫이에요", Book.Leaf)
                    bought -> {
                        Note("구매 완료")
                        InkPill("펫으로 쓰기", Book.Sun, onClick = { Dex.setPetStyle(ctx, e.id, style); usePet(ctx, e.id); changed() })
                    }
                    else -> InkPill(if (free) "개발판 무료" else "${Purchase.PRICE_WON}원", Book.Sun,
                        onClick = if (!locked && busy == null) ({ token?.let { t -> guarded { AiWork.buy(ctx, e, t, style) } } }) else null)
                }
            }
        }
        AiWork.message[e.id]?.let { Note(it, color = Book.Warn) }
        if (locked) Note("출시 후 열려요")
    }
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
