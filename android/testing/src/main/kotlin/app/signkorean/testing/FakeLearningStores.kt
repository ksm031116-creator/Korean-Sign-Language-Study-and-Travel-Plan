package app.signkorean.testing

import app.signkorean.domain.content.CardId
import app.signkorean.domain.learning.CardState
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.CardStatusRestore
import app.signkorean.domain.learning.LearningSession
import app.signkorean.domain.learning.LearningStateStore
import app.signkorean.domain.learning.SessionStore
import app.signkorean.domain.learning.TransactionRunner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** 트랜잭션에 참여하는 가짜 저장소. 시작 시 상태를 찍어 두고 실패하면 되돌린다. */
interface FakeTransactional {
    fun snapshot(): Any
    fun rollback(snapshot: Any)
}

/** 저장 실패를 주입할 수 있다(L-10). [failNextSave] 가 켜지면 다음 저장 한 번이 예외를 던진다. */
class FakeLearningStateStore(
    initial: Map<CardId, CardStatus> = emptyMap(),
    /** 읽기·쓰기마다 다른 코루틴에 차례를 넘긴다. 동시 호출 경합을 테스트에서 드러내기 위해서다. */
    private val yieldOnAccess: Boolean = false,
) : LearningStateStore, FakeTransactional {
    private val states = initial.toMutableMap()

    private suspend fun pause() {
        if (yieldOnAccess) yield()
    }
    var failNextSave = false
    var saveCount = 0
        private set

    override suspend fun all(): Map<CardId, CardStatus> {
        pause()
        return states.toMap()
    }

    override suspend fun save(state: CardState) {
        pause()
        if (failNextSave) {
            failNextSave = false
            throw FakeStorageException("card_state")
        }
        saveCount++
        // Room 처럼 Unseen 은 기록을 지운다(행이 없으면 unseen).
        if (state.status == CardStatus.Unseen) states.remove(state.card) else states[state.card] = state.status
    }

    override suspend fun restore(restore: CardStatusRestore) {
        if (restore.status == CardStatus.Unseen) states.remove(restore.card) else states[restore.card] = restore.status
    }

    override suspend fun clear() = states.clear()

    override fun snapshot(): Any = states.toMap()

    @Suppress("UNCHECKED_CAST")
    override fun rollback(snapshot: Any) {
        states.clear()
        states.putAll(snapshot as Map<CardId, CardStatus>)
    }
}

class FakeSessionStore(
    initial: LearningSession? = null,
    private val yieldOnAccess: Boolean = false,
) : SessionStore, FakeTransactional {
    private var session: LearningSession? = initial

    private suspend fun pause() {
        if (yieldOnAccess) yield()
    }
    var failNextSave = false
    var saveCount = 0
        private set

    override suspend fun current(): LearningSession? {
        pause()
        return session
    }

    override suspend fun save(session: LearningSession) {
        pause()
        if (failNextSave) {
            failNextSave = false
            throw FakeStorageException("session")
        }
        saveCount++
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }

    override fun snapshot(): Any = Box(session)

    override fun rollback(snapshot: Any) {
        session = (snapshot as Box).value
    }

    private class Box(val value: LearningSession?)
}

/**
 * Room 트랜잭션과 같은 의미: 한 번에 하나씩 실행하고, 예외가 나면 참여한 저장소를 모두 시작 상태로 되돌린다.
 * Room 의 withTransaction 처럼 안쪽에서 다시 run 해도(중첩) 같은 트랜잭션으로 이어진다.
 */
class FakeTransactionRunner(private vararg val stores: FakeTransactional) : TransactionRunner {
    init {
        require(stores.isNotEmpty()) { "되돌릴 저장소가 없으면 트랜잭션 의미가 없다" }
    }

    private val mutex = Mutex()
    var runs = 0
        private set

    private object InTransaction : AbstractCoroutineContextElement(Key) {
        object Key : CoroutineContext.Key<InTransaction>
    }

    override suspend fun <T> run(block: suspend () -> T): T {
        if (coroutineContext[InTransaction.Key] != null) return block()
        return mutex.withLock {
            runs++
            val snapshots = stores.map { it.snapshot() }
            try {
                withContext(InTransaction) { block() }
            } catch (e: Throwable) {
                stores.zip(snapshots).forEach { (store, snap) -> store.rollback(snap) }
                throw e
            }
        }
    }
}

class FakeStorageException(what: String) : RuntimeException("가짜 저장 실패: $what")
