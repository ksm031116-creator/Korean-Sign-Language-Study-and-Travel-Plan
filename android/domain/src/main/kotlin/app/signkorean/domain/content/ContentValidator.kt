package app.signkorean.domain.content

import app.signkorean.domain.common.LocaleTag

/** 콘텐츠 팩 검사 결과 하나. [isError] 가 true 면 출시 빌드를 막는다(BundledContentPackTest). */
sealed interface ContentIssue {
    val isError: Boolean

    /** 같은 종류 안에서 ID 가 겹친다. 앱에서는 처음 항목만 남긴다. */
    data class DuplicateId(val id: String) : ContentIssue {
        override val isError get() = true
    }

    /** 같은 한국어 단어가 두 카드 이상에 있다 (C-06, BUG-01). 앱에서는 처음 카드만 남긴다. */
    data class DuplicateWord(val word: String, val cards: List<CardId>) : ContentIssue {
        override val isError get() = true
    }

    /** 원문 앞뒤에 공백이 있다. 연결 규칙은 원문을 그대로 비교하므로 조용히 연결이 끊긴다. */
    data class UntrimmedText(val card: CardId) : ContentIssue {
        override val isError get() = true
    }

    /** 예시 장소 표시와 이름이 어긋난다 (C-09). 예시인데 "Example" 이 없거나, 실제 장소인데 있다. 앱에서는 뺀다. */
    data class ExampleNotMarked(val place: PlaceId) : ContentIssue {
        override val isError get() = true
    }

    /** 없는 지역을 가리키는 장소. 앱에서는 뺀다. */
    data class UnknownArea(val place: PlaceId, val area: AreaId) : ContentIssue {
        override val isError get() = true
    }

    /** 장소 signWords 의 단어가 그 유형의 어느 카드에도 없다(오타 등). 그 단어로는 연결되지 않는다. */
    data class UnknownSignWord(val place: PlaceId, val word: String) : ContentIssue {
        override val isError get() = true
    }

    /** 장소 유형에 카드가 하나도 없어 어떤 카드에서도 이 장소로 갈 수 없다. */
    data class PlaceTypeWithoutCards(val place: PlaceId, val typeId: String) : ContentIssue {
        override val isError get() = true
    }

    /** 장소 유형 카드인데 연결되는 장소가 없다. 아래로 넘기기가 빠질 뿐 학습은 된다. */
    data class CardWithoutPlaces(val card: CardId) : ContentIssue {
        override val isError get() = false
    }

    /** 앱에 없는 픽토그램. 카드는 활성으로 두고 뒷면에 단어만 그린다 (C-11). */
    data class UnknownPictogram(val card: CardId, val pictogram: PictogramId) : ContentIssue {
        override val isError get() = false
    }

    /** 출제하지 않는 카드. 콘텐츠 작업 중엔 생길 수 있어 경고로 둔다. */
    data class InactiveCard(val card: CardId, val reasons: Set<InactiveReason>) : ContentIssue {
        override val isError get() = false
    }
}

/** [pack] 은 앱에서 써도 되도록 문제 항목을 뺀 팩, [issues] 는 발견한 문제 전부. */
data class ValidatedContent(val pack: ContentPack, val issues: List<ContentIssue>) {
    val hasErrors: Boolean get() = issues.any { it.isError }
}

/**
 * 콘텐츠 팩의 규칙 검사. 순수 함수라 앱 실행 시(어댑터)와 출시 검사(테스트)에서 같은 규칙을 쓴다.
 *
 * 설계서 §7.1: 항목 하나의 문제로 앱 전체가 멈추면 안 된다. 문제 항목은 빼거나 고친 팩을 돌려주고,
 * 문제는 이슈로 남긴다. 출시 빌드는 이슈 중 오류가 하나라도 있으면 테스트에서 막힌다.
 */
class ContentValidator(private val pictograms: PictogramCatalog) {

    fun validate(pack: ContentPack): ValidatedContent {
        val issues = mutableListOf<ContentIssue>()

        // ID 는 종류별 이름 공간이다. 지역 gyeongbokgung 과 장소 gyeongbokgung 은 서로 다른 값이다.
        val areas = pack.areas.firstOfEach({ it.id.value }, issues)
        val uniqueCards = pack.cards.firstOfEach({ it.id.value }, issues)

        val cards = mutableListOf<SignCard>()
        uniqueCards.groupBy { it.koText.trim() }.forEach { (word, same) ->
            if (same.size > 1) issues += ContentIssue.DuplicateWord(word, same.map { it.id })
        }
        // 같은 단어가 겹치면 출제할 수 있는(활성) 카드를 남긴다. 둘 다 같으면 먼저 나온 카드.
        val keptForWord = uniqueCards.groupBy { it.koText.trim() }
            .mapValues { (_, same) -> same.firstOrNull { it.active } ?: same.first() }
        uniqueCards.forEach { card ->
            if (card.koText != card.koText.trim()) issues += ContentIssue.UntrimmedText(card.id)
            if (keptForWord[card.koText.trim()] !== card) return@forEach
            val reasons = CardActivation.reasons(card)
            if (reasons.isNotEmpty()) issues += ContentIssue.InactiveCard(card.id, reasons)
            val pictogram = card.pictogram
            cards += if (pictogram != null && pictogram !in pictograms) {
                issues += ContentIssue.UnknownPictogram(card.id, pictogram)
                card.copy(pictogram = null)
            } else {
                card
            }
        }

        val activeCards = cards.filter { it.active }
        val areaIds = areas.map { it.id }.toSet()
        val places = pack.places.firstOfEach({ it.id.value }, issues).filter { place ->
            var keep = true
            if (place.areaId !in areaIds) {
                issues += ContentIssue.UnknownArea(place.id, place.areaId)
                keep = false
            }
            val namedExample = place.name[EXAMPLE_LOCALE]?.startsWith(EXAMPLE_PREFIX) == true
            if (place.isExample != namedExample) {
                issues += ContentIssue.ExampleNotMarked(place.id)
                keep = false
            }
            // 앱에서 뺀 장소는 연결 검사를 하지 않는다(이미 오류로 보고했다).
            if (!keep) return@filter false
            // 연결은 출제할 수 있는 카드로만 이어진다. 비활성 카드만 가리키는 장소는 어느 카드에서도 갈 수 없다.
            val typeCards = activeCards.filter { (it.kind as? CardKind.PlaceSign)?.typeId == place.typeId }
            if (typeCards.isEmpty()) issues += ContentIssue.PlaceTypeWithoutCards(place.id, place.typeId)
            place.signWords?.forEach { word ->
                // 단어별로 연결 규칙(CardPlaceLinker)을 그대로 적용해, 그 단어 하나만으로 연결되는 카드가 있는지 본다.
                val probe = place.copy(signWords = setOf(word))
                if (typeCards.none { CardPlaceLinker.links(it, probe) }) issues += ContentIssue.UnknownSignWord(place.id, word)
            }
            true
        }

        activeCards.filter { it.kind is CardKind.PlaceSign && !CardPlaceLinker.hasPlaces(it, places) }
            .forEach { issues += ContentIssue.CardWithoutPlaces(it.id) }

        return ValidatedContent(pack.copy(areas = areas, cards = cards, places = places), issues)
    }

    /** 같은 ID 가 여러 번 나오면 처음 것만 남기고 중복을 보고한다. */
    private fun <T> List<T>.firstOfEach(id: (T) -> String, issues: MutableList<ContentIssue>): List<T> {
        groupingBy(id).eachCount().filterValues { it > 1 }.keys.forEach { issues += ContentIssue.DuplicateId(it) }
        return distinctBy(id)
    }

    private companion object {
        val EXAMPLE_LOCALE = LocaleTag("en")
        const val EXAMPLE_PREFIX = "Example"
    }
}
