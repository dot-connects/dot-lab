package kr.dot.catchpet.pet

import android.content.pm.PackageManager
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import kr.dot.catchpet.ar.Critters
import kr.dot.catchpet.dex.Dex
import kr.dot.catchpet.ui.PetScreen
import kr.dot.catchpet.ui.setPet
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class PetAppTest {
    @get:Rule val rule = createComposeRule()

    /** A renamed or moved class in the manifest only fails on the phone; catch it here (plan R5). */
    @Test fun everyManifestComponentExists() {
        val ctx = RuntimeEnvironment.getApplication()
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES)
        val names = (info.activities.orEmpty().map { it.name } + info.services.orEmpty().map { it.name })
        assertTrue("services: $names", names.contains("kr.dot.catchpet.pet.PetService"))
        assertTrue("services: $names", names.contains("kr.dot.catchpet.pet.PetWallpaper"))
        for (n in names) Class.forName(n)
    }

    @Test fun opaqueBoxFindsTheVisiblePixels() {
        val px = IntArray(5 * 4)
        px[1 * 5 + 2] = 0xFF000000.toInt()
        px[3 * 5 + 3] = 0x80FFFFFF.toInt()
        px[0] = 0x10FFFFFF   // too faint to count
        assertArrayEquals(intArrayOf(2, 1, 3, 3), Frames.opaqueBox(px, 5, 4))
        assertNull(Frames.opaqueBox(IntArray(4), 2, 2))
    }

    @Test fun missingCritterPicturesFallBack() {
        val ctx = RuntimeEnvironment.getApplication()
        assertNotNull(Frames.asset(ctx, "no-such-critter", "dir3"))   // -> _placeholder/front.png
        val e = Dex.add(ctx, "critter", null, "monggeul", "dir0")
        val cells = Frames.load(ctx, e)!!
        assertEquals(Frames.CELLS, cells.keys.toList())
    }

    @Test fun azimuthDifferenceWraps() {
        assertEquals(-10f, Critters.diff(350f, 0f), 1e-3f)
        assertEquals(10f, Critters.diff(-175f, 175f), 1e-3f)
        assertEquals(0f, Critters.diff(720f, 0f), 1e-3f)
    }

    @Test fun petScreenShot() {
        val ctx = RuntimeEnvironment.getApplication()
        Dex.root(ctx).deleteRecursively()
        val e = Dex.add(ctx, "critter", null, "monggeul", "dir0")
        Dex.add(ctx, "critter", null, "ppiyak", "dir0")
        setPet(ctx, e.id)
        rule.setContent { PetScreen {} }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/outputs/roborazzi/pet.png")
    }
}
