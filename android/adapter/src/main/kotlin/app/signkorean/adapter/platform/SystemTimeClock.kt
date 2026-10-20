package app.signkorean.adapter.platform

import app.signkorean.domain.common.Clock
import java.time.Instant

/**
 * 시스템 시계. 이름을 `SystemClock` 으로 하지 않는 이유: `android.os.SystemClock` 과 헷갈리지 않게 (설계서 §5.5).
 */
object SystemTimeClock : Clock {
    override fun now(): Instant = Instant.now()
}
