package app.signkorean.testing

import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class TestDispatchersTest {

    @Test
    fun `모든 디스패처를 하나의 테스트 디스패처로 묶는다 - 가상 시간으로 결정적으로 실행`() {
        val dispatcher = StandardTestDispatcher()
        val dispatchers = testDispatchers(dispatcher)
        assertSame(dispatcher, dispatchers.io)
        assertSame(dispatcher, dispatchers.default)
        assertSame(dispatcher, dispatchers.main)
    }
}
