package app.signkorean.domain.locale

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.ReviewStatus

/** 지원 언어로 채워지지 않은 콘텐츠 칸 하나. [id] 는 카드 또는 장소 ID. */
data class LanguageGap(val id: String, val locale: LocaleTag, val field: Field) {
    enum class Field { Meaning, Note, Reading, PlaceName, PlaceAddress, AreaName }
}

data class LanguageAuditReport(
    val gaps: List<LanguageGap>,
    val draftLocales: List<LocaleTag>,
    /** 기준 언어 en 이 팩에 없다. 대체의 마지막 단계가 비어 빈 문구가 보일 수 있다. */
    val baseLocaleMissing: Boolean = false,
) {
    /** 출시 빌드에 넣어도 되는지. 누락도, 검수 전 언어도 없고 기준 언어가 있어야 한다 (설계서 §8.8). */
    val isReleasable: Boolean get() = gaps.isEmpty() && draftLocales.isEmpty() && !baseLocaleMissing
}

/**
 * 출시 전 콘텐츠 언어 검사 (설계서 §8.8). 활성 카드·장소마다 팩의 모든 언어가 채워졌는지 본다.
 * 앱 실행 중에는 대체(사용자 언어 → en)가 빈칸을 가리지만, 출시 빌드에서는 대체가 일어나면 안 된다.
 */
object ContentLanguageAudit {

    fun audit(pack: ContentPack): LanguageAuditReport {
        val gaps = mutableListOf<LanguageGap>()
        pack.locales.keys.forEach { locale ->
            pack.cards.filter { it.active }.forEach { card ->
                if (card.meaning[locale] == null) gaps += LanguageGap(card.id.value, locale, LanguageGap.Field.Meaning)
                if (card.note[locale] == null) gaps += LanguageGap(card.id.value, locale, LanguageGap.Field.Note)
                // en 은 로마자 자체가 읽기 표기라 따로 쓰지 않는다. 다른 언어는 고유 표기(ja 카타카나)가 필요하다.
                if (locale != LanguagePolicy.BASE && card.reading[locale] == null) {
                    gaps += LanguageGap(card.id.value, locale, LanguageGap.Field.Reading)
                }
            }
            pack.places.forEach { place ->
                if (place.name[locale] == null) gaps += LanguageGap(place.id.value, locale, LanguageGap.Field.PlaceName)
                if (place.address[locale] == null) gaps += LanguageGap(place.id.value, locale, LanguageGap.Field.PlaceAddress)
            }
            pack.areas.filter { it.active }.forEach { area ->
                if (area.name[locale] == null) gaps += LanguageGap(area.id.value, locale, LanguageGap.Field.AreaName)
            }
        }
        val drafts = pack.locales.filterValues { it == ReviewStatus.Draft }.keys.toList()
        return LanguageAuditReport(gaps, drafts, baseLocaleMissing = LanguagePolicy.BASE !in pack.locales)
    }
}
