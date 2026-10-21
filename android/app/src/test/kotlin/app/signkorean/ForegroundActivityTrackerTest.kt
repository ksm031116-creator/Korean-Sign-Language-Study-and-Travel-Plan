package app.signkorean

import android.app.Activity
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 결제 화면을 띄울 Activity 와 전면 복귀 확인(E-13)의 신호. 실제 생명주기 콜백으로 검사한다. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ForegroundActivityTrackerTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val tracker = ForegroundActivityTracker()
    private var foregrounds = 0

    @Before
    fun register() {
        tracker.onForeground = { foregrounds++ }
        app.registerActivityLifecycleCallbacks(tracker)
    }

    @After
    fun unregister() = app.unregisterActivityLifecycleCallbacks(tracker)

    @Test
    fun `앱 시작과 다른 앱에서 돌아올 때만 확인한다`() {
        val main = Robolectric.buildActivity(Activity::class.java).setup()
        assertEquals(1, foregrounds)
        assertSame(main.get(), tracker.current())

        main.pause().stop()
        assertNull(tracker.current())
        main.start().resume()
        assertEquals("다른 앱에서 돌아왔다", 2, foregrounds)
    }

    @Test
    fun `같은 앱 안의 결제 화면이 열리고 닫혀도 확인하지 않는다`() {
        val main = Robolectric.buildActivity(Activity::class.java).setup()
        main.pause()
        val billing = Robolectric.buildActivity(Activity::class.java).setup() // ProxyBillingActivity 자리
        main.stop()
        assertSame(billing.get(), tracker.current())

        main.start()
        billing.pause()
        main.resume()
        billing.stop().destroy()
        assertEquals(1, foregrounds)
        assertSame(main.get(), tracker.current())
    }

    @Test
    fun `화면 회전으로 다시 만들어져도 확인하지 않는다`() {
        val main = Robolectric.buildActivity(Activity::class.java).setup()
        main.configurationChange()
        assertEquals(1, foregrounds)
    }
}
