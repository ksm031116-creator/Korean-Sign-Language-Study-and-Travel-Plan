package app.signkorean.architecture

/**
 * AR-06: 화면 코드에 사용자에게 보이는 문구를 직접 쓴 줄을 찾는다.
 *
 * Android Lint 의 HardcodedText 는 XML 레이아웃만 검사하고 Compose 코드는 보지 않는다.
 * 그래서 소스 텍스트를 읽는 가벼운 규칙으로 대신한다. 글자(라틴·한글·가나·한자)가 들어 있는
 * 문자열 리터럴이 Text(...) 의 첫 인자이거나 text/label/contentDescription/placeholder 로 넘어가면 위반이다.
 * 상표명처럼 의도한 예외는 그 줄에 `// i18n-ignore` 를 붙인다.
 */
object HardcodedUiText {

    private const val LETTER = """[A-Za-z가-힣぀-ヿ一-鿿]"""
    private const val STRING_WITH_LETTER = """"(?:[^"\\]|\\.)*$LETTER(?:[^"\\]|\\.)*""""

    private val patterns = listOf(
        Regex("""\bText\(\s*$STRING_WITH_LETTER"""),
        Regex("""\b(?:text|label|contentDescription|placeholder)\s*=\s*$STRING_WITH_LETTER"""),
    )

    fun findIn(source: String): List<String> =
        source.lines()
            .filterNot { it.contains("// i18n-ignore") }
            .filter { line -> patterns.any { it.containsMatchIn(line) } }
            .map { it.trim() }
}
