package app.signkorean.adapter.billing

import app.signkorean.domain.entitlement.FailReason
import app.signkorean.domain.entitlement.PurchaseResult
import app.signkorean.domain.entitlement.StorePurchase
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.Purchase.PurchaseState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 결제 응답 코드 → 도메인 결과 매핑표 (설계서 §7.3, 구현 계획 Phase 12 D2). 표가 바뀌면 이 테스트가 먼저 바뀐다. */
class BillingResponsesTest {

    private val product = "full_unlock"

    @Test
    fun `실패 응답 코드 매핑표`() {
        val table = mapOf(
            BillingResponseCode.USER_CANCELED to PurchaseResult.Cancelled,
            BillingResponseCode.ITEM_ALREADY_OWNED to PurchaseResult.AlreadyOwned,
            BillingResponseCode.SERVICE_UNAVAILABLE to PurchaseResult.Failed(FailReason.StoreUnavailable),
            BillingResponseCode.SERVICE_DISCONNECTED to PurchaseResult.Failed(FailReason.StoreUnavailable),
            BillingResponseCode.NETWORK_ERROR to PurchaseResult.Failed(FailReason.StoreUnavailable),
            BillingResponseCode.BILLING_UNAVAILABLE to PurchaseResult.Failed(FailReason.StoreUnavailable),
            BillingResponseCode.ITEM_UNAVAILABLE to PurchaseResult.Failed(FailReason.ProductUnavailable),
            BillingResponseCode.FEATURE_NOT_SUPPORTED to PurchaseResult.Failed(FailReason.ProductUnavailable),
            BillingResponseCode.DEVELOPER_ERROR to PurchaseResult.Failed(FailReason.Unknown),
            BillingResponseCode.ERROR to PurchaseResult.Failed(FailReason.Unknown),
            BillingResponseCode.ITEM_NOT_OWNED to PurchaseResult.Failed(FailReason.Unknown),
            12345 to PurchaseResult.Failed(FailReason.Unknown),
        )
        table.forEach { (code, expected) -> assertEquals("code $code", expected, BillingResponses.failure(code)) }
    }

    @Test
    fun `구매 상태 매핑, 다른 상품과 미지정 상태는 버린다`() {
        assertEquals(StorePurchase("t", StorePurchase.State.Purchased, true),
            BillingResponses.storePurchase(product, listOf(product), PurchaseState.PURCHASED, "t", true))
        assertEquals(StorePurchase("t", StorePurchase.State.Pending, false),
            BillingResponses.storePurchase(product, listOf(product), PurchaseState.PENDING, "t", false))
        assertNull(BillingResponses.storePurchase(product, listOf(product), PurchaseState.UNSPECIFIED_STATE, "t", false))
        assertNull(BillingResponses.storePurchase(product, listOf("other"), PurchaseState.PURCHASED, "t", true))
    }

    @Test
    fun `결제 결과 콜백 매핑`() {
        val bought = StorePurchase("t", StorePurchase.State.Purchased, false)
        val waiting = StorePurchase("p", StorePurchase.State.Pending, false)
        assertEquals(PurchaseResult.Purchased(bought), BillingResponses.update(BillingResponseCode.OK, listOf(waiting, bought)))
        assertEquals(PurchaseResult.Pending, BillingResponses.update(BillingResponseCode.OK, listOf(waiting)))
        assertEquals("OK 인데 구매가 없으면 실패로 본다", PurchaseResult.Failed(FailReason.Unknown),
            BillingResponses.update(BillingResponseCode.OK, emptyList()))
        assertEquals(PurchaseResult.Cancelled, BillingResponses.update(BillingResponseCode.USER_CANCELED, emptyList()))
    }
}
