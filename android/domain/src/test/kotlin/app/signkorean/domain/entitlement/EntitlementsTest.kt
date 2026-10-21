package app.signkorean.domain.entitlement

import app.signkorean.testing.FakeEntitlementCache
import app.signkorean.testing.FakeEntitlementGateway
import app.signkorean.testing.FakeEntitlementGateway.Companion.pending
import app.signkorean.testing.FakeEntitlementGateway.Companion.purchased
import app.signkorean.testing.FakeNetworkMonitor
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 권한과 결제 (FR23~26, NFR09, 설계서 §6.4). E-01~09·11·13 */
class EntitlementsTest {

    private val gateway = FakeEntitlementGateway()
    private val cache = FakeEntitlementCache()
    private val network = FakeNetworkMonitor()
    private val entitlements = Entitlements(gateway, cache, network)

    private val flow get() = entitlements.purchaseFlow.value

    @Test
    fun `처음에는 마지막으로 저장된 권한을 쓴다`() = runTest {
        cache.stored = Entitlement.Paid
        assertEquals(Entitlement.Paid, entitlements.current())
        assertEquals(0, gateway.queryCalls, "캐시를 읽는 데 스토어 조회가 필요 없다")
    }

    @Test
    fun `E-01 스토어 조회가 실패하면 캐시의 Paid 를 유지한다`() = runTest {
        cache.stored = Entitlement.Paid
        gateway.queryFails = true
        entitlements.refresh()
        assertEquals(Entitlement.Paid, entitlements.current())
        assertEquals(Entitlement.Paid, cache.stored)
    }

    @Test
    fun `E-11 조회는 성공했는데 구매가 없으면(환불) Free 로 바꾼다`() = runTest {
        cache.stored = Entitlement.Paid
        gateway.owned = emptyList()
        entitlements.refresh()
        assertEquals(Entitlement.Free, entitlements.current())
        assertEquals(Entitlement.Free, cache.stored)
    }

    @Test
    fun `조회에서 구매가 보이면 Paid 로 저장한다`() = runTest {
        gateway.owned = listOf(purchased("t"))
        entitlements.refresh()
        assertEquals(Entitlement.Paid, entitlements.current())
        assertEquals(Entitlement.Paid, cache.stored)
    }

    @Test
    fun `결제 성공은 Paid 저장, 구매 확인, 성공 상태`() = runTest {
        entitlements.unlock()
        assertEquals(PurchaseFlowState.Succeeded, flow)
        assertEquals(Entitlement.Paid, entitlements.current())
        assertEquals(Entitlement.Paid, cache.stored)
        assertEquals(listOf("token-1"), gateway.acknowledged)
    }

    @Test
    fun `E-02 대기 결제는 Free 를 유지하고 대기 안내를 띄운다`() = runTest {
        gateway.nextPurchase = PurchaseResult.Pending
        entitlements.unlock()
        assertEquals(PurchaseFlowState.Pending, flow)
        assertEquals(Entitlement.Free, entitlements.current())
        assertEquals(Entitlement.Free, cache.stored)
    }

    @Test
    fun `E-03 대기 결제가 끝나면 다음 확인에서 Paid, 구매 확인을 한다`() = runTest {
        gateway.nextPurchase = PurchaseResult.Pending
        entitlements.unlock()
        gateway.completePending()
        entitlements.refresh()
        assertEquals(Entitlement.Paid, entitlements.current())
        assertEquals(listOf("token-pending"), gateway.acknowledged)
        assertEquals(PurchaseFlowState.Succeeded, flow, "대기 안내를 보고 있었다면 성공으로 바뀐다")
    }

    @Test
    fun `E-04 구매 확인이 실패해도 Paid 를 유지하고 다음 확인 때 다시 한다`() = runTest {
        gateway.acknowledgeSucceeds = false
        entitlements.unlock()
        assertEquals(Entitlement.Paid, entitlements.current())
        assertTrue(gateway.acknowledged.isEmpty())

        gateway.acknowledgeSucceeds = true
        entitlements.refresh()
        assertEquals(listOf("token-1"), gateway.acknowledged)

        entitlements.refresh()
        assertEquals(listOf("token-1"), gateway.acknowledged, "확인된 구매는 다시 확인하지 않는다")
    }

    @Test
    fun `E-05 결제 취소는 오류 없이 돌아온다`() = runTest {
        gateway.nextPurchase = PurchaseResult.Cancelled
        entitlements.unlock()
        assertEquals(PurchaseFlowState.Cancelled, flow)
        assertEquals(Entitlement.Free, entitlements.current())
    }

    @Test
    fun `E-06 결제 실패는 이유를 담고, 다시 시도할 수 있다`() = runTest {
        gateway.failWith(FailReason.StoreUnavailable)
        entitlements.unlock()
        assertEquals(PurchaseFlowState.Failed(FailReason.StoreUnavailable), flow)
        assertEquals(Entitlement.Free, entitlements.current())

        gateway.nextPurchase = PurchaseResult.Purchased(purchased("token-2", acknowledged = false))
        entitlements.unlock()
        assertEquals(PurchaseFlowState.Succeeded, flow)
        assertEquals(2, gateway.purchaseCalls)
    }

    @Test
    fun `결제 중 예외도 실패로 끝나고 진행 중에 머물지 않는다`() = runTest {
        val throwing = object : EntitlementGateway by gateway {
            override suspend fun purchase(): PurchaseResult = error("billing crashed")
        }
        val e = Entitlements(throwing, cache, network)
        e.unlock()
        assertEquals(PurchaseFlowState.Failed(FailReason.Unknown), e.purchaseFlow.value)
    }

    @Test
    fun `E-07 오프라인에서 Unlock 하면 결제를 부르지 않는다`() = runTest {
        network.online = false
        entitlements.unlock()
        assertEquals(PurchaseFlowState.Offline, flow)
        assertEquals(0, gateway.purchaseCalls)
    }

    @Test
    fun `E-08 결제 진행 중 연타해도 결제는 한 번`() = runTest {
        gateway.holdPurchase = true
        repeat(3) { launch { entitlements.unlock() } }
        runCurrent()
        assertEquals(PurchaseFlowState.InProgress, flow)
        gateway.releasePurchase()
        runCurrent()
        assertEquals(1, gateway.purchaseCalls)
        assertEquals(PurchaseFlowState.Succeeded, flow)
    }

    @Test
    fun `이미 가진 상품이면 스토어를 다시 보고 Paid 로 끝낸다`() = runTest {
        gateway.owned = listOf(purchased("old"))
        gateway.nextPurchase = PurchaseResult.AlreadyOwned
        entitlements.unlock()
        assertEquals(PurchaseFlowState.Succeeded, flow)
        assertEquals(Entitlement.Paid, entitlements.current())
    }

    @Test
    fun `E-09 복원은 있음 없음 실패를 구별한다`() = runTest {
        gateway.owned = listOf(purchased("t", acknowledged = false))
        assertEquals(RestoreResult.Restored, entitlements.restore())
        assertEquals(Entitlement.Paid, entitlements.current())
        assertEquals(listOf("t"), gateway.acknowledged)

        gateway.queryFails = true
        assertEquals(RestoreResult.Failed, entitlements.restore())
        assertEquals(Entitlement.Paid, entitlements.current(), "실패는 권한을 내리지 않는다")

        gateway.queryFails = false
        gateway.refund()
        assertEquals(RestoreResult.NothingToRestore, entitlements.restore())
        assertEquals(Entitlement.Free, entitlements.current())
    }

    @Test
    fun `오프라인 복원은 스토어를 부르지 않고 실패`() = runTest {
        network.online = false
        assertEquals(RestoreResult.Failed, entitlements.restore())
        assertEquals(0, gateway.queryCalls)
    }

    @Test
    fun `E-13 앱 밖에서 끝난 대기 결제는 복귀 확인에서 Paid`() = runTest {
        gateway.owned = listOf(pending("p"))
        entitlements.refresh()
        assertEquals(Entitlement.Free, entitlements.current())
        assertEquals(PurchaseFlowState.Pending, flow, "대기 중인 결제가 있다고 알린다")

        gateway.completePending()
        entitlements.refresh() // onResume
        assertEquals(Entitlement.Paid, entitlements.current())
        assertEquals(listOf("p"), gateway.acknowledged)
    }

    @Test
    fun `대기 결제가 스토어에서 사라지면 대기 안내를 거둔다`() = runTest {
        gateway.owned = listOf(pending("p"))
        entitlements.refresh()
        gateway.refund()
        entitlements.refresh()
        assertEquals(PurchaseFlowState.Idle, flow)
    }

    @Test
    fun `안내를 닫으면 Idle, 진행 중에는 닫히지 않는다`() = runTest {
        gateway.nextPurchase = PurchaseResult.Cancelled
        entitlements.unlock()
        entitlements.dismiss()
        assertEquals(PurchaseFlowState.Idle, flow)

        gateway.holdPurchase = true
        launch { entitlements.unlock() }
        runCurrent()
        entitlements.dismiss()
        assertEquals(PurchaseFlowState.InProgress, flow)
        gateway.releasePurchase()
    }

    @Test
    fun `캐시 저장이 실패해도 이번 실행에서는 확인된 권한을 쓴다`() = runTest {
        gateway.owned = listOf(purchased("t"))
        cache.failNextSave = true
        entitlements.refresh()
        assertEquals(Entitlement.Paid, entitlements.current())
    }

    @Test
    fun `권한 변화는 흐름으로 알린다`() = runTest {
        assertEquals(Entitlement.Free, entitlements.current())
        entitlements.unlock()
        assertEquals(Entitlement.Paid, entitlements.entitlement.value)
    }

    @Test
    fun `가격은 스토어의 현지화 문자열 그대로, 없으면 null`() = runTest {
        gateway.price = "¥150"
        assertEquals("¥150", entitlements.localizedPrice())
        gateway.price = null
        assertEquals(null, entitlements.localizedPrice())
    }
}
