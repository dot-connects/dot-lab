package kr.dot.catchpet.ui

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
import kr.dot.catchpet.pet.PetWallpaper
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pet settings: which pet, its size, where it shows (over other apps = overlay, home / lock screen = live wallpaper;
 * where on screen is set by dragging the pet itself), and a developer corner.
 */
@Composable
fun PetScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pets by produceState<List<Dex.Entry>>(emptyList()) {
        value = withContext(Dispatchers.IO) { Dex.list(ctx).filter { it.critterId != null || it.ai.owned } }
    }
    var chosen by remember { mutableStateOf(petId(ctx)) }
    var size by remember { mutableStateOf(PetPrefs.size(ctx)) }
    var home by remember { mutableStateOf(PetPrefs.homeOn(ctx)) }
    var lock by remember { mutableStateOf(PetPrefs.lockOn(ctx)) }
    var on by remember { mutableStateOf(PetPrefs.overlayOn(ctx)) }
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(ctx)) }
    var photoNote by remember { mutableStateOf<String?>(null) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canDraw = Settings.canDrawOverlays(ctx) }

    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { PetService.start(ctx); on = true }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { copyWallpaper(ctx, uri) }.isSuccess }
            photoNote = if (ok) "배경 사진을 바꿨어요" else "사진을 읽지 못했어요"
        }
    }
    fun restartIfOn() { if (on) PetService.start(ctx) }

    Column(Modifier.fillMaxSize().background(BG).safeDrawingPadding().verticalScroll(rememberScrollState())) {
        TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("‹ 뽑기", color = DIM, fontSize = 16.sp) }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("펫", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            Title("누구를 띄울까요")
            if (pets.isEmpty()) Text("털 녀석을 뽑거나 진짜 녀석의 펫 그림을 사면 여기에 나와요.", color = DIM, fontSize = 14.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (e in pets) Column(
                    Modifier.width(84.dp).clip(RoundedCornerShape(16.dp))
                        .border(2.dp, if (e.id == chosen) GOLD else Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                        .clickable { setPet(ctx, e.id); chosen = e.id; restartIfOn() }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    rememberPose(e, "front")?.let { Image(it, e.name, Modifier.size(60.dp), contentScale = ContentScale.Fit) }
                        ?: Spacer(Modifier.size(60.dp))
                    Text(e.name, color = Color.White, fontSize = 13.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(24.dp))

            Title("크기")
            Slider(
                value = size, valueRange = 32f..112f,
                onValueChange = { size = it },
                onValueChangeFinished = { PetPrefs.setSize(ctx, size); restartIfOn() },
                colors = sliderColors(),
            )
            Text("녀석을 끌어서 아래 줄이나 양옆 벽에 놓을 수 있어요.", color = DIM, fontSize = 14.sp)
            Spacer(Modifier.height(24.dp))

            Title("어디에 보일까요")
            when {
                chosen == null -> Text("도감에서 펫을 먼저 골라 주세요.", color = DIM, fontSize = 14.sp)
                else -> {
                    Toggle("다른 앱 위", on) {
                        when {
                            !it -> { PetService.stop(ctx); on = false }
                            !canDraw -> ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                            else -> notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    if (!canDraw) Text("켜려면 '다른 앱 위에 표시' 권한을 허용해 주세요.", color = DIM, fontSize = 13.sp)
                    Toggle("홈 화면", home) {
                        home = it; PetPrefs.setShow(ctx, "show_home", it)
                        if (it && !wallpaperIsOurs(ctx)) openWallpaperPicker(ctx)
                    }
                    Toggle("잠금화면", lock) {
                        lock = it; PetPrefs.setShow(ctx, "show_lock", it)
                        if (it && !wallpaperIsOurs(ctx)) openWallpaperPicker(ctx)
                    }
                    if (home || lock) {
                        Text(
                            "홈·잠금화면의 녀석은 배경화면 '우리집 몬스터'가 그려요. 잠금화면은 배경화면을 '홈 및 잠금화면'에 지정해야 하고, 폰에 따라 안 될 수 있어요.",
                            color = DIM, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { openWallpaperPicker(ctx) }) { Text("배경화면 설정 열기", color = GOLD) }
                            TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                Text("배경 사진 고르기", color = GOLD)
                            }
                        }
                        photoNote?.let { Text(it, color = DIM, fontSize = 13.sp) }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
            Developer(ctx)
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Developer(ctx: Context) {
    val prefs = remember { PetPrefs.prefs(ctx) }
    var token by remember { mutableStateOf(Purchase.token(ctx) ?: "") }
    var savedNote by remember { mutableStateOf(false) }
    var scale by remember { mutableStateOf(prefs.getFloat("critter_scale", 0.25f)) }
    var ar by remember { mutableStateOf(prefs.getBoolean("ar", false)) }
    Title("개발자")
    OutlinedTextField(
        value = token, onValueChange = { token = it; savedNote = false }, singleLine = true,
        label = { Text("디버그 구매 토큰") }, modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White,
            focusedBorderColor = GOLD, unfocusedBorderColor = DIM, focusedLabelColor = GOLD, unfocusedLabelColor = DIM),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { Purchase.saveToken(ctx, token); savedNote = true }) { Text("저장", color = GOLD) }
        if (savedNote) Text(if (token.isBlank()) "토큰을 지웠어요" else "저장했어요", color = DIM, fontSize = 13.sp)
    }
    Spacer(Modifier.height(12.dp))
    // critter height = scale x 40 % of the screen: 0.1 -> 4 %, 0.6 -> 24 % (1/4 and 1/5 looked the same, 11-13)
    Text("털 녀석 크기  ${(scale * 40).roundToInt()}% (화면 높이 대비)", color = Color.White, fontSize = 15.sp)
    Slider(
        value = scale, valueRange = 0.1f..0.6f,
        onValueChange = { scale = it },
        onValueChangeFinished = { prefs.edit().putFloat("critter_scale", scale).apply() },
        colors = sliderColors(),
    )
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("AR 털 녀석 (실험)", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = ar, onCheckedChange = { ar = it; prefs.edit().putBoolean("ar", it).apply() })
    }
    Text("털 녀석을 실제 바닥에 붙여 둬요. 뽑기 화면에 들어가면 켜졌는지 알려 줘요.", color = DIM, fontSize = 13.sp)
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(thumbColor = GOLD, activeTrackColor = GOLD, inactiveTrackColor = Color(0x33FFFFFF))

private fun wallpaperIsOurs(ctx: Context): Boolean =
    WallpaperManager.getInstance(ctx).wallpaperInfo?.component == ComponentName(ctx, PetWallpaper::class.java)

@Composable
private fun Title(t: String) {
    Text(t, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
}

private fun openWallpaperPicker(ctx: Context) {
    val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(ctx, PetWallpaper::class.java))
    runCatching { ctx.startActivity(direct) }
        .recoverCatching { ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
}

/** Copies the picked photo (upright, long side <= ~2000 px) to filesDir/wallpaper.jpg; the URI is not kept (plan R9). */
private fun copyWallpaper(ctx: Context, uri: Uri) {
    val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
        val long = max(info.size.width, info.size.height)
        var s = 1
        while (long / (s * 2) >= 2000) s *= 2
        dec.setTargetSampleSize(s)
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }
    val bytes = ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    Dex.atomicWrite(File(ctx.filesDir, "wallpaper.jpg"), bytes)
}
