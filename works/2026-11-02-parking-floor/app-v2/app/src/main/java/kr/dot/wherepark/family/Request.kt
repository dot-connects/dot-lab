package kr.dot.wherepark.family

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import java.security.MessageDigest
import java.security.SecureRandom
import android.content.Intent
import kr.dot.wherepark.MainActivity
import kr.dot.wherepark.data.Api
import kr.dot.wherepark.data.ApiException
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.Prefs
import kr.dot.wherepark.ui.AppPrefs
import kr.dot.wherepark.ui.Btn
import kr.dot.wherepark.ui.BtnKind
import kr.dot.wherepark.ui.WScreen
import kr.dot.wherepark.ui.Wp
import kr.dot.wherepark.ui.WpTheme
import kr.dot.wherepark.ui.gro
import kr.dot.wherepark.ui.ts
import org.json.JSONObject

/** [secret] never leaves this phone except in the poll body; the link carries [hash] = sha256(secret). */
data class PendingRequest(val secret: String, val name: String, val createdMs: Long) {
    val hash: String get() = MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()).joinToString("") { "%02x".format(it) }
}

/**
 * 가족에게 요청하기, the reverse of an invite: the new phone sends a /r#<hash>.<name> link, a member taps [받기] and the server
 * makes the request its own invite code, which the new phone picks up by polling with the secret behind the hash (anyone who
 * only saw the link cannot). The phone then peeks it and asks before joining (the normal join path); no blind join.
 */
object JoinRequest {
    const val TTL_MS = 24L * 60 * 60 * 1000
    private const val KEY = "wp_request"
    private const val LINK = "https://dot-lab-parking.dotconnects.workers.dev/r"
    val SECRET = Regex("[0-9a-f]{32}")
    val HASH = Regex("[0-9a-f]{64}")

    /** The stored request; one older than 24 h is dropped here. */
    fun pending(ctx: Context, now: Long = System.currentTimeMillis()): PendingRequest? {
        val j = Prefs.obj(ctx, KEY)
        val secret = j.optString("secret")
        if (!SECRET.matches(secret)) return null
        val created = j.optLong("createdMs")
        if (now - created > TTL_MS || created > now) { cancel(ctx); return null }
        return PendingRequest(secret, j.optString("name"), created)
    }

    fun start(ctx: Context, name: String, now: Long = System.currentTimeMillis(), rnd: SecureRandom = SecureRandom()): PendingRequest {
        val secret = ByteArray(16).also(rnd::nextBytes).joinToString("") { "%02x".format(it) }
        return PendingRequest(secret, name.trim().take(12), now).also {
            Prefs.put(ctx, KEY, JSONObject().put("secret", it.secret).put("name", it.name).put("createdMs", it.createdMs), sync = true)
        }
    }

    fun cancel(ctx: Context) = Prefs.put(ctx, KEY, null, sync = true)

    fun link(r: PendingRequest) = "$LINK#${r.hash}.${Uri.encode(r.name)}"
    fun message(link: String) = "어디댔어 같이 써요. 링크를 누르고 [받기]를 눌러 주세요\n$link"

    /** `/r#<hash>.<name>` or `/r?q=<hash>.<name>` (the /r page's [앱으로 열기]; q holds the still-encoded fragment) -> (hash, name). */
    fun fromUri(uri: Uri?): Pair<String, String>? {
        if (uri?.path != "/r") return null
        val raw = uri.encodedFragment ?: uri.getQueryParameter("q") ?: return null
        val id = raw.substringBefore('.')
        val name = Uri.decode(raw.substringAfter('.', "")).trim().take(12)
        return if (HASH.matches(id) && name.isNotEmpty()) id to name else null
    }

    sealed interface Poll {
        data object None : Poll                                  // no pending request
        data object Waiting : Poll                               // nobody accepted yet (404)
        data class Code(val code: String, val name: String, val byName: String?) : Poll  // accepted (by [byName]): peek, then ask
        data class Failed(val status: Int) : Poll                // 429 / offline / server: keep the request, try later
    }

    /** Requester side, one poll. Never joins and never drops the request (24 h aside). */
    fun poll(ctx: Context, now: Long = System.currentTimeMillis()): Poll {
        val r = pending(ctx, now) ?: return Poll.None
        return Api.requestCode(r.secret).fold({ if (it == null) Poll.Waiting else Poll.Code(it.first, r.name, it.second) }, { Poll.Failed((it as? ApiException)?.status ?: 0) })
    }

    /** [취소] / [아니요]: forget locally now, withdraw on the server best effort ([bg] runs the network call). */
    fun withdraw(ctx: Context, bg: (() -> Unit) -> Unit = { Thread(it).start() }) {
        val r = pending(ctx) ?: return
        cancel(ctx)
        bg { Api.cancelRequest(r.secret) }
    }

    /** Member side [받기]: the server makes an invite of my family for the request [id] (the link's hash). */
    fun accept(ctx: Context, id: String): Result<Unit> = Api.putRequest(ctx, id)

    /** 을/를 after a Korean name (a final consonant takes 을); other scripts get 를. */
    fun objectOf(name: String): String = name + (name.lastOrNull()?.takeIf { it in '가'..'힣' }?.let { if ((it - '가') % 28 != 0) "을" else null } ?: "를")
}

enum class AcceptState { ASK, BUSY, SENT, TAKEN, FAILED }

/** [own] = the link is this phone's own request (no [받기]). */
@Composable
internal fun AcceptScreen(name: String, joined: Boolean, own: Boolean, state: AcceptState, accept: () -> Unit, no: () -> Unit, create: () -> Unit = {}) = WScreen("가족 요청", onBack = no) {
    Column(Modifier.fillMaxWidth().weight(1f), Arrangement.spacedBy(18.dp, Alignment.CenterVertically), Alignment.CenterHorizontally) {
        val head = when {
            own -> "내가 보낸 요청이에요. 가족이 링크를 누르면 들어가요"
            !joined -> "먼저 가족을 만들어야 받을 수 있어요"
            state == AcceptState.SENT -> "보냈어요. $name 폰에서 곧 들어와요"
            else -> "${JoinRequest.objectOf(name)} 가족으로 받을까요?"
        }
        Text(head, style = ts(24.sp, FontWeight.ExtraBold, lh = 1.25f, family = gro(800), ls = (-0.02).em), textAlign = TextAlign.Center)
        if (own) return@Column
        if (!joined) { Btn("가족 만들기", BtnKind.ACC, onClick = create); return@Column }
        if (state != AcceptState.SENT) {
            Btn(if (state == AcceptState.BUSY) "보내는 중" else "받기", BtnKind.ACC, enabled = state != AcceptState.BUSY && state != AcceptState.TAKEN, onClick = accept)
            Btn("아니요", BtnKind.GHOST, onClick = no)
        }
        when (state) {
            AcceptState.TAKEN -> "이미 다른 가족이 받은 요청이에요"
            AcceptState.FAILED -> "보내지 못했어요. 인터넷 연결을 확인해 주세요"
            else -> null
        }?.let { Text(it, style = ts(13.sp, color = Wp.DANGER), textAlign = TextAlign.Center) }
    }
}

/** /r link (App Links, singleTask): "<name>를 가족으로 받을까요?" [받기] [아니요]. */
class RequestActivity : ComponentActivity() {
    private var req by mutableStateOf<Pair<String, String>?>(null)
    private var state by mutableStateOf(AcceptState.ASK)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        req = JoinRequest.fromUri(intent?.data) ?: run { finish(); return }
        setContent {
            WpTheme(AppPrefs.bigText(this)) {
                val (id, name) = req ?: return@WpTheme
                AcceptScreen(name, FamilyStore.joined(this), JoinRequest.pending(this)?.hash == id, state, accept = { accept(id) }, no = { finish() }, create = {
                    // home / onboarding is where a family gets made (MainActivity routes a phone with no family to onboarding)
                    startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                    finish()
                })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        JoinRequest.fromUri(intent.data)?.let { if (it != req) { req = it; state = AcceptState.ASK } }
    }

    private fun accept(id: String) {
        if (state == AcceptState.BUSY) return
        state = AcceptState.BUSY
        val app = applicationContext
        Thread {
            val r = JoinRequest.accept(app, id)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                state = r.fold({ AcceptState.SENT }, { if ((it as? ApiException)?.status == 409) AcceptState.TAKEN else AcceptState.FAILED })
            }
        }.start()
    }
}
