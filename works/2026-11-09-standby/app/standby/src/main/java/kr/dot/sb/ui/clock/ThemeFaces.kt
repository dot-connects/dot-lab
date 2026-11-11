package kr.dot.sb.ui.clock

import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FontId
import kr.dot.sb.ui.cards.Ic
import kr.dot.sb.ui.cards.IconView
import kr.dot.sb.ui.cards.Txt
import kr.dot.sb.ui.cards.weatherIc
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.Grid
import kr.dot.sb.ui.theme.Ink
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/*
 * Theme B and C versions of the two faces whose layout (not just colour) differs per theme (mockup r5 B, C):
 * Digital = B masthead between rules / C glowing digits over three pills; Analog = B hairline dial beside a date
 * article / C lamp plate dial beside pills. Same lightness rules as the iPhone faces: one cached canvas per minute,
 * the second hand on its own layer at 1 Hz, every glow a static gradient (none at night).
 */

/** Unit tests pin the second hand (Gradle sets `shot.second`) so shots compare byte-for-byte. */
internal val fixedSecond: Int? = System.getProperty("shot.second")?.toIntOrNull()

/** Seconds 0-59, ticking at 1 Hz off the frame clock while composed (analog second hands). */
@Composable
internal fun rememberSecond(): IntState {
    fixedSecond?.let { return remember { mutableIntStateOf(it) } }
    val second = remember { mutableIntStateOf(((System.currentTimeMillis() / 1000) % 60).toInt()) }
    LaunchedEffect(Unit) {
        while (true) {
            val ms = System.currentTimeMillis()
            second.intValue = ((ms / 1000) % 60).toInt()
            // Wait off the frame clock so idle tests and the UI thread never spin on this ticker.
            withContext(Dispatchers.Default) { delay(1000 - ms % 1000) }
        }
    }
    return second
}

/* ------------------------------------------------------------------ B: masthead Digital */

/**
 * B Digital (mockup r5 B): masthead "11월 9일 월요일" with "서울 7° · 알람 6:30" on the right, a rule, the serif time
 * with a vermilion colon, a rule, then "오늘 남은 시간 2:19" and "제 313 일". Portrait: two masthead rows, hours over
 * minutes. Every word is at least 6 % of the short side.
 */
@Composable
internal fun PaperDigital(style: ClockStyle, d: FaceData, ink: Ink, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val head = rememberUi(700)
    val kick = rememberUi(500)
    val look = Look.of(style, ink)
    val colon = Look(Effect.SOLID, ink.weekday.toArgb(), ink.weekday.toArgb())
    FaceCanvas(modifier) {
        val w = size.width; val h = size.height
        val short = min(w, h)
        val two = h > w
        val m = Grid.margin(short.toDp()).toPx()
        val mx = max(m, 0.06f * w)
        val cw = w - 2 * mx
        head.textSize = 0.068f * short
        kick.textSize = 0.06f * short
        kick.letterSpacing = 0.08f
        val hc = head.capRatio(); val kc = kick.capRatio()
        val right = listOfNotNull(
            d.temp?.let { t -> d.city?.let { "$it $t" } ?: t },
            d.alarmShort?.let { "알람 $it" },
        ).joinToString(" · ")
        val oneRow = head.measureText(d.dateLong) + kick.measureText(right) + 0.05f * w <= cw
        val dateBase = m + head.textSize * hc
        val kickBase = if (oneRow || right.isEmpty()) dateBase else dateBase + 0.035f * short + kick.textSize * kc
        val rule1 = kickBase + 0.045f * short
        val footBase = h - m
        val rule2 = footBase - kick.textSize * kc - 0.045f * short
        val leftMin = 24 * 60 - (d.now.hour * 60 + d.now.minute)
        val remain = "%d:%02d".format(leftMin / 60, leftMin % 60)
        val dayN = "제 ${d.now.dayOfYear} 일"
        val leftFoot = listOf("오늘 남은 시간 $remain", "남은 시간 $remain").firstOrNull { kick.measureText(it) + kick.measureText(dayN) + 0.04f * w <= cw } ?: remain
        val zoneTop = rule1 + 0.05f * short; val zoneBot = rule2 - 0.05f * short
        val gap = 0.03f * h
        val sizePct = style.sizePct / 100f
        val maxCap = (if (two) (zoneBot - zoneTop - gap) / 2 else zoneBot - zoneTop) * sizePct
        val fs = fitSize(p, d, two, maxCap, cw * 1.03f)
        val cap = p.capEm * fs
        val top = (zoneTop + zoneBot - (if (two) 2 * cap + gap else cap)) / 2
        val rp = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink.ink3.toArgb(); strokeWidth = max(1f, 1.dp.toPx()) }
        val bg = bgColors(style, ink)
        val fg = ink.ink.toArgb(); val fg2 = ink.ink2.toArgb()
        val date = head.fit(d.dateLong, cw)
        return@FaceCanvas {
            drawFaceBg(bg, w, h, 0f)
            head.color = fg; drawText(date, mx, dateBase, head)
            kick.color = fg2
            if (right.isNotEmpty()) {
                if (oneRow) drawText(right, w - mx - kick.measureText(right), kickBase, kick) else drawText(kick.fit(right, cw), mx, kickBase, kick)
            }
            drawLine(mx, rule1, w - mx, rule1, rp)
            if (two) {
                p.draw(this, d.h, mx, top + cap, fs, look)
                p.draw(this, d.m, mx, top + 2 * cap + gap, fs, look)
            } else p.drawHm(this, d.h, d.m, mx, top + cap, fs, look, colon)
            drawLine(mx, rule2, w - mx, rule2, rp)
            drawText(leftFoot, mx, footBase, kick)
            drawText(dayN, w - mx - kick.measureText(dayN), footBase, kick)
        }
    }
}

/* ------------------------------------------------------------------ C: lamp Digital */

/** The C chips: "월 9일", weather glyph + "7°", alarm glyph + "6:30" on round plate2 pills. */
@Composable
internal fun LampPills(d: FaceData, ink: Ink, text: Dp, modifier: Modifier, column: Boolean = false) {
    val items = buildList {
        add(Triple<Ic?, String, String>(null, if (column) "${d.weekday}요일" else "${d.weekday} ${d.day}일", if (column) "${d.day}" else ""))
        d.temp?.let { add(Triple(d.cond?.let(::weatherIc) ?: Ic.CLOUD, "", it)) }
        d.alarmShort?.let { add(Triple(Ic.ALARM, "", it)) }
    }
    val pill = @Composable { ic: Ic?, word: String, num: String, mod: Modifier ->
        Row(
            mod.height(text * (if (column) 2.6f else 1.95f)).clip(RoundedCornerShape(50)).background(ink.plate2)
                .padding(horizontal = text * (if (column) 0.75f else 0.6f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (ic != null) {
                IconView(ic, ink.ink, Modifier.size(text * 1.05f), ink.plate2, color2 = if (ic == Ic.ALARM) ink.ink else ink.sun)
                Spacer(Modifier.width(text * 0.35f))
            }
            if (word.isNotEmpty()) Txt(word, text, ink.ink, weight = 500, num = true)
            if (word.isNotEmpty() && num.isNotEmpty()) Spacer(Modifier.width(text * 0.3f))
            // the value in the lamp's heavy rounded numerals (column = the analog face's big chips)
            if (num.isNotEmpty()) Txt(num, if (column) text * 1.2f else text, ink.ink, weight = if (column) 900 else 500, family = if (column) Fonts.nunitoHeavy else null, cap = if (column) 0.705f else 0.707f, num = !column)
        }
    }
    if (column) Column(modifier, verticalArrangement = Arrangement.spacedBy(text * 0.55f)) {
        items.forEach { (ic, w, n) -> pill(ic, w, n, Modifier.fillMaxWidth()) }
    } else Row(modifier, horizontalArrangement = Arrangement.spacedBy(text * 0.45f)) {
        items.forEach { (ic, w, n) -> pill(ic, w, n, Modifier) }
    }
}

/**
 * C Digital (mockup r5 C): butter → peach digits (top to bottom) centred over a soft peach pool of light, three pills
 * along the bottom. Portrait: hours over minutes. The pool is a static radial gradient and is gone at night.
 */
@Composable
internal fun LampDigital(style: ClockStyle, d: FaceData, ink: Ink, modifier: Modifier) {
    val p = rememberTimePainter(style)
    val look = Look.of(style, ink, vertical = true)
    val pool = lerp(Color.Transparent, ink.orange.copy(alpha = 0.22f), 1f - ink.night)
    BoxWithConstraints(modifier) {
        val short = minOf(maxWidth, maxHeight)
        val text = short * 0.066f
        val pillH = text * 1.95f
        val bottom = maxHeight * 0.09f
        val reserve = bottom + pillH + short * (if (style.showInfo) 0.07f else 0f)
        FaceCanvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            val two = h > w
            val gap = 0.03f * h
            val zoneTop = 0.06f * h
            val zoneBot = h - (if (style.showInfo) reserve.toPx() else 0.06f * h)
            val maxCap = (if (two) (zoneBot - zoneTop - gap) / 2 else zoneBot - zoneTop) * style.sizePct / 100f
            val fs = fitSize(p, d, two, maxCap, 0.9f * w)
            val cap = p.capEm * fs
            val blockH = if (two) 2 * cap + gap else cap
            val top = (zoneTop + zoneBot - blockH) / 2
            val bg = bgColors(style, ink)
            val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.RadialGradient(
                    w / 2, top + blockH * 0.45f, max(blockH, w * 0.4f), pool.toArgb(), Color.Transparent.toArgb(), android.graphics.Shader.TileMode.CLAMP,
                )
            }
            return@FaceCanvas {
                drawFaceBg(bg, w, h, 0f)
                if (pool.alpha > 0f) drawRect(0f, 0f, w, h, glow)
                drawTime(p, d, fs, two, gap, w / 2, top, look)
            }
        }
        if (style.showInfo) LampPills(d, ink, text, Modifier.align(Alignment.BottomCenter).padding(bottom = bottom))
    }
}
