package app.signkorean.adapter.platform

import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SystemTimeClockTest {

    @Test
    fun `현재 시스템 시각을 돌려준다`() {
        val before = Instant.now()
        val now = SystemTimeClock.now()
        val after = Instant.now()
        assertTrue("$now 는 $before 와 $after 사이여야 한다", !now.isBefore(before) && !now.isAfter(after))
    }
}
