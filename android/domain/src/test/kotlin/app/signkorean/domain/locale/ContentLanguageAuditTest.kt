package app.signkorean.domain.locale

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.common.LocalizedText
import app.signkorean.domain.content.ReviewStatus
import app.signkorean.testing.ContentFixtures.EN
import app.signkorean.testing.ContentFixtures.JA
import app.signkorean.testing.ContentFixtures.card
import app.signkorean.testing.ContentFixtures.pack
import app.signkorean.testing.ContentFixtures.place
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 출시 전 콘텐츠 언어 검사 (설계서 §8.8). L10N-04·15 */
class ContentLanguageAuditTest {

    private fun both(en: String, ja: String) = LocalizedText(mapOf(EN to en, JA to ja))

    private val complete = card("b01", meaning = both("Exit", "出口"), note = both("n", "n")).copy(reading = LocalizedText(mapOf(JA to "チュルグ")))

    @Test
    fun `모든 지원 언어가 채워져 있으면 빈 결과`() {
        val p = pack(cards = listOf(complete), places = listOf(place("p").copy(name = both("A", "A"))), locales = mapOf(EN to ReviewStatus.Reviewed, JA to ReviewStatus.Reviewed))
        val report = ContentLanguageAudit.audit(p)
        assertTrue(report.isReleasable, "$report")
    }

    @Test
    fun `L10N-04 ja 뜻·설명·읽기·장소 이름 누락을 항목별로 보고한다`() {
        val p = pack(cards = listOf(card("b01")), places = listOf(place("p")), locales = mapOf(EN to ReviewStatus.Reviewed, JA to ReviewStatus.Reviewed))
        val gaps = ContentLanguageAudit.audit(p).gaps
        assertEquals(
            setOf(
                LanguageGap("b01", JA, LanguageGap.Field.Meaning),
                LanguageGap("b01", JA, LanguageGap.Field.Note),
                LanguageGap("b01", JA, LanguageGap.Field.Reading),
                LanguageGap("p", JA, LanguageGap.Field.PlaceName),
            ),
            gaps.toSet(),
        )
    }

    @Test
    fun `en 은 로마자가 읽기 표기라 reading 을 요구하지 않는다`() {
        val p = pack(cards = listOf(card("b01")), locales = mapOf(EN to ReviewStatus.Reviewed))
        assertTrue(ContentLanguageAudit.audit(p).isReleasable)
    }

    @Test
    fun `비활성 카드는 검사하지 않는다`() {
        val p = pack(cards = listOf(card("b01", reviewed = false)), locales = mapOf(EN to ReviewStatus.Reviewed, JA to ReviewStatus.Reviewed))
        assertEquals(emptyList<LanguageGap>(), ContentLanguageAudit.audit(p).gaps)
    }

    @Test
    fun `L10N-15 draft 언어가 있으면 출시할 수 없다`() {
        val p = pack(cards = listOf(complete), locales = mapOf(EN to ReviewStatus.Reviewed, JA to ReviewStatus.Draft))
        val report = ContentLanguageAudit.audit(p)
        assertEquals(listOf(JA), report.draftLocales)
        assertEquals(false, report.isReleasable)
    }

    @Test
    fun `출시 가능한 언어 목록은 검수 완료 언어만`() {
        val p = pack(locales = mapOf(EN to ReviewStatus.Reviewed, JA to ReviewStatus.Draft, LocaleTag("xx") to ReviewStatus.Reviewed))
        assertEquals(listOf(EN, LocaleTag("xx")), p.selectableLocales(includeDrafts = false))
        assertEquals(listOf(EN, JA, LocaleTag("xx")), p.selectableLocales(includeDrafts = true))
    }
}
