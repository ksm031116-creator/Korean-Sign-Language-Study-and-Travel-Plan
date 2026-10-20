package app.signkorean.ui.designsystem

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertContentDescriptionContains
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.signkorean.domain.content.PictogramId
import app.signkorean.testing.ContentFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import app.signkorean.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DesignSystemTest {

    @get:Rule
    val compose = createComposeRule()

    private val restroomCard = ContentFixtures.basic("b01", "화장실").copy(pictogram = PictogramId("restroom"))
    private val plainCard = ContentFixtures.basic("b02", "입구")

    @Test
    // 화면 밖 요소는 누르는 영역이 잘려 보이므로 카탈로그 전체가 보이는 긴 화면에서 잰다.
    @Config(qualifiers = "w411dp-h2400dp")
    fun `U-10 카탈로그의 누를 수 있는 요소는 모두 48dp 이상`() {
        compose.setContent { SignKoreanTheme { DesignSystemCatalog() } }
        val clickable = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).fetchSemanticsNodes()
        assertTrue("누를 수 있는 요소가 너무 적다: ${clickable.size}", clickable.size >= 8)
        // 그림 크기가 아니라 누르는 영역(touch bounds)을 본다. Material 은 작은 부품의 누르는 영역을 48dp 로 넓힌다.
        val small = clickable.filter { node ->
            val bounds = node.touchBoundsInRoot
            val w = with(compose.density) { bounds.width.toDp() }
            val h = with(compose.density) { bounds.height.toDp() }
            w < 48.dp || h < 48.dp
        }.map { it.config.getOrNull(SemanticsProperties.ContentDescription) ?: it.config.getOrNull(SemanticsProperties.Text) ?: it.id }
        assertEquals(emptyList<Any>(), small)
    }

    private fun drawnPictogram(): String? =
        compose.onNodeWithTag(SignPlateTags.PLATE).fetchSemanticsNode().config.getOrNull(DrawnPictogram)

    @Test
    fun `U-18 앞면에는 픽토그램이 있는 카드도 픽토그램을 그리지 않는다`() {
        compose.setContent { SignKoreanTheme { SignPlate(restroomCard, PlateFace.Front) } }
        assertEquals(null, drawnPictogram())
        compose.onNodeWithTag(SignPlateTags.PLATE).assertContentDescriptionContains("화장실", substring = true)
    }

    @Test
    fun `U-18 앞면의 스크린리더 설명은 그림을 말하지 않는다`() {
        compose.setContent { SignKoreanTheme { SignPlate(restroomCard, PlateFace.Front) } }
        val description = compose.onNodeWithTag(SignPlateTags.PLATE).fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        val resources = RuntimeEnvironment.getApplication().resources
        // 앞면 설명은 단어 설명 문구와 정확히 같고, 그림 이름을 담지 않는다.
        assertEquals(resources.getString(R.string.plate_description, "화장실"), description)
        assertTrue(description, resources.getString(R.string.pictogram_restroom) !in description)
    }

    @Test
    fun `U-18 뒷면은 픽토그램이 있는 카드만 표지판 안에 그린다`() {
        compose.setContent { SignKoreanTheme { SignPlate(restroomCard, PlateFace.Back) } }
        assertEquals("restroom", drawnPictogram())
        compose.onNodeWithTag(SignPlateTags.PLATE).assertContentDescriptionContains("restroom", substring = true)
    }

    @Test
    fun `U-18 픽토그램이 없는 카드의 뒷면은 단어만`() {
        compose.setContent { SignKoreanTheme { SignPlate(plainCard, PlateFace.Back) } }
        assertEquals(null, drawnPictogram())
        compose.onNodeWithTag(SignPlateTags.PLATE).assertContentDescriptionContains("입구", substring = true)
    }

    @Test
    fun `아직 그림이 없는 픽토그램 ID 는 단어만 그린다 (C-11)`() {
        val noArt = plainCard.copy(pictogram = PictogramId("wifi-not-drawn-yet"))
        compose.setContent { SignKoreanTheme { SignPlate(noArt, PlateFace.Back) } }
        assertEquals(null, drawnPictogram())
    }

    @Test
    fun `UX09 LockBadge 는 자물쇠와 기능명을 함께 보이고 누를 수 있다`() {
        var opened = 0
        compose.setContent { SignKoreanTheme { LockBadge(feature = "Trip planner", onClick = { opened++ }) } }
        compose.onNodeWithText("Trip planner", substring = true).assertHasClickAction().performClick()
        compose.onNodeWithTag(LockBadgeTags.LOCK_ICON, useUnmergedTree = true).assertExists()
        assertEquals(1, opened)
    }

    @Test
    fun `UX03 오류 상태의 다시 시도는 콜백을 부른다`() {
        var retried = 0
        compose.setContent { SignKoreanTheme { ErrorState(message = "Couldn't load.", onRetry = { retried++ }) } }
        compose.onNodeWithText("Try again").performClick()
        assertEquals(1, retried)
    }

    @Test
    fun `SC20 확인 모달은 확인과 취소를 각각 알린다`() {
        val events = mutableListOf<String>()
        compose.setContent {
            SignKoreanTheme {
                ConfirmDialog(title = "End session?", body = "Progress so far is saved.", confirm = "End session", dismiss = "Keep session",
                    onConfirm = { events += "confirm" }, onDismiss = { events += "dismiss" })
            }
        }
        compose.onNodeWithText("Keep session").performClick()
        compose.onNodeWithText("End session").performClick()
        assertEquals(listOf("dismiss", "confirm"), events)
    }
}
