package kr.dot.wherepark.family

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.Member
import kr.dot.wherepark.ui.AppPrefs
import kr.dot.wherepark.ui.Btn
import kr.dot.wherepark.ui.BtnKind
import kr.dot.wherepark.ui.Li
import kr.dot.wherepark.ui.WList
import kr.dot.wherepark.ui.WScreen
import kr.dot.wherepark.ui.WpTheme
import kr.dot.wherepark.ui.Wp
import kr.dot.wherepark.ui.ts

object FamilyRules {
    const val STALE_MS = 30L * 24 * 3600 * 1000

    /** No lastSeen = never stale here: the server counts from the join time (coalesce(last_seen, created)), which we do not have. */
    fun stale(m: Member, nowMs: Long): Boolean = !m.me && m.lastSeenMs != null && m.lastSeenMs < nowMs - STALE_MS

    /** Anyone may remove a member idle 30+ days; the owner may remove anyone but themself. */
    fun canRemove(viewer: Member?, m: Member, nowMs: Long): Boolean =
        !m.me && viewer != null && (viewer.owner || stale(m, nowMs))

    /** "방금" · "12분 전" · "2시간 전" · "3일 전". */
    fun ago(atMs: Long, nowMs: Long): String {
        val m = ((nowMs - atMs) / 60_000).coerceAtLeast(0)
        return when { m < 1 -> "방금"; m < 60 -> "${m}분 전"; m < 1440 -> "${m / 60}시간 전"; else -> "${m / 1440}일 전" }
    }

    fun lastActive(m: Member, nowMs: Long): String =
        if (m.me) "이 폰" + (if (m.owner) " · 방장" else "")
        else m.lastSeenMs?.let { "마지막 활동 " + ago(it, nowMs) } ?: "마지막 활동 모름"
}

/** Mockup .avatars span: 38 dp circle with the first two letters. */
@Composable
private fun Avatar(name: String) = Box(Modifier.size(38.dp).clip(CircleShape).background(Wp.BG).border(3.dp, Wp.SF, CircleShape), Alignment.Center) {
    Text(name.take(2), style = ts(12.sp, FontWeight.Bold, lh = 1.2f))
}

/** 가족 관리 (mockup section 6): members with last activity, 30-day idle [내보내기] for anyone, the owner's row menu, invite, leave. */
@Composable
internal fun FamilyScreen(group: String, members: List<Member>, now: Long, error: String?, back: () -> Unit, invite: () -> Unit,
                          remove: (Member) -> Unit, leave: () -> Unit) {
    var removing by remember { mutableStateOf<Member?>(null) }
    var leaving by remember { mutableStateOf(false) }
    val me = members.firstOrNull { it.me }
    WScreen("가족", onBack = back) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            WList {
                members.forEachIndexed { i, m ->
                    var menu by remember { mutableStateOf(false) }
                    val stale = FamilyRules.stale(m, now)
                    val owner = !stale && FamilyRules.canRemove(me, m, now)
                    Box {
                        Li(m.name, last = i == members.lastIndex, lead = { Avatar(m.name) }, sub = FamilyRules.lastActive(m, now),
                            right = when { stale -> "내보내기"; m.me -> null; else -> m.lastSeenMs?.let { FamilyRules.ago(it, now) } },
                            rightColor = if (stale) Wp.FG else Wp.MU, rightBold = stale,
                            onClick = when { stale -> ({ removing = m }); owner -> ({ menu = true }); else -> null })
                        DropdownMenu(menu, { menu = false }, Modifier.background(Wp.SF)) {
                            DropdownMenuItem({ Text("가족에서 내보내기", color = Wp.FG) }, { menu = false; removing = m })
                        }
                    }
                }
            }
            Text("30일 넘게 안 쓴 폰은 누구나 내보낼 수 있어요. 잃어버린 폰 정리용이에요.", Modifier.padding(horizontal = 4.dp), style = ts(13.sp, color = Wp.MU))
            error?.let { Text(it, style = ts(13.sp, color = Wp.DANGER)) }
        }
        Btn("가족 초대하기", BtnKind.ACC, onClick = invite)
        Btn("가족에서 나가기", BtnKind.GHOST, onClick = { leaving = true })
    }
    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null }, containerColor = Wp.SF,
            text = { Text("${m.name}을(를) 가족에서 내보낼까요?") },
            confirmButton = { TextButton({ removing = null; remove(m) }) { Text("내보내기", color = Wp.FG) } },
            dismissButton = { TextButton({ removing = null }) { Text("취소", color = Wp.MU) } },
        )
    }
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false }, containerColor = Wp.SF,
        text = { Text("가족에서 나갈까요? 내 차와 기록은 이 폰에 남아요.") },
        confirmButton = { TextButton({ leaving = false; leave() }) { Text("나가기", color = Wp.DANGER) } },
        dismissButton = { TextButton({ leaving = false }) { Text("취소", color = Wp.MU) } },
    )
}

class FamilyActivity : ComponentActivity() {
    private var members by mutableStateOf(emptyList<Member>())
    private var group by mutableStateOf("")
    private var error by mutableStateOf<String?>(null)

    override fun onResume() {
        super.onResume()
        load()
        val app = applicationContext
        Thread { Api.state(app).onSuccess { st -> FamilyStore.setState(app, st.groupName, st.members); onUi { load() } } }.start()
    }

    private fun onUi(f: () -> Unit) = runOnUiThread { if (!isFinishing && !isDestroyed) f() }

    /** Main thread only (Compose state). */
    private fun load() { members = FamilyStore.members(this); group = FamilyStore.groupName(this) ?: "우리 가족" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WpTheme(AppPrefs.bigText(this)) {
                FamilyScreen(group, members, remember(members) { System.currentTimeMillis() }, error, back = { finish() },
                    invite = { startActivity(Intent(this, InviteActivity::class.java)) }, remove = ::remove, leave = ::leave)
            }
        }
    }

    private fun remove(m: Member) {
        val app = applicationContext
        Thread {
            val r = Api.removeMember(app, m.id)
            if (r.isSuccess) Api.state(app).onSuccess { FamilyStore.setState(app, it.groupName, it.members) }
            onUi { error = if (r.isSuccess) null else "내보내지 못했어요. 다시 해 주세요"; load() }
        }.start()
    }

    private fun leave() {
        val app = applicationContext
        Thread {
            val r = Api.deleteMe(app)
            onUi { if (r.isSuccess) finish() else error = "나가지 못했어요. 인터넷 연결을 확인해 주세요" }
        }.start()
    }
}
