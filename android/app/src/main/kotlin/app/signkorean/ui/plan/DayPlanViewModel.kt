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
    val trip: Trip? = null,
    val date: LocalDate? = null,
    val day: TripDay? = null,
    val rows: List<StopRowUi> = emptyList(),
    val schedule: DaySchedule? = null,
    /** 무료 상태: 보기만 된다(E-12). 편집하려 하면 SC21. */
    val readOnly: Boolean = false,
    val originPlace: Place? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
    /** SC16 시트가 열린 장소. */
    val editing: StopId? = null,
    val stayError: UiText? = null,
    val confirmRemove: StopId? = null,
    /** SC17 시트. */
    val startSheet: Boolean = false,
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
    data class SaveStay(val stop: StopId, val text: String) : DayPlanAction
    data class MoveTo(val stop: StopId, val date: LocalDate) : DayPlanAction
    data class Remove(val stop: StopId) : DayPlanAction
    data object ConfirmRemove : DayPlanAction
    data object CancelRemove : DayPlanAction
    data object OpenStart : DayPlanAction
    data class ApplyStart(val time: LocalTime, val origin: Origin?) : DayPlanAction
    data object AddPlace : DayPlanAction
    data object EditTrip : DayPlanAction
}

sealed interface DayPlanEffect {
    data class OpenPicker(val trip: TripId, val date: LocalDate) : DayPlanEffect
    data class EditTrip(val trip: TripId) : DayPlanEffect
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

    private suspend fun load() {
        try {
            val p = pack ?: content.load().also { pack = it }
            val trip = store.get(tripId)
            if (trip == null) {
                _state.update { it.copy(loading = false, missing = true) }
                return
            }
            val readOnly = entitlement.current() != Entitlement.Paid
            show(trip, readOnly)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loading = false, message = UiText.Res(R.string.common_save_failed)) }
        }
    }

    private suspend fun show(trip: Trip, readOnly: Boolean = _state.value.readOnly) {
        val p = pack ?: return
        val date = _state.value.date?.takeIf { d -> trip.days.any { it.date == d } } ?: trip.start
        val day = trip.day(date)
        val places = p.places.associateBy { it.id }
        val legSeconds = try {
            legs.legs(trip.id, day).takeIf { it.size == legCount(day) } ?: List(legCount(day)) { null }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            List(legCount(day)) { null }
        }
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
                loading = false, missing = false, trip = trip, date = date, day = day, rows = rows, schedule = schedule, readOnly = readOnly,
                originPlace = (day.origin as? Origin.AtPlace)?.let { o -> places[o.place] },
            )
        }
    }

    fun onAction(action: DayPlanAction) {
        val s = _state.value
        val date = s.date ?: return
        if (s.readOnly && action.isEdit()) {
            viewModelScope.launch { _effects.send(DayPlanEffect.Upgrade(UpgradeFeature.Plan)) }
            return
        }
        when (action) {
            DayPlanAction.Refresh -> viewModelScope.launch { load() }
            is DayPlanAction.SelectDate -> {
                _state.update { it.copy(date = action.date, editing = null, message = null) }
                s.trip?.let { t -> viewModelScope.launch { show(t) } }
            }
            is DayPlanAction.MoveUp -> s.indexOf(action.stop)?.let { i -> if (i > 0) apply { editor.reorder(tripId, date, action.stop, i - 1) } }
            is DayPlanAction.MoveDown -> s.indexOf(action.stop)?.let { i -> if (i < s.rows.lastIndex) apply { editor.reorder(tripId, date, action.stop, i + 1) } }
            is DayPlanAction.OpenStop -> _state.update { it.copy(editing = action.stop, stayError = null, message = null) }
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
            DayPlanAction.ConfirmRemove -> s.confirmRemove?.let { stop ->
                _state.update { it.copy(confirmRemove = null) }
                apply(closeSheet = true) { editor.removeStop(tripId, date, stop) }
            }
            DayPlanAction.OpenStart -> _state.update { it.copy(startSheet = true) }
            is DayPlanAction.ApplyStart -> apply(closeSheet = true) { editor.setStart(tripId, date, action.time, action.origin) }
            DayPlanAction.AddPlace -> viewModelScope.launch { _effects.send(DayPlanEffect.OpenPicker(tripId, date)) }
            DayPlanAction.EditTrip -> viewModelScope.launch { _effects.send(DayPlanEffect.EditTrip(tripId)) }
        }
    }

    private fun DayPlanAction.isEdit(): Boolean = when (this) {
        is DayPlanAction.MoveUp, is DayPlanAction.MoveDown, is DayPlanAction.OpenStop, is DayPlanAction.SaveStay,
        is DayPlanAction.MoveTo, is DayPlanAction.Remove, DayPlanAction.ConfirmRemove, DayPlanAction.OpenStart,
        is DayPlanAction.ApplyStart, DayPlanAction.AddPlace, DayPlanAction.EditTrip -> true
        DayPlanAction.Refresh, is DayPlanAction.SelectDate, DayPlanAction.CloseSheet, DayPlanAction.CancelRemove -> false
    }

    private fun DayPlanUiState.indexOf(stop: StopId): Int? = rows.indexOfFirst { it.stop.id == stop }.takeIf { it >= 0 }

    /** 저장이 성공해야 화면을 바꾼다. 실패하면 지금 화면 그대로 안내한다(UX02). */
    private fun apply(closeSheet: Boolean = false, change: suspend () -> TripResult) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val r = change()) {
                is TripResult.Ok -> {
                    _state.update { it.copy(busy = false, stayError = null, editing = if (closeSheet) null else it.editing, startSheet = if (closeSheet) false else it.startSheet) }
                    show(r.trip)
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

/** SC17 의 출발지 후보: 그날 일정에 없는 등록 장소. 현재 위치·추천 출발점은 Phase 19 (SC19). */
internal fun originCandidates(pack: ContentPack, day: TripDay): List<Place> =
    PlaceListing.visiblePlaces(pack).filter { p -> day.stops.none { it.place == p.id } }
