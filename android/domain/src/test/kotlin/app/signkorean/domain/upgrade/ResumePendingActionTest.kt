package app.signkorean.domain.upgrade

import app.signkorean.domain.bookmark.Bookmark
import app.signkorean.domain.bookmark.SetBookmark
import app.signkorean.domain.content.CardId
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.testing.FakeBookmarkStore
import app.signkorean.testing.FakePendingActionStore
import app.signkorean.testing.FakeTransactionRunner
import app.signkorean.testing.FixedClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/** 결제 뒤 원래 하려던 동작 이어서 하기 (PRD §14.1, 설계서 §6.3). B-04·07 */
class ResumePendingActionTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private var entitlement = Entitlement.Free
    private val bookmarks = FakeBookmarkStore((1..5).map { Bookmark(CardId("b%02d".format(it)), clock.now()) })
    private val pending = FakePendingActionStore()
    private val setBookmark = SetBookmark(bookmarks, FakeTransactionRunner(bookmarks), { entitlement }, clock)
    private val resume = ResumePendingAction(pending, setBookmark)
    private val sixth = CardId("b06")

    @Test
    fun `B-04 결제하면 기존 5개를 그대로 두고 보류한 6번째를 저장한다`() = runTest {
        pending.save(PendingAction.SaveBookmark(sixth))
        entitlement = Entitlement.Paid
        assertEquals(ResumeResult.Done(PendingAction.SaveBookmark(sixth)), resume())
        assertEquals(6, bookmarks.count())
        assertTrue(bookmarks.contains(sixth))
        assertNull(pending.peek(), "이어서 한 동작은 지운다")
    }

    @Test
    fun `B-07 결제 중 프로세스가 죽어도 다시 켠 뒤 보류 동작을 이어서 한다`() = runTest {
        pending.save(PendingAction.SaveBookmark(sixth))
        // 프로세스 종료: 메모리의 객체는 사라지고 저장소(DataStore)에 남은 값만 있다.
        val restartedStore = FakePendingActionStore(pending.peek())
        entitlement = Entitlement.Paid // 복귀 후 권한 확인에서 대기 결제가 끝났다
        val afterRestart = ResumePendingAction(restartedStore, setBookmark)
        assertEquals(ResumeResult.Done(PendingAction.SaveBookmark(sixth)), afterRestart())
        assertTrue(bookmarks.contains(sixth))
    }

    @Test
    fun `아직 무료면 보류 동작을 지우지 않는다`() = runTest {
        pending.save(PendingAction.SaveBookmark(sixth))
        assertEquals(ResumeResult.StillLocked, resume())
        assertEquals(PendingAction.SaveBookmark(sixth), pending.peek())
        assertEquals(5, bookmarks.count())
    }

    @Test
    fun `저장 실패면 보류 동작을 남겨 다음에 다시 한다`() = runTest {
        pending.save(PendingAction.SaveBookmark(sixth))
        entitlement = Entitlement.Paid
        bookmarks.failNextWrite = true
        assertEquals(ResumeResult.Failed, resume())
        assertEquals(PendingAction.SaveBookmark(sixth), pending.peek())
        assertEquals(ResumeResult.Done(PendingAction.SaveBookmark(sixth)), resume())
    }

    @Test
    fun `보류 동작이 없으면 아무것도 하지 않는다`() = runTest {
        entitlement = Entitlement.Paid
        assertEquals(ResumeResult.Nothing, resume())
        assertEquals(5, bookmarks.count())
    }

    @Test
    fun `동시에 두 번 불러도 한 번만 수행한다`() = runTest {
        pending.save(PendingAction.SaveBookmark(sixth))
        entitlement = Entitlement.Paid
        val results = (1..3).map { async { resume() } }.awaitAll()
        assertEquals(1, results.count { it is ResumeResult.Done })
        assertEquals(6, bookmarks.count())
    }

    @Test
    fun `이미 저장된 카드면 그대로 끝낸다`() = runTest {
        pending.save(PendingAction.SaveBookmark(CardId("b01")))
        entitlement = Entitlement.Paid
        assertEquals(ResumeResult.Done(PendingAction.SaveBookmark(CardId("b01"))), resume())
        assertEquals(5, bookmarks.count())
        assertNull(pending.peek())
    }

    @Test
    fun `새 보류 동작은 이전 것을 바꾼다`() = runTest {
        pending.save(PendingAction.SaveBookmark(CardId("b07")))
        pending.save(PendingAction.SaveBookmark(sixth))
        assertEquals(PendingAction.SaveBookmark(sixth), pending.peek())
    }
}
