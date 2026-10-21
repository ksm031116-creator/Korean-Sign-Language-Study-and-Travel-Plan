package app.signkorean.ui.plan

import app.signkorean.R
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.CardId
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.learning.CardState
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.StartSession
import app.signkorean.domain.trip.Trip
import app.signkorean.domain.trip.TripEditor
import app.signkorean.domain.trip.TripResult
import app.signkorean.domain.trip.TripStudy
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeLearningStateStore
import app.signkorean.testing.FakeSessionStore
import app.signkorean.testing.FakeTransactionRunner
import app.signkorean.testing.FakeTripStore
import app.signkorean.testing.FixedClock
import app.signkorean.testing.InMemoryContentRepository
import app.signkorean.testing.SequentialIdGenerator
import app.signkorean.ui.text.UiText
import app.signkorean.ui.upgrade.UpgradeFeature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
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
import java.time.LocalDate

/** SC13 Study this day (FR21, UI05). T-13~15 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayStudyViewModelTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val pack = ContentFixtures.pack(
        cards = listOf(
            ContentFixtures.card("m1", "국밥", CardKind.PlaceSign("market")),
            ContentFixtures.card("m2", "떡", CardKind.PlaceSign("market")),
            ContentFixtures.card("c1", "커피", CardKind.PlaceSign("cafe")),
        ),
        places = listOf(
            ContentFixtures.place("marketA", typeId = "market"),
            ContentFixtures.place("marketB", typeId = "market"),
            ContentFixtures.place("cafe", typeId = "cafe"),
            ContentFixtures.place("palace", typeId = "palace"),
        ),
    )
    private val content = InMemoryContentRepository(pack)
    private val trips = FakeTripStore()
    private var entitlement = Entitlement.Paid
    private val editor = TripEditor(trips, SequentialIdGenerator("id"), clock, content, { entitlement })
    private val states = FakeLearningStateStore()
    private val sessions = FakeSessionStore()
    private val start = StartSession(sessions, states, FakeTransactionRunner(states, sessions), SequentialIdGenerator("s"))
    private val d1 = LocalDate.of(2026, 11, 3)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun trip(vararg places: String): Trip {
        val t = (editor.create("Autumn", d1, d1) as TripResult.Ok).trip
        places.forEach { editor.addStop(t.id, d1, it) }
        return trips.get(t.id)!!
    }

    private fun vm(t: Trip) = DayStudyViewModel(t.id, d1, trips, content, states, start, { entitlement })
    private fun <T> ReceiveChannel<T>.drain(): List<T> = buildList { while (true) add(tryReceive().getOrNull() ?: break) }

    @Test
    fun `T-13 같은 카드는 한 번만, 방문 순서대로 세션에 담긴다`() = runTest {
        val t = trip("cafe", "marketA", "marketB")
        val vm = vm(t)
        assertEquals(3, vm.state.value.cardCount)
        vm.onAction(DayStudyAction.Start)
        assertEquals(listOf<DayStudyEffect>(DayStudyEffect.OpenLearn), vm.effects.drain())
        val session = sessions.current()!!
        assertEquals(listOf("c1", "m1", "m2"), session.cardIds.map { it.value })
        assertEquals(TripStudy.source(t.id, d1), session.source)
    }

    @Test
    fun `T-14 진행 중 세션은 일정이 바뀌어도 그대로, 다음 세션부터 반영`() = runTest {
        val t = trip("cafe", "marketA")
        val vm = vm(t)
        vm.onAction(DayStudyAction.Start)
        val before = sessions.current()!!.cardIds
        editor.removeStop(t.id, d1, trips.get(t.id)!!.day(d1).stops.first().id)
        assertEquals(before, sessions.current()!!.cardIds)
        // 새 세션: 같은 출처라도 사용자가 교체를 확인하면 바뀐 일정의 카드로 만든다.
        vm.onAction(DayStudyAction.Refresh)
        assertEquals(2, vm.state.value.cardCount)
        vm.onAction(DayStudyAction.Start) // 같은 출처의 진행 중 세션은 이어 간다
        assertEquals(before, sessions.current()!!.cardIds)
    }

    @Test
    fun `T-15 그날 장소에 카드가 없으면 No study cards, 세션을 만들지 않는다`() = runTest {
        val t = trip("palace")
        val vm = vm(t)
        assertTrue(vm.state.value.noCards)
        vm.onAction(DayStudyAction.Start)
        assertNull(sessions.current())
        assertTrue(vm.effects.drain().isEmpty())
    }

    @Test
    fun `Review 는 learning 카드만, 없으면 고를 수 없다`() = runTest {
        val t = trip("cafe", "marketA")
        val vm = vm(t)
        assertEquals(0, vm.state.value.learningCount)
        vm.onAction(DayStudyAction.SetMode(Mode.Review))
        assertEquals(Mode.All, vm.state.value.mode)
        states.save(CardState(CardId("m2"), CardStatus.Learning, clock.now()))
        vm.onAction(DayStudyAction.Refresh)
        assertEquals(1, vm.state.value.learningCount)
        vm.onAction(DayStudyAction.SetMode(Mode.Review))
        vm.onAction(DayStudyAction.Start)
        // cardIds 는 출처의 카드 전부, 출제 대기열은 learning 만(FR05).
        assertEquals(listOf("m2"), sessions.current()!!.queue.map { it.card.value })
    }

    @Test
    fun `다른 출처의 끝나지 않은 세션이 있으면 교체를 묻고, 확인하면 바꾼다`() = runTest {
        start(SessionSource.Basics, Mode.All, listOf(ContentFixtures.basic("b1")), replaceUnfinished = false)
        val t = trip("cafe")
        val vm = vm(t)
        vm.onAction(DayStudyAction.Start)
        assertTrue(vm.state.value.confirmReplace)
        vm.onAction(DayStudyAction.KeepSession)
        assertFalse(vm.state.value.confirmReplace)
        assertEquals(SessionSource.Basics, sessions.current()!!.source)
        vm.onAction(DayStudyAction.Start)
        vm.onAction(DayStudyAction.ConfirmReplace)
        assertEquals(TripStudy.source(t.id, d1), sessions.current()!!.source)
        assertEquals(listOf<DayStudyEffect>(DayStudyEffect.OpenLearn), vm.effects.drain())
    }

    @Test
    fun `무료면 잠금 - 시작하면 SC21, 세션을 만들지 않는다`() = runTest {
        val t = trip("cafe")
        entitlement = Entitlement.Free
        val vm = vm(t)
        assertTrue(vm.state.value.locked)
        vm.onAction(DayStudyAction.Start)
        assertNull(sessions.current())
        assertEquals(listOf<DayStudyEffect>(DayStudyEffect.Upgrade(UpgradeFeature.Plan)), vm.effects.drain())
    }

    @Test
    fun `저장 실패는 안내`() = runTest {
        val t = trip("cafe")
        val vm = vm(t)
        sessions.failNextSave = true
        vm.onAction(DayStudyAction.Start)
        assertEquals(UiText.Res(R.string.common_save_failed), vm.state.value.message)
        assertFalse(vm.state.value.busy)
    }

    @Test
    fun `결과 화면에서 다시 하면 그날의 지금 일정 카드로 새 세션(T-14)`() = runTest {
        val t = trip("cafe")
        vm(t).onAction(DayStudyAction.Start)
        editor.addStop(t.id, d1, "marketA")
        val result = app.signkorean.ui.learn.ResultViewModel(content, sessions, start, app.signkorean.domain.trip.StudyCards(trips), { entitlement })
        result.onAction(app.signkorean.ui.learn.ResultAction.PracticeAll)
        assertEquals(listOf("c1", "m1", "m2"), sessions.current()!!.cardIds.map { it.value })
    }

    // ---------- Phase 20 D1 리뷰 수정 ----------

    @Test
    fun `STY-M1 일정을 바꾼 직후 다시 읽기 전에 시작해도 바뀐 일정의 카드로 만든다(T-14)`() = runTest {
        val t = trip("cafe", "marketA")
        val vm = vm(t)
        editor.removeStop(t.id, d1, trips.get(t.id)!!.day(d1).stops.first().id) // Refresh 없이
        vm.onAction(DayStudyAction.Start)
        assertEquals(listOf("m1", "m2"), sessions.current()!!.cardIds.map { it.value })
    }

    @Test
    fun `STY-M2 읽기 실패는 카드 없음이 아니라 다시 시도`() = runTest {
        val t = trip("cafe")
        trips.failNextGet = true
        val vm = vm(t)
        assertTrue(vm.state.value.loadFailed)
        assertFalse(vm.state.value.noCards)
        vm.onAction(DayStudyAction.Refresh)
        assertFalse(vm.state.value.loadFailed)
        assertEquals(1, vm.state.value.cardCount)
    }

    @Test
    fun `STY-L1 무료가 된 뒤 결과 화면에서 일정 학습을 다시 열 수 없다`() = runTest {
        val t = trip("cafe")
        vm(t).onAction(DayStudyAction.Start)
        val before = sessions.current()!!.id
        entitlement = Entitlement.Free
        val result = app.signkorean.ui.learn.ResultViewModel(content, sessions, start, app.signkorean.domain.trip.StudyCards(trips), { entitlement })
        result.onAction(app.signkorean.ui.learn.ResultAction.PracticeAll)
        assertEquals(before, sessions.current()!!.id)
        assertEquals(listOf<app.signkorean.ui.learn.ResultEffect>(app.signkorean.ui.learn.ResultEffect.Upgrade(UpgradeFeature.Plan)), result.effects.drain())
    }
}
