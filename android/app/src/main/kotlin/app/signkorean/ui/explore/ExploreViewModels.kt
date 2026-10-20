package app.signkorean.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.common.LocalizedText
import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.CardPlaceLinker
import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.Place
import app.signkorean.domain.content.SignCard
import app.signkorean.domain.content.SignGroup
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.LearningSession
import app.signkorean.domain.learning.LearningStateStore
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.learning.ResumeSession
import app.signkorean.domain.learning.SessionSource
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

/** 출처 하나의 진행률. Known x/y 는 고유 카드 기준이다(PRD §15.1, 세션의 판정 수와 다르다). */
data class Progress(val total: Int, val known: Int, val learning: Int) {
    val new: Int get() = total - known - learning

    companion object {
        fun of(cards: List<SignCard>, statuses: Map<CardId, CardStatus>) = Progress(
            total = cards.size,
            known = cards.count { statuses[it.id] == CardStatus.Known },
            learning = cards.count { statuses[it.id] == CardStatus.Learning },
        )
    }
}

data class AreaUi(
    val id: AreaId,
    val name: LocalizedText,
    val koName: String,
    val tag: LocalizedText,
    val color: String,
    val placeCount: Int,
    val progress: Progress,
)

/** 이어 할 세션 (SC02 Resume 배너, FR07). */
data class ResumeUi(val source: SessionSource, val areaName: LocalizedText?, val decisions: Int)

data class ExploreUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val basics: Progress = Progress(0, 0, 0),
    val basicsPreview: List<SignCard> = emptyList(),
    val areas: List<AreaUi> = emptyList(),
    val query: String = "",
    val resume: ResumeUi? = null,
)

sealed interface ExploreAction {
    data class Search(val query: String) : ExploreAction
    data object Refresh : ExploreAction
}

/**
 * SC02 Explore. 지역 검색은 지역 이름(모든 언어)·한국어 이름·그 지역 간판 단어·뜻에서 찾는다(대소문자·앞뒤 공백 무시).
 * 화면에 돌아올 때마다 Refresh 로 학습 상태와 Resume 배너를 다시 읽는다.
 */
class ExploreViewModel(
    private val content: ContentRepository,
    private val states: LearningStateStore,
    private val resume: ResumeSession,
) : ViewModel() {

    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    private var all: List<AreaUi> = emptyList()
    private var searchIndex: Map<AreaId, String> = emptyMap()

    init {
        refresh()
    }

    fun onAction(action: ExploreAction) {
        when (action) {
            is ExploreAction.Search -> _state.update { it.copy(query = action.query, areas = filter(action.query)) }
            ExploreAction.Refresh -> refresh()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            try {
                val pack = content.load()
                val statuses = states.all()
                val basics = SourceCards.of(pack, SessionSource.Basics)
                all = pack.areas.filter { it.active }.map { area ->
                    AreaUi(
                        id = area.id,
                        name = area.name,
                        koName = area.koName,
                        tag = area.tag,
                        color = area.color,
                        placeCount = pack.places.count { it.areaId == area.id },
                        progress = Progress.of(SourceCards.of(pack, SessionSource.Area(area.id)), statuses),
                    )
                }
                searchIndex = pack.areas.associate { area ->
                    val cards = SourceCards.of(pack, SessionSource.Area(area.id))
                    area.id to buildString {
                        append(area.koName).append(' ')
                        area.name.locales.forEach { append(area.name[it]).append(' ') }
                        cards.forEach { c -> append(c.koText).append(' '); c.meaning.locales.forEach { append(c.meaning[it]).append(' ') } }
                    }.lowercase()
                }
                val session = resume()
                _state.update {
                    it.copy(
                        loading = false,
                        failed = false,
                        basics = Progress.of(basics, statuses),
                        basicsPreview = basics.take(3),
                        areas = filter(it.query),
                        resume = session?.let { s -> resumeOf(s, pack) },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    private fun resumeOf(s: LearningSession, pack: ContentPack) = ResumeUi(
        source = s.source,
        areaName = (s.source as? SessionSource.Area)?.let { src -> pack.areas.firstOrNull { it.id == src.area }?.name },
        decisions = s.decisions.size,
    )

    private fun filter(query: String): List<AreaUi> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { searchIndex[it.id]?.contains(q) == true }
    }
}

/** SC03 단어 칩 하나. */
data class WordChip(val id: CardId, val koText: String, val status: CardStatus)

data class GroupUi(val group: SignGroup?, val words: List<WordChip>)

data class PlaceUi(val place: Place, val group: SignGroup?)

data class CollectionUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val isBasics: Boolean = false,
    val name: LocalizedText = LocalizedText.EMPTY,
    val koName: String = "",
    val description: LocalizedText = LocalizedText.EMPTY,
    val color: String = "#2f6fb0",
    val progress: Progress = Progress(0, 0, 0),
    val mode: Mode = Mode.All,
    val groups: List<GroupUi> = emptyList(),
    val places: List<PlaceUi> = emptyList(),
    val busy: Boolean = false,
    /** 끝나지 않은 다른 세션이 있어 교체 확인(SC20)을 묻는 중 (L-16). */
    val confirmReplace: Boolean = false,
    val message: UiText? = null,
) {
    val canReview: Boolean get() = progress.learning > 0
    val canStart: Boolean get() = progress.total > 0
}

sealed interface CollectionAction {
    data class SetMode(val mode: Mode) : CollectionAction
    data object Start : CollectionAction
    data class StartFrom(val card: CardId) : CollectionAction
    data object ConfirmReplace : CollectionAction
    data object KeepSession : CollectionAction
}

sealed interface CollectionEffect {
    data object OpenLearn : CollectionEffect
}

/**
 * SC03 지역·Basics 상세. Basics 는 장소가 없어 장소 목록·리뷰·지도·일정 진입점을 그리지 않는다(FR31, UX09).
 * 지역 자체는 목적지가 아니다(Map·Add 없음).
 */
class CollectionViewModel(
    private val source: SessionSource,
    private val content: ContentRepository,
    private val states: LearningStateStore,
    private val start: StartSession,
) : ViewModel() {

    private val _state = MutableStateFlow(CollectionUiState())
    val state: StateFlow<CollectionUiState> = _state.asStateFlow()

    private val _effects = Channel<CollectionEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<CollectionEffect> = _effects

    private var cards: List<SignCard> = emptyList()

    /** 확인을 기다리는 시작 요청. 확인하면 이 요청으로 교체한다. */
    private var pending: Pair<Mode, CardId?>? = null

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val pack = content.load()
            val statuses = states.all()
            cards = SourceCards.of(pack, source)
            val base = CollectionUiState(loading = false, progress = Progress.of(cards, statuses), mode = _state.value.mode)
            _state.value = when (source) {
                SessionSource.Basics -> base.copy(
                    isBasics = true,
                    groups = cards.groupBy { (it.kind as CardKind.Basic).groupId }
                        .map { (id, cs) -> GroupUi(pack.groups.firstOrNull { it.id == id }, cs.map { WordChip(it.id, it.koText, statuses[it.id] ?: CardStatus.Unseen) }) },
                )
                is SessionSource.Area -> {
                    val area = pack.areas.firstOrNull { it.id == source.area }
                    if (area == null) {
                        CollectionUiState(loading = false, missing = true)
                    } else {
                        val places = pack.places.filter { it.areaId == area.id }
                        base.copy(
                            name = area.name,
                            koName = area.koName,
                            description = area.description,
                            color = area.color,
                            places = places.map { p -> PlaceUi(p, pack.groups.firstOrNull { it.id == p.typeId }) },
                            groups = places.map { p -> p to CardPlaceLinker.cardsFor(p, cards) }
                                .flatMap { it.second }.distinct()
                                .groupBy { (it.kind as CardKind.PlaceSign).typeId }
                                .map { (id, cs) -> GroupUi(pack.groups.firstOrNull { it.id == id }, cs.map { WordChip(it.id, it.koText, statuses[it.id] ?: CardStatus.Unseen) }) },
                        )
                    }
                }
                else -> CollectionUiState(loading = false, missing = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = CollectionUiState(loading = false, missing = true)
        }
    }

    fun onAction(action: CollectionAction) {
        val s = _state.value
        if (s.busy) return
        when (action) {
            is CollectionAction.SetMode -> if (action.mode == Mode.All || s.canReview) _state.update { it.copy(mode = action.mode, message = null) }
            CollectionAction.Start -> begin(s.mode, null, replace = false)
            is CollectionAction.StartFrom -> begin(Mode.All, action.card, replace = false)
            CollectionAction.ConfirmReplace -> pending?.let { (mode, from) -> begin(mode, from, replace = true) }
            CollectionAction.KeepSession -> {
                pending = null
                _state.update { it.copy(confirmReplace = false) }
            }
        }
    }

    private fun begin(mode: Mode, from: CardId?, replace: Boolean) {
        // 단어 칩에서 시작하면 그 카드를 맨 앞에 둔다(카드에서 바로 시작).
        val ordered = if (from == null) cards else cards.sortedBy { if (it.id == from) 0 else 1 }
        _state.update { it.copy(busy = true, confirmReplace = false, message = null) }
        viewModelScope.launch {
            val outcome = try {
                start(source, mode, ordered, replaceUnfinished = replace)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StartOutcome.SaveFailed
            }
            when (outcome) {
                is StartOutcome.Started, is StartOutcome.Resumed -> {
                    pending = null
                    _state.update { it.copy(busy = false) }
                    _effects.send(CollectionEffect.OpenLearn)
                }
                is StartOutcome.NeedsConfirmation -> {
                    pending = mode to from
                    _state.update { it.copy(busy = false, confirmReplace = true) }
                }
                StartOutcome.NoCards -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.result_no_cards)) }
                StartOutcome.NothingToReview -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.result_nothing_to_review)) }
                StartOutcome.SaveFailed -> _state.update { it.copy(busy = false, message = UiText.Res(R.string.common_save_failed)) }
            }
        }
    }
}
