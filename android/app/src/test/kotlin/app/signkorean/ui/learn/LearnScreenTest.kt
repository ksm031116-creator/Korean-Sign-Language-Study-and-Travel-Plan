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
import androidx.compose.ui.test.swipe
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
import org.junit.Assert.assertTrue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onRoot
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

    private var host by androidx.compose.runtime.mutableStateOf(LearnUiState())

    /** ViewModel 처럼 판정마다 순번을 올리는 작은 상태 주인. 실패를 흉내 내려면 [failNext] 를 켠다. */
    private var failNext = false

    private fun show(card: app.signkorean.domain.content.SignCard = market, hasPlaces: Boolean = true, reduceMotion: Boolean = true, face: Face = Face.Front) {
        host = LearnUiState(loading = false, card = card, hasPlaces = hasPlaces, face = face)
        compose.setContent {
            SignKoreanTheme {
                LearnContent(host, { action ->
                    actions += action
                    if (action is LearnAction.Judge) {
                        host = if (failNext) host.copy(error = app.signkorean.ui.text.UiText.Res(app.signkorean.R.string.common_save_failed))
                        else host.copy(seq = host.seq + 1, decisions = host.decisions + 1)
                        failNext = false
                    }
                }, {}, reduceMotion)
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
    fun `U-01 임계 미달·대각선 끌기는 원위치`() {
        show()
        compose.onNodeWithTag(LearnTags.CARD).performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(width * 0.20f, 0f)) }
        compose.onNodeWithTag(LearnTags.CARD).performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(width * 0.30f, width * 0.30f)) }
        compose.waitForIdle()
        assertEquals(emptyList<LearnAction>(), actions)
    }

    @Test
    fun `날아가는 동안 두 번째 입력은 무시한다 (첫 판정을 덮지 않게)`() {
        show(reduceMotion = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(LearnTags.BUTTON_KNOW).performClick()
        compose.onNodeWithTag(LearnTags.BUTTON_DONT_KNOW).performClick()
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(listOf(LearnAction.Judge(Verdict.Know)), actions)
    }

    @Test
    fun `저장 실패면 날아간 카드가 제자리로 돌아와 보인다 (리뷰 HIGH-1)`() {
        failNext = true
        show(reduceMotion = false)
        compose.onNodeWithTag(LearnTags.BUTTON_KNOW).performClick()
        compose.waitForIdle()
        compose.onNodeWithTag(LearnTags.CARD).assertIsDisplayed()
        val bounds = compose.onNodeWithTag(LearnTags.CARD).fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertTrue("카드가 화면 안에 있다: $bounds", bounds.left >= root.left - 1 && bounds.right <= root.right + 1)
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
        compose.onNodeWithText("note").assertDoesNotExist()
        compose.onNodeWithTag(app.signkorean.ui.designsystem.SignPlateTags.PLATE, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `뒷면은 로마자와 뜻과 설명을 보인다`() {
        show(face = Face.Back)
        compose.onNodeWithText("roman").assertExists()
        compose.onNodeWithText("meaning").assertExists()
        compose.onNodeWithText("note").assertExists()
    }

    @Test
    fun `U-06 날아간 카드는 화면에서 잘려 가로로 넘치지 않는다`() {
        show(reduceMotion = false)
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag(LearnTags.BUTTON_KNOW).performClick()
        compose.mainClock.advanceTimeBy(200) // 날아가는 중
        val root = compose.onRoot().fetchSemanticsNode()
        val window = root.layoutInfo.coordinates.size.width
        assertEquals("화면 루트의 폭이 창보다 넓어지면 가로 스크롤이 생긴다", window, root.size.width)
        compose.onNodeWithTag(LearnTags.CARD).assertIsNotDisplayed()
    }
}
