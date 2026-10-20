package app.signkorean.ui

import androidx.compose.runtime.Composable
import app.signkorean.navigation.AppNavigator
import app.signkorean.navigation.Route
import app.signkorean.navigation.ScreenId
import app.signkorean.ui.learn.LearnScreen

/** 화면 ID → 화면. 아직 만들지 않은 화면은 자리 표시이며 진입점을 숨긴다(구현 계획 원칙 3). */
@Composable
fun AppScreen(route: Route, navigator: AppNavigator) {
    when (route.screen) {
        ScreenId.SC04 -> LearnScreen(
            onClose = { navigator.back() },
            onOpenPlaces = { card -> navigator.push(Route(ScreenId.SC08, card.value)) },
            onFinished = { navigator.replaceTop(Route(ScreenId.SC06)) },
        )
        else -> PlaceholderScreen(route)
    }
}
