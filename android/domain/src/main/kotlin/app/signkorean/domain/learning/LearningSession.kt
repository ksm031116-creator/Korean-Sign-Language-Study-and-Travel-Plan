package app.signkorean.domain.learning

import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.CardId
import java.time.Instant

@JvmInline value class SessionId(val value: String)

/** 오른쪽 = 알아요, 왼쪽 = 몰라요. 아래(장소 목록)·위는 판정이 아니라 여기 없다 (PRD §4.3). */
enum class Verdict { Know, DontKnow }

/** unseen: 최초 판정 전, learning: 최근 판정이 모름, known: 최근 판정이 앎 (PRD §5.1). */
enum class CardStatus { Unseen, Learning, Known }

/** 저장 대상인 카드별 학습 상태. */
data class CardState(val card: CardId, val status: CardStatus, val updatedAt: Instant)

/** All cards: 출처의 활성 카드 전부. Review: learning 카드만 (FR05). */
enum class Mode { All, Review }

/** 앞면은 한국어만, 뒷면은 표기·음성·뜻·설명 (FR02). 뒤집기는 판정이 아니다. */
enum class Face { Front, Back }

/** 세션을 시작한 곳. 결과 화면의 "다시"와 세션 교체 확인에 쓴다. */
sealed interface SessionSource {
    data object Basics : SessionSource
    data class Area(val area: AreaId) : SessionSource
    data class Place(val place: String) : SessionSource
    data class TripDay(val trip: String, val day: Int) : SessionSource
    data object Saved : SessionSource
}

object SessionRules {
    /** 한 세션의 최대 판정 수 (FR08). */
    const val MAX_DECISIONS = 10

    /** 모름 카드는 다른 카드 이만큼 판정한 뒤 재출제한다 (PRD §5.1). */
    const val RETRY_GAP = 3
}

/** 대기열 항목. [readyAt] 은 이 카드를 다시 보여도 되는 판정 수. 처음 보는 카드는 0. */
data class QueueEntry(val card: CardId, val readyAt: Int = 0)

data class Decision(val card: CardId, val verdict: Verdict)

/** 직전 판정 1건을 되돌리기 위한 기록 (FR06). 상태·큐·집계를 함께 복구한다. */
data class UndoSnapshot(
    val card: CardId,
    val previousStatus: CardStatus,
    val queue: List<QueueEntry>,
    val decisions: List<Decision>,
    val face: Face,
)

/**
 * 학습 세션. 값 객체이며 [SessionReducer] 가 새 값을 만든다.
 * [statuses] 는 이 세션 카드들의 현재 학습 상태다. Undo 가 판정 전 상태로 되돌릴 때 쓴다.
 */
data class LearningSession(
    val id: SessionId,
    val source: SessionSource,
    val mode: Mode,
    val queue: List<QueueEntry>,
    val decisions: List<Decision>,
    /** 판정·Undo 마다 오른다. 화면이 본 순번과 저장 직전 순번을 비교해 중복 입력을 막는다 (AT01, NFR03). */
    val decisionSeq: Int,
    val face: Face,
    val undo: UndoSnapshot?,
    val statuses: Map<CardId, CardStatus>,
) {
    /** 10회 판정 또는 대기열 소진 (FR08). */
    val finished: Boolean get() = decisions.size >= SessionRules.MAX_DECISIONS || queue.isEmpty()

    val current: CardId? get() = if (finished) null else queue.first().card

    /** 사이에 넣을 카드가 부족해 재출제 간격을 다 채우지 못하고 다시 보여 주는 중 ("Try again"). */
    val isTryAgain: Boolean get() = !finished && queue.first().readyAt > decisions.size

    val canUndo: Boolean get() = undo != null
}
