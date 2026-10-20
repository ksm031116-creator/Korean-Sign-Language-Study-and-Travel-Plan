package app.signkorean.di

import app.signkorean.architecture.SourceRules
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.dsl.module

/**
 * AR-04: 릴리스 Koin 그래프에 가짜 구현이 연결되면 안 된다 (설계서 §5.5, §9.6).
 * 디버그 전용 모듈(DebugOverrides)은 가짜를 쓸 수 있으므로 소스 검색이 아니라 실제 조립 결과를 본다.
 */
class ReleaseGraphTest {

    private class FakeBillingGateway

    @Test
    fun `AR-04 앱 모듈의 정의에 Fake 타입이 없다`() {
        assertEquals(emptyList<String>(), KoinGraph.boundTypeNames(appModules).filter(SourceRules::isFakeTypeName))
    }

    @Test
    fun `가짜가 연결된 모듈은 검사가 잡아낸다`() {
        val leaked = module { single { FakeBillingGateway() } }
        assertEquals(listOf("FakeBillingGateway"),
            KoinGraph.boundTypeNames(listOf(leaked)).filter(SourceRules::isFakeTypeName))
    }
}
