package app.signkorean.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.audio.AudioPlayer
import app.signkorean.domain.bookmark.BookmarkOutcome
import app.signkorean.domain.bookmark.BookmarkStore
import app.signkorean.domain.bookmark.SetBookmark
import app.signkorean.domain.content.Area
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.Place
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.content.SignCard
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.Entitlements
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.LearningStateStore
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.SourceCards
import app.signkorean.domain.learning.StartOutcome
import app.signkorean.domain.learning.StartSession
import app.signkorean.domain.locale.ResolveAppLanguage
import app.signkorean.domain.place.AreaPlaces
import app.signkorean.domain.place.PlaceListing
import app.signkorean.domain.place.PlaceSummary
import app.signkorean.domain.place.PlaceSummaryProvider
import app.signkorean.domain.place.PlaceSummaryResult
import app.signkorean.domain.upgrade.PendingAction
import app.signkorean.domain.upgrade.PendingActionStore
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

/** 장소 요약(평점·리뷰 수·발췌) 상태. 실패해도 장소 자체는 보인다 (설계서 §6.1). */
sealed interface SummaryState {
    data object Loading : SummaryState
    data class Loaded(val byPlace: Map<PlaceId, PlaceSummary>) : SummaryState
    data object Unavailable : SummaryState
}

data class PlacesUiState(
    val loading: Boolean = true,
    /** 카드가 없거나(콘텐츠에서 빠짐) 읽지 못했다. */
    val missing: Boolean = false,
    val card: SignCard? = null,
    val groups: List<AreaPlaces> = emptyList(),
    val summary: SummaryState = SummaryState.Loading,
    val paid: Boolean = false,
) {
    val placeCount: Int get() = groups.sumOf { it.places.size }
    fun summaryFor(id: PlaceId): PlaceSummary? = (summary as? SummaryState.Loaded)?.byPlace?.get(id)
}

sealed interface PlacesAction {
    data object RetrySummaries : PlacesAction
    data object PlayAudio : PlacesAction
    data class OpenPlace(val id: PlaceId) : PlacesAction
    data class Upgrade(val feature: UpgradeFeature) : PlacesAction
}

sealed interface PlacesEffect {
    data class OpenPlace(val id: PlaceId) : PlacesEffect
    data class Upgrade(val feature: UpgradeFeature) : PlacesEffect
}

/**
 * SC08 "이 간판을 볼 수 있는 장소" (FR09). 장소 이름·사진·주소는 콘텐츠 팩에서, 평점·리뷰 수·발췌는 요약 공급자에서 온다.
 * 학습 세션을 건드리지 않는다. 닫으면 Learn 의 카드·뜻 공개·순서가 그대로다(Learn ViewModel 이 스택에 남아 있다).
 */
class PlacesViewModel(
    private val cardId: CardId,
    private val content: ContentRepository,
    private val summaries: PlaceSummaryProvider,
    private val language: ResolveAppLanguage,
    entitlements: Entitlements,
    private val audio: AudioPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(PlacesUiState())
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()

    private val _effects = Channel<PlacesEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<PlacesEffect> = _effects

    private var pack: ContentPack? = null

    init {
        // SC21 에서 결제하고 돌아오면 잠금이 바로 풀린다.
        viewModelScope.launch { entitlements.entitlement.collect { e -> _state.update { it.copy(paid = e == Entitlement.Paid) } } }
        viewModelScope.launch {
            entitlements.current()
            load()
        }
    }

    private suspend fun load() {
        val p = try {
            content.load()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loading = false, missing = true) }
            return
        }
        pack = p
        val card = p.cards.firstOrNull { it.id == cardId && it.active }
        if (card == null) {
            _state.update { it.copy(loading = false, missing = true) }
            return
        }
        val groups = PlaceListing.forCard(p, card)
        _state.update { it.copy(loading = false, card = card, groups = groups) }
        if (groups.isNotEmpty()) loadSummaries(p)
    }

    private suspend fun loadSummaries(p: ContentPack) {
        _state.update { it.copy(summary = SummaryState.Loading) }
        val result = try {
            summaries.summaries(cardId, language(p.locales.keys.toList()))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            PlaceSummaryResult.Unavailable
        }
        _state.update {
            it.copy(summary = when (result) {
                is PlaceSummaryResult.Loaded -> SummaryState.Loaded(result.byPlace)
                PlaceSummaryResult.Unavailable -> SummaryState.Unavailable
            })
        }
    }

    fun onAction(action: PlacesAction) {
        when (action) {
            PlacesAction.RetrySummaries -> pack?.let { p -> viewModelScope.launch { loadSummaries(p) } }
            PlacesAction.PlayAudio -> _state.value.card?.audio?.let { audio.play(it) }
            is PlacesAction.OpenPlace -> viewModelScope.launch { _effects.send(PlacesEffect.OpenPlace(action.id)) }
            is PlacesAction.Upgrade -> viewModelScope.launch { _effects.send(PlacesEffect.Upgrade(action.feature)) }
        }
    }
}

data class PlaceDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val place: Place? = null,
    val area: Area? = null,
    val signs: List<SignCard> = emptyList(),
    val selectedId: CardId? = null,
    val statuses: Map<CardId, CardStatus> = emptyMap(),
    val saved: Set<CardId> = emptySet(),
    val paid: Boolean = false,
    val busy: Boolean = false,
    /** 다른 세션이 진행 중이다. 교체를 묻는다 (SC20). */
    val confirmReplace: Boolean = false,
    val message: UiText? = null,
) {
    val selected: SignCard? get() = signs.firstOrNull { it.id == selectedId }
    val selectedStatus: CardStatus get() = selectedId?.let { statuses[it] } ?: CardStatus.Unseen
    val selectedSaved: Boolean get() = selectedId != null && selectedId in saved
}

sealed interface PlaceDetailAction {
    data class Select(val id: CardId) : PlaceDetailAction
    data object ToggleBookmark : PlaceDetailAction
    data object Practice : PlaceDetailAction
    data object ConfirmReplace : PlaceDetailAction
    data object KeepSession : PlaceDetailAction
    data object PlayAudio : PlaceDetailAction
    data object Refresh : PlaceDetailAction
    data class Upgrade(val feature: UpgradeFeature) : PlaceDetailAction
}

sealed interface PlaceDetailEffect {
    data object OpenLearn : PlaceDetailEffect
    data class Upgrade(val feature: UpgradeFeature) : PlaceDetailEffect
}

/** SC07 장소 상세 (구현 계획 Phase 14 D3). 그 장소의 간판 카드, Save sign, Practice these signs(장소 출처 세션). */
class PlaceDetailViewModel(
    private val placeId: PlaceId,
    private val content: ContentRepository,
    private val states: LearningStateStore,
    private val bookmarks: BookmarkStore,
    private val setBookmark: SetBookmark,
    private val pending: PendingActionStore,
    entitlements: Entitlements,
    private val start: StartSession,
    private val audio: AudioPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaceDetailUiState())
    val state: StateFlow<PlaceDetailUiState> = _state.asStateFlow()

    private val _effects = Channel<PlaceDetailEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<PlaceDetailEffect> = _effects

    private var pack: ContentPack? = null

    init {
        viewModelScope.launch { entitlements.entitlement.collect { e -> _state.update { it.copy(paid = e == Entitlement.Paid) } } }
        viewModelScope.launch {
            entitlements.current()
            load()
        }
    }

    private suspend fun load() {
        try {
            val p = content.load()
            pack = p
            val place = p.places.firstOrNull { it.id == placeId }
            if (place == null) {
                _state.update { it.copy(loading = false, missing = true) }
                return
            }
            val signs = PlaceListing.signsAt(p, place)
            val statuses = states.all()
            val saved = bookmarks.all().map { it.card }.toSet()
            _state.update {
                it.copy(
                    loading = false,
                    place = place,
                    area = p.areas.firstOrNull { a -> a.id == place.areaId },
                    signs = signs,
                    selectedId = it.selectedId?.takeIf { id -> signs.any { s -> s.id == id } } ?: signs.firstOrNull()?.id,
                    statuses = statuses,
                    saved = saved,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loading = false, missing = true) }
        }
    }

    fun onAction(action: PlaceDetailAction) {
        val s = _state.value
        when (action) {
            is PlaceDetailAction.Select -> _state.update { it.copy(selectedId = action.id, message = null) }
            PlaceDetailAction.ToggleBookmark -> s.selectedId?.let { toggle(it, !s.selectedSaved) }
            PlaceDetailAction.Practice -> practice(replace = false)
            PlaceDetailAction.ConfirmReplace -> {
                _state.update { it.copy(confirmReplace = false) }
                practice(replace = true)
            }
            PlaceDetailAction.KeepSession -> _state.update { it.copy(confirmReplace = false) }
            PlaceDetailAction.PlayAudio -> s.selected?.audio?.let { audio.play(it) }
            PlaceDetailAction.Refresh -> viewModelScope.launch { load() }
            is PlaceDetailAction.Upgrade -> viewModelScope.launch { _effects.send(PlaceDetailEffect.Upgrade(action.feature)) }
        }
    }

    private fun toggle(card: CardId, target: Boolean) {
        viewModelScope.launch {
            when (val o = setBookmark(card, target)) {
                BookmarkOutcome.Saved, BookmarkOutcome.Removed, BookmarkOutcome.Unchanged ->
                    _state.update { it.copy(saved = if (target) it.saved + card else it.saved - card, message = null) }
                is BookmarkOutcome.NeedsUpgrade -> {
                    // SC21 을 열기 전에 보류해 둔다. 결제 뒤 이어서 저장된다(B-04·07).
                    try {
                        pending.save(PendingAction.SaveBookmark(card))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                    _state.update { it.copy(message = UiText.Res(R.string.bookmark_limit, listOf(o.count, o.limit))) }
                    _effects.send(PlaceDetailEffect.Upgrade(UpgradeFeature.BookmarkLimit))
                }
                BookmarkOutcome.SaveFailed -> _state.update { it.copy(message = UiText.Res(R.string.common_save_failed)) }
            }
        }
    }

    private fun practice(replace: Boolean) {
        val p = pack ?: return
        if (_state.value.busy) return
        val source = SessionSource.Place(placeId.value)
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            val outcome = start(source, Mode.All, SourceCards.of(p, source), replaceUnfinished = replace)
            _state.update { it.copy(busy = false) }
            when (outcome) {
                is StartOutcome.Started, is StartOutcome.Resumed -> _effects.send(PlaceDetailEffect.OpenLearn)
                is StartOutcome.NeedsConfirmation -> _state.update { it.copy(confirmReplace = true) }
                StartOutcome.NoCards, StartOutcome.NothingToReview ->
                    _state.update { it.copy(message = UiText.Res(R.string.result_no_cards)) }
                StartOutcome.SaveFailed -> _state.update { it.copy(message = UiText.Res(R.string.common_save_failed)) }
            }
        }
    }
}
