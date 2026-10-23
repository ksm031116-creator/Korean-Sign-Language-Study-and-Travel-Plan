package app.signkorean.di

import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.Entitlements
import app.signkorean.domain.entitlement.PurchaseResult
import app.signkorean.domain.entitlement.StoreQuery
import app.signkorean.testing.FakeEntitlementCache
import app.signkorean.testing.FakeNetworkMonitor
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 모의 결제 (BIL-01). 스토어에 올리지 않아 디버그·릴리스 모두 이 결제를 쓰므로, 앱을 껐다 켜도(새 인스턴스)
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

    @Test
    fun `토큰에 구분자가 들어 있어도 구매를 버리지 않는다`() = runTest {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("simulated_billing", android.content.Context.MODE_PRIVATE).edit()
            .putStringSet("owned", setOf("odd|token|1")).commit()
        val owned = (SimulatedBillingGateway(context).queryPurchases() as StoreQuery.Success).purchases
        assertEquals(listOf("odd|token"), owned.map { it.token })
        assertTrue(owned.single().acknowledged)
    }

    @Test
    fun `동시에 결제·확인해도 구매가 사라지지 않는다`() = runTest {
        val gateway = SimulatedBillingGateway(context)
        gateway.relock()
        val tokens = kotlinx.coroutines.coroutineScope {
            (1..20).map { async(kotlinx.coroutines.Dispatchers.Default) { (gateway.purchase() as PurchaseResult.Purchased).purchase.token } }.map { it.await() }
        }
        val owned = (SimulatedBillingGateway(context).queryPurchases() as StoreQuery.Success).purchases.map { it.token }.toSet()
        assertEquals(tokens.toSet(), owned)
    }
}
