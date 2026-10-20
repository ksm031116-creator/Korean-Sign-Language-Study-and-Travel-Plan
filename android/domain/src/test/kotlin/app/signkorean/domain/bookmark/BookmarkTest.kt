package app.signkorean.domain.bookmark

import app.signkorean.domain.content.CardId
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.testing.FakeBookmarkStore
import app.signkorean.testing.FakeTransactionRunner
import app.signkorean.testing.FixedClock
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/** 북마크 (FR12, FR27, 설계서 §6.3). B-01~03·05·06·08·09 */
class BookmarkTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private var entitlement = Entitlement.Free
    private val store = FakeBookmarkStore(yieldOnAccess = true)
    private val set = SetBookmark(store, FakeTransactionRunner(store), { entitlement }, clock)
    private fun id(n: Int) = CardId("b%02d".format(n))

    private suspend fun saveMany(n: Int) = (1..n).forEach {
        assertEquals(BookmarkOutcome.Saved, set(id(it), true))
        clock.advance(Duration.ofSeconds(1))
    }

    @Test
    fun `B-01 무료 5개 저장 후 6번째는 업그레이드 안내`() = runTest {
        saveMany(5)
        assertEquals(BookmarkOutcome.NeedsUpgrade(5, 5), set(id(6), true))
        assertEquals(5, store.count())
    }

    @Test
    fun `B-02 하나 해제하면 한도가 돌아온다`() = runTest {
        saveMany(5)
        assertEquals(BookmarkOutcome.Removed, set(id(1), false))
        assertEquals(BookmarkOutcome.Saved, set(id(6), true))
    }

    @Test
    fun `B-03 같은 카드를 두 번 저장해도 하나, 동시에 와도 하나`() = runTest {
        assertEquals(BookmarkOutcome.Saved, set(id(1), true))
        assertEquals(BookmarkOutcome.Unchanged, set(id(1), true))
        val results = (1..4).map { async { set(id(2), true) } }.awaitAll()
        assertEquals(1, results.count { it == BookmarkOutcome.Saved })
        assertEquals(2, store.count())
    }

    @Test
    fun `동시 저장도 무료 한도를 넘지 않는다`() = runTest {
        saveMany(4)
        val results = (10..13).map { n -> async { set(id(n), true) } }.awaitAll()
        assertEquals(1, results.count { it == BookmarkOutcome.Saved })
        assertEquals(5, store.count())
    }

    @Test
    fun `유료는 한도가 없고, 무료로 바뀌어도 기존 북마크를 지우지 않는다 (E-12)`() = runTest {
        entitlement = Entitlement.Paid
        saveMany(8)
        entitlement = Entitlement.Free
        assertEquals(8, store.count())
        assertTrue(set(id(20), true) is BookmarkOutcome.NeedsUpgrade)
        assertEquals(BookmarkOutcome.Removed, set(id(1), false), "한도를 넘었어도 해제는 된다")
    }

    @Test
    fun `저장 실패는 SaveFailed, 상태는 그대로`() = runTest {
        store.failNextWrite = true
        assertEquals(BookmarkOutcome.SaveFailed, set(id(1), true))
        assertEquals(0, store.count())
    }

    @Test
    fun `목록은 최신순`() = runTest {
        saveMany(3)
        assertEquals(listOf(id(3), id(2), id(1)), store.all().map { it.card })
    }

    @Test
    fun `B-09 북마크 초기화는 북마크만 지운다`() = runTest {
        saveMany(3)
        assertTrue(ClearBookmarks(store)())
        assertEquals(0, store.count())
    }
}
