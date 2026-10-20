package app.signkorean.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.learning.LearningSession
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.SessionStore
import app.signkorean.domain.learning.SessionSummary
import app.signkorean.domain.learning.SourceCards
import app.signkorean.domain.learning.StartOutcome
import app.signkorean.domain.learning.StartSession
import app.signkorean.ui.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** SC06 결과 화면 상태 (FR08). */
data class ResultUiState(
    val loading: Boolean = true,
    val summary: SessionSummary? = null,
    /** 10회를 다 채웠는지(완료) 대기열이 먼저 비었는지. */
    val reachedLimit: Boolean = false,
    val canReview: Boolean = false,
    val busy: Boolean = false,
    val message: UiText? = null,
)

sealed interface ResultAction {
    data object ReviewAgain : ResultAction
    data object PracticeAll : ResultAction
    data object Done : ResultAction
}

sealed interface ResultEffect {
    data object OpenLearn : ResultEffect
    data object Close : ResultEffect
}

class ResultViewModel(
    private val content: ContentRepository,
    private val sessions: SessionStore,
    private val start: StartSession,
) : ViewModel() {

    private val _state = MutableStateFlow(ResultUiState())
    val state: StateFlow<ResultUiState> = _state.asStateFlow()

    private val _effects = Channel<ResultEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<ResultEffect> = _effects

    private var session: LearningSession? = null

    init {
        viewModelScope.launch {
            val s = try {
                sessions.current()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            session = s
            _state.value = if (s == null) {
                ResultUiState(loading = false)
            } else {
                val summary = SessionSummary.of(s)
                ResultUiState(
                    loading = false,
                    summary = summary,
                    reachedLimit = s.decisions.size >= app.signkorean.domain.learning.SessionRules.MAX_DECISIONS,
                    canReview = summary.remainingReview > 0,
                )
            }
        }
    }

    fun onAction(action: ResultAction) {
        if (_state.value.busy) return
        when (action) {
            ResultAction.Done -> _effects.trySend(ResultEffect.Close)
            ResultAction.ReviewAgain -> restart(Mode.Review)
            ResultAction.PracticeAll -> restart(Mode.All)
        }
    }

    /** 같은 출처로 새 세션. 결과 화면의 세션은 끝났으므로 교체 확인이 필요 없다. */
    private fun restart(mode: Mode) {
        val source = session?.source ?: return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val outcome = try {
                start(source, mode, SourceCards.of(content.load(), source), replaceUnfinished = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StartOutcome.SaveFailed
            }
            when (outcome) {
                is StartOutcome.Started, is StartOutcome.Resumed -> {
                    _state.update { it.copy(busy = false) }
                    _effects.send(ResultEffect.OpenLearn)
                }
                StartOutcome.NothingToReview -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.result_nothing_to_review)) }
                StartOutcome.NoCards -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.result_no_cards)) }
                else -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.common_save_failed)) }
            }
        }
    }
}
