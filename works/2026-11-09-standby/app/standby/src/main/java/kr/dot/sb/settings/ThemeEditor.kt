package kr.dot.sb.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.GlowShape
import kr.dot.sb.model.MyTheme
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.ui.clock.ColorShuffle
import kr.dot.sb.ui.theme.Contrast
import kr.dot.sb.ui.theme.Hsl
import kr.dot.sb.ui.theme.PhotoPalette
import kr.dot.sb.ui.theme.PlateStyle
import kr.dot.sb.ui.theme.ThemeTokens
import kr.dot.sb.ui.theme.Themes
import kr.dot.sb.ui.theme.withBoardBg
import kr.dot.sb.ui.theme.nextMyThemeName
import kr.dot.sb.ui.theme.saveMyTheme
import kr.dot.sb.ui.theme.withTheme
import kr.dot.sb.ui.theme.withThemeEdit
import kotlin.random.Random

/*
 * r11 theme editor (spec-theme-editor.md, plan-r11 A5): under the theme strip of 테마와 배경. Top row [색 조합 바꾸기]
 * [이전 조합] [테마 기본으로], then only the controls the theme has ([ThemeEditor.controls]). Every write goes through
 * AppConfig.withThemeEdit; colours and sliders preview a screen-local draft and write once on release.
 */

/** One field of [ThemeEdit] a control writes (하늘's three stops are three fields of `bg`). */
enum class EditField { BG, SKY_TOP, SKY_MID, SKY_BOTTOM, GLOW, GLOW_STRENGTH, GLOW_AT, GLOW_SHAPE, PLATE, RULE, PLATE_ALPHA, PLATE_STYLE, RADIUS, ACCENT, INK, FLOW }

/** A row of the editor. [label] is what the row says; [field] what it writes. */
sealed class Control(val field: EditField, val label: String) {
    /** Swatches + 직접 고르기; [names] = named swatches (터미널 형광 색). */
    class Colour(field: EditField, label: String, val swatches: List<Long>, val names: List<String>? = null) : Control(field, label)
    class Slider(field: EditField, label: String, val range: ClosedFloatingPointRange<Float>) : Control(field, label)
    class Shape : Control(EditField.GLOW_SHAPE, "모양")
    class CardStyle(val offer: List<PlateStyle>) : Control(EditField.PLATE_STYLE, "카드 모양")
    class Flow : Control(EditField.FLOW, "시간 따라 흐름")
    /** 위치: a small square pad (plan round 3), off with 노을. */
    class GlowPad : Control(EditField.GLOW_AT, "불빛 위치")
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
    val TERMINAL_INKS = listOf("초록" to 0xFF59FF7A, "호박" to 0xFFFFB000, "흰색" to 0xFFE8E8E8)

    internal fun Color.long(): Long = toArgb().toLong() and 0xFFFFFFFFL
    private fun opaque(c: Color) = c.copy(alpha = 1f).long()

    /** The theme's own colour first, then [rest] (a row with no edit shows the theme's colour selected). */
    private fun with(own: Long, rest: List<Long>) = (listOf(own) + rest).distinct()

    /** Every row of [theme]'s editor (plan-r11 A5 table). */
    fun controls(theme: Theme): List<Control> {
        val tk = Themes.of(theme).tokens
        val sw = ClockStyle.SWATCHES
        val page = with(tk.bg.base.long(), if (tk.light) LIGHT_BG else BG_SWATCHES)
        // cards: deep (or, on a light page, pale) tones of the swatches, so the ink keeps reading on them
        val plates = with(opaque(tk.plate), sw.map { ColorShuffle.tone(it, if (tk.light) 0.86f else 0.2f) }.distinct())
        val inks = with(tk.ink.long(), if (tk.light) sw.map { ColorShuffle.tone(it, 0.16f) }.distinct() else sw)
        fun colour(f: EditField, l: String, s: List<Long>) = Control.Colour(f, l, s)
        fun accent(l: String) = colour(EditField.ACCENT, l, with(tk.accent.long(), sw))
        fun ink(l: String) = colour(EditField.INK, l, inks)
        fun bg(l: String) = colour(EditField.BG, l, page)
        fun alpha(l: String) = Control.Slider(EditField.PLATE_ALPHA, l, ThemeEdit.PLATE_ALPHA)
        val radius = Control.Slider(EditField.RADIUS, "모서리", 0f..0.2f)
        val glow = tk.bg.glow?.let { opaque(it) }
        return when (theme) {
            Theme.IPHONE -> listOf(bg("배경 색"), colour(EditField.PLATE, "카드 색", plates), alpha("카드 진하기"), radius, accent("강조 색"), ink("글자 색"))
            Theme.PAPER -> listOf(bg("종이 색"), ink("잉크 색"), colour(EditField.RULE, "구분선 색", with(tk.ink.long(), sw)), accent("강조 색"))
            Theme.MOODLIGHT -> listOf(
                bg("배경"), colour(EditField.GLOW, "불빛 색", with(glow!!, sw)), Control.Slider(EditField.GLOW_STRENGTH, "세기", 0f..1f),
                Control.GlowPad(), Control.Shape(), Control.Flow(), Control.CardStyle(listOf(PlateStyle.FILL, PlateStyle.GLASS, PlateStyle.OUTLINE)),
                alpha("진하기"), radius, accent("강조"), ink("글자"),
            )
            Theme.CANDLE -> listOf(
                bg("배경"), colour(EditField.GLOW, "불꽃 빛 색", with(glow!!, sw)), Control.Slider(EditField.GLOW_STRENGTH, "세기", 0f..1f),
                Control.Flow(), alpha("카드 진하기"), accent("강조"), ink("글자"),
            )
            Theme.FLIP -> listOf(bg("배경"), colour(EditField.PLATE, "플레이트 색", plates), accent("숫자 색"), ink("글자 색"), radius)
            Theme.SKY -> listOf(
                colour(EditField.SKY_TOP, "하늘 위", with(tk.bg.stops[0].long(), BG_SWATCHES)),
                colour(EditField.SKY_MID, "하늘 가운데", with(tk.bg.stops[1].long(), BG_SWATCHES)),
                colour(EditField.SKY_BOTTOM, "하늘 아래", with(tk.bg.stops[2].long(), BG_SWATCHES)),
                Control.Flow(), alpha("카드 유리 진하기"), accent("강조"), ink("글자"),
            )
            Theme.DAYPAPER -> listOf(bg("종이 색"), ink("잉크 색"), colour(EditField.PLATE, "카드 색", plates), accent("강조"))
            Theme.RADIO -> listOf(bg("본체 색"), colour(EditField.PLATE, "판 색", plates), accent("다이얼 색"), ink("글자"))
            Theme.NEON -> listOf(colour(EditField.INK, "네온관 색", with(tk.ink.long(), sw)), accent("두 번째 네온"), Control.Slider(EditField.GLOW_STRENGTH, "빛 번짐 세기", 0f..1f))
            Theme.TERMINAL -> listOf(
                Control.Colour(EditField.INK, "형광 색", TERMINAL_INKS.map { it.second }, TERMINAL_INKS.map { it.first }), accent("강조"),
            )
        }
    }

    private fun skyIndex(f: EditField) = when (f) { EditField.SKY_TOP -> 0; EditField.SKY_MID -> 1; EditField.SKY_BOTTOM -> 2; else -> -1 }

    /** [f]'s colour in [e] (null = not edited). */
    fun colour(f: EditField, e: ThemeEdit): Long? = when (f) {
        EditField.BG -> e.bg?.firstOrNull()
        EditField.SKY_TOP, EditField.SKY_MID, EditField.SKY_BOTTOM -> e.bg?.getOrNull(skyIndex(f))
        EditField.GLOW -> e.glow
        EditField.PLATE -> e.plate
        EditField.RULE -> e.rule
        EditField.ACCENT -> e.accent
        EditField.INK -> e.ink
        else -> null
    }

    /** The theme's own colour for [f] (what an unedited row shows). */
    fun token(f: EditField, tk: ThemeTokens): Long = when (f) {
        EditField.BG -> tk.bg.base.long()
        EditField.SKY_TOP, EditField.SKY_MID, EditField.SKY_BOTTOM -> tk.bg.stops.getOrElse(skyIndex(f)) { tk.bg.base }.long()
        EditField.GLOW -> opaque(tk.bg.glow ?: tk.accent)
        EditField.PLATE -> opaque(tk.plate)
        EditField.RULE -> tk.ink.long()
        EditField.ACCENT -> tk.accent.long()
        else -> tk.ink.long()
    }

    /** [e] with [f] set to [v] (null = back to the theme's). 하늘's stops fill the others from the theme. */
    fun withColour(f: EditField, e: ThemeEdit, v: Long?, tk: ThemeTokens): ThemeEdit = when (f) {
        EditField.BG -> e.copy(bg = v?.let { listOf(it) })
        EditField.SKY_TOP, EditField.SKY_MID, EditField.SKY_BOTTOM -> {
            val own = tk.bg.stops.map { it.long() }
            val l = (e.bg?.takeIf { it.size == 3 } ?: own).toMutableList().also { it[skyIndex(f)] = v ?: own[skyIndex(f)] }
            e.copy(bg = l.takeIf { it != own })
        }
        EditField.GLOW -> e.copy(glow = v)
        EditField.PLATE -> e.copy(plate = v)
        EditField.RULE -> e.copy(rule = v)
        EditField.ACCENT -> e.copy(accent = v)
        EditField.INK -> e.copy(ink = v)
        else -> e
    }

    /** A slider's value in [e], else the theme's. */
    fun slider(f: EditField, e: ThemeEdit, tk: ThemeTokens): Float = when (f) {
        EditField.GLOW_STRENGTH -> e.glowStrength ?: 0.5f
        EditField.PLATE_ALPHA -> e.plateAlpha ?: tk.plate.alpha.coerceIn(ThemeEdit.PLATE_ALPHA)
        else -> e.radius ?: tk.radiusRatio
    }

    fun withSlider(f: EditField, e: ThemeEdit, v: Float?): ThemeEdit = when (f) {
        EditField.GLOW_STRENGTH -> e.copy(glowStrength = v)
        EditField.PLATE_ALPHA -> e.copy(plateAlpha = v)
        else -> e.copy(radius = v)
    }

    /** [f] cleared (the row's 기본). */
    fun reset(f: EditField, e: ThemeEdit, tk: ThemeTokens): ThemeEdit = when (f) {
        EditField.GLOW_STRENGTH, EditField.PLATE_ALPHA, EditField.RADIUS -> withSlider(f, e, null)
        EditField.GLOW_AT -> e.copy(glowAt = null)
        EditField.GLOW_SHAPE -> e.copy(glowShape = null)
        EditField.PLATE_STYLE -> e.copy(plateStyle = null)
        EditField.FLOW -> e.copy(flow = null)
        else -> withColour(f, e, null, tk)
    }

    /** True when [f] is set in [e] (the row's 기본 is enabled). */
    fun isSet(f: EditField, e: ThemeEdit): Boolean = when (f) {
        EditField.GLOW_STRENGTH -> e.glowStrength != null
        EditField.PLATE_ALPHA -> e.plateAlpha != null
        EditField.RADIUS -> e.radius != null
        EditField.GLOW_AT -> e.glowAt != null
        EditField.GLOW_SHAPE -> e.glowShape != null
        EditField.PLATE_STYLE -> e.plateStyle != null
        EditField.FLOW -> e.flow != null
        else -> colour(f, e) != null
    }

    /** 시간 따라 흐름 switched on: 아침 warm, 낮 = the theme's glow / sky top, 저녁 lilac (dusk blue on 하늘). */
    fun defaultFlow(theme: Theme): List<Long> {
        val tk = Themes.of(theme).tokens
        return if (tk.bg.glow != null) listOf(0xFFFF9C56, opaque(tk.bg.glow!!), 0xFFAA88FE)
        else listOf(0xFF3A5A8C, tk.bg.base.long(), 0xFF5A3A6A)
    }

    /**
     * 사진에서 색 뽑기: [p] onto [cur] for [theme], only the fields the theme's editor has. Page = the dark muted colour
     * (light themes: the lightest), glow = the vivid one, cards = the vivid hue at the theme's card lightness, accent = the
     * light vivid one; then ink and accent are moved until they read (manual-pick floors: 4.5 / 3).
     */
    fun fromPhoto(theme: Theme, cur: ThemeEdit, p: PhotoPalette.Picked): ThemeEdit {
        val tk = Themes.of(theme).tokens
        val fields = controls(theme).map { it.field }.toSet()
        val vivid = p.vivid
        var e = cur
        if (EditField.BG in fields) e = e.copy(bg = listOf(if (tk.light) p.lightest else p.bg))
        if (EditField.SKY_TOP in fields) e = e.copy(bg = listOf(p.bg) + listOf(0.2f, 0.28f).map { ColorShuffle.tone(vivid ?: p.bg, it) })
        if (vivid != null) {
            if (EditField.GLOW in fields) e = e.copy(glow = vivid)
            if (EditField.PLATE in fields) e = e.copy(plate = ColorShuffle.tone(vivid, Hsl.toHsl(tk.plate)[2]))
            if (EditField.RULE in fields) e = e.copy(rule = vivid)
        }
        if (EditField.ACCENT in fields && p.accent != null) e = e.copy(accent = p.accent)
        val t = tk.with(e.sanitized(theme))
        if (EditField.INK in fields) Contrast.fit(t.ink, t.surfaces, tk.light, 4.5f).let { if (it != t.ink) e = e.copy(ink = it.long()) }
        if (EditField.ACCENT in fields) Contrast.fit(t.accent, t.pages.map { t.plate.compositeOver(it) }, tk.light).let { if (it != t.accent) e = e.copy(accent = it.long()) }
        return e.sanitized(theme)
    }
}

/**
 * 색 조합 바꾸기 for a theme (plan-r11 A5): pure. Draws only the theme's colour rows, hue-matched like the mockup (page,
 * glow and cards from one swatch's hue at the theme's lightness, accent from another), keeps every other field of
 * [current], and draws again (up to 30 times) until [Contrast.warnings] is empty; else returns [current]. Never draws
 * the flow.
 */
object ThemeShuffle {
    fun next(theme: Theme, base: ThemeTokens, current: ThemeEdit, rnd: Random): ThemeEdit {
        val fields = ThemeEditor.controls(theme).filterIsInstance<Control.Colour>().map { it.field }
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
        // NEON / TERMINAL: tubes of light on black, so their ink and accent are full swatches
        val tubes = base.plateStyle == PlateStyle.OUTLINE
        fun hsl(hue: Float, s: Float, l: Float) = with(ThemeEditor) { Color.hsl(hue, s, l).long() }
        var e = current
        for (f in fields) {
            val v = when (f) {
                EditField.BG -> if (light) hsl(h, 0.3f, 0.9f) else hsl(h, 0.35f, 0.06f)
                EditField.SKY_TOP -> hsl(h, 0.4f, 0.13f)
                EditField.SKY_MID -> hsl((h + 20f) % 360f, 0.35f, 0.2f)
                EditField.SKY_BOTTOM -> hsl(pop, 0.35f, 0.22f)
                EditField.GLOW -> hsl(h, 0.6f, 0.42f)
                EditField.PLATE -> if (light) hsl(h, 0.25f, 0.84f) else hsl(h, 0.25f, 0.14f)
                EditField.RULE -> hsl(pop, 0.5f, 0.6f)
                EditField.ACCENT -> if (tubes) ClockStyle.SWATCHES.random(rnd) else if (light) hsl(pop, 0.7f, 0.32f) else hsl(pop, 0.85f, 0.72f)
                EditField.INK -> when {
                    theme == Theme.TERMINAL -> ThemeEditor.TERMINAL_INKS.random(rnd).second
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

/** Korean for the editor's warnings (amber under the controls). */
private fun Contrast.Warning.ko(): String = when (this) {
    Contrast.Warning.INK -> "글자가 흐려요"
    Contrast.Warning.INK2 -> "작은 글자가 흐려요"
    Contrast.Warning.ACCENT -> "강조 색이 흐려요"
}

private val PLATE_KO = mapOf(PlateStyle.FILL to "판", PlateStyle.GLASS to "유리", PlateStyle.OUTLINE to "선")
private val FLOW_KO = listOf("아침", "낮", "저녁")
/** Preview chips 아침 · 낮 · 저녁 (plan round 3), minutes of the day. */
private val FLOW_AT = listOf(7 * 60, 13 * 60, 19 * 60)

/**
 * The editor for the current theme. [draft] / [setDraft] = the screen-local edit while a colour or slider is held (the
 * preview shows it); [previewAt] / [setPreviewAt] = the 아침/낮/저녁 chip (minute of day, null = now).
 */
@Composable
internal fun ThemeEditorSection(
    config: AppConfig, update: Update,
    draft: ThemeEdit?, setDraft: (ThemeEdit?) -> Unit,
    previewAt: Int?, setPreviewAt: (Int?) -> Unit,
) {
    val theme = config.theme
    if (config.boardBg != BoardBg.THEME) {
        Section("색 꾸미기") {
            item {
                ControlRow(null, note = "배경을 '테마 기본'으로 두면 편집한 색이 보여요") {
                    ActionButton("테마 기본으로 바꾸기", { update { it.withBoardBg(BoardBg.THEME) } }, primary = false)
                }
            }
        }
        return
    }
    val tk = Themes.of(theme).tokens
    val saved = config.themeEdits[theme]
    val cur = draft ?: saved ?: ThemeEdit()
    var undo by rememberSaveable(theme, stateSaver = UndoSlot.SAVER) { mutableStateOf(UndoSlot.NONE) }
    var presses by rememberSaveable(theme) { androidx.compose.runtime.mutableIntStateOf(0) }
    var naming by remember { mutableStateOf<String?>(null) }
    fun commit(e: ThemeEdit) = update { it.withThemeEdit(theme, e) }
    /** A whole-edit change [이전 조합] can take back. */
    fun commitUndoable(e: ThemeEdit?) { undo = UndoSlot(true, theme, saved, config.customFrom); update { it.withThemeEdit(theme, e) } }
    val ctx = LocalContext.current
    val io = LocalSettingsEnv.current.io
    val scope = rememberCoroutineScope()
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val px = withContext(io) { runCatching { PhotoPalette.decode(ctx, uri) }.getOrNull() } ?: return@launch
            commitUndoable(ThemeEditor.fromPhoto(theme, cur, PhotoPalette.pick(px)))
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 24.dp)) {
        T("색 꾸미기", 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, bottom = 8.dp))
        // the same order as the clock editor: [색 조합 바꾸기] [이전 조합] [테마 기본으로]
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("색 조합 바꾸기", {
                // toString, not hashCode: enum hashCodes change every process (ColorShuffle)
                val e = ThemeShuffle.next(theme, tk, cur, Random(cur.toString().hashCode() + presses++))
                if (e != cur) commitUndoable(e)
            }, Modifier.weight(1.3f), primary = false, padH = 8.dp)
            ActionButton("이전 조합", { val u = undo; undo = UndoSlot.NONE; if (u.has) update { it.withTheme(u.theme).withThemeEdit(u.theme, u.edit).copy(customFrom = u.customFrom) } }, Modifier.weight(1f), primary = false, enabled = undo.has, padH = 8.dp)
            ActionButton("테마 기본으로", { commitUndoable(null) }, Modifier.weight(1.2f), primary = false, enabled = saved != null, padH = 8.dp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("사진에서 색 뽑기", { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), primary = false)
            ActionButton("내 테마로 저장", { naming = config.customFrom ?: nextMyThemeName(config.myThemes) }, Modifier.weight(1f), primary = false, enabled = saved != null)
        }
        Section(null) {
            ThemeEditor.controls(theme).forEach { c ->
                item { ControlView(c, theme, tk, cur, setDraft, ::commit, previewAt, setPreviewAt) }
            }
        }
        val warn = Contrast.warnings(tk.with(cur), tk)
        if (warn.isNotEmpty()) T(warn.joinToString(" · ") { it.ko() }, 13.sp, 500, SK.why, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
    naming?.let { n ->
        NameDialog("내 테마로 저장", n, { naming = null }) { name ->
            naming = null
            update { it.saveMyTheme(name) }
        }
    }
}

/** A row's title with a small 기본 that clears just this field. */
@Composable
private fun EditTitle(label: String, set: Boolean, reset: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        T(label, 16.sp, 500, SK.text, Modifier.weight(1f))
        ResetChip(set, reset)
    }
}

@Composable
private fun ResetChip(set: Boolean, reset: () -> Unit) =
    Box(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(50)).clickable(enabled = set, onClick = reset).padding(horizontal = 12.dp), Alignment.Center) {
        T("기본", 13.sp, 600, if (set) SK.muted else SK.line)
    }

@Composable
private fun ControlView(
    c: Control, theme: Theme, tk: ThemeTokens, edit: ThemeEdit, setDraft: (ThemeEdit?) -> Unit, commit: (ThemeEdit) -> Unit,
    previewAt: Int?, setPreviewAt: (Int?) -> Unit,
) {
    // read through state: `::pick` / `::release` are memoized without their captures, so a plain parameter went stale
    // there and a swatch tap wrote its colour onto the first-seen edit (11-15: 강조 색 wiped 카드 진하기)
    val cur by androidx.compose.runtime.rememberUpdatedState(edit)
    val f = c.field
    // a colour or slider being held: the latest draft, written once on release
    var held by remember { mutableStateOf<ThemeEdit?>(null) }
    fun hold(e: ThemeEdit) { held = e; setDraft(e) }
    fun release() { held?.let(commit); held = null }
    val reset = { commit(ThemeEditor.reset(f, cur, tk)) }
    when (c) {
        is Control.Colour -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            EditTitle(c.label, ThemeEditor.isSet(f, cur), reset)
            val v = ThemeEditor.colour(f, cur) ?: ThemeEditor.token(f, tk)
            fun pick(x: Long) = hold(ThemeEditor.withColour(f, cur, if (theme in ThemeEdit.LIGHT && f == EditField.BG) Hsl.floor(x, ThemeEdit.LIGHT_FLOOR) else x, tk))
            if (c.names != null) NamedSwatches(c.names.zip(c.swatches), v, onChangeFinished = ::release, onChange = ::pick)
            else ColorPalette(v, ::pick, c.swatches, onChangeFinished = ::release)
        }
        is Control.Slider -> {
            // 유리 caps the cards at 55 %: the slider shows (and offers) what is drawn
            val glass = f == EditField.PLATE_ALPHA && cur.plateStyle == PlateStyle.GLASS
            val range = if (glass) c.range.start..ThemeTokens.GLASS_MAX else c.range
            val v = ThemeEditor.slider(f, cur, tk).coerceIn(range)
            SliderRow(
                c.label, "${(v / c.range.endInclusive * 100).toInt()}%", v, range, { x -> hold(ThemeEditor.withSlider(f, cur, x)) },
                onChangeFinished = ::release, trailing = { ResetChip(ThemeEditor.isSet(f, cur), reset) },
            )
        }
        is Control.Shape -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            EditTitle(c.label, ThemeEditor.isSet(f, cur), reset)
            val s = cur.glowShape ?: GlowShape.ROUND
            ChipFlow(GlowShape.entries.map { it.ko }, s.ordinal, { i -> commit(cur.copy(glowShape = GlowShape.entries[i])) })
        }
        is Control.CardStyle -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            EditTitle(c.label, ThemeEditor.isSet(f, cur), reset)
            val s = cur.plateStyle ?: tk.plateStyle
            ChipFlow(c.offer.map { PLATE_KO.getValue(it) }, c.offer.indexOf(s), { i -> commit(cur.copy(plateStyle = c.offer[i])) })
        }
        is Control.GlowPad -> Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            EditTitle(c.label, ThemeEditor.isSet(f, cur), reset)
            val sunset = cur.glowShape == GlowShape.SUNSET
            val at = cur.glowAt ?: tk.bg.glowAt.let { it.x to it.y }
            GlowPad(at, enabled = !sunset, glow = Color(ThemeEditor.colour(EditField.GLOW, cur) ?: ThemeEditor.token(EditField.GLOW, tk)),
                onMove = { p -> hold(cur.copy(glowAt = p)) }, onEnd = ::release)
            if (sunset) T("노을은 아래에서 올라와서 위치가 정해져 있어요", 13.sp, 400, SK.why, Modifier.padding(top = 8.dp))
        }
        // the stop rows (ColorRow) carry their own 16 dp, so only the text rows are padded here
        is Control.Flow -> Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            val on = cur.flow != null
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                T(c.label, 16.sp, 500, SK.text, Modifier.weight(1f))
                androidx.compose.material3.Switch(on, { v -> commit(cur.copy(flow = if (v) ThemeEditor.defaultFlow(theme) else null)); if (!v) setPreviewAt(null) })
            }
            T(if (tk.bg.glow != null) "불빛 색이 아침 → 낮 → 저녁으로 천천히 바뀌어요. 밤에는 밤 화면이에요." else "하늘 위쪽 색이 아침 → 낮 → 저녁으로 천천히 바뀌어요. 밤에는 밤 화면이에요.",
                13.sp, 400, SK.muted, Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp), lineHeight = 18.sp)
            val flow = cur.flow
            if (flow != null) {
                flow.forEachIndexed { i, col ->
                    ColorRow(FLOW_KO.getOrElse(i) { "저녁" }, col, onChangeFinished = ::release) { x -> hold(cur.copy(flow = flow.toMutableList().also { it[i] = x })) }
                }
                T("미리보기", 13.sp, 600, SK.muted, Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp))
                Box(Modifier.padding(horizontal = 16.dp)) { ChipFlow(FLOW_KO, FLOW_AT.indexOf(previewAt), { i -> setPreviewAt(FLOW_AT[i]) }) }
            }
        }
    }
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

/** A name for a 내 테마 (save or rename): a field and [저장] / [취소]. */
@Composable
internal fun NameDialog(title: String, initial: String, dismiss: () -> Unit, done: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = dismiss,
        containerColor = SK.surface,
        title = { T(title, 18.sp, 700) },
        text = { Field(name, { name = it.take(20) }, "이름") },
        confirmButton = { ActionButton("저장", { name.trim().takeIf { it.isNotEmpty() }?.let(done) }, enabled = name.isNotBlank()) },
        dismissButton = { ActionButton("취소", dismiss, primary = false) },
    )
}

/** A label under a thumbnail in the theme strip (used by [ThemePicker] for 내 테마). */
@Composable
internal fun StripLabel(text: String, on: Boolean, acc: Color) =
    T(text, 13.sp, if (on) 700 else 500, if (on) acc else SK.text, Modifier.fillMaxWidth().padding(top = 6.dp), maxLines = 1, align = TextAlign.Center)
