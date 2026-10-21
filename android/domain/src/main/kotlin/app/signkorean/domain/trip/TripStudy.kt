package app.signkorean.domain.trip

import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.SignCard
import app.signkorean.domain.learning.SessionSource
import app.signkorean.domain.learning.SourceCards
import app.signkorean.domain.place.PlaceListing
import java.time.LocalDate

/**
 * Study this day (FR21). 그날 장소에 연결된 활성 간판 카드를 방문 순서대로 모은다. 같은 카드는 처음 나온 자리에 한 번만
 * (T-13). 세션은 시작할 때 카드 목록을 저장하므로 도중의 일정 변경은 진행 중 세션에 영향이 없다(T-14).
 */
object TripStudy {
    fun cards(pack: ContentPack, day: TripDay): List<SignCard> {
        val places = pack.places.associateBy { it.id }
        return day.stops.mapNotNull { places[it.place] }
            .flatMap { PlaceListing.signsAt(pack, it) }
            .distinctBy { it.id }
    }

    /** 세션 출처. 날짜는 epoch day 로 저장한다. */
    fun source(trip: TripId, date: LocalDate): SessionSource.TripDay = SessionSource.TripDay(trip.value, date.toEpochDay().toInt())

    fun parse(source: SessionSource.TripDay): Pair<TripId, LocalDate> = TripId(source.trip) to LocalDate.ofEpochDay(source.day.toLong())
}

/**
 * 세션 출처의 카드 (결과 화면의 "다시 하기" 등). 일정의 날은 여행 저장소에서 지금 일정을 읽는다 — 다음 세션부터 바뀐 일정이
 * 반영된다(T-14). 나머지 출처는 [SourceCards].
 */
class StudyCards(private val trips: TripStore) {
    suspend fun of(pack: ContentPack, source: SessionSource): List<SignCard> = when (source) {
        is SessionSource.TripDay -> {
            val (id, date) = TripStudy.parse(source)
            trips.get(id)?.days?.firstOrNull { it.date == date }?.let { TripStudy.cards(pack, it) }.orEmpty()
        }
        else -> SourceCards.of(pack, source)
    }
}
