package app.signkorean.domain.trip

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalTime
import kotlin.random.Random

/** 하루 일정 계산 (FR19·20, PRD §9.2, 설계서 §6.5). T-01~06, T-P1 */
class ScheduleCalculatorTest {

    private fun t(h: Int, m: Int) = LocalTime.of(h, m)
    private fun min(m: Int) = m * 60L

    @Test
    fun `T-01 PRD 예시 - 출발지에서 A 10분, A 30분, B 20분, B 40분`() {
        val s = ScheduleCalculator.calculate(t(9, 0), hasOrigin = true, stays = listOf(30, 40), legSeconds = listOf(min(10), min(20)))
        assertEquals(t(9, 10), s.stops[0].arrive)
        assertEquals(t(9, 40), s.stops[0].leave)
        assertEquals(t(10, 0), s.stops[1].arrive)
        assertEquals(t(10, 40), s.stops[1].leave)
        assertEquals(30, s.travelMinutes)
        assertEquals(70, s.stayMinutes)
        assertEquals(100, s.totalMinutes)
        assertEquals(t(10, 40), s.end)
        assertFalse(s.incomplete)
    }

    @Test
    fun `T-02 출발지가 없으면 첫 장소 도착이 출발 시각`() {
        val s = ScheduleCalculator.calculate(t(9, 0), hasOrigin = false, stays = listOf(30, 40), legSeconds = listOf(min(20)))
        assertEquals(t(9, 0), s.stops[0].arrive)
        assertEquals(t(9, 50), s.stops[1].arrive)
        assertEquals(20, s.travelMinutes)
    }

    @Test
    fun `T-03 체류 0분이면 도착과 출발이 같다`() {
        val s = ScheduleCalculator.calculate(t(9, 0), hasOrigin = false, stays = listOf(0), legSeconds = emptyList())
        assertEquals(s.stops[0].arrive, s.stops[0].leave)
        assertEquals(0, s.totalMinutes)
    }

    @Test
    fun `T-04 두 번째 구간이 실패하면 그 뒤 시각과 총합은 Incomplete`() {
        val s = ScheduleCalculator.calculate(t(9, 0), hasOrigin = true, stays = listOf(30, 40, 20), legSeconds = listOf(min(10), null, min(5)))
        assertEquals(t(9, 10), s.stops[0].arrive, "실패 전 구간은 그대로")
        assertNull(s.stops[1].arrive)
        assertNull(s.stops[2].arrive)
        assertNull(s.travelMinutes, "허위 합계를 만들지 않는다")
        assertNull(s.totalMinutes)
        assertNull(s.end)
        assertTrue(s.incomplete)
        assertEquals(listOf(10, null, 5), s.legMinutes, "구간 자체는 아는 만큼 보인다")
    }

    @Test
    fun `T-05 23시 30분 출발이 자정을 넘기면 다음 날 도착`() {
        val s = ScheduleCalculator.calculate(t(23, 30), hasOrigin = true, stays = listOf(30, 30), legSeconds = listOf(min(10), min(15)))
        assertFalse(s.stops[0].arriveNextDay)
        assertTrue(s.stops[1].arriveNextDay)
        assertTrue(s.nextDay)
        assertEquals(t(0, 25), s.stops[1].arrive)
    }

    @Test
    fun `T-06 59초 구간 셋은 초 단위로 더한 뒤 올려 3분, 구간 표시도 1분씩`() {
        val s = ScheduleCalculator.calculate(t(9, 0), hasOrigin = true, stays = listOf(0, 0, 0), legSeconds = listOf(59, 59, 59))
        assertEquals(3, s.travelMinutes)
        assertEquals(listOf(1, 1, 1), s.legMinutes)
        assertFalse(s.roundingDiffers)
    }

    @Test
    fun `구간 표시를 더한 값과 합계가 다르면 안내 플래그`() {
        // 30초 + 30초 = 60초 → 합계 1분, 구간 표시는 1분 + 1분 = 2분
        val s = ScheduleCalculator.calculate(t(9, 0), hasOrigin = true, stays = listOf(0, 0), legSeconds = listOf(30, 30))
        assertEquals(1, s.travelMinutes)
        assertTrue(s.roundingDiffers)
    }

    @Test
    fun `장소가 없으면 빈 일정`() {
        val s = ScheduleCalculator.calculate(t(9, 0), hasOrigin = false, stays = emptyList(), legSeconds = emptyList())
        assertTrue(s.stops.isEmpty())
        assertEquals(0, s.totalMinutes)
        assertNull(s.end)
    }

    @Test
    fun `T-P1 임의 일정 - 도착은 늘 앞 장소 출발 뒤, 완전하면 총합 = 이동 + 체류`() {
        val random = Random(20261021)
        repeat(500) {
            val n = random.nextInt(1, 11)
            val hasOrigin = random.nextBoolean()
            val stays = List(n) { random.nextInt(0, 481) }
            val legs = List(n - 1 + if (hasOrigin) 1 else 0) { if (random.nextInt(10) == 0) null else random.nextLong(0, 7200) }
            val start = LocalTime.ofSecondOfDay(random.nextLong(0, 86_400))
            val s = ScheduleCalculator.calculate(start, hasOrigin, stays, legs)
            assertEquals(n, s.stops.size)
            assertEquals(stays.sum(), s.stayMinutes)
            if (legs.none { it == null }) {
                assertFalse(s.incomplete)
                assertEquals(s.travelMinutes!! + s.stayMinutes, s.totalMinutes)
                assertTrue(s.stops.all { it.arrive != null && it.leave != null })
            } else {
                assertTrue(s.incomplete)
                assertNull(s.totalMinutes)
                val firstGap = legs.indexOfFirst { it == null }
                val firstUnknown = firstGap + if (hasOrigin) 0 else 1
                assertTrue(s.stops.drop(firstUnknown).all { it.arrive == null }, "실패 구간 뒤는 모두 모른다")
            }
        }
    }

    @Test
    fun `구간 수가 장소 수와 맞지 않으면 계산하지 않는다`() {
        org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            ScheduleCalculator.calculate(t(9, 0), hasOrigin = true, stays = listOf(30, 30), legSeconds = listOf(60))
        }
    }
}
