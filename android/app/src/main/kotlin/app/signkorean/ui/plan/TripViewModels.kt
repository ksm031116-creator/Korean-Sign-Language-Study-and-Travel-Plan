package app.signkorean.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.common.Clock
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.Place
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.place.PlaceListing
import app.signkorean.domain.trip.Trip
import app.signkorean.domain.trip.TripEditor
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.trip.TripProblem
import app.signkorean.domain.trip.TripResult
import app.signkorean.domain.trip.TripRules
import app.signkorean.domain.trip.TripStore
import app.signkorean.ui.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** 일정의 날짜는 서울 기준이다(설계서 §6.5). 기기 시간대와 무관하다. */
internal val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
internal fun Clock.seoulToday(): LocalDate = now().atZone(SEOUL).toLocalDate()

/** 거부 이유 → 문구 (설계서 원칙 9: 도메인은 코드, 화면이 문구). */
internal fun TripProblem.text(dates: List<LocalDate> = emptyList()): UiText = when (this) {
    TripProblem.TitleBlank -> UiText.Res(R.string.trip_error_title)
    TripProblem.EndBeforeStart -> UiText.Res(R.string.trip_error_end_before_start)
    TripProblem.TooLong -> UiText.Res(R.string.trip_error_too_long, listOf(TripRules.MAX_DAYS))
    TripProblem.Duplicate -> UiText.Res(R.string.trip_error_duplicate)
    TripProblem.DayFull -> UiText.Res(R.string.trip_error_day_full, listOf(TripRules.MAX_STOPS_PER_DAY))
    TripProblem.NotADestination -> UiText.Res(R.string.trip_error_not_destination)
    TripProblem.InvalidStay -> UiText.Res(R.string.trip_error_stay, listOf(TripRules.MAX_STAY))
    TripProblem.StopsOutsideDates -> UiText.Res(R.string.trip_error_outside, listOf(dates.size))
    TripProblem.DateOutsideTrip, TripProblem.NotFound -> UiText.Res(R.string.trip_error_missing)
    TripProblem.PaidOnly -> UiText.Res(R.string.trip_error_paid)
}

// ---------------------------------------------------------------- SC11

data class TripCardUi(val id: TripId, val title: String, val start: LocalDate, val end: LocalDate, val placeCount: Int)

data class TripListUiState(
    val loading: Boolean = true,
    val trips: List<TripCardUi> = emptyList(),
    val busy: Boolean = false,
    val message: UiText? = null,
    /** 목록을 못 읽었다. 빈 상태·샘플을 보이지 않는다(있던 여행 위에 샘플을 또 만들지 않게, PLN-L8). */
    val loadFailed: Boolean = false,
)

sealed interface TripListAction {
    data object Refresh : TripListAction
    /** [title] 은 화면이 지금 언어의 리소스에서 넣는다(설계서 원칙 9). */
    data class CreateSample(val title: String) : TripListAction
}

sealed interface TripListEffect {
    data class OpenTrip(val id: TripId) : TripListEffect
}

/** SC11 여행 목록 (유료). 기기 안에만 저장된다(PRD §10.4). */
class TripListViewModel(
    private val store: TripStore,
    private val editor: TripEditor,
    private val content: ContentRepository,
    private val clock: Clock,
) : ViewModel() {
    private val _state = MutableStateFlow(TripListUiState())
    val state: StateFlow<TripListUiState> = _state.asStateFlow()
    private val _effects = Channel<TripListEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<TripListEffect> = _effects

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val trips = try {
            store.all()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loading = false, loadFailed = true, message = UiText.Res(R.string.common_load_failed)) }
            return
        }
        _state.update { s -> s.copy(loading = false, loadFailed = false, message = null, trips = trips.map { TripCardUi(it.id, it.title, it.start, it.end, it.placeCount) }) }
    }

    fun onAction(action: TripListAction) {
        when (action) {
            TripListAction.Refresh -> viewModelScope.launch { load() }
            is TripListAction.CreateSample -> if (!_state.value.busy) viewModelScope.launch { sample(action.title) }
        }
    }

    /** 프로토타입의 sample trip. 오늘 하루, 등록 장소 세 곳. 이름은 사용자가 나중에 고친다. */
    private suspend fun sample(title: String) {
        _state.update { it.copy(busy = true, message = null) }
        // 장소부터 읽는다. 못 읽으면 빈 샘플 여행을 남기지 않고 안내한다(PLN-M2).
        val places = try {
            PlaceListing.visiblePlaces(content.load()).take(3)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(busy = false, message = UiText.Res(R.string.common_load_failed)) }
            return
        }
        val today = clock.seoulToday()
        val made = editor.create(title, today, today)
        if (made !is TripResult.Ok) {
            _state.update { it.copy(busy = false, message = UiText.Res(R.string.common_save_failed)) }
            return
        }
        places.forEach { editor.addStop(made.trip.id, today, it.id.value) }
        load()
        _state.update { it.copy(busy = false) }
        _effects.send(TripListEffect.OpenTrip(made.trip.id))
    }
}

// ---------------------------------------------------------------- SC12

enum class TripField { Title, End }

data class TripFormUiState(
    val loading: Boolean = true,
    val editing: Boolean = false,
    val title: String = "",
    val start: LocalDate = LocalDate.MIN,
    val end: LocalDate = LocalDate.MIN,
    val titleError: UiText? = null,
    val endError: UiText? = null,
    /** 저장 실패 시 스크롤·알림할 첫 오류 칸 (UX07). */
    val focusError: TripField? = null,
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val confirmDiscard: Boolean = false,
    val confirmDelete: Boolean = false,
    val message: UiText? = null,
    /** 저장이 실패해 오류를 보인 횟수. 같은 오류로 다시 저장해도 화면이 매번 오류로 스크롤·알린다(PLN-M3). */
    val errorShownCount: Int = 0,
    /** 편집할 여행이 없다. 저장할 수 없다(PLN-L10). */
    val missing: Boolean = false,
)

sealed interface TripFormAction {
    data class Title(val text: String) : TripFormAction
    data class Start(val date: LocalDate) : TripFormAction
    data class End(val date: LocalDate) : TripFormAction
    data object Save : TripFormAction
    data object Back : TripFormAction
    data object KeepEditing : TripFormAction
    data object ConfirmDiscard : TripFormAction
    data object Delete : TripFormAction
    data object ConfirmDelete : TripFormAction
    data object CancelDelete : TripFormAction
}

sealed interface TripFormEffect {
    data class Saved(val id: TripId, val created: Boolean) : TripFormEffect
    data object Deleted : TripFormEffect
    data object Close : TripFormEffect

    /** 무료 상태라 바꿀 수 없다. SC21 을 연다(E-12, PLN-M7). */
    data object Upgrade : TripFormEffect
}

/** SC12 여행 만들기·고치기 (FR17, UX02·07). Save 로만 확정한다. 편집 중 Back 은 확인(SC20). */
class TripFormViewModel(
    private val tripId: TripId?,
    private val store: TripStore,
    private val editor: TripEditor,
    clock: Clock,
) : ViewModel() {
    private val today = clock.seoulToday()
    private val _state = MutableStateFlow(TripFormUiState(editing = tripId != null, start = today, end = today.plusDays(2)))
    val state: StateFlow<TripFormUiState> = _state.asStateFlow()
    private val _effects = Channel<TripFormEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<TripFormEffect> = _effects

    init {
        viewModelScope.launch {
            val trip = tripId?.let { runCatching { store.get(it) }.getOrNull() }
            _state.update {
                if (trip == null) it.copy(loading = false, missing = tripId != null, message = if (tripId != null) UiText.Res(R.string.trip_error_missing) else null)
                else it.copy(loading = false, title = trip.title, start = trip.start, end = trip.end)
            }
        }
    }

    fun onAction(action: TripFormAction) {
        when (action) {
            is TripFormAction.Title -> _state.update { it.copy(title = action.text, titleError = null, dirty = true) }
            is TripFormAction.Start -> _state.update {
                // 시작일을 종료일 뒤로 옮기면 종료일을 같이 민다. 기간은 그대로 둔다.
                val length = java.time.temporal.ChronoUnit.DAYS.between(it.start, it.end).coerceAtLeast(0)
                it.copy(start = action.date, end = if (action.date.isAfter(it.end)) action.date.plusDays(length) else it.end, endError = null, dirty = true)
            }
            is TripFormAction.End -> _state.update { it.copy(end = action.date, endError = null, dirty = true) }
            TripFormAction.Save -> save()
            // 저장 중에는 떠나지 않는다. 이미 저장된 여행을 버렸다고 여기지 않게(PLN-L1).
            TripFormAction.Back -> when {
                _state.value.saving -> Unit
                _state.value.dirty -> _state.update { it.copy(confirmDiscard = true) }
                else -> close()
            }
            TripFormAction.KeepEditing -> _state.update { it.copy(confirmDiscard = false) }
            TripFormAction.ConfirmDiscard -> close()
            TripFormAction.Delete -> _state.update { it.copy(confirmDelete = true) }
            TripFormAction.CancelDelete -> _state.update { it.copy(confirmDelete = false) }
            TripFormAction.ConfirmDelete -> tripId?.let { id ->
                _state.update { it.copy(confirmDelete = false) }
                viewModelScope.launch {
                    if (editor.delete(id)) _effects.send(TripFormEffect.Deleted)
                    else _state.update { it.copy(message = UiText.Res(R.string.common_save_failed)) }
                }
            }
        }
    }

    private fun close() {
        _state.update { it.copy(confirmDiscard = false) }
        viewModelScope.launch { _effects.send(TripFormEffect.Close) }
    }

    private fun save() {
        val s = _state.value
        if (s.saving || s.missing) return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            val r = if (tripId == null) editor.create(s.title, s.start, s.end) else editor.update(tripId, s.title, s.start, s.end)
            when (r) {
                is TripResult.Ok -> {
                    _state.update { it.copy(saving = false, dirty = false) }
                    _effects.send(TripFormEffect.Saved(r.trip.id, created = tripId == null))
                }
                is TripResult.Rejected -> when (r.problem) {
                    TripProblem.PaidOnly -> {
                        _state.update { it.copy(saving = false) }
                        _effects.send(TripFormEffect.Upgrade)
                    }
                    TripProblem.TitleBlank, TripProblem.EndBeforeStart, TripProblem.TooLong, TripProblem.StopsOutsideDates -> {
                        val field = if (r.problem == TripProblem.TitleBlank) TripField.Title else TripField.End
                        _state.update {
                            it.copy(
                                saving = false,
                                titleError = if (field == TripField.Title) r.problem.text() else null,
                                endError = if (field == TripField.End) r.problem.text(r.dates) else null,
                                focusError = field,
                                errorShownCount = it.errorShownCount + 1,
                            )
                        }
                    }
                    // 날짜 칸의 문제가 아니다. 일반 안내로 보인다(PLN-M7).
                    else -> _state.update { it.copy(saving = false, message = r.problem.text(r.dates)) }
                }
                TripResult.SaveFailed -> _state.update { it.copy(saving = false, message = UiText.Res(R.string.common_save_failed)) }
            }
        }
    }
}

// ---------------------------------------------------------------- SC15

data class AddToPlanUiState(
    val loading: Boolean = true,
    val trips: List<Trip> = emptyList(),
    val selectedTrip: TripId? = null,
    val selectedDate: LocalDate? = null,
    val busy: Boolean = false,
    val message: UiText? = null,
) {
    val noTrips: Boolean get() = !loading && trips.isEmpty()
    val trip: Trip? get() = trips.firstOrNull { it.id == selectedTrip }
}

sealed interface AddToPlanAction {
    data object Refresh : AddToPlanAction
    data class SelectTrip(val id: TripId) : AddToPlanAction
    data class SelectDate(val date: LocalDate) : AddToPlanAction
    data object Add : AddToPlanAction
}

sealed interface AddToPlanEffect {
    data class Added(val date: LocalDate) : AddToPlanEffect
}

/** SC15 여행·날짜 고르기 (FR18). 장소 목록·장소 상세의 Add to plan 이 연다. 여행이 없으면 SC12 를 거쳐 돌아온다. */
class AddToPlanViewModel(
    private val place: PlaceId,
    private val store: TripStore,
    private val editor: TripEditor,
) : ViewModel() {
    private val _state = MutableStateFlow(AddToPlanUiState())
    val state: StateFlow<AddToPlanUiState> = _state.asStateFlow()
    private val _effects = Channel<AddToPlanEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<AddToPlanEffect> = _effects

    init {
        viewModelScope.launch { load() }
    }

    /** 지난번에 본 여행들. 다시 읽었을 때 새로 생긴 여행(SC12 에서 방금 만든 것)을 고른다(PLN-M1, UI03). */
    private var known: Set<TripId>? = null

    private suspend fun load() {
        val trips = try {
            store.all()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 못 읽었다고 "여행 없음"으로 보이지 않는다(PLN-L5).
            _state.update { it.copy(loading = false, message = UiText.Res(R.string.common_load_failed)) }
            return
        }
        val before = known
        known = trips.map { it.id }.toSet()
        val created = if (before == null) null else trips.lastOrNull { it.id !in before }
        _state.update { s ->
            val trip = created ?: trips.firstOrNull { it.id == s.selectedTrip } ?: trips.firstOrNull()
            val keepDate = created == null && trip != null && s.selectedDate?.let { d -> !d.isBefore(trip.start) && !d.isAfter(trip.end) } == true
            // 다시 열면 지난 안내(중복 등)를 지운다(PLN-L4).
            s.copy(loading = false, trips = trips, selectedTrip = trip?.id, selectedDate = if (keepDate) s.selectedDate else trip?.start, message = null)
        }
    }

    fun onAction(action: AddToPlanAction) {
        when (action) {
            AddToPlanAction.Refresh -> viewModelScope.launch { load() }
            is AddToPlanAction.SelectTrip -> _state.update { s ->
                val t = s.trips.firstOrNull { it.id == action.id }
                s.copy(selectedTrip = action.id, selectedDate = t?.start, message = null)
            }
            is AddToPlanAction.SelectDate -> _state.update { it.copy(selectedDate = action.date, message = null) }
            AddToPlanAction.Add -> add()
        }
    }

    private fun add() {
        val s = _state.value
        val trip = s.selectedTrip ?: return
        val date = s.selectedDate ?: return
        if (s.busy) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            when (val r = editor.addStop(trip, date, place.value)) {
                is TripResult.Ok -> {
                    _state.update { it.copy(busy = false, trips = it.trips.map { t -> if (t.id == trip) r.trip else t }) }
                    _effects.send(AddToPlanEffect.Added(date))
                }
                is TripResult.Rejected -> _state.update { it.copy(busy = false, message = r.problem.text(r.dates)) }
                TripResult.SaveFailed -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.common_save_failed)) }
            }
        }
    }
}

// ---------------------------------------------------------------- SC14

data class PickPlaceUi(val place: Place, val added: Boolean)

data class PlacePickerUiState(
    val loading: Boolean = true,
    val query: String = "",
    val results: List<PickPlaceUi> = emptyList(),
    val message: UiText? = null,
    val busy: Boolean = false,
)

sealed interface PlacePickerAction {
    data class Query(val text: String) : PlacePickerAction
    data class Add(val place: PlaceId) : PlacePickerAction
}

/** SC14 하루에 넣을 등록 장소 고르기 (FR18). 이름(사용자 언어·한국어)으로 찾는다. 이미 넣은 곳은 표시한다. */
class PlacePickerViewModel(
    private val tripId: TripId,
    private val date: LocalDate,
    private val store: TripStore,
    private val editor: TripEditor,
    private val content: ContentRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PlacePickerUiState())
    val state: StateFlow<PlacePickerUiState> = _state.asStateFlow()
    private var places: List<Place> = emptyList()
    private var added: Set<PlaceId> = emptySet()

    init {
        viewModelScope.launch {
            places = runCatching { PlaceListing.visiblePlaces(content.load()) }.getOrDefault(emptyList())
            added = runCatching { store.get(tripId)?.day(date)?.stops?.map { it.place }?.toSet() }.getOrNull().orEmpty()
            _state.update { it.copy(loading = false) }
            filter()
        }
    }

    fun onAction(action: PlacePickerAction) {
        when (action) {
            is PlacePickerAction.Query -> {
                _state.update { it.copy(query = action.text) }
                filter()
            }
            // 빠른 두 번 탭이 "이미 있음" 오류로 보이지 않게 한 번에 하나(PLN-L6).
            is PlacePickerAction.Add -> if (!_state.value.busy) viewModelScope.launch {
                _state.update { it.copy(busy = true) }
                val r = editor.addStop(tripId, date, action.place.value)
                _state.update { it.copy(busy = false) }
                when (r) {
                    is TripResult.Ok -> {
                        added = r.trip.day(date).stops.map { it.place }.toSet()
                        _state.update { it.copy(message = null) }
                        filter()
                    }
                    is TripResult.Rejected -> _state.update { it.copy(message = r.problem.text(r.dates)) }
                    TripResult.SaveFailed -> _state.update { it.copy(message = UiText.Res(R.string.common_save_failed)) }
                }
            }
        }
    }

    private fun filter() {
        val q = _state.value.query.trim().lowercase()
        val hits = places.filter { p ->
            q.isEmpty() || p.koName.lowercase().contains(q) || p.name.locales.any { tag -> p.name[tag]?.lowercase()?.contains(q) == true }
        }
        _state.update { it.copy(results = hits.map { p -> PickPlaceUi(p, p.id in added) }) }
    }
}
