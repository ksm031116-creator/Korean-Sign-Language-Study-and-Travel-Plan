package app.signkorean.di

import app.signkorean.BuildConfig
import app.signkorean.R
import app.signkorean.domain.entitlement.EntitlementGateway
import app.signkorean.domain.entitlement.PurchaseResult
import app.signkorean.domain.entitlement.StorePurchase
import app.signkorean.domain.entitlement.StoreQuery
import app.signkorean.ui.plan.DebugRelock
import kotlinx.coroutines.delay
import org.koin.core.module.Module
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.ContentRepository
import app.signkorean.domain.place.PlaceListing
import app.signkorean.domain.place.PlaceSummary
import app.signkorean.domain.place.PlaceSummaryProvider
import app.signkorean.domain.place.PlaceSummaryResult
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.review.Review
import app.signkorean.domain.review.ReviewError
import app.signkorean.domain.review.ReviewPage
import app.signkorean.domain.review.ReviewProvider
import app.signkorean.domain.review.ReviewResponse
import kotlin.math.abs
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import java.util.UUID

/**
 * 디버그 빌드 전용 조립 (설계서 §5.5 DebugOverrides). 이 소스 세트는 릴리스 APK 에 들어가지 않는다.
 *
 * 에뮬레이터 이미지에는 Play 스토어가 없고, 실결제는 Play Console 에 올린 빌드에서만 된다. 그래서 디버그 빌드는
 * 기본으로 모의 결제를 쓴다. 실기기에서 Play 결제를 시험하려면 `./gradlew installDebug -PrealBilling=true`.
 */
// 중계 서버 주소를 넣은 디버그 빌드는 실제 서버를 쓴다(표본으로 덮지 않는다).
val variantModules: List<Module> = (if (BuildConfig.API_BASE_URL.isBlank()) listOf(sampleDataModule) else emptyList()) + if (BuildConfig.SIMULATED_BILLING) {
    listOf(
        module {
            single { SimulatedBillingGateway(androidContext()) }
            single<EntitlementGateway> { get<SimulatedBillingGateway>() }
            single<DebugRelock> {
                val gateway = get<SimulatedBillingGateway>()
                object : DebugRelock {
                    override val label = R.string.debug_relock
                    override suspend fun relock() = gateway.relock()
                }
            }
        },
    )
} else {
    emptyList()
}

/**
 * 디버그 빌드의 표본 데이터. 중계 서버가 없어도 SC08 의 평점·리뷰 수·발췌 화면을 확인할 수 있게 한다(AT04,
 * Phase 14 리뷰 PLC-02). 발췌에 "Sample" 을 붙여 실제 리뷰로 보이지 않게 한다. 릴리스는 "없음"이다(지어내지 않는다).
 */
// getter: 이 파일의 최상위 값은 위에서부터 초기화되므로, 위의 variantModules 가 읽을 때 아직 null 이 아니게.
private val sampleDataModule get() = module {
    single<PlaceSummaryProvider> { SamplePlaceSummaries(get()) }
    single<ReviewProvider> { SampleReviews }
    single<app.signkorean.domain.route.RouteProvider> { SampleRoutes }
}

/**
 * 표본 리뷰. 영어만 있다 — 일본어 화면에서는 "영어 리뷰" 대체(L10N-10)가 보인다. 작성자 이름에 "Sample" 을 넣어
 * 실제 리뷰로 보이지 않게 한다.
 */
object SampleReviews : ReviewProvider {
    override suspend fun reviews(place: PlaceId, language: LocaleTag): ReviewResponse {
        if (language.value != "en") return ReviewResponse.Error(ReviewError.NoData, sourceUrl = null)
        val items = listOf(
            Review("Sample reviewer A", 5, "Sample (debug build): easy to find from the station, and the sign above the door matched the card.", null, language, translated = false, originalText = null),
            Review("Sample reviewer B", 4, "Sample (debug build): a provider translation of a Korean review.", null, language, translated = true, originalText = "샘플(디버그 빌드): 한국어 원문 리뷰입니다."),
        )
        return ReviewResponse.Found(ReviewPage(items, attribution = "Sample data (debug build)", sourceUrl = null, partial = true))
    }
}

class SamplePlaceSummaries(private val content: ContentRepository) : PlaceSummaryProvider {
    override suspend fun summaries(card: CardId, language: LocaleTag): PlaceSummaryResult {
        val pack = content.load()
        val sign = pack.cards.firstOrNull { it.id == card } ?: return PlaceSummaryResult.Loaded(emptyMap())
        return PlaceSummaryResult.Loaded(
            PlaceListing.forCard(pack, sign).flatMap { it.places }.associate { place ->
                val seed = abs(place.id.value.hashCode())
                place.id to PlaceSummary(
                    rating = 3.6 + (seed % 13) / 10.0,
                    reviewCount = 12 + seed % 480,
                    excerpt = "Sample (debug build): visitors mention the " + sign.romanization + " sign near the entrance.",
                )
            },
        )
    }
}

/**
 * 모의 결제. 결제는 잠깐 기다린 뒤 성공하고, 구매는 프로세스가 살아 있는 동안만 기억한다(다시 켜면 캐시의 권한을 쓰고,
 * 시작 확인에서 구매가 없으니 Free 로 돌아간다 — 실제 환불과 같은 경로라 E-11 도 눈으로 볼 수 있다).
 */
/**
 * 디버그 빌드의 모의 결제. 결제 버튼을 누르면 0.8초 뒤 구매 완료가 된다(실제 돈은 오가지 않는다).
 * 구매 기록은 기기에 저장해 앱을 껐다 켜도 남는다 — 메모리에만 두면 다시 켤 때 무료로 돌아갔다(BIL-01).
 */
class SimulatedBillingGateway(context: android.content.Context) : EntitlementGateway {
    private val prefs = context.applicationContext.getSharedPreferences("simulated_billing", android.content.Context.MODE_PRIVATE)

    // "토큰|acknowledged" 를 한 줄씩 저장한다.
    @Volatile private var owned: List<StorePurchase> = prefs.getStringSet(KEY, emptySet()).orEmpty().sorted().mapNotNull { line ->
        val parts = line.split('|')
        if (parts.size == 2) StorePurchase(parts[0], StorePurchase.State.Purchased, acknowledged = parts[1] == "1") else null
    }
        set(value) {
            field = value
            prefs.edit().putStringSet(KEY, value.map { it.token + "|" + (if (it.acknowledged) "1" else "0") }.toSet()).commit()
        }

    override suspend fun queryPurchases(): StoreQuery = StoreQuery.Success(owned)

    // 스토어가 주는 문자열 자리. 실제 앱은 이 값을 Play 에서 받는다 (L10N-11).
    override suspend fun localizedPrice(): String = "US$0.99"

    override suspend fun purchase(): PurchaseResult {
        delay(800)
        val bought = StorePurchase("simulated-" + UUID.randomUUID(), StorePurchase.State.Purchased, acknowledged = false)
        owned = owned + bought
        return PurchaseResult.Purchased(bought)
    }

    override suspend fun acknowledge(token: String): Boolean {
        owned = owned.map { if (it.token == token) it.copy(acknowledged = true) else it }
        return true
    }

    fun relock() {
        owned = emptyList()
    }

    private companion object {
        const val KEY = "owned"
    }
}

/**
 * 표본 경로(디버그 빌드 전용). 중계 서버 없이 SC18·SC13 화면을 확인하려고 쓴다. 출처 표기에 "Sample" 을 넣어 실제 도보
 * 경로로 보이지 않게 한다. 릴리스는 서버가 없으면 "지원 안 됨"이다(FR15 — 직선거리로 대신하지 않는다).
 */
object SampleRoutes : app.signkorean.domain.route.RouteProvider {
    override suspend fun route(request: app.signkorean.domain.route.RouteRequest): app.signkorean.domain.route.RouteResult {
        val a = request.origin
        val b = request.destination
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = Math.sin(dLat / 2).let { it * it } + Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLng / 2).let { it * it }
        val meters = (2 * r * Math.asin(Math.sqrt(h)) * 1.3).toInt().coerceAtLeast(1)
        val bend = app.signkorean.domain.location.Coordinate((a.lat + b.lat) / 2 + (b.lng - a.lng) * 0.15, (a.lng + b.lng) / 2 - (b.lat - a.lat) * 0.15)
        return app.signkorean.domain.route.RouteResult.Found(
            app.signkorean.domain.route.Route(meters, (meters / 1.2).toLong(), listOf(a, bend, b), attribution = "Sample routes (debug build), not real walking directions"),
        )
    }
}
