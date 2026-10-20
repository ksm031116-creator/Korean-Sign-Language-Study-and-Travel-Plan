package app.signkorean.adapter.content

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.PhotoLicense
import app.signkorean.domain.content.PictogramId
import app.signkorean.domain.content.ReviewStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPackParserTest {

    private val parser = ContentPackParser()

    private fun pack(cards: String = "[]", places: String = "[]", schema: Int = 1) = """
        {"schemaVersion":$schema,"version":"abc","locales":{"en":"reviewed","ja":"draft"},
         "areas":[{"id":"myeongdong","koName":"명동","name":{"en":"Myeongdong"},"active":true}],
         "cards":$cards,"places":$places}
    """.trimIndent()

    private val basicCard = """{"id":"b01","kind":"basic","group":"restroom","koText":"화장실","romanization":"hwajangsil",
        "reading":{"ja":"ファジャンシル"},"meaning":{"en":"Restroom"},"note":{"en":"The word itself."},
        "pictogram":"restroom","plateColor":"#2f6fb0","audio":null,"reviewed":true}"""

    private fun place(photo: String) = """[{"id":"p","areaId":"myeongdong","typeId":"services","koName":"약국","name":{"en":"Example pharmacy"},
        "address":{"en":"Example place"},"lat":37.5,"lng":126.9,"signWords":["약국"],"photo":$photo,"isExample":true}]"""

    @Test
    fun `카드 필드를 도메인 모델로 옮긴다`() {
        val card = parser.parse(pack(cards = "[$basicCard]")).cards.single()
        assertEquals("b01", card.id.value)
        assertEquals(CardKind.Basic("restroom"), card.kind)
        assertEquals("화장실", card.koText)
        assertEquals("ファジャンシル", card.reading[LocaleTag("ja")])
        assertEquals("Restroom", card.meaning[LocaleTag("en")])
        assertEquals(PictogramId("restroom"), card.pictogram)
        assertNull(card.audio)
        assertTrue(card.active)
    }

    @Test
    fun `장소 유형 카드는 PlaceSign 이 된다`() {
        val json = basicCard.replace("\"basic\"", "\"place\"").replace("\"restroom\",\"koText\"", "\"market\",\"koText\"")
        assertEquals(CardKind.PlaceSign("market"), parser.parse(pack(cards = "[$json]")).cards.single().kind)
    }

    @Test
    fun `장소의 signWords·Example·좌표를 옮긴다`() {
        val p = parser.parse(pack(places = place("null"))).places.single()
        assertEquals(setOf("약국"), p.signWords)
        assertTrue(p.isExample)
        assertEquals(37.5, p.coordinate.lat, 0.0)
        assertNull(p.photo)
    }

    @Test
    fun `사진 라이선스 네 종류를 옮긴다`() {
        fun license(json: String) = parser.parse(pack(places = place("""{"url":"u","license":$json,"source":"s","sourceUrl":null}"""))).places.single().photo!!.license
        assertEquals(PhotoLicense.KoglType1, license("""{"type":"KOGL_1"}"""))
        assertEquals(PhotoLicense.KoglType3, license("""{"type":"KOGL_3"}"""))
        assertEquals(PhotoLicense.Cc0, license("""{"type":"CC0"}"""))
        assertEquals(PhotoLicense.CcBy("Suohros", "4.0"), license("""{"type":"CC_BY","version":"4.0","author":"Suohros"}"""))
    }

    @Test
    fun `C-10 허용되지 않는 라이선스는 팩 전체를 거부한다`() {
        val e = assertThrows(ContentPackFormatException::class.java) {
            parser.parse(pack(places = place("""{"url":"u","license":{"type":"CC_BY_SA"},"source":"s","sourceUrl":null}""")))
        }
        assertTrue(e.message, e.message!!.contains("places[0].photo.license.type"))
    }

    @Test
    fun `C-10 CC BY 에 저작자가 없으면 거부한다`() {
        assertThrows(ContentPackFormatException::class.java) {
            parser.parse(pack(places = place("""{"url":"u","license":{"type":"CC_BY","version":"4.0","author":" "},"source":"s","sourceUrl":null}""")))
        }
    }

    @Test
    fun `C-10 출처가 비면 거부한다`() {
        assertThrows(ContentPackFormatException::class.java) {
            parser.parse(pack(places = place("""{"url":"u","license":{"type":"CC0"},"source":"","sourceUrl":null}""")))
        }
    }

    @Test
    fun `언어별 검수 상태를 읽는다`() {
        val locales = parser.parse(pack()).locales
        assertEquals(mapOf(LocaleTag("en") to ReviewStatus.Reviewed, LocaleTag("ja") to ReviewStatus.Draft), locales)
    }

    @Test
    fun `모르는 검수 상태는 거부한다`() {
        val e = assertThrows(ContentPackFormatException::class.java) { parser.parse(pack().replace("\"draft\"", "\"wip\"")) }
        assertTrue(e.message, e.message!!.contains("locales.ja"))
    }

    @Test
    fun `모르는 스키마 버전은 거부한다`() {
        val e = assertThrows(ContentPackFormatException::class.java) { parser.parse(pack(schema = 2)) }
        assertTrue(e.message!!.contains("schemaVersion"))
    }

    @Test
    fun `필드가 빠지면 위치를 알려 준다`() {
        val broken = basicCard.replace("\"romanization\":\"hwajangsil\",", "")
        val e = assertThrows(ContentPackFormatException::class.java) { parser.parse(pack(cards = "[$broken]")) }
        assertTrue(e.message, e.message!!.contains("cards[0].romanization"))
    }

    @Test
    fun `JSON 이 아니면 형식 오류`() {
        assertThrows(ContentPackFormatException::class.java) { parser.parse("{not json") }
    }
}
