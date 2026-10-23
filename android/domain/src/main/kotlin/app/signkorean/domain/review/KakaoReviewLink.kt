package app.signkorean.domain.review

import app.signkorean.domain.content.Place
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.route.ExternalMapLink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

/**
 * 카카오맵 장소 페이지 찾기 포트. 구현은 중계 서버 `POST /v1/places/kakao-page`. 이름이 정확히 같은 카카오 장소가
 * 없으면 null 이다 — 비슷한 이름의 다른 장소로 대신하지 않는다.
 */
interface PlacePageLookup {
    suspend fun kakaoPlacePage(koName: String, at: Coordinate): String?
}

/** 중계 서버가 없는 빌드. 늘 검색 링크로 간다. */
object NoPlacePageLookup : PlacePageLookup {
    override suspend fun kakaoPlacePage(koName: String, at: Coordinate): String? = null
}

/**
 * "카카오맵에서 리뷰 보기" (리뷰 공급자 대신). 카카오·네이버 모두 리뷰를 주는 공식 API 가 없어 앱 안에 리뷰를 보이지
 * 않고, 카카오맵의 장소 페이지로 보낸다. 찾지 못하거나 늦으면 한국어 이름으로 카카오맵 검색을 연다(사용자가 고른다).
 * 예시 장소(isExample)는 실제 장소가 아니므로 링크가 없다(null).
 */
class KakaoReviewLink(private val lookup: PlacePageLookup, private val timeoutMs: Long = TIMEOUT_MS) {

    suspend operator fun invoke(place: Place): String? {
        if (place.isExample) return null
        val found = try {
            withTimeoutOrNull(timeoutMs) { lookup.kakaoPlacePage(place.koName, place.coordinate) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        return found?.takeIf(::isKakaoPlacePage) ?: ExternalMapLink.search(place.koName)
    }

    /** `https://place.map.kakao.com/<숫자>` 만. 서버가 무엇을 주든 허용 목록·모양을 다시 본다. */
    private fun isKakaoPlacePage(url: String): Boolean {
        if (!ExternalLinks.isAllowed(url)) return false
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.host.equals("place.map.kakao.com", ignoreCase = true) && PAGE_PATH.matches(uri.rawPath.orEmpty()) && uri.rawQuery == null
    }

    private companion object {
        val PAGE_PATH = Regex("""/\d+""")
        const val TIMEOUT_MS = 5_000L
    }
}
