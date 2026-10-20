package app.signkorean.domain.locale

import app.signkorean.domain.common.LocaleTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** 언어 결정과 대체 (설계서 §8.2). L10N-01·03·04·08·13 */
class LanguagePolicyTest {

    private val en = LocaleTag("en")
    private val ja = LocaleTag("ja")
    private val supported = listOf(en, ja)

    @Test
    fun `L10N-01 기기 언어가 일본어면 ja`() {
        assertEquals(ja, LanguagePolicy.resolve(listOf("ja"), supported))
    }

    @Test
    fun `L10N-03 지원하지 않는 기기 언어는 en`() {
        assertEquals(en, LanguagePolicy.resolve(listOf("fr-FR"), supported))
        assertEquals(en, LanguagePolicy.resolve(emptyList(), supported))
    }

    @Test
    fun `L10N-13 지역 태그는 언어 부분으로 맞춘다`() {
        assertEquals(ja, LanguagePolicy.resolve(listOf("ja-JP"), supported))
        assertEquals(en, LanguagePolicy.resolve(listOf("en-GB"), supported))
        assertEquals(en, LanguagePolicy.resolve(listOf("en-US"), supported))
    }

    @Test
    fun `기기 언어 목록에서 처음으로 지원되는 언어를 고른다`() {
        assertEquals(ja, LanguagePolicy.resolve(listOf("fr-FR", "ja-JP", "en-US"), supported))
    }

    @Test
    fun `태그 정규화는 대소문자·밑줄을 견딘다`() {
        assertEquals(ja, LanguagePolicy.normalize("JA_jp"))
        assertEquals(en, LanguagePolicy.normalize("en-Latn-US"))
        assertNull(LanguagePolicy.normalize(""))
        assertNull(LanguagePolicy.normalize("und"))
    }

    @Test
    fun `L10N-04 콘텐츠 대체 순서는 사용자 언어 다음 en`() {
        assertEquals(listOf(ja, en), LanguagePolicy.fallbackChain(ja))
        assertEquals(listOf(en), LanguagePolicy.fallbackChain(en))
    }

    @Test
    fun `L10N-08 지원 목록은 데이터에서 오므로 새 언어가 코드 변경 없이 선택된다`() {
        val xx = LocaleTag("xx")
        assertEquals(xx, LanguagePolicy.resolve(listOf("xx-YY"), supported + xx))
    }

    @Test
    fun `지원 목록에 en 이 없어도 기본값은 en`() {
        assertEquals(en, LanguagePolicy.resolve(listOf("fr"), listOf(ja)))
    }
}
