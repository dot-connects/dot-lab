package kr.dot.wherepark.family

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.ApiException
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.GroupStore
import kr.dot.wherepark.data.Op
import kr.dot.wherepark.data.SyncQueue
import kr.dot.wherepark.data.WidgetRefresh
import kr.dot.wherepark.ui.AppPrefs
import kr.dot.wherepark.ui.Btn
import kr.dot.wherepark.ui.BtnKind
import kr.dot.wherepark.ui.FieldCard
import kr.dot.wherepark.ui.Li
import kr.dot.wherepark.ui.Sect
import kr.dot.wherepark.ui.Swatch
import kr.dot.wherepark.ui.ToggleLi
import kr.dot.wherepark.ui.WList
import kr.dot.wherepark.ui.WScreen
import kr.dot.wherepark.ui.Wp
import kr.dot.wherepark.ui.WpTheme
import kr.dot.wherepark.ui.gro
import kr.dot.wherepark.ui.trimLine
import kr.dot.wherepark.ui.ts

object AddGroupCopy {
    const val MAX_GROUPS = 3

    /** Create / join failures (spec 5.4): the server's code, else offline. */
    fun error(e: Throwable): String = when ((e as? ApiException)?.error) {
        "member" -> "이미 들어가 있는 그룹이에요"
        "groups" -> "그룹은 3개까지예요"
        "full" -> "그룹이 꽉 찼어요"
        else -> if ((e as? ApiException)?.status == 400) "그룹 이름을 확인해 주세요" else "인터넷 연결을 확인해 주세요"
    }

    /** True when [error] is one of the codes [error] words specially. */
    fun known(e: Throwable): Boolean = (e as? ApiException)?.error in setOf("member", "groups", "full")

    fun footer(count: Int) = "그룹은 ${MAX_GROUPS}개까지 만들거나 들어갈 수 있어요 · 지금 ${count}개"

    /** Shares [carIds] into [groupId]: local car.groups now, `Share` ops to the server. */
    fun share(ctx: Context, carIds: Collection<String>, groupId: String) {
        synchronized(SyncQueue) {
            CarStore.cars(ctx).filter { it.id in carIds }.forEach { c ->
                CarStore.upsert(ctx, c.copy(groups = (c.groups + groupId).distinct()))
                SyncQueue.add(ctx, Op.Share(c.id, groupId, true))
            }
        }
        SyncQueue.kick(ctx)
        WidgetRefresh.request(ctx)
    }
}

/** A list of my cars with a switch each (create: all on, join: all off) and one button. */
@Composable
internal fun CarPicker(cars: List<Car>, on: Set<String>, toggle: (String) -> Unit) {
    Sect("이 그룹에 넣을 내 차")
    WList { cars.forEachIndexed { i, c -> ToggleLi(c.name, c.id in on, last = i == cars.lastIndex) { toggle(c.id) } } }
}

/** 새 그룹 만들기 (spec 5.4 / D8): group name (+ my name only without a device) + my cars, all on. */
@Composable
internal fun CreateGroupScreen(needName: Boolean, cars: List<Car>, busy: Boolean, error: String?, back: () -> Unit, create: (String, String, Set<String>) -> Unit) {
    var group by remember { mutableStateOf("") }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var me by remember { mutableStateOf(AppPrefs.myName(ctx).orEmpty()) }
    var on by remember { mutableStateOf(cars.map { it.id }.toSet()) }
    WScreen("새 그룹 만들기", onBack = back, scroll = true) {
        FieldCard("그룹 이름", group, { group = it.take(40) }, placeholder = "회사 주차", size = 18.sp, underline = false)
        if (needName) FieldCard("그룹에 보일 내 이름", me, { me = it.take(12) }, placeholder = "아빠", size = 18.sp, underline = false)
        if (cars.isNotEmpty()) {
            CarPicker(cars, on) { id -> on = if (id in on) on - id else on + id }
            Text("내 차는 나중에 차 화면에서 넣고 뺄 수 있어요", Modifier.padding(horizontal = 4.dp), style = ts(13.sp, color = Wp.MU))
        }
        error?.let { Text(it, style = ts(13.sp, color = Wp.DANGER)) }
        Btn(if (busy) "만드는 중" else "만들기", BtnKind.ACC, enabled = !busy && group.isNotBlank() && (!needName || me.isNotBlank())) { create(group.trim(), me.trim(), on) }
    }
}

/** 그룹 추가 (B-2): the three ways in. Hidden rows at the 3-group limit (Settings does not offer it then). */
@Composable
internal fun AddGroupSheet(count: Int, back: () -> Unit, create: () -> Unit, join: () -> Unit, ask: () -> Unit) = WScreen("그룹 추가", onBack = back) {
    if (count < AddGroupCopy.MAX_GROUPS) WList {
        Li("새 그룹 만들기", right = "›", onClick = create)
        Li("초대 코드로 들어가기", right = "›", onClick = join)
        Li("그룹에게 요청하기", last = true, right = "›", onClick = ask)
    }
    Text(AddGroupCopy.footer(count), Modifier.padding(horizontal = 4.dp), style = ts(13.sp, color = Wp.MU))
}

class AddGroupActivity : ComponentActivity() {
    private var creating by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var count by mutableStateOf(0)

    override fun onResume() { super.onResume(); count = GroupStore.groups(this).size }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        count = GroupStore.groups(this).size
        setContent {
            WpTheme(AppPrefs.bigText(this)) {
                if (creating) CreateGroupScreen(!GroupStore.joined(this), CarStore.mine(this), busy, error, back = { creating = false }, create = ::create)
                else AddGroupSheet(count, back = { finish() }, create = { error = null; creating = true },
                    join = { startActivity(Intent(this, JoinActivity::class.java)); finish() },
                    ask = { startActivity(Intent(this, JoinActivity::class.java).putExtra(JoinActivity.ASK, true)); finish() })
            }
        }
    }

    private fun create(group: String, me: String, on: Set<String>) {
        if (busy) return
        busy = true; error = null
        val app = applicationContext
        val needName = !GroupStore.joined(app)
        Thread {
            val r = Api.createGroup(app, group, if (needName) me else null)
            r.onSuccess { gid ->
                if (needName) AppPrefs.setMyName(app, me)
                AddGroupCopy.share(app, on, gid)
                JoinFlow.startPush(app)
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                r.fold({ finish() }, { error = AddGroupCopy.error(it) })
            }
        }.start()
    }
}
