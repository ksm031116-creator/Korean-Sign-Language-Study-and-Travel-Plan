package app.signkorean.ui.text

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList

private val KOREAN = LocaleList("ko-KR")

/**
 * 한국어 텍스트 스타일. 앱 언어가 일본어여도 한글·한자를 한국어 글꼴로 그리게 한다(한중일 통합 한자 글리프 차이).
 * 설계서 §8.7, L10N-06.
 */
fun TextStyle.korean(): TextStyle = copy(localeList = KOREAN)

/**
 * 학습 대상인 한국어 원문(간판 단어, 범례의 한국어 부분). 번역하지 않는 콘텐츠라 리소스가 아니라 값으로 받는다.
 */
@Composable
fun KoreanText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current) {
    Text(text = text, modifier = modifier, style = style.korean())
}
