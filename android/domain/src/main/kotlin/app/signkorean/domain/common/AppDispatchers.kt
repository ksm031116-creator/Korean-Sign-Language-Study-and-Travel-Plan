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

/**
 * 앱 프로세스와 수명이 같은 코루틴 범위. 화면이 닫혀도 끝까지 가야 하는 일(결제 진행, 권한 확인)에 쓴다.
 * 화면 범위(viewModelScope)에서 결제를 돌리면 화면을 떠나는 순간 결제 결과를 기다리던 코루틴이 취소된다
 * (Phase 13 리뷰 UPG-02).
 */
class AppScope(val scope: kotlinx.coroutines.CoroutineScope)
