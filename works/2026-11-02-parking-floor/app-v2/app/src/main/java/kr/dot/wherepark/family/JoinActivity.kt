package kr.dot.wherepark.family

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kr.dot.wherepark.MainActivity
import kr.dot.wherepark.data.ApiException
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.ui.AppPrefs
import kr.dot.wherepark.ui.Brand
import kr.dot.wherepark.ui.Btn
import kr.dot.wherepark.ui.BtnKind
import kr.dot.wherepark.ui.FieldCard
import kr.dot.wherepark.ui.WScreen
import kr.dot.wherepark.ui.Wp
import kr.dot.wherepark.ui.WpTheme
import kr.dot.wherepark.ui.gro
import kr.dot.wherepark.ui.trimLine
import kr.dot.wherepark.ui.ts

/**
 * The join screen's state, UI-free so tests can drive it. [bg] runs network work, [main] posts results back.
 * Every state write happens on the main thread; a peek answer for a code that is no longer current is dropped.
 */
class JoinModel(private val ctx: Context, private val bg: (() -> Unit) -> Unit = { Thread(it).start() }, private val main: (() -> Unit) -> Unit) {
    var code by mutableStateOf<String?>(null); private set
    var typed by mutableStateOf("")
    var peek by mutableStateOf<Pair<String, Int>?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var name by mutableStateOf("")
    var busy by mutableStateOf(false); private set
    var carCount by mutableIntStateOf(0); private set
    var finished by mutableStateOf(false); private set

    fun useCode(c: String?) {
        if (c == null || c == code) return
        code = c; peek = null; error = null
        bg {
            val r = JoinFlow.peek(c)
            main {
                if (code != c) return@main  // a newer code took over
                r.fold({ peek = it }, { error = message(it, "코드가 만료됐거나 잘못됐어요"); code = null })
            }
        }
    }

    /** Main thread. A second tap while the first join runs does nothing. */
    fun join() {
        if (busy) return
        val c = code ?: return
        busy = true
        error = null
        val n = name
        bg {
            val r = JoinFlow.join(ctx, c, n)
            main {
                r.fold({
                    Referrer.consume(ctx)
                    val cars = JoinFlow.carryOver(ctx).size
                    if (cars > 0) carCount = cars else finished = true
                }, { busy = false; error = message(it, "코드가 만료됐거나 잘못됐어요. 새 초대를 받아 주세요") })
            }
        }
    }

    fun carryOver(take: Boolean) {
        if (take) FamilyStore.acceptCarryOver(ctx) else FamilyStore.declineCarryOver(ctx)
        carCount = 0; finished = true
    }

    /** Offline (status 0) or a server error = connection; a 4xx = the code itself. */
    private fun message(e: Throwable, codeProblem: String) =
        if (e is ApiException && e.status in 400..499) codeProblem else "인터넷 연결을 확인해 주세요"
}

/** 받는 쪽 (mockup section 6): group name + head count only, then my name -> [들어가기]. Without a code: paste box. */
@Composable
internal fun JoinScreen(m: JoinModel, clip: String?, tapJoin: () -> Unit) = WScreen(null, gap = 18.dp) {
    Column(Modifier.fillMaxWidth().weight(1f), Arrangement.spacedBy(18.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        Brand(88.dp, 24.dp)
        Text("가족 초대를 받았어요", Modifier.trimLine(31.2.sp), style = ts(26.sp, FontWeight.ExtraBold, lh = 1.2f, family = gro(800), ls = (-0.02).em), textAlign = TextAlign.Center)
        val p = m.peek
        if (p != null) {
            Text("${p.first} · ${p.second}명", style = ts(18.sp, FontWeight.SemiBold))
            Text("들어가면 가족 차의 주차 자리가\n이 폰 위젯에도 보여요", style = ts(15.sp, color = Wp.MU), textAlign = TextAlign.Center)
            FieldCard("가족에게 보일 내 이름", m.name, { m.name = it.take(12) }, placeholder = "엄마", size = 18.sp, valuePad = 4.dp)
            Btn(if (m.busy) "들어가는 중" else "들어가기", BtnKind.ACC, enabled = !m.busy && m.name.isNotBlank(), onClick = tapJoin)
            Text("내 위치는 공유되지 않아요. 차 자리만 보여요", style = ts(13.sp, color = Wp.MU), textAlign = TextAlign.Center)
        } else {
            Text("받은 초대 코드를 넣어 주세요", style = ts(15.sp, color = Wp.MU))
            FieldCard("초대 코드", m.typed, { m.typed = it; m.useCode(JoinCode.fromText(it)) }, placeholder = "K7QM-28XP-4HDA", size = 18.sp,
                keyboard = KeyboardOptions(capitalization = KeyboardCapitalization.Characters))
            if (m.code != null && m.error == null) Text("초대를 확인하는 중이에요", style = ts(13.sp, color = Wp.MU))
            clip?.let { c -> Btn("복사한 코드 붙여 넣기 ($c)", BtnKind.GHOST) { m.typed = c; m.useCode(c) } }
        }
        m.error?.let { Text(it, style = ts(13.sp, color = Wp.DANGER), textAlign = TextAlign.Center) }
    }
}

/** Link (autoVerify, singleTask) or pasted code -> peek -> name -> join. */
class JoinActivity : ComponentActivity() {
    private lateinit var model: JoinModel
    private var clip by mutableStateOf<String?>(null)
    private var clipRead = false
    private var confirmLeave by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = JoinModel(applicationContext, main = { f -> runOnUiThread { if (!isFinishing && !isDestroyed) f() } })
        model.useCode(JoinCode.fromUri(intent?.data) ?: Referrer.pendingCode(this))
        setContent {
            WpTheme(AppPrefs.bigText(this)) {
                JoinScreen(model, clip) { if (FamilyStore.joined(this)) confirmLeave = true else model.join() }
                if (confirmLeave) AlertDialog(
                    onDismissRequest = { confirmLeave = false }, containerColor = Wp.SF,
                    text = { Text("지금 가족에서 나가고 들어갈까요?") },
                    confirmButton = { TextButton({ confirmLeave = false; model.join() }) { Text("나가고 들어가기", color = Wp.FG) } },
                    dismissButton = { TextButton({ confirmLeave = false }) { Text("취소", color = Wp.MU) } },
                )
                if (model.carCount > 0) AlertDialog(
                    onDismissRequest = {}, containerColor = Wp.SF,
                    text = { Text("차 ${model.carCount}대를 새 가족으로 가져갈까요?") },
                    confirmButton = { TextButton({ model.carryOver(true) }) { Text("가져가기", color = Wp.FG) } },
                    dismissButton = { TextButton({ model.carryOver(false) }) { Text("안 가져가요", color = Wp.MU) } },
                )
                LaunchedEffect(model.finished) { if (model.finished) done() }
            }
        }
    }

    /** singleTask: a second invite link while this screen is open lands here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        model.useCode(JoinCode.fromUri(intent.data))
    }

    /** The clipboard can only be read while focused (Android 10+); read it once, on the first focus. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !clipRead) { clipRead = true; if (model.code == null) clip = InviteShare.clipboardCode(this) }
    }

    private fun done() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
