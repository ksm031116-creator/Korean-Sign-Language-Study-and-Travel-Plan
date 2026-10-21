package app.signkorean.domain.trip

import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * 한 장소의 도착·출발. 모르면(앞 구간 실패) null.
 * - [arriveMinute]·[leaveMinute]: 하루 출발 시각부터 지난 분. 자정을 넘겨도 계속 늘어나 순서를 비교할 수 있다.
 * - [arriveDay]·[leaveDay]: 출발일 기준 날 수(0 = 그날, 1 = 다음 날 …). 며칠 넘어가는 일정도 구별한다.
 */
data class StopTiming(
    val arrive: LocalTime?,
    val leave: LocalTime?,
    val arriveMinute: Int?,
    val leaveMinute: Int?,
    val arriveDay: Int,
    val leaveDay: Int,
)

/**
 * 하루 일정 계산 결과. 합계는 초 단위로 더한 뒤 분으로 올린다(PRD §9.2). 시각도 같은 규칙이다 — 누적 초를 분으로
 * 올려서, 끝 시각 = 출발 + 이동 합계 + 체류 합계가 늘 맞는다(Phase 16 리뷰 TRP-02).
 * - [legMinutes]: 구간마다 올린 분. 실패한 구간은 null.
 * - [incomplete]: 구간이 하나라도 실패했다. 그 뒤 시각·이동 합계·총합·종료 시각은 null 이다(허위 합계 금지, FR20).
 * - [roundingDiffers]: 구간 표시를 더한 값과 이동 합계가 다르다. 화면은 그 이유를 안내한다.
 * - [nextDay]: 어느 도착·출발이든 출발일을 넘긴다. 화면은 "다음 날 도착"과 일정 단축 안내를 보인다.
 */
data class DaySchedule(
    val stops: List<StopTiming>,
    val legMinutes: List<Int?>,
    val travelMinutes: Int?,
    val stayMinutes: Int,
    val totalMinutes: Int?,
    val end: LocalTime?,
    val incomplete: Boolean,
    val nextDay: Boolean,
    val roundingDiffers: Boolean,
)

/** 순수 함수 (설계서 §6.5). 경로 공급자가 준 구간 초를 받아 시각을 계산한다. 임의 속도로 시간을 만들지 않는다(FR15). */
object ScheduleCalculator {

    private const val DAY_MINUTES = 1_440

    /**
     * @param start 하루 출발 시각. 초는 버린다.
     * @param hasOrigin 출발지가 있으면 [legSeconds] 의 첫 값은 출발지 → 첫 장소다. 없으면 첫 장소 도착 = [start].
     * @param stays 장소별 체류 분(0 이상).
     * @param legSeconds 구간별 이동 초(0 이상). 길이 = 장소 수 - 1 (+1, 출발지가 있을 때). null 이면 그 구간 경로 실패.
     */
    fun calculate(start: LocalTime, hasOrigin: Boolean, stays: List<Int>, legSeconds: List<Long?>): DaySchedule {
        val expectedLegs = if (stays.isEmpty()) 0 else stays.size - 1 + if (hasOrigin) 1 else 0
        require(legSeconds.size == expectedLegs) { "구간 수 ${legSeconds.size} 가 장소 ${stays.size} 곳(출발지 $hasOrigin)과 맞지 않는다" }
        require(legSeconds.all { it == null || it >= 0 }) { "이동 시간은 0 이상이어야 한다: $legSeconds" }
        require(stays.all { it >= 0 }) { "체류 시간은 0 이상이어야 한다: $stays" }
        val begin = start.truncatedTo(ChronoUnit.MINUTES)
        val beginMinute = begin.toSecondOfDay() / 60
        var elapsedSeconds: Long? = 0L
        var legIndex = 0
        val timings = stays.mapIndexed { i, stay ->
            if (i > 0 || hasOrigin) {
                val leg = legSeconds[legIndex++]
                elapsedSeconds = if (leg == null || elapsedSeconds == null) null else elapsedSeconds!! + leg
            }
            val arriveMinute = elapsedSeconds?.let { ceilMinutes(it) }
            elapsedSeconds = elapsedSeconds?.plus(stay * 60L)
            val leaveMinute = elapsedSeconds?.let { ceilMinutes(it) }
            StopTiming(
                arrive = arriveMinute?.let { begin.plusMinutes(it.toLong()) },
                leave = leaveMinute?.let { begin.plusMinutes(it.toLong()) },
                arriveMinute = arriveMinute,
                leaveMinute = leaveMinute,
                arriveDay = arriveMinute?.let { (beginMinute + it) / DAY_MINUTES } ?: 0,
                leaveDay = leaveMinute?.let { (beginMinute + it) / DAY_MINUTES } ?: 0,
            )
        }
        val incomplete = legSeconds.any { it == null }
        val travel = if (incomplete) null else ceilMinutes(legSeconds.sumOf { it!! })
        val legMinutes = legSeconds.map { it?.let(::ceilMinutes) }
        val stay = stays.sum()
        return DaySchedule(
            stops = timings,
            legMinutes = legMinutes,
            travelMinutes = travel,
            stayMinutes = stay,
            totalMinutes = travel?.plus(stay),
            end = if (incomplete) null else timings.lastOrNull()?.leave,
            incomplete = incomplete,
            nextDay = timings.any { it.arriveDay > 0 || it.leaveDay > 0 },
            roundingDiffers = travel != null && legMinutes.sumOf { it ?: 0 } != travel,
        )
    }

    private fun ceilMinutes(seconds: Long): Int = ((seconds + 59) / 60).toInt()
}
