package kr.dot.wherepark

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.RuntimeEnvironment

/**
 * Screenshot harness (copied from StandBy's ShotKit, trimmed to what this app needs). Each device is a Robolectric
 * qualifier string; shots land in app-v2/shots/<group>/<name>-<device>.png (run with -Proborazzi.test.record=true).
 * Tests must use @GraphicsMode(NATIVE) and @Config(sdk = [34]).
 */
enum class Device(val qualifiers: String, val label: String) {
    /** The mockup phone's screen (340 px frame minus the 7 px border), at 2x so it lines up with a 2x Chrome capture. */
    MOCK_PHONE("w326dp-h706dp-port-xhdpi", "mock"),
    BAR_P("w360dp-h780dp-port-xxhdpi", "bar-port"),
}

/** `-Pshot.group=listen` captures only those groups; the rest still runs (cheap) but writes nothing. */
private val GROUPS: Set<String> = System.getProperty("shot.group").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
fun groupOn(group: String): Boolean = GROUPS.isEmpty() || group in GROUPS

fun shotPath(group: String, name: String, device: Device? = null): String =
    "../shots/$group/$name${device?.let { "-" + it.label } ?: ""}.png"

/** Render [content] on [device]; the clock is held still so timers (undo bar) show their first frame. */
fun ComposeContentTestRule.shot(
    group: String, name: String, device: Device = Device.MOCK_PHONE,
    /** Runs after the first frame, before the capture. */
    act: (ComposeContentTestRule.() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (!groupOn(group)) return
    RuntimeEnvironment.setQualifiers(device.qualifiers)
    mainClock.autoAdvance = false
    setContent(content)
    mainClock.advanceTimeByFrame()
    waitForIdle()
    act?.invoke(this)
    onRoot().captureRoboImage(shotPath(group, name, device))
}
