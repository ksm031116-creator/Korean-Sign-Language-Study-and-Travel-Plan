package app.signkorean.adapter.net

import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.place.PlaceSummary
import app.signkorean.domain.place.PlaceSummaryProvider
import app.signkorean.domain.place.PlaceSummaryResult
import app.signkorean.domain.review.Review
import app.signkorean.domain.review.ReviewError
import app.signkorean.domain.review.ReviewPage
import app.signkorean.domain.review.ReviewProvider
import app.signkorean.domain.review.ReviewResponse
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.route.Route
import app.signkorean.domain.route.RouteFailure
import app.signkorean.domain.route.RouteProvider
import app.signkorean.domain.route.RouteRequest
import app.signkorean.domain.route.RouteResult
import app.signkorean.domain.route.TransitKind
import app.signkorean.domain.route.TransitStep
import app.signkorean.domain.route.TransitSummary
import app.signkorean.domain.route.TravelMode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.longOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/** 중계 서버 응답. 성공은 본문, 실패는 서버 오류 코드 또는 연결 문제. */
sealed interface ApiResult {
    data class Ok(val body: JsonObject) : ApiResult
    /** [retryable] 은 서버가 알려 준 재시도 가능 여부(PRD §10.3). 모르는 코드는 이 값을 따른다. */
    data class ServerError(val code: String?, val sourceUrl: String?, val retryable: Boolean? = null) : ApiResult
    data object Timeout : ApiResult
    data object Network : ApiResult

    /** 서버 주소가 잘못됐거나(형식·http) 이 빌드에서 쓸 수 없다. 다시 시도해도 같다(RVW-05). */
    data object Misconfigured : ApiResult
}

/**
 * 중계 서버 HTTP 클라이언트 (PRD §10.3, 설계서 §7.5). PRD 의 계약에만 의존하고 공급자(Kakao 등) 응답은 모른다.
 * 시간 제한은 8초(NFR02). 요청 언어는 `language` 쿼리와 `Accept-Language` 헤더 둘 다로 보낸다.
 * 비밀키를 앱에 두지 않는다 — 서버가 공급자 키를 가진다(설계서 원칙 13).
 */
class ApiClient(
    private val baseUrl: String,
    private val timeoutMs: Int = 8_000,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(pathSegments: List<String>, language: LocaleTag): ApiResult =
        send(pathSegments, "?language=${URLEncoder.encode(language.value, "UTF-8")}", language, body = null)

    /** JSON 본문을 보낸다(경로 요청). 경로에는 언어가 없어 기본 언어를 헤더로만 보낸다. */
    suspend fun post(pathSegments: List<String>, body: String, language: LocaleTag = LocaleTag("en")): ApiResult =
        send(pathSegments, "", language, body)

    // runInterruptible: 막힌 소켓 읽기도 코루틴 취소(시트 닫기, 8초 제한)로 끊는다(RVW-14).
    private suspend fun send(pathSegments: List<String>, query: String, language: LocaleTag, body: String?): ApiResult = runInterruptible(io) {
        val path = pathSegments.joinToString("/") { segment(it) }
        // https 만(같은 기기의 루프백 테스트 서버는 예외). Android 9+ 는 평문 http 를 막아
        // 영원히 "다시 시도" 로 보이게 된다(NFR05, RVW-05).
        if (!baseUrl.startsWith("https://") && !LOOPBACK.matches(baseUrl)) return@runInterruptible ApiResult.Misconfigured
        val conn = try {
            URL("${baseUrl.trimEnd('/')}/$path$query").openConnection() as HttpURLConnection
        } catch (_: java.net.MalformedURLException) {
            return@runInterruptible ApiResult.Misconfigured
        } catch (_: IOException) {
            return@runInterruptible ApiResult.Network
        }
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Language", language.value)
            if (body != null) {
                val bytes = body.toByteArray(Charsets.UTF_8)
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setFixedLengthStreamingMode(bytes.size)
                conn.outputStream.use { it.write(bytes) }
            }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val body = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: return@runInterruptible ApiResult.Network
            if (status in 200..299) {
                ApiResult.Ok(body)
            } else {
                // 오류 봉투가 약속과 다른 모양이어도(문자열 등) 예외를 밖으로 내지 않는다(RVW-11).
                val error = runCatching { body["error"]?.takeUnless { it is JsonNull }?.jsonObject }.getOrNull()
                ApiResult.ServerError(
                    error?.string("code"),
                    error?.string("source_url"),
                    runCatching { error?.get("retryable")?.jsonPrimitive?.booleanOrNull }.getOrNull(),
                )
            }
        } catch (_: SocketTimeoutException) {
            ApiResult.Timeout
        } catch (_: java.net.UnknownServiceException) {
            // 평문 http 차단 등. 이 빌드의 설정 문제다.
            ApiResult.Misconfigured
        } catch (_: IOException) {
            ApiResult.Network
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        val LOOPBACK = Regex("""http://(127\.0\.0\.1|localhost)(:\d+)?(/.*)?""")
    }

    /** 경로 조각 하나를 퍼센트 인코딩한다. URLEncoder 는 공백을 + 로 바꾸므로 URI 로 인코딩한다. */
    private fun segment(raw: String): String =
        URI(null, null, "/$raw", null).rawPath.removePrefix("/").replace("/", "%2F")
}

/** 서버 오류 코드 → 도메인 오류 (설계서 §7.5 ApiErrorMapper). 모르는 코드는 다시 시도할 수 있는 실패로 본다. */
object ApiErrorMapper {
    fun review(result: ApiResult): ReviewError = when (result) {
        is ApiResult.Ok -> error("성공 응답은 오류가 아니다")
        ApiResult.Timeout -> ReviewError.Timeout
        ApiResult.Network -> ReviewError.Network
        ApiResult.Misconfigured -> ReviewError.Unsupported
        is ApiResult.ServerError -> when (result.code) {
            "NO_DATA" -> ReviewError.NoData
            // 장소 ID 형식이 틀렸다 = 서버가 그 장소를 모른다(RVW-09).
            "NOT_FOUND", "INVALID_INPUT" -> ReviewError.NotFound
            "UPSTREAM_TIMEOUT" -> ReviewError.Timeout
            "RATE_LIMITED" -> ReviewError.RateLimited
            "UNSUPPORTED" -> ReviewError.Unsupported
            else -> if (result.retryable == false) ReviewError.Unsupported else ReviewError.Network
        }
    }
}

/** `GET /v1/places/{id}/reviews?language=` (PRD §10.3). */
class HttpReviewProvider(private val client: ApiClient) : ReviewProvider {
    override suspend fun reviews(place: PlaceId, language: LocaleTag): ReviewResponse {
        val result = client.get(listOf("v1", "places", place.value, "reviews"), language)
        if (result !is ApiResult.Ok) {
            return ReviewResponse.Error(ApiErrorMapper.review(result), (result as? ApiResult.ServerError)?.sourceUrl)
        }
        val body = result.body
        val page = runCatching {
            ReviewPage(
                // 형식이 틀린 리뷰 하나 때문에 페이지 전체를 실패로 만들지 않는다(RVW-03).
                items = (body["items"]?.takeUnless { it is JsonNull }?.jsonArray ?: emptyList())
                    .mapNotNull { runCatching { item(it.jsonObject, language) }.getOrNull() },
                attribution = body.string("attribution").orEmpty(),
                sourceUrl = body.string("source_url"),
                partial = body.string("availability") != "full",
            )
        }.getOrNull() ?: return ReviewResponse.Error(ReviewError.Network, sourceUrl = null)
        return ReviewResponse.Found(page)
    }

    private fun item(o: JsonObject, asked: LocaleTag) = Review(
        author = o.string("author"),
        rating = o["rating"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull?.let { kotlin.math.round(it).toInt() },
        text = o.string("text") ?: error("리뷰 본문이 없다"),
        writtenAt = o.string("written_at")?.let { runCatching { Instant.parse(it) }.getOrNull() },
        language = o.string("language")?.let { runCatching { LocaleTag(it) }.getOrNull() } ?: asked,
        translated = o["translated"]?.jsonPrimitive?.booleanOrNull ?: false,
        originalText = o.string("original_text"),
    )
}

/** `GET /v1/cards/{id}/places?language=` 의 요약 필드 (PRD §10.3, 설계서 §6.1). */
class HttpPlaceSummaryProvider(private val client: ApiClient) : PlaceSummaryProvider {
    override suspend fun summaries(card: CardId, language: LocaleTag): PlaceSummaryResult {
        val result = client.get(listOf("v1", "cards", card.value, "places"), language)
        if (result !is ApiResult.Ok) return PlaceSummaryResult.Unavailable
        return runCatching {
            PlaceSummaryResult.Loaded(
                (result.body["places"]?.takeUnless { it is JsonNull }?.jsonArray ?: emptyList()).associate { e ->
                    val o = e.jsonObject
                    PlaceId(o.string("id") ?: error("장소 id 가 없다")) to PlaceSummary(
                        rating = o["rating"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull,
                        reviewCount = o["review_count"]?.jsonPrimitive?.intOrNull ?: 0,
                        excerpt = o.string("excerpt"),
                    )
                },
            )
        }.getOrDefault(PlaceSummaryResult.Unavailable)
    }
}

private fun JsonObject.string(key: String): String? =
    this[key]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

/**
 * `POST /v1/routes` (PRD §10.3). 도보만 보낸다(FR14). 공급자의 거리·시간·경로선을 그대로 쓰고, 빠진 값을 지어내지 않는다(FR15).
 */
class HttpRouteProvider(private val client: ApiClient) : RouteProvider {
    override suspend fun route(request: RouteRequest): RouteResult {
        val body = buildJsonObject {
            put("origin", point(request.origin))
            put("destination", point(request.destination))
            put("mode", if (request.mode == TravelMode.Transit) "transit" else "walk")
        }.toString()
        return when (val r = client.post(listOf("v1", "routes"), body)) {
            // 형식이 계약과 다르면 연결 문제가 아니다. 다시 해도 같으므로 미지원으로 본다. 출처 표기가 없는 경로는 보이지 않는다.
            is ApiResult.Ok -> runCatching {
                val o = r.body
                val attribution = requireNotNull(o.string("attribution"))
                RouteResult.Found(
                    Route(
                        distanceMeters = Math.round(o["distance_m"]!!.jsonPrimitive.doubleOrNull!!).toInt(),
                        durationSeconds = Math.round(o["duration_s"]!!.jsonPrimitive.doubleOrNull!!),
                        path = o["path"]!!.jsonArray.map { p -> p.jsonArray.let { Coordinate(it[0].jsonPrimitive.doubleOrNull!!, it[1].jsonPrimitive.doubleOrNull!!) } },
                        attribution = attribution,
                        transit = if (request.mode == TravelMode.Transit) transit(o["transit"]!!.jsonObject) else null,
                    ),
                )
            }.getOrElse { RouteResult.Failed(RouteFailure.Unsupported) }
            ApiResult.Timeout -> RouteResult.Failed(RouteFailure.Timeout)
            ApiResult.Network -> RouteResult.Failed(RouteFailure.Network)
            ApiResult.Misconfigured -> RouteResult.Failed(RouteFailure.Unsupported)
            is ApiResult.ServerError -> RouteResult.Failed(
                when (r.code) {
                    "NO_DATA", "NOT_FOUND" -> RouteFailure.NoRoute
                    "UNSUPPORTED", "INVALID_INPUT" -> RouteFailure.Unsupported
                    "UPSTREAM_TIMEOUT" -> RouteFailure.Timeout
                    else -> if (r.retryable == false) RouteFailure.Unsupported else RouteFailure.Network
                },
            )
        }
    }

    /** 대중교통 요약. 모르는 탈것이 오면 실패시킨다 — 버스를 걷기로 바꿔 보이지 않게. */
    private fun transit(o: JsonObject) = TransitSummary(
        transfers = o["transfers"]!!.jsonPrimitive.intOrNull!!,
        fareWon = o["fare_won"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.doubleOrNull?.let { Math.round(it).toInt() },
        steps = o["steps"]!!.jsonArray.map { s ->
            val step = s.jsonObject
            TransitStep(
                kind = when (step.string("kind")) {
                    "walk" -> TransitKind.Walk
                    "bus" -> TransitKind.Bus
                    "subway" -> TransitKind.Subway
                    else -> error("unknown transit kind")
                },
                lines = step["lines"]?.jsonArray?.mapNotNull { it.jsonPrimitive.content.takeIf(String::isNotBlank) }.orEmpty(),
                // 공급자 초·미터 그대로. 없으면 null — 0 이나 추정값으로 채우지 않는다.
                seconds = step.number("time_s")?.let(Math::round),
                distanceMeters = step.number("distance_m")?.let { Math.round(it).toInt() },
            )
        },
    )

    /** 값이 없거나 null 이면 null. 숫자가 아닌 값은 응답이 깨진 것이므로 던진다(미지원으로 처리된다). */
    private fun JsonObject.number(key: String): Double? =
        this[key]?.takeUnless { it is JsonNull }?.let { requireNotNull(it.jsonPrimitive.doubleOrNull) { key } }

    private fun point(c: Coordinate) = buildJsonObject {
        put("lat", c.lat)
        put("lng", c.lng)
    }
}
