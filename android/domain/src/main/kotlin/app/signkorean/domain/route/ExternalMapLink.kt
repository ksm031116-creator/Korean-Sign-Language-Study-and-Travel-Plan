package app.signkorean.domain.route

import app.signkorean.domain.location.Coordinate
import java.net.URLEncoder
import java.util.Locale

/**
 * SC18 "외부 지도 열기" 주소 (구현 계획 Phase 19 D2). 카카오맵 링크만 만든다 — [app.signkorean.domain.review.ExternalLinks]
 * 허용 목록 안이다. 이름은 인코딩해서 쉼표·슬래시가 좌표 자리를 바꾸지 못한다.
 */
object ExternalMapLink {
    private const val BASE = "https://map.kakao.com/link"

    /** 도보 길찾기(FR14). `/link/from/../to/..` 는 앱의 기본 수단(대개 자동차)으로 열리므로 쓰지 않는다. */
    fun walk(fromName: String, from: Coordinate, toName: String, to: Coordinate): String =
        "$BASE/by/walk/${point(fromName, from)}/${point(toName, to)}"

    fun place(name: String, at: Coordinate): String = "$BASE/map/${point(name, at)}"

    private fun point(name: String, c: Coordinate): String =
        encode(name) + "," + String.format(Locale.ROOT, "%.6f,%.6f", c.lat, c.lng)

    private fun encode(s: String): String = URLEncoder.encode(s, Charsets.UTF_8).replace("+", "%20")
}
