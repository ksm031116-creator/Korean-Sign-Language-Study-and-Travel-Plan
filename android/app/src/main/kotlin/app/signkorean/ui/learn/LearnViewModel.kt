package app.signkorean.ui.learn

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.audio.AudioPlayer
import app.signkorean.domain.bookmark.BookmarkOutcome
import app.signkorean.domain.bookmark.BookmarkStore
import app.signkorean.domain.bookmark.SetBookmark
import app.signkorean.domain.audio.PlaybackState
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
import app.signkorean.domain.onboarding.OnboardingStore
import app.signkorean.domain.upgrade.PendingAction
import app.signkorean.domain.upgrade.PendingActionStore
import app.signkorean.ui.text.UiText
import kotlinx.coroutines.CancellationException
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
    /** 진행 중 세션이 없거나 카드를 찾을 수 없다. 화면은 Explore 로 돌아갈 길을 보인다. */
    val noSession: Boolean = false,
    /** 세션·콘텐츠를 읽지 못했다. 다시 시도로 다시 읽는다. */
    val loadFailed: Boolean = false,
    val card: SignCard? = null,
    val face: Face = Face.Front,
    /** 장소 없는 카드(Basics)는 아래 방향·장소 버튼이 없다 (FR31, UX11). */
    val hasPlaces: Boolean = false,
    val decisions: Int = 0,
    val maxDecisions: Int = SessionRules.MAX_DECISIONS,
    /** 세션 판정 순번. 같은 카드가 다시 나와도(재출제·Undo·저장 실패) 화면이 카드 위치를 되돌리는 기준이다. */
    val seq: Int = 0,
    val isTryAgain: Boolean = false,
    /** 저장 중에는 입력을 받지 않는다 (UX03). */
    val saving: Boolean = false,
    /** 저장 실패. 같은 카드에 머문다 (FR07). */
    val error: UiText? = null,
    val canUndo: Boolean = false,
    val finished: Boolean = false,
    /** 첫 진입 스와이프 시연을 보일지 (UX11 ③). */
    val showCoach: Boolean = false,
    /** 스크린리더에 알릴 판정 결과 (U-03). 화면이 알림 영역에 한 번 싣는다. */
    val announcement: UiText? = null,
    /** 이 카드의 발음 버튼 상태 (FR29, UX10, U-12). */
    val audio: AudioUi = AudioUi.Ready,
    /** 이 카드가 저장돼 있는지 (FR12). */
    val bookmarked: Boolean = false,
    /** 한 번 보이고 사라지는 안내(무료 한도 등). */
    val notice: UiText? = null,
)

/** Ready: 누르면 재생. Playing: 재생 중(다시 누르면 처음부터). Unavailable: 음성 없음·실패. */
enum class AudioUi { Ready, Playing, Unavailable }

sealed interface LearnAction {
    data class Judge(val verdict: Verdict) : LearnAction
    data object Flip : LearnAction
    data object Undo : LearnAction
    data object OpenPlaces : LearnAction
    data object Retry : LearnAction
    data object DismissCoach : LearnAction

    /** 발음 듣기. 판정이 아니며 학습 상태를 바꾸지 않는다 (FR29, L-11). */
    data object PlayAudio : LearnAction

    /** 화면을 떠나거나 앱이 뒤로 갈 때. */
    data object StopAudio : LearnAction

    /** 카드 저장·해제. 판정이 아니며 카드를 뒤집거나 넘기지 않는다 (FR12). */
    data object ToggleBookmark : LearnAction

    /** 화면에 다시 들어왔을 때(SC21 에서 결제 뒤 돌아옴 등) 저장 여부를 다시 읽는다. */
    data object RefreshBookmarks : LearnAction
}

/** 한 번만 처리할 일. 화면 재구성으로 두 번 처리되지 않게 Channel 로 낸다 (설계서 §5.3). */
sealed interface LearnEffect {
    data class OpenPlaces(val card: CardId) : LearnEffect
    data object Finished : LearnEffect

    /** 무료 한도에서 6번째 저장 (FR27). 화면은 SC21 을 연다. 저장은 보류 동작으로 남겨 결제 뒤 이어서 한다. */
    data class BookmarkLimit(val count: Int, val limit: Int) : LearnEffect
}

class LearnViewModel(
    private val content: ContentRepository,
    private val sessions: SessionStore,
    private val judge: JudgeCard,
    private val undo: UndoJudgement,
    private val onboarding: OnboardingStore,
    private val audio: AudioPlayer,
    private val bookmarks: BookmarkStore,
    private val setBookmark: SetBookmark,
    private val pendingActions: PendingActionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LearnUiState())
    val state: StateFlow<LearnUiState> = _state.asStateFlow()

    private val _effects = Channel<LearnEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<LearnEffect> = _effects

    private var pack: ContentPack? = null
    private var session: LearningSession? = null

    /** 저장에 실패한 입력. 다시 시도하면 같은 입력을 보낸다. */
    private var failed: LearnAction? = null

    /** 시연을 아직 보지 않았다. 닫으면 저장하고 다시 보이지 않는다(설정에서 다시 보기). */
    private var coach = false

    init {
        load()
        viewModelScope.launch {
            audio.state.collect { playback -> _state.update { it.copy(audio = audioUi(it.card, playback)) } }
        }
    }

    private fun audioUi(card: SignCard?, playback: PlaybackState): AudioUi = when {
        card?.audio == null -> AudioUi.Unavailable
        playback is PlaybackState.Playing && playback.clip == card.audio -> AudioUi.Playing
        // 이 카드의 음성이 실패했을 때만(늦게 난 오류 포함) "음성 없음"이다.
        playback is PlaybackState.Unavailable && playback.clip == card.audio -> AudioUi.Unavailable
        else -> AudioUi.Ready
    }

    /** 이 화면이 마지막으로 튼 음성. 재생기는 앱에 하나라서 남이 튼 음성은 멈추지 않는다. */
    private var playedByMe: app.signkorean.domain.content.AudioClip? = null

    private fun stopMine() {
        val playing = (audio.state.value as? PlaybackState.Playing)?.clip
        if (playing != null && playing == playedByMe) audio.stop()
        playedByMe = null
    }

    override fun onCleared() {
        stopMine()
    }

    /** 콘텐츠와 현재 세션을 읽는다. 실패하면 앱을 멈추지 않고 다시 시도할 수 있는 상태로 둔다. */
    private fun load() {
        _state.value = LearnUiState(loading = true)
        viewModelScope.launch {
            try {
                pack = content.load()
                coach = runCatching { !onboarding.coachSeen() }.getOrDefault(false)
                savedIds = runCatching { bookmarks.all().map { it.card }.toSet() }.getOrDefault(emptySet())
                val current = sessions.current()
                when {
                    current == null -> _state.value = LearnUiState(loading = false, noSession = true)
                    current.finished -> {
                        show(current)
                        _effects.send(LearnEffect.Finished)
                    }
                    else -> show(current)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = LearnUiState(loading = false, loadFailed = true)
            }
        }
    }

    fun onAction(action: LearnAction) {
        val s = _state.value
        if (action == LearnAction.Retry && s.loadFailed) {
            load()
            return
        }
        if (action == LearnAction.PlayAudio) {
            val card = s.card ?: return
            if (card.audio == null) return
            playedByMe = card.audio
            audio.play(card.audio)
            _state.update { it.copy(audio = audioUi(it.card, audio.state.value)) }
            return
        }
        if (action == LearnAction.StopAudio) {
            stopMine()
            return
        }
        if (action == LearnAction.ToggleBookmark) {
            val card = s.card ?: return
            val target = !s.bookmarked
            _state.update { it.copy(bookmarked = target, notice = null) } // 바로 보이고, 실패하면 되돌린다
            viewModelScope.launch {
                when (val o = setBookmark(card.id, target)) {
                    BookmarkOutcome.Saved, BookmarkOutcome.Removed, BookmarkOutcome.Unchanged ->
                        savedIds = if (target) savedIds + card.id else savedIds - card.id
                    is BookmarkOutcome.NeedsUpgrade -> {
                        // SC21 을 열기 전에 보류해 둔다. 결제 중 프로세스가 끝나도 복귀 후 이어서 저장된다(B-07).
                        try {
                            pendingActions.save(PendingAction.SaveBookmark(card.id))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                        _state.update { if (it.card?.id == card.id) it.copy(bookmarked = false, notice = UiText.Res(R.string.bookmark_limit, listOf(o.count, o.limit))) else it }
                        _effects.send(LearnEffect.BookmarkLimit(o.count, o.limit))
                    }
                    BookmarkOutcome.SaveFailed ->
                        _state.update { if (it.card?.id == card.id) it.copy(bookmarked = !target, notice = UiText.Res(R.string.common_save_failed)) else it }
                }
            }
            return
        }
        if (action == LearnAction.RefreshBookmarks) {
            viewModelScope.launch {
                val ids = try {
                    bookmarks.all().map { it.card }.toSet()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    return@launch
                }
                savedIds = ids
                _state.update { it.copy(bookmarked = it.card != null && it.card.id in ids) }
            }
            return
        }
        if (action == LearnAction.DismissCoach) {
            coach = false
            _state.update { it.copy(showCoach = false) }
            viewModelScope.launch { runCatching { onboarding.markCoachSeen() } }
            return
        }
        if (s.loading || s.saving) return
        when (action) {
            // 뒤집기는 면만 바꾼다. 저장 오류 안내와 다시 시도는 그대로 둔다.
            LearnAction.Flip -> session?.let { cur ->
                val flipped = SessionReducer.flip(cur)
                session = flipped
                _state.update { it.copy(face = flipped.face) }
            }
            LearnAction.OpenPlaces -> if (s.hasPlaces) s.card?.let { _effects.trySend(LearnEffect.OpenPlaces(it.id)) }
            is LearnAction.Judge -> save(action)
            LearnAction.Undo -> if (s.canUndo) save(action)
            LearnAction.Retry -> failed?.let { save(it) }
            LearnAction.DismissCoach, LearnAction.PlayAudio, LearnAction.StopAudio, LearnAction.ToggleBookmark,
            LearnAction.RefreshBookmarks -> Unit // 위에서 처리했다
        }
    }

    private fun save(action: LearnAction) {
        val current = session ?: return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result: Result = when (action) {
                is LearnAction.Judge -> when (val o = judge(current.id, current.decisionSeq, action.verdict)) {
                    is JudgeOutcome.Judged -> Result.Saved(o.session)
                    JudgeOutcome.StaleInput -> Result.Stale
                    JudgeOutcome.NoSession -> Result.Gone
                    JudgeOutcome.SaveFailed -> Result.Failed
                }
                LearnAction.Undo -> when (val o = undo(current.id, current.decisionSeq)) {
                    is UndoOutcome.Undone -> Result.Saved(o.session)
                    UndoOutcome.StaleInput, UndoOutcome.NothingToUndo -> Result.Stale
                    UndoOutcome.SaveFailed -> Result.Failed
                }
                else -> return@launch
            }
            when (result) {
                is Result.Saved -> {
                    failed = null
                    show(result.session, announce(action))
                    if (result.session.finished) _effects.send(LearnEffect.Finished)
                }
                // 이미 지난 입력(중복 탭, 다른 화면에서 바뀐 세션)은 오류가 아니다. 저장된 세션을 다시 읽어 보여 준다.
                Result.Stale -> {
                    failed = null
                    reloadSession()
                }
                Result.Gone -> {
                    failed = null
                    stopMine()
                    _state.value = LearnUiState(loading = false, noSession = true)
                }
                Result.Failed -> {
                    failed = action
                    _state.update { it.copy(saving = false, error = UiText.Res(R.string.common_save_failed)) }
                }
            }
        }
    }

    private suspend fun reloadSession() {
        val current = try {
            sessions.current()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (current == null) _state.value = LearnUiState(loading = false, noSession = true) else show(current)
    }

    private fun announce(action: LearnAction): UiText? = when (action) {
        is LearnAction.Judge -> UiText.Res(if (action.verdict == Verdict.Know) R.string.learn_announce_know else R.string.learn_announce_dont_know)
        LearnAction.Undo -> UiText.Res(R.string.learn_announce_undo)
        else -> null
    }

    private fun show(s: LearningSession, announcement: UiText? = null) {
        session = s
        val p = pack
        val card = s.current?.let { id -> p?.cards?.firstOrNull { it.id == id } }
        // 다른 카드로 넘어가거나 같은 카드가 다시 나오면(순번이 바뀜) 재생을 멈춘다(설계서 §7.4).
        if (card?.id != _state.value.card?.id || s.decisionSeq != _state.value.seq) stopMine()
        if (!s.finished && card == null) {
            // 콘텐츠 갱신으로 카드가 사라졌다. 빈 화면 대신 돌아갈 길을 보인다.
            _state.value = LearnUiState(loading = false, noSession = true)
            return
        }
        _state.value = LearnUiState(
            loading = false,
            card = card,
            face = s.face,
            hasPlaces = card != null && p != null && CardPlaceLinker.hasPlaces(card, p.places),
            decisions = s.decisions.size,
            seq = s.decisionSeq,
            isTryAgain = s.isTryAgain,
            canUndo = s.canUndo,
            finished = s.finished,
            showCoach = coach && !s.finished,
            announcement = announcement,
            audio = audioUi(card, audio.state.value),
            bookmarked = card != null && card.id in savedIds,
        )
    }

    /** 저장된 카드 id. 카드가 바뀔 때마다 다시 읽지 않도록 들고 있다가 저장·해제 때 고친다. */
    private var savedIds: Set<CardId> = emptySet()

    private sealed interface Result {
        data class Saved(val session: LearningSession) : Result
        data object Stale : Result
        data object Gone : Result
        data object Failed : Result
    }
}
