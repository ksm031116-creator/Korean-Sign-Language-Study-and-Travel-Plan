package app.signkorean.domain.entitlement

/** 구매 권한. 두 가지뿐이다 (PRD §3.4). 결제 진행 상태와 따로 둔다 (설계서 §6.4). */
enum class Entitlement { Free, Paid }

/** 지금의 권한. [Entitlements] 가 마지막으로 확인된 값을 돌려준다. */
fun interface EntitlementSource {
    suspend fun current(): Entitlement
}

/** 결제가 실패한 이유. 화면은 어느 경우든 "과금되지 않았음"을 알리고 다시 시도하게 한다 (E-06). */
enum class FailReason {
    /** 스토어 앱·서비스에 닿지 않는다. 잠시 뒤 다시 시도하면 될 수 있다. */
    StoreUnavailable,

    /** 상품을 찾지 못했다(국가·계정 제한, 등록 누락). */
    ProductUnavailable,
    Unknown,
}

/**
 * SC21 의 결제 진행 상태 (설계서 §6.4). 권한과 따로 움직인다. [Pending] 이어도 권한은 Free 다.
 * [Succeeded] 는 설계서 표에 없던 상태로, 성공 토스트와 보류 동작 재개(Phase 13)의 신호다.
 */
sealed interface PurchaseFlowState {
    data object Idle : PurchaseFlowState

    /** 버튼 비활성, 중복 탭 차단 (E-08). */
    data object InProgress : PurchaseFlowState

    /** 편의점 결제 등. 앱 밖에서 끝나면 다음 확인 때 Paid 가 된다 (E-03·13). */
    data object Pending : PurchaseFlowState
    data object Succeeded : PurchaseFlowState

    /** 오류 표시 없이 돌아간다 (E-05). */
    data object Cancelled : PurchaseFlowState
    data class Failed(val reason: FailReason) : PurchaseFlowState

    /** 결제를 시도하지 않았다 (E-07). */
    data object Offline : PurchaseFlowState
}

/** 스토어가 알려 준 이 앱 상품의 구매 한 건. 상품 ID 거르기는 게이트웨이가 한다. */
data class StorePurchase(val token: String, val state: State, val acknowledged: Boolean) {
    enum class State { Purchased, Pending }
}

/** 구매 목록 조회. 실패와 "구매 없음"(성공 + 빈 목록)을 구별한다 (NFR09). */
sealed interface StoreQuery {
    data class Success(val purchases: List<StorePurchase>) : StoreQuery
    data object Failure : StoreQuery
}

sealed interface PurchaseResult {
    data class Purchased(val purchase: StorePurchase) : PurchaseResult
    data object Pending : PurchaseResult
    data object Cancelled : PurchaseResult

    /** 이 계정이 이미 샀다(재설치 후 Unlock). 구매 목록을 다시 읽어 Paid 로 만든다. */
    data object AlreadyOwned : PurchaseResult
    data class Failed(val reason: FailReason) : PurchaseResult
}

sealed interface RestoreResult {
    data object Restored : RestoreResult
    data object NothingToRestore : RestoreResult

    /** 확인하지 못했다. 권한은 그대로다. */
    data object Failed : RestoreResult
}

/** 스토어 결제 포트 (설계서 §6.6). 구현은 Play Billing. 사용자 동작 없이 결제를 다시 시도하지 않는다. */
interface EntitlementGateway {
    suspend fun queryPurchases(): StoreQuery

    /** 스토어가 현지화한 가격 문자열(예: "US$0.99", "¥150"). 상품을 못 찾으면 null. */
    suspend fun localizedPrice(): String?

    /** 결제 화면을 띄우고 결과를 기다린다. */
    suspend fun purchase(): PurchaseResult

    /** 구매 확인. 3일 안에 하지 않으면 Play 가 환불한다. 실패하면 false. */
    suspend fun acknowledge(token: String): Boolean
}

/** 마지막으로 확인된 권한. 스토어에 닿지 않을 때 이 값을 쓴다 (E-01). 구현은 DataStore. */
interface EntitlementCache {
    suspend fun load(): Entitlement
    suspend fun save(entitlement: Entitlement)
}

/** 지금 인터넷에 연결돼 있는지. 오프라인이면 결제를 시도하지 않는다 (E-07). */
fun interface NetworkMonitor {
    fun isOnline(): Boolean
}
