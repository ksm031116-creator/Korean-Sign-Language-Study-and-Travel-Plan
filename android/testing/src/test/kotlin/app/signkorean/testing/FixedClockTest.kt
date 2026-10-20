package app.signkorean.testing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant

class FixedClockTest {

    private val start = Instant.parse("2026-10-07T00:00:00Z")

    @Test
    fun `시각을 바꾸기 전까지 같은 값을 돌려준다`() {
        val clock = FixedClock(start)
        assertEquals(start, clock.now())
        assertEquals(start, clock.now())
    }

    @Test
    fun `advance 만큼 시각이 앞으로 간다`() {
        val clock = FixedClock(start)
        clock.advance(Duration.ofMinutes(90))
        assertEquals(Instant.parse("2026-10-07T01:30:00Z"), clock.now())
    }

    @Test
    fun `음수 advance 는 거부한다 - 시간이 거꾸로 가면 순서 검증 테스트가 거짓 통과한다`() {
        val clock = FixedClock(start)
        assertThrows<IllegalArgumentException> { clock.advance(Duration.ofSeconds(-1)) }
        assertEquals(start, clock.now())
    }
}
