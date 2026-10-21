package app.signkorean

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.signkorean.adapter.billing.ForegroundActivity
import java.lang.ref.WeakReference

/**
 * 지금 화면에 있는 Activity 와, 앱이 전면으로 나오는 순간.
 *
 * - [current]: 결제 화면을 띄울 Activity (설계서 §7.3). 약한 참조로만 잡는다.
 * - [onForeground]: 시작된 Activity 가 0개 → 1개가 될 때만 부른다(앱 시작·다른 앱에서 복귀, 설계서 §6.4·E-13).
 *   결제 화면(ProxyBillingActivity)은 같은 프로세스의 Activity 라서 resume 마다 부르면 결제 중에 확인이 끼어든다.
 *   화면 회전처럼 Activity 를 다시 만드는 경우도 복귀가 아니다.
 *
 * 생명주기 콜백은 메인 스레드에서만 오므로 개수는 잠그지 않는다.
 */
class ForegroundActivityTracker : Application.ActivityLifecycleCallbacks, ForegroundActivity {

    @Volatile private var resumed: WeakReference<Activity>? = null
    private var started = 0
    private var recreating = false

    @Volatile var onForeground: () -> Unit = {}

    override fun current(): Activity? = resumed?.get()

    override fun onActivityStarted(activity: Activity) {
        started++
        if (started == 1 && !recreating) onForeground()
        recreating = false
    }

    override fun onActivityStopped(activity: Activity) {
        started--
        recreating = activity.isChangingConfigurations
    }

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
