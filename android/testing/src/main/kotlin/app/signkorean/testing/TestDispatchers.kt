package app.signkorean.testing

import app.signkorean.domain.common.AppDispatchers
import kotlinx.coroutines.test.TestDispatcher

/** 모든 디스패처를 하나의 테스트 디스패처로 바꾼다. `runTest` 의 가상 시간으로 결정적으로 실행된다. */
fun testDispatchers(dispatcher: TestDispatcher): AppDispatchers =
    AppDispatchers(io = dispatcher, default = dispatcher, main = dispatcher)
