package app.signkorean.ui.plan

import app.signkorean.R
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.trip.TripEditor
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.trip.TripResult
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeTripStore
import app.signkorean.testing.FixedClock
import app.signkorean.testing.InMemoryContentRepository
import app.signkorean.testing.SequentialIdGenerator
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
import java.time.LocalDate

/** SC11 여행 목록·SC12 폼·SC15 추가 시트·SC14 장소 선택 (Phase 17). U-14·15 */
@OptIn(ExperimentalCoroutinesApi::class)
class TripViewModelsTest {

    // 2026-10-21 09:00 UTC = 18:00 서울
    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val today = LocalDate.of(2026, 10, 21)
    private val pack = ContentFixtures.pack(
        cards = listOf(ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market"))),
        places = listOf(
            ContentFixtures.place("gwangjang", name = "Gwangjang Market"),
            ContentFixtures.place("namdaemun", name = "Namdaemun Market"),
            ContentFixtures.place("tongin", name = "Tongin Market"),
            ContentFixtures.place("cheonggye", name = "Cheonggyecheon"),
        ),
    )
    private val content = InMemoryContentRepository(pack)
    private val store = FakeTripStore()
    private val editor = TripEditor(store, SequentialIdGenerator("id"), clock, content, { app.signkorean.domain.entitlement.Entitlement.Paid })

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun trip(title: String = "Autumn", days: Long = 2) =
        (editor.create(title, today.plusDays(10), today.plusDays(10 + days - 1)) as TripResult.Ok).trip

    // ---------- SC11 ----------

    @Test
    fun `SC11 여행 목록 - 시작일 순, 기간·장소 수`() = runTest {
        val later = trip("Later")
        editor.addStop(later.id, later.start, "gwangjang")
        val vm = TripListViewModel(store, editor, content, clock)
        assertEquals(listOf("Later"), vm.state.value.trips.map { it.title })
        assertEquals(1, vm.state.value.trips.single().placeCount)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun `SC11 샘플 여행 만들기는 오늘부터 하루, 등록 장소 세 곳`() = runTest {
        val vm = TripListViewModel(store, editor, content, clock)
        vm.onAction(TripListAction.CreateSample("Sample day in Seoul"))
        val made = store.all().single()
        assertEquals(today, made.start)
        assertEquals(3, made.placeCount)
        assertEquals(TripListEffect.OpenTrip(made.id), vm.effects.tryReceive().getOrNull())
    }

    // ---------- SC12 ----------

    private fun form(id: TripId? = null) = TripFormViewModel(id, store, editor, clock)

    @Test
    fun `SC12 새 여행 기본값 - 오늘부터 3일`() = runTest {
        val s = form().state.value
        assertEquals(today, s.start)
        assertEquals(today.plusDays(2), s.end)
        assertFalse(s.dirty)
    }

    @Test
    fun `U-15 앞뒤 공백은 다듬어 저장하고, 빈 제목은 필드 아래 오류와 알림`() = runTest {
        val vm = form()
        vm.onAction(TripFormAction.Title("   "))
        vm.onAction(TripFormAction.Save)
        assertEquals(UiText.Res(R.string.trip_error_title), vm.state.value.titleError)
        assertEquals(TripField.Title, vm.state.value.focusError)
        assertTrue(store.all().isEmpty())

        vm.onAction(TripFormAction.Title("  Autumn in Seoul  "))
        assertNull("고치면 오류가 사라진다", vm.state.value.titleError)
        vm.onAction(TripFormAction.Save)
        val saved = store.all().single()
        assertEquals("Autumn in Seoul", saved.title)
        assertEquals(TripFormEffect.Saved(saved.id, created = true), vm.effects.tryReceive().getOrNull())
    }

    @Test
    fun `SC12 종료일이 앞이거나 15일이면 종료일 아래 오류`() = runTest {
        val vm = form()
        vm.onAction(TripFormAction.Title("A"))
        vm.onAction(TripFormAction.End(today.minusDays(1)))
        vm.onAction(TripFormAction.Save)
        assertEquals(UiText.Res(R.string.trip_error_end_before_start), vm.state.value.endError)
        assertEquals(TripField.End, vm.state.value.focusError)
        vm.onAction(TripFormAction.End(today.plusDays(14)))
        vm.onAction(TripFormAction.Save)
        assertEquals(UiText.Res(R.string.trip_error_too_long, listOf(14)), vm.state.value.endError)
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `U-14 편집 중 Back 은 이탈 확인, 취소하면 입력이 남는다`() = runTest {
        val vm = form()
        vm.onAction(TripFormAction.Title("Half typed"))
        vm.onAction(TripFormAction.Back)
        assertTrue(vm.state.value.confirmDiscard)
        assertNull(vm.effects.tryReceive().getOrNull())
        vm.onAction(TripFormAction.KeepEditing)
        assertFalse(vm.state.value.confirmDiscard)
        assertEquals("Half typed", vm.state.value.title)
        vm.onAction(TripFormAction.Back)
        vm.onAction(TripFormAction.ConfirmDiscard)
        assertEquals(TripFormEffect.Close, vm.effects.tryReceive().getOrNull())
    }

    @Test
    fun `바꾼 게 없으면 Back 은 바로 닫는다`() = runTest {
        val vm = form()
        vm.onAction(TripFormAction.Back)
        assertEquals(TripFormEffect.Close, vm.effects.tryReceive().getOrNull())
    }

    @Test
    fun `SC12 편집 - 기간을 줄여 일정이 밖이 되면 막고 날짜를 알린다(AT11)`() = runTest {
        val t = trip(days = 3)
        editor.addStop(t.id, t.end, "gwangjang")
        val vm = form(t.id)
        assertEquals("Autumn", vm.state.value.title)
        vm.onAction(TripFormAction.End(t.end.minusDays(1)))
        vm.onAction(TripFormAction.Save)
        assertEquals(UiText.Res(R.string.trip_error_outside, listOf(1)), vm.state.value.endError)
        assertEquals(t.end, store.get(t.id)!!.end)
    }

    @Test
    fun `SC12 여행 삭제는 확인 뒤`() = runTest {
        val t = trip()
        val vm = form(t.id)
        vm.onAction(TripFormAction.Delete)
        assertTrue(vm.state.value.confirmDelete)
        assertTrue(store.get(t.id) != null)
        vm.onAction(TripFormAction.ConfirmDelete)
        assertNull(store.get(t.id))
        assertEquals(TripFormEffect.Deleted, vm.effects.tryReceive().getOrNull())
    }

    // ---------- SC15 ----------

    @Test
    fun `SC15 여행이 없으면 새 여행 만들기를 권하고, 돌아오면 다시 읽는다`() = runTest {
        val vm = AddToPlanViewModel(PlaceId("gwangjang"), store, editor)
        assertTrue(vm.state.value.noTrips)
        val t = trip()
        vm.onAction(AddToPlanAction.Refresh)
        assertEquals(t.id, vm.state.value.selectedTrip)
        assertEquals(t.start, vm.state.value.selectedDate)
    }

    @Test
    fun `SC15 고른 날에 넣고, 같은 날 중복과 10곳은 안내`() = runTest {
        val t = trip()
        val vm = AddToPlanViewModel(PlaceId("gwangjang"), store, editor)
        vm.onAction(AddToPlanAction.Add)
        assertEquals(AddToPlanEffect.Added(t.start), vm.effects.tryReceive().getOrNull())
        assertEquals(1, store.get(t.id)!!.day(t.start).stops.size)
        vm.onAction(AddToPlanAction.Add)
        assertEquals(UiText.Res(R.string.trip_error_duplicate), vm.state.value.message)
        vm.onAction(AddToPlanAction.SelectDate(t.end))
        vm.onAction(AddToPlanAction.Add)
        assertEquals(AddToPlanEffect.Added(t.end), vm.effects.tryReceive().getOrNull())
    }

    // ---------- SC14 ----------

    @Test
    fun `SC14 이름으로 찾고, 이미 넣은 곳은 표시, 넣으면 바로 반영`() = runTest {
        val t = trip()
        editor.addStop(t.id, t.start, "gwangjang")
        val vm = PlacePickerViewModel(t.id, t.start, store, editor, content)
        vm.onAction(PlacePickerAction.Query("market"))
        assertEquals(listOf("gwangjang", "namdaemun", "tongin"), vm.state.value.results.map { it.place.id.value })
        assertTrue(vm.state.value.results.first { it.place.id.value == "gwangjang" }.added)
        vm.onAction(PlacePickerAction.Add(PlaceId("tongin")))
        assertTrue(vm.state.value.results.first { it.place.id.value == "tongin" }.added)
        assertEquals(2, store.get(t.id)!!.day(t.start).stops.size)
    }

    @Test
    fun `SC14 한국어 이름으로도 찾는다`() = runTest {
        val t = trip()
        val vm = PlacePickerViewModel(t.id, t.start, store, editor, content)
        vm.onAction(PlacePickerAction.Query("장소-tong"))
        assertEquals(listOf("tongin"), vm.state.value.results.map { it.place.id.value })
    }
}
