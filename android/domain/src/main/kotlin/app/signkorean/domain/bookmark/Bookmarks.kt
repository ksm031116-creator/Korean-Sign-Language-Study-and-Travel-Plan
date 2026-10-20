package app.signkorean.domain.bookmark

import app.signkorean.domain.common.Clock
import app.signkorean.domain.content.CardId
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.EntitlementSource
import app.signkorean.domain.learning.TransactionRunner
import kotlinx.coroutines.CancellationException
import java.time.Instant

data class Bookmark(val card: CardId, val createdAt: Instant)

/** 북마크 저장소 (설계서 §6.6). 같은 카드는 한 번만 저장된다(B-03). */
interface BookmarkStore {
    /** 최신순. */
    suspend fun all(): List<Bookmark>
    suspend fun count(): Int
    suspend fun contains(card: CardId): Boolean
    suspend fun add(bookmark: Bookmark)
    suspend fun remove(card: CardId)

    /** 북마크 초기화 (FR16). 학습 기록·구매 권한은 건드리지 않는다. */
    suspend fun clear()
}

sealed interface BookmarkDecision {
    data object Allow : BookmarkDecision
    data class NeedsUpgrade(val count: Int, val limit: Int) : BookmarkDecision
}

/** 무료는 5개까지 (FR27). 한도를 넘었다고 기존 북마크를 지우지 않는다. */
object BookmarkPolicy {
    const val FREE_LIMIT = 5

    fun canAdd(count: Int, entitlement: Entitlement): BookmarkDecision =
        if (entitlement == Entitlement.Paid || count < FREE_LIMIT) BookmarkDecision.Allow
        else BookmarkDecision.NeedsUpgrade(count, FREE_LIMIT)
}

sealed interface BookmarkOutcome {
    data object Saved : BookmarkOutcome
    data object Removed : BookmarkOutcome

    /** 이미 원하는 상태다(빠른 두 번 탭). 화면은 그대로 둔다. */
    data object Unchanged : BookmarkOutcome

    /** 무료 한도. 6번째 저장 시도는 SC21 로 간다 (FR27). */
    data class NeedsUpgrade(val count: Int, val limit: Int) : BookmarkOutcome
    data object SaveFailed : BookmarkOutcome
}

/**
 * 북마크를 [saved] 상태로 만든다. 토글이 아니라 원하는 상태를 받아서, 같은 입력을 두 번 받아도 한 번 저장한다(B-03).
 * 세기·검사·쓰기를 한 트랜잭션에서 해서 두 저장이 동시에 와도 한도를 넘지 않는다.
 */
class SetBookmark(
    private val store: BookmarkStore,
    private val tx: TransactionRunner,
    private val entitlement: EntitlementSource,
    private val clock: Clock,
) {
    suspend operator fun invoke(card: CardId, saved: Boolean): BookmarkOutcome = try {
        tx.run {
            val present = store.contains(card)
            when {
                saved == present -> BookmarkOutcome.Unchanged
                !saved -> {
                    store.remove(card)
                    BookmarkOutcome.Removed
                }
                else -> when (val d = BookmarkPolicy.canAdd(store.count(), entitlement.current())) {
                    is BookmarkDecision.NeedsUpgrade -> BookmarkOutcome.NeedsUpgrade(d.count, d.limit)
                    BookmarkDecision.Allow -> {
                        store.add(Bookmark(card, clock.now()))
                        BookmarkOutcome.Saved
                    }
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        BookmarkOutcome.SaveFailed
    }
}

/** 북마크 초기화 (FR16, B-09). 확인은 화면(SC20)이 받는다. */
class ClearBookmarks(private val store: BookmarkStore) {
    suspend operator fun invoke(): Boolean = try {
        store.clear()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }
}
