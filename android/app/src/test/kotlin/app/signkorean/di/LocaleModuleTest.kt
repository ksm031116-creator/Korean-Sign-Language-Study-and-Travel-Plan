package app.signkorean.di

import app.signkorean.adapter.locale.AppCompatCurrentLocale
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.locale.CurrentLocale
import app.signkorean.domain.locale.ResolveAppLanguage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.dsl.koinApplication
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** 언어 조립이 실제 Android 컨텍스트로 만들어진다 (KoinModulesTest 의 verify 는 만들어 보지 않는다). */
@RunWith(RobolectricTestRunner::class)
class LocaleModuleTest {

    private val app: KoinApplication = koinApplication {
        androidContext(RuntimeEnvironment.getApplication())
        modules(localeModule)
    }

    @After
    fun close() = app.close()

    @Test
    fun `CurrentLocale 은 앱 하나에 하나, ResolveAppLanguage 는 매번 새로 만든다`() {
        val locale = app.koin.get<CurrentLocale>()
        assertTrue(locale is AppCompatCurrentLocale)
        assertSame(locale, app.koin.get<CurrentLocale>())
        assertNotSame(app.koin.get<ResolveAppLanguage>(), app.koin.get<ResolveAppLanguage>())
    }

    @Test
    fun `조립된 ResolveAppLanguage 가 기기 언어로 언어를 정한다`() {
        assertEquals(LocaleTag("en"), app.koin.get<ResolveAppLanguage>().invoke(listOf(LocaleTag("en"), LocaleTag("ja"))))
    }
}
