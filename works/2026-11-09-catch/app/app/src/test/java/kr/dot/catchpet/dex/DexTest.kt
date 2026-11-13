package kr.dot.catchpet.dex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DexTest {
    private val ctx get() = RuntimeEnvironment.getApplication()
    private val png = byteArrayOf(1, 2, 3)

    @Before fun clean() { Dex.root(ctx).deleteRecursively() }

    @Test fun sameCritterMergesAndANewViewAddsAPose() {
        val a = Dex.add(ctx, "critter", null, "monggeul", "dir0")
        val b = Dex.add(ctx, "critter", null, "monggeul", "dir1")   // also front: no new pose
        val c = Dex.add(ctx, "critter", null, "monggeul", "dir4")
        assertEquals(a.id, b.id); assertEquals(a.id, c.id)
        assertEquals(listOf("front", "back"), c.poses)
        assertEquals("몽글", c.name)
        assertEquals(1, Dex.list(ctx).size)
        assertEquals(listOf("front", "front", "side", "back", "back", "back", "side", "front"), (0..7).map { Dex.poseOf("dir$it") })
    }

    @Test fun realCatchesAreNewEntriesNumberedPerKindWithOneRunningNo() {
        val d1 = Dex.add(ctx, "dog", png, null, "front")
        val k = Dex.add(ctx, "critter", null, "ppiyak", "dir2")
        val c1 = Dex.add(ctx, "cat", png, null, "front")
        val d2 = Dex.add(ctx, "dog", png, null, "front")
        assertEquals(listOf("강아지 1", "삐약", "고양이 1", "강아지 2"), listOf(d1, k, c1, d2).map { it.name })
        assertEquals(listOf(1, 2, 3, 4), Dex.list(ctx).map { it.no })
        assertTrue(Dex.list(ctx).all { Regex("^[A-Za-z0-9_-]{1,64}$").matches(it.id) })   // the Worker's entry regex
        assertTrue(Dex.file(ctx, d2.id, "front.png").readBytes().contentEquals(png))
    }

    @Test fun survivesARestartAndKeepsAiState() {
        val e = Dex.add(ctx, "person", png, null, "front")
        Dex.savePreview(ctx, e.id, byteArrayOf(9))
        Dex.savePreview(ctx, e.id, byteArrayOf(8))
        Dex.saveSheet(ctx, e.id, byteArrayOf(7))
        val back = Dex.get(ctx, e.id)!!     // a fresh read from disk, as after an app restart
        assertEquals(listOf("ai/preview1.webp", "ai/preview2.webp"), back.ai.previews)
        assertEquals("ai/sheet.webp", back.ai.sheet)
        assertTrue(back.ai.owned)
        assertEquals("사람 1", back.name)
        assertTrue(Dex.file(ctx, e.id, "ai/preview2.webp").readBytes().contentEquals(byteArrayOf(8)))
    }

    @Test fun corruptOrHalfWrittenEntriesAreSkippedAndTheirNumbersNotReused() {
        Dex.add(ctx, "dog", png, null, "front")
        val bad = Dex.dir(ctx, "e2").apply { mkdirs() }
        File(bad, "meta.json").writeText("{nope")
        Dex.dir(ctx, "e3").mkdirs()                                     // crash before meta.json
        File(Dex.dir(ctx, "e1"), "meta.json.tmp").writeText("partial")  // crash mid-write of e1
        assertEquals(listOf("e1"), Dex.list(ctx).map { it.id })
        assertNull(Dex.get(ctx, "e2"))
        val next = Dex.add(ctx, "dog", png, null, "front")
        assertEquals(4, next.no)
        assertEquals("강아지 2", next.name)
        assertFalse(File(Dex.dir(ctx, "e4"), "meta.json.tmp").exists())   // written by rename
    }

    @Test fun speciesNamesAndTheirParticle() {
        val h = Dex.add(ctx, "hamster", png, null, "front")
        assertEquals("햄스터 1", h.name)
        assertEquals("햄스터를", Dex.withObject("햄스터"))
        assertEquals("곰을", Dex.withObject(Dex.kindName("bear")))
        assertEquals("사람을", Dex.withObject(Dex.kindName("person")))
    }

    @Test fun getRejectsIdsThatAreNotEntries() {
        assertNull(Dex.get(ctx, "../x"))
    }
}
