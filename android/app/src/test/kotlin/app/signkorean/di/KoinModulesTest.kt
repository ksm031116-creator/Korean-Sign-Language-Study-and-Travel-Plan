package app.signkorean.di

import android.content.Context
import android.content.res.AssetManager
import app.signkorean.adapter.platform.SystemTimeClock
import app.signkorean.adapter.platform.UuidIdGenerator
import app.signkorean.domain.common.AppDispatchers
import app.signkorean.domain.common.Clock
import app.signkorean.domain.common.IdGenerator
import app.signkorean.domain.content.PictogramCatalog
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify

/** AR-05: Composition Root 의 조립이 빠짐없이 해석되는지 검사한다. */
class KoinModulesTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `AR-05 모든 Koin 모듈의 정의가 해석된다`() {
        // verify() 는 생성자 인자 타입마다 Koin 정의를 찾는다. 아래 타입은 정의가 아니라 조립부에서 값을 직접 넣는다.
        // 디스패처: kotlinx.coroutines.Dispatchers / AssetManager·Context: androidContext().
        // String·Set 같은 넓은 타입은 넣지 않는다. 넣으면 앞으로 그런 의존성이 빠져도 검사가 통과해 버린다.
        val passedDirectly = listOf(CoroutineDispatcher::class, AssetManager::class, Context::class)
        // 픽토그램 목록 Set 은 PictogramCatalog 하나에만 값으로 들어간다. 그 정의에만 허용한다.
        // 화면 인자(SC03 의 출처)는 koinViewModel 의 parameters 로 넘긴다.
        val catalogIds = injectedParameters(
            definition<PictogramCatalog>(Set::class),
            definition<app.signkorean.ui.explore.CollectionViewModel>(app.signkorean.domain.learning.SessionSource::class),
            // 디버그 빌드 여부(앱 정보에서 읽은 값).
            definition<app.signkorean.ui.onboarding.FirstRunViewModel>(Boolean::class),
            // 앱 범위 코루틴은 조립부에서 바로 만든다(SupervisorJob + 기본 디스패처).
            definition<app.signkorean.domain.common.AppScope>(kotlinx.coroutines.CoroutineScope::class),
            // SC08·SC07 의 카드·장소 ID (화면 인자).
            definition<app.signkorean.ui.places.PlacesViewModel>(app.signkorean.domain.content.CardId::class),
            definition<app.signkorean.ui.places.PlaceDetailViewModel>(app.signkorean.domain.content.PlaceId::class),
            definition<app.signkorean.ui.reviews.ReviewsViewModel>(app.signkorean.domain.content.PlaceId::class),
            // 중계 서버 주소(BuildConfig)와 시간 제한 기본값은 조립부에서 값으로 넣는다.
            definition<app.signkorean.adapter.net.ApiClient>(String::class, Int::class),
            // 여행 화면 인자: 여행·날짜·장소 ID.
            definition<app.signkorean.ui.plan.TripFormViewModel>(app.signkorean.domain.trip.TripId::class),
            definition<app.signkorean.ui.plan.AddToPlanViewModel>(app.signkorean.domain.content.PlaceId::class),
            definition<app.signkorean.ui.plan.DayPlanViewModel>(app.signkorean.domain.trip.TripId::class, java.time.LocalDate::class),
            definition<app.signkorean.ui.plan.PlacePickerViewModel>(app.signkorean.domain.trip.TripId::class, java.time.LocalDate::class),
            // SC18 이 보여 줄 것(하루 경로 또는 한 장소).
            definition<app.signkorean.ui.map.MapViewModel>(app.signkorean.ui.map.MapTarget::class),
            definition<app.signkorean.ui.plan.DayStudyViewModel>(app.signkorean.domain.trip.TripId::class, java.time.LocalDate::class),
            // 설정: 디버그 빌드 여부와 디버그 전용 다시 잠그기.
            definition<app.signkorean.ui.settings.SettingsViewModel>(Boolean::class, app.signkorean.ui.plan.DebugRelock::class),
            // SC21 을 연 진입점(화면 인자).
            definition<app.signkorean.ui.upgrade.UpgradeViewModel>(app.signkorean.ui.upgrade.UpgradeFeature::class),
            // 다시 잠그기는 디버그 빌드의 모의 결제에만 있고(getOrNull), 릴리스에는 없다.
            definition<app.signkorean.ui.plan.PlanGateViewModel>(app.signkorean.ui.plan.DebugRelock::class),
        )
        // 모듈끼리 서로의 정의를 쓰므로(예: StartSession 이 platformModule 의 IdGenerator) 전체를 한 그래프로 검사한다.
        module { includes(appModules) }.verify(extraTypes = passedDirectly, injections = catalogIds)
    }

    @Test
    fun `포트마다 실제 구현이 연결된다`() {
        val app = koinApplication { modules(appModules) }
        try {
            val koin = app.koin
            assertSame(SystemTimeClock, koin.get<Clock>())
            assertTrue(koin.get<IdGenerator>() is UuidIdGenerator)
            val dispatchers = koin.get<AppDispatchers>()
            assertTrue(dispatchers.io !== dispatchers.default)
        } finally {
            app.close()
        }
    }

    @Test
    fun `싱글턴 포트는 같은 인스턴스를 돌려준다`() {
        val app = koinApplication { modules(appModules) }
        try {
            assertSame(app.koin.get<IdGenerator>(), app.koin.get<IdGenerator>())
        } finally {
            app.close()
        }
    }
}
