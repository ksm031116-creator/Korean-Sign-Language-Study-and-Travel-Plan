package app.signkorean.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import app.signkorean.R
import app.signkorean.navigation.AppNavigator
import app.signkorean.navigation.RootTab
import app.signkorean.navigation.Route
import app.signkorean.ui.designsystem.SignKoreanTheme

/** 화면 회전·언어 변경으로 Activity 가 다시 만들어져도 이동 상태를 지킨다. 프로세스가 끝나면 처음부터(PRD §14). */
class NavigatorViewModel : ViewModel() {
    val navigator = AppNavigator()
    val entryStores = EntryViewModelStores()

    override fun onCleared() = entryStores.clearAll()
}

/**
 * 스택 항목마다 따로 둔 ViewModel 저장소. 화면 ViewModel 이 Activity 에 묶이면 같은 화면을 다시 열 때
 * 지난번 ViewModel(지난 세션 상태, 남은 효과)을 그대로 받는다. 항목이 닫히면 그 저장소도 비운다.
 */
class EntryViewModelStores {
    private val stores = mutableMapOf<Long, ViewModelStore>()

    fun ownerFor(entryId: Long): ViewModelStoreOwner {
        val store = stores.getOrPut(entryId) { ViewModelStore() }
        return object : ViewModelStoreOwner {
            override val viewModelStore = store
        }
    }

    fun clear(entryId: Long) {
        stores.remove(entryId)?.clear()
    }

    fun clearAll() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }
}

/** 넓은 화면(태블릿·펼친 폴더블)은 옆 레일, 휴대폰은 아래 탭 (구현 계획 Phase 4). */
private const val WIDE_SCREEN_DP = 600

/**
 * 앱의 최상위 셸. 루트 탭 Explore·Saved·Plan 과 화면 스택을 그린다.
 * 각 화면의 스크롤·필터는 스택 항목 id 로 저장해 탭을 오가도 보존하고(PRD §14), 닫힌 항목의 저장 상태는 지운다.
 */
@Composable
fun AppRoot(
    root: NavigatorViewModel = viewModel(),
    navigator: AppNavigator = root.navigator,
    entryStores: EntryViewModelStores = root.entryStores,
    screen: @Composable (Route, AppNavigator) -> Unit = { route, nav -> AppScreen(route, nav) },
) {
    SignKoreanTheme {
        val state by navigator.state.collectAsState()
        val holder = rememberSaveableStateHolder()
        val wide = LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_DP

        // 닫힌 화면의 저장 상태를 지운다. 같은 화면을 다시 열면 새 항목이라 지난번 입력이 되살아나지 않는다.
        val seen = remember { mutableSetOf<Long>() }
        LaunchedEffect(state) {
            navigator.closedSince(seen.toSet()).forEach { id ->
                holder.removeState(id)
                entryStores.clear(id)
                seen -= id
            }
            seen += state.allEntryIds
        }

        BackHandler(enabled = state.stack.size > 1 || state.tab != RootTab.Explore) { navigator.back() }

        val content: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier) {
                val entry = state.current
                holder.SaveableStateProvider(key = entry.id) {
                    CompositionLocalProvider(LocalViewModelStoreOwner provides entryStores.ownerFor(entry.id)) {
                        screen(entry.route, navigator)
                    }
                }
            }
        }

        if (wide) {
            // 휴대폰은 Scaffold 가 시스템 막대 여백을 넣는다. 넓은 화면도 같은 여백을 직접 넣는다(Android 15 전체 화면).
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
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
internal fun PlaceholderScreen(route: Route) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
        Text(text = stringResource(R.string.screen_placeholder), style = MaterialTheme.typography.bodyLarge)
        Text(text = route.screen.name, style = MaterialTheme.typography.labelMedium, color = SignKoreanTheme.tokens.muted)
    }
}
