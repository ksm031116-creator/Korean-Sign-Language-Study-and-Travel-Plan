package app.signkorean.ui.explore

import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.learning.CardState
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.JudgeCard
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.ResumeSession
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.StartOutcome
import app.signkorean.domain.learning.StartSession
import app.signkorean.domain.learning.Verdict
import app.signkorean.testing.ContentFixtures
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelsTest {

    private val now = Instant.parse("2026-10-21T09:00:00Z")
    private val gukbap = ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market"))
    private val tteok = ContentFixtures.card("t-market-2", "떡볶이", CardKind.PlaceSign("market"))
    private val restroom = ContentFixtures.basic("b01", "화장실")
    private val exit = ContentFixtures.basic("b02", "출구", group = "transit")
    private val pack = ContentFixtures.pack(
        cards = listOf(gukbap, tteok, restroom, exit),
        places = listOf(ContentFixtures.place("gwangjang", areaId = "myeongdong"), ContentFixtures.place("other", areaId = "myeongdong")),
        areas = listOf(ContentFixtures.area("myeongdong"), ContentFixtures.area("hongdae").copy(name = ContentFixtures.en("Hongdae"), koName = "홍대")),
    )
    private val states = FakeLearningStateStore()
    private val sessions = FakeSessionStore()
    private val tx = FakeTransactionRunner(states, sessions)
    private val start = StartSession(sessions, states, tx, SequentialIdGenerator("s"))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun explore() = ExploreViewModel(InMemoryContentRepository(pack), states, ResumeSession(sessions))
    private fun collection(source: SessionSource) = CollectionViewModel(source, InMemoryContentRepository(pack), states, start)

    @Test
    fun `Explore 는 Basics 와 지역별 장소 수·카드 수·Known x-y 를 보인다`() = runTest {
        states.save(CardState(CardId("t-market-1"), CardStatus.Known, now))
        val s = explore().state.value
        assertEquals(2, s.basics.total)
        val myeongdong = s.areas.single { it.id == AreaId("myeongdong") }
        assertEquals(2, myeongdong.placeCount)
        assertEquals(2, myeongdong.progress.total)
        assertEquals(1, myeongdong.progress.known)
        assertEquals(0, s.areas.single { it.id == AreaId("hongdae") }.progress.total)
    }

    @Test
    fun `검색은 지역 이름·한국어 이름·간판 단어로 찾고 지우면 전부 보인다`() = runTest {
        val vm = explore()
        vm.onAction(ExploreAction.Search(" HONG "))
        assertEquals(listOf(AreaId("hongdae")), vm.state.value.areas.map { it.id })
        vm.onAction(ExploreAction.Search("떡볶이"))
        assertEquals(listOf(AreaId("myeongdong")), vm.state.value.areas.map { it.id })
        vm.onAction(ExploreAction.Search(""))
        assertEquals(2, vm.state.value.areas.size)
    }

    @Test
    fun `끝나지 않은 세션이 있으면 Resume 배너, 끝나면 사라진다`() = runTest {
        assertNull(explore().state.value.resume)
        val s = (start(SessionSource.Basics, Mode.All, listOf(restroom), false) as StartOutcome.Started).session
        val vm = explore()
        assertEquals(SessionSource.Basics, vm.state.value.resume?.source)
        JudgeCard(sessions, states, tx, FixedClock(now))(s.id, s.decisionSeq, Verdict.Know)
        vm.onAction(ExploreAction.Refresh)
        assertNull(vm.state.value.resume)
    }

    @Test
    fun `C-03 G-06 U-08 Basics 상세는 장소가 없고 바로 학습을 시작한다 (AT18)`() = runTest {
        val vm = collection(SessionSource.Basics)
        val s = vm.state.value
        assertTrue(s.isBasics)
        assertEquals(emptyList<PlaceUi>(), s.places)
        assertEquals(listOf("restroom", "transit"), s.groups.map { it.group?.id })
        vm.onAction(CollectionAction.Start)
        assertEquals(CollectionEffect.OpenLearn, vm.effects.tryReceive().getOrNull())
        assertEquals(CardId("b01"), sessions.current()!!.current)
    }

    @Test
    fun `지역 상세는 등록 장소와 그 장소들의 간판을 보인다`() = runTest {
        val s = collection(SessionSource.Area(AreaId("myeongdong"))).state.value
        assertFalse(s.isBasics)
        assertEquals(listOf("gwangjang", "other"), s.places.map { it.place.id.value })
        assertEquals(listOf("국밥", "떡볶이"), s.groups.flatMap { it.words }.map { it.koText })
    }

    @Test
    fun `단어 칩에서 시작하면 그 카드가 먼저 나온다`() = runTest {
        val vm = collection(SessionSource.Basics)
        vm.onAction(CollectionAction.StartFrom(CardId("b02")))
        assertEquals(CardId("b02"), sessions.current()!!.current)
    }

    @Test
    fun `Review 는 learning 카드가 있을 때만 고를 수 있다`() = runTest {
        val vm = collection(SessionSource.Basics)
        vm.onAction(CollectionAction.SetMode(Mode.Review))
        assertEquals(Mode.All, vm.state.value.mode)
        states.save(CardState(CardId("b02"), CardStatus.Learning, now))
        val vm2 = collection(SessionSource.Basics)
        vm2.onAction(CollectionAction.SetMode(Mode.Review))
        vm2.onAction(CollectionAction.Start)
        assertEquals(listOf(CardId("b02")), sessions.current()!!.queue.map { it.card })
    }

    @Test
    fun `L-16 다른 세션 중이면 확인을 묻고, Keep 은 그대로, End 는 새 세션`() = runTest {
        val area = collection(SessionSource.Area(AreaId("myeongdong")))
        area.onAction(CollectionAction.Start)
        area.effects.tryReceive()
        states.save(CardState(CardId("t-market-1"), CardStatus.Known, now))
        val before = sessions.current()!!

        val basics = collection(SessionSource.Basics)
        basics.onAction(CollectionAction.Start)
        assertTrue(basics.state.value.confirmReplace)
        basics.onAction(CollectionAction.KeepSession)
        assertFalse(basics.state.value.confirmReplace)
        assertEquals(before, sessions.current())

        basics.onAction(CollectionAction.Start)
        basics.onAction(CollectionAction.ConfirmReplace)
        assertEquals(CollectionEffect.OpenLearn, basics.effects.tryReceive().getOrNull())
        assertEquals(SessionSource.Basics, sessions.current()!!.source)
        assertEquals("카드 상태는 유지", CardStatus.Known, states.all()[CardId("t-market-1")])
    }

    @Test
    fun `없는 지역이면 빈 상태`() = runTest {
        assertTrue(collection(SessionSource.Area(AreaId("nowhere"))).state.value.missing)
    }

    @Test
    fun `학습에서 돌아와 새로 읽으면 진행률과 칩 색이 바뀐다 (리뷰 HIGH-2)`() = runTest {
        val vm = collection(SessionSource.Basics)
        assertEquals(0, vm.state.value.progress.known)
        states.save(CardState(CardId("b01"), CardStatus.Known, now))
        vm.onAction(CollectionAction.Refresh)
        assertEquals(1, vm.state.value.progress.known)
        assertEquals(CardStatus.Known, vm.state.value.groups.flatMap { it.words }.single { it.id == CardId("b01") }.status)
    }

    @Test
    fun `같은 출처 세션 중에 단어 칩을 누르면 이어 하지 않고 교체를 묻는다`() = runTest {
        val vm = collection(SessionSource.Basics)
        vm.onAction(CollectionAction.Start)
        vm.effects.tryReceive()
        vm.onAction(CollectionAction.StartFrom(CardId("b02")))
        assertTrue(vm.state.value.confirmReplace)
        vm.onAction(CollectionAction.ConfirmReplace)
        assertEquals(CardId("b02"), sessions.current()!!.current)
    }

    @Test
    fun `검색어가 기초 표지판 단어면 Basics 를 보인다`() = runTest {
        val vm = explore()
        vm.onAction(ExploreAction.Search("화장실"))
        assertTrue(vm.state.value.basicsMatches)
        vm.onAction(ExploreAction.Search("떡볶이"))
        assertFalse(vm.state.value.basicsMatches)
    }

    @Test
    fun `읽기에 실패하면 사라진 콘텐츠가 아니라 다시 시도할 수 있는 상태`() = runTest {
        val failing = object : app.signkorean.domain.content.ContentRepository {
            override suspend fun load(): app.signkorean.domain.content.ContentPack = throw java.io.IOException("x")
        }
        val s = CollectionViewModel(SessionSource.Basics, failing, states, start).state.value
        assertTrue(s.failed)
        assertFalse(s.missing)
    }
}
