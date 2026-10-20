package app.signkorean.domain.locale

import app.signkorean.domain.common.LocaleTag

/**
 * 앱 언어 설정 포트. 언어 값의 저장은 구현(AppCompat 앱별 언어) 한 곳만 한다 (설계서 §8.2).
 */
interface CurrentLocale {
    /** 사용자가 앱에서 고른 언어. null 이면 기기 언어를 따른다. */
    fun selected(): LocaleTag?

    /** 앱 언어를 바꾼다. null 이면 기기 언어를 따르게 되돌린다. */
    fun select(tag: LocaleTag?)

    /** 기기 언어 목록(BCP 47, 선호 순서). */
    fun deviceTags(): List<String>
}

/** 지금 화면과 콘텐츠에 쓸 언어. 고른 언어 → 기기 언어 → en 순서. */
class ResolveAppLanguage(private val currentLocale: CurrentLocale) {

    operator fun invoke(supported: List<LocaleTag>): LocaleTag {
        val chosen = currentLocale.selected()?.let { LanguagePolicy.normalize(it.value) }
        if (chosen != null && chosen in supported) return chosen
        return LanguagePolicy.resolve(currentLocale.deviceTags(), supported)
    }
}
