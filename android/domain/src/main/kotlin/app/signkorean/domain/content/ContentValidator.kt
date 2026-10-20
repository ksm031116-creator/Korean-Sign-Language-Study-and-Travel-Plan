package app.signkorean.domain.content

import app.signkorean.domain.common.LocaleTag

/** 콘텐츠 팩 검사 결과 하나. [isError] 가 true 면 출시 빌드를 막는다. */
sealed interface ContentIssue {
    val isError: Boolean

    data class DuplicateId(val id: String) : ContentIssue {
        override val isError get() = true
    }

    /** 같은 한국어 단어가 두 카드 이상에 있다 (C-06, BUG-01). */
    data class DuplicateWord(val word: String, val cards: List<CardId>) : ContentIssue {
        override val isError get() = true
    }

    /** 예시 장소가 실제 상호처럼 보인다 (C-09). */
    data class ExampleNotMarked(val place: PlaceId) : ContentIssue {
        override val isError get() = true
    }

    data class UnknownArea(val place: PlaceId, val area: AreaId) : ContentIssue {
        override val isError get() = true
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

data class ValidatedContent(val pack: ContentPack, val issues: List<ContentIssue>) {
    val hasErrors: Boolean get() = issues.any { it.isError }
}

/**
 * 콘텐츠 팩의 규칙 검사. 순수 함수라 앱 실행 시(어댑터)와 빌드 검사에서 같은 규칙을 쓴다.
 * 복구할 수 있는 문제(없는 픽토그램)는 고친 팩을 돌려주고, 나머지는 이슈로만 보고한다.
 */
class ContentValidator(private val knownPictograms: Set<PictogramId>) {

    fun validate(pack: ContentPack): ValidatedContent {
        val issues = mutableListOf<ContentIssue>()

        // ID 는 종류별 이름 공간이다. 지역 gyeongbokgung 과 장소 gyeongbokgung 은 서로 다른 값이다.
        listOf(pack.cards.map { it.id.value }, pack.places.map { it.id.value }, pack.areas.map { it.id.value })
            .forEach { ids ->
                ids.groupingBy { it }.eachCount()
                    .filterValues { it > 1 }
                    .keys.forEach { issues += ContentIssue.DuplicateId(it) }
            }

        pack.cards.groupBy { it.koText.trim() }
            .filterValues { it.size > 1 }
            .forEach { (word, cards) -> issues += ContentIssue.DuplicateWord(word, cards.map { it.id }) }

        val areaIds = pack.areas.map { it.id }.toSet()
        pack.places.forEach { place ->
            if (place.areaId !in areaIds) issues += ContentIssue.UnknownArea(place.id, place.areaId)
            if (place.isExample && place.name[EXAMPLE_LOCALE]?.startsWith(EXAMPLE_PREFIX) != true) {
                issues += ContentIssue.ExampleNotMarked(place.id)
            }
        }

        val cards = pack.cards.map { card ->
            val reasons = CardActivation.reasons(card)
            if (reasons.isNotEmpty()) issues += ContentIssue.InactiveCard(card.id, reasons)
            val pictogram = card.pictogram
            if (pictogram != null && pictogram !in knownPictograms) {
                issues += ContentIssue.UnknownPictogram(card.id, pictogram)
                card.copy(pictogram = null)
            } else {
                card
            }
        }

        return ValidatedContent(pack.copy(cards = cards), issues)
    }

    private companion object {
        val EXAMPLE_LOCALE = LocaleTag("en")
        const val EXAMPLE_PREFIX = "Example"
    }
}
