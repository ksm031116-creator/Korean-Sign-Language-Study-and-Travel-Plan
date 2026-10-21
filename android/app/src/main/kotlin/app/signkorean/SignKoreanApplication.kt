package app.signkorean

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import app.signkorean.di.appModules
import app.signkorean.di.variantModules
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.domain.upgrade.ResumePendingAction
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
            // 디버그 빌드는 모의 결제 등으로 덮어쓴다. 릴리스의 variantModules 는 비어 있다.
            modules(appModules + variantModules)
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
        (koin.get<EntitlementGateway>() as? PlayBillingEntitlementGateway)?.onStoreChanged = refresh

        // 권한이 Paid 로 확인될 때마다 보류 동작을 이어서 한다. 결제 중 프로세스가 끝났다가 다시 켠 뒤
        // 시작 확인에서 Paid 가 된 경우도 여기서 저장된다(B-07). 보류 동작이 없으면 아무 일도 없다.
        val resume = koin.get<ResumePendingAction>()
        scope.launch { entitlements.entitlement.collect { if (it == Entitlement.Paid) resume() } }

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
