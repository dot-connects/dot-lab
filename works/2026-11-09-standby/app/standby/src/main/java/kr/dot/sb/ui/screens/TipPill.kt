package kr.dot.sb.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kr.dot.sb.model.AppConfig
import kr.dot.sb.settings.GestureGlyph
import kr.dot.sb.settings.Gesture
import kr.dot.sb.settings.Tip
import kr.dot.sb.settings.TipContext
import kr.dot.sb.settings.Tips
import kr.dot.sb.ui.theme.Fonts

/**
 * plan-r15 F10 ①: one tip per Stand Screen session, at the screen's edge for 4 s, never again (stored through
 * [onSeen]). Picked 1.5 s after the page settles ([Tips.pick]); [busy] (an overlay, edit mode, the mood light) waits.
 * The pull-down nudge sits at the top under the bar's handle, the rest at the bottom.
 */
@Composable
internal fun BoxScope.TipOverlay(config: AppConfig, at: TipContext?, busy: Boolean, onSeen: (String) -> Unit) {
    var shown by remember { mutableStateOf<Tip?>(null) }
    var done by remember { mutableStateOf(false) } // one per session
    val cfg by rememberUpdatedState(config)
    val seen by rememberUpdatedState(onSeen)
    LaunchedEffect(at, busy, done) {
        if (done || busy || at == null) return@LaunchedEffect
        delay(1_500)
        val t = Tips.pick(cfg, at) ?: return@LaunchedEffect
        done = true
        shown = t
        seen(t.name)
    }
    LaunchedEffect(shown) { if (shown != null) { delay(4_000); shown = null } }
    val t = shown
    AnimatedVisibility(t != null && !busy, Modifier.align(if (t?.gesture == Gesture.PULL_DOWN) Alignment.TopCenter else Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
        if (t != null) Row(
            Modifier.padding(vertical = 18.dp).clip(RoundedCornerShape(50)).background(Color(0xE6202024)).padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), // literal-ok: neutral pill over any theme
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.padding(2.dp)) { GestureGlyph(t.gesture, Color.White, size = 28.dp) }
            Text(t.pill.orEmpty(), color = Color.White, fontSize = 15.sp, fontFamily = Fonts.ui(600), maxLines = 1)
        }
    }
}
