package app.signkorean.adapter.billing

import android.app.Activity
import android.content.Context
import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.domain.entitlement.FailReason
import app.signkorean.domain.entitlement.PurchaseResult
import app.signkorean.domain.entitlement.StorePurchase
import app.signkorean.domain.entitlement.StoreQuery
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** 결제 화면을 띄울 지금 화면의 Activity. 앱이 뒤에 있으면 null 이고 결제를 시작하지 않는다. 구현은 :app 이 한다. */
fun interface ForegroundActivity {
    fun current(): Activity?
}

/**
 * Play Billing 게이트웨이 (설계서 §7.3). 콜백 API 를 suspend 함수로 감싼다.
 *
 * - 대기 결제(편의점 결제 등)를 받으려고 `enablePendingPurchases` 를 켠다.
 * - 연결이 끊기면 다음 호출에서 지수 백오프로 다시 연결한다. 결제를 사용자 동작 없이 다시 시도하지는 않는다.
 * - 모든 콜백 대기에 시간 상한이 있다. 콜백이 오지 않아도 호출이 끝나야 권한 확인·결제가 앱 전체에서 멈추지 않는다.
 * - 결제 화면이 열려 있지 않을 때 온 구매 갱신은 [onStoreChanged] 로 알린다. 받는 쪽이 스토어 목록을 다시 읽는다.
 */
class PlayBillingEntitlementGateway(
    context: Context,
    private val foreground: ForegroundActivity,
    private val main: CoroutineDispatcher,
    private val productId: String = PRODUCT_ID,
) : EntitlementGateway {

    /** 결제 화면 밖에서 이 앱 상품의 구매가 바뀌었다(앱이 앞에 있는 동안 대기 결제가 끝남 등). */
    @Volatile var onStoreChanged: () -> Unit = {}

    @Volatile private var inFlight: CompletableDeferred<PurchaseResult>? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val mine = purchases.orEmpty().mapNotNull(::toStore)
        val waiting = inFlight
        if (waiting == null) {
            if (result.responseCode == BillingResponseCode.OK && mine.isNotEmpty()) onStoreChanged()
            return@PurchasesUpdatedListener
        }
        inFlight = null
        waiting.complete(BillingResponses.update(result.responseCode, mine))
    }

    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(listener)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .build()

    private val connectLock = Mutex()

    @Volatile private var details: ProductDetails? = null

    override suspend fun queryPurchases(): StoreQuery {
        if (!connect()) return StoreQuery.Failure
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.INAPP).build()
        return callback<StoreQuery> { cont ->
            client.queryPurchasesAsync(params) { result, purchases ->
                val query = if (result.responseCode == BillingResponseCode.OK) {
                    StoreQuery.Success(purchases.mapNotNull(::toStore))
                } else {
                    StoreQuery.Failure
                }
                if (cont.isActive) cont.resume(query)
            }
        } ?: StoreQuery.Failure
    }

    override suspend fun localizedPrice(): String? = productDetails()?.oneTimePurchaseOfferDetails?.formattedPrice

    override suspend fun purchase(): PurchaseResult {
        if (!connect()) return PurchaseResult.Failed(FailReason.StoreUnavailable)
        val product = productDetails() ?: return PurchaseResult.Failed(FailReason.ProductUnavailable)
        val waiting = CompletableDeferred<PurchaseResult>()
        // inFlight 를 세운 순간부터 끝까지 한 try 안에 둔다. 띄운 직후 취소돼도 기다리는 사람 없는 대기가 남지 않는다.
        try {
            val launch = withContext(main) {
                val activity = foreground.current() ?: return@withContext null
                inFlight = waiting
                val params = BillingFlowParams.newBuilder()
                    .setProductDetailsParamsList(
                        listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(product).build()),
                    )
                    .build()
                client.launchBillingFlow(activity, params)
            } ?: return PurchaseResult.Failed(FailReason.Unknown)
            if (launch.responseCode != BillingResponseCode.OK) return BillingResponses.failure(launch.responseCode)
            // 결제 앱이 결과 없이 사라지면 콜백이 오지 않는다. 상한이 지나면 스토어 목록으로 정하게 한다.
            return withTimeoutOrNull(PURCHASE_TIMEOUT_MS) { waiting.await() } ?: PurchaseResult.Unconfirmed
        } finally {
            if (inFlight === waiting) inFlight = null
        }
    }

    override suspend fun acknowledge(token: String): Boolean {
        if (!connect()) return false
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        return callback<Boolean> { cont ->
            client.acknowledgePurchase(params) { result ->
                if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
            }
        } ?: false
    }

    private suspend fun productDetails(): ProductDetails? {
        details?.let { return it }
        if (!connect()) return null
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(ProductType.INAPP)
                        .build(),
                ),
            )
            .build()
        return callback<ProductDetails?> { cont ->
            client.queryProductDetailsAsync(params) { result, queried ->
                val found = if (result.responseCode == BillingResponseCode.OK) {
                    queried.productDetailsList.firstOrNull { it.productId == productId }
                } else {
                    null
                }
                if (cont.isActive) cont.resume(found)
            }
        }?.also { details = it }
    }

    /** 연결돼 있으면 바로 true. 아니면 최대 [MAX_ATTEMPTS] 번, 0.5초·1초 간격을 두고 다시 연결한다. */
    private suspend fun connect(): Boolean = connectLock.withLock {
        if (client.isReady) return true
        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(BASE_BACKOFF_MS shl (attempt - 1))
            if (startConnection() == BillingResponseCode.OK) return true
        }
        false
    }

    /** 설정 결과 코드. 설정 전에 끊기거나 시간 안에 답이 없으면 SERVICE_DISCONNECTED 다. */
    private suspend fun startConnection(): Int = callback<Int> { cont ->
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (cont.isActive) cont.resume(result.responseCode)
            }

            // 설정이 끝나기 전에 끊기면 onBillingSetupFinished 가 오지 않는다. 기다리는 쪽을 깨운다.
            // 설정 뒤에 끊긴 경우는 이미 재개돼 있어 아무 일도 없고, 다음 호출의 connect() 가 다시 연결한다.
            override fun onBillingServiceDisconnected() {
                if (cont.isActive) cont.resume(BillingResponseCode.SERVICE_DISCONNECTED)
            }
        })
    } ?: BillingResponseCode.SERVICE_DISCONNECTED

    /** 콜백 하나를 기다린다. [CALL_TIMEOUT_MS] 안에 오지 않으면 null. 늦게 온 콜백은 isActive 검사로 버려진다. */
    private suspend fun <T> callback(block: (CancellableContinuation<T>) -> Unit): T? =
        withTimeoutOrNull(CALL_TIMEOUT_MS) { suspendCancellableCoroutine(block) }

    private fun toStore(p: Purchase): StorePurchase? =
        BillingResponses.storePurchase(productId, p.products, p.purchaseState, p.purchaseToken, p.isAcknowledged)

    companion object {
        /** Play Console 에 등록할 비소모성 상품 ID. 바꾸면 기존 구매자의 복원이 끊긴다. */
        const val PRODUCT_ID = "full_unlock"
        private const val MAX_ATTEMPTS = 3
        private const val BASE_BACKOFF_MS = 500L
        private const val CALL_TIMEOUT_MS = 15_000L

        /** 결제 화면에서 카드를 입력하는 시간까지 넉넉히. 지나면 스토어 목록으로 정하고, 늦은 결과는 onStoreChanged 가 받는다. */
        private const val PURCHASE_TIMEOUT_MS = 5 * 60_000L
    }
}
