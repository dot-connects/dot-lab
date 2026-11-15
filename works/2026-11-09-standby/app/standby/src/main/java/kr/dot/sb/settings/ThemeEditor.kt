package kr.dot.sb.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.BgKind
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.Border
import kr.dot.sb.model.CardFont
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.GlowShape
import kr.dot.sb.model.MyTheme
import kr.dot.sb.model.TextWeight
import kr.dot.sb.model.Texture
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.ui.clock.ColorShuffle
import kr.dot.sb.ui.theme.Contrast
import kr.dot.sb.ui.theme.Hsl
import kr.dot.sb.ui.theme.LocalThemeTime
import kr.dot.sb.ui.theme.Look
import kr.dot.sb.ui.theme.PlateStyle
import kr.dot.sb.ui.theme.StripPick
import kr.dot.sb.ui.theme.ThemeTokens
import kr.dot.sb.ui.theme.Themes
import kr.dot.sb.ui.theme.nextMyThemeName
import kr.dot.sb.ui.theme.restoreLook
import kr.dot.sb.ui.theme.saveMyTheme
import kr.dot.sb.ui.theme.switchLook
import kr.dot.sb.ui.theme.withBoardBg
import kr.dot.sb.ui.theme.withThemeEdit
import java.time.LocalTime
import kotlin.random.Random

/*
 * r13 사용자 지정 (plan-r13 A4): one universal list of controls, each tagged with its tab (배경 · 카드 · 글자); the open
 * tab filters it and [ThemeEditor.visible] hides the rows that do nothing for the drawn look. Every write goes through
 * AppConfig.withThemeEdit; colours and sliders preview a screen-local draft and write once on release.
 */

/** One field of [ThemeEdit] a control writes (the gradient's three stops are three fields of `bg`). */
enum class EditField {
    BG_KIND, BG, GRAD_TOP, GRAD_MID, GRAD_BOTTOM, GLOW, GLOW_STRENGTH, GLOW_SHAPE, GLOW_AT, FLOW, BG_TEXTURE,
    PLATE_STYLE, PLATE, PLATE_ALPHA, RADIUS, BORDER, BORDER_COLOR, RULE, CARD_TEXTURE,
    INK, ACCENT, TEXT_FONT, TEXT_WEIGHT, TEXT_GLOW,
}

enum class EditTab(val ko: String) { BG("배경"), CARD("카드"), TEXT("글자") }

/** A row of the editor. [label] is what the row says; [field] what it writes; [tab] where it lives. */
sealed class Control(val field: EditField, val label: String, val tab: EditTab) {
    /** Swatches + 직접 고르기. */
    class Colour(field: EditField, label: String, tab: EditTab, val swatches: List<Long>) : Control(field, label, tab)
    class Slider(field: EditField, label: String, tab: EditTab, val range: ClosedFloatingPointRange<Float>) : Control(field, label, tab)
    /** One of a few named options ([ThemeEditor.choice] reads, [ThemeEditor.withChoice] writes). */
    class Choice(field: EditField, label: String, tab: EditTab, val options: List<String>) : Control(field, label, tab)
    /** A texture's chips, then 질감 정도 while it is not 없음 (one amount for both textures). */
    class Grain(field: EditField, label: String, tab: EditTab) : Control(field, label, tab)
    class Flow : Control(EditField.FLOW, "시간 따라 흐름", EditTab.BG)
    /** 불빛 위치: a small square pad (plan round 3), off with 노을. */
    class GlowPad : Control(EditField.GLOW_AT, "불빛 위치", EditTab.BG)
}

/**
 * 이전 조합 (plan-r13 A4): [has] = something to undo, the whole look ([theme], [edit], [customFrom]); [edit] null = no
 * edit. Saved as JSON; a slot it cannot read (an r12 one) is dropped.
 */
data class UndoSlot(val has: Boolean, val theme: Theme = Theme.IPHONE, val edit: ThemeEdit? = null, val customFrom: String? = null) {
    companion object {
        val NONE = UndoSlot(false)

        val SAVER = Saver<UndoSlot, String>(
            save = { u ->
                if (!u.has) "" else org.json.JSONObject().put("theme", u.theme.name)
                    .apply { u.edit?.let { put("edit", ConfigCodec.encodeEdit(it)) }; u.customFrom?.let { put("customFrom", it) } }.toString()
            },
            restore = { s ->
                runCatching {
                    val o = org.json.JSONObject(s)
                    val th = Theme.valueOf(o.getString("theme"))
                    UndoSlot(true, th, if (o.has("edit")) ConfigCodec.decodeEdit(o.getString("edit"), th) else null, if (o.has("customFrom")) o.getString("customFrom") else null)
                }.getOrDefault(NONE)
            },
        )
    }
}

object ThemeEditor {
    /** Pages for the light themes (종이, 라디오). */
    private val LIGHT_BG = listOf(0xFFF3EEE4, 0xFFE9E2D2, 0xFFD9D3C7, 0xFFEDE6F0, 0xFFE3ECE6)
    val TERMINAL_INKS = listOf(0xFF7CF7A0, 0xFFFFB000, 0xFFE8E8E8)

    /** 카드 모양 in the order the row offers them (판 · 유리 · 테두리선 · 구분선). */
    val PLATES = listOf(PlateStyle.FILL, PlateStyle.GLASS, PlateStyle.OUTLINE, PlateStyle.RULE)
    /** The textures the editor offers (SCANLINE is a token-only texture). */
    val TEXTURES = listOf(Texture.NONE, Texture.PAPER, Texture.GRAIN, Texture.LINEN, Texture.METAL, Texture.FROST)
    val TEXTURE_KO = listOf("없음", "종이결", "필름 그레인", "린넨", "헤어라인 금속", "서리 유리")

    internal fun Color.long(): Long = toArgb().toLong() and 0xFFFFFFFFL
    private fun opaque(c: Color) = c.copy(alpha = 1f).long()

    /** The theme's own colour first, then [rest] (a row with no edit shows the theme's colour selected). */
    private fun with(own: Long, rest: List<Long>) = (listOf(own) + rest).distinct()

    /** Every row of the editor, in tab order (plan-r13 A4 list); swatches start from [theme]'s own colours. */
    fun controls(theme: Theme): List<Control> {
        val tk = Themes.of(theme).tokens
        val sw = ClockStyle.SWATCHES
        val pages = if (tk.light) LIGHT_BG else BG_SWATCHES
        // cards: deep (or, on a light page, pale) tones of the swatches, so the ink keeps reading on them
        val plates = with(opaque(tk.plate), sw.map { ColorShuffle.tone(it, if (tk.light) 0.86f else 0.2f) }.distinct())
        val inks = with(tk.ink.long(), if (tk.light) sw.map { ColorShuffle.tone(it, 0.16f) }.distinct() else sw)
        fun stop(i: Int) = with(tk.bg.stops.getOrElse(i) { tk.bg.base }.long(), pages)
        val bg = EditTab.BG; val card = EditTab.CARD; val text = EditTab.TEXT
        return listOf(
            Control.Choice(EditField.BG_KIND, "배경 종류", bg, listOf("단색", "그라데이션", "불빛")),
            Control.Colour(EditField.BG, "배경 색", bg, with(tk.bg.base.long(), pages)),
            Control.Colour(EditField.GRAD_TOP, "그라데이션 위", bg, stop(0)),
            Control.Colour(EditField.GRAD_MID, "그라데이션 가운데", bg, stop(1)),
            Control.Colour(EditField.GRAD_BOTTOM, "그라데이션 아래", bg, stop(2)),
            Control.Colour(EditField.GLOW, "불빛 색", bg, with(opaque(tk.bg.glow ?: tk.accent), sw)),
            Control.Slider(EditField.GLOW_STRENGTH, "불빛 세기", bg, 0f..1f),
            Control.Choice(EditField.GLOW_SHAPE, "불빛 모양", bg, GlowShape.entries.map { it.ko }),
            Control.GlowPad(),
            Control.Flow(),
            Control.Grain(EditField.BG_TEXTURE, "배경 질감", bg),
            Control.Choice(EditField.PLATE_STYLE, "카드 모양", card, listOf("판", "유리", "테두리선", "구분선")),
            Control.Colour(EditField.PLATE, "카드 색", card, plates),
            Control.Slider(EditField.PLATE_ALPHA, "카드 진하기", card, ThemeEdit.PLATE_ALPHA),
            Control.Slider(EditField.RADIUS, "모서리", card, 0f..0.2f),
            Control.Choice(EditField.BORDER, "테두리", card, listOf("없음", "얇게", "굵게")),
            Control.Colour(EditField.BORDER_COLOR, "테두리 색", card, with(opaque(tk.borderColor ?: tk.ink), listOf(0xFFFFFFFF, 0xFF000000) + sw)),
            Control.Colour(EditField.RULE, "구분선 색", card, with(opaque(tk.rule ?: tk.ink), sw)),
            Control.Grain(EditField.CARD_TEXTURE, "카드 질감", card),
            Control.Colour(EditField.INK, "글자 색", text, inks),
            Control.Colour(EditField.ACCENT, "강조 색", text, with(tk.accent.long(), sw)),
            Control.Choice(EditField.TEXT_FONT, "글꼴", text, listOf("고딕", "명조", "둥근 고딕", "손글씨")),
            Control.Choice(EditField.TEXT_WEIGHT, "글자 두께", text, listOf("가늘게", "보통", "굵게")),
            Control.Slider(EditField.TEXT_GLOW, "글자 빛 번짐", text, 0f..1f),
        )
    }

    /** The background kind [d] (drawn tokens) shows: a glow, more than one stop, or one colour. */
    fun kindOf(d: ThemeTokens): BgKind = when {
        d.bg.glow != null -> BgKind.GLOW
        d.bg.stops.size > 1 -> BgKind.GRADIENT
        else -> BgKind.SOLID
    }

    /** The glow shape [d] draws (ThemeTokens.with: 노을 sits below the page, 둘 adds an unnamed mirror). */
    private fun shapeOf(d: ThemeTokens): GlowShape = when {
        d.bg.glowAt.y > 1f -> GlowShape.SUNSET
        d.bg.glow2At != null && d.bg.glow2 == null -> GlowShape.TWIN
        else -> GlowShape.ROUND
    }

    /** Whether [c] does anything for the look [d] (drawn tokens of the edit): rows of another bg kind or card shape hide. */
    fun visible(c: Control, d: ThemeTokens): Boolean {
        val kind = kindOf(d)
        val style = d.plateStyle
        return when (c.field) {
            EditField.BG -> kind != BgKind.GRADIENT
            EditField.GRAD_TOP, EditField.GRAD_MID, EditField.GRAD_BOTTOM -> kind == BgKind.GRADIENT
            EditField.GLOW, EditField.GLOW_STRENGTH, EditField.GLOW_SHAPE, EditField.GLOW_AT -> kind == BgKind.GLOW
            EditField.PLATE, EditField.PLATE_ALPHA -> style == PlateStyle.FILL || style == PlateStyle.GLASS
            // the OUTLINE line is drawn in the border colour
            EditField.BORDER_COLOR -> d.border != Border.NONE || style == PlateStyle.OUTLINE
            EditField.RULE -> style == PlateStyle.RULE
            else -> true
        }
    }

    private fun gradIndex(f: EditField) = when (f) { EditField.GRAD_TOP -> 0; EditField.GRAD_MID -> 1; EditField.GRAD_BOTTOM -> 2; else -> -1 }

    /** [f]'s colour in [e] (null = not edited). */
    fun colour(f: EditField, e: ThemeEdit): Long? = when (f) {
        EditField.BG -> e.bg?.firstOrNull()
        EditField.GRAD_TOP, EditField.GRAD_MID, EditField.GRAD_BOTTOM -> e.bg?.takeIf { it.size == 3 }?.get(gradIndex(f))
        EditField.GLOW -> e.glow
        EditField.PLATE -> e.plate
        EditField.BORDER_COLOR -> e.borderColor
        EditField.RULE -> e.rule
        EditField.ACCENT -> e.accent
        EditField.INK -> e.ink
        else -> null
    }

    /** The colour [d] (drawn tokens) shows for [f] (what an unedited row shows). */
    fun token(f: EditField, d: ThemeTokens): Long = when (f) {
        EditField.BG -> d.bg.base.long()
        EditField.GRAD_TOP, EditField.GRAD_MID, EditField.GRAD_BOTTOM -> d.bg.stops.getOrElse(gradIndex(f)) { d.bg.base }.long()
        EditField.GLOW -> opaque(d.bg.glow ?: d.accent)
        EditField.PLATE -> opaque(d.plate)
        EditField.BORDER_COLOR -> opaque(d.borderColor ?: d.ink)
        EditField.RULE -> opaque(d.rule ?: d.ink)
        EditField.ACCENT -> d.accent.long()
        else -> d.ink.long()
    }

    /** [e] with [f] set to [v] (null = back to the theme's). A gradient stop fills the other two from what is drawn. */
    fun withColour(f: EditField, e: ThemeEdit, v: Long?, tk: ThemeTokens): ThemeEdit = when (f) {
        EditField.BG -> e.copy(bg = v?.let { listOf(it) })
        EditField.GRAD_TOP, EditField.GRAD_MID, EditField.GRAD_BOTTOM -> {
            val drawn = tk.with(e.copy(flow = null)).bg.stops.map { it.long() }
            val own = tk.bg.stops.map { it.long() }
            if (drawn.size != 3) e
            else {
                val i = gradIndex(f)
                val l = (e.bg?.takeIf { it.size == 3 } ?: drawn).toMutableList().also { it[i] = v ?: own.getOrElse(i) { drawn[i] } }
                e.copy(bg = l.takeIf { it != own })
            }
        }
        EditField.GLOW -> e.copy(glow = v)
        EditField.PLATE -> e.copy(plate = v)
        EditField.BORDER_COLOR -> e.copy(borderColor = v)
        EditField.RULE -> e.copy(rule = v)
        EditField.ACCENT -> e.copy(accent = v)
        EditField.INK -> e.copy(ink = v)
        else -> e
    }

    /** A slider's value in [e], else what [d] (drawn tokens) draws. */
    fun slider(f: EditField, e: ThemeEdit, d: ThemeTokens): Float = when (f) {
        EditField.GLOW_STRENGTH -> e.glowStrength ?: 0.5f
        // unset = the theme's: NEON's halo (drawn at 2 x g), TERMINAL's weak card glow, else off
        EditField.TEXT_GLOW -> e.textGlow ?: if (d.haloAmt > 0f) (d.haloAmt / 2f).coerceAtMost(1f) else d.cardGlow ?: 0f
        EditField.PLATE_ALPHA -> e.plateAlpha ?: d.plate.alpha.coerceIn(ThemeEdit.PLATE_ALPHA)
        else -> e.radius ?: d.radiusRatio
    }

    fun withSlider(f: EditField, e: ThemeEdit, v: Float?): ThemeEdit = when (f) {
        EditField.GLOW_STRENGTH -> e.copy(glowStrength = v)
        EditField.TEXT_GLOW -> e.copy(textGlow = v)
        EditField.PLATE_ALPHA -> e.copy(plateAlpha = v)
        else -> e.copy(radius = v)
    }

    /** The option [d] (drawn tokens) shows for a [Control.Choice] or [Control.Grain] field; -1 = none of the offered. */
    fun choice(f: EditField, d: ThemeTokens): Int = when (f) {
        EditField.BG_KIND -> kindOf(d).ordinal
        EditField.GLOW_SHAPE -> shapeOf(d).ordinal
        EditField.PLATE_STYLE -> PLATES.indexOf(d.plateStyle)
        EditField.BORDER -> d.border.ordinal
        EditField.TEXT_FONT -> d.cardFont.ordinal
        EditField.TEXT_WEIGHT -> d.textWeight.ordinal
        EditField.BG_TEXTURE -> TEXTURES.indexOf(d.bgTexture)
        EditField.CARD_TEXTURE -> TEXTURES.indexOf(d.cardTexture)
        else -> -1
    }

    fun withChoice(f: EditField, e: ThemeEdit, i: Int): ThemeEdit = when (f) {
        EditField.BG_KIND -> e.copy(bgKind = BgKind.entries[i])
        EditField.GLOW_SHAPE -> e.copy(glowShape = GlowShape.entries[i])
        EditField.PLATE_STYLE -> e.copy(plateStyle = PLATES[i])
        EditField.BORDER -> e.copy(border = Border.entries[i])
        EditField.TEXT_FONT -> e.copy(textFont = CardFont.entries[i])
        EditField.TEXT_WEIGHT -> e.copy(textWeight = TextWeight.entries[i])
        EditField.BG_TEXTURE -> e.copy(bgTexture = TEXTURES[i])
        EditField.CARD_TEXTURE -> e.copy(cardTexture = TEXTURES[i])
        else -> e
    }

    /** 질감 정도 shown for [f]'s texture: the edit's, else the drawn one (a card texture may carry its own, RADIO). */
    fun textureAmt(f: EditField, e: ThemeEdit, d: ThemeTokens): Float =
        (e.textureAmt ?: if (f == EditField.CARD_TEXTURE) d.cardTextureAmt ?: d.textureAmt else d.textureAmt).coerceIn(ThemeEdit.TEXTURE_AMT)

    /** [f] cleared (the row's 기본). */
    fun reset(f: EditField, e: ThemeEdit, tk: ThemeTokens): ThemeEdit = when (f) {
        EditField.GLOW_STRENGTH, EditField.TEXT_GLOW, EditField.PLATE_ALPHA, EditField.RADIUS -> withSlider(f, e, null)
        EditField.BG_KIND -> e.copy(bgKind = null)
        EditField.GLOW_AT -> e.copy(glowAt = null)
        EditField.GLOW_SHAPE -> e.copy(glowShape = null)
        EditField.PLATE_STYLE -> e.copy(plateStyle = null)
        EditField.FLOW -> e.copy(flow = null)
        EditField.BORDER -> e.copy(border = null)
        EditField.TEXT_FONT -> e.copy(textFont = null)
        EditField.TEXT_WEIGHT -> e.copy(textWeight = null)
        EditField.BG_TEXTURE -> e.copy(bgTexture = null)
        EditField.CARD_TEXTURE -> e.copy(cardTexture = null)
        else -> withColour(f, e, null, tk)
    }

    /** True when [f] is set in [e] (the row's 기본 is enabled). */
    fun isSet(f: EditField, e: ThemeEdit): Boolean = when (f) {
        EditField.GLOW_STRENGTH -> e.glowStrength != null
        EditField.TEXT_GLOW -> e.textGlow != null
        EditField.PLATE_ALPHA -> e.plateAlpha != null
        EditField.RADIUS -> e.radius != null
        EditField.BG_KIND -> e.bgKind != null
        EditField.GLOW_AT -> e.glowAt != null
        EditField.GLOW_SHAPE -> e.glowShape != null
        EditField.PLATE_STYLE -> e.plateStyle != null
        EditField.FLOW -> e.flow != null
        EditField.BORDER -> e.border != null
        EditField.TEXT_FONT -> e.textFont != null
        EditField.TEXT_WEIGHT -> e.textWeight != null
        EditField.BG_TEXTURE -> e.bgTexture != null
        EditField.CARD_TEXTURE -> e.cardTexture != null
        else -> colour(f, e) != null
    }

    /** 시간 따라 흐름 switched on: 아침 warm, 낮 = the drawn glow / top stop, 저녁 lilac (dusk blue without a glow). */
    fun defaultFlow(d: ThemeTokens): List<Long> =
        if (d.bg.glow != null) listOf(0xFFFF9C56, opaque(d.bg.glow!!), 0xFFAA88FE)
        else listOf(0xFF3A5A8C, d.bg.base.long(), 0xFF5A3A6A)
}

/**
 * 색 조합 바꾸기 (plan-r13 A4): pure. Draws only colour fields ([fields]: the background colours of the drawn bg kind,
 * cards, border, 구분선, ink, accent), hue-matched like the mockup (page, glow and cards from one hue, accent 30–150°
 * away), keeps every other field of [current], and draws again (up to 30 times) until [Contrast.warnings] is empty;
 * else returns [current]. Never bg kind, shapes, fonts, textures or the flow.
 */
object ThemeShuffle {
    /** The fields a shuffle draws on a [kind] background. */
    fun fields(kind: BgKind): List<EditField> = when (kind) {
        BgKind.SOLID -> listOf(EditField.BG)
        BgKind.GRADIENT -> listOf(EditField.GRAD_TOP, EditField.GRAD_MID, EditField.GRAD_BOTTOM)
        BgKind.GLOW -> listOf(EditField.BG, EditField.GLOW)
    } + listOf(EditField.PLATE, EditField.BORDER_COLOR, EditField.RULE, EditField.ACCENT, EditField.INK)

    fun next(theme: Theme, base: ThemeTokens, current: ThemeEdit, rnd: Random): ThemeEdit {
        val fields = fields(ThemeEditor.kindOf(base.with(current)))
        repeat(30) {
            val e = draw(theme, fields, base, current, rnd).sanitized(theme)
            if (e != current && Contrast.warnings(base.with(e), base).isEmpty()) return e
        }
        return current
    }

    private fun draw(theme: Theme, fields: List<EditField>, base: ThemeTokens, current: ThemeEdit, rnd: Random): ThemeEdit {
        // the mockup's rule: one hue for page, glow, cards and ink at set saturation/lightness, the accent 30–150° away
        val h = rnd.nextFloat() * 360f
        val pop = (h + 30f + rnd.nextFloat() * 120f) % 360f
        val light = base.light
        // NEON: tubes of light on black, so its ink and accent are full swatches
        val tubes = base.plateStyle == PlateStyle.OUTLINE
        fun hsl(hue: Float, s: Float, l: Float) = with(ThemeEditor) { Color.hsl(hue, s, l).long() }
        var e = current
        for (f in fields) {
            val v = when (f) {
                EditField.BG -> if (light) hsl(h, 0.3f, 0.9f) else hsl(h, 0.35f, 0.06f)
                EditField.GRAD_TOP -> if (light) hsl(h, 0.3f, 0.9f) else hsl(h, 0.4f, 0.13f)
                EditField.GRAD_MID -> if (light) hsl((h + 20f) % 360f, 0.3f, 0.85f) else hsl((h + 20f) % 360f, 0.35f, 0.2f)
                EditField.GRAD_BOTTOM -> if (light) hsl(pop, 0.3f, 0.8f) else hsl(pop, 0.35f, 0.22f)
                EditField.GLOW -> hsl(h, 0.6f, 0.42f)
                EditField.PLATE -> if (light) hsl(h, 0.25f, 0.84f) else hsl(h, 0.25f, 0.14f)
                EditField.RULE, EditField.BORDER_COLOR -> if (light) hsl(pop, 0.5f, 0.35f) else hsl(pop, 0.5f, 0.6f)
                EditField.ACCENT -> if (tubes) ClockStyle.SWATCHES.random(rnd) else if (light) hsl(pop, 0.7f, 0.32f) else hsl(pop, 0.85f, 0.72f)
                EditField.INK -> when {
                    theme == Theme.TERMINAL -> ThemeEditor.TERMINAL_INKS.random(rnd)
                    tubes -> ClockStyle.SWATCHES.random(rnd)
                    light -> hsl(h, 0.3f, 0.12f)
                    else -> hsl(h, 0.4f, 0.94f)
                }
                else -> continue
            }
            e = ThemeEditor.withColour(f, e, v, base)
        }
        return e
    }
}

// ---- UI -------------------------------------------------------------------------------------------------------------

/** Korean for the editor's warnings (amber under the tabs). */
private fun Contrast.Warning.ko(): String = when (this) {
    Contrast.Warning.INK -> "글자가 흐려요"
    Contrast.Warning.INK2 -> "작은 글자가 흐려요"
    Contrast.Warning.ACCENT -> "강조 색이 흐려요"
}

private val FLOW_KO = listOf("아침", "낮", "저녁")
/** Preview chips 아침 · 낮 · 저녁 (plan round 3), minutes of the day. */
private val FLOW_AT = listOf(7 * 60, 13 * 60, 19 * 60)

/**
 * 사용자 지정 (plan-r13 A4, mockup custom-theme.html layout B): preview, "어디서 시작할까요" chips, [색 조합 바꾸기]
 * [이전 조합] [저장], then the tabs 배경 · 카드 · 글자 (sticky) with the contrast warnings under them and the tab's rows.
 */
@Composable
fun CustomThemeScreen(config: AppConfig, update: Update, back: () -> Unit) {
    val theme = config.theme
    val tk = Themes.of(theme).tokens
    val saved = config.themeEdits[theme]
    // a held colour/slider (never saved); a new saved edit or theme drops it
    var draft by remember(theme, saved) { mutableStateOf<ThemeEdit?>(null) }
    // the 아침/낮/저녁 preview chip (minute of the day), only while 시간 따라 흐름 is on
    var previewAt by rememberSaveable(theme) { mutableStateOf<Int?>(null) }
    var tab by rememberSaveable { mutableStateOf(EditTab.BG) }
    // one level, not keyed by theme: a start chip changes the theme and must still be undoable
    var undo by rememberSaveable(stateSaver = UndoSlot.SAVER) { mutableStateOf(UndoSlot.NONE) }
    var presses by rememberSaveable { mutableIntStateOf(0) }
    var naming by rememberSaveable { mutableStateOf<String?>(null) }
    // the guard's target as a key (a 내 테마 by name), so the dialog survives rotation
    var guardKey by rememberSaveable { mutableStateOf<String?>(null) }
    val guard = guardKey?.let { k -> lookOf(k, config) }
    val cur = draft ?: saved ?: ThemeEdit()
    val drawn = remember(theme, cur) { tk.with(cur) }
    val shown = remember(config, draft) { draft?.let { config.withThemeEdit(theme, it) } ?: config }
    val flowOn = shown.themeEdits[theme]?.flow != null
    val at = previewAt?.takeIf { flowOn }?.let { LocalTime.of(it / 60, it % 60) } ?: LocalThemeTime.current ?: LocalTime.now()
    // lambdas, not `::` references: a function reference is memoized without its captures, so after a start chip it
    // kept writing the previous theme (review r13 H1)
    val commit: (ThemeEdit) -> Unit = { e -> update { it.withThemeEdit(theme, e) } }
    val keepUndo: () -> Unit = { undo = UndoSlot(true, theme, saved, config.customFrom) }
    val start: (Theme) -> Unit = { th ->
        val to = Look.Builtin(th)
        if (StripPick.of(config) == StripPick.Custom) guardKey = to.key() else { keepUndo(); update { it.switchLook(to) } }
    }
    val themed = config.boardBg == BoardBg.THEME
    kr.dot.sb.ui.theme.ProvideTheme(shown, at) {
        Page(
            "사용자 지정", back, preview = Preview(shown, pageSlides(shown)),
            sticky = if (!themed) null else { { Segmented(EditTab.entries.map { it.ko }, tab.ordinal, { tab = EditTab.entries[it] }) } },
            after = if (!themed) null else { {
                val warn = Contrast.warnings(drawn, tk)
                if (warn.isNotEmpty()) T(warn.joinToString(" · ") { it.ko() }, 13.sp, 500, SK.why, Modifier.padding(horizontal = 16.dp).padding(top = 8.dp))
                Section(null) {
                    ThemeEditor.controls(theme).filter { it.tab == tab && ThemeEditor.visible(it, drawn) }.forEach { c ->
                        // keyed: a row's own state (an open picker) must not pass to the row at its place in another tab or theme
                        item { androidx.compose.runtime.key(theme, c.field) { ControlView(c, theme, tk, drawn, cur, { draft = it }, commit, previewAt.takeIf { flowOn }, { previewAt = it }) } }
                    }
                }
            } },
        ) {
            if (!themed) {
                Section(null) {
                    item {
                        ControlRow(null, note = "배경을 '테마 기본'으로 두면 꾸민 색·글꼴이 보여요") {
                            ActionButton("테마 기본으로 바꾸기", { update { it.withBoardBg(BoardBg.THEME) } }, primary = false)
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                    T("어디서 시작할까요", 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, bottom = 8.dp))
                    ChipFlow(Theme.entries.map { it.ko }, Theme.entries.indexOf(theme), { i -> start(Theme.entries[i]) })
                    T("고른 테마의 색·모양을 그대로 불러와요. 그다음 아래에서 아무거나 바꿔요.", 13.sp, 400, SK.muted, Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp), lineHeight = 18.sp)
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("색 조합 바꾸기", {
                        // toString, not hashCode: enum hashCodes change every process (ColorShuffle)
                        val e = ThemeShuffle.next(theme, tk, cur, Random(cur.toString().hashCode() + presses++))
                        if (e != cur) { keepUndo(); commit(e) }
                    }, Modifier.weight(1.3f), primary = false, padH = 8.dp)
                    ActionButton("이전 조합", {
                        val u = undo
                        undo = UndoSlot.NONE
                        if (u.has) update { it.restoreLook(u.theme, u.edit, u.customFrom) }
                    }, Modifier.weight(1f), primary = false, enabled = undo.has, padH = 8.dp)
                    ActionButton("저장", { naming = config.customFrom ?: nextMyThemeName(config.myThemes) }, Modifier.weight(1f), enabled = saved != null, padH = 8.dp)
                }
            }
        }
    }
    naming?.let { n ->
        NameDialog("내 테마로 저장", n, { naming = null }, taken = config.myThemes) { name ->
            naming = null
            update { it.saveMyTheme(name) }
        }
    }
    guard?.let { to -> UnsavedGuard(config, update, to, { guardKey = null }, onSwitch = keepUndo) }
}

/** A row's title with a small 기본 that clears just this field. */
@Composable
private fun EditTitle(label: String, set: Boolean, reset: () -> Unit, value: String? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 16.sp, 500, SK.text, Modifier.weight(1f))
        if (value != null) T(value, 14.sp, 500, SK.muted)
        ResetChip(set, reset)
    }
}

@Composable
private fun ResetChip(set: Boolean, reset: () -> Unit) =
    Box(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50)).clickable(enabled = set, onClick = reset).padding(horizontal = 12.dp), Alignment.Center) {
        T("기본", 13.sp, 600, if (set) SK.muted else SK.line)
    }

private fun pct(v: Float) = "${kotlin.math.round(v * 100).toInt()}%"

@Composable
private fun ControlView(
    c: Control, theme: Theme, tk: ThemeTokens, drawn: ThemeTokens, edit: ThemeEdit, setDraft: (ThemeEdit?) -> Unit, commit: (ThemeEdit) -> Unit,
    previewAt: Int?, setPreviewAt: (Int?) -> Unit,
) {
    // read through state, and no `::` references (memoized without their captures): a plain parameter went stale and a
    // swatch tap wrote its colour onto the first-seen edit (11-15: 강조 색 wiped 카드 진하기)
    val cur by androidx.compose.runtime.rememberUpdatedState(edit)
    val commitNow by androidx.compose.runtime.rememberUpdatedState(commit)
    val draftNow by androidx.compose.runtime.rememberUpdatedState(setDraft)
    val f = c.field
    // a colour or slider being held: the latest draft, written once on release
    var held by remember { mutableStateOf<ThemeEdit?>(null) }
    val hold: (ThemeEdit) -> Unit = { e -> held = e; draftNow(e) }
    val release: () -> Unit = { held?.let(commitNow); held = null }
    val reset = { commit(ThemeEditor.reset(f, cur, tk)) }
    val pad = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    when (c) {
        is Control.Colour -> Column(pad) {
            val label = if (f == EditField.BG && ThemeEditor.kindOf(drawn) == BgKind.GLOW) "바탕 색" else c.label
            EditTitle(label, ThemeEditor.isSet(f, cur), reset)
            val v = ThemeEditor.colour(f, cur) ?: ThemeEditor.token(f, drawn)
            val page = f == EditField.BG || f == EditField.GRAD_TOP || f == EditField.GRAD_MID || f == EditField.GRAD_BOTTOM
            val pick: (Long) -> Unit = { x -> hold(ThemeEditor.withColour(f, cur, if (theme in ThemeEdit.LIGHT && page) Hsl.floor(x, ThemeEdit.LIGHT_FLOOR) else x, tk)) }
            ColorPalette(v, pick, c.swatches, onChangeFinished = release)
        }
        is Control.Slider -> {
            // 유리 caps the cards at 55 %: the slider offers what is drawn and reads as % of that cap
            val glass = f == EditField.PLATE_ALPHA && drawn.plateStyle == PlateStyle.GLASS
            val range = if (glass) c.range.start..ThemeTokens.GLASS_MAX else c.range
            val v = ThemeEditor.slider(f, cur, drawn).coerceIn(range)
            val shown = when {
                f == EditField.TEXT_GLOW && v <= 0f -> "끔"
                else -> pct(v / range.endInclusive)
            }
            SliderRow(
                c.label, shown, v, range, { x -> hold(ThemeEditor.withSlider(f, cur, x)) },
                note = if (glass) "유리는 뒤가 비쳐 보이도록 55%까지만 진해져요." else null,
                onChangeFinished = release, trailing = { ResetChip(ThemeEditor.isSet(f, cur), reset) },
            )
        }
        is Control.Choice -> Column(pad) {
            EditTitle(c.label, ThemeEditor.isSet(f, cur), reset)
            // Pretendard is the only card font with weights (plan-r13 A3)
            val single = f == EditField.TEXT_WEIGHT && drawn.cardFont != CardFont.GOTHIC
            val sel = ThemeEditor.choice(f, drawn)
            if (c.options.size > 3 && f == EditField.TEXT_FONT) ChipFlow(c.options, sel, { i -> commit(ThemeEditor.withChoice(f, cur, i)) })
            else Segmented(c.options, sel, { i -> commit(ThemeEditor.withChoice(f, cur, i)) }, enabled = !single)
            if (single) T("이 글꼴은 두께가 하나예요", 13.sp, 400, SK.why, Modifier.padding(top = 8.dp))
        }
        is Control.Grain -> Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                EditTitle(c.label, ThemeEditor.isSet(f, cur), reset)
                ChipFlow(ThemeEditor.TEXTURE_KO, ThemeEditor.choice(f, drawn), { i -> commit(ThemeEditor.withChoice(f, cur, i)) })
            }
            val on = (if (f == EditField.BG_TEXTURE) drawn.bgTexture else drawn.cardTexture) != Texture.NONE
            if (on) {
                val v = ThemeEditor.textureAmt(f, cur, drawn)
                SliderRow(
                    "질감 정도", pct(v), v, ThemeEdit.TEXTURE_AMT, { x -> hold(cur.copy(textureAmt = x)) },
                    onChangeFinished = release, trailing = { ResetChip(cur.textureAmt != null) { commit(cur.copy(textureAmt = null)) } },
                )
            }
        }
        is Control.GlowPad -> Column(pad) {
            EditTitle(c.label, ThemeEditor.isSet(f, cur), reset)
            val sunset = ThemeEditor.choice(EditField.GLOW_SHAPE, drawn) == GlowShape.SUNSET.ordinal
            val at = cur.glowAt ?: drawn.bg.glowAt.let { it.x to it.y }
            GlowPad(at, enabled = !sunset, glow = Color(ThemeEditor.colour(EditField.GLOW, cur) ?: ThemeEditor.token(EditField.GLOW, drawn)),
                onMove = { p -> hold(cur.copy(glowAt = p)) }, onEnd = release)
            if (sunset) T("노을은 아래에서 올라와서 위치가 정해져 있어요", 13.sp, 400, SK.why, Modifier.padding(top = 8.dp))
        }
        // the stop rows (ColorRow) carry their own 16 dp, so only the text rows are padded here
        is Control.Flow -> Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            val on = cur.flow != null
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                T(c.label, 16.sp, 500, SK.text, Modifier.weight(1f))
                androidx.compose.material3.Switch(on, { v -> commit(cur.copy(flow = if (v) ThemeEditor.defaultFlow(drawn) else null)); if (!v) setPreviewAt(null) })
            }
            T(if (drawn.bg.glow != null) "불빛 색이 아침 → 낮 → 저녁으로 천천히 바뀌어요. 밤에는 밤 화면이에요." else "배경 위쪽 색이 아침 → 낮 → 저녁으로 천천히 바뀌어요. 밤에는 밤 화면이에요.",
                13.sp, 400, SK.muted, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), lineHeight = 18.sp)
            val flow = cur.flow
            if (flow != null) {
                flow.forEachIndexed { i, col ->
                    ColorRow(FLOW_KO.getOrElse(i) { "저녁" }, col, onChangeFinished = release) { x -> hold(cur.copy(flow = flow.toMutableList().also { it[i] = x })) }
                }
                T("미리보기", 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
                Box(Modifier.padding(horizontal = 16.dp)) { ChipFlow(FLOW_KO, FLOW_AT.indexOf(previewAt), { i -> setPreviewAt(FLOW_AT[i]) }) }
            }
        }
    }
}

/** A saveable key for [Look] ([lookOf] reads it back; a 내 테마 by name). */
private fun Look.key(): String = when (this) { is Look.Builtin -> "B:${theme.name}"; is Look.Mine -> "M:${my.name}" }

private fun lookOf(k: String, c: AppConfig): Look? = when {
    k.startsWith("B:") -> runCatching { Look.Builtin(Theme.valueOf(k.drop(2))) }.getOrNull()
    else -> c.myThemes.firstOrNull { it.name == k.drop(2) }?.let { Look.Mine(it) }
}

/** 불빛 위치: a square standing for the page; a tap or a drag moves the dot, the config is written when the finger lifts. */
@Composable
private fun GlowPad(at: Pair<Float, Float>, enabled: Boolean, glow: Color, onMove: (Pair<Float, Float>) -> Unit, onEnd: () -> Unit) {
    // the gesture detectors live across recompositions: always call the latest callbacks (they carry the latest edit)
    val move by androidx.compose.runtime.rememberUpdatedState(onMove)
    val end by androidx.compose.runtime.rememberUpdatedState(onEnd)
    val alpha = if (enabled) 1f else 0.35f
    Canvas(
        Modifier.size(width = 200.dp, height = 120.dp).clip(RoundedCornerShape(12.dp)).background(SK.surface2).border(1.dp, SK.line, RoundedCornerShape(12.dp))
            .then(
                if (!enabled) Modifier else Modifier
                    .pointerInput(Unit) { detectTapGestures { o -> move((o.x / size.width).coerceIn(0f, 1f) to (o.y / size.height).coerceIn(0f, 1f)); end() } }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragEnd = { end() }, onDragCancel = { end() }) { ch, _ -> move((ch.position.x / size.width).coerceIn(0f, 1f) to (ch.position.y / size.height).coerceIn(0f, 1f)) }
                    },
            ),
    ) {
        val p = Offset(at.first * size.width, at.second.coerceIn(0f, 1f) * size.height)
        drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(glow.copy(alpha = 0.7f * alpha), glow.copy(alpha = 0f)), p, size.minDimension * 0.6f), size.minDimension * 0.6f, p)
        drawCircle(SK.text.copy(alpha = alpha), 9.dp.toPx(), p, style = Stroke(2.dp.toPx()))
    }
}

/**
 * The unsaved-edit guard (plan-r13 A1) before switching to [to]: [저장] names the edit and keeps it as a 내 테마 first,
 * [버리기] switches, [취소] stays. [onSwitch] runs right before either switch (the custom screen keeps its 이전 조합).
 */
@Composable
internal fun UnsavedGuard(config: AppConfig, update: Update, to: Look, done: () -> Unit, onSwitch: () -> Unit = {}) {
    var naming by rememberSaveable { mutableStateOf<String?>(null) }
    val n = naming
    if (n != null) {
        NameDialog("내 테마로 저장", n, done, taken = config.myThemes) { name -> onSwitch(); update { it.saveMyTheme(name).switchLook(to) }; done() }
        return
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = done,
        containerColor = SK.surface,
        title = { T("저장 안 한 꾸미기가 사라져요", 18.sp, 700) },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("저장", { naming = config.customFrom ?: nextMyThemeName(config.myThemes) }, padH = 14.dp)
                ActionButton("버리기", { onSwitch(); update { it.switchLook(to) }; done() }, primary = false, padH = 14.dp)
                ActionButton("취소", done, primary = false, padH = 14.dp)
            }
        },
    )
}

/**
 * A name for a 내 테마 (save or rename): a field and [저장] (or [덮어쓰기] when [taken] has the name) / [취소].
 * [unique] (rename): a name in [taken] is refused instead, with a short reason.
 */
@Composable
internal fun NameDialog(title: String, initial: String, dismiss: () -> Unit, taken: List<MyTheme> = emptyList(), unique: Boolean = false, done: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val over = taken.any { it.name == name.trim() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = dismiss,
        containerColor = SK.surface,
        title = { T(title, 18.sp, 700) },
        text = {
            Column {
                Field(name, { name = it.take(20) }, "이름")
                if (unique && over) T("같은 이름이 있어요", 13.sp, 400, SK.why, Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = {
            ActionButton(if (over && !unique) "덮어쓰기" else "저장", { name.trim().takeIf { it.isNotEmpty() }?.let(done) }, enabled = name.isNotBlank() && !(unique && over))
        },
        dismissButton = { ActionButton("취소", dismiss, primary = false) },
    )
}

/** A label under a thumbnail in the theme strip (used by [ThemePicker] for 내 테마). */
@Composable
internal fun StripLabel(text: String, on: Boolean, acc: Color) =
    T(text, 13.sp, if (on) 700 else 500, if (on) acc else SK.text, Modifier.fillMaxWidth().padding(top = 6.dp), maxLines = 1, align = TextAlign.Center)
