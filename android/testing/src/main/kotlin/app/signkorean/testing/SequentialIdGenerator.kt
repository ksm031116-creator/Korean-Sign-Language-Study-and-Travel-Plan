package app.signkorean.testing

import app.signkorean.domain.common.IdGenerator
import java.util.concurrent.atomic.AtomicInteger

/**
 * 테스트용 ID 생성기. `id-1`, `id-2` … 처럼 예측 가능한 ID를 만든다.
 * 코드가 실제 디스패처에서 동시에 불러도 ID가 겹치지 않도록 원자적으로 센다.
 */
class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {

    private val count = AtomicInteger()

    override fun next(): String = "$prefix-${count.incrementAndGet()}"
}
