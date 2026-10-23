package app.signkorean.domain.review

import app.signkorean.domain.content.AreaId
import app.signkorean.domain.content.Place
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.common.LocalizedText
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.route.ExternalMapLink
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** "카카오맵에서 리뷰 보기" (리뷰 공급자 대신). 이름이 정확히 같은 장소 페이지만 열고, 아니면 카카오맵 검색으로 보낸다. */
class KakaoReviewLinkTest {

    private val at = Coordinate(37.5796, 126.977)
    private fun place(koName: String = "경복궁", example: Boolean = false) = Place(
        id = PlaceId("p"), areaId = AreaId("a"), typeId = "palace", koName = koName,
        name = LocalizedText(mapOf(LocaleTag("en") to "Gyeongbokgung Palace")), address = LocalizedText(mapOf(LocaleTag("en") to "Seoul")),
        coordinate = at, signWords = null, photo = null, isExample = example,
    )

    private class Lookup(val answer: suspend () -> String?) : PlacePageLookup {
        val asked = mutableListOf<Pair<String, Coordinate>>()
        override suspend fun kakaoPlacePage(koName: String, at: Coordinate): String? {
            asked += koName to at
            return answer()
        }
    }

    @Test
    fun `이름이 같은 카카오 장소가 있으면 그 장소 페이지를 연다`() = runTest {
        val lookup = Lookup { "https://place.map.kakao.com/18619553" }
        assertEquals("https://place.map.kakao.com/18619553", KakaoReviewLink(lookup)(place()))
        assertEquals(listOf("경복궁" to at), lookup.asked)
    }

    @Test
    fun `찾지 못하면 한국어 이름으로 카카오맵 검색을 연다(엉뚱한 장소로 보내지 않는다)`() = runTest {
        val url = KakaoReviewLink(Lookup { null })(place("을지로 인쇄골목"))!!
        assertEquals("https://map.kakao.com/link/search/%EC%9D%84%EC%A7%80%EB%A1%9C%20%EC%9D%B8%EC%87%84%EA%B3%A8%EB%AA%A9", url)
        assertTrue(ExternalLinks.isAllowed(url))
    }

    @Test
    fun `찾기가 실패하거나 허용 목록 밖 주소를 주면 검색으로`() = runTest {
        val search = ExternalMapLink.search("경복궁")
        assertEquals(search, KakaoReviewLink(Lookup { throw java.io.IOException("down") })(place()))
        assertEquals(search, KakaoReviewLink(Lookup { "https://evil.example.com/1" })(place()))
        assertEquals(search, KakaoReviewLink(Lookup { "http://place.map.kakao.com/1" })(place()))
    }

    @Test
    fun `찾기가 늦으면 기다리지 않고 검색으로`() = runTest {
        assertEquals(ExternalMapLink.search("경복궁"), KakaoReviewLink(Lookup { awaitCancellation() }, timeoutMs = 1_000)(place()))
    }

    @Test
    fun `예시 장소는 실제 장소가 아니므로 링크가 없다`() = runTest {
        val lookup = Lookup { "https://place.map.kakao.com/1" }
        assertNull(KakaoReviewLink(lookup)(place("서촌 약국", example = true)))
        assertTrue(lookup.asked.isEmpty())
    }
}
