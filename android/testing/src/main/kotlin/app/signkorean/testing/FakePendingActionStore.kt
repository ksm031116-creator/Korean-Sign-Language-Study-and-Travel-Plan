package app.signkorean.testing

import app.signkorean.domain.upgrade.PendingAction
import app.signkorean.domain.upgrade.PendingActionStore

/** 메모리 보류 동작 저장소. 프로세스 종료는 [peek] 값으로 새 인스턴스를 만들어 흉내 낸다. */
class FakePendingActionStore(@Volatile private var action: PendingAction? = null) : PendingActionStore {
    override suspend fun save(action: PendingAction) {
        this.action = action
    }

    override suspend fun peek(): PendingAction? = action

    override suspend fun clear() {
        action = null
    }
}
