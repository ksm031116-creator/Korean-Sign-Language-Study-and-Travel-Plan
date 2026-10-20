package app.signkorean.adapter.content

import app.signkorean.domain.content.ContentIssue
import app.signkorean.domain.content.ContentValidator
import app.signkorean.domain.content.Pictograms
import app.signkorean.testing.testDispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class AssetContentRepositoryTest {

    private fun card(id: String, koText: String, pictogram: String?) =
        """{"id":"$id","kind":"basic","group":"restroom","koText":"$koText","romanization":"r","reading":{},
           "meaning":{"en":"m"},"note":{"en":"n"},"pictogram":${pictogram?.let { "\"$it\"" }},"plateColor":"#2f6fb0","audio":null,"reviewed":true}"""

    private fun pack(vararg cards: String) = """{"schemaVersion":1,"version":"v1","locales":{"en":"reviewed"},"areas":[],"cards":[${cards.joinToString(",")}],"places":[]}"""

    private val opens = AtomicInteger()
    private val reported = mutableListOf<ContentIssue>()

    private fun TestScope.repo(json: String) = AssetContentRepository(
        source = { opens.incrementAndGet(); json.byteInputStream() },
        parser = ContentPackParser(),
        validator = ContentValidator(Pictograms.ALL),
        reporter = { reported += it },
        dispatchers = testDispatchers(StandardTestDispatcher(testScheduler)),
    )

    @Test
    fun `동시에 여러 번 불러도 팩은 한 번만 읽고 같은 결과를 돌려준다`() = runTest {
        val repository = repo(pack(card("b01", "화장실", "restroom")))
        val results = (1..5).map { async { repository.load() } }.awaitAll()
        assertEquals(1, opens.get())
        results.forEach { assertSame(results.first(), it) }
    }

    @Test
    fun `경고는 보고하고 고친 팩을 돌려준다`() = runTest {
        val loaded = repo(pack(card("b01", "화장실", "unicorn"))).load()
        assertNull(loaded.cards.single().pictogram)
        assertTrue(reported.single() is ContentIssue.UnknownPictogram)
    }

    @Test
    fun `오류가 있는 팩은 예외로 알리고 다음 호출에서 다시 읽는다`() = runTest {
        val repository = repo(pack(card("b01", "출구", null), card("b02", "출구", null)))
        repeat(2) {
            val error = runCatching { repository.load() }.exceptionOrNull()
            assertTrue("$error", error is InvalidContentPackException)
        }
        assertEquals(2, opens.get())
        assertTrue(reported.any { it is ContentIssue.DuplicateWord })
    }

    @Test
    fun `형식 오류도 예외로 알린다`() = runTest {
        val error = runCatching { repo("{broken").load() }.exceptionOrNull()
        assertTrue("$error", error is ContentPackFormatException)
    }
}
