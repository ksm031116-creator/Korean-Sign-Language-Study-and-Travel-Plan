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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    data class ServerError(val code: String?, val sourceUrl: String?) : ApiResult
    data object Timeout : ApiResult
    data object Network : ApiResult
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

    suspend fun get(pathSegments: List<String>, language: LocaleTag): ApiResult = withContext(io) {
        val path = pathSegments.joinToString("/") { segment(it) }
        val url = URL("${baseUrl.trimEnd('/')}/$path?language=${URLEncoder.encode(language.value, "UTF-8")}")
        val conn = try {
            url.openConnection() as HttpURLConnection
        } catch (_: IOException) {
            return@withContext ApiResult.Network
        }
        try {
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Accept-Language", language.value)
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val body = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
                ?: return@withContext ApiResult.Network
            if (status in 200..299) {
                ApiResult.Ok(body)
            } else {
                val error = body["error"]?.takeUnless { it is JsonNull }?.jsonObject
                ApiResult.ServerError(error?.string("code"), error?.string("source_url"))
            }
        } catch (_: SocketTimeoutException) {
            ApiResult.Timeout
        } catch (_: IOException) {
            ApiResult.Network
        } finally {
            conn.disconnect()
        }
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
        is ApiResult.ServerError -> when (result.code) {
            "NO_DATA" -> ReviewError.NoData
            "NOT_FOUND" -> ReviewError.NotFound
            "UPSTREAM_TIMEOUT" -> ReviewError.Timeout
            "RATE_LIMITED" -> ReviewError.RateLimited
            "UNSUPPORTED", "INVALID_INPUT" -> ReviewError.Unsupported
            else -> ReviewError.Network
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
                items = (body["items"]?.takeUnless { it is JsonNull }?.jsonArray ?: emptyList()).map { item(it.jsonObject, language) },
                attribution = body.string("attribution").orEmpty(),
                sourceUrl = body.string("source_url"),
                partial = body.string("availability") != "full",
            )
        }.getOrNull() ?: return ReviewResponse.Error(ReviewError.Network, sourceUrl = null)
        return ReviewResponse.Found(page)
    }

    private fun item(o: JsonObject, asked: LocaleTag) = Review(
        author = o.string("author"),
        rating = o["rating"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.intOrNull,
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
