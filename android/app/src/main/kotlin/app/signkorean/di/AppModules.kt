package app.signkorean.di

import app.signkorean.adapter.content.AndroidAssetContentPackSource
import app.signkorean.adapter.content.AssetContentRepository
import app.signkorean.adapter.content.ContentIssueReporter
import app.signkorean.adapter.content.ContentPackParser
import app.signkorean.adapter.content.ContentPackSource
import app.signkorean.adapter.content.LogcatContentIssueReporter
import app.signkorean.adapter.platform.SystemTimeClock
import app.signkorean.adapter.platform.UuidIdGenerator
import app.signkorean.domain.common.AppDispatchers
import app.signkorean.domain.common.Clock
import app.signkorean.domain.common.IdGenerator
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.ContentValidator
import app.signkorean.domain.content.PictogramCatalog
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
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

/** 앱이 시작할 때 올리는 모듈 전체. 기능이 늘면 여기에 모듈을 추가한다. */
val appModules = listOf(platformModule, contentModule)
