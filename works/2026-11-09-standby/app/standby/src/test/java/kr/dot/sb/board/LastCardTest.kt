package kr.dot.sb.board

import kr.dot.sb.model.CardType
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

    @Test fun notifIsNeverRemembered() {
        assertEquals(null, LastCard.keyOf(CardType.NOTIF))
        assertEquals("WEATHER", LastCard.keyOf(CardType.WEATHER))
    }
}
