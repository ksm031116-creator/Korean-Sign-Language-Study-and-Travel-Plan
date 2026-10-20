package app.signkorean.ui.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 글자 대비 4.5:1 이상, 판정 색은 밝기로도 구별 (설계서 §5.4, NFR04). */
class ContrastTest {

    private val c = SignKoreanTokens.Light

    private fun assertContrast(fg: Color, bg: Color, min: Double, label: String) {
        val ratio = Contrast.ratio(fg, bg)
        assertTrue("$label 대비 ${"%.2f".format(ratio)} < $min", ratio >= min)
    }

    @Test
    fun `대비 계산은 WCAG 정의를 따른다`() {
        assertEquals(21.0, Contrast.ratio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, Contrast.ratio(Color.White, Color.White), 0.001)
    }

    @Test
    fun `본문·보조 글자는 바탕과 카드 위에서 4·5 이상`() {
        listOf(c.paper to "paper", c.surface to "surface").forEach { (bg, name) ->
            assertContrast(c.ink, bg, 4.5, "ink/$name")
            assertContrast(c.muted, bg, 4.5, "muted/$name")
            assertContrast(c.primary, bg, 4.5, "primary/$name")
        }
    }

    @Test
    fun `채운 버튼의 흰 글자는 4·5 이상`() {
        assertContrast(Color.White, c.primary, 4.5, "white/primary")
        assertContrast(Color.White, c.know, 4.5, "white/know")
        assertContrast(Color.White, c.danger, 4.5, "white/danger")
    }

    @Test
    fun `연한 바탕의 진한 글자는 4·5 이상`() {
        assertContrast(c.knowInk, c.knowSoft, 4.5, "knowInk/knowSoft")
        assertContrast(c.accentInk, c.accentSoft, 4.5, "accentInk/accentSoft")
        assertContrast(c.primary, c.primarySoft, 4.5, "primary/primarySoft")
        assertContrast(c.danger, c.dangerSoft, 4.5, "danger/dangerSoft")
    }

    @Test
    fun `알아요와 몰라요 버튼은 색이 아니라 밝기로도 3 이상 차이 난다`() {
        assertContrast(c.know, c.accentSoft, 3.0, "know/accentSoft")
    }

    @Test
    fun `토큰 값은 디자인 시스템 표와 같다`() {
        assertEquals(Color(0xFF2B4C7E), c.primary)
        assertEquals(Color(0xFF1E7A4F), c.know)
        assertEquals(Color(0xFFC4661F), c.accent)
        assertEquals(Color(0xFFA8460F), c.accentInk)
        assertEquals(Color(0xFFF6F7F9), c.paper)
    }
}
