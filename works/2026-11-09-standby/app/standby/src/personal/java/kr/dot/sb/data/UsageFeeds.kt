package kr.dot.sb.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.dot.sb.BuildConfig
import kr.dot.sb.ui.state.UsageFeed
import kr.dot.sb.ui.state.UsageInfo
import kr.dot.sb.ui.state.UsageSession
import kr.dot.sb.ui.state.UsageWindow
import org.json.JSONObject
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Personal build: the usage card and the PC feed exist (the public flavor has a stub with enabled = false). */
object UsageFeature {
    const val enabled = true

    fun feed(ctx: Context): UsageFeed = object : UsageFeed {
        override suspend fun fetch(): UsageInfo? {
            if (!Feeds.onWifi(ctx)) return null
            return try {
                withContext(Dispatchers.IO) { UsageParser.parse(UsageFeeds.usageText(ctx), System.currentTimeMillis() / 1000) }
            } catch (e: Exception) { null }
        }
    }
}

/** Fetch order copied from 눈금's Fetcher: home LAN first, else Telegram. Blocking: call from Dispatchers.IO. */
object UsageFeeds {
    /** Raw status.json text. Throws when neither route works. */
    fun usageText(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("sb_feed", Context.MODE_PRIVATE)
        val lan = prefs.getString("lan", null)
        val errors = mutableListOf<String>()
        // token only over Wi-Fi to a private IPv4 literal (same rule as 눈금); never over mobile data
        if (lan != null && Feeds.onWifi(ctx) && BuildConfig.LAN_TOKEN.isNotEmpty() && UsageParser.isPrivateHttp(lan)) {
            try { return Feeds.get(lan, 1500, "Bearer " + BuildConfig.LAN_TOKEN) } catch (e: Exception) { errors += "lan:${e.javaClass.simpleName}" }
        }
        if (BuildConfig.STATUS_BOT_TOKEN.isEmpty()) throw IllegalStateException("no bot token " + errors.joinToString())
        try {
            val chat = Feeds.get(
                "https://api.telegram.org/bot${BuildConfig.STATUS_BOT_TOKEN}/getChat?chat_id=" +
                    URLEncoder.encode(BuildConfig.STATUS_CHAT_ID, "UTF-8"), 8000, null,
            )
            val text = JSONObject(chat).getJSONObject("result").getJSONObject("pinned_message").getString("text")
            // remember the PC's home address, but only a private-network one: the token goes there
            UsageParser.lanUrl(text)?.takeIf { UsageParser.isPrivateHttp(it) }?.let { prefs.edit().putString("lan", it).apply() }
            return text
        } catch (e: Exception) {
            errors += "tg:${e.javaClass.simpleName}"
        }
        throw IllegalStateException(errors.joinToString(" "))
    }
}

/** status.json of ai-hub (shape copied from 눈금's Status.kt; "pct" is the share USED). */
object UsageParser {
    /** The four session dots of 눈금, in its order. */
    private val DOTS = listOf("dev", "dev2", "sync", "lab")
    private val BOTS = listOf("content", "trading")

    fun parse(text: String, fetchedAtSec: Long): UsageInfo {
        val j = JSONObject(text)
        val limits = j.optJSONObject("limits")
        fun win(acct: String, key: String): UsageWindow? {
            val w = limits?.optJSONObject(acct)?.optJSONObject(key) ?: return null
            if (!w.has("pct") || w.isNull("pct")) return null
            fun long(k: String) = if (!w.has(k) || w.isNull(k)) null else w.getLong(k)
            val pace = if (!w.has("pace") || w.isNull("pace")) null else w.getDouble("pace")
            return UsageWindow(w.getDouble("pct").roundToInt().coerceIn(0, 100), long("reset"), pace)
        }
        return UsageInfo(
            win("claude", "5h"), win("claude", "7d"), win("codex", "5h"), win("codex", "7d"), fetchedAtSec,
            sessions = dots(j), bots = bots(j),
        )
    }

    private val STATES = setOf("busy", "wait", "idle", "off")

    /** dev, dev2, sync, lab; a session that is not running is "off". */
    fun dots(j: JSONObject): List<UsageSession> {
        val arr = j.optJSONArray("sessions")
        val all = (0 until (arr?.length() ?: 0)).map { arr!!.getJSONObject(it) }
        return DOTS.map { name ->
            val s = all.firstOrNull { it.optString("name") == name }
            fun long(k: String) = if (s == null || !s.has(k) || s.isNull(k)) null else s.getLong(k)
            // a state this build does not know (a newer PC feed) reads as off: the card has no words for it
            UsageSession(name, s?.optString("state")?.takeIf { it in STATES } ?: "off", long("ctx")?.toInt()?.coerceIn(0, 100), long("cache_until"))
        }
    }

    /** content, trading: busy | idle | off (missing or an errored "work" section = off). */
    fun bots(j: JSONObject): List<Pair<String, String>> {
        val w = j.optJSONObject("work")?.takeIf { !it.has("error") }
        return BOTS.map { it to (w?.optJSONObject(it)?.optString("state", "off") ?: "off") }
    }

    /** The PC's home address advertised in the status text ("lan"), or null. */
    fun lanUrl(text: String): String? = JSONObject(text).optString("lan").ifEmpty { null }

    /** http://<private IPv4 literal>:port/... only: the LAN token goes there. */
    fun isPrivateHttp(url: String): Boolean {
        val m = Regex("""^http://(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})(:\d+)?/""").find(url) ?: return false
        val p = m.groupValues.drop(1).take(4).map { it.toInt() }
        if (p.any { it > 255 }) return false
        return p[0] == 10 || (p[0] == 172 && p[1] in 16..31) || (p[0] == 192 && p[1] == 168)
    }
}
