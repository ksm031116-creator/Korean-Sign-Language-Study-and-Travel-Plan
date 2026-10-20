package app.signkorean.domain.locale

import app.signkorean.domain.common.LocaleTag

/**
 * 앱 언어 결정과 콘텐츠 대체 순서 (설계서 §8.2). 대체 순서는 여기 한 곳에서만 정한다.
 * 지원 언어 목록은 인자로 받는다. 목록은 코드가 아니라 콘텐츠 팩·리소스 데이터에서 온다(L10N-08).
 */
object LanguagePolicy {

    /** 기준 언어. 지원 언어가 하나도 맞지 않을 때와 콘텐츠 대체의 마지막 단계. */
    val BASE = LocaleTag("en")

    /**
     * 기기·사용자 언어 태그를 언어 부분으로 맞춘다(ja-JP → ja, en_GB → en). 해석할 수 없으면 null.
     * 지금 지원 언어에는 문자 체계(zh-Hant 등) 구분이 필요 없어서 언어 부분만 쓴다.
     */
    fun normalize(tag: String): LocaleTag? {
        val language = tag.trim().replace('_', '-').substringBefore('-').lowercase()
        if (language.isEmpty() || language == "und" || !LANGUAGE.matches(language)) return null
        return LocaleTag(language)
    }

    /** 기기 언어 목록에서 처음으로 지원되는 언어. 없으면 [BASE]. */
    fun resolve(deviceTags: List<String>, supported: List<LocaleTag>): LocaleTag =
        deviceTags.asSequence().mapNotNull(::normalize).firstOrNull { it in supported } ?: BASE

    /** 콘텐츠 문구 대체 순서: 사용자 언어 → en. */
    fun fallbackChain(user: LocaleTag): List<LocaleTag> = listOf(user, BASE).distinct()

    private val LANGUAGE = Regex("[a-z]{2,3}")
}
