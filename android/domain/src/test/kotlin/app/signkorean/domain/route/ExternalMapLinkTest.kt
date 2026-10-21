package app.signkorean.domain.route

import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.review.ExternalLinks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/** SC18 "외부 지도 열기" (구현 계획 Phase 19 D2). 허용 목록 안의 주소만 만든다. */
class ExternalMapLinkTest {

    private val from = Coordinate(37.5547, 126.9707)
    private val to = Coordinate(37.5796, 126.9770)

    @Test
    fun `도보 길찾기 링크는 카카오맵이고 허용 목록을 통과한다`() {
        val url = ExternalMapLink.walk("Seoul Station", from, "Gyeongbokgung", to)
        // 도보 길찾기로 연다(FR14). /link/from/../to/.. 는 기본 수단(대개 자동차)으로 열린다.
        assertTrue(url.startsWith("https://map.kakao.com/link/by/walk/"), url)
        assertTrue(ExternalLinks.isAllowed(url))
        assertTrue("37.554700,126.970700" in url && "37.579600,126.977000" in url)
    }

    @Test
    fun `이름의 쉼표·슬래시는 인코딩해서 좌표 자리를 바꾸지 못한다`() {
        val url = ExternalMapLink.walk("A,1/2", from, "경복궁", to)
        assertFalse("A,1/2" in url)
        assertEquals(2, url.split("/link/")[1].split("/").size - 2, url) // from/<..>/to/<..>
        assertTrue(url.contains("%EA%B2%BD%EB%B3%B5%EA%B6%81"))
    }

    @Test
    fun `좌표는 기기 언어와 관계없이 점으로 쓴다`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertTrue("37.554700,126.970700" in ExternalMapLink.walk("a", from, "b", to))
            assertTrue("37.579600,126.977000" in ExternalMapLink.place("b", to))
        } finally {
            Locale.setDefault(before)
        }
    }
}
