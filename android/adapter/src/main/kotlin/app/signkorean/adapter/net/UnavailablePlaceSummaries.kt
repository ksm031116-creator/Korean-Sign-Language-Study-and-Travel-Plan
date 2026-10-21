package app.signkorean.adapter.net

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.CardId
import app.signkorean.domain.place.PlaceSummaryProvider
import app.signkorean.domain.place.PlaceSummaryResult

/**
 * 중계 서버(PRD §10.3)에 연결하기 전의 요약 공급자. 평점·리뷰 수를 지어내지 않고 "지금은 없음"으로 답한다.
 * 장소 이름·사진·주소는 콘텐츠 팩으로 그대로 보인다. HTTP 구현은 Phase 15 D3.
 */
object UnavailablePlaceSummaries : PlaceSummaryProvider {
    override suspend fun summaries(card: CardId, language: LocaleTag) = PlaceSummaryResult.Unavailable
}
