package app.signkorean.di

import app.signkorean.adapter.content.AndroidAssetContentPackSource
import app.signkorean.adapter.content.AssetContentRepository
import app.signkorean.adapter.content.ContentIssueReporter
import app.signkorean.adapter.content.ContentPackParser
import app.signkorean.adapter.content.ContentPackSource
import app.signkorean.adapter.content.LogcatContentIssueReporter
import app.signkorean.adapter.db.RoomLearningStorage
import app.signkorean.adapter.audio.MediaPlayerAudioPlayer
import app.signkorean.adapter.locale.AppCompatCurrentLocale
import app.signkorean.adapter.prefs.DataStoreOnboardingStore
import app.signkorean.adapter.platform.SystemTimeClock
import app.signkorean.adapter.platform.UuidIdGenerator
import app.signkorean.domain.common.AppDispatchers
import app.signkorean.domain.common.Clock
import app.signkorean.domain.common.IdGenerator
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.ContentValidator
import app.signkorean.domain.content.PictogramCatalog
import app.signkorean.domain.learning.JudgeCard
import app.signkorean.domain.learning.LearningStateStore
import app.signkorean.domain.learning.ResumeSession
import app.signkorean.domain.learning.SessionStore
import app.signkorean.domain.learning.StartSession
import app.signkorean.domain.learning.TransactionRunner
import app.signkorean.domain.learning.UndoJudgement
import app.signkorean.domain.audio.AudioPlayer
import app.signkorean.domain.bookmark.BookmarkStore
import app.signkorean.domain.bookmark.ClearBookmarks
import app.signkorean.domain.bookmark.SetBookmark
import app.signkorean.ForegroundActivityTracker
import app.signkorean.adapter.billing.ForegroundActivity
import app.signkorean.adapter.billing.PlayBillingEntitlementGateway
import app.signkorean.adapter.platform.AndroidNetworkMonitor
import app.signkorean.adapter.prefs.DataStoreEntitlementCache
import app.signkorean.domain.entitlement.EntitlementCache
import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.domain.entitlement.EntitlementSource
import app.signkorean.domain.entitlement.Entitlements
import app.signkorean.domain.entitlement.NetworkMonitor
import app.signkorean.domain.learning.ResetLearning
import org.koin.dsl.bind
import app.signkorean.adapter.prefs.DataStorePendingActionStore
import app.signkorean.domain.upgrade.PendingActionStore
import app.signkorean.domain.upgrade.ResumePendingAction
import app.signkorean.domain.upgrade.StartupPendingCheck
import app.signkorean.domain.common.AppScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import app.signkorean.ui.plan.PlanGateViewModel
import app.signkorean.ui.places.PlaceDetailViewModel
import app.signkorean.ui.places.PlacesViewModel
import app.signkorean.adapter.net.UnavailablePlaceSummaries
import app.signkorean.domain.place.PlaceSummaryProvider
import app.signkorean.ui.upgrade.UpgradeViewModel
import app.signkorean.domain.locale.CurrentLocale
import app.signkorean.domain.onboarding.OnboardingStore
import app.signkorean.domain.locale.ResolveAppLanguage
import kotlinx.coroutines.Dispatchers
import android.content.pm.ApplicationInfo
import app.signkorean.ui.explore.CollectionViewModel
import app.signkorean.ui.explore.ExploreViewModel
import app.signkorean.ui.learn.LearnViewModel
import app.signkorean.ui.saved.SavedViewModel
import app.signkorean.ui.onboarding.AppGateViewModel
import app.signkorean.ui.onboarding.FirstRunViewModel
import org.koin.core.module.dsl.viewModel
import app.signkorean.ui.learn.ResultViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Composition Root. 포트와 구현의 연결은 이 패키지에서만 한다 (설계서 원칙 4, AR-03).
 * 클래스는 생성자로만 의존성을 받고, Koin 타입을 모른다.
 */
val platformModule = module {
    single<Clock> { SystemTimeClock }
    single<IdGenerator> { UuidIdGenerator() }
    single { AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Main) }
    // 화면이 닫혀도 끝까지 가야 하는 일(결제, 권한 확인). 실패가 다른 작업을 멈추지 않게 SupervisorJob.
    single { AppScope(CoroutineScope(SupervisorJob() + get<AppDispatchers>().default)) }
}

/** 콘텐츠 팩 (Phase 2). 팩은 앱에 내장되고 한 번만 읽는다. */
val contentModule = module {
    single<ContentPackSource> { AndroidAssetContentPackSource(androidContext().assets) }
    single<ContentIssueReporter> { LogcatContentIssueReporter }
    single { ContentPackParser() }
    single { PictogramCatalog.BUNDLED }
    single { ContentValidator(get()) }
    single<ContentRepository> { AssetContentRepository(get(), get(), get(), get(), get()) }
}

/** 장소 (Phase 14). 리뷰 요약은 중계 서버가 생기기 전까지 "없음"이다(Phase 15 에서 HTTP). */
val placeModule = module {
    single<PlaceSummaryProvider> { UnavailablePlaceSummaries }
}

/** 언어 (Phase 3). 앱 언어 저장은 AppCompat 하나만 한다 (설계서 §8.2). */
val localeModule = module {
    single<CurrentLocale> { AppCompatCurrentLocale(androidContext()) }
    factory { ResolveAppLanguage(get()) }
}

/** 학습 저장과 판정 (Phase 6). 판정은 카드 상태·세션을 한 트랜잭션으로 저장한다. */
val learningModule = module {
    single { RoomLearningStorage.open(androidContext()) }
    single<LearningStateStore> { get<RoomLearningStorage>().states }
    single<SessionStore> { get<RoomLearningStorage>().sessions }
    single<TransactionRunner> { get<RoomLearningStorage>().transactions }
    single<BookmarkStore> { get<RoomLearningStorage>().bookmarks }
    factory { SetBookmark(get(), get(), get(), get()) }
    factory { ClearBookmarks(get()) }
    factory { StartSession(get(), get(), get(), get()) }
    factory { ResumeSession(get()) }
    factory { JudgeCard(get(), get(), get(), get()) }
    factory { UndoJudgement(get(), get(), get()) }
    factory { ResetLearning(get(), get(), get()) }
    single<OnboardingStore> { DataStoreOnboardingStore.create(androidContext()) }
    single<AudioPlayer> { MediaPlayerAudioPlayer(androidContext()) }
}

/**
 * 권한과 결제 (Phase 12, 설계서 §6.4). [Entitlements] 는 앱에 하나다. 권한 확인은 앱 시작과 전면 복귀 때
 * SignKoreanApplication 이 부르고, 결제·복원 직후에는 Entitlements 가 스스로 반영한다.
 */
val entitlementModule = module {
    single { ForegroundActivityTracker() } bind ForegroundActivity::class
    single { PlayBillingEntitlementGateway(androidContext(), get(), get<AppDispatchers>().main) } bind EntitlementGateway::class
    single<EntitlementCache> { DataStoreEntitlementCache.create(androidContext()) }
    single<NetworkMonitor> { AndroidNetworkMonitor(androidContext()) }
    single { Entitlements(get(), get(), get()) } bind EntitlementSource::class
    // 결제 뒤 이어서 할 동작 (Phase 13). 여러 곳(SC21·앱 수준 확인)이 불러도 한 번만 수행하도록 하나만 둔다.
    single<PendingActionStore> { DataStorePendingActionStore.create(androidContext()) }
    single { ResumePendingAction(get(), get()) }
    single { StartupPendingCheck(get(), get(), get()) }
}

/** 화면 ViewModel. 화면은 koinViewModel() 로만 받는다 (AR-03). */
val uiModule = module {
    viewModelOf(::LearnViewModel)
    viewModelOf(::ResultViewModel)
    viewModelOf(::ExploreViewModel)
    viewModelOf(::SavedViewModel)
    viewModel { params -> UpgradeViewModel(params.get(), get(), get(), get(), get(), get()) }
    // 디버그 빌드의 모의 결제에만 다시 잠그기가 있다(variantModules). 릴리스는 null.
    viewModel { PlanGateViewModel(get(), getOrNull()) }
    viewModel { params -> PlacesViewModel(params.get(), get(), get(), get(), get(), get()) }
    viewModel { params -> PlaceDetailViewModel(params.get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params -> CollectionViewModel(params.get(), get(), get(), get()) }
    viewModelOf(::AppGateViewModel)
    viewModel {
        val debuggable = (androidContext().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        // 디버그 빌드만 검수 전 언어(ja)를 고를 수 있다. 출시 빌드는 검수 완료 언어만 (설계서 §8.8).
        FirstRunViewModel(get(), get(), get(), includeDraftLanguages = debuggable)
    }
}

/** 앱이 시작할 때 올리는 모듈 전체. 기능이 늘면 여기에 모듈을 추가한다. */
val appModules = listOf(platformModule, contentModule, placeModule, localeModule, learningModule, entitlementModule, uiModule)
