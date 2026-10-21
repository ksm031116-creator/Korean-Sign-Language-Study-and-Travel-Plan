package app.signkorean.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.SignCard
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.EntitlementSource
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.LearningStateStore
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.StartOutcome
import app.signkorean.domain.learning.StartSession
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.trip.TripStore
import app.signkorean.domain.trip.TripStudy
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

data class DayStudyUiState(
    val loading: Boolean = true,
    val cardCount: Int = 0,
    val learningCount: Int = 0,
    val mode: Mode = Mode.All,
    /** Study this day 는 유료 기능이다(PRD §4). 무료면 자물쇠 + 기능명. */
    val locked: Boolean = false,
    val busy: Boolean = false,
    /** 끝나지 않은 다른 세션이 있어 교체를 묻는 중(SC20). */
    val confirmReplace: Boolean = false,
    /** 읽지 못했다. "카드 없음"과 다르다 — 다시 시도할 수 있다(STY-M2). */
    val loadFailed: Boolean = false,
    val message: UiText? = null,
) {
    /** 그날 장소에 카드가 없다(T-15). */
    val noCards: Boolean get() = !loading && !loadFailed && cardCount == 0
    val canReview: Boolean get() = learningCount > 0
}

sealed interface DayStudyAction {
    data class SetMode(val mode: Mode) : DayStudyAction
    data object Start : DayStudyAction
    data object ConfirmReplace : DayStudyAction
    data object KeepSession : DayStudyAction
    data object Refresh : DayStudyAction
}

sealed interface DayStudyEffect {
    data object OpenLearn : DayStudyEffect
    data class Upgrade(val feature: UpgradeFeature) : DayStudyEffect
}

/**
 * SC13 의 Study this day (FR21, UI05). 그날 일정의 카드를 방문 순서대로 모아(T-13) All 또는 Review 로 시작한다.
 * 판정은 다른 학습과 같은 카드 상태를 쓴다. 카드 목록은 시작할 때 세션에 저장된다(T-14).
 */
class DayStudyViewModel(
    private val tripId: TripId,
    private val date: LocalDate,
    private val trips: TripStore,
    private val content: ContentRepository,
    private val states: LearningStateStore,
    private val start: StartSession,
    private val entitlement: EntitlementSource,
) : ViewModel() {

    private val _state = MutableStateFlow(DayStudyUiState())
    val state: StateFlow<DayStudyUiState> = _state.asStateFlow()
    private val _effects = Channel<DayStudyEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<DayStudyEffect> = _effects

    private var cards: List<SignCard> = emptyList()
    private var loadJob: Job? = null

    init {
        reload()
    }

    private fun reload() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val locked = try {
                entitlement.current() != Entitlement.Paid
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                true
            }
            val pack = content.load()
            val day = trips.get(tripId)?.days?.firstOrNull { it.date == date }
            cards = day?.let { TripStudy.cards(pack, it) }.orEmpty()
            val statuses = states.all()
            val learning = cards.count { statuses[it.id] == CardStatus.Learning }
            _state.update {
                it.copy(
                    loading = false, loadFailed = false, locked = locked, cardCount = cards.size, learningCount = learning,
                    // 복습할 카드가 없어졌으면 All 로 돌린다.
                    mode = if (learning == 0) Mode.All else it.mode,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            cards = emptyList()
            _state.update { it.copy(loading = false, loadFailed = true, cardCount = 0) }
        }
    }

    fun onAction(action: DayStudyAction) {
        val s = _state.value
        if (action == DayStudyAction.Refresh) {
            if (!s.busy) reload()
            return
        }
        if (s.busy) return
        when (action) {
            DayStudyAction.Refresh -> Unit
            is DayStudyAction.SetMode -> if (action.mode == Mode.All || s.canReview) _state.update { it.copy(mode = action.mode, message = null) }
            DayStudyAction.Start -> begin(replace = false)
            DayStudyAction.ConfirmReplace -> begin(replace = true)
            DayStudyAction.KeepSession -> _state.update { it.copy(confirmReplace = false) }
        }
    }

    private fun begin(replace: Boolean) {
        val s = _state.value
        if (s.locked) {
            viewModelScope.launch { _effects.send(DayStudyEffect.Upgrade(UpgradeFeature.Plan)) }
            return
        }
        if (s.noCards) return
        _state.update { it.copy(busy = true, confirmReplace = false, message = null) }
        viewModelScope.launch {
            val outcome = try {
                // 시작할 때 지금 일정을 다시 읽는다. 방금 바꾼 일정이 아직 화면 숫자에 반영되기 전이어도 새 세션은 바뀐 일정을 쓴다(T-14).
                val day = trips.get(tripId)?.days?.firstOrNull { it.date == date }
                cards = day?.let { TripStudy.cards(content.load(), it) }.orEmpty()
                start(TripStudy.source(tripId, date), s.mode, cards, replaceUnfinished = replace)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                StartOutcome.SaveFailed
            }
            when (outcome) {
                is StartOutcome.Started, is StartOutcome.Resumed -> {
                    _state.update { it.copy(busy = false) }
                    _effects.send(DayStudyEffect.OpenLearn)
                }
                is StartOutcome.NeedsConfirmation -> _state.update { it.copy(busy = false, confirmReplace = true) }
                StartOutcome.NoCards -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.study_no_cards)) }
                StartOutcome.NothingToReview -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.result_nothing_to_review)) }
                StartOutcome.SaveFailed -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.common_save_failed)) }
            }
        }
    }
}
