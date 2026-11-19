package kr.dot.wherepark.family

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.Invite
import kr.dot.wherepark.ui.AppPrefs
import kr.dot.wherepark.ui.Btn
import kr.dot.wherepark.ui.BtnKind
import kr.dot.wherepark.ui.Ic
import kr.dot.wherepark.ui.WCard
import kr.dot.wherepark.ui.WScreen
import kr.dot.wherepark.ui.Wp
import kr.dot.wherepark.ui.WpTheme
import kr.dot.wherepark.ui.gro
import kr.dot.wherepark.ui.trimLine
import kr.dot.wherepark.ui.ts

/** 가족 초대 (mockup section 6, sender side): KakaoTalk, other apps, QR, code with [복사]. */
@Composable
internal fun InviteScreen(inv: Invite?, failed: Boolean, back: () -> Unit, kakao: (Invite) -> Unit, other: (Invite) -> Unit, copy: (Invite) -> Unit, renew: () -> Unit = {}) =
    WScreen("가족 초대", onBack = back) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("같이 쓸 가족에게 보내요", Modifier.trimLine(26.4.sp), style = ts(22.sp, FontWeight.ExtraBold, lh = 1.2f, family = gro(800), ls = (-0.02).em))
            Text("받은 사람은 링크만 누르면 돼요. 가입은 없어요.", style = ts(15.sp, color = Wp.MU))
            if (inv == null) {
                Text(if (failed) "초대를 만들지 못했어요. 인터넷 연결을 확인하고 다시 열어 주세요" else "초대를 만드는 중이에요", style = ts(14.sp, color = Wp.MU))
            } else {
                Btn("카카오톡으로 보내기", BtnKind.KAKAO) { kakao(inv) }
                Btn("다른 앱으로 보내기", BtnKind.GHOST) { other(inv) }
                WCard {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("옆에 있으면 카메라로", Modifier.padding(bottom = 8.dp), style = ts(13.sp, color = Wp.MU), textAlign = TextAlign.Center)
                        val bmp = remember(inv.link) { InviteShare.qr(inv.link).asImageBitmap() }
                        Box(Modifier.size(150.dp).clip(RoundedCornerShape(8.dp)).background(Color.White).padding(8.dp)) {
                            Image(bmp, "초대 QR", Modifier.fillMaxWidth(), filterQuality = FilterQuality.None)
                        }
                    }
                }
                var copied by remember { mutableStateOf(false) }
                WCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("초대 코드", style = ts(13.sp, color = Wp.MU))
                            Text(inv.code, style = ts(17.sp, FontWeight.ExtraBold, family = gro(800), ls = .02.em), maxLines = 1)
                            Text("24시간 · 한 번 쓸 수 있어요", style = ts(12.sp, color = Wp.MU))
                        }
                        Btn(if (copied) "복사됨" else "복사", BtnKind.NT, Modifier.width(84.dp), h = 44.dp, size = 14.sp, lead = { Ic("copy", 17.dp) }) { copy(inv); copied = true }
                    }
                }
                Btn("새 코드 만들기", BtnKind.GHOST, h = 44.dp, size = 14.sp, onClick = renew)
            }
        }
    }

class InviteActivity : ComponentActivity() {
    private var invite by mutableStateOf<Invite?>(null)
    private var failed by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        load(false)  // reuses the stored invite: a rotation must not replace the code already sent
        setContent {
            WpTheme(AppPrefs.bigText(this)) {
                InviteScreen(invite, failed, back = { finish() }, kakao = { InviteShare.sendKakao(this, it.link) },
                    other = { InviteShare.sendOther(this, it.link) }, copy = { InviteShare.copyCode(this, it.code) }, renew = { load(true) })
            }
        }
    }

    private fun load(fresh: Boolean) {
        val app = applicationContext
        invite = null; failed = false
        Thread { Api.invite(app, fresh).let { r -> runOnUiThread { if (!isFinishing && !isDestroyed) r.fold({ invite = it }, { failed = true }) } } }.start()
    }
}
