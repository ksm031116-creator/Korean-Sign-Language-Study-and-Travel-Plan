package app.signkorean.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 설계서 §9.6 아키텍처 규칙. 위반하면 빌드(테스트)가 실패한다.
 *
 * 각 규칙은 검사 대상 파일이 실제로 있는지 먼저 확인한다. 대상이 비어 있으면 규칙이
 * 아무것도 검사하지 않고 통과해 버리기 때문이다(경로·모듈 이름이 바뀌었을 때 흔한 거짓 통과).
 */
class ArchitectureTest {

    private fun production(module: String): List<KoFileDeclaration> =
        Konsist.scopeFromProduction(moduleName = module).files.toList()
            .also { assertTrue("모듈 '$module' 의 프로덕션 파일을 찾지 못했다 — 규칙이 아무것도 검사하지 않는다", it.isNotEmpty()) }

    private fun KoFileDeclaration.importsStartingWith(vararg prefixes: String) =
        imports.map { it.name }.filter { name -> prefixes.any { name.startsWith(it) } }

    private fun assertNoViolations(rule: String, violations: List<String>) =
        assertEquals("$rule 위반:\n" + violations.joinToString("\n"), emptyList<String>(), violations)

    @Test
    fun `AR-01 domain 은 Android·Koin·Billing 을 import 하지 않는다`() {
        val forbidden = arrayOf("android.", "androidx.", "org.koin.", "com.android.billingclient.", "app.signkorean.adapter", "app.signkorean.ui")
        val violations = production("domain").flatMap { f -> f.importsStartingWith(*forbidden).map { "${f.path}: $it" } }
        assertNoViolations("AR-01", violations)
    }

    @Test
    fun `AR-02 ui 패키지는 adapter 를 import 하지 않는다 - 화면은 유스케이스만 안다`() {
        val uiFiles = production("app").filter { it.packagee?.name?.startsWith("app.signkorean.ui") == true }
        assertTrue("ui 패키지 파일이 없다", uiFiles.isNotEmpty())
        val violations = uiFiles.flatMap { f -> f.importsStartingWith("app.signkorean.adapter").map { "${f.path}: $it" } }
        assertNoViolations("AR-02", violations)
    }

    @Test
    fun `AR-03 Koin 은 app 의 조립부에서만 쓰고 KoinComponent 는 어디서도 쓰지 않는다`() {
        val outsideApp = (production("domain") + production("adapter"))
            .flatMap { f -> f.importsStartingWith("org.koin.").map { "${f.path}: $it" } }
        val serviceLocator = (production("domain") + production("adapter") + production("app"))
            .flatMap { f -> f.importsStartingWith("org.koin.core.component.").map { "${f.path}: $it" } }
        val appOutsideRoot = production("app")
            .filter { f -> f.packagee?.name?.let { it == "app.signkorean.di" || it == "app.signkorean" } != true }
            .flatMap { f -> f.importsStartingWith("org.koin.").filterNot { it.startsWith("org.koin.androidx.compose.") }.map { "${f.path}: $it" } }
        assertNoViolations("AR-03", outsideApp + serviceLocator + appOutsideRoot)
    }

    @Test
    fun `AR-04 프로덕션 코드는 테스트용 가짜를 참조하지 않는다`() {
        val violations = (production("domain") + production("adapter") + production("app")).flatMap { f ->
            val imports = f.importsStartingWith("app.signkorean.testing").map { "${f.path}: import $it" }
            val fakes = Regex("""\bFake[A-Z]\w*""").findAll(f.text).map { "${f.path}: ${it.value}" }.toList()
            imports + fakes
        }
        assertNoViolations("AR-04", violations)
    }

    @Test
    fun `AR-06 화면 코드에 사용자에게 보이는 문구를 직접 쓰지 않는다`() {
        val violations = production("app").flatMap { f -> HardcodedUiText.findIn(f.text).map { "${f.path}: $it" } }
        assertNoViolations("AR-06", violations)
    }
}
