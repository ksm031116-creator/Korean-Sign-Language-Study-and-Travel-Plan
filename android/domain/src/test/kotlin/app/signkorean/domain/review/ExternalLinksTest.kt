package app.signkorean.domain.review

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 외부 링크는 허용 목록의 https 주소만 연다 (UX08). U-16 */
class ExternalLinksTest {

    @Test
    fun `U-16 허용한 공급자의 https 주소만 연다`() {
        assertTrue(ExternalLinks.isAllowed("https://place.map.kakao.com/12345"))
        assertTrue(ExternalLinks.isAllowed("https://map.kakao.com/link/to/x,37.5,127.0"))
        assertTrue(ExternalLinks.isAllowed("https://korean.visitkorea.or.kr/detail/ms_detail.do?cotid=1"))
        assertTrue(ExternalLinks.isAllowed("https://commons.wikimedia.org/wiki/File:A.jpg"))
    }

    @Test
    fun `U-16 http, 다른 도메인, 비슷하게 꾸민 도메인, 다른 scheme 은 막는다`() {
        assertFalse(ExternalLinks.isAllowed("http://place.map.kakao.com/1"))
        assertFalse(ExternalLinks.isAllowed("https://evil.example.com/kakao.com"))
        assertFalse(ExternalLinks.isAllowed("https://place.map.kakao.com.evil.io/1"))
        assertFalse(ExternalLinks.isAllowed("https://notkakao.com/1"))
        assertFalse(ExternalLinks.isAllowed("https://user@evil.io/place.map.kakao.com"))
        assertFalse(ExternalLinks.isAllowed("javascript:alert(1)"))
        assertFalse(ExternalLinks.isAllowed("intent://scan#Intent;scheme=zxing;end"))
        assertFalse(ExternalLinks.isAllowed(""))
        assertFalse(ExternalLinks.isAllowed("not a url"))
    }

    @Test
    fun `RVW-21 대문자·포트·IPv6·백슬래시·호스트 없는 주소·IDN 회귀`() {
        assertTrue(ExternalLinks.isAllowed("HTTPS://Place.Map.Kakao.com/1"))
        assertFalse(ExternalLinks.isAllowed("https://place.map.kakao.com:8443/1"), "기본 포트가 아니면 막는다")
        assertFalse(ExternalLinks.isAllowed("https://[::1]/kakao.com"))
        assertFalse(ExternalLinks.isAllowed("https://evil.io\\@place.map.kakao.com/"))
        assertFalse(ExternalLinks.isAllowed("https:place.map.kakao.com"))
        assertFalse(ExternalLinks.isAllowed("https:///place.map.kakao.com"))
        assertFalse(ExternalLinks.isAllowed("https://kаkao.com/1"), "키릴 문자 а 로 꾸민 주소")
    }
}
