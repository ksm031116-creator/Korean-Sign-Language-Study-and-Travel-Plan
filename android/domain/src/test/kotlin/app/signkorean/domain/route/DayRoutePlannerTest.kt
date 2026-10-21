package app.signkorean.domain.route

import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.trip.Origin
import app.signkorean.domain.trip.StopId
import app.signkorean.domain.trip.TripDay
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.trip.TripStop
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeRouteProvider
import app.signkorean.testing.InMemoryContentRepository
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** 하루 경로 (FR13~15, NFR08, 설계서 §6.5). M-01·03~06, T-07·16 */
class DayRoutePlannerTest {

    private val a = ContentFixtures.place("a").copy(coordinate = Coordinate(37.570, 126.990))
    private val b = ContentFixtures.place("b").copy(coordinate = Coordinate(37.575, 126.985))
    private val c = ContentFixtures.place("c").copy(coordinate = Coordinate(37.580, 126.980))
    private val pack = ContentFixtures.pack(places = listOf(a, b, c))
    private val provider = FakeRouteProvider()
    private val planner = DayRoutePlanner(provider, InMemoryContentRepository(pack))
    private val trip = TripId("t")
    private val date = LocalDate.of(2026, 11, 3)

    private fun day(vararg ids: String, origin: Origin? = null, revision: Int = 1) =
        TripDay(date, origin = origin, stops = ids.map { TripStop(StopId("s-$it"), PlaceId(it)) }, revision = revision)

    @Test
    fun `M-01 기본 이동 수단은 도보`() = runTest {
        planner.legs(trip, day("a", "b"))
        assertTrue(provider.requests.all { it.mode == TravelMode.Walk })
    }

    @Test
    fun `M-04 목적지는 등록 장소의 실제 좌표다(지역 중심점이 아니다)`() = runTest {
        planner.legs(trip, day("a", "b"))
        assertEquals(RouteRequest(a.coordinate, b.coordinate, TravelMode.Walk), provider.requests.single())
    }

    @Test
    fun `출발지가 있으면 첫 구간은 출발지에서 첫 장소`() = runTest {
        val station = Coordinate(37.5547, 126.9707)
        val legs = planner.legs(trip, day("a", "b", origin = Origin.AtCoordinate(station, "Seoul Station")))
        assertEquals(2, legs.size)
        assertEquals(station, provider.requests.first().origin)
        val placeOrigin = planner.legs(trip, day("b", origin = Origin.AtPlace(PlaceId("c"))))
        assertEquals(1, placeOrigin.size)
        assertEquals(RouteRequest(c.coordinate, b.coordinate, TravelMode.Walk), provider.requests.last())
    }

    @Test
    fun `M-05 구간 하나가 실패하면 그 구간만 모름, 나머지는 공급자 시간`() = runTest {
        provider.failing += (b.coordinate to c.coordinate)
        assertEquals(listOf(provider.seconds(a.coordinate, b.coordinate), null), planner.legs(trip, day("a", "b", "c")))
    }

    @Test
    fun `M-03 실패를 직선거리나 임의 속도로 채우지 않는다`() = runTest {
        provider.failAll = RouteFailure.NoRoute
        assertEquals(listOf<Long?>(null, null), planner.legs(trip, day("a", "b", "c")))
    }

    @Test
    fun `M-06 순서를 바꾸면 새로 생긴 구간만 요청한다`() = runTest {
        planner.legs(trip, day("a", "b", "c"))
        assertEquals(2, provider.requests.size)
        planner.legs(trip, day("a", "c", "b", revision = 2))
        // a→c, c→b 둘 다 처음 보는 구간. b→c 결과를 c→b 로 쓰지 않는다(방향이 다르다).
        assertEquals(4, provider.requests.size)
        planner.legs(trip, day("a", "b", "c", revision = 3))
        assertEquals(4, provider.requests.size, "이미 계산한 구간은 다시 묻지 않는다")
    }

    @Test
    fun `T-16 오프라인이면 모름으로 두고, 다시 연결되면 모르는 구간만 묻는다`() = runTest {
        planner.legs(trip, day("a", "b"))
        provider.failAll = RouteFailure.Network
        val offline = planner.legs(trip, day("a", "b", "c", revision = 2))
        assertEquals(listOf(provider.seconds(a.coordinate, b.coordinate), null), offline)
        provider.failAll = null
        val before = provider.requests.size
        val online = planner.legs(trip, day("a", "b", "c", revision = 2))
        assertEquals(before + 1, provider.requests.size)
        assertTrue(online.none { it == null })
    }

    @Test
    fun `실패한 구간은 기억하지 않는다(다시 연결되면 다시 묻는다)`() = runTest {
        provider.failAll = RouteFailure.Timeout
        planner.legs(trip, day("a", "b"))
        provider.failAll = null
        assertTrue(planner.legs(trip, day("a", "b")).single() != null)
    }

    @Test
    fun `없는 장소가 끼어 있으면 그 앞뒤 구간은 모름`() = runTest {
        assertEquals(listOf<Long?>(null, null), planner.legs(trip, day("a", "gone", "b")))
    }

    @Test
    fun `같은 날의 결과는 revision 과 함께 돌려준다(오래된 응답 판별, T-07)`() = runTest {
        val result = planner.plan(trip, day("a", "b", revision = 3))
        assertEquals(3, result.revision)
        assertTrue(RouteFreshness.isCurrent(result, day("a", "b", revision = 3)))
        assertTrue(!RouteFreshness.isCurrent(result, day("a", "b", "c", revision = 4)))
    }

    @Test
    fun `단일 경로 - 거리·시간·경로선, 실패는 이유`() = runTest {
        val ok = planner.single(a.coordinate, b.coordinate) as RouteResult.Found
        assertTrue(ok.route.distanceMeters > 0 && ok.route.path.size >= 2)
        provider.failAll = RouteFailure.Unsupported
        assertEquals(RouteResult.Failed(RouteFailure.Unsupported), planner.single(a.coordinate, c.coordinate))
    }
}
