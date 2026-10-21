package app.signkorean.ui.upgrade

import app.signkorean.R
import app.signkorean.domain.bookmark.Bookmark
import app.signkorean.domain.bookmark.SetBookmark
import app.signkorean.domain.content.CardId
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.Entitlements
import app.signkorean.domain.entitlement.FailReason
import app.signkorean.domain.entitlement.PurchaseResult
import app.signkorean.domain.upgrade.PendingAction
import app.signkorean.domain.upgrade.ResumePendingAction
import app.signkorean.testing.FakeBookmarkStore
import app.signkorean.testing.FakeEntitlementCache
import app.signkorean.testing.FakeEntitlementGateway
import app.signkorean.testing.FakeEntitlementGateway.Companion.purchased
import app.signkorean.testing.FakeNetworkMonitor
import app.signkorean.testing.FakePendingActionStore
import app.signkorean.testing.FakeTransactionRunner
import app.signkorean.testing.FixedClock
import app.signkorean.ui.text.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import app.signkorean.domain.common.AppScope
import app.signkorean.domain.entitlement.PurchaseFlowState
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/** SC21 (PRD §14.1, FR23~27). B-04·E-05~09 의 화면 쪽 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpgradeViewModelTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val gateway = FakeEntitlementGateway(price = "¥150")
    private val cache = FakeEntitlementCache()
    private val network = FakeNetworkMonitor()
    private val entitlements = Entitlements(gateway, cache, network)
    private val bookmarks = FakeBookmarkStore((1..5).map { Bookmark(CardId("b%02d".format(it)), clock.now()) })
    private val pending = FakePendingActionStore()
    private val resume = ResumePendingAction(pending, SetBookmark(bookmarks, FakeTransactionRunner(bookmarks), entitlements, clock))
    private val sixth = CardId("b06")

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.vm(feature: UpgradeFeature = UpgradeFeature.BookmarkLimit) =
        UpgradeViewModel(feature, entitlements, bookmarks, pending, resume, AppScope(CoroutineScope(UnconfinedTestDispatcher(testScheduler))))

    private fun UpgradeViewModel.nextEffect() = effects.tryReceive().getOrNull()

    @Test
    fun `L10N-11 가격은 스토어가 준 문자열 그대로`() = runTest {
        assertEquals("¥150", vm().state.value.price)
    }

    @Test
    fun `가격을 못 받으면 가격 없이도 결제할 수 있다`() = runTest {
        gateway.price = null
        val s = vm().state.value
        assertNull(s.price)
        assertTrue(s.priceChecked)
    }

    @Test
    fun `북마크 한도에서 왔으면 n of 5 를 보인다`() = runTest {
        assertEquals(5, vm().state.value.savedCount)
        assertNull("다른 진입점은 저장 수를 보이지 않는다", vm(UpgradeFeature.Plan).state.value.savedCount)
    }

    @Test
    fun `B-04 결제에 성공하면 보류한 6번째 저장을 이어서 하고 닫는다`() = runTest {
        pending.save(PendingAction.SaveBookmark(sixth))
        val vm = vm()
        vm.onAction(UpgradeAction.Unlock)
        assertEquals(UpgradeEffect.Unlocked(UiText.Res(R.string.upgrade_unlocked)), vm.nextEffect())
        assertTrue(bookmarks.contains(sixth))
        assertEquals(6, bookmarks.count())
        assertNull(pending.peek())
    }

    @Test
    fun `E-05 결제 취소는 오류 없이 원래 화면으로`() = runTest {
        gateway.nextPurchase = PurchaseResult.Cancelled
        pending.save(PendingAction.SaveBookmark(sixth))
        val vm = vm()
        vm.onAction(UpgradeAction.Unlock)
        assertEquals(UpgradeEffect.Close, vm.nextEffect())
        assertNull(vm.state.value.message)
        assertNull("취소하면 보류 동작도 버린다(나중 결제에서 뜻밖에 저장되지 않게)", pending.peek())
    }

    @Test
    fun `E-06 결제 실패는 과금되지 않았다고 알리고 다시 시도 버튼`() = runTest {
        gateway.failWith(FailReason.StoreUnavailable)
        val vm = vm()
        vm.onAction(UpgradeAction.Unlock)
        assertEquals(UiText.Res(R.string.upgrade_failed_store), vm.state.value.message)
        assertTrue(vm.state.value.failed)
        assertNull(vm.nextEffect())

        gateway.nextPurchase = PurchaseResult.Purchased(purchased("t2", acknowledged = false))
        vm.onAction(UpgradeAction.Unlock)
        assertTrue(vm.nextEffect() is UpgradeEffect.Unlocked)
    }

    @Test
    fun `실패 이유마다 문구가 다르다`() = runTest {
        val reasons = mapOf(
            FailReason.StoreUnavailable to R.string.upgrade_failed_store,
            FailReason.ProductUnavailable to R.string.upgrade_failed_product,
            FailReason.Unknown to R.string.upgrade_failed,
        )
        reasons.forEach { (reason, text) ->
            gateway.failWith(reason)
            val vm = vm()
            vm.onAction(UpgradeAction.Unlock)
            assertEquals(UiText.Res(text), vm.state.value.message)
        }
    }

    @Test
    fun `E-07 오프라인이면 결제를 부르지 않고 연결 안내`() = runTest {
        network.online = false
        val vm = vm()
        vm.onAction(UpgradeAction.Unlock)
        assertEquals(UiText.Res(R.string.upgrade_offline), vm.state.value.message)
        assertEquals(0, gateway.purchaseCalls)
    }

    @Test
    fun `E-08 결제 중에는 바쁨 상태, 연타해도 결제 한 번`() = runTest {
        gateway.holdPurchase = true
        val vm = vm()
        repeat(3) { vm.onAction(UpgradeAction.Unlock) }
        assertTrue(vm.state.value.busy)
        assertEquals(1, gateway.purchaseCalls)
        gateway.releasePurchase()
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `E-02 대기 결제는 화면에 남아 대기 안내`() = runTest {
        gateway.nextPurchase = PurchaseResult.Pending
        val vm = vm()
        vm.onAction(UpgradeAction.Unlock)
        assertEquals(UiText.Res(R.string.upgrade_pending), vm.state.value.message)
        assertNull(vm.nextEffect())
    }

    @Test
    fun `E-09 복원 - 있음은 닫고 이어서, 없음과 실패는 서로 다른 안내`() = runTest {
        pending.save(PendingAction.SaveBookmark(sixth))
        val vm = vm()
        vm.onAction(UpgradeAction.Restore)
        assertEquals(UiText.Res(R.string.upgrade_restore_none), vm.state.value.message)

        gateway.queryFails = true
        vm.onAction(UpgradeAction.Restore)
        assertEquals(UiText.Res(R.string.upgrade_restore_failed), vm.state.value.message)

        gateway.queryFails = false
        gateway.owned = listOf(purchased("old"))
        vm.onAction(UpgradeAction.Restore)
        assertEquals(UpgradeEffect.Unlocked(UiText.Res(R.string.upgrade_restored)), vm.nextEffect())
        assertTrue(bookmarks.contains(sixth))
    }

    @Test
    fun `Not now 는 보류 동작을 지우고 닫는다`() = runTest {
        pending.save(PendingAction.SaveBookmark(sixth))
        val vm = vm()
        vm.onAction(UpgradeAction.NotNow)
        assertEquals(UpgradeEffect.Close, vm.nextEffect())
        assertNull(pending.peek())
        assertEquals(5, bookmarks.count())
    }

    @Test
    fun `이미 Paid 면 보류 동작을 하고 바로 닫는다`() = runTest {
        cache.stored = Entitlement.Paid
        pending.save(PendingAction.SaveBookmark(sixth))
        val vm = vm()
        assertEquals(UpgradeEffect.Close, vm.nextEffect())
        assertTrue(bookmarks.contains(sixth))
    }

    @Test
    fun `지난번 결과 안내는 새로 연 화면에 남지 않는다`() = runTest {
        gateway.failWith(FailReason.Unknown)
        vm().onAction(UpgradeAction.Unlock)
        assertNull(vm().state.value.message)
    }

    // ---------- Phase 13 리뷰 수정 ----------

    @Test
    fun `UPG-01 대기 결제 중 Not now 로 떠나도 보류 동작은 남고, 결제가 끝나면 저장된다`() = runTest {
        pending.save(PendingAction.SaveBookmark(sixth))
        gateway.nextPurchase = PurchaseResult.Pending
        val vm = vm()
        vm.onAction(UpgradeAction.Unlock)
        vm.onAction(UpgradeAction.NotNow)
        assertEquals(UpgradeEffect.Close, vm.nextEffect())
        assertEquals(PendingAction.SaveBookmark(sixth), pending.peek())
        assertEquals("다음 SC21 도 대기 중임을 안다", PurchaseFlowState.Pending, entitlements.purchaseFlow.value)

        gateway.completePending()
        entitlements.refresh()
        resume() // 앱 수준 확인이 하는 일
        assertTrue(bookmarks.contains(sixth))
    }

    @Test
    fun `UPG-02 결제 중에는 Not now 가 듣지 않는다`() = runTest {
        pending.save(PendingAction.SaveBookmark(sixth))
        gateway.holdPurchase = true
        val vm = vm()
        vm.onAction(UpgradeAction.Unlock)
        vm.onAction(UpgradeAction.NotNow)
        assertNull(vm.nextEffect())
        assertEquals(PendingAction.SaveBookmark(sixth), pending.peek())
        gateway.releasePurchase()
    }

    @Test
    fun `UPG-02 화면이 닫혀도 결제는 앱 범위에서 끝까지 간다`() = runTest {
        gateway.holdPurchase = true
        val vm = vm()
        vm.onAction(UpgradeAction.Unlock)
        vm.viewModelScope.cancel() // 화면 항목이 닫혀 ViewModel 이 정리됐다
        gateway.releasePurchase()
        assertEquals(Entitlement.Paid, entitlements.current())
    }

    @Test
    fun `UPG-04 Unlock 을 누른 즉시 바빠서 Restore 가 끼어들지 않는다`() = runTest {
        gateway.holdPurchase = true
        val vm = vm()
        vm.onAction(UpgradeAction.Unlock)
        val queries = gateway.queryCalls
        vm.onAction(UpgradeAction.Restore)
        assertEquals(queries, gateway.queryCalls)
        gateway.releasePurchase()
        assertTrue(vm.nextEffect() is UpgradeEffect.Unlocked)
    }

    @Test
    fun `UPG-05 실패 뒤 다시 시도하면 실패 표시가 풀린다`() = runTest {
        gateway.failWith(FailReason.Unknown)
        val vm = vm()
        vm.onAction(UpgradeAction.Unlock)
        assertTrue(vm.state.value.failed)
        gateway.nextPurchase = PurchaseResult.Pending
        vm.onAction(UpgradeAction.Unlock)
        assertFalse(vm.state.value.failed)
        assertEquals(UiText.Res(R.string.upgrade_pending), vm.state.value.message)
    }

    @Test
    fun `다른 곳에서 시작한 결제의 취소는 이 화면을 닫지 않는다`() = runTest {
        pending.save(PendingAction.SaveBookmark(sixth))
        val vm = vm()
        gateway.nextPurchase = PurchaseResult.Cancelled
        entitlements.unlock()
        assertNull(vm.nextEffect())
        assertEquals(PendingAction.SaveBookmark(sixth), pending.peek())
    }

    @Test
    fun `UPG-13 가격을 못 받았으면 다시 시도할 때 다시 묻는다`() = runTest {
        gateway.price = null
        gateway.holdPurchase = true
        val vm = vm()
        assertNull(vm.state.value.price)
        gateway.price = "US$0.99"
        vm.onAction(UpgradeAction.Unlock)
        assertEquals("US$0.99", vm.state.value.price)
        gateway.releasePurchase()
    }

    @Test
    fun `UPG-14 Saved 잠금 배지에서 연 SC21 은 저장 수를 보이지 않는다`() = runTest {
        assertNull(vm(UpgradeFeature.Bookmarks).state.value.savedCount)
    }
}
