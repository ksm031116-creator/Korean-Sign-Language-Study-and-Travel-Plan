package app.signkorean.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.Place
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.location.LocationProvider
import app.signkorean.domain.location.SeoulArea
import app.signkorean.domain.location.StartLocation
import app.signkorean.domain.location.StartLocationProblem
import app.signkorean.domain.location.StartLocations
import app.signkorean.domain.location.SuggestedStart
import app.signkorean.domain.place.PlaceListing
import app.signkorean.domain.trip.Origin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class OriginPickerUiState(
    val query: String = "",
    val places: List<Place> = emptyList(),
    val suggestions: List<SuggestedStart> = SeoulArea.suggestedStarts,
    /** "현재 위치" 설명을 보이는 중. OS 권한 창은 사용자가 계속할 때만 띄운다. */
    val explaining: Boolean = false,
    val locating: Boolean = false,
    /** 현재 위치를 못 쓴 이유. 수동 선택은 그대로 된다(M-02). */
    val problem: StartLocationProblem? = null,
    val loadFailed: Boolean = false,
)

sealed interface OriginPickerAction {
    data class Query(val text: String) : OriginPickerAction
    data class PickPlace(val place: PlaceId) : OriginPickerAction

    /** [label] 은 화면이 현지화한 이름. 일정에 그대로 저장된다. */
    data class PickSuggested(val key: String, val label: String) : OriginPickerAction
    data object UseCurrentLocation : OriginPickerAction
    data object ConfirmExplain : OriginPickerAction
    data object DismissExplain : OriginPickerAction
    data class PermissionResult(val granted: Boolean) : OriginPickerAction
}

sealed interface OriginPickerEffect {
    data class Picked(val origin: Origin) : OriginPickerEffect
    data object RequestPermission : OriginPickerEffect
}

/**
 * SC19 출발지 (FR14, 구현 계획 Phase 19 D3): 등록 장소 검색·추천 출발점(서울역·시청)·현재 위치.
 * 현재 위치는 사용자가 고르고 설명을 본 뒤에만 OS 권한을 묻고 한 번 읽는다. 저장은 하루 출발지로 Apply 할 때뿐이다(PRD §10.4).
 */
class OriginPickerViewModel(
    private val content: ContentRepository,
    private val location: LocationProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(OriginPickerUiState())
    val state: StateFlow<OriginPickerUiState> = _state.asStateFlow()
    private val _effects = Channel<OriginPickerEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<OriginPickerEffect> = _effects

    private var all: List<Place> = emptyList()

    init {
        viewModelScope.launch {
            try {
                all = PlaceListing.visiblePlaces(content.load())
                _state.update { it.copy(places = filter(it.query)) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loadFailed = true) }
            }
        }
    }

    fun onAction(action: OriginPickerAction) {
        when (action) {
            is OriginPickerAction.Query -> _state.update { it.copy(query = action.text, places = filter(action.text)) }
            is OriginPickerAction.PickPlace -> send(OriginPickerEffect.Picked(Origin.AtPlace(action.place)))
            is OriginPickerAction.PickSuggested -> SeoulArea.suggestedStarts.firstOrNull { it.key == action.key }?.let {
                send(OriginPickerEffect.Picked(Origin.AtCoordinate(it.coordinate, action.label)))
            }
            OriginPickerAction.UseCurrentLocation -> _state.update { it.copy(explaining = true, problem = null) }
            OriginPickerAction.DismissExplain -> _state.update { it.copy(explaining = false) }
            OriginPickerAction.ConfirmExplain -> {
                _state.update { it.copy(explaining = false) }
                send(OriginPickerEffect.RequestPermission)
            }
            is OriginPickerAction.PermissionResult -> if (!action.granted) {
                _state.update { it.copy(problem = StartLocationProblem.Denied) }
            } else {
                locate()
            }
        }
    }

    private fun locate() {
        if (_state.value.locating) return
        _state.update { it.copy(locating = true, problem = null) }
        viewModelScope.launch {
            when (val r = StartLocations.locate(location)) {
                is StartLocation.Found -> {
                    _state.update { it.copy(locating = false) }
                    // 이름은 붙이지 않는다. 화면이 "현재 위치"로 보인다. 정확한 좌표는 로그에 남기지 않는다(NFR05).
                    _effects.send(OriginPickerEffect.Picked(Origin.AtCoordinate(r.coordinate, null)))
                }
                is StartLocation.Problem -> _state.update { it.copy(locating = false, problem = r.problem) }
            }
        }
    }

    private fun send(effect: OriginPickerEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    /** 한국어 이름과 콘텐츠의 각 언어 이름에서 찾는다. 대소문자를 가리지 않는다. */
    private fun filter(query: String): List<Place> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return all
        return all.filter { p ->
            sequenceOf(p.koName, p.name[EN], p.name[JA]).filterNotNull().any { it.lowercase(Locale.ROOT).contains(q) }
        }
    }

    private companion object {
        val EN = LocaleTag("en")
        val JA = LocaleTag("ja")
    }
}
