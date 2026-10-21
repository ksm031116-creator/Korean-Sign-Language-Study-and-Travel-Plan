package app.signkorean.testing

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.review.ReviewError
import app.signkorean.domain.review.ReviewProvider
import app.signkorean.domain.review.ReviewResponse
import kotlinx.coroutines.CompletableDeferred

/**
 * 리뷰 공급자 가짜 (설계서 §6.6). 언어마다 답을 정한다. 정하지 않은 언어는 NoData.
 * 시나리오: 0건(빈 Found), 언어 없음(NoData), 장소 불일치(NotFound), 시간 초과(Timeout), 지연([hold]).
 */
class FakeReviewProvider : ReviewProvider {
    val answers = mutableMapOf<LocaleTag, ReviewResponse>()
    val requests = mutableListOf<Pair<PlaceId, LocaleTag>>()

    @Volatile var throwNext = false
    @Volatile var hold = false
    private var gate = CompletableDeferred<Unit>()

    fun release() = gate.complete(Unit)

    override suspend fun reviews(place: PlaceId, language: LocaleTag): ReviewResponse {
        requests += place to language
        if (hold) {
            gate.await()
            gate = CompletableDeferred()
        }
        if (throwNext) {
            throwNext = false
            throw IllegalStateException("provider crashed")
        }
        return answers[language] ?: ReviewResponse.Error(ReviewError.NoData, sourceUrl = null)
    }
}
