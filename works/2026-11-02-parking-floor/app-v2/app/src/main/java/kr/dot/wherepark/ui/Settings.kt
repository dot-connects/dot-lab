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
import kr.dot.wherepark.data.CarLinkStore
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.isMine
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.Group
import kr.dot.wherepark.data.GroupStore
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.data.WidgetRefresh
import kr.dot.wherepark.widget.WidgetReceiver
import kr.dot.wherepark.widget.widgetLabel
import kr.dot.wherepark.widget.widgetThumb

object AppPrefs {
    const val PRIVACY_URL = "https://dot-connects.github.io/dot-lab/works/2026-11-02-parking-floor/privacy.html"

    fun bigText(ctx: Context): Boolean = Prefs.sp(ctx).getString("wp_big_text", null) != null
    fun setBigText(ctx: Context, on: Boolean) = Prefs.put(ctx, "wp_big_text", if (on) "1" else null)

    /** The name typed on the first group create/join (spec 5.7), kept locally for the 401 band. */
    fun myName(ctx: Context): String? = Prefs.sp(ctx).getString("wp_my_name", null)
    fun setMyName(ctx: Context, name: String) = Prefs.put(ctx, "wp_my_name", name)

    /** 메인 차량 for the widgets (null = first registered car); the widget falls back to the first car when this one is deleted. */
    fun mainCar(ctx: Context): String? = Prefs.sp(ctx).getString("wp_main_car", null)
    fun setMainCar(ctx: Context, id: String?) { Prefs.put(ctx, "wp_main_car", id); WidgetRefresh.request(ctx) }
}

class SettingsActions(
    val back: () -> Unit = {}, val editCar: (String?) -> Unit = {}, val testNotice: () -> Unit = {}, val group: (String) -> Unit = {},
    val addGroup: () -> Unit = {}, val privacy: () -> Unit = {}, val deleteAll: () -> Unit = {}, val bigText: (Boolean) -> Unit = {}, val widget: (Int) -> Unit = {},   // appWidgetId to reconfigure, or INVALID_APPWIDGET_ID = pin a new one

)

/** "내 차 1대 · 다른 사람 차 2대" under a group row. */
internal fun groupSummary(g: Group, cars: List<Car>, meId: String?): String =
    "내 차 ${cars.count { it.isMine(meId) && g.id in it.groups }}대 · 다른 사람 차 ${cars.count { !it.isMine(meId) && g.id in it.groups }}대"

/**
 * 설정 (spec 5.2): 내 차 (mine only, group names under each) · 그룹 · 알림 (시험 only; per-car switches live on the car screen) ·
 * 보기 · 정보.
 */
@Composable
fun SettingsScreen(a: SettingsActions) {
    val ctx = LocalContext.current
    var v by remember { mutableIntStateOf(0) }   // bumped by each toggle so the rows re-read the stores
    var askDelete by remember { mutableStateOf(false) }
    var pickMain by remember { mutableStateOf(false) }
    var pickWidget by remember { mutableStateOf<List<Int>?>(null) }
    val groups = remember(v) { GroupStore.groups(ctx) }
    val meId = remember(v) { GroupStore.me(ctx)?.id }
    val all = remember(v) { CarStore.cars(ctx) }
    val cars = remember(v) { all.filter { it.isMine(meId) } }
    WScreen("설정", gap = 10.dp, scroll = true) {
        Sect("내 차")
        WList {
            cars.forEach { c ->
                val names = groups.filter { it.id in c.groups }.joinToString(" · ") { it.name }
                Li(c.name, lead = { Swatch(c.color) }, sub = names.ifEmpty { null }, right = if (CarLinkStore.address(ctx, c.id) != null) "차 블루투스 연결됨" else "연결 안 함",
                    last = cars.size >= 4 && c == cars.last(), onClick = { a.editCar(c.id) })
            }
            if (cars.size < 4) Li("＋ 차 추가", last = true, onClick = { a.editCar(null) })
        }
        Sect("그룹")
        WList {
            if (groups.isEmpty()) Li("아직 혼자 쓰고 있어요", sub = "그룹을 만들면 가족·동료 폰에서도 이 차 자리가 보여요. 내 위치는 공유되지 않아요.")
            groups.forEach { g -> Li("${g.name} · ${g.members.size}명", sub = groupSummary(g, all, meId), right = "›", onClick = { a.group(g.id) }) }
            if (groups.size < 3) Li("＋ 그룹 추가", last = true, onClick = a.addGroup)
        }
        Sect("알림")
        WList { Li("알림 시험", last = true, right = "지금 한 번 보내 보기 ›", onClick = a.testNotice) }
        Text("차마다 알림은 위 '내 차'에서 차를 눌러 바꿔요", Modifier.padding(horizontal = 4.dp), style = ts(13.sp, color = Wp.MU))
        Sect("보기")
        WList {
            ToggleLi("글자 크게", v >= 0 && AppPrefs.bigText(ctx)) { AppPrefs.setBigText(ctx, it); v++; a.bigText(it) }
            if (all.size > 1) {
                val main = all.firstOrNull { it.id == AppPrefs.mainCar(ctx) } ?: cars.firstOrNull() ?: all.first()
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
            Li("버전", last = true, right = "${BuildConfig.VERSION_NAME} · ${BuildConfig.GIT_SHA}")
        }
    }
    if (pickMain) AlertDialog(
        onDismissRequest = { pickMain = false },
        title = { Text("위젯 메인 차량") },
        text = {
            Column {
                val main = all.firstOrNull { it.id == AppPrefs.mainCar(ctx) } ?: cars.firstOrNull() ?: all.firstOrNull()
                all.forEach { c ->
                    Li(c.name, lead = { Swatch(c.color) }, sub = if (c.isMine(meId)) null else "${c.ownerName.orEmpty()}님 차", right = if (c == main) "✓" else null, last = c == all.last(),
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
        text = { Text("이 폰의 차·기록·그룹 정보를 모두 지우고 모든 그룹에서 나갈까요? 되돌릴 수 없어요.") },
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
