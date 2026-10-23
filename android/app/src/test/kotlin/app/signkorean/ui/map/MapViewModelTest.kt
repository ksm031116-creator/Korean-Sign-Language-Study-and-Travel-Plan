package app.signkorean.ui.map

import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.review.ExternalLinks
import app.signkorean.domain.route.DayRoutePlanner
import app.signkorean.domain.route.RouteFailure
import app.signkorean.domain.route.RouteProvider
import app.signkorean.domain.route.RouteRequest
import app.signkorean.domain.route.RouteResult
import app.signkorean.domain.route.TravelMode
import app.signkorean.domain.trip.Origin
import app.signkorean.domain.trip.Trip
import app.signkorean.domain.trip.TripEditor
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.trip.TripResult
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeRouteProvider
import app.signkorean.testing.FakeTripStore
import app.signkorean.testing.FixedClock
import app.signkorean.testing.InMemoryContentRepository
import app.signkorean.testing.SequentialIdGenerator
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** SC18 지도 (FR13~15, 구현 계획 Phase 19 D2). M-01·03·05, T-07, AT07·12·14 (가짜 공급자로 상태만). */
@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {

    private val clock = FixedClock(Instant.parse("2026-10-21T09:00:00Z"))
    private val places = (1..4).map { ContentFixtures.place("p$it", name = "Place $it").copy(coordinate = Coordinate(37.56 + it * 0.004, 126.97 + it * 0.003)) }
    private val content = InMemoryContentRepository(ContentFixtures.pack(places = places))
    private val store = FakeTripStore()
    private var entitlement = Entitlement.Paid
    private val editor = TripEditor(store, SequentialIdGenerator("id"), clock, content, { entitlement })
    private val provider = FakeRouteProvider()
    private val planner = DayRoutePlanner(provider, content)
    private val d1 = LocalDate.of(2026, 11, 3)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun trip(vararg ids: String = arrayOf("p1", "p2", "p3")): Trip {
        val t = (editor.create("Autumn", d1, d1) as TripResult.Ok).trip
        ids.forEach { editor.addStop(t.id, d1, it) }
        return store.get(t.id)!!
    }

    private fun vm(target: MapTarget, routes: RouteProvider? = null) =
        MapViewModel(target, store, editor, content, routes?.let { DayRoutePlanner(it, content) } ?: planner, { entitlement }, clock)

    private fun <T> ReceiveChannel<T>.drain(): List<T> = buildList { while (true) add(tryReceive().getOrNull() ?: break) }

    @Test
    fun `하루 경로 - 장소마다 번호, 구간마다 공급자 거리·시간, 계산 시각`() = runTest {
        val t = trip()
        val s = vm(MapTarget.Day(t.id, d1)).state.value
        assertEquals(listOf(1, 2, 3), s.points.map { it.number })
        assertEquals(2, s.legs.size)
        assertTrue(s.legs.all { it.route != null && it.failure == null })
        assertEquals(provider.seconds(places[0].coordinate, places[1].coordinate), s.legs[0].route!!.durationSeconds)
        assertEquals(clock.now(), s.calculatedAt)
        assertEquals(0, s.selected)
    }

    @Test
    fun `T-16 모든 구간을 계산하면 일정의 다시 계산 표시가 꺼진다`() = runTest {
        val t = trip()
        assertTrue(t.day(d1).needsRecalculation)
        vm(MapTarget.Day(t.id, d1))
        assertFalse(store.get(t.id)!!.day(d1).needsRecalculation)
    }

    @Test
    fun `M-05 실패한 구간은 이유를 보이고 나머지는 그대로, 다시 계산 표시는 남는다`() = runTest {
        val t = trip()
        provider.failing += (places[1].coordinate to places[2].coordinate)
        val s = vm(MapTarget.Day(t.id, d1)).state.value
        assertNotNull(s.legs[0].route)
        assertNull(s.legs[1].route)
        assertEquals(RouteFailure.NoRoute, s.legs[1].failure)
        assertTrue(store.get(t.id)!!.day(d1).needsRecalculation)
    }

    @Test
    fun `다시 조회하면 실패했던 구간을 다시 묻는다`() = runTest {
        val t = trip()
        provider.failAll = RouteFailure.Network
        val vm = vm(MapTarget.Day(t.id, d1))
        assertEquals(RouteFailure.Network, vm.state.value.legs[0].failure)
        provider.failAll = null
        vm.onAction(MapAction.Refresh)
        assertTrue(vm.state.value.legs.all { it.route != null })
    }

    @Test
    fun `출발지가 있으면 0번 점이 출발지이고 첫 구간은 출발지에서 시작`() = runTest {
        val t = trip("p1", "p2")
        val station = Coordinate(37.5547, 126.9707)
        editor.setStart(t.id, d1, LocalTime.of(9, 0), Origin.AtCoordinate(station, "Seoul Station"))
        val s = vm(MapTarget.Day(t.id, d1)).state.value
        assertEquals(listOf(null, 1, 2), s.points.map { it.number })
        assertEquals(station, s.points[0].coordinate)
        assertEquals(2, s.legs.size)
        assertEquals(station, provider.requests.first().origin)
    }

    @Test
    fun `T-07 계산 중 일정이 바뀌면 이전 결과를 버리고 새 일정으로 다시 계산한다`() = runTest {
        val t = trip("p1", "p2")
        var edited = false
        val racing = object : RouteProvider {
            override suspend fun route(request: RouteRequest): RouteResult {
                if (!edited) {
                    edited = true
                    editor.addStop(t.id, d1, "p3")
                }
                return provider.route(request)
            }
        }
        val s = vm(MapTarget.Day(t.id, d1), racing).state.value
        assertEquals(listOf(1, 2, 3), s.points.map { it.number })
        assertEquals(store.get(t.id)!!.day(d1).revision, s.revision)
    }

    @Test
    fun `구간을 고르면 그 구간이 선택된다`() = runTest {
        val t = trip()
        val vm = vm(MapTarget.Day(t.id, d1))
        vm.onAction(MapAction.SelectLeg(1))
        assertEquals(1, vm.state.value.selected)
        vm.onAction(MapAction.SelectLeg(9))
        assertEquals(1, vm.state.value.selected)
    }

    @Test
    fun `한 장소 경로 - 출발지를 고르기 전에는 묻지 않는다`() = runTest {
        val vm = vm(MapTarget.ToPlace(PlaceId("p2")))
        assertTrue(vm.state.value.needsOrigin)
        assertTrue(provider.requests.isEmpty())
        assertEquals(1, vm.state.value.points.size)
        vm.onAction(MapAction.OriginChosen(Origin.AtPlace(PlaceId("p1"))))
        val s = vm.state.value
        assertFalse(s.needsOrigin)
        assertEquals(RouteRequest(places[0].coordinate, places[1].coordinate), provider.requests.single())
        assertEquals(1, s.legs.size)
        assertNotNull(s.legs[0].route)
    }

    @Test
    fun `무료면 잠금, 공급자에 묻지 않고 SC21`() = runTest {
        entitlement = Entitlement.Free
        val vm = vm(MapTarget.ToPlace(PlaceId("p2")))
        assertTrue(vm.state.value.locked)
        assertEquals(listOf<MapEffect>(MapEffect.Upgrade(UpgradeFeature.Route)), vm.effects.drain())
        vm.onAction(MapAction.OriginChosen(Origin.AtPlace(PlaceId("p1"))))
        assertTrue(provider.requests.isEmpty())
    }

    @Test
    fun `외부 지도 열기는 허용된 카카오맵 주소`() = runTest {
        val t = trip()
        val vm = vm(MapTarget.Day(t.id, d1))
        vm.effects.drain()
        vm.onAction(MapAction.OpenExternal("Place 1", "Place 2"))
        val effect = vm.effects.drain().single() as MapEffect.OpenUrl
        assertTrue(ExternalLinks.isAllowed(effect.url))
    }

    @Test
    fun `여행이 없으면 없음, 읽기 실패면 다시 시도`() = runTest {
        assertTrue(vm(MapTarget.Day(TripId("gone"), d1)).state.value.missing)
        val t = trip()
        store.failNextGet = true
        val vm = vm(MapTarget.Day(t.id, d1))
        assertTrue(vm.state.value.loadFailed)
        vm.onAction(MapAction.Refresh)
        assertFalse(vm.state.value.loadFailed)
        assertEquals(3, vm.state.value.points.size)
    }

    // ---------- Phase 19 리뷰 수정 ----------

    @Test
    fun `MAP-M3 잠긴 상태에서 다시 읽어도 SC21 을 또 열지 않는다(뒤로 가기 반복 방지)`() = runTest {
        entitlement = Entitlement.Free
        val vm = vm(MapTarget.ToPlace(PlaceId("p2")))
        assertEquals(1, vm.effects.drain().size)
        vm.onAction(MapAction.Refresh)
        assertTrue(vm.effects.drain().isEmpty())
        assertTrue(vm.state.value.locked)
        // 결제하고 돌아오면 잠금이 풀린다.
        entitlement = Entitlement.Paid
        vm.onAction(MapAction.Refresh)
        assertFalse(vm.state.value.locked)
    }

    @Test
    fun `MAP-M2 계산할 때마다 일정이 바뀌면 이전 revision 결과를 보이지 않는다(T-07)`() = runTest {
        val t = trip("p1", "p2")
        // 실패는 기억하지 않으므로 다시 물을 때마다 일정이 또 바뀐다.
        var edits = 0
        val restless = object : RouteProvider {
            override suspend fun route(request: RouteRequest): RouteResult {
                edits++
                editor.setStart(t.id, d1, LocalTime.of(9, edits), null)
                return RouteResult.Failed(RouteFailure.Network)
            }
        }
        val s = vm(MapTarget.Day(t.id, d1), restless).state.value
        assertTrue(s.loadFailed)
        assertTrue(s.legs.isEmpty())
    }

    @Test
    fun `한 장소 경로 실패는 이유와 함께`() = runTest {
        provider.failAll = RouteFailure.NoRoute
        val vm = vm(MapTarget.ToPlace(PlaceId("p2")))
        vm.onAction(MapAction.OriginChosen(Origin.AtPlace(PlaceId("p1"))))
        assertEquals(RouteFailure.NoRoute, vm.state.value.legs.single().failure)
    }

    // ---------- 대중교통 (TRN) ----------

    @Test
    fun `TRN-04 대중교통을 고르면 대중교통으로 다시 묻고, 도보로 돌아오면 도보 결과(기억)를 쓴다`() = runTest {
        val t = trip("p1", "p2")
        val vm = vm(MapTarget.Day(t.id, d1))
        assertEquals(TravelMode.Walk, vm.state.value.mode)
        vm.onAction(MapAction.SetMode(TravelMode.Transit))
        assertEquals(TravelMode.Transit, vm.state.value.mode)
        assertEquals(TravelMode.Transit, provider.requests.last().mode)
        assertNotNull(vm.state.value.legs.single().route!!.transit)
        val asked = provider.requests.size
        vm.onAction(MapAction.SetMode(TravelMode.Walk))
        assertEquals(asked, provider.requests.size)
        assertNull(vm.state.value.legs.single().route!!.transit)
    }

    @Test
    fun `TRN-05 대중교통으로 보고 있으면 외부 지도도 대중교통 길찾기로 연다`() = runTest {
        val t = trip("p1", "p2")
        val vm = vm(MapTarget.Day(t.id, d1))
        vm.onAction(MapAction.SetMode(TravelMode.Transit))
        vm.effects.drain()
        vm.onAction(MapAction.OpenExternal("Place 1", "Place 2"))
        val url = (vm.effects.drain().single() as MapEffect.OpenUrl).url
        assertTrue(url, url.startsWith("https://map.kakao.com/link/by/traffic/"))
    }

    @Test
    fun `TRN-06 대중교통 경로는 일정의 다시 계산 표시를 끄지 않는다(일정은 도보 기준)`() = runTest {
        val t = trip("p1", "p2")
        // 도보는 실패해 표시가 켜진 채로 시작한다.
        val walkFails = object : RouteProvider {
            override suspend fun route(request: RouteRequest): RouteResult =
                if (request.mode == TravelMode.Walk) RouteResult.Failed(RouteFailure.Network) else provider.route(request)
        }
        val vm = vm(MapTarget.Day(t.id, d1), walkFails)
        assertTrue(store.get(t.id)!!.day(d1).needsRecalculation)
        vm.onAction(MapAction.SetMode(TravelMode.Transit))
        assertNotNull(vm.state.value.legs.single().route)
        assertTrue(store.get(t.id)!!.day(d1).needsRecalculation)
    }

    @Test
    fun `TRN-07 수단을 바꾸면 이전 수단의 결과를 바로 지운다(잠깐 섞여 보이지 않게)`() = runTest {
        val t = trip("p1", "p2")
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slowTransit = object : RouteProvider {
            override suspend fun route(request: RouteRequest): RouteResult {
                if (request.mode == TravelMode.Transit) gate.await()
                return provider.route(request)
            }
        }
        val vm = vm(MapTarget.Day(t.id, d1), slowTransit)
        vm.onAction(MapAction.SetMode(TravelMode.Transit))
        assertTrue(vm.state.value.legs.isEmpty())
        gate.complete(Unit)
        assertNotNull(vm.state.value.legs.single().route!!.transit)
    }

    @Test
    fun `TRN-08 대중교통에서 다시 계산하면 기억한 결과를 버리고 다시 묻는다(시간대마다 다르다)`() = runTest {
        val t = trip("p1", "p2")
        val vm = vm(MapTarget.Day(t.id, d1))
        vm.onAction(MapAction.SetMode(TravelMode.Transit))
        val asked = provider.requests.count { it.mode == TravelMode.Transit }
        vm.onAction(MapAction.Refresh)
        assertEquals(asked + 1, provider.requests.count { it.mode == TravelMode.Transit })
    }
}
