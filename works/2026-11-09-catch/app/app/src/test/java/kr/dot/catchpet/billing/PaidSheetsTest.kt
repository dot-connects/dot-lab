package kr.dot.catchpet.billing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaidSheetsTest {
    private class FakeStore(var list: MutableList<Paid> = mutableListOf()) : Store {
        val consumed = mutableListOf<String>()
        override suspend fun owned(): List<Paid> = list.toList()
        override suspend fun consume(token: String): Boolean { consumed += token; list.removeAll { it.token == token }; return true }
    }
    private class FakeDrawn : Drawn {
        val set = mutableSetOf<String>()
        override fun has(token: String) = token in set
        override fun add(token: String) { set += token }
    }

    @Test fun purchasedThenSheetThenConsume() = runBlocking {
        val store = FakeStore(); val drawn = FakeDrawn(); val sent = mutableListOf<Pair<String, String>>()
        val ps = PaidSheets(store, drawn) { e, t -> sent += e to t; assertTrue("consumed before the sheet", store.consumed.isEmpty()); Result.success(Unit) }
        assertEquals(PaidSheets.Outcome.DONE, ps.handle(Paid("tok", "e1", pending = false)))
        assertEquals(listOf("e1" to "tok"), sent)
        assertEquals(listOf("tok"), store.consumed)
        assertTrue(drawn.has("tok"))
    }

    @Test fun pendingIsNeitherSentNorConsumed() = runBlocking {
        val store = FakeStore(); var sent = 0
        val ps = PaidSheets(store, FakeDrawn()) { _, _ -> sent++; Result.success(Unit) }
        assertEquals(PaidSheets.Outcome.PENDING, ps.handle(Paid("tok", "e1", pending = true)))
        assertEquals(0, sent); assertTrue(store.consumed.isEmpty())
    }

    @Test fun sheetFailureKeepsThePurchaseForARetry() = runBlocking {
        val p = Paid("tok", "e1", pending = false)
        val store = FakeStore(mutableListOf(p)); var ok = false
        val ps = PaidSheets(store, FakeDrawn()) { _, _ -> if (ok) Result.success(Unit) else Result.failure(Exception("502 model")) }
        assertEquals(PaidSheets.Outcome.FAILED, ps.handle(p))
        assertTrue(store.consumed.isEmpty())
        ok = true
        assertEquals(listOf(p to PaidSheets.Outcome.DONE), ps.restore())
        assertEquals(listOf("tok"), store.consumed)
    }

    @Test fun restoreOnStartFinishesUnconsumedAndSkipsPending() = runBlocking {
        val store = FakeStore(mutableListOf(Paid("a", "e1", false), Paid("b", "e2", true), Paid("c", null, false)))
        val sent = mutableListOf<String>()
        val ps = PaidSheets(store, FakeDrawn()) { e, _ -> sent += e; Result.success(Unit) }
        assertEquals(listOf(PaidSheets.Outcome.DONE, PaidSheets.Outcome.PENDING, PaidSheets.Outcome.NO_ENTRY), ps.restore().map { it.second })
        assertEquals(listOf("e1"), sent)
        assertEquals(listOf("a"), store.consumed)   // the unknown-entry one stays unconsumed (Play refunds it)
    }

    @Test fun noDoubleSheetForOnePurchase() = runBlocking {
        val store = FakeStore(); val drawn = FakeDrawn(); var sent = 0
        val gate = CompletableDeferred<Unit>()
        val ps = PaidSheets(store, drawn) { _, _ -> sent++; gate.await(); Result.success(Unit) }
        val p = Paid("tok", "e1", false)
        val first = async { ps.handle(p) }
        yield()   // the first send is in flight
        assertEquals(PaidSheets.Outcome.BUSY, ps.handle(p))   // listener + restore racing on the same token
        gate.complete(Unit)
        assertEquals(PaidSheets.Outcome.DONE, first.await())
        // drawn but the consume did not stick (crash): the next start only consumes it
        assertEquals(PaidSheets.Outcome.DONE, ps.handle(p))
        assertEquals(1, sent)
        assertEquals(listOf("tok", "tok"), store.consumed)
    }
}
