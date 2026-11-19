package kr.dot.wherepark.family

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.wherepark.core.ParkRules
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.Member

object FamilyRules {
    const val STALE_MS = 30L * 24 * 3600 * 1000

    fun stale(m: Member, nowMs: Long): Boolean = !m.me && (m.lastSeenMs ?: 0L) < nowMs - STALE_MS

    /** Anyone may remove a member idle 30+ days; the owner may remove anyone but themself. */
    fun canRemove(viewer: Member?, m: Member, nowMs: Long): Boolean =
        !m.me && viewer != null && (viewer.owner || stale(m, nowMs))

    fun lastActive(m: Member, nowMs: Long): String =
        if (m.me) "이 폰" + (if (m.owner) " · 방장" else "")
        else m.lastSeenMs?.let { "마지막 활동 " + ParkRules.whenLabel(it, nowMs) } ?: "마지막 활동 모름"
}

/** 가족 관리: members with last activity, remove (stale for anyone, owner for all), invite, leave. */
class FamilyActivity : ComponentActivity() {
    private var members by mutableStateOf(emptyList<Member>())
    private var group by mutableStateOf("")
    private var removing by mutableStateOf<Member?>(null)
    private var leaving by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)

    override fun onResume() {
        super.onResume()
        load()
        val app = applicationContext
        Thread { Api.state(app).onSuccess { st -> FamilyStore.setState(app, st.groupName, st.members); load() } }.start()
    }

    private fun load() { members = FamilyStore.members(this); group = FamilyStore.groupName(this) ?: "우리 가족" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val now = remember(members) { System.currentTimeMillis() }
            val me = members.firstOrNull { it.me }
            Screen("가족", onBack = { finish() }) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("$group · ${members.size}명", fontSize = 14.sp, color = MU)
                    Card {
                        Column {
                            members.forEach { m ->
                                var menu by remember { mutableStateOf(false) }
                                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(m.name, fontSize = 16.sp, color = FG)
                                        Text(FamilyRules.lastActive(m, now), fontSize = 12.5.sp, color = MU)
                                    }
                                    when {
                                        FamilyRules.stale(m, now) -> Text("내보내기", Modifier.heightIn(min = 48.dp).clickable { removing = m }.padding(horizontal = 8.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FG)
                                        FamilyRules.canRemove(me, m, now) -> Column {
                                            Text("메뉴", Modifier.heightIn(min = 48.dp).clickable { menu = true }.padding(horizontal = 12.dp), fontSize = 14.sp, color = MU)
                                            DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("내보내기") }, { menu = false; removing = m }) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("30일 넘게 안 쓴 폰은 누구나 내보낼 수 있어요. 잃어버린 폰 정리용이에요.", fontSize = 13.sp, color = MU)
                    error?.let { Text(it, fontSize = 13.sp, color = MU) }
                }
                Btn("가족 초대하기", "acc") { startActivity(Intent(this@FamilyActivity, InviteActivity::class.java)) }
                Btn("가족에서 나가기") { leaving = true }
            }
            removing?.let { m ->
                AlertDialog(
                    onDismissRequest = { removing = null },
                    text = { Text("${m.name}을(를) 가족에서 내보낼까요?") },
                    confirmButton = { TextButton({ removing = null; remove(m) }) { Text("내보내기") } },
                    dismissButton = { TextButton({ removing = null }) { Text("취소") } },
                )
            }
            if (leaving) AlertDialog(
                onDismissRequest = { leaving = false },
                text = { Text("가족에서 나갈까요? 내 차와 기록은 이 폰에 남아요.") },
                confirmButton = { TextButton({ leaving = false; leave() }) { Text("나가기") } },
                dismissButton = { TextButton({ leaving = false }) { Text("취소") } },
            )
        }
    }

    private fun remove(m: Member) {
        val app = applicationContext
        Thread {
            Api.removeMember(app, m.id).fold(
                { Api.state(app).onSuccess { FamilyStore.setState(app, it.groupName, it.members) }; error = null; load() },
                { error = "내보내지 못했어요. 다시 해 주세요" },
            )
        }.start()
    }

    private fun leave() {
        val app = applicationContext
        Thread { Api.deleteMe(app).fold({ finish() }, { error = "나가지 못했어요. 네트워크를 확인해 주세요" }) }.start()
    }
}
