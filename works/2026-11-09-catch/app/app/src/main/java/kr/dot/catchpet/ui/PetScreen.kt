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
                !homeOnly -> "어떤 앱을 쓰든 녀석이 화면 위에 있어요."
                usage -> "홈 화면에서만 보이고, 앱을 열면 숨어요."
                else -> "홈 화면에서만 보이려면 '사용정보 접근'에서 리얼뽑기를 허용해 주세요. 허용 전에는 항상 보여요."
            })
            if (homeOnly && !usage) Row { InkPill("사용정보 접근 열기", Book.Sun, onClick = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) }
        }

        AppsPanel()

        // operator 11-15: optional, so the pet never sits on the keyboard (accessibility window list, as in 눈금)
        var kbOn by remember { mutableStateOf(PetService.keyboardWatchOn(ctx)) }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { kbOn = PetService.keyboardWatchOn(ctx) }
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("키보드 피하기", fontFamily = Book.Display, fontSize = 18.sp, modifier = Modifier.weight(1f))
                InkPill(if (kbOn) "켜짐" else "켜러 가기", if (kbOn) Book.Leaf else Book.Sun,
                    onClick = { kr.dot.catchpet.pet.KeyboardWatch.openSettings(ctx) })
            }
            Note(if (kbOn) "키보드가 열리면 녀석이 키보드 위로 올라가요." else
                "꺼 둬도 홈이 아닌 앱에서는 녀석이 벽 위쪽에 머물러 키보드를 가리지 않아요. 켜면 어디서든 키보드만 딱 피해요. 누르면 리얼뽑기 스위치로 바로 가고, 켜면 이 화면으로 돌아와요.")
            if (!kbOn && kr.dot.catchpet.pet.KeyboardWatch.sideloaded(ctx)) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Note("스위치가 회색이면: 앱 정보 → 오른쪽 위 ⋮ → 제한된 설정 허용 → 다시 '켜러 가기'.", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    InkPill("앱 정보", onClick = { kr.dot.catchpet.pet.KeyboardWatch.openAppInfo(ctx) })
                }
            }
        }

        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("뽑기 소리", fontFamily = Book.Display, fontSize = 18.sp, modifier = Modifier.weight(1f))
                var sound by remember { mutableStateOf(!Sfx.muted(ctx)) }
                InkSwitch(sound) { sound = it; Sfx.setMuted(ctx, !it) }
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
        OutlinedTextField(
            value = token, onValueChange = { token = it; savedNote = false }, singleLine = true,
            label = { Text("디버그 구매 토큰") }, modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Book.Ink, unfocusedTextColor = Book.Ink,
                focusedBorderColor = Book.Ink, unfocusedBorderColor = Book.Muted, focusedLabelColor = Book.Ink, unfocusedLabelColor = Book.Muted),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
private fun AppsPanel() {
    val ctx = LocalContext.current
    var picking by remember { mutableStateOf(false) }
    var rev by remember { mutableStateOf(0) }
    val apps by produceState<List<kr.dot.catchpet.pet.PetApps.App>>(emptyList(), rev) {
        value = withContext(Dispatchers.IO) { kr.dot.catchpet.pet.PetApps.load(ctx) }
    }
    Panel("두 번 톡톡 누르면") {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (a in apps) Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
                Image(rememberIcon(a.icon), a.label, Modifier.size(40.dp))
                Text(a.label, fontSize = 10.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)
                .clip(RoundedCornerShape(10.dp)).clickable { picking = true }) {
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
    if (picking) AppPicker(onDone = { picking = false; rev++ })
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
    fun close() { kr.dot.catchpet.pet.PetApps.setChosen(ctx, chosen); onDone() }
    androidx.compose.ui.window.Dialog(onDismissRequest = { close() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BookPage {
            Row { InkPill("‹ 펫", onClick = { close() }) }
            Heading("열 앱 고르기", 26.sp)
            Note("${chosen.size}개 골랐어요 · 최대 ${kr.dot.catchpet.pet.PetApps.MAX}개")
            Panel {
                val list = all
                if (list == null) CircularProgressIndicator(color = Book.Ink)
                else for (a in list) {
                    val on = a.pkg in chosen
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                        chosen = when {
                            on -> chosen - a.pkg
                            chosen.size < kr.dot.catchpet.pet.PetApps.MAX -> chosen + a.pkg
                            else -> { android.widget.Toast.makeText(ctx, "최대 ${kr.dot.catchpet.pet.PetApps.MAX}개까지 고를 수 있어요", android.widget.Toast.LENGTH_SHORT).show(); chosen }
                        }
                    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
