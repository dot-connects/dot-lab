package kr.dot.sb.ui.cards

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kr.dot.sb.data.BatteryRules
import kr.dot.sb.data.ChargeKind
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.FontId
import kr.dot.sb.platform.Brightness
import kr.dot.sb.ui.state.BatteryInfo
import kr.dot.sb.ui.state.PhoneControls
import kr.dot.sb.ui.state.Ringer
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState
import kr.dot.sb.ui.theme.Fonts
import kr.dot.sb.ui.theme.SizeClass
import kotlin.math.pow
import kotlin.math.roundToInt

/** How the 배터리 card lays out a slot (mockup battery-card.html round 3, `card()`). */
internal enum class BatteryShape { TINY, SHORT, PHONE, TALL, SQUARE, WIDE }

/** Slot → layout: the two control layouts need the room (square ≥ M and 380 dp tall, wide ≥ 280 dp tall). */
internal fun batteryShape(w: Dp, h: Dp): BatteryShape {
    val r = w / h
    return when {
        SizeClass.of(min(w, h)) == SizeClass.XS -> BatteryShape.TINY
        r >= 1.5f && h >= 280.dp -> BatteryShape.WIDE
        r >= 1.15f -> BatteryShape.SHORT
        h / w >= 1.5f -> BatteryShape.TALL
        SizeClass.of(min(w, h)) >= SizeClass.M && h >= 380.dp -> BatteryShape.SQUARE
        else -> BatteryShape.PHONE
    }
}

/** Colours and sizes of one 배터리 card, all from the theme ink (night tokens included). */
private class BatteryLook(val e: Env, val b: BatteryInfo) {
    val short: Dp = min(e.w, e.h)
    val meta: Dp = (short * 0.07f).coerceIn(13.dp, 15.dp)
    val read: Dp = (short * 0.08f).coerceIn(15.dp, 21.dp)
    val day = e.ink.night < 0.5f
    /** Day only (mockup round 2: no warn colour at night): low, slow, hot. */
    val warn = day && (b.pct <= 20 || b.kind == ChargeKind.SLOW || b.tempC >= 40)
    val warnC: Color = e.ink.red
    /** Fill and curve colour. */
    val fillC: Color = if (warn) warnC else e.ink.acc
    private val light = e.t.tokens.light
    private fun n(dayV: Float, nightV: Float) = dayV + (nightV - dayV) * e.ink.night
    val fillLo = n(if (light) 0.06f else 0.10f, 0.04f)
    val fillHi = n(if (light) 0.16f else 0.26f, 0.12f)
    /** Slider fill. */
    val ctl: Color = e.ink.acc.copy(alpha = n(if (light) 0.26f else 0.42f, 0.22f))
    val plate2: Color get() = e.ink.ink4
}

@Composable
internal fun BatteryCard(e: Env, state: StandbyState, config: AppConfig, actions: StandbyActions) {
    val b = state.battery ?: return StateCard(e, Ic.BATT, "배터리", Ic.BATT, null, null, null)
    val l = remember(e, b) { BatteryLook(e, b) }
    val frac by animateFloatAsState(if (b.kind == ChargeKind.PROTECT || b.kind == ChargeKind.FULL) 1f else b.pct / 100f, tween(800), label = "fill")
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(e.plateRadius)).chargeFill(frac, l)) {
        val shape = batteryShape(e.w, e.h)
        Column(Modifier.fillMaxSize().padding(e.pad)) {
            when (shape) {
                BatteryShape.TINY -> {
                    Label(l)
                    Spacer(Modifier.weight(1f))
                    Pct(l, min(46.dp, e.ch * 0.42f), e.cw)
                    Spacer(Modifier.weight(1f))
                    Eta(l, state)
                }
                BatteryShape.SHORT -> {
                    Label(l, trailing = (if (b.kind.plugged && b.watts > 0f) "${BatteryRules.wattText(b.watts)}W · " else "") + "${b.tempC}°")
                    Spacer(Modifier.weight(1f))
                    Pct(l, min(96.dp, e.ch * 0.45f), e.cw)
                    Spacer(Modifier.weight(1f))
                    EtaSub(l, state)
                }
                BatteryShape.PHONE -> {
                    Label(l); Spacer(Modifier.height(12.dp))
                    Pct(l, min(80.dp, e.ch * 0.3f), e.cw); Spacer(Modifier.height(10.dp))
                    EtaSub(l, state)
                    Spacer(Modifier.weight(1f))
                    Stats(l, baseStats(l), e.cw)
                }
                BatteryShape.TALL -> {
                    Label(l); Spacer(Modifier.height(16.dp))
                    Pct(l, min(100.dp, e.ch * 0.22f), e.cw); Spacer(Modifier.height(14.dp))
                    EtaSub(l, state)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(e.ink.ink3))
                    Spacer(Modifier.height(16.dp))
                    Stats(l, baseStats(l) + moreStats(l, state, config), e.cw)
                }
                BatteryShape.SQUARE -> {
                    Label(l); Spacer(Modifier.height(12.dp))
                    Pct(l, min(104.dp, e.ch * 0.22f), e.cw); Spacer(Modifier.height(12.dp))
                    EtaSub(l, state)
                    Spacer(Modifier.weight(1f))
                    Stats(l, baseStats(l), e.cw)
                    Spacer(Modifier.weight(1f))
                    state.phone?.let { p ->
                        Modes(l, p, actions, iconOnly = false)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BrightPill(l, state, config, actions, false, Modifier.weight(1f).height(56.dp))
                            VolumePill(l, p, actions, false, Modifier.weight(1f).height(56.dp))
                        }
                    }
                }
                BatteryShape.WIDE -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    val c1 = min(200.dp, e.cw * 0.3f)
                    Column(Modifier.width(c1).fillMaxHeight()) {
                        Label(l); Spacer(Modifier.height(16.dp))
                        Pct(l, min(124.dp, e.ch * 0.34f), c1); Spacer(Modifier.height(14.dp))
                        EtaSub(l, state, c1)
                        Spacer(Modifier.weight(1f))
                        Stats(l, baseStats(l), c1)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        val first = b.curve.firstOrNull()
                        // meta size, not the card label: the curve column is ~260 dp and the label size clipped it (mockup: a small caption)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconView(Ic.PLUG, e.ink.ink2, Modifier.size(l.meta * 1.15f), e.ink.bg)
                            Spacer(Modifier.width(l.meta * 0.4f))
                            Txt(if (b.kind.plugged && first != null) "${hhmm(first.first / 1000)}에 ${first.second}%로 꽂음" else "충전 곡선", l.meta, e.ink.ink2, weight = 600)
                        }
                        Spacer(Modifier.height(14.dp))
                        Curve(l, state, Modifier.fillMaxWidth().weight(1f))
                    }
                    state.phone?.let { p ->
                        Column(Modifier.width(CTL_W).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                BrightPill(l, state, config, actions, true, Modifier.weight(1f).fillMaxHeight())
                                VolumePill(l, p, actions, true, Modifier.weight(1f).fillMaxHeight())
                            }
                            Modes(l, p, actions, iconOnly = true)
                            DndSwitch(l, p, actions)
                        }
                    }
                }
            }
        }
    }
}

/** Wide layout's control column: three 48 dp icon segments in the mode row. */
private val CTL_W = 160.dp

/** Rises from the bottom to [frac]: [BatteryLook.fillHi] at the bottom, [BatteryLook.fillLo] near the top, the top 28 dp feathered (no water line). */
private fun Modifier.chargeFill(frac: Float, l: BatteryLook): Modifier = drawBehind {
    if (frac <= 0f) return@drawBehind
    val top = size.height * (1f - frac.coerceIn(0f, 1f))
    val h = size.height - top
    val f = (28.dp.toPx() / h).coerceAtMost(1f)
    val c = l.fillC
    drawRect(
        Brush.verticalGradient(0f to c.copy(alpha = 0f), f to c.copy(alpha = l.fillLo), 1f to c.copy(alpha = l.fillHi), startY = top, endY = size.height),
        topLeft = Offset(0f, top), size = Size(size.width, h),
    )
}

private val BatteryInfo.labelIc: Ic get() = when (kind) { ChargeKind.UNPLUGGED -> Ic.BATT; ChargeKind.PROTECT -> Ic.SHIELD; else -> Ic.BOLT }

/** Icon + state ("고속 충전", "배터리 보호", "완충", "배터리"): accent while charging, warn when low/slow/hot, else ink2. */
@Composable
private fun Label(l: BatteryLook, trailing: String? = null) {
    val b = l.b
    val c = when {
        l.warn && b.kind.plugged -> l.warnC
        b.kind.plugged && b.kind != ChargeKind.PROTECT -> l.e.ink.accText
        else -> l.e.ink.ink2
    }
    LabelRow(l.e, b.labelIc, b.kind.ko, c, trailing = trailing)
}

/** The big thin number with a small top-aligned "%", width-fit so "100%" never clips (mockup `num()`). */
@Composable
private fun Pct(l: BatteryLook, max: Dp, maxW: Dp) {
    val e = l.e
    val latin = e.t.tokens.latinFont
    val fam = latin ?: Fonts.clock(FontId.LIGHT, 200)
    val cap = if (latin != null) e.numCap else 0.727f // Inter
    val digits = l.b.pct.toString()
    // Inter 200 digit ≈ 0.56 em, "%" at 0.3 em + gap ≈ 0.42 em
    val size = min(max, maxW / (digits.length * 0.56f + 0.42f))
    Row(verticalAlignment = Alignment.Top) {
        Txt(digits, size, if (l.warn) l.warnC else e.ink.ink, weight = 200, family = fam, cap = cap, tracking = -0.055f, maxW = maxW - size * 0.42f)
        Spacer(Modifier.width(size * 0.04f))
        Txt("%", size * 0.3f, e.ink.ink2, weight = 400, family = fam, cap = cap)
    }
}

/** "16:40 완충" / "85%에서 멈춤" / "14:52부터 가득" / "충전기 빠짐"; null while charging with no estimate. */
private fun etaText(b: BatteryInfo, state: StandbyState): String? = when (b.kind) {
    ChargeKind.UNPLUGGED -> "충전기 빠짐"
    ChargeKind.PROTECT -> "${b.pct}%에서 멈춤"
    ChargeKind.FULL -> b.fullSince?.let { "${hhmm(it / 1000)}부터 가득" } ?: "가득 참"
    else -> b.leftMs?.let { "${BatteryRules.fullAt(state.now, it)} 완충" }
}

private fun subText(b: BatteryInfo): String? = when (b.kind) {
    ChargeKind.UNPLUGGED -> "꽂으면 완충 시각 표시"
    ChargeKind.PROTECT -> "갤럭시 배터리 보호 켜짐"
    ChargeKind.FULL -> "충전기를 빼도 돼요"
    else -> BatteryRules.left(b.leftMs)?.let { "$it 남음" }
}

@Composable
private fun Eta(l: BatteryLook, state: StandbyState, maxW: Dp = l.e.cw) {
    val t = etaText(l.b, state) ?: return
    val c = when {
        l.b.kind == ChargeKind.UNPLUGGED -> l.e.ink.ink
        l.warn -> l.warnC
        else -> l.e.ink.accText
    }
    Txt(t, l.read, c, weight = 600, num = true, maxW = maxW)
}

@Composable
private fun ColumnScope.EtaSub(l: BatteryLook, state: StandbyState, maxW: Dp = l.e.cw) {
    Eta(l, state, maxW)
    subText(l.b)?.let {
        Spacer(Modifier.height(3.dp + l.meta * 0.3f))
        Txt(it, l.meta, l.e.readInk2, weight = 500, num = true)
    }
}

/** One stat block: small ink2 key with an icon over a bigger ink value with a small unit. */
private class Stat(val ic: Ic, val key: String, val value: String, val unit: String? = null, val warn: Boolean = false)

private fun baseStats(l: BatteryLook): List<Stat> {
    val b = l.b
    val key = when (b.kind) { ChargeKind.SLOW, ChargeKind.FAST, ChargeKind.SUPER -> b.kind.ko; else -> "충전" }
    val charge = when {
        !b.kind.plugged -> Stat(Ic.PLUG, key, "안 함")
        b.kind == ChargeKind.PROTECT -> Stat(Ic.PLUG, key, "멈춤")
        b.kind == ChargeKind.FULL -> Stat(Ic.PLUG, key, "완충")
        b.watts > 0f -> Stat(Ic.PLUG, key, BatteryRules.wattText(b.watts), "W", l.day && b.kind == ChargeKind.SLOW)
        else -> Stat(Ic.PLUG, key, "—")
    }
    return listOf(charge, Stat(Ic.THERMO, "온도", b.tempC.toString(), "°", l.day && b.tempC >= 40))
}

private fun ringerIc(r: Ringer) = when (r) { Ringer.SOUND -> Ic.SPEAKER; Ringer.VIBRATE -> Ic.VIBRATE; Ringer.SILENT -> Ic.MUTE }

private fun moreStats(l: BatteryLook, state: StandbyState, config: AppConfig): List<Stat> {
    val p = state.phone
    val first = l.b.curve.firstOrNull()
    return listOf(
        Stat(p?.let { ringerIc(it.ringer) } ?: Ic.SPEAKER, "소리", p?.ringer?.ko ?: "—"),
        Stat(Ic.SUN, "밝기", (Brightness.shown(state.night >= 0.5f, config) * 100).roundToInt().toString(), "%"),
        Stat(if (p?.network == "모바일 데이터") Ic.CELL else Ic.WIFI, "네트워크", p?.network ?: "끊김"),
        if (l.b.kind.plugged && first != null) Stat(Ic.BATT, "꽂은 뒤", "+${l.b.pct - first.second}", "%") else Stat(Ic.BATT, "꽂은 뒤", "—"),
    )
}

@Composable
private fun Stats(l: BatteryLook, items: List<Stat>, width: Dp) {
    val e = l.e
    val colW = (width - 12.dp) / 2
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { s ->
                    Column(Modifier.width(colW), verticalArrangement = Arrangement.spacedBy(4.dp + l.meta * 0.35f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconView(s.ic, e.ink.ink2, Modifier.size(14.dp))
                            Spacer(Modifier.width(5.dp))
                            Txt(s.key, l.meta, e.ink.ink2, weight = 500)
                        }
                        val v = l.read * 1.15f
                        Row(verticalAlignment = Alignment.Bottom) {
                            Txt(s.value, v, if (s.warn) l.warnC else e.ink.ink, weight = 600, num = true, maxW = colW - (if (s.unit != null) v * 0.9f else 0.dp))
                            if (s.unit != null) { Spacer(Modifier.width(2.dp)); Txt(s.unit, v * 0.72f, e.ink.ink2, weight = 500, num = true) }
                        }
                    }
                }
            }
        }
    }
}

/** 소리 / 진동 / 무음: the current one on an ink chip. 무음 without 방해 금지 access wears a lock and opens that page. */
@Composable
private fun Modes(l: BatteryLook, p: PhoneControls, actions: StandbyActions, iconOnly: Boolean) {
    val e = l.e
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(l.plate2).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Ringer.entries.forEach { r ->
            val on = p.ringer == r
            val locked = r == Ringer.SILENT && !on && !p.dndAccess
            val fg = if (on) e.ink.on(e.ink.ink) else e.ink.ink2
            Row(
                Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(11.dp)).background(if (on) e.ink.ink else Color.Transparent)
                    .semantics { contentDescription = if (locked) "${r.ko} · 방해 금지 권한 필요" else r.ko; role = Role.Button; selected = on }
                    .tap { if (locked) actions.openDndAccess() else actions.setRinger(r) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                IconView(ringerIc(r), fg, Modifier.size(16.dp))
                if (!iconOnly) { Spacer(Modifier.width(5.dp)); Txt(r.ko, 14.dp, fg, weight = 600) }
                if (locked) { Spacer(Modifier.width(3.dp)); IconView(Ic.LOCK, fg, Modifier.size(11.dp)) }
            }
        }
    }
}

/** 방해 금지 row with a switch; without access a tap opens the access page. */
@Composable
private fun DndSwitch(l: BatteryLook, p: PhoneControls, actions: StandbyActions) {
    val e = l.e
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(l.plate2)
            .semantics { contentDescription = "방해 금지"; role = Role.Switch; stateDescription = if (p.dnd) "켜짐" else "꺼짐" }
            .tap { if (p.dndAccess) actions.setDnd(!p.dnd) else actions.openDndAccess() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconView(if (p.dndAccess) Ic.MOON else Ic.LOCK, e.ink.ink2, Modifier.size(16.dp), stroke = true)
        Spacer(Modifier.width(6.dp))
        Txt("방해 금지", 13.dp, e.ink.ink, Modifier.weight(1f), weight = 600)
        Box(Modifier.size(34.dp, 20.dp).clip(CircleShape).background(if (p.dnd) e.ink.acc else e.ink.ink3).padding(2.dp), contentAlignment = if (p.dnd) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(if (p.dnd) e.ink.on(e.ink.acc) else e.ink.ink))
        }
    }
}

/** Brightness pill: this window only, through the screen's own bar rules (day value, or the night cap at night). */
@Composable
private fun BrightPill(l: BatteryLook, state: StandbyState, config: AppConfig, actions: StandbyActions, vertical: Boolean, modifier: Modifier) {
    val stored = Brightness.shown(state.night >= 0.5f, config)
    var held by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(stored) { held = null } // the saved value came back
    val v = held ?: stored
    DragPill(l, Ic.SUN, "밝기", v, vertical, null, { held = it; actions.setBrightness(it) }, modifier)
}

/** Ring volume pill; off (dimmed, "진동 중" / "무음 중") unless the sound mode is 소리. */
@Composable
private fun VolumePill(l: BatteryLook, p: PhoneControls, actions: StandbyActions, vertical: Boolean, modifier: Modifier) {
    var held by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(p.volume) { held = null }
    val off = if (p.ringer == Ringer.SOUND) null else "${p.ringer.ko} 중"
    DragPill(l, ringerIc(p.ringer), "볼륨", held ?: p.volume, vertical, off, { held = it; actions.setVolume(it) }, modifier)
}

/**
 * A slider that fills like the card (from the bottom / the left). A press sets the value where it lands and the drag
 * follows; the down is consumed so the page swipe and the board's long press leave it alone. [off] = disabled text.
 */
@Composable
private fun DragPill(l: BatteryLook, ic: Ic, name: String, value: Float, vertical: Boolean, off: String?, onChange: (Float) -> Unit, modifier: Modifier) {
    val e = l.e
    val cb by rememberUpdatedState(onChange)
    val pct = (value.coerceIn(0f, 1f) * 100).roundToInt()
    val input = if (off != null) Modifier.alpha(0.45f) else Modifier.pointerInput(vertical) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            fun at(p: Offset) = (if (vertical) 1f - p.y / size.height else p.x / size.width).coerceIn(0f, 1f)
            cb(at(down.position))
            while (true) {
                val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                if (!ch.pressed) break
                cb(at(ch.position)); ch.consume()
            }
        }
    }
    val shown = if (off != null) 0f else value.coerceIn(0f, 1f)
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(l.plate2).then(input)
            .semantics { contentDescription = name; stateDescription = off ?: "$pct%" }
            .drawBehind {
                if (vertical) drawRect(l.ctl, Offset(0f, size.height * (1f - shown)), Size(size.width, size.height * shown))
                else drawRect(l.ctl, Offset.Zero, Size(size.width * shown, size.height))
            },
    ) {
        if (vertical) Column(Modifier.fillMaxSize().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Txt(if (off != null) "—" else "$pct", 14.dp, e.ink.ink, weight = 600, num = true)
            IconView(ic, e.ink.ink, Modifier.size(20.dp))
        } else Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconView(ic, e.ink.ink, Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Txt(off ?: name, 15.dp, e.ink.ink, Modifier.weight(1f), weight = 600)
            if (off == null) Txt("$pct%", 15.dp, e.ink.ink, weight = 600, num = true)
        }
    }
}

/**
 * Charge curve since plug-in (wide layout): dotted 25/50/75/100 grid with labels on the right, the solid curve over a
 * soft area, a dotted projection to the full time when the phone knows it, the "지금" dot, and start / 지금 / full labels.
 */
@Composable
private fun Curve(l: BatteryLook, state: StandbyState, modifier: Modifier) {
    val e = l.e
    val b = l.b
    if (!b.kind.plugged) {
        Box(modifier, contentAlignment = Alignment.Center) { Txt("충전기를 꽂으면 곡선을 그려요", l.meta, e.readInk2, weight = 500) }
        return
    }
    val tm = rememberTextMeasurer()
    val nowMs = state.now.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val pts = b.curve.ifEmpty { listOf(nowMs to b.pct) }
    val t0 = pts.first().first
    val tNow = maxOf(nowMs, pts.last().first)
    val left = if (b.kind.filling) b.leftMs else null
    val end = tNow + (left ?: 0L)
    val col = l.fillC
    val ink = e.ink
    val words = e.t.cardWords(500)
    val nums = e.num
    Canvas(modifier) {
        val labW = 30.dp.toPx()
        val gh = size.height - 22.dp.toPx()
        val pw = size.width - labW - 4.dp.toPx()
        val span = (end - t0).coerceAtLeast(1L).toFloat()
        fun x(t: Long) = if (end == t0) 0f else (t - t0) / span * pw
        fun y(p: Int) = gh - p / 100f * gh
        val dot = PathEffect.dashPathEffect(floatArrayOf(1f, 5.dp.toPx()))
        val small = TextStyle(fontFamily = nums, fontSize = (11f / fontScale).sp, color = ink.ink2, fontWeight = FontWeight.Medium)
        for (v in listOf(25, 50, 75, 100)) {
            drawLine(ink.ink3, Offset(0f, y(v)), Offset(pw, y(v)), 1.dp.toPx(), StrokeCap.Round, dot)
            val t = tm.measure(v.toString(), small)
            drawText(t, topLeft = Offset(size.width - t.size.width, y(v) - t.size.height / 2f))
        }
        val line = Path()
        pts.forEachIndexed { i, (t, p) -> if (i == 0) line.moveTo(x(t), y(p)) else line.lineTo(x(t), y(p)) }
        val nowX = x(tNow)
        val nowY = y(b.pct)
        if (pts.last().first < tNow) line.lineTo(nowX, nowY)
        val area = Path().apply { addPath(line); lineTo(nowX, gh); lineTo(x(t0), gh); close() }
        drawPath(area, Brush.verticalGradient(0f to col.copy(alpha = 0.32f), 1f to col.copy(alpha = 0f), startY = 0f, endY = gh))
        drawPath(line, col, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (left != null) {
            val fut = Path().apply {
                moveTo(nowX, nowY)
                for (i in 1..14) { val k = i / 14f; lineTo(nowX + k * (pw - nowX), y((b.pct + (100 - b.pct) * (1 - (1 - k).pow(2.2f))).roundToInt())) }
            }
            drawPath(fut, col, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, pathEffect = dot))
        }
        drawCircle(col.copy(alpha = 0.25f), 9.dp.toPx(), Offset(nowX, nowY))
        drawCircle(col, 4.5.dp.toPx(), Offset(nowX, nowY))
        val base = small.copy(fontSize = small.fontSize * (12f / 11f))
        val by = size.height
        if (nowX > 64.dp.toPx()) tm.measure(hhmm(t0 / 1000), base).let { drawText(it, topLeft = Offset(0f, by - it.size.height)) }
        tm.measure("지금", base.copy(fontFamily = words, color = ink.ink, fontWeight = FontWeight.SemiBold)).let {
            drawText(it, topLeft = Offset((nowX - it.size.width / 2f).coerceIn(0f, size.width - it.size.width), by - it.size.height))
        }
        if (left != null) tm.measure(BatteryRules.fullAt(state.now, left), base).let { drawText(it, topLeft = Offset(pw - it.size.width, by - it.size.height)) }
    }
}
