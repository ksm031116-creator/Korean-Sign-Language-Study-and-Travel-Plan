package app.signkorean.ui.saved

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.signkorean.domain.content.CardId
import app.signkorean.domain.learning.CardStatus
import app.signkorean.testing.ContentFixtures
import app.signkorean.ui.designsystem.SignKoreanTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1400dp")
class SavedScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val restroom = ContentFixtures.basic("b01", "화장실")

    @Test
    fun `무료면 n-5 를 보이고, 사라진 카드는 해제만 된다`() {
        val actions = mutableListOf<SavedAction>()
        val state = SavedUiState(
            loading = false,
            all = listOf(
                SavedItem(CardId("b01"), restroom, ContentFixtures.groups[0], 0, emptySet(), CardStatus.Known),
                SavedItem(CardId("gone"), null, null, 0, emptySet(), CardStatus.Unseen),
            ),
            freeLimit = 5,
        )
        compose.setContent { SignKoreanTheme { SavedContent(state) { actions += it } } }
        compose.onNodeWithText("2 of 5 free bookmarks used").assertExists()
        compose.onNodeWithText("This sign is no longer available.").assertExists()
        compose.onNodeWithTag(SavedTags.item("gone")).assertExists()
        // 두 줄 모두 해제할 수 있고, 학습 시작은 남아 있는 카드에만 있다.
        compose.onAllNodesWithText("Remove").assertCountEquals(2)
        compose.onAllNodesWithText("Practise").assertCountEquals(1)
    }

    @Test
    fun `비어 있으면 안내 문구`() {
        compose.setContent { SignKoreanTheme { SavedContent(SavedUiState(loading = false)) {} } }
        compose.onNodeWithText("No saved signs yet").assertExists()
    }

    @Test
    fun `유료면 한도 표시가 없다`() {
        compose.setContent { SignKoreanTheme { SavedContent(SavedUiState(loading = false, freeLimit = null)) {} } }
        compose.onNodeWithTag(SavedTags.QUOTA).assertDoesNotExist()
    }
}
