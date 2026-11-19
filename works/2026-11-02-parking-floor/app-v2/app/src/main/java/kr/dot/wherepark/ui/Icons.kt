package kr.dot.wherepark.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The mockup's IP line icons (24 grid, round caps), rect/circle turned into paths. "micw" = the filled big-mic glyph. */
object Icons {
    private fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r} ${cy}a$r $r 0 1 0 ${2 * r} 0a$r $r 0 1 0 ${-2 * r} 0"
    private const val MIC_BODY = "M12 3.5a3 3 0 0 1 3 3v4.5a3 3 0 0 1 -6 0v-4.5a3 3 0 0 1 3 -3z"

    private val P: Map<String, List<String>> = mapOf(
        "note" to listOf("M6 3.5h8.5L18 7v13.5H6z", "M14.5 3.5V7H18M9 11h6M9 14.5h6M9 18h3.5"),
        "gear" to listOf(circle(12f, 12f, 3.2f), "M12 3.5v2.3M12 18.2v2.3M3.5 12h2.3M18.2 12h2.3M6 6l1.6 1.6M16.4 16.4L18 18M6 18l1.6-1.6M16.4 7.6L18 6"),
        "pen" to listOf("M5 19l.8-3.6L15.6 5.6a1.9 1.9 0 0 1 2.8 2.8L8.6 18.2z", "M13.8 7.4l2.8 2.8"),
        "back" to listOf("M14.5 5.5L8 12l6.5 6.5"),
        "bt" to listOf("M7.5 8l9 8-4.5 3.8V4.2L16.5 8l-9 8"),
        "car" to listOf("M5 15.5l1.4-5.1A2 2 0 0 1 8.3 9h7.4a2 2 0 0 1 1.9 1.4l1.4 5.1", "M4 15.5h16v3H4z", "M6 18.5V20M18 18.5V20",
            circle(7.6f, 17f, .5f), circle(16.4f, 17f, .5f)),
        "copy" to listOf("M10 8h7.5a2 2 0 0 1 2 2v7.5a2 2 0 0 1 -2 2h-7.5a2 2 0 0 1 -2 -2v-7.5a2 2 0 0 1 2 -2z",
            "M15.5 8V5.5a1.5 1.5 0 0 0-1.5-1.5H5.5A1.5 1.5 0 0 0 4 5.5V14a1.5 1.5 0 0 0 1.5 1.5H8"),
        "chev" to listOf("M7 9.5l5 5 5-5"),
        "right" to listOf("M9.5 7l5 5-5 5"),
        "check" to listOf("M5 12.5l4.2 4.2L19 7"),
        "mic" to listOf(MIC_BODY, "M6 11a6 6 0 0 0 12 0M12 17v3.5"),
        "up" to listOf("M12 19V6M6.5 11.5L12 6l5.5 5.5"),
        "cam" to listOf("M4.5 8h3l1.5-2.5h6L16.5 8h3v10.5h-15z", circle(12f, 13f, 3.2f)),
        "plus" to listOf("M12 5v14M5 12h14"),
    )
    private val cache = HashMap<String, ImageVector>()

    fun get(name: String, stroke: Float = 1.7f): ImageVector = synchronized(cache) {
        cache.getOrPut("$name/$stroke") {
            val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
            if (name == "micw") {
                // mockup MICW: filled capsule x9 y3 w6 h11 + stroked stand, width 2
                b.addPath(addPathNodes("M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1 -6 0v-5a3 3 0 0 1 3 -3z"), fill = SolidColor(Color.Black))
                b.addPath(addPathNodes("M6 11a6 6 0 0 0 12 0M12 17v4"), stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round)
            } else P.getValue(name).forEach { d ->
                b.addPath(addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = stroke, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
            }
            b.build()
        }
    }
}

@Composable
fun Ic(name: String, size: Dp, tint: Color = Wp.FG, stroke: Float = 1.7f, desc: String? = null) =
    Icon(Icons.get(name, stroke), desc, Modifier.size(size), tint = tint)
