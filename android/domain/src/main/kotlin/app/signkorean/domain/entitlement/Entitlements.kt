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

    /** 스토어에 닿아 결과를 반영했으면 true. 실패하면 마지막 권한을 그대로 둔다. */
    suspend fun refresh(): Boolean = sync() is StoreQuery.Success

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
        PurchaseResult.AlreadyOwned, PurchaseResult.Unconfirmed -> settleFromStore()
    }

    /** 결제 결과를 스토어 목록으로 정한다. 대기 중인 구매가 있으면(같은 상품을 다시 결제) 실패가 아니라 대기다. */
    private suspend fun settleFromStore(): PurchaseFlowState {
        val query = sync()
        return when {
            _entitlement.value == Entitlement.Paid -> PurchaseFlowState.Succeeded
            query is StoreQuery.Success && query.purchases.any { it.state == StorePurchase.State.Pending } ->
                PurchaseFlowState.Pending
            else -> PurchaseFlowState.Failed(FailReason.Unknown)
        }
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
            val wasFree = _entitlement.value == Entitlement.Free
            grant(Entitlement.Paid)
            owned.filterNot { it.acknowledged }.forEach { acknowledge(it.token) }
            // 대기 결제가 끝났거나, 결제 화면을 벗어난 뒤 결과가 스토어에만 남은 경우도 성공 신호를 준다.
            // 이미 Paid 였던 확인(앱을 켤 때마다)은 신호를 만들지 않는다. 결제 중이면 unlock 이 직접 정한다.
            _purchaseFlow.update {
                when {
                    it == PurchaseFlowState.Pending -> PurchaseFlowState.Succeeded
                    wasFree && it != PurchaseFlowState.InProgress -> PurchaseFlowState.Succeeded
                    else -> it
                }
            }
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
        // 스토어로 확인한 값이 캐시보다 새롭다. 앞서 캐시 읽기가 실패했어도 이제 다시 읽지 않는다.
        loadLock.withLock {
            _entitlement.value = entitlement
            loaded = true
        }
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
            // 읽기가 실패하면 이번에는 Free 로 두되 loaded 를 세우지 않는다. 일시적 입출력 오류 하나로
            // 오프라인 Paid 사용자가 그 실행 내내 Free 가 되지 않게, 다음 호출이 다시 읽는다.
            _entitlement.value = try {
                cache.load().also { loaded = true }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Entitlement.Free
            }
        }
    }
}
