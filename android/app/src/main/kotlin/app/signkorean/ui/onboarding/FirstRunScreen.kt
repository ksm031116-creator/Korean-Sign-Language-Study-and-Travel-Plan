package app.signkorean.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.R
import app.signkorean.ui.designsystem.AppChip
import app.signkorean.ui.designsystem.PrimaryButton
import app.signkorean.ui.designsystem.QuietButton
import app.signkorean.ui.designsystem.SecondaryButton
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.text.KoreanText
import java.util.Locale

private val TITLES = listOf(R.string.first_run_title_1, R.string.first_run_title_2, R.string.first_run_title_3)
private val BODIES = listOf(R.string.first_run_body_1, R.string.first_run_body_2, R.string.first_run_body_3)
private val ARTS = listOf(R.string.ko_first_run_art_1, R.string.ko_first_run_art_2, R.string.ko_first_run_art_3)

@Composable
fun FirstRunScreen(viewModel: FirstRunViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }
    FirstRunContent(state, viewModel::onAction)
}

/** SC01 첫 실행 안내. 영어·한국어 병기 설명, Skip/Next/Back/Get started. */
@Composable
fun FirstRunContent(state: FirstRunUiState, onAction: (FirstRunAction) -> Unit) {
    val t = SignKoreanTheme.tokens
    val last = state.step == state.steps - 1
    Column(
        Modifier.fillMaxSize().background(t.paper).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (!last) QuietButton(stringResource(R.string.first_run_skip), onClick = { onAction(FirstRunAction.Finish) })
        }
        Box(Modifier.fillMaxWidth().height(150.dp).background(t.primarySoft, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            KoreanText(stringResource(ARTS[state.step]), style = MaterialTheme.typography.headlineSmall.copy(fontSize = 34.sp, fontWeight = FontWeight.Bold, color = t.primary))
        }
        Text(stringResource(R.string.first_run_step, state.step + 1, state.steps), style = MaterialTheme.typography.labelMedium, color = t.muted)
        Text(stringResource(TITLES[state.step]), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(BODIES[state.step]), style = MaterialTheme.typography.bodyLarge)
        if (state.step == 0 && state.languages.size > 1) {
            Text(stringResource(R.string.first_run_language), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.languages.forEach { tag ->
                    // 언어 이름은 그 언어로 쓴다(English, 日本語). 언어를 더해도 코드는 바뀌지 않는다(L10N-08).
                    val locale = Locale.forLanguageTag(tag.value)
                    val name = locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
                    AppChip(name, selected = tag == state.current, onClick = { onAction(FirstRunAction.SelectLanguage(tag)) })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
            repeat(state.steps) { i ->
                Box(Modifier.padding(4.dp).size(8.dp).background(if (i == state.step) t.primary else t.line, CircleShape))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (state.step > 0) SecondaryButton(stringResource(R.string.first_run_back), onClick = { onAction(FirstRunAction.Back) }, modifier = Modifier.weight(1f))
            PrimaryButton(
                stringResource(if (last) R.string.first_run_start else R.string.first_run_next),
                onClick = { onAction(if (last) FirstRunAction.Finish else FirstRunAction.Next) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}
