package app.signkorean.testing

import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.route.Route
import app.signkorean.domain.route.RouteFailure
import app.signkorean.domain.route.RouteProvider
import app.signkorean.domain.route.RouteRequest
import app.signkorean.domain.route.RouteResult
import kotlin.math.abs

/**
 * 경로 공급자 가짜 (설계서 §6.6). 좌표 쌍마다 정해진 값을 돌려준다(같은 쌍 = 같은 값). 테스트가 결과를 미리 안다.
 * [failAll] 로 모든 요청 실패, [failing] 으로 특정 구간만 실패.
 */
class FakeRouteProvider : RouteProvider {
    val requests = mutableListOf<RouteRequest>()
    @Volatile var failAll: RouteFailure? = null
    val failing = mutableSetOf<Pair<Coordinate, Coordinate>>()

    /** 이 가짜가 그 구간에 돌려주는 초. 좌표 차이로 정해지는 결정적 값이며 실제 보행 시간이 아니다. */
    fun seconds(from: Coordinate, to: Coordinate): Long =
        60L + ((abs(from.lat - to.lat) + abs(from.lng - to.lng)) * 100_000).toLong()

    override suspend fun route(request: RouteRequest): RouteResult {
        requests += request
        failAll?.let { return RouteResult.Failed(it) }
        if ((request.origin to request.destination) in failing) return RouteResult.Failed(RouteFailure.NoRoute)
        val s = seconds(request.origin, request.destination)
        return RouteResult.Found(Route(distanceMeters = (s * 1.2).toInt(), durationSeconds = s, path = listOf(request.origin, request.destination), attribution = "Fake routes"))
    }
}
