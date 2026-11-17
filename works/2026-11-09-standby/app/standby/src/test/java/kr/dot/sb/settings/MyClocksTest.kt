package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.Mood
import kr.dot.sb.model.MoodEffect
import kr.dot.sb.model.SlotOptions
import org.json.JSONArray
import org.json.JSONObject
import kr.dot.sb.model.FaceId.ANALOG
import kr.dot.sb.model.FaceId.DIGITAL
import kr.dot.sb.model.FaceId.FLIP
import kr.dot.sb.model.FaceId.HANGUL
import org.junit.Assert.assertEquals
import org.junit.Test

class MyClocksTest {
    private val my = listOf(DIGITAL, FLIP, HANGUL)

    @Test fun addAppendsOnceOnly() {
        assertEquals(my + ANALOG, MyClocks.add(my, ANALOG))
        assertEquals(my, MyClocks.add(my, FLIP))
    }

    @Test fun removeKeepsTheLastClock() {
        assertEquals(listOf(DIGITAL, HANGUL), MyClocks.remove(my, FLIP))
        assertEquals(my, MyClocks.remove(my, ANALOG))
        assertEquals(listOf(FLIP), MyClocks.remove(listOf(FLIP), FLIP))
    }

    @Test fun moveClampsTheTarget() {
        assertEquals(listOf(FLIP, HANGUL, DIGITAL), MyClocks.move(my, 0, 2))
        assertEquals(listOf(HANGUL, DIGITAL, FLIP), MyClocks.move(my, 2, -5))
        assertEquals(listOf(FLIP, HANGUL, DIGITAL), MyClocks.move(my, 0, 99))
        assertEquals(my, MyClocks.move(my, 7, 0))
    }

    @Test fun pageFollowsTheFaceElseClampsTheIndex() {
        assertEquals(2, MyClocks.page(my, HANGUL, 0))
        assertEquals(1, MyClocks.page(listOf(DIGITAL, HANGUL), FLIP, 1))   // shown face removed: stay on that slot
        assertEquals(1, MyClocks.page(listOf(DIGITAL, HANGUL), FLIP, 2))   // ... clamped when the list got shorter
        assertEquals(0, MyClocks.page(my, null, 0))
    }

    // ---- plan-r15 E5

    private fun board(vararg cards: CardType, face: FaceId = DIGITAL) = Board("b", "b", Layout.TWO, cards.map { listOf(it) }, clockFace = face)

    @Test fun tapWithRotationOffReplacesTheFirstClock() {
        assertEquals(listOf(ANALOG, FLIP, HANGUL), MyClocks.pick(my, ANALOG, emptySet()))   // unused old [0] goes: the list never grows
        assertEquals(listOf(HANGUL, DIGITAL, FLIP), MyClocks.pick(my, HANGUL, emptySet()))  // listed: moves to 0, nothing lost
        assertEquals(listOf(ANALOG, DIGITAL, FLIP, HANGUL), MyClocks.pick(my, ANALOG, setOf(DIGITAL)))   // a card draws [0]: it stays
    }

    @Test fun useMovesOrAddsToTheFront() {
        assertEquals(listOf(HANGUL, DIGITAL, FLIP), MyClocks.use(my, HANGUL))
        assertEquals(listOf(ANALOG) + my, MyClocks.use(my, ANALOG))
    }

    @Test fun cardFacesResolveLikeTheCards() {
        val none = AppConfig(myClocks = my, boards = listOf(board(CardType.WEATHER)), widgetLeft = emptyList(), widgetRight = emptyList())
        assertEquals(emptySet<FaceId>(), MyClocks.cardFaces(none))
        // a card set to a face that left the list draws the first board face
        assertEquals(setOf(DIGITAL), MyClocks.cardFaces(none.copy(boards = listOf(board(CardType.CLOCK, face = FaceId.WALL)))))
        val own = board(CardType.CLOCK).copy(options = mapOf("0:0" to SlotOptions(face = FLIP)))
        assertEquals(setOf(FLIP), MyClocks.cardFaces(none.copy(boards = listOf(own))))
    }

    @Test fun clockPageShowsOnlyTheFirstWithoutRotation() {
        val c = AppConfig(myClocks = my)
        assertEquals(my, MyClocks.pages(c))
        assertEquals(listOf(DIGITAL), MyClocks.pages(c.copy(rotateClocks = false)))
    }

    @Test fun rotationMigratesFromTheListSize() {
        fun v14(vararg ids: String) = ConfigCodec.decode(
            JSONObject(ConfigCodec.encode(AppConfig())).put("v", 14).put("myClocks", JSONArray(ids.toList())).apply { remove("rotateClocks") }.toString(),
        ).rotateClocks
        assertEquals(false, v14("FLIP"))
        assertEquals(true, v14("FLIP", "DIGITAL"))
        assertEquals(true, ConfigCodec.decode(null).rotateClocks)   // fresh install rotates the default four
        val off = AppConfig(rotateClocks = false)
        assertEquals(off, ConfigCodec.decode(ConfigCodec.encode(off)))   // v15 keeps the stored flag
    }

    @Test fun moodTakesTheOldGlowColourAndRoundTrips() {
        val old = JSONObject(ConfigCodec.encode(AppConfig(glowColor = 0xFFFF453AL))).put("v", 14).apply { remove("mood") }
        assertEquals(0xFFFF453AL, ConfigCodec.decode(old.toString()).mood.color)
        val m = AppConfig(mood = Mood(1f, 0xFFB48CFFL, MoodEffect.CANDLE, 0.2f), tentClock = false)
        assertEquals(m, ConfigCodec.decode(ConfigCodec.encode(m)))
    }
}
