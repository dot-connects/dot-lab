package kr.dot.sb.ui.clock

import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.clock.StyleRow.ACCENT_COLOR
import kr.dot.sb.ui.clock.StyleRow.ANALOG_NUMBERS
import kr.dot.sb.ui.clock.StyleRow.ANALOG_TICKS
import kr.dot.sb.ui.clock.StyleRow.BAR_COLOR
import kr.dot.sb.ui.clock.StyleRow.CALENDAR_COLOR
import kr.dot.sb.ui.clock.StyleRow.CITY_COLOR
import kr.dot.sb.ui.clock.StyleRow.DAY_COLOR
import kr.dot.sb.ui.clock.StyleRow.DIAL_FONT
import kr.dot.sb.ui.clock.StyleRow.DIGIT_COLOR
import kr.dot.sb.ui.clock.StyleRow.DOT_COLOR
import kr.dot.sb.ui.clock.StyleRow.FACE_COLOR
import kr.dot.sb.ui.clock.StyleRow.FONT
import kr.dot.sb.ui.clock.StyleRow.HAND_COLOR
import kr.dot.sb.ui.clock.StyleRow.HOUR_COLOR
import kr.dot.sb.ui.clock.StyleRow.INFO
import kr.dot.sb.ui.clock.StyleRow.INK_COLOR
import kr.dot.sb.ui.clock.StyleRow.KFONT
import kr.dot.sb.ui.clock.StyleRow.LAYOUT
import kr.dot.sb.ui.clock.StyleRow.LIT_COLOR
import kr.dot.sb.ui.clock.StyleRow.MINUTE_COLOR
import kr.dot.sb.ui.clock.StyleRow.NUMERAL_COLOR
import kr.dot.sb.ui.clock.StyleRow.PAIR
import kr.dot.sb.ui.clock.StyleRow.PANEL_COLOR
import kr.dot.sb.ui.clock.StyleRow.PAPER_COLOR
import kr.dot.sb.ui.clock.StyleRow.PLATE_COLOR
import kr.dot.sb.ui.clock.StyleRow.RIM_COLOR
import kr.dot.sb.ui.clock.StyleRow.SECOND_COLOR
import kr.dot.sb.ui.clock.StyleRow.SHAPE
import kr.dot.sb.ui.clock.StyleRow.SIZE
import kr.dot.sb.ui.clock.StyleRow.TEXT_COLOR
import kr.dot.sb.ui.clock.StyleRow.TICK_COLOR
import kr.dot.sb.ui.clock.StyleRow.TRIO
import kr.dot.sb.ui.clock.StyleRow.WEIGHT

/** Gallery families (consensus); declaration order = gallery order. */
enum class Family(val ko: String) { SIMPLE("심플"), POP("팝"), RETRO("레트로"), CLASSIC("클래식"), GAUGE("계기") }

/**
 * One 꾸미기 row (mockup `STYLE` keys 1:1). [part] = a colour row's label (mockup `PART`); null for the other rows.
 * Preset rows ([LED], [PAIR], [TRIO], [SOLAR]) pick from a fixed list instead of a free colour.
 */
enum class StyleRow(val part: String? = null) {
    TEXT_COLOR("글자 색"), HOUR_COLOR("시 색"), MINUTE_COLOR("분 색"), CITY_COLOR("도시 색"), LIT_COLOR("밝은 점 색"),
    PLATE_COLOR("판 색"), DIGIT_COLOR("숫자 색"), PANEL_COLOR("판 색"), INK_COLOR("글자 색"), DOT_COLOR("점 색"),
    HAND_COLOR("바늘 색"), SECOND_COLOR("초침 색"), FACE_COLOR("판 색"), TICK_COLOR("눈금 색"), NUMERAL_COLOR("숫자 색"),
    DAY_COLOR("요일 색"), CALENDAR_COLOR("달력 색"), ACCENT_COLOR("강조 색"), BAR_COLOR("막대 색"), RIM_COLOR("점 색"),
    PAPER_COLOR("바탕 색"),
    LED, PAIR, TRIO, SOLAR,
    SIZE, FONT, KFONT, INFO, WEIGHT, FX, BG, LAYOUT, SHAPE, ANALOG_NUMBERS, ANALOG_TICKS, DIAL_FONT;

    val preset: Boolean get() = this == LED || this == PAIR || this == TRIO || this == SOLAR

    /** The colour this row edits, null fields resolved through [FaceDefaults]; null for the other rows (presets too). */
    fun color(s: ClockStyle): Long? = when (this) {
        TEXT_COLOR, CITY_COLOR -> s.color
        LIT_COLOR -> s.litDotColor
        HAND_COLOR -> s.handColor
        SECOND_COLOR -> s.secondColor
        TICK_COLOR -> s.tickColor
        NUMERAL_COLOR -> s.numeralColor
        DAY_COLOR -> s.weekdayColor
        HOUR_COLOR -> s.hourColor ?: FaceDefaults.HOUR
        MINUTE_COLOR -> s.minuteColor ?: FaceDefaults.MINUTE
        PLATE_COLOR -> s.plateColor ?: FaceDefaults.PLATE
        DIGIT_COLOR -> s.digitColor ?: FaceDefaults.DIGIT
        PANEL_COLOR -> s.panelColor ?: FaceDefaults.PANEL
        INK_COLOR -> s.inkColor ?: FaceDefaults.INK
        DOT_COLOR -> s.dotColor ?: FaceDefaults.DOT
        FACE_COLOR -> s.faceColor ?: FaceDefaults.FACE
        CALENDAR_COLOR -> s.calendarColor ?: FaceDefaults.CALENDAR
        ACCENT_COLOR -> s.accentColor ?: FaceDefaults.ACCENT
        BAR_COLOR -> s.barColor ?: FaceDefaults.BAR
        RIM_COLOR -> s.rimColor ?: FaceDefaults.RIM
        PAPER_COLOR -> s.paperColor ?: FaceDefaults.PAPER
        else -> null
    }

    /** [s] with this colour row set to [c]; unchanged for the other rows. */
    fun withColor(s: ClockStyle, c: Long): ClockStyle = when (this) {
        TEXT_COLOR, CITY_COLOR -> s.copy(color = c)
        LIT_COLOR -> s.copy(litDotColor = c)
        HAND_COLOR -> s.copy(handColor = c)
        SECOND_COLOR -> s.copy(secondColor = c)
        TICK_COLOR -> s.copy(tickColor = c)
        NUMERAL_COLOR -> s.copy(numeralColor = c)
        DAY_COLOR -> s.copy(weekdayColor = c)
        HOUR_COLOR -> s.copy(hourColor = c)
        MINUTE_COLOR -> s.copy(minuteColor = c)
        PLATE_COLOR -> s.copy(plateColor = c)
        DIGIT_COLOR -> s.copy(digitColor = c)
        PANEL_COLOR -> s.copy(panelColor = c)
        INK_COLOR -> s.copy(inkColor = c)
        DOT_COLOR -> s.copy(dotColor = c)
        FACE_COLOR -> s.copy(faceColor = c)
        CALENDAR_COLOR -> s.copy(calendarColor = c)
        ACCENT_COLOR -> s.copy(accentColor = c)
        BAR_COLOR -> s.copy(barColor = c)
        RIM_COLOR -> s.copy(rimColor = c)
        PAPER_COLOR -> s.copy(paperColor = c)
        else -> s
    }
}

/**
 * A clock of the gallery. [rows] = 꾸미기 rows, [more] = its 자세히 rows (mockup `STYLE`); [ours] = the "우리만" badge;
 * [built] = its renderer exists (an unbuilt face is skipped by the clocks page and the board chips).
 */
data class ClockSpec(
    val id: FaceId, val family: Family, val ko: String, val ours: Boolean,
    val rows: List<StyleRow>, val more: List<StyleRow>, val built: Boolean,
) {
    /** What the clocks-page long-press sheet edits: the first colour or preset row (plan-r10 Decisions). */
    val sheetRow: StyleRow? get() = rows.firstOrNull { it.part != null || it.preset }
}

/** Rows only typeset-digit faces can use (mockup `DT`). */
private val DT = listOf(WEIGHT, StyleRow.FX, StyleRow.BG)

/** The registry: one exhaustive `when`, so a new [FaceId] does not compile without its spec. */
fun spec(id: FaceId): ClockSpec {
    fun c(f: Family, ko: String, rows: List<StyleRow>, more: List<StyleRow>, ours: Boolean = false, built: Boolean = false) =
        ClockSpec(id, f, ko, ours, rows, more, built)
    return when (id) {
        FaceId.DIGITAL -> c(Family.SIMPLE, "디지털", listOf(TEXT_COLOR, SIZE, FONT, INFO), DT + LAYOUT, built = true)
        FaceId.THIN -> c(Family.SIMPLE, "가는 선", listOf(TEXT_COLOR, SIZE, FONT, INFO), DT, built = true)
        FaceId.SPLIT -> c(Family.SIMPLE, "시·분 나눔", listOf(HOUR_COLOR, MINUTE_COLOR, SIZE, FONT, INFO), DT, built = true)
        FaceId.DESK -> c(Family.SIMPLE, "책상", listOf(TEXT_COLOR, SIZE, FONT), DT, built = true)
        FaceId.COLOR_BLOCK -> c(Family.POP, "색 블록", listOf(PAIR, SIZE, FONT, INFO), emptyList(), built = true)
        FaceId.STICKER -> c(Family.POP, "스티커", listOf(TRIO, SIZE, FONT, INFO), listOf(PAPER_COLOR), ours = true, built = true)
        FaceId.BALLS -> c(Family.POP, "공 숫자", listOf(TRIO, SIZE, FONT, INFO), listOf(DIGIT_COLOR), ours = true, built = true)
        FaceId.SOLAR -> c(Family.POP, "솔라", listOf(StyleRow.SOLAR, TEXT_COLOR, SIZE, FONT, INFO), listOf(WEIGHT, StyleRow.FX, LAYOUT), built = true)
        FaceId.FLOAT -> c(Family.POP, "둥근 굵은 글씨", listOf(TEXT_COLOR, SIZE, FONT, INFO), DT + LAYOUT, built = true)
        FaceId.FLIP -> c(Family.RETRO, "플립 시계", listOf(PLATE_COLOR, DIGIT_COLOR, SIZE, FONT, INFO), emptyList(), built = true)
        FaceId.LED -> c(Family.RETRO, "빨간 LED", listOf(StyleRow.LED, SIZE), emptyList(), built = true)
        FaceId.LCD -> c(Family.RETRO, "액정 시계", listOf(PANEL_COLOR, INK_COLOR, SIZE), emptyList(), built = true)
        FaceId.DOT_MATRIX -> c(Family.RETRO, "전광판", listOf(DOT_COLOR, SIZE), emptyList(), built = true)
        FaceId.WALL -> c(
            Family.CLASSIC, "벽시계", listOf(HAND_COLOR, SECOND_COLOR, ANALOG_NUMBERS, INFO),
            listOf(FACE_COLOR, TICK_COLOR, NUMERAL_COLOR, ANALOG_TICKS, DIAL_FONT, SIZE),
        )
        FaceId.CALENDAR -> c(Family.CLASSIC, "달력과 시계", listOf(TEXT_COLOR, CALENDAR_COLOR, SIZE, FONT), listOf(WEIGHT), ours = true)
        FaceId.HANGUL -> c(Family.CLASSIC, "한글 시계", listOf(TEXT_COLOR, ACCENT_COLOR, SIZE, KFONT), emptyList(), ours = true)
        FaceId.ANALOG -> c(
            Family.CLASSIC, "아날로그", listOf(SHAPE, HAND_COLOR, SECOND_COLOR, ANALOG_NUMBERS),
            listOf(FACE_COLOR, TICK_COLOR, NUMERAL_COLOR, DAY_COLOR, ANALOG_TICKS, DIAL_FONT, SIZE), built = true,
        )
        FaceId.PHOTOS_TIME -> c(Family.CLASSIC, "사진 위 시간", listOf(TEXT_COLOR, SIZE, FONT), listOf(WEIGHT, StyleRow.FX), built = true)
        FaceId.DAY_BAR -> c(Family.GAUGE, "하루 막대", listOf(TEXT_COLOR, BAR_COLOR, SIZE, FONT), listOf(WEIGHT), built = true)
        FaceId.RIM -> c(Family.GAUGE, "테두리 초", listOf(TEXT_COLOR, RIM_COLOR, SIZE, FONT, INFO), listOf(WEIGHT), ours = true, built = true)
        FaceId.WORLD -> c(Family.GAUGE, "세계", listOf(CITY_COLOR, LIT_COLOR, SIZE, FONT), DT, built = true)
    }
}

object ClockRegistry {
    /** Every face in gallery order (by family, then declaration order). */
    val all: List<FaceId> = FaceId.entries.sortedBy { spec(it).family }

    /** The clocks page: the built faces of [myClocks] in order; none built = digital only. */
    fun pages(myClocks: List<FaceId>): List<FaceId> = myClocks.filter { spec(it).built }.ifEmpty { listOf(FaceId.DIGITAL) }

    /** Board editor clock chips: built faces that draw in a slot (사진 위 시간 places itself on a photo screen). */
    val boardFaces: List<FaceId> get() = all.filter { spec(it).built && it != FaceId.PHOTOS_TIME }
}
