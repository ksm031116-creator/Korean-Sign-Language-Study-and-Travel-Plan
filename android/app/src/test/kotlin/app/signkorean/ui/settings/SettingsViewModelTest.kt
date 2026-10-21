package app.signkorean.ui.settings

import app.signkorean.R
import app.signkorean.domain.bookmark.Bookmark
import app.signkorean.domain.bookmark.ClearBookmarks
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.ReviewStatus
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.Entitlements
import app.signkorean.domain.learning.CardState
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.ResetLearning
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeBookmarkStore
import app.signkorean.testing.FakeCurrentLocale
import app.signkorean.testing.FakeEntitlementCache
import app.signkorean.testing.FakeEntitlementGateway
import app.signkorean.testing.FakeLearningStateStore
import app.signkorean.testing.FakeNetworkMonitor
import app.signkorean.testing.FakeOnboardingStore
import app.signkorean.testing.FakeSessionStore
import app.signkorean.testing.FakeTransactionRunner
import app.signkorean.testing.InMemoryContentRepository
import app.signkorean.ui.plan.DebugRelock
import app.signkorean.ui.text.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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

/** SC10 설정 (FR16, 구현 계획 Phase 20 D1). E-10, B-09, L10N-02 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val now = Instant.parse("2026-10-21T09:00:00Z")
    private val states = FakeLearningStateStore()
    private val sessions = FakeSessionStore()
    private val bookmarks = FakeBookmarkStore(listOf(Bookmark(CardId("c1"), now)))
    private val tx = FakeTransactionRunner(states, sessions)
    private val gateway = FakeEntitlementGateway()
    private val entitlements = Entitlements(gateway, FakeEntitlementCache(Entitlement.Paid), FakeNetworkMonitor())
    private val onboarding = FakeOnboardingStore(firstRun = true, coach = true)
    private val locale = FakeCurrentLocale()
    private val content = InMemoryContentRepository(
        ContentFixtures.pack(locales = mapOf(ContentFixtures.EN to ReviewStatus.Reviewed, ContentFixtures.JA to ReviewStatus.Draft)),
    )
    private var relocked = false

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(drafts: Boolean = true, relock: DebugRelock? = null) = SettingsViewModel(
        ResetLearning(states, sessions, tx), ClearBookmarks(bookmarks), entitlements, onboarding, content, locale, drafts, relock,
    )

    private suspend fun learned() = states.save(CardState(CardId("c2"), CardStatus.Known, now))

    @Test
    fun `E-10 학습 기록 초기화 확인 - 학습만 지우고 권한·북마크는 남는다`() = runTest {
        learned()
        val vm = vm()
        vm.onAction(SettingsAction.AskResetLearning)
        assertEquals(SettingsConfirm.ResetLearning, vm.state.value.confirm)
        vm.onAction(SettingsAction.Confirm)
        assertNull(vm.state.value.confirm)
        assertTrue(states.all().isEmpty())
        assertEquals(1, bookmarks.count())
        assertEquals(Entitlement.Paid, entitlements.current())
        assertEquals(UiText.Res(R.string.settings_reset_done), vm.state.value.message)
    }

    @Test
    fun `E-10 취소 - 변경 없음`() = runTest {
        learned()
        val vm = vm()
        vm.onAction(SettingsAction.AskResetLearning)
        vm.onAction(SettingsAction.Cancel)
        assertNull(vm.state.value.confirm)
        assertEquals(1, states.all().size)
        assertNull(vm.state.value.message)
    }

    @Test
    fun `B-09 북마크 초기화 확인 - 북마크만 지우고 학습·권한은 남는다`() = runTest {
        learned()
        val vm = vm()
        vm.onAction(SettingsAction.AskClearBookmarks)
        vm.onAction(SettingsAction.Confirm)
        assertEquals(0, bookmarks.count())
        assertEquals(1, states.all().size)
        assertEquals(Entitlement.Paid, entitlements.current())
        assertEquals(UiText.Res(R.string.settings_bookmarks_cleared), vm.state.value.message)
    }

    @Test
    fun `B-09 취소 - 변경 없음`() = runTest {
        val vm = vm()
        vm.onAction(SettingsAction.AskClearBookmarks)
        vm.onAction(SettingsAction.Cancel)
        assertEquals(1, bookmarks.count())
    }

    @Test
    fun `저장 실패는 안내하고 그대로 둔다`() = runTest {
        bookmarks.failNextWrite = true
        val vm = vm()
        vm.onAction(SettingsAction.AskClearBookmarks)
        vm.onAction(SettingsAction.Confirm)
        assertEquals(UiText.Res(R.string.common_save_failed), vm.state.value.message)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `구매 상태와 복원 - 복원됨, 없음, 실패`() = runTest {
        val vm = vm()
        assertEquals(Entitlement.Paid, vm.state.value.entitlement)
        gateway.owned = listOf(FakeEntitlementGateway.purchased("t1"))
        vm.onAction(SettingsAction.Restore)
        assertEquals(UiText.Res(R.string.upgrade_restored), vm.state.value.message)
        gateway.owned = emptyList()
        vm.onAction(SettingsAction.Restore)
        assertEquals(UiText.Res(R.string.upgrade_restore_none), vm.state.value.message)
        gateway.queryFails = true
        vm.onAction(SettingsAction.Restore)
        assertEquals(UiText.Res(R.string.upgrade_restore_failed), vm.state.value.message)
    }

    @Test
    fun `L10N-02 앱 언어를 바꿔도 학습 상태는 그대로`() = runTest {
        learned()
        val vm = vm()
        assertEquals(listOf(ContentFixtures.EN, ContentFixtures.JA), vm.state.value.languages)
        vm.onAction(SettingsAction.SelectLanguage(ContentFixtures.JA))
        assertEquals(ContentFixtures.JA, locale.selected())
        assertEquals(ContentFixtures.JA, vm.state.value.current)
        assertEquals(1, states.all().size)
    }

    @Test
    fun `출시 빌드는 검수 완료 언어만`() = runTest {
        assertEquals(listOf(ContentFixtures.EN), vm(drafts = false).state.value.languages)
    }

    @Test
    fun `제스처 안내 다시 보기`() = runTest {
        val vm = vm()
        vm.onAction(SettingsAction.ResetCoach)
        assertFalse(onboarding.coachSeen())
        assertEquals(UiText.Res(R.string.settings_coach_reset_done), vm.state.value.message)
    }

    @Test
    fun `다시 잠그기는 디버그 모의 결제에만 있다`() = runTest {
        assertNull(vm().state.value.relockLabel)
        val relock = object : DebugRelock {
            override val label = R.string.debug_relock
            override suspend fun relock() { relocked = true }
        }
        val vm = vm(relock = relock)
        assertEquals(R.string.debug_relock, vm.state.value.relockLabel)
        vm.onAction(SettingsAction.Relock)
        assertTrue(relocked)
    }
}
