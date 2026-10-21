package app.signkorean.domain.trip

import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.learning.SessionSource
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeTripStore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime

/** Study this day (FR21, 설계서 §6.3). T-13·15 */
class TripStudyTest {

    private val market1 = ContentFixtures.card("m1", "국밥", CardKind.PlaceSign("market"))
    private val market2 = ContentFixtures.card("m2", "떡", CardKind.PlaceSign("market"))
    private val cafe1 = ContentFixtures.card("c1", "커피", CardKind.PlaceSign("cafe"))
    private val draft = ContentFixtures.card("c2", "라떼", CardKind.PlaceSign("cafe"), reviewed = false)
    private val pack = ContentFixtures.pack(
        cards = listOf(market1, market2, cafe1, draft),
        places = listOf(
            ContentFixtures.place("cafe", typeId = "cafe"),
            ContentFixtures.place("marketA", typeId = "market"),
            ContentFixtures.place("marketB", typeId = "market"),
            ContentFixtures.place("palace", typeId = "palace"),
        ),
    )
    private val date = LocalDate.of(2026, 11, 3)

    private fun day(vararg places: String) =
        TripDay(date, LocalTime.of(9, 0), null, places.mapIndexed { i, p -> TripStop(StopId("s$i"), PlaceId(p)) }, revision = 1)

    @Test
    fun `T-13 두 장소가 같은 카드면 한 번만, 방문 순서대로`() {
        val cards = TripStudy.cards(pack, day("cafe", "marketA", "marketB"))
        assertEquals(listOf("c1", "m1", "m2"), cards.map { it.id.value })
    }

    @Test
    fun `순서를 바꾸면 카드 순서도 바뀐다`() {
        assertEquals(listOf("m1", "m2", "c1"), TripStudy.cards(pack, day("marketB", "cafe")).map { it.id.value })
    }

    @Test
    fun `검수 전 카드와 없는 장소는 빠진다`() {
        val cards = TripStudy.cards(pack, day("gone", "cafe"))
        assertEquals(listOf("c1"), cards.map { it.id.value })
    }

    @Test
    fun `T-15 그날 장소에 카드가 없으면 빈 목록`() {
        assertTrue(TripStudy.cards(pack, day("palace")).isEmpty())
        assertTrue(TripStudy.cards(pack, day()).isEmpty())
    }

    @Test
    fun `세션 출처는 여행과 날짜로 정해지고 다시 날짜로 돌아온다`() {
        val source = TripStudy.source(TripId("t1"), date)
        assertEquals(SessionSource.TripDay("t1", date.toEpochDay().toInt()), source)
        assertEquals(TripId("t1") to date, TripStudy.parse(source))
    }

    @Test
    fun `StudyCards 는 일정의 날을 저장소에서 읽는다 - 다음 세션부터 바뀐 일정이 반영된다(T-14)`() = runTest {
        val store = FakeTripStore()
        val trip = Trip(TripId("t1"), "Autumn", date, date, listOf(day("cafe")), createdAt = java.time.Instant.EPOCH)
        store.save(trip)
        val study = StudyCards(store)
        val source = TripStudy.source(trip.id, date)
        assertEquals(listOf("c1"), study.of(pack, source).map { it.id.value })
        store.save(trip.copy(days = listOf(day("marketA"))))
        assertEquals(listOf("m1", "m2"), study.of(pack, source).map { it.id.value })
        assertTrue(study.of(pack, TripStudy.source(TripId("gone"), date)).isEmpty())
        assertEquals(1, study.of(ContentFixtures.pack(cards = listOf(ContentFixtures.basic("b1"))), SessionSource.Basics).size)
    }
}
