package app.signkorean.domain.learning

import app.signkorean.domain.content.CardId
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeLearningStateStore
import app.signkorean.testing.FakeSessionStore
import app.signkorean.testing.FakeTransactionRunner
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 학습 기록 초기화 (FR16). E-10 의 확인 경로. 취소 경로(SC20 확인 모달)는 설정 화면(Phase 20)에서 검사한다. */
class ResetLearningTest {

    private val states = FakeLearningStateStore(mapOf(CardId("b01") to CardStatus.Known, CardId("b02") to CardStatus.Learning))
    private val session = (SessionFactory.start(
        SessionId("s1"), SessionSource.Basics, Mode.All,
        listOf(ContentFixtures.basic("b01"), ContentFixtures.basic("b02")), emptyMap(),
    ) as StartResult.Started).session
    private val sessions = FakeSessionStore(session)
    private val reset = ResetLearning(states, sessions, FakeTransactionRunner(states, sessions))

    @Test
    fun `E-10 확인하면 학습 상태와 세션을 지운다`() = runTest {
        assertTrue(reset())
        assertTrue(states.all().isEmpty())
        assertNull(sessions.current())
    }

    @Test
    fun `E-10 북마크와 구매 권한 저장소는 초기화가 받지도 않는다`() {
        // 받지 않은 저장소는 지울 수 없다. 누가 생성자에 BookmarkStore·EntitlementCache 를 더하면 여기서 막힌다.
        val params = ResetLearning::class.java.constructors.single().parameterTypes.map { it.simpleName }.toSet()
        assertEquals(setOf("LearningStateStore", "SessionStore", "TransactionRunner"), params)
    }

    @Test
    fun `저장 실패면 false, 둘 다 원래대로`() = runTest {
        val failing = object : SessionStore by sessions {
            override suspend fun clear() = throw IllegalStateException("disk")
        }
        val r = ResetLearning(states, failing, FakeTransactionRunner(states, sessions))
        assertFalse(r())
        assertEquals(2, states.all().size, "세션을 못 지우면 학습 상태도 되돌린다")
        assertNotNull(sessions.current())
    }
}
