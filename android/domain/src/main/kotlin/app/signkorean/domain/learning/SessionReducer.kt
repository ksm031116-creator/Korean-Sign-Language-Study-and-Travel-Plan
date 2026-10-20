package app.signkorean.domain.learning

import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.SignCard
import java.time.Instant

sealed interface StartResult {
    data class Started(val session: LearningSession) : StartResult

    /** 출처에 출제할 카드가 없다 (L-14). 빈 상태와 돌아가기를 보여 준다. */
    data object NoCards : StartResult

    /** Review 모드에 learning 카드가 없다 (L-06). 복습 완료 안내. */
    data object NothingToReview : StartResult
}

/** 새 세션 만들기. 카드 순서는 출처가 준 순서를 지킨다. */
object SessionFactory {

    fun start(
        id: SessionId,
        source: SessionSource,
        mode: Mode,
        cards: List<SignCard>,
        statuses: Map<CardId, CardStatus>,
    ): StartResult {
        // 비활성 카드는 출제하지 않는다(L-13). 진행 중 세션은 이미 만든 큐를 그대로 쓴다.
        val active = cards.filter { it.active }.map { it.id }.distinct()
        if (active.isEmpty()) return StartResult.NoCards
        val eligible = when (mode) {
            Mode.All -> active
            Mode.Review -> active.filter { statuses[it] == CardStatus.Learning }
        }
        if (eligible.isEmpty()) return StartResult.NothingToReview
        return StartResult.Started(
            LearningSession(
                id = id,
                source = source,
                mode = mode,
                cardIds = active,
                queue = eligible.map { QueueEntry(it) },
                decisions = emptyList(),
                decisionSeq = 0,
                face = Face.Front,
                undo = null,
                statuses = active.associateWith { statuses[it] ?: CardStatus.Unseen },
            ),
        )
    }
}

data class JudgeTransition(val session: LearningSession, val cardState: CardState)

data class UndoTransition(val session: LearningSession, val restoredState: CardStatusRestore)

/** Undo 로 되돌린 카드 상태. Unseen 이면 저장소에서 그 카드 상태를 지운다. */
data class CardStatusRestore(val card: CardId, val status: CardStatus)

/**
 * 세션 규칙 (PRD §5.1, 설계서 §6.2). 입력과 현재 상태를 받아 새 상태를 돌려준다. I/O 가 없다.
 * 뒤집기·음성·북마크·장소 목록은 판정이 아니라서 상태를 바꾸는 입력이 없다(뒤집기는 화면 면만 바꾼다).
 */
object SessionReducer {

    /** 끝난 세션이면 null. */
    fun judge(s: LearningSession, verdict: Verdict, now: Instant): JudgeTransition? {
        val card = s.current ?: return null
        val decisions = s.decisions + Decision(card, verdict)
        var queue = s.queue.drop(1)
        if (verdict == Verdict.DontKnow) {
            // 다른 카드 RETRY_GAP 회 판정 뒤 다시 낸다.
            queue = queue + QueueEntry(card, readyAt = decisions.size + SessionRules.RETRY_GAP)
        }
        queue = promoteReadyRetry(queue, decisions.size)
        val status = if (verdict == Verdict.Know) CardStatus.Known else CardStatus.Learning
        val next = s.copy(
            queue = queue,
            decisions = decisions,
            decisionSeq = s.decisionSeq + 1,
            face = Face.Front, // 다음 카드는 항상 앞면 (FR02, L-17)
            undo = UndoSnapshot(card, s.statuses[card] ?: CardStatus.Unseen, s.queue, s.decisions),
            statuses = s.statuses + (card to status),
        )
        return JudgeTransition(next, CardState(card, status, now))
    }

    /** 직전 판정 1건만 되돌린다 (FR06). 되돌릴 판정이 없으면 null. */
    fun undo(s: LearningSession): UndoTransition? {
        val u = s.undo ?: return null
        val restored = s.copy(
            queue = u.queue,
            decisions = u.decisions,
            decisionSeq = s.decisionSeq + 1,
            face = Face.Front,
            undo = null,
            statuses = s.statuses + (u.card to u.previousStatus),
        )
        return UndoTransition(restored, CardStatusRestore(u.card, u.previousStatus))
    }

    fun flip(s: LearningSession): LearningSession =
        s.copy(face = if (s.face == Face.Front) Face.Back else Face.Front)

    /**
     * 재출제 시간이 된 카드를 맨 앞으로. 아직 안 본 카드보다 먼저 낸다(간격이 지나치게 벌어지지 않게).
     * 시간이 된 카드가 없으면 그대로 두며, 그때 맨 앞이 재출제 카드면 "Try again" 이다.
     */
    private fun promoteReadyRetry(queue: List<QueueEntry>, decided: Int): List<QueueEntry> {
        val ready = queue.indexOfFirst { it.readyAt in 1..decided }
        if (ready <= 0) return queue
        return listOf(queue[ready]) + queue.filterIndexed { i, _ -> i != ready }
    }
}

/** 결과 화면 (FR08). 판정 수와 고유 카드 수를 구별한다. */
data class SessionSummary(
    val knownDecisions: Int,
    val learningDecisions: Int,
    val uniqueCards: Int,
    /** 이 세션 출처 카드 중 지금 learning 인 카드 수(다음 Review 후보). 이번에 보지 못한 카드도 센다(프로토타입과 같음). */
    val remainingReview: Int,
) {
    companion object {
        fun of(s: LearningSession, statuses: Map<CardId, CardStatus> = s.statuses): SessionSummary {
            val cards = s.decisions.map { it.card }.toSet()
            return SessionSummary(
                knownDecisions = s.decisions.count { it.verdict == Verdict.Know },
                learningDecisions = s.decisions.count { it.verdict == Verdict.DontKnow },
                uniqueCards = cards.size,
                remainingReview = s.cardIds.count { statuses[it] == CardStatus.Learning },
            )
        }
    }
}
