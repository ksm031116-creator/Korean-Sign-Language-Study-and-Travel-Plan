package app.signkorean.domain.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** 추천 출발점. 이름은 화면이 [key] 로 현지화한다. */
data class SuggestedStart(val key: String, val coordinate: Coordinate)

/** 서비스 지역 범위 (FR14). 서울 행정 경계를 감싸는 사각형이다 — 경계 근처 몇 백 m 는 넉넉히 서울로 본다. */
object SeoulArea {
    private const val SOUTH = 37.413
    private const val NORTH = 37.716
    private const val WEST = 126.734
    private const val EAST = 127.270

    fun contains(c: Coordinate): Boolean = c.lat in SOUTH..NORTH && c.lng in WEST..EAST

    /** SC19 추천 출발점 (구현 계획 Phase 19 D3). */
    val suggestedStarts: List<SuggestedStart> = listOf(
        SuggestedStart("seoul-station", Coordinate(37.5547, 126.9707)),
        SuggestedStart("city-hall", Coordinate(37.5663, 126.9779)),
    )
}

sealed interface LocationResult {
    data class Found(val coordinate: Coordinate) : LocationResult

    /** OS 위치 권한이 없다. */
    data object Denied : LocationResult

    /** 위치를 얻지 못했다(꺼짐·신호 없음). */
    data object Unavailable : LocationResult
}

/**
 * 현재 위치 포트 (설계서 §6.6). 사용자가 "현재 위치"를 고를 때만 부른다. 위치는 저장하거나 로그에 남기지 않는다
 * — 하루 출발지로 Apply 했을 때만 그 좌표가 일정에 저장된다(PRD §10.4, NFR05).
 */
interface LocationProvider {
    suspend fun current(): LocationResult
}

enum class StartLocationProblem { Denied, OutsideSeoul, Failed }

sealed interface StartLocation {
    data class Found(val coordinate: Coordinate) : StartLocation
    data class Problem(val problem: StartLocationProblem) : StartLocation
}

/** 현재 위치를 출발지로 쓸 수 있는지 (M-02, AT08). 거부·해외·실패면 이유를 돌려주고 화면은 수동 선택을 그대로 둔다. */
object StartLocations {
    const val TIMEOUT_MILLIS = 10_000L

    suspend fun locate(provider: LocationProvider, timeoutMillis: Long = TIMEOUT_MILLIS): StartLocation {
        val result = try {
            withTimeoutOrNull(timeoutMillis) { provider.current() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return when (result) {
            is LocationResult.Found ->
                if (SeoulArea.contains(result.coordinate)) StartLocation.Found(result.coordinate) else StartLocation.Problem(StartLocationProblem.OutsideSeoul)
            LocationResult.Denied -> StartLocation.Problem(StartLocationProblem.Denied)
            LocationResult.Unavailable, null -> StartLocation.Problem(StartLocationProblem.Failed)
        }
    }
}
