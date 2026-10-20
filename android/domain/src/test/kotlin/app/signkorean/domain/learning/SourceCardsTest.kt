package app.signkorean.domain.learning

import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.CardKind
import app.signkorean.testing.ContentFixtures.basic
import app.signkorean.testing.ContentFixtures.card
import app.signkorean.testing.ContentFixtures.pack
import app.signkorean.testing.ContentFixtures.place
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 세션 출처 → 출제 카드. 연결 규칙은 CardPlaceLinker 만 쓴다. */
class SourceCardsTest {

    private val gukbap = card("t-market-1", "국밥", CardKind.PlaceSign("market"))
    private val ticket = card("t-palace-1", "매표소", CardKind.PlaceSign("palace"))
    private val pharmacy = card("t-services-1", "약국", CardKind.PlaceSign("services"))
    private val bank = card("t-services-2", "은행", CardKind.PlaceSign("services"))
    private val restroom = basic("b01", "화장실")
    private val p = pack(
        cards = listOf(gukbap, ticket, pharmacy, bank, restroom),
        places = listOf(
            place("gwangjang", typeId = "market", areaId = "myeongdong"),
            place("ph", typeId = "services", signWords = setOf("약국"), areaId = "myeongdong"),
            place("palace", typeId = "palace", areaId = "gyeongbokgung"),
        ),
    )

    @Test
    fun `Basics 는 기초 카드만`() = assertEquals(listOf(restroom), SourceCards.of(p, SessionSource.Basics))

    @Test
    fun `지역은 그 지역 장소에 연결된 카드만, 카드 순서대로`() =
        assertEquals(listOf(gukbap, pharmacy), SourceCards.of(p, SessionSource.Area(AreaId("myeongdong"))))

    @Test
    fun `장소는 그 장소에 연결된 카드만`() =
        assertEquals(listOf(pharmacy), SourceCards.of(p, SessionSource.Place("ph")))

    @Test
    fun `없는 지역·장소는 빈 목록`() {
        assertEquals(emptyList<Any>(), SourceCards.of(p, SessionSource.Area(AreaId("nowhere"))))
        assertEquals(emptyList<Any>(), SourceCards.of(p, SessionSource.Place("nowhere")))
    }
}
