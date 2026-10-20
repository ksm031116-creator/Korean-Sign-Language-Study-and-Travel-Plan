package app.signkorean.domain.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LocalizedTextTest {

    private val en = LocaleTag("en")
    private val ja = LocaleTag("ja")

    @Test
    fun `대체 순서에서 처음 있는 언어를 쓴다`() {
        val text = LocalizedText(mapOf(en to "Exit"))
        assertEquals("Exit", text.resolve(listOf(ja, en)))
    }

    @Test
    fun `빈 문자열과 공백은 없는 값으로 본다`() {
        val text = LocalizedText(mapOf(ja to "  ", en to "Exit"))
        assertEquals("Exit", text.resolve(listOf(ja, en)))
        assertNull(LocalizedText(mapOf(en to "")).get(en))
    }

    @Test
    fun `어느 언어도 없으면 null`() {
        assertNull(LocalizedText.EMPTY.resolve(listOf(en)))
    }

    @Test
    fun `LocaleTag 는 BCP 47 형식만 받는다`() {
        assertEquals("ja", LocaleTag("ja").value)
        assertEquals("zh-Hant", LocaleTag("zh-Hant").value)
        assertThrows<IllegalArgumentException> { LocaleTag("") }
        assertThrows<IllegalArgumentException> { LocaleTag("en_US") }
    }

    @Test
    fun `LocaleTag 는 정규형만 받는다 (EN 과 en 이 다른 키가 되어 카드가 꺼지는 것을 막는다)`() {
        assertEquals("en-US", LocaleTag("en-US").value)
        assertEquals("zh-Hant-TW", LocaleTag("zh-Hant-TW").value)
        assertThrows<IllegalArgumentException> { LocaleTag("EN") }
        assertThrows<IllegalArgumentException> { LocaleTag("en-us") }
        assertThrows<IllegalArgumentException> { LocaleTag("zh-hant") }
    }
}
