package app.signkorean.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.onboarding.AppGateViewModel
import app.signkorean.ui.onboarding.FirstRunScreen
import org.koin.androidx.compose.koinViewModel

/** 처음 실행이면 SC01 안내, 아니면 앱 셸. 판단 전에는 아무것도 그리지 않는다. */
@Composable
fun AppEntry(gate: AppGateViewModel = koinViewModel()) {
    val needsFirstRun by gate.needsFirstRun.collectAsStateWithLifecycle()
    when (needsFirstRun) {
        null -> Unit
        true -> SignKoreanTheme { FirstRunScreen(koinViewModel(), onFinished = gate::finished) }
        false -> AppRoot()
    }
}
