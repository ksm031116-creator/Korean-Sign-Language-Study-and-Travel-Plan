package app.signkorean.domain.content

import app.signkorean.domain.common.LocaleTag

enum class InactiveReason { MissingKoreanText, MissingRomanization, MissingBaseMeaning, MissingBaseNote, NotReviewed }

/**
 * 카드 활성화 규칙 (설계서 §6.1). 언어와 무관하게 기준 언어 [BASE_LOCALE] 로만 판단한다.
 * ja 같은 다른 언어가 비어 있어도 카드는 활성이고, 그 언어 사용자에게는 en 으로 대체해 보여 준다.
 * 일부만 번역된 새 언어를 추가해도 영어 사용자의 카드가 꺼지지 않게 하기 위해서다.
 */
object CardActivation {

    val BASE_LOCALE = LocaleTag("en")

    fun reasons(card: SignCard): Set<InactiveReason> = buildSet {
        if (card.koText.isBlank()) add(InactiveReason.MissingKoreanText)
        if (card.romanization.isBlank()) add(InactiveReason.MissingRomanization)
        if (card.meaning[BASE_LOCALE] == null) add(InactiveReason.MissingBaseMeaning)
        if (card.note[BASE_LOCALE] == null) add(InactiveReason.MissingBaseNote)
        if (!card.reviewed) add(InactiveReason.NotReviewed)
    }
}
