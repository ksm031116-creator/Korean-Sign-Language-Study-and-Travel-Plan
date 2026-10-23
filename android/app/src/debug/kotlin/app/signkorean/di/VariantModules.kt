package app.signkorean.di

import app.signkorean.BuildConfig
import app.signkorean.R
import app.signkorean.ui.plan.DebugRelock
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

/**
 * 디버그 빌드 전용 조립 (설계서 §5.5 DebugOverrides). 이 소스 세트는 릴리스 APK 에 들어가지 않는다.
 *
 * 에뮬레이터 이미지에는 Play 스토어가 없고, 실결제는 Play Console 에 올린 빌드에서만 된다. 그래서 디버그 빌드는
 * 기본으로 모의 결제를 쓴다. 실기기에서 Play 결제를 시험하려면 `./gradlew installDebug -PrealBilling=true`.
 */
// 중계 서버 주소를 넣은 디버그 빌드는 실제 서버를 쓴다(표본으로 덮지 않는다).
val variantModules: List<Module> = (if (BuildConfig.API_BASE_URL.isBlank()) listOf(sampleDataModule) else emptyList()) + if (BuildConfig.SIMULATED_BILLING) {
    listOf(
        simulatedBillingModule,
        // 다시 잠그기는 디버그 빌드에만 있다(설정 화면의 확인용 버튼).
        module {
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
        val walkSeconds = (meters / 1.2).toLong()
        if (request.mode == app.signkorean.domain.route.TravelMode.Transit) {
            // 표본 대중교통(리뷰 L9): 걷기 → 지하철 → 걷기. 도보 표본을 대중교통으로 보이지 않게 단계와 출처를 따로 둔다.
            val ride = (walkSeconds / 3).coerceAtLeast(120)
            val transit = app.signkorean.domain.route.TransitSummary(
                transfers = 0,
                fareWon = 1_550,
                steps = listOf(
                    app.signkorean.domain.route.TransitStep(app.signkorean.domain.route.TransitKind.Walk, emptyList(), 240, 250),
                    app.signkorean.domain.route.TransitStep(app.signkorean.domain.route.TransitKind.Subway, listOf("Sample line"), ride, meters),
                    app.signkorean.domain.route.TransitStep(app.signkorean.domain.route.TransitKind.Walk, emptyList(), 180, 200),
                ),
            )
            return app.signkorean.domain.route.RouteResult.Found(
                app.signkorean.domain.route.Route(meters + 450, ride + 420 + 300, listOf(a, bend, b), attribution = "Sample routes (debug build), not real transit directions", transit = transit),
            )
        }
        return app.signkorean.domain.route.RouteResult.Found(
            app.signkorean.domain.route.Route(meters, walkSeconds, listOf(a, bend, b), attribution = "Sample routes (debug build), not real walking directions"),
        )
    }
}
