package kr.dot.sb.ui.board

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.ui.state.StandbyActions
import kr.dot.sb.ui.state.StandbyState

/** OWNER: board agent. One board: layout grid, slots (stacks), cards, shared label size, hero accent. */
@Composable
fun BoardView(board: Board, state: StandbyState, config: AppConfig, actions: StandbyActions, modifier: Modifier = Modifier) {
    Box(modifier)
}
