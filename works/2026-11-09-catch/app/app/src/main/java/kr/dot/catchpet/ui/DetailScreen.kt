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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import kr.dot.catchpet.ai.AiWork
import kotlinx.coroutines.launch
import kr.dot.catchpet.billing.Billing
import kr.dot.catchpet.billing.Purchase
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin
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
    LaunchedEffect(e.id) { AiWork.refresh(ctx); Billing.start(ctx) }
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
                        // the capsule keeps rocking until the sheet lands, then pops open (a failed draw just stops)
                        var hatching by remember(e.id) { mutableStateOf(false) }
                        LaunchedEffect(making, has) { if (making && !has) hatching = true }
                        when {
                            hatching && (making || has) -> HatchTile(pic, opened = has, onOpened = { hatching = false }, modifier = Modifier.fillMaxSize().padding(8.dp))
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
            free -> {   // free sheets first (the Worker counts them)
                InkButton(Purchase.makeLabel(left.sheet, dev)) { guarded { AiWork.buyBoth(ctx, e, if (dev) token else null) } }
                DtNote("털뭉치·피규어 둘 다 한 번에 만들어요 · 1장만 써요")
            }
            else -> PayButton(Billing.price, kept = e.id in Billing.kept, waiting = Billing.waiting[e.id] == true) { guarded { Billing.buy(ctx, e.id) } }
        }
        AiWork.message[e.id]?.let { DtNote(it, Book.Warn) }
    }
}

/** The paid button once the free sheets are gone: Play's price, a paid-but-not-drawn retry, or "준비 중" (no product yet). */
@Composable
internal fun PayButton(price: String?, kept: Boolean, waiting: Boolean, onBuy: () -> Unit) {
    when {
        kept -> { InkButton("펫 만들기 · 결제됨", onClick = onBuy); DtNote("결제한 펫을 다시 그려요 · 새로 결제하지 않아요") }
        waiting -> { InkButton("결제 확인 중…", enabled = false) {}; DtNote("결제가 끝나면 바로 만들어 줄게요") }
        price == null -> { InkButton("펫 만들기 · 준비 중", enabled = false) {}; DtNote("무료를 다 썼어요. 구매는 곧 열려요") }
        else -> { InkButton("펫 만들기 · $price", onClick = onBuy); DtNote("털뭉치·피규어 둘 다 한 번에 만들어요") }
    }
}

// the cracks (mockup next7v4 .dt-hatch svg, 100 x 100 over the capsule); each group shows up 1.45 s after the last
private val CRACKS = listOf(floatArrayOf(52f, 8f, 46f, 20f, 55f, 28f, 48f, 41f), floatArrayOf(48f, 41f, 37f, 49f, 41f, 60f),
    floatArrayOf(48f, 41f, 61f, 51f, 58f, 66f, 66f, 72f))

/**
 * The making capsule (mockup next7v4 hatch): it rocks harder as it nears and cracks spread; [opened] squashes it, the
 * lid flies off, the bottom drops and [pic] jumps out. Then [onOpened].
 */
@Composable
internal fun HatchTile(pic: ImageBitmap?, opened: Boolean, onOpened: () -> Unit, modifier: Modifier = Modifier) {
    val cap = ImageBitmap.imageResource(kr.dot.catchpet.R.drawable.felt_cap)
    var s by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) { val t0 = withFrameMillis { it }; while (true) withFrameMillis { s = (it - t0) / 1000f } }
    val open = remember { Animatable(0f) }
    LaunchedEffect(opened) { if (opened) { open.animateTo(1f, tween(760, easing = LinearEasing)); onOpened() } }
    Canvas(modifier) {
        val o = open.value
        val squash = min(1f, o / 0.2f)                       // the first 150 ms of the pop
        val f = ((o - 0.2f) / 0.8f).coerceIn(0f, 1f)          // the halves flying, the pet jumping
        val w = size.width * 0.74f; val h = size.height * 0.79f
        val x0 = (size.width - w) / 2; val y0 = (size.height - h) / 2
        val k = s % 1.15f; val amp = 4f + min(s, 6f) * 2.2f   // a kick every 1.15 s, stronger as it nears
        val rock = if (opened) 0f else amp * sin(k / 0.26f * 2f * PI.toFloat()) * exp(-k * 3f)
        val base = Offset(x0 + w / 2, y0 + h * 0.88f)
        val sx = 1f + 0.1f * squash * (1f - f); val sy = 1f - 0.14f * squash * (1f - f)
        withTransform({ rotate(rock, base); scale(sx, sy, base) }) {
            if (f == 0f) pic?.let { p ->   // the pet inside, before the pop
                val bw = w * 0.58f; val bh = h * 0.64f; val q = min(bw / p.width, bh / p.height)
                val pw = p.width * q; val ph = p.height * q
                drawImage(p, dstOffset = IntOffset((x0 + w / 2 - pw / 2).toInt(), (y0 + h * 0.2f + bh / 2 - ph / 2).toInt()),
                    dstSize = IntSize(pw.toInt(), ph.toInt()), alpha = 0.9f)
            }
            fun half(fromY: Float, toY: Float, dx: Float, dy: Float, deg: Float, a: Float) = withTransform({
                translate(dx, dy); rotate(deg, Offset(x0 + w / 2, y0 + h / 2))
            }) {
                clipRect(x0, y0 + h * fromY, x0 + w, y0 + h * toY) {
                    drawImage(cap, dstOffset = IntOffset(x0.toInt(), y0.toInt()), dstSize = IntSize(w.toInt(), h.toInt()), alpha = a)
                }
            }
            half(0.52f, 1f, -0.1f * w * f, 0.7f * h * f, -25f * f, 1f - f)
            half(0f, 0.54f, 0.26f * w * f, -0.9f * h * f, 160f * f, 0.78f * (1f - f))
            if (!opened) CRACKS.forEachIndexed { i, pts ->
                val grow = ((s - 1.5f - i * 1.45f) / 0.45f).coerceIn(0f, 1f)
                if (grow > 0f) {
                    crack(pts, grow, x0, y0, w, h, Color(0xFF5A4632), 2.4.dp.toPx())
                    crack(pts, grow, x0 + w / 100f, y0 + h / 100f, w, h, Color(0xCCFFFAF0), 1.2.dp.toPx())
                }
            }
        }
        if (f > 0f) pic?.let { p ->   // the pet jumps out: squashed low, stretched high, settles
            val bw = size.width * 0.86f; val q = min(bw / p.width, bw / p.height)
            val pw = p.width * q; val ph = p.height * q
            val jump = sin(f * PI.toFloat())
            val c = Offset(size.width / 2, size.height / 2 + (1f - f) * 30.dp.toPx() - jump * 16.dp.toPx())
            withTransform({ scale(0.3f + 0.7f * f + 0.1f * jump * (1f - f), 0.5f + 0.5f * f + 0.15f * jump, c) }) {
                drawImage(p, dstOffset = IntOffset((c.x - pw / 2).toInt(), (c.y - ph / 2).toInt()), dstSize = IntSize(pw.toInt(), ph.toInt()),
                    alpha = min(1f, f * 2.2f))
            }
        }
    }
}

/** The first [frac] of one crack polyline (100 x 100 units) over the capsule box. */
private fun DrawScope.crack(pts: FloatArray, frac: Float, x0: Float, y0: Float, w: Float, h: Float, color: Color, width: Float) {
    val segs = (2 until pts.size step 2).map { i -> Offset(pts[i - 2], pts[i - 1]) to Offset(pts[i], pts[i + 1]) }
    var left = segs.sumOf { (a, b) -> (b - a).getDistance().toDouble() }.toFloat() * frac
    val path = Path().apply { moveTo(x0 + pts[0] * w / 100f, y0 + pts[1] * h / 100f) }
    for ((a, b) in segs) {
        if (left <= 0f) break
        val d = (b - a).getDistance(); val t = min(1f, left / d); left -= d
        val p = a + (b - a) * t
        path.lineTo(x0 + p.x * w / 100f, y0 + p.y * h / 100f)
    }
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
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
