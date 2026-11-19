package kr.dot.wherepark.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import kr.dot.wherepark.Device
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.data.CarStore
import kr.dot.wherepark.data.FakeNet
import kr.dot.wherepark.data.Kit
import kr.dot.wherepark.shot
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The listen sheet's states at the mockup phone size -> app-v2/shots/listen/ (mockups/v2/final section 4). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ListenShots {
    @get:Rule val rule = createComposeRule()
    private val ctx get() = Kit.ctx()

    @Before fun setUp() {
        Kit.reset(FakeNet(), mutableListOf())
        CarStore.upsert(ctx, Kit.car("w", 0).copy(name = "흰 차", color = 0xFFF4F1EA.toInt()))
        CarStore.upsert(ctx, Kit.car("g", 1).copy(name = "회색 차", color = 0xFF8A8C8E.toInt()))
    }

    private fun model(from: String = "widget") = ListenModel(ctx, "w", null, from)

    private fun render(name: String, m: ListenModel) = rule.shot("listen", name, Device.MOCK_PHONE) {
        ListenSheet(m, SheetActions()) { Box(Modifier.fillMaxSize().background(Color(0xFFE9E7E3))) }
    }

    private val b2c37 = Spot(Floor.Under(2), "C", 37, null, "엘리베이터 옆")

    @Test fun listening() = render("1-listening", model().apply { state = Sheet.Listening("지하 2층 C", rms = 10f) })

    @Test fun saved() = render("2-saved", model().apply {
        state = Sheet.Saved(Park(1, "w", b2c37, 1, "m1", "나", null, false, ParkState.PARKED), ListenModel.SENT)
    })

    @Test fun manual() = render("3-manual", model().apply {
        state = Sheet.Manual(ManualWhy.FAILED, b2c37.copy(memo = null), listOf(Floor.Under(2), Floor.Under(3)))
    })

    @Test fun ambiguous() = render("4-ambiguous", model().apply { result("지하 이층 이 구역 삼십칠") })

    @Test fun practice() = render("5-practice", model("practice").apply { result("지하 2층 C 37") })

    @Test fun noPack() = render("6-nopack", model().apply { packMissing() })

    @Test fun askMic() = render("7-askmic", model().apply { micDenied(0) })
}
