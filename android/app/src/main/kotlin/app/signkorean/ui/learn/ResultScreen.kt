package app.signkorean.ui.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SecondaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.text.asString
import org.koin.androidx.compose.koinViewModel

object ResultTags {
    const val KNOWN = "result-known"
    const val LEARNING = "result-learning"
    const val UNIQUE = "result-unique"
    const val REVIEW = "result-review"
}

/** SC06 학습 결과 (FR08). */
@Composable
fun ResultScreen(
    onOpenLearn: () -> Unit,
    onClose: () -> Unit,
    onUpgrade: (app.signkorean.ui.upgrade.UpgradeFeature) -> Unit = {},
    viewModel: ResultViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                ResultEffect.OpenLearn -> onOpenLearn()
                ResultEffect.Close -> onClose()
                is ResultEffect.Upgrade -> onUpgrade(effect.feature)
            }
        }
    }
    ResultContent(state, viewModel::onAction)
}

@Composable
fun ResultContent(state: ResultUiState, onAction: (ResultAction) -> Unit) {
    val t = SignKoreanTheme.tokens
    val summary = state.summary
    if (state.loading) return
    if (summary == null) {
        app.signkorean.ui.designsystem.EmptyState(title = stringResource(R.string.learn_no_session_title), body = "") {
            QuietButton(stringResource(R.string.result_done), onClick = { onAction(ResultAction.Done) })
        }
        return
    }
    Column(
        Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(if (state.reachedLimit) R.string.result_title_complete else R.string.result_title_ended),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        run {
            // 판정 횟수와 고유 카드 수는 다를 수 있어 따로 보인다 (FR08, PRD §15.1).
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.result_known), summary.knownDecisions, t.knowSoft, t.knowInk, ResultTags.KNOWN, Modifier.weight(1f))
                Stat(stringResource(R.string.result_learning), summary.learningDecisions, t.accentSoft, t.accentInk, ResultTags.LEARNING, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(stringResource(R.string.result_unique), summary.uniqueCards, t.surface, t.ink, ResultTags.UNIQUE, Modifier.weight(1f))
                Stat(stringResource(R.string.result_review_left), summary.remainingReview, t.surface, t.ink, ResultTags.REVIEW, Modifier.weight(1f))
            }
        }
        state.message?.let { Text(it.asString(), color = t.muted, style = MaterialTheme.typography.bodyMedium) }
        if (state.canReview) {
            PrimaryButton(stringResource(R.string.result_review_again), onClick = { onAction(ResultAction.ReviewAgain) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        }
        SecondaryButton(stringResource(R.string.result_practice_all), onClick = { onAction(ResultAction.PracticeAll) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
        QuietButton(stringResource(R.string.result_done), onClick = { onAction(ResultAction.Done) }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Stat(label: String, value: Int, fill: Color, ink: Color, tag: String, modifier: Modifier) {
    Column(
        modifier.background(fill, RoundedCornerShape(14.dp)).padding(14.dp).testTag(tag).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(value.toString(), color = ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(label, color = ink, style = MaterialTheme.typography.labelMedium)
    }
}
