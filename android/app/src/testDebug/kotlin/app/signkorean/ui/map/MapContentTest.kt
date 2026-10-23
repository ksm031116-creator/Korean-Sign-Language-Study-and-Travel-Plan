package app.signkorean.ui.map

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import app.signkorean.domain.location.Coordinate
import app.signkorean.domain.route.Route
import app.signkorean.domain.route.TransitKind
import app.signkorean.domain.route.TransitStep
import app.signkorean.domain.route.TransitSummary
import app.signkorean.domain.route.TravelMode
import app.signkorean.ui.designsystem.SignKoreanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** SC18 대중교통 표시 (최종 리뷰 후속). 수단 칩의 접근성 역할과 단계·남은 시간의 분 합계를 본다. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class MapContentTest {

    @get:Rule
    val compose = createComposeRule()

    private val a = Coordinate(37.57, 126.99)
    private val b = Coordinate(37.575, 126.985)

    private fun show(steps: List<TransitStep>, totalSeconds: Long) {
        val route = Route(1_000, totalSeconds, listOf(a, b), "Kakao Map", TransitSummary(0, null, steps))
        val state = MapUiState(
            loading = false,
            mode = TravelMode.Transit,
            points = listOf(MapPoint(a, null, null, "Start"), MapPoint(b, 1, null, "End")),
            legs = listOf(MapLegUi(0, 1, route, null)),
        )
        compose.setContent { SignKoreanTheme { MapContent(state, {}, {}, {}) } }
    }

    @Test
    fun `수단 칩은 라디오로 읽힌다`() {
        show(listOf(TransitStep(TransitKind.Walk, emptyList(), 60, 50)), 60)
        compose.onNodeWithText("Transit").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).assertIsSelected()
        compose.onNodeWithText("Walk").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
    }

    @Test
    fun `보이는 단계 분과 남은 분을 더하면 총 분이다`() {
        // 61초 단계 셋 + 217초. 총 400초 → 7분. 단계마다 올림하면 2+2+2 에 나머지 4분 = 10분이 되어 맞지 않는다.
        show(List(3) { TransitStep(TransitKind.Walk, emptyList(), 61, 50) }, 400)
        compose.onNodeWithText("7min", substring = true).assertExists()
        compose.onAllNodesWithText("Walk 1min").assertCountEquals(3)
        compose.onNodeWithText("Walking to stops and waiting · 4min").assertExists()
    }

    @Test
    fun `시간이 없는 단계는 시간 없이 쓰고 남은 시간은 쓰지 않는다`() {
        show(listOf(TransitStep(TransitKind.Bus, listOf("7212"), null, null), TransitStep(TransitKind.Walk, emptyList(), 120, 100)), 900)
        compose.onNodeWithText("Bus 7212").assertExists()
        compose.onNodeWithText("Walking to stops and waiting", substring = true).assertDoesNotExist()
    }
}
