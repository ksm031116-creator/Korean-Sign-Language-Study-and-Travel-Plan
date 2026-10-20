package app.signkorean.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.signkorean.R
import app.signkorean.navigation.AppNavigator
import app.signkorean.navigation.RootTab
import app.signkorean.navigation.Route
import app.signkorean.ui.designsystem.SignKoreanTheme

private val NavigatorSaver = Saver<AppNavigator, String>(save = { it.save() }, restore = { AppNavigator.restore(it) })

/** 넓은 화면(태블릿·펼친 폴더블)은 옆 레일, 휴대폰은 아래 탭 (구현 계획 Phase 4). */
private const val WIDE_SCREEN_DP = 600

/**
 * 앱의 최상위 셸. 루트 탭 Explore·Saved·Plan 과 화면 스택을 그린다.
 * 각 화면의 스크롤·필터는 화면별 저장 상태로 보존한다(탭을 오가도 그대로, PRD §14).
 */
@Composable
fun AppRoot(screen: @Composable (Route, AppNavigator) -> Unit = { route, _ -> PlaceholderScreen(route) }) {
    SignKoreanTheme {
        val navigator = rememberSaveable(saver = NavigatorSaver) { AppNavigator() }
        val state by navigator.state.collectAsState()
        val holder = rememberSaveableStateHolder()
        val wide = LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_DP

        BackHandler(enabled = state.stack.size > 1 || state.tab != RootTab.Explore) { navigator.back() }

        val content: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier) {
                holder.SaveableStateProvider(key = "${state.tab}/${state.stack.size}/${state.current}") {
                    screen(state.current, navigator)
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    RootTab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = state.tab == tab,
                            onClick = { navigator.selectTab(tab) },
                            icon = { Icon(tab.icon(), contentDescription = null) },
                            label = { Text(stringResource(tab.label())) },
                        )
                    }
                }
                content(Modifier.weight(1f))
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        RootTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = state.tab == tab,
                                onClick = { navigator.selectTab(tab) },
                                icon = { Icon(tab.icon(), contentDescription = null) },
                                label = { Text(stringResource(tab.label())) },
                            )
                        }
                    }
                },
            ) { padding -> content(Modifier.padding(padding)) }
        }
    }
}

private fun RootTab.icon(): ImageVector = when (this) {
    RootTab.Explore -> Icons.Filled.Search
    RootTab.Saved -> Icons.Filled.Star
    RootTab.Plan -> Icons.Filled.DateRange
}

private fun RootTab.label(): Int = when (this) {
    RootTab.Explore -> R.string.tab_explore
    RootTab.Saved -> R.string.tab_saved
    RootTab.Plan -> R.string.tab_plan
}

/** 아직 만들지 않은 화면. 진입점은 숨기므로 사용자에게는 보이지 않는다(구현 계획 원칙 3). */
@Composable
private fun PlaceholderScreen(route: Route) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(text = stringResource(R.string.screen_placeholder), style = MaterialTheme.typography.bodyLarge)
        Text(text = route.screen.name, style = MaterialTheme.typography.labelMedium, color = SignKoreanTheme.tokens.muted)
    }
}
