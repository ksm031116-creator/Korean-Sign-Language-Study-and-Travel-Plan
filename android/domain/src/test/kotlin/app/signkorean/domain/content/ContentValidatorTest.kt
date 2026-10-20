package app.signkorean.domain.content

import app.signkorean.testing.ContentFixtures.area
import app.signkorean.testing.ContentFixtures.basic
import app.signkorean.testing.ContentFixtures.card
import app.signkorean.testing.ContentFixtures.pack
import app.signkorean.testing.ContentFixtures.place
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentValidatorTest {

    private val restroom = PictogramId("restroom")
    private val validator = ContentValidator(PictogramCatalog(setOf(restroom)))

    /** 시장 장소 하나와 그 장소에 연결되는 시장 카드 하나. 장소 관련 검사의 기본 배경. */
    private val marketCard = card("t-market-1", "국밥")

    private fun ids(cards: List<SignCard>) = cards.map { it.id.value }

    @Test
    fun `문제가 없으면 이슈 0, 팩은 그대로`() {
        val input = pack(cards = listOf(basic("b01", "화장실"), marketCard), places = listOf(place("p")))
        val result = validator.validate(input)
        assertEquals(emptyList<ContentIssue>(), result.issues)
        assertEquals(input, result.pack)
    }

    @Test
    fun `C-06 같은 한국어 단어가 두 카드에 있으면 오류, 앱에서는 처음 카드만 남긴다 (BUG-01 회귀)`() {
        val result = validator.validate(pack(cards = listOf(basic("b01", "출구"), card("t-subway-3", "출구", CardKind.Basic("transit")), basic("b02", "입구"))))
        assertEquals(listOf(ContentIssue.DuplicateWord("출구", listOf(CardId("b01"), CardId("t-subway-3")))), result.issues)
        assertTrue(result.hasErrors)
        assertEquals(listOf("b01", "b02"), ids(result.pack.cards))
    }

    @Test
    fun `앞뒤 공백이 있는 원문은 오류다 (연결 규칙이 원문을 그대로 비교하므로)`() {
        val result = validator.validate(pack(cards = listOf(basic("b01", "출구 "))))
        assertEquals(listOf(ContentIssue.UntrimmedText(CardId("b01"))), result.issues)
        assertTrue(result.hasErrors)
    }

    @Test
    fun `같은 카드 ID 가 두 번 나오면 오류, 앱에서는 처음 카드만 남긴다`() {
        val result = validator.validate(pack(cards = listOf(basic("b01", "가"), basic("b01", "나"))))
        assertEquals(listOf(ContentIssue.DuplicateId("b01")), result.issues)
        assertTrue(result.hasErrors)
        assertEquals(listOf("가"), result.pack.cards.map { it.koText })
    }

    @Test
    fun `지역과 장소가 같은 ID 를 써도 중복이 아니다`() {
        val result = validator.validate(pack(cards = listOf(marketCard), places = listOf(place("myeongdong", areaId = "myeongdong")), areas = listOf(area("myeongdong"))))
        assertEquals(emptyList<ContentIssue>(), result.issues)
    }

    @Test
    fun `C-09 Example 장소는 이름이 Example 로 시작해야 하고, 아니면 앱에서 뺀다`() {
        val marked = place("ok", name = "Example pharmacy · Seochon", isExample = true)
        val unmarked = place("bad", name = "Seochon Pharmacy", isExample = true)
        val result = validator.validate(pack(cards = listOf(marketCard), places = listOf(marked, unmarked)))
        assertEquals(listOf(ContentIssue.ExampleNotMarked(PlaceId("bad"))), result.issues)
        assertTrue(result.hasErrors)
        assertEquals(listOf("ok"), result.pack.places.map { it.id.value })
    }

    @Test
    fun `C-09 실제 장소인데 이름이 Example 로 시작하면 오류`() {
        val result = validator.validate(pack(cards = listOf(marketCard), places = listOf(place("p", name = "Example market"))))
        assertEquals(listOf(ContentIssue.ExampleNotMarked(PlaceId("p"))), result.issues.filter { it.isError })
    }

    @Test
    fun `C-11 없는 픽토그램 ID 는 경고 후 지우고 카드는 활성으로 남긴다`() {
        val input = pack(cards = listOf(basic("b01").copy(pictogram = PictogramId("unicorn")), basic("b02").copy(pictogram = restroom)))
        val result = validator.validate(input)
        assertEquals(listOf(ContentIssue.UnknownPictogram(CardId("b01"), PictogramId("unicorn"))), result.issues)
        assertFalse(result.hasErrors)
        val b01 = result.pack.cards.single { it.id.value == "b01" }
        assertNull(b01.pictogram)
        assertTrue(b01.active)
        assertEquals(restroom, result.pack.cards.single { it.id.value == "b02" }.pictogram)
    }

    @Test
    fun `비활성 카드는 경고로 보고한다`() {
        val result = validator.validate(pack(cards = listOf(basic("b01").copy(romanization = ""))))
        assertEquals(listOf(ContentIssue.InactiveCard(CardId("b01"), setOf(InactiveReason.MissingRomanization))), result.issues)
        assertFalse(result.hasErrors)
    }

    @Test
    fun `없는 지역을 가리키는 장소는 오류, 앱에서는 뺀다`() {
        val result = validator.validate(pack(cards = listOf(marketCard), places = listOf(place("p", areaId = "nowhere")), areas = listOf(area("myeongdong"))))
        // 장소가 빠지면 시장 카드는 장소 없는 카드가 되어 경고가 하나 더 붙는다.
        assertEquals(listOf(ContentIssue.UnknownArea(PlaceId("p"), AreaId("nowhere"))), result.issues.filter { it.isError })
        assertTrue(result.hasErrors)
        assertEquals(emptyList<Place>(), result.pack.places)
    }

    @Test
    fun `signWords 에 그 유형 카드에 없는 단어가 있으면 오류 (오타로 연결이 끊기는 것을 막는다)`() {
        val result = validator.validate(pack(cards = listOf(marketCard), places = listOf(place("p", signWords = setOf("국밥", "국 밥")))))
        assertEquals(listOf(ContentIssue.UnknownSignWord(PlaceId("p"), "국 밥")), result.issues)
        assertTrue(result.hasErrors)
    }

    @Test
    fun `카드가 하나도 없는 유형의 장소는 오류`() {
        val result = validator.validate(pack(cards = listOf(marketCard), places = listOf(place("p"), place("zoo", typeId = "zoo"))))
        assertEquals(listOf(ContentIssue.PlaceTypeWithoutCards(PlaceId("zoo"), "zoo")), result.issues)
    }

    @Test
    fun `장소가 하나도 없는 장소 유형 카드는 경고`() {
        val result = validator.validate(pack(cards = listOf(marketCard)))
        assertEquals(listOf(ContentIssue.CardWithoutPlaces(CardId("t-market-1"))), result.issues)
        assertFalse(result.hasErrors)
    }
}
