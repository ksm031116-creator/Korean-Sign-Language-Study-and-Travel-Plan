package app.signkorean.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 설계서 §9.6 아키텍처 규칙. 위반하면 빌드(테스트)가 실패한다.
 *
 * - 각 규칙은 검사 대상 파일이 실제로 있는지 먼저 확인한다. 대상이 비어 있으면 규칙이
 *   아무것도 검사하지 않고 통과해 버리기 때문이다(경로·모듈 이름이 바뀌었을 때 흔한 거짓 통과).
 * - import 줄만 보지 않는다. 본문에 전체 이름으로 쓴 참조(`app.signkorean.adapter.X.y()`)도 위반이다.
 * - 주석 안의 이름은 참조가 아니므로 주석을 지우고 본다.
 */
class ArchitectureTest {

    private fun production(module: String): List<KoFileDeclaration> =
        Konsist.scopeFromProduction(moduleName = module).files.toList()
            .also { assertTrue("모듈 '$module' 의 프로덕션 파일을 찾지 못했다 — 규칙이 아무것도 검사하지 않는다", it.isNotEmpty()) }

    /** 릴리스에 들어가는 main 소스 세트만. 디버그 전용(src/debug)은 가짜를 쓸 수 있다 (설계서 §5.5). */
    private fun releaseSources(module: String) = production(module).filter { it.sourceSetName == "main" }
        .also { assertTrue("모듈 '$module' 의 main 소스를 찾지 못했다", it.isNotEmpty()) }

    private val KoFileDeclaration.code get() = SourceRules.stripComments(text)

    /** import 와 본문 전체 이름 참조 중 `prefix` 로 시작하는 것. */
    private fun KoFileDeclaration.references(prefix: String): List<String> =
        imports.map { it.name }.filter { it == prefix || it.startsWith("$prefix.") } +
            SourceRules.qualifiedReferences(code, prefix).filterNot { ref -> imports.any { it.name == ref } }

    private fun assertNoViolations(rule: String, violations: List<String>) =
        assertEquals("$rule 위반:\n" + violations.joinToString("\n"), emptyList<String>(), violations)

    @Test
    fun `AR-01 domain 은 Android·Koin·Billing·바깥 레이어를 참조하지 않는다`() {
        val forbidden = listOf("android", "androidx", "org.koin", "com.android.billingclient", "app.signkorean.adapter", "app.signkorean.ui", "app.signkorean.di")
        val violations = production("domain").flatMap { f -> forbidden.flatMap { f.references(it) }.map { "${f.path}: $it" } }
        assertNoViolations("AR-01", violations)
    }

    @Test
    fun `AR-02 ui 패키지는 adapter 를 참조하지 않는다 - 화면은 유스케이스만 안다`() {
        val uiFiles = production("app").filter { SourceRules.inPackage(it.packagee?.name, "app.signkorean.ui") }
        assertTrue("ui 패키지 파일이 없다", uiFiles.isNotEmpty())
        val violations = uiFiles.flatMap { f -> f.references("app.signkorean.adapter").map { "${f.path}: $it" } }
        assertNoViolations("AR-02", violations)
    }

    @Test
    fun `AR-03 Koin 은 조립부에서만 쓰고 KoinComponent 는 어디서도 쓰지 않는다`() {
        val outsideApp = (production("domain") + production("adapter"))
            .flatMap { f -> f.references("org.koin").map { "${f.path}: $it" } }
        val serviceLocator = (production("domain") + production("adapter") + production("app"))
            .flatMap { f -> f.references("org.koin.core.component").map { "${f.path}: $it" } }
        // 조립부: Application 클래스 파일과 di 패키지. 화면은 ViewModel 을 받는 koinViewModel 만 쓸 수 있다.
        val compositionRoot = { f: KoFileDeclaration ->
            SourceRules.inPackage(f.packagee?.name, "app.signkorean.di") || f.name == "SignKoreanApplication"
        }
        // 화면은 ViewModel 을 받기만 한다. 화면 인자(예: SC03 의 출처)를 넘기는 parametersOf 도 같은 받기의 일부다.
        val allowedInScreens = setOf(
            "org.koin.androidx.compose.koinViewModel",
            "org.koin.compose.viewmodel.koinViewModel",
            "org.koin.core.parameter.parametersOf",
        )
        val appOutsideRoot = production("app").filterNot(compositionRoot)
            .flatMap { f -> f.references("org.koin").filterNot { it in allowedInScreens }.map { "${f.path}: $it" } }
        assertNoViolations("AR-03", outsideApp + serviceLocator + appOutsideRoot)
    }

    @Test
    fun `AR-04 릴리스 코드는 테스트용 가짜를 참조하지 않는다`() {
        // 조립 결과 검사는 ReleaseGraphTest. 여기서는 릴리스 소스에 가짜 타입 이름이 쓰였는지 본다(주석 제외).
        val fakeName = Regex("""\bFake[A-Z]\w*""")
        val violations = (releaseSources("domain") + releaseSources("adapter") + releaseSources("app")).flatMap { f ->
            f.references("app.signkorean.testing").map { "${f.path}: $it" } +
                fakeName.findAll(f.code).map { "${f.path}: ${it.value}" }.toList()
        }
        assertNoViolations("AR-04", violations)
    }

    @Test
    fun `AR-06 화면 코드에 사용자에게 보이는 문구를 직접 쓰지 않는다`() {
        val violations = production("app").flatMap { f -> HardcodedUiText.findIn(f.text).map { "${f.path}: $it" } }
        assertNoViolations("AR-06", violations)
    }
}
