package kr.dot.wherepark.family

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    /** 가족에게 요청하기: the request this phone is waiting on, and whether the name box for it is open. */
    var waiting by mutableStateOf(JoinRequest.pending(ctx)); private set
    var asking by mutableStateOf(false)
    /** The current code came from an accepted request: the screen asks "<가족> · N명에 들어갈까요?" before joining. */
    var fromRequest by mutableStateOf(false); private set
    private var polling = false  // not [busy]: a poll must not disable the code entry

    fun useCode(c: String?) {
        if (c == null || c == code) return
        code = c; peek = null; error = null
        bg {
            val r = JoinFlow.peek(c)
            main {
                if (code != c) return@main  // a newer code took over
                r.fold({ peek = it }, { code = null; error = if (fromRequest) requestFailed(it) else message(it, "코드가 만료됐거나 잘못됐어요") })
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
            main { r.fold({ joined() }, { busy = false; error = if (fromRequest) requestFailed(it) else message(it, "코드가 만료됐거나 잘못됐어요. 새 초대를 받아 주세요") }) }
        }
    }

    private fun joined() {
        waiting = null; fromRequest = false  // Api.join already cleared the stored request
        Referrer.consume(ctx)
        val cars = JoinFlow.carryOver(ctx).size
        if (cars > 0) carCount = cars else finished = true
    }

    /** Stores a new request under [name] and hands it to [share] (KakaoTalk); the screen switches to waiting. */
    fun request(name: String, share: (PendingRequest) -> Unit) {
        if (name.isBlank()) return
        AppPrefs.setMyName(ctx, name.trim())
        asking = false; error = null
        waiting = JoinRequest.start(ctx, name).also(share)
    }

    /** [취소] while waiting and [아니요] on the confirm: withdraw on the server (best effort) and forget locally. */
    fun cancelRequest() {
        JoinRequest.withdraw(ctx, bg)
        waiting = null; fromRequest = false; error = null
        if (!busy) { code = null; peek = null }
    }

    /** One poll of the waiting request. Accepted -> peek the code and ask; never joins by itself. */
    fun poll() {
        if (polling || waiting == null || code != null) return
        polling = true
        bg {
            val p = JoinRequest.poll(ctx)
            main {
                polling = false
                waiting = JoinRequest.pending(ctx)
                when (p) {
                    is JoinRequest.Poll.Code -> if (waiting != null && code == null) { error = null; fromRequest = true; name = p.name; useCode(p.code) }
                    is JoinRequest.Poll.Failed -> error = if (p.status == 429) "잠시 뒤 다시 확인할게요" else "인터넷 연결을 확인해 주세요"
                    JoinRequest.Poll.Waiting -> error = null
                    JoinRequest.Poll.None -> {}
                }
            }
        }
    }

    /** A refused code (400/404) ends the request; a full family, 429 or offline keeps it for later. */
    private fun requestFailed(e: Throwable): String = when ((e as? ApiException)?.status) {
        400, 404 -> { JoinRequest.withdraw(ctx, bg); waiting = null; fromRequest = false; code = null; peek = null; "요청이 만료됐어요. 다시 보내 주세요" }
        409 -> "가족이 꽉 찼어요"
        429 -> "잠시 뒤 다시 확인할게요"
        else -> "인터넷 연결을 확인해 주세요"
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
internal fun JoinScreen(m: JoinModel, clip: String?, ask: () -> Unit = {}, share: (PendingRequest) -> Unit = {}, tapJoin: () -> Unit) = WScreen(null, gap = 18.dp) {
    Column(Modifier.fillMaxWidth().weight(1f), Arrangement.spacedBy(18.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        Brand(88.dp, 24.dp)
        val w = m.waiting
        val rp = m.peek
        if (m.fromRequest && rp != null) {
            Text("${rp.first} · ${rp.second}명에 들어갈까요?", style = ts(22.sp, FontWeight.ExtraBold, lh = 1.25f, family = gro(800), ls = (-0.02).em), textAlign = TextAlign.Center)
            Btn(if (m.busy) "들어가는 중" else "들어가기", BtnKind.ACC, enabled = !m.busy, onClick = tapJoin)
            Btn("아니요", BtnKind.GHOST, enabled = !m.busy) { m.cancelRequest() }
            m.error?.let { Text(it, style = ts(13.sp, color = Wp.DANGER), textAlign = TextAlign.Center) }
            return@Column
        }
        if (w != null) {
            Text("가족이 [받기]를 누르면 바로 들어가요", style = ts(22.sp, FontWeight.ExtraBold, lh = 1.25f, family = gro(800), ls = (-0.02).em), textAlign = TextAlign.Center)
            if (m.fromRequest) Text("가족을 확인하는 중이에요", style = ts(13.sp, color = Wp.MU))
            Btn("다시 보내기", BtnKind.KAKAO) { share(w) }
            Btn("취소", BtnKind.GHOST) { m.cancelRequest() }
            m.error?.let { Text(it, style = ts(13.sp, color = Wp.DANGER), textAlign = TextAlign.Center) }
            return@Column
        }
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
            if (m.asking) {
                FieldCard("가족에게 보일 내 이름", m.name, { m.name = it.take(12) }, placeholder = "엄마", size = 18.sp, valuePad = 4.dp)
                Btn("요청 보내기", BtnKind.KAKAO, enabled = m.name.isNotBlank()) { m.request(m.name) { share(it) } }
            } else Btn("가족에게 요청하기", BtnKind.GHOST, onClick = ask)
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
    private val handler = Handler(Looper.getMainLooper())
    private val poller = object : Runnable {
        override fun run() { model.poll(); handler.postDelayed(this, 10_000) }  // 10 s: the poll route allows 10 calls a minute per IP
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model = JoinModel(applicationContext, main = { f -> runOnUiThread { if (!isFinishing && !isDestroyed) f() } })
        model.useCode(JoinCode.fromUri(intent?.data) ?: Referrer.pendingCode(this))
        if (intent?.getBooleanExtra(ASK, false) == true && model.waiting == null) ask()
        setContent {
            WpTheme(AppPrefs.bigText(this)) {
                JoinScreen(model, clip, ask = ::ask, share = ::share) { if (FamilyStore.joined(this)) confirmLeave = true else model.join() }
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

    /** Polls every 10 s while visible (the first poll right away); a request with no answer just waits. */
    override fun onResume() { super.onResume(); handler.post(poller) }
    override fun onPause() { handler.removeCallbacks(poller); super.onPause() }

    /** [가족에게 요청하기]: the stored name goes straight to sharing; without one the name box opens first. */
    private fun ask() {
        val name = AppPrefs.myName(this)
        if (name.isNullOrBlank()) model.asking = true else model.request(name, ::share)
    }

    private fun share(r: PendingRequest) = JoinRequest.link(r).let { InviteShare.sendKakao(this, it, JoinRequest.message(it)) }

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

    companion object { const val ASK = "ask" }

    private fun done() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
