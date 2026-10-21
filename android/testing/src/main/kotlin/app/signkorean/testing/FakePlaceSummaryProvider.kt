package app.signkorean.testing

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.place.PlaceSummary
import app.signkorean.domain.place.PlaceSummaryProvider
import app.signkorean.domain.place.PlaceSummaryResult
import kotlinx.coroutines.CompletableDeferred

/** 장소 요약(평점·리뷰 수·발췌) 가짜. [fail] 이면 실패, [hold] 면 [release] 까지 기다린다(불러오는 중 화면). */
class FakePlaceSummaryProvider(
    @Volatile var summaries: Map<PlaceId, PlaceSummary> = emptyMap(),
    @Volatile var fail: Boolean = false,
) : PlaceSummaryProvider {
    val requests = mutableListOf<Pair<CardId, LocaleTag>>()

    @Volatile var hold = false
    private var gate = CompletableDeferred<Unit>()

    fun release() = gate.complete(Unit)

    override suspend fun summaries(card: CardId, language: LocaleTag): PlaceSummaryResult {
        requests += card to language
        if (hold) {
            gate.await()
            gate = CompletableDeferred()
        }
        return if (fail) PlaceSummaryResult.Unavailable else PlaceSummaryResult.Loaded(summaries)
    }
}
