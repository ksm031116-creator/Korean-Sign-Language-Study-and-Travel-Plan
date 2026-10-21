package app.signkorean.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.signkorean.ui.reviews.ReviewsSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.Place
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.content.SignCard
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.place.PlaceSummary
import app.signkorean.ui.designsystem.AppChip
import app.signkorean.ui.designsystem.ConfirmDialog
import app.signkorean.ui.designsystem.EmptyState
import app.signkorean.ui.designsystem.ErrorState
import app.signkorean.ui.designsystem.LoadingSkeleton
import app.signkorean.ui.designsystem.LockBadge
import app.signkorean.ui.designsystem.PlateFace
import app.signkorean.ui.designsystem.PlateSize
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SecondaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.designsystem.SignPlate
import app.signkorean.ui.designsystem.StatusBadge
import app.signkorean.ui.designsystem.StatusTone
import app.signkorean.ui.explore.text
import app.signkorean.ui.text.KoreanText
import app.signkorean.ui.text.asString
import app.signkorean.ui.upgrade.UpgradeFeature
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

object PlacesTags {
    fun place(id: PlaceId) = "place-${id.value}"
    fun rating(id: PlaceId) = "place-rating-${id.value}"
    fun example(id: PlaceId) = "place-example-${id.value}"
}

/** SC08 (FR09). 닫으면 [onClose] 로 Learn 의 같은 카드에 돌아간다. */
@Composable
fun PlacesScreen(
    card: CardId,
    onClose: () -> Unit,
    onOpenPlace: (PlaceId) -> Unit,
    onUpgrade: (UpgradeFeature) -> Unit,
    viewModel: PlacesViewModel = koinViewModel { parametersOf(card) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                is PlacesEffect.OpenPlace -> onOpenPlace(effect.id)
                is PlacesEffect.Upgrade -> onUpgrade(effect.feature)
            }
        }
    }
    var reviewsFor by rememberSaveable { mutableStateOf<String?>(null) }
    PlacesContent(state, viewModel::onAction, onClose, onOpenReviews = { reviewsFor = it.value })
    reviewsFor?.let { ReviewsSheet(PlaceId(it), onDismiss = { reviewsFor = null }) }
}

@Composable
fun PlacesContent(state: PlacesUiState, onAction: (PlacesAction) -> Unit, onClose: () -> Unit, onOpenReviews: (PlaceId) -> Unit = {}) {
    val t = SignKoreanTheme.tokens
    if (state.loading) return
    // 장소가 많은 유형(시장 등)도 보이는 카드의 사진만 받도록 지연 목록으로 그린다(Phase 14 리뷰 PLC-08).
    LazyColumn(
        Modifier.fillMaxSize().background(t.paper),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) } }
        item { Text(stringResource(R.string.places_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() }) }
        val card = state.card
        when {
            state.loadFailed -> item { ErrorState(stringResource(R.string.explore_load_failed), onRetry = { onAction(PlacesAction.Retry) }) }
            state.missing || card == null -> item { EmptyState(title = stringResource(R.string.places_missing), body = "") }
            else -> {
                item { SignStrip(card, onPlay = { onAction(PlacesAction.PlayAudio) }) }
                if (state.placeCount == 0) {
                    item { EmptyState(title = stringResource(R.string.places_none), body = "") }
                } else {
                    item { Text(pluralStringResource(R.plurals.places_count, state.placeCount, state.placeCount), style = MaterialTheme.typography.labelLarge, color = t.muted) }
                    if (state.summary == SummaryState.Unavailable) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.places_summary_unavailable), style = MaterialTheme.typography.bodyMedium, color = t.muted, modifier = Modifier.weight(1f))
                                QuietButton(stringResource(R.string.common_retry), onClick = { onAction(PlacesAction.RetrySummaries) })
                            }
                        }
                    }
                    state.groups.forEach { group ->
                        item(key = "area-" + group.area.id.value) {
                            Text(group.area.name.text(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        }
                        items(group.places, key = { it.id.value }) { place ->
                            PlaceCard(place, group.area.color, state.summaryFor(place.id), state.summary == SummaryState.Loading, state.paid, onAction, onOpenReviews)
                        }
                    }
                }
            }
        }
    }
}

/** 간판 띠: 판·단어·로마자·음성. 이 목록이 어떤 간판의 장소인지 늘 보인다. */
@Composable
private fun SignStrip(card: SignCard, onPlay: () -> Unit) {
    val t = SignKoreanTheme.tokens
    Row(
        Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SignPlate(card, PlateFace.Front, Modifier.width(110.dp), size = PlateSize.Mini)
        Column(Modifier.weight(1f)) {
            KoreanText(card.koText, style = MaterialTheme.typography.titleLarge)
            Text(card.romanization, style = MaterialTheme.typography.bodyMedium, color = t.muted)
        }
        if (card.audio != null) QuietButton(stringResource(R.string.audio_listen), onClick = onPlay)
    }
}

@Composable
private fun PlaceCard(place: Place, areaColor: String, summary: PlaceSummary?, summaryLoading: Boolean, paid: Boolean, onAction: (PlacesAction) -> Unit, onOpenReviews: (PlaceId) -> Unit) {
    val t = SignKoreanTheme.tokens
    val openLabel = stringResource(R.string.place_open, place.name.text())
    Column(
        Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(16.dp))
            // 스크린리더가 "두 번 탭하면 활성화" 대신 무엇이 열리는지 읽는다(PLC-09).
            .clickable(role = Role.Button, onClickLabel = openLabel) { onAction(PlacesAction.OpenPlace(place.id)) }
            .padding(12.dp).testTag(PlacesTags.place(place.id)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlacePhotoView(place.photo, areaColor)
        PlaceTitle(place)
        if (summaryLoading) LoadingSkeleton(rows = 2)
        if (summary != null) {
            // 따로 합치지 않는다. 카드 안내에 이름과 함께 읽힌다(PLC-09).
            Row(Modifier.testTag(PlacesTags.rating(place.id)), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                summary.rating?.let { Text(stringResource(R.string.places_rating, it), style = MaterialTheme.typography.labelLarge) }
                Text(pluralStringResource(R.plurals.reviews_count, summary.reviewCount, summary.reviewCount), style = MaterialTheme.typography.labelLarge, color = t.muted)
            }
            summary.excerpt?.let { Text(it, style = MaterialTheme.typography.bodyMedium, maxLines = 3) }
        }
        Text(place.address.text(), style = MaterialTheme.typography.bodySmall, color = t.muted)
        // 리뷰는 무료다(PRD §3.4). 자물쇠 없이 바로 연다.
        QuietButton(stringResource(R.string.reviews_title), onClick = { onOpenReviews(place.id) })
        PaidActions(paid, onUpgrade = { onAction(PlacesAction.Upgrade(it)) })
    }
}

@Composable
private fun PlaceTitle(place: Place) {
    val t = SignKoreanTheme.tokens
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f)) {
            Text(place.name.text(), style = MaterialTheme.typography.titleMedium)
            KoreanText(place.koName, style = MaterialTheme.typography.labelMedium.copy(color = t.muted))
        }
        // 개별 상점을 대신하는 예시다. 실제 상호처럼 보이지 않게 늘 표시한다 (C-09).
        if (place.isExample) {
            StatusBadge(stringResource(R.string.collection_example), StatusTone.Neutral, Modifier.testTag(PlacesTags.example(place.id)))
        }
    }
}

/**
 * 유료 진입점 (UX09). 무료면 자물쇠 + 기능명으로 보이고 SC21 을 연다. 유료면 그 화면이 생기는 phase 까지 그리지 않는다
 * (구현 계획 원칙 3: Add 는 Phase 17, Route 는 Phase 19).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaidActions(paid: Boolean, onUpgrade: (UpgradeFeature) -> Unit) {
    if (paid) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LockBadge(stringResource(R.string.lock_route), onClick = { onUpgrade(UpgradeFeature.Route) })
        LockBadge(stringResource(R.string.lock_add_to_plan), onClick = { onUpgrade(UpgradeFeature.Plan) })
    }
}

/** SC07 장소 상세 (구현 계획 Phase 14 D3). */
@Composable
fun PlaceDetailScreen(
    place: PlaceId,
    onClose: () -> Unit,
    onOpenLearn: () -> Unit,
    onUpgrade: (UpgradeFeature) -> Unit,
    viewModel: PlaceDetailViewModel = koinViewModel { parametersOf(place) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // 학습·결제에서 돌아오면 상태·저장 여부를 다시 읽는다.
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(PlaceDetailAction.Refresh)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                PlaceDetailEffect.OpenLearn -> onOpenLearn()
                is PlaceDetailEffect.Upgrade -> onUpgrade(effect.feature)
            }
        }
    }
    var reviewsOpen by rememberSaveable { mutableStateOf(false) }
    PlaceDetailContent(state, viewModel::onAction, onClose, onOpenReviews = { reviewsOpen = true })
    if (reviewsOpen) ReviewsSheet(place, onDismiss = { reviewsOpen = false })
}

@Composable
fun PlaceDetailContent(state: PlaceDetailUiState, onAction: (PlaceDetailAction) -> Unit, onClose: () -> Unit, onOpenReviews: () -> Unit = {}) {
    val t = SignKoreanTheme.tokens
    if (state.loading) return
    if (state.confirmReplace) {
        ConfirmDialog(
            title = stringResource(R.string.replace_title),
            body = stringResource(R.string.replace_body),
            confirm = stringResource(R.string.replace_end),
            dismiss = stringResource(R.string.replace_keep),
            onConfirm = { onAction(PlaceDetailAction.ConfirmReplace) },
            onDismiss = { onAction(PlaceDetailAction.KeepSession) },
        )
    }
    Column(
        Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
        val place = state.place
        if (state.loadFailed) {
            ErrorState(stringResource(R.string.explore_load_failed), onRetry = { onAction(PlaceDetailAction.Retry) })
            return@Column
        }
        if (state.missing || place == null) {
            EmptyState(title = stringResource(R.string.place_missing), body = "")
            return@Column
        }
        PlacePhotoView(place.photo, state.area?.color ?: "#2f6f5e")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(place.name.text(), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                KoreanText(place.koName, style = MaterialTheme.typography.titleSmall.copy(color = t.muted))
            }
            if (place.isExample) StatusBadge(stringResource(R.string.collection_example), StatusTone.Neutral)
        }
        if (place.isExample) {
            Text(
                stringResource(R.string.place_example_notice),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().background(t.primarySoft, RoundedCornerShape(12.dp)).padding(12.dp),
            )
        }
        // 지역 · 장소 유형 (구현 계획 Phase 14 D3).
        val where = listOfNotNull(state.area?.name?.text(), state.typeLabel?.text()).filter { it.isNotBlank() }.joinToString(" · ")
        if (where.isNotEmpty()) Text(where, style = MaterialTheme.typography.labelLarge, color = t.primary)
        Text(place.address.text(), style = MaterialTheme.typography.bodyMedium, color = t.muted)
        SecondaryButton(stringResource(R.string.reviews_title), onClick = onOpenReviews)
        PaidActions(state.paid, onUpgrade = { onAction(PlaceDetailAction.Upgrade(it)) })

        if (state.signs.isNotEmpty()) {
            Text(stringResource(R.string.place_signs), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.signs.forEach { s ->
                    // 한글만 있으면 영어·일본어 스크린리더가 한글을 제대로 읽지 못한다. 로마자를 함께 읽힌다(PLC-11).
                    val spoken = s.koText + ", " + s.romanization
                    AppChip(
                        s.koText,
                        selected = s.id == state.selectedId,
                        onClick = { onAction(PlaceDetailAction.Select(s.id)) },
                        modifier = Modifier.semantics { contentDescription = spoken },
                    )
                }
            }
            state.selected?.let { SelectedSign(it, state.selectedStatus, state.selectedSaved, onAction) }
            PrimaryButton(stringResource(R.string.place_practice), onClick = { onAction(PlaceDetailAction.Practice) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        }
        state.message?.let { Text(it.asString(), style = MaterialTheme.typography.bodyMedium, color = t.muted) }
    }
}

@Composable
private fun SelectedSign(card: SignCard, status: CardStatus, saved: Boolean, onAction: (PlaceDetailAction) -> Unit) {
    val t = SignKoreanTheme.tokens
    Column(
        Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SignPlate(card, PlateFace.Back, Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(card.romanization, style = MaterialTheme.typography.bodyLarge)
                // 언어별 발음 보조(ja 는 가타카나). Learn 뒷면과 같다(PLC-11).
                card.reading.text().takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = t.muted) }
            }
            val (label, tone) = when (status) {
                CardStatus.Known -> R.string.collection_known to StatusTone.Known
                CardStatus.Learning -> R.string.collection_learning to StatusTone.Learning
                CardStatus.Unseen -> R.string.collection_new to StatusTone.Neutral
            }
            StatusBadge(stringResource(label), tone)
        }
        Text(card.meaning.text(), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton(
                stringResource(if (saved) R.string.bookmark_remove else R.string.bookmark_save),
                onClick = { onAction(PlaceDetailAction.ToggleBookmark) },
            )
            if (card.audio != null) QuietButton(stringResource(R.string.audio_listen), onClick = { onAction(PlaceDetailAction.PlayAudio) })
        }
    }
}

