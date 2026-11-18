package kr.dot.catchpet.ui

import androidx.core.graphics.drawable.toBitmap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.Manifest
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.dot.catchpet.billing.Purchase
import kr.dot.catchpet.dex.Dex
import kr.dot.catchpet.pet.PetPrefs
import kr.dot.catchpet.pet.PetService
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pet settings (mockup A): on/off and where it shows (home screen only, or always over every app; where on screen is
 * set by dragging the pet itself), its size shown at real size, which pet, and a developer corner (long press on
 * the title). No wallpaper: operator 11-14.
 */
@Composable
fun PetScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val pets by produceState<List<Dex.Entry>>(emptyList()) {
        value = withContext(Dispatchers.IO) { Dex.list(ctx).filter { it.critterId != null || it.ai.owned } }
    }
    var chosen by remember { mutableStateOf(petId(ctx)) }
    var petLook by remember { mutableStateOf(petId(ctx)?.let { Dex.get(ctx, it)?.ai?.petStyle }) }
    var size by remember { mutableStateOf(PetPrefs.size(ctx)) }
    var dev by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var askedDraw by remember { mutableStateOf(false) }
    var homeOnly by remember { mutableStateOf(PetPrefs.homeOnly(ctx)) }
    var usage by remember { mutableStateOf(PetPrefs.usageGranted(ctx)) }
    var on by remember { mutableStateOf(PetPrefs.overlayOn(ctx)) }
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        on = PetPrefs.overlayOn(ctx)
        canDraw = Settings.canDrawOverlays(ctx)
        val u = PetPrefs.usageGranted(ctx); if (u != usage) { usage = u; if (on) PetService.start(ctx) }
    }

    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { PetService.start(ctx); on = true }
    fun restartIfOn() { if (on) PetService.start(ctx) }
    val pet = pets.firstOrNull { it.id == chosen }?.let { p -> p.copy(ai = p.ai.copy(sheet = p.ai.sheets[petLook] ?: p.ai.sheet, petStyle = petLook ?: p.ai.petStyle)) }

    if (picking) { AppPicker(onDone = { picking = false }); return }
    BookPage {
        Row { InkPill("‹ 뽑기", onClick = onBack) }
        Heading("펫", modifier = Modifier.pointerInput(Unit) { detectTapGestures(onLongPress = { dev = true }) })

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("펫 띄우기", fontFamily = Book.Display, fontSize = 18.sp, modifier = Modifier.weight(1f))
                InkSwitch(on) {
                    when {
                        chosen == null -> {}
                        !it -> { PetService.stop(ctx); on = false }
                        !canDraw -> { askedDraw = true; ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) }
                        else -> notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }
            if (chosen == null) Note("아래에서 띄울 녀석을 먼저 골라 주세요.")
            if (!canDraw && askedDraw) Note("'다른 앱 위에 표시'에서 리얼뽑기를 허용하면 켜져요.")
            Seg(listOf("홈 화면" to true, "항상" to false), homeOnly) { v ->
                homeOnly = v; PetPrefs.setHomeOnly(ctx, v); restartIfOn()
                if (v && !usage) ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            Note(when {
                // operator 11-16: say that in other apps it only sits on the sides
                !homeOnly && usage -> "앱을 쓰는 동안에도 녀석이 늘 있어요. 다만 홈이 아닌 앱에서는 키보드를 가리지 않게 양옆 벽 위쪽에만 붙어 있어요."
                !homeOnly -> "앱을 쓰는 동안에도 녀석이 늘 있어요. 앱에서는 양옆 벽 위쪽에만 붙어 있게 하려면 '사용정보 접근'에서 리얼뽑기를 허용해 주세요. 허용 전에는 바닥에도 내려와요."
                usage -> "홈 화면에서만 보이고, 앱을 열면 숨어요."
                else -> "홈 화면에서만 보이려면 '사용정보 접근'에서 리얼뽑기를 허용해 주세요. 허용 전에는 항상 보여요."
            })
            if (!usage) Row { InkPill("사용정보 접근 열기", Book.Sun, onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) }
        }

        Panel("누구를 띄울까") {
            if (pets.isEmpty()) Note("털뭉치를 뽑거나 진짜 녀석의 펫 그림을 만들면 여기에 나와요.")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // each bought look is its own choice (operator 11-15: the figurine was missing here)
                for ((e0, look) in pets.flatMap { p -> if (p.critterId != null || p.ai.sheets.isEmpty()) listOf(p to null) else p.ai.sheets.keys.map { p to it } }) {
                    val e = if (look == null) e0 else e0.copy(ai = e0.ai.copy(sheet = e0.ai.sheets[look], petStyle = look))
                    val sel = e.id == chosen && (look == null || look == petLook)
                    Column(
                        Modifier.width(76.dp).clip(RoundedCornerShape(14.dp))
                            .let { if (sel) it.background(Book.Sun.copy(alpha = 0.35f)).border(2.dp, Book.Ink, RoundedCornerShape(14.dp)) else it }
                            .clickable {
                                setPet(ctx, e.id); chosen = e.id
                                if (look != null) { Dex.setPetStyle(ctx, e.id, look); petLook = look }
                                restartIfOn()
                            }
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        rememberPetFront(e)?.let { Image(it, e.name, Modifier.size(54.dp).alpha(if (sel) 1f else 0.6f), contentScale = ContentScale.Fit) }
                            ?: Spacer(Modifier.size(54.dp))
                        Text(e.name, fontFamily = Book.Display, fontSize = 13.sp, maxLines = 1)
                        if (look != null) Text(if (look == "figure") "피규어" else "털뭉치", fontSize = 10.sp, color = Book.Muted)
                    }
                }
            }
        }
        Panel("크기") {
            // three sizes, each shown at the size it will have on the home screen
            val pic = pet?.let { rememberPetFront(it) }
            Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                for ((label, dp) in SIZES) {
                    val sel = kotlin.math.abs(size - dp) < 1f
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp)).clickable { size = dp; PetPrefs.setSize(ctx, dp); restartIfOn() }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(Modifier.size(dp.dp).let { if (sel) it.border(3.dp, Book.Sun, RoundedCornerShape(12.dp)) else it }, contentAlignment = Alignment.BottomCenter) {
                            if (pic != null) Image(pic, label, Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
                            else Box(Modifier.fillMaxSize().padding(6.dp).background(Book.Field, CircleShape))
                        }
                        Text(label, fontFamily = Book.Display, fontSize = 14.sp, color = if (sel) Book.Ink else Book.Muted)
                    }
                }
            }
            Note("녀석을 끌어서 아래 줄이나 양옆 벽에 놓을 수 있어요.")
        }

        AppsPanel(onPick = { picking = true })

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("말풍선", fontFamily = Book.Display, fontSize = 18.sp, modifier = Modifier.weight(1f))
                var talk by remember { mutableStateOf(PetPrefs.talkOn(ctx)) }
                InkSwitch(talk) { talk = it; PetPrefs.setTalkOn(ctx, it) }
            }
            Note("잠금을 풀거나 충전기를 꽂을 때 녀석이 가끔 혼잣말을 해요. 두 번 톡톡 눌러 여는 말풍선은 그대로예요.")
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("소리", fontFamily = Book.Display, fontSize = 18.sp, modifier = Modifier.weight(1f))
                var sound by remember { mutableStateOf(!Sfx.muted(ctx)) }
                InkSwitch(sound) { sound = it; Sfx.setMuted(ctx, !it) }
            }
        }

        if (dev) Developer(ctx)
        Spacer(Modifier.height(24.dp))
    }
}

private val SIZES = listOf("작게" to 40f, "보통" to 56f, "크게" to 80f)

@Composable
private fun Developer(ctx: Context) {
    val prefs = remember { PetPrefs.prefs(ctx) }
    var token by remember { mutableStateOf(prefs.getString("debug_purchase", null) ?: "") }
    var savedNote by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(prefs.getFloat("critter_scale", 0.25f)) }
    var ar by remember { mutableStateOf(prefs.getBoolean("ar", false)) }
    Panel("개발자") {
        if (kr.dot.catchpet.BuildConfig.DEBUG) OutlinedTextField(
            value = token, onValueChange = { token = it; savedNote = false }, singleLine = true,
            label = { Text("디버그 구매 토큰") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Book.Ink, unfocusedTextColor = Book.Ink,
                focusedBorderColor = Book.Ink, unfocusedBorderColor = Book.Muted, focusedLabelColor = Book.Ink, unfocusedLabelColor = Book.Muted),
        )
        if (kr.dot.catchpet.BuildConfig.DEBUG) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InkPill("저장", Book.Sun, onClick = { Purchase.saveToken(ctx, token); savedNote = true })
            if (savedNote) Note(if (token.isBlank()) "토큰을 지웠어요" else "저장했어요")
        }
        // critter height = scale x 40 % of the screen: 0.1 -> 4 %, 0.6 -> 24 % (1/4 and 1/5 looked the same, 11-13)
        Text("털뭉치 크기  ${(scale * 40).roundToInt()}% (화면 높이 대비)")
        Slider(
            value = scale, valueRange = 0.1f..0.6f,
            onValueChange = { scale = it },
            onValueChangeFinished = { prefs.edit().putFloat("critter_scale", scale).apply() },
            colors = SliderDefaults.colors(thumbColor = Book.Ink, activeTrackColor = Book.Leaf, inactiveTrackColor = Book.Field),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AR 털뭉치 (실험)", modifier = Modifier.weight(1f))
            InkSwitch(ar) { ar = it; prefs.edit().putBoolean("ar", it).apply() }
        }
        Note("털뭉치를 실제 바닥에 붙여 둬요. 뽑기 화면에 들어가면 켜졌는지 알려 줘요.")
    }
}

/** The pet's front picture as the overlay draws it (critter asset or the bought sheet's first cell). */
@Composable
private fun rememberPetFront(e: Dex.Entry): androidx.compose.ui.graphics.ImageBitmap? {
    val ctx = LocalContext.current
    val v by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, e.id, e.ai.sheet, e.ai.petStyle) {
        value = withContext(Dispatchers.IO) { kr.dot.catchpet.pet.Frames.load(ctx, e)?.get("front")?.asImageBitmap() }
    }
    return v
}

/** "두 번 톡톡 누르면": the apps the pet opens; + opens the picker, a long press takes one out (operator 11-15). */
@Composable
private fun AppsPanel(onPick: () -> Unit) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(kr.dot.catchpet.pet.PetApps.mode(ctx)) }
    var usage by remember { mutableStateOf(PetPrefs.usageGranted(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { usage = PetPrefs.usageGranted(ctx) }
    var count by remember { mutableStateOf(kr.dot.catchpet.pet.PetApps.count(ctx)) }
    val auto = mode != kr.dot.catchpet.pet.PetApps.Mode.CHOSEN
    val showAuto = auto && usage
    val apps by produceState<List<kr.dot.catchpet.pet.PetApps.App>>(emptyList(), mode, usage, count) {
        value = withContext(Dispatchers.IO) { kr.dot.catchpet.pet.PetApps.load(ctx) }
    }
    Panel("두 번 톡톡 누르면") {
        // operator 11-18: recent / most used this week / chosen (the first two need usage access; Android hides other apps' open tasks)
        Seg(listOf("최근 앱" to kr.dot.catchpet.pet.PetApps.Mode.RECENT, "자주 쓴 앱" to kr.dot.catchpet.pet.PetApps.Mode.FREQUENT,
            "직접 선택" to kr.dot.catchpet.pet.PetApps.Mode.CHOSEN), mode) { v ->
            mode = v; kr.dot.catchpet.pet.PetApps.setMode(ctx, v)
            if (v != kr.dot.catchpet.pet.PetApps.Mode.CHOSEN && !usage) ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        if (auto && !usage) {
            Note("최근 앱·자주 쓴 앱을 보려면 '사용정보 접근'에서 리얼뽑기를 허용해 주세요. 허용 전에는 고른 앱이 떠요.")
            Row { InkPill("사용정보 접근 열기", Book.Sun, onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) }
        }
        if (showAuto) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (a in apps) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                    Image(rememberIcon(a.icon), a.label, Modifier.size(40.dp))
                    Text(a.label, fontSize = 10.sp, maxLines = 1)
                }
            }
            Seg(kr.dot.catchpet.pet.PetApps.COUNTS.map { "${it}개" to it }, count) { n -> count = n; kr.dot.catchpet.pet.PetApps.setCount(ctx, n) }
            Note(if (mode == kr.dot.catchpet.pet.PetApps.Mode.RECENT) "두 번 톡톡 누르면 방금까지 쓴 앱 ${count}개가 최근 순서로 떠요."
                else "두 번 톡톡 누르면 최근 일주일 동안 오래 쓴 앱 ${count}개가 떠요.")
            return@Panel
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (a in apps) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                Image(rememberIcon(a.icon), a.label, Modifier.size(40.dp))
                Text(a.label, fontSize = 10.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)
                .clip(RoundedCornerShape(10.dp)).clickable(onClick = onPick)) {
                Box(Modifier.size(40.dp).border(2.dp, Book.Muted, RoundedCornerShape(10.dp)).background(Book.Field, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) { Text(if (apps.isEmpty()) "+" else "✎", fontFamily = Book.Display, fontSize = 20.sp, color = Book.Muted) }
                Text(if (apps.isEmpty()) "추가" else "바꾸기", fontSize = 10.sp)
            }
        }
        Note(when (apps.size) {
            0 -> "앱을 고르면 두 번 톡톡 눌렀을 때 말풍선에 떠요. 말풍선 끝의 [펫 끄기]로 펫을 끌 수 있어요."
            else -> "두 번 톡톡 누르면 말풍선에 ${apps.size}개 앱과 [펫 끄기]가 떠요(최대 ${kr.dot.catchpet.pet.PetApps.MAX}개)."
        })
    }
}

@Composable
private fun rememberIcon(d: android.graphics.drawable.Drawable): androidx.compose.ui.graphics.ImageBitmap =
    remember(d) { d.toBitmap(96, 96).asImageBitmap() }

/** Full-screen list of installed apps with check marks; leaving saves (mockup allmenus "앱 고르기"). */
@Composable
private fun AppPicker(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val all by produceState<List<kr.dot.catchpet.pet.PetApps.App>?>(null) { value = withContext(Dispatchers.IO) { kr.dot.catchpet.pet.PetApps.installed(ctx) } }
    var chosen by remember { mutableStateOf(kr.dot.catchpet.pet.PetApps.chosen(ctx)) }
    // the ones already chosen sit on top (operator 11-16); fixed at opening so a tap doesn't make rows jump
    val first = remember { chosen }
    fun close() { kr.dot.catchpet.pet.PetApps.setChosen(ctx, chosen); onDone() }
    // a page in the activity, not a Dialog window: on the Fold8 the dialog's bottom was cut (operator 11-16)
    androidx.activity.compose.BackHandler { close() }
    val max = kr.dot.catchpet.pet.PetApps.MAX
    fun toggle(pkg: String) {
        chosen = when {
            pkg in chosen -> chosen - pkg
            chosen.size < max -> chosen + pkg
            else -> { android.widget.Toast.makeText(ctx, "최대 ${max}개까지 고를 수 있어요", android.widget.Toast.LENGTH_SHORT).show(); chosen }
        }
    }
    BookPage(scroll = false) {
        Row { InkPill("‹ 펫", onClick = { close() }) }
        Heading("열 앱 고르기", 26.sp)
        // the chosen ones stay pinned above the list while it scrolls (operator 11-16, mockup picker A)
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("고른 앱", fontFamily = Book.Display, fontSize = 16.sp, modifier = Modifier.weight(1f))
                Note("${chosen.size}/$max · 누르면 빠져요")
            }
            val byPkg = all?.associateBy { it.pkg }.orEmpty()
            val picked = chosen.mapNotNull { byPkg[it] }
            if (picked.isEmpty()) Note("아래에서 앱을 골라 주세요")
            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (a in picked) Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(48.dp).clip(RoundedCornerShape(10.dp)).clickable { toggle(a.pkg) }) {
                    Box {
                        Image(rememberIcon(a.icon), a.label, Modifier.padding(top = 4.dp, end = 4.dp).size(36.dp))
                        Box(Modifier.align(Alignment.TopEnd).size(16.dp).background(Book.Ink, CircleShape), contentAlignment = Alignment.Center) {
                            Text("×", fontSize = 11.sp, color = Color.White, lineHeight = 11.sp)
                        }
                    }
                    Text(a.label, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        val list = all?.sortedBy { it.pkg !in first }
        Box(Modifier.weight(1f).fillMaxWidth().inkCard()) {
            if (list == null) CircularProgressIndicator(color = Book.Ink, modifier = Modifier.align(Alignment.Center))
            else androidx.compose.foundation.lazy.LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                items(list.size, key = { list[it].pkg }) { i ->
                    val a = list[i]
                    val on = a.pkg in chosen
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { toggle(a.pkg) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Image(rememberIcon(a.icon), a.label, Modifier.size(32.dp))
                        Text(a.label, modifier = Modifier.weight(1f), maxLines = 1)
                        Box(Modifier.size(22.dp).background(if (on) Book.Leaf else Book.Paper, CircleShape).border(2.dp, Book.Ink, CircleShape),
                            contentAlignment = Alignment.Center) { if (on) Text("✓", fontSize = 13.sp, color = Color.White) }
                    }
                }
            }
        }
    }
}
