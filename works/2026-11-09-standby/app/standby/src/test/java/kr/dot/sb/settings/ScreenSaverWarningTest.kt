package kr.dot.sb.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** plan-r16 T9: warn only while the system screen saver is on and set to ours; unreadable = no warning. */
class ScreenSaverWarningTest {
    private val pkg = "kr.dot.standby"
    private val ours = "$pkg/kr.dot.sb.platform.StandbyDream"

    @Test fun table() {
        assertEquals(true, Perms.screenSaverWarning(1, ours, pkg))
        assertEquals(true, Perms.screenSaverWarning(1, "com.android.deskclock/.Screensaver, $ours", pkg))
        assertEquals(false, Perms.screenSaverWarning(0, ours, pkg))
        assertEquals(false, Perms.screenSaverWarning(1, "com.android.deskclock/.Screensaver", pkg))
        assertEquals(false, Perms.screenSaverWarning(1, "kr.dot.standscreen/kr.dot.sb.platform.StandbyDream", pkg))
        assertEquals(false, Perms.screenSaverWarning(null, null, pkg))
    }
}
