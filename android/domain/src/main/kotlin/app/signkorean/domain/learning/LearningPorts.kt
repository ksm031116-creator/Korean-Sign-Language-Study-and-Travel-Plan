package app.signkorean.domain.learning

import app.signkorean.domain.content.CardId

/** 카드별 학습 상태 저장소 (설계서 §6.6). 구현은 Room `card_state`. */
interface LearningStateStore {
    suspend fun all(): Map<CardId, CardStatus>

    suspend fun save(state: CardState)

    /** Undo 로 되돌린다. Unseen 이면 그 카드 기록을 지운다. */
    suspend fun restore(restore: CardStatusRestore)

    /** 학습 기록 초기화 (FR16). 북마크·구매 권한은 건드리지 않는다. */
    suspend fun clear()
}

/** 진행 중 세션 저장소. 세션은 한 번에 하나다. 구현은 Room `session`. */
interface SessionStore {
    /** 가장 최근 세션. 끝난 세션일 수도 있다(결과 화면 복구용). */
    suspend fun current(): LearningSession?

    /** 저장하면 그 세션이 현재 세션이 된다. 다른 세션은 지운다. */
    suspend fun save(session: LearningSession)

    suspend fun clear()
}

/**
 * 여러 저장을 한 트랜잭션으로 묶는 포트. 판정은 카드 상태와 세션을 함께 저장해야 하며(FR07, NFR03),
 * 둘 중 하나만 저장된 채로 앱이 죽으면 안 된다. [block] 이 예외를 던지면 안의 쓰기는 모두 취소된다.
 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}
