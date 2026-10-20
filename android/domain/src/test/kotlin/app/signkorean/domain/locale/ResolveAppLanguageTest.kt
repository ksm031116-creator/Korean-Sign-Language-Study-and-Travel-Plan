package app.signkorean.domain.locale

import app.signkorean.domain.common.LocaleTag
import app.signkorean.testing.FakeCurrentLocale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ResolveAppLanguageTest {

    private val en = LocaleTag("en")
    private val ja = LocaleTag("ja")

    @Test
    fun `사용자가 고른 언어가 기기 언어보다 우선한다`() {
        val locale = FakeCurrentLocale(device = listOf("ja-JP"), selected = en)
        assertEquals(en, ResolveAppLanguage(locale).invoke(listOf(en, ja)))
    }

    @Test
    fun `고른 언어가 없으면 기기 언어로 정한다`() {
        val locale = FakeCurrentLocale(device = listOf("ja-JP"))
        assertEquals(ja, ResolveAppLanguage(locale).invoke(listOf(en, ja)))
    }

    @Test
    fun `고른 언어가 더 이상 지원되지 않으면 기기 언어로 돌아간다`() {
        val locale = FakeCurrentLocale(device = listOf("en-GB"), selected = LocaleTag("xx"))
        assertEquals(en, ResolveAppLanguage(locale).invoke(listOf(en, ja)))
    }

    @Test
    fun `고른 언어의 지역 태그도 정규화한다`() {
        val locale = FakeCurrentLocale(device = listOf("en-US"), selected = LocaleTag("ja-JP"))
        assertEquals(ja, ResolveAppLanguage(locale).invoke(listOf(en, ja)))
    }
}
