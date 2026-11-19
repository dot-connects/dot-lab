package kr.dot.wherepark.family

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.wherepark.MainActivity
import kr.dot.wherepark.data.FamilyStore

/** 받는 쪽: link (autoVerify) or pasted code -> peek (group name + head count only) -> name -> join. */
class JoinActivity : ComponentActivity() {
    private var code by mutableStateOf<String?>(null)
    private var typed by mutableStateOf("")
    private var clip by mutableStateOf<String?>(null)
    private var peek by mutableStateOf<Pair<String, Int>?>(null)
    private var error by mutableStateOf<String?>(null)
    private var name by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var confirmLeave by mutableStateOf(false)
    private var carCount by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        useCode(JoinCode.fromUri(intent?.data) ?: Referrer.pendingCode(this))
        setContent {
            Screen(null, null) {
                Column(Modifier.fillMaxWidth().weight(1f), Arrangement.spacedBy(16.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
                    Text("가족 초대를 받았어요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FG)
                    val p = peek
                    if (p != null) {
                        Text("${p.first} · ${p.second}명", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = FG)
                        Text("들어가면 가족 차의 주차 자리가\n이 폰 위젯에도 보여요", fontSize = 14.sp, color = MU, textAlign = TextAlign.Center)
                        Card {
                            Column {
                                Text("가족에게 보일 내 이름", fontSize = 13.sp, color = MU)
                                BasicTextField(name, { name = it.take(12) }, singleLine = true, textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = FG))
                            }
                        }
                        Btn(if (busy) "들어가는 중" else "들어가기", "acc", enabled = !busy && name.isNotBlank()) {
                            if (FamilyStore.joined(this@JoinActivity)) confirmLeave = true else doJoin()
                        }
                        Text("내 위치는 공유되지 않아요. 차 자리만 보여요", fontSize = 13.sp, color = MU)
                    } else {
                        Text("받은 초대 코드를 넣어 주세요", fontSize = 14.sp, color = MU)
                        Card {
                            BasicTextField(typed, { typed = it; useCode(JoinCode.fromText(it)) }, singleLine = true,
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                textStyle = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = FG))
                        }
                        clip?.let { c -> Btn("클립보드의 코드 붙여 넣기 ($c)") { typed = c; useCode(c) } }
                    }
                    error?.let { Text(it, fontSize = 13.sp, color = MU, textAlign = TextAlign.Center) }
                }
            }
            if (confirmLeave) AlertDialog(
                onDismissRequest = { confirmLeave = false },
                text = { Text("지금 가족에서 나가고 들어갈까요?") },
                confirmButton = { TextButton({ confirmLeave = false; doJoin() }) { Text("나가고 들어가기") } },
                dismissButton = { TextButton({ confirmLeave = false }) { Text("취소") } },
            )
            if (carCount > 0) AlertDialog(
                onDismissRequest = {},
                text = { Text("차 ${carCount}대를 새 가족으로 가져갈까요?") },
                confirmButton = { TextButton({ FamilyStore.acceptCarryOver(this@JoinActivity); done() }) { Text("가져가기") } },
                dismissButton = { TextButton({ FamilyStore.declineCarryOver(this@JoinActivity); done() }) { Text("안 가져가요") } },
            )
        }
    }

    /** The clipboard can only be read while focused (Android 10+). */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && code == null) clip = InviteShare.clipboardCode(this)
    }

    private fun useCode(c: String?) {
        if (c == null || c == code) return
        code = c
        error = null
        Thread { JoinFlow.peek(c).fold({ peek = it }, { error = "초대가 없거나 만료됐어요. 새 초대를 받아 주세요"; code = null }) }.start()
    }

    private fun doJoin() {
        val c = code ?: return
        busy = true
        val app = applicationContext
        Thread {
            JoinFlow.join(app, c, name).fold({
                Referrer.consume(app)
                val n = JoinFlow.carryOver(app).size
                if (n > 0) carCount = n else done()
            }, { busy = false; error = "들어가지 못했어요. 잠시 뒤 다시 해 주세요" })
        }.start()
    }

    private fun done() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
