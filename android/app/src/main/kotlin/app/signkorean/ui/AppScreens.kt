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
import app.signkorean.ui.plan.PlacePickerScreen
import app.signkorean.ui.plan.DayPlanScreen
import app.signkorean.ui.plan.TripFormScreen
import app.signkorean.ui.plan.TripListScreen
import app.signkorean.domain.trip.TripId
import app.signkorean.domain.content.CardId
import app.signkorean.domain.content.PlaceId
import app.signkorean.ui.places.PlaceDetailScreen
import app.signkorean.ui.places.PlacesScreen
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

/** SC12 인자: "new", "new-sheet"(SC15 에서 옴 — 저장하면 시트로 돌아간다), "edit:<여행 id>". */
object TripFormArg {
    const val NEW = "new"
    const val NEW_FROM_SHEET = "new-sheet"
    fun edit(id: TripId) = "edit:${id.value}"

    data class Parsed(val trip: TripId?, val returnToSheet: Boolean)

    fun parse(arg: String?): Parsed = when {
        arg == NEW_FROM_SHEET -> Parsed(null, returnToSheet = true)
        arg != null && arg.startsWith("edit:") -> Parsed(TripId(arg.removePrefix("edit:")), returnToSheet = false)
        else -> Parsed(null, returnToSheet = false)
    }
}

/** SC13·SC14 의 하루 인자: "<여행 id>|<yyyy-MM-dd>". */
object TripDayArg {
    fun of(trip: TripId, date: java.time.LocalDate) = "${trip.value}|$date"
    fun parse(arg: String?): Pair<TripId, java.time.LocalDate>? {
        val (id, date) = arg?.split("|")?.takeIf { it.size == 2 } ?: return null
        return runCatching { TripId(id) to java.time.LocalDate.parse(date) }.getOrNull()
    }
}

/** SC18 인자: "day:<여행 id>|<날짜>" 또는 "place:<장소 id>". */
object MapArg {
    fun day(trip: TripId, date: java.time.LocalDate) = "day:" + TripDayArg.of(trip, date)
    fun place(id: PlaceId) = "place:${id.value}"

    fun parse(arg: String?): app.signkorean.ui.map.MapTarget? = when {
        arg == null -> null
        arg.startsWith("day:") -> TripDayArg.parse(arg.removePrefix("day:"))?.let { (t, d) -> app.signkorean.ui.map.MapTarget.Day(t, d) }
        arg.startsWith("place:") -> arg.removePrefix("place:").takeIf { it.isNotBlank() }?.let { app.signkorean.ui.map.MapTarget.ToPlace(PlaceId(it)) }
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
            onResume = { navigator.openLearn() },
            onOpenSettings = null, // SC10 은 Phase 20 에서 연다
        )
        ScreenId.SC03 -> {
            val source = CollectionArg.parse(route.arg)
            if (source == null) {
                PlaceholderScreen(route)
            } else {
                CollectionScreen(
                    source = source,
                    onOpenLearn = { navigator.openLearn() },
                    onOpenPlace = { id -> navigator.push(Route(ScreenId.SC07, id)) },
                    onBack = { navigator.back() },
                )
            }
        }
        ScreenId.SC04 -> LearnScreen(
            onClose = { navigator.back() },
            onOpenPlaces = { card -> navigator.push(Route(ScreenId.SC08, card.value)) },
            onFinished = { navigator.replaceTop(Route(ScreenId.SC06)) },
            onBookmarkLimit = { navigator.push(Route(ScreenId.SC21, UpgradeFeature.BookmarkLimit.arg)) },
        )
        ScreenId.SC09 -> SavedScreen(
            onOpenLearn = { navigator.openLearn() },
            onUpgrade = { navigator.push(Route(ScreenId.SC21, UpgradeFeature.Bookmarks.arg)) },
            onOpenPlaces = { card -> navigator.push(Route(ScreenId.SC08, card.value)) },
        )
        // 무료면 잠금 안내(U-09). 유료 여행 목록은 Phase 17 에서 만든다.
        ScreenId.SC11 -> PlanScreen(
            onUpgrade = { navigator.push(Route(ScreenId.SC21, UpgradeFeature.Plan.arg)) },
            // 무료가 된 뒤에도 여행은 보인다(E-12). 만들기·고치기는 SC21.
            freeContent = {
                TripListScreen(
                    onNewTrip = { navigator.push(Route(ScreenId.SC21, UpgradeFeature.Plan.arg)) },
                    onOpenTrip = { id -> navigator.push(Route(ScreenId.SC13, id.value)) },
                    readOnly = true,
                    onUpgrade = { navigator.push(Route(ScreenId.SC21, UpgradeFeature.Plan.arg)) },
                )
            },
            paidContent = {
                TripListScreen(
                    onNewTrip = { navigator.push(Route(ScreenId.SC12, TripFormArg.NEW)) },
                    onOpenTrip = { id -> navigator.push(Route(ScreenId.SC13, id.value)) },
                )
            },
        )
        // 간판의 장소 목록. Learn 아래 스와이프로 온다(FR09). 닫으면 같은 카드로 돌아간다.
        ScreenId.SC08 -> route.arg?.let { card ->
            PlacesScreen(
                card = CardId(card),
                onClose = { navigator.back() },
                onOpenPlace = { id -> navigator.push(Route(ScreenId.SC07, id.value)) },
                onUpgrade = { f -> navigator.push(Route(ScreenId.SC21, f.arg)) },
                onCreateTrip = { navigator.push(Route(ScreenId.SC12, TripFormArg.NEW_FROM_SHEET)) },
                onRoute = { id -> navigator.push(Route(ScreenId.SC18, MapArg.place(id))) },
            )
        } ?: PlaceholderScreen(route)
        ScreenId.SC07 -> route.arg?.let { place ->
            PlaceDetailScreen(
                place = PlaceId(place),
                onClose = { navigator.back() },
                onOpenLearn = { navigator.openLearn() },
                onUpgrade = { f -> navigator.push(Route(ScreenId.SC21, f.arg)) },
                onCreateTrip = { navigator.push(Route(ScreenId.SC12, TripFormArg.NEW_FROM_SHEET)) },
                onRoute = { id -> navigator.push(Route(ScreenId.SC18, MapArg.place(id))) },
            )
        } ?: PlaceholderScreen(route)
        ScreenId.SC12 -> {
            val arg = TripFormArg.parse(route.arg)
            TripFormScreen(
                tripId = arg.trip,
                // 새 여행은 그 여행의 일정(SC13)으로 간다. 추가 시트에서 왔으면 시트로 돌아간다(PRD §14 SC15).
                onSaved = { id, created -> if (created && !arg.returnToSheet) navigator.replaceTop(Route(ScreenId.SC13, id.value)) else navigator.back() },
                onDeleted = { navigator.selectTab(app.signkorean.navigation.RootTab.Plan) },
                onClose = { navigator.back() },
                onUpgrade = { navigator.push(Route(ScreenId.SC21, UpgradeFeature.Plan.arg)) },
            )
        }
        // SC13 인자: 여행 id, 또는 "여행 id|날짜"(그날을 열어 둔다).
        ScreenId.SC13 -> route.arg?.let { arg ->
            val (trip, date) = TripDayArg.parse(arg) ?: (TripId(arg) to null)
            DayPlanScreen(
                trip = trip,
                date = date,
                onClose = { navigator.back() },
                onOpenPicker = { id, d -> navigator.push(Route(ScreenId.SC14, TripDayArg.of(id, d))) },
                onEditTrip = { id -> navigator.push(Route(ScreenId.SC12, TripFormArg.edit(id))) },
                onOpenMap = { id, d -> navigator.push(Route(ScreenId.SC18, MapArg.day(id, d))) },
                onUpgrade = { f -> navigator.push(Route(ScreenId.SC21, f.arg)) },
            )
        } ?: PlaceholderScreen(route)
        ScreenId.SC18 -> MapArg.parse(route.arg)?.let { target ->
            app.signkorean.ui.map.MapScreen(target, onClose = { navigator.back() }, onUpgrade = { f -> navigator.push(Route(ScreenId.SC21, f.arg)) })
        } ?: PlaceholderScreen(route)
        ScreenId.SC14 -> TripDayArg.parse(route.arg)?.let { (trip, date) ->
            PlacePickerScreen(trip, date, onClose = { navigator.back() })
        } ?: PlaceholderScreen(route)
        // 결제·복원·Not now 모두 호출한 화면으로 돌아간다. 이어서 할 동작은 보류 동작이 맡는다 (PRD §14.1).
        ScreenId.SC21 -> UpgradeScreen(UpgradeFeature.parse(route.arg), onClose = { navigator.back() })
        ScreenId.SC06 -> ResultScreen(
            onOpenLearn = { navigator.replaceTop(Route(ScreenId.SC04)) },
            onClose = { navigator.back() },
        )
        else -> PlaceholderScreen(route)
    }
}
