package app.signkorean.domain.upgrade

import app.signkorean.domain.bookmark.SetBookmark
import app.signkorean.domain.content.CardId
import app.signkorean.domain.entitlement.Entitlements
import app.signkorean.testing.FakeBookmarkStore
import app.signkorean.testing.FakeEntitlementCache
import app.signkorean.testing.FakeEntitlementGateway
import app.signkorean.testing.FakeEntitlementGateway.Companion.pending
import app.signkorean.testing.FakeEntitlementGateway.Companion.purchased
import app.signkorean.testing.FakeNetworkMonitor
import app.signkorean.testing.FakePendingActionStore
import app.signkorean.testing.FakeTransactionRunner
import app.signkorean.testing.FixedClock
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 앱 시작 때 보류 동작 정리 (Phase 13 리뷰 UPG-07). 화면 이동은 복원되지 않으므로(PRD §14) 시작 시점에 SC21 은 없다.
 * 결제가 진행된 흔적이 없으면 지난 실행의 보류 동작을 버리고, 결제가 끝났거나 대기 중이면 남긴다(B-07).
 */
class StartupPendingCheckTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val gateway = FakeEntitlementGateway()
    private val entitlements = Entitlements(gateway, FakeEntitlementCache(), FakeNetworkMonitor())
    private val bookmarks = FakeBookmarkStore()
    private val action = PendingAction.SaveBookmark(CardId("b06"))
    private val pending = FakePendingActionStore(action)
    private val resume = ResumePendingAction(pending, SetBookmark(bookmarks, FakeTransactionRunner(bookmarks), entitlements, clock))
    private val check = StartupPendingCheck(entitlements, pending, resume)

    @Test
    fun `결제 흔적이 없으면 지난 실행의 보류 동작을 버린다`() = runTest {
        check()
        assertNull(pending.peek())
    }

    @Test
    fun `B-07 결제가 끝났으면 이어서 저장한다`() = runTest {
        gateway.owned = listOf(purchased("t", acknowledged = false))
        check()
        assertTrue(bookmarks.contains(CardId("b06")))
        assertNull(pending.peek())
    }

    @Test
    fun `대기 중인 결제가 있으면 남긴다`() = runTest {
        gateway.owned = listOf(pending("p"))
        check()
        assertEquals(action, pending.peek())
    }

    @Test
    fun `스토어를 확인하지 못하면 남긴다`() = runTest {
        gateway.queryFails = true
        check()
        assertEquals(action, pending.peek())
    }
}
