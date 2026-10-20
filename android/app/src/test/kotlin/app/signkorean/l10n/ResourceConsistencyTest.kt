package app.signkorean.l10n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 언어별 리소스 검사 (설계서 §8.8). L10N-08·12 */
class ResourceConsistencyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `ja 리소스가 있고 기본 언어와 함께 검사 대상이다`() {
        assertEquals(listOf("default", "ja"), ResourceStrings.all().map { it.qualifier })
    }

    @Test
    fun `L10N-12 모든 키의 자리표시자가 언어마다 같다`() {
        assertEquals(emptyList<String>(), ResourceCheck.problems(ResourceStrings.all()))
    }

    @Test
    fun `자리표시자가 다르면 잡는다`() {
        val res = resDir(
            "values" to """<string name="quota">%1${'$'}d of %2${'$'}d used</string>""",
            "values-ja" to """<string name="quota">%1${'$'}d 件使用中</string>""",
        )
        assertEquals(listOf("ja: quota 자리표시자 [%1\$d, %2\$d] ≠ [%1\$d]"), ResourceCheck.problems(ResourceStrings.all(res)))
    }

    @Test
    fun `기본 언어에 없는 키가 번역에만 있으면 잡는다`() {
        val res = resDir("values" to """<string name="a">A</string>""", "values-ja" to """<string name="a">あ</string><string name="b">い</string>""")
        assertEquals(listOf("ja: b 는 기본 언어(values)에 없다"), ResourceCheck.problems(ResourceStrings.all(res)))
    }

    @Test
    fun `번역하지 않는 키가 번역 폴더에 있으면 잡는다`() {
        val res = resDir("values" to """<string name="app_name" translatable="false">Sign Korean</string>""", "values-ja" to """<string name="app_name">サイン</string>""")
        assertEquals(listOf("ja: app_name 은 translatable=false 다"), ResourceCheck.problems(ResourceStrings.all(res)))
    }

    @Test
    fun `L10N-08 리소스만 추가한 가짜 언어 xx 도 코드 변경 없이 같은 검사를 받는다`() {
        val res = resDir(
            "values" to """<string name="hello">Hi %1${'$'}s</string>""",
            "values-ja" to """<string name="hello">%1${'$'}s さん</string>""",
            "values-xx" to """<string name="hello">Xx %1${'$'}s</string>""",
        )
        val all = ResourceStrings.all(res)
        assertEquals(listOf("default", "ja", "xx"), all.map { it.qualifier })
        assertTrue(ResourceCheck.problems(all).isEmpty())
    }

    private fun resDir(vararg folders: Pair<String, String>): File {
        val root = tmp.newFolder("res")
        folders.forEach { (name, body) ->
            File(root, name).apply { mkdirs() }.resolve("strings.xml").writeText("<resources>$body</resources>")
        }
        return root
    }
}
