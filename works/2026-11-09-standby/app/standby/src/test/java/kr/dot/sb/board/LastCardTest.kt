package kr.dot.sb.board

import kr.dot.sb.model.CardType
import kr.dot.sb.model.FaceId
import kr.dot.sb.ui.board.LastCard
import org.junit.Assert.assertEquals
import org.junit.Test

/** 11-16: a stack starts on the card last settled on, found by card identity, not by index. */
class LastCardTest {
    private val stack = listOf(CardType.CLOCK, CardType.WEATHER, CardType.AGENDA)

    @Test fun nothingStored_firstCard() = assertEquals(0, LastCard.start(null, stack))

    @Test fun storedCard_itsIndex() = assertEquals(2, LastCard.start("AGENDA", stack))

    @Test fun cardInsertedBefore_stillThatCard() =
        assertEquals(3, LastCard.start("AGENDA", listOf(CardType.NOTIF) + stack))

    @Test fun cardGone_firstCard() = assertEquals(0, LastCard.start("MUSIC", stack))

    @Test fun unknownName_firstCard() = assertEquals(0, LastCard.start("NO_SUCH_CARD", stack))

    // plan-r15 E10: the notification card is remembered like any card
    @Test fun notifIsRemembered() {
        assertEquals("NOTIF", LastCard.keyOf(CardType.NOTIF))
        assertEquals(1, LastCard.start(LastCard.keyOf(CardType.NOTIF), listOf(CardType.CLOCK, CardType.NOTIF)))
    }

    private val faces = listOf(FaceId.DIGITAL, FaceId.FLIP, FaceId.ANALOG)

    @Test fun clockFace_storedAndListed_thatFace() = assertEquals(FaceId.FLIP, LastCard.face("FLIP", faces))

    @Test fun clockFace_removedFromMyClocks_firstPage() = assertEquals(null, LastCard.face("WORLD", faces))

    @Test fun clockFace_nothingStored_firstPage() = assertEquals(null, LastCard.face(null, faces))
}
