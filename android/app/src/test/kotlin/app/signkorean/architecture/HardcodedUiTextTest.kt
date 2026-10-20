package app.signkorean.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** AR-06 규칙 자체의 테스트. 규칙이 잡아야 할 것과 잡으면 안 되는 것을 고정한다. */
class HardcodedUiTextTest {

    @Test
    fun `Text 에 직접 쓴 문구를 잡는다`() {
        assertEquals(1, HardcodedUiText.findIn("""Text("Start practice")""").size)
        assertEquals(1, HardcodedUiText.findIn("""Text(text = "Saved.")""").size)
    }

    @Test
    fun `일본어와 한국어 문구도 잡는다`() {
        assertEquals(1, HardcodedUiText.findIn("""Text("保存しました")""").size)
        assertEquals(1, HardcodedUiText.findIn("""Button(onClick = {}) { Text("알아요") }""").size)
    }

    @Test
    fun `contentDescription 과 label 에 직접 쓴 문구를 잡는다 - 스크린리더도 번역 대상이다`() {
        assertEquals(1, HardcodedUiText.findIn("""Icon(icon, contentDescription = "Save sign")""").size)
        assertEquals(1, HardcodedUiText.findIn("""OutlinedTextField(label = "Trip title")""").size)
    }

    @Test
    fun `리소스로 가져온 문구는 통과한다`() {
        assertTrue(HardcodedUiText.findIn("""Text(stringResource(R.string.start_practice))""").isEmpty())
        assertTrue(HardcodedUiText.findIn("""Icon(icon, contentDescription = stringResource(R.string.save))""").isEmpty())
    }

    @Test
    fun `글자가 없는 문자열과 의도적으로 표시한 줄은 통과한다`() {
        assertTrue(HardcodedUiText.findIn("""Text("")""").isEmpty())
        assertTrue(HardcodedUiText.findIn("""Text(" · ")""").isEmpty())
        assertTrue(HardcodedUiText.findIn("""Text("✓")""").isEmpty())
        assertTrue(HardcodedUiText.findIn("""Text("Sign Korean") // i18n-ignore: 상표명""").isEmpty())
    }

    @Test
    fun `여러 줄 중 위반한 줄만 보고한다`() {
        val source = """
            Column {
                Text(stringResource(R.string.ok))
                Text("Oops")
            }
        """.trimIndent()
        assertEquals(listOf("""Text("Oops")"""), HardcodedUiText.findIn(source))
    }
}
