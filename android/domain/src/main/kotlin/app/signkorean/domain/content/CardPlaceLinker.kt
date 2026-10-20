package app.signkorean.domain.content

/**
 * 카드 → 장소 연결 규칙의 유일한 구현 (PRD §10.2, 설계서 §6.1). 다른 곳에서 다시 구현하지 않는다.
 *
 * 장소 유형이 카드 유형과 같고, 장소에 signWords 가 없거나 그 안에 카드 단어가 있으면 연결한다.
 * 기초 표지판(Basic) 카드는 어떤 장소와도 연결하지 않는다.
 */
object CardPlaceLinker {

    fun links(card: SignCard, place: Place): Boolean {
        val kind = card.kind as? CardKind.PlaceSign ?: return false
        if (place.typeId != kind.typeId) return false
        val words = place.signWords ?: return true
        return card.koText in words
    }

    fun placesFor(card: SignCard, places: List<Place>): List<Place> = places.filter { links(card, it) }

    fun cardsFor(place: Place, cards: List<SignCard>): List<SignCard> = cards.filter { links(it, place) }

    /** 아래로 넘기기(장소 목록)를 보여 줄지. 없으면 안내에서 그 방향을 뺀다 (FR31, UX11). */
    fun hasPlaces(card: SignCard, places: List<Place>): Boolean = places.any { links(card, it) }
}
