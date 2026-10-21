package app.signkorean.ui.plan

import app.signkorean.R
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.route.DayLegs
import app.signkorean.domain.trip.Trip
import app.signkorean.domain.trip.TripDay
import app.signkorean.domain.trip.TripEditor
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.trip.TripResult
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeTripStore
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
import java.time.LocalDate
import java.time.LocalTime

/** SC13 날짜별 일정·SC16 방문 장소 편집·SC17 하루 출발 (FR19·20, Phase 18). T-16, AT11 */
@OptIn(ExperimentalCoroutinesApi::class)
class DayPlanViewModelTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val pack = ContentFixtures.pack(
        cards = listOf(
            ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market")),
            ContentFixtures.card("t-market-2", "떡", CardKind.PlaceSign("market")),
        ),
        places = (1..11).map { ContentFixtures.place("p$it", name = "Place $it") },
    )
    private val content = InMemoryContentRepository(pack)
    private val store = FakeTripStore()
    private var entitlement = Entitlement.Paid
    private val editor = TripEditor(store, SequentialIdGenerator("id"), clock, content, { entitlement })
    private val d1 = LocalDate.of(2026, 11, 3)

    /** 경로 공급자 자리. 테스트마다 구간 초를 정한다. 기본은 "아직 계산 안 됨". */
    private var legs: (TripDay) -> List<Long?> = { day -> List(app.signkorean.domain.route.legCount(day)) { null } }
    /** 구간 수를 그날에 맞춘 고정 초. 길이가 틀려 조용히 "모름"이 되지 않게(DPV-M5). */
    private fun every(seconds: Long): (TripDay) -> List<Long?> = { day -> List(app.signkorean.domain.route.legCount(day)) { seconds } }
    private val dayLegs = DayLegs { _, day -> legs(day) }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun trip(days: Long = 2, places: List<String> = listOf("p1", "p2")): Trip {
        val t = (editor.create("Autumn", d1, d1.plusDays(days - 1)) as TripResult.Ok).trip
        places.forEach { editor.addStop(t.id, d1, it) }
        return store.get(t.id)!!
    }

    private fun vm(id: TripId) = DayPlanViewModel(id, null, store, editor, content, dayLegs, { entitlement })

    @Test
    fun `SC13 첫날의 타임라인 - 이름, 간판 수, 기본 09시 시작과 30분 체류`() = runTest {
        legs = every(600L)
        val t = trip()
        val s = vm(t.id).state.value
        assertEquals(d1, s.date)
        assertEquals(listOf("p1", "p2"), s.rows.map { it.stop.place.value })
        assertEquals(2, s.rows[0].signCount)
        assertEquals(LocalTime.of(9, 0), s.rows[0].timing?.arrive)
        assertEquals(LocalTime.of(9, 40), s.rows[1].timing?.arrive)
        assertEquals(10, s.schedule?.travelMinutes)
        assertEquals(60, s.schedule?.stayMinutes)
        assertFalse(s.readOnly)
    }

    @Test
    fun `FR20 경로를 모르면 Incomplete, 허위 합계 없음`() = runTest {
        val s = vm(trip().id).state.value
        assertTrue(s.schedule!!.incomplete)
        assertNull(s.schedule!!.totalMinutes)
        assertNull(s.rows[1].timing?.arrive)
    }

    @Test
    fun `날짜 탭을 바꾸면 그날 일정`() = runTest {
        val t = trip()
        val vm = vm(t.id)
        vm.onAction(DayPlanAction.SelectDate(d1.plusDays(1)))
        assertEquals(d1.plusDays(1), vm.state.value.date)
        assertTrue(vm.state.value.rows.isEmpty())
    }

    @Test
    fun `T-16 순서를 바꾸면 저장 뒤 반영되고 경로 다시 계산 표시`() = runTest {
        val t = trip()
        val vm = vm(t.id)
        // 장소를 넣은 것만으로도 경로를 계산해야 한다. 순서를 바꾸면 revision 이 올라 이전 경로 응답을 버린다(T-07).
        val before = vm.state.value.day!!.revision
        vm.onAction(DayPlanAction.MoveDown(vm.state.value.rows[0].stop.id))
        assertEquals(listOf("p2", "p1"), vm.state.value.rows.map { it.stop.place.value })
        assertTrue(vm.state.value.needsRecalculation)
        assertEquals(before + 1, vm.state.value.day!!.revision)
        assertEquals(listOf(PlaceId("p2"), PlaceId("p1")), store.get(t.id)!!.day(d1).stops.map { it.place })
    }

    @Test
    fun `UX02 저장이 실패하면 원래 순서를 지키고 안내`() = runTest {
        val t = trip()
        val vm = vm(t.id)
        store.failNextSave = true
        vm.onAction(DayPlanAction.MoveUp(vm.state.value.rows[1].stop.id))
        assertEquals(listOf("p1", "p2"), vm.state.value.rows.map { it.stop.place.value })
        assertEquals(UiText.Res(R.string.common_save_failed), vm.state.value.message)
    }

    @Test
    fun `SC16 체류 시간 - 정수 0~480 만, 틀리면 필드 오류`() = runTest {
        val t = trip()
        val vm = vm(t.id)
        val stop = vm.state.value.rows[0].stop.id
        vm.onAction(DayPlanAction.OpenStop(stop))
        assertEquals(stop, vm.state.value.editing)
        vm.onAction(DayPlanAction.SaveStay(stop, "1.5"))
        assertEquals(UiText.Res(R.string.trip_error_stay, listOf(480)), vm.state.value.stayError)
        vm.onAction(DayPlanAction.SaveStay(stop, " 45 "))
        assertNull(vm.state.value.stayError)
        assertNull("저장하면 시트를 닫는다", vm.state.value.editing)
        assertEquals(45, store.get(t.id)!!.day(d1).stops[0].stayMinutes)
    }

    @Test
    fun `SC16 다른 날로 옮기기 - 10곳인 날은 막는다`() = runTest {
        val t = trip(places = listOf("p1"))
        (1..10).forEach { editor.addStop(t.id, d1.plusDays(1), "p${it + 1}".let { id -> if (id == "p11") "p11" else id }) }
        val vm = vm(t.id)
        val stop = vm.state.value.rows[0].stop.id
        vm.onAction(DayPlanAction.MoveTo(stop, d1.plusDays(1)))
        assertEquals(UiText.Res(R.string.trip_error_day_full, listOf(10)), vm.state.value.message)
        assertEquals(1, store.get(t.id)!!.day(d1).stops.size)
    }

    @Test
    fun `SC16 삭제는 확인 뒤`() = runTest {
        val t = trip()
        val vm = vm(t.id)
        val stop = vm.state.value.rows[0].stop.id
        vm.onAction(DayPlanAction.Remove(stop))
        assertEquals(stop, vm.state.value.confirmRemove)
        assertEquals(2, store.get(t.id)!!.day(d1).stops.size)
        vm.onAction(DayPlanAction.ConfirmRemove)
        assertEquals(1, store.get(t.id)!!.day(d1).stops.size)
        assertNull(vm.state.value.confirmRemove)
    }

    @Test
    fun `SC17 출발 시각을 바꾸면 그날 시각이 모두 따라온다`() = runTest {
        legs = every(600L)
        val t = trip()
        val vm = vm(t.id)
        vm.onAction(DayPlanAction.ApplyStart(LocalTime.of(10, 30), null))
        assertEquals(LocalTime.of(10, 30), vm.state.value.rows[0].timing?.arrive)
        assertEquals(LocalTime.of(11, 10), vm.state.value.rows[1].timing?.arrive)
    }

    @Test
    fun `장소 추가는 SC14 로`() = runTest {
        val t = trip()
        val vm = vm(t.id)
        vm.onAction(DayPlanAction.AddPlace)
        assertEquals(DayPlanEffect.OpenPicker(t.id, d1), vm.effects.tryReceive().getOrNull())
    }

    @Test
    fun `E-12 무료면 읽기 전용, 편집하려 하면 SC21`() = runTest {
        val t = trip()
        entitlement = Entitlement.Free
        val vm = vm(t.id)
        assertTrue(vm.state.value.readOnly)
        assertEquals(2, vm.state.value.rows.size)
        vm.onAction(DayPlanAction.MoveDown(vm.state.value.rows[0].stop.id))
        assertEquals(DayPlanEffect.Upgrade(UpgradeFeature.Plan), vm.effects.tryReceive().getOrNull())
        assertEquals(listOf(PlaceId("p1"), PlaceId("p2")), store.get(t.id)!!.day(d1).stops.map { it.place })
    }

    @Test
    fun `다음 날 도착이면 안내 플래그`() = runTest {
        legs = every(600L)
        val t = trip()
        val vm = vm(t.id)
        vm.onAction(DayPlanAction.ApplyStart(LocalTime.of(23, 40), null))
        assertTrue(vm.state.value.schedule!!.nextDay)
    }

    @Test
    fun `없는 여행은 빈 상태`() = runTest {
        assertTrue(vm(TripId("nope")).state.value.missing)
    }

    @Test
    fun `여는 날짜를 정할 수 있다(SC14 에서 돌아올 때)`() = runTest {
        val t = trip()
        val vm = DayPlanViewModel(t.id, d1.plusDays(1), store, editor, content, dayLegs, { entitlement })
        assertEquals(d1.plusDays(1), vm.state.value.date)
    }

    // ---------- Phase 18 리뷰 수정 ----------

    @Test
    fun `DPV-H1 처음 읽기가 실패해도 여행 없음이 아니라 다시 시도, 다시 읽으면 보인다`() = runTest {
        val t = trip()
        store.failNextGet = true
        val vm = vm(t.id)
        assertTrue(vm.state.value.loadFailed)
        assertFalse(vm.state.value.missing)
        vm.onAction(DayPlanAction.Refresh)
        assertFalse(vm.state.value.loadFailed)
        assertEquals(2, vm.state.value.rows.size)
    }

    @Test
    fun `DPV-M5 구간 매핑 - 출발지가 없으면 첫 장소 앞 구간 없음, 있으면 첫 장소 앞에도 있다`() = runTest {
        legs = { day -> List(app.signkorean.domain.route.legCount(day)) { i -> (i + 1) * 60L } }
        val t = trip(places = listOf("p1", "p2", "p3"))
        val vm = vm(t.id)
        assertEquals(listOf(false, true, true), vm.state.value.rows.map { it.hasLegBefore })
        assertEquals(listOf(null, 1, 2), vm.state.value.rows.map { it.legBefore })
        vm.onAction(DayPlanAction.ApplyStart(LocalTime.of(9, 0), app.signkorean.domain.trip.Origin.AtPlace(PlaceId("p4"))))
        assertEquals(listOf(true, true, true), vm.state.value.rows.map { it.hasLegBefore })
        assertEquals(listOf(1, 2, 3), vm.state.value.rows.map { it.legBefore })
    }

    @Test
    fun `DPV-M5 읽기 전용이면 시트와 확인창이 열리지 않는다(뷰모델에서 막는다)`() = runTest {
        val t = trip()
        entitlement = Entitlement.Free
        val vm = vm(t.id)
        val stop = vm.state.value.rows[0].stop.id
        vm.onAction(DayPlanAction.OpenStop(stop))
        vm.onAction(DayPlanAction.OpenStart)
        vm.onAction(DayPlanAction.Remove(stop))
        assertNull(vm.state.value.editing)
        assertFalse(vm.state.value.startSheet)
        assertNull(vm.state.value.confirmRemove)
        assertNull(vm.state.value.message)
    }

    @Test
    fun `DPV-M1 날짜를 바꾼 뒤 늦게 온 이전 날 결과는 덮어쓰지 않는다`() = runTest {
        val t = trip(days = 2)
        editor.addStop(t.id, d1.plusDays(1), "p5")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var holdDay1 = false
        val slow = DayLegs { _, day -> if (holdDay1 && day.date == d1) gate.await(); List(app.signkorean.domain.route.legCount(day)) { 60L } }
        val vm = DayPlanViewModel(t.id, null, store, editor, content, slow, { entitlement })
        holdDay1 = true
        vm.onAction(DayPlanAction.Refresh) // 첫날 다시 그리기가 경로에서 멈춘다
        vm.onAction(DayPlanAction.SelectDate(d1.plusDays(1)))
        gate.complete(Unit)
        assertEquals(d1.plusDays(1), vm.state.value.date)
        assertEquals(listOf("p5"), vm.state.value.rows.map { it.stop.place.value })
    }

    @Test
    fun `DPV-M6 진행 중 삭제 확인은 창을 닫지 않는다`() = runTest {
        val t = trip()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        var hold = false
        val slow = DayLegs { _, day -> if (hold) gate.await(); List(app.signkorean.domain.route.legCount(day)) { null } }
        val vm = DayPlanViewModel(t.id, null, store, editor, content, slow, { entitlement })
        val first = vm.state.value.rows[0].stop.id
        hold = true
        vm.onAction(DayPlanAction.MoveDown(first)) // 저장 뒤 그리기가 멈춰 busy 가 남는다
        assertTrue(vm.state.value.busy)
        vm.onAction(DayPlanAction.Remove(first))
        vm.onAction(DayPlanAction.ConfirmRemove)
        assertEquals(first, vm.state.value.confirmRemove)
        gate.complete(Unit)
        assertFalse(vm.state.value.busy)
    }
}
