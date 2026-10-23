package app.signkorean

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import app.signkorean.di.appModules
import app.signkorean.di.variantModules
import app.signkorean.domain.entitlement.Entitlement
import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.domain.upgrade.ResumePendingAction
import app.signkorean.domain.upgrade.StartupPendingCheck
import app.signkorean.domain.common.AppScope
import app.signkorean.domain.content.ContentRepository
import app.signkorean.adapter.billing.PlayBillingEntitlementGateway
import app.signkorean.domain.entitlement.Entitlements
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class SignKoreanApplication : Application(), ImageLoaderFactory {

    /**
     * 장소 사진 로더. Wikimedia 는 일반 User-Agent 요청을 막을 수 있어 앱 이름과 연락처를 밝힌다
     * (Wikimedia User-Agent 정책, Phase 14 리뷰 질문). 메모리·디스크 캐시는 Coil 기본값을 쓴다.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build()) }
                .build()
        }
        .build()


    override fun onCreate() {
        super.onCreate()
        val koin = startKoin {
            androidContext(this@SignKoreanApplication)
            // 빌드 종류별 모듈로 덮어쓴다: 모의 결제(기본, 디버그·릴리스 모두), 디버그는 표본 데이터·다시 잠그기도.
            modules(appModules + variantModules)
        }.koin

        // 콘텐츠 팩을 미리 읽어 첫 화면의 대기를 줄이고, 읽은 결과를 로그로 남긴다.
        // 디버그 빌드에서는 팩을 못 읽으면 바로 죽여서 놓치지 않게 한다(출시 빌드는 화면의 오류 상태가 처리).
        val content = koin.get<ContentRepository>()
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val scope = koin.get<AppScope>().scope

        // 권한 확인: 앱 시작과 다른 앱에서 돌아올 때 한 번씩 (설계서 §6.4, E-13). 앱이 앞에 있는 동안
        // 결제 화면 밖에서 구매가 바뀌면(대기 결제 완료) 그때도 확인한다. 실패하면 마지막 권한을 유지한다.
        val entitlements = koin.get<Entitlements>()
        val resume = koin.get<ResumePendingAction>()
        val startupCheck = koin.get<StartupPendingCheck>()
        // 확인할 때마다 Paid 면 보류 동작을 이어서 한다. 지난번 이어서 하기가 실패했어도 다음 확인에서 다시 한다(리뷰 UPG-06).
        val refresh = {
            scope.launch {
                entitlements.refresh()
                if (entitlements.current() == Entitlement.Paid) resume()
            }
            Unit
        }
        // 프로세스의 첫 전면 진입은 시작 확인이다. 결제 흔적이 없으면 지난 실행의 보류 동작을 버린다(리뷰 UPG-07).
        var started = false
        val onForeground = {
            if (started) refresh() else {
                started = true
                scope.launch { startupCheck() }
            }
            Unit
        }
        koin.get<ForegroundActivityTracker>().also {
            it.onForeground = onForeground
            registerActivityLifecycleCallbacks(it)
        }
        (koin.get<EntitlementGateway>() as? PlayBillingEntitlementGateway)?.onStoreChanged = refresh

        // 권한이 Paid 로 바뀌는 순간에도 이어서 한다(SC21 밖에서 결제가 끝난 경우). 보류 동작이 없으면 아무 일도 없다.
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
        val USER_AGENT = "SignKorean/${BuildConfig.VERSION_NAME} (Android; https://github.com/ksm031116-creator)"
    }
}
