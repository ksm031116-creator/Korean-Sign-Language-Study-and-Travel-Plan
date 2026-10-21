package app.signkorean

import android.app.Activity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 결제 화면을 띄울 Activity 와 전면 복귀 확인(E-13)의 신호. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class ForegroundActivityTrackerTest {

    private val tracker = ForegroundActivityTracker()

    @Test
    fun `전면에 온 Activity 를 알고, 뒤로 가면 잊는다`() {
        var resumes = 0
        tracker.onResume = { resumes++ }
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()

        tracker.onActivityResumed(activity)
        assertSame(activity, tracker.current())
        tracker.onActivityPaused(activity)
        assertNull(tracker.current())

        tracker.onActivityResumed(activity) // 결제 화면이나 다른 앱에서 돌아왔다
        assertEquals("복귀할 때마다 권한을 다시 확인한다", 2, resumes)
    }

    @Test
    fun `다른 Activity 가 멈춰도 지금 Activity 는 그대로`() {
        val first = Robolectric.buildActivity(Activity::class.java).setup().get()
        val second = Robolectric.buildActivity(Activity::class.java).setup().get()
        tracker.onActivityResumed(first)
        tracker.onActivityResumed(second)
        tracker.onActivityPaused(first)
        assertSame(second, tracker.current())
    }
}
