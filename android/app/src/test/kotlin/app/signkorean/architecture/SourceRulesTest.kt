package app.signkorean.architecture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AR-02·03·04 가 쓰는 소스 판정 도우미의 테스트. */
class SourceRulesTest {

    @Test
    fun `주석을 지우되 문자열 안의 슬래시는 남긴다`() {
        val code = "val a = \"https://x\" // note\n/* block\n FakeClock */ val b = 1 /** doc FakeX */"
        val stripped = SourceRules.stripComments(code)
        assertTrue(stripped.contains("\"https://x\""))
        assertFalse(stripped.contains("note"))
        assertFalse(stripped.contains("FakeClock"))
        assertFalse(stripped.contains("FakeX"))
        assertTrue(stripped.contains("val b = 1"))
    }

    @Test
    fun `전체 이름으로 쓴 참조도 찾는다`() {
        val code = "fun f() = app.signkorean.adapter.platform.SystemTimeClock.now()"
        assertEquals(listOf("app.signkorean.adapter.platform.SystemTimeClock"),
            SourceRules.qualifiedReferences(code, "app.signkorean.adapter"))
    }

    @Test
    fun `주석 안의 전체 이름은 참조가 아니다`() {
        val code = "// see app.signkorean.adapter.platform.SystemTimeClock\nval x = 1"
        assertTrue(SourceRules.qualifiedReferences(SourceRules.stripComments(code), "app.signkorean.adapter").isEmpty())
    }

    @Test
    fun `패키지 접두어는 점 경계까지 맞아야 한다`() {
        assertTrue(SourceRules.inPackage("app.signkorean.ui", "app.signkorean.ui"))
        assertTrue(SourceRules.inPackage("app.signkorean.ui.learn", "app.signkorean.ui"))
        assertFalse(SourceRules.inPackage("app.signkorean.uikit", "app.signkorean.ui"))
        assertFalse(SourceRules.inPackage(null, "app.signkorean.ui"))
    }

    @Test
    fun `Fake 로 시작하는 타입 이름만 가짜로 본다`() {
        assertTrue(SourceRules.isFakeTypeName("FakeBillingGateway"))
        assertFalse(SourceRules.isFakeTypeName("Fake"))
        assertFalse(SourceRules.isFakeTypeName("FakeoutDetector"))
        assertFalse(SourceRules.isFakeTypeName("SystemTimeClock"))
    }
}
