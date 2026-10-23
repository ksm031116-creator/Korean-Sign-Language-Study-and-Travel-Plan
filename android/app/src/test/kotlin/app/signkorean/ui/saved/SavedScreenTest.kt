package app.signkorean.ui.saved

import androidx.compose.ui.test.onNodeWithContentDescription
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

    private val myeongdong = app.signkorean.domain.content.AreaId("myeongdong")

    @Test
    fun `P11 지역 칩으로 좁힌다 (분류·지역·상태 필터)`() {
        val actions = mutableListOf<SavedAction>()
        val state = SavedUiState(
            loading = false,
            all = listOf(SavedItem(CardId("b01"), restroom, ContentFixtures.groups[0], 1, setOf(myeongdong), CardStatus.Unseen)),
            areaNames = mapOf(myeongdong to ContentFixtures.area("myeongdong").name),
            freeLimit = null,
        )
        compose.setContent { SignKoreanTheme { SavedContent(state) { actions += it } } }
        compose.onNodeWithText("Myeongdong").performClick()
        assertEquals(listOf<SavedAction>(SavedAction.FilterArea(myeongdong)), actions)
    }

    @Test
    fun `P11 무료로 돌아가 5개를 넘게 갖고 있으면 실제 개수를 보인다 (E-12)`() {
        val many = (1..8).map { SavedItem(CardId("c$it"), restroom.copy(id = CardId("c$it")), ContentFixtures.groups[0], 0, emptySet(), CardStatus.Unseen) }
        compose.setContent { SignKoreanTheme { SavedContent(SavedUiState(loading = false, all = many, freeLimit = 5)) {} } }
        compose.onNodeWithText("8 saved. The free plan keeps 5 — remove some or unlock to save more.").assertExists()
        compose.onNodeWithText("5 of 5 free bookmarks used").assertDoesNotExist()
    }

    @Test
    fun `P11 불러오지 못하면 오류만 보이고 빈 목록 안내는 보이지 않는다`() {
        compose.setContent { SignKoreanTheme { SavedContent(SavedUiState(loading = false, failed = true)) {} } }
        compose.onNodeWithText("No saved signs yet").assertDoesNotExist()
        compose.onNodeWithTag(SavedTags.QUOTA).assertDoesNotExist()
    }

    @Test
    fun `P11 줄마다 버튼 이름에 그 카드가 들어간다 (스크린리더로 구분)`() {
        val state = SavedUiState(
            loading = false,
            all = listOf(
                SavedItem(CardId("b01"), restroom, ContentFixtures.groups[0], 0, emptySet(), CardStatus.Known),
                SavedItem(CardId("gone"), null, null, 0, emptySet(), CardStatus.Unseen),
            ),
        )
        compose.setContent { SignKoreanTheme { SavedContent(state) {} } }
        compose.onNodeWithContentDescription("Practise 화장실").assertExists()
        compose.onNodeWithContentDescription("Remove 화장실 from saved").assertExists()
        compose.onNodeWithContentDescription("Remove the unavailable sign from saved").assertExists()
    }
}
