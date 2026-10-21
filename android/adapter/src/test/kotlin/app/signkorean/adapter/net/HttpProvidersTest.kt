package app.signkorean.adapter.net

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.place.PlaceSummary
import app.signkorean.domain.place.PlaceSummaryResult
import app.signkorean.domain.review.ReviewError
import app.signkorean.domain.review.ReviewResponse
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors

/**
 * 중계 서버 계약 (PRD §10.3) 대 HTTP 어댑터. 실제 소켓으로 테스트용 로컬 서버에 붙는다(구현 계획 Phase 15 D3).
 * 계약: 요청 언어는 `language` 와 `Accept-Language` 둘 다, 오류는 `{"error":{"code":..}}`, 8초 시간 제한.
 */
class HttpProvidersTest {

    // android.jar 에는 JDK 의 HTTP 서버가 없어 소켓으로 최소 HTTP/1.1 서버를 만든다(한 요청 = 한 연결).
    private val server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
    private val pool = Executors.newCachedThreadPool()
    private var status = 200
    private var body = "{}"
    // 켜면 서버가 답하지 않고 기다린다(sleep 없이). 클라이언트의 읽기 시간 제한을 실제 소켓으로 확인한다.
    @Volatile private var hang = false
    private val released = java.util.concurrent.CountDownLatch(1)
    private var lastPath = ""
    private var lastQuery: String? = null
    private var lastAcceptLanguage: String? = null
    private var lastRawPath = ""

    init {
        pool.execute {
            while (!server.isClosed) {
                val socket = runCatching { server.accept() }.getOrNull() ?: break
                // 기다리는 요청이 다음 요청을 막지 않게 요청마다 스레드.
                pool.execute { serve(socket) }
            }
        }
    }

    private fun serve(socket: Socket) = socket.use { s ->
        val reader = s.getInputStream().bufferedReader()
        val target = reader.readLine()?.split(" ")?.getOrNull(1) ?: return
        val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
        val uri = URI(target)
        lastPath = uri.path
        lastRawPath = uri.rawPath
        lastQuery = uri.query
        lastAcceptLanguage = headers.firstOrNull { it.startsWith("Accept-Language:", ignoreCase = true) }?.substringAfter(":")?.trim()
        if (hang) released.await()
        val bytes = body.toByteArray()
        val out = s.getOutputStream()
        val head = "HTTP/1.1 $status X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private val base = "http://127.0.0.1:${server.localPort}"
    private val client = ApiClient(base, timeoutMs = 500)

    @After
    fun stop() {
        released.countDown()
        server.close()
        pool.shutdownNow()
    }

    @Test
    fun `리뷰 - 언어를 쿼리와 헤더로 보내고 목록·출처·일부 여부를 읽는다`() = runBlocking {
        body = """
            {"items":[{"author":"Mina","rating":5,"text":"Great tteok","written_at":"2026-09-01T10:00:00Z","language":"en",
            "translated":true,"original_text":"떡이 맛있어요"}],
            "attribution":"Kakao Map","source_url":"https://place.map.kakao.com/1","availability":"partial"}
        """.trimIndent()
        val r = HttpReviewProvider(client).reviews(PlaceId("gwangjang"), LocaleTag("en")) as ReviewResponse.Found
        assertEquals("/v1/places/gwangjang/reviews", lastPath)
        assertEquals("language=en", lastQuery)
        assertEquals("en", lastAcceptLanguage)
        assertEquals("Kakao Map", r.page.attribution)
        assertTrue(r.page.partial)
        val item = r.page.items.single()
        assertTrue(item.translated)
        assertEquals("떡이 맛있어요", item.originalText)
        assertEquals(5, item.rating)
    }

    @Test
    fun `오류 코드 매핑표`() = runBlocking {
        val table = mapOf(
            "NO_DATA" to ReviewError.NoData,
            "NOT_FOUND" to ReviewError.NotFound,
            "UPSTREAM_TIMEOUT" to ReviewError.Timeout,
            "RATE_LIMITED" to ReviewError.RateLimited,
            "UNSUPPORTED" to ReviewError.Unsupported,
            "INVALID_INPUT" to ReviewError.Unsupported,
            "SOMETHING_NEW" to ReviewError.Network,
        )
        table.forEach { (code, expected) ->
            status = 404
            body = """{"error":{"code":"$code","retryable":false,"source_url":"https://place.map.kakao.com/1"}}"""
            val r = HttpReviewProvider(client).reviews(PlaceId("p"), LocaleTag("en")) as ReviewResponse.Error
            assertEquals(code, expected, r.error)
        }
    }

    @Test
    fun `NO_DATA 오류는 원문 링크를 함께 준다`() = runBlocking {
        status = 404
        body = """{"error":{"code":"NO_DATA","source_url":"https://place.map.kakao.com/1"}}"""
        assertEquals(ReviewResponse.Error(ReviewError.NoData, "https://place.map.kakao.com/1"),
            HttpReviewProvider(client).reviews(PlaceId("p"), LocaleTag("ja")))
    }

    @Test
    fun `시간 제한을 넘기면 Timeout, 연결이 안 되면 Network, 읽을 수 없는 본문도 Network`() = runBlocking {
        hang = true
        assertEquals(ReviewError.Timeout, (HttpReviewProvider(client).reviews(PlaceId("p"), LocaleTag("en")) as ReviewResponse.Error).error)
        hang = false
        val closed = ApiClient("http://127.0.0.1:1", timeoutMs = 500)
        assertEquals(ReviewError.Network, (HttpReviewProvider(closed).reviews(PlaceId("p"), LocaleTag("en")) as ReviewResponse.Error).error)
        body = "<html>oops</html>"
        assertEquals(ReviewError.Network, (HttpReviewProvider(client).reviews(PlaceId("p"), LocaleTag("en")) as ReviewResponse.Error).error)
    }

    @Test
    fun `장소 요약 - 카드의 장소별 평점·리뷰 수·발췌, 실패는 Unavailable`() = runBlocking {
        body = """{"places":[{"id":"gwangjang","rating":4.5,"review_count":120,"excerpt":"Busy"},{"id":"mangwon","rating":null,"review_count":0}]}"""
        val r = HttpPlaceSummaryProvider(client).summaries(CardId("t-market-1"), LocaleTag("ja")) as PlaceSummaryResult.Loaded
        assertEquals("/v1/cards/t-market-1/places", lastPath)
        assertEquals("language=ja", lastQuery)
        assertEquals(PlaceSummary(4.5, 120, "Busy"), r.byPlace[PlaceId("gwangjang")])
        assertEquals(PlaceSummary(null, 0, null), r.byPlace[PlaceId("mangwon")])
        status = 500
        body = """{"error":{"code":"UPSTREAM_TIMEOUT"}}"""
        assertEquals(PlaceSummaryResult.Unavailable, HttpPlaceSummaryProvider(client).summaries(CardId("t"), LocaleTag("en")))
    }

    @Test
    fun `경로 조각은 인코딩한다`() = runBlocking {
        body = """{"items":[],"attribution":"x","availability":"full"}"""
        HttpReviewProvider(client).reviews(PlaceId("a b/c"), LocaleTag("en"))
        assertEquals("/v1/places/a%20b%2Fc/reviews", lastRawPath)
    }
}
