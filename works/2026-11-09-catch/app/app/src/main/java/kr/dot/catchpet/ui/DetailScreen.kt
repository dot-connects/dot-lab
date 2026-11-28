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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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

/** One entry (felt mockup v5.2 screen 5): the catch on a pedestal, its AI line, a star that pins it in the dex, and
 *  one button that previews both looks and then makes both pets at once. */
@Composable
fun DetailScreen(id: String, onPet: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val rev = AiWork.rev
    var tick by remember { mutableStateOf(0) }
    val e = remember(id, rev, tick) { Dex.get(ctx, id) }
    var deleting by remember { mutableStateOf(false) }
    var bragPerson by remember { mutableStateOf<Dex.Entry?>(null) }
    var fav by remember(id) { mutableStateOf(id in Dex.favs(ctx)) }
    // the pet may have been turned off from its own bubble meanwhile
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { tick++ }
    BookPage(sky = 0.37f) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InkPill("‹ 도감", onClick = onBack)
            Spacer(Modifier.weight(1f))
            if (e != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.felt(if (fav) Color(0xFFFFF1C2) else Book.Cream, 999.dp).clip(RoundedCornerShape(50))
                    .clickable { fav = !fav; Dex.setFav(ctx, e.id, fav) }.padding(horizontal = 11.dp, vertical = 6.dp)) {
                    Star(fav, Modifier.size(19.dp))
                }
                // spec-extras 3: share a card of it; a person asks first
                InkPill("자랑하기", onClick = { if (Dex.hasPerson(e.kind)) bragPerson = e else ShareCard.share(ctx, e) })
            }
        }
        if (e == null) {
            Text("이 녀석을 찾지 못했어요.", fontSize = 17.sp)
            return@BookPage
        }
        var renaming by remember { mutableStateOf(false) }
        var shown by remember(e.id, e.name) { mutableStateOf(e.name) }
        val real = e.critterId == null
        // the hero on its felt pedestal
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.BottomCenter) {
            Box(Modifier.width(150.dp).height(26.dp).drawBehind {
                drawOval(Color(0xFFC9AD83), topLeft = Offset(0f, 5.dp.toPx()), size = size)
                drawOval(Brush.radialGradient(listOf(Color(0xFFFFF8E2), Color(0xFFE6D2AD))), size = size)
            })
            rememberPose(e, if (real) e.poses.first() else "front")?.let {
                Image(it, e.name, Modifier.padding(bottom = 13.dp).size(150.dp).let { m -> if (real) m.rotate(-4f) else m }, contentScale = ContentScale.Fit)
            }
        }
        val card = remember(e.id, rev) { Dex.card(ctx, e.id) }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Heading(shown, 32.sp)
            Text(card?.first ?: "${if (real) Dex.kindName(e.kind) else "털뭉치"} · No.${e.no} · ${SimpleDateFormat("yyyy.M.d", Locale.KOREA).format(Date(e.caughtAt))} 뽑음",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF4F6A3A), textAlign = TextAlign.Center, lineHeight = 18.sp)
            card?.second?.takeIf { it.isNotEmpty() }?.let { tags ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    for (t in tags) Text(t, fontFamily = Book.Display, fontSize = 12.5.sp,
                        modifier = Modifier.felt(Book.Sun, 999.dp, stitch = false).padding(horizontal = 10.dp, vertical = 1.dp))
                }
            }
        }
        if (renaming) RenameDialog(shown, onDone = { n -> renaming = false; if (n != null) Dex.rename(ctx, e.id, n)?.let { shown = it.name } })
        if (real) RealAi(ctx, e) { tick++ } else CritterPet(ctx, e) { tick++ }
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            if (real) Text("이름 바꾸기", fontSize = 14.sp, color = Book.Muted, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { renaming = true }.padding(12.dp))
            Text("도감에서 지우기", fontSize = 14.sp, color = Book.Warn, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { deleting = true }.padding(12.dp))
        }
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

/** "콩떡이" / "감자가": the subject particle after a Korean word. */
private fun withSubject(word: String): String {
    val c = word.lastOrNull() ?: return word
    return word + if (c in '가'..'힣' && (c - '가') % 28 != 0) "이" else "가"
}

/**
 * Make a pet (mockup v5.2 screen 5): [펫 미리보기] draws both looks, then [펫 만들기 · 무료 N장] makes both pets for
 * one free sheet (700 won once they run out); the two capsules wobble until they arrive, and a notification says so.
 */
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
    // operator 11-18: free previews and free pet sheets per device, the counts on the button
    LaunchedEffect(e.id) { AiWork.refresh(ctx) }
    val left = remember(AiWork.rev) { AiWork.left(ctx) }
    val token = remember { Purchase.token(ctx) }
    val dev = AiWork.dev
    // previews from before the look was recorded: the first counts as fur-ball, the second as figurine
    val old = e.ai.oldPreviews
    fun preview(style: String) = e.ai.previewFor(style) ?: old.getOrNull(if (style == "fluff") 0 else 1)
    val drawing = busy.any { it.startsWith("preview-") }
    val making = busy.any { it.startsWith("sheet-") }
    val bought = e.ai.sheets.isNotEmpty()
    val missing = LOOKS.map { it.first }.filter { preview(it) == null }
    Panel {
        Text("펫으로 만들기", fontFamily = Book.Display, fontSize = 19.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((style, label) in LOOKS) {
                val drawn = preview(style)
                val has = e.ai.sheets.containsKey(style)
                val redraw = dev && "preview-$style" !in busy && !making   // dev app: tap a look to draw it again
                Column(Modifier.weight(1f).felt(if (has) Color(0xFFFFF1C2) else Book.Cream, 16.dp)
                    .padding(start = 7.dp, end = 7.dp, top = 7.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(11.dp)).background(Color(0xFFF1E7D6))
                        .let { if (redraw) it.clickable { guarded { AiWork.preview(ctx, e, style) } } else it },
                        contentAlignment = Alignment.Center) {
                        val pic = drawn?.let { rememberFile(e.id, it, keyGreen = true) }
                        when {
                            making && !has -> Wobble(Modifier.fillMaxSize().padding(8.dp)) {
                                pic?.let { Image(it, null, Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp).alpha(0.9f), contentScale = ContentScale.Fit) }
                                Image(painterResource(kr.dot.catchpet.R.drawable.felt_cap), label, Modifier.fillMaxSize().alpha(0.85f), contentScale = ContentScale.Fit)
                            }
                            "preview-$style" in busy -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                CircularProgressIndicator(Modifier.size(26.dp), color = Color(0xFF8A6E4C), strokeWidth = 2.dp)
                                Text("그리는 중…", fontFamily = Book.Display, fontSize = 11.5.sp, color = Color(0xFF8A6E4C))
                            }
                            pic != null -> Image(pic, label, Modifier.fillMaxSize().padding(7.dp), contentScale = ContentScale.Fit)
                            else -> Box(Modifier.fillMaxSize().padding(6.dp).dashed(), contentAlignment = Alignment.Center) {
                                Text("미리보기 전", fontSize = 12.sp, color = Book.Muted)
                            }
                        }
                    }
                    Text(label, fontFamily = Book.Display, fontSize = 15.sp)
                    // Play AI-content rule: every AI picture can be reported without leaving the app (spec-release 4)
                    if (drawn != null || has) Text("신고", fontSize = 12.sp, color = Book.Muted,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { reporting = style }.padding(horizontal = 10.dp, vertical = 2.dp))
                }
            }
        }
        val free = dev || left.sheet != 0   // -1 = not heard yet: let the Worker decide
        val name = withSubject(e.name)
        when {
            making -> { InkButton("만드는 중… 나가도 돼요", enabled = false) {}; DtNote("보통 1~3분 · 다 되면 알림으로 알려 줄게요") }
            bought -> {
                if (isShownPet(ctx, e.id)) InkButton("지금 홈 화면에 있어요", enabled = false) {}
                else InkButton("홈 화면에 데려가기") { usePet(ctx, e.id); changed() }
                DtNote(if (e.ai.sheets.size > 1) "털뭉치·피규어 $name 왔어요! 홈에 띄울 쪽은 펫 설정에서 골라요" else "$name 왔어요! 펫 설정에서 크기와 자리를 바꿀 수 있어요")
                if (dev) Text("다시 만들기 (개발판)", fontSize = 13.sp, color = Book.Muted, textDecoration = TextDecoration.Underline,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50)).clickable { guarded { AiWork.buyBoth(ctx, e, token) } }.padding(6.dp))
            }
            drawing -> { InkButton("그리는 중…", enabled = false) {}; DtNote("털뭉치와 피규어를 같이 그리고 있어요") }
            missing.isNotEmpty() -> {
                val can = dev || left.preview != 0
                InkButton(if (can) "펫 미리보기" else "무료 미리보기를 다 썼어요", enabled = can) { guarded { missing.forEach { AiWork.preview(ctx, e, it) } } }
                DtNote("사진으로 털뭉치·피규어 두 가지를 그려 볼게요 · 미리보기는 무료" + if (!dev && left.preview > 0) " (${left.preview}번 남음)" else "")
            }
            else -> {
                InkButton(Purchase.makeLabel(left.sheet, dev), enabled = free) { guarded { AiWork.buyBoth(ctx, e, if (dev) token else null) } }
                // the release app: free sheets first (the Worker counts them); Play purchase comes next version
                DtNote(if (free) "털뭉치·피규어 둘 다 한 번에 만들어요 · 1장만 써요" else "무료를 다 썼어요. 구매는 다음 버전에서 열려요")
            }
        }
        AiWork.message[e.id]?.let { DtNote(it, Book.Warn) }
    }
}

@Composable
private fun DtNote(text: String, color: Color = Book.Muted) {
    Text(text, Modifier.fillMaxWidth(), fontSize = 12.sp, color = color, textAlign = TextAlign.Center, lineHeight = 17.sp)
}

/** A dashed outline (the empty look tile). */
private fun Modifier.dashed() = drawBehind {
    drawRoundRect(Color(0x556E5640), cornerRadius = androidx.compose.ui.geometry.CornerRadius(9.dp.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
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
