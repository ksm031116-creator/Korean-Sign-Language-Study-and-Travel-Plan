package app.signkorean.di

import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.dsl.koinApplication

/** 조립 결과를 살펴보는 테스트 도우미. 소스 검색이 아니라 실제로 등록된 정의를 본다. */
object KoinGraph {

    /** 모듈들을 올렸을 때 등록되는 정의의 주 타입 이름. */
    @OptIn(KoinInternalApi::class)
    fun boundTypeNames(modules: List<Module>): List<String> {
        val app = koinApplication { modules(modules) }
        return try {
            app.koin.instanceRegistry.instances.values
                .map { it.beanDefinition.primaryType.simpleName ?: it.beanDefinition.primaryType.toString() }
                .distinct()
        } finally {
            app.close()
        }
    }
}
