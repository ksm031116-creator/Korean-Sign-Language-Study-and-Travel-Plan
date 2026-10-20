package app.signkorean.testing

import app.signkorean.domain.common.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

/**
 * 테스트용 시계. 시각은 [advance] 로만 앞으로 간다.
 * 다른 스레드에서 읽어도 최신 값을 보도록 원자적 참조에 둔다.
 */
class FixedClock(start: Instant) : Clock {

    private val instant = AtomicReference(start)

    override fun now(): Instant = instant.get()

    fun advance(by: Duration) {
        require(!by.isNegative) { "시간을 거꾸로 돌릴 수 없다: $by" }
        instant.updateAndGet { it.plus(by) }
    }
}
