package app.signkorean.ui.explore

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.CardId
import app.signkorean.domain.learning.CardStatus
import app.signkorean.domain.learning.SessionSource
import app.signkorean.testing.ContentFixtures
import app.signkorean.ui.designsystem.SignKoreanTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/** SC02·SC03 스크린샷 (390dp, en). */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExploreScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val restroom = ContentFixtures.basic("b01", "화장실")
    private val exit = ContentFixtures.basic("b02", "출구", group = "transit").copy(plateColor = "#1f6f78")
    private val push = ContentFixtures.basic("b03", "미세요", group = "transit").copy(plateColor = "#3a7d44")

    @Test
    fun explore() {
        RuntimeEnvironment.setQualifiers("en-w390dp-h1400dp")
        val state = ExploreUiState(
            loading = false,
            basics = Progress(100, 12, 5),
            basicsPreview = listOf(restroom, exit, push),
            areas = listOf(
                AreaUi(AreaId("myeongdong"), ContentFixtures.en("Myeongdong & Namdaemun"), "명동·남대문", ContentFixtures.en("Food & shopping"), "#b8554a", 6, Progress(18, 4, 2)),
                AreaUi(AreaId("hongdae"), ContentFixtures.en("Hongdae & Yeonnam"), "홍대·연남", ContentFixtures.en("Youth & late nights"), "#9c5a9e", 5, Progress(14, 0, 0)),
            ),
            resume = ResumeUi(SessionSource.Basics, null, 4),
        )
        compose.setContent { SignKoreanTheme { ExploreContent(state, {}, {}, {}, {}, {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/explore_en.png")
    }

    @Test
    fun basics() {
        RuntimeEnvironment.setQualifiers("en-w390dp-h1200dp")
        val state = CollectionUiState(
            loading = false,
            isBasics = true,
            progress = Progress(3, 1, 1),
            groups = listOf(
                GroupUi(ContentFixtures.groups[0], listOf(WordChip(CardId("b01"), "화장실", CardStatus.Known))),
                GroupUi(ContentFixtures.groups[1], listOf(WordChip(CardId("b02"), "출구", CardStatus.Learning), WordChip(CardId("b03"), "미세요", CardStatus.Unseen))),
            ),
        )
        compose.setContent { SignKoreanTheme { CollectionContent(state, {}, {}, {}) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/collection_basics_en.png")
    }
}
