package app.signkorean.ui.text

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * 화면 상태에 담는 문구. ViewModel 은 문구 대신 리소스 ID 와 인자를 담고, 문구 조립은 Compose 가 지금 언어로 한다
 * (설계서 §5.3). 그래야 언어를 바꿔도 상태를 다시 만들 필요가 없다.
 */
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** 문구의 첫 자리표시자는 항상 [count] 다. [formatArgs] 는 그 뒤 자리표시자. count 를 바꿔도 숫자가 따라 바뀐다. */
    data class Plural(@PluralsRes val id: Int, val count: Int, val formatArgs: List<Any> = emptyList()) : UiText
}

@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *(listOf<Any>(count) + formatArgs).toTypedArray())
}
