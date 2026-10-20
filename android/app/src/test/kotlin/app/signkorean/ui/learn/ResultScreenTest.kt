package app.signkorean.ui.learn

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.signkorean.domain.learning.SessionSummary
import app.signkorean.ui.designsystem.SignKoreanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ResultScreenTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `U-17 네 가지 수가 각각 따로 보인다`() {
        val actions = mutableListOf<ResultAction>()
        compose.setContent {
            SignKoreanTheme {
                ResultContent(ResultUiState(loading = false, summary = SessionSummary(6, 4, 7, 3), reachedLimit = true, canReview = true)) { actions += it }
            }
        }
        compose.onNodeWithTag(ResultTags.KNOWN).assertTextContains("6", substring = true)
        compose.onNodeWithTag(ResultTags.LEARNING).assertTextContains("4", substring = true)
        compose.onNodeWithTag(ResultTags.UNIQUE).assertTextContains("7", substring = true)
        compose.onNodeWithTag(ResultTags.REVIEW).assertTextContains("3", substring = true)
        compose.onNodeWithText("Review again").performClick()
        compose.onNodeWithText("Practice all").performClick()
        compose.onNodeWithText("Done").performClick()
        assertEquals(listOf(ResultAction.ReviewAgain, ResultAction.PracticeAll, ResultAction.Done), actions)
    }

    @Test
    fun `복습할 카드가 없으면 Review again 이 없다`() {
        compose.setContent {
            SignKoreanTheme { ResultContent(ResultUiState(loading = false, summary = SessionSummary(3, 0, 3, 0), canReview = false)) {} }
        }
        compose.onNodeWithText("Review again").assertDoesNotExist()
    }
}
