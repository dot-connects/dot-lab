package kr.dot.wherepark.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.wherepark.BuildConfig
import kr.dot.wherepark.car.ExitNotifier
import kr.dot.wherepark.data.CarLinkStore
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.data.WidgetRefresh
import kr.dot.wherepark.family.Push
import kr.dot.wherepark.widget.WidgetReceiver
import kr.dot.wherepark.widget.widgetLabel
import kr.dot.wherepark.widget.widgetThumb

object AppPrefs {
    const val PRIVACY_URL = "https://dot-connects.github.io/dot-lab/works/2026-11-02-parking-floor/privacy.html"
    const val CONTACT = "dotconnects.lab@gmail.com"

    fun bigText(ctx: Context): Boolean = Prefs.sp(ctx).getString("wp_big_text", null) != null
    fun setBigText(ctx: Context, on: Boolean) = Prefs.put(ctx, "wp_big_text", if (on) "1" else null)

    /** The name typed at onboarding, kept so [새 가족 만들기] after a 401 can recreate the family without asking again. */
    fun myName(ctx: Context): String? = Prefs.sp(ctx).getString("wp_my_name", null)
    fun setMyName(ctx: Context, name: String) = Prefs.put(ctx, "wp_my_name", name)

    /** 메인 차량 for the widgets (null = first registered car); the widget falls back to the first car when this one is deleted. */
    fun mainCar(ctx: Context): String? = Prefs.sp(ctx).getString("wp_main_car", null)
    fun setMainCar(ctx: Context, id: String?) { Prefs.put(ctx, "wp_main_car", id); WidgetRefresh.request(ctx) }
}

class SettingsActions(
    val back: () -> Unit = {}, val editCar: (String?) -> Unit = {}, val testNotice: () -> Unit = {}, val family: () -> Unit = {},
    val privacy: () -> Unit = {}, val deleteAll: () -> Unit = {}, val copyContact: () -> Unit = {}, val bigText: (Boolean) -> Unit = {}, val widget: (Int) -> Unit = {},   // appWidgetId to reconfigure, or INVALID_APPWIDGET_ID = pin a new one

)

/**
 * 설정 (mockup section 7): 내 차 · 알림 (this phone only, per car: exit prompt / family parked / family drove off, [알림 시험]) ·
 * 가족 · 보기 (글자 크게) · 정보. Toggles write the stores directly.
 */
@Composable
fun SettingsScreen(a: SettingsActions) {
    val ctx = LocalContext.current
    var v by remember { mutableIntStateOf(0) }   // bumped by each toggle so the rows re-read the stores
    var askDelete by remember { mutableStateOf(false) }
    var contact by remember { mutableStateOf(false) }
    var pickMain by remember { mutableStateOf(false) }
    var pickWidget by remember { mutableStateOf<List<Int>?>(null) }
    val cars = remember(v) { CarStore.cars(ctx) }
    WScreen("설정", gap = 10.dp, scroll = true) {
        Sect("내 차")
        WList {
            cars.forEach { c ->
                Li(c.name, lead = { Swatch(c.color) }, right = if (CarLinkStore.address(ctx, c.id) != null) "차 블루투스 연결됨" else "연결 안 함",
                    last = cars.size >= 4 && c == cars.last(), onClick = { a.editCar(c.id) })
            }
            if (cars.size < 4) Li("＋ 차 추가", last = true, onClick = { a.editCar(null) })
        }
        Sect("알림 · 나만 적용돼요")
        WList {
            cars.forEach { c ->
                if (cars.size > 1) Li(c.name, lead = { Swatch(c.color) }, textColor = Wp.MU)
                ToggleLi("차에서 내리면 물어보기", v >= 0 && ExitNotifier.enabled(ctx, c.id)) { ExitNotifier.setEnabled(ctx, c.id, it); v++ }
                ToggleLi("가족이 댔을 때", v >= 0 && Push.enabled(ctx, "park:${c.id}")) { Push.setEnabled(ctx, "park:${c.id}", it); v++ }
                ToggleLi("가족이 가져갔을 때", v >= 0 && Push.enabled(ctx, "drive:${c.id}")) { Push.setEnabled(ctx, "drive:${c.id}", it); v++ }
            }
            Li("알림 시험", last = true, right = "지금 한 번 보내 보기 ›", onClick = a.testNotice)
        }
        Sect("가족")
        WList {
            val g = FamilyStore.groupName(ctx)
            Li(if (g != null) "$g · ${FamilyStore.members(ctx).size}명" else if (FamilyStore.pendingGroup(ctx) != null) "가족 만드는 중" else "초대 코드로 들어가기",
                last = true, right = "›", onClick = a.family)
        }
        Sect("보기")
        WList {
            ToggleLi("글자 크게", v >= 0 && AppPrefs.bigText(ctx)) { AppPrefs.setBigText(ctx, it); v++; a.bigText(it) }
            if (cars.size > 1) {
                val main = cars.firstOrNull { it.id == AppPrefs.mainCar(ctx) } ?: cars.first()
                Li("위젯 메인 차량", lead = { Swatch(main.color) }, right = "${main.name} ›", onClick = { pickMain = true })
            }
            Li("위젯 꾸미기", last = true, right = "›", onClick = {
                val ids = WidgetReceiver.realIds(ctx).sorted()
                when (ids.size) { 0 -> a.widget(AppWidgetManager.INVALID_APPWIDGET_ID); 1 -> a.widget(ids[0]); else -> pickWidget = ids }
            })
        }
        Sect("정보")
        WList {
            Li("개인정보 처리방침", right = "›", onClick = a.privacy)
            Li("내 데이터 지우기", right = "›", onClick = { askDelete = true })
            Li("문의", right = if (contact) AppPrefs.CONTACT else "›", onClick = { if (contact) a.copyContact() else contact = true })
            Li("버전", last = true, right = BuildConfig.VERSION_NAME)
        }
    }
    if (pickMain) AlertDialog(
        onDismissRequest = { pickMain = false },
        title = { Text("위젯 메인 차량") },
        text = {
            Column {
                val main = cars.firstOrNull { it.id == AppPrefs.mainCar(ctx) } ?: cars.firstOrNull()
                cars.forEach { c ->
                    Li(c.name, lead = { Swatch(c.color) }, right = if (c == main) "✓" else null, last = c == cars.last(),
                        onClick = { AppPrefs.setMainCar(ctx, c.id); v++; pickMain = false })
                }
            }
        },
        confirmButton = { TextButton({ pickMain = false }) { Text("닫기", color = Wp.FG) } },
        containerColor = Wp.SF,
    )
    pickWidget?.let { ids ->
        AlertDialog(
            onDismissRequest = { pickWidget = null },
            title = { Text("어떤 위젯을 꾸밀까요?") },
            text = { WidgetPicker(ids) { id -> pickWidget = null; a.widget(id) } },
            confirmButton = { TextButton({ pickWidget = null }) { Text("닫기", color = Wp.FG) } },
            containerColor = Wp.SF,
        )
    }
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        text = { Text("이 폰의 차·기록·가족 정보를 모두 지우고 가족에서 나갈까요? 되돌릴 수 없어요.") },
        confirmButton = { TextButton({ askDelete = false; a.deleteAll() }) { Text("모두 지우기", color = Wp.DANGER) } },
        dismissButton = { TextButton({ askDelete = false }) { Text("취소", color = Wp.FG) } },
        containerColor = Wp.SF,
    )
}

/** 위젯 꾸미기 with several widgets: each one as it is drawn now (its own settings and size) over the wallpaper colours, caption under it. */
@Composable
internal fun WidgetPicker(ids: List<Int>, onPick: (Int) -> Unit) {
    val ctx = LocalContext.current
    val density = LocalDensity.current.density
    val wall = remember { WidgetReceiver.wallpaper(ctx) }
    // drawing every size is slow: off the main thread, a placeholder box until it is ready
    val thumbs by produceState<List<android.graphics.Bitmap>?>(null, ids) {
        value = withContext(kotlinx.coroutines.Dispatchers.Default) { ids.map { widgetThumb(ctx, it, density, wall) } }
    }
    val grad = Brush.verticalGradient(wall.map { Color(it) }.let { if (it.size == 1) it + it else it })
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ids.forEachIndexed { i, id ->
            val bmp = thumbs?.get(i)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button) { onPick(id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // one scale for all, so a 2x1 reads smaller than a 4x2 as on the home screen
                BoxWithConstraints(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(grad).padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (bmp == null) Box(Modifier.fillMaxWidth().height(80.dp))
                    else {
                        val k = minOf(1f, maxWidth.value / (thumbs!!.maxOf { it.width } / density))
                        Image(bmp.asImageBitmap(), contentDescription = null,
                            modifier = Modifier.size((bmp.width / density * k).dp, (bmp.height / density * k).dp))
                    }
                }
                Text(widgetLabel(ctx, i + 1, id), style = ts(13.sp, color = Wp.FG), modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
