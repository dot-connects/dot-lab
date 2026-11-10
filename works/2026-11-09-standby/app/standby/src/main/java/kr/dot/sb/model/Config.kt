package kr.dot.sb.model

import java.time.LocalDate

/**
 * Everything the user configures. Plain data so the UI can be rendered from a fixed value in screenshot tests.
 * Persisted as JSON by [ConfigStore]. Colours are ARGB Longs (0xFFRRGGBB).
 */

/** The 11 iPhone clock fonts mapped to bundled open fonts (design-system.md §4.3, spec C10). */
enum class FontId(val ko: String, val ios: String, val defaultWeight: Int) {
    BOLD("굵게", "Bold", 800),
    LIGHT("가늘게", "Light", 250),
    ROUNDED("둥글게", "Rounded", 800),
    STENCIL("스텐실", "Stencil", 800),
    SERIF("명조", "Serif", 500),
    SERIF_HEAVY("굵은 명조", "Serif Heavy", 900),
    SLAB("슬랩", "Slab", 700),
    RAILS("레일", "Rails", 800),
    CONDENSED("좁게", "StandBy 디지털", 600),
    ROUNDED_HEAVY("둥글고 아주 굵게", "StandBy 플로트", 950),
    THIN("얇게", "StandBy 솔라", 350),
}

enum class Effect(val ko: String) { SOLID("단색"), TWO_COLOR("두 색"), RAINBOW("무지개"), GLASS("유리"), GLOW("빛 번짐") }
enum class Background(val ko: String) { BLACK("검정"), COLOR("단색"), GRADIENT("그라데이션") }
enum class Numerals(val ko: String, val digits: String) {
    LATN("서양 (0123)", "0123456789"),
    ARAB("아랍 (٠١٢٣)", "٠١٢٣٤٥٦٧٨٩"),
    DEVA("데바나가리 (०१२३)", "०१२३४५६७८९"),
    BENG("벵골 (০১২৩)", "০১২৩৪৫৬৭৮৯"),
    KHMR("크메르 (០១២៣)", "០១២៣៤៥៦៧៨៩"),
    MYMR("미얀마 (၀၁၂၃)", "၀၁၂၃၄၅၆၇၈၉");

    fun convert(s: String): String = if (this == LATN) s else s.map { c -> if (c in '0'..'9') digits[c - '0'] else c }.joinToString("")
}
/** The look of every StandBy surface (mockup r5): A = iPhone (default), B = newspaper front page, C = mood lamp. */
enum class Theme(val ko: String) { IPHONE("아이폰 그대로"), PAPER("신문 1면"), MOODLIGHT("무드등") }

enum class ClockLayout(val ko: String) { IPHONE("아이폰"), CENTER("가운데"), TWO_LINES("두 줄") }
enum class AnalogNumbers(val ko: String) { FOUR("12·3·6·9"), TWELVE("12개"), ROMAN("로마"), NONE("없음"), DUO("Duo") }
enum class AnalogTicks(val ko: String) { IPHONE("아이폰"), SHORT("짧게"), HOURS("정시만"), NONE("없음") }

/** Full-screen clock faces in vertical-swipe order (spec C1), plus the two other places a styled time appears. */
enum class FaceId(val ko: String) {
    DIGITAL("디지털"), ANALOG("아날로그"), WORLD("세계"), SOLAR("솔라"), FLOAT("플로트"), DESK("책상"), PHOTOS_TIME("사진 위 시간");

    companion object { val CLOCKS = listOf(DIGITAL, ANALOG, WORLD, SOLAR, FLOAT) }
}

data class ClockStyle(
    val font: FontId,
    val weight: Int,
    val sizePct: Int = 100,
    val widthPct: Int = 100,
    val color: Long,
    val color2: Long = 0xFFAA88FE,
    val effect: Effect = Effect.SOLID,
    val background: Background = Background.BLACK,
    val bgColor: Long = 0xFF101828,
    val bgColor2: Long = 0xFF2A1846,
    val numerals: Numerals = Numerals.LATN,
    val layout: ClockLayout = ClockLayout.IPHONE,
    val showInfo: Boolean = true,
    // analog only
    val analogNumbers: AnalogNumbers = AnalogNumbers.FOUR,
    val analogTicks: AnalogTicks = AnalogTicks.IPHONE,
    val numeralColor: Long = 0xFFD8D8DA,
    val handColor: Long = 0xFFC5C0C4,
    val secondColor: Long = 0xFFE9893A,
    val weekdayColor: Long = 0xFFD04450,
    val tickColor: Long = 0xFF98989F,
    // world only
    val litDotColor: Long = 0xFF98989F,
) {
    companion object {
        /** iPhone look per face (canon §2-3, design-system §2.1). */
        fun default(face: FaceId): ClockStyle = when (face) {
            FaceId.DIGITAL, FaceId.DESK -> ClockStyle(FontId.CONDENSED, 700, widthPct = 94, color = 0xFF76A6F3)
            FaceId.FLOAT -> ClockStyle(FontId.ROUNDED_HEAVY, 950, color = 0xFF4D9BEF)
            FaceId.SOLAR -> ClockStyle(
                FontId.THIN, 350, color = 0xFFDCCAF7, color2 = 0xFF9DF0F7, effect = Effect.TWO_COLOR,
                background = Background.GRADIENT, bgColor = 0xFFA396AE, bgColor2 = 0xFFB4E6E2,
            )
            FaceId.WORLD -> ClockStyle(FontId.LIGHT, 400, color = 0xFFFFAF3B)
            // iPhone Duo analog (mockup r5 A): 8 pale-blue rounded numerals, white hands, blue second hand
            FaceId.ANALOG -> ClockStyle(
                FontId.ROUNDED, 800, widthPct = 115, color = 0xFF76A6F3, analogNumbers = AnalogNumbers.DUO,
                numeralColor = 0xFFCFEAFF, handColor = 0xFFFFFFFF, secondColor = 0xFF76A6F3, weekdayColor = 0xFFFFFFFF, tickColor = 0xFFDDE6F0,
            )
            FaceId.PHOTOS_TIME -> ClockStyle(FontId.BOLD, 600, color = 0xFFFFFFFF)
        }

        /** Shared accent swatches (design-system §2.1). */
        val SWATCHES = listOf(
            0xFF76A6F3, 0xFF4D9BEF, 0xFFAA88FE, 0xFFFD6890, 0xFFE162B6, 0xFFFF453A, 0xFFFE7F7A,
            0xFFFF9C56, 0xFFFFD74B, 0xFF6FDC86, 0xFF30CEDA, 0xFFD5D9DF, 0xFFFFFFFF,
        )

        /** Solar palettes: bg start, bg end, digits start, digits end (mockup r3). */
        val SOLAR_SETS = listOf(
            listOf(0xFFA396AE, 0xFFB4E6E2, 0xFFDCCAF7, 0xFF9DF0F7),
            listOf(0xFFB07A86, 0xFFF2C49B, 0xFFFFD0DC, 0xFFFFE6B0),
            listOf(0xFF4F5DA8, 0xFF9C7FD8, 0xFFB9C8FF, 0xFFE2C9FF),
            listOf(0xFF5E8F6E, 0xFFD8E39A, 0xFFCFF5D0, 0xFFFFF7B8),
            listOf(0xFF9B4F4F, 0xFFF59B6B, 0xFFFFC2B0, 0xFFFFE0A6),
            listOf(0xFF3D7F8F, 0xFF7FD3E6, 0xFFB6F1FF, 0xFFD8FFF4),
            listOf(0xFF6B6B70, 0xFFB5B5BA, 0xFFE6E6EA, 0xFFFFFFFF),
        )
    }
}

enum class Layout(val ko: String, val slots: Int) {
    ONE("한 칸", 1), TWO("두 칸", 2), BIG2("큰 칸 + 작은 칸 2", 3), TWO2("작은 칸 2 + 큰 칸", 3), FOUR("네 칸", 4), THREE("세 칸", 3)
}

enum class CardType(val ko: String) {
    CLOCK("시계"), ANALOG("아날로그 시계"), MUSIC("음악"), VIDEO("영상 앱"), TIMER("타이머·뽀모도로"), SLEEP("수면 소리"),
    DDAY("D-day"), AGENDA("오늘 일정"), USAGE("Claude·Codex 사용량"), WEATHER("날씨"), CALENDAR("달력"), MEMORIES("추억"),
    /** The phone's notifications: newest big on a plate, the rest peeking behind it. */
    NOTIF_STACK("알림 · 쌓기"),
    /** The phone's notifications as the shade list (scrolls, swipe a row away, 모두 지우기). */
    NOTIF_LIST("알림 · 목록");

    val isNotif: Boolean get() = this == NOTIF_STACK || this == NOTIF_LIST
}

/**
 * One swipeable screen of slots. In a slot, a list of more than one card is a vertical stack (iPhone widget stack).
 * [accent] null = follow the hero clock (design-system §5.1).
 */
data class Board(
    val id: String,
    val name: String,
    val layout: Layout,
    val slots: List<List<CardType>>,
    val accent: Long? = null,
    /** Which clock style the board's clock-like cards use. */
    val clockFace: FaceId = FaceId.DIGITAL,
)

enum class NightMode(val ko: String) { AUTO("자동 (조도)"), SCHEDULE("시간 예약"), OFF("끔") }
enum class DisplayOff(val ko: String) { AUTO("자동"), AFTER_20S("20초 후"), NEVER("안 함") }
enum class UnfoldAction(val ko: String) { BOOK_BOARD("책 보드"), MOOD_LIGHT("무드등") }
enum class SleepSound(val ko: String) { RAIN("빗소리"), WAVES("파도"), WHITE("백색소음"), PINK("핑크 노이즈"), BROWN("브라운 노이즈") }

data class DDay(val name: String, val date: LocalDate)
data class City(val name: String, val lat: Double, val lon: Double)

data class AppConfig(
    val styles: Map<FaceId, ClockStyle> = FaceId.entries.associateWith { ClockStyle.default(it) },
    val boards: List<Board> = Presets.boards(),
    /** Horizontal page order: widgets, photos, clocks are fixed first three pages (iPhone), then [boards]. */
    val startPage: Int = 2,
    val use24h: Boolean? = null, // null = follow system
    val nightMode: NightMode = NightMode.AUTO,
    val nightStartHour: Int = 22,
    val nightEndHour: Int = 7,
    val nightEnterLux: Float = 3f,
    val nightExitLux: Float = 8f,
    val nightMaxBrightness: Float = 0.3f,
    /** Day brightness 0..1 set from the on-screen bar or settings; null = follow the system auto brightness. */
    val dayBrightness: Float? = null,
    /** Plug in with the screen off/locked and StandBy comes up (job + full-screen intent, no service). */
    val startOnCharge: Boolean = true,
    val displayOff: DisplayOff = DisplayOff.AUTO,
    val motionToWake: Boolean = true,
    val showNotifications: Boolean = true,
    val previewOnTapOnly: Boolean = false,
    val burnInShift: Boolean = true,
    val widgetLeft: List<CardType> = listOf(CardType.ANALOG, CardType.CALENDAR, CardType.WEATHER),
    val widgetRight: List<CardType> = listOf(CardType.CALENDAR, CardType.WEATHER, CardType.AGENDA),
    val glowColor: Long = 0xFFFFB36B,
    val glowStartMinBefore: Int = 0,
    val glowRampSec: Int = 6,
    val onUnfold: UnfoldAction = UnfoldAction.BOOK_BOARD,
    val ddays: List<DDay> = listOf(DDay("수능", LocalDate.of(2026, 11, 19))),
    val city: City = City("서울", 37.5665, 126.9780),
    val worldCities: List<City> = listOf(City("서울", 37.5665, 126.9780)),
    val usageEnabled: Boolean = true,
    val sleepSound: SleepSound = SleepSound.RAIN,
    val sleepMinutes: Int = 45,
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    /** Look of every StandBy surface; clock styles stay per clock on top of it. */
    val theme: Theme = Theme.IPHONE,
)

object Presets {
    private fun b(id: String, name: String, layout: Layout, vararg cards: CardType, face: FaceId = FaceId.DIGITAL) =
        Board(id, name, layout, cards.map { listOf(it) }, clockFace = face)

    /** Mockup r4 presets (desk = iPhone-less "책상" face kept as a board). */
    fun boards(): List<Board> = listOf(
        b("modular", "모듈러", Layout.TWO2, CardType.WEATHER, CardType.AGENDA, CardType.MUSIC),
        b("desk", "작업 책상", Layout.BIG2, CardType.CLOCK, CardType.TIMER, CardType.USAGE),
        b("music", "음악", Layout.TWO, CardType.CLOCK, CardType.MUSIC),
        b("bed", "잠자리", Layout.TWO, CardType.CLOCK, CardType.SLEEP),
        b("exam", "수능", Layout.FOUR, CardType.DDAY, CardType.CLOCK, CardType.CALENDAR, CardType.AGENDA),
        b("book", "책 (펼침)", Layout.BIG2, CardType.MEMORIES, CardType.WEATHER, CardType.CALENDAR),
        b("video", "영상", Layout.ONE, CardType.VIDEO),
    )
}
