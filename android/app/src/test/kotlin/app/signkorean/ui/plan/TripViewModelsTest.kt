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

    // 2026-10-20 15:30 UTC = 10-21 00:30 서울. UTC 로 계산하면 하루 앞이라 시간대를 잘못 쓰면 드러난다(PLN-M9).
    private val clock = FixedClock(Instant.parse("2026-10-20T15:30:00Z"))
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
        editor.create("Sooner", today.plusDays(1), today.plusDays(1))
        editor.addStop(later.id, later.start, "gwangjang")
        val vm = TripListViewModel(store, editor, content, clock)
        assertEquals(listOf("Sooner", "Later"), vm.state.value.trips.map { it.title })
        assertEquals(1, vm.state.value.trips.last().placeCount)
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

    @Test
    fun `SC15 하루 10곳이면 안내`() = runTest {
        val t = trip()
        val many = ContentFixtures.pack(places = (1..11).map { ContentFixtures.place("m$it") })
        val ed = TripEditor(store, SequentialIdGenerator("x"), clock, InMemoryContentRepository(many), { app.signkorean.domain.entitlement.Entitlement.Paid })
        (1..10).forEach { ed.addStop(t.id, t.start, "m$it") }
        val vm = AddToPlanViewModel(PlaceId("m11"), store, ed)
        vm.onAction(AddToPlanAction.Add)
        assertEquals(UiText.Res(R.string.trip_error_day_full, listOf(10)), vm.state.value.message)
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
        // 픽스처의 한국어 이름은 "장소-<id>" 다. 한글 부분만으로 찾는다(PLN-M9).
        vm.onAction(PlacePickerAction.Query("장소"))
        assertEquals(4, vm.state.value.results.size)
        vm.onAction(PlacePickerAction.Query("장소-tong"))
        assertEquals(listOf("tongin"), vm.state.value.results.map { it.place.id.value })
    }

    // ---------- Phase 17 리뷰 수정 ----------

    @Test
    fun `PLN-M1 시트에서 새 여행을 만들고 돌아오면 그 여행이 골라져 있다`() = runTest {
        val a = trip("A")
        val vm = AddToPlanViewModel(PlaceId("gwangjang"), store, editor)
        assertEquals(a.id, vm.state.value.selectedTrip)
        val b = trip("B", days = 1)
        vm.onAction(AddToPlanAction.Refresh)
        assertEquals(b.id, vm.state.value.selectedTrip)
        assertEquals(b.start, vm.state.value.selectedDate)
    }

    @Test
    fun `PLN-M2 샘플을 만들 때 콘텐츠를 못 읽으면 안내하고 빈 샘플을 남기지 않는다`() = runTest {
        val broken = object : app.signkorean.domain.content.ContentRepository {
            override suspend fun load() = throw java.io.IOException("asset")
        }
        val vm = TripListViewModel(store, editor, broken, clock)
        vm.onAction(TripListAction.CreateSample("Sample"))
        assertTrue(store.all().isEmpty())
        assertFalse(vm.state.value.busy)
        assertEquals(UiText.Res(R.string.common_load_failed), vm.state.value.message)
    }

    @Test
    fun `PLN-M3 같은 오류로 다시 저장해도 매번 오류 위치로 간다`() = runTest {
        val vm = form()
        vm.onAction(TripFormAction.Title(""))
        vm.onAction(TripFormAction.Save)
        val first = vm.state.value.errorShownCount
        vm.onAction(TripFormAction.Save)
        assertEquals(first + 1, vm.state.value.errorShownCount)
    }

    @Test
    fun `PLN-M7 무료가 된 뒤 저장하면 날짜 오류가 아니라 SC21`() = runTest {
        var paid = app.signkorean.domain.entitlement.Entitlement.Paid
        val ed = TripEditor(store, SequentialIdGenerator("y"), clock, content, { paid })
        val vm = TripFormViewModel(null, store, ed, clock)
        vm.onAction(TripFormAction.Title("A"))
        paid = app.signkorean.domain.entitlement.Entitlement.Free
        vm.onAction(TripFormAction.Save)
        assertEquals(TripFormEffect.Upgrade, vm.effects.tryReceive().getOrNull())
        assertNull(vm.state.value.endError)
    }

    @Test
    fun `PLN-L4 시트를 다시 열면 지난 안내가 남지 않는다`() = runTest {
        val t = trip()
        editor.addStop(t.id, t.start, "gwangjang")
        val vm = AddToPlanViewModel(PlaceId("gwangjang"), store, editor)
        vm.onAction(AddToPlanAction.Add)
        assertTrue(vm.state.value.message != null)
        vm.onAction(AddToPlanAction.Refresh)
        assertNull(vm.state.value.message)
    }

    @Test
    fun `PLN-L10 편집할 여행이 없으면 저장할 수 없다`() = runTest {
        val vm = form(TripId("nope"))
        assertTrue(vm.state.value.missing)
        vm.onAction(TripFormAction.Save)
        assertTrue(store.all().isEmpty())
    }
}
