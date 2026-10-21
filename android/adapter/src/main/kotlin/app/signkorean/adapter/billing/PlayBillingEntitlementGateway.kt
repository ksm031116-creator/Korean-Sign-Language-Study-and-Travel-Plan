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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Play Billing 게이트웨이 (설계서 §7.3). 콜백 API 를 suspend 함수로 감싼다.
 *
 * - 대기 결제(편의점 결제 등)를 받으려고 `enablePendingPurchases` 를 켠다.
 * - 연결이 끊기면 다음 호출에서 지수 백오프로 다시 연결한다. 결제를 사용자 동작 없이 다시 시도하지는 않는다.
 * - 결제 화면이 열려 있지 않을 때 온 구매 갱신은 버린다. 앱 복귀 때의 확인(onResume)이 스토어 목록으로 다시 읽는다.
 */
/** 결제 화면을 띄울 지금 화면의 Activity. 앱이 뒤에 있으면 null 이고 결제를 시작하지 않는다. 구현은 :app 이 한다. */
fun interface ForegroundActivity {
    fun current(): Activity?
}

class PlayBillingEntitlementGateway(
    context: Context,
    private val foreground: ForegroundActivity,
    private val main: CoroutineDispatcher,
    private val productId: String = PRODUCT_ID,
) : EntitlementGateway {

    @Volatile private var inFlight: CompletableDeferred<PurchaseResult>? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        val waiting = inFlight ?: return@PurchasesUpdatedListener
        inFlight = null
        waiting.complete(BillingResponses.update(result.responseCode, purchases.orEmpty().mapNotNull(::toStore)))
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
        return suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { result, purchases ->
                val query = if (result.responseCode == BillingResponseCode.OK) {
                    StoreQuery.Success(purchases.mapNotNull(::toStore))
                } else {
                    StoreQuery.Failure
                }
                if (cont.isActive) cont.resume(query)
            }
        }
    }

    override suspend fun localizedPrice(): String? = productDetails()?.oneTimePurchaseOfferDetails?.formattedPrice

    override suspend fun purchase(): PurchaseResult {
        if (!connect()) return PurchaseResult.Failed(FailReason.StoreUnavailable)
        val product = productDetails() ?: return PurchaseResult.Failed(FailReason.ProductUnavailable)
        val waiting = CompletableDeferred<PurchaseResult>()
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
        if (launch.responseCode != BillingResponseCode.OK) {
            inFlight = null
            return BillingResponses.failure(launch.responseCode)
        }
        return try {
            waiting.await()
        } finally {
            if (inFlight === waiting) inFlight = null
        }
    }

    override suspend fun acknowledge(token: String): Boolean {
        if (!connect()) return false
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        return suspendCancellableCoroutine { cont ->
            client.acknowledgePurchase(params) { result ->
                if (cont.isActive) cont.resume(result.responseCode == BillingResponseCode.OK)
            }
        }
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
        return suspendCancellableCoroutine<ProductDetails?> { cont ->
            client.queryProductDetailsAsync(params) { result, queried ->
                val found = if (result.responseCode == BillingResponseCode.OK) {
                    queried.productDetailsList.firstOrNull { it.productId == productId }
                } else {
                    null
                }
                if (cont.isActive) cont.resume(found)
            }
        }.also { details = it }
    }

    /** 연결돼 있으면 바로 true. 아니면 최대 [MAX_ATTEMPTS] 번, 0.5초·1초 간격을 두고 다시 연결한다. */
    private suspend fun connect(): Boolean = connectLock.withLock {
        if (client.isReady) return true
        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(BASE_BACKOFF_MS shl (attempt - 1))
            if (startConnection().responseCode == BillingResponseCode.OK) return true
        }
        false
    }

    private suspend fun startConnection(): BillingResult = suspendCancellableCoroutine { cont ->
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (cont.isActive) cont.resume(result)
            }

            // 다음 호출의 connect() 가 다시 연결한다.
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    private fun toStore(p: Purchase): StorePurchase? =
        BillingResponses.storePurchase(productId, p.products, p.purchaseState, p.purchaseToken, p.isAcknowledged)

    companion object {
        /** Play Console 에 등록할 비소모성 상품 ID. 바꾸면 기존 구매자의 복원이 끊긴다. */
        const val PRODUCT_ID = "full_unlock"
        private const val MAX_ATTEMPTS = 3
        private const val BASE_BACKOFF_MS = 500L
    }
}
