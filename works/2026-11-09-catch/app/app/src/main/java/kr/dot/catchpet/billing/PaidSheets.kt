package kr.dot.catchpet.billing

import java.util.Collections

/** One Play purchase of [PaidSheets.PRODUCT] as the app sees it. [entry]: the dex entry it was bought for (null = unknown). */
class Paid(val token: String, val entry: String?, val pending: Boolean)

/** What the app needs from Play Billing: the seam a fake replaces in tests. */
interface Store {
    /** Unconsumed purchases of the product (purchased or pending); null = Play could not be asked. */
    suspend fun owned(): List<Paid>?
    /** true = Play consumed it (or it was already gone). */
    suspend fun consume(token: String): Boolean
}

/** Tokens whose sheets are already saved on this phone (persisted: a crash after the save must not draw again). */
interface Drawn {
    fun has(token: String): Boolean
    fun add(token: String)
}

/**
 * Turns paid purchases into pet sheets (spec-release 2). A purchase is consumed only after its sheets are saved, so a
 * failed draw or a crash mid-way keeps it for [restore] / a retry. One token is sent at most once at a time, and a
 * token already drawn is only consumed, never drawn again.
 */
class PaidSheets(private val store: Store, private val drawn: Drawn, private val send: suspend (entry: String, token: String) -> Result<Unit>) {
    enum class Outcome { DONE, PENDING, FAILED, BUSY, NO_ENTRY }

    private val sending = Collections.synchronizedSet(HashSet<String>())

    suspend fun handle(p: Paid): Outcome {
        if (p.pending) return Outcome.PENDING
        if (drawn.has(p.token)) { store.consume(p.token); return Outcome.DONE }
        val entry = p.entry ?: return Outcome.NO_ENTRY   // kept unconsumed: Play refunds it after 3 days
        if (!sending.add(p.token)) return Outcome.BUSY
        try {
            if (send(entry, p.token).isFailure) return Outcome.FAILED
            drawn.add(p.token)
            store.consume(p.token)   // a failed consume is retried by the next restore (the Worker consumes too)
            return Outcome.DONE
        } finally { sending.remove(p.token) }
    }

    /** On start: every purchase left unconsumed (a crash, a failed draw, a pending one that cleared) is finished. */
    suspend fun restore(): List<Pair<Paid, Outcome>> = store.owned().orEmpty().map { it to handle(it) }

    companion object { const val PRODUCT = "sheet_both" }
}
