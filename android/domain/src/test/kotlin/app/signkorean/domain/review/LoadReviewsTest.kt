package app.signkorean.domain.review

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.PlaceId
import app.signkorean.testing.FakeReviewProvider
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.currentTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 리뷰 언어 대체와 예외 상태 (FR10·11, 설계서 §8.6). L10N-10, U-13 의 도메인 쪽, 포트 계약 */
class LoadReviewsTest {

    private val ja = LocaleTag("ja")
    private val en = LocaleTag("en")
    private val place = PlaceId("gwangjang")
    private fun review(text: String, lang: LocaleTag) = Review(author = "Mina", rating = 5, text = text, writtenAt = null, language = lang, translated = false, originalText = null)
    private fun page(vararg items: Review) = ReviewPage(items.toList(), attribution = "Kakao Map", sourceUrl = "https://place.map.kakao.com/1", partial = true)

    private val provider = FakeReviewProvider()
    private val load = LoadReviews(provider)

    @Test
    fun `L10N-10 사용자 언어 리뷰가 있으면 그것을 보인다`() = runTest {
        provider.answers[ja] = ReviewResponse.Found(page(review("おいしい", ja)))
        val r = load(place, ja) as ReviewState.Shown
        assertEquals(ja, r.language)
        assertFalse(r.fallback)
        assertEquals(listOf(place to ja), provider.requests)
    }

    @Test
    fun `L10N-10 사용자 언어가 없으면 영어로 한 번 더 묻고, 영어임을 표시한다`() = runTest {
        provider.answers[ja] = ReviewResponse.Error(ReviewError.NoData, sourceUrl = "https://place.map.kakao.com/1")
        provider.answers[en] = ReviewResponse.Found(page(review("Great!", en)))
        val r = load(place, ja) as ReviewState.Shown
        assertEquals(en, r.language)
        assertTrue(r.fallback)
        assertEquals(listOf(place to ja, place to en), provider.requests)
    }

    @Test
    fun `L10N-10 둘 다 없으면 언어 없음 + 원문 링크, 번역해서 채우지 않는다`() = runTest {
        provider.answers[ja] = ReviewResponse.Error(ReviewError.NoData, sourceUrl = "https://place.map.kakao.com/1")
        provider.answers[en] = ReviewResponse.Error(ReviewError.NoData, sourceUrl = "https://place.map.kakao.com/1")
        assertEquals(ReviewState.LanguageUnavailable("https://place.map.kakao.com/1"), load(place, ja))
    }

    @Test
    fun `영어 사용자는 영어가 없으면 다시 묻지 않는다`() = runTest {
        provider.answers[en] = ReviewResponse.Error(ReviewError.NoData, sourceUrl = null)
        assertEquals(ReviewState.LanguageUnavailable(null), load(place, en))
        assertEquals(1, provider.requests.size)
    }

    @Test
    fun `U-13 네 가지 예외는 서로 다른 상태다`() = runTest {
        provider.answers[en] = ReviewResponse.Found(page())
        val none = load(place, en)
        provider.answers[en] = ReviewResponse.Error(ReviewError.NotFound, sourceUrl = null)
        val mismatch = load(place, en)
        provider.answers[en] = ReviewResponse.Error(ReviewError.Timeout, sourceUrl = null)
        val timeout = load(place, en)
        provider.answers[en] = ReviewResponse.Error(ReviewError.NoData, sourceUrl = null)
        val language = load(place, en)

        assertEquals(ReviewState.NoReviews("https://place.map.kakao.com/1"), none)
        assertEquals(ReviewState.PlaceMismatch, mismatch)
        assertEquals(ReviewState.NetworkFailure, timeout)
        assertEquals(ReviewState.LanguageUnavailable(null), language)
        assertEquals(4, setOf(none, mismatch, timeout, language).map { it::class }.toSet().size)
    }

    @Test
    fun `영어로 다시 물었는데 네트워크가 끊기면 네트워크 실패`() = runTest {
        provider.answers[ja] = ReviewResponse.Error(ReviewError.NoData, sourceUrl = null)
        provider.answers[en] = ReviewResponse.Error(ReviewError.Network, sourceUrl = null)
        assertEquals(ReviewState.NetworkFailure, load(place, ja))
    }

    @Test
    fun `한도 초과는 다시 시도할 수 있는 실패, 미지원은 연결 안 됨`() = runTest {
        provider.answers[en] = ReviewResponse.Error(ReviewError.RateLimited, sourceUrl = null)
        assertEquals(ReviewState.NetworkFailure, load(place, en))
        provider.answers[en] = ReviewResponse.Error(ReviewError.Unsupported, sourceUrl = null)
        assertEquals(ReviewState.NotConnected, load(place, en))
    }

    @Test
    fun `일부 목록 표시와 출처는 공급자 값 그대로`() = runTest {
        provider.answers[en] = ReviewResponse.Found(page(review("Great!", en)))
        val r = load(place, en) as ReviewState.Shown
        assertTrue(r.page.partial)
        assertEquals("Kakao Map", r.page.attribution)
    }

    @Test
    fun `공급자 예외는 네트워크 실패로`() = runTest {
        provider.throwNext = true
        assertEquals(ReviewState.NetworkFailure, load(place, en))
    }

    // ---------- Phase 15 리뷰 수정 ----------

    @Test
    fun `RVW-01 두 번 묻더라도 전체 8초를 넘기면 네트워크 실패 (NFR02)`() = runTest {
        provider.answers[ja] = ReviewResponse.Error(ReviewError.NoData, sourceUrl = null)
        provider.answers[en] = ReviewResponse.Found(page(review("Great!", en)))
        provider.delayMs = 7_000
        assertEquals(ReviewState.NetworkFailure, load(place, ja))
        assertEquals(8_000, currentTime, "8초에 끝낸다")
    }

    @Test
    fun `RVW-04 사용자 언어가 0건으로 와도 영어로 한 번 더 묻는다`() = runTest {
        provider.answers[ja] = ReviewResponse.Found(page())
        provider.answers[en] = ReviewResponse.Found(page(review("Great!", en)))
        val r = load(place, ja) as ReviewState.Shown
        assertTrue(r.fallback)
    }

    @Test
    fun `RVW-04 영어로 다시 물어 0건이면 언어 없음이고, 처음 받은 원문 링크를 지킨다`() = runTest {
        provider.answers[ja] = ReviewResponse.Error(ReviewError.NoData, sourceUrl = "https://place.map.kakao.com/1")
        provider.answers[en] = ReviewResponse.Found(ReviewPage(emptyList(), "Kakao Map", sourceUrl = null, partial = true))
        assertEquals(ReviewState.LanguageUnavailable("https://place.map.kakao.com/1"), load(place, ja))
    }
}
