package app.signkorean.domain.learning

import app.signkorean.domain.content.CardId
import app.signkorean.testing.ContentFixtures.basic
import app.signkorean.testing.FakeLearningStateStore
import app.signkorean.testing.FakeSessionStore
import app.signkorean.testing.FakeTransactionRunner
import app.signkorean.testing.FixedClock
import app.signkorean.testing.SequentialIdGenerator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** 판정이 저장 성공 뒤에만 확정된다 (FR07, NFR03, 설계서 §6.2). */
class LearningUseCasesTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val states = FakeLearningStateStore()
    private val sessions = FakeSessionStore()
    private val tx = FakeTransactionRunner(states, sessions)
    private val cards = (1..4).map { basic("b0$it", "단어$it") }

    private val start = StartSession(sessions, states, tx, SequentialIdGenerator("s"))
    private val judge = JudgeCard(sessions, states, tx, clock)
    private val undo = UndoJudgement(sessions, states, tx)

    private suspend fun started(): LearningSession =
        (start(SessionSource.Basics, Mode.All, cards, replaceUnfinished = false) as StartOutcome.Started).session

    @Test
    fun `판정은 카드 상태와 세션을 한 트랜잭션으로 저장한다`() = runTest {
        val s = started()
        val out = judge(s.decisionSeq, Verdict.Know) as JudgeOutcome.Judged
        assertEquals(CardStatus.Known, states.all()[CardId("b01")])
        assertEquals(out.session, sessions.current())
        assertEquals(CardId("b02"), out.session.current)
    }

    @Test
    fun `L-09 같은 순번 판정 두 번은 한 번만 저장한다 (중복 탭)`() = runTest {
        val s = started()
        val results = listOf(async { judge(s.decisionSeq, Verdict.Know) }, async { judge(s.decisionSeq, Verdict.Know) }).awaitAll()
        assertEquals(1, results.count { it is JudgeOutcome.Judged })
        assertEquals(1, results.count { it == JudgeOutcome.StaleInput })
        assertEquals(1, sessions.current()!!.decisions.size)
        assertEquals(1, states.saveCount)
    }

    @Test
    fun `L-18 오래된 순번은 StaleInput, 저장 0`() = runTest {
        started()
        val before = states.saveCount
        assertEquals(JudgeOutcome.StaleInput, judge(expectedSeq = 7, verdict = Verdict.Know))
        assertEquals(before, states.saveCount)
    }

    @Test
    fun `L-10 저장 실패면 세션·카드 상태가 그대로이고 같은 카드에 머문다`() = runTest {
        val s = started()
        sessions.failNextSave = true
        assertEquals(JudgeOutcome.SaveFailed, judge(s.decisionSeq, Verdict.Know))
        assertNull(states.all()[CardId("b01")], "카드 상태가 먼저 저장됐어도 트랜잭션이 되돌린다")
        assertEquals(s, sessions.current())
        assertEquals(CardId("b01"), sessions.current()!!.current)
    }

    @Test
    fun `L-12 카드 상태 저장 직후 죽어도 다시 시작하면 마지막 확정 판정까지만 남는다`() = runTest {
        val s = started()
        val first = judge(s.decisionSeq, Verdict.Know) as JudgeOutcome.Judged
        sessions.failNextSave = true // 두 번째 판정: 카드 상태는 쓰고 세션 쓰기에서 "죽음"
        assertEquals(JudgeOutcome.SaveFailed, judge(first.session.decisionSeq, Verdict.DontKnow))
        val resumed = ResumeSession(sessions)()!!
        assertEquals(first.session, resumed)
        assertEquals(mapOf(CardId("b01") to CardStatus.Known), states.all())
    }

    @Test
    fun `세션이 없거나 끝났으면 판정하지 않는다`() = runTest {
        assertEquals(JudgeOutcome.NoSession, judge(0, Verdict.Know))
        var s = (start(SessionSource.Basics, Mode.All, cards.take(1), replaceUnfinished = false) as StartOutcome.Started).session
        s = (judge(s.decisionSeq, Verdict.Know) as JudgeOutcome.Judged).session
        assertTrue(s.finished)
        assertEquals(JudgeOutcome.NoSession, judge(s.decisionSeq, Verdict.Know))
    }

    @Test
    fun `Undo 는 세션과 카드 상태를 함께 되돌린다`() = runTest {
        val s = started()
        val j = judge(s.decisionSeq, Verdict.DontKnow) as JudgeOutcome.Judged
        val u = undo(j.session.decisionSeq) as UndoOutcome.Undone
        assertEquals(CardId("b01"), u.session.current)
        assertEquals(emptyMap<CardId, CardStatus>(), states.all())
        assertEquals(u.session, sessions.current())
    }

    @Test
    fun `L-15 Undo 저장 중 실패하면 Undo 전 상태로 일관된다`() = runTest {
        val s = started()
        val j = judge(s.decisionSeq, Verdict.DontKnow) as JudgeOutcome.Judged
        sessions.failNextSave = true
        assertEquals(UndoOutcome.SaveFailed, undo(j.session.decisionSeq))
        assertEquals(j.session, sessions.current())
        assertEquals(CardStatus.Learning, states.all()[CardId("b01")])
    }

    @Test
    fun `Undo 도 순번을 비교한다 (두 번 눌러도 한 번)`() = runTest {
        val s = started()
        val j = judge(s.decisionSeq, Verdict.Know) as JudgeOutcome.Judged
        assertTrue(undo(j.session.decisionSeq) is UndoOutcome.Undone)
        assertEquals(UndoOutcome.StaleInput, undo(j.session.decisionSeq))
    }

    @Test
    fun `되돌릴 판정이 없으면 Nothing`() = runTest {
        val s = started()
        assertEquals(UndoOutcome.NothingToUndo, undo(s.decisionSeq))
    }

    @Test
    fun `L-16 끝나지 않은 세션이 있으면 확인을 요구하고, 취소하면 그대로다`() = runTest {
        val s = started()
        val again = start(SessionSource.Basics, Mode.All, cards.reversed(), replaceUnfinished = false)
        assertEquals(StartOutcome.NeedsConfirmation(s), again)
        assertEquals(s, sessions.current())
    }

    @Test
    fun `L-16 확인하면 새 세션, 카드 상태는 유지`() = runTest {
        val s = started()
        judge(s.decisionSeq, Verdict.Know)
        val replaced = start(SessionSource.Basics, Mode.All, cards.reversed(), replaceUnfinished = true) as StartOutcome.Started
        assertEquals(CardId("b04"), replaced.session.current)
        assertEquals(CardStatus.Known, states.all()[CardId("b01")])
        assertEquals(replaced.session, sessions.current())
    }

    @Test
    fun `끝난 세션은 확인 없이 바꾼다`() = runTest {
        var s = (start(SessionSource.Basics, Mode.All, cards.take(1), replaceUnfinished = false) as StartOutcome.Started).session
        s = (judge(s.decisionSeq, Verdict.Know) as JudgeOutcome.Judged).session
        assertTrue(start(SessionSource.Basics, Mode.All, cards, replaceUnfinished = false) is StartOutcome.Started)
    }

    @Test
    fun `Review 는 저장된 학습 상태로 후보를 고른다`() = runTest {
        states.save(CardState(CardId("b03"), CardStatus.Learning, clock.now()))
        val r = start(SessionSource.Basics, Mode.Review, cards, replaceUnfinished = false) as StartOutcome.Started
        assertEquals(listOf(CardId("b03")), r.session.queue.map { it.card })
    }

    @Test
    fun `카드가 없으면 세션을 저장하지 않는다`() = runTest {
        assertEquals(StartOutcome.NoCards, start(SessionSource.Basics, Mode.All, emptyList(), replaceUnfinished = false))
        assertNull(sessions.current())
    }

    @Test
    fun `ResumeSession 은 끝나지 않은 세션만 돌려준다`() = runTest {
        assertNull(ResumeSession(sessions)())
        var s = (start(SessionSource.Basics, Mode.All, cards.take(1), replaceUnfinished = false) as StartOutcome.Started).session
        assertEquals(s, ResumeSession(sessions)())
        s = (judge(s.decisionSeq, Verdict.Know) as JudgeOutcome.Judged).session
        assertNull(ResumeSession(sessions)())
    }
}
