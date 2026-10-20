package app.signkorean.ui.learn

import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.Face
import app.signkorean.domain.learning.JudgeCard
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.StartOutcome
import app.signkorean.domain.learning.StartSession
import app.signkorean.domain.learning.UndoJudgement
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LearnViewModelTest {

    private val cards = listOf(
        ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market")),
        ContentFixtures.basic("b01", "화장실"),
        ContentFixtures.basic("b02", "출구"),
    )
    private val pack = ContentFixtures.pack(cards = cards, places = listOf(ContentFixtures.place("gwangjang")))
    private val states = FakeLearningStateStore()
    private val sessions = FakeSessionStore()
    private val tx = FakeTransactionRunner(states, sessions)
    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun viewModel(): LearnViewModel {
        val started = StartSession(sessions, states, tx, SequentialIdGenerator())(SessionSource.Basics, Mode.All, cards, false)
        assertTrue(started is StartOutcome.Started)
        return LearnViewModel(InMemoryContentRepository(pack), sessions, JudgeCard(sessions, states, tx, clock), UndoJudgement(sessions, states, tx))
    }

    @Test
    fun `첫 카드를 앞면으로 보이고, 장소 있는 카드는 아래 방향을 연다`() = runTest {
        val s = viewModel().state.value
        assertEquals(CardId("t-market-1"), s.card?.id)
        assertEquals(Face.Front, s.face)
        assertTrue(s.hasPlaces)
        assertEquals(0, s.decisions)
        assertFalse(s.canUndo)
    }

    @Test
    fun `알아요 판정 뒤 다음 카드, Basics 는 아래 방향이 없다`() = runTest {
        val vm = viewModel()
        vm.onAction(LearnAction.Judge(Verdict.Know))
        val s = vm.state.value
        assertEquals(CardId("b01"), s.card?.id)
        assertFalse(s.hasPlaces)
        assertEquals(1, s.decisions)
        assertTrue(s.canUndo)
        assertEquals(CardStatus.Known, states.all()[CardId("t-market-1")])
    }

    @Test
    fun `뒤집기는 판정이 아니고 다음 카드는 앞면`() = runTest {
        val vm = viewModel()
        vm.onAction(LearnAction.Flip)
        assertEquals(Face.Back, vm.state.value.face)
        assertEquals("뒤집기는 저장하지 않는다(시작 저장 1회뿐)", 1, sessions.saveCount)
        vm.onAction(LearnAction.Judge(Verdict.DontKnow))
        assertEquals(Face.Front, vm.state.value.face)
    }

    @Test
    fun `저장 실패면 같은 카드에 머물고 오류를 보인다, 다시 시도하면 판정된다`() = runTest {
        val vm = viewModel()
        sessions.failNextSave = true
        vm.onAction(LearnAction.Judge(Verdict.Know))
        assertEquals(CardId("t-market-1"), vm.state.value.card?.id)
        assertNotNull(vm.state.value.error)
        vm.onAction(LearnAction.Retry)
        assertNull(vm.state.value.error)
        assertEquals(CardId("b01"), vm.state.value.card?.id)
    }

    @Test
    fun `Undo 는 직전 카드로 돌아간다`() = runTest {
        val vm = viewModel()
        vm.onAction(LearnAction.Judge(Verdict.Know))
        vm.onAction(LearnAction.Undo)
        assertEquals(CardId("t-market-1"), vm.state.value.card?.id)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `장소 목록은 판정이 아니고 이동 효과만 낸다`() = runTest {
        val vm = viewModel()
        vm.onAction(LearnAction.OpenPlaces)
        assertEquals(LearnEffect.OpenPlaces(CardId("t-market-1")), vm.effects.tryReceive().getOrNull())
        assertEquals(0, vm.state.value.decisions)
    }

    @Test
    fun `장소 없는 카드에서 장소 목록 요청은 무시한다 (FR31)`() = runTest {
        val vm = viewModel()
        vm.onAction(LearnAction.Judge(Verdict.Know))
        vm.onAction(LearnAction.OpenPlaces)
        assertNull(vm.effects.tryReceive().getOrNull())
    }

    @Test
    fun `마지막 판정 뒤 끝남 효과`() = runTest {
        val vm = viewModel()
        repeat(3) { vm.onAction(LearnAction.Judge(Verdict.Know)) }
        assertTrue(vm.state.value.finished)
        assertEquals(LearnEffect.Finished, vm.effects.tryReceive().getOrNull())
    }

    @Test
    fun `세션이 없으면 빈 상태`() = runTest {
        val vm = LearnViewModel(InMemoryContentRepository(pack), FakeSessionStore(), JudgeCard(sessions, states, tx, clock), UndoJudgement(sessions, states, tx))
        assertNull(vm.state.value.card)
        assertFalse(vm.state.value.loading)
        assertTrue(vm.state.value.noSession)
    }

    @Test
    fun `다른 곳에서 세션이 바뀌어 지난 입력이 되면 오류가 아니라 새 세션을 보인다 (리뷰 HIGH-3)`() = runTest {
        val vm = viewModel()
        // 다른 화면이 같은 세션에서 판정을 하나 진행했다.
        val s = sessions.current()!!
        JudgeCard(sessions, states, tx, clock)(s.id, s.decisionSeq, Verdict.Know)
        vm.onAction(LearnAction.Judge(Verdict.Know))
        assertNull(vm.state.value.error)
        assertEquals(CardId("b01"), vm.state.value.card?.id)
        assertEquals(1, vm.state.value.decisions)
    }

    @Test
    fun `콘텐츠를 읽지 못하면 멈추지 않고 다시 시도로 다시 읽는다 (리뷰 HIGH-4)`() = runTest {
        StartSession(sessions, states, tx, SequentialIdGenerator())(SessionSource.Basics, Mode.All, cards, false)
        var fail = true
        val flaky = object : app.signkorean.domain.content.ContentRepository {
            override suspend fun load() = if (fail) throw java.io.IOException("asset") else pack
        }
        val vm = LearnViewModel(flaky, sessions, JudgeCard(sessions, states, tx, clock), UndoJudgement(sessions, states, tx))
        assertTrue(vm.state.value.loadFailed)
        fail = false
        vm.onAction(LearnAction.Retry)
        assertEquals(CardId("t-market-1"), vm.state.value.card?.id)
    }

    @Test
    fun `끝난 세션으로 들어오면 결과로 보낸다`() = runTest {
        val one = StartSession(sessions, states, tx, SequentialIdGenerator())(SessionSource.Basics, Mode.All, cards.take(1), false) as StartOutcome.Started
        JudgeCard(sessions, states, tx, clock)(one.session.id, 0, Verdict.Know)
        val vm = LearnViewModel(InMemoryContentRepository(pack), sessions, JudgeCard(sessions, states, tx, clock), UndoJudgement(sessions, states, tx))
        assertEquals(LearnEffect.Finished, vm.effects.tryReceive().getOrNull())
    }

    @Test
    fun `콘텐츠에서 사라진 카드면 빈 화면 대신 돌아갈 길을 보인다`() = runTest {
        viewModel()
        val withoutFirst = ContentFixtures.pack(cards = cards.drop(1))
        val vm = LearnViewModel(InMemoryContentRepository(withoutFirst), sessions, JudgeCard(sessions, states, tx, clock), UndoJudgement(sessions, states, tx))
        assertTrue(vm.state.value.noSession)
    }

    @Test
    fun `뒤집어도 저장 오류 안내는 남는다`() = runTest {
        val vm = viewModel()
        sessions.failNextSave = true
        vm.onAction(LearnAction.Judge(Verdict.Know))
        vm.onAction(LearnAction.Flip)
        assertNotNull(vm.state.value.error)
        assertEquals(Face.Back, vm.state.value.face)
    }

    @Test
    fun `같은 카드가 다시 나와도 순번이 바뀌어 화면이 카드를 제자리로 돌린다`() = runTest {
        val vm = viewModel()
        val before = vm.state.value.seq
        vm.onAction(LearnAction.Judge(Verdict.Know))
        vm.onAction(LearnAction.Undo)
        assertEquals(CardId("t-market-1"), vm.state.value.card?.id)
        assertTrue(vm.state.value.seq != before)
    }
}
