package app.signkorean.adapter.content

import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.CardPlaceLinker
import app.signkorean.domain.content.ContentIssue
import app.signkorean.domain.content.ContentPack
import app.signkorean.domain.content.ContentValidator
import app.signkorean.domain.content.PhotoLicense
import app.signkorean.domain.content.Pictograms
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * 앱에 실제로 들어가는 콘텐츠 팩 검사. 팩을 다시 만들 때마다 이 테스트가 출시 기준을 지킨다.
 * 작업 디렉터리는 :adapter 모듈 루트다.
 */
class BundledContentPackTest {

    private val packFile = File("src/main/assets/content/content-pack.json")
    private val pack: ContentPack by lazy { ContentPackParser().parse(packFile.readText()) }

    @Test
    fun `팩은 135장·49곳·8지역이고 오류 없이 검사를 통과한다 (C-06·09)`() {
        assertEquals(135, pack.cards.size)
        assertEquals(49, pack.places.size)
        assertEquals(8, pack.areas.size)
        val issues = ContentValidator(Pictograms.ALL).validate(pack).issues
        assertEquals(emptyList<ContentIssue>(), issues)
    }

    @Test
    fun `모든 카드가 활성이다 (C-04·05b)`() {
        assertEquals(emptyList<String>(), pack.cards.filterNot { it.active }.map { it.id.value })
    }

    @Test
    fun `C-08 잠긴 ID 는 모두 남아 있고 원문이 같다`() {
        val lock = Json.parseToJsonElement(File("../content-id-lock.json").readText()).jsonObject
            .mapValues { it.value.jsonPrimitive.content }
        val current = pack.cards.associate { it.id.value to it.koText } + pack.places.associate { "place:${it.id.value}" to it.koName }
        val broken = lock.filter { (id, text) -> current[id] != text }
        assertEquals(emptyMap<String, String>(), broken)
        assertEquals("잠금에 없는 ID 가 있다. build_content_pack.py 로 팩과 잠금을 함께 만들어야 한다", emptySet<String>(), current.keys - lock.keys)
    }

    @Test
    fun `C-10 모든 사진에 라이선스와 출처가 있고 CC BY 는 저작자가 있다`() {
        val photos = pack.places.mapNotNull { it.photo }
        assertTrue(photos.isNotEmpty())
        photos.forEach { photo ->
            assertTrue(photo.url, photo.source.isNotBlank())
            assertTrue(photo.url, photo.url.startsWith("https://") || photo.url.startsWith("asset://"))
            (photo.license as? PhotoLicense.CcBy)?.let { assertTrue(photo.url, it.author.isNotBlank()) }
        }
    }

    @Test
    fun `내장 사진 파일이 실제로 있다`() {
        pack.places.mapNotNull { it.photo?.url }.filter { it.startsWith("asset://") }.forEach { url ->
            assertTrue(url, File("src/main/assets/" + url.removePrefix("asset://")).isFile)
        }
    }

    @Test
    fun `C-07 카드별 연결 장소가 승인된 스냅샷과 같다`() {
        val actual = buildString {
            appendLine("# 카드별 연결 장소 (C-07). 바뀌면 사람이 검토한 뒤 -PupdateSnapshots=true 로 갱신한다.")
            pack.cards.forEach { card ->
                val places = CardPlaceLinker.placesFor(card, pack.places).joinToString(", ") { it.id.value }
                val tag = if (card.kind is CardKind.Basic) "basic" else "place"
                appendLine("${card.id.value}\t${card.koText}\t$tag\t$places")
            }
        }
        val snapshot = File("src/test/resources/content/link-audit.snapshot.tsv")
        if (System.getProperty("updateSnapshots") == "true") {
            snapshot.parentFile.mkdirs()
            snapshot.writeText(actual)
            return
        }
        if (!snapshot.isFile) fail("스냅샷이 없다. 검토 후 -PupdateSnapshots=true 로 만든다")
        assertEquals(snapshot.readText().replace("\r\n", "\n"), actual)
    }
}
