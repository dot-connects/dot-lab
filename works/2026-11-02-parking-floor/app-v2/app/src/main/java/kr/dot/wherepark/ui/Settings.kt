package kr.dot.wherepark.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kr.dot.wherepark.BuildConfig
import kr.dot.wherepark.car.ExitNotifier
import kr.dot.wherepark.data.CarLinkStore
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.family.Push

object AppPrefs {
    const val PRIVACY_URL = "https://dot-connects.github.io/dot-lab/works/2026-11-02-parking-floor/privacy.html"
    const val CONTACT = "dotconnects.lab@gmail.com"

    fun bigText(ctx: Context): Boolean = Prefs.sp(ctx).getString("wp_big_text", null) != null
    fun setBigText(ctx: Context, on: Boolean) = Prefs.put(ctx, "wp_big_text", if (on) "1" else null)

    /** The name typed at onboarding, kept so [새 가족 만들기] after a 401 can recreate the family without asking again. */
    fun myName(ctx: Context): String? = Prefs.sp(ctx).getString("wp_my_name", null)
    fun setMyName(ctx: Context, name: String) = Prefs.put(ctx, "wp_my_name", name)
}

class SettingsActions(
    val back: () -> Unit = {}, val editCar: (String?) -> Unit = {}, val testNotice: () -> Unit = {}, val family: () -> Unit = {},
    val privacy: () -> Unit = {}, val deleteAll: () -> Unit = {}, val copyContact: () -> Unit = {}, val bigText: (Boolean) -> Unit = {}, val widget: () -> Unit = {},
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
            Li("홈 화면에 위젯 놓기", last = true, right = "›", onClick = a.widget)
        }
        Sect("정보")
        WList {
            Li("개인정보 처리방침", right = "›", onClick = a.privacy)
            Li("내 데이터 지우기", right = "›", onClick = { askDelete = true })
            Li("문의", right = if (contact) AppPrefs.CONTACT else "›", onClick = { if (contact) a.copyContact() else contact = true })
            Li("버전", last = true, right = BuildConfig.VERSION_NAME)
        }
    }
    if (askDelete) AlertDialog(
        onDismissRequest = { askDelete = false },
        text = { Text("이 폰의 차·기록·가족 정보를 모두 지우고 가족에서 나갈까요? 되돌릴 수 없어요.") },
        confirmButton = { TextButton({ askDelete = false; a.deleteAll() }) { Text("모두 지우기", color = Wp.DANGER) } },
        dismissButton = { TextButton({ askDelete = false }) { Text("취소", color = Wp.FG) } },
        containerColor = Wp.SF,
    )
}
