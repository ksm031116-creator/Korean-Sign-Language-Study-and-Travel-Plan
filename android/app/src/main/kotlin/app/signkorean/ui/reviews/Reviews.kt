package app.signkorean.ui.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.signkorean.R
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.Place
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.locale.ResolveAppLanguage
import app.signkorean.domain.review.ExternalLinks
import app.signkorean.domain.review.LoadReviews
import app.signkorean.domain.review.Review
import app.signkorean.domain.review.ReviewState
import app.signkorean.ui.designsystem.AppBottomSheet
import app.signkorean.ui.designsystem.ErrorState
import app.signkorean.ui.designsystem.LoadingSkeleton
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SecondaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.designsystem.StatusBadge
import app.signkorean.ui.designsystem.StatusTone
import app.signkorean.ui.explore.text
import app.signkorean.ui.format.rememberAppFormats
import app.signkorean.ui.text.KoreanText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.ZoneId

data class ReviewsUiState(
    val loading: Boolean = true,
    val place: Place? = null,
    /** null 이면 아직 불러오는 중. */
    val reviews: ReviewState? = null,
    /** 원문을 보고 있는 리뷰(목록 순번). 공급자 번역만 원문이 있다 (FR10). */
    val showingOriginal: Set<Int> = emptySet(),
    val expanded: Set<Int> = emptySet(),
)

sealed interface ReviewsAction {
    data object Retry : ReviewsAction
    data class OpenLink(val url: String) : ReviewsAction
    data class ToggleOriginal(val index: Int) : ReviewsAction
    data class ToggleExpand(val index: Int) : ReviewsAction
}

sealed interface ReviewsEffect {
    data class OpenUrl(val url: String) : ReviewsEffect
}

/** 장소 리뷰 (FR10·11, 구현 계획 Phase 15 D2). 리뷰는 공급자의 일부 목록이며, 앱은 번역하지 않는다. */
class ReviewsViewModel(
    private val placeId: PlaceId,
    private val content: ContentRepository,
    private val load: LoadReviews,
    private val language: ResolveAppLanguage,
) : ViewModel() {

    private val _state = MutableStateFlow(ReviewsUiState())
    val state: StateFlow<ReviewsUiState> = _state.asStateFlow()

    private val _effects = Channel<ReviewsEffect>(Channel.BUFFERED)
    val effects: ReceiveChannel<ReviewsEffect> = _effects

    private var job: Job? = null

    init {
        fetch()
    }

    private fun fetch() {
        job?.cancel()
        _state.update { it.copy(reviews = null, showingOriginal = emptySet(), expanded = emptySet()) }
        job = viewModelScope.launch {
            val pack = try {
                content.load()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(loading = false, reviews = ReviewState.NetworkFailure) }
                return@launch
            }
            val place = pack.places.firstOrNull { it.id == placeId }
            _state.update { it.copy(loading = false, place = place) }
            val result = load(placeId, language(pack.locales.keys.toList()))
            _state.update { it.copy(reviews = result) }
        }
    }

    fun onAction(action: ReviewsAction) {
        when (action) {
            ReviewsAction.Retry -> fetch()
            // 허용 목록의 주소만 연다 (UX08, U-16).
            is ReviewsAction.OpenLink -> if (ExternalLinks.isAllowed(action.url)) viewModelScope.launch { _effects.send(ReviewsEffect.OpenUrl(action.url)) }
            is ReviewsAction.ToggleOriginal -> _state.update { it.copy(showingOriginal = it.showingOriginal.toggle(action.index)) }
            is ReviewsAction.ToggleExpand -> _state.update { it.copy(expanded = it.expanded.toggle(action.index)) }
        }
    }

    private fun Set<Int>.toggle(i: Int) = if (i in this) this - i else this + i
}

/** 리뷰 바닥 시트. SC07·SC08 의 Reviews 버튼이 연다. */
@Composable
fun ReviewsSheet(place: PlaceId, onDismiss: () -> Unit) {
    val viewModel: ReviewsViewModel = koinViewModel(key = "reviews-${place.value}") { parametersOf(place) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                is ReviewsEffect.OpenUrl -> uri.openUri(effect.url)
            }
        }
    }
    AppBottomSheet(title = stringResource(R.string.reviews_title), onDismiss = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) { ReviewsContent(state, viewModel::onAction) }
    }
}

@Composable
fun ReviewsContent(state: ReviewsUiState, onAction: (ReviewsAction) -> Unit) {
    val t = SignKoreanTheme.tokens
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        state.place?.let { place ->
            Text(place.name.text(), style = MaterialTheme.typography.titleMedium)
            KoreanText(place.koName, style = MaterialTheme.typography.labelMedium.copy(color = t.muted))
            Text(place.address.text(), style = MaterialTheme.typography.bodySmall, color = t.muted)
        }
        when (val r = state.reviews) {
            null -> LoadingSkeleton(rows = 4)
            is ReviewState.Shown -> {
                // 사용자 언어가 없어 영어로 보일 때 그 사실을 먼저 알린다 (L10N-10).
                if (r.fallback) StatusBadge(stringResource(R.string.reviews_in_english), StatusTone.Neutral)
                // 공급자가 준 일부 목록임을 밝힌다 (FR11).
                Text(
                    stringResource(if (r.page.partial) R.string.reviews_partial else R.string.reviews_source, r.page.attribution),
                    style = MaterialTheme.typography.labelMedium,
                    color = t.muted,
                )
                r.page.items.forEachIndexed { i, review ->
                    ReviewItem(review, showingOriginal = i in state.showingOriginal, expanded = i in state.expanded, index = i, onAction = onAction)
                }
                SourceLink(r.page.sourceUrl, onAction)
            }
            is ReviewState.NoReviews -> {
                Notice(stringResource(R.string.reviews_none))
                SourceLink(r.sourceUrl, onAction)
            }
            is ReviewState.LanguageUnavailable -> {
                Notice(stringResource(R.string.reviews_language_unavailable))
                SourceLink(r.sourceUrl, onAction)
            }
            // 다른 장소의 리뷰로 채우지 않는다. 링크도 보이지 않는다(엉뚱한 장소로 갈 수 있다).
            ReviewState.PlaceMismatch -> Notice(stringResource(R.string.reviews_mismatch))
            ReviewState.NetworkFailure -> ErrorState(stringResource(R.string.reviews_network), onRetry = { onAction(ReviewsAction.Retry) })
            ReviewState.NotConnected -> Notice(stringResource(R.string.reviews_not_connected))
        }
    }
}

@Composable
private fun Notice(text: String) {
    val t = SignKoreanTheme.tokens
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.fillMaxWidth().background(t.primarySoft, RoundedCornerShape(12.dp)).padding(14.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** 원문 페이지 링크. 허용 목록 밖이면 버튼을 그리지 않는다 (U-16). */
@Composable
private fun SourceLink(url: String?, onAction: (ReviewsAction) -> Unit) {
    if (url == null || !ExternalLinks.isAllowed(url)) return
    SecondaryButton(stringResource(R.string.reviews_open_source), onClick = { onAction(ReviewsAction.OpenLink(url)) })
}

@Composable
private fun ReviewItem(review: Review, showingOriginal: Boolean, expanded: Boolean, index: Int, onAction: (ReviewsAction) -> Unit) {
    val t = SignKoreanTheme.tokens
    val formats = rememberAppFormats()
    Column(
        Modifier.fillMaxWidth().background(t.paper, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val meta = listOfNotNull(
            review.author,
            review.rating?.let { stringResource(R.string.reviews_rating, it) },
            review.writtenAt?.let { formats.date(it.atZone(SEOUL).toLocalDate()) },
        ).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = t.muted)
        if (review.translated) StatusBadge(stringResource(R.string.reviews_translated), StatusTone.Neutral)
        val text: String = review.originalText?.takeIf { showingOriginal } ?: review.text
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (text.length > LONG_REVIEW) {
                QuietButton(stringResource(if (expanded) R.string.reviews_read_less else R.string.reviews_read_more), onClick = { onAction(ReviewsAction.ToggleExpand(index)) })
            }
            if (review.translated && review.originalText != null) {
                QuietButton(
                    stringResource(if (showingOriginal) R.string.reviews_show_translation else R.string.reviews_show_original),
                    onClick = { onAction(ReviewsAction.ToggleOriginal(index)) },
                )
            }
        }
    }
}

/** 네 줄을 넘길 만한 길이. 넘으면 펼치기 버튼을 보인다. */
private const val LONG_REVIEW = 220
private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
