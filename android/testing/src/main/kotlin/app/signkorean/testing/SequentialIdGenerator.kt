package app.signkorean.testing

import app.signkorean.domain.common.IdGenerator

/** 테스트용 ID 생성기. `id-1`, `id-2` … 처럼 예측 가능한 ID를 만든다. */
class SequentialIdGenerator(private val prefix: String = "id") : IdGenerator {

    private var count = 0

    override fun next(): String = "$prefix-${++count}"
}
