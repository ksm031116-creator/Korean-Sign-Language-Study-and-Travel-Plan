package app.signkorean.domain.place

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.PlaceId
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakePlaceSummaryProvider
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 간판의 장소 목록(SC08)과 장소의 간판(SC07) (FR09, PRD §6.1). */
class PlaceListingTest {

    private val gukbap = ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market"))
    private val pharmacy = ContentFixtures.card("t-service-1", "약국", CardKind.PlaceSign("service"))
    private val restroom = ContentFixtures.basic("b01", "화장실")
    private val pack = ContentFixtures.pack(
        cards = listOf(gukbap, pharmacy, restroom),
        places = listOf(
            ContentFixtures.place("gwangjang", areaId = "jongno", typeId = "market"),
            ContentFixtures.place("namdaemun", areaId = "myeongdong", typeId = "market"),
            ContentFixtures.place("mangwon", areaId = "mapo", typeId = "market"),
            ContentFixtures.place("seochon-pharmacy", areaId = "jongno", typeId = "service", signWords = setOf("약국")),
        ),
        areas = listOf(ContentFixtures.area("myeongdong"), ContentFixtures.area("jongno"), ContentFixtures.area("mapo")),
    )

    @Test
    fun `FR09 간판의 장소를 지역 순서대로 묶는다`() {
        val groups = PlaceListing.forCard(pack, gukbap)
        assertEquals(listOf("myeongdong", "jongno", "mapo"), groups.map { it.area.id.value })
        assertEquals(listOf("namdaemun"), groups[0].places.map { it.id.value })
        assertEquals(3, groups.sumOf { it.places.size })
    }

    @Test
    fun `C-03 Basics 카드는 장소가 없다`() {
        assertTrue(PlaceListing.forCard(pack, restroom).isEmpty())
    }

    @Test
    fun `C-02 단어를 지정한 장소는 그 단어 카드에만 나온다`() {
        assertEquals(listOf("seochon-pharmacy"), PlaceListing.forCard(pack, pharmacy).flatMap { it.places }.map { it.id.value })
        assertTrue(PlaceListing.forCard(pack, gukbap).flatMap { it.places }.none { it.id.value == "seochon-pharmacy" })
    }

    @Test
    fun `비활성 지역의 장소는 보이지 않는다`() {
        val closed = pack.copy(areas = pack.areas.map { if (it.id.value == "mapo") it.copy(active = false) else it })
        assertEquals(listOf("myeongdong", "jongno"), PlaceListing.forCard(closed, gukbap).map { it.area.id.value })
    }

    @Test
    fun `장소의 간판은 연결 규칙을 따르고 활성 카드만`() {
        val place = pack.places.first { it.id.value == "gwangjang" }
        assertEquals(listOf(CardId("t-market-1")), PlaceListing.signsAt(pack, place).map { it.id })
        val pharmacyPlace = pack.places.first { it.id.value == "seochon-pharmacy" }
        assertEquals(listOf(CardId("t-service-1")), PlaceListing.signsAt(pack, pharmacyPlace).map { it.id })
    }

    @Test
    fun `가짜 요약 공급자 - 불러오기, 실패, 장소별 값`() = runTest {
        val provider = FakePlaceSummaryProvider(mapOf(PlaceId("gwangjang") to PlaceSummary(4.5, 120, "Busy and friendly.")))
        val loaded = provider.summaries(CardId("t-market-1"), LocaleTag("en"))
        assertEquals(PlaceSummaryResult.Loaded(mapOf(PlaceId("gwangjang") to PlaceSummary(4.5, 120, "Busy and friendly."))), loaded)
        assertEquals(listOf(CardId("t-market-1") to LocaleTag("en")), provider.requests)
        provider.fail = true
        assertEquals(PlaceSummaryResult.Unavailable, provider.summaries(CardId("t-market-1"), LocaleTag("en")))
    }
}
