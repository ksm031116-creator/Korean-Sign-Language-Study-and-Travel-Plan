package app.signkorean.domain.learning

import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.CardPlaceLinker
import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.SignCard

/**
 * 세션 출처의 카드 (FR01). 순서는 콘텐츠 팩의 카드 순서다. 장소와의 연결은 CardPlaceLinker 로만 판단한다.
 * Saved(북마크)·TripDay(일정)는 각자의 저장소가 카드 목록을 만들어 넘긴다(Phase 11·20).
 */
object SourceCards {
    fun of(pack: ContentPack, source: SessionSource): List<SignCard> = when (source) {
        SessionSource.Basics -> pack.cards.filter { it.kind is CardKind.Basic }
        is SessionSource.Area -> {
            val places = pack.places.filter { it.areaId == source.area }
            pack.cards.filter { card -> places.any { CardPlaceLinker.links(card, it) } }
        }
        is SessionSource.Place -> {
            val place = pack.places.firstOrNull { it.id.value == source.place } ?: return emptyList()
            CardPlaceLinker.cardsFor(place, pack.cards)
        }
        SessionSource.Saved, is SessionSource.TripDay -> emptyList()
    }
}
