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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.domain.learning.CardStatus
import app.signkorean.ui.designsystem.AppChip
import app.signkorean.ui.designsystem.ConfirmDialog
import app.signkorean.ui.designsystem.EmptyState
import app.signkorean.ui.designsystem.ErrorState
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.QuietButton
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
fun SavedScreen(onOpenLearn: () -> Unit, viewModel: SavedViewModel = koinViewModel()) {
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
    SavedContent(state, viewModel::onAction)
}

@Composable
fun SavedContent(state: SavedUiState, onAction: (SavedAction) -> Unit) {
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
        if (state.failed) ErrorState(stringResource(R.string.explore_load_failed), onRetry = { onAction(SavedAction.Refresh) })
        state.freeLimit?.let { limit -> Quota(state.all.size, limit) }
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
        state.items.forEach { item -> SavedRow(item, state.playing == item.id, onAction) }
    }
}

/** 무료 한도 표시 (FR27): n/5 와 막대. */
@Composable
private fun Quota(count: Int, limit: Int) {
    val t = SignKoreanTheme.tokens
    Column(Modifier.fillMaxWidth().testTag(SavedTags.QUOTA).semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.saved_quota, count.coerceAtMost(limit), limit), style = MaterialTheme.typography.labelLarge)
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
        if (state.filters.active) QuietButton(stringResource(R.string.saved_clear_filters), onClick = { onAction(SavedAction.ClearFilters) })
    }
}

@Composable
private fun SavedRow(item: SavedItem, playing: Boolean, onAction: (SavedAction) -> Unit) {
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
                if (item.placeCount > 0) {
                    Text(pluralStringResource(R.plurals.places_count, item.placeCount, item.placeCount), style = MaterialTheme.typography.labelMedium, color = t.muted)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (card?.audio != null) {
                QuietButton(stringResource(if (playing) R.string.audio_playing else R.string.audio_listen), onClick = { onAction(SavedAction.Play(item.id)) })
            }
            if (card != null) QuietButton(stringResource(R.string.saved_practice_this), onClick = { onAction(SavedAction.StartFrom(item.id)) })
            QuietButton(stringResource(R.string.saved_unsave), onClick = { onAction(SavedAction.Unsave(item.id)) })
        }
    }
}
