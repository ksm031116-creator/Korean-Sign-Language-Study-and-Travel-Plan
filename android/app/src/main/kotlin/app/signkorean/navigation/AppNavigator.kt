package app.signkorean.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

data class NavState(val tab: RootTab, val stacks: Map<RootTab, List<Route>>) {
    val stack: List<Route> get() = stacks.getValue(tab)
    val current: Route get() = stack.last()
}

/**
 * 화면 이동 규칙 (PRD §14). Compose 밖의 순수 상태라 JVM 에서 시험한다.
 * - Back 은 스택 한 단계. 탭 루트에서 Back 은 Explore 로, Explore 루트에서는 앱을 떠난다(false).
 * - 탭 전환은 각 탭의 스택을 보존한다. 지금 탭을 다시 누르면 그 탭의 루트로 간다.
 */
class AppNavigator private constructor(initial: NavState) {

    constructor() : this(initialState())

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<NavState> = _state.asStateFlow()

    fun push(route: Route) {
        val rootTab = RootTab.entries.firstOrNull { it.root == route.screen }
        if (rootTab != null) {
            selectTab(rootTab, popToRoot = false)
            return
        }
        _state.update { s ->
            if (s.current == route) s else s.copy(stacks = s.stacks + (s.tab to s.stack + route))
        }
    }

    fun selectTab(tab: RootTab) = selectTab(tab, popToRoot = true)

    private fun selectTab(tab: RootTab, popToRoot: Boolean) {
        _state.update { s ->
            if (s.tab == tab && popToRoot) s.copy(stacks = s.stacks + (tab to listOf(Route(tab.root))))
            else s.copy(tab = tab)
        }
    }

    /** 현재 화면만 바꾼다. 예: SC21 결제 성공 뒤 원래 하려던 화면으로. 루트 화면은 바꾸지 않는다. */
    fun replaceTop(route: Route) {
        _state.update { s ->
            if (s.stack.size == 1) s.copy(stacks = s.stacks + (s.tab to s.stack + route))
            else s.copy(stacks = s.stacks + (s.tab to s.stack.dropLast(1) + route))
        }
    }

    /** 한 단계 뒤로. 앱을 떠나야 하면 false. */
    fun back(): Boolean {
        val s = _state.value
        return when {
            s.stack.size > 1 -> {
                _state.value = s.copy(stacks = s.stacks + (s.tab to s.stack.dropLast(1)))
                true
            }
            s.tab != RootTab.Explore -> {
                _state.value = s.copy(tab = RootTab.Explore)
                true
            }
            else -> false
        }
    }

    /** 프로세스 종료 뒤 복원용 문자열. rememberSaveable 에 넣는다. */
    fun save(): String {
        val s = _state.value
        return buildString {
            append(s.tab.name)
            RootTab.entries.forEach { tab ->
                append('\n').append(tab.name).append('=')
                append(s.stacks.getValue(tab).joinToString(ROUTE_SEP) { r -> r.screen.name + (r.arg?.let { ARG_SEP + escape(it) } ?: "") })
            }
        }
    }

    companion object {
        private const val ROUTE_SEP = ";"
        private const val ARG_SEP = ":"

        private fun initialState() = NavState(RootTab.Explore, RootTab.entries.associateWith { listOf(Route(it.root)) })

        /** 깨진 값이면 처음 상태로 시작한다. 저장된 보조 화면을 억지로 살리지 않는다(PRD §14). */
        fun restore(saved: String): AppNavigator = AppNavigator(runCatching { parse(saved) }.getOrElse { initialState() })

        private fun parse(saved: String): NavState {
            val lines = saved.split('\n')
            val tab = RootTab.valueOf(lines.first())
            val stacks = lines.drop(1).associate { line ->
                val t = RootTab.valueOf(line.substringBefore('='))
                val routes = line.substringAfter('=').split(ROUTE_SEP).map { token ->
                    val screen = ScreenId.valueOf(token.substringBefore(ARG_SEP))
                    Route(screen, if (ARG_SEP in token) unescape(token.substringAfter(ARG_SEP)) else null)
                }
                require(routes.first() == Route(t.root))
                t to routes
            }
            require(stacks.keys == RootTab.entries.toSet())
            return NavState(tab, stacks)
        }

        private fun escape(s: String) = java.net.URLEncoder.encode(s, "UTF-8") // Charset 오버로드는 API 33+
        private fun unescape(s: String) = java.net.URLDecoder.decode(s, "UTF-8")
    }
}
