package app.signkorean.testing

import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.EntitlementCache
import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.domain.entitlement.FailReason
import app.signkorean.domain.entitlement.NetworkMonitor
import app.signkorean.domain.entitlement.PurchaseResult
import app.signkorean.domain.entitlement.StorePurchase
import app.signkorean.domain.entitlement.StoreQuery
import kotlinx.coroutines.CompletableDeferred

/**
 * 결제 가짜 (설계서 §6.6). 성공·취소·실패·대기·오프라인·환불 시나리오를 값으로 고른다.
 * [holdPurchase] 를 켜면 결제가 [releasePurchase] 까지 멈춰 있어 진행 중 연타(E-08)를 재현할 수 있다.
 */
class FakeEntitlementGateway(
    @Volatile var owned: List<StorePurchase> = emptyList(),
    @Volatile var queryFails: Boolean = false,
    @Volatile var nextPurchase: PurchaseResult = PurchaseResult.Purchased(purchased("token-1", acknowledged = false)),
    @Volatile var acknowledgeSucceeds: Boolean = true,
    @Volatile var price: String? = "US$0.99",
) : EntitlementGateway {
    @Volatile var queryCalls = 0; private set
    @Volatile var purchaseCalls = 0; private set
    val acknowledged = mutableListOf<String>()

    @Volatile var holdPurchase = false
    private var gate = CompletableDeferred<Unit>()

    fun releasePurchase() = gate.complete(Unit)

    /** 결제가 끝나면 스토어 목록에도 들어간다(실제 Play 와 같다). */
    override suspend fun queryPurchases(): StoreQuery {
        queryCalls++
        return if (queryFails) StoreQuery.Failure else StoreQuery.Success(owned)
    }

    override suspend fun localizedPrice(): String? = price

    override suspend fun purchase(): PurchaseResult {
        purchaseCalls++
        if (holdPurchase) {
            gate.await()
            gate = CompletableDeferred()
        }
        val result = nextPurchase
        when (result) {
            is PurchaseResult.Purchased -> owned = owned + result.purchase
            PurchaseResult.Pending -> owned = owned + pending("token-pending")
            else -> Unit
        }
        return result
    }

    override suspend fun acknowledge(token: String): Boolean {
        if (!acknowledgeSucceeds) return false
        acknowledged += token
        owned = owned.map { if (it.token == token) it.copy(acknowledged = true) else it }
        return true
    }

    /** 앱 밖에서 대기 결제가 끝났다(E-03·13). */
    fun completePending() {
        owned = owned.map { if (it.state == StorePurchase.State.Pending) it.copy(state = StorePurchase.State.Purchased) else it }
    }

    /** 환불(E-11). 스토어 목록에서 사라진다. */
    fun refund() {
        owned = emptyList()
    }

    fun failWith(reason: FailReason) {
        nextPurchase = PurchaseResult.Failed(reason)
    }

    companion object {
        fun purchased(token: String, acknowledged: Boolean = true) =
            StorePurchase(token, StorePurchase.State.Purchased, acknowledged)

        fun pending(token: String) = StorePurchase(token, StorePurchase.State.Pending, acknowledged = false)
    }
}

class FakeEntitlementCache(@Volatile var stored: Entitlement = Entitlement.Free) : EntitlementCache {
    @Volatile var failNextSave = false

    override suspend fun load(): Entitlement = stored
    override suspend fun save(entitlement: Entitlement) {
        if (failNextSave) {
            failNextSave = false
            throw FakeStorageException("entitlement")
        }
        stored = entitlement
    }
}

class FakeNetworkMonitor(@Volatile var online: Boolean = true) : NetworkMonitor {
    override fun isOnline() = online
}
