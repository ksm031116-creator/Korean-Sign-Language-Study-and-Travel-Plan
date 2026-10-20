package app.signkorean.di

import app.signkorean.adapter.content.AndroidAssetContentPackSource
import app.signkorean.adapter.content.AssetContentRepository
import app.signkorean.adapter.content.ContentIssueReporter
import app.signkorean.adapter.content.ContentPackParser
import app.signkorean.adapter.content.ContentPackSource
import app.signkorean.adapter.content.LogcatContentIssueReporter
import app.signkorean.adapter.db.RoomLearningStorage
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
import app.signkorean.domain.locale.CurrentLocale
import app.signkorean.domain.onboarding.OnboardingStore
import app.signkorean.domain.locale.ResolveAppLanguage
import kotlinx.coroutines.Dispatchers
import app.signkorean.ui.learn.LearnViewModel
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
    factory { StartSession(get(), get(), get(), get()) }
    factory { ResumeSession(get()) }
    factory { JudgeCard(get(), get(), get(), get()) }
    factory { UndoJudgement(get(), get(), get()) }
    single<OnboardingStore> { DataStoreOnboardingStore.create(androidContext()) }
}

/** 화면 ViewModel. 화면은 koinViewModel() 로만 받는다 (AR-03). */
val uiModule = module {
    viewModelOf(::LearnViewModel)
}

/** 앱이 시작할 때 올리는 모듈 전체. 기능이 늘면 여기에 모듈을 추가한다. */
val appModules = listOf(platformModule, contentModule, localeModule, learningModule, uiModule)
