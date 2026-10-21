package app.signkorean.domain.trip

import java.time.LocalTime

/** 한 장소의 도착·출발. 모르면(앞 구간 실패) null. [arriveNextDay] 는 자정을 넘겨 도착함을 뜻한다. */
data class StopTiming(val arrive: LocalTime?, val leave: LocalTime?, val arriveNextDay: Boolean)

/**
 * 하루 일정 계산 결과. 합계는 초 단위로 더한 뒤 분으로 올린다(PRD §9.2).
 * - [legMinutes]: 구간마다 올린 분. 실패한 구간은 null.
 * - [incomplete]: 구간이 하나라도 실패했다. 그 뒤 시각·이동 합계·총합·종료 시각은 null 이다(허위 합계 금지, FR20).
 * - [roundingDiffers]: 구간 표시를 더한 값과 이동 합계가 다르다. 화면은 그 이유를 안내한다.
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

    private const val DAY_SECONDS = 86_400L

    /**
     * @param hasOrigin 출발지가 있으면 [legSeconds] 의 첫 값은 출발지 → 첫 장소다. 없으면 첫 장소 도착 = [start].
     * @param stays 장소별 체류 분.
     * @param legSeconds 구간별 이동 초. 길이 = 장소 수 - 1 (+1, 출발지가 있을 때). null 이면 그 구간 경로 실패.
     */
    fun calculate(start: LocalTime, hasOrigin: Boolean, stays: List<Int>, legSeconds: List<Long?>): DaySchedule {
        val expectedLegs = if (stays.isEmpty()) 0 else stays.size - 1 + if (hasOrigin) 1 else 0
        require(legSeconds.size == expectedLegs) { "구간 수 ${legSeconds.size} 가 장소 ${stays.size} 곳(출발지 $hasOrigin)과 맞지 않는다" }
        val begin = start.toSecondOfDay().toLong()
        var elapsed: Long? = 0L
        var legIndex = 0
        val timings = stays.mapIndexed { i, stay ->
            if (i > 0 || hasOrigin) {
                val leg = legSeconds[legIndex++]
                elapsed = if (leg == null || elapsed == null) null else elapsed!! + leg
            }
            val arriveAt = elapsed
            val leaveAt = arriveAt?.plus(stay * 60L)
            elapsed = leaveAt
            StopTiming(
                arrive = arriveAt?.let { at(begin + it) },
                leave = leaveAt?.let { at(begin + it) },
                arriveNextDay = arriveAt != null && begin + arriveAt >= DAY_SECONDS,
            )
        }
        val incomplete = legSeconds.any { it == null }
        val travelSeconds = if (incomplete) null else legSeconds.sumOf { it!! }
        val travel = travelSeconds?.let { ceilMinutes(it) }
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
            nextDay = timings.any { it.arriveNextDay } || (!incomplete && stays.isNotEmpty() && begin + (elapsed ?: 0) >= DAY_SECONDS),
            roundingDiffers = travel != null && legMinutes.sumOf { it ?: 0 } != travel,
        )
    }

    private fun ceilMinutes(seconds: Long): Int = ((seconds + 59) / 60).toInt()

    private fun at(secondOfDay: Long): LocalTime = LocalTime.ofSecondOfDay(secondOfDay % DAY_SECONDS)
}
