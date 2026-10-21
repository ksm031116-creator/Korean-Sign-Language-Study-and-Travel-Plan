package app.signkorean.ui.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.audio.AudioPlayer
import app.signkorean.domain.audio.PlaybackState
import app.signkorean.domain.bookmark.BookmarkOutcome
import app.signkorean.domain.bookmark.BookmarkPolicy
import app.signkorean.domain.bookmark.BookmarkStore
import app.signkorean.domain.bookmark.SetBookmark
import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardPlaceLinker
import app.signkorean.domain.place.PlaceListing
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.SignCard
import app.signkorean.domain.content.SignGroup
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.EntitlementSource
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.LearningStateStore
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.StartOutcome
import app.signkorean.domain.learning.StartSession
import app.signkorean.ui.text.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Saved 한 줄. [card] 가 null 이면 콘텐츠에서 사라진 카드다(Unavailable, 해제만 가능, B-05). */
data class SavedItem(
    val id: CardId,
    val card: SignCard?,
    val group: SignGroup?,
    val placeCount: Int,
    val areas: Set<AreaId>,
    val status: CardStatus,
) {
    val unavailable: Boolean get() = card == null
}

data class SavedFilters(val group: String? = null, val area: AreaId? = null, val status: CardStatus? = null) {
    val active: Boolean get() = group != null || area != null || status != null
}

data class SavedUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val all: List<SavedItem> = emptyList(),
    val filters: SavedFilters = SavedFilters(),
    /** 무료면 n/5 를 보인다 (FR27). 유료면 null. */
    val freeLimit: Int? = BookmarkPolicy.FREE_LIMIT,
    val playing: CardId? = null,
    val busy: Boolean = false,
    val confirmReplace: Boolean = false,
    val message: UiText? = null,
) {
    val items: List<SavedItem>
        get() = all.filter { item ->
            (filters.group == null || item.group?.id == filters.group) &&
                (filters.area == null || filters.area in item.areas) &&
                (filters.status == null || item.status == filters.status)
        }

    val groups: List<SignGroup> get() = all.mapNotNull { it.group }.distinct()
    val areas: Set<AreaId> get() = all.flatMap { it.areas }.toSet()
}

sealed interface SavedAction {
    data object Refresh : SavedAction
    data class Unsave(val id: CardId) : SavedAction
    data class Play(val id: CardId) : SavedAction
    data class FilterGroup(val group: String?) : SavedAction
    data class FilterArea(val area: AreaId?) : SavedAction
    data class FilterStatus(val status: CardStatus?) : SavedAction
    data object ClearFilters : SavedAction
    data object StartAll : SavedAction
    data class StartFrom(val id: CardId) : SavedAction
    data object ConfirmReplace : SavedAction
    data object KeepSession : SavedAction
}

sealed interface SavedEffect {
    data object OpenLearn : SavedEffect
}

/**
 * SC09 Saved (FR12). 최신순, 분류·지역·상태 필터, 무료 n/5, 해제·음성·카드에서 학습 시작.
 * 학습 세션의 출처는 Saved 이며, 지금 필터로 보이는 카드(사라진 카드 제외)를 그 순서대로 낸다.
 */
class SavedViewModel(
    private val content: ContentRepository,
    private val bookmarks: BookmarkStore,
    private val states: LearningStateStore,
    private val setBookmark: SetBookmark,
    private val entitlement: EntitlementSource,
    private val start: StartSession,
    private val audio: AudioPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(SavedUiState())
    val state: StateFlow<SavedUiState> = _state.asStateFlow()

    private val _effects = Channel<SavedEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<SavedEffect> = _effects

    private var loadJob: Job? = null
    private var pendingFrom: CardId? = null
    private var pending = false

    init {
        load()
        viewModelScope.launch {
            audio.state.collect { p -> _state.update { it.copy(playing = (p as? PlaybackState.Playing)?.let { playing -> it.all.firstOrNull { item -> item.card?.audio == playing.clip }?.id }) } }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                val pack = content.load()
                val statuses = states.all()
                val items = bookmarks.all().map { b ->
                    val card = pack.cards.firstOrNull { it.id == b.card }
                    val places = card?.let { CardPlaceLinker.placesFor(it, PlaceListing.visiblePlaces(pack)) }.orEmpty()
                    SavedItem(
                        id = b.card,
                        card = card,
                        group = card?.let(pack::groupOf),
                        placeCount = places.size,
                        areas = places.map { it.areaId }.toSet(),
                        status = statuses[b.card] ?: CardStatus.Unseen,
                    )
                }
                val paid = entitlement.current() == Entitlement.Paid
                _state.update { it.copy(loading = false, failed = false, all = items, freeLimit = if (paid) null else BookmarkPolicy.FREE_LIMIT) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun onAction(action: SavedAction) {
        val s = _state.value
        when (action) {
            SavedAction.Refresh -> load()
            is SavedAction.Unsave -> viewModelScope.launch {
                when (setBookmark(action.id, saved = false)) {
                    BookmarkOutcome.Removed, BookmarkOutcome.Unchanged -> _state.update { st -> st.copy(all = st.all.filterNot { it.id == action.id }) }
                    else -> _state.update { it.copy(message = UiText.Res(R.string.common_save_failed)) }
                }
            }
            is SavedAction.Play -> s.all.firstOrNull { it.id == action.id }?.card?.audio?.let { audio.play(it) }
            is SavedAction.FilterGroup -> _state.update { it.copy(filters = it.filters.copy(group = action.group)) }
            is SavedAction.FilterArea -> _state.update { it.copy(filters = it.filters.copy(area = action.area)) }
            is SavedAction.FilterStatus -> _state.update { it.copy(filters = it.filters.copy(status = action.status)) }
            SavedAction.ClearFilters -> _state.update { it.copy(filters = SavedFilters()) }
            SavedAction.StartAll -> begin(null, replace = false)
            is SavedAction.StartFrom -> begin(action.id, replace = false)
            SavedAction.ConfirmReplace -> if (pending) begin(pendingFrom, replace = true)
            SavedAction.KeepSession -> {
                pending = false
                _state.update { it.copy(confirmReplace = false) }
            }
        }
    }

    private fun begin(from: CardId?, replace: Boolean) {
        val s = _state.value
        if (s.busy) return
        val cards = s.items.mapNotNull { it.card }.let { cs -> if (from == null) cs else cs.sortedBy { if (it.id == from) 0 else 1 } }
        _state.update { it.copy(busy = true, confirmReplace = false, message = null) }
        viewModelScope.launch {
            val outcome = try {
                start(SessionSource.Saved, Mode.All, cards, replaceUnfinished = replace)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StartOutcome.SaveFailed
            }
            when (outcome) {
                is StartOutcome.Started -> open()
                // 저장 목록은 바뀌므로 진행 중인 Saved 세션도 교체할지 묻는다(카드에서 바로 시작하면 특히).
                is StartOutcome.Resumed, is StartOutcome.NeedsConfirmation -> {
                    pending = true
                    pendingFrom = from
                    _state.update { it.copy(busy = false, confirmReplace = true) }
                }
                StartOutcome.NoCards, StartOutcome.NothingToReview -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.result_no_cards)) }
                StartOutcome.SaveFailed -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.common_save_failed)) }
            }
        }
    }

    private suspend fun open() {
        pending = false
        _state.update { it.copy(busy = false) }
        _effects.send(SavedEffect.OpenLearn)
    }

    override fun onCleared() {
        if (_state.value.playing != null) audio.stop()
    }
}
