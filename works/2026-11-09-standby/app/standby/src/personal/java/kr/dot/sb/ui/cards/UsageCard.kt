package kr.dot.sb.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.state.UsageInfo
import kr.dot.sb.ui.state.UsageSession
import kr.dot.sb.ui.state.UsageWindow
import java.time.Instant
import java.time.ZoneId
import kotlin.math.PI

/*
 * Usage card (spec §1 "Usage", mockup "사용량" v3), personal build only. The 눈금 widget's rings per account (270°, open at
 * the bottom; outer = 5 h left, thick; inner = 7 d left, thin; a gap at the even-spend pace point; number = 7 d left)
 * + one row per session (dev · dev2 · sync · lab: state dot, context fill, cache arc). Wide: rings left at full height,
 * sessions right. Stacked otherwise: sessions take what they need, rings grow into the rest; tall slots add the bots and
 * the last update. Short side < 140: the two rings only. The feed carries no "what it is doing" text, so rows say the
 * state + context % instead of the mockup's task line.
 */
@Composable
internal fun UsageCard(e: Env, state: StandbyState, actions: StandbyActions) {
    val u = state.usage ?: return LoadingCard(e, Ic.USAGE, "사용량")
    val nowSec = state.now.atZone(ZoneId.systemDefault()).toEpochSecond()
    val dim = u.offline || nowSec - u.fetchedAtEpochSec > 3 * 3600
    val p = e.p7
    val iw = e.w - p * 2
    val ih = e.h - p * 2
    val ts = e.look.textScale
    val oneH = 28.dp * ts
    val twoH = 46.dp * ts
    fun labH(full: Boolean) = if (full) e.meta * 1.3f * 3 + 4.dp else e.meta * 1.3f + 4.dp
    val rings = @Composable { d: Dp, full: Boolean, m: Modifier ->
        Row(m, horizontalArrangement = Arrangement.SpaceAround) {
            listOf(Triple("Claude", u.claude5h, u.claude7d), Triple("Codex", u.codex5h, u.codex7d)).forEach { (name, h5, d7) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    NgRing(e, d, h5, d7, dim)
                    Txt(name, e.meta, e.ink.ink, weight = 700, maxW = d + 10.dp)
                    if (full) {
                        Txt("5시간 ${left(h5)} · ${in5h(h5, nowSec)}", e.meta, e.readInk2, weight = 500, num = true, maxW = d + 40.dp)
                        Txt("7일 ${left(d7)} · ${at7d(d7)}", e.meta, e.readInk2, weight = 500, num = true, maxW = d + 40.dp)
                    }
                }
            }
        }
    }
    val rows = @Composable { n: Int, two: Boolean, colW: Dp ->
        Column(verticalArrangement = Arrangement.spacedBy(if (two) 4.dp else 2.dp)) {
            u.sessions.take(n).forEach { SessionRow(e, it, nowSec, two, if (two) twoH else oneH, colW) }
        }
    }
    val tap = Modifier.tap(if (u.offline) actions::retryFeeds else null)
    when {
        min(e.w, e.h) < 140.dp -> {
            val d = min(iw / 2.2f, ih - labH(false))
            Box(Modifier.fillMaxSize().padding(p).then(tap), contentAlignment = Alignment.Center) { rings(d, false, Modifier.fillMaxWidth()) }
        }
        e.w / e.h >= 1.25f -> {
            val full = ih >= 220.dp
            // the rings take the width the session column does not need (mockup: half the width; that left big slots empty)
            val d = min(ih - labH(full) - 4.dp, max(iw * 0.5f - 10.dp, iw - 10.dp - p - 260.dp * ts) / 2)
            val right = iw - (d * 2 + 10.dp) - p
            val two = ih >= twoH * 4 && right >= 220.dp
            val n = minOf(u.sessions.size, (ih / (if (two) twoH else oneH)).toInt())
            Row(Modifier.fillMaxSize().padding(p).then(tap), horizontalArrangement = Arrangement.spacedBy(p), verticalAlignment = Alignment.CenterVertically) {
                rings(d, full, Modifier.width(d * 2 + 10.dp))
                Box(Modifier.weight(1f)) { rows(n, two, right) }
            }
        }
        else -> {
            val two = iw >= 260.dp && ih >= 420.dp
            val rowH = if (two) twoH else oneH
            var n = minOf(u.sessions.size, 4, (ih * 0.5f / rowH).toInt())
            fun full(n: Int) = iw >= 240.dp && ih - rowH * n >= 200.dp
            fun ring(n: Int) = min((iw - 10.dp) / 2.15f, ih - rowH * n - labH(full(n)) - 16.dp)
            while (n > 0 && ring(n) < 56.dp) n--
            val d = ring(n)
            val spare = ih - (d + labH(full(n)) + rowH * n + 14.dp)
            val extra = spare > 80.dp && u.bots.isNotEmpty()
            Column(
                Modifier.fillMaxSize().padding(p).then(tap),
                verticalArrangement = if (extra) Arrangement.SpaceBetween else Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            ) {
                rings(d, full(n), Modifier.fillMaxWidth())
                rows(n, two, iw)
                if (extra) Footer(e, u, spare > 140.dp)
            }
        }
    }
}

private fun left(w: UsageWindow?) = w?.let { "${100 - it.usedPct}%" } ?: "–"

/** "1:20 뒤" until the 5-hour reset. */
private fun in5h(w: UsageWindow?, nowSec: Long): String {
    val s = (w?.resetsAtEpochSec ?: return "–") - nowSec
    if (s <= 0) return "곧"
    return "%d:%02d 뒤".format(s / 3600, s / 60 % 60)
}

/** "금 06:00" for the 7-day reset. */
private fun at7d(w: UsageWindow?): String {
    val r = w?.resetsAtEpochSec ?: return "–"
    return dowKo(Instant.ofEpochSecond(r).atZone(ZoneId.systemDefault()).toLocalDate()) + " " + hhmm(r)
}

/**
 * 눈금 ring [d] wide: arcs from 135° over 270° (open at the bottom). Outer = 5 h left (red role under 20 %), inner =
 * 7 d left in ink2, both broken at their pace point; the 7 d number in the theme digits. Stale data draws in ink2.
 */
@Composable
private fun NgRing(e: Env, d: Dp, h5: UsageWindow?, d7: UsageWindow?, dim: Boolean) {
    val ink = e.ink
    val left5 = h5?.let { (100 - it.usedPct) / 100f }
    val left7 = d7?.let { (100 - it.usedPct) / 100f }
    val c5 = when { left5 != null && left5 < 0.2f -> ink.weekday; dim -> ink.ink2; else -> ink.ink }
    val c7 = if (dim) ink.ink3 else ink.ink2
    Box(Modifier.size(d), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = size.minDimension / 2
            fun arc(r: Float, w: Float, from: Float, to: Float, col: Color) {
                if (to - from < 0.005f) return
                drawArc(col, 135f + from * 270f, (to - from) * 270f, false, Offset(c - r, c - r), Size(r * 2, r * 2), style = Stroke(w, cap = StrokeCap.Round))
            }
            fun broken(r: Float, w: Float, to: Float, pace: Double?, col: Color) {
                val g = w * 0.9f / (2 * PI.toFloat() * r)
                val pc = pace?.toFloat()
                if (pc == null || to <= pc - g) arc(r, w, 0f, to, col)
                else { arc(r, w, 0f, pc - g, col); if (to > pc + g) arc(r, w, pc + g, to, col) }
            }
            val r5 = c * 0.86f; val w5 = c * 0.14f
            val r7 = c * 0.64f; val w7 = c * 0.084f
            broken(r5, w5, 1f, h5?.pace, ink.ink3)
            left5?.let { broken(r5, w5, it, h5?.pace, c5) }
            broken(r7, w7, 1f, d7?.pace, ink.ink3)
            left7?.let { broken(r7, w7, it, d7?.pace, c7) }
        }
        Hero(e, AnnotatedString(d7?.let { "${100 - it.usedPct}" } ?: "–"), d * 0.17f, if (dim) ink.ink2 else ink.ink, d * 0.5f)
    }
}

private val STATE_KO = mapOf("busy" to "작업 중", "wait" to "답 기다림", "idle" to "쉬는 중", "off" to "꺼짐")

/** One session: dot + "dev · 작업 중 · 문맥 62%" (one line), or name · state over context · cache minutes (two lines). */
@Composable
private fun SessionRow(e: Env, s: UsageSession, nowSec: Long, two: Boolean, h: Dp, colW: Dp) {
    val cacheS = s.cacheUntil?.let { it - nowSec }?.takeIf { s.state != "off" && it in 1..CACHE_WARN_S }
    val stCol = if (s.state == "wait") e.ink.accText else e.readInk2
    val ctx = s.ctx?.takeIf { s.state != "off" }?.let { "문맥 $it%" }
    Row(Modifier.fillMaxWidth().height(h), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (two) 10.dp else 8.dp)) {
        val dot = if (two) 18.dp else 14.dp
        SessionDot(e, s, cacheS?.let { it.toFloat() / CACHE_WARN_S } ?: 0f, dot)
        val textW = colW - dot - 10.dp
        if (!two) {
            // narrow columns drop the context share rather than cut the line (text is never clipped)
            val withCtx = ctx != null && fitsOneLine("${s.name}  ${STATE_KO.getValue(s.state)} · $ctx", e.meta, 600, textW)
            val line = buildAnnotatedString {
                append(s.name)
                withStyle(SpanStyle(color = stCol)) { append("  " + STATE_KO.getValue(s.state)) }
                if (withCtx) withStyle(SpanStyle(color = e.readInk2)) { append(" · $ctx") }
            }
            Txt(line, e.meta, e.ink.ink, weight = 600, maxW = textW)
        } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Txt(buildAnnotatedString { append(s.name); withStyle(SpanStyle(color = stCol)) { append(" · " + STATE_KO.getValue(s.state)) } }, e.read, e.ink.ink, weight = 600, maxW = textW)
            val sub = listOfNotNull(ctx, cacheS?.let { "캐시 ${(it + 59) / 60}분" }).joinToString(" · ")
            if (sub.isNotEmpty()) Txt(sub, e.meta, e.readInk2, weight = 500, num = true, maxW = textW)
        }
    }
}

private val CACHE_WARN_S = 10 * 60L

/**
 * 눈금 session dot: busy = ink, wait = accent, idle = ink ring on a faint fill, off = dashed ink3 ring. Context used
 * rises from the bottom as a page-colour veil; the accent arc around it = the prompt cache's last 10 minutes.
 */
@Composable
private fun SessionDot(e: Env, s: UsageSession, cache: Float, d: Dp) {
    val ink = e.ink
    Canvas(Modifier.size(d)) {
        val r = size.minDimension / 2
        val ring = 2.dp.toPx()
        when (s.state) {
            "busy" -> drawCircle(ink.ink, r)
            "wait" -> drawCircle(ink.acc, r)
            "idle" -> { drawCircle(ink.ink.copy(alpha = 0.15f), r); drawCircle(ink.ink, r - ring / 2, style = Stroke(ring)) }
            else -> drawCircle(ink.ink3, r - 0.8.dp.toPx(), style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.dp.toPx()))))
        }
        val ctx = s.ctx
        if (ctx != null && s.state != "off") {
            clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) }) {
                val hh = size.height * ctx / 100f
                drawRect(ink.bg.copy(alpha = 0.42f), Offset(0f, size.height - hh), Size(size.width, hh))
            }
        }
        if (cache > 0f) {
            val rr = d.toPx() * 0.72f
            drawArc(ink.acc, -90f, 360f * cache, false, Offset(r - rr, r - rr), Size(rr * 2, rr * 2), style = Stroke(max(1.5.dp, d * 0.1f).toPx(), cap = StrokeCap.Round))
        }
    }
}

/** Tall slots: the bots (content, trading) and the last update ("PC 21:40 갱신 · 연결 안 됨"). */
@Composable
private fun Footer(e: Env, u: UsageInfo, update: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(e.ink.ink4))
        u.bots.forEach { (name, st) ->
            Row(Modifier.height(24.dp * e.look.textScale), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val busy = st == "busy"
                val col = when (st) { "busy" -> e.ink.ink; "idle" -> e.ink.ink2; else -> e.ink.ink3 }
                Canvas(Modifier.size(14.dp)) {
                    (if (busy) listOf(0.55f, 1f, 0.72f) else listOf(0.25f, 0.25f, 0.25f)).forEachIndexed { i, k ->
                        val bw = 3.dp.toPx(); val bh = size.height * k
                        drawRoundRect(col, Offset(i * (bw + 2.dp.toPx()), (size.height - bh) / 2), Size(bw, bh), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                    }
                }
                Txt(buildAnnotatedString { append("$name 봇"); withStyle(SpanStyle(color = e.readInk2)) { append("  " + (STATE_KO[st] ?: st)) } }, e.meta, e.ink.ink, weight = 600)
            }
        }
        if (update) Txt("PC ${hhmm(u.fetchedAtEpochSec)} 갱신" + if (u.offline) " · 연결 안 됨" else "", e.meta, e.readInk2, weight = 500, num = true)
    }
}
