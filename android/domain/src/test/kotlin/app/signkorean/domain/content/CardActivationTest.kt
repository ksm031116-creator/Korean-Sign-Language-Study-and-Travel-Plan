package app.signkorean.domain.content

import app.signkorean.domain.common.LocalizedText
import app.signkorean.testing.ContentFixtures.EN
import app.signkorean.testing.ContentFixtures.JA
import app.signkorean.testing.ContentFixtures.card
import app.signkorean.testing.ContentFixtures.en
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 카드 활성화는 언어와 무관하게 기준 언어 en 으로 판단한다 (설계서 §6.1). C-04·05·05b */
class CardActivationTest {

    @Test
    fun `모든 조건을 갖춘 카드는 활성`() {
        assertTrue(card("b01").active)
        assertEquals(emptySet<InactiveReason>(), CardActivation.reasons(card("b01")))
    }

    @Test
    fun `C-04 로마자가 없으면 비활성`() {
        val c = card("b01", romanization = " ")
        assertFalse(c.active)
        assertEquals(setOf(InactiveReason.MissingRomanization), CardActivation.reasons(c))
    }

    @Test
    fun `C-05 ja 뜻이 없어도 en 이 있으면 활성`() {
        val c = card("b01", meaning = LocalizedText(mapOf(EN to "Exit")))
        assertTrue(c.active)
    }

    @Test
    fun `C-05b en 뜻이 없으면 ja 가 있어도 비활성`() {
        val c = card("b01", meaning = LocalizedText(mapOf(JA to "出口")))
        assertFalse(c.active)
        assertEquals(setOf(InactiveReason.MissingBaseMeaning), CardActivation.reasons(c))
    }

    @Test
    fun `en 설명·한국어 원문·검수 중 하나라도 없으면 비활성`() {
        assertEquals(setOf(InactiveReason.MissingBaseNote), CardActivation.reasons(card("b01", note = LocalizedText.EMPTY)))
        assertEquals(setOf(InactiveReason.MissingKoreanText), CardActivation.reasons(card("b01", koText = "")))
        assertEquals(setOf(InactiveReason.NotReviewed), CardActivation.reasons(card("b01", reviewed = false)))
    }

    @Test
    fun `여러 이유가 겹치면 모두 보고한다`() {
        val c = card("b01", romanization = "", meaning = en(""), reviewed = false)
        assertEquals(
            setOf(InactiveReason.MissingRomanization, InactiveReason.MissingBaseMeaning, InactiveReason.NotReviewed),
            CardActivation.reasons(c),
        )
    }
}
