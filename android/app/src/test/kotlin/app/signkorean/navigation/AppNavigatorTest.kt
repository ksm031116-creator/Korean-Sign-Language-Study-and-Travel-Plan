package app.signkorean.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 화면 이동 규칙 (PRD §14: Back 은 한 단계, 루트 탭 전환은 각 탭의 스택 보존). */
class AppNavigatorTest {

    private val nav = AppNavigator()
    private fun current() = nav.state.value.current

    @Test
    fun `처음에는 Explore 루트`() {
        assertEquals(RootTab.Explore, nav.state.value.tab)
        assertEquals(Route(ScreenId.SC02), current())
    }

    @Test
    fun `push 한 화면에서 Back 은 한 단계 돌아간다`() {
        nav.push(Route(ScreenId.SC03, "myeongdong"))
        nav.push(Route(ScreenId.SC04, "area:myeongdong"))
        assertTrue(nav.back())
        assertEquals(Route(ScreenId.SC03, "myeongdong"), current())
    }

    @Test
    fun `탭을 바꿨다 돌아오면 그 탭의 스택이 그대로다`() {
        nav.push(Route(ScreenId.SC03, "myeongdong"))
        nav.selectTab(RootTab.Saved)
        assertEquals(Route(ScreenId.SC09), current())
        nav.push(Route(ScreenId.SC08, "b01"))
        nav.selectTab(RootTab.Explore)
        assertEquals(Route(ScreenId.SC03, "myeongdong"), current())
        nav.selectTab(RootTab.Saved)
        assertEquals(Route(ScreenId.SC08, "b01"), current())
    }

    @Test
    fun `지금 탭을 다시 누르면 그 탭의 루트로 간다`() {
        nav.push(Route(ScreenId.SC03, "myeongdong"))
        nav.selectTab(RootTab.Explore)
        assertEquals(Route(ScreenId.SC02), current())
    }

    @Test
    fun `다른 탭의 루트에서 Back 은 Explore 로, Explore 루트에서 Back 은 앱을 떠난다`() {
        nav.selectTab(RootTab.Plan)
        assertTrue(nav.back())
        assertEquals(RootTab.Explore, nav.state.value.tab)
        assertFalse(nav.back())
        assertEquals(Route(ScreenId.SC02), current())
    }

    @Test
    fun `루트 화면은 push 할 수 없다 (탭 전환으로만 간다)`() {
        nav.push(Route(ScreenId.SC09))
        assertEquals(RootTab.Saved, nav.state.value.tab)
        assertEquals(1, nav.state.value.stack.size)
    }

    @Test
    fun `같은 화면을 연달아 push 하면 한 번만 쌓인다 (빠른 두 번 탭)`() {
        nav.push(Route(ScreenId.SC03, "myeongdong"))
        nav.push(Route(ScreenId.SC03, "myeongdong"))
        assertEquals(2, nav.state.value.stack.size)
    }

    @Test
    fun `replaceTop 은 현재 화면만 바꾼다 (SC21 결제 성공 뒤 원래 동작으로)`() {
        nav.push(Route(ScreenId.SC21, "plan"))
        nav.replaceTop(Route(ScreenId.SC12))
        assertEquals(listOf(Route(ScreenId.SC02), Route(ScreenId.SC12)), nav.state.value.stack)
    }

    @Test
    fun `저장했다가 복원하면 탭과 모든 스택이 같다 (프로세스 종료 후)`() {
        nav.push(Route(ScreenId.SC03, "a:b|c"))
        nav.selectTab(RootTab.Saved)
        nav.push(Route(ScreenId.SC08, "b01"))
        val restored = AppNavigator.restore(nav.save())
        assertEquals(nav.state.value, restored.state.value)
    }

    @Test
    fun `깨진 저장값은 처음 상태로 복원한다`() {
        assertEquals(AppNavigator().state.value, AppNavigator.restore("garbage").state.value)
        assertEquals(AppNavigator().state.value, AppNavigator.restore("").state.value)
    }

    @Test
    fun `모든 화면 ID 는 SC01~SC21 이고 SC05 는 없다 (v2_2 에서 삭제)`() {
        assertEquals(20, ScreenId.entries.size)
        assertFalse(ScreenId.entries.any { it.name == "SC05" })
        assertEquals(setOf(ScreenId.SC02, ScreenId.SC09, ScreenId.SC11), RootTab.entries.map { it.root }.toSet())
    }
}
