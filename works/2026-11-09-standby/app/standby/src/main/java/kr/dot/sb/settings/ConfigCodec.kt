package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.AppSource
import kr.dot.sb.model.BgKind
import kr.dot.sb.model.Border
import kr.dot.sb.model.Texture
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.City
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.DDay
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.GlowShape
import kr.dot.sb.model.Layout
import kr.dot.sb.model.MyTheme
import kr.dot.sb.model.Mood
import kr.dot.sb.model.IconStyle
import kr.dot.sb.model.Theme
import kr.dot.sb.model.ThemeEdit
import kr.dot.sb.ui.theme.PlateStyle
import kr.dot.sb.model.MOOD_AUTO_OFF_MIN
import kr.dot.sb.model.PHOTO_INTERVAL_SEC
import kr.dot.sb.model.PAGE_PHOTOS
import kr.dot.sb.model.PAGE_WIDGETS
import kr.dot.sb.model.MAX_APPS
import kr.dot.sb.model.SleepSound
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.ui.theme.Themes
import kr.dot.sb.ui.theme.nextMyThemeName
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Hand-written JSON codec for [AppConfig]. Enums go by name. Every read falls back to the default of that field,
 * so a save from an older or newer build (missing or unknown fields, renamed enum values) never crashes.
 */
object ConfigCodec {
    /**
     * Stored schema: 9 = r9 (styles are overrides only), 10 = r10, 11 = r13 (NEON/TERMINAL halo in `textGlow`, edits
     * kept only for the current look), 12 = r14 (FLOAT read as DIGITAL), 13 = r15, 14 = r15 B1 (photoFolders = SAF tree URIs), 15 = r15 E5/E8 (rotateClocks, mood), 16 = r15 E9 (입체 시계/입체 네모 date off unless switched on again). Saves without "v" are r8 or r9 dev
     * builds.
     */
    const val VERSION = 16

    fun encode(c: AppConfig): String = JSONObject().apply {
        put("v", VERSION)
        put("theme", c.theme.name)
        put("styles", JSONObject().also { o -> c.styles.forEach { (f, s) -> o.put(f.name, style(s)) } })   // overrides only
        put("boardBg", c.boardBg.name)
        put("boardBgColor", c.boardBgColor)
        put("analogStyle", c.analogStyle.name)
        put("boards", JSONArray().also { a -> c.boards.forEach { a.put(board(it)) } })
        put("startPage", c.startPage)
        put("use24h", c.use24h ?: JSONObject.NULL)
        put("nightMode", c.nightMode.name)
        put("nightStartHour", c.nightStartHour)
        put("nightEndHour", c.nightEndHour)
        put("nightEnterLux", c.nightEnterLux.toDouble())
        put("nightExitLux", c.nightExitLux.toDouble())
        put("nightMaxBrightness", c.nightMaxBrightness.toDouble())
        put("dayBrightness", c.dayBrightness?.toDouble() ?: JSONObject.NULL)
        put("startOnCharge", c.startOnCharge)
        put("chargeWirelessOnly", c.chargeWirelessOnly)
        put("startLandscapeOnly", c.startLandscapeOnly)
        put("displayOff", c.displayOff.name)
        put("motionToWake", c.motionToWake)
        put("showNotifications", c.showNotifications)
        put("previewOnTapOnly", c.previewOnTapOnly)
        put("burnInShift", c.burnInShift)
        put("widgetLeft", cards(c.widgetLeft))
        put("widgetRight", cards(c.widgetRight))
        put("widgetOptions", options(c.widgetOptions))
        put("alarmScreen", c.alarmScreen)
        put("alarmGlow", c.alarmGlow)
        put("glowColor", c.glowColor)
        put("glowStartMinBefore", c.glowStartMinBefore)
        put("glowRampSec", c.glowRampSec)
        put("onUnfold", c.onUnfold.name)
        put("ddays", JSONArray().also { a -> c.ddays.forEach { a.put(JSONObject().put("name", it.name).put("date", it.date.toString())) } })
        put("city", city(c.city))
        put("worldCities", JSONArray().also { a -> c.worldCities.forEach { a.put(city(it)) } })
        put("usageEnabled", c.usageEnabled)
        put("sleepSound", c.sleepSound.name)
        put("sleepMinutes", c.sleepMinutes)
        put("focusMinutes", c.focusMinutes)
        put("breakMinutes", c.breakMinutes)
        put("timerAlert", c.timerAlert.name)
        put("myClocks", JSONArray().also { a -> c.myClocks.forEach { a.put(it.name) } })
        put("rotateClocks", c.rotateClocks)
        put("mood", JSONObject().put("bright", c.mood.bright.toDouble()).put("color", c.mood.color).put("effect", c.mood.effect.name).put("speed", c.mood.speed.toDouble()))
        put("tentClock", c.tentClock)
        put("tipsSeen", JSONArray().also { a -> c.tipsSeen.forEach { a.put(it) } })
        put("excludedCalendars", JSONArray().also { a -> c.excludedCalendars.forEach { a.put(it) } })

        put("heatProtect", c.heatProtect)
        put("moodAutoOffMin", c.moodAutoOffMin)
        put("nightNotifQuiet", c.nightNotifQuiet)
        put("photoIntervalSec", c.photoIntervalSec)
        put("pagesOff", JSONArray().also { a -> c.pagesOff.forEach { a.put(it) } })
        c.photoFilter?.let { put("photoFilter", it) }
        put("photoFolders", JSONArray().also { a -> c.photoFolders.forEach { a.put(it) } })
        // r11: only when used, so a config without edits encodes exactly as before
        val edits = c.themeEdits.mapNotNull { (th, e) -> e.sanitized(th).takeUnless { it.isEmpty }?.let { th to it } }
        if (edits.isNotEmpty()) put("themeEdits", JSONObject().also { o -> edits.forEach { (th, e) -> o.put(th.name, edit(e)) } })
        if (c.myThemes.isNotEmpty()) put("myThemes", JSONArray().also { a ->
            c.myThemes.forEach { m -> a.put(JSONObject().put("name", m.name).put("base", m.base.name).put("edit", edit(m.edit.sanitized(m.base)))) }
        })
        c.customFrom?.let { put("customFrom", it) }
    }.toString()

    /** One [ThemeEdit] as JSON (the editor's undo slot is saved this way). */
    fun encodeEdit(e: ThemeEdit): String = edit(e).toString()

    /** [encodeEdit] back, sanitised for [theme]; anything unreadable is the empty edit. */
    fun decodeEdit(json: String, theme: Theme): ThemeEdit =
        runCatching { edit(JSONObject(json), theme) }.getOrNull() ?: ThemeEdit()

    private fun edit(e: ThemeEdit) = JSONObject().apply {
        fun longs(l: List<Long>) = JSONArray().also { a -> l.forEach { a.put(it) } }
        e.bg?.let { put("bg", longs(it)) }
        e.glow?.let { put("glow", it) }
        e.glowStrength?.let { put("glowStrength", it.toDouble()) }
        e.glowAt?.let { put("glowAt", JSONArray().put(it.first.toDouble()).put(it.second.toDouble())) }
        e.glowShape?.let { put("glowShape", it.name) }
        e.plate?.let { put("plate", it) }
        e.rule?.let { put("rule", it) }
        e.plateAlpha?.let { put("plateAlpha", it.toDouble()) }
        e.plateStyle?.let { put("plateStyle", it.name) }
        e.radius?.let { put("radius", it.toDouble()) }
        e.accent?.let { put("accent", it) }
        e.ink?.let { put("ink", it) }
        e.flow?.let { put("flow", longs(it)) }
        e.bgKind?.let { put("bgKind", it.name) }
        e.border?.let { put("border", it.name) }
        e.borderColor?.let { put("borderColor", it) }
        e.bgTexture?.let { put("bgTexture", it.name) }
        e.cardTexture?.let { put("cardTexture", it.name) }
        e.textureAmt?.let { put("textureAmt", it.toDouble()) }
        e.textGlow?.let { put("textGlow", it.toDouble()) }
    }

    /** A field that does not read is null (not the whole edit); the result is sanitised for [theme]. */
    private fun edit(o: JSONObject, theme: Theme): ThemeEdit {
        fun long(k: String): Long? = if (o.has(k) && !o.isNull(k)) runCatching { o.getLong(k) }.getOrNull() else null
        fun float(k: String): Float? = if (o.has(k)) runCatching { o.getDouble(k).toFloat() }.getOrNull() else null
        fun longs(k: String): List<Long>? = o.optJSONArray(k)?.let { a -> runCatching { (0 until a.length()).map { a.getLong(it) } }.getOrNull() }
        return ThemeEdit(
            bg = longs("bg"), glow = long("glow"), glowStrength = float("glowStrength"),
            glowAt = o.optJSONArray("glowAt")?.let { a -> runCatching { a.getDouble(0).toFloat() to a.getDouble(1).toFloat() }.getOrNull() },
            glowShape = enumOrNull<GlowShape>(o, "glowShape"),
            plate = long("plate"), rule = long("rule"), plateAlpha = float("plateAlpha"),
            plateStyle = enumOrNull<PlateStyle>(o, "plateStyle"),
            radius = float("radius"), accent = long("accent"), ink = long("ink"), flow = longs("flow"),
            bgKind = enumOrNull<BgKind>(o, "bgKind"), border = enumOrNull<Border>(o, "border"), borderColor = long("borderColor"),
            bgTexture = enumOrNull<Texture>(o, "bgTexture"), cardTexture = enumOrNull<Texture>(o, "cardTexture"), textureAmt = float("textureAmt"),
            textGlow = float("textGlow"),
        ).sanitized(theme)
    }

    /** A stored theme name, removed ones read as their replacement (r13 Part D, [Migrations.RENAMED]); unknown = null. */
    private fun themeOf(name: String): Theme? = Migrations.RENAMED[name] ?: runCatching { Theme.valueOf(name) }.getOrNull()

    /** An edit stored under theme name [name]: on CANDLE it is laid over [Migrations.CANDLE_LOOK] (its fields win). */
    private fun storedEdit(name: String, o: JSONObject?): ThemeEdit? {
        val th = themeOf(name) ?: return null
        if (name != "CANDLE") return o?.let { edit(it, th) }
        val m = edit(Migrations.CANDLE_LOOK)
        o?.keys()?.forEach { k -> m.put(k, o.get(k)) }
        return edit(m, th)
    }

    /**
     * Theme edits by (mapped) theme, and the edits that lost their key to another old theme now sharing it (PAPER and
     * DAYPAPER in a v10 save): the current theme's own entry [current] wins. A current CANDLE without an edit gets the
     * candle look as its edit.
     */
    private fun themeEdits(o: JSONObject?, current: String): Pair<Map<Theme, ThemeEdit>, List<Pair<Theme, ThemeEdit>>> {
        val names = o?.keys()?.asSequence()?.toList().orEmpty().let { if (current == "CANDLE" && "CANDLE" !in it) it + "CANDLE" else it }
        val read = names.mapNotNull { k ->
            val th = themeOf(k) ?: return@mapNotNull null
            storedEdit(k, o?.optJSONObject(k))?.takeUnless { it.isEmpty }?.let { k to (th to it) }
        }.sortedBy { it.first != current }.map { it.second }
        val kept = read.distinctBy { it.first }
        return kept.toMap() to (read - kept.toSet())
    }

    private fun myThemes(a: JSONArray?): List<MyTheme> = a?.let {
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val name = o.optString("base")
            val base = themeOf(name) ?: return@mapNotNull null
            MyTheme(o.optString("name").trim().ifEmpty { "내 테마" }, base, storedEdit(name, o.optJSONObject("edit")) ?: ThemeEdit())
        }
    } ?: emptyList()

    /** Any parse failure returns defaults; a bad field returns that field's default. */
    fun decode(json: String?): AppConfig {
        if (json.isNullOrBlank()) return AppConfig()
        return decodeOrNull(json) ?: AppConfig()
    }

    /** plan-r15 C8: null when [json] does not parse (ConfigStore then tries its backup before the defaults). */
    fun decodeOrNull(json: String): AppConfig? = try { fromJson(JSONObject(json)) } catch (e: Exception) { null }

    private fun fromJson(o: JSONObject): AppConfig {
        val d = AppConfig()
        val storedTheme = o.optString("theme")
        val theme = themeOf(storedTheme) ?: d.theme
        val v = o.optInt("v")
        val (edits, bumped) = themeEdits(o.optJSONObject("themeEdits"), storedTheme)
        // r8 = before the r9 schema; stays on 9 whatever VERSION becomes (a v9 edit equal to the r8 look is an edit)
        val (read, migratedAnalog) = Migrations.styles(o.optJSONObject("styles"), theme, r8 = v < 9) { so, base -> style(so, base) }
        // r10 dropped the width slider: every face draws at its default width
        val styles = read.mapValues { (f, s) -> s.copy(widthPct = Themes.of(theme).clockDefaults(f).widthPct) }
        val left = stack(o.optJSONArray("widgetLeft"), d.widgetLeft)
        val right = stack(o.optJSONArray("widgetRight"), d.widgetRight)
        return AppConfig(
            theme = theme,
            styles = styles,
            boardBg = enumOf(o, "boardBg", d.boardBg),
            boardBgColor = o.optLong("boardBgColor", d.boardBgColor),
            analogStyle = if (o.has("analogStyle")) enumOf(o, "analogStyle", d.analogStyle) else migratedAnalog ?: d.analogStyle,
            boards = o.optJSONArray("boards")?.let { a -> (0 until a.length()).mapNotNull { runCatching { board(a.getJSONObject(it)) }.getOrNull() } } ?: d.boards,
            startPage = o.optInt("startPage", d.startPage),
            use24h = if (o.has("use24h") && !o.isNull("use24h")) o.optBoolean("use24h") else null,
            nightMode = enumOf(o, "nightMode", d.nightMode),
            nightStartHour = o.optInt("nightStartHour", d.nightStartHour).coerceIn(0, 23),
            nightEndHour = o.optInt("nightEndHour", d.nightEndHour).coerceIn(0, 23),
            nightEnterLux = num(o, "nightEnterLux", d.nightEnterLux),
            nightExitLux = num(o, "nightExitLux", d.nightExitLux),
            nightMaxBrightness = num(o, "nightMaxBrightness", d.nightMaxBrightness),
            dayBrightness = if (o.has("dayBrightness") && !o.isNull("dayBrightness")) num(o, "dayBrightness", 0.5f).coerceIn(0f, 1f) else null,
            startOnCharge = o.optBoolean("startOnCharge", d.startOnCharge),
            chargeWirelessOnly = o.optBoolean("chargeWirelessOnly", d.chargeWirelessOnly),
            startLandscapeOnly = o.optBoolean("startLandscapeOnly", d.startLandscapeOnly),
            displayOff = enumOf(o, "displayOff", d.displayOff),
            motionToWake = o.optBoolean("motionToWake", d.motionToWake),
            showNotifications = o.optBoolean("showNotifications", d.showNotifications),
            previewOnTapOnly = o.optBoolean("previewOnTapOnly", d.previewOnTapOnly),
            burnInShift = o.optBoolean("burnInShift", d.burnInShift),
            widgetLeft = left.first,
            widgetRight = right.first,
            widgetOptions = when {
                o.has("widgetOptions") -> options(o.optJSONObject("widgetOptions"))
                // a side that was not stored keeps its default faces
                o.has("widgetLeft") || o.has("widgetRight") -> withFaces(d.widgetOptions.filterKeys { k -> !o.has(if (k.startsWith("0:")) "widgetLeft" else "widgetRight") }, listOf(left, right))
                else -> d.widgetOptions
            },
            alarmScreen = o.optBoolean("alarmScreen", d.alarmScreen),
            alarmGlow = o.optBoolean("alarmGlow", d.alarmGlow),
            glowColor = o.optLong("glowColor", d.glowColor),
            glowStartMinBefore = o.optInt("glowStartMinBefore", d.glowStartMinBefore),
            glowRampSec = o.optInt("glowRampSec", d.glowRampSec),
            onUnfold = enumOf(o, "onUnfold", d.onUnfold),
            ddays = o.optJSONArray("ddays")?.let { a ->
                (0 until a.length()).mapNotNull {
                    runCatching { a.getJSONObject(it).let { x -> DDay(x.getString("name"), LocalDate.parse(x.getString("date"))) } }.getOrNull()
                }
            }?.let { Migrations.ddays(it, v) } ?: d.ddays,
            city = o.optJSONObject("city")?.let { runCatching { city(it) }.getOrNull() } ?: d.city,
            worldCities = o.optJSONArray("worldCities")?.let { a -> (0 until a.length()).mapNotNull { runCatching { city(a.getJSONObject(it)) }.getOrNull() } } ?: d.worldCities,
            usageEnabled = o.optBoolean("usageEnabled", d.usageEnabled),
            sleepSound = if (o.optString("sleepSound") in OLD_NOISE) SleepSound.WHITE else enumOf(o, "sleepSound", d.sleepSound),
            sleepMinutes = o.optInt("sleepMinutes", d.sleepMinutes),
            focusMinutes = o.optInt("focusMinutes", d.focusMinutes),
            breakMinutes = o.optInt("breakMinutes", d.breakMinutes),
            timerAlert = enumOf(o, "timerAlert", d.timerAlert),
            myClocks = Migrations.myClocks(o.optJSONArray("myClocks"), d.myClocks),
            rotateClocks = o.optBoolean("rotateClocks", d.rotateClocks),
            mood = o.optJSONObject("mood")?.let { m ->
                Mood(
                    bright = num(m, "bright", d.mood.bright).coerceIn(0f, 1f), color = m.optLong("color", d.mood.color),
                    effect = enumOf(m, "effect", d.mood.effect), speed = num(m, "speed", d.mood.speed).coerceIn(0f, 1f),
                )
            } ?: d.mood.copy(color = o.optLong("glowColor", d.mood.color)), // before v15 the mood light used the glow colour
            tentClock = o.optBoolean("tentClock", d.tentClock),
            tipsSeen = o.optJSONArray("tipsSeen")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.toSet() } ?: d.tipsSeen,
            excludedCalendars = o.optJSONArray("excludedCalendars")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.toSet() } ?: d.excludedCalendars,
            heatProtect = o.optBoolean("heatProtect", d.heatProtect),
            moodAutoOffMin = o.optInt("moodAutoOffMin", d.moodAutoOffMin).takeIf { it in MOOD_AUTO_OFF_MIN } ?: d.moodAutoOffMin,
            nightNotifQuiet = o.optBoolean("nightNotifQuiet", d.nightNotifQuiet),
            photoIntervalSec = o.optInt("photoIntervalSec", d.photoIntervalSec).takeIf { it in PHOTO_INTERVAL_SEC } ?: d.photoIntervalSec,
            pagesOff = strings(o, "pagesOff")?.filter { it == PAGE_WIDGETS || it == PAGE_PHOTOS }?.toSet() ?: d.pagesOff,
            // plan-r15 B1: before v14 these were MediaStore album names (and photoSource PICKS/RECENT): none is a SAF grant
            photoFolders = if (v < 14) d.photoFolders else strings(o, "photoFolders")?.distinct() ?: d.photoFolders,
            themeEdits = edits,
            myThemes = bumped.fold(myThemes(o.optJSONArray("myThemes"))) { l, (th, e) -> l + MyTheme(nextMyThemeName(l), th, e) },
        ).let { c ->
            // r13 Part D: a removed current theme keeps its look ([Migrations.candle] first, so 촛불 is the 내 테마 v10 finds)
            if (storedTheme == "CANDLE") Migrations.candle(c) else c
        }.let { c ->
            // a customFrom naming no 내 테마 is dropped
            if (v < 11) Migrations.looks(c)
            else c.copy(customFrom = o.optString("customFrom").takeIf { n -> c.myThemes.any { it.name == n } } ?: c.customFrom)
        }.let { c -> if (storedTheme == "FLIP") Migrations.flip(c) else c }
            .let { c -> c.copy(photoFilter = o.optString("photoFilter").takeIf { it in c.photoFolders }) }
            .let { c -> if (v < 12) Migrations.floatToDigital(c) else c }
            // plan-r15 E5: before v15 every clock in the list rotated; one clock = no rotation
            .let { c -> if (v < 15) c.copy(rotateClocks = c.myClocks.size > 1) else c }
            // plan-r15 E9: 입체 시계 and 입체 네모 got a 날짜·요일 switch, off by default; an older stored style carried
            // showInfo = true from the old default and would show the date after the update
            .let { c -> if (v < 16) Migrations.reliefDateOff(c) else c }
    }

    /** r10 removals: RAINBOW/GLASS draw as SOLID (explicitly, not the face's base effect); pink/brown noise is the one WHITE. */
    private val OLD_EFFECTS = setOf("RAINBOW", "GLASS")
    private val OLD_NOISE = setOf("PINK", "BROWN")

    private fun effect(o: JSONObject, def: Effect): Effect =
        if (o.optString("effect") in OLD_EFFECTS) Effect.SOLID else enumOf(o, "effect", def)

    private fun style(s: ClockStyle) = JSONObject().apply {
        put("font", s.font.name); put("weight", s.weight); put("sizePct", s.sizePct); put("widthPct", s.widthPct)
        put("color", s.color); put("color2", s.color2); put("effect", s.effect.name)
        put("background", s.background.name); put("bgColor", s.bgColor); put("bgColor2", s.bgColor2)
        put("numerals", s.numerals.name); put("layout", s.layout.name); put("showInfo", s.showInfo)
        put("analogNumbers", s.analogNumbers.name); put("analogTicks", s.analogTicks.name)
        put("numeralColor", s.numeralColor); put("handColor", s.handColor); put("secondColor", s.secondColor)
        put("weekdayColor", s.weekdayColor); put("tickColor", s.tickColor); put("litDotColor", s.litDotColor)
        R10_COLOURS.forEach { (k, get, _) -> get(s)?.let { put(k, it) } }   // null = FaceDefaults, omitted
        // r11 Part C (Relief faces); h24 null = the phone's setting, omitted
        put("lumeView", s.lumeView.name); put("sweep", s.sweep); s.h24?.let { put("h24", it) }; put("ghost", s.ghost); put("squareNumerals", s.squareNumerals.name)
        // r14 디지털 options; absent = false
        if (s.accentMiddle) put("am", true); if (s.fill) put("fl", true); if (s.overlap) put("ov", true)
        if (s.clearBg) put("cb", true)
    }

    /** r10 per-face colours (nullable): key, read, write. Presets are indices, stored as numbers too. */
    private val R10_COLOURS: List<Triple<String, (ClockStyle) -> Long?, (ClockStyle, Long?) -> ClockStyle>> = listOf(
        Triple("hourColor", { s -> s.hourColor }, { s, v -> s.copy(hourColor = v) }),
        Triple("minuteColor", { s -> s.minuteColor }, { s, v -> s.copy(minuteColor = v) }),
        Triple("accentColor", { s -> s.accentColor }, { s, v -> s.copy(accentColor = v) }),
        Triple("plateColor", { s -> s.plateColor }, { s, v -> s.copy(plateColor = v) }),
        Triple("digitColor", { s -> s.digitColor }, { s, v -> s.copy(digitColor = v) }),
        Triple("panelColor", { s -> s.panelColor }, { s, v -> s.copy(panelColor = v) }),
        Triple("inkColor", { s -> s.inkColor }, { s, v -> s.copy(inkColor = v) }),
        Triple("dotColor", { s -> s.dotColor }, { s, v -> s.copy(dotColor = v) }),
        Triple("faceColor", { s -> s.faceColor }, { s, v -> s.copy(faceColor = v) }),
        Triple("calendarColor", { s -> s.calendarColor }, { s, v -> s.copy(calendarColor = v) }),
        Triple("barColor", { s -> s.barColor }, { s, v -> s.copy(barColor = v) }),
        Triple("rimColor", { s -> s.rimColor }, { s, v -> s.copy(rimColor = v) }),
        Triple("paperColor", { s -> s.paperColor }, { s, v -> s.copy(paperColor = v) }),
        Triple("ledColor", { s -> s.ledColor }, { s, v -> s.copy(ledColor = v) }),
        Triple("pairPreset", { s -> s.pairPreset?.toLong() }, { s, v -> s.copy(pairPreset = v?.toInt()) }),
        Triple("trioPreset", { s -> s.trioPreset?.toLong() }, { s, v -> s.copy(trioPreset = v?.toInt()) }),
    )

    /** Fields missing from [o] keep the value in [b] (the default for that face), including fields added after this save. */
    private fun style(o: JSONObject, b: ClockStyle) = b.copy(
        font = enumOf(o, "font", b.font), weight = o.optInt("weight", b.weight), sizePct = o.optInt("sizePct", b.sizePct),
        widthPct = o.optInt("widthPct", b.widthPct), color = o.optLong("color", b.color), color2 = o.optLong("color2", b.color2),
        effect = effect(o, b.effect), background = enumOf(o, "background", b.background),
        bgColor = o.optLong("bgColor", b.bgColor), bgColor2 = o.optLong("bgColor2", b.bgColor2),
        numerals = enumOf(o, "numerals", b.numerals), layout = enumOf(o, "layout", b.layout),
        showInfo = o.optBoolean("showInfo", b.showInfo),
        analogNumbers = enumOf(o, "analogNumbers", b.analogNumbers), analogTicks = enumOf(o, "analogTicks", b.analogTicks),
        numeralColor = o.optLong("numeralColor", b.numeralColor), handColor = o.optLong("handColor", b.handColor),
        secondColor = o.optLong("secondColor", b.secondColor), weekdayColor = o.optLong("weekdayColor", b.weekdayColor),
        tickColor = o.optLong("tickColor", b.tickColor), litDotColor = o.optLong("litDotColor", b.litDotColor),
        lumeView = enumOf(o, "lumeView", b.lumeView), sweep = o.optBoolean("sweep", b.sweep),
        h24 = if (o.has("h24")) o.opt("h24") as? Boolean else b.h24, ghost = o.optBoolean("ghost", b.ghost),
        squareNumerals = enumOf(o, "squareNumerals", b.squareNumerals),
        accentMiddle = o.optBoolean("am", false), fill = o.optBoolean("fl", false), overlap = o.optBoolean("ov", false),
        clearBg = o.optBoolean("cb", false),
    ).let { s -> R10_COLOURS.fold(s) { acc, (k, _, set) -> if (o.has(k) && !o.isNull(k)) set(acc, o.optLong(k)) else acc } }

    private fun board(b: Board) = JSONObject().apply {
        put("id", b.id); put("name", b.name); put("layout", b.layout.name)
        put("slots", JSONArray().also { a -> b.slots.forEach { a.put(cards(it)) } })
        put("accent", b.accent ?: JSONObject.NULL)
        put("clockFace", b.clockFace.name)
        if (!b.on) put("on", false)
        if (b.options.isNotEmpty()) put("options", options(b.options))
    }

    private fun options(m: Map<String, SlotOptions>) = JSONObject().also { o -> m.forEach { (k, v) -> o.put(k, slotOptions(v)) } }
    private fun options(o: JSONObject?): Map<String, SlotOptions> =
        o?.let { oo -> oo.keys().asSequence().mapNotNull { k -> runCatching { k to slotOptions(oo.getJSONObject(k)) }.getOrNull() }.toMap() } ?: emptyMap()

    /** [stacks] by slot index: their per-index faces (a stored ANALOG card) laid over [m]'s options. */
    private fun withFaces(m: Map<String, SlotOptions>, stacks: List<Pair<List<CardType>, Map<Int, FaceId>>>): Map<String, SlotOptions> {
        val out = m.toMutableMap()
        stacks.forEachIndexed { s, (_, faces) -> faces.forEach { (j, f) -> val k = "$s:$j"; out[k] = (out[k] ?: SlotOptions()).copy(face = f) } }
        return out
    }

    private fun slotOptions(s: SlotOptions) = JSONObject().apply {
        if (s.apps.isNotEmpty()) put("apps", JSONArray().also { a -> s.apps.forEach { a.put(it) } })
        s.face?.let { put("face", it.name) }
        if (s.iconStyle != IconStyle.ORIGINAL) put("iconStyle", s.iconStyle.name)
        if (s.appSource != AppSource.PICKED) put("appSource", s.appSource.name)
        if (s.appCount != 4) put("appCount", s.appCount)
    }

    /** r14 C1: the old phone-widget keys (widgetId, opacity, widgetMode, radiusDp, hideAtNight) are dropped. */
    private fun slotOptions(o: JSONObject): SlotOptions = SlotOptions(
        apps = strings(o, "apps")?.distinct()?.take(MAX_APPS) ?: emptyList(),
        face = if (o.has("face")) runCatching { FaceId.valueOf(o.getString("face")) }.getOrNull() else null,
        iconStyle = enumOf(o, "iconStyle", IconStyle.ORIGINAL),
        appSource = enumOf(o, "appSource", AppSource.PICKED),
        appCount = o.optDouble("appCount", 4.0).takeIf { it in listOf(4.0, 6.0, 8.0) }?.toInt() ?: 4,
    )

    private fun board(o: JSONObject): Board {
        val stacks = o.optJSONArray("slots")?.let { a -> (0 until a.length()).map { stack(a.optJSONArray(it), emptyList()) } } ?: emptyList()
        val slots = stacks.map { it.first }
        return BoardOps.fit(
            Board(
                id = o.getString("id"), name = o.optString("name", "페이지"), layout = enumOf(o, "layout", Layout.TWO), slots = slots,
                accent = if (o.has("accent") && !o.isNull("accent")) o.optLong("accent") else null,
                clockFace = enumOf(o, "clockFace", FaceId.DIGITAL),
                options = withFaces(options(o.optJSONObject("options")), stacks),
                on = o.optBoolean("on", true),
            ),
        )
    }

    private fun strings(o: JSONObject, k: String): List<String>? =
        o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() } }

    private fun cards(l: List<CardType>) = JSONArray().also { a -> l.forEach { a.put(it.name) } }

    /** r9 merged cards: old names read as their new card (D3; r9 dev builds stored NOTIF_LIST), and a stack never holds the same card twice. */
    private val CARD_ALIAS = mapOf("NOTIF_STACK" to CardType.NOTIF, "NOTIF_LIST" to CardType.NOTIF, "VIDEO" to CardType.MUSIC, "WIDGET" to CardType.APPS)

    /**
     * A stored stack and the faces it implies by new index: an "ANALOG" card (before 11-14) reads as CLOCK with the
     * ANALOG face. A stack never holds the same card twice; two clocks count as different when their faces differ.
     */
    private fun stack(a: JSONArray?, def: List<CardType>): Pair<List<CardType>, Map<Int, FaceId>> {
        if (a == null) return def to emptyMap()
        val read = (0 until a.length()).mapNotNull {
            val n = a.optString(it)
            if (n == "ANALOG") CardType.CLOCK to FaceId.ANALOG
            else (CARD_ALIAS[n] ?: runCatching { CardType.valueOf(n) }.getOrNull())?.let { c -> c to null }
        }.distinct()
        return read.map { it.first } to read.withIndex().mapNotNull { (j, p) -> p.second?.let { j to it } }.toMap()
    }

    private fun city(c: City) = JSONObject().put("name", c.name).put("lat", c.lat).put("lon", c.lon)
    private fun city(o: JSONObject) = City(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"))

    private fun num(o: JSONObject, k: String, def: Float): Float = o.optDouble(k, def.toDouble()).let { if (it.isNaN()) def else it.toFloat() }

    private inline fun <reified E : Enum<E>> enumOrNull(o: JSONObject, k: String): E? =
        runCatching { enumValueOf<E>(o.getString(k)) }.getOrNull()

    private inline fun <reified E : Enum<E>> enumOf(o: JSONObject, k: String, def: E): E =
        runCatching { enumValueOf<E>(o.getString(k)) }.getOrDefault(def)
}
