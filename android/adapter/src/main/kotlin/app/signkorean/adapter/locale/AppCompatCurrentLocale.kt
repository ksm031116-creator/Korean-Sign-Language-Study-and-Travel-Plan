package app.signkorean.adapter.locale

import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.locale.CurrentLocale
import app.signkorean.domain.locale.LanguagePolicy

/**
 * 앱별 언어(per-app language). 별도 저장소에 이중으로 저장하지 않는다 (설계서 §8.2).
 *
 * - API 33+: OS 의 LocaleManager 가 저장한다. AppCompatDelegate 를 거치면 살아 있는 AppCompat 화면이
 *   없을 때(앱 시작 직후, 백그라운드) 아무 일도 하지 않으므로 LocaleManager 를 직접 쓴다.
 * - API 30~32: AppCompatDelegate 가 바꾸고 autoStoreLocales 로 직접 저장한다.
 */
class AppCompatCurrentLocale(private val context: Context) : CurrentLocale {

    override fun selected(): LocaleTag? {
        val tags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localeManager().applicationLocales.toLanguageTags()
        } else {
            AppCompatDelegate.getApplicationLocales().toLanguageTags()
        }
        return tags.split(',').firstOrNull { it.isNotBlank() }?.let(LanguagePolicy::normalize)
    }

    override fun select(tag: LocaleTag?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            localeManager().applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag.value)
        } else {
            AppCompatDelegate.setApplicationLocales(
                if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag.value),
            )
        }
    }

    override fun deviceTags(): List<String> {
        val system = LocaleManagerCompat.getSystemLocales(context)
        return (0 until system.size()).mapNotNull { system[it]?.toLanguageTag() }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun localeManager(): LocaleManager = context.getSystemService(LocaleManager::class.java)
}
