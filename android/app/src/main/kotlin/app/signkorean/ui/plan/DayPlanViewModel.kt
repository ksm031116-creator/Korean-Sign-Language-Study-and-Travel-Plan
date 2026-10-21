package app.signkorean.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.Place
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.EntitlementSource
import app.signkorean.domain.place.PlaceListing
import app.signkorean.domain.route.DayLegs
import app.signkorean.domain.route.legCount
import app.signkorean.domain.trip.DaySchedule
import app.signkorean.domain.trip.Origin
import app.signkorean.domain.trip.ScheduleCalculator
import app.signkorean.domain.trip.StopId
import app.signkorean.domain.trip.StopTiming
import app.signkorean.domain.trip.Trip
import app.signkorean.domain.trip.TripDay
import app.signkorean.domain.trip.TripEditor
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.trip.TripProblem
import app.signkorean.domain.trip.TripResult
import app.signkorean.domain.trip.TripRules
import app.signkorean.domain.trip.TripStop
import app.signkorean.domain.trip.TripStore
import app.signkorean.ui.text.UiText
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
import java.time.LocalDate
import java.time.LocalTime

/** 타임라인 한 줄. [legBefore] 는 앞 장소(또는 출발지)에서 오는 구간의 분 — 모르면 null. */
data class StopRowUi(
    val stop: TripStop,
    val place: Place?,
    val timing: StopTiming?,
    val legBefore: Int?,
    val hasLegBefore: Boolean,
    val signCount: Int,
)

data class DayPlanUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    /** 여행을 읽지 못했다. "여행 없음"과 다르다 — 다시 시도할 수 있다(DPV-H1). */
    val loadFailed: Boolean = false,
    val trip: Trip? = null,
    val date: LocalDate? = null,
    val day: TripDay? = null,
    val rows: List<StopRowUi> = emptyList(),
    val schedule: DaySchedule? = null,
    /** 무료 상태: 보기만 된다(E-12). 편집하려 하면 SC21. */
    val readOnly: Boolean = false,
    val originPlace: Place? = null,
    /** SC17 출발지 후보: 그날 일정에 없는 등록 장소(DPV-H2). */
    val originCandidates: List<Place> = emptyList(),
    val busy: Boolean = false,
    val message: UiText? = null,
    /** SC16 시트가 열린 장소. */
    val editing: StopId? = null,
    val stayError: UiText? = null,
    val confirmRemove: StopId? = null,
    /** SC17 시트. */
    val startSheet: Boolean = false,
    /** 경로를 받는 중. 일정은 이미 보이고 편집할 수 있다(DPH-H1). */
    val routing: Boolean = false,
    /** 구간 시간의 출처 표기(디버그 표본이면 그렇게 적혀 있다, DPH-M5). */
    val routeAttributions: List<String> = emptyList(),
) {
    /** 일정이 바뀌어 경로를 다시 계산해야 한다(T-16, AT14). 경로 화면이 다시 계산하면 꺼진다(Phase 19). */
    val needsRecalculation: Boolean get() = day?.needsRecalculation == true && day.stops.size + (if (day.origin != null) 1 else 0) > 1
    val dates: List<LocalDate> get() = trip?.days?.map { it.date }.orEmpty()
}

sealed interface DayPlanAction {
    data object Refresh : DayPlanAction
    data class SelectDate(val date: LocalDate) : DayPlanAction
    data class MoveUp(val stop: StopId) : DayPlanAction
    data class MoveDown(val stop: StopId) : DayPlanAction
    data class OpenStop(val stop: StopId) : DayPlanAction
    data object CloseSheet : DayPlanAction
    /** 체류 시간을 고쳐 쓰기 시작했다. 이전 오류를 지운다(DPV-L5). */
    data object StayEdited : DayPlanAction
    data class SaveStay(val stop: StopId, val text: String) : DayPlanAction
    data class MoveTo(val stop: StopId, val date: LocalDate) : DayPlanAction
    data class Remove(val stop: StopId) : DayPlanAction
    data object ConfirmRemove : DayPlanAction
    data object CancelRemove : DayPlanAction
    data object OpenStart : DayPlanAction
    data class ApplyStart(val time: LocalTime, val origin: Origin?) : DayPlanAction
    data object AddPlace : DayPlanAction
    data object EditTrip : DayPlanAction

    /** SC18 하루 경로. 경로는 유료 기능이라 무료면 SC21 (UX09). */
    data object OpenMap : DayPlanAction
}

sealed interface DayPlanEffect {
    data class OpenPicker(val trip: TripId, val date: LocalDate) : DayPlanEffect
    data class EditTrip(val trip: TripId) : DayPlanEffect
    data class OpenMap(val trip: TripId, val date: LocalDate) : DayPlanEffect
    data class Upgrade(val feature: UpgradeFeature) : DayPlanEffect
}

/**
 * SC13 날짜별 일정 (FR19·20, 구현 계획 Phase 18). 바뀌는 것은 모두 저장 성공 뒤에 화면에 반영한다(UX02).
 * 시각은 [ScheduleCalculator] 가 [DayLegs](경로 공급자)의 구간 초로 계산한다. 경로를 모르면 Incomplete 다.
 */
class DayPlanViewModel(
    private val tripId: TripId,
    initialDate: LocalDate?,
    private val store: TripStore,
    private val editor: TripEditor,
    private val content: ContentRepository,
    private val legs: DayLegs,
    private val entitlement: EntitlementSource,
) : ViewModel() {

    private val _state = MutableStateFlow(DayPlanUiState(date = initialDate))
    val state: StateFlow<DayPlanUiState> = _state.asStateFlow()
    private val _effects = Channel<DayPlanEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<DayPlanEffect> = _effects

    private var pack: ContentPack? = null

    init {
        viewModelScope.launch { load() }
    }

    /** 권한을 못 읽으면 보기만 한다 — 편집을 열어 두지 않는다(DPV-L11). */
    private suspend fun readOnly(): Boolean = try {
        entitlement.current() != Entitlement.Paid
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        true
    }

    /** 그리기 세대. 늦게 끝난 이전 그리기가 새 화면을 덮지 않게 한다(DPV-M1). */
    private var generation = 0

    private suspend fun load() {
        try {
            if (pack == null) pack = content.load()
            val trip = store.get(tripId)
            if (trip == null) {
                _state.update { it.copy(loading = false, missing = true) }
                return
            }
            show(trip, readOnly())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loading = false, loadFailed = it.trip == null, message = if (it.trip == null) null else UiText.Res(R.string.common_load_failed)) }
        }
    }

    private var routeJob: Job? = null

    /** 마지막으로 그린(저장 성공한) 여행. 날짜를 바꿀 때 옛 스냅샷을 쓰지 않게 한다(DPH-H2). */
    private var latestTrip: Trip? = null

    /**
     * 먼저 이미 아는 구간으로 바로 그리고(NFR02, UX02), 경로는 따로 받아 끝나면 다시 그린다(DPH-H1).
     * 보기만(무료)이면 경로 공급자에 묻지 않는다 — 경로는 유료 기능이다(UX09).
     */
    private suspend fun show(trip: Trip, readOnly: Boolean = _state.value.readOnly) {
        val p = pack ?: return
        latestTrip = trip
        val gen = ++generation
        routeJob?.cancel()
        val date = _state.value.date?.takeIf { d -> trip.days.any { it.date == d } } ?: trip.start
        val day = trip.day(date)
        val known = safeLegs(day) { legs.known(trip.id, day) }
        if (gen != generation) return
        val ask = !readOnly && legCount(day) > 0
        render(p, trip, date, day, known, readOnly, routing = ask, attributions = emptyList())
        if (!ask) return
        routeJob = viewModelScope.launch {
            var attributions = emptyList<String>()
            val full = safeLegs(day) { legs.times(trip.id, day).also { attributions = it.attributions }.legs }
            if (gen != generation) return@launch
            var shown = day
            // 모든 구간을 공급자가 계산했으면 "다시 계산" 표시를 끈다. 그 사이 일정이 바뀌었으면(revision 다름) 끄지 않는다(T-07·16).
            if (day.needsRecalculation && full.isNotEmpty() && full.none { it == null } && editor.markRoutesCalculated(trip.id, date, day.revision)) {
                shown = day.copy(needsRecalculation = false)
            }
            if (gen != generation) return@launch
            render(p, trip, date, shown, full, readOnly, routing = false, attributions = attributions)
        }
    }

    private suspend fun safeLegs(day: TripDay, read: suspend () -> List<Long?>): List<Long?> = try {
        read().takeIf { it.size == legCount(day) } ?: List(legCount(day)) { null }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        List(legCount(day)) { null }
    }

    private fun render(
        p: ContentPack, trip: Trip, date: LocalDate, day: TripDay, legSeconds: List<Long?>, readOnly: Boolean, routing: Boolean, attributions: List<String>,
    ) {
        val places = p.places.associateBy { it.id }
        val schedule = ScheduleCalculator.calculate(day.startTime, day.origin != null, day.stops.map { it.stayMinutes }, legSeconds)
        val rows = day.stops.mapIndexed { i, stop ->
            val legIndex = if (day.origin != null) i else i - 1
            StopRowUi(
                stop = stop,
                place = places[stop.place],
                timing = schedule.stops.getOrNull(i),
                legBefore = schedule.legMinutes.getOrNull(legIndex),
                hasLegBefore = legIndex >= 0,
                signCount = places[stop.place]?.let { PlaceListing.signsAt(p, it).size } ?: 0,
            )
        }
        _state.update {
            it.copy(
                loading = false, missing = false, loadFailed = false, trip = trip, date = date, day = day, rows = rows, schedule = schedule, readOnly = readOnly,
                originPlace = (day.origin as? Origin.AtPlace)?.let { o -> places[o.place] },
                originCandidates = originCandidates(p, day),
                routing = routing,
                routeAttributions = if (routing) it.routeAttributions else attributions,
            )
        }
    }

    fun onAction(action: DayPlanAction) {
        if (action == DayPlanAction.Refresh) {
            viewModelScope.launch { load() }
            return
        }
        val s = _state.value
        val date = s.date ?: return
        if (s.readOnly && action.isEdit()) {
            viewModelScope.launch { _effects.send(DayPlanEffect.Upgrade(UpgradeFeature.Plan)) }
            return
        }
        // 저장 중에는 화면을 바꾸는 동작을 받지 않는다. 저장 결과가 옛 화면에 덮이지 않게(DPH-H2).
        if (s.busy && action.changesScreen()) return
        // 날짜를 막 바꿔 화면의 줄이 아직 이전 날이면 그 줄에 대한 편집은 받지 않는다(DPV-M1).
        if (action.isRowEdit() && s.day?.date != date) return
        when (action) {
            DayPlanAction.Refresh -> Unit
            is DayPlanAction.SelectDate -> {
                _state.update { it.copy(date = action.date, editing = null, message = null) }
                // 저장소의 최신 여행으로 그린다. 못 읽으면 마지막으로 저장에 성공한 여행(DPH-H2).
                viewModelScope.launch {
                    val fresh = try {
                        store.get(tripId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                    (fresh ?: latestTrip ?: s.trip)?.let { show(it) }
                }
            }
            is DayPlanAction.MoveUp -> s.indexOf(action.stop)?.let { i -> if (i > 0) apply { editor.reorder(tripId, date, action.stop, i - 1) } }
            is DayPlanAction.MoveDown -> s.indexOf(action.stop)?.let { i -> if (i < s.rows.lastIndex) apply { editor.reorder(tripId, date, action.stop, i + 1) } }
            is DayPlanAction.OpenStop -> _state.update { it.copy(editing = action.stop, stayError = null, message = null) }
            DayPlanAction.StayEdited -> if (s.stayError != null) _state.update { it.copy(stayError = null) }
            DayPlanAction.CloseSheet -> _state.update { it.copy(editing = null, startSheet = false, stayError = null) }
            is DayPlanAction.SaveStay -> {
                val minutes = TripRules.parseStay(action.text)
                if (minutes == null) {
                    _state.update { it.copy(stayError = TripProblem.InvalidStay.text()) }
                    return
                }
                apply(closeSheet = true) { editor.setStay(tripId, date, action.stop, minutes) }
            }
            is DayPlanAction.MoveTo -> apply(closeSheet = true) { editor.moveStop(tripId, date, action.stop, action.date) }
            is DayPlanAction.Remove -> _state.update { it.copy(confirmRemove = action.stop) }
            DayPlanAction.CancelRemove -> _state.update { it.copy(confirmRemove = null) }
            // 진행 중이면 창을 그대로 둔다. 눌렀는데 아무 일 없이 닫히지 않게(DPV-M6).
            DayPlanAction.ConfirmRemove -> if (!s.busy) s.confirmRemove?.let { stop ->
                _state.update { it.copy(confirmRemove = null) }
                apply(closeSheet = true) { editor.removeStop(tripId, date, stop) }
            }
            DayPlanAction.OpenStart -> _state.update { it.copy(startSheet = true) }
            is DayPlanAction.ApplyStart -> apply(closeSheet = true) { editor.setStart(tripId, date, action.time, action.origin) }
            DayPlanAction.AddPlace -> viewModelScope.launch { _effects.send(DayPlanEffect.OpenPicker(tripId, date)) }
            DayPlanAction.EditTrip -> viewModelScope.launch { _effects.send(DayPlanEffect.EditTrip(tripId)) }
            DayPlanAction.OpenMap -> viewModelScope.launch {
                _effects.send(if (s.readOnly) DayPlanEffect.Upgrade(UpgradeFeature.Route) else DayPlanEffect.OpenMap(tripId, date))
            }
        }
    }

    private fun DayPlanAction.isEdit(): Boolean = when (this) {
        is DayPlanAction.MoveUp, is DayPlanAction.MoveDown, is DayPlanAction.OpenStop, is DayPlanAction.SaveStay,
        is DayPlanAction.MoveTo, is DayPlanAction.Remove, DayPlanAction.ConfirmRemove, DayPlanAction.OpenStart,
        is DayPlanAction.ApplyStart, DayPlanAction.AddPlace, DayPlanAction.EditTrip -> true
        DayPlanAction.Refresh, is DayPlanAction.SelectDate, DayPlanAction.CloseSheet, DayPlanAction.CancelRemove, DayPlanAction.StayEdited, DayPlanAction.OpenMap -> false
    }

    private fun DayPlanAction.changesScreen(): Boolean =
        this is DayPlanAction.SelectDate || this == DayPlanAction.OpenStart || this == DayPlanAction.EditTrip ||
            this == DayPlanAction.OpenMap || this == DayPlanAction.AddPlace || this is DayPlanAction.OpenStop

    /** 보이는 그날 줄을 대상으로 하는 편집. */
    private fun DayPlanAction.isRowEdit(): Boolean = isEdit() && this != DayPlanAction.AddPlace && this != DayPlanAction.EditTrip

    private fun DayPlanUiState.indexOf(stop: StopId): Int? = rows.indexOfFirst { it.stop.id == stop }.takeIf { it >= 0 }

    /** 저장이 성공해야 화면을 바꾼다. 실패하면 지금 화면 그대로 안내한다(UX02). */
    private fun apply(closeSheet: Boolean = false, change: suspend () -> TripResult) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val r = change()) {
                is TripResult.Ok -> {
                    _state.update { it.copy(stayError = null, editing = if (closeSheet) null else it.editing, startSheet = if (closeSheet) false else it.startSheet) }
                    // 새 일정을 그린 뒤에 풀어 준다(경로는 기다리지 않는다). 그 사이 다른 편집이 옛 줄을 보고 들어오지 않게(DPV-M1).
                    try {
                        show(r.trip)
                    } finally {
                        _state.update { it.copy(busy = false) }
                    }
                }
                is TripResult.Rejected -> {
                    if (r.problem == TripProblem.PaidOnly) _effects.send(DayPlanEffect.Upgrade(UpgradeFeature.Plan))
                    _state.update { it.copy(busy = false, message = r.problem.text(r.dates)) }
                }
                TripResult.SaveFailed -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.common_save_failed)) }
            }
        }
    }
}

/** SC17 의 출발지 후보: 그날 일정에 없는 등록 장소. 현재 위치·추천 출발점은 SC19. */
internal fun originCandidates(pack: ContentPack, day: TripDay): List<Place> =
    PlaceListing.visiblePlaces(pack).filter { p -> day.stops.none { it.place == p.id } }
