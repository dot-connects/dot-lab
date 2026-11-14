package kr.dot.sb.settings

import kr.dot.sb.model.AppConfig
import kr.dot.sb.model.Board
import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import kr.dot.sb.model.Layout
import kr.dot.sb.model.SlotOptions
import org.junit.Assert.assertEquals
import org.junit.Test

/** 11-14 #7: the face picked with the clock card's arrows is saved to that one card (boards and the widgets page). */
class CardFaceTest {
    private val b = Board("b1", "b", Layout.TWO, listOf(listOf(CardType.CLOCK, CardType.WEATHER), listOf(CardType.CLOCK)),
        options = mapOf("0:0" to SlotOptions(hideAtNight = true)))
    private val c = AppConfig(boards = listOf(b))

    @Test fun boardCardKeepsItsOtherOptions() {
        val out = BoardOps.setFace(c, "b1", 0, 0, FaceId.FLIP).boards[0]
        assertEquals(SlotOptions(hideAtNight = true, face = FaceId.FLIP), out.options["0:0"])
        assertEquals(null, out.options["1:0"])
    }

    @Test fun theBoardsOwnFaceClearsTheCardFace() {
        val flip = BoardOps.setFace(c, "b1", 1, 0, FaceId.FLIP)
        val back = BoardOps.setFace(flip, "b1", 1, 0, FaceId.DIGITAL).boards[0]
        assertEquals(null, back.options["1:0"])
    }

    @Test fun widgetsPage() {
        val d = AppConfig()
        val out = BoardOps.setFace(d, "widgets", 0, 0, FaceId.WALL)
        assertEquals(FaceId.WALL, out.widgetOptions["0:0"]?.face)
        assertEquals(d.widgetLeft, out.widgetLeft)
    }

    @Test fun unknownBoardChangesNothing() = assertEquals(c, BoardOps.setFace(c, "nope", 0, 0, FaceId.FLIP))
}
