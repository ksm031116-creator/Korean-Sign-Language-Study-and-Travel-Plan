package app.signkorean

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.signkorean.adapter.billing.ForegroundActivity
import java.lang.ref.WeakReference

/**
 * 지금 화면에 있는 Activity. 결제 화면을 띄울 때 쓰고(설계서 §7.3), 앱이 전면으로 돌아올 때마다
 * [onResume] 을 불러 권한을 다시 확인하게 한다(설계서 §6.4, E-13). Activity 는 약한 참조로만 잡는다.
 */
class ForegroundActivityTracker : Application.ActivityLifecycleCallbacks, ForegroundActivity {

    @Volatile private var resumed: WeakReference<Activity>? = null

    @Volatile var onResume: () -> Unit = {}

    override fun current(): Activity? = resumed?.get()

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
        onResume()
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed?.get() === activity) resumed = null
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
