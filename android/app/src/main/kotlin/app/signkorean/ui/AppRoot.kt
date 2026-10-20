package app.signkorean.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.signkorean.R

/**
 * 앱의 최상위 화면. Phase 1 에서는 빈 셸이며, 탭·내비게이션과 디자인 시스템 테마는 Phase 4 에서 채운다.
 * 사용자에게 보이는 문구는 모두 문자열 리소스에서 가져온다 (AR-06).
 */
@Composable
fun AppRoot() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(text = stringResource(R.string.app_tagline), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
