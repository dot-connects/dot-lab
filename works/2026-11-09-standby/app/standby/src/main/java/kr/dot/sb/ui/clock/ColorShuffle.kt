package kr.dot.sb.ui.clock

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import kr.dot.sb.model.Background
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.theme.Contrast
import kr.dot.sb.ui.theme.Hsl
import kotlin.random.Random

/**
 * 색 조합 바꾸기 (operator 2026-11-13, plan-r10 Task 12): a new colour combo for one face. Digits/hands ([Plan.fg]) are
 * drawn from `ClockStyle.SWATCHES` and every combo whose digits/hands fall under WCAG 3.0 against the background is
 * rejected (BLACK = black, GRADIENT = both ends; 솔라 and 사진 위 시간 draw on a picture and skip the check).
 * A background the face lets you colour ([Plan.bg]) changes too, as a deep (or, on a light dial, pale) tone of a swatch,
 * so the face keeps its dark or light character; digits on a light background take the deep tones.
 * Faces whose colours are presets (LED, 색 블록, 스티커, 공 숫자) have no shuffle.
 */
object ColorShuffle {
    /** A colour field of [ClockStyle]: the face's colour rows plus the second text colour and the background colours. */
    enum class Field(private val row: StyleRow?) {
        COLOR(StyleRow.TEXT_COLOR), COLOR2(null), BG(null), BG2(null),
        HOUR(StyleRow.HOUR_COLOR), MINUTE(StyleRow.MINUTE_COLOR), HAND(StyleRow.HAND_COLOR), SECOND(StyleRow.SECOND_COLOR),
        WEEKDAY(StyleRow.DAY_COLOR), NUMERAL(StyleRow.NUMERAL_COLOR), TICK(StyleRow.TICK_COLOR), LIT(StyleRow.LIT_COLOR),
        PLATE(StyleRow.PLATE_COLOR), DIGIT(StyleRow.DIGIT_COLOR), PANEL(StyleRow.PANEL_COLOR), INK(StyleRow.INK_COLOR),
        DOT(StyleRow.DOT_COLOR), CALENDAR(StyleRow.CALENDAR_COLOR), ACCENT(StyleRow.ACCENT_COLOR), BAR(StyleRow.BAR_COLOR),
        RIM(StyleRow.RIM_COLOR);

        fun get(s: ClockStyle, face: FaceId): Long = when (this) {
            COLOR2 -> s.color2
            BG -> s.bgColor
            BG2 -> s.bgColor2
            else -> row!!.color(s, face)!!
        }

        fun set(s: ClockStyle, c: Long): ClockStyle = when (this) {
            COLOR2 -> s.copy(color2 = c)
            BG -> s.copy(bgColor = c)
            BG2 -> s.copy(bgColor2 = c)
            else -> row!!.withColor(s, c)
        }
    }

    /** [fg] must read on the background: [bg] fields (shuffled) or, when there are none, [fixed]; [extra] = accents, unchecked. */
    private class Plan(val fg: List<Field>, val bg: List<Field>, val fixed: List<Long>, val extra: List<Field> = emptyList(), val check: Boolean = true) {
        val fields get() = fg + bg + extra
    }

    private const val BLACK = 0xFF000000

    private fun plan(face: FaceId, s: ClockStyle): Plan? {
        val text = listOf(Field.COLOR) + if (s.effect == Effect.TWO_COLOR) listOf(Field.COLOR2) else emptyList()
        val bg = when (s.background) {
            Background.BLACK -> emptyList()
            Background.COLOR -> listOf(Field.BG)
            Background.GRADIENT -> listOf(Field.BG, Field.BG2)
        }
        fun page(fg: List<Field>, extra: List<Field> = emptyList()) = Plan(fg, bg, listOf(BLACK), extra)
        return when (face) {
            FaceId.LED, FaceId.COLOR_BLOCK, FaceId.STICKER, FaceId.BALLS -> null
            FaceId.SOLAR, FaceId.PHOTOS_TIME -> Plan(text, emptyList(), emptyList(), check = false)
            FaceId.FLIP -> Plan(listOf(Field.DIGIT), listOf(Field.PLATE), emptyList())
            FaceId.LCD -> Plan(listOf(Field.INK), listOf(Field.PANEL), emptyList())
            FaceId.DOT_MATRIX -> Plan(listOf(Field.DOT), emptyList(), listOf(BLACK))
            FaceId.WALL -> Plan(listOf(Field.HAND, Field.SECOND, Field.NUMERAL, Field.TICK), emptyList(), listOf(s.faceColor ?: FaceDefaults.FACE))
            FaceId.ANALOG -> page(listOf(Field.HAND, Field.SECOND, Field.WEEKDAY, Field.NUMERAL, Field.TICK))
            FaceId.SPLIT -> page(listOf(Field.HOUR, Field.MINUTE))
            FaceId.HANGUL -> page(text + Field.ACCENT)
            FaceId.WORLD -> page(text, listOf(Field.LIT))
            FaceId.CALENDAR -> page(text, listOf(Field.CALENDAR))
            FaceId.DAY_BAR -> page(text, listOf(Field.BAR))
            FaceId.RIM -> page(text, listOf(Field.RIM))
            FaceId.DIGITAL, FaceId.THIN, FaceId.DESK, FaceId.FLOAT -> page(text)
        }
    }

    fun supports(face: FaceId): Boolean = plan(face, ClockStyle.default(face)) != null

    /** The colours [next] would replace, as they are now (what [이전 조합] restores). */
    fun current(face: FaceId, s: ClockStyle): Map<Field, Long> =
        plan(face, s)?.fields?.associateWith { it.get(s, face) } ?: emptyMap()

    fun apply(face: FaceId, s: ClockStyle, combo: Map<Field, Long>): ClockStyle =
        combo.entries.fold(s) { acc, (f, c) -> f.set(acc, c) }

    private fun tone(c: Long, l: Float): Long {
        val (h, sat, _) = Hsl.toHsl(Color(c))
        return Color.hsl(h, sat, l).toArgb().toLong() and 0xFFFFFFFFL
    }

    private fun light(c: Long) = Color(c).luminance() > 0.4f

    /** A new combo for [face] from [s]; empty when the face has none. Same [seed] = same combo. */
    fun next(face: FaceId, s: ClockStyle, seed: Int): Map<Field, Long> {
        val p = plan(face, s) ?: return emptyMap()
        val rnd = Random(seed)
        val lightBg = p.check && light(p.bg.firstOrNull()?.get(s, face) ?: p.fixed.first())
        val swatches = ClockStyle.SWATCHES
        val bgPool = swatches.map { tone(it, if (lightBg) 0.88f else 0.16f) }
        val fgPool = if (lightBg) swatches.map { tone(it, 0.3f) } else swatches
        val bgs = p.bg.associateWith { bgPool.random(rnd) }
        val behind = bgs.values.toList().ifEmpty { p.fixed }
        val ok = fgPool.filter { c -> !p.check || behind.all { Contrast.ratio(Color(c), Color(it)) >= 3f } }
        if (ok.isEmpty()) return emptyMap()
        // the first digit colour always moves, so a shuffle never shows the same combo again
        val first = p.fg.first()
        val pick = ok.filter { it != first.get(s, face) }.ifEmpty { ok }.random(rnd)
        val rest = (ok - pick).ifEmpty { ok }.shuffled(rnd)
        val fgs = p.fg.mapIndexed { i, f -> f to if (i == 0) pick else rest[(i - 1) % rest.size] }.toMap()
        return fgs + bgs + p.extra.associateWith { swatches.random(rnd) }
    }
}
