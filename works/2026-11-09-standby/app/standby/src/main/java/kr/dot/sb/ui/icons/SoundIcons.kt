package kr.dot.sb.ui.icons

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import kr.dot.sb.model.SleepSound

/** One glyph part on the 24 grid: a filled path ([stroke] null) or a round-capped stroke [stroke] units wide. */
private class Part(d: String, val stroke: Float? = null) {
    val path: Path = PathParser().parsePathString(d).toPath().apply { fillType = PathFillType.EvenOdd }
}

/** Sleep-sound glyphs in the SF Symbols manner (mockup "수면 소리" v3), filled or 2-unit strokes. */
private val GLYPHS: Map<SleepSound, List<Part>> = mapOf(
    SleepSound.RAIN to listOf(
        Part("M7.2 13.2h9.6a3.9 3.9 0 0 0 .6-7.75A5.4 5.4 0 0 0 7.1 6.3a3.45 3.45 0 0 0 .1 6.9z"),
        Part("M8.6 16l-1.2 2.6M12.6 16l-1.2 2.6M16.6 16l-1.2 2.6M10.4 20.2l-.6 1.3M14.4 20.2l-.6 1.3", 2.1f),
    ),
    SleepSound.WAVES to listOf(
        Part("M2.5 8.2q2.4-2.6 4.75 0t4.75 0 4.75 0 4.75 0M2.5 13.2q2.4-2.6 4.75 0t4.75 0 4.75 0 4.75 0M2.5 18.2q2.4-2.6 4.75 0t4.75 0 4.75 0 4.75 0", 2.2f),
    ),
    SleepSound.FIRE to listOf(
        Part("M12.3 2.3c.4 2.8-1 4.6-2.6 6.3-1.7 1.8-3.4 3.6-3.4 6.6a5.7 5.7 0 0 0 11.4.3c.1-2.2-.8-3.9-1.8-5.1.1 1.4-.4 2.6-1.4 3.1.4-3.9-.6-8.2-2.2-11.2zm-.3 12.1c.9 1.2 1.9 2.2 1.9 3.6a1.9 1.9 0 0 1-3.8 0c0-1.3.8-2.4 1.9-3.6z"),
    ),
    SleepSound.WHITE to listOf(Part("M3 11v2M6 8v8M9 5v14M12 9v6M15 6v12M18 9v6M21 11v2", 2.1f)),
)

@Composable
fun SoundIcon(sound: SleepSound, color: Color, modifier: Modifier) {
    val parts = GLYPHS.getValue(sound)
    Canvas(modifier) {
        scale(size.minDimension / 24f, pivot = Offset.Zero) {
            for (p in parts) {
                if (p.stroke == null) drawPath(p.path, color)
                else drawPath(p.path, color, style = Stroke(p.stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
