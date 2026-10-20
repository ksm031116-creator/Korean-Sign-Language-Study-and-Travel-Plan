package app.signkorean.adapter.platform

import app.signkorean.domain.common.IdGenerator
import java.util.UUID

/** 무작위 UUID(v4) 로 ID를 만든다. 기기 안에서만 쓰는 ID라 충돌 확률은 무시할 수 있다. */
class UuidIdGenerator : IdGenerator {
    override fun next(): String = UUID.randomUUID().toString()
}
