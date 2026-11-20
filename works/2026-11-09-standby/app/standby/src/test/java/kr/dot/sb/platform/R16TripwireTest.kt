package kr.dot.sb.platform

import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** plan-r16 T0: the dream stub must never end itself or keep the panel bright (that line woke the phone all night). */
class R16TripwireTest {
    private val src = File("src/main/java/kr/dot/sb/platform/StandbyDream.kt").readText()

    @Test fun dreamNeverFinishes() = assertFalse(src.contains("finish("))

    @Test fun dreamNeverKeepsTheScreenBright() = assertFalse(src.contains("isScreenBright = true"))
}
