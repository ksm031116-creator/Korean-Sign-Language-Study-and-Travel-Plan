package app.signkorean.ui.map

import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.location.LocationResult
import app.signkorean.domain.location.SeoulArea
import app.signkorean.domain.location.StartLocationProblem
import app.signkorean.domain.trip.Origin
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeLocationProvider
import app.signkorean.testing.InMemoryContentRepository
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

/** SC19 출발지 (FR14, AT08). M-02: 거부·해외·실패면 수동 선택을 유지한다. 위치는 사용자가 고를 때만 읽는다. */
@OptIn(ExperimentalCoroutinesApi::class)
class OriginPickerViewModelTest {

    private val gyeongbok = ContentFixtures.place("gyeongbokgung", name = "Gyeongbokgung Palace").copy(koName = "경복궁")
    private val market = ContentFixtures.place("gwangjang", name = "Gwangjang Market").copy(koName = "광장시장")
    private val content = InMemoryContentRepository(ContentFixtures.pack(places = listOf(gyeongbok, market)))
    private val location = FakeLocationProvider()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = OriginPickerViewModel(content, location)
    private fun <T> ReceiveChannel<T>.drain(): List<T> = buildList { while (true) add(tryReceive().getOrNull() ?: break) }

    @Test
    fun `등록 장소를 영어·한국어 이름으로 찾는다`() = runTest {
        val vm = vm()
        assertEquals(2, vm.state.value.places.size)
        vm.onAction(OriginPickerAction.Query("palace"))
        assertEquals(listOf(gyeongbok.id), vm.state.value.places.map { it.id })
        vm.onAction(OriginPickerAction.Query("광장"))
        assertEquals(listOf(market.id), vm.state.value.places.map { it.id })
    }

    @Test
    fun `등록 장소를 고르면 그 장소가 출발지`() = runTest {
        val vm = vm()
        vm.onAction(OriginPickerAction.PickPlace(market.id))
        assertEquals(listOf<OriginPickerEffect>(OriginPickerEffect.Picked(Origin.AtPlace(market.id))), vm.effects.drain())
    }

    @Test
    fun `추천 출발점 - 서울역`() = runTest {
        val vm = vm()
        assertEquals(SeoulArea.suggestedStarts, vm.state.value.suggestions)
        vm.onAction(OriginPickerAction.PickSuggested("seoul-station", "Seoul Station"))
        val station = SeoulArea.suggestedStarts.first { it.key == "seoul-station" }.coordinate
        assertEquals(listOf<OriginPickerEffect>(OriginPickerEffect.Picked(Origin.AtCoordinate(station, "Seoul Station"))), vm.effects.drain())
    }

    @Test
    fun `현재 위치는 설명을 보이고, 사용자가 계속할 때만 권한을 묻는다`() = runTest {
        val vm = vm()
        vm.onAction(OriginPickerAction.UseCurrentLocation)
        assertTrue(vm.state.value.explaining)
        assertEquals(0, location.calls)
        assertTrue(vm.effects.drain().isEmpty())
        vm.onAction(OriginPickerAction.ConfirmExplain)
        assertFalse(vm.state.value.explaining)
        assertEquals(listOf<OriginPickerEffect>(OriginPickerEffect.RequestPermission), vm.effects.drain())
        assertEquals(0, location.calls)
    }

    @Test
    fun `M-02 권한 거부 - 위치를 읽지 않고 수동 선택을 유지`() = runTest {
        val vm = vm()
        vm.onAction(OriginPickerAction.PermissionResult(granted = false))
        assertEquals(StartLocationProblem.Denied, vm.state.value.problem)
        assertEquals(0, location.calls)
        assertEquals(2, vm.state.value.places.size)
        assertTrue(vm.effects.drain().isEmpty())
    }

    @Test
    fun `M-02 해외 위치와 위치 실패`() = runTest {
        val vm = vm()
        location.result = LocationResult.Found(Coordinate(35.6812, 139.7671))
        vm.onAction(OriginPickerAction.PermissionResult(granted = true))
        assertEquals(StartLocationProblem.OutsideSeoul, vm.state.value.problem)
        location.result = LocationResult.Unavailable
        vm.onAction(OriginPickerAction.PermissionResult(granted = true))
        assertEquals(StartLocationProblem.Failed, vm.state.value.problem)
        assertTrue(vm.effects.drain().isEmpty())
        assertFalse(vm.state.value.locating)
    }

    @Test
    fun `서울 안 현재 위치는 이름 없는 좌표 출발지`() = runTest {
        val vm = vm()
        val here = Coordinate(37.5700, 126.9920)
        location.result = LocationResult.Found(here)
        vm.onAction(OriginPickerAction.PermissionResult(granted = true))
        assertNull(vm.state.value.problem)
        assertEquals(listOf<OriginPickerEffect>(OriginPickerEffect.Picked(Origin.AtCoordinate(here, null))), vm.effects.drain())
    }

    @Test
    fun `ORG-M6 찾는 중에 닫으면 위치 읽기를 멈추고 나중에 저절로 고르지 않는다`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<LocationResult>()
        val slow = object : app.signkorean.domain.location.LocationProvider {
            override suspend fun current(): LocationResult = gate.await()
        }
        val vm = OriginPickerViewModel(content, slow)
        vm.onAction(OriginPickerAction.PermissionResult(granted = true))
        assertTrue(vm.state.value.locating)
        vm.onAction(OriginPickerAction.Reset)
        assertFalse(vm.state.value.locating)
        gate.complete(LocationResult.Found(Coordinate(37.57, 126.99)))
        assertTrue(vm.effects.drain().isEmpty())
    }
}
