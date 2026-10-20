package app.signkorean.adapter.locale

import android.app.LocaleManager
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.signkorean.domain.common.LocaleTag
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * 앱별 언어 저장은 한 곳만 한다 (설계서 §8.2). API 30(AppCompat)과 34(OS LocaleManager) 모두 확인한다.
 * 화면(AppCompatActivity)이 하나도 없는 상태에서 실행된다. 앱 시작 직후·백그라운드와 같은 조건이다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30, 34])
class AppCompatCurrentLocaleTest {

    private val context = RuntimeEnvironment.getApplication()
    private val locale = AppCompatCurrentLocale(context)

    /** 이 SDK 에서 실제로 언어를 저장하는 곳의 값. */
    private fun stored(): String =
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
        else AppCompatDelegate.getApplicationLocales().toLanguageTags()

    private fun storeRaw(tags: String) {
        if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
        else AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
    }

    @After
    fun reset() = storeRaw("")

    @Test
    fun `고른 언어가 없으면 null`() {
        assertNull(locale.selected())
    }

    @Test
    fun `화면이 없어도 고른 언어를 저장하고 다시 읽는다`() {
        locale.select(LocaleTag("ja"))
        assertEquals("ja", stored())
        assertEquals(LocaleTag("ja"), locale.selected())
    }

    @Test
    fun `null 을 고르면 기기 언어를 따르게 되돌린다`() {
        locale.select(LocaleTag("ja"))
        locale.select(null)
        assertEquals("", stored())
        assertNull(locale.selected())
    }

    @Test
    fun `지역 태그로 저장돼 있어도 언어 부분으로 읽는다`() {
        storeRaw("ja-JP")
        assertEquals(LocaleTag("ja"), locale.selected())
    }

    @Test
    @Config(qualifiers = "ja-rJP")
    fun `기기 언어 목록을 BCP 47 로 돌려준다`() {
        assertTrue(locale.deviceTags().toString(), locale.deviceTags().first().startsWith("ja"))
    }
}
