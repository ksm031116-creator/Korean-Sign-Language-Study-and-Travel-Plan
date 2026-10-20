package app.signkorean.di

import app.signkorean.adapter.content.AssetContentRepository
import app.signkorean.adapter.content.ContentPackSource
import app.signkorean.domain.common.AppDispatchers
import app.signkorean.domain.content.ContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.io.File

/** 조립된 콘텐츠 그래프가 실제 팩을 읽는다. Android assets 대신 같은 파일을 연다. */
class ContentModuleTest {

    private val fromFile = module {
        single<ContentPackSource> { ContentPackSource { File("../adapter/src/main/assets/content/content-pack.json").inputStream() } }
        // 단위 테스트에는 Android 메인 루퍼가 없다.
        single { AppDispatchers(io = Dispatchers.IO, default = Dispatchers.Default, main = Dispatchers.Unconfined) }
    }

    @Test
    fun `ContentRepository 는 내장 팩을 읽는 실제 구현이고 135장·49곳을 돌려준다`() {
        val app = koinApplication {
            allowOverride(true)
            modules(appModules + fromFile)
        }
        try {
            val repository = app.koin.get<ContentRepository>()
            assertTrue(repository is AssetContentRepository)
            assertSame(repository, app.koin.get<ContentRepository>())
            val pack = runBlocking { repository.load() }
            assertEquals(135, pack.cards.size)
            assertEquals(49, pack.places.size)
        } finally {
            app.close()
        }
    }
}
