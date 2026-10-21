package app.signkorean.adapter.billing

import app.signkorean.domain.entitlement.FailReason
import app.signkorean.domain.entitlement.PurchaseResult
import app.signkorean.domain.entitlement.StorePurchase
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase.PurchaseState

/** Play Billing 응답 → 도메인 값 (설계서 §7.3). SDK 객체 없이 값만 받아서 JVM 테스트로 고정한다. */
object BillingResponses {

    /** OK 가 아닌 응답 코드. */
    fun failure(code: Int): PurchaseResult = when (code) {
        BillingResponseCode.USER_CANCELED -> PurchaseResult.Cancelled
        BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseResult.AlreadyOwned
        BillingResponseCode.SERVICE_UNAVAILABLE,
        BillingResponseCode.SERVICE_DISCONNECTED,
        BillingResponseCode.NETWORK_ERROR,
        BillingResponseCode.BILLING_UNAVAILABLE -> PurchaseResult.Failed(FailReason.StoreUnavailable)
        BillingResponseCode.ITEM_UNAVAILABLE,
        BillingResponseCode.FEATURE_NOT_SUPPORTED -> PurchaseResult.Failed(FailReason.ProductUnavailable)
        else -> PurchaseResult.Failed(FailReason.Unknown)
    }

    /** 이 앱 상품의 구매만 남긴다. 상태 미지정은 구매로 보지 않는다. */
    fun storePurchase(
        productId: String,
        products: List<String>,
        state: Int,
        token: String,
        acknowledged: Boolean,
    ): StorePurchase? {
        if (productId !in products) return null
        val mapped = when (state) {
            PurchaseState.PURCHASED -> StorePurchase.State.Purchased
            PurchaseState.PENDING -> StorePurchase.State.Pending
            else -> return null
        }
        return StorePurchase(token, mapped, acknowledged)
    }

    /** 결제 화면이 닫힌 뒤 받은 콜백. */
    fun update(code: Int, purchases: List<StorePurchase>): PurchaseResult {
        if (code != BillingResponseCode.OK) return failure(code)
        purchases.firstOrNull { it.state == StorePurchase.State.Purchased }?.let { return PurchaseResult.Purchased(it) }
        if (purchases.any { it.state == StorePurchase.State.Pending }) return PurchaseResult.Pending
        return PurchaseResult.Failed(FailReason.Unknown)
    }
}
