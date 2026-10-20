package app.signkorean.domain.common

/** BCP 47 언어 태그. 예: "en", "ja", "zh-Hant". 밑줄 형식(en_US)은 받지 않는다. */
@JvmInline
value class LocaleTag(val value: String) {
    init {
        require(PATTERN.matches(value)) { "BCP 47 언어 태그가 아니다: '$value'" }
    }

    private companion object {
        val PATTERN = Regex("[a-zA-Z]{2,3}(-[a-zA-Z0-9]{2,8})*")
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
