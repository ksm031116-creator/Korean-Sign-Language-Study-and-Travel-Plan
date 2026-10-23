package app.signkorean.architecture

/**
 * AR-06: 화면 코드에 사용자에게 보이는 문구를 직접 쓴 곳을 찾는다.
 *
 * Android Lint 의 HardcodedText 는 XML 레이아웃만 검사하고 Compose 코드는 보지 않는다. 그래서
 * 소스를 읽는 규칙으로 대신한다. 한 줄씩 보지 않고 파일 전체에서 호출 단위로 본다(여러 줄 호출,
 * raw 문자열, 조건식 포함). 주석은 지우고 본다.
 *
 * 위반: 글자(라틴·한글·한글 자모·가나·한자)가 든 문자열 리터럴이
 *  - `Text(` / `BasicText(` 의 첫 번째 위치 인자나 `text =` 인자에 있을 때
 *  - `label =`, `contentDescription =`, `placeholder =` 에 바로 올 때
 *  - 스크린리더가 읽는 `stateDescription =`·`contentDescription =` 값(조건식 포함, 그 줄 끝까지)에 있을 때
 *  - `makeText(` 의 두 번째 인자(토스트 문구)에 있을 때
 * 템플릿(`$count`, `${item.price}`)만 있는 문자열은 값이므로 통과한다.
 * 상표명처럼 의도한 예외는 그 줄에 `// i18n-ignore` 를 붙인다.
 */
object HardcodedUiText {

    private val letter = Regex("""[A-Za-zㄱ-ㆎ가-힣぀-ヿ一-鿿]""")
    private val template = Regex("""\$\{[^}]*\}|\$[A-Za-z_]\w*""")
    private const val LITERAL = "(\"\"\"[\\s\\S]*?\"\"\"|\"(?:[^\"\\\\]|\\\\.)*\")"
    private val namedUiArgument = Regex("""\b(?:label|contentDescription|placeholder)\s*=\s*$LITERAL""")
    private val spokenProperty = Regex("""\b(?:stateDescription|contentDescription)\s*=(?!=)\s*([^\n]*)""")
    private val textCall = Regex("""\b(?:BasicText|Text)\(""")
    private val toastCall = Regex("""\bmakeText\(""")
    private val namedArgument = Regex("""^([A-Za-z_]\w*)\s*=(?!=)""")

    fun findIn(source: String): List<String> {
        val kept = source.lines().joinToString("\n") { if ("// i18n-ignore" in it) "" else it }
        val code = SourceRules.stripComments(kept)
        val hits = sortedMapOf<Int, String>()

        textCall.findAll(code).forEach { call ->
            val args = SourceRules.callArguments(code, call.range.last)
            // 화면에 보이는 인자: 첫 번째 위치 인자, 또는 이름이 text 인 인자. 그 값(= 뒤)만 본다.
            val displayed = args.mapIndexedNotNull { index, arg ->
                val name = namedArgument.find(arg)?.groupValues?.get(1)
                when {
                    name == "text" -> arg.substringAfter("=")
                    name == null && index == 0 -> arg
                    else -> null
                }
            }
            if (displayed.any(::hasWording)) hits[call.range.first] = snippet(code, call.range.first)
        }
        toastCall.findAll(code).forEach { call ->
            val args = SourceRules.callArguments(code, call.range.last)
            if (args.size >= 2 && hasWording(args[1])) hits[call.range.first] = snippet(code, call.range.first)
        }
        namedUiArgument.findAll(code).forEach { match ->
            if (literalHasWording(match.groupValues[1])) hits[match.range.first] = snippet(code, lineStart(code, match.range.first))
        }
        spokenProperty.findAll(code).forEach { match ->
            if (hasWording(match.groupValues[1])) hits[match.range.first] = snippet(code, lineStart(code, match.range.first))
        }
        return hits.values.toList()
    }

    private fun hasWording(expression: String): Boolean =
        SourceRules.stringLiterals(SourceRules.withoutNestedCalls(expression)).any { letter.containsMatchIn(template.replace(it, "")) }

    private fun literalHasWording(literal: String): Boolean =
        SourceRules.stringLiterals(literal).any { letter.containsMatchIn(template.replace(it, "")) }

    private fun lineStart(code: String, index: Int): Int = code.lastIndexOf('\n', index - 1) + 1

    private fun snippet(code: String, from: Int): String {
        val start = lineStart(code, from)
        val end = code.indexOf('\n', from).let { if (it < 0) code.length else it }
        return code.substring(start, end).trim()
    }
}
