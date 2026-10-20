package app.signkorean.domain.common

import kotlinx.coroutines.CoroutineDispatcher

/**
 * 코루틴 디스패처 묶음. `Dispatchers.IO` 등을 코드 안에서 직접 부르지 않고 주입받는다
 * (설계서 원칙 5). 테스트에서는 모두 하나의 테스트 디스패처로 바꿔 가상 시간으로 실행한다.
 */
data class AppDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher,
)
