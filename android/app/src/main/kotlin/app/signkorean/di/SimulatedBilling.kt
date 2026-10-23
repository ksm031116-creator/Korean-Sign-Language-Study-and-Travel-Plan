package app.signkorean.di

import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.domain.entitlement.PurchaseResult
import app.signkorean.domain.entitlement.StorePurchase
import app.signkorean.domain.entitlement.StoreQuery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.UUID

/**
 * 결제 방식. 이 앱은 Play 스토어에 올리지 않으므로 기본은 모의 결제다(디버그·릴리스 모두). 실제 Play 결제는
 * `-PrealBilling=true` 로 만든 빌드에서만 쓴다 — 코드는 PlayBillingEntitlementGateway 에 그대로 있다.
 */
val simulatedBillingModule: Module get() = module {
    single { SimulatedBillingGateway(androidContext()) }
    single<EntitlementGateway> { get<SimulatedBillingGateway>() }
}

/**
 * 모의 결제. 결제 버튼을 누르면 0.8초 뒤 구매 완료가 된다(실제 돈은 오가지 않는다).
 * 구매 기록은 기기에 저장해 앱을 껐다 켜도 남는다 — 메모리에만 두면 다시 켤 때 무료로 돌아갔다(BIL-01).
 */
class SimulatedBillingGateway(context: android.content.Context) : EntitlementGateway {
    private val prefs = context.applicationContext.getSharedPreferences("simulated_billing", android.content.Context.MODE_PRIVATE)

    // 읽고-고치고-저장하기를 한 번에 하나씩. 결제와 확인이 겹쳐도 한쪽이 다른 쪽을 덮어 구매가 사라지지 않는다(리뷰 L11).
    private val lock = Mutex()

    // "토큰|acknowledged" 를 한 줄씩 저장한다. 구분자는 마지막 '|' 다 — 토큰에 '|' 가 있어도 버리지 않는다.
    @Volatile private var owned: List<StorePurchase> = prefs.getStringSet(KEY, emptySet()).orEmpty().sorted().mapNotNull { line ->
        val token = line.substringBeforeLast('|', missingDelimiterValue = "")
        if (token.isEmpty()) null else StorePurchase(token, StorePurchase.State.Purchased, acknowledged = line.substringAfterLast('|') == "1")
    }

    private suspend fun change(transform: (List<StorePurchase>) -> List<StorePurchase>) = lock.withLock {
        val next = transform(owned)
        // commit() 은 디스크에 쓸 때까지 막으므로 메인 스레드가 아닌 IO 에서. 쓰기 시작했으면 부른 쪽이 취소돼도
        // 끝까지 쓰고 메모리도 맞춘다 — 디스크에만 있고 화면에는 없는 구매가 생기지 않게(리뷰 후속 M2).
        withContext(NonCancellable + Dispatchers.IO) {
            prefs.edit().putStringSet(KEY, next.map { it.token + "|" + (if (it.acknowledged) "1" else "0") }.toSet()).commit()
            owned = next
        }
    }

    override suspend fun queryPurchases(): StoreQuery = StoreQuery.Success(owned)

    // 스토어가 주는 문자열 자리. 실제 앱은 이 값을 Play 에서 받는다 (L10N-11).
    override suspend fun localizedPrice(): String = "US$0.99"

    override suspend fun purchase(): PurchaseResult {
        delay(800)
        val bought = StorePurchase("simulated-" + UUID.randomUUID(), StorePurchase.State.Purchased, acknowledged = false)
        change { it + bought }
        return PurchaseResult.Purchased(bought)
    }

    override suspend fun acknowledge(token: String): Boolean {
        change { list -> list.map { if (it.token == token) it.copy(acknowledged = true) else it } }
        return true
    }

    suspend fun relock() {
        change { emptyList() }
    }

    private companion object {
        const val KEY = "owned"
    }
}
