package kr.dot.wherepark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.ZoneId
import java.time.ZonedDateTime
import kr.dot.wherepark.Device
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.core.SpotParser
import kr.dot.wherepark.data.CarLinkStore
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.FamilyStore
import kr.dot.wherepark.data.Invite
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.data.Member
import kr.dot.wherepark.data.ParkStore
import kr.dot.wherepark.family.FamilyScreen
import kr.dot.wherepark.family.InviteScreen
import kr.dot.wherepark.family.JoinModel
import kr.dot.wherepark.family.JoinScreen
import kr.dot.wherepark.groupOn
import kr.dot.wherepark.shotPath
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * App screens at the mockup phone size -> app-v2/shots/app/ (mockups/v2/final sections 2-8). A fake 30 dp status bar
 * stands in for the mockup's .sb so both line up from the top. Contact sheet: tools/sheets.py app.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppShots {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = Kit.ctx()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = ZonedDateTime.of(2026, 11, 20, 16, 36, 0, 0, zone).toInstant().toEpochMilli()
    private fun at(day: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private val white = Car("w", "흰 차", 0xFFF4F1EA.toInt(), "12가 3456", 0)
    private val grey = Car("g", "회색 차", 0xFF8E9399.toInt(), null, 1)
    private val wPark = Park(at(20, 14, 32), "w", Spot(Floor.Under(2), "C", 37, null, "엘리베이터 옆, 3번 출구 쪽"), at(20, 14, 32), "m1", "아빠", null, false, ParkState.PARKED)
    private val gPark = Park(at(19, 19, 5), "g", Spot(Floor.Under(4), "A", 12, null, null), at(19, 19, 5), "m2", "엄마", null, false, ParkState.DRIVING, "엄마")

    @Before fun setUp() {
        Kit.reset(FakeNet(), mutableListOf())
        FamilyStore.save(ctx, "m1", "s1", "민지네 가족", listOf(Kit.me.copy(name = "아빠"), Member("m2", "엄마", false, false, now), Member("m3", "민지", false, false, now)))
        CarStore.upsert(ctx, white); CarStore.upsert(ctx, grey)
        ParkStore.save(ctx, gPark); ParkStore.save(ctx, wPark)
        CarLinkStore.set(ctx, "w", "AA:BB:CC:DD:EE:01"); CarLinkStore.set(ctx, "g", "AA:BB:CC:DD:EE:02")
    }

    @Composable
    private fun StatusBar(bg: Color = Wp.BG) = Row(Modifier.fillMaxWidth().height(30.dp).background(bg).padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("14:36", Modifier.weight(1f), style = ts(12.5.sp, FontWeight.SemiBold, family = gro(600)))
        Text("● ▲ 82%", style = ts(12.5.sp, FontWeight.SemiBold, family = gro(600)))
    }

    private fun phone(name: String, device: Device = Device.MOCK_PHONE, qualifiers: String? = null, bar: Color = Wp.BG, scale: Float = 1f, content: @Composable () -> Unit) {
        if (!groupOn("app")) return
        RuntimeEnvironment.setQualifiers(qualifiers ?: device.qualifiers)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * scale)) {
                Column(Modifier.fillMaxSize().background(bar)) { StatusBar(bar); Box(Modifier.weight(1f)) { content() } }
            }
        }
        rule.mainClock.advanceTimeByFrame()
        rule.waitForIdle()
        rule.onRoot().captureRoboImage(shotPath("app", name, device))
        texts(shotPath("app", name, device))
    }

    /** Text boxes (dp, root coordinates) next to the PNG, for tools/sheets.py to match against the mockup's DOM boxes. */
    private fun texts(png: String, origin: androidx.compose.ui.geometry.Offset = androidx.compose.ui.geometry.Offset.Zero) {
        val density = ctx.resources.displayMetrics.density
        val out = org.json.JSONArray()
        fun walk(n: androidx.compose.ui.semantics.SemanticsNode) {
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.joinToString("") { it.text }?.takeIf { it.isNotBlank() }?.let { t ->
                val b = n.boundsInRoot
                out.put(org.json.JSONObject().put("text", t).put("x", ((b.left - origin.x) / density).toDouble()).put("y", ((b.top - origin.y) / density).toDouble())
                    .put("w", (b.width / density).toDouble()).put("h", (b.height / density).toDouble()))
            }
            n.children.forEach(::walk)
        }
        walk(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        java.io.File(png.removeSuffix(".png") + ".json").writeText(out.toString())
    }

    private fun homeData() = HomeData.load(ctx, now, micReady = true)

    @Test fun home() = phone("home", qualifiers = "w326dp-h736dp-port-xhdpi") { HomeScreen(homeData(), AppActions()) }

    /** Accessibility check: 200 % font, the chips must wrap and nothing may be cut. */
    @Test fun homeFont200() = phone("home-font200", qualifiers = "w326dp-h736dp-port-xhdpi", scale = 2f) { HomeScreen(homeData(), AppActions()) }

    @Test fun detail() = phone("detail") { DetailScreen(white, wPark, null, now, DetailActions()) }

    @Test fun plate() = phone("plate", bar = Color.White) { PlateScreen(white, false) {} }

    @Test fun settings() = phone("settings", qualifiers = "w326dp-h976dp-port-xhdpi") { SettingsScreen(SettingsActions()) }

    @Test fun settingsOneCar() {
        CarStore.remove(ctx, "g")
        phone("settings-onecar", qualifiers = "w326dp-h976dp-port-xhdpi") { SettingsScreen(SettingsActions()) }
    }

    @Test fun carEdit() = phone("caredit") { CarEditScreen(CarDraft(white), isNew = false, linked = true, a = EditActions()) }

    @Test fun onboarding1() = phone("ob1") { Step1 {} }

    @Test fun onboarding2() = phone("ob2") {
        Step2(CarStep("new").apply { car = "흰 차"; me = "아빠"; packDownloading = true }, {}, {})
    }

    @Test fun onboarding3() = phone("ob3-practice") {
        Step3(Practice(mic = true, pack = true, raw = "지하 2층 C37", spot = SpotParser.parse("지하 2층 C37").spot), {}, {})
    }

    @Test fun onboarding4() = phone("ob4") { Step4({}, {}, {}) }

    @Test fun invite() = phone("invite") {
        InviteScreen(Invite("K7QM-28XP-4HDA", 0, "https://dot-lab-parking.dotconnects.workers.dev/j#K7QM-28XP-4HDA"), false, {}, {}, {}, {})
    }

    @Test fun join() {
        kr.dot.wherepark.data.Api.transport = FakeNet().apply { handler = { _, _, _, _ -> 200 to """{"groupName":"민지네 가족","count":3}""" } }
        val m = JoinModel(ctx, bg = { it() }, main = { it() }).apply { useCode("K7QM-28XP-4HDA"); name = "엄마" }
        phone("join") { JoinScreen(m, null) {} }
    }

    @Test fun family() {
        val day = 24 * 3600_000L
        val ms = listOf(Member("m1", "아빠", true, true, now), Member("m2", "엄마", false, false, now - 2 * 3600_000L),
            Member("m3", "민지", false, false, now - 3 * day), Member("m4", "옛날 폰 (민지)", false, false, now - 41 * day))
        phone("family") { FamilyScreen("민지네 가족", ms, now, null, {}, {}, {}, {}) }
    }

    // home card states (mockup section 2, the 300 px state grid: 44/24 numerals, no chips)

    private fun card(name: String, car: Car, park: Park?, pending: Boolean = false, mic: Boolean = true) {
        if (!groupOn("app")) return
        RuntimeEnvironment.setQualifiers("w360dp-h400dp-port-xhdpi")
        rule.setContent {
            Box(Modifier.fillMaxSize().background(Color(0xFFECEBE8))) {
                Box(Modifier.width(300.dp).testTag("card")) {
                    HomeCard(car, park, HomeCards.of(park, pending, mic, CarLinkStore.address(ctx, car.id) != null, now), AppActions(), big = false, chips = false)
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("card").captureRoboImage(shotPath("app", name))
        texts(shotPath("app", name), rule.onNodeWithTag("card").fetchSemanticsNode().boundsInRoot.topLeft)
    }

    @Test fun stateNone() = card("st0-none", white, null)
    @Test fun statePending() {
        val p = wPark.copy(id = now - 10_000, atMs = now - 10_000, spot = wPark.spot.copy(memo = null))
        card("st1-pend", white, p, pending = true)
    }
    @Test fun stateOk() = card("st2-ok", white, wPark)
    @Test fun stateDriving() = card("st3-gone", grey, gPark)
    @Test fun stateOld() = card("st4-old", white, wPark.copy(atMs = at(16, 14, 32), spot = wPark.spot.copy(memo = null)))
    @Test fun stateNoMic() = card("st5-nomic", white, wPark, mic = false)
}
