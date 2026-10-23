package app.signkorean.di

import app.signkorean.BuildConfig
import app.signkorean.adapter.billing.PlayBillingEntitlementGateway
import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.ui.plan.DebugRelock
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 릴리스 조립 (설계 결정 20). 스토어에 올리지 않으므로 릴리스 APK 도 모의 결제를 쓴다. 다시 잠그기(디버그 전용)는 없다.
 * 모듈 순서·덮어쓰기 설정이 바뀌어 결제 버튼이 Play 연결 실패로 바뀌는 회귀를 잡는다(리뷰 M6).
 */
@RunWith(RobolectricTestRunner::class)
class ReleaseVariantGraphTest {

    @Test
    fun `릴리스는 결제 방식대로 조립되고 다시 잠그기는 없다`() {
        // 건너뛰지 않는다: -PrealBilling=true 빌드면 Play 결제여야 한다(리뷰 후속 L3).
        val app = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(appModules + variantModules)
        }
        try {
            val gateway = app.koin.get<EntitlementGateway>()
            if (BuildConfig.SIMULATED_BILLING) assertTrue(gateway is SimulatedBillingGateway)
            else assertTrue(gateway is PlayBillingEntitlementGateway)
            assertNull(app.koin.getOrNull<DebugRelock>())
        } finally {
            app.close()
        }
    }
}
