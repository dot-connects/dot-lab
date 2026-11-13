package kr.dot.sb.ui.clock

import androidx.compose.ui.graphics.Color
import kr.dot.sb.model.Background
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.theme.Contrast
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 색 조합 바꾸기 (plan-r10 Task 12): every shuffled combo keeps digits/hands readable (WCAG >= 3.0) and changes something. */
class ColorShuffleTest {
    private val black = 0xFF000000

    /** Independent oracle: what each face draws its digits/hands on, read from the applied style. null = not checked. */
    private fun pairs(face: FaceId, s: ClockStyle): Pair<List<Long>, List<Long>>? {
        val pageBg = when (s.background) {
            Background.BLACK -> listOf(black)
            Background.COLOR -> listOf(s.bgColor)
            Background.GRADIENT -> listOf(s.bgColor, s.bgColor2)
        }
        val text = listOf(s.color) + if (s.effect == Effect.TWO_COLOR) listOf(s.color2) else emptyList()
        return when (face) {
            FaceId.SOLAR, FaceId.PHOTOS_TIME -> null
            FaceId.FLIP -> listOf(s.digitColor!!) to listOf(s.plateColor!!)
            FaceId.LCD -> listOf(s.inkColor!!) to listOf(s.panelColor!!)
            FaceId.DOT_MATRIX -> listOf(s.dotColor!!) to listOf(black)
            FaceId.WALL -> listOf(s.handColor, s.secondColor, s.numeralColor, s.tickColor) to listOf(s.faceColor ?: FaceDefaults.FACE)
            FaceId.ANALOG -> listOf(s.handColor, s.secondColor, s.weekdayColor, s.numeralColor, s.tickColor) to pageBg
            FaceId.SPLIT -> listOf(s.hourColor!!, s.minuteColor!!) to pageBg
            FaceId.HANGUL -> (text + s.accentColor!!) to pageBg
            else -> text to pageBg
        }
    }

    private fun starts(face: FaceId): List<ClockStyle> {
        val d = ClockStyle.default(face)
        return listOf(
            d,
            d.copy(background = Background.COLOR, bgColor = 0xFFF4EFE6),
            d.copy(background = Background.GRADIENT, effect = Effect.TWO_COLOR),
            d.copy(faceColor = 0xFF1E1E20, color = 0xFFFFFFFF, handColor = 0xFFFF453A),
        )
    }

    @Test
    fun everyComboIsReadableAndNew() {
        val faces = FaceId.entries.filter { ColorShuffle.supports(it) }
        assertTrue(faces.size >= 15)
        for (face in faces) for (start in starts(face)) for (seed in 0 until 1000) {
            val change = ColorShuffle.next(face, start, seed)
            val out = ColorShuffle.apply(face, start, change)
            assertNotEquals("$face seed $seed changed nothing", ColorShuffle.apply(face, start, ColorShuffle.current(face, start)), out)
            val (fg, bg) = pairs(face, out) ?: continue
            for (f in fg) for (b in bg) {
                val r = Contrast.ratio(Color(f), Color(b))
                assertTrue("$face seed $seed: #%08X on #%08X = %.2f".format(f, b, r), r >= 3f)
            }
        }
    }

    @Test
    fun undoRestoresTheCombo() {
        val start = ClockStyle.default(FaceId.WALL).copy(handColor = 0xFF123456)
        val before = ColorShuffle.current(FaceId.WALL, start)
        val shuffled = ColorShuffle.apply(FaceId.WALL, start, ColorShuffle.next(FaceId.WALL, start, 7))
        assertTrue(shuffled != start)
        assertTrue(ColorShuffle.apply(FaceId.WALL, shuffled, before) == start)
    }

    @Test
    fun presetFacesHaveNoShuffle() {
        listOf(FaceId.LED, FaceId.COLOR_BLOCK, FaceId.STICKER, FaceId.BALLS).forEach { assertTrue(!ColorShuffle.supports(it)) }
    }
}
