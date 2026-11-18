package kr.dot.sb.settings

import kr.dot.sb.model.AnalogNumbers
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.AppSource
import kr.dot.sb.model.Background
import kr.dot.sb.model.CardType
import kr.dot.sb.model.City
import kr.dot.sb.model.ClockStyle
import kr.dot.sb.model.DDay
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.Effect
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.FontId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.NightMode
import kr.dot.sb.model.Numerals
import kr.dot.sb.model.SleepSound
import kr.dot.sb.model.UnfoldAction
import kr.dot.sb.model.AnalogStyle
import kr.dot.sb.model.Board
import kr.dot.sb.model.BoardBg
import kr.dot.sb.model.SlotOptions
import kr.dot.sb.model.Theme
import kr.dot.sb.ui.theme.Themes
import kr.dot.sb.ui.theme.clockStyle
import kr.dot.sb.ui.theme.withTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import java.time.LocalDate

class ConfigCodecTest {
    private fun changed(): AppConfig {
        val d = AppConfig()
        val digital = d.clockStyle(FaceId.DIGITAL).copy(
            font = FontId.SLAB, weight = 640, sizePct = 85, color = 0xFF123456, effect = Effect.GLOW,
            background = Background.GRADIENT, analogNumbers = AnalogNumbers.ROMAN, showInfo = false,
        )
        return d.copy(
            styles = d.styles + (FaceId.DIGITAL to digital),
            boards = BoardOps.add(d).boards.map { it.copy(accent = 0xFFFD6890) },
            startPage = 4, use24h = true, nightMode = NightMode.SCHEDULE, nightEnterLux = 2.5f, nightExitLux = 11f,
            nightMaxBrightness = 0.45f, dayBrightness = 0.6f, startOnCharge = false, displayOff = DisplayOff.NEVER, motionToWake = false, showNotifications = false,
            previewOnTapOnly = true, burnInShift = false, widgetLeft = listOf(CardType.MUSIC), widgetRight = emptyList(),
            glowColor = 0xFF85D4BA, glowStartMinBefore = 10, glowRampSec = 9, onUnfold = UnfoldAction.MOOD_LIGHT,
            ddays = listOf(DDay("수능", LocalDate.of(2026, 11, 19)), DDay("생일", LocalDate.of(2027, 2, 3))),
            city = City("부산", 35.1796, 129.0756), worldCities = listOf(City("도쿄", 35.6762, 139.6503)),
            usageEnabled = false, focusMinutes = 50, breakMinutes = 10, timerAlert = kr.dot.sb.model.TimerAlert.BELL, sleepMinutes = 90, theme = Theme.MOODLIGHT,
            boardBg = BoardBg.AURORA, boardBgColor = 0xFF223344, analogStyle = AnalogStyle.BIG4,
            sleepSound = SleepSound.WAVES, myClocks = listOf(FaceId.WALL, FaceId.DIGITAL),
            excludedCalendars = setOf("com.google|a@b.c|Work", "LOCAL|phone|Holidays"), heatProtect = false,
            moodAutoOffMin = 10, nightNotifQuiet = false, photoIntervalSec = 60,
        )
    }

    @Test fun roundTripKeepsEveryField() {
        val c = changed()
        assertNotEquals(AppConfig(), c)
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
    }

    @Test fun defaultsRoundTrip() {
        assertEquals(AppConfig(), ConfigCodec.decode(ConfigCodec.encode(AppConfig())))
    }

    @Test fun storedRotationFlagIsIgnoredWithoutChangingOtherSettings() {
        val c = changed().copy(rotateClocks = false, clockCardFace = FaceId.DIGITAL)
        val encoded = ConfigCodec.encode(c)
        assertFalse(JSONObject(encoded).getBoolean("rotateClocks"))
        for (version in listOf(14, 15, ConfigCodec.VERSION)) {
            val stored = JSONObject(encoded).put("v", version).toString()
            assertEquals(c.copy(rotateClocks = true), ConfigCodec.decode(stored))
        }
    }

    @Test fun dayBrightnessNullMeansAutoAndSurvives() {
        assertEquals(null, ConfigCodec.decode(ConfigCodec.encode(AppConfig(dayBrightness = null))).dayBrightness)
        assertEquals(0.35f, ConfigCodec.decode(ConfigCodec.encode(AppConfig(dayBrightness = 0.35f))).dayBrightness)
    }

    @Test fun oldSaveWithoutNewFieldsGetsDefaults() {
        val c = ConfigCodec.decode("""{"startPage":1}""")
        assertEquals(null, c.dayBrightness)
        assertEquals(true, c.startOnCharge)
    }

    @Test fun outOfRangeDayBrightnessIsClamped() =
        assertEquals(1f, ConfigCodec.decode("""{"dayBrightness":7.5}""").dayBrightness)

    @Test fun nullUse24hSurvives() {
        assertEquals(null, ConfigCodec.decode(ConfigCodec.encode(AppConfig(use24h = null))).use24h)
    }

    @Test fun emptyOrBrokenSavesGiveDefaults() {
        assertEquals(AppConfig(), ConfigCodec.decode(null))
        assertEquals(AppConfig(myClocks = Migrations.OLD_CLOCKS - FaceId.FLOAT), ConfigCodec.decode("{}"))   // no myClocks = an upgrade (r14: FLOAT left)
        assertEquals(AppConfig(), ConfigCodec.decode("not json {{{"))
    }

    @Test fun unknownFieldsAndEnumValuesFallBack() {
        val json = """{"future":{"x":1},"nightMode":"MOON","displayOff":"NEVER","startPage":5,
            |"styles":{"DIGITAL":{"font":"COMIC","weight":300,"newField":true},"HOLOGRAM":{"font":"BOLD"}},
            |"boards":[{"id":"a","layout":"HEX","slots":[["CLOCK","TELEPORT"]]},{"name":"no id"}],
            |"widgetLeft":["MUSIC","NOPE"],"ddays":[{"name":"x","date":"garbage"}]}""".trimMargin()
        val c = ConfigCodec.decode(json)
        assertEquals(NightMode.AUTO, c.nightMode)          // unknown enum value
        assertEquals(DisplayOff.NEVER, c.displayOff)       // known value in the same file still read
        assertEquals(5, c.startPage)
        val digital = c.clockStyle(FaceId.DIGITAL)
        assertEquals(Migrations.r8Default(FaceId.DIGITAL).font, digital.font)   // unknown font value
        assertEquals(300, digital.weight)
        assertEquals(null, c.styles[FaceId.ANALOG])         // never stored = follows the theme
        assertEquals(1, c.boards.size)                     // the board without an id is dropped
        assertEquals(Layout.TWO, c.boards[0].layout)       // unknown layout
        assertEquals(2, c.boards[0].slots.size)            // slots refitted to the layout
        assertEquals(listOf(CardType.CLOCK), c.boards[0].slots[0])
        assertEquals(listOf(CardType.MUSIC), c.widgetLeft)
        assertTrue(c.ddays.isEmpty())                      // unparsable date dropped
    }

    // ---- r9 (spec-r9, plan D3/D4/D6/D7)

    @Test fun oldNotificationAndVideoCardsReadAsTheMergedCards() {
        val c = ConfigCodec.decode("""{"boards":[{"id":"a","layout":"TWO","slots":[["NOTIF_STACK","NOTIF_LIST"],["VIDEO","MUSIC"]]}],
            |"widgetLeft":["NOTIF_STACK"],"widgetRight":["VIDEO","CLOCK"]}""".trimMargin())
        assertEquals(listOf(listOf(CardType.NOTIF), listOf(CardType.MUSIC)), c.boards[0].slots)
        assertEquals(listOf(CardType.NOTIF), c.widgetLeft)
        assertEquals(listOf(CardType.MUSIC, CardType.CLOCK), c.widgetRight)
    }

    @Test fun r9DevNamesAndTheNewNameDedupeToOneNotifCard() {
        val c = ConfigCodec.decode("""{"boards":[{"id":"a","layout":"THREE","slots":[["NOTIF_LIST"],["NOTIF","NOTIF_STACK","NOTIF_LIST"],["MUSIC","VIDEO","NOTIF_LIST"]]}],
            |"widgetLeft":["NOTIF_LIST","NOTIF"],"widgetRight":["VIDEO"]}""".trimMargin())
        assertEquals(listOf(listOf(CardType.NOTIF), listOf(CardType.NOTIF), listOf(CardType.MUSIC, CardType.NOTIF)), c.boards[0].slots)
        assertEquals(listOf(CardType.NOTIF), c.widgetLeft)
        assertEquals(listOf(CardType.MUSIC), c.widgetRight)
        // and it writes the new name back
        val back = ConfigCodec.encode(c)
        assertTrue("NOTIF_LIST" !in back && "NOTIF_STACK" !in back && "VIDEO" !in back)
        assertEquals(c.boards, ConfigCodec.decode(back).boards)
    }

    @Test fun unknownThemeFallsBackToIphone() = assertEquals(Theme.IPHONE, ConfigCodec.decode("""{"theme":"XYZ"}""").theme)

    @Test fun r8UntouchedStylesAreDroppedEditsKept() {
        val r8 = JSONObject().also { o -> FaceId.entries.forEach { f -> o.put(f.name, styleJson(Migrations.r8Default(f))) } }
        r8.put("WORLD", styleJson(Migrations.r8Default(FaceId.WORLD).copy(sizePct = 80)))
        val c = ConfigCodec.decode(JSONObject().put("theme", "PAPER").put("styles", r8).toString())
        assertEquals(setOf(FaceId.WORLD), c.styles.keys)
        assertEquals(Themes.of(Theme.DAYPAPER).clockDefaults(FaceId.DIGITAL), c.clockStyle(FaceId.DIGITAL))
    }

    @Test fun r9EditEqualToTheR8LookSurvivesARestart() {
        val edit = Migrations.r8Default(FaceId.DIGITAL)
        val back = ConfigCodec.decode(ConfigCodec.encode(AppConfig(theme = Theme.DAYPAPER, styles = mapOf(FaceId.DIGITAL to edit))))
        assertEquals(edit.copy(widthPct = 100), back.styles[FaceId.DIGITAL])      // a versioned save skips the r8 "untouched" drop
    }

    @Test fun r8DuoDialBecomesTheDuoStyleOnlyWhereItWasSeen() {
        val analog = JSONObject().put("ANALOG", styleJson(Migrations.r8Default(FaceId.ANALOG)))
        val iphone = ConfigCodec.decode(JSONObject().put("theme", "IPHONE").put("styles", analog).toString())
        assertEquals(AnalogStyle.DUO, iphone.analogStyle)
        assertEquals(null, iphone.styles[FaceId.ANALOG])    // untouched: dropped after the carry-over
        val paper = ConfigCodec.decode(JSONObject().put("theme", "PAPER").put("styles", analog).toString())
        assertEquals(AnalogStyle.ROUND, paper.analogStyle)    // the paper user saw the paper dial, not Duo
    }

    @Test fun themeSwitchSurvivesASaveAndFollowsTheNewTheme() {
        val c = AppConfig(theme = Theme.DAYPAPER).withTheme(Theme.MOODLIGHT)
        val back = ConfigCodec.decode(ConfigCodec.encode(c))
        assertEquals(Themes.of(Theme.MOODLIGHT).clockDefaults(FaceId.DIGITAL), back.clockStyle(FaceId.DIGITAL))
    }

    @Test fun withThemeResetsColoursButKeepsShape() {
        val d = AppConfig()
        val edited = d.clockStyle(FaceId.ANALOG).copy(handColor = 0xFFFFFFFF, sizePct = 80, background = kr.dot.sb.model.Background.COLOR)
        val c = d.copy(styles = mapOf(FaceId.ANALOG to edited)).withTheme(Theme.MOODLIGHT)
        val def = Themes.of(Theme.MOODLIGHT).clockDefaults(FaceId.ANALOG)
        val s = c.clockStyle(FaceId.ANALOG)
        assertEquals(def.handColor, s.handColor)
        assertEquals(def.background, s.background)
        assertEquals(80, s.sizePct)
        // a colour-only edit collapses back into the theme default (same-shape themes)
        val candle = AppConfig(theme = Theme.MOODLIGHT)
        val only = candle.copy(styles = mapOf(FaceId.WORLD to candle.clockStyle(FaceId.WORLD).copy(color = 0xFF123456))).withTheme(Theme.SKY)
        assertEquals(null, only.styles[FaceId.WORLD])
    }

    @Test fun resetMeansNoOverride() {
        val c = AppConfig(styles = mapOf(FaceId.DIGITAL to AppConfig().clockStyle(FaceId.DIGITAL).copy(sizePct = 70)))
        assertEquals(AppConfig().clockStyle(FaceId.DIGITAL), c.copy(styles = c.styles - FaceId.DIGITAL).clockStyle(FaceId.DIGITAL))
    }

    /** r14 C1: a saved 폰 위젯 card reads as an empty 앱 바로가기 and is written back without the widget keys. */
    @Test fun oldWidgetSlotReadsAsEmptyApps() {
        val json = """{"boards":[{"id":"a","layout":"TWO","slots":[["WIDGET"],["CLOCK"]],
            |"options":{"0:0":{"widgetId":7,"opacity":0.5,"widgetMode":"DARK","radiusDp":8,"hideAtNight":true}}}]}""".trimMargin()
        val c = ConfigCodec.decode(json)
        assertEquals(listOf(CardType.APPS), c.boards[0].slots[0])
        assertEquals(emptyList<String>(), c.appsCard.apps)
        val out = ConfigCodec.encode(ConfigCodec.decode(json))
        for (k in listOf("widgetId", "opacity", "widgetMode", "radiusDp", "hideAtNight", "\"WIDGET\"")) assertFalse(k, out.contains(k))
        assertEquals(ConfigCodec.decode(json), ConfigCodec.decode(out))
    }

    @Test fun appSourceRoundTripAndLegacyDefaults() {
        val picked = listOf("com.a", "com.b")
        val board = Board("a", "a", Layout.ONE, listOf(listOf(CardType.APPS)))
        for (source in AppSource.entries) for (count in listOf(4, 6, 8)) {
            val opts = SlotOptions(apps = picked, appSource = source, appCount = count)
            val config = AppConfig(boards = listOf(board), appsCard = opts)
            val json = ConfigCodec.encode(config)
            assertEquals(config, ConfigCodec.decode(json))
            val stored = JSONObject(json).getJSONObject("appsCard")
            assertEquals(source != AppSource.PICKED, stored.has("appSource"))
            assertEquals(count != 4, stored.has("appCount"))
        }
        val old = """{"boards":[{"id":"a","layout":"ONE","slots":[["APPS"]],"options":{"0:0":{"apps":["com.a","com.b"]}}}]}"""
        assertEquals(SlotOptions(apps = picked), ConfigCodec.decode(old).appsCard)
        for (bad in listOf(-1, 0, 5, 7, 9, 4.5, 6.5, "invalid", JSONObject.NULL)) {
            val json = JSONObject(old).apply {
                getJSONArray("boards").getJSONObject(0).getJSONObject("options").getJSONObject("0:0")
                    .put("appSource", "RECENT").put("appCount", bad)
            }
            assertEquals(SlotOptions(apps = picked, appSource = AppSource.RECENT, appCount = 4), ConfigCodec.decode(json.toString()).appsCard)
        }
    }

    @Test fun appsKeepTheirOrderAndCap() {
        val many = (1..10).map { "com.app$it" }
        val c = AppConfig(appsCard = SlotOptions(apps = many.reversed()))
        assertEquals(many.reversed().take(8), ConfigCodec.decode(ConfigCodec.encode(c)).appsCard.apps)
    }

    // ---- r10 (plan-r10 Task 2)

    /** [c] saved by r10, then rewritten as a v9 save: "v" = 9 and no fields r9 did not have. */
    private fun v9(c: AppConfig, edit: (JSONObject) -> Unit = {}): String = JSONObject(ConfigCodec.encode(c)).apply {
        put("v", 9)
        listOf("myClocks", "excludedCalendars", "heatProtect", "moodAutoOffMin", "nightNotifQuiet", "photoIntervalSec").forEach { remove(it) }
        edit(this)
    }.toString()

    private fun storedStyle(face: FaceId, vararg kv: Pair<String, Any>): (JSONObject) -> Unit = { o ->
        o.getJSONObject("styles").getJSONObject(face.name).apply { kv.forEach { (k, v) -> put(k, v) } }
    }

    private val edited = AppConfig(styles = mapOf(FaceId.DIGITAL to AppConfig().clockStyle(FaceId.DIGITAL).copy(sizePct = 80)))

    @Test fun removedEffectsReadAsSolid() {   // (a)
        for (e in listOf("RAINBOW", "GLASS")) {
            assertEquals(Effect.SOLID, ConfigCodec.decode(v9(edited, storedStyle(FaceId.DIGITAL, "effect" to e))).styles[FaceId.DIGITAL]!!.effect)
        }
        // an explicit alias, not the face's base effect: solar's base is TWO_COLOR
        val solar = AppConfig(styles = mapOf(FaceId.SOLAR to AppConfig().clockStyle(FaceId.SOLAR).copy(sizePct = 80)))
        assertEquals(Effect.SOLID, ConfigCodec.decode(v9(solar, storedStyle(FaceId.SOLAR, "effect" to "GLASS"))).styles[FaceId.SOLAR]!!.effect)
    }

    @Test fun otherNumeralsReadAsLatin() {   // (b)
        assertEquals(Numerals.LATN, ConfigCodec.decode(v9(edited, storedStyle(FaceId.DIGITAL, "numerals" to "ARAB"))).styles[FaceId.DIGITAL]!!.numerals)
    }

    @Test fun pinkAndBrownNoiseReadAsWhite() {   // (c)
        for (s in listOf("PINK", "BROWN")) assertEquals(SleepSound.WHITE, ConfigCodec.decode(v9(AppConfig()) { it.put("sleepSound", s) }).sleepSound)
        assertEquals(SleepSound.FIRE, ConfigCodec.decode(v9(AppConfig()) { it.put("sleepSound", "FIRE") }).sleepSound)
    }

    @Test fun storedWidthGivesWayToTheFaceDefault() {   // (d)
        val c = ConfigCodec.decode(v9(edited.copy(theme = Theme.NEON), storedStyle(FaceId.DIGITAL, "widthPct" to 127)))
        assertEquals(Themes.of(Theme.NEON).clockDefaults(FaceId.DIGITAL).widthPct, c.styles[FaceId.DIGITAL]!!.widthPct)
        assertEquals(80, c.styles[FaceId.DIGITAL]!!.sizePct)   // the rest of the edit stays
    }

    @Test fun videoAndExamPresetsLeaveTheDefaultsButStoredOnesStay() {   // (e)
        assertTrue(AppConfig().boards.none { it.id == "video" || it.id == "exam" })
        val old = Board("video", "v", Layout.ONE, listOf(listOf(CardType.MUSIC)))
        val exam = Board("exam", "e", Layout.FOUR, listOf(listOf(CardType.DDAY), listOf(CardType.CLOCK), listOf(CardType.CALENDAR), listOf(CardType.AGENDA)))
        assertEquals(listOf("video", "exam"), ConfigCodec.decode(v9(AppConfig(boards = listOf(old, exam)))).boards.map { it.id })
    }

    @Test fun newFieldsRoundTrip() {   // (f)
        val c = AppConfig(
            myClocks = listOf(FaceId.HANGUL, FaceId.RIM), excludedCalendars = setOf("a|b|c"), heatProtect = false,
            moodAutoOffMin = 15, nightNotifQuiet = false, photoIntervalSec = 300, tipsSeen = setOf("PULL_DOWN", "TIMER_HOLD"),
            pagesOff = setOf("photos"), photoFolders = listOf(TREE_CAM, TREE_TRIP), photoFilter = TREE_TRIP,
            boards = AppConfig().boards.mapIndexed { i, b -> if (i == 1) b.copy(on = false) else b },
        )
        assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
        // out-of-menu values fall back to the default
        val bad = ConfigCodec.decode(JSONObject(ConfigCodec.encode(c)).put("moodAutoOffMin", 7).put("photoIntervalSec", 1).toString())
        assertEquals(AppConfig().moodAutoOffMin, bad.moodAutoOffMin)
        assertEquals(AppConfig().photoIntervalSec, bad.photoIntervalSec)
        // a board that is on stores nothing extra
        assertTrue(!JSONObject(ConfigCodec.encode(AppConfig())).getJSONArray("boards").getJSONObject(0).has("on"))
    }

    @Test fun v13AlbumNamesAndSourcesReadAsNoFolders() {   // plan-r15 B1: no old value is a SAF grant
        for (src in listOf("PICKS", "RECENT", "FOLDERS")) {
            val old = JSONObject(ConfigCodec.encode(AppConfig())).put("v", 13).put("photoSource", src)
                .put("photoFolders", JSONArray(listOf("Camera", "여행"))).put("photoFilter", "Camera").toString()
            val c = ConfigCodec.decode(old)
            assertEquals(emptyList<String>(), c.photoFolders)
            assertEquals(null, c.photoFilter)
        }
        // a v14 save keeps its tree URIs; a filter naming no picked folder goes back to every folder
        val v14 = AppConfig(photoFolders = listOf(TREE_CAM, TREE_TRIP))
        assertEquals(v14.photoFolders, ConfigCodec.decode(ConfigCodec.encode(v14)).photoFolders)
        assertEquals(null, ConfigCodec.decode(JSONObject(ConfigCodec.encode(v14)).put("photoFilter", "content://gone").toString()).photoFilter)
        assertEquals(null, v14.copy(photoFilter = TREE_TRIP).withPhotoFolders(listOf(TREE_CAM)).photoFilter)
    }

    /** plan-r15 E9 (v16): a stored 입체 시계 / 입체 네모 style loses its old showInfo = true; other faces and v16 saves keep it. */
    @Test fun v15ReliefStylesTurnTheDateOffOnce() {
        val styles = mapOf(
            FaceId.RELIEF to ClockStyle(FontId.BOLD, 700, color = 0xFFFFFFFF, showInfo = true), FaceId.RELIEF_SQUARE to ClockStyle(FontId.BOLD, 700, color = 0xFFFFFFFF, showInfo = true), FaceId.DIGITAL to ClockStyle(FontId.BOLD, 700, color = 0xFFFFFFFF, showInfo = true),
        )
        val saved = JSONObject(ConfigCodec.encode(AppConfig(styles = styles)))
        val old = ConfigCodec.decode(saved.put("v", 15).toString())
        assertEquals(false, old.styles[FaceId.RELIEF]?.showInfo)
        assertEquals(false, old.styles[FaceId.RELIEF_SQUARE]?.showInfo)
        assertEquals(true, old.styles[FaceId.DIGITAL]?.showInfo)
        // switched on again after the update: a v16 save keeps it
        val again = old.copy(styles = old.styles + (FaceId.RELIEF to ClockStyle(FontId.BOLD, 700, color = 0xFFFFFFFF, showInfo = true)))
        assertEquals(true, ConfigCodec.decode(ConfigCodec.encode(again)).styles[FaceId.RELIEF]?.showInfo)
    }

    @Test fun upgradeKeepsTheOldFiveFreshInstallGetsTheNewFour() {   // (g)
        // r14: the fifth, FLOAT, reads as DIGITAL, already first
        assertEquals(listOf(FaceId.DIGITAL, FaceId.ANALOG, FaceId.WORLD, FaceId.SOLAR), ConfigCodec.decode(v9(AppConfig())).myClocks)
        assertEquals(listOf(FaceId.DIGITAL, FaceId.FLIP, FaceId.HANGUL, FaceId.ANALOG), ConfigCodec.decode(null).myClocks)
        assertEquals(listOf(FaceId.DIGITAL, FaceId.FLIP, FaceId.HANGUL, FaceId.ANALOG), AppConfig().myClocks)
    }

    @Test fun unknownClockIdsDropEmptyListIsTheDefault() {   // (h)
        fun my(vararg ids: String) = ConfigCodec.decode(JSONObject(ConfigCodec.encode(AppConfig())).put("myClocks", JSONArray(ids.toList())).toString()).myClocks
        assertEquals(listOf(FaceId.LED, FaceId.DIGITAL), my("LED", "HOLOGRAM", "DIGITAL", "LED"))
        assertEquals(AppConfig().myClocks, my())
        assertEquals(AppConfig().myClocks, my("HOLOGRAM"))
    }

    @Test fun v9EditsSurviveAV10SaveAndLoad() {   // (i) the r8 drop is keyed on v < 9, not on the current version
        val digital = Migrations.r8Default(FaceId.DIGITAL).copy(widthPct = 100)   // an edit equal to the r8 look
        val analog = AppConfig().clockStyle(FaceId.ANALOG).copy(analogNumbers = AnalogNumbers.DUO, handColor = 0xFF112233)
        val c = AppConfig(theme = Theme.DAYPAPER, styles = mapOf(FaceId.DIGITAL to digital, FaceId.ANALOG to analog))
        val once = ConfigCodec.decode(v9(c))
        assertEquals(c.styles, once.styles)
        assertEquals(AnalogStyle.ROUND, once.analogStyle)
        assertEquals(once, ConfigCodec.decode(ConfigCodec.encode(once)))
    }

    @Test fun v10DropsExactlyTheOldExamDday() {   // (j)
        val exam = DDay("수능", LocalDate.of(2026, 11, 19))
        val keep = listOf(DDay("수능", LocalDate.of(2027, 11, 18)), DDay("생일", LocalDate.of(2026, 11, 19)))
        assertEquals(keep, ConfigCodec.decode(v9(AppConfig(ddays = listOf(exam) + keep))).ddays)
        assertTrue(AppConfig().ddays.isEmpty())
        // a v10 user who adds it back keeps it
        assertEquals(listOf(exam), ConfigCodec.decode(ConfigCodec.encode(AppConfig(ddays = listOf(exam)))).ddays)
    }

    @Test fun faceDefaultColoursRoundTripNullsOmitted() {   // (k) r10 per-face colours: null = FaceDefaults
        val base = AppConfig().clockStyle(FaceId.DIGITAL)
        assertTrue(styleJson(base).keys().asSequence().none { it in R10_COLOURS })
        val set = base.copy(
            hourColor = 1, minuteColor = 2, accentColor = 3, plateColor = 4, digitColor = 5, panelColor = 6, inkColor = 7,
            dotColor = 8, faceColor = 9, calendarColor = 10, barColor = 11, rimColor = 12, paperColor = 13, ledColor = 14,
            pairPreset = 3, trioPreset = 4,
        )
        assertEquals(R10_COLOURS, styleJson(set).keys().asSequence().filter { it in R10_COLOURS }.toSet())
        for (s in listOf(base, set, base.copy(plateColor = 0xFF1E1E20))) {
            val c = AppConfig(styles = mapOf(FaceId.FLIP to s))
            assertEquals(c, ConfigCodec.decode(ConfigCodec.encode(c)))
        }
    }

    private val R10_COLOURS = setOf(
        "hourColor", "minuteColor", "accentColor", "plateColor", "digitColor", "panelColor", "inkColor", "dotColor", "faceColor",
        "calendarColor", "barColor", "rimColor", "paperColor", "ledColor", "pairPreset", "trioPreset",
    )

    private fun styleJson(s: ClockStyle): JSONObject = JSONObject(ConfigCodec.encode(AppConfig(styles = mapOf(FaceId.DIGITAL to s)))).getJSONObject("styles").getJSONObject("DIGITAL")

    private companion object {
        const val TREE_CAM = "content://com.android.externalstorage.documents/tree/primary%3ADCIM%2FCamera"
        const val TREE_TRIP = "content://com.android.externalstorage.documents/tree/primary%3APictures%2F%EC%97%AC%ED%96%89"
    }
}
