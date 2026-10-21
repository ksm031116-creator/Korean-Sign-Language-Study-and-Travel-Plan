package app.signkorean.domain.trip

import app.signkorean.domain.common.Clock
import app.signkorean.domain.common.IdGenerator
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.EntitlementSource
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.place.PlaceListing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@JvmInline value class TripId(val value: String)

@JvmInline value class StopId(val value: String)

/** 하루의 출발지 (SC17·19). 사용자가 Apply 한 좌표만 저장한다(PRD §10.4). */
sealed interface Origin {
    data class AtPlace(val place: PlaceId) : Origin
    data class AtCoordinate(val coordinate: Coordinate, val label: String?) : Origin
}

/** 일정의 방문 장소 하나. 같은 장소를 다른 날에 넣을 수 있어 장소 ID 와 별도의 ID 를 가진다. */
data class TripStop(val id: StopId, val place: PlaceId, val stayMinutes: Int = TripRules.DEFAULT_STAY)

/**
 * 하루 일정. [revision] 은 일정이 바뀔 때마다 오른다. 경로 요청에 실어 보내고, 응답의 revision 이 다르면 버린다
 * (PRD §9.2, AT12). [needsRecalculation] 은 경로를 다시 계산해야 함을 뜻한다(오프라인 편집, AT14).
 */
data class TripDay(
    val date: LocalDate,
    val startTime: LocalTime = TripRules.DEFAULT_START,
    val origin: Origin? = null,
    val stops: List<TripStop> = emptyList(),
    val revision: Int = 0,
    val needsRecalculation: Boolean = false,
)

/** 여행. 시작일부터 종료일까지 날짜마다 하루가 하나씩 있다. 시간대는 Asia/Seoul 고정이다(설계서 §6.5). */
data class Trip(
    val id: TripId,
    val title: String,
    val start: LocalDate,
    val end: LocalDate,
    val days: List<TripDay>,
    val createdAt: Instant,
) {
    fun day(date: LocalDate): TripDay = days.firstOrNull { it.date == date } ?: TripDay(date)
    val placeCount: Int get() = days.sumOf { it.stops.size }
}

/** 여행 저장소 (설계서 §6.6). 저장은 여행 하나를 통째로, 한 트랜잭션으로 바꾼다. 구현은 Room. */
interface TripStore {
    /** 시작일 순, 같으면 만든 순. */
    suspend fun all(): List<Trip>
    suspend fun get(id: TripId): Trip?
    suspend fun save(trip: Trip)
    suspend fun delete(id: TripId)
}

/** 거부 이유. 화면은 이 값을 문구로 바꾼다(설계서 원칙 9). */
enum class TripProblem {
    TitleBlank,
    EndBeforeStart,
    TooLong,
    Duplicate,
    DayFull,
    NotADestination,
    InvalidStay,
    StopsOutsideDates,
    DateOutsideTrip,
    NotFound,

    /** 무료 상태. 여행은 지우지 않고 읽기만 된다(E-12). 화면은 SC21 을 연다. */
    PaidOnly,
}

sealed interface TripResult {
    data class Ok(val trip: Trip) : TripResult

    /** [dates] 는 StopsOutsideDates 일 때 범위 밖이 되는 일정 날짜. */
    data class Rejected(val problem: TripProblem, val dates: List<LocalDate> = emptyList()) : TripResult
    data object SaveFailed : TripResult
}

/** 여행 규칙 (FR17~22, 설계서 §6.5). 순수 함수다. */
object TripRules {
    const val MAX_DAYS = 14
    const val MAX_STOPS_PER_DAY = 10
    const val MAX_STAY = 480
    const val DEFAULT_STAY = 30
    val DEFAULT_START: LocalTime = LocalTime.of(9, 0)

    fun dayCount(start: LocalDate, end: LocalDate): Long = ChronoUnit.DAYS.between(start, end) + 1

    fun checkDates(start: LocalDate, end: LocalDate): TripProblem? = when {
        end.isBefore(start) -> TripProblem.EndBeforeStart
        dayCount(start, end) > MAX_DAYS -> TripProblem.TooLong
        else -> null
    }

    fun isValidStay(minutes: Int): Boolean = minutes in 0..MAX_STAY

    /** 체류 시간 입력. 0~480 의 정수만. 앞뒤 공백은 무시한다(UX07). */
    fun parseStay(text: String): Int? {
        val t = text.trim()
        if (t.isEmpty() || !t.all { it.isDigit() }) return null
        return t.toIntOrNull()?.takeIf(::isValidStay)
    }

    fun checkAdd(day: TripDay, place: PlaceId): TripProblem? = when {
        day.stops.any { it.place == place } -> TripProblem.Duplicate
        day.stops.size >= MAX_STOPS_PER_DAY -> TripProblem.DayFull
        else -> null
    }

    /** 일정이 있는 날 중 새 기간 밖이 되는 날. 하나라도 있으면 기간 변경을 막는다(T-11, AT11). */
    fun plannedDaysOutside(trip: Trip, start: LocalDate, end: LocalDate): List<LocalDate> =
        trip.days.filter { it.stops.isNotEmpty() && (it.date.isBefore(start) || it.date.isAfter(end)) }.map { it.date }

    fun datesOf(start: LocalDate, end: LocalDate): List<LocalDate> =
        generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
}

/**
 * 여행 만들기·고치기 (구현 계획 Phase 16 D3). 모든 변경은 규칙 검사 → 저장 → 성공해야 결과를 돌려준다(설계서 원칙 8).
 * 하루가 바뀌면 그날의 revision 을 올리고 경로를 다시 계산해야 한다고 표시한다.
 * 학습 상태·북마크 저장소를 받지 않는다 — 여행을 지워도 그것들은 그대로다(FR22, T-17).
 */
class TripEditor(
    private val store: TripStore,
    private val ids: IdGenerator,
    private val clock: Clock,
    private val content: ContentRepository,
    private val entitlement: EntitlementSource,
) {
    private val mutex = Mutex()

    suspend fun create(title: String, start: LocalDate, end: LocalDate): TripResult = mutex.withLock {
        if (!paid()) return@withLock TripResult.Rejected(TripProblem.PaidOnly)
        val name = title.trim()
        if (name.isEmpty()) return@withLock TripResult.Rejected(TripProblem.TitleBlank)
        TripRules.checkDates(start, end)?.let { return@withLock TripResult.Rejected(it) }
        val trip = Trip(TripId(ids.next()), name, start, end, TripRules.datesOf(start, end).map { TripDay(it) }, clock.now())
        persist(trip)
    }

    suspend fun update(id: TripId, title: String, start: LocalDate, end: LocalDate): TripResult = edit(id) { trip ->
        val name = title.trim()
        if (name.isEmpty()) return@edit TripResult.Rejected(TripProblem.TitleBlank)
        TripRules.checkDates(start, end)?.let { return@edit TripResult.Rejected(it) }
        val outside = TripRules.plannedDaysOutside(trip, start, end)
        if (outside.isNotEmpty()) return@edit TripResult.Rejected(TripProblem.StopsOutsideDates, outside)
        val days = TripRules.datesOf(start, end).map { date -> trip.days.firstOrNull { it.date == date } ?: TripDay(date) }
        TripResult.Ok(trip.copy(title = name, start = start, end = end, days = days))
    }

    suspend fun delete(id: TripId): Boolean = mutex.withLock {
        try {
            store.delete(id)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** [rawPlace] 는 화면이 넘긴 목적지 ID. 등록 장소(활성 지역)만 목적지다. 카드·지역 ID 는 거부한다(T-12). */
    suspend fun addStop(id: TripId, date: LocalDate, rawPlace: String): TripResult = editDay(id, date) { _, day ->
        val pack = content.load()
        val place = PlaceListing.visiblePlaces(pack).firstOrNull { it.id.value == rawPlace }?.id
            ?: return@editDay DayEdit.Reject(TripProblem.NotADestination)
        TripRules.checkAdd(day, place)?.let { return@editDay DayEdit.Reject(it) }
        DayEdit.Change(day.copy(stops = day.stops + TripStop(StopId(ids.next()), place)))
    }

    suspend fun moveStop(id: TripId, from: LocalDate, stop: StopId, to: LocalDate): TripResult = edit(id) { trip ->
        if (from == to) return@edit TripResult.Ok(trip)
        if (!inTrip(trip, from) || !inTrip(trip, to)) return@edit TripResult.Rejected(TripProblem.DateOutsideTrip)
        val source = trip.day(from)
        val moving = source.stops.firstOrNull { it.id == stop } ?: return@edit TripResult.Rejected(TripProblem.NotFound)
        val target = trip.day(to)
        TripRules.checkAdd(target, moving.place)?.let { return@edit TripResult.Rejected(it) }
        TripResult.Ok(
            trip.withDay(changed(source.copy(stops = source.stops - moving)))
                .withDay(changed(target.copy(stops = target.stops + moving))),
        )
    }

    suspend fun reorder(id: TripId, date: LocalDate, stop: StopId, toIndex: Int): TripResult = editDay(id, date) { _, day ->
        val moving = day.stops.firstOrNull { it.id == stop } ?: return@editDay DayEdit.Reject(TripProblem.NotFound)
        val rest = day.stops - moving
        val at = toIndex.coerceIn(0, rest.size)
        DayEdit.Change(day.copy(stops = rest.take(at) + moving + rest.drop(at)))
    }

    suspend fun setStay(id: TripId, date: LocalDate, stop: StopId, minutes: Int): TripResult = editDay(id, date) { _, day ->
        if (!TripRules.isValidStay(minutes)) return@editDay DayEdit.Reject(TripProblem.InvalidStay)
        if (day.stops.none { it.id == stop }) return@editDay DayEdit.Reject(TripProblem.NotFound)
        DayEdit.Change(day.copy(stops = day.stops.map { if (it.id == stop) it.copy(stayMinutes = minutes) else it }))
    }

    suspend fun removeStop(id: TripId, date: LocalDate, stop: StopId): TripResult = editDay(id, date) { _, day ->
        if (day.stops.none { it.id == stop }) return@editDay DayEdit.Reject(TripProblem.NotFound)
        DayEdit.Change(day.copy(stops = day.stops.filterNot { it.id == stop }))
    }

    /** 시작 시각은 분 단위로 저장한다(TRP-05). 출발지 장소도 등록 장소만이다(TRP-L3). */
    suspend fun setStart(id: TripId, date: LocalDate, time: LocalTime, origin: Origin?): TripResult = editDay(id, date) { _, day ->
        if (origin is Origin.AtPlace && PlaceListing.visiblePlaces(content.load()).none { it.id == origin.place }) {
            return@editDay DayEdit.Reject(TripProblem.NotADestination)
        }
        DayEdit.Change(day.copy(startTime = time.truncatedTo(ChronoUnit.MINUTES), origin = origin))
    }

    private sealed interface DayEdit {
        data class Change(val day: TripDay) : DayEdit
        data class Reject(val problem: TripProblem) : DayEdit
    }

    private suspend fun editDay(id: TripId, date: LocalDate, block: suspend (Trip, TripDay) -> DayEdit): TripResult = edit(id) { trip ->
        if (!inTrip(trip, date)) return@edit TripResult.Rejected(TripProblem.DateOutsideTrip)
        val day = trip.day(date)
        when (val e = block(trip, day)) {
            is DayEdit.Reject -> TripResult.Rejected(e.problem)
            // 바뀐 게 없으면 revision 도 저장도 그대로(진행 중 경로 응답을 버리지 않게, TRP-L1).
            is DayEdit.Change -> if (e.day == day) TripResult.Ok(trip) else TripResult.Ok(trip.withDay(changed(e.day)))
        }
    }

    /** 규칙을 통과한 새 여행만 저장한다. 저장에 실패하면 저장소의 여행은 그대로다. */
    private suspend fun edit(id: TripId, block: suspend (Trip) -> TripResult): TripResult = mutex.withLock {
        if (!paid()) return@withLock TripResult.Rejected(TripProblem.PaidOnly)
        val trip = try {
            store.get(id)?.withAllDays()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withLock TripResult.SaveFailed
        } ?: return@withLock TripResult.Rejected(TripProblem.NotFound)
        // 규칙 검사 중 실패(콘텐츠 읽기 등)도 예외가 아니라 결과로 끝낸다(설계서 원칙 8, TRP-04).
        val result = try {
            block(trip)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return@withLock TripResult.SaveFailed
        }
        when (val r = result) {
            is TripResult.Ok -> if (r.trip == trip) r else persist(r.trip)
            else -> r
        }
    }

    private suspend fun persist(trip: Trip): TripResult = try {
        store.save(trip)
        TripResult.Ok(trip)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        TripResult.SaveFailed
    }

    private fun inTrip(trip: Trip, date: LocalDate) = !date.isBefore(trip.start) && !date.isAfter(trip.end)

    private fun changed(day: TripDay) = day.copy(revision = day.revision + 1, needsRecalculation = true)

    /** 그날이 없으면 넣는다(바꾸기만 하면 없는 날로 옮긴 장소가 사라진다, TRP-01). 날짜 순서를 지킨다. */
    private fun Trip.withDay(day: TripDay) = copy(days = (days.filterNot { it.date == day.date } + day).sortedBy { it.date })

    /** 저장된 여행에 빠진 날짜가 있으면 빈 하루로 채운다. */
    private fun Trip.withAllDays() = copy(days = TripRules.datesOf(start, end).map { d -> days.firstOrNull { it.date == d } ?: TripDay(d) })

    private suspend fun paid(): Boolean = try {
        entitlement.current() == Entitlement.Paid
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        false
    }
}
