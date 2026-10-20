package app.signkorean.domain.learning

import app.signkorean.testing.FakeLearningStateStore
import app.signkorean.testing.FakeSessionStore
import app.signkorean.testing.FakeTransactionRunner
import app.signkorean.testing.LearningStoreContract
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** 가짜 저장소도 Room 과 같은 계약을 지킨다. 그래야 가짜로 시험한 유스케이스가 실제로도 맞다. */
class FakeLearningStoreContractTest {
    @Test
    fun `가짜 저장소는 포트 계약을 지킨다`() = runTest {
        val states = FakeLearningStateStore()
        val sessions = FakeSessionStore()
        LearningStoreContract(states, sessions, FakeTransactionRunner(states, sessions)).verifyAll()
    }
}
