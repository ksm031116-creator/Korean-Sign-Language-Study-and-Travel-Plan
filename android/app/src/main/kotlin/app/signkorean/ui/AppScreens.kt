package app.signkorean.ui

import androidx.compose.runtime.Composable
import app.signkorean.domain.content.AreaId
import app.signkorean.domain.learning.SessionSource
import app.signkorean.navigation.AppNavigator
import app.signkorean.navigation.Route
import app.signkorean.navigation.ScreenId
import app.signkorean.ui.explore.CollectionScreen
import app.signkorean.ui.explore.ExploreScreen
import app.signkorean.ui.learn.LearnScreen
import app.signkorean.ui.learn.ResultScreen
import app.signkorean.ui.saved.SavedScreen
import app.signkorean.ui.plan.PlanScreen
import app.signkorean.ui.upgrade.UpgradeFeature
import app.signkorean.ui.upgrade.UpgradeScreen

/** SC03 인자: "basics" 또는 "area:<지역 id>". */
object CollectionArg {
    const val BASICS = "basics"
    fun area(id: AreaId) = "area:${id.value}"

    fun parse(arg: String?): SessionSource? = when {
        arg == BASICS -> SessionSource.Basics
        arg != null && arg.startsWith("area:") -> SessionSource.Area(AreaId(arg.removePrefix("area:")))
        else -> null
    }
}

/** 화면 ID → 화면. 아직 만들지 않은 화면은 자리 표시이며 진입점을 숨긴다(구현 계획 원칙 3). */
@Composable
fun AppScreen(route: Route, navigator: AppNavigator) {
    when (route.screen) {
        ScreenId.SC02 -> ExploreScreen(
            onOpenBasics = { navigator.push(Route(ScreenId.SC03, CollectionArg.BASICS)) },
            onOpenArea = { id -> navigator.push(Route(ScreenId.SC03, CollectionArg.area(id))) },
            onResume = { navigator.push(Route(ScreenId.SC04)) },
            onOpenSettings = null, // SC10 은 Phase 20 에서 연다
        )
        ScreenId.SC03 -> {
            val source = CollectionArg.parse(route.arg)
            if (source == null) {
                PlaceholderScreen(route)
            } else {
                CollectionScreen(
                    source = source,
                    onOpenLearn = { navigator.push(Route(ScreenId.SC04)) },
                    onOpenPlace = null, // SC07 은 Phase 14 에서 연다
                    onBack = { navigator.back() },
                )
            }
        }
        ScreenId.SC04 -> LearnScreen(
            onClose = { navigator.back() },
            onOpenPlaces = { card -> navigator.push(Route(ScreenId.SC08, card.value)) },
            onFinished = { navigator.replaceTop(Route(ScreenId.SC06)) },
            onBookmarkLimit = { navigator.push(Route(ScreenId.SC21, UpgradeFeature.Bookmark.arg)) },
        )
        ScreenId.SC09 -> SavedScreen(
            onOpenLearn = { navigator.push(Route(ScreenId.SC04)) },
            onUpgrade = { navigator.push(Route(ScreenId.SC21, UpgradeFeature.Bookmark.arg)) },
        )
        // 무료면 잠금 안내(U-09). 유료 여행 목록은 Phase 17 에서 만든다.
        ScreenId.SC11 -> PlanScreen(
            onUpgrade = { navigator.push(Route(ScreenId.SC21, UpgradeFeature.Plan.arg)) },
            paidContent = { PlaceholderScreen(route) },
        )
        // 결제·복원·Not now 모두 호출한 화면으로 돌아간다. 이어서 할 동작은 보류 동작이 맡는다 (PRD §14.1).
        ScreenId.SC21 -> UpgradeScreen(UpgradeFeature.parse(route.arg), onClose = { navigator.back() })
        ScreenId.SC06 -> ResultScreen(
            onOpenLearn = { navigator.replaceTop(Route(ScreenId.SC04)) },
            onClose = { navigator.back() },
        )
        else -> PlaceholderScreen(route)
    }
}
