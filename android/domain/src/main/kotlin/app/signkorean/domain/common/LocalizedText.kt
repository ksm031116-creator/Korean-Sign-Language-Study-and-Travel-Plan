package app.signkorean.domain.common

/**
 * BCP 47 언어 태그의 정규형. 예: "en", "ja", "en-US", "zh-Hant".
 * 언어는 소문자, 문자 체계는 첫 글자만 대문자, 지역은 대문자다. "EN" 과 "en" 이 서로 다른 키가 되어
 * 콘텐츠를 못 찾는 일이 없도록 정규형이 아니면 받지 않는다. 기기 태그는 LanguagePolicy.normalize 로 맞춘다.
 * 앱의 언어 결정은 언어 부분만 쓰므로 콘텐츠 팩의 언어 키도 언어 부분만 허용한다(ContentPackParser).
 */
@JvmInline
value class LocaleTag(val value: String) {
    init {
        require(PATTERN.matches(value)) { "BCP 47 언어 태그가 아니다: '$value'" }
    }

    private companion object {
        val PATTERN = Regex("[a-z]{2,3}(-[A-Z][a-z]{3})?(-([A-Z]{2}|[0-9]{3}))?")
    }
}

/**
 * 언어별 문구. 사용자 언어로 보여 줄 콘텐츠(뜻·설명·장소 이름)에 쓴다.
 * 빈 문자열·공백은 "없음"으로 본다. 그래야 번역이 덜 된 칸이 빈 화면으로 나오지 않고 대체 언어로 넘어간다.
 */
class LocalizedText(values: Map<LocaleTag, String>) {

    private val values: Map<LocaleTag, String> = values.filterValues { it.isNotBlank() }

    operator fun get(tag: LocaleTag): String? = values[tag]

    /** [chain] 순서대로 처음 있는 언어의 문구. 대체 순서는 호출하는 쪽(언어 정책)이 정한다. */
    fun resolve(chain: List<LocaleTag>): String? = chain.firstNotNullOfOrNull { values[it] }

    val locales: Set<LocaleTag> get() = values.keys

    override fun equals(other: Any?) = other is LocalizedText && other.values == values
    override fun hashCode() = values.hashCode()
    override fun toString() = "LocalizedText($values)"

    companion object {
        val EMPTY = LocalizedText(emptyMap())
    }
}
