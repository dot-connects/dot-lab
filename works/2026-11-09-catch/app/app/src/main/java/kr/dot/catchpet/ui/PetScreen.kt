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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import kr.dot.catchpet.pet.PetBrain.Zone
import kr.dot.catchpet.pet.PetPrefs
import kr.dot.catchpet.pet.PetService
import kr.dot.catchpet.pet.PetWallpaper
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max

private val PLACES = listOf(Zone.FLOOR to "아래 줄", Zone.LEFT to "왼쪽 벽", Zone.RIGHT to "오른쪽 벽", Zone.BEHIND to "아이콘 뒤")

/** Pet settings: which pet, where it lives, overlay on/off (with the permission steps), and a developer corner. */
@Composable
fun PetScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pets by produceState<List<Dex.Entry>>(emptyList()) {
        value = withContext(Dispatchers.IO) { Dex.list(ctx).filter { it.critterId != null || it.ai.owned } }
    }
    var chosen by remember { mutableStateOf(petId(ctx)) }
    var place by remember { mutableStateOf(PetPrefs.place(ctx)) }
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
    fun restartIfOn() { if (on && place != Zone.BEHIND) PetService.start(ctx) }

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

            Title("어디에 살까요")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((z, label) in PLACES) FilterChip(
                    selected = place == z, label = { Text(label) }, colors = chipColors(),
                    onClick = {
                        place = z; PetPrefs.setPlace(ctx, z)
                        if (z == Zone.BEHIND) {
                            if (on) { PetService.stop(ctx); on = false }
                            openWallpaperPicker(ctx)
                        } else restartIfOn()
                    },
                )
            }
            if (place == Zone.BEHIND) {
                Text("배경화면을 '우리집 몬스터'로 바꾸면 녀석이 아이콘 뒤를 걸어 다녀요.", color = DIM, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { openWallpaperPicker(ctx) }) { Text("배경화면 설정 열기", color = GOLD) }
                    TextButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Text("배경 사진 고르기", color = GOLD)
                    }
                }
                photoNote?.let { Text(it, color = DIM, fontSize = 13.sp) }
            } else {
                Spacer(Modifier.height(16.dp))
                when {
                    chosen == null -> Text("도감에서 펫을 먼저 골라 주세요.", color = DIM, fontSize = 14.sp)
                    !canDraw -> {
                        Text("화면 위에 띄우려면 '다른 앱 위에 표시' 권한을 켜 주세요.", color = Color.White, fontSize = 15.sp)
                        Spacer(Modifier.height(8.dp))
                        BigButton("권한 설정 열기") {
                            ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
                        }
                    }
                    on -> BigButton("펫 내리기", gold = false) { PetService.stop(ctx); on = false }
                    else -> BigButton("펫 띄우기") { notify.launch(Manifest.permission.POST_NOTIFICATIONS) }
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
    Text("털 녀석 크기", color = Color.White, fontSize = 15.sp)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((v, label) in listOf(0.25f to "1/4", 0.2f to "1/5")) FilterChip(
            selected = scale == v, label = { Text(label) }, colors = chipColors(),
            onClick = { scale = v; prefs.edit().putFloat("critter_scale", v).apply() },
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("AR 털 녀석 (실험)", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = ar, onCheckedChange = { ar = it; prefs.edit().putBoolean("ar", it).apply() })
    }
}

@Composable
private fun Title(t: String) {
    Text(t, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 10.dp))
}

@Composable
private fun BigButton(label: String, gold: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = if (gold) ButtonDefaults.buttonColors(containerColor = GOLD, contentColor = Color(0xFF2A1D00))
        else ButtonDefaults.buttonColors(containerColor = Color(0x26FFFFFF), contentColor = Color.White),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(label, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun chipColors() =
    FilterChipDefaults.filterChipColors(labelColor = DIM, selectedContainerColor = GOLD, selectedLabelColor = Color(0xFF2A1D00))

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
