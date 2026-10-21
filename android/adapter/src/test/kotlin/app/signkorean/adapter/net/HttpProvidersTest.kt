package app.signkorean.adapter.net

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.place.PlaceSummary
import app.signkorean.domain.place.PlaceSummaryResult
import app.signkorean.domain.review.ReviewError
import app.signkorean.domain.review.ReviewResponse
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.route.RouteFailure
import app.signkorean.domain.route.RouteRequest
import app.signkorean.domain.route.RouteResult
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
    @Volatile private var status = 200
    @Volatile private var body = "{}"
    // 켜면 서버가 답하지 않고 기다린다(sleep 없이). 클라이언트의 읽기 시간 제한을 실제 소켓으로 확인한다.
    @Volatile private var hang = false
    private val released = java.util.concurrent.CountDownLatch(1)
    @Volatile private var lastPath = ""
    @Volatile private var lastQuery: String? = null
    @Volatile private var lastAcceptLanguage: String? = null
    @Volatile private var lastRawPath = ""
    @Volatile private var lastMethod = ""
    @Volatile private var lastBody = ""

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
        val requestLine = reader.readLine()?.split(" ") ?: return
        lastMethod = requestLine.getOrNull(0).orEmpty()
        val target = requestLine.getOrNull(1) ?: return
        val headers = generateSequence { reader.readLine()?.takeIf { it.isNotEmpty() } }.toList()
        val length = headers.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }?.substringAfter(":")?.trim()?.toIntOrNull() ?: 0
        lastBody = if (length > 0) CharArray(length).also { buf -> var read = 0; while (read < length) { val n = reader.read(buf, read, length - read); if (n < 0) break; read += n } }.let(::String) else ""
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
            "INVALID_INPUT" to ReviewError.NotFound,
            "SOMETHING_NEW" to ReviewError.Network,
        )
        table.forEach { (code, expected) ->
            status = 404
            body = """{"error":{"code":"$code","source_url":"https://place.map.kakao.com/1"}}"""
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

    // ---------- Phase 15 리뷰 수정 ----------

    @Test
    fun `RVW-03 형식이 틀린 리뷰 하나는 건너뛰고 나머지를 보인다`() = runBlocking {
        body = """{"items":[{"author":"A","text":""},{"author":"B","rating":4.6,"text":"Good"}],"attribution":"Kakao Map","availability":"partial"}"""
        val r = HttpReviewProvider(client).reviews(PlaceId("p"), LocaleTag("en")) as ReviewResponse.Found
        assertEquals(listOf("Good"), r.page.items.map { it.text })
        assertEquals("소수 평점은 반올림", 5, r.page.items.single().rating)
    }

    @Test
    fun `RVW-09 모르는 코드는 서버의 retryable 을 따른다`() = runBlocking {
        status = 503
        body = """{"error":{"code":"MAINTENANCE","retryable":false}}"""
        assertEquals(ReviewError.Unsupported, (HttpReviewProvider(client).reviews(PlaceId("p"), LocaleTag("en")) as ReviewResponse.Error).error)
        body = """{"error":{"code":"MAINTENANCE","retryable":true}}"""
        assertEquals(ReviewError.Network, (HttpReviewProvider(client).reviews(PlaceId("p"), LocaleTag("en")) as ReviewResponse.Error).error)
    }

    @Test
    fun `RVW-05 잘못된 서버 주소는 다시 시도해도 같으니 연결 안 됨으로`() = runBlocking {
        val bad = ApiClient("api.example.com", timeoutMs = 500)
        assertEquals(ReviewError.Unsupported, (HttpReviewProvider(bad).reviews(PlaceId("p"), LocaleTag("en")) as ReviewResponse.Error).error)
    }

    @Test
    fun `오류 본문이 문자열이어도 결과로 끝난다(예외가 새지 않는다)`() = runBlocking {
        status = 429
        body = """{"error":"RATE_LIMITED"}"""
        assertEquals(ReviewError.Network, (HttpReviewProvider(client).reviews(PlaceId("p"), LocaleTag("en")) as ReviewResponse.Error).error)
    }

    // ---------- 경로 (Phase 19) ----------

    @Test
    fun `경로 - 도보로 출발·도착 좌표를 POST 하고 거리·시간·경로선·출처를 읽는다`() = runBlocking {
        body = """{"distance_m":1200,"duration_s":900,"path":[[37.57,126.99],[37.575,126.985]],"attribution":"Kakao Map"}"""
        val r = HttpRouteProvider(client).route(RouteRequest(Coordinate(37.57, 126.99), Coordinate(37.575, 126.985))) as RouteResult.Found
        assertEquals("POST", lastMethod)
        assertEquals("/v1/routes", lastPath)
        assertTrue(lastBody.contains("\"mode\":\"walk\""))
        assertTrue(lastBody.contains("\"origin\":{\"lat\":37.57,\"lng\":126.99}"))
        assertEquals(1200, r.route.distanceMeters)
        assertEquals(900, r.route.durationSeconds)
        assertEquals(2, r.route.path.size)
        assertEquals("Kakao Map", r.route.attribution)
    }

    @Test
    fun `경로 오류 매핑 - 길 없음·미지원·시간 초과·그 밖`() = runBlocking {
        val table = mapOf(
            "NO_DATA" to RouteFailure.NoRoute,
            "NOT_FOUND" to RouteFailure.NoRoute,
            "UNSUPPORTED" to RouteFailure.Unsupported,
            "UPSTREAM_TIMEOUT" to RouteFailure.Timeout,
            "RATE_LIMITED" to RouteFailure.Network,
        )
        table.forEach { (code, expected) ->
            status = 422
            body = """{"error":{"code":"$code"}}"""
            assertEquals(code, RouteResult.Failed(expected), HttpRouteProvider(client).route(RouteRequest(Coordinate(37.5, 127.0), Coordinate(37.51, 127.01))))
        }
    }

    @Test
    fun `경로 응답이 비었거나 형식이 틀리면 지어내지 않고 실패 - 연결 문제가 아니라 미지원으로(RTE-L)`() = runBlocking {
        body = """{"distance_m":1200}"""
        assertEquals(RouteResult.Failed(RouteFailure.Unsupported), HttpRouteProvider(client).route(RouteRequest(Coordinate(37.5, 127.0), Coordinate(37.51, 127.01))))
    }

    @Test
    fun `RTE-L 거리에 소수점이 붙어도 읽는다`() = runBlocking {
        body = """{"distance_m":1234.6,"duration_s":900,"path":[[37.57,126.99],[37.575,126.985]],"attribution":"Kakao Map"}"""
        val r = HttpRouteProvider(client).route(RouteRequest(Coordinate(37.57, 126.99), Coordinate(37.575, 126.985))) as RouteResult.Found
        assertEquals(1235, r.route.distanceMeters)
    }

    @Test
    fun `RTE-L 출처 표기가 없는 경로는 보이지 않는다`() = runBlocking {
        body = """{"distance_m":1200,"duration_s":900,"path":[[37.57,126.99],[37.575,126.985]]}"""
        assertEquals(RouteResult.Failed(RouteFailure.Unsupported), HttpRouteProvider(client).route(RouteRequest(Coordinate(37.57, 126.99), Coordinate(37.575, 126.985))))
    }
}
