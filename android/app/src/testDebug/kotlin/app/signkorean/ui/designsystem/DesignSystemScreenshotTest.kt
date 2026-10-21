package app.signkorean.ui.designsystem

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * 부품 스크린샷 (구현 계획 Phase 4). en·ja × 폭 360/390/430dp.
 * 기준 이미지를 바꾸려면 화면을 확인한 뒤 ./gradlew recordRoborazziDebug, 비교는 verifyRoborazziDebug.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignSystemScreenshotTest(private val language: String, private val widthDp: Int) {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun catalog() {
        RuntimeEnvironment.setQualifiers("$language-w${widthDp}dp-h1600dp")
        compose.setContent { SignKoreanTheme { DesignSystemCatalog() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/designsystem_catalog_${language}_$widthDp.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}dp")
        fun params() = listOf("en", "ja").flatMap { lang -> listOf(360, 390, 430).map { arrayOf<Any>(lang, it) } }
    }
}
