package app.signkorean.di

import app.signkorean.BuildConfig
import app.signkorean.R
import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.domain.entitlement.PurchaseResult
import app.signkorean.domain.entitlement.StorePurchase
import app.signkorean.domain.entitlement.StoreQuery
import app.signkorean.ui.plan.DebugRelock
import kotlinx.coroutines.delay
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.UUID

/**
 * 디버그 빌드 전용 조립 (설계서 §5.5 DebugOverrides). 이 소스 세트는 릴리스 APK 에 들어가지 않는다.
 *
 * 에뮬레이터 이미지에는 Play 스토어가 없고, 실결제는 Play Console 에 올린 빌드에서만 된다. 그래서 디버그 빌드는
 * 기본으로 모의 결제를 쓴다. 실기기에서 Play 결제를 시험하려면 `./gradlew installDebug -PrealBilling=true`.
 */
val variantModules: List<Module> = if (BuildConfig.SIMULATED_BILLING) {
    listOf(
        module {
            single { SimulatedBillingGateway() }
            single<EntitlementGateway> { get<SimulatedBillingGateway>() }
            single<DebugRelock> {
                val gateway = get<SimulatedBillingGateway>()
                object : DebugRelock {
                    override val label = R.string.debug_relock
                    override suspend fun relock() = gateway.relock()
                }
            }
        },
    )
} else {
    emptyList()
}

/**
 * 모의 결제. 결제는 잠깐 기다린 뒤 성공하고, 구매는 프로세스가 살아 있는 동안만 기억한다(다시 켜면 캐시의 권한을 쓰고,
 * 시작 확인에서 구매가 없으니 Free 로 돌아간다 — 실제 환불과 같은 경로라 E-11 도 눈으로 볼 수 있다).
 */
class SimulatedBillingGateway : EntitlementGateway {
    @Volatile private var owned: List<StorePurchase> = emptyList()

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
}
