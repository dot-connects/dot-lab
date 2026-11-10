package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.City
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.DDay
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Hand-written JSON codec for [AppConfig]. Enums go by name. Every read falls back to the default of that field,
 * so a save from an older or newer build (missing or unknown fields, renamed enum values) never crashes.
 */
object ConfigCodec {
    fun encode(c: AppConfig): String = JSONObject().apply {
        put("theme", c.theme.name)
        put("styles", JSONObject().also { o -> c.styles.forEach { (f, s) -> o.put(f.name, style(s)) } })
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
    }.toString()

    /** Any parse failure returns defaults; a bad field returns that field's default. */
    fun decode(json: String?): AppConfig {
        if (json.isNullOrBlank()) return AppConfig()
        return try { fromJson(JSONObject(json)) } catch (e: Exception) { AppConfig() }
    }

    private fun fromJson(o: JSONObject): AppConfig {
        val d = AppConfig()
        val styles = o.optJSONObject("styles")
        return AppConfig(
            theme = enumOf(o, "theme", d.theme),
            styles = FaceId.entries.associateWith { f ->
                val so = styles?.optJSONObject(f.name)
                if (so == null) ClockStyle.default(f) else style(so, ClockStyle.default(f))
            },
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
            glowColor = o.optLong("glowColor", d.glowColor),
            glowStartMinBefore = o.optInt("glowStartMinBefore", d.glowStartMinBefore),
            glowRampSec = o.optInt("glowRampSec", d.glowRampSec),
            onUnfold = enumOf(o, "onUnfold", d.onUnfold),
            ddays = o.optJSONArray("ddays")?.let { a ->
                (0 until a.length()).mapNotNull {
                    runCatching { a.getJSONObject(it).let { x -> DDay(x.getString("name"), LocalDate.parse(x.getString("date"))) } }.getOrNull()
                }
            } ?: d.ddays,
            city = o.optJSONObject("city")?.let { runCatching { city(it) }.getOrNull() } ?: d.city,
            worldCities = o.optJSONArray("worldCities")?.let { a -> (0 until a.length()).mapNotNull { runCatching { city(a.getJSONObject(it)) }.getOrNull() } } ?: d.worldCities,
            usageEnabled = o.optBoolean("usageEnabled", d.usageEnabled),
            sleepSound = enumOf(o, "sleepSound", d.sleepSound),
            sleepMinutes = o.optInt("sleepMinutes", d.sleepMinutes),
            focusMinutes = o.optInt("focusMinutes", d.focusMinutes),
            breakMinutes = o.optInt("breakMinutes", d.breakMinutes),
        )
    }

    private fun style(s: ClockStyle) = JSONObject().apply {
        put("font", s.font.name); put("weight", s.weight); put("sizePct", s.sizePct); put("widthPct", s.widthPct)
        put("color", s.color); put("color2", s.color2); put("effect", s.effect.name)
        put("background", s.background.name); put("bgColor", s.bgColor); put("bgColor2", s.bgColor2)
        put("numerals", s.numerals.name); put("layout", s.layout.name); put("showInfo", s.showInfo)
        put("analogNumbers", s.analogNumbers.name); put("analogTicks", s.analogTicks.name)
        put("numeralColor", s.numeralColor); put("handColor", s.handColor); put("secondColor", s.secondColor)
        put("weekdayColor", s.weekdayColor); put("tickColor", s.tickColor); put("litDotColor", s.litDotColor)
    }

    /** Fields missing from [o] keep the value in [b] (the default for that face), including fields added after this save. */
    private fun style(o: JSONObject, b: ClockStyle) = b.copy(
        font = enumOf(o, "font", b.font), weight = o.optInt("weight", b.weight), sizePct = o.optInt("sizePct", b.sizePct),
        widthPct = o.optInt("widthPct", b.widthPct), color = o.optLong("color", b.color), color2 = o.optLong("color2", b.color2),
        effect = enumOf(o, "effect", b.effect), background = enumOf(o, "background", b.background),
        bgColor = o.optLong("bgColor", b.bgColor), bgColor2 = o.optLong("bgColor2", b.bgColor2),
        numerals = enumOf(o, "numerals", b.numerals), layout = enumOf(o, "layout", b.layout),
        showInfo = o.optBoolean("showInfo", b.showInfo),
        analogNumbers = enumOf(o, "analogNumbers", b.analogNumbers), analogTicks = enumOf(o, "analogTicks", b.analogTicks),
        numeralColor = o.optLong("numeralColor", b.numeralColor), handColor = o.optLong("handColor", b.handColor),
        secondColor = o.optLong("secondColor", b.secondColor), weekdayColor = o.optLong("weekdayColor", b.weekdayColor),
        tickColor = o.optLong("tickColor", b.tickColor), litDotColor = o.optLong("litDotColor", b.litDotColor),
    )

    private fun board(b: Board) = JSONObject().apply {
        put("id", b.id); put("name", b.name); put("layout", b.layout.name)
        put("slots", JSONArray().also { a -> b.slots.forEach { a.put(cards(it)) } })
        put("accent", b.accent ?: JSONObject.NULL)
        put("clockFace", b.clockFace.name)
    }

    private fun board(o: JSONObject): Board {
        val slots = o.optJSONArray("slots")?.let { a -> (0 until a.length()).map { cards(a.optJSONArray(it), emptyList()) } } ?: emptyList()
        return BoardOps.fit(
            Board(
                id = o.getString("id"), name = o.optString("name", "화면"), layout = enumOf(o, "layout", Layout.TWO), slots = slots,
                accent = if (o.has("accent") && !o.isNull("accent")) o.optLong("accent") else null,
                clockFace = enumOf(o, "clockFace", FaceId.DIGITAL),
            ),
        )
    }

    private fun cards(l: List<CardType>) = JSONArray().also { a -> l.forEach { a.put(it.name) } }

    private fun cards(a: JSONArray?, def: List<CardType>): List<CardType> =
        if (a == null) def else (0 until a.length()).mapNotNull { runCatching { CardType.valueOf(a.getString(it)) }.getOrNull() }

    private fun city(c: City) = JSONObject().put("name", c.name).put("lat", c.lat).put("lon", c.lon)
    private fun city(o: JSONObject) = City(o.getString("name"), o.getDouble("lat"), o.getDouble("lon"))

    private fun num(o: JSONObject, k: String, def: Float): Float = o.optDouble(k, def.toDouble()).let { if (it.isNaN()) def else it.toFloat() }

    private inline fun <reified E : Enum<E>> enumOf(o: JSONObject, k: String, def: E): E =
        runCatching { enumValueOf<E>(o.getString(k)) }.getOrDefault(def)
}
