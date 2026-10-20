package app.signkorean.domain.content

import app.signkorean.testing.ContentFixtures.basic
import app.signkorean.testing.ContentFixtures.card
import app.signkorean.testing.ContentFixtures.place
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 카드 → 장소 연결 규칙 (PRD §10.2, 설계서 §6.1). C-01~03 */
class CardPlaceLinkerTest {

    private val market1 = card("t-market-1", "국밥", CardKind.PlaceSign("market"))
    private val market2 = card("t-market-2", "떡볶이", CardKind.PlaceSign("market"))
    private val pharmacy = card("t-services-1", "약국", CardKind.PlaceSign("services"))
    private val bank = card("t-services-2", "은행", CardKind.PlaceSign("services"))

    @Test
    fun `C-01 signWords 없는 시장은 시장 유형 카드 전부와 연결된다`() {
        val gwangjang = place("gwangjang", typeId = "market", signWords = null)
        assertEquals(listOf(market1, market2), CardPlaceLinker.cardsFor(gwangjang, listOf(market1, market2, pharmacy, bank)))
    }

    @Test
    fun `C-02 signWords 가 약국뿐인 서비스 장소는 약국 카드에만 연결된다 (BUG-03 회귀)`() {
        val seochonPh = place("seochon-ph", typeId = "services", signWords = setOf("약국"))
        assertTrue(CardPlaceLinker.links(pharmacy, seochonPh))
        assertFalse(CardPlaceLinker.links(bank, seochonPh))
        assertEquals(listOf(pharmacy), CardPlaceLinker.cardsFor(seochonPh, listOf(market1, pharmacy, bank)))
    }

    @Test
    fun `C-03 Basics 카드는 연결 장소가 없다`() {
        val restroom = basic("b01", "화장실")
        val everywhere = listOf(place("a", typeId = "restroom"), place("b", typeId = "market"), place("c", typeId = "services", signWords = setOf("화장실")))
        assertEquals(emptyList<Place>(), CardPlaceLinker.placesFor(restroom, everywhere))
        assertFalse(CardPlaceLinker.hasPlaces(restroom, everywhere))
    }

    @Test
    fun `유형이 다르면 signWords 에 단어가 있어도 연결하지 않는다`() {
        val museum = place("m", typeId = "museum", signWords = setOf("국밥"))
        assertFalse(CardPlaceLinker.links(market1, museum))
    }

    @Test
    fun `빈 signWords 는 어떤 카드와도 연결하지 않는다`() {
        val emptyWords = place("e", typeId = "market", signWords = emptySet())
        assertFalse(CardPlaceLinker.links(market1, emptyWords))
    }

    @Test
    fun `placesFor 는 입력 순서를 지킨다`() {
        val places = listOf(place("z"), place("a"), place("m", typeId = "museum"))
        assertEquals(listOf("z", "a"), CardPlaceLinker.placesFor(market1, places).map { it.id.value })
    }
}
