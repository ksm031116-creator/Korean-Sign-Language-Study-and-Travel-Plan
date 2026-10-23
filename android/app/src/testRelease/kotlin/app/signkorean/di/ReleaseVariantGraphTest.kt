package app.signkorean.di

import app.signkorean.BuildConfig
import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.ui.plan.DebugRelock
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
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
    fun `릴리스는 모의 결제를 쓰고 다시 잠그기는 없다`() {
        assumeTrue(BuildConfig.SIMULATED_BILLING)
        val app = koinApplication {
            androidContext(RuntimeEnvironment.getApplication())
            modules(appModules + variantModules)
        }
        try {
            assertTrue(app.koin.get<EntitlementGateway>() is SimulatedBillingGateway)
            assertNull(app.koin.getOrNull<DebugRelock>())
        } finally {
            app.close()
        }
    }
}
