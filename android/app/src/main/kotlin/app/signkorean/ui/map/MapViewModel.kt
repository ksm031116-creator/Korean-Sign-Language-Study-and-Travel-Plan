package app.signkorean.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.domain.common.Clock
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.Place
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.EntitlementSource
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.place.PlaceListing
import app.signkorean.domain.review.ExternalLinks
import app.signkorean.domain.route.DayRoutePlan
import app.signkorean.domain.route.DayRoutePlanner
import app.signkorean.domain.route.ExternalMapLink
import app.signkorean.domain.route.Route
import app.signkorean.domain.route.RouteFailure
import app.signkorean.domain.route.RouteResult
import app.signkorean.domain.trip.Origin
import app.signkorean.domain.trip.TripDay
import app.signkorean.domain.trip.TripEditor
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.trip.TripStore
import app.signkorean.ui.upgrade.UpgradeFeature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate

/** SC18 이 보여 줄 것: 하루 경로(SC13 에서) 또는 한 장소까지의 경로(SC07·SC08 에서). */
sealed interface MapTarget {
    data class Day(val trip: TripId, val date: LocalDate) : MapTarget
    data class ToPlace(val place: PlaceId) : MapTarget
}

/** 지도 위의 점. [number] 가 null 이면 출발지. 좌표를 모르는 점(지워진 장소)은 그리지 않는다. */
data class MapPoint(val coordinate: Coordinate?, val number: Int?, val place: Place?, val label: String?)

/** [from]→[to] 는 [MapUiState.points] 의 위치. 경로도 이유도 null 이면 끝점을 몰라 묻지 않은 구간이다. */
data class MapLegUi(val from: Int, val to: Int, val route: Route?, val failure: RouteFailure?)

data class MapUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val loadFailed: Boolean = false,
    /** 무료: 경로는 유료 기능이다(UX09). */
    val locked: Boolean = false,
    val tripTitle: String? = null,
    val date: LocalDate? = null,
    val points: List<MapPoint> = emptyList(),
    val legs: List<MapLegUi> = emptyList(),
    val selected: Int = 0,
    val calculatedAt: Instant? = null,
    /** 계산에 쓴 그날의 revision (T-07). 한 장소 경로는 null. */
    val revision: Int? = null,
    /** 한 장소 경로인데 출발지를 아직 고르지 않았다. */
    val needsOrigin: Boolean = false,
    val origin: Origin? = null,
    val pickingOrigin: Boolean = false,
    val busy: Boolean = false,
) {
    val selectedLeg: MapLegUi? get() = legs.getOrNull(selected)
    val attributions: List<String> get() = legs.mapNotNull { it.route?.attribution }.distinct()
}

sealed interface MapAction {
    data object Refresh : MapAction
    data class SelectLeg(val index: Int) : MapAction
    data object OpenOriginPicker : MapAction
    data object CloseOriginPicker : MapAction
    data class OriginChosen(val origin: Origin) : MapAction

    /** 이름은 화면이 현지화해서 준다. */
    data class OpenExternal(val fromName: String, val toName: String) : MapAction
}

sealed interface MapEffect {
    data class Upgrade(val feature: UpgradeFeature) : MapEffect
    data class OpenUrl(val url: String) : MapEffect
}

/**
 * SC18 지도 (FR13~15, 구현 계획 Phase 19 D2). 거리·시간은 경로 공급자 값 그대로다. 실패한 구간은 이유를 보이고
 * 직선거리·임의 속도로 채우지 않는다(M-03). 계산하는 동안 일정이 바뀌면 이전 결과를 버리고 다시 계산한다(T-07).
 * 모든 구간을 계산하면 일정의 "다시 계산" 표시를 끈다(T-16).
 */
class MapViewModel(
    private val target: MapTarget,
    private val store: TripStore,
    private val editor: TripEditor,
    private val content: ContentRepository,
    private val planner: DayRoutePlanner,
    private val entitlement: EntitlementSource,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(MapUiState())
    val state: StateFlow<MapUiState> = _state.asStateFlow()
    private val _effects = Channel<MapEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<MapEffect> = _effects

    private var job: Job? = null

    init {
        reload(announceLock = true)
    }

    fun onAction(action: MapAction) {
        val s = _state.value
        when (action) {
            MapAction.Refresh -> reload()
            is MapAction.SelectLeg -> if (action.index in s.legs.indices) _state.update { it.copy(selected = action.index) }
            MapAction.OpenOriginPicker -> if (!s.locked) _state.update { it.copy(pickingOrigin = true) }
            MapAction.CloseOriginPicker -> _state.update { it.copy(pickingOrigin = false) }
            is MapAction.OriginChosen -> {
                if (s.locked || target !is MapTarget.ToPlace) return
                _state.update { it.copy(origin = action.origin, pickingOrigin = false) }
                reload()
            }
            is MapAction.OpenExternal -> link(s, action)?.let { url -> viewModelScope.launch { _effects.send(MapEffect.OpenUrl(url)) } }
        }
    }

    private fun link(s: MapUiState, action: MapAction.OpenExternal): String? {
        val leg = s.selectedLeg
        val url = if (leg != null) {
            val from = s.points.getOrNull(leg.from)?.coordinate ?: return null
            val to = s.points.getOrNull(leg.to)?.coordinate ?: return null
            ExternalMapLink.walk(action.fromName, from, action.toName, to)
        } else {
            val only = s.points.singleOrNull()?.coordinate ?: return null
            ExternalMapLink.place(action.toName, only)
        }
        return url.takeIf { ExternalLinks.isAllowed(it) }
    }

    /**
     * [announceLock]: 잠겨 있으면 SC21 을 연다. 처음 열 때만 그렇다 — SC21 에서 돌아와 다시 읽을 때마다 열면
     * 뒤로 가기로 빠져나갈 수 없다(MAP-M3).
     */
    private fun reload(announceLock: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            try {
                if (!paid()) {
                    _state.update { it.copy(loading = false, locked = true, busy = false) }
                    if (announceLock) _effects.send(MapEffect.Upgrade(UpgradeFeature.Route))
                    return@launch
                }
                _state.update { it.copy(locked = false) }
                when (target) {
                    is MapTarget.Day -> loadDay(target)
                    is MapTarget.ToPlace -> loadPlace(target)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, loadFailed = true, busy = false) }
            }
        }
    }

    /** 권한을 못 읽으면 잠근다 — 유료 기능을 열어 두지 않는다. */
    private suspend fun paid(): Boolean = try {
        entitlement.current() == Entitlement.Paid
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }

    private suspend fun places(): Map<PlaceId, Place> = PlaceListing.visiblePlaces(content.load()).associateBy { it.id }

    private suspend fun loadDay(t: MapTarget.Day) {
        val places = places()
        // 계산 뒤 그날을 다시 읽어 revision 이 같을 때만 보인다. 계속 바뀌면 옛 결과를 보이지 않고 다시 시도를 안내한다(T-07).
        repeat(MAX_ATTEMPTS) {
            val trip = store.get(t.trip)
            val day = trip?.days?.firstOrNull { it.date == t.date }
            if (trip == null || day == null) {
                _state.update { MapUiState(loading = false, missing = true) }
                return
            }
            val plan = planner.plan(trip.id, day)
            val now = store.get(t.trip)?.days?.firstOrNull { it.date == t.date }
            if (now != null && now.revision == plan.revision) {
                showDay(trip.title, day, plan, places)
                if (plan.complete) editor.markRoutesCalculated(t.trip, t.date, plan.revision)
                return
            }
        }
        _state.update { it.copy(loading = false, busy = false, loadFailed = true, legs = emptyList()) }
    }

    private fun showDay(title: String, day: TripDay, plan: DayRoutePlan, places: Map<PlaceId, Place>) {
        val points = buildList {
            when (val o = day.origin) {
                null -> Unit
                is Origin.AtPlace -> add(MapPoint(places[o.place]?.coordinate, null, places[o.place], null))
                is Origin.AtCoordinate -> add(MapPoint(o.coordinate, null, null, o.label))
            }
            day.stops.forEachIndexed { i, stop -> add(MapPoint(places[stop.place]?.coordinate, i + 1, places[stop.place], null)) }
        }
        val legs = plan.routes.indices.map { i -> MapLegUi(i, i + 1, plan.routes[i], plan.failures.getOrNull(i)) }
        _state.update {
            it.copy(
                loading = false, missing = false, loadFailed = false, busy = false, tripTitle = title, date = day.date,
                points = points, legs = legs, selected = it.selected.coerceIn(0, (legs.size - 1).coerceAtLeast(0)),
                calculatedAt = clock.now(), revision = plan.revision,
            )
        }
    }

    private suspend fun loadPlace(t: MapTarget.ToPlace) {
        val places = places()
        val destination = places[t.place]
        if (destination == null) {
            _state.update { MapUiState(loading = false, missing = true) }
            return
        }
        val end = MapPoint(destination.coordinate, 1, destination, null)
        val origin = _state.value.origin
        if (origin == null) {
            _state.update { it.copy(loading = false, loadFailed = false, busy = false, needsOrigin = true, points = listOf(end), legs = emptyList()) }
            return
        }
        val start = when (origin) {
            is Origin.AtPlace -> MapPoint(places[origin.place]?.coordinate, null, places[origin.place], null)
            is Origin.AtCoordinate -> MapPoint(origin.coordinate, null, null, origin.label)
        }
        val from = start.coordinate
        val result = if (from == null) null else planner.single(from, destination.coordinate)
        val leg = MapLegUi(0, 1, (result as? RouteResult.Found)?.route, (result as? RouteResult.Failed)?.reason)
        _state.update {
            it.copy(
                loading = false, loadFailed = false, busy = false, needsOrigin = false, points = listOf(start, end), legs = listOf(leg),
                selected = 0, calculatedAt = clock.now(),
            )
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
