package app.signkorean.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

/** PRD §14 화면 목록. SC05 는 v2.2 에서 삭제했고 ID 를 다시 쓰지 않는다. */
enum class ScreenId {
    SC01, SC02, SC03, SC04, SC06, SC07, SC08, SC09, SC10,
    SC11, SC12, SC13, SC14, SC15, SC16, SC17, SC18, SC19, SC20, SC21,
}

/** 루트 탭. 각 탭은 자기 스택을 가진다. */
enum class RootTab(val root: ScreenId) {
    Explore(ScreenId.SC02),
    Saved(ScreenId.SC09),
    Plan(ScreenId.SC11),
}

/** 화면 하나. [arg] 는 그 화면이 보여 줄 대상의 ID(카드·지역·장소·여행) 하나. */
data class Route(val screen: ScreenId, val arg: String? = null)

/**
 * 스택에 올라간 화면 하나. 같은 [Route] 를 다시 열어도 [id] 는 새로 받는다.
 * 화면별 저장 상태(입력·스크롤)는 이 id 로 묶여서, 닫았다 다시 연 화면이 지난번 입력을 되살리지 않는다.
 */
data class NavEntry(val id: Long, val route: Route)

data class NavState(val tab: RootTab, val stacks: Map<RootTab, List<NavEntry>>) {
    val stack: List<NavEntry> get() = stacks.getValue(tab)
    val current: NavEntry get() = stack.last()
    val allEntryIds: Set<Long> get() = stacks.values.flatten().map { it.id }.toSet()
}

/**
 * 화면 이동 규칙 (PRD §14). Compose 밖의 순수 상태라 JVM 에서 시험한다.
 * - Back 은 스택 한 단계. 탭 루트에서 Back 은 Explore 로, Explore 루트에서는 앱을 떠난다(false).
 * - 탭 전환은 각 탭의 스택을 보존한다. 지금 탭을 다시 누르면 그 탭의 루트로 간다.
 * - 앱을 다시 실행하면 보조 화면을 자동으로 되살리지 않는다(PRD §14). 그래서 이 상태는 Bundle 에 저장하지 않고
 *   화면 회전·언어 변경을 견디는 ViewModel 에 둔다.
 */
class AppNavigator {

    private val nextId = AtomicLong()
    private fun entry(route: Route) = NavEntry(nextId.getAndIncrement(), route)

    private val _state = MutableStateFlow(
        NavState(RootTab.Explore, RootTab.entries.associateWith { listOf(entry(Route(it.root))) }),
    )
    val state: StateFlow<NavState> = _state.asStateFlow()

    private fun rootTabOf(route: Route): RootTab? = RootTab.entries.firstOrNull { it.root == route.screen }

    fun push(route: Route) {
        val rootTab = rootTabOf(route)
        if (rootTab != null) {
            require(route.arg == null) { "루트 화면에는 인자를 주지 않는다: $route" }
            selectTab(rootTab, popToRoot = false)
            return
        }
        _state.update { s ->
            if (s.current.route == route) s else s.copy(stacks = s.stacks + (s.tab to s.stack + entry(route)))
        }
    }

    fun selectTab(tab: RootTab) = selectTab(tab, popToRoot = true)

    private fun selectTab(tab: RootTab, popToRoot: Boolean) {
        _state.update { s ->
            if (s.tab == tab && popToRoot) s.copy(stacks = s.stacks + (tab to s.stack.take(1)))
            else s.copy(tab = tab)
        }
    }

    /**
     * 현재 화면을 [route] 로 바꾼다. 예: SC21 결제 성공 뒤 원래 하려던 화면으로.
     * - 루트 화면이면 현재 화면을 닫고 그 탭으로 간다.
     * - 바로 아래 화면과 같으면 겹쳐 쌓지 않고 그 화면으로 돌아간다.
     * - 루트만 있으면 루트는 그대로 두고 위에 쌓는다.
     */
    fun replaceTop(route: Route) {
        val rootTab = rootTabOf(route)
        _state.update { s ->
            val popped = if (s.stack.size > 1) s.stack.dropLast(1) else s.stack
            when {
                rootTab != null -> s.copy(tab = rootTab, stacks = s.stacks + (s.tab to popped))
                popped.last().route == route -> s.copy(stacks = s.stacks + (s.tab to popped))
                else -> s.copy(stacks = s.stacks + (s.tab to popped + entry(route)))
            }
        }
    }

    /**
     * 학습 화면(SC04)을 연다. 지금 탭 스택에 SC04 가 이미 있으면 그 위의 화면과 함께 닫고 새로 연다.
     * 그대로 하나 더 쌓으면 아래에 남은 SC04 가 교체 전 세션을 보인다(Phase 14 리뷰 PLC-04).
     */
    fun openLearn() {
        _state.update { s ->
            val at = s.stack.indexOfFirst { it.route.screen == ScreenId.SC04 }
            val base = if (at > 0) s.stack.take(at) else s.stack
            s.copy(stacks = s.stacks + (s.tab to base + entry(Route(ScreenId.SC04))))
        }
    }

    /** 한 단계 뒤로. 앱을 떠나야 하면 false. */
    fun back(): Boolean {
        var moved = false
        _state.update { s ->
            when {
                s.stack.size > 1 -> s.copy(stacks = s.stacks + (s.tab to s.stack.dropLast(1))).also { moved = true }
                s.tab != RootTab.Explore -> s.copy(tab = RootTab.Explore).also { moved = true }
                else -> s.also { moved = false }
            }
        }
        return moved
    }

    /** [known] 중 지금 어느 스택에도 없는 항목. 화면별 저장 상태를 지우는 데 쓴다. */
    fun closedSince(known: Set<Long>): Set<Long> = known - _state.value.allEntryIds
}
