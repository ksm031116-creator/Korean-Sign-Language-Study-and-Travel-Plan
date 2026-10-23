package app.signkorean.di

import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.domain.entitlement.PurchaseResult
import app.signkorean.domain.entitlement.StorePurchase
import app.signkorean.domain.entitlement.StoreQuery
import kotlinx.coroutines.delay
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

    // "토큰|acknowledged" 를 한 줄씩 저장한다.
    @Volatile private var owned: List<StorePurchase> = prefs.getStringSet(KEY, emptySet()).orEmpty().sorted().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size == 2) StorePurchase(parts[0], StorePurchase.State.Purchased, acknowledged = parts[1] == "1") else null
    }
        set(value) {
            field = value
            prefs.edit().putStringSet(KEY, value.map { it.token + "|" + (if (it.acknowledged) "1" else "0") }.toSet()).commit()
        }

    override suspend fun queryPurchases(): StoreQuery = StoreQuery.Success(owned)

    // 스토어가 주는 문자열 자리. 실제 앱은 이 값을 Play 에서 받는다 (L10N-11).
    override suspend fun localizedPrice(): String = "US$0.99"

    override suspend fun purchase(): PurchaseResult {
        delay(800)
        val bought = StorePurchase("simulated-" + UUID.randomUUID(), StorePurchase.State.Purchased, acknowledged = false)
        owned = owned + bought
        return PurchaseResult.Purchased(bought)
    }

    override suspend fun acknowledge(token: String): Boolean {
        owned = owned.map { if (it.token == token) it.copy(acknowledged = true) else it }
        return true
    }

    fun relock() {
        owned = emptyList()
    }

    private companion object {
        const val KEY = "owned"
    }
}
