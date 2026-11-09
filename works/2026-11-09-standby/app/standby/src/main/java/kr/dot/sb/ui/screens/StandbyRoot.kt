package kr.dot.sb.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.dot.sb.model.AppConfig
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState

/** OWNER: screens agent. Whole StandBy surface: pages (widgets, photos, clocks, boards), overlays, night, off state. */
@Composable
fun StandbyRoot(state: StandbyState, config: AppConfig, actions: StandbyActions, modifier: Modifier = Modifier) {
    Box(modifier)
}
