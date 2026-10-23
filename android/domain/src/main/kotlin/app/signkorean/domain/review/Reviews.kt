package app.signkorean.domain.review

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.PlaceId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI
import java.time.Instant

/**
 * 공급자가 준 리뷰 한 건 (FR10). 앱이 번역하지 않는다. [translated] 는 공급자가 적법하게 번역해 준 경우이고,
 * 그때만 [originalText] 가 있다("Translated" 표시와 원문 보기).
 */
data class Review(
    val author: String?,
    val rating: Int?,
    val text: String,
    val writtenAt: Instant?,
    val language: LocaleTag,
    val translated: Boolean,
    val originalText: String?,
)

/** 공급자가 준 리뷰 목록. 전체가 아니라 일부일 수 있다([partial], FR11). 출처 표기는 그대로 보인다. */
data class ReviewPage(val items: List<Review>, val attribution: String, val sourceUrl: String?, val partial: Boolean)

/** 중계 서버 오류 코드 (PRD §10.3) 를 도메인 값으로 옮긴 것. */
enum class ReviewError {
    /** 요청한 언어의 리뷰가 없다. */
    NoData,

    /** 이 장소와 일치하는 공급자 장소가 없다. 다른 장소의 리뷰로 채우지 않는다. */
    NotFound,
    Timeout,
    Network,
    RateLimited,

    /** 서비스가 이 기능을 지원하지 않거나 아직 연결되지 않았다. */
    Unsupported,
}

sealed interface ReviewResponse {
    data class Found(val page: ReviewPage) : ReviewResponse
    data class Error(val error: ReviewError, val sourceUrl: String?) : ReviewResponse
}

/** 리뷰 포트 (설계서 §6.6). 서버는 요청받은 언어만 돌려준다. 대체 언어는 앱이 정한다(§7.5). */
interface ReviewProvider {
    suspend fun reviews(place: PlaceId, language: LocaleTag): ReviewResponse
}

/** 화면이 그리는 리뷰 상태 (U-13). 다섯 가지가 서로 다른 화면이다. */
sealed interface ReviewState {
    /** [fallback] 이면 사용자 언어가 없어 [language](영어)로 보인다. */
    data class Shown(val page: ReviewPage, val language: LocaleTag, val fallback: Boolean) : ReviewState
    data class NoReviews(val sourceUrl: String?) : ReviewState
    data class LanguageUnavailable(val sourceUrl: String?) : ReviewState
    data object PlaceMismatch : ReviewState

    /** 시간 초과·연결 실패·한도 초과. 다시 시도할 수 있다. */
    data object NetworkFailure : ReviewState

    /** 리뷰 서비스에 연결되지 않았다(중계 서버 없음·미지원). 다시 시도해도 같다. */
    data object NotConnected : ReviewState
}

/**
 * 리뷰 불러오기 (설계서 §8.6). 사용자 언어 → 없으면 영어 한 번 더 → 없으면 "언어 없음" + 원문 링크.
 * 기계 번역으로 채우지 않는다(FR10·11).
 */
class LoadReviews(private val provider: ReviewProvider, private val timeoutMs: Long = TIMEOUT_MS) {

    /** 두 번 묻더라도 전체가 [timeoutMs] 안에 끝난다(NFR02, Phase 15 리뷰 RVW-01). 넘기면 다시 시도할 수 있는 실패다. */
    suspend operator fun invoke(place: PlaceId, language: LocaleTag): ReviewState =
        withTimeoutOrNull(timeoutMs) { resolve(place, language) } ?: ReviewState.NetworkFailure

    private suspend fun resolve(place: PlaceId, language: LocaleTag): ReviewState {
        val first = ask(place, language)
        // 사용자 언어가 "없음" 오류로 오든 0건으로 오든 영어로 한 번 더 묻는다(RVW-04).
        val nothingInLanguage = (first is ReviewResponse.Error && first.error == ReviewError.NoData) ||
            (first is ReviewResponse.Found && first.page.items.isEmpty())
        if (nothingInLanguage && language != ENGLISH) {
            val firstSource = (first as? ReviewResponse.Error)?.sourceUrl ?: (first as? ReviewResponse.Found)?.page?.sourceUrl
            return when (val second = ask(place, ENGLISH)) {
                is ReviewResponse.Found ->
                    if (second.page.items.isEmpty()) ReviewState.LanguageUnavailable(second.page.sourceUrl ?: firstSource)
                    else ReviewState.Shown(second.page, ENGLISH, fallback = true)
                is ReviewResponse.Error -> errorState(second, sourceUrl = second.sourceUrl ?: firstSource)
            }
        }
        return when (first) {
            is ReviewResponse.Found -> shown(first.page, language, fallback = false)
            is ReviewResponse.Error -> errorState(first, first.sourceUrl)
        }
    }

    private suspend fun ask(place: PlaceId, language: LocaleTag): ReviewResponse = try {
        provider.reviews(place, language)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        ReviewResponse.Error(ReviewError.Network, sourceUrl = null)
    }

    private fun shown(page: ReviewPage, language: LocaleTag, fallback: Boolean): ReviewState =
        if (page.items.isEmpty()) ReviewState.NoReviews(page.sourceUrl) else ReviewState.Shown(page, language, fallback)

    private fun errorState(e: ReviewResponse.Error, sourceUrl: String?): ReviewState = when (e.error) {
        ReviewError.NoData -> ReviewState.LanguageUnavailable(sourceUrl)
        ReviewError.NotFound -> ReviewState.PlaceMismatch
        ReviewError.Timeout, ReviewError.Network, ReviewError.RateLimited -> ReviewState.NetworkFailure
        ReviewError.Unsupported -> ReviewState.NotConnected
    }

    private companion object {
        val ENGLISH = LocaleTag("en")
        const val TIMEOUT_MS = 8_000L
    }
}

/**
 * 외부 링크 허용 목록 (UX08, U-16). 원문 리뷰·관광 정보·사진 출처·지도 링크만 연다. https 만, 호스트가 목록의
 * 도메인이거나 그 하위 도메인일 때만. 사용자 정보(user@host)가 붙은 주소는 막는다(눈속임).
 */
object ExternalLinks {
    private val ALLOWED = setOf(
        "kakao.com",
        "visitkorea.or.kr",
        "wikimedia.org",
        "wikipedia.org",
        // CC BY 사진의 원본 페이지 (연남동 카페거리).
        "flickr.com",
    )

    fun isAllowed(url: String): Boolean {
        val uri = try {
            URI(url.trim())
        } catch (_: Exception) {
            return false
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return false
        if (uri.rawUserInfo != null) return false
        // 기본 포트(443)만. 같은 도메인의 다른 포트는 다른 서비스일 수 있다(RVW-21).
        if (uri.port != -1 && uri.port != 443) return false
        val host = uri.host?.lowercase() ?: return false
        return ALLOWED.any { host == it || host.endsWith(".$it") }
    }
}
