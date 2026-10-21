package app.signkorean.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.ui.designsystem.AppChip
import app.signkorean.ui.designsystem.ConfirmDialog
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SecondaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.text.asString
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

/** SC10 설정 (Phase 20 D1). */
@Composable
fun SettingsScreen(onClose: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onAction(SettingsAction.Refresh)
        onPauseOrDispose { }
    }
    SettingsContent(state, viewModel::onAction, onClose)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsContent(state: SettingsUiState, onAction: (SettingsAction) -> Unit, onClose: () -> Unit) {
    val t = SignKoreanTheme.tokens
    when (state.confirm) {
        SettingsConfirm.ResetLearning -> ConfirmDialog(
            title = stringResource(R.string.settings_reset_title), body = stringResource(R.string.settings_reset_body),
            confirm = stringResource(R.string.settings_reset_confirm), dismiss = stringResource(R.string.settings_cancel),
            onConfirm = { onAction(SettingsAction.Confirm) }, onDismiss = { onAction(SettingsAction.Cancel) },
        )
        SettingsConfirm.ClearBookmarks -> ConfirmDialog(
            title = stringResource(R.string.settings_clear_title), body = stringResource(R.string.settings_clear_body),
            confirm = stringResource(R.string.settings_clear_confirm), dismiss = stringResource(R.string.settings_cancel),
            onConfirm = { onAction(SettingsAction.Confirm) }, onDismiss = { onAction(SettingsAction.Cancel) },
        )
        null -> Unit
    }
    Column(
        Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        state.message?.let {
            Text(
                it.asString(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().background(t.accentSoft, RoundedCornerShape(12.dp)).padding(12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        if (state.languages.size > 1) {
            Section(stringResource(R.string.settings_language)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.languages.forEach { tag ->
                        // 언어 이름은 그 언어로 쓴다(English, 日本語).
                        val locale = Locale.forLanguageTag(tag.value)
                        val name = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
                        AppChip(name, selected = tag == state.current, onClick = { onAction(SettingsAction.SelectLanguage(tag)) })
                    }
                }
            }
        }

        Section(stringResource(R.string.settings_purchase)) {
            Text(
                stringResource(
                    when (state.entitlement) {
                        Entitlement.Paid -> R.string.settings_paid
                        Entitlement.Free -> R.string.settings_free
                        null -> R.string.settings_status_unknown
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            SecondaryButton(stringResource(R.string.upgrade_restore), onClick = { onAction(SettingsAction.Restore) }, enabled = !state.busy)
            state.relockLabel?.let { QuietButton(stringResource(it), onClick = { onAction(SettingsAction.Relock) }) }
        }

        Section(stringResource(R.string.settings_learning)) {
            QuietButton(stringResource(R.string.settings_coach_reset), onClick = { onAction(SettingsAction.ResetCoach) })
            QuietButton(stringResource(R.string.settings_reset_learning), onClick = { onAction(SettingsAction.AskResetLearning) }, enabled = !state.busy)
            QuietButton(stringResource(R.string.settings_clear_bookmarks), onClick = { onAction(SettingsAction.AskClearBookmarks) }, enabled = !state.busy)
        }

        Section(stringResource(R.string.settings_about)) {
            Info(stringResource(R.string.settings_audio_title), stringResource(R.string.settings_audio_body))
            Info(stringResource(R.string.settings_privacy_title), stringResource(R.string.settings_privacy_body))
            Info(stringResource(R.string.settings_photos_title), stringResource(R.string.settings_photos_body))
            // 일본 법적 표기 자리(Q-04). 일본어 사용자에게만 보인다(L10N-14). 내용은 Q-04 확정 뒤에 채운다.
            if (state.current?.value == "ja") Info(stringResource(R.string.settings_legal_jp), stringResource(R.string.settings_legal_jp_body))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val t = SignKoreanTheme.tokens
    Column(
        Modifier.fillMaxWidth().background(t.surface, RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun Info(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = SignKoreanTheme.tokens.muted)
    }
}
