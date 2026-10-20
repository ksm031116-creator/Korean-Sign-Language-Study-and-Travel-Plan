package app.signkorean.architecture

/**
 * 아키텍처 규칙이 쓰는 Kotlin 소스 판정 도우미.
 * 완전한 파서는 아니지만 문자열·raw 문자열·주석·괄호 중첩을 구별해서, import 줄만 보거나
 * 한 줄씩 정규식을 거는 방식이 놓치던 경우(전체 이름 참조, 여러 줄 호출, 주석 속 예시)를 다룬다.
 */
object SourceRules {

    /**
     * 주석을 공백으로 바꾼다. 문자열·raw 문자열·문자 리터럴 안의 주석 기호는 그대로 둔다. 줄 수는 유지한다.
     * (Kotlin 은 블록 주석이 중첩되므로 이 KDoc 에 블록 주석 시작 기호를 그대로 쓰면 파일이 닫히지 않는다.)
     */
    fun stripComments(code: String): String {
        val out = StringBuilder(code.length)
        var i = 0
        var blockDepth = 0
        while (i < code.length) {
            val c = code[i]
            val next = code.getOrNull(i + 1)
            when {
                blockDepth > 0 -> {
                    when {
                        c == '/' && next == '*' -> { blockDepth++; out.append("  "); i += 2 }
                        c == '*' && next == '/' -> { blockDepth--; out.append("  "); i += 2 }
                        else -> { out.append(if (c == '\n') '\n' else ' '); i++ }
                    }
                }
                c == '/' && next == '/' -> {
                    while (i < code.length && code[i] != '\n') { out.append(' '); i++ }
                }
                c == '/' && next == '*' -> { blockDepth = 1; out.append("  "); i += 2 }
                code.startsWith("\"\"\"", i) -> {
                    val end = code.indexOf("\"\"\"", i + 3).let { if (it < 0) code.length else it + 3 }
                    out.append(code, i, end); i = end
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < code.length && code[j] != c && code[j] != '\n') { if (code[j] == '\\') j++; j++ }
                    val end = minOf(j + 1, code.length)
                    out.append(code, i, end); i = end
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /** `prefix` 로 시작하는 전체 이름 참조(예: `app.signkorean.adapter.platform.SystemTimeClock`)를 찾는다. */
    fun qualifiedReferences(code: String, prefix: String): List<String> =
        Regex("""(?<![\w.])${Regex.escape(prefix)}(?:\.[a-z_]\w*)*\.[A-Z]\w*""").findAll(code).map { it.value }.toList()

    /** 패키지가 `root` 이거나 그 하위 패키지인가. `app.signkorean.uikit` 은 `app.signkorean.ui` 의 하위가 아니다. */
    fun inPackage(pkg: String?, root: String): Boolean = pkg != null && (pkg == root || pkg.startsWith("$root."))

    /** 테스트용 가짜 타입 이름인가 (`FakeBillingGateway`). `Fakeout…` 같은 일반 단어는 아니다. */
    fun isFakeTypeName(name: String): Boolean = Regex("""^Fake[A-Z]\w*$""").matches(name)

    /**
     * `code[openParen]` 이 `(` 일 때, 짝이 맞는 `)` 까지의 최상위 인자들을 돌려준다.
     * 문자열과 중첩 괄호 안의 쉼표는 나누지 않는다.
     */
    fun callArguments(code: String, openParen: Int): List<String> {
        require(code[openParen] == '(')
        val args = mutableListOf<String>()
        val current = StringBuilder()
        var depth = 0
        var i = openParen + 1
        while (i < code.length) {
            val c = code[i]
            when {
                code.startsWith("\"\"\"", i) -> {
                    val end = code.indexOf("\"\"\"", i + 3).let { if (it < 0) code.length else it + 3 }
                    current.append(code, i, end); i = end; continue
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < code.length && code[j] != c) { if (code[j] == '\\') j++; j++ }
                    val end = minOf(j + 1, code.length)
                    current.append(code, i, end); i = end; continue
                }
                c in "([{" -> depth++
                c in ")]}" -> {
                    if (depth == 0) { if (current.isNotBlank()) args += current.toString().trim(); return args }
                    depth--
                }
                c == ',' && depth == 0 -> { args += current.toString().trim(); current.clear(); i++; continue }
            }
            current.append(c); i++
        }
        return args
    }

    /** 중첩 괄호 안의 내용을 지운다. `stringResource(R.string.x)` 의 인자는 화면 문구가 아니다. */
    fun withoutNestedCalls(expression: String): String {
        val out = StringBuilder()
        var depth = 0
        var i = 0
        while (i < expression.length) {
            val c = expression[i]
            if (depth == 0 && (expression.startsWith("\"\"\"", i) || c == '"')) {
                val end = literalEnd(expression, i)
                out.append(expression, i, end); i = end; continue
            }
            when (c) {
                '(' -> { if (depth == 0) out.append('('); depth++ }
                ')' -> { depth--; if (depth == 0) out.append(')') }
                else -> if (depth == 0) out.append(c)
            }
            i++
        }
        return out.toString()
    }

    /** 표현식 안의 문자열 리터럴 내용(따옴표 제외)을 순서대로 돌려준다. */
    fun stringLiterals(expression: String): List<String> {
        val found = mutableListOf<String>()
        var i = 0
        while (i < expression.length) {
            if (expression.startsWith("\"\"\"", i) || expression[i] == '"') {
                val end = literalEnd(expression, i)
                val raw = expression.startsWith("\"\"\"", i)
                found += expression.substring(if (raw) i + 3 else i + 1, maxOf(if (raw) end - 3 else end - 1, if (raw) i + 3 else i + 1))
                i = end
            } else i++
        }
        return found
    }

    private fun literalEnd(s: String, start: Int): Int =
        if (s.startsWith("\"\"\"", start)) s.indexOf("\"\"\"", start + 3).let { if (it < 0) s.length else it + 3 }
        else {
            var j = start + 1
            while (j < s.length && s[j] != '"') { if (s[j] == '\\') j++; j++ }
            minOf(j + 1, s.length)
        }
}
