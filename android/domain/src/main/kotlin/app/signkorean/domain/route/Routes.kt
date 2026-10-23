package app.signkorean.domain.route

import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.place.PlaceListing
import app.signkorean.domain.trip.Origin
import app.signkorean.domain.trip.TripDay
import app.signkorean.domain.trip.TripId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 이동 수단. 도보가 기본이다(FR14, M-01). 대중교통은 지도 화면(SC18)에서 고른다 — 일정 시각 계산은 늘 도보다. */
enum class TravelMode { Walk, Transit }

enum class TransitKind { Walk, Bus, Subway }

/**
 * 대중교통 경로의 한 단계. [lines] 는 탈 노선 이름("3호선", "7212") — 걷기는 비어 있다.
 * 시간·거리는 공급자 값 그대로이고, 주지 않으면 null 이다(지어내지 않는다).
 */
data class TransitStep(val kind: TransitKind, val lines: List<String>, val seconds: Long?, val distanceMeters: Int?)

/** 대중교통 경로 요약. 요금은 공급자가 주지 않으면 null 이다(지어내지 않는다). */
data class TransitSummary(val transfers: Int, val fareWon: Int?, val steps: List<TransitStep>)

data class RouteRequest(val origin: Coordinate, val destination: Coordinate, val mode: TravelMode = TravelMode.Walk)

/** 공급자가 계산한 경로. 거리·시간은 공급자 값 그대로다. [attribution] 은 지도·경로 출처 표기. */
data class Route(
    val distanceMeters: Int,
    val durationSeconds: Long,
    val path: List<Coordinate>,
    val attribution: String,
    /** 대중교통일 때만 있다. */
    val transit: TransitSummary? = null,
)

enum class RouteFailure {
    /** 걸어서 갈 길이 없다. */
    NoRoute,

    /** 공급자가 이 지역·수단을 지원하지 않거나 연결되지 않았다. */
    Unsupported,
    Timeout,
    Network,
}

sealed interface RouteResult {
    data class Found(val route: Route) : RouteResult
    data class Failed(val reason: RouteFailure) : RouteResult
}

/** 경로 포트 (설계서 §6.6). 구현은 중계 서버 `POST /v1/routes`. 직선거리·임의 속도로 대신하지 않는다(FR15). */
interface RouteProvider {
    suspend fun route(request: RouteRequest): RouteResult
}

/**
 * 하루 경로 결과. [revision] 은 계산에 쓴 그날의 revision 이다. 지금 revision 과 다르면 버린다(T-07, AT12).
 * [failures] 는 구간별 실패 이유. 끝점 좌표를 몰라 묻지 않은 구간은 경로도 이유도 null 이다.
 */
data class DayRoutePlan(val revision: Int, val legs: List<Long?>, val routes: List<Route?>, val failures: List<RouteFailure?> = routes.map { null }) {
    /** 모든 구간을 공급자가 계산했다. 일정의 "다시 계산" 표시를 끌 수 있다(T-16). */
    val complete: Boolean get() = routes.none { it == null }
}

object RouteFreshness {
    fun isCurrent(plan: DayRoutePlan, day: TripDay): Boolean = plan.revision == day.revision
}

/**
 * 하루 경로 계산 (설계서 §6.5). 구간(출발 → 도착 좌표, 방향 있음)마다 성공한 결과를 기억해서, 순서를 바꾸면
 * 새로 생긴 구간만 묻는다(NFR08, M-06). 실패는 기억하지 않는다 — 다시 연결되면 다시 묻는다(T-16, AT14).
 * 구간은 동시에 묻고, 구간마다 [legTimeoutMillis] 를 넘기면 그 구간만 Timeout 이다(NFR02). 같은 구간을 동시에 물으면
 * 공급자에는 한 번만 묻는다. 앱에 하나만 두고 프로세스 동안 기억한다.
 */
class DayRoutePlanner(
    private val provider: RouteProvider,
    private val content: ContentRepository,
    private val legTimeoutMillis: Long = LEG_TIMEOUT_MILLIS,
) : DayLegs {
    private val cache = mutableMapOf<RouteRequest, Route>()
    private val inflight = mutableMapOf<RouteRequest, CompletableDeferred<RouteResult>>()
    private val lock = Mutex()

    override suspend fun legs(trip: TripId, day: TripDay): List<Long?> = plan(trip, day).legs

    override suspend fun times(trip: TripId, day: TripDay): LegTimes {
        val plan = plan(trip, day)
        return LegTimes(plan.legs, plan.routes.mapNotNull { it?.attribution?.takeIf(String::isNotBlank) }.distinct())
    }

    override suspend fun known(trip: TripId, day: TripDay): List<Long?> {
        val points = endpoints(day)
        return lock.withLock {
            points.zipWithNext { from, to -> if (from == null || to == null) null else cache[RouteRequest(from, to)]?.durationSeconds }
        }
    }

    /** [mode] 로 기억한 결과를 모두 버린다. 대중교통은 시간대마다 달라서 "다시 계산"이 실제로 다시 묻게 한다(TRN-08). */
    suspend fun forget(mode: TravelMode) {
        lock.withLock { cache.keys.removeAll { it.mode == mode } }
    }

    suspend fun plan(trip: TripId, day: TripDay, mode: TravelMode = TravelMode.Walk): DayRoutePlan {
        val points = endpoints(day)
        val results = coroutineScope {
            points.zipWithNext { from, to -> async { if (from == null || to == null) null else single(from, to, mode) } }.awaitAll()
        }
        val routes = results.map { (it as? RouteResult.Found)?.route }
        return DayRoutePlan(day.revision, routes.map { it?.durationSeconds }, routes, results.map { (it as? RouteResult.Failed)?.reason })
    }

    /** 한 장소까지의 경로(SC18 단일 경로). 실패하면 이유를 그대로 돌려준다(FR15). */
    suspend fun single(origin: Coordinate, destination: Coordinate, mode: TravelMode = TravelMode.Walk): RouteResult {
        val request = RouteRequest(origin, destination, mode)
        var owner = false
        val pending = lock.withLock {
            cache[request]?.let { return RouteResult.Found(it) }
            inflight[request] ?: CompletableDeferred<RouteResult>().also {
                inflight[request] = it
                owner = true
            }
        }
        if (!owner) {
            return try {
                pending.await()
            } catch (e: CancellationException) {
                // 먼저 물은 쪽이 취소됐다. 내가 살아 있으면 직접 묻는다.
                currentCoroutineContext().ensureActive()
                single(origin, destination, mode)
            }
        }
        try {
            val result = withTimeoutOrNull(legTimeoutMillis) { ask(request) } ?: RouteResult.Failed(RouteFailure.Timeout)
            withContext(NonCancellable) {
                lock.withLock {
                    if (result is RouteResult.Found) cache[request] = result.route
                    inflight.remove(request)
                }
            }
            pending.complete(result)
            return result
        } catch (e: Throwable) {
            withContext(NonCancellable) { lock.withLock { inflight.remove(request) } }
            pending.completeExceptionally(e)
            throw e
        }
    }

    private suspend fun ask(request: RouteRequest): RouteResult = try {
        provider.route(request)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        RouteResult.Failed(RouteFailure.Network)
    }

    /**
     * 그날 구간의 끝점들. 출발지가 있으면 맨 앞. 목적지는 등록 장소(활성 지역)의 좌표만이다(FR13, M-04).
     * 모르는 장소는 null 이라 그 앞뒤 구간은 "모름"이 된다.
     */
    private suspend fun endpoints(day: TripDay): List<Coordinate?> {
        if (day.stops.isEmpty()) return emptyList()
        val places = try {
            PlaceListing.visiblePlaces(content.load()).associateBy { it.id }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyMap()
        }
        fun at(id: PlaceId) = places[id]?.coordinate
        val origin = when (val o = day.origin) {
            null -> emptyList()
            is Origin.AtPlace -> listOf(at(o.place))
            is Origin.AtCoordinate -> listOf(o.coordinate)
        }
        return origin + day.stops.map { at(it.place) }
    }

    companion object {
        /** ApiClient 의 연결·읽기 제한(각 8초)보다 조금 길게. */
        const val LEG_TIMEOUT_MILLIS = 10_000L
    }
}
