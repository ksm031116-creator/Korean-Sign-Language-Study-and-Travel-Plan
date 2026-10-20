package app.signkorean.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** AR-06 규칙 자체의 테스트. 규칙이 잡아야 할 것과 잡으면 안 되는 것을 고정한다. */
class HardcodedUiTextTest {

    private fun violations(source: String) = HardcodedUiText.findIn(source)

    // ---------- 잡아야 하는 것 ----------

    @Test
    fun `Text 에 직접 쓴 문구를 잡는다`() {
        assertEquals(1, violations("""Text("Start practice")""").size)
        assertEquals(1, violations("""Text(text = "Saved.")""").size)
    }

    @Test
    fun `일본어와 한국어 문구도 잡는다`() {
        assertEquals(1, violations("""Text("保存しました")""").size)
        assertEquals(1, violations("""Button(onClick = {}) { Text("알아요") }""").size)
    }

    @Test
    fun `한글 자모만 있는 문구도 잡는다`() {
        assertEquals(1, violations("""Text("ㅋㅋ")""").size)
    }

    @Test
    fun `contentDescription 과 label 에 직접 쓴 문구를 잡는다 - 스크린리더도 번역 대상이다`() {
        assertEquals(1, violations("""Icon(icon, contentDescription = "Save sign")""").size)
        assertEquals(1, violations("""OutlinedTextField(label = "Trip title")""").size)
    }

    @Test
    fun `문구가 다음 줄에 있어도 잡는다`() {
        val source = "Text(\n    \"Start practice\",\n    style = style,\n)"
        assertEquals(1, violations(source).size)
    }

    @Test
    fun `raw 문자열도 잡는다`() {
        assertEquals(1, violations("Text(\"\"\"Hi there\"\"\")").size)
    }

    @Test
    fun `조건식 안의 문구도 잡는다`() {
        assertEquals(1, violations("""Text(text = if (ok) "Yes" else "No")""").size)
    }

    @Test
    fun `BasicText 와 Toast 문구도 잡는다`() {
        assertEquals(1, violations("""BasicText("Hi")""").size)
        assertEquals(1, violations("""Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()""").size)
    }

    // ---------- 잡으면 안 되는 것 ----------

    @Test
    fun `리소스로 가져온 문구는 통과한다`() {
        assertTrue(violations("""Text(stringResource(R.string.start_practice))""").isEmpty())
        assertTrue(violations("""Icon(icon, contentDescription = stringResource(R.string.save))""").isEmpty())
    }

    @Test
    fun `글자가 없는 문자열과 의도적으로 표시한 줄은 통과한다`() {
        assertTrue(violations("""Text("")""").isEmpty())
        assertTrue(violations("""Text(" · ")""").isEmpty())
        assertTrue(violations("""Text("✓")""").isEmpty())
        assertTrue(violations("""Text("Sign Korean") // i18n-ignore: 상표명""").isEmpty())
    }

    @Test
    fun `값만 넣은 템플릿 문자열은 통과한다`() {
        assertTrue(violations("""Text("${'$'}count")""").isEmpty())
        assertTrue(violations("""Text("${'$'}{item.price}")""").isEmpty())
        assertTrue(violations("""Text("${'$'}done/${'$'}total")""").isEmpty())
    }

    @Test
    fun `템플릿 옆에 붙은 문구는 잡는다`() {
        assertEquals(1, violations("""Text("${'$'}count places")""").size)
    }

    @Test
    fun `주석 안의 예시는 검사하지 않는다`() {
        assertTrue(violations("""// Text("Hello")""").isEmpty())
        assertTrue(violations("/**\n * 예: Text(\"Hello\")\n */").isEmpty())
    }

    @Test
    fun `Text 의 다른 인자에 있는 문자열은 문구가 아니다`() {
        assertTrue(violations("""Text(stringResource(R.string.title), modifier = Modifier.testTag("title"))""").isEmpty())
        assertTrue(violations("""Text(text = stringResource(R.string.ok), fontFamily = FontFamily(Font("ko")))""").isEmpty())
    }

    @Test
    fun `여러 줄 중 위반한 곳만 보고한다`() {
        val source = """
            Column {
                Text(stringResource(R.string.ok))
                Text("Oops")
            }
        """.trimIndent()
        assertEquals(listOf("""Text("Oops")"""), violations(source))
    }
}
