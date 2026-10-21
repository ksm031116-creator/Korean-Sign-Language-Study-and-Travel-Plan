package app.signkorean.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.domain.learning.Mode
import app.signkorean.domain.trip.TripId
import app.signkorean.ui.designsystem.AppChip
import app.signkorean.ui.designsystem.ConfirmDialog
import app.signkorean.ui.designsystem.LockBadge
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.text.asString
import app.signkorean.ui.upgrade.UpgradeFeature
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import java.time.LocalDate

/** SC13 의 Study this day 칸 (FR21, UI05). 날짜마다 따로 뷰모델을 둔다. [revision] 이 바뀌면(일정 편집) 카드를 다시 센다. */
@Composable
fun DayStudySection(
    trip: TripId,
    date: LocalDate,
    revision: Int,
    onOpenLearn: () -> Unit,
    onUpgrade: (UpgradeFeature) -> Unit,
    viewModel: DayStudyViewModel = koinViewModel(key = "study-${trip.value}-$date") { parametersOf(trip, date) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel, revision) { viewModel.onAction(DayStudyAction.Refresh) }
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                DayStudyEffect.OpenLearn -> onOpenLearn()
                is DayStudyEffect.Upgrade -> onUpgrade(effect.feature)
            }
        }
    }
    DayStudyContent(state, viewModel::onAction)
}

@Composable
fun DayStudyContent(state: DayStudyUiState, onAction: (DayStudyAction) -> Unit) {
    val t = SignKoreanTheme.tokens
    if (state.loading) return
    if (state.confirmReplace) {
        ConfirmDialog(
            title = stringResource(R.string.replace_title), body = stringResource(R.string.replace_body),
            confirm = stringResource(R.string.replace_end), dismiss = stringResource(R.string.replace_keep),
            onConfirm = { onAction(DayStudyAction.ConfirmReplace) }, onDismiss = { onAction(DayStudyAction.KeepSession) },
        )
    }
    Column(
        Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.study_title), style = MaterialTheme.typography.titleSmall)
        if (state.locked) {
            // 무료: 자물쇠 + 기능명. 누르면 SC21 (UX09).
            LockBadge(stringResource(R.string.study_title), onClick = { onAction(DayStudyAction.Start) })
            return@Column
        }
        if (state.loadFailed) {
            Text(stringResource(R.string.common_load_failed), style = MaterialTheme.typography.bodyMedium, color = t.danger, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            app.signkorean.ui.designsystem.QuietButton(stringResource(R.string.common_retry), onClick = { onAction(DayStudyAction.Refresh) })
            return@Column
        }
        if (state.noCards) {
            // T-15: 지어내지 않고 이유를 말한다.
            Text(stringResource(R.string.study_no_cards), style = MaterialTheme.typography.bodyMedium, color = t.muted)
            return@Column
        }
        Text(stringResource(R.string.study_body), style = MaterialTheme.typography.bodyMedium, color = t.muted)
        Text(pluralStringResource(R.plurals.study_count, state.cardCount, state.cardCount), style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppChip(stringResource(R.string.collection_mode_all), selected = state.mode == Mode.All, onClick = { onAction(DayStudyAction.SetMode(Mode.All)) })
            // 복습할 카드가 없으면 Review 를 고를 수 없다.
            if (state.canReview) {
                AppChip(stringResource(R.string.collection_mode_review), selected = state.mode == Mode.Review, onClick = { onAction(DayStudyAction.SetMode(Mode.Review)) })
            }
        }
        state.message?.let {
            Text(it.asString(), color = t.danger, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        PrimaryButton(stringResource(R.string.study_start), onClick = { onAction(DayStudyAction.Start) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
    }
}
