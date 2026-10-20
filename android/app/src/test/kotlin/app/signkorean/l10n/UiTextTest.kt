package app.signkorean.l10n

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import app.signkorean.R
import app.signkorean.ui.text.UiText
import app.signkorean.ui.text.asString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UiTextTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `Plural 의 수를 바꾸면 문구 속 숫자도 바뀐다 (copy 함정 회귀)`() {
        val three = UiText.Plural(R.plurals.places_count, 1).copy(count = 3)
        compose.setContent { Text(three.asString()) }
        compose.onNodeWithText("3 places").assertExists()
    }

    @Test
    fun `Res 는 인자를 자리표시자에 넣는다`() {
        compose.setContent { Text(UiText.Res(R.string.format_duration_hm, listOf(1, 20)).asString()) }
        compose.onNodeWithText("1h 20min").assertExists()
    }
}
