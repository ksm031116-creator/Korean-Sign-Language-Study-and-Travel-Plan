package app.signkorean.testing

import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.CardId
import app.signkorean.domain.learning.CardState
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.CardStatusRestore
import app.signkorean.domain.learning.LearningStateStore
import app.signkorean.domain.learning.SessionFactory
import app.signkorean.domain.learning.SessionId
import app.signkorean.domain.learning.SessionReducer
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.SessionStore
import app.signkorean.domain.learning.StartResult
import app.signkorean.domain.learning.TransactionRunner
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.Verdict
import java.time.Instant

/**
 * 학습 저장소 포트 계약 (구현 계획 Phase 6 D1). 가짜와 Room 이 같은 검사를 통과해야 한다.
 * 테스트 프레임워크에 묶이지 않도록 실패는 AssertionError 로 던진다(JUnit 4·5 어디서나 부른다).
 */
class LearningStoreContract(
    private val states: LearningStateStore,
    private val sessions: SessionStore,
    private val tx: TransactionRunner,
) {
    private val now = Instant.parse("2026-10-21T09:00:00Z")

    private fun expect(condition: Boolean, message: () -> String) {
        if (!condition) throw AssertionError(message())
    }

    suspend fun verifyAll() {
        cardStatesRoundTrip()
        restoreUnseenDeletes()
        clearRemovesOnlyStates()
        sessionRoundTripKeepsEveryField()
        saveReplacesCurrent()
        transactionRollsBackBothStores()
    }

    suspend fun cardStatesRoundTrip() {
        states.clear()
        states.save(CardState(CardId("b01"), CardStatus.Learning, now))
        states.save(CardState(CardId("b01"), CardStatus.Known, now))
        states.save(CardState(CardId("t-market-1"), CardStatus.Learning, now))
        val all = states.all()
        expect(all == mapOf(CardId("b01") to CardStatus.Known, CardId("t-market-1") to CardStatus.Learning)) { "상태 왕복: $all" }
    }

    suspend fun restoreUnseenDeletes() {
        states.clear()
        states.save(CardState(CardId("b01"), CardStatus.Learning, now))
        states.restore(CardStatusRestore(CardId("b01"), CardStatus.Unseen))
        val afterUnseen = states.all()
        expect(afterUnseen.isEmpty()) { "Unseen 으로 되돌리면 기록이 지워져야 한다: $afterUnseen" }
        states.restore(CardStatusRestore(CardId("b02"), CardStatus.Known))
        val afterKnown = states.all()
        expect(afterKnown == mapOf(CardId("b02") to CardStatus.Known)) { "Known 으로 되돌리기: $afterKnown" }
    }

    suspend fun clearRemovesOnlyStates() {
        sessions.save(sampleSession())
        states.save(CardState(CardId("b01"), CardStatus.Known, now))
        states.clear()
        expect(states.all().isEmpty()) { "학습 기록 초기화" }
        expect(sessions.current() != null) { "학습 기록 초기화가 세션까지 지우면 안 된다(세션은 따로 끝낸다)" }
        sessions.clear()
        expect(sessions.current() == null) { "세션 지우기" }
    }

    suspend fun sessionRoundTripKeepsEveryField() {
        val s = sampleSession()
        sessions.save(s)
        val loaded = sessions.current()
        expect(loaded == s) { "세션 왕복이 값을 잃었다:\n저장 $s\n읽음 $loaded" }
    }

    suspend fun saveReplacesCurrent() {
        val a = sampleSession("a")
        val b = sampleSession("b", SessionSource.TripDay("trip-1", 2))
        sessions.save(a)
        sessions.save(b)
        expect(sessions.current() == b) { "마지막에 저장한 세션이 현재 세션이어야 한다" }
    }

    suspend fun transactionRollsBackBothStores() {
        states.clear()
        sessions.clear()
        val s = sampleSession()
        val failed = runCatching {
            tx.run {
                states.save(CardState(CardId("b01"), CardStatus.Known, now))
                sessions.save(s)
                error("저장 도중 죽음")
            }
        }
        expect(failed.isFailure) { "예외가 밖으로 전해져야 한다" }
        val afterRollback = states.all()
        expect(afterRollback.isEmpty()) { "롤백 뒤 카드 상태가 남았다: $afterRollback" }
        expect(sessions.current() == null) { "롤백 뒤 세션이 남았다" }
    }

    /** 판정·재출제·Undo 기록이 모두 들어간 세션. 직렬화가 값을 하나라도 잃으면 왕복 검사에서 걸린다. */
    private fun sampleSession(id: String = "s1", source: SessionSource = SessionSource.Area(AreaId("myeongdong"))): app.signkorean.domain.learning.LearningSession {
        val cards = (1..4).map { ContentFixtures.basic("b0$it", "단어$it") }
        var s = (SessionFactory.start(SessionId(id), source, Mode.All, cards, mapOf(CardId("b02") to CardStatus.Known)) as StartResult.Started).session
        s = SessionReducer.judge(s, Verdict.DontKnow, now)!!.session
        s = SessionReducer.flip(s)
        s = SessionReducer.judge(s, Verdict.Know, now)!!.session
        return SessionReducer.flip(s)
    }
}
