package app.signkorean.domain.learning

import app.signkorean.domain.content.CardId
import app.signkorean.testing.ContentFixtures.basic
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** 학습 세션 규칙 (PRD §5.1, FR01~08, 설계서 §6.2). I/O 없는 순수 리듀서. */
class SessionReducerTest {

    private val now = Instant.parse("2026-10-21T09:00:00Z")
    private val source = SessionSource.Basics
    private fun ids(vararg s: String) = s.map(::CardId)
    private fun cards(n: Int) = (1..n).map { basic("b%02d".format(it), "단어$it") }

    private fun start(n: Int, mode: Mode = Mode.All, states: Map<CardId, CardStatus> = emptyMap()): LearningSession =
        (SessionFactory.start(SessionId("s1"), source, mode, cards(n), states) as StartResult.Started).session

    private fun LearningSession.judge(v: Verdict) = SessionReducer.judge(this, v, now)!!.session

    private fun LearningSession.order(): List<String> = queue.map { it.card.value }

    @Test
    fun `All cards 초기 큐는 활성 카드 각 1회, 첫 카드는 앞면`() {
        val s = start(3)
        assertEquals(listOf("b01", "b02", "b03"), s.order())
        assertEquals(CardId("b01"), s.current)
        assertEquals(Face.Front, s.face)
        assertEquals(0, s.decisionSeq)
    }

    @Test
    fun `L-01 오른쪽 판정은 known 으로 저장되고 다시 나오지 않는다`() {
        val t = SessionReducer.judge(start(3), Verdict.Know, now)!!
        assertEquals(CardState(CardId("b01"), CardStatus.Known, now), t.cardState)
        assertEquals(listOf("b02", "b03"), t.session.order())
    }

    @Test
    fun `L-02 왼쪽 판정은 learning 으로 저장되고 다른 카드 3회 판정 뒤 재출제된다`() {
        var s = start(6)
        val t = SessionReducer.judge(s, Verdict.DontKnow, now)!!
        assertEquals(CardStatus.Learning, t.cardState.status)
        s = t.session
        assertEquals(CardId("b02"), s.current)
        s = s.judge(Verdict.Know).judge(Verdict.Know)
        assertEquals(CardId("b04"), s.current, "다른 카드 2회 판정 뒤에는 아직 아니다")
        s = s.judge(Verdict.Know)
        assertEquals(CardId("b01"), s.current, "3회 판정 뒤 재출제")
        assertFalse(s.isTryAgain)
    }

    @Test
    fun `L-03 카드 2장에서 모름 1개면 남은 카드 먼저, 그다음 Try again 과 함께 재출제`() {
        var s = start(2).judge(Verdict.DontKnow)
        assertEquals(CardId("b02"), s.current)
        assertFalse(s.isTryAgain)
        s = s.judge(Verdict.Know)
        assertEquals(CardId("b01"), s.current)
        assertTrue(s.isTryAgain, "사이에 넣을 카드가 부족해 3회를 기다리지 못했다")
    }

    @Test
    fun `L-04 모름 4개 연속이면 각 재출제 간격이 지켜지고 10회에서 끝난다`() {
        var s = start(8)
        val shown = mutableListOf<String>()
        repeat(10) {
            shown += s.current!!.value
            s = s.judge(if (shown.size <= 4) Verdict.DontKnow else Verdict.Know)
        }
        // 각 모름 카드는 다른 카드 정확히 3장 뒤에 다시 나온다. 다시 나온 카드가 아직 안 본 카드보다 먼저다.
        assertEquals(listOf("b01", "b02", "b03", "b04", "b01", "b02", "b03", "b04", "b05", "b06"), shown)
        assertTrue(s.finished)
        assertNull(s.current)
    }

    @Test
    fun `L-05 10회 판정으로 끝나고 판정 수와 고유 카드 수는 다를 수 있다`() {
        var s = start(3)
        repeat(10) { s = s.judge(Verdict.DontKnow) }
        assertTrue(s.finished)
        val summary = SessionSummary.of(s, s.decisions.associate { it.card to CardStatus.Learning })
        assertEquals(10, summary.learningDecisions)
        assertEquals(0, summary.knownDecisions)
        assertEquals(3, summary.uniqueCards)
        assertEquals(3, summary.remainingReview)
    }

    @Test
    fun `대기열이 비면 10회 전에도 끝난다`() {
        var s = start(2)
        s = s.judge(Verdict.Know).judge(Verdict.Know)
        assertTrue(s.finished)
        assertNull(SessionReducer.judge(s, Verdict.Know, now), "끝난 세션은 더 판정하지 않는다")
    }

    @Test
    fun `L-06 Review 모드는 learning 카드만, 후보가 없으면 완료 상태`() {
        val states = mapOf(CardId("b01") to CardStatus.Known, CardId("b02") to CardStatus.Learning, CardId("b03") to CardStatus.Learning)
        assertEquals(listOf("b02", "b03"), start(3, Mode.Review, states).order())
        val none = SessionFactory.start(SessionId("s"), source, Mode.Review, cards(3), mapOf(CardId("b01") to CardStatus.Known))
        assertEquals(StartResult.NothingToReview, none)
    }

    @Test
    fun `L-14 카드가 0장이면 세션을 만들지 않는다`() {
        assertEquals(StartResult.NoCards, SessionFactory.start(SessionId("s"), source, Mode.All, emptyList(), emptyMap()))
    }

    @Test
    fun `L-13 비활성 카드는 새 세션에서 빠진다`() {
        val inactive = basic("b09", "비활성").copy(romanization = "")
        val r = SessionFactory.start(SessionId("s"), source, Mode.All, cards(2) + inactive, emptyMap()) as StartResult.Started
        assertEquals(listOf("b01", "b02"), r.session.order())
    }

    @Test
    fun `같은 카드가 두 번 주어져도 큐에는 한 번`() {
        val r = SessionFactory.start(SessionId("s"), source, Mode.All, cards(2) + cards(2), emptyMap()) as StartResult.Started
        assertEquals(listOf("b01", "b02"), r.session.order())
    }

    @Test
    fun `L-07 Undo 는 직전 1건만, 두 번째 Undo 는 불가`() {
        val s = start(3).judge(Verdict.Know).judge(Verdict.DontKnow)
        val undone = SessionReducer.undo(s)!!
        assertEquals(CardId("b02"), undone.session.current)
        assertEquals(1, undone.session.decisions.size)
        assertNull(SessionReducer.undo(undone.session))
    }

    @Test
    fun `Undo 는 카드 상태·큐·집계를 함께 되돌리고 이전 상태를 돌려준다`() {
        val before = start(3)
        val judged = SessionReducer.judge(before, Verdict.DontKnow, now)!!
        val undone = SessionReducer.undo(judged.session)!!
        assertEquals(before.queue, undone.session.queue)
        assertEquals(before.decisions, undone.session.decisions)
        assertEquals(CardStatus.Unseen, undone.restoredState.status, "처음 본 카드는 unseen 으로 돌아간다")
        assertEquals(CardId("b01"), undone.restoredState.card)
    }

    @Test
    fun `Undo 는 이전에 known 이던 카드를 known 으로 되돌린다`() {
        val s = start(2, states = mapOf(CardId("b01") to CardStatus.Known))
        val undone = SessionReducer.undo(SessionReducer.judge(s, Verdict.DontKnow, now)!!.session)!!
        assertEquals(CardStatus.Known, undone.restoredState.status)
    }

    @Test
    fun `L-08 판정 Undo 다른 판정 뒤에는 새 판정만 취소할 수 있다`() {
        val s = SessionReducer.undo(start(3).judge(Verdict.Know))!!.session.judge(Verdict.DontKnow)
        val undone = SessionReducer.undo(s)!!.session
        assertEquals(CardId("b01"), undone.current)
        assertTrue(undone.decisions.isEmpty())
    }

    @Test
    fun `끝난 세션도 마지막 판정은 Undo 할 수 있다`() {
        val s = start(1).judge(Verdict.Know)
        assertTrue(s.finished)
        val undone = SessionReducer.undo(s)!!.session
        assertFalse(undone.finished)
        assertEquals(CardId("b01"), undone.current)
    }

    @Test
    fun `판정과 Undo 마다 decisionSeq 가 오른다 (중복 입력 판별)`() {
        val s = start(3).judge(Verdict.Know)
        assertEquals(1, s.decisionSeq)
        assertEquals(2, SessionReducer.undo(s)!!.session.decisionSeq)
    }

    @Test
    fun `L-11 뒤집기는 판정이 아니다`() {
        val s = start(3)
        val flipped = SessionReducer.flip(s)
        assertEquals(Face.Back, flipped.face)
        assertEquals(s.copy(face = Face.Back), flipped)
        assertEquals(Face.Front, SessionReducer.flip(flipped).face)
    }

    @Test
    fun `L-17 뒷면에서 판정해도 다음 카드는 앞면`() {
        val s = SessionReducer.flip(start(3)).judge(Verdict.Know)
        assertEquals(Face.Front, s.face)
    }
}
