package kr.dot.catchpet

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class PetFileTest {
    private val v = JSONObject(javaClass.getResource("/petfile-vector.json")!!.readText())

    @Test fun sharedVectorMatchesWeb() {
        val r = PetFile.parse(v.getJSONObject("file").toString())
        val exp = v.getJSONObject("expect")
        val ec = exp.getJSONArray("creatures")
        assertEquals(ec.length(), r.creatures.size)
        for (i in 0 until ec.length()) {
            val e = ec.getJSONObject(i)
            val c = r.creatures[i]
            assertEquals(e.getString("id"), c.id)
            assertEquals(e.getString("name"), c.name)
            assertEquals(e.getString("kind"), c.kind)
            assertEquals(e.getInt("no"), c.no)
            assertEquals(e.getJSONArray("labels").toList(), c.poses.map { it.label })
        }
        assertEquals(exp.getJSONObject("dropped").getInt("poses"), r.droppedPoses)
        assertEquals(exp.getJSONObject("dropped").getInt("creatures"), r.droppedCreatures)
        assertEquals(25, r.creatures[1].pokeId)
        // the kept picture is the first of its label
        val first = v.getJSONObject("file").getJSONArray("creatures").getJSONObject(0)
            .getJSONArray("poses").getJSONObject(0).getString("png")
        assertArrayEquals(Base64.getDecoder().decode(first), r.creatures[0].poses[0].png)
    }

    @Test fun rejectsWrongFormatVersionMissingListOrNonJson() {
        val rej = v.getJSONArray("reject")
        val bad = (0 until rej.length()).map { rej.getJSONObject(it).toString() } + "{nope"
        for (text in bad) {
            try { PetFile.parse(text); fail("accepted $text") } catch (e: RuntimeException) { /* expected */ }
        }
    }

    @Test fun movesMirrorWeb() {
        assertEquals(emptyList<String>(), PetFile.moves(emptyList()))
        assertEquals(listOf("bounce", "flinch"), PetFile.moves(listOf("front")))
        assertEquals(listOf("bounce", "flinch", "hide"), PetFile.moves(listOf("back")))
        assertEquals(listOf("bounce", "flinch", "walk"), PetFile.moves(listOf("side")))
        assertEquals(listOf("bounce", "flinch"), PetFile.moves(listOf("walk")))
        assertEquals(listOf("bounce", "flinch", "walk", "run"), PetFile.moves(listOf("side", "walk")))
        assertEquals(listOf("bounce", "flinch", "sleep"), PetFile.moves(listOf("lying")))
        assertEquals(listOf("bounce", "flinch", "walk", "run", "sleep", "hide"), PetFile.moves(PetFile.LABELS))
        assertEquals(listOf("bounce", "flinch"), PetFile.moves(listOf("front", "front", "nope")))
        assertEquals("모습 2/5", PetFile.count(listOf("front", "side"), "dog"))
        assertEquals("모습 1/1", PetFile.count(listOf("front"), "pokemon"))
    }
}
