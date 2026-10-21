package app.signkorean.adapter.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.location.LocationProvider
import app.signkorean.domain.location.LocationResult
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * OS 위치 (설계서 §6.6). Play 서비스 없이 [LocationManager] 로 한 번만 읽는다 — 계속 따라가지 않고, 백그라운드 위치는
 * 쓰지 않는다. 권한이 없으면 묻지 않고 [LocationResult.Denied] 다(권한 요청은 화면이 설명 뒤에 한다).
 * 좌표는 돌려주기만 하고 저장·로그하지 않는다(NFR05). 시간 제한은 호출하는 쪽([app.signkorean.domain.location.StartLocations]).
 */
class AndroidLocationProvider(context: Context) : LocationProvider {
    private val app = context.applicationContext
    private val manager = app.getSystemService(LocationManager::class.java)

    override suspend fun current(): LocationResult {
        if (!granted()) return LocationResult.Denied
        val lm = manager ?: return LocationResult.Unavailable
        val provider = pickProvider(lm) ?: return LocationResult.Unavailable
        val location = request(lm, provider) ?: return LocationResult.Unavailable
        return try {
            LocationResult.Found(Coordinate(location.latitude, location.longitude))
        } catch (_: IllegalArgumentException) {
            LocationResult.Unavailable
        }
    }

    private fun granted(): Boolean =
        app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pickProvider(lm: LocationManager): String? {
        val enabled = lm.getProviders(true)
        val order = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(LocationManager.FUSED_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
            add(LocationManager.GPS_PROVIDER)
        }
        return order.firstOrNull { it in enabled }
    }

    @SuppressLint("MissingPermission") // granted() 로 확인했다.
    private suspend fun request(lm: LocationManager, provider: String): Location? = suspendCancellableCoroutine { cont ->
        val signal = CancellationSignal()
        cont.invokeOnCancellation { signal.cancel() }
        val direct = Executor { it.run() }
        try {
            lm.getCurrentLocation(provider, signal, direct) { location -> if (cont.isActive) cont.resume(location) }
        } catch (_: SecurityException) {
            if (cont.isActive) cont.resume(null)
        } catch (_: IllegalArgumentException) {
            if (cont.isActive) cont.resume(null)
        }
    }
}
