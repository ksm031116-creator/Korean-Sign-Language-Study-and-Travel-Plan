package app.signkorean.ui.learn

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.common.LocalizedText
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.PictogramId
import app.signkorean.domain.learning.Face
import app.signkorean.testing.ContentFixtures
import app.signkorean.ui.designsystem.SignKoreanTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/** SC04 스크린샷: 앞면(장소 있는 카드) · 뒷면(픽토그램 카드) × en·ja, 390dp. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LearnScreenshotTest(private val language: String) {

    @get:Rule
    val compose = createComposeRule()

    private val restroom = ContentFixtures.basic("b01", "화장실").copy(
        romanization = "hwajangsil",
        pictogram = PictogramId("restroom"),
        meaning = LocalizedText(mapOf(LocaleTag("en") to "Restroom", LocaleTag("ja") to "トイレ")),
        note = LocalizedText(mapOf(LocaleTag("en") to "The word itself. Everything else on a restroom door is a variation of this.")),
    )
    private val market = ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market")).copy(plateColor = "#b8554a")

    private fun capture(name: String, state: LearnUiState) {
        RuntimeEnvironment.setQualifiers("$language-w390dp-h844dp")
        compose.setContent { SignKoreanTheme { LearnContent(state, {}, {}, reduceMotion = true) } }
        compose.onRoot().captureRoboImage("src/test/screenshots/learn_${name}_$language.png")
    }

    @Test
    fun front() = capture("front", LearnUiState(loading = false, card = market, hasPlaces = true, decisions = 3))

    @Test
    fun back() = capture("back", LearnUiState(loading = false, card = restroom, hasPlaces = false, face = Face.Back, decisions = 4, canUndo = true))

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun params() = listOf(arrayOf<Any>("en"), arrayOf<Any>("ja"))
    }
}
