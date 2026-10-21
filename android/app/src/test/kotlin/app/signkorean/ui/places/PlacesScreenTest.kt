package app.signkorean.ui.places

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.signkorean.R
import app.signkorean.domain.content.CardKind
import app.signkorean.domain.content.PhotoLicense
import app.signkorean.domain.content.PlaceId
import app.signkorean.domain.content.PlacePhoto
import app.signkorean.domain.place.AreaPlaces
import app.signkorean.domain.place.PlaceSummary
import app.signkorean.testing.ContentFixtures
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.text.UiText
import app.signkorean.ui.upgrade.UpgradeFeature
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 장소 사진·SC08·SC07 그리기. C-09·10, U-11 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h2000dp")
class PlacesScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private val type3 = PlacePhoto("https://tong.visitkorea.or.kr/a.jpg", PhotoLicense.KoglType3, "한국관광공사", null)
    private val type1 = PlacePhoto("https://tong.visitkorea.or.kr/b.jpg", PhotoLicense.KoglType1, "한국관광공사", null)
    private val ccBy = PlacePhoto("https://upload.wikimedia.org/c.jpg", PhotoLicense.CcBy("Jane Doe", "4.0"), "Wikimedia Commons", null)
    private val gukbap = ContentFixtures.card("t-market-1", "국밥", CardKind.PlaceSign("market"))
    private val gwangjang = ContentFixtures.place("gwangjang", areaId = "jongno", name = "Gwangjang Market").copy(photo = type3)
    private val example = ContentFixtures.place("example-market", name = "Example market", isExample = true)

    @Test
    fun `C-10 사진 출처 표기 - 공공누리 유형과 CC BY 의 저작자·버전`() {
        assertEquals(UiText.Res(R.string.photo_credit_kogl3, listOf("한국관광공사")), PhotoCredits.of(type3))
        assertEquals(UiText.Res(R.string.photo_credit_kogl1, listOf("한국관광공사")), PhotoCredits.of(type1))
        assertEquals(UiText.Res(R.string.photo_credit_ccby, listOf("Jane Doe", "4.0", "Wikimedia Commons")), PhotoCredits.of(ccBy))
    }

    @Test
    fun `asset 사진은 앱 안의 파일 주소로 바꾼다`() {
        assertEquals("file:///android_asset/content/photos/ddp.jpg", PhotoModel.of("asset://content/photos/ddp.jpg"))
        assertEquals("https://x/y.jpg", PhotoModel.of("https://x/y.jpg"))
    }

    @Test
    fun `U-11 제3유형은 자르지 않고 원본 비율, 출처를 보인다`() {
        rule.setContent { SignKoreanTheme { PlacePhotoView(type3, fallbackColor = "#2f6f5e") } }
        rule.onNodeWithTag(PhotoTags.UNCROPPED).assertExists()
        rule.onNodeWithText("한국관광공사 · KOGL Type 3 (no changes)").assertIsDisplayed()
    }

    @Test
    fun `제1유형은 틀에 맞춰 잘라도 되고 출처를 보인다`() {
        rule.setContent { SignKoreanTheme { PlacePhotoView(type1, fallbackColor = "#2f6f5e") } }
        rule.onNodeWithTag(PhotoTags.CROPPED).assertExists()
        rule.onNodeWithText("한국관광공사 · KOGL Type 1").assertIsDisplayed()
    }

    @Test
    fun `사진이 없으면 자리 표시`() {
        rule.setContent { SignKoreanTheme { PlacePhotoView(null, fallbackColor = "#2f6f5e") } }
        rule.onNodeWithTag(PhotoTags.PLACEHOLDER).assertExists()
    }

    private fun placesState(paid: Boolean = false) = PlacesUiState(
        loading = false,
        card = gukbap,
        groups = listOf(
            AreaPlaces(ContentFixtures.area("jongno"), listOf(gwangjang)),
            AreaPlaces(ContentFixtures.area("myeongdong"), listOf(example)),
        ),
        summary = SummaryState.Loaded(mapOf(PlaceId("gwangjang") to PlaceSummary(4.5, 120, "Great tteok."))),
        paid = paid,
    )

    @Test
    fun `SC08 장소 카드 - 이름, 평점, 리뷰 수, 발췌, 주소`() {
        rule.setContent { SignKoreanTheme { PlacesContent(placesState(), {}, {}) } }
        rule.onNodeWithText("Gwangjang Market").assertExists()
        rule.onNodeWithText("Great tteok.").assertExists()
        rule.onNodeWithTag(PlacesTags.rating(PlaceId("gwangjang"))).assertExists()
        rule.onNodeWithText("2 places").assertExists()
    }

    @Test
    fun `C-09 SC08 의 Example 장소에는 Example 표시`() {
        rule.setContent { SignKoreanTheme { PlacesContent(placesState(), {}, {}) } }
        rule.onNodeWithTag(PlacesTags.example(PlaceId("example-market"))).assertExists()
    }

    @Test
    fun `SC08 무료면 Route·Add 는 자물쇠, 누르면 SC21`() {
        var actions = listOf<PlacesAction>()
        rule.setContent { SignKoreanTheme { PlacesContent(placesState(), { actions = actions + it }, {}) } }
        rule.onAllNodesWithText("Walking route")[0].performClick()
        rule.onAllNodesWithText("Add to plan")[0].performClick()
        assertEquals(listOf<PlacesAction>(PlacesAction.Upgrade(UpgradeFeature.Route), PlacesAction.Upgrade(UpgradeFeature.Plan)), actions)
    }

    @Test
    fun `SC08 유료면 아직 없는 화면의 진입점은 그리지 않는다`() {
        rule.setContent { SignKoreanTheme { PlacesContent(placesState(paid = true), {}, {}) } }
        assertEquals(0, rule.onAllNodesWithText("Walking route").fetchSemanticsNodes().size)
    }

    @Test
    fun `SC07 Example 안내와 간판 칩`() {
        val state = PlaceDetailUiState(loading = false, place = example, area = ContentFixtures.area(), signs = listOf(gukbap), selectedId = gukbap.id)
        rule.setContent { SignKoreanTheme { PlaceDetailContent(state, {}, {}) } }
        rule.onNodeWithText("This is an example of this kind of place, not a specific shop.").assertIsDisplayed()
        rule.onNodeWithText("Practise these signs").assertExists()
        rule.onNodeWithContentDescription("Locked. Add to plan. Opens the upgrade page.").assertExists()
    }
}
