package app.signkorean.domain.common

import java.time.Instant

/**
 * 현재 시각 포트. 도메인과 유스케이스는 시스템 시계를 직접 읽지 않고 이 포트를 주입받는다
 * (설계서 원칙 5). 테스트는 고정 시계를 넣어 결과를 결정적으로 만든다.
 */
fun interface Clock {
    fun now(): Instant
}
