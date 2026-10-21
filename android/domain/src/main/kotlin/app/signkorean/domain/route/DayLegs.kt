package app.signkorean.domain.route

import app.signkorean.domain.trip.TripDay
import app.signkorean.domain.trip.TripId

/**
 * 하루 일정의 구간별 이동 초 (일정 계산의 입력). 길이와 순서는 ScheduleCalculator 의 legSeconds 와 같다.
 * null 은 "이 구간 경로를 모름"이다 — 계산 전·실패·오프라인. 임의 속도로 채우지 않는다(FR15).
 */
fun interface DayLegs {
    suspend fun legs(trip: TripId, day: TripDay): List<Long?>

    /** 구간 시간과 경로 출처 표기. 화면이 출처(디버그 표본 포함)를 함께 보인다. */
    suspend fun times(trip: TripId, day: TripDay): LegTimes = LegTimes(legs(trip, day))

    /** 공급자에 묻지 않고 이미 아는 구간만. 경로를 기다리기 전에 화면을 먼저 그릴 때 쓴다(NFR02). */
    suspend fun known(trip: TripId, day: TripDay): List<Long?> = List(legCount(day)) { null }
}

data class LegTimes(val legs: List<Long?>, val attributions: List<String> = emptyList())

/** 경로 공급자가 아직 없을 때. 모든 구간이 "모름"이라 그날은 Incomplete 로 보인다. */
object UnknownLegs : DayLegs {
    override suspend fun legs(trip: TripId, day: TripDay): List<Long?> =
        List(legCount(day)) { null }
}

/** 그날 구간 수 = 장소 수 - 1 (+1, 출발지가 있으면). */
fun legCount(day: TripDay): Int =
    if (day.stops.isEmpty()) 0 else day.stops.size - 1 + if (day.origin != null) 1 else 0
