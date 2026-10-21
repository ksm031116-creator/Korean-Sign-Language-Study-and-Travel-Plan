package app.signkorean.testing

import app.signkorean.domain.upgrade.PendingAction
import app.signkorean.domain.upgrade.PendingActionStore
import kotlinx.coroutines.yield

/**
 * 메모리 보류 동작 저장소. 프로세스 종료는 [peek] 값으로 새 인스턴스를 만들어 흉내 낸다.
 * [yieldOnAccess] 면 읽기·쓰기마다 다른 코루틴에 차례를 넘겨 동시 호출 경합을 드러낸다.
 */
class FakePendingActionStore(
    @Volatile private var action: PendingAction? = null,
    private val yieldOnAccess: Boolean = false,
) : PendingActionStore {
    private suspend fun pause() {
        if (yieldOnAccess) yield()
    }

    override suspend fun save(action: PendingAction) {
        pause()
        this.action = action
    }

    override suspend fun peek(): PendingAction? {
        pause()
        return action
    }

    override suspend fun clear() {
        pause()
        action = null
    }
}
