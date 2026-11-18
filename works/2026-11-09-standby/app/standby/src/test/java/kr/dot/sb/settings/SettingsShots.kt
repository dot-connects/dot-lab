package kr.dot.sb.settings

import kr.dot.sb.ui.theme.clockStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.DisplayOff
import kr.dot.sb.model.FaceId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders every settings screen. Long pages use a tall viewport so one PNG shows the whole page. Split by area into
 * three classes (11-14) so Gradle can run them in parallel forks; shot names and paths are unchanged.
 */
abstract class SettingsShotKit {
    @get:Rule val rule = createComposeRule()

    protected val phone = "w411dp-h914dp-port-xxhdpi"
    protected val tall = "w411dp-h1700dp-port-xhdpi"
    protected val fold = "w932dp-h704dp-land-xhdpi"
    protected val foldTall = "w932dp-h1500dp-land-xhdpi"

    /** The fixtures' day (Sample / Fixtures 2026-11-09), so the D-day rows do not change per run day. */
    protected val day = java.time.LocalDate.of(2026, 11, 9)

    protected val nav = object : Nav { override fun push(route: String) {}; override fun back() {} }
    protected val env = SettingsEnv(
        foldable = true,
        granted = mapOf(Perm.DREAM to false, Perm.NOTIFICATIONS to true, Perm.CALENDAR to true, Perm.FULL_SCREEN to true, Perm.EXACT_ALARM to false, Perm.BATTERY to false, Perm.USAGE to true),
        lux = 2.4f,
        licenseNames = listOf("LICENSE-robotoslab.txt", "OFL-inter.txt", "OFL-nunito.txt", "OFL-pretendard.txt", "OFL-sourceserif4.txt"),
        licenseText = { "Copyright 2020 The Inter Project Authors\nThis Font Software is licensed under the SIL Open Font License, Version 1.1." },
        calendars = {
            listOf(
                kr.dot.sb.data.CalendarInfo(1, "com.google", "me@example.com", "내 일정", 0xFF4D9BEF), kr.dot.sb.data.CalendarInfo(2, "com.google", "me@example.com", "대한민국 공휴일", 0xFF6FDC86),
                kr.dot.sb.data.CalendarInfo(3, "LOCAL", "", "가족", 0xFFFF9C56),
            )
        },
        photoFolders = { f -> f.mapIndexed { i, u -> kr.dot.sb.data.PhotoFolder(u, listOf("Camera", "여행", "KakaoTalk")[i % 3], listOf(1240, 86, 54)[i % 3], broken = i == 2) } },
        chargeTrail = { listOf("11월 12일 23:12 · 무선 충전 → 켜짐", "11월 11일 08:05 · 케이블 충전 → 켜짐") },
        launcherApps = { kr.dot.sb.Fixtures.apps() },
        weekEvents = {
            val d = day.atTime(9, 30)
            listOf(
                kr.dot.sb.data.WeekEvent(1, d, false, "팀 회의"), kr.dot.sb.data.WeekEvent(1, d.plusDays(2).withHour(13), false, "병원 예약"),
                kr.dot.sb.data.WeekEvent(2, d.plusDays(4).withHour(0), true, "임시 공휴일"), kr.dot.sb.data.WeekEvent(3, d.plusDays(1).withHour(19), false, "가족 저녁"),
            )
        },
        io = kotlinx.coroutines.Dispatchers.Unconfined,
        today = { day },
    )
    protected val bar = SettingsEnv(phone = Phone.BAR, foldable = false, granted = Perm.entries.associateWith { true })

    /** [screen] = capture every window (a dialog is a window of its own; onRoot sees only one). */
    @OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
    protected fun snap(name: String, qualifiers: String, config: AppConfig = AppConfig(), route: String, e: SettingsEnv = env, screen: Boolean = false, after: ComposeContentTestRule.() -> Unit = {}) {
        RuntimeEnvironment.setQualifiers(qualifiers)
        rule.setContent {
            var cfg by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(config) }
            SettingsTheme(config.clockStyle(FaceId.DIGITAL).color.c()) {
                androidx.compose.runtime.CompositionLocalProvider(
                    LocalTipDemoProgress provides 0.45f, // Fixed scene frame: no demo coroutine in screenshot runs.
                    kr.dot.sb.ui.cards.LocalAppLookup provides kr.dot.sb.Fixtures.appLookup,
                    kr.dot.sb.ui.cards.LocalUsageApps provides { _, _, n ->
                        if (e.granted[Perm.USAGE] == true) listOf("app.3", "app.1", "app.2", "app.0").take(n) else null
                    },
                ) {
                    RouteHost(route, cfg, { f -> cfg = f(cfg) }, nav, e)
                }
            }
        }
        rule.waitForIdle()
        rule.after()
        rule.waitForIdle()
        if (screen) com.github.takahirom.roborazzi.captureScreenRoboImage("build/shots/settings/$name.png") else rule.onRoot().captureRoboImage("build/shots/settings/$name.png")
    }

    /** Pages on/off: 사진 and 음악 switched off (dimmed, gone from 처음 보이는 페이지 and the preview). */
    protected val someOff = AppConfig().let { c -> c.copy(pagesOff = setOf("photos"), boards = c.boards.map { if (it.id == "music") it.copy(on = false) else it }) }
    protected val folders = AppConfig(photoFolders = listOf("content://t/tree/primary%3ADCIM%2FCamera", "content://t/tree/primary%3APictures%2Ftrip", "content://t/tree/sd%3AKakaoTalk"))
    protected val tallest = "w411dp-h3000dp-port-xhdpi"

    /** The shared apps-card settings with two picked apps. */
    protected val withApps = AppConfig(appsCard = kr.dot.sb.model.SlotOptions(apps = listOf("app.2", "app.0")))
}

/** Home, 테마, card pages, 알림과 알람, 날씨 지역, 권한, 정보, 팁, the first run. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsHomeShots : SettingsShotKit() {
    @Test fun home() = snap("home", phone, route = Routes.HOME)
    @Test fun homeFold() = snap("home-fold", fold, route = Routes.HOME)
    @Test fun homePaper() = snap("home-paper", phone, AppConfig(theme = kr.dot.sb.model.Theme.DAYPAPER), route = Routes.HOME)
    @Test fun homeBar() = snap("home-bar", phone, route = Routes.HOME, e = bar)
    @Test fun homeMoodlight() = snap("home-moodlight", phone, AppConfig(theme = kr.dot.sb.model.Theme.MOODLIGHT), route = Routes.HOME)
    @Test fun theme() = snap("theme", tall, route = Routes.THEME)
    @Test fun themeFold() = snap("theme-fold", fold, route = Routes.THEME)
    @Test fun homeLook() = snap("home-look", tall, AppConfig(theme = kr.dot.sb.model.Theme.DAYPAPER, boardBg = kr.dot.sb.model.BoardBg.SOLID), route = Routes.THEME)
    /** r10 preview pattern: scrolled past the full preview, the 96 dp mini overlays the top. */
    @Test fun themeMini() = snap("theme-mini", "w411dp-h560dp-port-xxhdpi", route = Routes.THEME) {
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 1400f) }
    }
    /** plan-r15 F1: 알림과 알람 (the old 알림 and 알람 screens in one). */
    @Test fun notif() = snap("notif", tall, route = Routes.ALERTS)
    @Test fun notifFold() = snap("notif-fold", foldTall, route = Routes.ALERTS)
    /** plan-r15 F1b: 날씨 지역 lives on 시계 (the city sheet over it). */
    @Test fun weather() = snap("weather", phone, route = Routes.CLOCKS) { onNodeWithText("날씨 지역").performScrollTo().performClick() }
    @Test fun weatherFold() = snap("weather-fold", fold, route = Routes.card(kr.dot.sb.model.CardType.WEATHER)) { onNodeWithText("날씨 지역").performClick() }
    // card gears: a card with settings, one without, the calendar picker (F12) with one calendar open
    @Test fun cardTimer() = snap("card-timer", tall, route = Routes.card(kr.dot.sb.model.CardType.TIMER))
    @Test fun cardMemories() = snap("card-memories", phone, route = Routes.card(kr.dot.sb.model.CardType.APPS))
    @Test fun cardCalendar() = snap("card-calendar", tall, route = Routes.card(kr.dot.sb.model.CardType.CALENDAR)) { onNodeWithText("내 일정").performScrollTo().performClick() }
    @Test fun cardDdayDelete() = snap("card-dday-delete", phone, AppConfig(ddays = kr.dot.sb.Fixtures.ddays), route = Routes.card(kr.dot.sb.model.CardType.DDAY), screen = true) {
        onAllNodes(androidx.compose.ui.test.hasContentDescription("지우기", substring = true)).onFirst().performScrollTo().performClick()
    }
    @Test fun perms() = snap("perms", tall, route = Routes.PERMS)
    @Test fun permsFold() = snap("perms-fold", fold, route = Routes.PERMS)
    @Test fun about() = snap("about", tall, route = Routes.ABOUT) { onNodeWithText("Inter").performScrollTo().performClick() }
    @Test fun aboutFold() = snap("about-fold", fold, route = Routes.ABOUT)
    /** plan-r15 F8: 처음 설정으로 asks once. */
    @Test fun aboutReset() = snap("about-reset", phone, route = Routes.ABOUT, screen = true) { onNodeWithText("처음 설정으로").performScrollTo().performClick() }
    /** Inline tips: collapsed list, the pull-down panel, and the timer after its hold. */
    @Test fun tips() = snap("tips", tallest, route = Routes.TIPS)
    @Test fun tipsOpen() = snap("tips-open", tallest, route = Routes.TIPS) { onNodeWithText("위에서 끌어내리기").performScrollTo().performClick() }
    @Test fun tipsOpenTimer() = snap("tips-open-timer", tallest, route = Routes.TIPS) { onNodeWithText("타이머 가운데 길게 누르기").performScrollTo().performClick() }
    // every scene once at the fixed frame, for review (operator 2026-11-18: tips made properly)
    @Test fun tipClockSwipe() = snap("tip-clock_swipe", tallest, route = Routes.TIPS) { onNodeWithText("시계를 위아래로 넘기기").performScrollTo().performClick() }
    @Test fun tipClockHold() = snap("tip-clock_hold", tallest, route = Routes.TIPS) { onNodeWithText("시계를 길게 누르기").performScrollTo().performClick() }
    @Test fun tipPhotosSwipe() = snap("tip-photos_swipe", tallest, route = Routes.TIPS) { onNodeWithText("사진을 위아래로 넘기기").performScrollTo().performClick() }
    @Test fun tipBoardHold() = snap("tip-board_hold", tallest, route = Routes.TIPS) { onNodeWithText("페이지를 길게 누르기").performScrollTo().performClick() }
    @Test fun tipBannerUp() = snap("tip-banner_up", tallest, route = Routes.TIPS) { onNodeWithText("알림을 위로 밀기").performScrollTo().performClick() }
    @Test fun tipSideKey() = snap("tip-side_key", tallest, route = Routes.TIPS) { onNodeWithText("옆 버튼 누르기").performScrollTo().performClick() }
    @Test fun tipNotifSwipe() = snap("tip-notif_swipe", tallest, route = Routes.TIPS) { onNodeWithText("알림 밀어서 지우기").performScrollTo().performClick() }
    @Test fun tipStackSwipe() = snap("tip-stack_swipe", tallest, route = Routes.TIPS) { onNodeWithText("겹친 카드 넘기기").performScrollTo().performClick() }
    @Test fun tipClockCard() = snap("tip-clock_card", tallest, route = Routes.TIPS) { onNodeWithText("시계 카드 넘기기").performScrollTo().performClick() }
    /** plan-r15 F3: the four first-run steps (phone, flip cover, 780 x 360). */
    private fun firstRun(name: String, q: String, steps: Int) = snap(name, q, route = Routes.FIRST_RUN) {
        repeat(steps) { onNodeWithText(if (it == 2) "나중에" else "다음").performScrollTo().performClick(); waitForIdle() }
    }
    @Test fun firstRun1() = firstRun("first-run-1", tall, 0)
    @Test fun firstRun2() = firstRun("first-run-2", tall, 1)
    @Test fun firstRun3() = firstRun("first-run-3", tall, 2)
    @Test fun firstRun4() = firstRun("first-run-4", tall, 3)
    @Test fun firstRunFlip() = firstRun("first-run-3-flip", "w400dp-h362dp-land-xhdpi", 2)
    @Test fun firstRunBar() = firstRun("first-run-1-bar", "w780dp-h360dp-land-xxhdpi", 0)
}

/** 페이지 구성, the page editors (위젯, 사진, boards, card picker), 밤 화면, 켜짐과 꺼짐. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsPageShots : SettingsShotKit() {
    @Test fun pages() = snap("pages", tall, someOff, route = Routes.PAGES)
    @Test fun pagesFold() = snap("pages-fold", foldTall, someOff, route = Routes.PAGES)
    /** plan-r15 F5: 편집 shows the switches and the drag handles. */
    @Test fun pagesEdit() = snap("pages-edit", tall, someOff, route = Routes.PAGES) { onNodeWithText("편집").performClick() }
    @Test fun pagesAdd() = snap("pages-add", phone, BoardOps.delete(AppConfig(), 0), route = Routes.PAGES) { onNodeWithText("페이지 추가").performScrollTo().performClick() }
    /** plan-r13 B8: 처음 보이는 페이지 is a sheet over 페이지 구성. */
    @Test fun start() = snap("start", phone, someOff.copy(startPage = 5), route = Routes.PAGES) { onNodeWithText("처음 보이는 페이지").performClick() }
    @Test fun widgets() = snap("widgets", tall, route = Routes.WIDGETS)
    @Test fun widgetsFold() = snap("widgets-fold", fold, route = Routes.WIDGETS)
    @Test fun photos() = snap("photos", tall, folders, route = Routes.PHOTOS)
    @Test fun photosFold() = snap("photos-fold", foldTall, folders, route = Routes.PHOTOS)
    @Test fun boardEditor() = snap("board-editor", tall, route = Routes.board("desk"))
    @Test fun boardEditorFold() = snap("board-editor-fold", foldTall, route = Routes.board("modular"))
    @Test fun boardPicker() = snap("board-picker", phone, route = Routes.board("desk")) { onAllNodesWithText("카드 추가")[0].performScrollTo().performClick() }
    /** r14 C1 앱 고르기: two picked (veil + check), then a search. */
    @Test fun appPicker() = snap("app-picker", phone, withApps, route = Routes.card(kr.dot.sb.model.CardType.APPS))
    @Test fun appPickerRecent() = snap("app-picker-recent", phone, withApps, route = Routes.card(kr.dot.sb.model.CardType.APPS)) {
        onNodeWithText("최근 사용").performClick()
        onNode(androidx.compose.ui.test.hasSetTextAction()).assertDoesNotExist()
        onNodeWithText("직접 선택").performClick()
        onNodeWithText("고른 앱 2/8 · 누르면 빼요").assertIsDisplayed()
        onNodeWithText("최근 사용").performClick()
        onNodeWithText("4개").assertIsDisplayed()
    }
    /** plan-r15 E6: 테마 색 picked, the card preview draws the monochrome layers in the accent (app.2 has none). */
    @Test fun appPickerThemed() = snap("app-picker-themed", phone, withApps, route = Routes.card(kr.dot.sb.model.CardType.APPS)) { onNodeWithText("테마 색").performClick() }
    @Test fun appPickerSearch() = snap("app-picker-search", phone, withApps, route = Routes.card(kr.dot.sb.model.CardType.APPS)) { onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("카") }
    @Test fun night() = snap("night", tall, route = Routes.NIGHT)
    @Test fun nightFold() = snap("night-fold", foldTall, route = Routes.NIGHT)
    @Test fun nightNever() = snap("night-never", phone, AppConfig(displayOff = DisplayOff.NEVER), route = Routes.NIGHT)
    @Test fun nightLight() = snap("night-light", phone, AppConfig(theme = kr.dot.sb.model.Theme.DAYPAPER, nightMode = kr.dot.sb.model.NightMode.OFF), route = Routes.NIGHT)
    @Test fun onoff() = snap("onoff", tall, route = Routes.ONOFF) { onNodeWithText("자세히").performScrollTo().performClick() }
    @Test fun onoffFold() = snap("onoff-fold", foldTall, AppConfig(displayOff = DisplayOff.NEVER), route = Routes.ONOFF)
}

/** 시계, 꾸미기, 수면 소리, 알람, 폴드, 카드에 쓰는 정보. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class SettingsClockShots : SettingsShotKit() {
    @Test fun clocks() = snap("clocks", tallest, route = Routes.CLOCKS)
    @Test fun clocksFold() = snap("clocks-fold", "w932dp-h2000dp-port-xhdpi", route = Routes.CLOCKS)
    /** Scrolled past the full preview: the mini carries the face name (+ 이 시계 쓰기). */
    @Test fun clocksMini() = snap("clocks-mini", phone, route = Routes.CLOCKS) {
        onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))[0]
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 1600f) }
    }
    /** Add clocks: the selected strip and all faces, picked ones dimmed with the centred check. */
    @Test fun clocksRotate() = snap("clocks-rotate", tallest, route = Routes.CLOCKS_ROTATE)
    /** A single selected clock still uses the same scrolling grid. */
    @Test fun clocksOne() = snap("clocks-one", phone, AppConfig(myClocks = listOf(FaceId.DIGITAL)), route = Routes.CLOCKS)
    @Test fun clockLed() = snap("clock-led", tall, route = Routes.clock(FaceId.LED))
    @Test fun clockHangul() = snap("clock-hangul", tall, route = Routes.clock(FaceId.HANGUL))
    @Test fun clockAnalog() = snap("clock-analog", tallest, route = Routes.clock(FaceId.ANALOG)) { onNodeWithText("자세히").performScrollTo().performClick() }
    @Test fun clockColorBlock() = snap("clock-colorblock", tall, route = Routes.clock(FaceId.COLOR_BLOCK))
    /** 11-16: 입체 시계's rows down to 배경 투명. */
    @Test fun clockRelief() = snap("clock-relief", tallest, route = Routes.clock(FaceId.RELIEF)) { onNodeWithText("배경 없애기").performScrollTo() }
    /** 색 조합 바꾸기 pressed once: a new combo, [이전 조합] enabled. */
    @Test fun clockShuffle() = snap("clock-shuffle", tall, route = Routes.clock(FaceId.DIGITAL)) { onNodeWithText("색 조합 바꾸기").performClick() }
    @Test fun clockSolarFold() = snap("clock-solar-fold", foldTall, route = Routes.clock(FaceId.SOLAR))
    @Test fun sound() = snap("sound", phone, route = Routes.SOUND) { onAllNodesWithText("파도").let { it[it.fetchSemanticsNodes().size - 1] }.performScrollTo().performClick() } // the card preview has its own chip
    @Test fun soundFold() = snap("sound-fold", fold, route = Routes.SOUND)
    @Test fun alarmBar() = snap("alarm-bar", tall, AppConfig(alarmScreen = false), route = Routes.ALERTS, e = bar)
    /** 자동으로 켜기·끄기 on a Fold with the mood light (펼쳤을 때 lives here now). */
    @Test fun foldScreen() = snap("fold", tallest, AppConfig(onUnfold = kr.dot.sb.model.UnfoldAction.MOOD_LIGHT, moodAutoOffMin = 10), route = Routes.FOLD)
    @Test fun clocksCities() = snap("clocks-cities", phone, route = Routes.CLOCKS) { onNodeWithText("자세히").performScrollTo().performClick(); onNodeWithText("도시 추가").performScrollTo().performClick() }
}
