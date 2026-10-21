package app.signkorean.domain.location

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** 현재 위치를 출발지로 (FR14, AT08). M-02: 거부·해외·실패면 수동 선택을 그대로 둔다. */
class StartLocationsTest {

    private fun provider(result: suspend () -> LocationResult) = object : LocationProvider {
        override suspend fun current(): LocationResult = result()
    }

    @Test
    fun `서울 안이면 그 좌표`() = runTest {
        val c = Coordinate(37.5663, 126.9779)
        assertEquals(StartLocation.Found(c), StartLocations.locate(provider { LocationResult.Found(c) }))
    }

    @Test
    fun `M-02 권한 거부`() = runTest {
        assertEquals(StartLocation.Problem(StartLocationProblem.Denied), StartLocations.locate(provider { LocationResult.Denied }))
    }

    @Test
    fun `M-02 해외 위치는 쓰지 않는다`() = runTest {
        val tokyo = Coordinate(35.6812, 139.7671)
        assertEquals(StartLocation.Problem(StartLocationProblem.OutsideSeoul), StartLocations.locate(provider { LocationResult.Found(tokyo) }))
    }

    @Test
    fun `M-02 위치 실패 - 못 찾음, 예외, 시간 초과`() = runTest {
        val failed = StartLocation.Problem(StartLocationProblem.Failed)
        assertEquals(failed, StartLocations.locate(provider { LocationResult.Unavailable }))
        assertEquals(failed, StartLocations.locate(provider { error("gps") }))
        assertEquals(failed, StartLocations.locate(provider { awaitCancellation() }, timeoutMillis = 1_000))
    }
}
