package app.signkorean.domain.entitlement

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 권한 확인·결제·복원 (FR23~26, NFR09, 설계서 §6.4). 앱에 하나만 둔다.
 *
 * - 스토어 조회가 **실패**하면 마지막 권한을 유지한다. 조회가 **성공**했는데 구매가 없으면 Free 다(환불).
 * - 확인 시점은 앱 시작·복귀(onResume)·결제와 복원 직후. 화면 쪽이 [refresh] 를 부른다.
 * - 구매 확인(acknowledge)이 실패해도 Paid 다. 확인 안 된 구매는 다음 [refresh] 가 다시 확인한다.
 */
class Entitlements(
    private val gateway: EntitlementGateway,
    private val cache: EntitlementCache,
    private val network: NetworkMonitor,
) : EntitlementSource {

    private val _entitlement = MutableStateFlow(Entitlement.Free)

    /** 캐시를 읽기 전에는 Free 다. 값이 필요한 곳은 [current] 를 쓴다. */
    val entitlement: StateFlow<Entitlement> = _entitlement.asStateFlow()

    private val _purchaseFlow = MutableStateFlow<PurchaseFlowState>(PurchaseFlowState.Idle)
    val purchaseFlow: StateFlow<PurchaseFlowState> = _purchaseFlow.asStateFlow()

    private val loadLock = Mutex()

    @Volatile private var loaded = false

    /** 조회 결과 반영을 한 번에 하나씩. 두 확인이 겹쳐 오래된 결과가 새 결과를 덮지 않게 한다. */
    private val storeLock = Mutex()

    /** 결제 화면은 한 번에 하나 (E-08). */
    private val purchaseLock = Mutex()

    override suspend fun current(): Entitlement {
        ensureLoaded()
        return _entitlement.value
    }

    suspend fun refresh() {
        sync()
    }

    suspend fun unlock() {
        if (!purchaseLock.tryLock()) return
        try {
            ensureLoaded()
            if (_entitlement.value == Entitlement.Paid) {
                _purchaseFlow.value = PurchaseFlowState.Succeeded
                return
            }
            if (!network.isOnline()) {
                _purchaseFlow.value = PurchaseFlowState.Offline
                return
            }
            _purchaseFlow.value = PurchaseFlowState.InProgress
            _purchaseFlow.value = try {
                onPurchase(gateway.purchase())
            } catch (e: CancellationException) {
                _purchaseFlow.value = PurchaseFlowState.Idle
                throw e
            } catch (e: Exception) {
                PurchaseFlowState.Failed(FailReason.Unknown)
            }
        } finally {
            purchaseLock.unlock()
        }
    }

    suspend fun restore(): RestoreResult {
        ensureLoaded()
        if (!network.isOnline()) return RestoreResult.Failed
        return when (val q = sync()) {
            StoreQuery.Failure -> RestoreResult.Failed
            is StoreQuery.Success ->
                if (q.purchases.any { it.state == StorePurchase.State.Purchased }) RestoreResult.Restored
                else RestoreResult.NothingToRestore
        }
    }

    /** 결과 안내를 닫는다. 결제 중에는 닫히지 않는다. */
    fun dismiss() {
        _purchaseFlow.update { if (it == PurchaseFlowState.InProgress) it else PurchaseFlowState.Idle }
    }

    suspend fun localizedPrice(): String? = try {
        gateway.localizedPrice()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun onPurchase(result: PurchaseResult): PurchaseFlowState = when (result) {
        is PurchaseResult.Purchased -> {
            storeLock.withLock {
                grant(Entitlement.Paid)
                if (!result.purchase.acknowledged) acknowledge(result.purchase.token)
            }
            PurchaseFlowState.Succeeded
        }
        PurchaseResult.Pending -> PurchaseFlowState.Pending
        PurchaseResult.Cancelled -> PurchaseFlowState.Cancelled
        is PurchaseResult.Failed -> PurchaseFlowState.Failed(result.reason)
        PurchaseResult.AlreadyOwned ->
            if (sync() is StoreQuery.Success && _entitlement.value == Entitlement.Paid) PurchaseFlowState.Succeeded
            else PurchaseFlowState.Failed(FailReason.Unknown)
    }

    private suspend fun sync(): StoreQuery = storeLock.withLock {
        ensureLoaded()
        val query = try {
            gateway.queryPurchases()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StoreQuery.Failure
        }
        if (query is StoreQuery.Success) apply(query.purchases)
        query
    }

    private suspend fun apply(purchases: List<StorePurchase>) {
        val owned = purchases.filter { it.state == StorePurchase.State.Purchased }
        if (owned.isNotEmpty()) {
            grant(Entitlement.Paid)
            owned.filterNot { it.acknowledged }.forEach { acknowledge(it.token) }
            _purchaseFlow.update { if (it == PurchaseFlowState.Pending) PurchaseFlowState.Succeeded else it }
        } else {
            grant(Entitlement.Free)
            val pending = purchases.any { it.state == StorePurchase.State.Pending }
            _purchaseFlow.update {
                when {
                    pending && it == PurchaseFlowState.Idle -> PurchaseFlowState.Pending
                    !pending && it == PurchaseFlowState.Pending -> PurchaseFlowState.Idle
                    else -> it
                }
            }
        }
    }

    /** 이번 실행은 확인된 값을 쓴다. 캐시 저장 실패는 다음 확인에서 다시 저장된다. */
    private suspend fun grant(entitlement: Entitlement) {
        _entitlement.value = entitlement
        try {
            cache.save(entitlement)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun acknowledge(token: String) {
        try {
            gateway.acknowledge(token)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loadLock.withLock {
            if (loaded) return
            _entitlement.value = try {
                cache.load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Entitlement.Free
            }
            loaded = true
        }
    }
}
