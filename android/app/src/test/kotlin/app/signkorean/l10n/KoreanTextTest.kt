package app.signkorean.l10n

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList
import app.signkorean.ui.text.KoreanText
import app.signkorean.ui.text.korean
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 앱 언어가 일본어여도 한국어 간판은 한국어 글꼴로 그린다 (설계서 §8.7). L10N-06 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ja")
class KoreanTextTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `L10N-06 korean() 은 다른 속성을 지키고 localeList 만 ko-KR 로 바꾼다`() {
        val base = TextStyle(localeList = LocaleList("ja-JP"))
        val style = base.korean()
        assertEquals(LocaleList("ko-KR"), style.localeList)
        assertEquals(base.copy(localeList = LocaleList("ko-KR")), style)
    }

    @Test
    fun `KoreanText 는 일본어 앱에서도 원문을 그대로 그린다`() {
        compose.setContent { MaterialTheme { KoreanText("출구") } }
        compose.onNodeWithText("출구").assertExists()
    }
}
