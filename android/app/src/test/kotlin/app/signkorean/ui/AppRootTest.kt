package app.signkorean.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import app.signkorean.navigation.AppNavigator
import app.signkorean.navigation.RootTab
import app.signkorean.navigation.Route
import app.signkorean.navigation.ScreenId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** 탭 전환은 각 화면 상태를 보존하고, 닫았다 다시 연 화면은 새로 시작한다 (PRD §14, Phase 4 리뷰 HIGH). */
@RunWith(RobolectricTestRunner::class)
class AppRootTest {

    @get:Rule
    val compose = createComposeRule()

    private val root = NavigatorViewModel()
    private val navigator = root.navigator

    class Probe : androidx.lifecycle.ViewModel() {
        val id = counter++
        companion object { var counter = 0 }
    }

    private val seenProbes = mutableListOf<Int>()

    private fun setUp() = compose.setContent {
        AppRoot(root) { route, _ ->
            when (route.screen) {
                ScreenId.SC02 -> {
                    val list = rememberLazyListState()
                    LazyColumn(state = list, modifier = Modifier.testTag("explore-list")) {
                        items(100) { Text("row $it") }
                    }
                }
                ScreenId.SC04 -> {
                    val probe = androidx.lifecycle.viewmodel.compose.viewModel<Probe>()
                    androidx.compose.runtime.SideEffect { if (seenProbes.lastOrNull() != probe.id) seenProbes += probe.id }
                    Text("probe ${probe.id}")
                }
                else -> {
                    var draft by rememberSaveable { mutableStateOf("") }
                    TextField(draft, { draft = it }, modifier = Modifier.testTag("draft"))
                }
            }
        }
    }

    @Test
    fun `탭을 오가도 Explore 목록 스크롤이 그대로다`() {
        setUp()
        compose.onNodeWithTag("explore-list").performScrollToIndex(60)
        compose.onNodeWithText("row 60").assertExists()
        compose.runOnIdle { navigator.selectTab(RootTab.Saved) }
        compose.runOnIdle { navigator.selectTab(RootTab.Explore) }
        compose.onNodeWithText("row 60").assertExists()
    }

    @Test
    fun `취소하고 다시 연 폼은 지난 입력 없이 시작한다`() {
        setUp()
        compose.runOnIdle { navigator.push(Route(ScreenId.SC12)) }
        compose.onNodeWithTag("draft").performTextInput("Seoul trip")
        compose.onNodeWithText("Seoul trip").assertExists()
        compose.runOnIdle { navigator.back() }
        compose.runOnIdle { navigator.push(Route(ScreenId.SC12)) }
        compose.onNodeWithText("Seoul trip").assertDoesNotExist()
    }

    @Test
    fun `다른 탭에 갔다 와도 입력 중인 폼은 남아 있다`() {
        setUp()
        compose.runOnIdle { navigator.selectTab(RootTab.Plan); navigator.push(Route(ScreenId.SC12)) }
        compose.onNodeWithTag("draft").performTextReplacement("Busan")
        compose.runOnIdle { navigator.selectTab(RootTab.Explore) }
        compose.runOnIdle { navigator.selectTab(RootTab.Plan) }
        compose.onNodeWithText("Busan").assertExists()
    }

    @Test
    fun `같은 화면을 다시 열면 새 ViewModel 을 받는다 (지난 세션 상태가 남지 않게)`() {
        setUp()
        compose.runOnIdle { navigator.push(Route(ScreenId.SC04)) }
        compose.runOnIdle { navigator.back() }
        compose.runOnIdle { navigator.push(Route(ScreenId.SC04)) }
        compose.waitForIdle()
        assertEquals(2, seenProbes.distinct().size)
    }

    @Test
    fun `탭을 오가는 동안에는 같은 ViewModel 을 쓴다`() {
        setUp()
        compose.runOnIdle { navigator.push(Route(ScreenId.SC04)) }
        compose.runOnIdle { navigator.selectTab(RootTab.Saved) }
        compose.runOnIdle { navigator.selectTab(RootTab.Explore) }
        compose.waitForIdle()
        assertEquals(1, seenProbes.distinct().size)
    }
}
