package app.signkorean.l10n

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * res 의 values 폴더들(strings.xml)을 직접 읽는 테스트 도우미. Android 없이 실제 리소스 문구로 형식·자리표시자를 검사한다.
 * 언어 폴더는 코드에 적지 않고 디렉터리에서 찾는다(L10N-08: 새 언어를 코드 변경 없이 추가).
 */
class ResourceStrings private constructor(
    val qualifier: String,
    val strings: Map<String, String>,
    val plurals: Map<String, Map<String, String>>,
    val untranslatable: Set<String>,
) {
    operator fun get(name: String): String = strings[name] ?: error("$qualifier 에 문자열 '$name' 이 없다")

    companion object {
        val MAIN_RES = File("src/main/res")

        /** values(기본) 와 values-xx 폴더 전부. 의사 언어나 화면 크기 한정자 폴더는 뺀다. */
        fun all(resDir: File = MAIN_RES): List<ResourceStrings> =
            resDir.listFiles().orEmpty()
                .filter { it.isDirectory && (it.name == "values" || LANGUAGE_DIR.matches(it.name)) }
                .sortedBy { it.name }
                .map { load(it) }

        fun load(dir: File): ResourceStrings {
            val strings = mutableMapOf<String, String>()
            val plurals = mutableMapOf<String, Map<String, String>>()
            val untranslatable = mutableSetOf<String>()
            dir.listFiles { f -> f.extension == "xml" }.orEmpty().forEach { file ->
                val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
                val root = doc.documentElement
                if (root.tagName != "resources") return@forEach
                root.elements("string").forEach {
                    val name = it.getAttribute("name")
                    strings[name] = unescape(it.textContent)
                    if (it.getAttribute("translatable") == "false") untranslatable += name
                }
                root.elements("plurals").forEach { p ->
                    plurals[p.getAttribute("name")] = p.elements("item").associate { it.getAttribute("quantity") to unescape(it.textContent) }
                }
            }
            return ResourceStrings(dir.name.removePrefix("values").removePrefix("-").ifEmpty { "default" }, strings, plurals, untranslatable)
        }

        private fun Element.elements(tag: String): List<Element> {
            val nodes = getElementsByTagName(tag)
            return (0 until nodes.length).map { nodes.item(it) as Element }
        }

        /** Android 문자열 리소스의 이스케이프(\' \" \n)와 감싼 따옴표를 푼다. */
        private fun unescape(raw: String): String {
            val trimmed = raw.trim().let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1) else it }
            return trimmed.replace("\\'", "'").replace("\\\"", "\"").replace("\\n", "\n")
        }

        private val LANGUAGE_DIR = Regex("values-[a-z]{2,3}(-r[A-Z]{2})?")
    }
}
