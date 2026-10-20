package app.signkorean.testing

import app.signkorean.domain.common.Clock
import java.time.Duration
import java.time.Instant

/** 테스트용 시계. 시각은 [advance] 로만 앞으로 간다. */
class FixedClock(private var instant: Instant) : Clock {

    override fun now(): Instant = instant

    fun advance(by: Duration) {
        require(!by.isNegative) { "시간을 거꾸로 돌릴 수 없다: $by" }
        instant = instant.plus(by)
    }
}
