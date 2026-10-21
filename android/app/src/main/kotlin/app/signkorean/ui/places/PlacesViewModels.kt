package app.signkorean.ui.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.audio.AudioPlayer
import app.signkorean.domain.audio.PlaybackState
import app.signkorean.domain.content.AudioClip
import app.signkorean.domain.content.GroupKind
import kotlinx.coroutines.Job
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
    /** 카드가 콘텐츠에서 빠졌다. */
    val missing: Boolean = false,
    /** 콘텐츠를 읽지 못했다. 같은 화면에서 다시 시도한다 (UX02·03, 리뷰 PLC-05). */
    val loadFailed: Boolean = false,
    val card: SignCard? = null,
    val groups: List<AreaPlaces> = emptyList(),
    val summary: SummaryState = SummaryState.Loading,
    val paid: Boolean = false,
) {
    val placeCount: Int get() = groups.sumOf { it.places.size }
    fun summaryFor(id: PlaceId): PlaceSummary? = (summary as? SummaryState.Loaded)?.byPlace?.get(id)
}

sealed interface PlacesAction {
    data object Retry : PlacesAction
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
    private val entitlements: Entitlements,
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
        viewModelScope.launch { load() }
    }

    private var summaryJob: Job? = null
    private val sounds = OwnAudio(audio)

    private suspend fun load() {
        val p = try {
            content.load()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.update { it.copy(loading = false, loadFailed = true) }
            return
        }
        pack = p
        val card = p.cards.firstOrNull { it.id == cardId && it.active }
        val paid = entitlements.current() == Entitlement.Paid
        if (card == null) {
            _state.update { it.copy(loading = false, loadFailed = false, missing = true, paid = paid) }
            return
        }
        val groups = PlaceListing.forCard(p, card)
        // 권한을 같은 갱신에 넣는다. 유료 사용자에게 자물쇠가 한 프레임 비치지 않게(PLC-14).
        _state.update { it.copy(loading = false, loadFailed = false, card = card, groups = groups, paid = paid) }
        if (groups.isNotEmpty()) startSummaries(p)
    }

    /** 요약 요청은 하나만. 다시 시도를 연타해도 늦게 온 결과가 새 결과를 덮지 않는다(PLC-13). */
    private fun startSummaries(p: ContentPack) {
        summaryJob?.cancel()
        summaryJob = viewModelScope.launch { loadSummaries(p) }
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
            PlacesAction.Retry -> {
                _state.update { it.copy(loading = true, loadFailed = false) }
                viewModelScope.launch { load() }
            }
            PlacesAction.RetrySummaries -> pack?.let(::startSummaries)
            PlacesAction.PlayAudio -> sounds.play(_state.value.card?.audio)
            is PlacesAction.OpenPlace -> viewModelScope.launch { _effects.send(PlacesEffect.OpenPlace(action.id)) }
            is PlacesAction.Upgrade -> viewModelScope.launch { _effects.send(PlacesEffect.Upgrade(action.feature)) }
        }
    }

    override fun onCleared() = sounds.stopMine()
}

data class PlaceDetailUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val loadFailed: Boolean = false,
    /** 장소 유형 이름(시장·고궁 …). 콘텐츠 팩의 묶음 이름이다 (구현 계획 Phase 14 D3). */
    val typeLabel: app.signkorean.domain.common.LocalizedText? = null,
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
    data object Retry : PlaceDetailAction
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
    private val entitlements: Entitlements,
    private val start: StartSession,
    private val audio: AudioPlayer,
) : ViewModel() {

    private val _state = MutableStateFlow(PlaceDetailUiState())
    private val sounds = OwnAudio(audio)
    val state: StateFlow<PlaceDetailUiState> = _state.asStateFlow()

    private val _effects = Channel<PlaceDetailEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<PlaceDetailEffect> = _effects

    private var pack: ContentPack? = null

    init {
        viewModelScope.launch { entitlements.entitlement.collect { e -> _state.update { it.copy(paid = e == Entitlement.Paid) } } }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val p = content.load()
            pack = p
            val activeAreas = p.areas.filter { it.active }.map { it.id }.toSet()
            // 비활성 지역의 장소는 목록에도 상세에도 없다(PLC-12).
            val place = p.places.firstOrNull { it.id == placeId && it.areaId in activeAreas }
            if (place == null) {
                _state.update { it.copy(loading = false, missing = true) }
                return
            }
            val signs = PlaceListing.signsAt(p, place)
            val statuses = states.all()
            val saved = bookmarks.all().map { it.card }.toSet()
            val paid = entitlements.current() == Entitlement.Paid
            _state.update {
                val selected = it.selectedId?.takeIf { id -> signs.any { s -> s.id == id } } ?: signs.firstOrNull()?.id
                it.copy(
                    loading = false,
                    loadFailed = false,
                    paid = paid,
                    place = place,
                    typeLabel = p.groups.firstOrNull { g -> g.kind == GroupKind.PlaceType && g.id == place.typeId }?.label,
                    // 결제 뒤 이어서 저장됐으면 한도 안내를 거둔다(PLC-07, Learn 의 UPG-08 과 같다).
                    message = if (selected != null && selected in saved && it.message is UiText.Res && (it.message as UiText.Res).id == R.string.bookmark_limit) null else it.message,
                    area = p.areas.firstOrNull { a -> a.id == place.areaId },
                    signs = signs,
                    selectedId = selected,
                    statuses = statuses,
                    saved = saved,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 이미 보이는 화면이 있으면 그대로 둔다. 처음 읽기만 실패 화면이다(PLC-05).
            _state.update { if (it.place != null) it.copy(loading = false) else it.copy(loading = false, loadFailed = true) }
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
            PlaceDetailAction.PlayAudio -> sounds.play(s.selected?.audio)
            // 첫 읽기가 끝나기 전의 화면 resume 은 건너뛴다(같은 읽기를 두 번 하지 않게, PLC-14).
            PlaceDetailAction.Refresh -> if (!s.loading) viewModelScope.launch { load() }
            PlaceDetailAction.Retry -> {
                _state.update { it.copy(loading = true, loadFailed = false) }
                viewModelScope.launch { load() }
            }
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

    override fun onCleared() = sounds.stopMine()
}

/**
 * 이 화면이 튼 발음만 기억했다가 화면이 닫힐 때 멈춘다. 재생기는 앱에 하나라서 남이 튼 음성은 건드리지 않는다
 * (Learn 의 stopMine 과 같은 규칙, Phase 14 리뷰 PLC-15).
 */
internal class OwnAudio(private val audio: AudioPlayer) {
    private var mine: AudioClip? = null

    fun play(clip: AudioClip?) {
        clip ?: return
        mine = clip
        audio.play(clip)
    }

    fun stopMine() {
        val playing = (audio.state.value as? PlaybackState.Playing)?.clip
        if (playing != null && playing == mine) audio.stop()
        mine = null
    }
}
