package app.signkorean.ui.learn

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.learning.Face
import app.signkorean.domain.learning.Verdict
import app.signkorean.testing.ContentFixtures
import app.signkorean.ui.designsystem.SignKoreanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** SC04 입력 (U-01·02·05·06·08). 상태만 받는 LearnContent 에 실제 포인터·키 입력을 준다. */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class)
class LearnScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val actions = mutableListOf<LearnAction>()
    private val market = ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market"))
    private val basic = ContentFixtures.basic("b01", "화장실")

    private fun show(card: app.signkorean.domain.content.SignCard = market, hasPlaces: Boolean = true, reduceMotion: Boolean = true, face: Face = Face.Front) {
        compose.setContent {
            SignKoreanTheme {
                LearnContent(LearnUiState(loading = false, card = card, hasPlaces = hasPlaces, face = face), { actions += it }, {}, reduceMotion)
            }
        }
    }

    @Test
    fun `U-01 오른쪽 드래그는 알아요, 왼쪽은 몰라요, 아래는 장소, 위는 아무것도 아니다`() {
        show()
        compose.onNodeWithTag(LearnTags.CARD).performTouchInput { swipeRight() }
        compose.onNodeWithTag(LearnTags.CARD).performTouchInput { swipeLeft() }
        compose.onNodeWithTag(LearnTags.CARD).performTouchInput { swipeDown() }
        compose.onNodeWithTag(LearnTags.CARD).performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(listOf(LearnAction.Judge(Verdict.Know), LearnAction.Judge(Verdict.DontKnow), LearnAction.OpenPlaces), actions)
    }

    @Test
    fun `U-02 원형 버튼은 스와이프와 같은 결과`() {
        show()
        compose.onNodeWithTag(LearnTags.BUTTON_KNOW).performClick()
        compose.onNodeWithTag(LearnTags.BUTTON_DONT_KNOW).performClick()
        compose.onNodeWithTag(LearnTags.BUTTON_PLACES).performClick()
        compose.waitForIdle()
        assertEquals(listOf(LearnAction.Judge(Verdict.Know), LearnAction.Judge(Verdict.DontKnow), LearnAction.OpenPlaces), actions)
    }

    @Test
    fun `U-02 키보드 화살표와 Enter`() {
        show()
        val card = compose.onNodeWithTag(LearnTags.CARD)
        card.requestFocus()
        card.performKeyInput { pressKey(Key.DirectionRight) }
        card.performKeyInput { pressKey(Key.DirectionLeft) }
        card.performKeyInput { pressKey(Key.DirectionDown) }
        card.performKeyInput { pressKey(Key.Enter) }
        compose.waitForIdle()
        assertEquals(listOf(LearnAction.Judge(Verdict.Know), LearnAction.Judge(Verdict.DontKnow), LearnAction.OpenPlaces, LearnAction.Flip), actions)
    }

    @Test
    fun `탭은 뒤집기`() {
        show()
        compose.onNodeWithTag(LearnTags.CARD).performClick()
        assertEquals(listOf(LearnAction.Flip), actions)
    }

    @Test
    fun `U-05 움직임 줄이기면 시간이 흐르지 않아도 바로 판정한다`() {
        show(reduceMotion = true)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(LearnTags.BUTTON_KNOW).performClick()
        compose.mainClock.advanceTimeByFrame()
        assertEquals(listOf(LearnAction.Judge(Verdict.Know)), actions)
    }

    @Test
    fun `U-08 Basics 카드는 범례·버튼에 아래가 없고 아래 드래그·키도 무시한다`() {
        show(card = basic, hasPlaces = false)
        compose.onNodeWithTag(LearnTags.LEGEND_PLACES).assertDoesNotExist()
        compose.onNodeWithTag(LearnTags.BUTTON_PLACES).assertDoesNotExist()
        compose.onNodeWithTag(LearnTags.CARD).performTouchInput { swipeDown() }
        compose.onNodeWithTag(LearnTags.CARD).requestFocus()
        compose.onNodeWithTag(LearnTags.CARD).performKeyInput { pressKey(Key.DirectionDown) }
        compose.waitForIdle()
        assertEquals(emptyList<LearnAction>(), actions)
    }

    @Test
    fun `앞면은 단어만, 뒷면은 로마자·뜻·설명`() {
        show(face = Face.Front)
        compose.onNodeWithText("roman").assertDoesNotExist()
        compose.onNodeWithText("meaning").assertDoesNotExist()
    }

    @Test
    fun `뒷면은 로마자와 뜻과 설명을 보인다`() {
        show(face = Face.Back)
        compose.onNodeWithText("roman").assertExists()
        compose.onNodeWithText("meaning").assertExists()
        compose.onNodeWithText("note").assertExists()
    }

    @Test
    fun `U-06 날아간 카드가 있어도 가로 스크롤이 생기지 않는다`() {
        show(reduceMotion = false)
        compose.onNodeWithTag(LearnTags.CARD).performTouchInput { swipeRight() }
        compose.waitForIdle()
        val scrollable = compose.onAllNodes(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.HorizontalScrollAxisRange)).fetchSemanticsNodes()
        assertEquals(0, scrollable.size)
    }
}
