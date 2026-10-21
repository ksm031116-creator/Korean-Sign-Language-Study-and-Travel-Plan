package app.signkorean.testing

import app.signkorean.domain.location.LocationProvider
import app.signkorean.domain.location.LocationResult

/** 위치 가짜. [calls] 로 사용자가 고르기 전에 위치를 읽지 않았는지 확인한다(FR14, NFR05). */
class FakeLocationProvider(@Volatile var result: LocationResult = LocationResult.Unavailable) : LocationProvider {
    @Volatile var calls = 0
        private set

    override suspend fun current(): LocationResult {
        calls++
        return result
    }
}
