package kr.dot.sb.ui.cards

import androidx.compose.runtime.Composable
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState

/** Public build: the usage card does not exist (pickers hide it); a stored board that still names it draws an empty plate. */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun UsageCard(e: Env, state: StandbyState, actions: StandbyActions) = Unit
