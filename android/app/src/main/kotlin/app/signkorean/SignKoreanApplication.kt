package app.signkorean

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import app.signkorean.di.appModules
import app.signkorean.domain.common.AppDispatchers
import app.signkorean.domain.content.ContentRepository
import app.signkorean.adapter.billing.PlayBillingEntitlementGateway
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

        // 권한 확인: 앱 시작과 다른 앱에서 돌아올 때 한 번씩 (설계서 §6.4, E-13). 앱이 앞에 있는 동안
        // 결제 화면 밖에서 구매가 바뀌면(대기 결제 완료) 그때도 확인한다. 실패하면 마지막 권한을 유지한다.
        val entitlements = koin.get<Entitlements>()
        val refresh = { scope.launch { entitlements.refresh() }; Unit }
        koin.get<ForegroundActivityTracker>().also {
            it.onForeground = refresh
            registerActivityLifecycleCallbacks(it)
        }
        koin.get<PlayBillingEntitlementGateway>().onStoreChanged = refresh

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
