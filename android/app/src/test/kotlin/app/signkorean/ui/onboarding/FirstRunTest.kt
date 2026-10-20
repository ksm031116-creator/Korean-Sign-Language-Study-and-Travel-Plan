package app.signkorean.ui.onboarding

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.ReviewStatus
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeCurrentLocale
import app.signkorean.testing.FakeOnboardingStore
import app.signkorean.testing.InMemoryContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FirstRunTest {

    private val en = LocaleTag("en")
    private val ja = LocaleTag("ja")
    private val pack = ContentFixtures.pack(locales = mapOf(en to ReviewStatus.Reviewed, ja to ReviewStatus.Draft))
    private val onboarding = FakeOnboardingStore()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(device: String = "en-US", drafts: Boolean = false, locale: FakeCurrentLocale = FakeCurrentLocale(listOf(device))) =
        FirstRunViewModel(onboarding, InMemoryContentRepository(pack), locale, drafts)

    @Test
    fun `처음 실행이면 안내를 보이고 끝내면 다시 보이지 않는다`() = runTest {
        val gate = AppGateViewModel(onboarding)
        assertEquals(true, gate.needsFirstRun.value)
        val first = vm()
        first.onAction(FirstRunAction.Finish)
        assertTrue(first.state.value.finished)
        assertEquals(false, AppGateViewModel(onboarding).needsFirstRun.value)
    }

    @Test
    fun `3단계를 Next·Back 으로 오가고 범위를 넘지 않는다`() = runTest {
        val v = vm()
        v.onAction(FirstRunAction.Back)
        assertEquals(0, v.state.value.step)
        repeat(5) { v.onAction(FirstRunAction.Next) }
        assertEquals(2, v.state.value.step)
        v.onAction(FirstRunAction.Back)
        assertEquals(1, v.state.value.step)
    }

    @Test
    fun `출시 빌드는 검수 완료 언어만 고를 수 있다`() = runTest {
        assertEquals(listOf(en), vm(device = "ja-JP").state.value.languages)
    }

    @Test
    fun `L10N-01 디버그에서 기기가 일본어면 ja 가 골라져 있고, 다른 언어를 고르면 앱 언어로 저장한다`() = runTest {
        val locale = FakeCurrentLocale(listOf("ja-JP"))
        val v = vm(drafts = true, locale = locale)
        assertEquals(listOf(en, ja), v.state.value.languages)
        assertEquals(ja, v.state.value.current)
        v.onAction(FirstRunAction.SelectLanguage(en))
        assertEquals(en, locale.selected())
        assertEquals(en, v.state.value.current)
    }
}
