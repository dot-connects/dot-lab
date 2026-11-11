package kr.dot.sb.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import kr.dot.sb.ui.cards.Ic
import kr.dot.sb.ui.cards.IconView
import kr.dot.sb.ui.cards.Txt
import kr.dot.sb.ui.cards.italicSpans
import kr.dot.sb.ui.cards.lampPlate
import kr.dot.sb.ui.cards.nightFilter
import kr.dot.sb.ui.cards.tap
import kr.dot.sb.ui.state.AlarmRinging
import kr.dot.sb.ui.state.LiveActivity
import kr.dot.sb.ui.state.MediaInfo
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.OverlayType
import kr.dot.sb.ui.theme.LocalInk
import kr.dot.sb.ui.theme.LocalTheme
import kotlin.math.roundToInt

/*
 * Theme B and C versions of the interrupt layers whose layout differs (mockup r5 B, C): alarm, Live Activity and the
 * Now Playing body. Lifecycle (fades, auto-close, position ticking) stays in Overlays.kt; these only draw. Every glow
 * is a static gradient and is gone at night.
 */

private val WEEK = arrayOf("월", "화", "수", "목", "금", "토", "일")

/** B / C alarm title: the user's label, else "일어날 시간". */
private fun alarmTitle(a: AlarmRinging) = if (a.label.isBlank() || a.label == "알람") "일어날 시간" else a.label

// ------------------------------------------------------------------------------------------------- alarm

/**
 * B alarm (mockup r5 B): huge serif time top-left, "일어날 시간" + "알람 · 월요일" top-right, a rule, then a vermilion
 * band (다시 알림, black text) and an outlined box (중단). Portrait: text under the time, the two boxes stacked.
 */
@Composable
internal fun PaperAlarm(a: AlarmRinging, state: StandbyState, actions: StandbyActions) {
    val ink = LocalInk.current
    val t = LocalTheme.current
    BoxWithConstraints(Modifier.fillMaxSize().background(ink.bg).tap {}) {
        val w = maxWidth; val h = maxHeight
        val port = h > w
        val s = min(w, h)
        val pad = screenPadding(w, h)
        val px = pad.calculateLeftPadding(LayoutDirection.Ltr)
        val cw = w - px * 2
        val head = max(22.dp, s * 0.075f)
        val kick = max(18.dp, s * 0.06f)
        val time = clockText(state.now, state.use24h)
        val dark = Color.Black
        val sub = "알람 · ${WEEK[state.now.dayOfWeek.value - 1]}요일"
        val words = @Composable { align: Alignment.Horizontal ->
            Column(horizontalAlignment = align) {
                Txt(alarmTitle(a), head, ink.ink, weight = 800, maxW = cw * 0.45f)
                Spacer(Modifier.height(s * 0.03f))
                Txt(sub, kick, ink.ink2, weight = 500, tracking = t.labelTracking)
            }
        }
        val buttons = @Composable { mod: Modifier, stack: Boolean ->
            val snooze = @Composable { m: Modifier ->
                BoxWithConstraints(m.background(ink.snooze).tap(actions::alarmSnooze), contentAlignment = Alignment.Center) {
                    Txt("다시 알림", min(maxHeight * 0.4f, s * 0.085f), dark, weight = 800, maxW = maxWidth * 0.85f)
                }
            }
            val stop = @Composable { m: Modifier ->
                BoxWithConstraints(m.border(1.dp, ink.ink).tap(actions::alarmStop), contentAlignment = Alignment.Center) {
                    Txt("중단", min(maxHeight * 0.4f, s * 0.085f), ink.ink, weight = 800, maxW = maxWidth * 0.85f)
                }
            }
            if (stack) Column(mod, verticalArrangement = Arrangement.spacedBy(h * 0.02f)) {
                if (a.canSnooze) snooze(Modifier.fillMaxWidth().weight(1f))
                stop(Modifier.fillMaxWidth().weight(1f))
            } else Row(mod, horizontalArrangement = Arrangement.spacedBy(w * 0.04f)) {
                if (a.canSnooze) snooze(Modifier.weight(0.63f).fillMaxHeight())
                stop(Modifier.weight(0.37f).fillMaxHeight())
            }
        }
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (!port) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                        Txt(time, h * 0.46f / t.timeCap, ink.ink, family = t.time, cap = t.timeCap, weight = 400, maxW = cw * 0.6f, maxCapH = h * 0.46f)
                    }
                    words(Alignment.End)
                }
                Spacer(Modifier.height(h * 0.04f))
                Box(Modifier.fillMaxWidth().height(1.dp).background(ink.ink3))
                Spacer(Modifier.height(h * 0.04f))
                buttons(Modifier.fillMaxWidth().height(h * 0.2f), false)
            } else {
                Txt(time, h * 0.2f / t.timeCap, ink.ink, family = t.time, cap = t.timeCap, weight = 400, maxW = cw)
                Spacer(Modifier.height(h * 0.04f))
                words(Alignment.Start)
                Spacer(Modifier.weight(1f))
                Box(Modifier.fillMaxWidth().height(1.dp).background(ink.ink3))
                Spacer(Modifier.height(h * 0.03f))
                buttons(Modifier.fillMaxWidth().height(h * 0.26f), true)
            }
        }
    }
}

/**
 * C alarm (mockup r5 C): a sunrise glow from the bottom edge, butter "알람 · 일어날 시간" over the big round time,
 * a butter pill (다시 알림) and a dark outlined pill (중단).
 */
@Composable
internal fun LampAlarm(a: AlarmRinging, state: StandbyState, actions: StandbyActions) {
    val ink = LocalInk.current
    val t = LocalTheme.current
    val k = 1f - ink.night
    val peach = ink.orange; val rose = ink.weekday
    BoxWithConstraints(Modifier.fillMaxSize().background(ink.bg).tap {}) {
        val w = maxWidth; val h = maxHeight
        val port = h > w
        val s = min(w, h)
        val pad = screenPadding(w, h)
        if (k > 0f) Canvas(Modifier.fillMaxSize()) {
            drawRect(
                Brush.radialGradient(
                    0f to peach.copy(alpha = 0.7f * k), 0.6f to rose.copy(alpha = 0.25f * k), 1f to Color.Transparent,
                    center = Offset(size.width / 2, size.height * 1.1f), radius = maxOf(size.width * 0.6f, size.height * 0.75f),
                ),
            )
        }
        val label = max(20.dp, s * 0.07f)
        val dark = lerp(Color(0xFF2B1D07), Color.Black, ink.night)
        Column(Modifier.fillMaxSize().padding(pad), horizontalAlignment = Alignment.CenterHorizontally) {
            Txt("알람 · ${alarmTitle(a)}", label, ink.sun, weight = 500, maxW = w * 0.8f)
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                val cap = h * if (port) 0.2f else 0.32f
                Txt(clockText(state.now, state.use24h), cap / t.timeCap, ink.ink, family = t.time, cap = t.timeCap, weight = 900, tnum = true, maxW = w * 0.86f, maxCapH = cap)
            }
            val pill = @Composable { text: String, bg: Color, fg: Color, outline: Boolean, m: Modifier, onClick: () -> Unit ->
                BoxWithConstraints(
                    m.clip(RoundedCornerShape(50)).background(bg)
                        .then(if (outline) Modifier.border(max(2.dp, s * 0.005f), ink.ink.copy(alpha = 0.25f), RoundedCornerShape(50)) else Modifier)
                        .tap(onClick),
                    contentAlignment = Alignment.Center,
                ) { Txt(text, min(maxHeight * 0.36f, s * 0.08f), fg, weight = 600, maxW = maxWidth * 0.8f) }
            }
            if (!port) Row(Modifier.fillMaxWidth().height(h * 0.22f), horizontalArrangement = Arrangement.spacedBy(w * 0.04f)) {
                if (a.canSnooze) pill("다시 알림", ink.snooze, dark, false, Modifier.weight(0.6f).fillMaxHeight(), actions::alarmSnooze)
                pill("중단", Color.Black.copy(alpha = 0.66f), ink.ink, true, Modifier.weight(0.4f).fillMaxHeight(), actions::alarmStop)
            } else Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(h * 0.02f)) {
                if (a.canSnooze) pill("다시 알림", ink.snooze, dark, false, Modifier.fillMaxWidth().height(h * 0.13f), actions::alarmSnooze)
                pill("중단", Color.Black.copy(alpha = 0.66f), ink.ink, true, Modifier.fillMaxWidth().height(h * 0.13f), actions::alarmStop)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------- live activity

/**
 * B Live Activity: kicker (app) over a rule, the value in big serif, title + detail as headline and kicker, progress
 * as square step bars in the accent.
 */
@Composable
internal fun PaperLive(l: LiveActivity, actions: StandbyActions) {
    val ink = LocalInk.current
    val t = LocalTheme.current
    BoxWithConstraints(Modifier.fillMaxSize().background(ink.bg).tap(actions::liveToggle)) {
        val w = maxWidth; val h = maxHeight
        val s = min(w, h)
        val pad = screenPadding(w, h)
        val cw = w - pad.calculateLeftPadding(LayoutDirection.Ltr) * 2
        val kick = max(18.dp, s * 0.06f)
        Column(Modifier.fillMaxSize().padding(pad)) {
            Txt(l.appLabel, kick, ink.ink2, weight = 500, tracking = t.labelTracking, maxW = cw)
            Spacer(Modifier.height(s * 0.04f))
            Box(Modifier.fillMaxWidth().height(1.dp).background(ink.ink3))
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                Txt(l.value, h * 0.36f / t.numeralsCap, ink.ink, cap = t.numeralsCap, weight = 400, num = true, maxW = cw, maxCapH = h * 0.36f)
            }
            Txt(l.title, max(22.dp, s * 0.075f), ink.ink, weight = 800, maxW = cw)
            Spacer(Modifier.height(s * 0.03f))
            Txt(l.detail, kick, ink.ink2, weight = 500, maxW = cw, num = true)
            l.progress?.let { p ->
                Spacer(Modifier.height(s * 0.05f))
                val on = (p.coerceIn(0f, 1f) * l.steps).roundToInt().coerceAtLeast(if (p > 0f) 1 else 0)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(s * 0.02f)) {
                    repeat(l.steps) { Box(Modifier.weight(1f).height(max(5.dp, s * 0.014f)).background(if (it < on) ink.weekday else ink.ink3)) }
                }
            }
        }
    }
}

/** C Live Activity: one big peach-lit plate with the app pill, title, the value in heavy round digits, round steps. */
@Composable
internal fun LampLive(l: LiveActivity, actions: StandbyActions) {
    val ink = LocalInk.current
    val t = LocalTheme.current
    BoxWithConstraints(Modifier.fillMaxSize().background(ink.bg).tap(actions::liveToggle)) {
        val w = maxWidth; val h = maxHeight
        val s = min(w, h)
        val pad = screenPadding(w, h)
        val label = max(18.dp, s * 0.066f)
        Box(Modifier.fillMaxSize().padding(pad).lampPlate(ink, ink.pal.orange, s * 0.09f)) {
            val ip = s * 0.06f
            Column(Modifier.fillMaxSize().padding(ip)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconView(Ic.TIMER, ink.amber, Modifier.size(label * 1.1f))
                    Spacer(Modifier.width(label * 0.4f))
                    Txt(l.appLabel, label, ink.amber, weight = 600)
                }
                Spacer(Modifier.height(s * 0.04f))
                Txt(l.title, label, ink.ink2, weight = 500)
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) {
                    Txt(l.value, h * 0.3f / t.numeralsCap, ink.ink, cap = t.numeralsCap, weight = 800, maxW = w * 0.8f, maxCapH = h * 0.3f, num = true)
                }
                Txt(l.detail, label, ink.ink2, weight = 500, num = true)
                l.progress?.let { p ->
                    Spacer(Modifier.height(s * 0.04f))
                    val on = (p.coerceIn(0f, 1f) * l.steps).roundToInt().coerceAtLeast(if (p > 0f) 1 else 0)
                    val bar = max(10.dp, s * 0.03f)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(bar)) {
                        repeat(l.steps) { Box(Modifier.weight(1f).height(bar).clip(RoundedCornerShape(50)).background(if (it < on) ink.amber else ink.plate2)) }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------- now playing

/**
 * B Now Playing body (mockup r5 B): square art, then "지금 재생", the title set like a cover line in italic serif,
 * the artist in bold, a hairline progress with a vermilion knob, times, and word buttons "이전 멈춤 다음".
 */
@Composable
internal fun PaperNowPlayingBody(m: MediaInfo, pos: Long, actions: StandbyActions, w: Dp, h: Dp) {
    val ink = LocalInk.current
    val t = LocalTheme.current
    val s = min(w, h)
    val port = h > w
    val kick = OverlayType.meta(s)
    val art = if (port) min(w * 0.82f, h * 0.4f) else min(w * 0.36f, h * 0.76f)
    val artX = if (port) (w - art) / 2 else w * 0.06f
    val artY = if (port) h * 0.1f else (h - art) / 2
    Box(Modifier.offset(artX, artY).size(art).background(ink.ink4), contentAlignment = Alignment.Center) {
        val img = m.art
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = nightFilter(ink.night))
        else IconView(Ic.NOTE, ink.ink3, Modifier.fillMaxSize(0.4f), ink.ink4)
    }
    val colX = if (port) artX else w * 0.57f
    val colW = if (port) art else w * 0.37f
    val colY = if (port) artY + art + h * 0.04f else artY
    val colH = if (port) h * 0.92f - colY else art
    Column(Modifier.offset(colX, colY).size(colW, colH), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Txt("지금 재생", kick, ink.ink2, weight = 500, tracking = t.labelTracking)
            Spacer(Modifier.height(s * 0.03f))
            Txt(italicSpans(m.title), OverlayType.title(s) * 1.6f, ink.ink, weight = 400, maxW = colW) // r7: was 104 dp
            Spacer(Modifier.height(s * 0.03f))
            Txt(m.artist, OverlayType.sub(s), ink.ink, weight = 800, maxW = colW)
        }
        Column {
            val frac = (if (m.durationMs > 0) pos.toFloat() / m.durationMs else 0f).coerceIn(0f, 1f)
            val knob = max(12.dp, s * 0.033f)
            Box(Modifier.fillMaxWidth().height(knob), contentAlignment = Alignment.CenterStart) {
                Box(Modifier.fillMaxWidth().height(max(2.dp, s * 0.005f)).background(ink.ink3))
                Box(Modifier.fillMaxWidth(frac).height(max(2.dp, s * 0.005f)).background(ink.ink))
                Box(Modifier.offset((colW - knob) * frac).size(knob).clip(CircleShape).background(ink.weekday))
            }
            Spacer(Modifier.height(s * 0.03f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Txt(mmss(pos), kick, ink.ink2, weight = 500, tnum = true, num = true)
                Txt("-" + mmss((m.durationMs - pos).coerceAtLeast(0)), kick, ink.ink2, weight = 500, tnum = true, num = true)
            }
            Spacer(Modifier.height(s * 0.06f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val b = OverlayType.sub(s) * 1.15f
                Txt("이전", b, if (m.canPrev) ink.ink else ink.ink3, Modifier.semantics { contentDescription = "이전" }.tap(if (m.canPrev) actions::mediaPrev else null), weight = 700)
                Txt(if (m.playing) "멈춤" else "재생", b, ink.weekday, Modifier.tap(actions::mediaPlayPause), weight = 700)
                Txt("다음", b, if (m.canNext) ink.ink else ink.ink3, Modifier.semantics { contentDescription = "다음" }.tap(if (m.canNext) actions::mediaNext else null), weight = 700)
            }
        }
    }
}

/**
 * C Now Playing body (mockup r5 C): rounded art, heavy title, soft artist, a fat butter progress bar, prev / next on
 * plate discs around a big butter play disc. The album-colour wash comes from Overlays (artTint); this adds the peach
 * pool bottom-right.
 */
@Composable
internal fun LampNowPlayingBody(m: MediaInfo, pos: Long, actions: StandbyActions, w: Dp, h: Dp) {
    val ink = LocalInk.current
    val s = min(w, h)
    val port = h > w
    val k = 1f - ink.night
    val peach = ink.orange
    if (k > 0f) Canvas(Modifier.fillMaxSize()) {
        drawRect(
            Brush.radialGradient(
                0f to peach.copy(alpha = 0.18f * k), 1f to Color.Transparent,
                center = Offset(size.width * 0.8f, size.height), radius = size.width * 0.5f,
            ),
        )
    }
    val art = if (port) min(w * 0.8f, h * 0.44f) else min(w * 0.36f, h * 0.74f)
    val artX = if (port) (w - art) / 2 else w * 0.06f
    val artY = if (port) h * 0.1f else (h - art) / 2
    Box(Modifier.offset(artX, artY).size(art).clip(RoundedCornerShape(art * 0.12f)).background(ink.plate2), contentAlignment = Alignment.Center) {
        val img = m.art
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = nightFilter(ink.night))
        else IconView(Ic.NOTE, ink.ink3, Modifier.fillMaxSize(0.4f), ink.plate2)
    }
    val colX = if (port) artX else w * 0.58f
    val colW = if (port) art else w * 0.36f
    val colY = if (port) artY + art + h * 0.04f else artY
    val colH = if (port) h * 0.92f - colY else art
    val dark = lerp(Color(0xFF2B1D07), Color.Black, ink.night)
    Column(Modifier.offset(colX, colY).size(colW, colH), verticalArrangement = Arrangement.SpaceBetween) {
        Column {
            Txt(m.title, OverlayType.title(s) * 1.15f, ink.ink, weight = 900, maxW = colW, num = true) // r7: was 62 dp
            Spacer(Modifier.height(s * 0.035f))
            Txt(m.artist, OverlayType.sub(s), ink.ink2, weight = 500, maxW = colW, num = true)
        }
        val frac = (if (m.durationMs > 0) pos.toFloat() / m.durationMs else 0f).coerceIn(0f, 1f)
        val bar = max(8.dp, s * 0.04f)
        Box(Modifier.fillMaxWidth().height(bar).clip(RoundedCornerShape(50)).background(ink.ink.copy(alpha = 0.14f))) {
            Box(Modifier.fillMaxWidth(frac).height(bar).clip(RoundedCornerShape(50)).background(ink.sun))
        }
        val big = min(s * 0.26f, colW * 0.4f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val disc = @Composable { ic: Ic, d: Dp, bg: Color, fg: Color, enabled: Boolean, desc: String, onClick: () -> Unit ->
                Box(
                    Modifier.size(d).clip(CircleShape).background(bg).semantics { contentDescription = desc }.tap(if (enabled) onClick else null),
                    contentAlignment = Alignment.Center,
                ) { IconView(ic, if (enabled) fg else ink.ink3, Modifier.size(d * 0.4f)) }
            }
            disc(Ic.PREV, big * 0.65f, ink.plate2, ink.ink, m.canPrev, "이전", actions::mediaPrev)
            disc(if (m.playing) Ic.PAUSE else Ic.PLAY, big, ink.sun, dark, true, if (m.playing) "일시정지" else "재생", actions::mediaPlayPause)
            disc(Ic.NEXT, big * 0.65f, ink.plate2, ink.ink, m.canNext, "다음", actions::mediaNext)
        }
    }
}

