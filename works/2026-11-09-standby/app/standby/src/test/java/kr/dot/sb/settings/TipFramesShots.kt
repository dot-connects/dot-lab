package kr.dot.sb.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.theme.clockStyle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review frames of every 설정 › 팁 scene (operator 2026-11-18 asked to see all tips): [FRAMES] fixed progress steps
 * per tip into build/shots/tip-frames/<tip>/NN.png, stitched into a looping preview outside the test.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class TipFramesShots {
    @get:Rule val rule = createComposeRule()

    @Test fun frames() {
        RuntimeEnvironment.setQualifiers("w640dp-h360dp-land-xhdpi")
        val progress = mutableFloatStateOf(0f)
        var tip by androidx.compose.runtime.mutableStateOf(Tip.PULL_DOWN)
        val config = AppConfig()
        val env = SettingsEnv(phone = Phone.FOLD, foldable = true, granted = Perm.entries.associateWith { true }, io = kotlinx.coroutines.Dispatchers.Unconfined)
        rule.setContent {
            SettingsTheme(config.clockStyle(FaceId.DIGITAL).color.c()) {
                CompositionLocalProvider(LocalSettingsEnv provides env, LocalTipDemoProgress provides progress.floatValue) {
                    Box(Modifier.fillMaxWidth().padding(8.dp)) { TipDemo(tip, config, active = true, Modifier.fillMaxWidth()) }
                }
            }
        }
        Tip.entries.forEach { t ->
            tip = t
            for (i in 0 until FRAMES) {
                progress.floatValue = i / FRAMES.toFloat()
                rule.waitForIdle()
                rule.onRoot().captureRoboImage("build/shots/tip-frames/${t.name.lowercase()}/%02d.png".format(i))
            }
        }
    }

    private companion object { const val FRAMES = 24 }
}
