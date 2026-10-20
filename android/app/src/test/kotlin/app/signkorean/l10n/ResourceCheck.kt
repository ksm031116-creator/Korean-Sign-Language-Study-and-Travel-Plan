package app.signkorean.l10n

/** 언어별 리소스의 키·자리표시자 검사 (설계서 §8.8). 누락 키는 Lint MissingTranslation 이 따로 막는다. */
object ResourceCheck {

    private val PLACEHOLDER = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfxXc]""")

    fun placeholders(text: String): List<String> = PLACEHOLDER.findAll(text).map { it.value }.sorted().toList()

    fun problems(all: List<ResourceStrings>): List<String> {
        val base = all.first { it.qualifier == "default" }
        return all.filter { it !== base }.flatMap { lang ->
            buildList {
                lang.strings.forEach { (name, text) ->
                    when {
                        name in base.untranslatable -> add("${lang.qualifier}: $name 은 translatable=false 다")
                        name !in base.strings -> add("${lang.qualifier}: $name 는 기본 언어(values)에 없다")
                        placeholders(text) != placeholders(base[name]) ->
                            add("${lang.qualifier}: $name 자리표시자 ${placeholders(base[name])} ≠ ${placeholders(text)}")
                    }
                }
                lang.plurals.forEach { (name, items) ->
                    val expected = base.plurals[name]?.values?.firstOrNull()?.let(::placeholders)
                    if (expected == null) add("${lang.qualifier}: $name 는 기본 언어(values)에 없다")
                    else items.forEach { (quantity, text) ->
                        if (placeholders(text) != expected) add("${lang.qualifier}: $name/$quantity 자리표시자 $expected ≠ ${placeholders(text)}")
                    }
                }
            }
        }
    }
}
