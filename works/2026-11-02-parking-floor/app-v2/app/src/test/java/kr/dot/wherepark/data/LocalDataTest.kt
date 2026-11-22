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
        CarStore.upsert(ctx, Kit.car("c_00000001"))
        ParkStore.save(ctx, Kit.park("c_00000001", 3))
        ParkStore.save(ctx, Kit.park("c_00000001", 5).copy(photo = true))
        val stale = LocalData.photoFile(ctx, "c_00000001", 1).also { it.parentFile!!.mkdirs(); it.writeText("old") }
        val prev = LocalData.photoFile(ctx, "c_00000001", 3).also { it.writeText("prev") }
        net.handler = { m, p, _, _ -> if (m == "GET" && p == "/v3/cars/c_00000001/photo?park=5") 200 to "JPEGÿØ" else 500 to null }

        val f = LocalData.photo(ctx, ParkStore.latest(ctx, "c_00000001")!!)
        assertArrayEquals("JPEGÿØ".toByteArray(Charsets.ISO_8859_1), f!!.readBytes())
        assertEquals(LocalData.photoFile(ctx, "c_00000001", 5), f)
        assertFalse(stale.exists())          // older cached photo of that car is gone
        assertTrue(prev.exists())            // the undo target's photo stays
        assertEquals(listOf("m1.s1"), net.auths.distinct())

        LocalData.photo(ctx, ParkStore.latest(ctx, "c_00000001")!!)
        assertEquals(1, net.calls.count { it.startsWith("GET /v3/cars/c_00000001/photo") })  // second look = cache
    }

    @Test fun noPhotoFlagOrMissingPhotoIsQuiet() {
        val p = Kit.park("c_00000001", 5)
        assertNull(LocalData.photo(ctx, p))
        assertTrue(net.calls.isEmpty())                  // photo=false: never asks the server
        net.handler = { _, _, _, _ -> 404 to """{"error":"photo"}""" }
        assertNull(LocalData.photo(ctx, p.copy(photo = true)))
        assertFalse(LocalData.photoFile(ctx, "c_00000001", 5).exists())
        assertTrue(GroupStore.joined(ctx))
    }

    @Test fun wipeClearsEverythingAndDropsAPullInFlight() {
        CarStore.upsert(ctx, Kit.car("c_00000001"))
        ParkStore.save(ctx, Kit.park("c_00000001", 5))
        CarLinkStore.set(ctx, "c_00000001", "AA:BB:CC:DD:EE:01")
        CarLinkStore.set(ctx, "c_gone", "AA:BB:CC:DD:EE:02")   // a link whose car was never saved
        SyncQueue.add(ctx, Op.PutCar(Kit.car("c_00000001")))
        LocalData.photoFile(ctx, "c_00000001", 5).also { it.parentFile!!.mkdirs(); it.writeText("x") }
        val genBefore = SyncQueue.generation(ctx)   // a pull read this before the wipe

        LocalData.wipe(ctx)

        assertNotEquals(genBefore, SyncQueue.generation(ctx))
        assertFalse(Merge.state(ctx, ApiState(Kit.me, listOf(Group("g1", "우리", true, listOf(Kit.me))), listOf(Kit.car("c_00000001")), mapOf("c_00000001" to Kit.park("c_00000001", 5))), genBefore))
        assertTrue(CarStore.cars(ctx).isEmpty())
        assertNull(ParkStore.latest(ctx, "c_00000001"))
        assertTrue(CarLinkStore.carIds(ctx).isEmpty())
        assertTrue(SyncQueue.ops(ctx).isEmpty())
        assertFalse(GroupStore.joined(ctx))
        assertFalse(File(ctx.filesDir, "photos").exists())
    }
}
