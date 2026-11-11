package kr.dot.sb.ui.cards

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.model.WidgetMode
import kr.dot.sb.platform.WidgetHost
import kr.dot.sb.platform.WidgetView
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.theme.ThemeTokens

/** True only under the live StandBy host (activity / dream); previews and shots draw the placeholder (r9 D6). */
val LocalLiveWidgets = staticCompositionLocalOf { false }

/**
 * Phone widget card (spec §1): the hosted AppWidget inset in the slot and clipped round, at the slot's opacity. Night
 * (D13): a light widget is inverted into candle (white → black, dark text → candle ink), a dark one candle-toned.
 * Long press opens its options in settings ([menu] = the settings route).
 */
@Composable
internal fun PhoneWidget(e: Env, o: SlotOptions?, actions: StandbyActions, menu: String?) {
    val opt = o ?: SlotOptions()
    val night = e.ink.night >= 0.5f
    if (night && opt.hideAtNight) return
    val inset = (min(e.w, e.h) * 0.04f).coerceIn(6.dp, 14.dp)
    val box = Modifier.fillMaxSize().padding(inset).alpha(opt.opacity).clip(RoundedCornerShape(opt.radiusDp.dp))
        .then(if (menu != null) Modifier.widgetLongPress { actions.openSettings(menu) } else Modifier)
    val id = opt.widgetId
    val live = LocalLiveWidgets.current
    val dark = when (opt.widgetMode) { WidgetMode.THEME -> !e.t.tokens.light; WidgetMode.DARK -> true; WidgetMode.LIGHT -> false }
    val ctx = LocalContext.current
    val v = remember(id, dark, live) { if (id != null && live) WidgetHost.view(ctx, id, dark) else null }
    // the long press sits on a parent so the widget sees every event first
    Box(box) {
        if (v != null) Hosted(v, night, e.w.value - 2 * inset.value, e.h.value - 2 * inset.value)
        else Box(Modifier.fillMaxSize().background(e.ink.plate2), Alignment.Center) {
            Txt(if (id == null) "길게 눌러 위젯 고르기" else "폰 위젯", e.meta, e.ink.ink2, Modifier.padding(12.dp), weight = 500, lines = 2, align = TextAlign.Center)
        }
    }
}

@Composable
private fun Hosted(v: WidgetView, night: Boolean, w: Float, h: Float) {
    val light by v.light
    LaunchedEffect(v, w, h) { v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h))) }
    val paint = if (!night) null else Paint().apply { colorFilter = ColorMatrixColorFilter(if (light) LIGHT_AT_NIGHT else DARK_AT_NIGHT) }
    AndroidView(
        factory = { (v.parent as? ViewGroup)?.removeView(v); v },
        modifier = Modifier.fillMaxSize(),
        update = { it.setLayerType(if (paint == null) View.LAYER_TYPE_NONE else View.LAYER_TYPE_HARDWARE, paint) },
    )
}

/**
 * Long press that wins over the board's own (which opens the board editor): the down is consumed after the widget
 * saw it, and once the press fires the rest of the gesture is eaten before the widget, so it gets a cancel, not a tap.
 */
private fun Modifier.widgetLongPress(onLong: () -> Unit) = pointerInput(onLong) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val c = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            }
        }
        if (ended != null) return@awaitEachGesture
        onLong()
        do {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            ev.changes.forEach { it.consume() }
        } while (ev.changes.any { it.pressed })
    }
}

/** Candle ink as a per-channel scale: white maps onto #B9A48C. */
private fun candle() = ThemeTokens.NIGHT_INK.let { c -> ColorMatrix().apply { setScale(c.red, c.green, c.blue, 1f) } }

/** D13 light widget: invert, hue-rotate 180° (colours keep their hue), then the candle scale (warm, ~.7 bright). */
private val LIGHT_AT_NIGHT = ColorMatrix(floatArrayOf(
    -1f, 0f, 0f, 0f, 255f,
    0f, -1f, 0f, 0f, 255f,
    0f, 0f, -1f, 0f, 255f,
    0f, 0f, 0f, 1f, 0f,
)).apply {
    postConcat(ColorMatrix(floatArrayOf(
        -0.574f, 1.43f, 0.144f, 0f, 0f,
        0.426f, 0.43f, 0.144f, 0f, 0f,
        0.426f, 1.43f, -0.856f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
    postConcat(candle())
}

/** D13 dark widget: grayscale, then the candle scale (sepia, dim). */
private val DARK_AT_NIGHT = ColorMatrix().apply { setSaturation(0f); postConcat(candle()) }
