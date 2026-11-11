package kr.dot.sb.settings

import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AnalogStyle
import kr.dot.sb.model.Background
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.Theme
import org.json.JSONObject

/**
 * Stored-config migrations for r9. r8 saved every face's style, edited or not; r9 keeps edits only (D4), so a stored
 * style equal to what r8 shipped as the default is an untouched clock and is dropped on read.
 */
object Migrations {
    /** What r8 wrote for an untouched face (frozen copy of r8 `ClockStyle.default`; never edit). */
    fun r8Default(face: FaceId): ClockStyle = when (face) {
        FaceId.DIGITAL, FaceId.DESK -> ClockStyle(FontId.CONDENSED, 700, widthPct = 94, color = 0xFF76A6F3)
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
    fun styles(o: JSONObject?, theme: Theme, read: (JSONObject, ClockStyle) -> ClockStyle): Pair<Map<FaceId, ClockStyle>, AnalogStyle?> {
        if (o == null) return emptyMap<FaceId, ClockStyle>() to null
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
