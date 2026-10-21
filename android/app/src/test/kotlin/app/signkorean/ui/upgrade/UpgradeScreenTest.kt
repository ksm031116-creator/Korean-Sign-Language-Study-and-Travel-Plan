package app.signkorean.ui.upgrade

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.signkorean.ui.designsystem.SignKoreanTheme
import app.signkorean.ui.text.UiText
import app.signkorean.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SC21 과 Plan 탭 잠금의 그리기 (PRD §14.1, UX09). E-08·U-09·L10N-11 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w390dp-h1400dp")
class UpgradeScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private fun show(state: UpgradeUiState, onAction: (UpgradeAction) -> Unit = {}) = rule.setContent {
        SignKoreanTheme { UpgradeContent(state, onAction) }
    }

    @Test
    fun `L10N-11 가격 문자열을 그대로 보이고 Unlock 버튼에도 쓴다`() {
        show(UpgradeUiState(UpgradeFeature.Plan, price = "¥150", priceChecked = true))
        rule.onNodeWithText("¥150").assertIsDisplayed()
        rule.onNodeWithText("Unlock for ¥150").assertIsDisplayed()
    }

    @Test
    fun `북마크 한도에서 왔으면 위에 저장 수`() {
        show(UpgradeUiState(UpgradeFeature.Bookmark, savedCount = 5, price = "US$0.99", priceChecked = true))
        rule.onNodeWithText("You've saved 5 of 5 signs.").assertIsDisplayed()
    }

    @Test
    fun `E-08 결제 중에는 Unlock 과 Restore 가 눌리지 않는다`() {
        show(UpgradeUiState(UpgradeFeature.Plan, price = "US$0.99", priceChecked = true, busy = true))
        rule.onNodeWithTag(UpgradeTags.UNLOCK).assertIsNotEnabled()
        rule.onNodeWithTag(UpgradeTags.RESTORE).assertIsNotEnabled()
        rule.onNodeWithTag(UpgradeTags.PROGRESS).assertIsDisplayed()
    }

    @Test
    fun `실패 안내와 다시 시도`() {
        var actions = listOf<UpgradeAction>()
        show(UpgradeUiState(UpgradeFeature.Plan, priceChecked = true, failed = true, message = UiText.Res(R.string.upgrade_failed))) { actions = actions + it }
        rule.onNodeWithText("The payment didn't go through. You haven't been charged.").assertIsDisplayed()
        rule.onNodeWithText("Try again").performClick()
        assertEquals(listOf(UpgradeAction.Unlock), actions)
    }

    @Test
    fun `가격을 못 받으면 가격 없는 Unlock`() {
        show(UpgradeUiState(UpgradeFeature.Plan, price = null, priceChecked = true))
        rule.onNodeWithText("Unlock").assertIsDisplayed()
        rule.onNodeWithText("The price isn't available right now.").assertIsDisplayed()
    }

    @Test
    fun `무료 유지 안내와 유료 기능 세 가지`() {
        show(UpgradeUiState(UpgradeFeature.Plan, price = "US$0.99", priceChecked = true))
        rule.onNodeWithText("All signs, meanings, pronunciation and reviews stay free.").assertIsDisplayed()
        rule.onNodeWithText("Unlimited bookmarks").assertExists()
        rule.onNodeWithText("Walking routes & times").assertExists()
        rule.onNodeWithText("Day-by-day trip planner").assertExists()
    }

    @Test
    fun `U-09 무료 Plan 탭은 자물쇠와 기능명, 누르면 SC21`() {
        var opened = false
        rule.setContent { SignKoreanTheme { PlanLockedContent(onUpgrade = { opened = true }) } }
        rule.onNodeWithContentDescription("Locked. Trip planning. Opens the upgrade page.").performClick()
        assertEquals(true, opened)
    }
}
