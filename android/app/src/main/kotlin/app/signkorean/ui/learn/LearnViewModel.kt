package app.signkorean.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardPlaceLinker
import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.SignCard
import app.signkorean.domain.learning.Face
import app.signkorean.domain.learning.JudgeCard
import app.signkorean.domain.learning.JudgeOutcome
import app.signkorean.domain.learning.LearningSession
import app.signkorean.domain.learning.SessionReducer
import app.signkorean.domain.learning.SessionRules
import app.signkorean.domain.learning.SessionStore
import app.signkorean.domain.learning.UndoJudgement
import app.signkorean.domain.learning.UndoOutcome
import app.signkorean.domain.learning.Verdict
import app.signkorean.ui.text.UiText
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** SC04 Learn 화면 상태 (설계서 §5.3). 문구는 리소스 ID 로만 담는다. */
data class LearnUiState(
    val loading: Boolean = true,
    /** 진행 중 세션이 없다. 화면은 Explore 로 돌아갈 길을 보인다. */
    val noSession: Boolean = false,
    val card: SignCard? = null,
    val face: Face = Face.Front,
    /** 장소 없는 카드(Basics)는 아래 방향·장소 버튼이 없다 (FR31, UX11). */
    val hasPlaces: Boolean = false,
    val decisions: Int = 0,
    val maxDecisions: Int = SessionRules.MAX_DECISIONS,
    val isTryAgain: Boolean = false,
    /** 저장 중에는 입력을 받지 않는다 (UX03). */
    val saving: Boolean = false,
    /** 저장 실패. 같은 카드에 머문다 (FR07). */
    val error: UiText? = null,
    val canUndo: Boolean = false,
    val finished: Boolean = false,
)

sealed interface LearnAction {
    data class Judge(val verdict: Verdict) : LearnAction
    data object Flip : LearnAction
    data object Undo : LearnAction
    data object OpenPlaces : LearnAction
    data object Retry : LearnAction
}

/** 한 번만 처리할 일. 화면 재구성으로 두 번 처리되지 않게 Channel 로 낸다 (설계서 §5.3). */
sealed interface LearnEffect {
    data class OpenPlaces(val card: CardId) : LearnEffect
    data object Finished : LearnEffect
}

class LearnViewModel(
    private val content: ContentRepository,
    private val sessions: SessionStore,
    private val judge: JudgeCard,
    private val undo: UndoJudgement,
) : ViewModel() {

    private val _state = MutableStateFlow(LearnUiState())
    val state: StateFlow<LearnUiState> = _state.asStateFlow()

    private val _effects = Channel<LearnEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<LearnEffect> = _effects

    private var pack: ContentPack? = null
    private var session: LearningSession? = null

    /** 저장에 실패한 입력. 다시 시도하면 같은 입력을 보낸다. */
    private var failed: LearnAction? = null

    init {
        viewModelScope.launch {
            pack = content.load()
            val current = sessions.current()
            if (current == null) {
                _state.value = LearnUiState(loading = false, noSession = true)
            } else {
                show(current)
            }
        }
    }

    fun onAction(action: LearnAction) {
        val s = _state.value
        if (s.loading || s.saving) return
        when (action) {
            LearnAction.Flip -> session?.let { show(SessionReducer.flip(it)) }
            LearnAction.OpenPlaces -> if (s.hasPlaces) s.card?.let { _effects.trySend(LearnEffect.OpenPlaces(it.id)) }
            is LearnAction.Judge -> save(action)
            LearnAction.Undo -> if (s.canUndo) save(action)
            LearnAction.Retry -> failed?.let { save(it) }
        }
    }

    private fun save(action: LearnAction) {
        val current = session ?: return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val next: LearningSession? = when (action) {
                is LearnAction.Judge -> (judge(current.id, current.decisionSeq, action.verdict) as? JudgeOutcome.Judged)?.session
                LearnAction.Undo -> (undo(current.id, current.decisionSeq) as? UndoOutcome.Undone)?.session
                else -> null
            }
            if (next == null) {
                failed = action
                _state.update { it.copy(saving = false, error = UiText.Res(R.string.common_save_failed)) }
                return@launch
            }
            failed = null
            show(next)
            if (next.finished) _effects.send(LearnEffect.Finished)
        }
    }

    private fun show(s: LearningSession) {
        session = s
        val p = pack
        val card = s.current?.let { id -> p?.cards?.firstOrNull { it.id == id } }
        _state.value = LearnUiState(
            loading = false,
            card = card,
            face = s.face,
            hasPlaces = card != null && p != null && CardPlaceLinker.hasPlaces(card, p.places),
            decisions = s.decisions.size,
            isTryAgain = s.isTryAgain,
            canUndo = s.canUndo,
            finished = s.finished,
        )
    }
}
