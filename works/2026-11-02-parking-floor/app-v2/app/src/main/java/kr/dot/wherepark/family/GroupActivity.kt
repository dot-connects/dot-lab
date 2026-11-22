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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableIntStateOf
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.isMine
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.Group
import kr.dot.wherepark.data.SyncEngine
import kr.dot.wherepark.ui.RoundIcon
import kr.dot.wherepark.ui.Sect
import kr.dot.wherepark.ui.Swatch
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.GroupStore
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

/** 이/가 · 은/는 · 와/과: the particle that follows [word] (Hangul with a final consonant takes the first form). */
internal fun josa(word: String, withBatchim: String, without: String): String {
    val c = word.lastOrNull()
    return word + if (c != null && c in '가'..'힣' && (c - '가') % 28 != 0) withBatchim else without
}

/** The lines of the "그룹에서 나가기" dialog (mockup B-6), built from local `car.groups`; a line only appears when it applies. */
object LeaveText {
    fun of(group: Group, cars: List<Car>, groups: List<Group>, meId: String?): List<String> {
        val inThis = cars.filter { it.isMine(meId) && group.id in it.groups }
        val lost = cars.filter { !it.isMine(meId) && group.id in it.groups && (it.groups - group.id).isEmpty() }
        val stay = inThis.filter { (it.groups - group.id).isNotEmpty() }
        fun names(l: List<Car>) = l.joinToString("·") { it.name }
        val lines = mutableListOf("${group.name}에서 나갈까요?")
        when {
            inThis.isNotEmpty() && lost.isNotEmpty() -> lines += "${josa(names(inThis), "이", "가")} 이 그룹에서 빠지고, ${josa(names(lost), "은", "는")} 내 홈에서 사라져요."
            inThis.isNotEmpty() -> lines += "${josa(names(inThis), "이", "가")} 이 그룹에서 빠져요."
            lost.isNotEmpty() -> lines += "${josa(names(lost), "은", "는")} 내 홈에서 사라져요."
        }
        if (stay.isNotEmpty()) {
            val others = groups.filter { g -> g.id != group.id && stay.any { g.id in it.groups } }.joinToString("·") { it.name }
            lines += "${josa(names(stay), "과", "와")} 기록은 이 폰에 남고, ${others}에서는 계속 보여요."
        }
        if (groups.size <= 1) lines += "마지막 그룹이에요. 나가면 혼자 쓰는 상태가 돼요."
        return lines
    }
}

class GroupActions(
    val back: () -> Unit = {}, val rename: (String) -> Unit = {}, val invite: () -> Unit = {}, val remove: (Member) -> Unit = {},
    val leave: () -> Unit = {}, val openCar: (String) -> Unit = {},
)

/** 그룹 상세 (spec 5.3): name (방장 can rename), members, my cars here (read-only), others' cars, invite, leave. */
@Composable
internal fun GroupScreen(group: Group, cars: List<Car>, groups: List<Group>, meId: String?, now: Long, error: String?, a: GroupActions) {
    var removing by remember { mutableStateOf<Member?>(null) }
    var leaving by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var allMembers by remember { mutableStateOf(false) }
    val members = group.members
    val me = members.firstOrNull { it.me }
    val mine = cars.filter { it.isMine(meId) && group.id in it.groups }
    val others = cars.filter { !it.isMine(meId) && group.id in it.groups }
    WScreen(group.name, onBack = a.back, scroll = true, action = if (group.owner) ({ RoundIcon("pen", "그룹 이름 바꾸기", 19.dp, { renaming = true }) }) else null) {
        WList {
            val shown = if (allMembers) members else members.take(3)
            shown.forEachIndexed { i, m ->
                var menu by remember { mutableStateOf(false) }
                val stale = FamilyRules.stale(m, now)
                val owner = !stale && FamilyRules.canRemove(me, m, now)
                Box {
                    Li(m.name, last = i == shown.lastIndex && (allMembers || members.size <= 3), lead = { Avatar(m.name) }, sub = FamilyRules.lastActive(m, now),
                        right = when { stale -> "내보내기"; m.me -> null; else -> m.lastSeenMs?.let { FamilyRules.ago(it, now) } },
                        rightColor = if (stale) Wp.FG else Wp.MU, rightBold = stale,
                        onClick = when { stale -> ({ removing = m }); owner -> ({ menu = true }); else -> null })
                    DropdownMenu(menu, { menu = false }, Modifier.background(Wp.SF)) {
                        DropdownMenuItem({ Text("그룹에서 내보내기", color = Wp.FG) }, { menu = false; removing = m })
                    }
                }
            }
            if (!allMembers && members.size > 3) Li("${members.size - 3}명 더 보기", last = true, onClick = { allMembers = true })
        }
        Text("30일 넘게 안 쓴 폰은 누구나 내보낼 수 있어요. 잃어버린 폰 정리용이에요.", Modifier.padding(horizontal = 4.dp), style = ts(13.sp, color = Wp.MU))
        Sect("이 그룹에 보이는 내 차")
        WList {
            if (mine.isEmpty()) Li("아직 없어요", last = true)
            mine.forEachIndexed { i, c ->
                val also = groups.filter { it.id != group.id && it.id in c.groups }.joinToString("·") { it.name }
                Li(c.name, last = i == mine.lastIndex, lead = { Swatch(c.color) }, sub = if (also.isEmpty()) null else "${also}에도 보여요", right = "›", onClick = { a.openCar(c.id) })
            }
        }
        Text("내 차를 넣고 빼는 건 차 화면에서 해요", Modifier.padding(horizontal = 4.dp), style = ts(13.sp, color = Wp.MU))
        if (others.isNotEmpty()) {
            Sect("다른 사람 차")
            WList { others.forEachIndexed { i, c -> Li("${c.name} · ${c.ownerName.orEmpty()}님 차", last = i == others.lastIndex, lead = { Swatch(c.color) }, right = "›", onClick = { a.openCar(c.id) }) } }
            Text("다른 사람 차는 차 주인만 넣고 뺄 수 있어요.", Modifier.padding(horizontal = 4.dp), style = ts(13.sp, color = Wp.MU))
        }
        error?.let { Text(it, style = ts(13.sp, color = Wp.DANGER)) }
        Btn("그룹 초대하기", BtnKind.ACC, onClick = a.invite)
        Btn("그룹에서 나가기", BtnKind.GHOST, onClick = { leaving = true })
    }
    removing?.let { m ->
        AlertDialog(
            onDismissRequest = { removing = null }, containerColor = Wp.SF,
            text = { Text("${m.name}을(를) 그룹에서 내보낼까요?") },
            confirmButton = { TextButton({ removing = null; a.remove(m) }) { Text("내보내기", color = Wp.FG) } },
            dismissButton = { TextButton({ removing = null }) { Text("취소", color = Wp.MU) } },
        )
    }
    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false }, containerColor = Wp.SF,
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { LeaveText.of(group, cars, groups, meId).forEach { Text(it) } } },
        confirmButton = { TextButton({ leaving = false; a.leave() }) { Text("나가기", color = Wp.DANGER) } },
        dismissButton = { TextButton({ leaving = false }) { Text("취소", color = Wp.MU) } },
    )
    if (renaming) {
        var text by remember { mutableStateOf(group.name) }
        AlertDialog(
            onDismissRequest = { renaming = false }, containerColor = Wp.SF,
            title = { Text("그룹 이름 바꾸기") },
            text = { OutlinedTextField(text, { text = it.take(40) }, Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = { TextButton({ renaming = false; a.rename(text.trim()) }, enabled = text.isNotBlank()) { Text("바꾸기", color = Wp.FG) } },
            dismissButton = { TextButton({ renaming = false }) { Text("취소", color = Wp.MU) } },
        )
    }
}

class GroupActivity : ComponentActivity() {
    private var version by mutableIntStateOf(0)
    private var error by mutableStateOf<String?>(null)
    private val gid get() = intent.getStringExtra("groupId").orEmpty()

    override fun onResume() {
        super.onResume()
        version++
        val app = applicationContext
        Thread { SyncEngine.pull(app); onUi { version++ } }.start()
    }

    private fun onUi(f: () -> Unit) = runOnUiThread { if (!isFinishing && !isDestroyed) f() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WpTheme(AppPrefs.bigText(this)) {
                val v = version
                val groups = remember(v) { GroupStore.groups(this) }
                val group = groups.firstOrNull { it.id == gid }
                if (group == null) { androidx.compose.runtime.LaunchedEffect(v) { if (v > 1) finish() }; return@WpTheme }
                GroupScreen(group, remember(v) { CarStore.cars(this) }, groups, GroupStore.me(this)?.id, remember(v) { System.currentTimeMillis() }, error, GroupActions(
                    back = { finish() }, rename = ::rename, remove = ::remove, leave = ::leave,
                    invite = { startActivity(Intent(this, InviteActivity::class.java).putExtra("groupId", gid)) },
                    openCar = { id -> startActivity(Intent(this, kr.dot.wherepark.MainActivity::class.java).putExtra("edit_car", id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) },
                ))
            }
        }
    }

    private fun rename(name: String) = work("이름을 바꾸지 못했어요. 인터넷 연결을 확인해 주세요") { Api.renameGroup(it, gid, name) }
    private fun remove(m: Member) = work("내보내지 못했어요. 다시 해 주세요") { Api.removeMember(it, gid, m.id) }

    private fun leave() {
        val app = applicationContext
        Thread {
            val r = Api.leaveGroup(app, gid)
            onUi { if (r.isSuccess) finish() else error = "나가지 못했어요. 인터넷 연결을 확인해 주세요" }
        }.start()
    }

    private fun work(failure: String, call: (android.content.Context) -> Result<Unit>) {
        val app = applicationContext
        Thread {
            val r = call(app)
            if (r.isSuccess) SyncEngine.pull(app)
            onUi { error = if (r.isSuccess) null else failure; version++ }
        }.start()
    }
}
