package app.signkorean.ui.learn

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.signkorean.domain.content.CardKind
import app.signkorean.testing.ContentFixtures
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.text.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 시연·스크린리더·큰 글자 (U-03·04·07). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h844dp")
class LearnAccessibilityTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val actions = mutableListOf<LearnAction>()
    private val market = ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market"))

    private fun show(state: LearnUiState, fontScale: Float = 1f, reduceMotion: Boolean = false) = compose.setContent {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
            SignKoreanTheme { LearnContent(state, { actions += it }, {}, reduceMotion) }
        }
    }

    @Test
    fun `U-07 시연은 처음부터 화면 안에 있고 Got it 으로 닫힌다`() {
        show(LearnUiState(loading = false, card = market, hasPlaces = true, showCoach = true))
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val demo = compose.onNodeWithTag(CoachTags.DEMO).fetchSemanticsNode().boundsInRoot
        assertTrue("시연 카드가 화면 안에서 시작한다: $demo / $root", demo.left >= root.left && demo.right <= root.right && demo.top >= root.top)
        compose.onNodeWithText("Got it").performClick()
        assertEquals(listOf(LearnAction.DismissCoach), actions)
    }

    @Test
    fun `U-07 시연은 Back 으로도 닫힌다`() {
        show(LearnUiState(loading = false, card = market, hasPlaces = true, showCoach = true))
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        assertEquals(listOf(LearnAction.DismissCoach), actions)
    }

    @Test
    fun `장소 없는 카드의 시연은 아래 설명을 빼고, 움직임 줄이기면 멈춘 채 보인다`() {
        show(LearnUiState(loading = false, card = market, hasPlaces = false, showCoach = true), reduceMotion = true)
        compose.onNodeWithText("Swipe down", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(CoachTags.COACH).assertIsDisplayed()
    }

    @Test
    fun `U-03 카드에는 이름과 조작 방법, 판정 결과는 알림 영역으로`() {
        show(LearnUiState(loading = false, card = market, hasPlaces = true, announcement = UiText.Res(app.signkorean.R.string.learn_announce_know)))
        val card = compose.onNodeWithTag(LearnTags.CARD).fetchSemanticsNode().config
        assertTrue(card[SemanticsProperties.ContentDescription].joinToString().contains("국밥"))
        assertTrue(card[SemanticsProperties.ContentDescription].joinToString().contains("down"))
        compose.onNodeWithTag(LearnTags.ANNOUNCEMENT).assertContentDescriptionEquals("Saved as I know.")
        assertEquals(androidx.compose.ui.semantics.LiveRegionMode.Polite, compose.onNodeWithTag(LearnTags.ANNOUNCEMENT).fetchSemanticsNode().config[SemanticsProperties.LiveRegion])
    }

    @Test
    fun `U-04 글자 200 퍼센트에서도 판정 버튼이 보이고 서로 겹치지 않으며 48dp 이상`() {
        show(LearnUiState(loading = false, card = market, hasPlaces = true), fontScale = 2f)
        val tags = listOf(LearnTags.BUTTON_DONT_KNOW, LearnTags.BUTTON_PLACES, LearnTags.BUTTON_KNOW)
        val bounds = tags.map { tag ->
            compose.onNodeWithTag(tag).assertIsDisplayed()
            compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        }
        bounds.forEach { b -> assertTrue("48dp 이상: $b", with(compose.density) { b.width.toDp() >= 48.dp && b.height.toDp() >= 48.dp }) }
        bounds.zipWithNext().forEach { (a, b) -> assertTrue("겹침: $a / $b", a.right <= b.left) }
    }

    @Test
    fun `시연이 떠 있으면 어두운 배경을 끌어도 카드가 판정되지 않는다 (리뷰 HIGH-1)`() {
        show(LearnUiState(loading = false, card = market, hasPlaces = true, showCoach = true), reduceMotion = true)
        compose.onNodeWithTag(CoachTags.COACH).performTouchInput { swipe(androidx.compose.ui.geometry.Offset(width * 0.1f, height * 0.05f), androidx.compose.ui.geometry.Offset(width * 0.95f, height * 0.05f)) }
        compose.waitForIdle()
        assertTrue("$actions", actions.none { it is LearnAction.Judge })
    }

    @Test
    fun `같은 판정이 이어져도 알림 노드를 새로 만들어 다시 읽힌다`() {
        var state by androidx.compose.runtime.mutableStateOf(LearnUiState(loading = false, card = market, hasPlaces = true, seq = 1, announcement = UiText.Res(app.signkorean.R.string.learn_announce_know)))
        var ids = mutableSetOf<Int>()
        compose.setContent { SignKoreanTheme { LearnContent(state, {}, {}, true) } }
        ids += compose.onNodeWithTag(LearnTags.ANNOUNCEMENT).fetchSemanticsNode().id
        compose.runOnIdle { state = state.copy(seq = 2) }
        ids += compose.onNodeWithTag(LearnTags.ANNOUNCEMENT).fetchSemanticsNode().id
        assertEquals("같은 문구라도 판정마다 새 알림 노드", 2, ids.size)
    }
}
