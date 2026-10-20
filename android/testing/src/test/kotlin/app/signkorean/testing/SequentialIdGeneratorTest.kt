package app.signkorean.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SequentialIdGeneratorTest {

    @Test
    fun `호출 순서대로 예측 가능한 ID를 만든다`() {
        val ids = SequentialIdGenerator()
        assertEquals(listOf("id-1", "id-2", "id-3"), List(3) { ids.next() })
    }

    @Test
    fun `접두어를 바꿀 수 있다`() {
        val ids = SequentialIdGenerator(prefix = "trip")
        assertEquals("trip-1", ids.next())
    }

    @Test
    fun `인스턴스마다 번호가 따로 시작한다 - 테스트끼리 상태를 공유하지 않는다`() {
        SequentialIdGenerator().next()
        assertEquals("id-1", SequentialIdGenerator().next())
    }
}
