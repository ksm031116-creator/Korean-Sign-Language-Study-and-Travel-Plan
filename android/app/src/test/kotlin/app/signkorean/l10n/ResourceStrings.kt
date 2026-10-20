package app.signkorean.l10n

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * res 의 values 폴더들(strings.xml)을 직접 읽는 테스트 도우미. Android 없이 실제 리소스 문구로 형식·자리표시자를 검사한다.
 * 언어 폴더는 코드에 적지 않고 디렉터리에서 찾는다(L10N-08: 새 언어를 코드 변경 없이 추가).
 */
class ResourceStrings private constructor(
    /** "default"(values), "ja", "zh-Hant" 처럼 BCP 47 로 바꾼 언어. */
    val qualifier: String,
    val strings: Map<String, String>,
    val plurals: Map<String, Map<String, String>>,
    val untranslatable: Set<String>,
) {
    operator fun get(name: String): String = strings[name] ?: error("$qualifier 에 문자열 '$name' 이 없다")

    companion object {
        /** 출시 빌드 리소스. */
        val MAIN_RES = File("src/main/res")

        /**
         * 디버그 빌드에만 들어가는 리소스. 원어민 검수 전 언어(ja, Q-01)는 여기 두어 출시 빌드에 섞이지 않게 한다.
         * 검수가 끝나면 src/main/res 로 옮긴다.
         */
        val DEBUG_RES = File("src/debug/res")

        /** 출시·디버그 리소스를 합친 언어별 문구. 같은 언어 폴더가 두 곳에 있으면 합친다. */
        fun all(vararg resDirs: File = arrayOf(MAIN_RES, DEBUG_RES)): List<ResourceStrings> =
            resDirs.flatMap { dir -> dir.listFiles().orEmpty().mapNotNull { f -> languageOf(f)?.let { it to f } } }
                .groupBy({ it.first }, { it.second })
                .map { (qualifier, dirs) -> load(qualifier, dirs) }
                .sortedBy { if (it.qualifier == "default") "" else it.qualifier }

        /** 한 언어의 문구. 기본 언어에 없는 키는 기본 언어에서 채워 읽는 형식 함수용. */
        fun language(qualifier: String): ResourceStrings = all().single { it.qualifier == qualifier }

        /** values 폴더 이름 → 언어. 언어 폴더가 아니면(night, land, car, sw600dp …) null. */
        fun languageOf(dir: File): String? {
            if (!dir.isDirectory) return null
            if (dir.name == "values") return "default"
            OLD_STYLE.matchEntire(dir.name)?.let { m ->
                val lang = m.groupValues[1]
                if (lang in NON_LANGUAGE) return null
                return lang + (m.groupValues[2].takeIf { it.isNotEmpty() }?.let { "-$it" } ?: "")
            }
            BCP47_STYLE.matchEntire(dir.name)?.let { return it.groupValues[1].replace('+', '-') }
            return null
        }

        private fun load(qualifier: String, dirs: List<File>): ResourceStrings {
            val strings = mutableMapOf<String, String>()
            val plurals = mutableMapOf<String, Map<String, String>>()
            val untranslatable = mutableSetOf<String>()
            dirs.flatMap { it.listFiles { f -> f.extension == "xml" }.orEmpty().toList() }.forEach { file ->
                val root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
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
            return ResourceStrings(qualifier, strings, plurals, untranslatable)
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

        private val OLD_STYLE = Regex("values-([a-z]{2,3})(?:-r([A-Z]{2}))?")
        private val BCP47_STYLE = Regex("values-b\\+([a-z]{2,3}(?:\\+[A-Za-z0-9]{2,8})*)")

        /** 언어처럼 보이는 두세 글자 리소스 한정자. */
        private val NON_LANGUAGE = setOf("car", "tv", "land", "port", "dpi", "v21", "sw", "h", "w")
    }
}
