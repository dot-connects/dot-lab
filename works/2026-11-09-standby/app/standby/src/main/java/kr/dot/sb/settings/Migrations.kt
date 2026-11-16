package kr.dot.sb.settings

import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.MyTheme
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.ui.theme.nextMyThemeName
import kr.dot.sb.model.AnalogStyle
import kr.dot.sb.model.Background
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.DDay
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.Theme
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Stored-config migrations for r9. r8 saved every face's style, edited or not; r9 keeps edits only (D4), so a stored
 * style equal to what r8 shipped as the default is an untouched clock and is dropped on read.
 */
object Migrations {
    /** The clocks page of r9 and older: a save without `myClocks` keeps these (plan-r10 "upgrade keeps the user's clocks"). */
    val OLD_CLOCKS = listOf(FaceId.DIGITAL, FaceId.ANALOG, FaceId.WORLD, FaceId.SOLAR, FaceId.FLOAT)

    /** The default D-day r9 shipped; v10 removes exactly this entry from older saves. */
    private val OLD_EXAM = DDay("수능", LocalDate.of(2026, 11, 19))

    /**
     * r13 (plan-r13 A1/A2), for saves before v11: NEON/TERMINAL `glowStrength` (their halo) moves to `textGlow` on every
     * edit; then each theme edit equal to no 내 테마 becomes "내 테마 N" (Theme order). The current theme's edit stays
     * applied with `customFrom` = its 내 테마; the other themes' edits are cleared.
     */
    fun looks(c: AppConfig): AppConfig {
        fun halo(th: Theme, e: ThemeEdit) =
            if (th != Theme.NEON && th != Theme.TERMINAL || e.glowStrength == null) e else e.copy(textGlow = e.glowStrength, glowStrength = null)
        val edits = c.themeEdits.mapValues { (th, e) -> halo(th, e) }
        var my = c.myThemes.map { it.copy(edit = halo(it.base, it.edit)) }
        var from: String? = null
        for (th in Theme.entries) {
            val e = edits[th]?.takeUnless { it.isEmpty } ?: continue
            val m = my.firstOrNull { it.base == th && it.edit == e } ?: MyTheme(nextMyThemeName(my), th, e).also { my = my + it }
            if (th == c.theme) from = m.name
        }
        return c.copy(myThemes = my, themeEdits = edits.filterKeys { it == c.theme }, customFrom = from)
    }

    /** r13 Part D: removed themes read as the theme that replaced them (every save version). */
    val RENAMED = mapOf("PAPER" to Theme.DAYPAPER, "CANDLE" to Theme.MOODLIGHT, "FLIP" to Theme.IPHONE)

    /**
     * 촛불 as a 무드등 edit (mockup theme-lineup 무드등 과감하게): a 노을 light from below, warm ink, faint glass. Only the
     * fields that differ from MOODLIGHT's tokens; glow strength .567 = the candle's α .34 over MOODLIGHT's .30 (× 2 rule).
     */
    val CANDLE_LOOK = ThemeEdit(
        bg = listOf(0xFF080504), glow = 0xFFFF7A2E, glowStrength = 0.567f, glowShape = kr.dot.sb.model.GlowShape.SUNSET,
        ink = 0xFFF7E6CF, accent = 0xFFFFC070, plate = 0xFFFFCD96, plateAlpha = 0.08f, textureAmt = 0.6f, borderColor = 0x2EFFCD96,
    )
    const val CANDLE_NAME = "촛불"

    /**
     * A save whose current theme was CANDLE (now MOODLIGHT + its edit): that look is also kept as the 내 테마 "촛불"
     * (another name when taken by a different look) and marked as where the look came from.
     */
    fun candle(c: AppConfig): AppConfig {
        val e = c.themeEdits[c.theme] ?: ThemeEdit()
        val m = c.myThemes.firstOrNull { it.base == c.theme && it.edit == e }
            ?: MyTheme(if (c.myThemes.none { it.name == CANDLE_NAME }) CANDLE_NAME else nextMyThemeName(c.myThemes), c.theme, e)
        return c.copy(myThemes = if (m in c.myThemes) c.myThemes else c.myThemes + m, customFrom = m.name)
    }

    /**
     * A save whose current theme was FLIP (now IPHONE): FLIP drew the DIGITAL clock as split-flap tiles, on the clocks
     * page and in board cards. The FLIP face draws the same tiles, so DIGITAL becomes FLIP wherever it is the face:
     * 내 시계, each board's clock face, and per-card faces. Clock style edits stay on their face.
     */
    fun flip(c: AppConfig): AppConfig = swapFace(c, FaceId.DIGITAL, FaceId.FLIP)

    /** [from] becomes [to] in 내 시계 (same position, repeats dropped), each board's clock face and per-card faces. */
    private fun swapFace(c: AppConfig, from: FaceId, to: FaceId): AppConfig {
        fun f(x: FaceId?) = if (x == from) to else x
        fun opts(m: Map<String, kr.dot.sb.model.SlotOptions>, board: FaceId?) = m.mapValues { (_, o) -> o.copy(face = f(o.face)?.takeIf { it != board }) }
        return c.copy(
            myClocks = c.myClocks.map { f(it)!! }.distinct(),
            boards = c.boards.map { b -> val face = f(b.clockFace)!!; b.copy(clockFace = face, options = opts(b.options, face)) },
            widgetOptions = opts(c.widgetOptions, null),
        )
    }

    /**
     * r14 (plan-r14 A1), saves before v12, after [flip]: 둥근 굵은 글씨 (FLOAT) is gone and reads as DIGITAL wherever
     * it is the face. Its stored style becomes the DIGITAL style (centre digit in accent, overlapping digits) only when
     * the user had no digital clock of their own (DIGITAL neither in 내 시계 nor styled); otherwise it is dropped.
     */
    fun floatToDigital(c: AppConfig): AppConfig {
        val float = c.styles[FaceId.FLOAT]
        val move = float != null && FaceId.DIGITAL !in c.myClocks && FaceId.DIGITAL !in c.styles
        val styles = (c.styles - FaceId.FLOAT).let { if (move) it + (FaceId.DIGITAL to float!!.copy(accentMiddle = true, overlap = true)) else it }
        return swapFace(c, FaceId.FLOAT, FaceId.DIGITAL).copy(styles = styles)
    }

    /** Stored `myClocks`: missing = [OLD_CLOCKS]; unknown ids and repeats dropped; nothing left = [fresh]. */
    fun myClocks(a: JSONArray?, fresh: List<FaceId>): List<FaceId> {
        if (a == null) return OLD_CLOCKS
        val ids = (0 until a.length()).mapNotNull { runCatching { FaceId.valueOf(a.optString(it)) }.getOrNull() }.distinct()
        return ids.ifEmpty { fresh }
    }

    fun ddays(read: List<DDay>, v: Int): List<DDay> = if (v < 10) read - OLD_EXAM else read

    /** What r8 wrote for an untouched face (frozen copy of r8 `ClockStyle.default`; never edit). */
    fun r8Default(face: FaceId): ClockStyle = when (face) {
        // r10 faces never existed in r8; they read with the DIGITAL base
        FaceId.DIGITAL, FaceId.DESK, FaceId.THIN, FaceId.SPLIT, FaceId.COLOR_BLOCK, FaceId.STICKER, FaceId.BALLS, FaceId.FLIP,
        FaceId.LED, FaceId.LCD, FaceId.DOT_MATRIX, FaceId.WALL, FaceId.CALENDAR, FaceId.HANGUL, FaceId.DAY_BAR, FaceId.RIM,
        FaceId.RELIEF, FaceId.RELIEF_SQUARE, FaceId.RELIEF_DIGITAL,
        -> ClockStyle(FontId.CONDENSED, 700, widthPct = 94, color = 0xFF76A6F3)
        FaceId.FLOAT -> ClockStyle(FontId.ROUNDED_HEAVY, 950, color = 0xFF4D9BEF)
        FaceId.SOLAR -> ClockStyle(
            FontId.THIN, 350, color = 0xFFDCCAF7, color2 = 0xFF9DF0F7, effect = Effect.TWO_COLOR,
            background = Background.GRADIENT, bgColor = 0xFFA396AE, bgColor2 = 0xFFB4E6E2,
        )
        FaceId.WORLD -> ClockStyle(FontId.LIGHT, 400, color = 0xFFFFAF3B)
        FaceId.ANALOG -> ClockStyle(
            FontId.ROUNDED, 800, widthPct = 115, color = 0xFF76A6F3, analogNumbers = AnalogNumbers.DUO,
            numeralColor = 0xFFCFEAFF, handColor = 0xFFFFFFFF, secondColor = 0xFF76A6F3, weekdayColor = 0xFFFFFFFF, tickColor = 0xFFDDE6F0,
        )
        FaceId.PHOTOS_TIME -> ClockStyle(FontId.BOLD, 600, color = 0xFFFFFFFF)
    }

    /**
     * Read the stored styles: edits only, plus the analog dial layout carried over from r8's Duo numerals (D7).
     * Order matters: (1) note whether the analog style is r8's untouched default, (2) move a Duo dial to
     * [AnalogStyle.DUO] when the user saw it (iPhone theme, or an edited analog style), (3) drop untouched styles.
     * Returns the styles and the migrated analog style (null = nothing to carry over).
     */
    fun styles(o: JSONObject?, theme: Theme, r8: Boolean, read: (JSONObject, ClockStyle) -> ClockStyle): Pair<Map<FaceId, ClockStyle>, AnalogStyle?> {
        if (o == null) return emptyMap<FaceId, ClockStyle>() to null
        // an r9 save holds edits only, read as written (an edit can equal the r8 look on purpose)
        if (!r8) return FaceId.entries.mapNotNull { f -> o.optJSONObject(f.name)?.let { f to read(it, r8Default(f)) } }.toMap() to null
        var analog: AnalogStyle? = null
        val out = mutableMapOf<FaceId, ClockStyle>()
        for (f in FaceId.entries) {
            val so = o.optJSONObject(f.name) ?: continue
            var s = read(so, r8Default(f))
            val untouched = s == r8Default(f)
            if (f == FaceId.ANALOG && s.analogNumbers == AnalogNumbers.DUO) {
                if (theme == Theme.IPHONE || !untouched) analog = AnalogStyle.DUO
                s = s.copy(analogNumbers = AnalogNumbers.TWELVE)
            }
            if (!untouched) out[f] = s
        }
        return out to analog
    }
}
