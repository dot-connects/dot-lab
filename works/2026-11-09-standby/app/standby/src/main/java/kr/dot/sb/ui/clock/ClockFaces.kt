package kr.dot.sb.ui.clock

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.state.StandbyState

/** OWNER: clock agent. Full-screen face, or the same face fitted into a board slot when [inSlot]. */
@Composable
fun ClockFace(face: FaceId, style: ClockStyle, state: StandbyState, modifier: Modifier = Modifier, inSlot: Boolean = false) {
    Box(modifier)
}
