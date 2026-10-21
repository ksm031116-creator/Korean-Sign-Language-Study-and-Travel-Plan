package app.signkorean.ui.upgrade

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SecondaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.text.UiText
import app.signkorean.ui.text.asString
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

object UpgradeTags {
    const val UNLOCK = "upgrade-unlock"
    const val RESTORE = "upgrade-restore"
    const val PROGRESS = "upgrade-progress"
}

/** SC21 (PRD §14.1). 닫히면 [onClose] 로 원래 화면에 돌아간다. 해제됐으면 토스트를 먼저 보인다. */
@Composable
fun UpgradeScreen(
    feature: UpgradeFeature,
    onClose: () -> Unit,
    viewModel: UpgradeViewModel = koinViewModel { parametersOf(feature) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 시스템 Back 은 Not now 와 같다. 결제·복원 중에는 아무것도 하지 않는다(받아서 버린다, 리뷰 UPG-02).
    BackHandler { if (!state.busy) viewModel.onAction(UpgradeAction.NotNow) }
    LaunchedEffect(viewModel) {
        for (effect in viewModel.effects) {
            when (effect) {
                is UpgradeEffect.Unlocked -> {
                    // Activity 의 Context 라 앱 언어(AppCompat)를 따른다.
                    val text = (effect.message as? UiText.Res)?.let { context.getString(it.id) }
                    if (text != null) Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                    onClose()
                }
                UpgradeEffect.Close -> onClose()
            }
        }
    }
    UpgradeContent(state, viewModel::onAction)
}

@Composable
fun UpgradeContent(state: UpgradeUiState, onAction: (UpgradeAction) -> Unit) {
    val t = SignKoreanTheme.tokens
    Column(
        Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.upgrade_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.upgrade_subtitle), style = MaterialTheme.typography.bodyMedium, color = t.muted)
        val lead = when {
            state.savedCount != null -> stringResource(R.string.upgrade_lead_bookmark, state.savedCount, state.limit)
            state.feature == UpgradeFeature.Route -> stringResource(R.string.upgrade_lead_route)
            state.feature == UpgradeFeature.Bookmarks -> stringResource(R.string.upgrade_lead_bookmarks)
            else -> stringResource(R.string.upgrade_lead_plan)
        }
        Text(lead, style = MaterialTheme.typography.titleMedium, color = t.primary)

        Column(
            Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(16.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Feature(Icons.Filled.Star, R.string.upgrade_feature_bookmarks, R.string.upgrade_feature_bookmarks_body)
            Feature(Icons.Filled.Place, R.string.upgrade_feature_routes, R.string.upgrade_feature_routes_body)
            Feature(Icons.Filled.DateRange, R.string.upgrade_feature_planner, R.string.upgrade_feature_planner_body)
        }

        Text(
            stringResource(R.string.upgrade_free_note),
            style = MaterialTheme.typography.bodyMedium,
            color = t.knowInk,
            modifier = Modifier.fillMaxWidth().background(t.knowSoft, RoundedCornerShape(12.dp)).padding(14.dp),
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            when {
                state.price != null -> Text(state.price, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                state.priceChecked -> Text(stringResource(R.string.upgrade_price_unavailable), style = MaterialTheme.typography.bodyMedium, color = t.muted)
            }
            Text(stringResource(R.string.upgrade_price_note), style = MaterialTheme.typography.labelMedium, color = t.muted)
        }

        state.message?.let {
            val error = state.failed || it == UiText.Res(R.string.upgrade_offline)
            Text(
                it.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) t.danger else t.ink,
                modifier = Modifier.fillMaxWidth()
                    .background(if (error) t.dangerSoft else t.primarySoft, RoundedCornerShape(12.dp))
                    .padding(14.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (state.busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                // 버튼이 왜 막혔는지 스크린리더에도 알린다 (UX03, 리뷰 UPG-12).
                modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                CircularProgressIndicator(Modifier.size(24.dp).testTag(UpgradeTags.PROGRESS), strokeWidth = 3.dp)
                Text(stringResource(R.string.upgrade_in_progress), style = MaterialTheme.typography.bodyMedium, color = t.muted)
            }
        }

        val unlockText = when {
            state.failed -> stringResource(R.string.common_retry)
            state.price != null -> stringResource(R.string.upgrade_unlock_for, state.price)
            else -> stringResource(R.string.upgrade_unlock)
        }
        PrimaryButton(
            unlockText,
            onClick = { onAction(UpgradeAction.Unlock) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag(UpgradeTags.UNLOCK),
        )
        SecondaryButton(
            stringResource(R.string.upgrade_restore),
            onClick = { onAction(UpgradeAction.Restore) },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth().testTag(UpgradeTags.RESTORE),
        )
        QuietButton(stringResource(R.string.upgrade_not_now), onClick = { onAction(UpgradeAction.NotNow) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun Feature(icon: ImageVector, title: Int, body: Int) {
    val t = SignKoreanTheme.tokens
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(icon, contentDescription = null, tint = t.primary, modifier = Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = t.muted)
        }
    }
}
