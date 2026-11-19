package kr.dot.wherepark.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kr.dot.wherepark.core.Car
import kr.dot.wherepark.core.Floor
import kr.dot.wherepark.core.Park
import kr.dot.wherepark.core.ParkState
import kr.dot.wherepark.core.Spot
import kr.dot.wherepark.core.newCarId
import kr.dot.wherepark.ui.Onboarding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real HttpTransport against the deployed Worker (BuildConfig.SYNC_URL). Skipped unless -Pwp.live=true.
 * Creates a throwaway family through the onboarding path and deletes it (DELETE /v2/me) at the end.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LiveSyncTest {
    @Test fun onboardingParkPhotoRoundTrip() {
        assumeTrue("set -Pwp.live=true", System.getProperty("wp.live") == "true")
        val ctx: Context = ApplicationProvider.getApplicationContext()
        Prefs.sp(ctx).edit().clear().commit()
        SyncQueue.clear(ctx)
        Api.transport = HttpTransport
        val notices = mutableListOf<String>()
        SyncEngine.notify = { _, t -> notices += t }
        kr.dot.wherepark.family.JoinFlow.startPush = {}
        try {
            val car = Car(newCarId(), "라이브 차", -1, null, 0)
            Onboarding.saveCar(ctx, "라이브", car)
            val now = System.currentTimeMillis()
            val park = Park(now, car.id, Spot(Floor.Under(2), "C", 37, null, "live test"), now, "", "라이브", "지하 2층 C37", false, ParkState.PARKED)
            synchronized(SyncQueue) { ParkStore.save(ctx, park); SyncQueue.add(ctx, Op.PutPark(park)) }

            assertTrue("sync run", SyncEngine.run(ctx))
            assertTrue("joined", FamilyStore.joined(ctx))
            assertTrue("queue empty: ${SyncQueue.ops(ctx)}", SyncQueue.ops(ctx).isEmpty())
            assertEquals(listOf(car.id), CarStore.cars(ctx).map { it.id })
            assertEquals(park.id, ParkStore.latest(ctx, car.id)!!.id)
            val st = Api.state(ctx).getOrThrow()
            assertEquals(listOf(car.id), st.cars.map { it.id })
            assertEquals(park.id, st.parks[car.id]!!.id)
            assertEquals(Floor.Under(2), st.parks[car.id]!!.spot.floor)
            println("LIVE state: group=${st.groupName} members=${st.members.size} car=${car.id} park=${park.id}")

            // photo: ~10 KB JPEG up through the queue, down through the family photo path
            val bmp = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
            val rnd = java.util.Random(7)
            for (y in 0 until 160) for (x in 0 until 160) bmp.setPixel(x, y, Color.rgb(rnd.nextInt(256), x, y))
            val f = File(ctx.cacheDir, "live.jpg").apply { outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) } }
            println("LIVE photo file: ${f.length()} B")
            SyncQueue.add(ctx, Op.PutPhoto(car.id, park.id, f.path))
            assertTrue("photo sync", SyncEngine.run(ctx))
            assertTrue("photo op sent", SyncQueue.ops(ctx).isEmpty())
            val got = Api.photo(ctx, car.id, park.id)
            assertNotNull("photo download", got)
            assertTrue("jpeg", got!![0] == 0xFF.toByte() && got[1] == 0xD8.toByte())
            assertTrue("server photo flag", Api.state(ctx).getOrThrow().parks[car.id]!!.photo)
            assertTrue("local photo flag after pull", ParkStore.latest(ctx, car.id)!!.photo)
            println("LIVE photo download: ${got.size} B, notices=$notices")
            assertTrue(notices.isEmpty())
        } finally {
            if (FamilyStore.joined(ctx)) println("LIVE cleanup deleteMe: ${Api.deleteMe(ctx).isSuccess}")
        }
    }
}
