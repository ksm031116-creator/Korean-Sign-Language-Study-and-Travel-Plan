package app.signkorean.di

import app.signkorean.BuildConfig
import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.domain.place.PlaceSummaryProvider
import app.signkorean.domain.review.ReviewProvider
import app.signkorean.domain.route.DayLegs
import app.signkorean.domain.route.DayRoutePlanner
import app.signkorean.domain.route.RouteProvider
import app.signkorean.ui.plan.DebugRelock
import app.signkorean.ui.settings.SettingsViewModel
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import app.signkorean.SignKoreanApplication
import org.junit.After
import org.koin.core.context.GlobalContext
import org.koin.core.context.stopKoin
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

/**
 * 실제 앱이 시작할 때 만드는 디버그 Koin 그래프 (UPG-10). 앱 클래스가 appModules + variantModules 로 startKoin 하므로
 * Robolectric 이 앱을 띄운 뒤의 GlobalContext 를 그대로 본다. 모의 결제·표본 데이터 덮어쓰기와 getOrNull 연결이 실제로
 * 이어지는지 확인한다(정의 검사만으로는 덮어쓰기 순서와 getOrNull 누락을 잡지 못한다).
 */
// 다른 화면 테스트와 달리 실제 앱 클래스를 띄운다(robolectric.properties 의 기본값을 이 테스트만 바꾼다).
@RunWith(RobolectricTestRunner::class)
@Config(application = SignKoreanApplication::class)
class DebugGraphTest {

    private val koin get() = GlobalContext.get()

    @After
    fun tearDown() = stopKoin()

    @Test
    fun `모의 결제 빌드는 모의 게이트웨이와 다시 잠그기를 쓴다`() {
        assumeTrue(BuildConfig.SIMULATED_BILLING)
        assertTrue(koin.get<EntitlementGateway>() is SimulatedBillingGateway)
        assertNotNull(koin.getOrNull<DebugRelock>())
        // 설정 화면까지 getOrNull 로 실제 이어진다.
        assertNotNull(koin.get<SettingsViewModel>().state.value.relockLabel)
    }

    @Test
    fun `서버 주소가 없는 디버그 빌드는 표본 요약·리뷰·경로를 쓴다`() {
        assumeTrue(BuildConfig.API_BASE_URL.isBlank())
        assertTrue(koin.get<PlaceSummaryProvider>() is SamplePlaceSummaries)
        assertSame(SampleReviews, koin.get<ReviewProvider>())
        assertSame(SampleRoutes, koin.get<RouteProvider>())
    }

    @Test
    fun `일정의 구간 시간은 앱에 하나뿐인 경로 계획기에서 온다`() {
        assertSame(koin.get<DayRoutePlanner>(), koin.get<DayLegs>())
    }
}
