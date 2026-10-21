package app.signkorean.testing

import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.trip.Origin
import app.signkorean.domain.trip.StopId
import app.signkorean.domain.trip.Trip
import app.signkorean.domain.trip.TripDay
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.trip.TripStop
import app.signkorean.domain.trip.TripStore
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/** 여행 저장소 가짜. 저장은 여행 하나를 통째로 바꾼다. [failNextSave] 로 저장 실패를 주입한다. */
class FakeTripStore : TripStore {
    private val trips = linkedMapOf<TripId, Trip>()
    @Volatile var failNextSave = false
    @Volatile var failNextGet = false

    override suspend fun all(): List<Trip> = trips.values.sortedWith(compareBy<Trip> { it.start }.thenBy { it.createdAt })
    override suspend fun get(id: TripId): Trip? {
        if (failNextGet) {
            failNextGet = false
            throw FakeStorageException("trip read")
        }
        return trips[id]
    }

    override suspend fun save(trip: Trip) {
        if (failNextSave) {
            failNextSave = false
            throw FakeStorageException("trip")
        }
        trips[trip.id] = trip
    }

    override suspend fun delete(id: TripId) {
        trips.remove(id)
    }
}

/**
 * 여행 저장소 포트 계약 (구현 계획 Phase 16 D3). 가짜와 Room 이 같은 검사를 통과해야 한다.
 * 테스트 프레임워크에 묶이지 않도록 실패는 AssertionError 로 던진다.
 */
class TripStoreContract(private val store: TripStore) {

    private fun expect(condition: Boolean, message: () -> String) {
        if (!condition) throw AssertionError(message())
    }

    private val d1 = LocalDate.of(2026, 11, 3)
    private val created = Instant.parse("2026-10-21T09:00:00Z")

    private fun sample(id: String, start: LocalDate = d1) = Trip(
        id = TripId(id),
        title = "Seoul $id",
        start = start,
        end = start.plusDays(1),
        days = listOf(
            TripDay(
                date = start,
                startTime = LocalTime.of(9, 30),
                origin = Origin.AtCoordinate(Coordinate(37.5547, 126.9707), "Seoul Station"),
                stops = listOf(TripStop(StopId("$id-s1"), PlaceId("gwangjang"), 45), TripStop(StopId("$id-s2"), PlaceId("tongin"), 0)),
                revision = 4,
                needsRecalculation = true,
            ),
            TripDay(date = start.plusDays(1), origin = Origin.AtPlace(PlaceId("gwangjang"))),
        ),
        createdAt = created,
    )

    suspend fun verifyAll() {
        roundTripKeepsEveryField()
        saveReplacesWholeTrip()
        listIsOrderedByStart()
        deleteRemovesOnlyThatTrip()
    }

    suspend fun roundTripKeepsEveryField() {
        val t = sample("a")
        store.save(t)
        val back = store.get(t.id)
        expect(back == t) { "저장한 여행을 그대로 읽어야 한다: $t\n읽은 값: $back" }
        store.delete(t.id)
    }

    suspend fun saveReplacesWholeTrip() {
        val t = sample("b")
        store.save(t)
        val changed = t.copy(title = "Changed", days = t.days.map { it.copy(stops = it.stops.reversed().drop(1)) })
        store.save(changed)
        expect(store.get(t.id) == changed) { "다시 저장하면 이전 정류장이 남지 않고 통째로 바뀌어야 한다" }
        store.delete(t.id)
    }

    suspend fun listIsOrderedByStart() {
        store.save(sample("late", d1.plusDays(10)))
        store.save(sample("early", d1))
        val order = store.all().map { it.id.value }
        expect(order == listOf("early", "late")) { "목록은 시작일 순서여야 한다: $order" }
        store.delete(TripId("late"))
        store.delete(TripId("early"))
    }

    suspend fun deleteRemovesOnlyThatTrip() {
        store.save(sample("x"))
        store.save(sample("y"))
        store.delete(TripId("x"))
        expect(store.get(TripId("x")) == null) { "지운 여행이 남았다" }
        expect(store.get(TripId("y")) != null) { "다른 여행까지 지웠다" }
        store.delete(TripId("y"))
        expect(store.all().isEmpty()) { "모두 지운 뒤 비어 있어야 한다" }
    }
}
