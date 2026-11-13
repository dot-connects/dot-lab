package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.City
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.DDay
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.MOOD_AUTO_OFF_MIN
import kr.dot.sb.model.PHOTO_INTERVAL_SEC
import kr.dot.sb.model.SleepSound
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.ui.theme.Themes
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Hand-written JSON codec for [AppConfig]. Enums go by name. Every read falls back to the default of that field,
 * so a save from an older or newer build (missing or unknown fields, renamed enum values) never crashes.
 */
object ConfigCodec {
    /** Stored schema: 9 = r9 (styles are overrides only), 10 = r10. Saves without "v" are r8 or r9 dev builds. */
    const val VERSION = 10

    fun encode(c: AppConfig): String = JSONObject().apply {
        put("v", VERSION)
        put("theme", c.theme.name)
        put("styles", JSONObject().also { o -> c.styles.forEach { (f, s) -> o.put(f.name, style(s)) } })   // overrides only
        put("boardBg", c.boardBg.name)
        put("boardBgColor", c.boardBgColor)
        put("textSize", c.textSize.name)
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
        put("displayOff", c.displayOff.name)
        put("motionToWake", c.motionToWake)
        put("showNotifications", c.showNotifications)
        put("previewOnTapOnly", c.previewOnTapOnly)
        put("burnInShift", c.burnInShift)
        put("widgetLeft", cards(c.widgetLeft))
        put("widgetRight", cards(c.widgetRight))
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
        put("myClocks", JSONArray().also { a -> c.myClocks.forEach { a.put(it.name) } })
        put("excludedCalendars", JSONArray().also { a -> c.excludedCalendars.forEach { a.put(it) } })
        put("heatProtect", c.heatProtect)
        put("moodAutoOffMin", c.moodAutoOffMin)
        put("nightNotifQuiet", c.nightNotifQuiet)
        put("photoIntervalSec", c.photoIntervalSec)
    }.toString()

    /** Any parse failure returns defaults; a bad field returns that field's default. */
    fun decode(json: String?): AppConfig {
        if (json.isNullOrBlank()) return AppConfig()
        return try { fromJson(JSONObject(json)) } catch (e: Exception) { AppConfig() }
    }

    private fun fromJson(o: JSONObject): AppConfig {
        val d = AppConfig()
        val theme = enumOf(o, "theme", d.theme)
        val v = o.optInt("v")
        // r8 = before the r9 schema; stays on 9 whatever VERSION becomes (a v9 edit equal to the r8 look is an edit)
        val (read, migratedAnalog) = Migrations.styles(o.optJSONObject("styles"), theme, r8 = v < 9) { so, base -> style(so, base) }
        // r10 dropped the width slider: every face draws at its default width
        val styles = read.mapValues { (f, s) -> s.copy(widthPct = Themes.of(theme).clockDefaults(f).widthPct) }
        return AppConfig(
            theme = theme,
            styles = styles,
            boardBg = enumOf(o, "boardBg", d.boardBg),
            boardBgColor = o.optLong("boardBgColor", d.boardBgColor),
            textSize = enumOf(o, "textSize", d.textSize),
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
            displayOff = enumOf(o, "displayOff", d.displayOff),
            motionToWake = o.optBoolean("motionToWake", d.motionToWake),
            showNotifications = o.optBoolean("showNotifications", d.showNotifications),
            previewOnTapOnly = o.optBoolean("previewOnTapOnly", d.previewOnTapOnly),
            burnInShift = o.optBoolean("burnInShift", d.burnInShift),
            widgetLeft = cards(o.optJSONArray("widgetLeft"), d.widgetLeft),
            widgetRight = cards(o.optJSONArray("widgetRight"), d.widgetRight),
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
            myClocks = Migrations.myClocks(o.optJSONArray("myClocks"), d.myClocks),
            excludedCalendars = o.optJSONArray("excludedCalendars")?.let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotEmpty() }.toSet() } ?: d.excludedCalendars,
            heatProtect = o.optBoolean("heatProtect", d.heatProtect),
            moodAutoOffMin = o.optInt("moodAutoOffMin", d.moodAutoOffMin).takeIf { it in MOOD_AUTO_OFF_MIN } ?: d.moodAutoOffMin,
            nightNotifQuiet = o.optBoolean("nightNotifQuiet", d.nightNotifQuiet),
            photoIntervalSec = o.optInt("photoIntervalSec", d.photoIntervalSec).takeIf { it in PHOTO_INTERVAL_SEC } ?: d.photoIntervalSec,
        )
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
    ).let { s -> R10_COLOURS.fold(s) { acc, (k, _, set) -> if (o.has(k) && !o.isNull(k)) set(acc, o.optLong(k)) else acc } }

    private fun board(b: Board) = JSONObject().apply {
        put("id", b.id); put("name", b.name); put("layout", b.layout.name)
        put("slots", JSONArray().also { a -> b.slots.forEach { a.put(cards(it)) } })
        put("accent", b.accent ?: JSONObject.NULL)
        put("clockFace", b.clockFace.name)
        if (b.options.isNotEmpty()) put("options", JSONObject().also { o -> b.options.forEach { (k, v) -> o.put(k, slotOptions(v)) } })
    }

    private fun slotOptions(s: SlotOptions) = JSONObject().apply {
        put("widgetId", s.widgetId ?: JSONObject.NULL); put("opacity", s.opacity.toDouble()); put("widgetMode", s.widgetMode.name)
        put("radiusDp", s.radiusDp); put("hideAtNight", s.hideAtNight)
    }

    private fun slotOptions(o: JSONObject): SlotOptions {
        val d = SlotOptions()
        return SlotOptions(
            widgetId = if (o.has("widgetId") && !o.isNull("widgetId")) o.optInt("widgetId") else null,
            opacity = num(o, "opacity", d.opacity).coerceIn(0f, 1f), widgetMode = enumOf(o, "widgetMode", d.widgetMode),
            radiusDp = o.optInt("radiusDp", d.radiusDp), hideAtNight = o.optBoolean("hideAtNight", d.hideAtNight),
        )
    }

    private fun board(o: JSONObject): Board {
        val slots = o.optJSONArray("slots")?.let { a -> (0 until a.length()).map { cards(a.optJSONArray(it), emptyList()) } } ?: emptyList()
        return BoardOps.fit(
            Board(
                id = o.getString("id"), name = o.optString("name", "화면"), layout = enumOf(o, "layout", Layout.TWO), slots = slots,
                accent = if (o.has("accent") && !o.isNull("accent")) o.optLong("accent") else null,
                clockFace = enumOf(o, "clockFace", FaceId.DIGITAL),
                options = o.optJSONObject("options")?.let { oo -> oo.keys().asSequence().mapNotNull { k -> runCatching { k to slotOptions(oo.getJSONObject(k)) }.getOrNull() }.toMap() } ?: emptyMap(),
            ),
        )
    }

    private fun cards(l: List<CardType>) = JSONArray().also { a -> l.forEach { a.put(it.name) } }

    /** r9 merged cards: old names read as their new card (D3; r9 dev builds stored NOTIF_LIST), and a stack never holds the same card twice. */
    private val CARD_ALIAS = mapOf("NOTIF_STACK" to CardType.NOTIF, "NOTIF_LIST" to CardType.NOTIF, "VIDEO" to CardType.MUSIC)

    private fun cards(a: JSONArray?, def: List<CardType>): List<CardType> =
        if (a == null) def else (0 until a.length()).mapNotNull {
            val n = a.optString(it)
            CARD_ALIAS[n] ?: runCatching { CardType.valueOf(n) }.getOrNull()
        }.distinct()

    private fun city(c: City) = JSONObject().put("name", c.name).put("lat", c.lat).put("lon", c.lon)
    private fun city(o: JSONObject) = City(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"))

    private fun num(o: JSONObject, k: String, def: Float): Float = o.optDouble(k, def.toDouble()).let { if (it.isNaN()) def else it.toFloat() }

    private inline fun <reified E : Enum<E>> enumOf(o: JSONObject, k: String, def: E): E =
        runCatching { enumValueOf<E>(o.getString(k)) }.getOrDefault(def)
}
