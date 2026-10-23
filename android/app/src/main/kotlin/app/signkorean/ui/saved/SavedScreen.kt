package app.signkorean.ui.saved

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.domain.learning.CardStatus
import app.signkorean.ui.designsystem.AppChip
import app.signkorean.ui.designsystem.LockBadge
import app.signkorean.ui.designsystem.ConfirmDialog
import app.signkorean.ui.designsystem.EmptyState
import app.signkorean.ui.designsystem.ErrorState
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.domain.content.CardId
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.designsystem.StatusBadge
import app.signkorean.ui.designsystem.StatusTone
import app.signkorean.ui.explore.text
import app.signkorean.ui.text.KoreanText
import app.signkorean.ui.text.asString
import org.koin.androidx.compose.koinViewModel

object SavedTags {
    const val QUOTA = "saved-quota"
    fun item(id: String) = "saved-item-$id"
}

/** SC09 Saved. */
@Composable
fun SavedScreen(onOpenLearn: () -> Unit, onUpgrade: () -> Unit = {}, onOpenPlaces: (CardId) -> Unit = {}, viewModel: SavedViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(SavedAction.Refresh)
        onPauseOrDispose { }
    }
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                SavedEffect.OpenLearn -> onOpenLearn()
            }
        }
    }
    SavedContent(state, onUpgrade = onUpgrade, onOpenPlaces = onOpenPlaces, onAction = viewModel::onAction)
}

@Composable
fun SavedContent(state: SavedUiState, onUpgrade: () -> Unit = {}, onOpenPlaces: (CardId) -> Unit = {}, onAction: (SavedAction) -> Unit) {
    val t = SignKoreanTheme.tokens
    if (state.loading) return
    if (state.confirmReplace) {
        ConfirmDialog(
            title = stringResource(R.string.replace_title),
            body = stringResource(R.string.replace_body),
            confirm = stringResource(R.string.replace_end),
            dismiss = stringResource(R.string.replace_keep),
            onConfirm = { onAction(SavedAction.ConfirmReplace) },
            onDismiss = { onAction(SavedAction.KeepSession) },
        )
    }
    Column(
        Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.saved_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        if (state.failed) {
            ErrorState(stringResource(R.string.explore_load_failed), onRetry = { onAction(SavedAction.Refresh) })
            // 읽지 못했는데 "저장한 간판 없음"·"0/5"를 같이 보이면 북마크가 사라진 것처럼 보인다.
            if (state.all.isEmpty()) return@Column
        }
        state.freeLimit?.let { limit ->
            Quota(state.all.size, limit)
            // 유료 기능은 숨기지 않고 자물쇠 + 기능명으로 보인다 (UX09). 누르면 SC21.
            LockBadge(stringResource(R.string.lock_unlimited_bookmarks), onClick = onUpgrade)
        }
        if (state.all.isEmpty()) {
            EmptyState(title = stringResource(R.string.saved_empty_title), body = stringResource(R.string.saved_empty_body))
            return@Column
        }
        Filters(state, onAction)
        state.message?.let { Text(it.asString(), color = t.muted, style = MaterialTheme.typography.bodyMedium) }
        if (state.items.any { !it.unavailable }) {
            PrimaryButton(stringResource(R.string.saved_practice), onClick = { onAction(SavedAction.StartAll) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        }
        if (state.items.isEmpty()) Text(stringResource(R.string.saved_no_match), color = t.muted, style = MaterialTheme.typography.bodyMedium)
        state.items.forEach { item -> SavedRow(item, state.playing == item.id, onAction, onOpenPlaces) }
    }
}

/** 무료 한도 표시 (FR27): n/5 와 막대. */
@Composable
private fun Quota(count: Int, limit: Int) {
    val t = SignKoreanTheme.tokens
    Column(Modifier.fillMaxWidth().testTag(SavedTags.QUOTA).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // 무료로 돌아가 한도보다 많이 갖고 있으면(E-12) 실제 개수를 보인다. 5로 잘라 보이지 않는다.
        Text(
            if (count > limit) stringResource(R.string.saved_quota_over, count, limit) else stringResource(R.string.saved_quota, count, limit),
            style = MaterialTheme.typography.labelLarge,
        )
        Box(Modifier.fillMaxWidth().height(6.dp).background(t.line, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth((count.toFloat() / limit).coerceIn(0f, 1f)).height(6.dp).background(t.primary, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun Filters(state: SavedUiState, onAction: (SavedAction) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AppChip(stringResource(R.string.collection_new), selected = state.filters.status == CardStatus.Unseen, onClick = { onAction(SavedAction.FilterStatus(if (state.filters.status == CardStatus.Unseen) null else CardStatus.Unseen)) })
        AppChip(stringResource(R.string.collection_learning), selected = state.filters.status == CardStatus.Learning, onClick = { onAction(SavedAction.FilterStatus(if (state.filters.status == CardStatus.Learning) null else CardStatus.Learning)) })
        AppChip(stringResource(R.string.collection_known), selected = state.filters.status == CardStatus.Known, onClick = { onAction(SavedAction.FilterStatus(if (state.filters.status == CardStatus.Known) null else CardStatus.Known)) })
        state.groups.forEach { g ->
            AppChip(g.label.text(), selected = state.filters.group == g.id, onClick = { onAction(SavedAction.FilterGroup(if (state.filters.group == g.id) null else g.id)) })
        }
        state.areaNames.forEach { (area, name) ->
            AppChip(name.text(), selected = state.filters.area == area, onClick = { onAction(SavedAction.FilterArea(if (state.filters.area == area) null else area)) })
        }
        if (state.filters.active) QuietButton(stringResource(R.string.saved_clear_filters), onClick = { onAction(SavedAction.ClearFilters) })
    }
}

@Composable
private fun SavedRow(item: SavedItem, playing: Boolean, onAction: (SavedAction) -> Unit, onOpenPlaces: (CardId) -> Unit) {
    val t = SignKoreanTheme.tokens
    Column(
        Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(14.dp)).padding(14.dp).testTag(SavedTags.item(item.id.value)),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val card = item.card
        if (card == null) {
            // 콘텐츠에서 사라진 카드: 해제만 할 수 있다 (FR12, B-05).
            Text(stringResource(R.string.saved_unavailable), style = MaterialTheme.typography.bodyLarge, color = t.muted)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KoreanText(card.koText, style = MaterialTheme.typography.titleLarge)
                StatusBadge(
                    stringResource(
                        when (item.status) {
                            CardStatus.Known -> R.string.collection_known
                            CardStatus.Learning -> R.string.collection_learning
                            CardStatus.Unseen -> R.string.collection_new
                        },
                    ),
                    when (item.status) {
                        CardStatus.Known -> StatusTone.Known
                        CardStatus.Learning -> StatusTone.Learning
                        CardStatus.Unseen -> StatusTone.Neutral
                    },
                )
            }
            Text(card.meaning.text(), style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.group?.let { Text(it.label.text(), style = MaterialTheme.typography.labelMedium, color = t.muted) }
                // 그 간판을 볼 수 있는 장소 목록(SC08)으로 간다 (PRD §14 SC09 → SC08). Basics 는 장소가 없어 그리지 않는다.
                if (item.placeCount > 0 && !item.unavailable) {
                    QuietButton(pluralStringResource(R.plurals.places_count, item.placeCount, item.placeCount), onClick = { onOpenPlaces(item.id) })
                }
            }
        }
        // 줄마다 같은 "Listen·Practise·Remove" 가 반복되므로, 스크린리더 이름에는 그 카드를 넣는다.
        val name = card?.koText
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (card?.audio != null) {
                val listen = stringResource(R.string.saved_a11y_listen, card.koText)
                QuietButton(stringResource(if (playing) R.string.audio_playing else R.string.audio_listen), onClick = { onAction(SavedAction.Play(item.id)) }, modifier = Modifier.semantics { contentDescription = listen })
            }
            if (card != null) {
                val practise = stringResource(R.string.saved_a11y_practise, card.koText)
                QuietButton(stringResource(R.string.saved_practice_this), onClick = { onAction(SavedAction.StartFrom(item.id)) }, modifier = Modifier.semantics { contentDescription = practise })
            }
            val remove = if (name != null) stringResource(R.string.saved_a11y_remove, name) else stringResource(R.string.saved_a11y_remove_unavailable)
            QuietButton(stringResource(R.string.saved_unsave), onClick = { onAction(SavedAction.Unsave(item.id)) }, modifier = Modifier.semantics { contentDescription = remove })
        }
    }
}
