package app.signkorean

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import app.signkorean.di.appModules
import app.signkorean.domain.common.AppDispatchers
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.entitlement.Entitlements
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SignKoreanApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@SignKoreanApplication)
            modules(appModules)
        }.koin

        // 콘텐츠 팩을 미리 읽어 첫 화면의 대기를 줄이고, 읽은 결과를 로그로 남긴다.
        // 디버그 빌드에서는 팩을 못 읽으면 바로 죽여서 놓치지 않게 한다(출시 빌드는 화면의 오류 상태가 처리).
        val content = koin.get<ContentRepository>()
        val dispatchers = koin.get<AppDispatchers>()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val scope = CoroutineScope(SupervisorJob() + dispatchers.default)

        // 권한 확인: 앱 시작(첫 화면의 resume)과 전면 복귀마다 한 번 (설계서 §6.4, E-13).
        // 확인 실패는 Entitlements 가 마지막 권한을 유지하는 것으로 처리한다.
        val entitlements = koin.get<Entitlements>()
        val tracker = koin.get<ForegroundActivityTracker>()
        tracker.onResume = { scope.launch { entitlements.refresh() } }
        registerActivityLifecycleCallbacks(tracker)

        scope.launch {
            try {
                val pack = content.load()
                Log.i(TAG, "content ${pack.version}: cards=${pack.cards.size} places=${pack.places.size} areas=${pack.areas.size}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "content pack failed to load", e)
                if (debuggable) throw IllegalStateException("content pack failed to load", e)
            }
        }
    }

    private companion object {
        const val TAG = "SignKorean"
    }
}
