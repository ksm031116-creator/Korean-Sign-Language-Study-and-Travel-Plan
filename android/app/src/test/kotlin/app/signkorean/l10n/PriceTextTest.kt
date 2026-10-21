package app.signkorean.l10n

import org.junit.Assert.assertEquals
import org.junit.Test

/** L10N-11: 가격은 스토어의 현지 가격 문자열만 쓴다. 문구에 금액·통화 기호를 박아 두지 않는다 (PRD §14.1, 설계서 §6.4). */
class PriceTextTest {

    private val money = Regex("""(US\$|\$\s?\d|¥\s?\d|\d\s?円|€\s?\d|₩\s?\d|0\.99)""")

    @Test
    fun `L10N-11 어느 언어 문구에도 금액이 없다`() {
        val found = ResourceStrings.all().flatMap { lang ->
            (lang.strings + lang.plurals.flatMap { (k, v) -> v.map { (q, s) -> "$k[$q]" to s } })
                .filter { (_, text) -> money.containsMatchIn(text) }
                .map { (key, text) -> "${lang.qualifier}/$key: $text" }
        }
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun `검사식은 금액을 실제로 잡는다`() {
        listOf("Unlock for US$0.99", "¥150 で解放", "150円", "$1").forEach { assert(money.containsMatchIn(it)) { it } }
        listOf("Unlock for %1\$s", "5 of 5").forEach { assert(!money.containsMatchIn(it)) { it } }
    }
}
