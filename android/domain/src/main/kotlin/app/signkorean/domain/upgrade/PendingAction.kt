package app.signkorean.domain.upgrade

import app.signkorean.domain.bookmark.BookmarkOutcome
import app.signkorean.domain.bookmark.SetBookmark
import app.signkorean.domain.content.CardId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 업그레이드 화면(SC21)을 열게 만든 동작. 결제에 성공하면 이어서 수행한다 (PRD §14.1, 설계서 §6.3).
 * 화면 이동만 필요한 진입점(Plan 탭 등)은 보류 동작이 없다. 결제 뒤 그 화면으로 돌아가면 이미 열려 있다.
 */
sealed interface PendingAction {
    /** 무료 한도에서 막힌 북마크 저장 (FR27). */
    data class SaveBookmark(val card: CardId) : PendingAction
}

/**
 * 보류 동작 한 건. SC21 을 열기 전에 저장하고, 결제 중 프로세스가 끝나도 남는다(B-07). 구현은 DataStore.
 * 새로 저장하면 이전 것을 바꾼다.
 */
interface PendingActionStore {
    suspend fun save(action: PendingAction)
    suspend fun peek(): PendingAction?
    suspend fun clear()
}

sealed interface ResumeResult {
    data object Nothing : ResumeResult
    data class Done(val action: PendingAction) : ResumeResult

    /** 아직 무료다. 보류 동작은 남겨 둔다. */
    data object StillLocked : ResumeResult

    /** 수행하다 실패했다. 다음 확인 때 다시 한다. */
    data object Failed : ResumeResult
}

/**
 * 보류 동작을 이어서 수행한다. 결제 성공 직후(SC21)와 권한이 Paid 로 확인될 때(앱 시작 포함) 부른다.
 * 성공했을 때만 지운다. 여러 곳에서 동시에 불러도 한 번만 수행하도록 앱에 하나만 둔다.
 */
class ResumePendingAction(
    private val store: PendingActionStore,
    private val setBookmark: SetBookmark,
) {
    private val mutex = Mutex()

    suspend operator fun invoke(): ResumeResult = mutex.withLock {
        val action = try {
            store.peek()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return@withLock ResumeResult.Failed
        } ?: return@withLock ResumeResult.Nothing
        val result = when (action) {
            is PendingAction.SaveBookmark -> when (setBookmark(action.card, saved = true)) {
                BookmarkOutcome.Saved, BookmarkOutcome.Unchanged -> ResumeResult.Done(action)
                is BookmarkOutcome.NeedsUpgrade -> ResumeResult.StillLocked
                BookmarkOutcome.SaveFailed, BookmarkOutcome.Removed -> ResumeResult.Failed
            }
        }
        if (result is ResumeResult.Done) {
            try {
                store.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 지우지 못해도 다시 수행하면 Unchanged 로 끝난다(같은 카드는 한 번만 저장된다).
            }
        }
        result
    }
}
