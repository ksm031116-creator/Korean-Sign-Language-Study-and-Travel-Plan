package app.signkorean.domain.place

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.Area
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.CardPlaceLinker
import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.Place
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.content.SignCard

/** 한 지역의 장소들. SC08 은 지역별로 묶어 보인다 (구현 계획 Phase 14 D2). */
data class AreaPlaces(val area: Area, val places: List<Place>)

/** 콘텐츠 팩에서 장소 목록을 만든다. 연결 규칙은 [CardPlaceLinker] 하나만 쓴다 (C-01~03). */
object PlaceListing {

    /** 간판을 볼 수 있는 장소. 팩의 지역 순서, 지역 안에서는 팩의 장소 순서. 비활성 지역은 뺀다. */
    fun forCard(pack: ContentPack, card: SignCard): List<AreaPlaces> {
        val places = CardPlaceLinker.placesFor(card, pack.places)
        return pack.areas.filter { it.active }
            .map { area -> AreaPlaces(area, places.filter { it.areaId == area.id }) }
            .filter { it.places.isNotEmpty() }
    }

    /** 그 장소에서 볼 수 있는 간판. 출제할 수 있는(활성) 카드만. */
    fun signsAt(pack: ContentPack, place: Place): List<SignCard> =
        CardPlaceLinker.cardsFor(place, pack.cards).filter { it.active }
}

/**
 * 장소 목록에 붙는 리뷰 요약 (PRD §10.3 `GET /v1/cards/{id}/places`). 공급자의 값을 그대로 보인다.
 * [excerpt] 는 요청한 언어의 발췌이며, 없으면 비워 둔다(번역해서 채우지 않는다, FR11).
 */
data class PlaceSummary(val rating: Double?, val reviewCount: Int, val excerpt: String?)

sealed interface PlaceSummaryResult {
    data class Loaded(val byPlace: Map<PlaceId, PlaceSummary>) : PlaceSummaryResult

    /** 서버에 닿지 않거나 실패했다. 장소 이름·사진·주소는 콘텐츠 팩으로 그대로 보인다. */
    data object Unavailable : PlaceSummaryResult
}

/** 장소 요약 포트 (설계서 §6.6). 구현은 중계 서버 HTTP (Phase 15). */
interface PlaceSummaryProvider {
    suspend fun summaries(card: CardId, language: LocaleTag): PlaceSummaryResult
}
