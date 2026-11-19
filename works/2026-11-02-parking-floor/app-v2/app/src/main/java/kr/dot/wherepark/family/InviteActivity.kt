package kr.dot.wherepark.family

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.wherepark.R
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.Invite

/** 가족 초대 (sender side): KakaoTalk, other apps, QR, code with [복사]. */
class InviteActivity : ComponentActivity() {
    private var invite by mutableStateOf<Invite?>(null)
    private var failed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = applicationContext
        Thread { Api.invite(app).fold({ invite = it }, { failed = true }) }.start()
        setContent {
            Screen("가족 초대", onBack = { finish() }) {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("같이 쓸 가족에게 보내요", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FG)
                    Text("받은 사람은 링크만 누르면 돼요. 가입은 없어요.", fontSize = 14.sp, color = MU)
                    val inv = invite
                    if (inv == null) {
                        Text(if (failed) "초대를 만들지 못했어요. 네트워크를 확인하고 다시 열어 주세요" else "초대를 만드는 중이에요", fontSize = 14.sp, color = MU)
                    } else {
                        Btn("카카오톡으로 보내기", "fill", KAKAO, Color(0xFF191919)) { InviteShare.sendKakao(this@InviteActivity, inv.link) }
                        Btn("다른 앱으로 보내기") { InviteShare.sendOther(this@InviteActivity, inv.link) }
                        Card {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("옆에 있으면 카메라로", fontSize = 13.sp, color = MU)
                                Spacer(Modifier.size(8.dp))
                                val bmp = remember(inv.link) { InviteShare.qr(inv.link).asImageBitmap() }
                                Image(bmp, "초대 QR", Modifier.size(200.dp))
                            }
                        }
                        var copied by remember { mutableStateOf(false) }
                        Card {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("초대 코드", fontSize = 13.sp, color = MU)
                                    Text(inv.code, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = FG, maxLines = 1)
                                    Text("24시간 · 한 번 쓸 수 있어요", fontSize = 12.sp, color = MU)
                                }
                                Row(
                                    Modifier.heightIn(min = 48.dp).border(1.5.dp, FG, RoundedCornerShape(14.dp))
                                        .clickable { InviteShare.copyCode(this@InviteActivity, inv.code); copied = true }.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Ic(R.drawable.ic_copy, 17)
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (copied) "복사됨" else "복사", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FG)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
