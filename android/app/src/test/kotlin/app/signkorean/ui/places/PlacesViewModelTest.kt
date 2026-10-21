package app.signkorean.ui.places

import app.signkorean.R
import app.signkorean.domain.bookmark.Bookmark
import app.signkorean.domain.bookmark.SetBookmark
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.AudioClip
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.PhotoLicense
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.content.PlacePhoto
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.Entitlements
import app.signkorean.domain.learning.CardState
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.StartSession
import app.signkorean.domain.locale.ResolveAppLanguage
import app.signkorean.domain.place.PlaceSummary
import app.signkorean.domain.upgrade.PendingAction
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeAudioPlayer
import app.signkorean.testing.FakeBookmarkStore
import app.signkorean.testing.FakeCurrentLocale
import app.signkorean.testing.FakeEntitlementCache
import app.signkorean.testing.FakeEntitlementGateway
import app.signkorean.testing.FakeLearningStateStore
import app.signkorean.testing.FakeNetworkMonitor
import app.signkorean.testing.FakePendingActionStore
import app.signkorean.testing.FakePlaceSummaryProvider
import app.signkorean.testing.FakeSessionStore
import app.signkorean.testing.FakeTransactionRunner
import app.signkorean.testing.FixedClock
import app.signkorean.testing.InMemoryContentRepository
import app.signkorean.testing.SequentialIdGenerator
import app.signkorean.ui.text.UiText
import app.signkorean.ui.upgrade.UpgradeFeature
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

/** SC08 장소 목록·SC07 장소 상세 (FR09, PRD §14). */
@OptIn(ExperimentalCoroutinesApi::class)
class PlacesViewModelTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val gukbap = ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market")).copy(audio = AudioClip("audio/t-market-1.m4a", "s", 1, 900))
    private val tteok = ContentFixtures.card("t-market-2", "떡", CardKind.PlaceSign("market"))
    private val restroom = ContentFixtures.basic("b01", "화장실")
    private val photo = PlacePhoto("https://tong.visitkorea.or.kr/a.jpg", PhotoLicense.KoglType3, "한국관광공사", null)
    private val pack = ContentFixtures.pack(
        cards = listOf(gukbap, tteok, restroom),
        places = listOf(
            ContentFixtures.place("gwangjang", areaId = "jongno").copy(photo = photo),
            ContentFixtures.place("namdaemun", areaId = "myeongdong"),
            ContentFixtures.place("example-market", areaId = "myeongdong", name = "Example market", isExample = true),
        ),
        areas = listOf(ContentFixtures.area("myeongdong"), ContentFixtures.area("jongno")),
    )
    private val content = InMemoryContentRepository(pack)
    private val summaries = FakePlaceSummaryProvider(mapOf(PlaceId("gwangjang") to PlaceSummary(4.5, 120, "Great tteok.")))
    private val gateway = FakeEntitlementGateway()
    private val cache = FakeEntitlementCache()
    private val entitlements = Entitlements(gateway, cache, FakeNetworkMonitor())
    private val language = ResolveAppLanguage(FakeCurrentLocale(selected = LocaleTag("en")))
    private val player = FakeAudioPlayer()
    private val states = FakeLearningStateStore()
    private val sessions = FakeSessionStore()
    private val bookmarks = FakeBookmarkStore()
    private val pending = FakePendingActionStore()
    private val setBookmark = SetBookmark(bookmarks, FakeTransactionRunner(bookmarks), entitlements, clock)
    private val start = StartSession(sessions, states, FakeTransactionRunner(states, sessions), SequentialIdGenerator("s"))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun list(card: String = "t-market-1") = PlacesViewModel(CardId(card), content, summaries, language, entitlements, player)
    private fun detail(place: String = "gwangjang") =
        PlaceDetailViewModel(PlaceId(place), content, states, bookmarks, setBookmark, pending, entitlements, start, player)

    // ---------- SC08 ----------

    @Test
    fun `SC08 간판 띠와 지역별 장소, 요약은 장소에 붙는다`() = runTest {
        val s = list().state.value
        assertEquals(CardId("t-market-1"), s.card?.id)
        assertEquals(listOf("myeongdong", "jongno"), s.groups.map { it.area.id.value })
        assertEquals(3, s.placeCount)
        assertEquals(PlaceSummary(4.5, 120, "Great tteok."), s.summaryFor(PlaceId("gwangjang")))
        assertNull("요약이 없는 장소는 비워 둔다(지어내지 않는다)", s.summaryFor(PlaceId("namdaemun")))
        assertEquals(listOf(CardId("t-market-1") to LocaleTag("en")), summaries.requests)
    }

    @Test
    fun `요약을 못 받아도 장소는 보이고 다시 시도할 수 있다`() = runTest {
        summaries.fail = true
        val vm = list()
        assertEquals(SummaryState.Unavailable, vm.state.value.summary)
        assertEquals(3, vm.state.value.placeCount)
        summaries.fail = false
        vm.onAction(PlacesAction.RetrySummaries)
        assertTrue(vm.state.value.summary is SummaryState.Loaded)
    }

    @Test
    fun `없는 카드나 Basics 카드는 빈 상태`() = runTest {
        assertTrue(list("nope").state.value.missing)
        assertEquals(0, list("b01").state.value.placeCount)
    }

    @Test
    fun `무료면 Route·Add 는 SC21, 장소를 누르면 SC07`() = runTest {
        val vm = list()
        assertFalse(vm.state.value.paid)
        vm.onAction(PlacesAction.Upgrade(UpgradeFeature.Route))
        assertEquals(PlacesEffect.Upgrade(UpgradeFeature.Route), vm.effects.tryReceive().getOrNull())
        vm.onAction(PlacesAction.OpenPlace(PlaceId("gwangjang")))
        assertEquals(PlacesEffect.OpenPlace(PlaceId("gwangjang")), vm.effects.tryReceive().getOrNull())
    }

    @Test
    fun `결제 뒤 돌아오면 잠금이 풀린 상태로 보인다`() = runTest {
        val vm = list()
        cache.stored = Entitlement.Paid
        gateway.owned = listOf(FakeEntitlementGateway.purchased("t"))
        entitlements.refresh()
        assertTrue(vm.state.value.paid)
    }

    @Test
    fun `발음 듣기`() = runTest {
        list().onAction(PlacesAction.PlayAudio)
        assertEquals(listOf(gukbap.audio), player.played)
    }

    // ---------- SC07 ----------

    @Test
    fun `SC07 장소의 간판을 칩으로 고르고 상태를 보인다`() = runTest {
        states.save(CardState(CardId("t-market-2"), CardStatus.Learning, clock.now()))
        val vm = detail()
        val s = vm.state.value
        assertEquals("gwangjang", s.place?.id?.value)
        assertEquals(listOf("t-market-1", "t-market-2"), s.signs.map { it.id.value })
        assertEquals(CardId("t-market-1"), s.selected?.id)
        vm.onAction(PlaceDetailAction.Select(CardId("t-market-2")))
        assertEquals(CardStatus.Learning, vm.state.value.selectedStatus)
    }

    @Test
    fun `SC07 Example 장소는 예시라고 알린다 (C-09)`() = runTest {
        assertTrue(detail("example-market").state.value.place!!.isExample)
    }

    @Test
    fun `SC07 Save sign, 무료 한도면 보류하고 SC21`() = runTest {
        (1..5).forEach { bookmarks.add(Bookmark(CardId("x$it"), clock.now())) }
        val vm = detail()
        vm.onAction(PlaceDetailAction.ToggleBookmark)
        assertEquals(PlaceDetailEffect.Upgrade(UpgradeFeature.BookmarkLimit), vm.effects.tryReceive().getOrNull())
        assertEquals(PendingAction.SaveBookmark(CardId("t-market-1")), pending.peek())
        assertFalse(vm.state.value.selectedSaved)
        assertEquals(UiText.Res(R.string.bookmark_limit, listOf(5, 5)), vm.state.value.message)
    }

    @Test
    fun `SC07 Save sign 은 저장·해제`() = runTest {
        val vm = detail()
        vm.onAction(PlaceDetailAction.ToggleBookmark)
        assertTrue(vm.state.value.selectedSaved)
        assertTrue(bookmarks.contains(CardId("t-market-1")))
        vm.onAction(PlaceDetailAction.ToggleBookmark)
        assertFalse(bookmarks.contains(CardId("t-market-1")))
    }

    @Test
    fun `SC07 Practice these signs 는 장소 출처 세션`() = runTest {
        val vm = detail()
        vm.onAction(PlaceDetailAction.Practice)
        assertEquals(PlaceDetailEffect.OpenLearn, vm.effects.tryReceive().getOrNull())
        assertEquals(SessionSource.Place("gwangjang"), sessions.current()?.source)
    }

    @Test
    fun `SC07 다른 세션이 진행 중이면 교체를 묻는다`() = runTest {
        start(SessionSource.Basics, app.signkorean.domain.learning.Mode.All, listOf(restroom), replaceUnfinished = false)
        val vm = detail()
        vm.onAction(PlaceDetailAction.Practice)
        assertTrue(vm.state.value.confirmReplace)
        vm.onAction(PlaceDetailAction.ConfirmReplace)
        assertEquals(PlaceDetailEffect.OpenLearn, vm.effects.tryReceive().getOrNull())
        assertEquals(SessionSource.Place("gwangjang"), sessions.current()?.source)
    }

    @Test
    fun `SC07 없는 장소는 빈 상태`() = runTest {
        assertTrue(detail("nope").state.value.missing)
    }
}
