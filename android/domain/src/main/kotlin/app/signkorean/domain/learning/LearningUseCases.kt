package app.signkorean.domain.learning

import app.signkorean.domain.common.Clock
import app.signkorean.domain.common.IdGenerator
import app.signkorean.domain.content.SignCard
import kotlinx.coroutines.CancellationException

sealed interface StartOutcome {
    data class Started(val session: LearningSession) : StartOutcome

    /** 끝나지 않은 다른 세션이 있다. SC20 확인 뒤 replaceUnfinished = true 로 다시 부른다 (L-16). */
    data class NeedsConfirmation(val unfinished: LearningSession) : StartOutcome

    /** 같은 출처·모드의 끝나지 않은 세션이 있다. 새로 만들지 않고 이어 한다(PRD §5.1: 새 카테고리·모드만 교체). */
    data class Resumed(val session: LearningSession) : StartOutcome

    data object NoCards : StartOutcome
    data object NothingToReview : StartOutcome
    data object SaveFailed : StartOutcome
}

/**
 * 새 세션 시작. 다른 출처·모드를 고르면 확인 뒤 현재 세션을 끝내고 새로 만든다.
 * 카드별 학습 상태는 그대로 둔다(PRD §5.1).
 */
class StartSession(
    private val sessions: SessionStore,
    private val states: LearningStateStore,
    private val tx: TransactionRunner,
    private val ids: IdGenerator,
) {
    suspend operator fun invoke(source: SessionSource, mode: Mode, cards: List<SignCard>, replaceUnfinished: Boolean): StartOutcome =
        try {
            tx.run {
                // 먼저 새 세션이 실제로 시작되는지 본다. 카드가 없는데 교체 확인부터 묻지 않는다.
                when (val r = SessionFactory.start(SessionId(ids.next()), source, mode, cards, states.all())) {
                    StartResult.NoCards -> StartOutcome.NoCards
                    StartResult.NothingToReview -> StartOutcome.NothingToReview
                    is StartResult.Started -> {
                        val existing = sessions.current()?.takeUnless { it.finished }
                        when {
                            existing != null && existing.isSameStart(source, mode) -> StartOutcome.Resumed(existing)
                            existing != null && !replaceUnfinished -> StartOutcome.NeedsConfirmation(existing)
                            else -> {
                                sessions.save(r.session)
                                StartOutcome.Started(r.session)
                            }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StartOutcome.SaveFailed
        }
}

/** 앱을 다시 열었을 때 이어 할 세션(SC02 Resume 배너, FR07). 끝난 세션은 이어 하지 않는다. */
class ResumeSession(private val sessions: SessionStore) {
    suspend operator fun invoke(): LearningSession? = sessions.current()?.takeUnless { it.finished }
}

sealed interface JudgeOutcome {
    data class Judged(val session: LearningSession) : JudgeOutcome

    /** 화면이 본 순번이 이미 지났다(중복 탭·동시 호출). 화면은 무시한다 (L-09, L-18). */
    data object StaleInput : JudgeOutcome

    data object NoSession : JudgeOutcome

    /** 저장 실패. 세션·카드 상태는 그대로이고 화면은 같은 카드에 머문다 (L-10). */
    data object SaveFailed : JudgeOutcome
}

/**
 * 판정 (FR03·04·07). 읽기·순번 비교·저장을 한 트랜잭션 안에서 한다.
 * 밖에서 읽으면 두 호출이 같은 상태를 읽고 둘 다 저장한다(설계서 §6.2).
 */
class JudgeCard(
    private val sessions: SessionStore,
    private val states: LearningStateStore,
    private val tx: TransactionRunner,
    private val clock: Clock,
) {
    /**
     * [sessionId]·[expectedSeq] 는 화면이 본 세션과 순번이다. 둘 중 하나라도 다르면 이미 지난 입력이다
     * (중복 탭, 또는 다른 세션의 화면이 남아 있는 경우). 순번 비교를 끝남 검사보다 먼저 해서,
     * 마지막 카드의 두 번째 탭도 StaleInput 으로 무시되게 한다.
     */
    suspend operator fun invoke(sessionId: SessionId, expectedSeq: Int, verdict: Verdict): JudgeOutcome =
        try {
            tx.run {
                val session = sessions.current() ?: return@run JudgeOutcome.NoSession
                if (session.id != sessionId || session.decisionSeq != expectedSeq) return@run JudgeOutcome.StaleInput
                if (session.finished) return@run JudgeOutcome.NoSession
                val t = SessionReducer.judge(session, verdict, clock.now()) ?: return@run JudgeOutcome.NoSession
                states.save(t.cardState)
                sessions.save(t.session)
                JudgeOutcome.Judged(t.session)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            JudgeOutcome.SaveFailed
        }
}

sealed interface UndoOutcome {
    data class Undone(val session: LearningSession) : UndoOutcome
    data object NothingToUndo : UndoOutcome
    data object StaleInput : UndoOutcome
    data object SaveFailed : UndoOutcome
}

/** 직전 판정 1건 되돌리기 (FR06). 판정과 같은 순번 비교·트랜잭션을 쓴다. */
class UndoJudgement(
    private val sessions: SessionStore,
    private val states: LearningStateStore,
    private val tx: TransactionRunner,
) {
    suspend operator fun invoke(sessionId: SessionId, expectedSeq: Int): UndoOutcome =
        try {
            tx.run {
                val session = sessions.current() ?: return@run UndoOutcome.NothingToUndo
                if (session.id != sessionId || session.decisionSeq != expectedSeq) return@run UndoOutcome.StaleInput
                val t = SessionReducer.undo(session) ?: return@run UndoOutcome.NothingToUndo
                states.restore(t.restoredState)
                sessions.save(t.session)
                UndoOutcome.Undone(t.session)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UndoOutcome.SaveFailed
        }
}
