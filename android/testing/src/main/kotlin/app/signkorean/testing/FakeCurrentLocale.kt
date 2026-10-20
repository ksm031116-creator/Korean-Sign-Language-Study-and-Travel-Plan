package app.signkorean.testing

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.locale.CurrentLocale

/** 기기 언어와 앱에서 고른 언어를 테스트가 정한다. */
class FakeCurrentLocale(
    private val device: List<String> = listOf("en-US"),
    @Volatile private var selected: LocaleTag? = null,
) : CurrentLocale {

    override fun selected(): LocaleTag? = selected

    override fun select(tag: LocaleTag?) {
        selected = tag
    }

    override fun deviceTags(): List<String> = device
}
