package app.signkorean.di

import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.Entitlements
import app.signkorean.domain.entitlement.PurchaseResult
import app.signkorean.domain.entitlement.StoreQuery
import app.signkorean.testing.FakeEntitlementCache
import app.signkorean.testing.FakeNetworkMonitor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 디버그 빌드의 모의 결제 (BIL-01). 실결제를 확인할 수 없는 동안 시연·채점에 쓰이므로, 앱을 껐다 켜도(새 인스턴스)
 * 구매가 남아야 한다. 다시 잠그기는 저장된 구매도 지운다.
 */
@RunWith(RobolectricTestRunner::class)
class SimulatedBillingGatewayTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `BIL-01 결제한 뒤 앱을 다시 켜도(새 게이트웨이) 구매가 남는다`() = runTest {
        val first = SimulatedBillingGateway(context)
        first.relock()
        val bought = (first.purchase() as PurchaseResult.Purchased).purchase
        assertTrue(first.acknowledge(bought.token))

        val restarted = SimulatedBillingGateway(context)
        val owned = (restarted.queryPurchases() as StoreQuery.Success).purchases
        assertEquals(listOf(bought.copy(acknowledged = true)), owned)
        // 다시 켠 뒤 권한 확인도 Paid 다(캐시가 비어 있어도 스토어 목록으로 정한다).
        val entitlements = Entitlements(restarted, FakeEntitlementCache(), FakeNetworkMonitor())
        assertTrue(entitlements.refresh())
        assertEquals(Entitlement.Paid, entitlements.current())
    }

    @Test
    fun `다시 잠그면 다시 켠 뒤에도 무료다`() = runTest {
        val first = SimulatedBillingGateway(context)
        first.purchase()
        first.relock()
        val owned = (SimulatedBillingGateway(context).queryPurchases() as StoreQuery.Success).purchases
        assertTrue(owned.isEmpty())
    }
}
