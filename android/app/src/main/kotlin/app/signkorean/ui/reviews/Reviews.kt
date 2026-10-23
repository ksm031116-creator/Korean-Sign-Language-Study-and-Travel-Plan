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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.contentDescription
import app.signkorean.ui.text.korean
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
import app.signkorean.domain.review.KakaoReviewLink
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
    /** "카카오맵에서 리뷰 보기"를 보일 수 있다. 예시 장소는 실제 장소가 아니라 없다. */
    val kakaoAvailable: Boolean = false,
    /** 카카오 장소 페이지를 찾는 중. 두 번 눌러 두 번 열지 않게. */
    val openingKakao: Boolean = false,
)

sealed interface ReviewsAction {
    /** 시트를 열었다. 지난번에 실패했으면 다시 불러온다(RVW-08). */
    data object Opened : ReviewsAction
    data object Retry : ReviewsAction
    data class OpenLink(val url: String) : ReviewsAction

    /** 카카오맵의 이 장소 페이지(없으면 카카오맵 검색)를 연다. */
    data object OpenKakao : ReviewsAction
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
    private val kakaoLink: KakaoReviewLink,
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
            _state.update { it.copy(loading = false, place = place, kakaoAvailable = place != null && !place.isExample) }
            val result = load(placeId, language(pack.locales.keys.toList()))
            _state.update { it.copy(reviews = result) }
        }
    }

    fun onAction(action: ReviewsAction) {
        when (action) {
            ReviewsAction.Retry -> fetch()
            ReviewsAction.Opened -> if (_state.value.reviews == ReviewState.NetworkFailure) fetch()
            // 허용 목록의 주소만 연다 (UX08, U-16).
            is ReviewsAction.OpenLink -> if (ExternalLinks.isAllowed(action.url)) viewModelScope.launch { _effects.send(ReviewsEffect.OpenUrl(action.url)) }
            ReviewsAction.OpenKakao -> openKakao()
            is ReviewsAction.ToggleOriginal -> _state.update { it.copy(showingOriginal = it.showingOriginal.toggle(action.index)) }
            is ReviewsAction.ToggleExpand -> _state.update { it.copy(expanded = it.expanded.toggle(action.index)) }
        }
    }

    private fun openKakao() {
        val s = _state.value
        val place = s.place ?: return
        if (!s.kakaoAvailable || s.openingKakao) return
        _state.update { it.copy(openingKakao = true) }
        viewModelScope.launch {
            try {
                // 허용 목록의 주소만 연다 (UX08, U-16).
                kakaoLink(place)?.takeIf(ExternalLinks::isAllowed)?.let { _effects.send(ReviewsEffect.OpenUrl(it)) }
            } finally {
                _state.update { it.copy(openingKakao = false) }
            }
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
    LaunchedEffect(Unit) { viewModel.onAction(ReviewsAction.Opened) }
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                // 열 앱이 없으면(브라우저 꺼짐·업무 프로필) 예외가 난다. 앱을 멈추지 않는다(RVW-06).
                is ReviewsEffect.OpenUrl -> runCatching { uri.openUri(effect.url) }
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
        // 원문 페이지 링크가 이미 있으면(그것도 카카오맵이다) 카카오맵 버튼을 따로 보이지 않는다.
        val sourceShown = when (val r = state.reviews) {
            is ReviewState.Shown -> r.page.sourceUrl
            is ReviewState.NoReviews -> r.sourceUrl
            is ReviewState.LanguageUnavailable -> r.sourceUrl
            else -> null
        }?.let(ExternalLinks::isAllowed) == true
        when (val r = state.reviews) {
            null -> LoadingSkeleton(rows = 4)
            is ReviewState.Shown -> {
                // 사용자 언어가 없어 영어로 보일 때 그 사실을 먼저 알린다 (L10N-10).
                if (r.fallback) StatusBadge(stringResource(R.string.reviews_in_english), StatusTone.Neutral)
                // 공급자가 준 일부 목록임을 밝힌다 (FR11).
                val source = r.page.attribution.ifBlank { stringResource(R.string.reviews_provider_unknown) }
                Text(
                    stringResource(if (r.page.partial) R.string.reviews_partial else R.string.reviews_source, source),
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
        // 카카오·네이버 모두 리뷰 공식 API 가 없다. 앱 안에 리뷰를 가져오지 않고 카카오맵으로 보낸다.
        if (state.kakaoAvailable && state.reviews != null && !sourceShown) {
            SecondaryButton(stringResource(R.string.reviews_open_kakao), onClick = { onAction(ReviewsAction.OpenKakao) }, enabled = !state.openingKakao)
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
        // "★ 5" 는 스크린리더가 "black star 5" 로 읽는다. 뜻을 읽힌다(RVW-17).
        val spoken = listOfNotNull(review.author, review.rating?.let { stringResource(R.string.reviews_rating_spoken, it) }).joinToString(", ")
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelMedium, color = t.muted, modifier = Modifier.semantics { contentDescription = spoken })
        if (review.translated) StatusBadge(stringResource(R.string.reviews_translated), StatusTone.Neutral)
        val original = review.originalText?.takeIf { showingOriginal }
        val text: String = original ?: review.text
        // 글자 수가 아니라 실제로 넘쳤는지로 펼치기를 보인다. 한·중·일 문장과 큰 글꼴도 끝까지 읽을 수 있다(RVW-02).
        var overflowing by remember(text) { mutableStateOf(false) }
        val style = MaterialTheme.typography.bodyMedium
        Text(
            text,
            // 원문(한국어)은 한국어 글꼴로 그린다(설계서 §8.7, RVW-16).
            style = if (original != null) style.korean() else style,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) overflowing = it.hasVisualOverflow },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (overflowing || expanded) {
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

private val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
