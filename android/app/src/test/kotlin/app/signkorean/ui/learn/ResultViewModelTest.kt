package app.signkorean.ui.learn

import app.signkorean.domain.content.CardId
import app.signkorean.domain.learning.JudgeCard
import app.signkorean.domain.learning.JudgeOutcome
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.StartOutcome
import app.signkorean.domain.learning.StartSession
import app.signkorean.domain.learning.Verdict
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeLearningStateStore
import app.signkorean.testing.FakeSessionStore
import app.signkorean.testing.FakeTransactionRunner
import app.signkorean.testing.FixedClock
import app.signkorean.testing.InMemoryContentRepository
import app.signkorean.testing.SequentialIdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** SC06 결과 (FR08, U-17). */
@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {

    private val cards = (1..3).map { ContentFixtures.basic("b0$it", "단어$it") }
    private val pack = ContentFixtures.pack(cards = cards)
    private val states = FakeLearningStateStore()
    private val sessions = FakeSessionStore()
    private val tx = FakeTransactionRunner(states, sessions)
    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val start = StartSession(sessions, states, tx, SequentialIdGenerator("s"))
    private val judge = JudgeCard(sessions, states, tx, clock)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** 모름 → 앎 → 앎 → (재출제된 첫 카드) 모름 순서로 판정해 세션을 끝낸다. */
    private suspend fun finishSession() {
        var s = (start(SessionSource.Basics, Mode.All, cards, false) as StartOutcome.Started).session
        for (v in listOf(Verdict.DontKnow, Verdict.Know, Verdict.Know, Verdict.DontKnow, Verdict.Know)) {
            if (s.finished) break
            s = (judge(s.id, s.decisionSeq, v) as JudgeOutcome.Judged).session
        }
    }

    private fun viewModel() = ResultViewModel(InMemoryContentRepository(pack), sessions, start)

    @Test
    fun `U-17 앎·모름 판정 수, 고유 카드 수, 남은 복습 수를 각각 보인다`() = runTest {
        finishSession()
        val s = viewModel().state.value.summary!!
        assertEquals(3, s.knownDecisions)
        assertEquals(2, s.learningDecisions)
        assertEquals(3, s.uniqueCards)
        assertEquals(0, s.remainingReview)
    }

    @Test
    fun `남은 복습이 없으면 Review again 을 막는다`() = runTest {
        finishSession()
        assertFalse(viewModel().state.value.canReview)
    }

    @Test
    fun `Practice all 은 같은 출처로 새 세션을 시작하고 Learn 으로 간다`() = runTest {
        finishSession()
        val vm = viewModel()
        val before = sessions.current()!!.id
        vm.onAction(ResultAction.PracticeAll)
        assertEquals(ResultEffect.OpenLearn, vm.effects.tryReceive().getOrNull())
        assertTrue(sessions.current()!!.id != before)
        assertEquals(CardId("b01"), sessions.current()!!.current)
    }

    @Test
    fun `Review again 은 learning 카드만으로 시작한다`() = runTest {
        var s = (start(SessionSource.Basics, Mode.All, cards, false) as StartOutcome.Started).session
        for (v in listOf(Verdict.Know, Verdict.DontKnow, Verdict.Know, Verdict.DontKnow)) {
            if (s.finished) break
            s = (judge(s.id, s.decisionSeq, v) as JudgeOutcome.Judged).session
        }
        val vm = viewModel()
        assertTrue(vm.state.value.canReview)
        vm.onAction(ResultAction.ReviewAgain)
        assertEquals(ResultEffect.OpenLearn, vm.effects.tryReceive().getOrNull())
        assertEquals(listOf(CardId("b02")), sessions.current()!!.queue.map { it.card })
    }

    @Test
    fun `출처에 카드가 없으면 안내만 하고 머문다`() = runTest {
        finishSession()
        val vm = ResultViewModel(InMemoryContentRepository(ContentFixtures.pack()), sessions, start)
        vm.onAction(ResultAction.PracticeAll)
        assertNotNull(vm.state.value.message)
        assertEquals(null, vm.effects.tryReceive().getOrNull())
    }

    @Test
    fun `Done 은 닫기 효과`() = runTest {
        finishSession()
        val vm = viewModel()
        vm.onAction(ResultAction.Done)
        assertEquals(ResultEffect.Close, vm.effects.tryReceive().getOrNull())
    }
}
