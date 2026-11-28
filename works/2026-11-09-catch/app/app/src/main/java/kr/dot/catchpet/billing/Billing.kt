package kr.dot.catchpet.billing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kr.dot.catchpet.ai.AiClient
import kr.dot.catchpet.ai.AiWork
import kotlin.coroutines.resume
import com.android.billingclient.api.Purchase as PlayPurchase

/** Play Billing for [PaidSheets.PRODUCT] (spec-release 2): the BillingClient behind [Store]. */
private class PlayStore(ctx: Context, onUpdate: (BillingResult, List<PlayPurchase>?) -> Unit, private val entryOf: (PlayPurchase) -> String?) : Store {
    val client: BillingClient = BillingClient.newBuilder(ctx).setListener(onUpdate)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection().build()
    private val connecting = Mutex()

    suspend fun ready(): Boolean = connecting.withLock {
        client.isReady || suspendCancellableCoroutine { c ->
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(r: BillingResult) { if (c.isActive) c.resume(r.responseCode == BillingResponseCode.OK) }
                override fun onBillingServiceDisconnected() {}   // auto reconnection handles retries
            })
        }
    }

    /** null = not created in Play Console yet, or Play is unreachable. */
    suspend fun product(): ProductDetails? {
        if (!ready()) return null
        val q = QueryProductDetailsParams.newBuilder().setProductList(listOf(QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PaidSheets.PRODUCT).setProductType(BillingClient.ProductType.INAPP).build())).build()
        return suspendCancellableCoroutine { c ->
            client.queryProductDetailsAsync(q) { r, res -> if (c.isActive) c.resume(if (r.responseCode == BillingResponseCode.OK) res.productDetailsList.firstOrNull() else null) }
        }
    }

    fun paid(p: PlayPurchase): Paid? = if (PaidSheets.PRODUCT !in p.products) null else when (p.purchaseState) {
        PlayPurchase.PurchaseState.PURCHASED -> Paid(p.purchaseToken, entryOf(p), pending = false)
        PlayPurchase.PurchaseState.PENDING -> Paid(p.purchaseToken, entryOf(p), pending = true)
        else -> null
    }

    override suspend fun owned(): List<Paid>? {
        if (!ready()) return null
        val q = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        return suspendCancellableCoroutine { c ->
            client.queryPurchasesAsync(q) { r, list -> if (c.isActive) c.resume(if (r.responseCode == BillingResponseCode.OK) list.mapNotNull(::paid) else null) }
        }
    }

    override suspend fun consume(token: String): Boolean {
        if (!ready()) return false
        val code = suspendCancellableCoroutine { c ->
            client.consumeAsync(ConsumeParams.newBuilder().setPurchaseToken(token).build()) { r, _ -> if (c.isActive) c.resume(r.responseCode) }
        }
        return code == BillingResponseCode.OK || code == BillingResponseCode.ITEM_NOT_OWNED   // the Worker consumed it first
    }
}

/**
 * The 700-won button (operator 11-28): one purchase of sheet_both = both pets of one dex entry. App-wide like AiWork,
 * so a purchase that lands after the screen closed is still drawn. The dev app never uses it (its token draws free).
 */
object Billing {
    /** "700원" from Play, or null while the product is not found (shown as "준비 중"). */
    var price by mutableStateOf<String?>(null); private set
    /** Entries with a purchase that is paid but not drawn yet: the button retries it instead of charging again. */
    val kept = mutableStateMapOf<String, String>()   // entry id -> token
    val waiting = mutableStateMapOf<String, Boolean>()   // entry id -> a pending payment

    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var store: PlayStore? = null
    private lateinit var sheets: PaidSheets
    private var details: ProductDetails? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("catchpet", Context.MODE_PRIVATE)

    /** Once per process: connect, read the price, finish every purchase left over (a crash mid-draw loses nothing). */
    fun start(ctx: Context) {
        if (AiWork.dev || store != null) return
        val app = ctx.applicationContext
        val p = prefs(app)
        val drawn = object : Drawn {
            override fun has(token: String) = p.getStringSet("bill_drawn", emptySet())!!.contains(token)
            override fun add(token: String) { p.edit().putStringSet("bill_drawn", p.getStringSet("bill_drawn", emptySet())!! + token).commit() }
        }
        val s = PlayStore(app, { r, list -> main.launch { updated(app, r, list) } },
            // the entry rides on the purchase (obfuscated profile id); the saved one covers a purchase without it
            entryOf = { it.accountIdentifiers?.obfuscatedProfileId ?: p.getString("bill_entry", null) })
        store = s
        sheets = PaidSheets(s, drawn) { entry, token -> AiWork.buyPaid(app, entry, token) }
        main.launch {
            details = s.product()
            price = details?.oneTimePurchaseOfferDetails?.formattedPrice?.let { it.replace("₩", "") + if (it.startsWith("₩")) "원" else "" }
            restore(app)
        }
    }

    private suspend fun restore(app: Context) {
        for ((paid, out) in sheets.restore()) after(app, paid, out)
    }

    private suspend fun updated(app: Context, r: BillingResult, list: List<PlayPurchase>?) {
        val entry = prefs(app).getString("bill_entry", null)
        when (r.responseCode) {
            BillingResponseCode.OK -> for (paid in list.orEmpty().mapNotNull { store?.paid(it) }) after(app, paid, sheets.handle(paid))
            BillingResponseCode.USER_CANCELED -> entry?.let { AiWork.message[it] = "결제가 취소됐어요" }
            BillingResponseCode.ITEM_ALREADY_OWNED -> restore(app)   // an earlier purchase was never finished: finish it now
            else -> entry?.let { AiWork.message[it] = "결제를 하지 못했어요. 잠시 뒤 다시 눌러 주세요" }
        }
    }

    private fun after(app: Context, paid: Paid, out: PaidSheets.Outcome) {
        val e = paid.entry ?: return
        when (out) {
            PaidSheets.Outcome.DONE -> { kept.remove(e); waiting.remove(e) }
            PaidSheets.Outcome.PENDING -> { waiting[e] = true; AiWork.message[e] = "결제를 확인하고 있어요. 끝나면 바로 만들어 줄게요" }
            PaidSheets.Outcome.FAILED -> { kept[e] = paid.token; waiting.remove(e)
                AiWork.message[e] = "그림을 못 그렸어요. 결제는 그대로 있으니 다시 누르면 새로 결제하지 않고 그려요" }
            PaidSheets.Outcome.BUSY, PaidSheets.Outcome.NO_ENTRY -> Log.w("Billing", "purchase $out")
        }
    }

    /** The 700-won tap: retry a kept purchase, else open Play's payment sheet for [entry]. */
    fun buy(ctx: Context, entry: String) {
        val app = ctx.applicationContext
        kept[entry]?.let { token -> main.launch { after(app, Paid(token, entry, false), sheets.handle(Paid(token, entry, false))) }; return }
        val d = details ?: return
        val act = generateSequence(ctx) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull() ?: return
        prefs(app).edit().putString("bill_entry", entry).commit()
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()))
            .setObfuscatedAccountId(AiClient.deviceId(app)).setObfuscatedProfileId(entry).build()
        val r = store?.client?.launchBillingFlow(act, params)
        if (r != null && r.responseCode != BillingResponseCode.OK) main.launch { updated(app, r, null) }
    }
}
