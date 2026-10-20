package app.signkorean.ui.saved

import app.signkorean.domain.bookmark.Bookmark
import app.signkorean.domain.bookmark.SetBookmark
import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.AudioClip
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.learning.CardState
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.StartSession
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeAudioPlayer
import app.signkorean.testing.FakeBookmarkStore
import app.signkorean.testing.FakeLearningStateStore
import app.signkorean.testing.FakeSessionStore
import app.signkorean.testing.FakeTransactionRunner
import app.signkorean.testing.FixedClock
import app.signkorean.testing.InMemoryContentRepository
import app.signkorean.testing.SequentialIdGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SavedViewModelTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val gukbap = ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market")).copy(audio = AudioClip("audio/t-market-1.m4a", "s", 1, 900))
    private val restroom = ContentFixtures.basic("b01", "화장실")
    private val exit = ContentFixtures.basic("b02", "출구", group = "transit")
    private val pack = ContentFixtures.pack(
        cards = listOf(gukbap, restroom, exit),
        places = listOf(ContentFixtures.place("gwangjang", areaId = "myeongdong")),
    )
    private val store = FakeBookmarkStore()
    private val states = FakeLearningStateStore()
    private val sessions = FakeSessionStore()
    private var entitlement = Entitlement.Free
    private val player = FakeAudioPlayer()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun save(vararg ids: String) = ids.forEach {
        store.add(Bookmark(CardId(it), clock.now()))
        clock.advance(Duration.ofSeconds(1))
    }

    private fun vm() = SavedViewModel(
        InMemoryContentRepository(pack), store, states,
        SetBookmark(store, FakeTransactionRunner(store), { entitlement }, clock),
        { entitlement },
        StartSession(sessions, states, FakeTransactionRunner(states, sessions), SequentialIdGenerator("s")),
        player,
    )

    @Test
    fun `최신순으로 한국어·분류·연결 장소 수·상태를 보인다`() = runTest {
        save("b01", "t-market-1")
        states.save(CardState(CardId("t-market-1"), CardStatus.Learning, clock.now()))
        val items = vm().state.value.items
        assertEquals(listOf("t-market-1", "b01"), items.map { it.id.value })
        assertEquals(1, items[0].placeCount)
        assertEquals("market", items[0].group?.id)
        assertEquals(CardStatus.Learning, items[0].status)
        assertEquals(0, items[1].placeCount)
    }

    @Test
    fun `B-05 콘텐츠에서 사라진 카드는 Unavailable 로 남고 해제할 수 있다`() = runTest {
        save("gone", "b01")
        val vm = vm()
        assertTrue(vm.state.value.items.first { it.id == CardId("gone") }.unavailable)
        vm.onAction(SavedAction.Unsave(CardId("gone")))
        assertEquals(listOf("b01"), vm.state.value.items.map { it.id.value })
        assertEquals(1, store.count())
    }

    @Test
    fun `필터는 분류·지역·상태로 좁히고 지우면 전부`() = runTest {
        save("b01", "b02", "t-market-1")
        states.save(CardState(CardId("b02"), CardStatus.Known, clock.now()))
        val vm = vm()
        vm.onAction(SavedAction.FilterGroup("transit"))
        assertEquals(listOf("b02"), vm.state.value.items.map { it.id.value })
        vm.onAction(SavedAction.ClearFilters)
        vm.onAction(SavedAction.FilterArea(AreaId("myeongdong")))
        assertEquals(listOf("t-market-1"), vm.state.value.items.map { it.id.value })
        vm.onAction(SavedAction.ClearFilters)
        vm.onAction(SavedAction.FilterStatus(CardStatus.Known))
        assertEquals(listOf("b02"), vm.state.value.items.map { it.id.value })
        vm.onAction(SavedAction.ClearFilters)
        assertEquals(3, vm.state.value.items.size)
    }

    @Test
    fun `무료면 n-5, 유료면 한도 표시 없음`() = runTest {
        save("b01")
        assertEquals(5, vm().state.value.freeLimit)
        entitlement = Entitlement.Paid
        assertNull(vm().state.value.freeLimit)
    }

    @Test
    fun `저장 목록에서 학습을 시작한다 (사라진 카드는 빼고, 고른 카드가 먼저)`() = runTest {
        save("gone", "b01", "b02")
        val vm = vm()
        vm.onAction(SavedAction.StartFrom(CardId("b01")))
        assertEquals(SavedEffect.OpenLearn, vm.effects.tryReceive().getOrNull())
        val s = sessions.current()!!
        assertEquals(SessionSource.Saved, s.source)
        assertEquals(listOf("b01", "b02"), s.queue.map { it.card.value })
    }

    @Test
    fun `음성 재생은 그 카드의 음성을 튼다`() = runTest {
        save("t-market-1")
        val vm = vm()
        vm.onAction(SavedAction.Play(CardId("t-market-1")))
        assertEquals(listOf(gukbap.audio), player.played)
        assertEquals(CardId("t-market-1"), vm.state.value.playing)
    }
}
