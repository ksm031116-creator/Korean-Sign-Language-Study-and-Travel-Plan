package app.signkorean.ui.reviews

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.signkorean.domain.common.LocaleTag
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.review.KakaoReviewLink
import app.signkorean.domain.review.LoadReviews
import app.signkorean.domain.review.PlacePageLookup
import app.signkorean.domain.review.Review
import app.signkorean.domain.review.ReviewError
import app.signkorean.domain.review.ReviewPage
import app.signkorean.domain.review.ReviewResponse
import app.signkorean.domain.review.ReviewState
import app.signkorean.domain.locale.ResolveAppLanguage
import app.signkorean.testing.ContentFixtures
import app.signkorean.testing.FakeCurrentLocale
import app.signkorean.testing.FakeReviewProvider
import app.signkorean.testing.InMemoryContentRepository
import app.signkorean.ui.designsystem.SignKoreanTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 리뷰 시트 (FR10·11). U-13·16, L10N-10 의 화면 쪽 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h2000dp")
class ReviewsTest {

    @get:Rule
    val rule = createComposeRule()

    private val en = LocaleTag("en")
    private val provider = FakeReviewProvider()
    private val pack = ContentFixtures.pack(places = listOf(ContentFixtures.place("gwangjang", name = "Gwangjang Market")))
    private val review = Review("Mina", 5, "Great tteok and kind people.", null, en, translated = true, originalText = "떡이 맛있고 친절해요.")
    private val page = ReviewPage(listOf(review), "Kakao Map", "https://place.map.kakao.com/1", partial = true)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    // 카카오 장소 페이지 찾기. null 이면 카카오맵 검색으로 간다.
    private var kakaoPage: String? = "https://place.map.kakao.com/10"
    private val lookup = object : PlacePageLookup {
        override suspend fun kakaoPlacePage(koName: String, at: app.signkorean.domain.location.Coordinate): String? = kakaoPage
    }

    private fun vm(placeId: String = "gwangjang", content: app.signkorean.domain.content.ContentPack = pack) = ReviewsViewModel(
        PlaceId(placeId), InMemoryContentRepository(content), LoadReviews(provider), ResolveAppLanguage(FakeCurrentLocale(selected = en)), KakaoReviewLink(lookup),
    )

    private fun show(state: ReviewsUiState, onAction: (ReviewsAction) -> Unit = {}) =
        rule.setContent { SignKoreanTheme { ReviewsContent(state, onAction) } }

    @Test
    fun `장소 이름·주소와 함께 리뷰를 불러오고, 실패하면 다시 시도한다`() = runTest {
        provider.answers[en] = ReviewResponse.Error(ReviewError.Timeout, null)
        val vm = vm()
        assertEquals("Gwangjang Market", vm.state.value.place?.name?.get(en))
        assertEquals(ReviewState.NetworkFailure, vm.state.value.reviews)
        provider.answers[en] = ReviewResponse.Found(page)
        vm.onAction(ReviewsAction.Retry)
        assertTrue(vm.state.value.reviews is ReviewState.Shown)
    }

    @Test
    fun `U-16 허용 목록의 링크만 연다`() = runTest {
        val vm = vm()
        vm.onAction(ReviewsAction.OpenLink("https://evil.example.com/x"))
        assertEquals(null, vm.effects.tryReceive().getOrNull())
        vm.onAction(ReviewsAction.OpenLink("https://place.map.kakao.com/1"))
        assertEquals(ReviewsEffect.OpenUrl("https://place.map.kakao.com/1"), vm.effects.tryReceive().getOrNull())
    }

    private fun loaded(r: ReviewState) = ReviewsUiState(loading = false, place = pack.places.first(), reviews = r)

    @Test
    fun `FR11 일부 목록임과 출처를 보인다, 공급자 번역은 Translated 와 원문 보기`() {
        var actions = listOf<ReviewsAction>()
        show(loaded(ReviewState.Shown(page, en, fallback = false))) { actions = actions + it }
        rule.onNodeWithText("Showing some reviews from Kakao Map.").assertIsDisplayed()
        rule.onNodeWithText("Translated").assertIsDisplayed()
        rule.onNodeWithText("Great tteok and kind people.").assertIsDisplayed()
        rule.onNodeWithText("Show original").performClick()
        assertEquals(listOf<ReviewsAction>(ReviewsAction.ToggleOriginal(0)), actions)
    }

    @Test
    fun `원문 보기를 켜면 원문을 보인다`() {
        show(loaded(ReviewState.Shown(page, en, fallback = false)).copy(showingOriginal = setOf(0)))
        rule.onNodeWithText("떡이 맛있고 친절해요.").assertIsDisplayed()
    }

    @Test
    fun `L10N-10 영어로 대신 보이면 영어 리뷰라고 알린다`() {
        show(loaded(ReviewState.Shown(page, en, fallback = true)))
        rule.onNodeWithText("Reviews in English").assertIsDisplayed()
    }

    @Test
    fun `U-13 리뷰 없음`() {
        show(loaded(ReviewState.NoReviews("https://place.map.kakao.com/1")))
        rule.onNodeWithText("No reviews for this place yet.").assertIsDisplayed()
        rule.onNodeWithText("Open the original page").assertIsDisplayed()
    }

    @Test
    fun `U-13 언어 없음 + 원문 링크`() {
        show(loaded(ReviewState.LanguageUnavailable("https://place.map.kakao.com/1")))
        rule.onNodeWithText("English reviews unavailable.").assertIsDisplayed()
        rule.onNodeWithText("Open the original page").assertIsDisplayed()
    }

    @Test
    fun `U-13 장소 불일치는 다른 장소 리뷰 없이 안내만`() {
        show(loaded(ReviewState.PlaceMismatch))
        rule.onNodeWithText("We couldn't match this place with the review service, so no reviews are shown.").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Open the original page").fetchSemanticsNodes().size)
    }

    @Test
    fun `U-13 네트워크 실패는 다시 시도`() {
        var actions = listOf<ReviewsAction>()
        show(loaded(ReviewState.NetworkFailure)) { actions = actions + it }
        rule.onNodeWithText("Couldn't load reviews.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        assertEquals(listOf<ReviewsAction>(ReviewsAction.Retry), actions)
    }

    @Test
    fun `U-16 허용되지 않은 원문 링크는 버튼을 그리지 않는다`() {
        show(loaded(ReviewState.NoReviews("https://evil.example.com/x")))
        assertEquals(0, rule.onAllNodesWithText("Open the original page").fetchSemanticsNodes().size)
    }

    @Test
    fun `서비스 미연결 안내`() {
        show(loaded(ReviewState.NotConnected))
        rule.onNodeWithText("Reviews can't be shown in the app. You can read them on Kakao Map.").assertIsDisplayed()
    }

    @Test
    fun `KRL 카카오맵에서 리뷰 보기 - 이름이 같은 장소 페이지, 없으면 검색`() = runTest {
        val vm = vm()
        vm.onAction(ReviewsAction.OpenKakao)
        assertEquals(ReviewsEffect.OpenUrl("https://place.map.kakao.com/10"), vm.effects.tryReceive().getOrNull())
        kakaoPage = null
        vm.onAction(ReviewsAction.OpenKakao)
        val search = (vm.effects.tryReceive().getOrNull() as ReviewsEffect.OpenUrl).url
        assertTrue(search, search.startsWith("https://map.kakao.com/link/search/"))
    }

    @Test
    fun `KRL 예시 장소는 카카오맵 버튼이 없고 눌러도 아무 일도 없다`() = runTest {
        val example = ContentFixtures.pack(places = listOf(ContentFixtures.place("ex", name = "Example pharmacy", isExample = true)))
        val vm = vm("ex", example)
        assertEquals(false, vm.state.value.kakaoAvailable)
        vm.onAction(ReviewsAction.OpenKakao)
        assertEquals(null, vm.effects.tryReceive().getOrNull())
        show(ReviewsUiState(loading = false, place = example.places.first(), reviews = ReviewState.NotConnected))
        rule.onNodeWithText("View reviews on Kakao Map").assertDoesNotExist()
    }

    @Test
    fun `KRL 리뷰를 못 보일 때 카카오맵 버튼을 보인다, 원문 링크가 있으면 겹쳐 보이지 않는다`() {
        var actions = listOf<ReviewsAction>()
        val base = loaded(ReviewState.NotConnected).copy(kakaoAvailable = true)
        show(base) { actions = actions + it }
        rule.onNodeWithText("Reviews can't be shown in the app. You can read them on Kakao Map.").assertIsDisplayed()
        rule.onNodeWithText("View reviews on Kakao Map").performClick()
        assertEquals(listOf<ReviewsAction>(ReviewsAction.OpenKakao), actions)
    }

    @Test
    fun `KRL 원문 링크가 있으면 카카오맵 버튼을 따로 보이지 않는다`() {
        show(loaded(ReviewState.NoReviews("https://place.map.kakao.com/1")).copy(kakaoAvailable = true))
        rule.onNodeWithText("Open the original page").assertIsDisplayed()
        rule.onNodeWithText("View reviews on Kakao Map").assertDoesNotExist()
    }

    @Test
    fun `KRL 찾는 중에 시트를 닫으면 나중에 다시 열어도 카카오맵이 저절로 열리지 않는다`() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<String?>()
        val slow = object : PlacePageLookup {
            override suspend fun kakaoPlacePage(koName: String, at: app.signkorean.domain.location.Coordinate): String? = gate.await()
        }
        val vm = ReviewsViewModel(PlaceId("gwangjang"), InMemoryContentRepository(pack), LoadReviews(provider), ResolveAppLanguage(FakeCurrentLocale(selected = en)), KakaoReviewLink(slow))
        vm.onAction(ReviewsAction.OpenKakao)
        assertTrue(vm.state.value.openingKakao)
        // 찾는 중 두 번 눌러도 한 번만.
        vm.onAction(ReviewsAction.OpenKakao)
        vm.onAction(ReviewsAction.Closed)
        assertEquals(false, vm.state.value.openingKakao)
        gate.complete("https://place.map.kakao.com/10")
        vm.onAction(ReviewsAction.Opened)
        assertEquals(null, vm.effects.tryReceive().getOrNull())
    }

    @Test
    fun `KRL 공급자가 다른 장소라고 하면 카카오맵 버튼도 보이지 않는다`() {
        show(loaded(ReviewState.PlaceMismatch).copy(kakaoAvailable = true))
        rule.onNodeWithText("View reviews on Kakao Map").assertDoesNotExist()
    }

    @Test
    fun `KRL 연결 실패에서도 카카오맵 버튼을 보인다, 찾는 중에는 누를 수 없다`() {
        show(loaded(ReviewState.NetworkFailure).copy(kakaoAvailable = true, openingKakao = true))
        rule.onNodeWithText("View reviews on Kakao Map").assertIsNotEnabled()
    }

    @Test
    fun `KRL 예시 장소에는 카카오맵을 가리키는 안내 대신 예시라고 알린다`() {
        val example = ContentFixtures.place("ex", name = "Example pharmacy", isExample = true)
        show(ReviewsUiState(loading = false, place = example, reviews = ReviewState.NotConnected, kakaoAvailable = false))
        rule.onNodeWithText("This is an example place, so it has no reviews.").assertIsDisplayed()
        rule.onNodeWithText("You can read them on Kakao Map", substring = true).assertDoesNotExist()
    }
}
