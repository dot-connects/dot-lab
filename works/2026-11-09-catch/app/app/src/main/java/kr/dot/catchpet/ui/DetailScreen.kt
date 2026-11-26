package kr.dot.catchpet.ui

import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.launch
import kr.dot.catchpet.billing.Purchase
import kr.dot.catchpet.dex.Dex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val POSE_NAMES = mapOf("front" to "앞", "side" to "옆", "back" to "뒤")
private val STYLES = listOf("fluff" to "털뭉치", "figure" to "피규어")

/** The chosen pet: a dex entry id in SharedPreferences "catchpet" / "pet" (the pet overlay reads it). */
fun petId(ctx: Context): String? = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).getString("pet", null)
fun setPet(ctx: Context, id: String) {
    ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE).edit().putString("pet", id).commit()   // the widget reads it next
    kr.dot.catchpet.widget.PetWidgets.refresh(ctx)
}

/** One entry (confirmed mockup allmenus 11-15): poses, one preview per look, each look bought apart, pet, delete. */
@Composable
fun DetailScreen(id: String, onPet: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rev = AiWork.rev
    var tick by remember { mutableStateOf(0) }
    val e = remember(id, rev, tick) { Dex.get(ctx, id) }
    var deleting by remember { mutableStateOf(false) }
    var bragPerson by remember { mutableStateOf<Dex.Entry?>(null) }
    // the pet may have been turned off from its own bubble meanwhile
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    BookPage {
        Row { InkPill("‹ 도감", onClick = onBack) }
        if (e == null) {
            Text("이 녀석을 찾지 못했어요.", fontSize = 17.sp)
            return@BookPage
        }
        var renaming by remember { mutableStateOf(false) }
        var shown by remember(e.id, e.name) { mutableStateOf(e.name) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(76.dp).background(Book.Paper, RoundedCornerShape(16.dp)).border(2.dp, Book.Ink, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center) {
                rememberPose(e, if (e.critterId != null) "front" else e.poses.first())?.let { Image(it, e.name, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit) }
            }
            Column(Modifier.weight(1f)) {
                Heading(shown, 30.sp)
                Note("${if (e.critterId != null) "털뭉치" else Dex.kindName(e.kind)} · No.${e.no} · ${SimpleDateFormat("yyyy.M.d", Locale.KOREA).format(Date(e.caughtAt))} 뽑음")
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (e.critterId == null) InkPill("이름 바꾸기", onClick = { renaming = true })
                    // spec-extras 3: share a card of it; a person asks first
                    InkPill("자랑하기", Book.Sun, onClick = { if (Dex.hasPerson(e.kind)) bragPerson = e else ShareCard.share(ctx, e) })
                }
            }
        }
        if (renaming) RenameDialog(shown, onDone = { n -> renaming = false; if (n != null) Dex.rename(ctx, e.id, n)?.let { shown = it.name } })
        if (e.critterId != null) CritterPet(ctx, e) { tick++ }
        else RealAi(ctx, e) { tick++ }
        Spacer(Modifier.height(8.dp))
        Text("도감에서 지우기", fontFamily = Book.Display, fontSize = 15.sp, color = Book.Warn,
            modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50)).clickable { deleting = true }.padding(12.dp))
        Spacer(Modifier.height(16.dp))
    }
    bragPerson?.let { b -> PersonConsent(PersonUse.SHARE) { ok -> bragPerson = null; if (ok) ShareCard.share(ctx, b) } }
    if (deleting && e != null) DeleteDialog(listOf(e), onDone = { yes -> deleting = false; if (yes && removeEntries(ctx, listOf(e.id)) == 0) onBack() })
}

/** Makes [id] the pet and, when the pet is on screen, swaps it at once (operator 11-15: it did not change). */
internal fun usePet(ctx: Context, id: String) {
    setPet(ctx, id)
    if (android.provider.Settings.canDrawOverlays(ctx)) PetService.start(ctx)
    else {   // review M3 (11-15): without the overlay permission nothing would happen; ask for it
        android.widget.Toast.makeText(ctx, "'다른 앱 위에 표시'를 허용하고 다시 눌러 주세요", android.widget.Toast.LENGTH_LONG).show()
        ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            android.net.Uri.parse("package:${ctx.packageName}")))
    }
}

/** This entry is the pet on screen right now (the overlay is on). */
internal fun isShownPet(ctx: Context, id: String) = petId(ctx) == id && PetPrefs.overlayOn(ctx)

/** Deletes entries; the pet among them turns the floating pet off. */
internal fun removeEntries(ctx: Context, ids: List<String>): Int {
    val pet = petId(ctx)
    val prefs = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE)
    val gone = ids.filter { !AiWork.isBusy(it) }   // a drawing in progress (maybe paid) finishes first
    for (id in gone) { Dex.delete(ctx, id); prefs.edit().remove("ai_ok_$id").apply() }
    if (pet != null && pet in gone) {
        prefs.edit().remove("pet").apply()
        PetService.stop(ctx)
    }
    return ids.size - gone.size
}

@Composable
internal fun DeleteDialog(es: List<Dex.Entry>, onDone: (Boolean) -> Unit) {
    val who = if (es.size == 1) Dex.withObject(es[0].name) else "${es.size}마리를"
    val bought = es.any { it.ai.owned }
    val drawing = es.count { AiWork.isBusy(it.id) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { onDone(false) },
        containerColor = Book.Paper,
        title = { Text("$who 지울까요?", fontFamily = Book.Display, color = Book.Ink) },
        text = {
            Text("사진과 그려 본 그림이 이 폰에서 지워져요." + (if (bought) " 산 펫 그림도 함께 지워지고" else "") + " 되돌릴 수 없어요." +
                (if (drawing > 0) " 지금 그리는 중인 ${drawing}마리는 다 그린 뒤에 지울 수 있어서 이번엔 빠져요." else ""),
                fontFamily = Book.Body, color = Book.Ink)
        },
        confirmButton = { InkPill("지우기", Color(0xFFFF8A80), onClick = { onDone(true) }) },
        dismissButton = { InkPill("그대로 두기", onClick = { onDone(false) }) },
    )
}

@Composable
private fun CritterPet(ctx: Context, e: Dex.Entry, changed: () -> Unit) {
    if (isShownPet(ctx, e.id)) Panel { Text("지금 홈 화면에 떠 있는 펫이에요", fontFamily = Book.Display, fontSize = 17.sp) }
    else InkButton("펫으로 쓰기") { usePet(ctx, e.id); changed() }
}

private val LOOKS = listOf("fluff" to "털뭉치", "figure" to "피규어")

@Composable
private fun RealAi(ctx: Context, e: Dex.Entry, changed: () -> Unit) {
    val busy = AiWork.busyOf(e.id)
    // a person's photo leaves the phone only after "허락받았어요" (asked once per dex entry)
    val prefs = remember { ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE) }
    var asking by remember { mutableStateOf<(() -> Unit)?>(null) }
    fun guarded(go: () -> Unit) {
        if (!Dex.hasPerson(e.kind) || prefs.getBoolean("ai_ok_${e.id}", false)) go() else asking = go
    }
    asking?.let { go ->
        PersonConsent(PersonUse.AI) { ok ->
            asking = null
            if (ok) { prefs.edit().putBoolean("ai_ok_${e.id}", true).apply(); go() }
        }
    }
    var reporting by remember { mutableStateOf<String?>(null) }   // the look being reported
    reporting?.let { style -> ReportDialog(ctx, e, style) { reporting = null } }
    // operator 11-18: free previews (10) and free pet sheets (5) per device, the counts written on the buttons
    LaunchedEffect(e.id) { AiWork.refresh(ctx) }
    val left = remember(AiWork.rev) { AiWork.left(ctx) }
    val token = remember { Purchase.token(ctx) }
    val free = kr.dot.catchpet.BuildConfig.DEBUG_PURCHASE.isNotEmpty()
    val sheetsLeft = left.sheet != 0   // -1 = not heard yet: let the Worker decide
    // previews from before the look was recorded: the first counts as fur-ball, the second as figurine
    val old = e.ai.oldPreviews
    fun preview(style: String) = e.ai.previewFor(style) ?: old.getOrNull(if (style == "fluff") 0 else 1)
    // operator 11-15: tap the ? box to draw; once drawn, the button under it buys that look, then uses it
    Panel("펫으로 만들기") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            for ((style, label) in LOOKS) {
                val drawn = preview(style)
                val bought = e.ai.sheets.containsKey(style)
                val canDraw = (drawn == null || AiWork.dev) && "preview-$style" !in busy && left.preview != 0   // dev app: tap again to redraw
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, fontFamily = Book.Display, fontSize = 16.sp)
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(Book.Field)
                        .border(2.dp, Book.Ink, RoundedCornerShape(16.dp))
                        .let { if (canDraw) it.clickable { guarded { AiWork.preview(ctx, e, style) } } else it },
                        contentAlignment = Alignment.Center) {
                        when {
                            "preview-$style" in busy -> CircularProgressIndicator(Modifier.size(26.dp), color = Book.Ink, strokeWidth = 2.dp)
                            drawn != null -> rememberFile(e.id, drawn, keyGreen = true)?.let { Image(it, label, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("?", fontFamily = Book.Display, fontSize = 40.sp, color = Book.Muted)
                                Note(when {
                                    left.preview == 0 -> "무료 미리보기 끝"
                                    left.preview > 0 && !AiWork.dev -> "눌러서 그려 보기\n무료 ${left.preview}/${left.previewTotal}"
                                    else -> "눌러서 그려 보기"
                                })
                            }
                        }
                    }
                    // Play AI-content rule: every AI picture can be reported without leaving the app (spec-release 4)
                    if (drawn != null || bought) Text("신고", fontFamily = Book.Body, fontSize = 13.sp, color = Book.Muted,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { reporting = style }.padding(horizontal = 10.dp, vertical = 4.dp))
                    when {
                        drawn == null && !bought -> {}
                        "sheet-$style" in busy -> Note("펫 그림을 그리는 중 (1~3분)")
                        bought && isShownPet(ctx, e.id) && e.ai.petStyle == style -> InkPill("펫으로 쓰는 중", Book.Leaf)
                        bought && AiWork.dev -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            InkPill("쓰기", Book.Sun, onClick = { Dex.setPetStyle(ctx, e.id, style); usePet(ctx, e.id); changed() })
                            InkPill("다시", Book.Paper, onClick = if ("sheet-$style" !in busy) ({ token?.let { t -> guarded { AiWork.buy(ctx, e, t, style) } } }) else null)
                        }
                        bought -> InkPill("펫으로 쓰기", Book.Sun, onClick = { Dex.setPetStyle(ctx, e.id, style); usePet(ctx, e.id); changed() })
                        free -> InkPill("구매 · 개발판 무료", Book.Sun,
                            onClick = if ("sheet-$style" !in busy) ({ token?.let { t -> guarded { AiWork.buy(ctx, e, t, style) } } }) else null)
                        // the release app: free test sheets first (the Worker counts them); Play purchase comes next version
                        sheetsLeft -> InkPill(if (left.sheet > 0) "펫 만들기 · 무료 ${left.sheet}장 남음" else "펫 만들기 · 무료", Book.Sun,
                            onClick = if ("sheet-$style" !in busy) ({ guarded { AiWork.buy(ctx, e, null, style) } }) else null)
                        else -> InkPill("구매 · ${Purchase.PRICE_WON}원", Book.Sun, onClick = null)
                    }
                }
            }
        }
        Note(when {
            AiWork.dev -> "개발판 · 그림을 누르면 몇 번이든 다시 그려요"
            left.preview < 0 -> "스타일마다 한 번 그려 볼 수 있어요"
            else -> "무료 미리보기 ${maxOf(left.preview, 0)}/${left.previewTotal}번 · 무료 펫 그림 ${maxOf(left.sheet, 0)}/${left.sheetTotal}장 남음"
        })
        AiWork.message[e.id]?.let { Note(it, color = Book.Warn) }
        if (!free && left.sheet == 0) Note("무료 펫 그림을 다 썼어요. 구매는 다음 버전에서 열려요")
    }
}

private val REASONS = listOf("odd" to "이상하거나 불쾌해요", "person" to "사람이 잘못 그려졌어요", "other" to "기타")

/** Three reasons -> Worker /report (no picture is sent), then a thank-you line. */
@Composable
private fun ReportDialog(ctx: Context, e: Dex.Entry, style: String, onDone: () -> Unit) {
    var state by remember { mutableStateOf<Boolean?>(null) }   // null = choosing, true = sent, false = failed
    var sending by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDone,
        containerColor = Book.Paper,
        title = { Text("이 그림 신고하기", fontFamily = Book.Display, color = Book.Ink) },
        text = {
            if (state == true) Text("알려 줘서 고마워요. 확인해 볼게요.", fontFamily = Book.Body, color = Book.Ink)
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state == false) Text("보내지 못했어요. 인터넷을 확인하고 다시 눌러 주세요.", fontFamily = Book.Body, color = Book.Warn)
                    for ((key, label) in REASONS) InkPill(label, onClick = if (sending) null else ({
                        sending = true
                        scope.launch { state = AiWork.report(ctx, e, style, key); sending = false }
                    }))
            }
        },
        confirmButton = { InkPill(if (state == true) "닫기" else "취소", onClick = onDone) },
    )
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
