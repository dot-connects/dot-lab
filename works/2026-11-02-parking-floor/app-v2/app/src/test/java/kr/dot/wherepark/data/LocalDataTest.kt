package kr.dot.wherepark.data

import android.content.Context
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalDataTest {
    private lateinit var ctx: Context
    private lateinit var net: FakeNet

    @Before fun setUp() {
        net = FakeNet(); ctx = Kit.reset(net, mutableListOf())
        File(ctx.filesDir, "photos").deleteRecursively()
    }

    @Test fun familyPhotoIsDownloadedOnceAndCached() {
        CarStore.upsert(ctx, Kit.car("c_1"))
        ParkStore.save(ctx, Kit.park("c_1", 3))
        ParkStore.save(ctx, Kit.park("c_1", 5).copy(photo = true))
        val stale = LocalData.photoFile(ctx, "c_1", 1).also { it.parentFile!!.mkdirs(); it.writeText("old") }
        val prev = LocalData.photoFile(ctx, "c_1", 3).also { it.writeText("prev") }
        net.handler = { m, p, _, _ -> if (m == "GET" && p == "/v2/cars/c_1/photo?park=5") 200 to "JPEGÿØ" else 500 to null }

        val f = LocalData.photo(ctx, ParkStore.latest(ctx, "c_1")!!)
        assertArrayEquals("JPEGÿØ".toByteArray(Charsets.ISO_8859_1), f!!.readBytes())
        assertEquals(LocalData.photoFile(ctx, "c_1", 5), f)
        assertFalse(stale.exists())          // older cached photo of that car is gone
        assertTrue(prev.exists())            // the undo target's photo stays
        assertEquals(listOf("m1.s1"), net.auths.distinct())

        LocalData.photo(ctx, ParkStore.latest(ctx, "c_1")!!)
        assertEquals(1, net.calls.count { it.startsWith("GET /v2/cars/c_1/photo") })  // second look = cache
    }

    @Test fun noPhotoFlagOrMissingPhotoIsQuiet() {
        val p = Kit.park("c_1", 5)
        assertNull(LocalData.photo(ctx, p))
        assertTrue(net.calls.isEmpty())                  // photo=false: never asks the server
        net.handler = { _, _, _, _ -> 404 to """{"error":"photo"}""" }
        assertNull(LocalData.photo(ctx, p.copy(photo = true)))
        assertFalse(LocalData.photoFile(ctx, "c_1", 5).exists())
        assertTrue(FamilyStore.joined(ctx))
    }

    @Test fun wipeClearsEverythingAndDropsAPullInFlight() {
        CarStore.upsert(ctx, Kit.car("c_1"))
        ParkStore.save(ctx, Kit.park("c_1", 5))
        CarLinkStore.set(ctx, "c_1", "AA:BB:CC:DD:EE:01")
        CarLinkStore.set(ctx, "c_gone", "AA:BB:CC:DD:EE:02")   // a link whose car was never saved
        SyncQueue.add(ctx, Op.PutCar(Kit.car("c_1")))
        LocalData.photoFile(ctx, "c_1", 5).also { it.parentFile!!.mkdirs(); it.writeText("x") }
        val genBefore = SyncQueue.generation(ctx)   // a pull read this before the wipe

        LocalData.wipe(ctx)

        assertNotEquals(genBefore, SyncQueue.generation(ctx))
        assertFalse(Merge.state(ctx, ApiState("우리", listOf(Kit.me), listOf(Kit.car("c_1")), mapOf("c_1" to Kit.park("c_1", 5))), genBefore))
        assertTrue(CarStore.cars(ctx).isEmpty())
        assertNull(ParkStore.latest(ctx, "c_1"))
        assertTrue(CarLinkStore.carIds(ctx).isEmpty())
        assertTrue(SyncQueue.ops(ctx).isEmpty())
        assertFalse(FamilyStore.joined(ctx))
        assertFalse(File(ctx.filesDir, "photos").exists())
    }
}
