package kr.dot.sb.settings

import kr.dot.sb.model.AnalogNumbers
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
