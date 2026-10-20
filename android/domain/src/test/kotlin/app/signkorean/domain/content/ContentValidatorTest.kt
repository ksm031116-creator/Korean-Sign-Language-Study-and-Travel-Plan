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
    private val validator = ContentValidator(knownPictograms = setOf(restroom))

    @Test
    fun `문제가 없으면 이슈 0, 팩은 그대로`() {
        val input = pack(cards = listOf(basic("b01", "화장실")), places = listOf(place("p")))
        val result = validator.validate(input)
        assertEquals(emptyList<ContentIssue>(), result.issues)
        assertEquals(input, result.pack)
    }

    @Test
    fun `C-06 같은 한국어 단어가 두 카드에 있으면 오류 (BUG-01 회귀)`() {
        val result = validator.validate(pack(cards = listOf(basic("b01", "출구"), card("t-subway-3", "출구"), basic("b02", "입구"))))
        assertEquals(listOf(ContentIssue.DuplicateWord("출구", listOf(CardId("b01"), CardId("t-subway-3")))), result.issues)
        assertTrue(result.hasErrors)
    }

    @Test
    fun `단어 비교는 앞뒤 공백을 무시한다`() {
        val result = validator.validate(pack(cards = listOf(basic("b01", "출구"), basic("b02", " 출구 "))))
        assertEquals(1, result.issues.filterIsInstance<ContentIssue.DuplicateWord>().size)
    }

    @Test
    fun `같은 카드 ID 가 두 번 나오면 오류`() {
        val result = validator.validate(pack(cards = listOf(basic("b01", "가"), basic("b01", "나"))))
        assertEquals(listOf(ContentIssue.DuplicateId("b01")), result.issues)
        assertTrue(result.hasErrors)
    }

    @Test
    fun `C-09 Example 장소는 이름이 Example 로 시작해야 한다`() {
        val marked = place("ok", name = "Example pharmacy · Seochon", isExample = true)
        val unmarked = place("bad", name = "Seochon Pharmacy", isExample = true)
        val result = validator.validate(pack(places = listOf(marked, unmarked)))
        assertEquals(listOf(ContentIssue.ExampleNotMarked(PlaceId("bad"))), result.issues)
        assertTrue(result.hasErrors)
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
    fun `없는 지역을 가리키는 장소는 오류`() {
        val result = validator.validate(pack(places = listOf(place("p", areaId = "nowhere")), areas = listOf(area("myeongdong"))))
        assertEquals(listOf(ContentIssue.UnknownArea(PlaceId("p"), AreaId("nowhere"))), result.issues)
        assertTrue(result.hasErrors)
    }
}
