package app.signkorean.di

import android.content.res.AssetManager
import app.signkorean.adapter.platform.SystemTimeClock
import app.signkorean.adapter.platform.UuidIdGenerator
import app.signkorean.domain.common.AppDispatchers
import app.signkorean.domain.common.Clock
import app.signkorean.domain.common.IdGenerator
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.test.verify.verify

/** AR-05: Composition Root 의 조립이 빠짐없이 해석되는지 검사한다. */
class KoinModulesTest {

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `AR-05 모든 Koin 모듈의 정의가 해석된다`() {
        // verify() 는 생성자 인자 타입마다 Koin 정의를 찾는다. 아래 타입은 정의가 아니라 조립부에서 값을 직접 넣는다.
        // 디스패처: kotlinx.coroutines.Dispatchers / AssetManager·String: androidContext().assets 와 기본 경로 /
        // Set: 픽토그램 목록(Pictograms.ALL).
        val passedDirectly = listOf(CoroutineDispatcher::class, AssetManager::class, String::class, Set::class)
        appModules.forEach { it.verify(extraTypes = passedDirectly) }
    }

    @Test
    fun `포트마다 실제 구현이 연결된다`() {
        val koin = koinApplication { modules(appModules) }.koin
        assertSame(SystemTimeClock, koin.get<Clock>())
        assertTrue(koin.get<IdGenerator>() is UuidIdGenerator)
        val dispatchers = koin.get<AppDispatchers>()
        assertTrue(dispatchers.io !== dispatchers.default)
    }

    @Test
    fun `싱글턴 포트는 같은 인스턴스를 돌려준다`() {
        val koin = koinApplication { modules(appModules) }.koin
        assertSame(koin.get<IdGenerator>(), koin.get<IdGenerator>())
    }
}
